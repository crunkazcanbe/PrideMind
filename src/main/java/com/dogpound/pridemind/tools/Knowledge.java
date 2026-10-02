package com.dogpound.pridemind.tools;

import static com.dogpound.pridemind.api.Tool.Level.LOOK;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.dogpound.pridemind.api.Tools;
import com.google.gson.JsonObject;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;

/**
 * KNOWLEDGE — the modpack encyclopedia: items, recipes, smelting, ore dictionary, mods, mobs, biomes.
 * The code walks the real recipe data; the model only explains it (so a small model can't make recipes up).
 */
public final class Knowledge {
    public static void register() {
        Tools.simple("search_items", "text:string limit:int", "Items whose name or id contains the text, from every mod.", LOOK, (c, a) -> {
            String q = J.s(a, "text", "").toLowerCase(Locale.ROOT);
            int lim = Math.max(1, Math.min(60, J.i(a, "limit", 20)));
            List<Object> out = new ArrayList<>();
            for (Item it : ForgeRegistries.ITEMS) {
                ItemStack st = new ItemStack(it);
                String id = String.valueOf(it.getRegistryName());
                String name;
                try { name = st.getDisplayName(); } catch (Throwable t) { name = id; }
                if (id.toLowerCase(Locale.ROOT).contains(q) || name.toLowerCase(Locale.ROOT).contains(q)) {
                    out.add(J.map("id", id, "name", name));
                    if (out.size() >= lim) break;
                }
            }
            return out;
        });

        Tools.simple("item_info", "item:string meta:int", "What an item is: name, mod, stack size, food value, fuel time, tool/armor, ore-dictionary names.", LOOK, (c, a) -> {
            ItemStack st = stack(a);
            if (st.isEmpty()) return J.map("error", "unknown item");
            Item it = st.getItem();
            List<String> ores = new ArrayList<>();
            for (int id : OreDictionary.getOreIDs(st)) ores.add(OreDictionary.getOreName(id));
            ModContainer mod = Loader.instance().getIndexedModList().get(it.getRegistryName().getResourceDomain());
            return J.map("id", String.valueOf(it.getRegistryName()), "name", st.getDisplayName(), "mod", mod == null ? it.getRegistryName().getResourceDomain() : mod.getName(),
                    "maxStack", st.getMaxStackSize(), "durability", st.isItemStackDamageable() ? st.getMaxDamage() : null,
                    "food", it instanceof ItemFood ? ((ItemFood) it).getHealAmount(st) : null, "fuelTicks", TileEntityFurnace.getItemBurnTime(st),
                    "toolClasses", it.getToolClasses(st), "isBlock", it instanceof net.minecraft.item.ItemBlock, "oreDict", ores,
                    "smeltsInto", J.item(FurnaceRecipes.instance().getSmeltingResult(st)), "craftingRecipes", recipesFor(st, 0).size());
        });

        Tools.simple("recipes_for", "item:string meta:int limit:int", "How to craft an item: every crafting-table recipe that makes it, with its ingredients.", LOOK, (c, a) -> {
            ItemStack st = stack(a);
            if (st.isEmpty()) return J.map("error", "unknown item");
            List<Object> out = new ArrayList<>();
            for (IRecipe r : recipesFor(st, Math.max(1, Math.min(10, J.i(a, "limit", 5))))) out.add(describe(r));
            Map<String, Object> m = J.map("item", st.getDisplayName(), "recipes", out);
            ItemStack smelt = smeltingSource(st);
            if (smelt != null) m.put("orSmelt", smelt.getDisplayName());
            return m;
        });

        Tools.simple("uses_of", "item:string meta:int limit:int", "What an item is used to craft (crafting-table recipes that take it).", LOOK, (c, a) -> {
            ItemStack st = stack(a);
            if (st.isEmpty()) return J.map("error", "unknown item");
            int lim = Math.max(1, Math.min(40, J.i(a, "limit", 15)));
            List<Object> out = new ArrayList<>();
            for (IRecipe r : ForgeRegistries.RECIPES) {
                for (Ingredient in : r.getIngredients())
                    if (in.apply(st)) { out.add(J.map("makes", r.getRecipeOutput().getDisplayName(), "count", r.getRecipeOutput().getCount())); break; }
                if (out.size() >= lim) break;
            }
            return J.map("item", st.getDisplayName(), "usedIn", out);
        });

        Tools.simple("can_craft", "item:string meta:int", "Can the asker craft this right now from their inventory? Lists what's missing per recipe.", LOOK, (c, a) -> {
            ItemStack st = stack(a);
            EntityPlayerMP p = Perception.player(c, a);
            if (st.isEmpty() || p == null) return J.map("error", st.isEmpty() ? "unknown item" : "no player");
            List<Object> out = new ArrayList<>();
            for (IRecipe r : recipesFor(st, 5)) {
                List<String> missing = new ArrayList<>();
                List<ItemStack> inv = new ArrayList<>();
                for (int i = 0; i < p.inventory.getSizeInventory(); i++) inv.add(p.inventory.getStackInSlot(i).copy());
                for (Ingredient in : r.getIngredients()) {
                    if (in == Ingredient.EMPTY || in.getMatchingStacks().length == 0) continue;
                    boolean got = false;
                    for (ItemStack s : inv) if (!s.isEmpty() && in.apply(s)) { s.shrink(1); got = true; break; }
                    if (!got) missing.add(in.getMatchingStacks()[0].getDisplayName());
                }
                out.add(J.map("recipe", describe(r), "missing", missing, "canCraft", missing.isEmpty()));
            }
            return J.map("item", st.getDisplayName(), "recipes", out);
        });

        Tools.simple("smelting", "item:string meta:int", "What an item turns into in a furnace, and the XP.", LOOK, (c, a) -> {
            ItemStack st = stack(a);
            ItemStack out = FurnaceRecipes.instance().getSmeltingResult(st);
            return out.isEmpty() ? J.map("smelts", false) : J.map("smelts", true, "into", J.item(out), "xp", FurnaceRecipes.instance().getSmeltingExperience(out));
        });

        Tools.simple("ore_dictionary", "name:string limit:int", "Every item registered under an ore-dictionary name (e.g. ingotCopper, oreTin, dustIron).", LOOK, (c, a) -> {
            List<Object> out = new ArrayList<>();
            for (ItemStack s : OreDictionary.getOres(J.s(a, "name", ""))) {
                out.add(J.item(s.getMetadata() == OreDictionary.WILDCARD_VALUE ? new ItemStack(s.getItem()) : s));
                if (out.size() >= J.i(a, "limit", 20)) break;
            }
            return out;
        });

        Tools.simple("mods", "text:string", "Installed mods (filter by text), with versions.", LOOK, (c, a) -> {
            String q = J.s(a, "text", "").toLowerCase(Locale.ROOT);
            List<Object> out = new ArrayList<>();
            for (ModContainer m : Loader.instance().getActiveModList())
                if (q.isEmpty() || m.getModId().toLowerCase(Locale.ROOT).contains(q) || m.getName().toLowerCase(Locale.ROOT).contains(q))
                    out.add(J.map("id", m.getModId(), "name", m.getName(), "version", m.getVersion()));
            return q.isEmpty() ? J.map("count", out.size(), "note", "give text to list them") : out;
        });

        Tools.simple("mob_types", "text:string limit:int", "Mob kinds from every mod whose id contains the text.", LOOK, (c, a) -> {
            String q = J.s(a, "text", "").toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (ResourceLocation r : EntityList.getEntityNameList())
                if (r.toString().contains(q)) { out.add(r.toString()); if (out.size() >= J.i(a, "limit", 30)) break; }
            return out;
        });
    }

