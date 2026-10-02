package com.dogpound.pridemind.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

/**
 * The catalogue of things the Mind can do. Built-in tools register at start-up (see tools/*); any mod can add its own:
 * <pre>  Tools.register(new Tool() { ... });  // or Tools.simple("reactor_status", "x:int y:int z:int", "...", Level.LOOK, (ctx, a) -> ...) </pre>
 */
public final class Tools {
    public interface Body { Object run(ToolContext ctx, JsonObject a) throws Exception; }

    private static final Map<String, Tool> ALL = new TreeMap<>();
    static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public static synchronized void register(Tool t) { ALL.put(t.name(), t); }

    public static void simple(String name, String args, String description, Tool.Level level, Body body) {
        register(new Tool() {
            public String name() { return name; }
            public String args() { return args; }
            public String description() { return description; }
            public Level level() { return level; }
            public Object run(ToolContext c, JsonObject a) throws Exception { return body.run(c, a); }
        });
    }

    public static synchronized List<Tool> available(Tool.Level max) {
        List<Tool> out = new ArrayList<>();
        for (Tool t : ALL.values()) if (t.level().ordinal() <= max.ordinal()) out.add(t);
        return out;
    }

    public static synchronized Tool get(String name) { return ALL.get(name); }

    /** run a tool on the server thread (waits up to 5 s) and return its result as JSON text — never throws */
    public static String call(ToolContext ctx, String name, JsonObject args) {
        Tool t = get(name);
        if (t == null) return "{\"error\":\"no tool called " + name + "\"}";
        if (t.level().ordinal() > ctx.allowed.ordinal()) return "{\"error\":\"not allowed: " + name + " needs " + t.level() + "\"}";
        JsonObject a = args == null ? new JsonObject() : args;
        try {
            Object r = ctx.server.isCallingFromMinecraftThread() ? t.run(ctx, a)
                    : ctx.server.callFromMainThread(() -> t.run(ctx, a)).get(5, TimeUnit.SECONDS);
            String s = GSON.toJson(r);
            return s.length() > 4000 ? s.substring(0, 4000) + "…(cut)" : s;
        } catch (Throwable e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            return "{\"error\":\"" + String.valueOf(c.getMessage()).replace("\"", "'") + "\"}";
        }
    }

    private Tools() {}
}
