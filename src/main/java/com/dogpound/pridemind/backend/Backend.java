package com.dogpound.pridemind.backend;

/** Where answers come from. Swappable without callers noticing: off, a local server, or (later) a model inside the game. */
public interface Backend {
    String name();
    /** false = every request gets its fallback (nothing is sent anywhere) */
    boolean ready();
    String complete(String system, String prompt, int maxTokens, double temperature) throws Exception;

    /** the default until she turns a model on: no model, mobs use their built-in reflexes */
    Backend OFF = new Backend() {
        public String name() { return "off (built-in reflexes only)"; }
        public boolean ready() { return false; }
        public String complete(String s, String p, int m, double t) { throw new IllegalStateException("off"); }
    };
}