    /** an item from "mod:name" (+ optional meta); also accepts a display-name search as a last resort */
    static ItemStack stack(JsonObject a) {
        String q = J.s(a, "item", "");
        Item it = Item.getByNameOrId(q);
        if (it == null) {
            String low = q.toLowerCase(Locale.ROOT);
            for (Item i : ForgeRegistries.ITEMS) {
                try { if (new ItemStack(i).getDisplayName().equalsIgnoreCase(low)) { it = i; break; } } catch (Throwable ignored) {}
            }
        }
        return it == null ? ItemStack.EMPTY : new ItemStack(it, 1, J.i(a, "meta", 0));
    }

    static List<IRecipe> recipesFor(ItemStack st, int limit) {
        List<IRecipe> out = new ArrayList<>();
        for (IRecipe r : ForgeRegistries.RECIPES) {
            ItemStack o = r.getRecipeOutput();
            if (!o.isEmpty() && o.getItem() == st.getItem() && (o.getMetadata() == st.getMetadata() || !st.getHasSubtypes())) {
                out.add(r);
                if (limit > 0 && out.size() >= limit) break;
            }
        }
        return out;
    }

    static Map<String, Object> describe(IRecipe r) {
        Map<String, Integer> need = new LinkedHashMap<>();
        for (Ingredient in : r.getIngredients()) {
            ItemStack[] m = in.getMatchingStacks();
            if (m.length == 0) continue;
            String name = m[0].getDisplayName() + (m.length > 1 ? " (or similar)" : "");
            need.merge(name, 1, Integer::sum);
        }
        return J.map("makes", r.getRecipeOutput().getCount(), "ingredients", need, "id", String.valueOf(r.getRegistryName()));
    }

    static ItemStack smeltingSource(ItemStack want) {
        for (Map.Entry<ItemStack, ItemStack> e : FurnaceRecipes.instance().getSmeltingList().entrySet())
            if (ItemStack.areItemsEqual(e.getValue(), want)) return e.getKey();
        return null;
    }

    private Knowledge() {}
}
