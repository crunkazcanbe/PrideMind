package com.dogpound.pridemind.tools;

import static com.dogpound.pridemind.api.Tool.Level.LOOK;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.dogpound.pridemind.api.Tools;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

/** PERCEPTION — what's around: blocks, light, biome, weather, structures, mobs, players, containers, machines. All look-only. */
public final class Perception {
    public static void register() {
        Tools.simple("block_at", "x:int y:int z:int", "The block at a position (default: where the asker stands).", LOOK, (c, a) -> {
            BlockPos p = J.pos(a, c.at);
            IBlockState s = c.world.getBlockState(p);
            TileEntity te = c.world.getTileEntity(p);
            return J.map("pos", J.at(p), "block", J.block(s), "hasMachineData", te != null, "hardness", s.getBlockHardness(c.world, p),
                    "light", c.world.getLight(p), "loaded", c.world.isBlockLoaded(p));
        });

        Tools.simple("find_blocks", "block:string radius:int limit:int", "Nearest blocks whose id contains the text (e.g. 'diamond_ore', 'mekanism:'), sorted by distance.", LOOK, (c, a) -> {
            String q = J.s(a, "block", "").toLowerCase(Locale.ROOT);
            int r = J.radius(a, 16, 48), lim = Math.max(1, Math.min(50, J.i(a, "limit", 10)));
            List<Map<String, Object>> out = new ArrayList<>();
            BlockPos c0 = c.at;
            List<BlockPos> hits = new ArrayList<>();
            for (BlockPos.MutableBlockPos p : BlockPos.getAllInBoxMutable(c0.add(-r, -r, -r), c0.add(r, r, r))) {
                if (!c.world.isBlockLoaded(p)) continue;
                if (J.id(c.world.getBlockState(p)).contains(q)) hits.add(p.toImmutable());
            }
            hits.sort((x, y) -> Double.compare(x.distanceSq(c0), y.distanceSq(c0)));
            for (BlockPos p : hits.subList(0, Math.min(lim, hits.size())))
                out.add(J.map("pos", J.at(p), "block", J.id(c.world.getBlockState(p)), "distance", (int) Math.sqrt(p.distanceSq(c0))));
            return J.map("searched", "radius " + r, "found", hits.size(), "nearest", out);
        });

        Tools.simple("scan_area", "radius:int", "Counts of every block type around (the 20 most common, air left out).", LOOK, (c, a) -> {
            int r = J.radius(a, 8, 24);
            Map<String, Integer> n = new HashMap<>();
            for (BlockPos.MutableBlockPos p : BlockPos.getAllInBoxMutable(c.at.add(-r, -r, -r), c.at.add(r, r, r))) {
                if (!c.world.isBlockLoaded(p)) continue;
                IBlockState s = c.world.getBlockState(p);
                if (s.getMaterial() != net.minecraft.block.material.Material.AIR) n.merge(J.id(s), 1, Integer::sum);
            }
            Map<String, Integer> top = new LinkedHashMap<>();
            n.entrySet().stream().sorted((x, y) -> y.getValue() - x.getValue()).limit(20).forEach(e -> top.put(e.getKey(), e.getValue()));
            return J.map("radius", r, "kinds", n.size(), "common", top);
        });

        Tools.simple("environment", "x:int y:int z:int", "Biome, temperature, time of day, weather, light, sky, dimension and moon phase at a position.", LOOK, (c, a) -> {
            BlockPos p = J.pos(a, c.at);
            World w = c.world;
            Biome b = w.getBiome(p);
            long t = w.getWorldTime() % 24000;
            return J.map("biome", b.getRegistryName() == null ? b.getBiomeName() : b.getRegistryName().toString(), "temperature", b.getTemperature(p),
                    "rainfall", b.getRainfall(), "time", t, "day", w.getTotalWorldTime() / 24000, "phase", t < 12000 ? "day" : t < 13800 ? "dusk" : t < 22200 ? "night" : "dawn",
                    "raining", w.isRaining(), "thunder", w.isThundering(), "rainingHere", w.isRainingAt(p.up()),
                    "skyLight", w.getLightFor(EnumSkyBlock.SKY, p), "blockLight", w.getLightFor(EnumSkyBlock.BLOCK, p), "seesSky", w.canSeeSky(p),
                    "dimension", w.provider.getDimension(), "dimensionName", w.provider.getDimensionType().getName(), "moonPhase", w.getMoonPhase(),
                    "difficulty", w.getDifficulty().name(), "surfaceY", w.getHeight(p).getY());
        });

        Tools.simple("nearest_structure", "type:string", "Nearest vanilla structure: Village, Stronghold, Mineshaft, Monument, Mansion, Temple, Fortress, EndCity.", LOOK, (c, a) -> {
            BlockPos p = c.world.findNearestStructure(J.s(a, "type", "Village"), c.at, false);
            return p == null ? J.map("found", false) : J.map("found", true, "pos", J.at(p), "distance", (int) Math.sqrt(p.distanceSq(c.at)));
        });

        Tools.simple("find_entities", "type:string radius:int limit:int hostile:bool", "Mobs/animals/items/players nearby, nearest first. type filters by id text (e.g. 'zombie', 'mca:').", LOOK, (c, a) -> {
            int r = J.radius(a, 24, 96), lim = Math.max(1, Math.min(60, J.i(a, "limit", 15)));
            String q = J.s(a, "type", "").toLowerCase(Locale.ROOT);
            boolean onlyHostile = J.b(a, "hostile", false);
            List<Entity> es = c.world.getEntitiesWithinAABB(Entity.class, new AxisAlignedBB(c.at).grow(r), e -> {
                if (onlyHostile && !(e instanceof IMob)) return false;
                if (q.isEmpty()) return true;
                ResourceLocation id = EntityList.getKey(e);
                return (id != null && id.toString().contains(q)) || e.getName().toLowerCase(Locale.ROOT).contains(q);
            });
            es.sort((x, y) -> Double.compare(x.getDistanceSq(c.at), y.getDistanceSq(c.at)));
            List<Object> out = new ArrayList<>();
            for (Entity e : es.subList(0, Math.min(lim, es.size()))) out.add(J.entity(e, c.at));
            return J.map("found", es.size(), "nearest", out);
        });

        Tools.simple("threat_assessment", "radius:int", "Hostile mobs around with a danger score, the most dangerous first, and which directions are clear.", LOOK, (c, a) -> {
            int r = J.radius(a, 24, 64);
            List<Entity> es = c.world.getEntitiesWithinAABB(Entity.class, new AxisAlignedBB(c.at).grow(r), e -> e instanceof IMob && e.isEntityAlive());
            List<Map<String, Object>> out = new ArrayList<>();
            int[] dir = new int[4];                                            // N E S W
            double total = 0;
            for (Entity e : es) {
                double d = Math.max(1, Math.sqrt(e.getDistanceSq(c.at)));
                double hp = e instanceof EntityLivingBase ? ((EntityLivingBase) e).getMaxHealth() : 10;
                double score = (hp / 10.0 + (e.getName().toLowerCase(Locale.ROOT).contains("creeper") ? 3 : 0)) * (16.0 / d);
                total += score;
                double dx = e.posX - c.at.getX(), dz = e.posZ - c.at.getZ();
                dir[Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? 1 : 3) : (dz > 0 ? 2 : 0)]++;
                Map<String, Object> m = J.entity(e, c.at);
                m.put("danger", Math.round(score * 10) / 10.0);
                out.add(m);
            }
            out.sort((x, y) -> Double.compare((double) y.get("danger"), (double) x.get("danger")));
            String[] names = {"north", "east", "south", "west"};
            Map<String, Object> dirs = new LinkedHashMap<>();
            for (int k = 0; k < 4; k++) dirs.put(names[k], dir[k] == 0 ? "clear" : dir[k] + " hostile");
            return J.map("level", total < 2 ? "LOW" : total < 6 ? "MEDIUM" : total < 15 ? "HIGH" : "EXTREME", "score", Math.round(total * 10) / 10.0,
                    "hostiles", out.subList(0, Math.min(12, out.size())), "directions", dirs);
        });

