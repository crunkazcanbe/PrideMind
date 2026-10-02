package com.dogpound.pridemind;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.dogpound.pridemind.api.Mind;
import com.dogpound.pridemind.core.Scheduler;
import com.dogpound.pridemind.core.Stats;
import com.dogpound.pridemind.mob.MobBrains;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

/** /mind — what the brain is doing. /mind mobs — what nearby mobs decided. /mind ask <text> — try it. /mind forget. */
public class MindCommand extends CommandBase {
    @Override public String getName() { return "mind"; }
    @Override public String getUsage(ICommandSender s) { return "/mind [stats|mobs|ask <text>|tools|forget|reload]"; }
    @Override public int getRequiredPermissionLevel() { return 2; }

    @Override
    public void execute(MinecraftServer server, ICommandSender s, String[] a) {
        String sub = a.length == 0 ? "stats" : a[0];
        switch (sub) {
            case "mobs":
                for (String line : MobBrains.describeNear(s.getEntityWorld(), s.getPosition(), 32)) say(s, line);
                return;
            case "ask": {
                String q = String.join(" ", Arrays.copyOfRange(a, 1, a.length));
                if (!(s instanceof net.minecraft.entity.player.EntityPlayerMP)) { say(s, "§cAsk from in-game."); return; }
                net.minecraft.entity.player.EntityPlayerMP p = (net.minecraft.entity.player.EntityPlayerMP) s;
                say(s, "§7thinking…");
                com.dogpound.pridemind.api.Agent.run("command.ask", q, com.dogpound.pridemind.api.ToolContext.of(p), 8, Mind.Priority.HIGH)
                    .thenAccept(r -> server.addScheduledTask(() -> {
                        for (String step : r.steps) say(s, "§8  ↳ " + step);
                        say(s, "§d" + r.answer);
                    }));
                return;
            }
            case "tools": {
                for (com.dogpound.pridemind.api.Tool t : com.dogpound.pridemind.api.Tools.available(com.dogpound.pridemind.api.Tool.Level.POWER))
                    say(s, "§b" + t.name() + " §8[" + t.level() + "] §7" + t.description());
                return;
            }
            case "forget": Scheduler.get().forget(); say(s, "§aForgot every remembered answer."); return;
            case "reload": MindConfig.load(new java.io.File("config/pridemind.cfg")); say(s, "§aReloaded config/pridemind.cfg — " + Mind.stats().backend); return;
            default:
                Stats st = Mind.stats();
                say(s, "§d§lPrideMind §7engine: §f" + st.backend + " §7· waiting §f" + st.queued + " §7· remembered §f" + st.memoSize);
                for (Map.Entry<String, Stats.Line> e : st.lines(true).entrySet()) {
                    Stats.Line l = e.getValue();
                    say(s, String.format("§b%s§7: asked §f%d§7, from memory §a%d§7, model §e%d§7 (%d ms), reflex §6%d§7, late %d, crowded %d, failed %d",
                            e.getKey(), l.asked.get(), l.remembered.get(), l.answered.get(), l.avgMs(), l.offline.get(), l.late.get(), l.full.get(), l.failed.get()));
                }
        }
    }

    private static void say(ICommandSender s, String t) { s.sendMessage(new TextComponentString(t)); }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender s, String[] a, BlockPos pos) {
        return a.length == 1 ? getListOfStringsMatchingLastWord(a, "stats", "mobs", "ask", "tools", "forget", "reload") : Collections.emptyList();
    }
}
