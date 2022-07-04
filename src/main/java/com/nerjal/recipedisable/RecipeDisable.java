package com.nerjal.recipedisable;

import net.fabricmc.api.ModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class RecipeDisable implements ModInitializer {

    // Config constants
    public static final String configFolder = "config";
    public static final String configFile = "recipeDisable.json5";
    public static final int configVersion = 1;
    public static final String configVersionKey = "config_version";

    public static final Logger LOGGER = LogManager.getLogger("ResourceDisabler");

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing recipe disabler mod");
        ConfigLoader.init();
    }
}
