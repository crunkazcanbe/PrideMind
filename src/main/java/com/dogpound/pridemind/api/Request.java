package com.dogpound.pridemind.api;

/** One question for the Mind. Every field has a sensible default; set only what you need. */
public final class Request {
    public final String purpose;
    public String system = "You are part of a Minecraft mod. Answer briefly.";
    public String prompt = "";
    public Mind.Priority priority = Mind.Priority.NORMAL;
    /** give up (and use the fallback) if not answered within this time */
    public long deadlineMs = 15_000;
    public int maxTokens = 64;
    public double temperature = 0.4;
    /** same key inside cacheTtlMs = same answer, without asking the model again (null = never cached) */
    public String cacheKey;
    public long cacheTtlMs = 60_000;
    /** what the caller gets when the model can't answer in time */
    public String fallback;
    final long created = System.currentTimeMillis();

    Request(String purpose) { this.purpose = purpose; }

    public Request system(String s) { system = s; return this; }
    public Request prompt(String p) { prompt = p; return this; }
    public Request priority(Mind.Priority p) { priority = p; return this; }
    public Request deadline(long ms) { deadlineMs = ms; return this; }
    public Request maxTokens(int n) { maxTokens = n; return this; }
    public Request temperature(double t) { temperature = t; return this; }
    public Request cacheKey(String k) { cacheKey = k; return this; }
    public Request cacheTtl(long ms) { cacheTtlMs = ms; return this; }
    public Request fallback(String f) { fallback = f; return this; }
    public long created() { return created; }
}
