package dev.henny.mobile_radars.cfg;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class MobileRadarsConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue PORTABLE_MAX_RANGE_BLOCKS = BUILDER
            .comment(
                    "Tablet max range",
                    "",
                    "Max distance (blocks, Euclidean) from the player's feet to the linked radar antenna.",
                    "This file is SERVER-side — on a dedicated server it lives under the world's serverconfigs folder.",
                    "Requires a logical server reload / reboot to apply safely.")
            .defineInRange("portableMaxRangeBlocks", 128d, 8d, 4096d);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private MobileRadarsConfig() {
    }

    public static double portableMaxRangeBlocks() {
        return PORTABLE_MAX_RANGE_BLOCKS.get();
    }
}
