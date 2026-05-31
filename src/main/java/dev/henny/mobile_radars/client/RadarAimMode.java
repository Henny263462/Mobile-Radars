package dev.henny.mobile_radars.client;

import net.minecraft.network.chat.Component;

/** Wie die Radar-Brille zielt (Fadenkreuz + Zielerfassung) — per G wechseln. */
public enum RadarAimMode {

    /** Fadenkreuz bleibt in der Mitte; Ziel nur per Blick-Raycast (auch durch Wände). */
    MANUAL,
    /** Fadenkreuz rastet voll auf das nächste Entity am Bildschirm ein. */
    SNAP,
    /** Weiches Anziehen zum nächsten Ziel im Sichtfeld. */
    ASSIST;

    public RadarAimMode next() {
        RadarAimMode[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public Component displayName() {
        return Component.translatable("create_radar_mobile_radars.aim_mode." + name().toLowerCase());
    }

    /** 0 = kein Snap, 1 = volles Einrasten auf Bildschirmposition. */
    public float crosshairPull() {
        return switch (this) {
            case MANUAL -> 0f;
            case ASSIST -> 0.55f;
            case SNAP -> 1f;
        };
    }

    public float crosshairLerp() {
        return switch (this) {
            case MANUAL -> 0.35f;
            case ASSIST -> 0.14f;
            case SNAP -> 0.32f;
        };
    }

    /** Max. Abstand (px) vom Bildschirmzentrum, in dem ein Ziel gewählt wird. */
    public float maxPickRadiusPx() {
        return switch (this) {
            case MANUAL -> 0f;
            case ASSIST -> 96f;
            case SNAP -> Float.MAX_VALUE;
        };
    }
}
