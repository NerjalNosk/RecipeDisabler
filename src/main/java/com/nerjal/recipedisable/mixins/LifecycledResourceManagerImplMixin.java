package com.nerjal.recipedisable.mixins;

import com.nerjal.recipedisable.ConfigLoader;
import com.nerjal.recipedisable.RecipeDisabler;
import net.minecraft.resource.LifecycledResourceManagerImpl;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

@Mixin(LifecycledResourceManagerImpl.class)
public abstract class LifecycledResourceManagerImplMixin implements ResourceManager {

    @Inject(method = "findResources", at = @At("RETURN"), locals = LocalCapture.CAPTURE_FAILEXCEPTION)
    private void injectFindResource(String startingPath, Predicate<String> pathPredicate,
                                    CallbackInfoReturnable<Collection<Identifier>> cir, Set<Identifier> set,
                                    List<Identifier> res) {
        List<Identifier> copy = List.copyOf(res);
        for (Identifier id : copy) {
            if (ConfigLoader.getConfig().checkId(id)) {
                if (ConfigLoader.getConfig().spamConsole()) {
                    String s = String.format("[%s] Recipe %s disabled", RecipeDisabler.MOD_ID, id);
                    RecipeDisabler.LOGGER.info(s);
                }
                res.remove(id);
                set.remove(id);
            }
        }
    }
}
