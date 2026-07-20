package net.pedito.mod.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.pedito.mod.Pedito;
import net.pedito.mod.block.PeditoChestBlock;

import java.util.function.Function;

public class ModBlocks {

    public static Block PEDITO_CHEST;
    public static net.minecraft.world.item.Item PEDITO_CHEST_ITEM;

    public static void register() {
        PEDITO_CHEST = registerBlock("pedito_chest", PeditoChestBlock::new, 
                BlockBehaviour.Properties.ofFullCopy(Blocks.CHEST).mapColor(net.minecraft.world.level.material.MapColor.COLOR_BROWN).strength(2.5f).sound(SoundType.WOOD));
        
        PEDITO_CHEST_ITEM = registerBlockItem("pedito_chest", PEDITO_CHEST, new net.minecraft.world.item.Item.Properties());
    }

    private static <T extends Block> T registerBlock(String name, Function<BlockBehaviour.Properties, T> blockFactory, BlockBehaviour.Properties properties) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Pedito.MOD_ID, name));
        T block = blockFactory.apply(properties.setId(key));
        return Registry.register(BuiltInRegistries.BLOCK, key, block);
    }

    private static net.minecraft.world.item.Item registerBlockItem(String name, Block block, net.minecraft.world.item.Item.Properties properties) {
        ResourceKey<net.minecraft.world.item.Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Pedito.MOD_ID, name));
        net.minecraft.world.item.Item item = new BlockItem(block, properties.setId(key));
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }
}
