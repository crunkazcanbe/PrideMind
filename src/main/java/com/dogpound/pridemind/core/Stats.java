package com.dogpound.pridemind.core;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** What the Mind has been doing, per purpose — shown by /mind. */
public final class Stats {
    public static final class Line {
        public final AtomicLong asked = new AtomicLong(), remembered = new AtomicLong(), answered = new AtomicLong(),
                late = new AtomicLong(), full = new AtomicLong(), failed = new AtomicLong(), offline = new AtomicLong(), totalMs = new AtomicLong();
        public long avgMs() { long a = answered.get(); return a == 0 ? 0 : totalMs.get() / a; }
    }

    private final Map<String, Line> lines = new ConcurrentHashMap<>();
    public volatile int queued, memoSize;
    public volatile String backend = "";

    Line line(String purpose) { return lines.computeIfAbsent(purpose, k -> new Line()); }

    /** per-purpose lines, grouped by the part before the first dot ("mob.zombie" → "mob") when asked */
    public Map<String, Line> lines(boolean grouped) {
        if (!grouped) return new TreeMap<>(lines);
        Map<String, Line> g = new TreeMap<>();
        lines.forEach((k, v) -> {
            Line t = g.computeIfAbsent(k.contains(".") ? k.substring(0, k.indexOf('.')) : k, x -> new Line());
            t.asked.addAndGet(v.asked.get()); t.remembered.addAndGet(v.remembered.get()); t.answered.addAndGet(v.answered.get());
            t.late.addAndGet(v.late.get()); t.full.addAndGet(v.full.get()); t.failed.addAndGet(v.failed.get());
            t.offline.addAndGet(v.offline.get()); t.totalMs.addAndGet(v.totalMs.get());
        });
        return g;
    }
}
