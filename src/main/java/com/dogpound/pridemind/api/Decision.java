package com.dogpound.pridemind.api;

import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * A decision with how sure the Mind is — so the game can act on it sensibly:
 * confidence ≥ 0.7 → do it; 0.4–0.7 → do it briefly, mixed with normal AI; below 0.4 → ignore and re-think later.
 * Reflex (no model / too busy) answers come back at 0.5 with source "reflex".
 */
public final class Decision {
    public final String decision, reason, source;
    public final double confidence;
    /** how long the decision should hold, in seconds (0 = caller's default) */
    public final int seconds;

    public Decision(String decision, String reason, double confidence, int seconds, String source) {
        this.decision = decision; this.reason = reason; this.confidence = confidence; this.seconds = seconds; this.source = source;
    }

    public boolean act() { return confidence >= 0.7; }
    public boolean blend() { return confidence >= 0.4 && confidence < 0.7; }
    public boolean ignore() { return confidence < 0.4; }

    /** read the model's JSON answer; anything unreadable falls back to picking an option word out of the text */
    static Decision parse(String text, List<String> options, String fallback, boolean fromModel) {
        if (!fromModel) return new Decision(fallback, "reflex", 0.5, 0, "reflex");
        try {
            int a = text.indexOf('{'), b = text.lastIndexOf('}');
            if (a >= 0 && b > a) {
                JsonObject o = new JsonParser().parse(text.substring(a, b + 1)).getAsJsonObject();
                String d = Mind.pick(o.has("decision") ? o.get("decision").getAsString() : "", options, null);
                if (d != null) {
                    double conf = o.has("confidence") ? Math.max(0, Math.min(1, o.get("confidence").getAsDouble())) : 0.6;
                    return new Decision(d, o.has("reason") ? o.get("reason").getAsString() : "", conf,
                            o.has("seconds") ? Math.max(0, Math.min(120, o.get("seconds").getAsInt())) : 0, "mind");
                }
            }
        } catch (Throwable ignored) {}
        String d = Mind.pick(text, options, null);
        return d == null ? new Decision(fallback, "unclear answer — used reflex", 0.5, 0, "reflex") : new Decision(d, "", 0.55, 0, "mind");
    }
}
