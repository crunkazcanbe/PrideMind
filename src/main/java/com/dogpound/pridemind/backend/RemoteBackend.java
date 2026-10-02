package com.dogpound.pridemind.backend;

import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Any OpenAI-style server: her llama-swap, LiteLLM, Ollama (/v1), or the future PrideAI server hub. */
public final class RemoteBackend implements Backend {
    private final String url, model, key;

    public RemoteBackend(String url, String model, String key) {
        this.url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.model = model;
        this.key = key;
    }

    public String name() { return "server " + url + " (" + model + ")"; }
    public boolean ready() { return true; }

    public String complete(String system, String prompt, int maxTokens, double temperature) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", maxTokens);
        body.addProperty("temperature", temperature);
        body.addProperty("stream", false);
        JsonArray msgs = new JsonArray();
        msgs.add(msg("system", system));
        msgs.add(msg("user", prompt));
        body.add("messages", msgs);
        HttpURLConnection c = (HttpURLConnection) new URL(url + "/chat/completions").openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(3000);
        c.setReadTimeout(60_000);
        c.setRequestProperty("Content-Type", "application/json");
        if (key != null && !key.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + key);
        try (OutputStream o = c.getOutputStream()) { o.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
        if (c.getResponseCode() / 100 != 2) throw new IllegalStateException("HTTP " + c.getResponseCode());
        try (Reader r = new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8)) {
            JsonObject res = new JsonParser().parse(r).getAsJsonObject();
            return res.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message").get("content").getAsString().trim();
        }
    }

    private static JsonObject msg(String role, String content) {
        JsonObject m = new JsonObject();
        m.addProperty("role", role);
        m.addProperty("content", content);
        return m;
    }
}
