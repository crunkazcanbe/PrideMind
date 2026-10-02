package com.dogpound.pridemind.core;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.dogpound.pridemind.api.Request;
import com.dogpound.pridemind.backend.Backend;

/**
 * The traffic cop between every mod and the model. Rules, in order:
 * 1. remembered answer → instant, no model;
 * 2. no model connected → the request's fallback, instantly;
 * 3. queue full → the lowest-priority waiting job gives way (gets its fallback);
 * 4. workers take the most important job first, at most callsPerSecond, and skip jobs whose deadline passed.
 * Nothing here ever runs on — or blocks — the game's threads.
 */
public final class Scheduler {
    private static final Scheduler INSTANCE = new Scheduler();
    public static Scheduler get() { return INSTANCE; }

    private static final AtomicLong SEQ = new AtomicLong();

    private static final class Job implements Comparable<Job> {
        final Request r; final CompletableFuture<String> f = new CompletableFuture<>(); final long seq = SEQ.incrementAndGet();
        Job(Request r) { this.r = r; }
        long deadline() { return r.created() + r.deadlineMs; }
        public int compareTo(Job o) {                   // higher priority first, then oldest first
            int c = o.r.priority.compareTo(r.priority);
            return c != 0 ? c : Long.compare(seq, o.seq);
        }
    }

    private final PriorityBlockingQueue<Job> queue = new PriorityBlockingQueue<>();
    private final Memo memo = new Memo(50_000);
    private final Stats stats = new Stats();
    private volatile Backend backend = Backend.OFF;
    private volatile int maxQueue = 256;
    private volatile double callsPerSecond = 3;
    private long nextCallNanos = System.nanoTime();
    private Thread[] workers = new Thread[0];

    /** (re)start with a backend and limits — safe to call again after a config change */
    public synchronized void configure(Backend b, int workerCount, double perSecond, int queueCap) {
        backend = b; callsPerSecond = Math.max(0.1, perSecond); maxQueue = Math.max(8, queueCap);
        stats.backend = b.name();
        for (Thread t : workers) t.interrupt();
        workers = new Thread[Math.max(1, workerCount)];
        for (int i = 0; i < workers.length; i++) {
            Thread t = new Thread(this::work, "PrideMind-" + i);
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY + 1);   // the game's own threads always win
            workers[i] = t;
            t.start();
        }
    }

    public boolean backendReady() { return backend.ready(); }

    public Stats stats() { stats.queued = queue.size(); stats.memoSize = memo.size(); return stats; }

    public void forget() { memo.clear(); }

    public CompletableFuture<String> submit(Request r) {
        Stats.Line line = stats.line(r.purpose);
        line.asked.incrementAndGet();
        if (r.cacheKey != null) {
            String m = memo.get(r.cacheKey);
            if (m != null) { line.remembered.incrementAndGet(); return CompletableFuture.completedFuture(m); }
        }
        if (!backend.ready()) { line.offline.incrementAndGet(); return CompletableFuture.completedFuture(r.fallback); }
        Job j = new Job(r);
        queue.add(j);
        while (queue.size() > maxQueue) {               // make room: the least important waiting job gives way
            Job worst = null;
            for (Job q : queue) if (worst == null || q.compareTo(worst) > 0) worst = q;
            if (worst == null || !queue.remove(worst)) break;
            stats.line(worst.r.purpose).full.incrementAndGet();
            worst.f.complete(worst.r.fallback);
        }
        return j.f;
    }

    private void work() {
        while (!Thread.currentThread().isInterrupted()) {
            Job j;
            try { j = queue.poll(1, TimeUnit.SECONDS); } catch (InterruptedException e) { return; }
            if (j == null || j.f.isDone()) continue;
            Stats.Line line = stats.line(j.r.purpose);
            if (System.currentTimeMillis() > j.deadline()) { line.late.incrementAndGet(); j.f.complete(j.r.fallback); continue; }
            if (j.r.cacheKey != null) {                 // an identical question may have been answered while this one waited
                String m = memo.get(j.r.cacheKey);
                if (m != null) { line.remembered.incrementAndGet(); j.f.complete(m); continue; }
            }
            try { pace(); } catch (InterruptedException e) { j.f.complete(j.r.fallback); return; }
            long t0 = System.currentTimeMillis();
            try {
                String a = backend.complete(j.r.system, j.r.prompt, j.r.maxTokens, j.r.temperature);
                line.answered.incrementAndGet();
                line.totalMs.addAndGet(System.currentTimeMillis() - t0);
                if (j.r.cacheKey != null && a != null) memo.put(j.r.cacheKey, a, j.r.cacheTtlMs);
                j.f.complete(a != null ? a : j.r.fallback);
            } catch (Throwable t) {
                line.failed.incrementAndGet();
                j.f.complete(j.r.fallback);
            }
        }
    }

    /** the budget: at most callsPerSecond model calls across all workers */
    private void pace() throws InterruptedException {
        long wait;
        synchronized (this) {
            long now = System.nanoTime(), gap = (long) (1e9 / callsPerSecond);
            if (nextCallNanos < now) nextCallNanos = now;
            wait = nextCallNanos - now;
            nextCallNanos += gap;
        }
        if (wait > 0) TimeUnit.NANOSECONDS.sleep(wait);
    }

    private Scheduler() {}
}
