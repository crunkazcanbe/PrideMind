package com.dogpound.pridemind;

import java.io.File;

import com.dogpound.pridemind.backend.Backend;
import com.dogpound.pridemind.backend.RemoteBackend;
import com.dogpound.pridemind.core.Scheduler;
import net.minecraftforge.common.config.Configuration;

/** config/pridemind.cfg — every limit that keeps the Mind from ever lagging the game. */
public final class MindConfig {
    public static String backend = "off", url, model, key;
    public static int workers, maxQueue;
    public static double callsPerSecond;
    public static boolean mobsOn, mobsLog;
    public static int mobCooldownSec, mobRange, intentSec;
    public static String[] mobExclude;

    private static Configuration cfg;

    static void load(File f) {
        cfg = new Configuration(f);
        cfg.load();
        backend = cfg.getString("backend", "engine", "off", "Where answers come from: off = no model, mobs use their built-in reflexes (nothing loads); "
                + "server = an OpenAI-style server (llama-swap, LiteLLM, Ollama, the PrideAI hub). An in-game model is coming.", new String[]{"off", "server"});
        url = cfg.getString("url", "engine", "http://127.0.0.1:11434/v1", "Server address ending in /v1.");
        model = cfg.getString("model", "engine", "gemma4-e2b", "Model name on that server.");
        key = cfg.getString("key", "engine", "", "API key if the server needs one.");
        workers = cfg.getInt("workers", "budget", 1, 1, 16, "Questions asked at the same time. One local model = 1.");
        callsPerSecond = cfg.getFloat("callsPerSecond", "budget", 3f, 0.1f, 100f, "Most model calls per second, all mods together. Everything else waits, "
                + "gets a remembered answer, or uses its fallback.");
        maxQueue = cfg.getInt("maxQueue", "budget", 256, 8, 100000, "Most questions waiting. When full, the least important one gives way.");
        mobsOn = cfg.getBoolean("enabled", "mobs", true, "Mob brains: every mob from every mod decides what it wants (hunt, stalk, flee, call friends, "
                + "group up) — vanilla AI still does the walking and fighting. With engine = off they use built-in reflexes.");
        mobCooldownSec = cfg.getInt("cooldownSeconds", "mobs", 15, 2, 600, "A mob thinks again at most this often.");
        mobRange = cfg.getInt("range", "mobs", 48, 8, 256, "Only mobs this close to a player think.");
        intentSec = cfg.getInt("intentSeconds", "mobs", 12, 2, 120, "How long a decision lasts before the mob goes back to normal.");
        mobsLog = cfg.getBoolean("logDecisions", "mobs", true, "Write every decision to logs/pridemind/decisions.jsonl (to train a fast reflex model later).");
        mobExclude = cfg.getStringList("exclude", "mobs", new String[]{"minecraft:ender_dragon", "minecraft:wither", "minecraft:armor_stand"},
                "Mobs that never get a brain (registry names; mod:* for a whole mod).");
        if (cfg.hasChanged()) cfg.save();
        apply();
    }

    static void apply() {
        Backend b = "server".equalsIgnoreCase(backend) ? new RemoteBackend(url, model, key) : Backend.OFF;
        Scheduler.get().configure(b, workers, callsPerSecond, maxQueue);
    }

    private MindConfig() {}
}
