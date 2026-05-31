package dev.henny.mobile_radars.client;

import dev.henny.mobile_radars.util.NetworkFilterBinding;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class MobilePortableOpener {

    private MobilePortableOpener() {
    }

    public static void open(InteractionHand hand, NetworkFilterBinding.Bound bound) {
        Minecraft.getInstance().setScreen(new PortableRadarScreen(hand, bound));
    }
}
