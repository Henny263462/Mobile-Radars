package dev.henny.mobile_radars.client;

import com.happysg.radar.block.behavior.networks.config.DetectionConfig;
import com.happysg.radar.block.controller.id.IDManager;
import com.happysg.radar.block.monitor.MonitorSprite;
import com.happysg.radar.block.radar.behavior.IRadar;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.block.radar.track.TrackCategory;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.happysg.radar.config.RadarConfig;
import dev.henny.mobile_radars.network.PortableHudSnapshot;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

final class PortableRadarHud {

    private PortableRadarHud() {
    }

    static Vec3 radarOrigin(Level level, @Nullable BlockPos radarBlock) {
        if (level == null || radarBlock == null) {
            return null;
        }
        return PhysicsHandler.getWorldVec(level, radarBlock).add(0.5, 0.5, 0.5);
    }

    static void drawTracks(GuiGraphics gg,
                           IRadar radar,
                           PortableHudSnapshot snap,
                           @Nullable String hoveredId,
                           int left,
                           int top,
                           int uiSize,
                           float uiScale,
                           Font font,
                           float trackPositionScale,
                           Direction projectionFacing) {
        DetectionConfig dc = DetectionConfig.fromTag(snap.detectionTag());
        Minecraft mc = Minecraft.getInstance();

        float range = radar.getRange();
        float cellWorld = 50f;
        int halfCells = Mth.floor(range / cellWorld);
        halfCells = Mth.clamp(halfCells, 2, 24);
        int totalCells = halfCells * 2;

        Color color = new Color(RadarConfig.client().groundRadarColor.get());
        int ga = (int) Math.round(0.1f * 255f);
        int gridRgb = ga << 24 | (color.getRGB() & 0xFFFFFF);

        int margin = Math.round(21 * uiScale);
        int gridLeft = left + margin;
        int gridTop = top + margin;
        int gridRight = left + uiSize - margin;
        int gridBottom = top + uiSize - margin;
        float spacing = (gridRight - gridLeft) / (float) totalCells;

        for (int i = 0; i <= totalCells; i++) {
            int x = gridLeft + Math.round(i * spacing);
            gg.fill(x, gridTop, x + 1, gridBottom, gridRgb);
        }
        for (int i = 0; i <= totalCells; i++) {
            int y = gridTop + Math.round(i * spacing);
            gg.fill(gridLeft, y, gridRight, y + 1, gridRgb);
        }

        gg.setColor(color.getRedAsFloat(), color.getGreenAsFloat(), color.getBlueAsFloat(), 0.35f);
        RenderSystem.enableBlend();
        gg.blit(MonitorSprite.RADAR_BG_CIRCLE.getTexture(), left, top, 0, 0, uiSize, uiSize, uiSize, uiSize);

        float screenAngle = (radar.getGlobalAngle() + 360f) % 360f;
        int cx = left + uiSize / 2;
        int cy = top + uiSize / 2;
        gg.setColor(color.getRedAsFloat(), color.getGreenAsFloat(), color.getBlueAsFloat(), 0.8f);
        gg.pose().pushPose();
        gg.pose().translate(cx, cy, 0);
        gg.pose().mulPose(Axis.ZP.rotationDegrees(-screenAngle));
        gg.pose().translate(-cx, -cy, 0);
        gg.blit(MonitorSprite.RADAR_SWEEP.getTexture(), left, top, 0, 0, uiSize, uiSize, uiSize, uiSize);
        gg.pose().popPose();
        gg.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();

        Vec3 antenna = radarOrigin(mc.level, snap.radarPos());
        if (antenna == null || mc.level == null) {
            return;
        }
        Level level = mc.level;

        for (RadarTrack track : snap.tracks()) {
            Vec3 rel = track.position().subtract(antenna);
            float xOff = offset(rel, projectionFacing, range, true);
            float zOff = offset(rel, projectionFacing, range, false);

            if (Math.abs(xOff) > 0.5f || Math.abs(zOff) > 0.5f) {
                continue;
            }

            xOff *= trackPositionScale;
            zOff *= trackPositionScale;

            int px = (int) (left + (0.5f + xOff) * uiSize);
            int pz = (int) (top + (0.5f + zOff) * uiSize);

            long currentTime = level.getGameTime();
            float fade = Mth.clamp((currentTime - track.scannedTime()) / 100f, 0f, 1f);
            float alpha = 1f - fade;
            if (alpha <= 0.02f) {
                continue;
            }

            Color c = dc.getColor(track);

            RenderSystem.enableBlend();
            gg.setColor(c.getRedAsFloat(), c.getGreenAsFloat(), c.getBlueAsFloat(), alpha);
            int spriteSize = Math.max(8, Math.round(256 * uiScale));

            gg.blit(track.getSprite().getTexture(), px - spriteSize / 2, pz - spriteSize / 2, 0, 0,
                    spriteSize, spriteSize, spriteSize, spriteSize);

            if (track.id().equals(hoveredId)) {
                gg.setColor(1f, 1f, 0f, alpha);
                gg.blit(MonitorSprite.TARGET_HOVERED.getTexture(), px - spriteSize / 2, pz - spriteSize / 2, 0, 0,
                        spriteSize, spriteSize, spriteSize, spriteSize);
            }

            String sel = snap.selectedId();
            if (sel != null && sel.equals(track.id())) {
                gg.setColor(1f, 0f, 0f, alpha);
                gg.blit(MonitorSprite.TARGET_SELECTED.getTexture(), px - spriteSize / 2, pz - spriteSize / 2, 0, 0,
                        spriteSize, spriteSize, spriteSize, spriteSize);
            }

            gg.setColor(1f, 1f, 1f, 1f);
            RenderSystem.disableBlend();

            String label = label(mc, track);
            if (label != null && !label.isBlank()) {
                int argb = (((int) Math.round(alpha * 255f)) << 24) | 0xFFFFFF;
                gg.pose().pushPose();
                gg.pose().translate(px, pz + Math.round(8 * uiScale), 0);
                float ts = RadarConfig.client().monitorTextScale.getF();
                gg.pose().scale(ts, ts, 1f);
                gg.drawCenteredString(font, label, 0, 0, argb);
                gg.pose().popPose();
            }
        }
    }

