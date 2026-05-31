package dev.henny.mobile_radars.client;

import com.happysg.radar.block.radar.track.RadarTrack;
import dev.henny.mobile_radars.network.PortableHudSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/** Zielerfassung und Fadenkreuz-Magnet für die Radar-Brille (ohne Welt-ESP). */
final class RadarAimController {

    private static final double AIM_RAY_REACH = 128.0;
    private static final long FRESH_FADE_TICKS = 100L;

    @Nullable private static volatile String aimTrackId;
    @Nullable private static volatile RadarEspScreenProjection.ScreenPoint aimScreenPoint;

    private RadarAimController() {
    }

    record SnapTarget(String trackId, RadarEspScreenProjection.ScreenPoint screenPoint) {}

    @Nullable
    static SnapTarget aimTarget() {
        if (aimTrackId == null || aimScreenPoint == null) {
            return null;
        }
        return new SnapTarget(aimTrackId, aimScreenPoint);
    }

    @Nullable
    static String aimTrackId() {
        return aimTrackId;
    }

    @Nullable
    static RadarTrack hoveredTrack() {
        String id = aimTrackId;
        if (id == null) {
            return null;
        }
        PortableHudSnapshot snap = RadarVisionClient.radarEspSnapshot();
        if (snap.tracks() != null) {
            for (RadarTrack t : snap.tracks()) {
                if (id.equals(t.id()) || id.equals(t.getId())) {
                    return t;
                }
            }
        }
        return null;
    }

