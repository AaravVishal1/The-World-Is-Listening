package com.worldislistening;

import net.fabricmc.api.ModInitializer;

public class WorldIsListeningMod implements ModInitializer {
    public static final String MOD_ID = "worldislistening";

    @Override
    public void onInitialize() {
        BehaviorTracker.init();
        WorldResponseHandler.init();
    }
}