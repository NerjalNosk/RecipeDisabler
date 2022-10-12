package com.nerjal.recipedisable.mixins.compat.bclib;

import com.nerjal.recipedisable.ConfigLoader;
import com.nerjal.recipedisable.RecipeDisabler;
import com.nerjal.recipedisable.compat.BclibRecipe;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeType;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.bclib.recipes.BCLRecipeManager;

@Mixin(BCLRecipeManager.class)
public abstract class BCLRecipeManagerMixin {

    @Inject(method = "addRecipe", at = @At("HEAD"), cancellable = true, remap = false)
    private static void bclRecipeManagerAddRecipeInjector(RecipeType<?> type, Recipe<?> recipe, CallbackInfo ci) {
        Identifier id = recipe.getId();
        if (ConfigLoader.getConfig().checkId(id, true)) {
            String s = String.format("Disabling recipe %s", id);
            RecipeDisabler.LOGGER.info(s);
            if (ConfigLoader.getConfig().spamConsole()) {
                RecipeDisabler.LOGGER.info(String.format("Recipe %s disabled",id));
            }
            ci.cancel();
        }
        BclibRecipe r = new BclibRecipe(type, recipe);
        ConfigLoader.addCompatRecipe(r);
    }
}
