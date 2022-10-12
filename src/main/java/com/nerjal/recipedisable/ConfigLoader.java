package com.nerjal.recipedisable;

import com.nerjal.json.elements.*;
import com.nerjal.json.JsonParser;
import com.nerjal.json.parser.FileParser;
import com.nerjal.recipedisable.compat.CompatRecipe;
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
    private static ConfigLoader instance = null;
    public static final String CONFIG_FILE_PATH = String.format(
            "%s/%s", RecipeDisabler.CONFIG_FOLDER, RecipeDisabler.CONFIG_FILE);
    private static final String CONFIG_VERSION_KEY = "config_version";
    private static final String DISABLED_LIST_KEY = "disabled_recipes";
    private static final String FORCE_RECIPES_IN_PATH_KEY = "only_track_recipes_folder";
    private static final String DONT_DISABLE_LIST_KEY = "keep_recipes";
    private static final String AUTO_RELOAD_KEY = "auto_reload";
    private static final String RELOAD_SAVE_KEY = "save_on_reload";
    private static final String LOG_DISABLING_KEY = "log_recipes_disabling";
    private static final String DEBUG_OBJECT_KEY = "debug";
    private static final String DEFAULT_CONFIGS_FOLDER = "configs";
    private static final String VANILLA_RECIPES_DATA_FOLDER = "recipes/";
    private static final Set<String> DISABLED_IDS = new HashSet<>();
    private static final Set<String> KEPT_IDS = new HashSet<>();
    private static final Set<CompatRecipe> COMPAT_RECIPES = new HashSet<>();

    private boolean autoReload;
    private boolean reloadSave;
    private final HashSet<String> ids;
    private final HashSet<String> keepIds;
    private boolean recipesFolderOnly;
    private boolean logDisabling;
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
                } catch (ChildNotFoundException ignored) {
                    // Will not happen (in theory)
                }
            },
            () -> {
                json.add("auto_reload", new JsonBoolean(false));
                json.add("save_on_reload", new JsonBoolean(false));
                json.add("log_recipes_disabling", new JsonBoolean(false));
            }
    };

    @SuppressWarnings({"ConstantConditions"})
    private ConfigLoader() {
        KEPT_IDS.clear();
        DISABLED_IDS.clear();
        ids = new HashSet<>();
        keepIds = new HashSet<>();
        JsonObject defaultValue = null;
        try {
            defaultValue = (JsonObject) FileParser.parseStream(new InputStreamReader(
                    ConfigLoader.class.getResourceAsStream(FabricLoader.getInstance().getModContainer(
                            RecipeDisabler.MOD_ID).get().findPath(RecipeDisabler.CONFIG_FILE).get().toString()
                    )));
            json = loadOrCreateFile(CONFIG_FILE_PATH,defaultValue);
        } catch (JsonParseException|NoSuchElementException e) {
            RecipeDisabler.LOGGER.warn("Something happened where it wasn't supposed to",e);
            // Should not happen
        }
        try {
            if (json.get(CONFIG_VERSION_KEY).getAsInt() > RecipeDisabler.CONFIG_VERSION) {
                RecipeDisabler.LOGGER.warn(String.format(
                        "RecipeDisabler config version higher than the mod support (%d > %d). "+
                        "Please update the mod or edit the config",
                        json.get(CONFIG_VERSION_KEY).getAsInt(),
                        RecipeDisabler.CONFIG_VERSION
                ));
                return;
            }
            while (json.getNumber(CONFIG_VERSION_KEY).intValue() < RecipeDisabler.CONFIG_VERSION) {
                updated = true;
                update(json.getNumber(CONFIG_VERSION_KEY).intValue());
            }
        } catch (Exception e) {
            RecipeDisabler.LOGGER.warn(
                    "An error occurred trying to update RecipeDisabler config. Loading default template", e);
            return;
        }
        try {
            json.get(DISABLED_LIST_KEY).getAsJsonArray().forEach(elem -> {
                try {
                    ids.add(elem.getAsString().toLowerCase(Locale.ENGLISH));
                } catch (JsonElementTypeException ignored) {
                    // We don't care
                }
            });
            json.get(DONT_DISABLE_LIST_KEY).getAsJsonArray().forEach(elem -> {
                try {
                    keepIds.add(elem.getAsString().toLowerCase(Locale.ENGLISH));
                } catch (JsonElementTypeException ignored) {
                    // We don't care either
                }
            });
            recipesFolderOnly = json.getBoolean(FORCE_RECIPES_IN_PATH_KEY);
            autoReload = json.contains(AUTO_RELOAD_KEY) ?
                    json.getBoolean(AUTO_RELOAD_KEY) : defaultValue.getBoolean(AUTO_RELOAD_KEY);
            reloadSave = json.contains(RELOAD_SAVE_KEY) ?
                    json.getBoolean(RELOAD_SAVE_KEY) : defaultValue.getBoolean(RELOAD_SAVE_KEY);
            logDisabling = json.contains(LOG_DISABLING_KEY) ?
                    json.getBoolean(LOG_DISABLING_KEY) : defaultValue.getBoolean(LOG_DISABLING_KEY);
        } catch (JsonElementTypeException | ChildNotFoundException e) {
            RecipeDisabler.LOGGER.warn("Error while parsing the config file. Loading default template.",e);
        }
    }

    public static JsonObject loadOrCreateFile(File f, JsonObject defaultValue) {
        try {
            if (f.exists() && (!f.isDirectory()) && f.canRead()) {
                return (JsonObject) JsonParser.parseFile(f);
            }
            throw new IOException();
        } catch (JsonParseException | IOException e) {
            RecipeDisabler.LOGGER.info(String.format("No file %s found, creating a new one", f.getPath()));
            try {
                if ((!f.getParentFile().mkdirs()) && !f.createNewFile())
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
            } catch (RecursiveJsonElementException ignored) {
                // Will not happen
            }
        }
        return defaultValue;
    }

    public static JsonObject loadOrCreateFile(String path, JsonObject defaultValue) {
        return loadOrCreateFile(new File(path), defaultValue);
    }

    public static ConfigLoader getConfig() {
        init();
        return instance;
    }

    public static void init() {
        if (instance == null) {
            instance = new ConfigLoader();
        }
    }

    void reload() {
        if (autoReload && !reloadSave) {
            instance = new ConfigLoader();
        }
    }

    public boolean checkId(Identifier id) {
        return checkId(id, false);
    }

    public boolean checkId(Identifier id, boolean compat) {
        if (!compat) {
            if (!id.getPath().endsWith(".json")) {
                return false;
            }
            if (recipesFolderOnly && !id.getPath().startsWith("recipes")) {
                return false;
            }
        }
        if (!checkKeeps(id)) {
            return false;
        }
        return checkDisabled(id);
    }

    private boolean checkKeeps(Identifier id) {
        for (String s : keepIds) {
            String r = idCheck(id, s);
            if (r != null) {
                KEPT_IDS.add(r);
                return false;
            }
        }
        return true;
    }

    private boolean checkDisabled(Identifier id) {
        for (String s : ids) {
            String r = idCheck(id, s);
            if (r != null) {
                DISABLED_IDS.add(r);
                return true;
            }
        }
        return false;
    }

    private static String idCheck(Identifier id, String s) {
        String[] ss = s.split(":");
        String s1 = ss[0];
        String s2 = ss[1];
        if (Pattern.matches(s1, id.getNamespace())) {
            String path = id.getPath();
            if (path.endsWith(".json")) {
                path = path.substring(0, path.length() - 5);
            }
            boolean sub = false;
            String subPath = path;
            if (path.startsWith(VANILLA_RECIPES_DATA_FOLDER)) {
                sub = true;
                subPath = path.substring(VANILLA_RECIPES_DATA_FOLDER.length());
            }
            if (Pattern.matches(s2, path) || (sub && Pattern.matches(s2, subPath))) {
                return id.getNamespace() + ":" + subPath;
            }
        }
        return null;
    }

    List<String> disabled() {
        return new ArrayList<>(ids);
    }

    List<String> disabledResources() {
        return new ArrayList<>(DISABLED_IDS);
    }

    List<String> kept() {
        return new ArrayList<>(keepIds);
    }

    List<String> keptResources() {
        return new ArrayList<>(KEPT_IDS);
    }

    public boolean spamConsole() {
        return logDisabling;
    }

    public boolean autoReload() {
        return autoReload && !reloadSave;
    }

    public boolean reloadSave() {
        return reloadSave;
    }

    public JsonObject debug() {
        JsonObject j = new JsonObject();
        try {
            j.recursivePushAll(json.getObject(DEBUG_OBJECT_KEY));
        } catch (ChildNotFoundException | JsonElementTypeException e) {
            return j;
        }
        return j;
    }

    public static String dKey() {
        return DEBUG_OBJECT_KEY;
    }

    public static void addCompatRecipe(CompatRecipe recipe) {
        COMPAT_RECIPES.add(recipe);
    }

    public static void reloadCompat(MinecraftServer server) {
        COMPAT_RECIPES.forEach(recipe -> recipe.add(server));
    }

    public boolean disable(String target) {
        String[] s = Arrays.copyOf(target.split(":"),2);
        if (s[1] == null) return false;
        updated = true;
        boolean b = ids.add(target);
        if (b)
            try {
                json.getArray(DISABLED_LIST_KEY).add(new JsonString(s[0]+":"+s[1]));
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
                JsonArray array = json.getArray(DISABLED_LIST_KEY);
                Iterator<JsonElement> iter = array.iterator();
                while (iter.hasNext()) {
                    JsonElement elem = iter.next();
                    try {
                        if (elem.getAsString().equals(target)) iter.remove();
                    } catch (JsonElementTypeException ignored) {
                        // I don't care
                    }
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
                json.getArray(DONT_DISABLE_LIST_KEY).add(new JsonString(s[0]+":"+s[1]));
            } catch (ChildNotFoundException | JsonElementTypeException ignored) {
                // I don't care
            }
        return b;
    }

    public boolean unkeep(String target) {
        String[] s = Arrays.copyOf(target.split(":"),2);
        if (s[1] == null) return false;
        updated = true;
        boolean b = keepIds.remove(target);
        if (b)
            try {
                JsonArray array = json.getArray(DONT_DISABLE_LIST_KEY);
                Iterator<JsonElement> iter = array.iterator();
                while (iter.hasNext()) {
                    JsonElement elem = iter.next();
                    try {
                        if (elem.getAsString().equals(target)) iter.remove();
                    } catch (JsonElementTypeException ignored) {
                    }
                }
            } catch (JsonElementTypeException | ChildNotFoundException ignored) {
                // I don't care
            }
        return b;
    }

    Success save(MinecraftServer server) {
        if (!server.isStopping() && !RecipeDisabler.isReloading()) return Success.HALT;
        return save(new File(CONFIG_FILE_PATH));
    }

    Success save(MinecraftClient client) {
        if (client.isRunning()) return Success.HALT;
        return save(new File(CONFIG_FILE_PATH));
    }

    Success save() {
        if (lock.isLocked())
            return Success.FAIL;
        return save(new File(CONFIG_FILE_PATH));
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
                                String.format("%s/v%d.json5", DEFAULT_CONFIGS_FOLDER, v+1)).get().toString()
                )));
        json.set(CONFIG_VERSION_KEY, new JsonNumber(v+1));
        updaters[v].run();
        object.push(json);
        json = object;
    }
}
