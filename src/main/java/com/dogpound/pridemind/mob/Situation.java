package com.dogpound.pridemind.mob;

import java.util.Locale;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.util.ResourceLocation;

/**
 * What a mob knows when it thinks, squeezed into a few buckets. The buckets ARE the answer-memory key: every zombie at
 * night, half health, near a player with a sword, in a group of 3–5, asks the same question — so the model answers it
 * once and the rest of the horde reuses it (that's how a whole world of mobs fits in a few model calls a second).
 */
final class Situation {
    final String type, mod, name, reason, held;
    final boolean hostile, night, wet;
    final int hp, dist, playerHp, group;

    Situation(EntityCreature m, EntityPlayer p, String reason, int sameTypeNearby) {
        ResourceLocation id = EntityList.getKey(m);
        type = id == null ? m.getClass().getSimpleName() : id.toString();
        mod = id == null ? "?" : id.getResourceDomain();
        name = id == null ? type : id.getResourcePath().replace('_', ' ');
        this.reason = reason;
        hostile = m instanceof IMob;
        night = !m.world.isDaytime();
        wet = m.isInWater();
        hp = bucket(m.getHealth() / Math.max(1f, m.getMaxHealth()));
        dist = p == null ? 99 : (int) m.getDistance(p);
        playerHp = p == null ? 100 : bucket(p.getHealth() / Math.max(1f, p.getMaxHealth()));
        held = p == null ? "nothing" : held(p.getHeldItemMainhand());
        group = sameTypeNearby;
    }

    private static int bucket(float f) { return f <= .25f ? 25 : f <= .5f ? 50 : f <= .75f ? 75 : 100; }

    private static String held(ItemStack s) {
        if (s.isEmpty()) return "nothing";
        Item i = s.getItem();
        return i instanceof ItemSword ? "a sword" : i instanceof ItemBow ? "a bow" : i instanceof ItemTool ? "a tool" : i instanceof ItemFood ? "food" : "an item";
    }

    private String distWord() { return dist <= 5 ? "right next to it" : dist <= 12 ? "close" : dist <= 24 ? "nearby" : "far away"; }
    private String groupWord() { return group == 0 ? "alone" : group <= 2 ? "with 1-2 of its kind" : group <= 5 ? "with 3-5 of its kind" : "in a big group of its kind"; }

    /** the memory key: same buckets → same answer */
    String key() {
        return String.join("|", type, reason, String.valueOf(hp), night ? "n" : "d", distWord(), String.valueOf(playerHp), held,
                groupWord(), wet ? "w" : "-");
    }

    String describe() {
        return String.format(Locale.ROOT, "Mob: %s (from the %s mod), %s, %d%% health, %s, %s%s. A player is %s with %d%% health, holding %s. What just happened: %s.",
                name, mod, hostile ? "hostile" : "not hostile", hp, night ? "night" : "day", groupWord(), wet ? ", in water" : "",
                distWord(), playerHp, held, reason);
    }
}
