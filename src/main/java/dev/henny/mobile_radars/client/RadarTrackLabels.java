package dev.henny.mobile_radars.client;

import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.block.radar.track.TrackCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.UUID;

/** Lokalisierte Anzeigenamen für Radar-Tracks (Entity-Typen, Spieler). */
public final class RadarTrackLabels {

    private RadarTrackLabels() {
    }

    public static Component displayName(Minecraft mc, RadarTrack track) {
        Level level = mc.level;
        if (level == null) {
            return Component.literal("?");
        }

        if (track.trackCategory() == TrackCategory.PLAYER) {
            try {
                UUID uuid = UUID.fromString(track.getId());
                Player player = level.getPlayerByUUID(uuid);
                if (player != null) {
                    return player.getDisplayName();
                }
            } catch (IllegalArgumentException ignored) {
                // fall through
            }
        }

        @Nullable ResourceLocation entityId = parseEntityTypeId(track.getEntityType());
        if (entityId != null) {
            return BuiltInRegistries.ENTITY_TYPE
                    .getOptional(entityId)
                    .map(EntityType::getDescription)
                    .orElse(Component.literal(humanizeRawId(track.getEntityType())));
        }

        return Component.literal(humanizeRawId(track.getEntityType()));
    }

    public static String displayNameString(Minecraft mc, RadarTrack track) {
        return displayName(mc, track).getString();
    }

    @Nullable
    static ResourceLocation parseEntityTypeId(@Nullable String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String s = raw.trim();
        int gtOpen = s.indexOf('<');
        int gtClose = s.indexOf('>');
        if (gtOpen >= 0 && gtClose > gtOpen) {
            s = s.substring(gtOpen + 1, gtClose).trim();
        }
        if (s.startsWith("entity.")) {
            s = s.substring("entity.".length());
        }
        int colon = s.indexOf(':');
        if (colon < 0) {
            int dot = s.indexOf('.');
            if (dot > 0 && dot < s.length() - 1) {
                s = s.substring(0, dot) + ':' + s.substring(dot + 1);
            }
        }
        return ResourceLocation.tryParse(s);
    }

    private static String humanizeRawId(@Nullable String raw) {
        if (raw == null || raw.isEmpty()) {
            return "?";
        }
        @Nullable ResourceLocation id = parseEntityTypeId(raw);
        if (id != null) {
            return id.getPath().replace('_', ' ');
        }
        String tail = raw;
        int colon = tail.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < tail.length()) {
            tail = tail.substring(colon + 1);
        }
        return tail.replace('_', ' ');
    }
}
