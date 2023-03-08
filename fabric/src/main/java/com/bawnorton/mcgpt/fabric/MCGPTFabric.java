package com.bawnorton.mcgpt.fabric;

import com.bawnorton.mcgpt.MCGPT;
import net.fabricmc.api.ModInitializer;

public class MCGPTFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        MCGPT.init();
    }
}