    static double hoveredDistance(Vec3 eye) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return -1;
        }
        if (!(mc.level instanceof ClientLevel cl)) {
            return -1;
        }
        @Nullable Entity entity = findEntityById(cl, aimTrackId);
        if (entity != null) {
            return eye.distanceTo(entity.getBoundingBox().getCenter());
        }
        RadarTrack t = hoveredTrack();
        return t == null ? -1 : eye.distanceTo(aimAnchor(mc, t));
    }

    static void clear() {
        aimTrackId = null;
        aimScreenPoint = null;
    }

    static void update(Minecraft mc, Player player, PortableHudSnapshot snap, float partialTick) {
        if (!RadarVisionClient.shouldShowRadarEsp(mc) || mc.level == null) {
            clear();
            return;
        }

        float centerX = mc.getWindow().getGuiScaledWidth() * 0.5f;
        float centerY = mc.getWindow().getGuiScaledHeight() * 0.5f;
        String selfUuid = player.getStringUUID();

        SnapTarget resolved = resolveAimTarget(mc, player, snap, partialTick, selfUuid, centerX, centerY);
        if (resolved == null) {
            clear();
            return;
        }
        aimTrackId = resolved.trackId();
        aimScreenPoint = resolved.screenPoint();
    }

    @Nullable
    private static SnapTarget resolveAimTarget(
            Minecraft mc,
            Player player,
            PortableHudSnapshot snap,
            float pt,
            String selfUuid,
            float centerX,
            float centerY) {
        return switch (RadarVisionClient.aimMode()) {
            case MANUAL -> resolveManual(mc, player, snap, pt, selfUuid);
            case SNAP, ASSIST -> findNearestToScreenCenter(mc, snap, pt, selfUuid, centerX, centerY);
        };
    }

    @Nullable
    private static SnapTarget resolveManual(
            Minecraft mc, Player player, PortableHudSnapshot snap, float pt, String selfUuid) {
        SnapTarget ray = findRaycastOnTracks(mc, player, snap, pt, selfUuid);
        if (ray != null) {
            return ray;
        }
        HitResult hr = mc.hitResult;
        if (hr instanceof EntityHitResult erh) {
            Entity picked = erh.getEntity();
            if (!picked.is(player)) {
                return screenTarget(mc, picked.getStringUUID(), picked.getBoundingBox().getCenter(), pt);
            }
        }
        return null;
    }

    @Nullable
    private static SnapTarget findNearestToScreenCenter(
            Minecraft mc,
            PortableHudSnapshot snap,
            float pt,
            String selfUuid,
            float centerX,
            float centerY) {
        if (snap.tracks() == null || mc.level == null) {
            return null;
        }

        float fovScale = RadarVisionClient.visionFovScale();
        float maxRadius = RadarVisionClient.aimMode().maxPickRadiusPx();
        float maxDist2 = maxRadius >= Float.MAX_VALUE * 0.5f ? Float.MAX_VALUE : maxRadius * maxRadius;
        float bestDist2 = Float.MAX_VALUE;
        String bestId = null;
        RadarEspScreenProjection.ScreenPoint bestPoint = null;

        for (RadarTrack track : snap.tracks()) {
            if (isSelfTrack(track, selfUuid) || !isTrackFresh(mc, track)) {
                continue;
            }
            if (!isTrackAimable(mc, track)) {
                continue;
            }
            RadarEspScreenProjection.ScreenPoint sp =
                    RadarEspScreenProjection.project(mc, aimAnchor(mc, track), pt, fovScale);
            if (sp == null) {
                continue;
            }
            float dx = sp.x() - centerX;
            float dy = sp.y() - centerY;
            float dist2 = dx * dx + dy * dy;
            if (dist2 > maxDist2 || dist2 >= bestDist2) {
                continue;
            }
            bestDist2 = dist2;
            bestId = track.id();
            bestPoint = sp;
        }

        return bestId == null ? null : new SnapTarget(bestId, bestPoint);
    }

    @Nullable
    private static SnapTarget findRaycastOnTracks(
            Minecraft mc, Player player, PortableHudSnapshot snap, float pt, String selfUuid) {
        if (snap.tracks() == null || mc.level == null) {
            return null;
        }
        Vec3 eye = player.getEyePosition(pt);
        Vec3 dir = player.getViewVector(pt).normalize();
        Vec3 end = eye.add(dir.scale(AIM_RAY_REACH));

        double bestAlong = Double.MAX_VALUE;
        String bestId = null;
        Vec3 bestAnchor = null;

        for (RadarTrack track : snap.tracks()) {
            if (isSelfTrack(track, selfUuid) || !isTrackFresh(mc, track) || !isTrackAimable(mc, track)) {
                continue;
            }
            AABB box = aimBox(mc, track).inflate(0.06);
            var hit = box.clip(eye, end);
            if (hit.isEmpty()) {
                continue;
            }
            double along = eye.distanceToSqr(hit.get());
            if (along < bestAlong) {
                bestAlong = along;
                bestId = track.id();
                bestAnchor = aimAnchor(mc, track);
            }
        }

        if (bestId == null || bestAnchor == null) {
            return null;
        }
        return screenTarget(mc, bestId, bestAnchor, pt);
    }

    @Nullable
    private static SnapTarget screenTarget(Minecraft mc, String id, Vec3 worldPos, float pt) {
        RadarEspScreenProjection.ScreenPoint sp =
                RadarEspScreenProjection.project(mc, worldPos, pt, RadarVisionClient.visionFovScale());
        if (sp == null) {
            return null;
        }
        return new SnapTarget(id, sp);
    }

    private static Vec3 aimAnchor(Minecraft mc, RadarTrack track) {
        if (mc.level instanceof ClientLevel cl) {
            Entity live = findEntityForTrack(cl, track);
            if (live != null && live.isAlive()) {
                return live.getBoundingBox().getCenter();
            }
        }
        float h = sanitizedHeight(track.getEnityHeight());
        return track.position().add(0, h * 0.5, 0);
    }

    private static AABB aimBox(Minecraft mc, RadarTrack track) {
        if (mc.level instanceof ClientLevel cl) {
            Entity live = findEntityForTrack(cl, track);
            if (live != null && live.isAlive()) {
                return live.getBoundingBox();
            }
        }
        float h = sanitizedHeight(track.getEnityHeight());
        float halfW = Mth.clamp(h * 0.28f, 0.28f, 1.2f);
        Vec3 p = track.position();
        return new AABB(p.x - halfW, p.y, p.z - halfW, p.x + halfW, p.y + h, p.z + halfW);
    }

    private static float sanitizedHeight(float h) {
        if (Float.isNaN(h) || h <= 0.05f) {
            return 1.8f;
        }
        return Math.min(h, 12f);
    }

    private static boolean isTrackFresh(Minecraft mc, RadarTrack track) {
        float fade = Mth.clamp((mc.level.getGameTime() - track.scannedTime()) / FRESH_FADE_TICKS, 0f, 1f);
        return 1f - fade > 0.02f;
    }

    /** Nur lebende Entities oder noch frische Radar-Daten — verhindert Geister-Ziele. */
    private static boolean isTrackAimable(Minecraft mc, RadarTrack track) {
        if (mc.level instanceof ClientLevel cl) {
            Entity live = findEntityForTrack(cl, track);
            if (live != null) {
                return live.isAlive();
            }
        }
        return isTrackFresh(mc, track);
    }

    private static boolean isSelfTrack(RadarTrack track, String selfUuid) {
        return selfUuid.equals(track.id()) || selfUuid.equals(track.getId());
    }

    @Nullable
    private static Entity findEntityById(ClientLevel level, @Nullable String trackId) {
        if (trackId == null) {
            return null;
        }
        for (Entity entity : level.entitiesForRendering()) {
            if (trackId.equals(entity.getStringUUID()) || trackId.equals(entity.getUUID().toString())) {
                return entity;
            }
        }
        return null;
    }

    @Nullable
    private static Entity findEntityForTrack(ClientLevel level, RadarTrack track) {
        Entity byId = findEntityById(level, track.id());
        if (byId != null) {
            return byId;
        }
        return findEntityById(level, track.getId());
    }
}
