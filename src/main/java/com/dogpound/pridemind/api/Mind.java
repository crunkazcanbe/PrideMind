package com.dogpound.pridemind.api;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import com.dogpound.pridemind.core.Scheduler;
import com.dogpound.pridemind.core.Stats;

/**
 * PrideMind — the one shared AI brain every Pride mod can ask.
 *
 * <pre>
 *   Mind.ask(Mind.request("buddy.chat").prompt("...").priority(Priority.HIGH))
 *       .thenAccept(answer -> ...);                         // completes off the game thread
 *
 *   Mind.choose(Mind.request("mob.zombie").prompt(situation).cacheKey(key).fallback("ATTACK"),
 *               Arrays.asList("ATTACK", "FLEE", "IGNORE"));  // always one of the options
 * </pre>
 *
 * Every call has a deadline and a fallback, so a caller never waits forever and the game never waits at all: when the
 * model is busy, off or too slow, the future completes with the fallback. Identical questions (same cacheKey) inside
 * the cache time are answered from memory without asking the model.
 */
public final class Mind {
    public enum Priority { BACKGROUND, LOW, NORMAL, HIGH, URGENT }

    /** start a request; {@code purpose} groups the stats ("mob.zombie", "buddy.chat", "quests.generate") */
    public static Request request(String purpose) { return new Request(purpose); }

    /** free text answer (or the request's fallback) */
    public static CompletableFuture<String> ask(Request r) { return Scheduler.get().submit(r); }

    /** an answer that is always exactly one of {@code options} (the fallback, or the first option, when unsure) */
    public static CompletableFuture<String> choose(Request r, List<String> options) {
        String fb = r.fallback != null && options.contains(r.fallback) ? r.fallback : options.get(0);
        r.fallback(fb);
        if (r.maxTokens > 8) r.maxTokens(8);
        r.prompt(r.prompt + "\nAnswer with exactly one of: " + String.join(", ", options) + ".");
        return ask(r).thenApply(a -> pick(a, options, fb));
    }

    /**
     * A decision with a reason and a confidence (see {@link Decision}). The model is asked for JSON
     * {"decision":..,"reason":..,"confidence":0-1,"seconds":..}; with no model the fallback comes back as a 0.5 "reflex".
     */
    public static CompletableFuture<Decision> decide(Request r, List<String> options) {
        String fb = r.fallback != null && options.contains(r.fallback) ? r.fallback : options.get(0);
        r.fallback(null);
        if (r.maxTokens < 60) r.maxTokens(60);
        r.prompt(r.prompt + "\nOptions: " + String.join(", ", options) + ".\nReply with JSON only: "
                + "{\"decision\":\"<option>\",\"reason\":\"<a few words>\",\"confidence\":<0 to 1>,\"seconds\":<how long it should last>}");
        return ask(r).thenApply(a -> Decision.parse(a == null ? "" : a, options, fb, a != null));
    }

    /** the first option named in the answer (case-insensitive), else the fallback */
    public static String pick(String answer, List<String> options, String fallback) {
        if (answer == null) return fallback;
        String a = answer.toUpperCase(Locale.ROOT);
        int best = Integer.MAX_VALUE; String hit = fallback;
        for (String o : options) {
            int i = a.indexOf(o.toUpperCase(Locale.ROOT));
            if (i >= 0 && i < best) { best = i; hit = o; }
        }
        return hit;
    }

    /** true when a model is actually connected (otherwise every request gets its fallback) */
    public static boolean thinking() { return Scheduler.get().backendReady(); }

    public static Stats stats() { return Scheduler.get().stats(); }

    private Mind() {}
}
