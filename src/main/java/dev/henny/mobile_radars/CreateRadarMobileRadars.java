package dev.henny.mobile_radars;

import dev.henny.mobile_radars.cfg.MobileRadarsConfig;
import dev.henny.mobile_radars.network.MobileRadarsNetworking;
import dev.henny.mobile_radars.registry.MobileRadarsCreativeTab;
import dev.henny.mobile_radars.registry.MobileRadarsItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(CreateRadarMobileRadars.MODID)
public final class CreateRadarMobileRadars {

    public static final String MODID = "create_radar_mobile_radars";

    public CreateRadarMobileRadars(IEventBus modEventBus, ModContainer container) {
        MobileRadarsItems.REGISTER.register(modEventBus);
        modEventBus.addListener(MobileRadarsCreativeTab::onBuildCreativeTab);

        container.registerConfig(ModConfig.Type.SERVER, MobileRadarsConfig.SPEC, MODID + "-server.toml");
        modEventBus.addListener(MobileRadarsNetworking::register);
    }
}
