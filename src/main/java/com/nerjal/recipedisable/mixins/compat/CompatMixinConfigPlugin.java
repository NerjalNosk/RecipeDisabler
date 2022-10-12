package com.nerjal.recipedisable.mixins.compat;

import com.nerjal.json.JsonParser;
import com.nerjal.json.elements.JsonArray;
import com.nerjal.json.elements.JsonObject;
import com.nerjal.json.elements.JsonString;
import com.nerjal.recipedisable.ConfigLoader;
import com.nerjal.recipedisable.RecipeDisabler;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.File;
import java.util.*;

import static com.nerjal.json.JsonError.*;

public class CompatMixinConfigPlugin implements IMixinConfigPlugin {
    private static final String PACKAGE_ROOT;
    private static final FabricLoader LOADER = FabricLoader.getInstance();
    private static final Set<String> loadedCompatibilities = new HashSet<>();
    private static final Set<String> ignoredCompatibilities;
    private static final List<String> output = new ArrayList<>();

    static {
        String[] path = CompatMixinConfigPlugin.class.getPackageName().split("\\.");
        PACKAGE_ROOT = String.join(".", Arrays.copyOfRange(path, 0, path.length-2))+'.';
        ignoredCompatibilities = new HashSet<>();
        JsonObject alt = loadAltConfig();
        String debugKey = ConfigLoader.dKey();
        try {
            if (alt != null && alt.contains(debugKey) && alt.get(debugKey).isJsonObject()) {
                JsonObject debug = alt.getObject(debugKey);
                if (debug.contains("compat")) {
                    JsonObject debugCompat = debug.getObject("compat");
                    if (debugCompat.contains("ignore")) {
                        JsonArray debugCompatIgnore = debugCompat.getArray("ignore");
                        debugCompatIgnore.forEach(e -> {
                            if (e instanceof JsonString ignored) {
                                ignoredCompatibilities.add(ignored.getAsString());
                            }
                        });
                    }
                }
            }
        } catch (ChildNotFoundException | JsonElementTypeException e) {
            // I don't care
        }
        output.add(String.format("[%s] IgnoredCompatibilities: %s", RecipeDisabler.MOD_ID, ignoredCompatibilities));
    }

    private static JsonObject loadAltConfig() {
        JsonObject out = null;
        try {
            File f = new File(RecipeDisabler.CONFIG_FOLDER + File.separator + RecipeDisabler.CONFIG_FILE);
            if (f.exists() && (!f.isDirectory()) && f.canRead()) {
                out = (JsonObject) JsonParser.parseFile(f);
            }
        } catch (Exception e) {
            // I don't care
        }
        return out;
    }

    @Override
    public void onLoad(String mixinPackage) {
        // nothing to see here
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!mixinClassName.startsWith(PACKAGE_ROOT)) {
            return true;
        }
        String[] path = mixinClassName.split("\\.");
        String modId = path[path.length-2];
        String name = path[path.length-1];
        if ((!LOADER.isModLoaded(modId)) || ignoredCompatibilities.contains(name)) {
            return false;
        }
        markLoaded(modId);
        return true;
    }

    private static void markLoaded(String s) {
        if (loadedCompatibilities.add(s)) {
            output.add(String.format("[%s] Loaded RecipeDisabler compatibility with mod %s", RecipeDisabler.MOD_ID, s));
        }
    }

    public static List<String> log() {
        return new ArrayList<>(output);
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // nothing to see here
    }

    @Override
    public List<String> getMixins() {
        // nothing to see here
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // nothing to see here
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // nothing to see here
    }
}
