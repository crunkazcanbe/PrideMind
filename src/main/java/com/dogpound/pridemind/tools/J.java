package com.dogpound.pridemind.tools;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonObject;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

/** small helpers shared by the built-in tools: reading arguments and describing things as JSON-friendly maps */
final class J {
    static int i(JsonObject a, String k, int def) { return a.has(k) ? a.get(k).getAsInt() : def; }
    static double d(JsonObject a, String k, double def) { return a.has(k) ? a.get(k).getAsDouble() : def; }
    static String s(JsonObject a, String k, String def) { return a.has(k) ? a.get(k).getAsString() : def; }
    static boolean b(JsonObject a, String k, boolean def) { return a.has(k) ? a.get(k).getAsBoolean() : def; }

    /** x/y/z from the arguments, defaulting to where the asker is */
    static BlockPos pos(JsonObject a, BlockPos def) {
        return new BlockPos(i(a, "x", def.getX()), i(a, "y", def.getY()), i(a, "z", def.getZ()));
    }

    static int radius(JsonObject a, int def, int max) { return Math.max(1, Math.min(max, i(a, "radius", def))); }

    static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int k = 0; k + 1 < kv.length; k += 2) if (kv[k + 1] != null) m.put(String.valueOf(kv[k]), kv[k + 1]);
        return m;
    }

    static Map<String, Object> at(BlockPos p) { return map("x", p.getX(), "y", p.getY(), "z", p.getZ()); }

    static String id(IBlockState s) { ResourceLocation r = s.getBlock().getRegistryName(); return r == null ? "?" : r.toString(); }

    static Map<String, Object> block(IBlockState s) {
        return map("id", id(s), "name", new ItemStack(s.getBlock(), 1, s.getBlock().damageDropped(s)).getDisplayName(), "meta", s.getBlock().getMetaFromState(s));
    }

    static Map<String, Object> item(ItemStack st) {
        if (st.isEmpty()) return null;
        ResourceLocation r = st.getItem().getRegistryName();
        return map("id", r == null ? "?" : r.toString(), "meta", st.getMetadata(), "count", st.getCount(), "name", st.getDisplayName());
    }

    static Map<String, Object> entity(Entity e, BlockPos from) {
        ResourceLocation r = EntityList.getKey(e);
        return map("entityId", e.getEntityId(), "type", r == null ? (e instanceof net.minecraft.entity.player.EntityPlayer ? "player" : e.getClass().getSimpleName()) : r.toString(),
                "name", e.getName(), "distance", (int) Math.sqrt(from.distanceSq(e.posX, e.posY, e.posZ)), "pos", at(e.getPosition()),
                "health", e instanceof net.minecraft.entity.EntityLivingBase ? (int) ((net.minecraft.entity.EntityLivingBase) e).getHealth() : null,
                "hostile", e instanceof net.minecraft.entity.monster.IMob);
    }

    private J() {}
}
