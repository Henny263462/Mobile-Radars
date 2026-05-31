package dev.henny.mobile_radars.util;

import dev.henny.mobile_radars.item.RadarGogglesItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import javax.annotation.Nullable;

/** Radar-Brille in der Hand; Optik nur im ausgeklappten Zustand. */
public final class GogglesState {

    private static final String KEY_DEPLOYED = "goggles_deployed";

    private GogglesState() {
    }

    public record Held(ItemStack stack, InteractionHand hand) {}

    public static boolean isDeployed(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null || cd.isEmpty()) {
            return false;
        }
        return cd.copyTag().getBoolean(KEY_DEPLOYED);
    }

    public static void setDeployed(ItemStack stack, boolean deployed) {
        CompoundTag merged = customDataCopy(stack);
        if (deployed) {
            merged.putBoolean(KEY_DEPLOYED, true);
        } else {
            merged.remove(KEY_DEPLOYED);
        }
        writeCustomData(stack, merged);
    }

    @Nullable
    public static Held findHeld(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof RadarGogglesItem) {
                return new Held(stack, hand);
            }
        }
        return null;
    }

    @Nullable
    public static NetworkFilterBinding.Bound heldBinding(Player player) {
        Held held = findHeld(player);
        return held == null ? null : NetworkFilterBinding.read(held.stack());
    }

    public static boolean isOpticsActive(Player player) {
        Held held = findHeld(player);
        return held != null && isDeployed(held.stack());
    }

    public static int packetHandOrdinal(InteractionHand hand) {
        return hand == InteractionHand.OFF_HAND ? 1 : 0;
    }

    private static CompoundTag customDataCopy(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null || cd.isEmpty()) {
            return new CompoundTag();
        }
        return cd.copyTag();
    }

    private static void writeCustomData(ItemStack stack, CompoundTag merged) {
        if (merged.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(merged));
        }
    }
}
