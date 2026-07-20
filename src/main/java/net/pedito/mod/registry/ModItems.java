package net.pedito.mod.registry;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.pedito.mod.Pedito;
import net.pedito.mod.item.PeditoBottleItem;
import net.pedito.mod.item.PeditoBottleRainbowItem;
import net.pedito.mod.item.PeditoStaffItem;
import net.pedito.mod.item.PeditoWhistleItem;

import java.util.function.Function;

public class ModItems {
	public static Item GAS_CAN;
	public static Item GAS_CAN_COPPER;
	public static Item GAS_CAN_IRON;
	public static Item GAS_CAN_GOLD;
	public static Item GAS_CAN_DIAMOND;
	public static Item GAS_CAN_NETHERITE;
	
	public static Item PEDITO_BOTTLE;
	public static Item PEDITO_BOTTLE_RAINBOW;
	public static Item PEDITO_STAFF;
	public static Item PEDITO_WHISTLE;

	public static void register() {
		GAS_CAN = registerItem("gas_can", Item::new, new Item.Properties().stacksTo(16));
		GAS_CAN_COPPER = registerItem("gas_can_copper", Item::new, new Item.Properties().stacksTo(16));
		GAS_CAN_IRON = registerItem("gas_can_iron", Item::new, new Item.Properties().stacksTo(16));
		GAS_CAN_GOLD = registerItem("gas_can_gold", Item::new, new Item.Properties().stacksTo(16));
		GAS_CAN_DIAMOND = registerItem("gas_can_diamond", Item::new, new Item.Properties().stacksTo(16));
		GAS_CAN_NETHERITE = registerItem("gas_can_netherite", Item::new, new Item.Properties().stacksTo(16));

		PEDITO_BOTTLE = registerItem("pedito_bottle", PeditoBottleItem::new,
				new Item.Properties().stacksTo(16));
		PEDITO_BOTTLE_RAINBOW = registerItem("pedito_bottle_rainbow", PeditoBottleRainbowItem::new,
				new Item.Properties().stacksTo(16));
		PEDITO_STAFF = registerItem("pedito_staff", PeditoStaffItem::new,
				PeditoStaffItem.createProperties());
		PEDITO_WHISTLE = registerItem("silbato_pedito", PeditoWhistleItem::new,
				new Item.Properties().stacksTo(1));
	}

	public static void registerItemGroupContents() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> {
			output.accept(GAS_CAN);
			output.accept(GAS_CAN_COPPER);
			output.accept(GAS_CAN_IRON);
			output.accept(GAS_CAN_GOLD);
			output.accept(GAS_CAN_DIAMOND);
			output.accept(GAS_CAN_NETHERITE);
			output.accept(PEDITO_BOTTLE);
			output.accept(PEDITO_BOTTLE_RAINBOW);
			output.accept(PEDITO_STAFF);
			output.accept(PEDITO_WHISTLE);
		});
	}

	private static <T extends Item> T registerItem(String name, Function<Item.Properties, T> itemFactory,
			Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM,
				Identifier.fromNamespaceAndPath(Pedito.MOD_ID, name));
		T item = itemFactory.apply(properties.setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}
}
