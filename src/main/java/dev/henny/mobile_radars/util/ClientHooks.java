package dev.henny.mobile_radars.util;

import dev.henny.mobile_radars.network.PortableHudSnapshot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Client-only entry points invoked from common code (reflection keeps dedicated-server classpath safe).
 */
public final class ClientHooks {

    private static final String HUD = "dev.henny.mobile_radars.client.MobilePortableHud";
    private static final String VISION = "dev.henny.mobile_radars.client.RadarVisionClient";
    private static final String OPENER = "dev.henny.mobile_radars.client.MobilePortableOpener";

    private ClientHooks() {
    }

    public static void applyPortableSnapshot(PortableHudSnapshot snapshot) {
        invoke(HUD, "applySnapshot", PortableHudSnapshot.class, snapshot);
        invoke(VISION, "applySnapshot", PortableHudSnapshot.class, snapshot);
    }

    public static void gogglesDeploy(Player player, InteractionHand hand, ItemStack stack) {
        invoke(VISION, "deployOptics", Player.class, InteractionHand.class, ItemStack.class, player, hand, stack);
    }

    public static void gogglesStow(Player player, InteractionHand hand, ItemStack stack) {
        invoke(VISION, "stowOptics", Player.class, InteractionHand.class, ItemStack.class, player, hand, stack);
    }

    public static void gogglesClearTarget(Player player, InteractionHand hand, ItemStack stack) {
        invoke(VISION, "clearRadarTarget", Player.class, InteractionHand.class, ItemStack.class, player, hand, stack);
    }

    public static void gogglesUnbind(Player player) {
        invoke(VISION, "onUnbind", Player.class, player);
    }

    public static void openPortableTablet(InteractionHand hand, NetworkFilterBinding.Bound binding) {
        invoke(OPENER, "open", InteractionHand.class, NetworkFilterBinding.Bound.class, hand, binding);
    }

    private static void invoke(String className, String method, Class<?> p1, Object a1) {
        try {
            Class.forName(className).getMethod(method, p1).invoke(null, a1);
        } catch (Throwable ignored) {
        }
    }

    private static void invoke(
            String className,
            String method,
            Class<?> p1,
            Class<?> p2,
            Object a1,
            Object a2) {
        try {
            Class.forName(className).getMethod(method, p1, p2).invoke(null, a1, a2);
        } catch (Throwable ignored) {
        }
    }

    private static void invoke(
            String className,
            String method,
            Class<?> p1,
            Class<?> p2,
            Class<?> p3,
            Object a1,
            Object a2,
            Object a3) {
        try {
            Class.forName(className).getMethod(method, p1, p2, p3).invoke(null, a1, a2, a3);
        } catch (Throwable ignored) {
        }
    }
}
