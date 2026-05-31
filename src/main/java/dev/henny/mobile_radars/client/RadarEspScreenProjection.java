package dev.henny.mobile_radars.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;

/** Projiziert Weltkoordinaten in GUI-Skalen-Pixel (für Fadenkreuz-Magnet). */
final class RadarEspScreenProjection {

    private RadarEspScreenProjection() {
    }

    @Nullable
    static ScreenPoint project(Minecraft mc, Vec3 worldPos, float partialTick, float fovScale) {
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();
        Quaternionf invRot = camera.rotation().conjugate(new Quaternionf());

        Vector3f view = new Vector3f(
                (float) (worldPos.x - camPos.x),
                (float) (worldPos.y - camPos.y),
                (float) (worldPos.z - camPos.z));
        invRot.transform(view);

        if (view.z >= -0.02f) {
            return null;
        }

        float fovDeg = mc.options.fov().get().floatValue() * Mth.clamp(fovScale, 0.35f, 1.0f);
        float aspect = (float) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        float halfH = (float) Math.tan(Math.toRadians(fovDeg) * 0.5);
        float halfW = halfH * aspect;

        float ndcX = view.x / (-view.z * halfW);
        float ndcY = view.y / (-view.z * halfH);

        if (ndcX < -1.35f || ndcX > 1.35f || ndcY < -1.35f || ndcY > 1.35f) {
            return null;
        }

        int gw = mc.getWindow().getGuiScaledWidth();
        int gh = mc.getWindow().getGuiScaledHeight();
        float sx = (ndcX * 0.5f + 0.5f) * gw;
        float sy = (1f - (ndcY * 0.5f + 0.5f)) * gh;
        return new ScreenPoint(sx, sy);
    }

    record ScreenPoint(float x, float y) {}
}
