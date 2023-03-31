package com.bawnorton.mcchatgpt.util;

import net.minecraft.block.Block;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class Context {
    private final String contextString;

    private Context(String contextString) {
        this.contextString = contextString;
    }

    public String get() {
        return contextString;
    }

    public static Builder builder() {
        return new Builder();
    }

    @SuppressWarnings("UnusedReturnValue")
    public static class Builder {
        private final StringBuilder context = new StringBuilder("Player Context:\n");

        private Iterable<ItemStack> filterAir(Iterable<ItemStack> inventory) {
            List<ItemStack> filtered = new ArrayList<>();
            for (ItemStack itemStack : inventory) {
                if (!itemStack.isEmpty()) {
                    filtered.add(itemStack);
                }
            }
            return filtered;
        }

        public Builder addInventory(String name, Iterable<ItemStack> inventory) {
            context.append(name);
            context.append("Inventory (Item [Count]): ");
            for (ItemStack itemStack : filterAir(inventory)) {
                if (itemStack.isEmpty()) continue;
                context.append(itemStack.getName().getString())
                        .append(" [")
                        .append(itemStack.getCount())
                        .append("], ");
            }
            context.delete(context.length() - 2, context.length());
            context.append("\n");
            return this;
        }

        public Builder addMainHand(ItemStack itemStack) {
            context.append("Main Hand: ");
            if (itemStack.isEmpty()) {
                context.append("Empty");
            } else {
                context.append(itemStack.getName().getString())
                        .append(" [")
                        .append(itemStack.getCount())
                        .append("]");
            }
            context.append("\n");
            return this;
        }

        public Builder addOffHand(ItemStack itemStack) {
            context.append("Off Hand: ");
            if (itemStack.isEmpty()) {
                context.append("Empty");
            } else {
                context.append(itemStack.getName().getString())
                        .append(" [")
                        .append(itemStack.getCount())
                        .append("]");
            }
            context.append("\n");
            return this;
        }

        public Builder addArmor(Iterable<ItemStack> armor) {
            context.append("Armor (Item [Count]): ");
            for (ItemStack itemStack : armor) {
                if (itemStack.isEmpty()) continue;
                context.append(itemStack.getName().getString())
                        .append(" [")
                        .append(itemStack.getCount())
                        .append("], ");
            }
            context.delete(context.length() - 2, context.length());
            context.append("\n");
            return this;
        }

        public Builder addPlayerPosition(BlockPos blockPos) {
            context.append("Player Position: x = ")
                    .append(blockPos.getX())
                    .append(", y=")
                    .append(blockPos.getY())
                    .append(", z=")
                    .append(blockPos.getZ())
                    .append("\n");
            return this;
        }

        public Builder addHotbar(Iterable<ItemStack> hotbar) {
            context.append("Hotbar (Item [Count]): ");
            for (ItemStack itemStack : hotbar) {
                if (itemStack.isEmpty()) continue;
                context.append(itemStack.getName().getString())
                        .append(" [")
                        .append(itemStack.getCount())
                        .append("], ");
            }
            context.delete(context.length() - 2, context.length());
            context.append("\n");
            return this;
        }

        public Builder addEntities(List<LivingEntity> nearbyEntities) {
            context.append("Nearby Entities (Type [Health, Position]): ");
            for (LivingEntity entity : nearbyEntities) {
                context.append(entity.getType().getName().getString())
                        .append(" [")
                        .append(entity.getHealth())
                        .append(", (x=")
                        .append(entity.getBlockPos().getX())
                        .append(", y=")
                        .append(entity.getBlockPos().getY())
                        .append(", z=")
                        .append(entity.getBlockPos().getZ())
                        .append(")], ");
            }
            context.delete(context.length() - 2, context.length());
            context.append("\n");
            return this;
        }

        public Builder addEntityTarget(@Nullable LivingEntity targetEntity) {
            if (targetEntity == null) {
                context.append("Looking At Entity: None\n");
                return this;
            }
            context.append("Looking At Entity (Type [Health, Position, Holding]): ")
                    .append(targetEntity.getType().getName().getString())
                    .append(" [")
                    .append(targetEntity.getHealth())
                    .append(", (x=")
                    .append(targetEntity.getBlockPos().getX())
                    .append(", y=")
                    .append(targetEntity.getBlockPos().getY())
                    .append(", z=")
                    .append(targetEntity.getBlockPos().getZ())
                    .append("),")
                    .append(targetEntity.getMainHandStack().getName().getString())
                    .append("]\n");
            return this;
        }

        public Builder addBiome(String biome) {
            context.append("Biome: ")
                    .append(biome)
                    .append("\n");
            return this;
        }

        public Builder addDimension(String dimension) {
            context.append("Dimension: ")
                    .append(dimension)
                    .append("\n");
            return this;
        }

        public Builder addBlockTarget(@Nullable Block block) {
            if (block == null) {
                context.append("Looking At Block: None\n");
                return this;
            }
            context.append("Looking At Block: ")
                    .append(block.getName().getString())
                    .append("\n");
            return this;
        }

        public Context build(int level) {
            context.insert(context.indexOf("\n") + 1, "Context Provided: " + Text.translatable("mcchatgpt.context.level." + level).getString() + "\n");
            return new Context(context.toString());
        }
    }
}
