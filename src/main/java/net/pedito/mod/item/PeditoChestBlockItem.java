package net.pedito.mod.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

public class PeditoChestBlockItem extends BlockItem {
    public PeditoChestBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag type) {
        super.appendHoverText(stack, context, display, tooltip, type);
        
        net.minecraft.world.item.component.CustomData customData = stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
        if (customData != null) {
            CompoundTag wrapper = customData.copyTag();
            if (wrapper.contains("Entities", 9)) {
                ListTag list = wrapper.getList("Entities", 10);
                int count = list.size();
                if (count > 0) {
                    tooltip.accept(Component.translatable("tooltip.pedito.chest_count", count).withStyle(ChatFormatting.GOLD));
                }
            }
        }
        
        if (customData == null) {
            tooltip.accept(Component.translatable("tooltip.pedito.chest_empty").withStyle(ChatFormatting.GRAY));
        }
    }
}
