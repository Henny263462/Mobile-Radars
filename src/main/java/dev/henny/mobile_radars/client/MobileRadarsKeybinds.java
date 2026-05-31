package dev.henny.mobile_radars.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.henny.mobile_radars.CreateRadarMobileRadars;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = CreateRadarMobileRadars.MODID, value = Dist.CLIENT)
public final class MobileRadarsKeybinds {

    private MobileRadarsKeybinds() {
    }

    public static final String CATEGORY = "key.categories." + CreateRadarMobileRadars.MODID;

    public static final KeyMapping RADAR_ZOOM = new KeyMapping(
            "key." + CreateRadarMobileRadars.MODID + ".radar_zoom",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            CATEGORY);

    public static final KeyMapping RADAR_MODE_CYCLE = new KeyMapping(
            "key." + CreateRadarMobileRadars.MODID + ".radar_mode_cycle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY);

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(RADAR_ZOOM);
        event.register(RADAR_MODE_CYCLE);
    }
}
