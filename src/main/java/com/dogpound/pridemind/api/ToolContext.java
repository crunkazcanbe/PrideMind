package com.dogpound.pridemind.api;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Who is asking, where, and how much they may change. */
public final class ToolContext {
    public final MinecraftServer server;
    public final WorldServer world;
    /** the player who asked, or null (a mob brain, a quest, the server) */
    public final EntityPlayerMP player;
    public final BlockPos at;
    public final Tool.Level allowed;

    public ToolContext(MinecraftServer server, WorldServer world, EntityPlayerMP player, BlockPos at, Tool.Level allowed) {
        this.server = server; this.world = world; this.player = player; this.at = at; this.allowed = allowed;
    }

    /** a player asking: ops get every tool, others look / take notes / do harmless things */
    public static ToolContext of(EntityPlayerMP p) {
        boolean op = p.getServer().getPlayerList().canSendCommands(p.getGameProfile());
        return new ToolContext(p.getServer(), p.getServerWorld(), p, p.getPosition(), op ? Tool.Level.POWER : Tool.Level.ACT);
    }
}
