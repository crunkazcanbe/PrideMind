package com.dogpound.pridemind.api;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The Mind using its tools, step by step: it sees the goal and the tools it's allowed, answers with ONE JSON line —
 * {"tool":"find_blocks","args":{...}} or {"answer":"..."} — the tool runs on the server thread, the result is shown to
 * it, and it continues, up to maxSteps. Every model call still goes through the scheduler (budget, priority).
 */
public final class Agent {
    public static final class Result {
        public final String answer;
        public final List<String> steps;
        Result(String answer, List<String> steps) { this.answer = answer; this.steps = steps; }
    }

    private static final ExecutorService RUN = Executors.newCachedThreadPool(r -> { Thread t = new Thread(r, "PrideMind-agent"); t.setDaemon(true); return t; });

    public static CompletableFuture<Result> run(String purpose, String goal, ToolContext ctx, int maxSteps, Mind.Priority priority) {
        return CompletableFuture.supplyAsync(() -> loop(purpose, goal, ctx, Math.max(1, Math.min(12, maxSteps)), priority), RUN);
    }

    private static Result loop(String purpose, String goal, ToolContext ctx, int maxSteps, Mind.Priority priority) {
        List<String> steps = new ArrayList<>();
        if (!Mind.thinking()) return new Result("(no model connected — set engine = server in config/pridemind.cfg)", steps);
        StringBuilder menu = new StringBuilder();
        for (Tool t : Tools.available(ctx.allowed))
            menu.append("- ").append(t.name()).append('(').append(t.args()).append(") — ").append(t.description()).append('\n');
        String system = "You are PrideMind, the brain inside a heavily modded Minecraft 1.12.2 world. Use tools to look things up or act; never "
                + "guess coordinates, recipes or counts — call a tool. Reply with ONE line of JSON and nothing else:\n"
                + "{\"tool\":\"<name>\",\"args\":{...}}  to use a tool, or  {\"answer\":\"<what to tell the player>\"}  when done.\n"
                + "Positions default to where the asker stands" + (ctx.player != null ? " (" + ctx.player.getName() + " at "
                + ctx.at.getX() + " " + ctx.at.getY() + " " + ctx.at.getZ() + ")" : "") + ".\nTools:\n" + menu;
        StringBuilder convo = new StringBuilder("Goal: ").append(goal).append('\n');
        for (int i = 0; i < maxSteps; i++) {
            String reply;
            try {
                reply = Mind.ask(Mind.request(purpose).system(system).prompt(convo.toString()).priority(priority).maxTokens(300).temperature(0.2)
                        .deadline(90_000).fallback(null)).get(100, TimeUnit.SECONDS);
            } catch (Exception e) { return new Result("(the brain took too long)", steps); }
            if (reply == null) return new Result("(the brain is busy — try again)", steps);
            JsonObject o = json(reply);
            if (o == null) return new Result(reply.trim(), steps);                     // plain text: treat as the answer
            if (o.has("answer")) return new Result(o.get("answer").getAsString(), steps);
            if (!o.has("tool")) return new Result(reply.trim(), steps);
            String name = o.get("tool").getAsString();
            JsonObject args = o.has("args") && o.get("args").isJsonObject() ? o.getAsJsonObject("args") : new JsonObject();
            String result = Tools.call(ctx, name, args);
            steps.add(name + " " + args + " → " + (result.length() > 160 ? result.substring(0, 160) + "…" : result));
            convo.append("You called ").append(name).append(' ').append(args).append("\nResult: ").append(result).append('\n');
        }
        convo.append("Steps are used up. Give your answer now.\n");
        try {
            String reply = Mind.ask(Mind.request(purpose).system(system).prompt(convo.toString()).priority(priority).maxTokens(300).deadline(60_000))
                    .get(70, TimeUnit.SECONDS);
            JsonObject o = reply == null ? null : json(reply);
            return new Result(o != null && o.has("answer") ? o.get("answer").getAsString() : String.valueOf(reply), steps);
        } catch (Exception e) { return new Result("(ran out of steps)", steps); }
    }

    private static JsonObject json(String s) {
        int a = s.indexOf('{'), b = s.lastIndexOf('}');
        if (a < 0 || b <= a) return null;
        try { return new JsonParser().parse(s.substring(a, b + 1)).getAsJsonObject(); } catch (Throwable t) { return null; }
    }

    private Agent() {}
}
