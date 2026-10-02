package com.dogpound.pridemind.mob;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.dogpound.pridemind.MindConfig;
import com.dogpound.pridemind.api.Mind;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Mob brains: every creature from every mod (within range of a player) decides what it WANTS when something happens —
 * it spots you, gets hurt, or you come close — at most once per cooldown. The decision goes through the Mind (answer
 * memory, budget, priority by distance); with no model connected it's the built-in reflex. Vanilla/mod AI still does
 * the walking and fighting; IntentTask only steers.
 */
public final class MobBrains {
    static final class Thought {
        final Intent intent; final UUID player; final long until; final String why, source, reason; final double confidence;
        Thought(Intent i, UUID p, long until, String why, String source, String reason, double confidence) {
            intent = i; player = p; this.until = until; this.why = why; this.source = source; this.reason = reason; this.confidence = confidence;
        }
        EntityPlayer target(World w) { return player == null ? null : w.getPlayerEntityByUUID(player); }
    }

    private static final Map<UUID, Thought> THOUGHTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> NEXT = new ConcurrentHashMap<>();
    private static final ExecutorService LOG = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "PrideMind-log"); t.setDaemon(true); return t; });
    private static final String SYSTEM = "You are the mind of a Minecraft mob. Decide what it wants to do next, true to what that creature is. "
            + "ATTACK = fight the player. STALK = follow at a distance, waiting for a chance. FLEE = run away. CALL_HELP = attack and bring nearby "
            + "friends of its kind. GROUP_UP = go to its group. IGNORE = carry on as normal. Be honest about confidence: 0.9 when the choice is obvious for that creature, 0.5 when it could go either way.";

    static Thought current(EntityCreature m) {
        Thought t = THOUGHTS.get(m.getUniqueID());
        if (t != null && t.until < System.currentTimeMillis()) { THOUGHTS.remove(m.getUniqueID()); return null; }
        return t;
    }

    // ---------------------------------------------------------------- triggers

    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent e) {
        if (!MindConfig.mobsOn || e.getWorld().isRemote || !(e.getEntity() instanceof EntityCreature)) return;
        EntityCreature m = (EntityCreature) e.getEntity();
        if (excluded(m)) return;
        for (EntityAITasks.EntityAITaskEntry t : m.tasks.taskEntries) if (t.action instanceof IntentTask) return;   // already has one
        m.tasks.addTask(0, new IntentTask(m));
    }

    @SubscribeEvent
    public void onTarget(LivingSetAttackTargetEvent e) {
        if (e.getTarget() instanceof EntityPlayer && e.getEntityLiving() instanceof EntityCreature && !e.getEntityLiving().world.isRemote)
            think((EntityCreature) e.getEntityLiving(), (EntityPlayer) e.getTarget(), "it spotted the player");
    }

    @SubscribeEvent
    public void onHurt(LivingHurtEvent e) {
        Entity src = e.getSource().getTrueSource();
        if (!(src instanceof EntityPlayer) || !(e.getEntityLiving() instanceof EntityCreature) || e.getEntityLiving().world.isRemote) return;
        EntityCreature hurt = (EntityCreature) e.getEntityLiving();
        think(hurt, (EntityPlayer) src, "got hurt by the player");
        for (EntityCreature o : hurt.world.getEntitiesWithinAABB(EntityCreature.class, hurt.getEntityBoundingBox().grow(16),
                o -> o != hurt && o.getClass() == hurt.getClass() && o.isEntityAlive()))
            think(o, (EntityPlayer) src, "a friend of its kind was attacked by the player");   // the group reacts together
    }

    private int tick;

    /** every 2 s: creatures within 16 blocks of a player that haven't thought lately */
    @SubscribeEvent
    public void onTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote || !MindConfig.mobsOn || ++tick % 40 != 0) return;
        for (EntityPlayer p : e.world.playerEntities) {
            if (p.isSpectator()) continue;
            for (EntityCreature m : e.world.getEntitiesWithinAABB(EntityCreature.class, p.getEntityBoundingBox().grow(16)))
                think(m, p, "the player came close");
            for (EntityCreature m : e.world.getEntitiesWithinAABB(EntityCreature.class, p.getEntityBoundingBox().grow(32),
                    m -> m.getAttackTarget() == p && !m.canEntityBeSeen(p)))
                think(m, p, "it lost sight of the player");
        }
    }

    // ---------------------------------------------------------------- thinking

    static void think(EntityCreature m, EntityPlayer p, String reason) {
        if (!MindConfig.mobsOn || p == null || excluded(m) || m.getDistance(p) > MindConfig.mobRange) return;
        long now = System.currentTimeMillis();
        Long next = NEXT.get(m.getUniqueID());
        if (next != null && next > now) return;
        NEXT.put(m.getUniqueID(), now + MindConfig.mobCooldownSec * 1000L);
        if (NEXT.size() > 20000) NEXT.values().removeIf(v -> v < now);

        Situation s = new Situation(m, p, reason, m.world.getEntitiesWithinAABB(EntityCreature.class, m.getEntityBoundingBox().grow(16),
                o -> o != m && o.getClass() == m.getClass()).size());
        Intent reflex = Intent.reflex(s);
        UUID id = m.getUniqueID(), pid = p.getUniqueID();
        double d = m.getDistance(p);
        Mind.decide(Mind.request("mob." + s.type).system(SYSTEM).prompt(s.describe()).cacheKey("mob:" + s.key()).cacheTtl(120_000)
                        .fallback(reflex.name()).priority(d <= 8 ? Mind.Priority.HIGH : d <= 20 ? Mind.Priority.NORMAL : Mind.Priority.LOW)
                        .deadline(MindConfig.mobCooldownSec * 500L).temperature(0.6),
                s.hostile ? Intent.HOSTILE : Intent.PASSIVE)
            .thenAccept(dec -> {
                if (MindConfig.mobsLog) log(s, dec);
                if (dec.ignore()) return;                                   // not sure: keep normal AI, think again later
                long secs = dec.seconds > 0 ? dec.seconds : MindConfig.intentSec;
                if (dec.blend()) secs = Math.max(2, secs / 2);              // fairly sure: a short nudge, normal AI takes back over
                THOUGHTS.put(id, new Thought(Intent.valueOf(dec.decision), pid, System.currentTimeMillis() + secs * 1000L, reason, dec.source,
                        dec.reason, dec.confidence));
            });
    }

    /** another mod or a tool steering a mob directly (confidence 1) */
    public static boolean command(EntityCreature m, String intent, EntityPlayer toward, int seconds, String why) {
        Intent i;
        try { i = Intent.valueOf(intent.toUpperCase(Locale.ROOT)); } catch (Exception e) { return false; }
        THOUGHTS.put(m.getUniqueID(), new Thought(i, toward == null ? null : toward.getUniqueID(),
                System.currentTimeMillis() + Math.max(1, seconds) * 1000L, why, "told", why, 1.0));
        return true;
    }

    private static boolean excluded(EntityCreature m) {
        ResourceLocation id = EntityList.getKey(m);
        if (id == null) return false;
        for (String x : MindConfig.mobExclude)
            if (x.equals(id.toString()) || (x.endsWith(":*") && x.substring(0, x.length() - 2).equals(id.getResourceDomain()))) return true;
        return false;
    }

    private static void log(Situation s, com.dogpound.pridemind.api.Decision d) {
        String line = String.format(Locale.ROOT, "{\"t\":%d,\"key\":\"%s\",\"situation\":\"%s\",\"intent\":\"%s\",\"reason\":\"%s\",\"confidence\":%.2f,\"source\":\"%s\"}%n",
                System.currentTimeMillis(), esc(s.key()), esc(s.describe()), d.decision, esc(d.reason), d.confidence, d.source);
        LOG.execute(() -> {
            File f = new File("logs/pridemind/decisions.jsonl");
            f.getParentFile().mkdirs();
            try (FileWriter w = new FileWriter(f, true)) { w.write(line); } catch (Exception ignored) {}
        });
    }

    private static String esc(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }

    /** /mind mobs: what nearby mobs are thinking */
    public static List<String> describeNear(World w, BlockPos at, int r) {
        List<String> out = new ArrayList<>();
        for (EntityCreature m : w.getEntitiesWithinAABB(EntityCreature.class, new net.minecraft.util.math.AxisAlignedBB(at).grow(r))) {
            Thought t = current(m);
            if (t == null) continue;
            out.add(String.format(Locale.ROOT, "§b%s§7 %dm: §f%s §7%.0f%% (%s: %s%s)", m.getName(), (int) Math.sqrt(at.distanceSq(m.getPosition())), t.intent,
                    t.confidence * 100, t.source, t.why, t.reason == null || t.reason.isEmpty() ? "" : " — \"" + t.reason + "\""));
        }
        if (out.isEmpty()) out.add("§7No mob nearby is thinking anything right now.");
        return out;
    }
}
