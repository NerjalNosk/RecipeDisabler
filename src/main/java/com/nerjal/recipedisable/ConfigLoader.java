package com.nerjal.recipedisable;

import com.nerjal.json.elements.*;
import com.nerjal.json.JsonParser;
import com.nerjal.json.parser.FileParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
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
    private static final String autoReloadKey = "auto_reload";
    private static final String reloadSaveKey = "save_on_reload";
    private static final String logDisablingKey = "log_recipes_disabling";
    private static final String defaultConfigsFolder = "configs";
    private static final Set<String> disabledIds = new HashSet<>();
    private static final Set<String> keptIds = new HashSet<>();

    private boolean autoReload;
    private boolean reloadSave;
    private final HashSet<String> ids;
    private final HashSet<String> keepIds;
    private boolean recipesFolderOnly;
    private boolean log_disabling;
    private JsonObject json;
    private boolean updated = false;
    private final ReentrantLock lock = new ReentrantLock();

    private final Runnable[] updaters = {
            () -> {},
            () -> {
                try {
                    json.rename("disabled_ids", "disabled_recipes");
                    json.add("only_track_recipes_folder", new JsonBoolean(true));
                    json.add("keep_recipes", new JsonArray());
                } catch (ChildNotFoundException ignored) {}
            },
            () -> {
                json.add("auto_reload", new JsonBoolean(false));
                json.add("save_on_reload", new JsonBoolean(false));
                json.add("log_recipes_disabling", new JsonBoolean(false));
            }
    };

    @SuppressWarnings({"ConstantConditions", "OptionalGetWithoutIsPresent"})
    private ConfigLoader() {
        keptIds.clear();
        disabledIds.clear();
        ids = new HashSet<>();
        keepIds = new HashSet<>();
        JsonObject defaultValue = null;
        try {
            defaultValue = (JsonObject) FileParser.parseStream(new InputStreamReader(
                    ConfigLoader.class.getResourceAsStream(FabricLoader.getInstance().getModContainer(
                            RecipeDisabler.MOD_ID).get().findPath(RecipeDisabler.configFile).get().toString()
                    )));
            json = loadOrCreateFile(configFilePath,defaultValue);
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
            while (json.getNumber(configVersionKey).intValue() < RecipeDisabler.configVersion) {
                updated = true;
                update(json.getNumber(configVersionKey).intValue());
            }
        } catch (Exception e) {
            RecipeDisabler.LOGGER.warn(
                    "An error occurred trying to update RecipeDisabler config. Loading default template", e);
            return;
        }
        try {
            json.get(disabledListKey).getAsJsonArray().forEach(elem -> {
                try {
                    ids.add(elem.getAsString().toLowerCase(Locale.ENGLISH));
                } catch (JsonElementTypeException ignored) {}
            });
            json.get(dontDisableListKey).getAsJsonArray().forEach(elem -> {
                try {
                    keepIds.add(elem.getAsString().toLowerCase(Locale.ENGLISH));
                } catch (JsonElementTypeException ignored) {}
            });
            recipesFolderOnly = json.getBoolean(forceRecipesInPathKey);
            autoReload = json.contains(autoReloadKey) ?
                    json.getBoolean(autoReloadKey) : defaultValue.getBoolean(autoReloadKey);
            reloadSave = json.contains(reloadSaveKey) ?
                    json.getBoolean(reloadSaveKey) : defaultValue.getBoolean(reloadSaveKey);
            log_disabling = json.contains(logDisablingKey) ?
                    json.getBoolean(logDisablingKey) : defaultValue.getBoolean(logDisablingKey);
        } catch (JsonElementTypeException | ChildNotFoundException e) {
            RecipeDisabler.LOGGER.warn("Error while parsing the config file. Loading default template.",e);
        }
    }

    public static JsonObject loadOrCreateFile(File f, JsonObject defaultValue) {
        try {
            if (f.exists() &! f.isDirectory() && f.canRead()) {
                return (JsonObject) JsonParser.parseFile(f);
            }
            throw new IOException();
        } catch (JsonParseException | IOException e) {
            RecipeDisabler.LOGGER.info(String.format("No file %s found, creating a new one", f.getPath()));
            try {
                if ((!f.getParentFile().mkdirs()) &! f.createNewFile())
                    RecipeDisabler.LOGGER.warn(
                        String.format("Potential error while attempting to create file %s", f.getPath()));
                FileWriter writer = new FileWriter(f);
                String s = JsonParser.stringify(defaultValue);
                writer.write(s, 0, s.length());
                writer.flush();
                writer.close();
            } catch (IOException ex) {
                RecipeDisabler.LOGGER.error(
                        String.format("Couldn't create file %s, please check edition perms", f.getPath()));
                RecipeDisabler.LOGGER.error(ex);
            } catch (RecursiveJsonElementException ignored) {}
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

    void reload() {
        if (autoReload &! reloadSave) {
            INSTANCE = new ConfigLoader();
        }
    }

    public boolean checkId(Identifier id) {
        if (!id.getPath().endsWith(".json")) return false;
        if (recipesFolderOnly &! id.getPath().startsWith("recipes")) return false;
        for (String s : keepIds) {
            String[] ss = s.split(":");
            String s1 = ss[0];
            String s2 = ss[1];
            if (!Pattern.matches(s1, id.getNamespace())) continue;
            String path = id.getPath().substring(0, id.getPath().length() - 5);
            String subPath = path.startsWith("recipes/") ? path.substring(8) : path;
            if (Pattern.matches(s2, path) || Pattern.matches(s2, subPath)) {
                String s3 = id.getPath();
                keptIds.add(id.getNamespace()+":"+(s3.startsWith("recipes/")?s3.substring(8):s3));
                return false;
            }
        }
        for (String s : ids) {
            String[] ss = s.split(":");
            String s1 = ss[0];
            String s2 = ss[1];
            if (Pattern.matches(s1, id.getNamespace())) {
                String path = id.getPath().substring(0, id.getPath().length() - 5);
                String subPath = path.startsWith("recipes/") ? path.substring(8) : path;
                if (Pattern.matches(s2, path) || Pattern.matches(s2, subPath)) {
                    String s3 = id.getPath();
                    disabledIds.add(id.getNamespace()+":"+(s3.startsWith("recipes/")?s3.substring(8):s3));
                    return true;
                }
            }
        }
        return false;
    }

    List<String> disabled() {
        return new ArrayList<>(ids);
    }

    List<String> disabledResources() {
        return new ArrayList<>(disabledIds);
    }

    List<String> kept() {
        return new ArrayList<>(keepIds);
    }

    List<String> keptResources() {
        return new ArrayList<>(keptIds);
    }

    public boolean spamConsole() {
        return log_disabling;
    }

    public boolean autoReload() {
        return autoReload &! reloadSave;
    }

    public boolean reloadSave() {
        return reloadSave;
    }

    public boolean disable(String target) {
        String[] s = Arrays.copyOf(target.split(":"),2);
        if (s[1] == null) return false;
        updated = true;
        boolean b = ids.add(target);
        if (b)
            try {
                json.getArray(disabledListKey).add(new JsonString(s[0]+":"+s[1]));
            } catch (ChildNotFoundException | JsonElementTypeException ignored) {}
        return b;
    }

    public boolean enable(String target) {
        String[] s = Arrays.copyOf(target.split(":"),2);
        if (s[1] == null) return false;
        updated = true;
        boolean b = ids.remove(target);
        if (b)
            try {
                JsonArray array = json.getArray(disabledListKey);
                Iterator<JsonElement> iter = array.iterator();
                while (iter.hasNext()) {
                    JsonElement elem = iter.next();
                    try {
                        if (elem.getAsString().equals(target)) iter.remove();
                    } catch (JsonElementTypeException ignored) {}
                }
            } catch (JsonElementTypeException | ChildNotFoundException ignored) {}
        return b;
    }

    public boolean keep(String target) {
        String[] s = Arrays.copyOf(target.split(":"),2);
        if (s[1] == null) return false;
        updated = true;
        boolean b = keepIds.add(target);
        if (b)
            try {
                json.getArray(dontDisableListKey).add(new JsonString(s[0]+":"+s[1]));
            } catch (ChildNotFoundException | JsonElementTypeException ignored) {}
        return b;
    }

    public boolean unkeep(String target) {
        String[] s = Arrays.copyOf(target.split(":"),2);
        if (s[1] == null) return false;
        updated = true;
        boolean b = keepIds.remove(target);
        if (b)
            try {
                JsonArray array = json.getArray(dontDisableListKey);
                Iterator<JsonElement> iter = array.iterator();
                while (iter.hasNext()) {
                    JsonElement elem = iter.next();
                    try {
                        if (elem.getAsString().equals(target)) iter.remove();
                    } catch (JsonElementTypeException ignored) {
                    }
                }
            } catch (JsonElementTypeException | ChildNotFoundException ignored) {}
        return b;
    }

    Success save(MinecraftServer server) {
        if (!server.isStopping() &! RecipeDisabler.isReloading()) return Success.HALT;
        return save(new File(configFilePath));
    }

    Success save(MinecraftClient client) {
        if (client.isRunning()) return Success.HALT;
        return save(new File(configFilePath));
    }

    Success save() {
        if (lock.isLocked())
            return Success.FAIL;
        return save(new File(configFilePath));
    }

    private Success save(File f) {
        if (!updated) return Success.HALT;
        lock.lock();
        try {
            String s = JsonParser.stringify(json);
            FileWriter writer = new FileWriter(f);
            writer.write(s, 0, s.length());
            writer.flush();
            writer.close();
            return Success.SUCCESS;
        } catch (Exception e) {
            return Success.FAIL;
        } finally {
            lock.unlock();
            updated = false;
        }
    }

    @SuppressWarnings({"ConstantConditions", "OptionalGetWithoutIsPresent"})
    private void update(int v) throws JsonParseException {
        JsonObject object = (JsonObject) FileParser.parseStream(new InputStreamReader(
                ConfigLoader.class.getResourceAsStream(FabricLoader.getInstance().getModContainer(
                        RecipeDisabler.MOD_ID).get().findPath(
                                String.format("%s/v%d.json5", defaultConfigsFolder, v+1)).get().toString()
                )));
        json.set(configVersionKey, new JsonNumber(v+1));
        updaters[v].run();
        object.push(json);
        json = object;
    }
}
