package dev.henny.mobile_radars.network;

import com.happysg.radar.block.radar.track.RadarTrack;

import javax.annotation.Nullable;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * Server-built state for the handheld radar UI.
 */
public record PortableHudSnapshot(
        boolean antennaPresent,
        boolean inRange,
        boolean sweepRunning,
        @Nullable BlockPos radarPos,
        CompoundTag detectionTag,
        List<RadarTrack> tracks,
        @Nullable String selectedId
) {
    public boolean canRenderLiveRadar() {
        return antennaPresent && inRange && sweepRunning;
    }

    public static PortableHudSnapshot emptyOffline() {
        return new PortableHudSnapshot(false, false, false, null, new CompoundTag(), List.of(), null);
    }
}
