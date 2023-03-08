package com.bawnorton.mcgpt.forge;

import dev.architectury.platform.forge.EventBuses;
import com.bawnorton.mcgpt.MCGPT;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(MCGPT.MOD_ID)
public class MCGPTForge {
    public MCGPTForge() {
        // Submit our event bus to let architectury register our content on the right time
        EventBuses.registerModEventBus(MCGPT.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());
        MCGPT.init();
    }
}
