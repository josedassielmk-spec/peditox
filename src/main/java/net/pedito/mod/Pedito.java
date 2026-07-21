package net.pedito.mod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.pedito.mod.entity.PeditoEntity;
import net.pedito.mod.registry.ModItems;
import net.pedito.mod.registry.ModSounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Pedito implements ModInitializer {

	public static final String MOD_ID = "pedito";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final EntityType<PeditoEntity> PEDITO_ENTITY = registerEntity();
	public static final EntityType<net.pedito.mod.entity.golem.PeditoGolemEntity> PEDITO_GOLEM_ENTITY = registerGolemEntity();

	private static EntityType<net.pedito.mod.entity.golem.PeditoGolemEntity> registerGolemEntity() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE,
				Identifier.fromNamespaceAndPath(MOD_ID, "pedito_golem"));
		
		EntityType.Builder<net.pedito.mod.entity.golem.PeditoGolemEntity> builder = FabricEntityType.Builder.createMob(
				net.pedito.mod.entity.golem.PeditoGolemEntity::new,
				MobCategory.CREATURE,
				mob -> mob)
				.sized(1.4f, 2.7f)
				.clientTrackingRange(10);

		EntityType<net.pedito.mod.entity.golem.PeditoGolemEntity> type = builder.build(key);
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}

	private static EntityType<PeditoEntity> registerEntity() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE,
				Identifier.fromNamespaceAndPath(MOD_ID, "pedito"));
		// El lambda de createMob configura el builder "Mob<T>" de Fabric (solo expone
		// spawnPlacement/defaultAttributes); no hay nada que tocar ahi porque los atributos
		// por defecto se registran aparte con FabricDefaultAttributeRegistry. El tamano y el
		// rango de seguimiento son propiedades del EntityType.Builder<T> normal, que es lo que
		// devuelve createMob(...), asi que se encadenan DESPUES, no dentro del lambda.
		EntityType.Builder<PeditoEntity> builder = FabricEntityType.Builder.createMob(
				PeditoEntity::new,
				MobCategory.CREATURE,
				mob -> mob)
				.sized(0.6f, 0.6f)
				.clientTrackingRange(10);
		EntityType<PeditoEntity> type = builder.build(key);
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}

	@Override
	public void onInitialize() {
		LOGGER.info("Inicializando Pedito...");

		ModSounds.register();
		ModItems.register();

		FabricDefaultAttributeRegistry.register(PEDITO_ENTITY, PeditoEntity.createPeditoAttributes());
		FabricDefaultAttributeRegistry.register(PEDITO_GOLEM_ENTITY, net.pedito.mod.entity.golem.PeditoGolemEntity.createAttributes());

		// Registrar las reglas de spawn (Placements)
		SpawnPlacements.register(
				PEDITO_ENTITY,
				SpawnPlacementTypes.ON_GROUND,
				Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				PeditoEntity::canSpawn
		);

		// Registrar en los biomas del Overworld (Día)
		BiomeModifications.addSpawn(
				BiomeSelectors.foundInOverworld(),
				MobCategory.CREATURE,
				PEDITO_ENTITY,
				12, // Peso (frecuencia similar a vacas y pollos)
				3,  // Mínimo de grupo
				10  // Máximo de grupo
		);
		// Registrar en los biomas del Overworld (Noche/Oscuridad)
		BiomeModifications.addSpawn(
				BiomeSelectors.foundInOverworld(),
				MobCategory.MONSTER,
				PEDITO_ENTITY,
				40, // Peso alto para competir con zombies (100) y esqueletos (100)
				1,  // Mínimo de grupo
				3   // Máximo de grupo
		);

		ModItems.registerItemGroupContents();
	}
}
