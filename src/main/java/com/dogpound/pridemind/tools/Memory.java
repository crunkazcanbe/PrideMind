package com.dogpound.pridemind.tools;

import static com.dogpound.pridemind.api.Tool.Level.LOOK;
import static com.dogpound.pridemind.api.Tool.Level.NOTE;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.dogpound.pridemind.api.ToolContext;
import com.dogpound.pridemind.api.Tools;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.DimensionManager;

/**
 * MEMORY — the world remembers: named places ("my industrial area"), events (found a village, died here), per world.
 * Saved in the world folder (pridemind/memory.json) so it travels with the save. Anyone may add notes; only the
 * owner of a note (or an op) removes it.
 */
public final class Memory {
    public static final class Note {
        public String kind, name, text, by;
        public int x, y, z, dim;
        public long when, day;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static List<Note> notes;
    private static File loadedFrom;

    public static synchronized List<Note> all() {
        File f = file();
        if (notes == null || !f.equals(loadedFrom)) {
            notes = new ArrayList<>();
            loadedFrom = f;
            if (f.isFile()) try (Reader r = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) {
                Note[] n = GSON.fromJson(r, Note[].class);
                if (n != null) for (Note x : n) notes.add(x);
            } catch (Exception e) { System.out.println("[PrideMind] couldn't read " + f + ": " + e); }
        }
        return notes;
    }

    private static synchronized void save() {
        File f = file();
        f.getParentFile().mkdirs();
        try (Writer w = Files.newBufferedWriter(f.toPath(), StandardCharsets.UTF_8)) { GSON.toJson(notes, w); }
        catch (Exception e) { System.out.println("[PrideMind] couldn't save memory: " + e); }
    }

    private static File file() {
        File root = DimensionManager.getCurrentSaveRootDirectory();
        return new File(root == null ? new File("config") : root, "pridemind/memory.json");
    }

    /** any mod can record an event ("Found a village", "Died to a creeper") */
    public static synchronized Note remember(ToolContext c, String kind, String name, String text, BlockPos p) {
        Note n = new Note();
        n.kind = kind; n.name = name; n.text = text; n.x = p.getX(); n.y = p.getY(); n.z = p.getZ();
        n.dim = c.world.provider.getDimension(); n.when = System.currentTimeMillis(); n.day = c.world.getTotalWorldTime() / 24000;
        n.by = c.player == null ? "world" : c.player.getName();
        all().removeIf(o -> "place".equals(kind) && "place".equals(o.kind) && o.name.equalsIgnoreCase(name) && o.by.equals(n.by));
        all().add(n);
        if (notes.size() > 5000) notes.remove(0);
        save();
        return n;
    }

    public static void register() {
        Tools.simple("remember_place", "name:string note:string x:int y:int z:int", "Save a named place (default: where the asker stands), e.g. 'industrial area'.", NOTE, (c, a) -> {
            Note n = remember(c, "place", J.s(a, "name", "unnamed place"), J.s(a, "note", ""), J.pos(a, c.at));
            return J.map("saved", n.name, "pos", J.at(new BlockPos(n.x, n.y, n.z)), "dimension", n.dim);
        });

        Tools.simple("remember_event", "text:string x:int y:int z:int", "Write down something that happened (the world's diary).", NOTE, (c, a) -> {
            Note n = remember(c, "event", "", J.s(a, "text", ""), J.pos(a, c.at));
            return J.map("saved", true, "day", n.day);
        });

        Tools.simple("recall", "text:string limit:int", "Search remembered places and events (by words in the name or note), nearest/newest first.", LOOK, (c, a) -> {
            String[] words = J.s(a, "text", "").toLowerCase(Locale.ROOT).split("\\s+");
            List<Note> hits = new ArrayList<>();
            for (Note n : all()) {
                String hay = (n.name + " " + n.text + " " + n.kind).toLowerCase(Locale.ROOT);
                boolean ok = true;
                for (String w : words) if (!w.isEmpty() && !hay.contains(w)) { ok = false; break; }
                if (ok) hits.add(n);
            }
            hits.sort((x, y) -> Long.compare(y.when, x.when));
            List<Object> out = new ArrayList<>();
            for (Note n : hits.subList(0, Math.min(J.i(a, "limit", 10), hits.size()))) {
                BlockPos p = new BlockPos(n.x, n.y, n.z);
                out.add(J.map("kind", n.kind, "name", n.name, "note", n.text, "pos", J.at(p), "dimension", n.dim, "day", n.day, "by", n.by,
                        "distance", n.dim == c.world.provider.getDimension() ? (int) Math.sqrt(p.distanceSq(c.at)) : null,
                        "direction", n.dim == c.world.provider.getDimension() ? direction(c.at, p) : "other dimension"));
            }
            return J.map("found", hits.size(), "notes", out);
        });

        Tools.simple("forget_place", "name:string", "Remove a saved place (yours, or any if you're an op).", NOTE, (c, a) -> {
            String name = J.s(a, "name", "");
            boolean op = c.allowed == com.dogpound.pridemind.api.Tool.Level.POWER;
            String who = c.player == null ? "world" : c.player.getName();
            synchronized (Memory.class) {
                boolean gone = all().removeIf(n -> "place".equals(n.kind) && n.name.equalsIgnoreCase(name) && (op || n.by.equals(who)));
                if (gone) save();
                return J.map("removed", gone);
            }
        });
    }

    static String direction(BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        if (Math.abs(dx) < 8 && Math.abs(dz) < 8) return "here";
        String ns = dz < -Math.abs(dx) / 2 ? "north" : dz > Math.abs(dx) / 2 ? "south" : "";
        String ew = dx > Math.abs(dz) / 2 ? "east" : dx < -Math.abs(dz) / 2 ? "west" : "";
        return ns + ew;
    }

    private Memory() {}
}
