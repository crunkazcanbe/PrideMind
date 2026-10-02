package com.dogpound.pridemind.tools;

import static com.dogpound.pridemind.api.Tool.Level.ACT;
import static com.dogpound.pridemind.api.Tool.Level.POWER;

import com.dogpound.pridemind.api.Tools;
import com.dogpound.pridemind.mob.MobBrains;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketTitle;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.storage.WorldInfo;

/**
 * ACTIONS — communicate, steer mobs, and (ops only) change the world. ACT tools are harmless (messages, sounds,
 * particles, nudging a mob); POWER tools are what an op could do with commands.
 */
public final class Actions {
    public static void register() {
        // ---- communicate
        Tools.simple("say", "text:string to:string", "Send a chat message to one player (default: the asker), or to:'all'.", ACT, (c, a) -> {
            String text = "§d[Mind]§r " + J.s(a, "text", "");
            String to = J.s(a, "to", "");
            if ("all".equalsIgnoreCase(to)) { c.server.getPlayerList().sendMessage(new TextComponentString(text)); return J.map("sent", "all"); }
            EntityPlayerMP p = Perception.player(c, to.isEmpty() ? new com.google.gson.JsonObject() : obj("name", to));
            if (p == null) return J.map("error", "no such player");
            p.sendMessage(new TextComponentString(text));
            return J.map("sent", p.getName());
        });

        Tools.simple("title", "text:string subtitle:string to:string", "Show big title text on a player's screen.", ACT, (c, a) -> {
            EntityPlayerMP p = Perception.player(c, obj("name", J.s(a, "to", "")));
            if (p == null) return J.map("error", "no such player");
            p.connection.sendPacket(new SPacketTitle(SPacketTitle.Type.TITLE, new TextComponentString(J.s(a, "text", ""))));
            if (a.has("subtitle")) p.connection.sendPacket(new SPacketTitle(SPacketTitle.Type.SUBTITLE, new TextComponentString(J.s(a, "subtitle", ""))));
            return J.map("shown", p.getName());
        });

        Tools.simple("play_sound", "sound:string x:int y:int z:int volume:float pitch:float", "Play any sound (e.g. 'minecraft:entity.wolf.howl') at a spot.", ACT, (c, a) -> {
            SoundEvent s = SoundEvent.REGISTRY.getObject(new ResourceLocation(J.s(a, "sound", "minecraft:block.note.bell")));
            if (s == null) return J.map("error", "unknown sound");
            BlockPos p = J.pos(a, c.at);
            c.world.playSound(null, p, s, SoundCategory.AMBIENT, (float) J.d(a, "volume", 1), (float) J.d(a, "pitch", 1));
            return J.map("played", true);
        });

        Tools.simple("particles", "type:string x:int y:int z:int count:int", "Puff of particles (heart, flame, smoke, portal, note, villager_happy, ...) at a spot.", ACT, (c, a) -> {
            EnumParticleTypes t = EnumParticleTypes.getByName(J.s(a, "type", "heart"));
            if (t == null) return J.map("error", "unknown particle");
            BlockPos p = J.pos(a, c.at);
            c.world.spawnParticle(t, p.getX() + .5, p.getY() + 1, p.getZ() + .5, Math.max(1, Math.min(200, J.i(a, "count", 12))), .5, .5, .5, .02);
            return J.map("shown", true);
        });

        // ---- mob control (the Mob Brain's hands)
        Tools.simple("mob_intent", "entityId:int intent:string seconds:int why:string",
                "Tell a mob what it wants: ATTACK, STALK, FLEE, CALL_HELP, GROUP_UP or IGNORE (toward the asker).", ACT, (c, a) -> {
            Entity e = c.world.getEntityByID(J.i(a, "entityId", -1));
            if (!(e instanceof EntityCreature)) return J.map("error", "no mob with that entityId");
            boolean ok = MobBrains.command((EntityCreature) e, J.s(a, "intent", "IGNORE"), c.player, J.i(a, "seconds", 12), J.s(a, "why", "told to"));
            return J.map("ok", ok);
        });

        Tools.simple("mob_move", "entityId:int x:int y:int z:int speed:float", "Walk a mob to a spot with its own pathfinding.", ACT, (c, a) -> {
            Entity e = c.world.getEntityByID(J.i(a, "entityId", -1));
            if (!(e instanceof EntityCreature)) return J.map("error", "no mob with that entityId");
            BlockPos p = J.pos(a, c.at);
            return J.map("pathFound", ((EntityCreature) e).getNavigator().tryMoveToXYZ(p.getX() + .5, p.getY(), p.getZ() + .5, J.d(a, "speed", 1.0)));
        });

        // ---- power (ops)
        Tools.simple("set_block", "block:string meta:int x:int y:int z:int", "Place any block (by id) at a spot.", POWER, (c, a) -> {
            Block b = Block.getBlockFromName(J.s(a, "block", "minecraft:stone"));
            if (b == null) return J.map("error", "unknown block");
            @SuppressWarnings("deprecation") IBlockState s = b.getStateFromMeta(J.i(a, "meta", 0));
            BlockPos p = J.pos(a, c.at);
            return J.map("placed", c.world.setBlockState(p, s), "pos", J.at(p));
        });

        Tools.simple("give", "item:string meta:int count:int to:string", "Give a player items (default: the asker).", POWER, (c, a) -> {
            ItemStack st = Knowledge.stack(a);
            EntityPlayerMP p = Perception.player(c, obj("name", J.s(a, "to", "")));
            if (st.isEmpty() || p == null) return J.map("error", st.isEmpty() ? "unknown item" : "no such player");
            st.setCount(Math.max(1, Math.min(st.getMaxStackSize() * 4, J.i(a, "count", 1))));
            String name = st.getDisplayName();
            int n = st.getCount();
            if (!p.inventory.addItemStackToInventory(st)) p.dropItem(st, false);
            return J.map("gave", n + " " + name, "to", p.getName());
        });

        Tools.simple("teleport", "x:int y:int z:int who:string", "Move a player (default: the asker) to a spot in their dimension.", POWER, (c, a) -> {
            EntityPlayerMP p = Perception.player(c, obj("name", J.s(a, "who", "")));
            if (p == null) return J.map("error", "no such player");
            BlockPos d = J.pos(a, p.getPosition());
            p.connection.setPlayerLocation(d.getX() + .5, d.getY(), d.getZ() + .5, p.rotationYaw, p.rotationPitch);
            return J.map("moved", p.getName(), "to", J.at(d));
        });

        Tools.simple("summon", "entity:string x:int y:int z:int", "Spawn any mob/entity by id (e.g. 'minecraft:cow').", POWER, (c, a) -> {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("id", J.s(a, "entity", "minecraft:pig"));
            BlockPos p = J.pos(a, c.at);
            Entity e = net.minecraft.world.chunk.storage.AnvilChunkLoader.readWorldEntityPos(tag, c.world, p.getX() + .5, p.getY(), p.getZ() + .5, true);
            return e == null ? J.map("error", "unknown entity") : J.map("spawned", EntityList.getKey(e) + "", "entityId", e.getEntityId());
        });

        Tools.simple("effect", "potion:string seconds:int level:int to:string", "Give a potion effect (e.g. 'minecraft:speed').", POWER, (c, a) -> {
            Potion pot = Potion.getPotionFromResourceLocation(J.s(a, "potion", "minecraft:speed"));
            EntityPlayerMP p = Perception.player(c, obj("name", J.s(a, "to", "")));
            if (pot == null || p == null) return J.map("error", pot == null ? "unknown potion" : "no such player");
            p.addPotionEffect(new PotionEffect(pot, 20 * Math.max(1, J.i(a, "seconds", 30)), Math.max(0, J.i(a, "level", 1) - 1)));
            return J.map("applied", true);
        });

        Tools.simple("heal", "who:string", "Fill a player's health and hunger.", POWER, (c, a) -> {
            EntityPlayerMP p = Perception.player(c, obj("name", J.s(a, "who", "")));
            if (p == null) return J.map("error", "no such player");
            p.setHealth(p.getMaxHealth());
            p.getFoodStats().setFoodLevel(20);
            p.getFoodStats().setFoodSaturationLevel(10f);
            return J.map("healed", p.getName());
        });

        Tools.simple("set_time", "time:int", "Set the time of day (0 dawn, 6000 noon, 13000 night, 18000 midnight).", POWER, (c, a) -> {
            for (net.minecraft.world.WorldServer w : c.server.worlds) w.setWorldTime(J.i(a, "time", 1000));
            return J.map("time", J.i(a, "time", 1000));
        });

        Tools.simple("set_weather", "weather:string seconds:int", "clear, rain or thunder.", POWER, (c, a) -> {
            WorldInfo i = c.world.getWorldInfo();
            String w = J.s(a, "weather", "clear");
            int t = 20 * Math.max(30, J.i(a, "seconds", 600));
            i.setCleanWeatherTime("clear".equals(w) ? t : 0);
            i.setRainTime("clear".equals(w) ? 0 : t);
            i.setThunderTime("thunder".equals(w) ? t : 0);
            i.setRaining(!"clear".equals(w));
            i.setThundering("thunder".equals(w));
            return J.map("weather", w);
        });

        Tools.simple("run_command", "command:string", "Run any server command (without the slash). Last resort — prefer a specific tool.", POWER, (c, a) -> {
            String cmd = J.s(a, "command", "");
            int r = c.server.getCommandManager().executeCommand(c.player != null ? c.player : c.server, cmd);
            return J.map("command", cmd, "result", r);
        });
    }

    static com.google.gson.JsonObject obj(String k, String v) {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        if (v != null && !v.isEmpty()) o.addProperty(k, v);
        return o;
    }

    private Actions() {}
}
