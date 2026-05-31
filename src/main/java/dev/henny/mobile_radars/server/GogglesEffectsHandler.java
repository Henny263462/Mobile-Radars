package dev.henny.mobile_radars.server;

import dev.henny.mobile_radars.CreateRadarMobileRadars;
import dev.henny.mobile_radars.util.GogglesState;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Nachtsicht nur, wenn die gekoppelte Brille in der Hand ausgeklappt ist.
 */
@EventBusSubscriber(modid = CreateRadarMobileRadars.MODID)
public final class GogglesEffectsHandler {

    private GogglesEffectsHandler() {
    }

    private static final int REFRESH_DURATION_TICKS = 300;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        if (!GogglesState.isOpticsActive(player) || GogglesState.heldBinding(player) == null) {
            return;
        }

        MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
        if (current != null && current.getDuration() > REFRESH_DURATION_TICKS - 40) {
            return;
        }
        player.addEffect(new MobEffectInstance(
                MobEffects.NIGHT_VISION, REFRESH_DURATION_TICKS, 0, true, false, false));
    }
}
