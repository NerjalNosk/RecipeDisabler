package com.nerjal.recipedisable;

import com.nerjal.json.elements.JsonArray;
import com.nerjal.json.elements.JsonBoolean;
import com.nerjal.json.elements.JsonNumber;
import com.nerjal.json.elements.JsonObject;
import com.nerjal.json.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.regex.Pattern;

import static com.nerjal.json.JsonError.*;

public final class ConfigLoader {
    private static ConfigLoader INSTANCE = null;
    public static final String configFilePath = String.format(
            "%s/%s", RecipeDisabler.configFolder, RecipeDisabler.configFile);
    private static final String configVersionKey = "config_version";
    private static final String disabledListKey = "disabled_recipes";
    private static final String forceRecipesInPathKey = "only_track_recipes_folder";
    private static final String dontDisableListKey = "keep_recipes";
    private static final String[] templates = {
            "{%s:0,disabled_ids:[]}",
            "{%s:1,disabled_ids:[]}",
            "{%s:2,%s:[],%s:true,%s:[]}"
    };

    private final HashSet<String[]> ids;
    private final HashSet<String[]> keepIds;
    private boolean recipesFolderOnly;
    private JsonObject json;
    private boolean updated = false;

    private final Runnable[] updaters = {
            this::update_v0_to_v1,
            this::update_v1_to_v2
    };

    private ConfigLoader() {
        ids = new HashSet<>();
        keepIds = new HashSet<>();
        try {
            json = loadOrCreateFile(configFilePath,(JsonObject) JsonParser.jsonify(
                    String.format(templates[RecipeDisabler.configVersion],
                            configVersionKey ,disabledListKey, forceRecipesInPathKey, dontDisableListKey)));
        } catch (JsonParseException ignored) {}
        try {
            if (json.get(configVersionKey).getAsInt() > RecipeDisabler.configVersion) {
                RecipeDisabler.LOGGER.warn(String.format(
                        "RecipeDisabler config version higher than the mod support (%d > %d). "+
                        "Please update the mod or edit the config",
                        json.get(configVersionKey).getAsInt(),
                        RecipeDisabler.configVersion
                ));
                return;
            }
            while (((JsonNumber)json.get(configVersionKey)).getAsInt() < RecipeDisabler.configVersion) {
                updated = true;
                updaters[json.get(configVersionKey).getAsInt()].run();
            }
        } catch (Exception e) {
            RecipeDisabler.LOGGER.warn(
                    "An error occurred trying to update RecipeDisabler config. Loading default template", e);
            return;
        }
        try {
            json.get(disabledListKey).getAsJsonArray().forEach(elem -> {
                try {
                    ids.add(elem.getAsString().split(":"));
                } catch (JsonElementTypeException ignored) {}
            });
            json.get(dontDisableListKey).getAsJsonArray().forEach(elem -> {
                try {
                    keepIds.add(elem.getAsString().split(":"));
                } catch (JsonElementTypeException ignored) {}
            });
            recipesFolderOnly = json.get(forceRecipesInPathKey).getAsBoolean();
        } catch (JsonElementTypeException | ChildNotFoundException e) {
            RecipeDisabler.LOGGER.warn("Error while parsing the config file. Loading default template.",e);
        }
    }

    public static JsonObject loadOrCreateFile(File f, JsonObject defaultValue) {
        try {
            BasicFileAttributes fileAttributes = Files.readAttributes(f.toPath(), BasicFileAttributes.class);
            if (f.exists() && fileAttributes.isRegularFile() && f.canRead()) {
                return (JsonObject) JsonParser.parseFile(f);
            }
        } catch (JsonParseException | IOException ignored) {
            try {
                if ((!f.getParentFile().mkdirs()) &! f.createNewFile())
                    RecipeDisabler.LOGGER.warn(
                        String.format("Potential error while attempting to create file %s", f.getPath()));
                FileWriter writer = new FileWriter(f);
                String s = JsonParser.stringify(defaultValue);
                writer.write(s, 0, s.length());
                writer.flush();
                writer.close();
            } catch (IOException e) {
                RecipeDisabler.LOGGER.error(
                        String.format("Couldn't create file %s, please check edition perms", f.getPath()));
            } catch (RecursiveJsonElementException ignore) {}
        }
        return defaultValue;
    }

    public static JsonObject loadOrCreateFile(String path, JsonObject defaultValue) {
        return loadOrCreateFile(new File(path), defaultValue);
    }

    public static ConfigLoader getConfig() {
        init();
        return INSTANCE;
    }

    public static void init() {
        if (INSTANCE == null) {
            INSTANCE = new ConfigLoader();
        }
    }

    public boolean checkId(Identifier id) {
        if (!id.getPath().endsWith(".json")) return false;
        if (recipesFolderOnly &! id.getPath().startsWith("recipes")) return false;
        for (String[] s : keepIds) {
            String s1 = s[0];
            String s2 = s[1];
            if (!Pattern.matches(s1, id.getNamespace())) continue;
            String path = id.getPath().substring(0, id.getPath().length() - 5);
            String subPath = path.startsWith("recipes/") ? path.substring(8) : path;
            if (Pattern.matches(s2, path) || Pattern.matches(s2, subPath))
                return false;
        }
        for (String[] s : ids) {
            String s1 = s[0];
            String s2 = s[1];
            if (Pattern.matches(s1, id.getNamespace())) {
                String path = id.getPath().substring(0, id.getPath().length() - 5);
                String subPath = path.startsWith("recipes/") ? path.substring(8) : path;
                if (Pattern.matches(s2, path) || Pattern.matches(s2, subPath))
                    return true;
            }
        }
        return false;
    }

    void save(MinecraftServer server) {
        if (!server.isStopping()) return;
        save(new File(configFilePath));
    }

    void save(MinecraftClient client) {
        if (client.isRunning()) return;
        save(new File(configFilePath));
    }

    private void save(File f) {
        if (!updated) return;
        try {
            String s = JsonParser.stringify(json);
            FileWriter writer = new FileWriter(f);
            writer.write(s, 0, s.length());
            writer.flush();
            writer.close();
            RecipeDisabler.LOGGER.info(String.format("Successfully saved %s", f.getPath()));
        } catch (Exception e) {
            RecipeDisabler.LOGGER.error(String.format("An error occurred while trying to save %s", f.getPath()));
        }
    }

    private void update_v0_to_v1() {
        try {
            json.get(configVersionKey).getAsJsonNumber().setValue(1);
        } catch (JsonElementTypeException | ChildNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
    private void update_v1_to_v2() {
        try {
            json.rename("disabled_ids", "disabled_recipes");
            json.add("only_track_recipes_folder", new JsonBoolean(true));
            json.add("keep_recipes", new JsonArray());
            json.get(configVersionKey).getAsJsonNumber().setValue(2);
        } catch (ChildNotFoundException | JsonElementTypeException e) {
            throw new RuntimeException(e);
        }
    }
}