        Tools.simple("player_state", "name:string", "A player's position, health, hunger, armour, effects, held item, level and game mode (default: the asker).", LOOK, (c, a) -> {
            EntityPlayerMP p = player(c, a);
            if (p == null) return J.map("error", "no such player online");
            List<String> fx = new ArrayList<>();
            for (PotionEffect e : p.getActivePotionEffects()) fx.add(e.getEffectName() + " " + (e.getAmplifier() + 1) + " (" + e.getDuration() / 20 + "s)");
            List<Object> armor = new ArrayList<>();
            for (EntityEquipmentSlot s : new EntityEquipmentSlot[]{EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET})
                armor.add(J.item(p.getItemStackFromSlot(s)));
            return J.map("name", p.getName(), "pos", J.at(p.getPosition()), "dimension", p.dimension, "health", p.getHealth(), "maxHealth", p.getMaxHealth(),
                    "food", p.getFoodStats().getFoodLevel(), "saturation", p.getFoodStats().getSaturationLevel(), "armorPoints", p.getTotalArmorValue(),
                    "armor", armor, "held", J.item(p.getHeldItemMainhand()), "offhand", J.item(p.getHeldItemOffhand()), "effects", fx,
                    "level", p.experienceLevel, "gameMode", p.interactionManager.getGameType().getName(), "flying", p.capabilities.isFlying,
                    "spawn", p.getBedLocation() == null ? null : J.at(p.getBedLocation()));
        });

