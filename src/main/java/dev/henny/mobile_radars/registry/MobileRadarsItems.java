package dev.henny.mobile_radars.registry;

import dev.henny.mobile_radars.CreateRadarMobileRadars;
import dev.henny.mobile_radars.item.MobileRadarTabletItem;
import dev.henny.mobile_radars.item.RadarGogglesItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MobileRadarsItems {

    public static final DeferredRegister.Items REGISTER =
            DeferredRegister.createItems(CreateRadarMobileRadars.MODID);

    public static final DeferredHolder<Item, MobileRadarTabletItem> MOBILE_RADAR_TABLET =
            REGISTER.registerItem("mobile_radar_tablet", props -> new MobileRadarTabletItem(props.stacksTo(1)));

    public static final DeferredHolder<Item, RadarGogglesItem> RADAR_GOGGLES =
            REGISTER.registerItem("radar_goggles", props -> new RadarGogglesItem(props.stacksTo(1)));

    private MobileRadarsItems() {
    }
}
