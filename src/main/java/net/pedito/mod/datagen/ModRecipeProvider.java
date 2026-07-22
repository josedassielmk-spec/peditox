package net.pedito.mod.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.Items;
import net.pedito.mod.registry.ModItems;
import net.minecraft.core.registries.Registries;

import java.util.concurrent.CompletableFuture;

public class ModRecipeProvider extends FabricRecipeProvider {
    public ModRecipeProvider(FabricPackOutput pack, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(pack, registriesFuture);
    }

    @Override
    protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries, RecipeOutput exporter) {
        return new RecipeProvider(registries, exporter) {
            @Override
            public void buildRecipes() {
                var itemLookup = registries.lookupOrThrow(Registries.ITEM);
                ShapedRecipeBuilder.shaped(itemLookup, RecipeCategory.MISC, ModItems.GAS_CAN)
                    .pattern("III")
                    .pattern("ICI")
                    .pattern(" P ")
                    .define('I', Items.IRON_INGOT)
                    .define('C', Items.ROTTEN_FLESH)
                    .define('P', Items.GUNPOWDER)
                    .unlockedBy("has_iron", has(Items.IRON_INGOT))
                    .save(exporter);
            }
        };
    }
    
    @Override
    public String getName() {
        return "Recipes";
    }
}
