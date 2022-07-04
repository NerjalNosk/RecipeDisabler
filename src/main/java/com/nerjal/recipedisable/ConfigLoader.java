package com.nerjal.recipedisable;

import com.nerjal.json.elements.JsonNumber;
import com.nerjal.json.elements.JsonObject;
import com.nerjal.json.JsonParser;
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
    private static final String[] templates = {
            "{%s:0,disabled_ids:[]}",
            "{%s:1,disabled_ids:[]}"
    };

    private final HashSet<String[]> ids;
    private JsonObject json;

    private final Runnable[] updaters = {
            this::update_v0_to_v1
    };

    private ConfigLoader() {
        ids = new HashSet<>();
        String path = String.format("%s/%s", RecipeDisable.configFolder, RecipeDisable.configFile);
        try {
            json = loadOrCreateFile(path,(JsonObject) JsonParser.jsonify(
                    String.format(templates[RecipeDisable.configVersion], RecipeDisable.configVersionKey)));
        } catch (JsonParseException ignored) {}
        try {
            if (json.get(RecipeDisable.configVersionKey).getAsInt() > RecipeDisable.configVersion) {
                RecipeDisable.LOGGER.warn(String.format(
                        "RecipeDisabler config version higher than the mod support (%d > %d). "+
                        "Please update the mod or edit the config",
                        json.get(RecipeDisable.configVersionKey).getAsInt(),
                        RecipeDisable.configVersion
                ));
                return;
            }
            while (((JsonNumber)json.get(RecipeDisable.configVersionKey)).getAsInt() < RecipeDisable.configVersion) {
                updaters[json.get(RecipeDisable.configVersionKey).getAsInt()].run();
            }
        } catch (Exception e) {
            RecipeDisable.LOGGER.warn(
                    "An error occurred trying to update RecipeDisabler config. Loading default template", e);
            return;
        }
        try {
            json.get("disabled_ids").getAsJsonArray().forEach(elem -> {
                try {
                    ids.add(elem.getAsString().split(":"));
                } catch (JsonElementTypeException ignored) {}
            });
        } catch (JsonElementTypeException | ChildNotFoundException e) {
            RecipeDisable.LOGGER.warn("Error while parsing the config file. Loading default template.",e);
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
                    RecipeDisable.LOGGER.warn(
                        String.format("Potential error while attempting to create file %s", f.getPath()));
                FileWriter writer = new FileWriter(f);
                String s = JsonParser.stringify(defaultValue);
                writer.write(s, 0, s.length());
                writer.flush();
                writer.close();
            } catch (IOException e) {
                RecipeDisable.LOGGER.error(
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

    private void update_v0_to_v1() {
        json.put(RecipeDisable.configVersionKey, new JsonNumber(1));
    }
}