        Tools.simple("inventory", "name:string", "Everything in a player's inventory (default: the asker), stacked by item.", LOOK, (c, a) -> {
            EntityPlayerMP p = player(c, a);
            if (p == null) return J.map("error", "no such player online");
            return J.map("player", p.getName(), "items", counted(p.inventory), "enderChest", counted(p.getInventoryEnderChest()));
        });

        Tools.simple("players_online", "", "Every player online with where they are.", LOOK, (c, a) -> {
            List<Object> out = new ArrayList<>();
            for (EntityPlayerMP p : c.server.getPlayerList().getPlayers())
                out.add(J.map("name", p.getName(), "dimension", p.dimension, "pos", J.at(p.getPosition()), "health", p.getHealth()));
            return out;
        });

        Tools.simple("container_at", "x:int y:int z:int", "What's inside a chest, machine or any inventory block.", LOOK, (c, a) -> {
            BlockPos p = J.pos(a, c.at);
            TileEntity te = c.world.getTileEntity(p);
            if (te == null) return J.map("error", "no container there");
            IItemHandler h = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
            if (h == null) for (EnumFacing f : EnumFacing.VALUES) if ((h = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, f)) != null) break;
            if (h == null && te instanceof IInventory) return J.map("block", J.id(c.world.getBlockState(p)), "items", counted((IInventory) te));
            if (h == null) return J.map("error", "that block holds no items");
            List<Object> slots = new ArrayList<>();
            int free = 0;
            for (int i = 0; i < h.getSlots(); i++) {
                ItemStack s = h.getStackInSlot(i);
                if (s.isEmpty()) free++; else slots.add(J.item(s));
            }
            return J.map("block", J.id(c.world.getBlockState(p)), "slots", h.getSlots(), "emptySlots", free, "items", slots);
        });

        Tools.simple("machine_status", "x:int y:int z:int", "Diagnose a machine: energy stored/capacity, fluid tanks, items, and the blocks touching it.", LOOK, (c, a) -> {
            BlockPos p = J.pos(a, c.at);
            TileEntity te = c.world.getTileEntity(p);
            Map<String, Object> m = J.map("pos", J.at(p), "block", J.block(c.world.getBlockState(p)), "isMachine", te != null,
                    "redstonePower", c.world.isBlockIndirectlyGettingPowered(p));
            if (te != null) {
                IEnergyStorage e = te.getCapability(CapabilityEnergy.ENERGY, null);
                if (e != null) m.put("energy", J.map("stored", e.getEnergyStored(), "capacity", e.getMaxEnergyStored(),
                        "full", e.getEnergyStored() >= e.getMaxEnergyStored(), "canReceive", e.canReceive(), "canExtract", e.canExtract()));
                IFluidHandler f = te.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null);
                if (f != null) {
                    List<Object> tanks = new ArrayList<>();
                    for (IFluidTankProperties t : f.getTankProperties()) {
                        FluidStack fs = t.getContents();
                        tanks.add(J.map("fluid", fs == null ? "empty" : fs.getLocalizedName(), "amount", fs == null ? 0 : fs.amount, "capacity", t.getCapacity()));
                    }
                    m.put("tanks", tanks);
                }
                IItemHandler h = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
                if (h != null) {
                    int used = 0;
                    for (int i = 0; i < h.getSlots(); i++) if (!h.getStackInSlot(i).isEmpty()) used++;
                    m.put("itemSlots", J.map("total", h.getSlots(), "used", used));
                }
            }
            Map<String, Object> around = new LinkedHashMap<>();
            for (EnumFacing f : EnumFacing.VALUES) {
                BlockPos q = p.offset(f);
                TileEntity n = c.world.getTileEntity(q);
                around.put(f.getName(), J.id(c.world.getBlockState(q)) + (n == null ? "" :
                        (n.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, f.getOpposite()) != null ? " [items]" : "")
                        + (n.getCapability(CapabilityEnergy.ENERGY, f.getOpposite()) != null ? " [energy]" : "")));
            }
            m.put("neighbours", around);
            return m;
        });

        Tools.simple("dimensions", "", "Every loaded dimension with its player and entity counts.", LOOK, (c, a) -> {
            List<Object> out = new ArrayList<>();
            for (Integer id : DimensionManager.getIDs()) {
                World w = DimensionManager.getWorld(id);
                if (w != null) out.add(J.map("id", id, "name", w.provider.getDimensionType().getName(), "players", w.playerEntities.size(), "entities", w.loadedEntityList.size()));
            }
            return out;
        });
    }

    static EntityPlayerMP player(com.dogpound.pridemind.api.ToolContext c, com.google.gson.JsonObject a) {
        String n = J.s(a, "name", "");
        if (n.isEmpty()) return c.player;
        return c.server.getPlayerList().getPlayerByUsername(n);
    }

    static Map<String, Integer> counted(IInventory inv) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty()) m.merge(s.getDisplayName(), s.getCount(), Integer::sum);
        }
        return m;
    }

    private Perception() {}
}
