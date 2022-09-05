package com.nerjal.recipedisable;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

@Environment(EnvType.CLIENT)
public class RecipeDisablerClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STOPPING.register(
            client -> {
                Success success = ConfigLoader.getConfig().save(client);
                if (success == Success.SUCCESS)
                    RecipeDisabler.LOGGER.info(String.format("Successfully saved %s/%s",
                            RecipeDisabler.configFolder, RecipeDisabler.configFile));
                else if (success == Success.FAIL) {
                    RecipeDisabler.LOGGER.error(String.format("An error occurred while trying to save %s/%s",
                            RecipeDisabler.configFolder, RecipeDisabler.configFile));
                }
            }
        );
    }
}
