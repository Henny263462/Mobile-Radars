package dev.henny.mobile_radars.registry;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/** Fügt Tablet und Brille dem Create: Radars-Kreativ-Tab hinzu. */
public final class MobileRadarsCreativeTab {

    private static final ResourceLocation CREATE_RADAR_TAB =
            ResourceLocation.fromNamespaceAndPath("create_radar", "radar");

    private MobileRadarsCreativeTab() {
    }

    public static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().location().equals(CREATE_RADAR_TAB)) {
            return;
        }
        event.accept(MobileRadarsItems.MOBILE_RADAR_TABLET.get());
        event.accept(MobileRadarsItems.RADAR_GOGGLES.get());
    }
}
