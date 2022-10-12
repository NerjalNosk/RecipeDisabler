package com.nerjal.recipedisable;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.recipe.Recipe;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

public final class RecipeDisabler implements ModInitializer {

    // Config constants
    public static final String CONFIG_FOLDER = "config";
    public static final String MOD_ID = "recipe_disabler";
    public static final String MOD_NAME = "recipedisabler";
    public static final String CONFIG_FILE = "recipeDisable.json5";
    public static final int CONFIG_VERSION = 3;
    private static boolean reloading = false;
    private static final ReentrantLock lock = new ReentrantLock();

    public static final Logger LOGGER = LogManager.getLogger(MOD_NAME);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing recipe disabler mod");
        ConfigLoader.init();

        // register command
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, dedicated) -> DisablerCommand.register(dispatcher));
        // reload events
        ServerLifecycleEvents.START_DATA_PACK_RELOAD.register((server, resourceManager) -> {
            reloading = true;
            if (ConfigLoader.getConfig().reloadSave()) ConfigLoader.getConfig().save(server);
            else if (ConfigLoader.getConfig().autoReload()) ConfigLoader.getConfig().reload();
        });
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            reloading = false;
        });
        // server (world) stop event
        ServerLifecycleEvents.SERVER_STOPPING.register((server -> {
            Success success = ConfigLoader.getConfig().save(server);
            if (success == Success.SUCCESS)
                LOGGER.info(String.format("Successfully saved %s/%s", CONFIG_FOLDER, CONFIG_FILE));
            else if (success == Success.FAIL) {
                LOGGER.error(String.format("An error occurred while trying to save %s/%s", CONFIG_FOLDER, CONFIG_FILE));
            }
        }));
    }

    static boolean isReloading() {
        return reloading;
    }

    static void disable(MinecraftServer server, String target) {
        new Thread(()-> {
            lock.lock();
            String[] t = target.split(":");
            Collection<Recipe<?>> recipes = server.getRecipeManager().values();
            recipes.removeIf(
                    r -> Pattern.matches(t[0],r.getId().getNamespace()) && Pattern.matches(t[1],r.getId().getPath()) &&
                    ConfigLoader.getConfig().checkId(new Identifier(t[0],t[1])));
            server.getRecipeManager().setRecipes(recipes);
            lock.unlock();
        }).start();
    }
}