    /**
     * Welcher Track liegt unter der Maus — gleiche Geometrie wie {@link #drawTracks}.
     */
    @Nullable
    static String pickHoveredTrack(
            int mouseX,
            int mouseY,
            IRadar radar,
            PortableHudSnapshot snap,
            int left,
            int top,
            int uiSize,
            float uiScale,
            float trackPositionScale,
            Direction projectionFacing) {
        if (mouseX < left || mouseX >= left + uiSize || mouseY < top || mouseY >= top + uiSize) {
            return null;
        }
        if (!snap.canRenderLiveRadar()) {
            return null;
        }

        Minecraft mc = Minecraft.getInstance();
        Vec3 origin = radarOrigin(mc.level, snap.radarPos());
        if (origin == null) {
            return null;
        }
        float rng = radar.getRange();

        float pickDist = Math.max(6, Math.round(20 * uiScale)) * 0.75f;
        pickDist *= pickDist;
        float best = pickDist;

        String bestId = null;
        for (RadarTrack track : snap.tracks()) {
            Vec3 rel = track.position().subtract(origin);
            float xOff = offset(rel, projectionFacing, rng, true);
            float zOff = offset(rel, projectionFacing, rng, false);
            if (Math.abs(xOff) > 0.5f || Math.abs(zOff) > 0.5f) {
                continue;
            }

            xOff *= trackPositionScale;
            zOff *= trackPositionScale;

            int px = (int) (left + (0.5f + xOff) * uiSize);
            int py = (int) (top + (0.5f + zOff) * uiSize);

            float dx = mouseX - px;
            float dy = mouseY - py;
            float d2 = dx * dx + dy * dy;
            if (d2 < best) {
                best = d2;
                bestId = track.id();
            }
        }
        return bestId;
    }

    static float offset(Vec3 relativePos, Direction monitorFacing, float scale, boolean isXOffset) {
        float axis;
        if (isXOffset) {
            axis = monitorFacing.getAxis() == Direction.Axis.Z
                    ? axisOffset(relativePos.x, scale)
                    : axisOffset(relativePos.z, scale);
            if (monitorFacing == Direction.NORTH || monitorFacing == Direction.EAST) {
                axis = -axis;
            }
        } else {
            axis = monitorFacing.getAxis() == Direction.Axis.Z
                    ? axisOffset(relativePos.z, scale)
                    : axisOffset(relativePos.x, scale);

            if (monitorFacing == Direction.NORTH || monitorFacing == Direction.WEST) {
                axis = -axis;
            }
        }
        return axis;
    }

    private static float axisOffset(double coord, float scale) {
        return (float) (coord / scale) / 2f;
    }

    private static String label(Minecraft mc, RadarTrack track) {
        Level level = mc.level;
        if (level == null) {
            return null;
        }
        if ("Sable:ship".equals(track.entityType())) {
            try {
                UUID shipId = UUID.fromString(track.id());
                IDManager.IDRecord rec = IDManager.getIDRecordByShipId(shipId);
                if (rec != null && rec.name() != null && !rec.name().isBlank()) {
                    return rec.name();
                }
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        if (track.trackCategory() == TrackCategory.PLAYER) {
            try {
                UUID uuid = UUID.fromString(track.getId());
                Player player = level.getPlayerByUUID(uuid);
                return player != null ? player.getName().getString() : null;
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }
}
