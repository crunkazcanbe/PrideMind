package com.dogpound.pridemind;

import com.dogpound.pridemind.mob.MobBrains;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

@Mod(modid = "pridemind", name = "PrideMind", version = "0.1.0", acceptableRemoteVersions = "*")
public class PrideMind {
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        MindConfig.load(e.getSuggestedConfigurationFile());
        MinecraftForge.EVENT_BUS.register(new MobBrains());
        com.dogpound.pridemind.tools.Perception.register();
        com.dogpound.pridemind.tools.Knowledge.register();
        com.dogpound.pridemind.tools.Memory.register();
        com.dogpound.pridemind.tools.Actions.register();
        System.out.println("[PrideMind] ready — engine: " + com.dogpound.pridemind.core.Scheduler.get().stats().backend
                + ", " + com.dogpound.pridemind.api.Tools.available(com.dogpound.pridemind.api.Tool.Level.POWER).size() + " tools");
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent e) {
        e.registerServerCommand(new MindCommand());
    }
}
