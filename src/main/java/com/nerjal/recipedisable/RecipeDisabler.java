package com.nerjal.recipedisable;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class RecipeDisabler implements ModInitializer {

    // Config constants
    public static final String configFolder = "config";
    public static final String configFile = "recipeDisable.json5";
    public static final int configVersion = 2;

    public static final Logger LOGGER = LogManager.getLogger("ResourceDisabler");

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing recipe disabler mod");
        ConfigLoader.init();

        ServerLifecycleEvents.SERVER_STOPPING.register((server -> ConfigLoader.getConfig().save(server)));
    }
}
