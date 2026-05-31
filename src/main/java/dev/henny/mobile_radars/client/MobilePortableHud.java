package dev.henny.mobile_radars.client;

import dev.henny.mobile_radars.network.PortableHudSnapshot;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class MobilePortableHud {

    private MobilePortableHud() {
    }

    public static void applySnapshot(PortableHudSnapshot snapshot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof PortableRadarScreen screen) {
            screen.applySnapshot(snapshot);
        }
    }
}
