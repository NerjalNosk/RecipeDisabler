package com.nerjal.recipedisable.compat;

import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.Recipe;
import net.minecraft.server.MinecraftServer;
import ru.bclib.recipes.BCLRecipeManager;

import java.util.Objects;

public final class BclibRecipe implements CompatRecipe {
    private final RecipeType<?> type;
    private final Recipe<?> recipe;

    public BclibRecipe(RecipeType<?> type, Recipe<?> recipe) {
        this.type = type;
        this.recipe = recipe;
    }

    @Override
    public void add(MinecraftServer server) {
        BCLRecipeManager.addRecipe(type, recipe);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BclibRecipe that = (BclibRecipe) o;

        if (!Objects.equals(type, that.type)) return false;
        return Objects.equals(recipe, that.recipe);
    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (recipe != null ? recipe.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "DisabledRecipe{" +
                "type=" + type +
                ", recipe=" + recipe +
                '}';
    }
}
