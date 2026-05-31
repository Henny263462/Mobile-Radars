package dev.henny.mobile_radars.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/** Binding to the filter / network trunk block in Create: Radars. */
public final class NetworkFilterBinding {

    public static final String ROOT_COMPOUND = "mobile_radars_filter";
    private static final String KEY_DIM = "Dim";
    private static final String KEY_POS = "Pos";

    private NetworkFilterBinding() {
    }

    public record Bound(ResourceKey<Level> dimension, BlockPos filtererPos) {
    }

    public static boolean isBound(ItemStack stack) {
        return read(stack) != null;
    }

    public static void bind(ItemStack stack, ResourceKey<Level> dimension, BlockPos filtererPos) {
        CompoundTag merged = unwrapCustomData(stack);
        CompoundTag inner = new CompoundTag();
        inner.putString(KEY_DIM, dimension.location().toString());
        inner.put(KEY_POS, NbtUtils.writeBlockPos(filtererPos));
        merged.put(ROOT_COMPOUND, inner);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(merged));
    }

    public static void clear(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null || cd.isEmpty()) {
            return;
        }
        CompoundTag merged = cd.copyTag();
        merged.remove(ROOT_COMPOUND);
        if (merged.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(merged));
        }
    }

    public static Bound read(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null || cd.isEmpty()) {
            return null;
        }
        CompoundTag merged = cd.copyTag();
        if (!merged.contains(ROOT_COMPOUND)) {
            return null;
        }
        CompoundTag inner = merged.getCompound(ROOT_COMPOUND);
        if (!inner.contains(KEY_DIM) || !inner.contains(KEY_POS)) {
            return null;
        }
        ResourceLocation dimLoc = ResourceLocation.parse(inner.getString(KEY_DIM));
        BlockPos fp = NbtUtils.readBlockPos(inner, KEY_POS).orElse(null);
        if (fp == null) {
            return null;
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dimLoc);
        return new Bound(key, fp);
    }

    private static CompoundTag unwrapCustomData(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null || cd.isEmpty()) {
            return new CompoundTag();
        }
        return cd.copyTag();
    }
}
