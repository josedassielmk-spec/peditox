package net.pedito.mod.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.pedito.mod.Pedito;
import net.pedito.mod.block.entity.PeditoChestBlockEntity;

import java.util.Set;

public class ModBlockEntities {
    public static BlockEntityType<PeditoChestBlockEntity> PEDITO_CHEST_BE;

    public static void register() {
        PEDITO_CHEST_BE = Registry.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(Pedito.MOD_ID, "pedito_chest_be"),
                new BlockEntityType<>(PeditoChestBlockEntity::new, Set.of(ModBlocks.PEDITO_CHEST))
        );
    }
}
