package dev.henny.mobile_radars.client;

import com.happysg.radar.block.radar.track.RadarTrack;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.henny.mobile_radars.CreateRadarMobileRadars;
import dev.henny.mobile_radars.item.RadarGogglesItem;
import dev.henny.mobile_radars.network.MobileRadarsNetworking;
import dev.henny.mobile_radars.network.PortableHudSnapshot;
import dev.henny.mobile_radars.util.GogglesState;
import dev.henny.mobile_radars.util.NetworkFilterBinding;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;

/**
 * Radar-Brille: Zielmodi über {@link RadarAimController}, eigenes „CRT"-GUI-Overlay, Zoom-Steuerung sowie das
 * Laden/Entladen eines barrel-distortion-Post-Effects der die Welt durch eine gewölbte Röhre aussehen lässt.
 * Selektion läuft per Linksklick auf ein gehovertes Radar-Symbol oder eine Entity unter dem Fadenkreuz; ein
 * Tick-Cooldown verhindert Spamming. Spieler kann sich nicht selbst auswählen.
 */
@EventBusSubscriber(modid = CreateRadarMobileRadars.MODID, value = Dist.CLIENT)
public final class RadarVisionClient {

    private RadarVisionClient() {
    }

    private static final int SELECT_COOLDOWN_TICKS = 12;
    private static final int LOCK_FLASH_TICKS = 10;
    private static final int BLOCKED_FLASH_TICKS = 8;
    private static final float ZOOM_FOV_FACTOR = 0.35f;
    private static final float ZOOM_LERP = 0.20f;
    private static final ResourceLocation POST_EFFECT =
            ResourceLocation.fromNamespaceAndPath(CreateRadarMobileRadars.MODID, "shaders/post/radar_vision.json");

    private static volatile boolean active;
    private static InteractionHand gogglesHand = InteractionHand.MAIN_HAND;
    private static PortableHudSnapshot snapshot = PortableHudSnapshot.emptyOffline();
    @Nullable private static NetworkFilterBinding.Bound binding;
    private static int syncCounter;

    private static long lastSelectGameTime = Long.MIN_VALUE / 4;
    private static long lockFlashGameTime = Long.MIN_VALUE / 4;
    private static long blockedFlashGameTime = Long.MIN_VALUE / 4;

    private static float zoomCurrent = 1.0f;
    private static float zoomTarget = 1.0f;
    private static boolean shaderActive;

    private static float crosshairOffsetX;
    private static float crosshairOffsetY;

    private static RadarAimMode aimMode = RadarAimMode.SNAP;

    static float visionFovScale() {
        return zoomCurrent;
    }

    static RadarAimMode aimMode() {
        return aimMode;
    }

    static boolean shouldShowRadarEsp(Minecraft mc) {
        return mc.player != null
                && mc.screen == null
                && mc.level != null
                && snapshot.canRenderLiveRadar()
                && hasValidSession(mc.player);
    }

    private static boolean hasValidSession(Player player) {
        if (!active || binding == null) {
            return false;
        }
        ItemStack held = player.getItemInHand(gogglesHand);
        if (!(held.getItem() instanceof RadarGogglesItem) || !GogglesState.isDeployed(held)) {
            return false;
        }
        if (!player.level().dimension().equals(binding.dimension())) {
            return false;
        }
        NetworkFilterBinding.Bound onStack = NetworkFilterBinding.read(held);
        return onStack != null
                && onStack.filtererPos().equals(binding.filtererPos())
                && onStack.dimension().equals(binding.dimension());
    }

    static PortableHudSnapshot radarEspSnapshot() {
        return snapshot;
    }

    public static void applySnapshot(PortableHudSnapshot snap) {
        snapshot = snap != null ? snap : PortableHudSnapshot.emptyOffline();
    }

    public static void onUnbind(Player player) {
        if (player.level().isClientSide()) {
            deactivateHud();
        }
    }

    public static void deployOptics(Player player, InteractionHand hand, ItemStack stack) {
        NetworkFilterBinding.Bound b = NetworkFilterBinding.read(stack);
        if (b == null || !player.level().dimension().equals(b.dimension())) {
            deactivateHud();
            return;
        }
        gogglesHand = hand;
        binding = b;
        active = true;
        syncCounter = 0;
        flushSync(player);
    }

    public static void stowOptics(Player player, InteractionHand hand, ItemStack unusedStack) {
        gogglesHand = hand;
        deactivateHud();
    }

    public static void clearRadarTarget(Player player, InteractionHand hand, ItemStack stack) {
        NetworkFilterBinding.Bound b = NetworkFilterBinding.read(stack);
        if (b == null || !player.level().dimension().equals(b.dimension())) {
            deactivateHud();
            return;
        }
        gogglesHand = hand;
        binding = b;
        if (!active) {
            active = true;
            syncCounter = 0;
            flushSync(player);
            return;
        }
        if (cooldownActive(player.level().getGameTime())) {
            blockedFlashGameTime = player.level().getGameTime();
            return;
        }
        lastSelectGameTime = player.level().getGameTime();
        PacketDistributor.sendToServer(
                new MobileRadarsNetworking.PortableSelect(
                        binding.filtererPos(),
                        binding.dimension(),
                        GogglesState.packetHandOrdinal(hand),
                        null));
    }

    private static void deactivateHud() {
        active = false;
        binding = null;
        syncCounter = 0;
        zoomTarget = 1.0f;
        crosshairOffsetX = 0f;
        crosshairOffsetY = 0f;
        applySnapshot(PortableHudSnapshot.emptyOffline());
        RadarAimController.clear();
    }

    private static void flushSync(Player player) {
        NetworkFilterBinding.Bound b = binding;
        if (b == null) {
            return;
        }
        int ho = GogglesState.packetHandOrdinal(gogglesHand);
        PacketDistributor.sendToServer(new MobileRadarsNetworking.PortableSyncRequest(b.filtererPos(), b.dimension(), ho));
    }

    private static boolean cooldownActive(long gameTime) {
        return gameTime - lastSelectGameTime < SELECT_COOLDOWN_TICKS;
    }

    private static float cooldownProgress(long gameTime) {
        long elapsed = gameTime - lastSelectGameTime;
        if (elapsed >= SELECT_COOLDOWN_TICKS) {
            return 1f;
        }
        return Mth.clamp(elapsed / (float) SELECT_COOLDOWN_TICKS, 0f, 1f);
    }

    private static void enablePostEffect(Minecraft mc) {
        if (shaderActive) {
            return;
        }
        try {
            mc.gameRenderer.loadEffect(POST_EFFECT);
            shaderActive = true;
        } catch (Throwable t) {
            // Fail silently when shaders are disabled / framebuffer is missing.
            shaderActive = false;
        }
    }

    private static void disablePostEffect(Minecraft mc) {
        if (!shaderActive) {
            return;
        }
        try {
            mc.gameRenderer.shutdownEffect();
        } catch (Throwable ignored) {
            // ignored
        }
        shaderActive = false;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post ignored) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            if (active) {
                deactivateHud();
            }
            disablePostEffect(mc);
            zoomCurrent = 1.0f;
            zoomTarget = 1.0f;
            return;
        }

        if (active && !hasValidSession(player)) {
            deactivateHud();
        }

        boolean espActive = shouldShowRadarEsp(mc);
        if (espActive && !shaderActive) {
            enablePostEffect(mc);
        } else if (!espActive && shaderActive) {
            disablePostEffect(mc);
        }

        if (hasValidSession(player)
                && mc.screen == null
                && MobileRadarsKeybinds.RADAR_MODE_CYCLE.consumeClick()) {
            aimMode = aimMode.next();
            crosshairOffsetX = 0f;
            crosshairOffsetY = 0f;
            RadarAimController.clear();
            player.displayClientMessage(
                    Component.translatable(
                            "create_radar_mobile_radars.overlay.aim_mode_switched", aimMode.displayName()),
                    true);
        }

        // Zoom-Target setzen: nur wenn Brille aktiv und Taste gedrückt.
        if (espActive && MobileRadarsKeybinds.RADAR_ZOOM.isDown()) {
            zoomTarget = ZOOM_FOV_FACTOR;
        } else {
            zoomTarget = 1.0f;
        }
        zoomCurrent = Mth.lerp(ZOOM_LERP, zoomCurrent, zoomTarget);

        if (active && binding != null) {
            syncCounter++;
            if (syncCounter >= 10) {
                syncCounter = 0;
                flushSync(player);
            }
        }
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        Minecraft mc = Minecraft.getInstance();
        if (!shouldShowRadarEsp(mc)) {
            return;
        }
        if (zoomCurrent < 0.999f) {
            event.setFOV(event.getFOV() * zoomCurrent);
        }
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        Minecraft mc = Minecraft.getInstance();
        if (!shouldShowRadarEsp(mc)) {
            return;
        }
        event.setNearPlaneDistance(0.0f);
        event.setFarPlaneDistance(Math.max(event.getFarPlaneDistance(), 384.0f));
        event.scaleNearPlaneDistance(1.0f);
        event.scaleFarPlaneDistance(2.2f);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRenderGuiLayerPre(RenderGuiLayerEvent.Pre event) {
        if (!VanillaGuiLayers.CROSSHAIR.equals(event.getName())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (shouldShowRadarEsp(mc)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null || mc.screen != null || !active || binding == null) {
            return;
        }

        GuiGraphics gg = event.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        long gameTime = mc.level.getGameTime();
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float t = gameTime + pt;

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();

        float overlay = hasValidSession(player) ? 1f : 0.35f;
        drawPhosphorWash(gg, w, h, t, overlay);
        drawNoise(gg, w, h, gameTime, overlay);
        drawBezelVignette(gg, w, h, t, overlay);

        if (hasValidSession(player)) {
            RadarAimController.update(mc, player, snapshot, pt);
            updateCrosshairMagnet(w, h, pt);
            drawCrosshair(gg, w, h, crosshairOffsetX, crosshairOffsetY);
            drawCooldownRing(gg, w, h, gameTime);
            drawLockFlash(gg, w, h, crosshairOffsetX, crosshairOffsetY);
            drawBlockedFlash(gg, w, h, gameTime);
            drawHudInfo(gg, mc, player, w, h);
        }

        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
    }

    @SubscribeEvent
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (trySelectTarget(mc, mc.player, true)) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public static void onMouseClick(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT || event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (trySelectTarget(mc, mc.player, false)) {
            event.setCanceled(true);
        }
    }

    static void sendTargetSelect(Player player, String trackOrEntityId) {
        NetworkFilterBinding.Bound b = binding;
        if (b == null || !player.level().isClientSide()) {
            return;
        }
        long gameTime = player.level().getGameTime();
        if (cooldownActive(gameTime)) {
            blockedFlashGameTime = gameTime;
            return;
        }
        lastSelectGameTime = gameTime;
        lockFlashGameTime = gameTime;
        int ho = GogglesState.packetHandOrdinal(gogglesHand);
        PacketDistributor.sendToServer(
                new MobileRadarsNetworking.PortableSelect(b.filtererPos(), b.dimension(), ho, trackOrEntityId));
    }

    private static boolean trySelectTarget(Minecraft mc, @Nullable Player player, boolean fromAttackKey) {
        if (player == null || mc.screen != null || !active || binding == null || mc.level == null) {
            return false;
        }

        if (!hasValidSession(player)) {
            return false;
        }

        NetworkFilterBinding.Bound b = binding;
        if (b == null
                || !b.filtererPos().equals(binding.filtererPos())
                || !b.dimension().equals(binding.dimension())) {
            deactivateHud();
            return false;
        }

        if (!shouldShowRadarEsp(mc)) {
            return false;
        }

        long gameTime = mc.level.getGameTime();
        if (cooldownActive(gameTime)) {
            blockedFlashGameTime = gameTime;
            return fromAttackKey;
        }

        @Nullable String aimId = RadarAimController.aimTrackId();
        if (aimId != null) {
            sendTargetSelect(player, aimId);
            return true;
        }

        HitResult hr = mc.hitResult;
        if (!(hr instanceof EntityHitResult erh)) {
            return false;
        }

        Entity picked = erh.getEntity();
        if (picked.is(player)) {
            return false;
        }

        sendTargetSelect(player, picked.getStringUUID());
        return true;
    }

    private static void drawPhosphorWash(GuiGraphics gg, int w, int h, float t, float strength) {
        int pulse = (int) ((Mth.sin(t * 0.05f) * 0.5f + 0.5f) * 6f * strength);
        int aBase = (int) ((16 + pulse) * strength);
        gg.fill(0, 0, w, h, (aBase << 24) | 0x002818);
    }

    private static void drawNoise(GuiGraphics gg, int w, int h, long tick, float strength) {
        int seed = (int) (tick * 1664525L + 1013904223L);
        int blockSize = 4;
        int count = strength < 0.5f ? 4 : 8;
        for (int i = 0; i < count; i++) {
            seed = seed * 1664525 + 1013904223;
            int x = Math.floorMod(seed >>> 8, Math.max(1, w / blockSize)) * blockSize;
            seed = seed * 1664525 + 1013904223;
            int y = Math.floorMod(seed >>> 8, Math.max(1, h / blockSize)) * blockSize;
            int a = (int) ((10 + ((seed >>> 24) & 0x18)) * strength);
            gg.fill(x, y, x + blockSize, y + blockSize, (a << 24) | 0x66CC99);
        }
    }

    private static void drawBezelVignette(GuiGraphics gg, int w, int h, float t, float strength) {
        int pulse = (int) ((Mth.sin(t * 0.07f) * 0.5f + 0.5f) * 5f * strength);
        int bandsV = Math.min(64, h / 5);
        for (int i = 0; i < bandsV; i++) {
            int a = Mth.clamp((int) (((bandsV - i) * 2 + pulse) * strength), 0, 120);
            gg.fill(0, i, w, i + 1, a << 24);
            gg.fill(0, h - 1 - i, w, h - i, a << 24);
        }
        int bandsH = Math.min(80, w / 5);
        for (int i = 0; i < bandsH; i++) {
            int a = Mth.clamp((int) (((bandsH - i) * 2 + pulse) * strength), 0, 110);
            gg.fill(i, 0, i + 1, h, a << 24);
            gg.fill(w - 1 - i, 0, w - i, h, a << 24);
        }
        int cor = 48;
        for (int i = 0; i < cor; i++) {
            int a = Mth.clamp((int) (((cor - i) * 2) * strength), 0, 90);
            gg.fill(0, i, cor - i, i + 1, a << 24);
            gg.fill(w - (cor - i), i, w, i + 1, a << 24);
            gg.fill(0, h - 1 - i, cor - i, h - i, a << 24);
            gg.fill(w - (cor - i), h - 1 - i, w, h - i, a << 24);
        }
    }

    private static void updateCrosshairMagnet(int w, int h, float ignoredPartialTick) {
        float centerX = w * 0.5f;
        float centerY = h * 0.5f;
        float targetX = 0f;
        float targetY = 0f;

        RadarAimMode mode = aimMode;
        float pull = mode.crosshairPull();
        float lerp = mode.crosshairLerp();

        RadarAimController.SnapTarget aim = RadarAimController.aimTarget();
        if (aim != null && pull > 0.001f) {
            targetX = (aim.screenPoint().x() - centerX) * pull;
            targetY = (aim.screenPoint().y() - centerY) * pull;
        }

        crosshairOffsetX = Mth.lerp(lerp, crosshairOffsetX, targetX);
        crosshairOffsetY = Mth.lerp(lerp, crosshairOffsetY, targetY);
        if (aim == null || pull < 0.001f) {
            crosshairOffsetX = Mth.lerp(0.22f, crosshairOffsetX, 0f);
            crosshairOffsetY = Mth.lerp(0.22f, crosshairOffsetY, 0f);
            if (Math.abs(crosshairOffsetX) < 0.2f) {
                crosshairOffsetX = 0f;
            }
            if (Math.abs(crosshairOffsetY) < 0.2f) {
                crosshairOffsetY = 0f;
            }
        }
    }

    private static void drawCrosshair(GuiGraphics gg, int w, int h, float offX, float offY) {
        int cx = Math.round(w * 0.5f + offX);
        int cy = Math.round(h * 0.5f + offY);
        int len = 7;
        int gap = 2;
        int col = 0x9933DDAA;
        gg.fill(cx - len - gap, cy, cx - gap, cy + 1, col);
        gg.fill(cx + gap, cy, cx + len + gap, cy + 1, col);
        gg.fill(cx, cy - len - gap, cx + 1, cy - gap, col);
        gg.fill(cx, cy + gap, cx + 1, cy + len + gap, col);

        int br = 11;
        int bo = 1;
        int bc = 0x4433AA88;
        gg.fill(cx - br, cy - br, cx - br + bo, cy - br + 5, bc);
        gg.fill(cx - br, cy - br, cx - br + 5, cy - br + bo, bc);
        gg.fill(cx + br - bo, cy - br, cx + br, cy - br + 5, bc);
        gg.fill(cx + br - 5, cy - br, cx + br, cy - br + bo, bc);
        gg.fill(cx - br, cy + br - 5, cx - br + bo, cy + br, bc);
        gg.fill(cx - br, cy + br - bo, cx - br + 5, cy + br, bc);
        gg.fill(cx + br - bo, cy + br - 5, cx + br, cy + br, bc);
        gg.fill(cx + br - 5, cy + br - bo, cx + br, cy + br, bc);
    }

    private static void drawCooldownRing(GuiGraphics gg, int w, int h, long gameTime) {
        if (!cooldownActive(gameTime)) {
            return;
        }
        float p = cooldownProgress(gameTime);
        int cx = w / 2;
        int cy = h / 2;
        int radius = 13;
        int color = 0x99CCAA33;
        int total = 48;
        int filled = (int) (total * p);
        for (int i = 0; i < total; i++) {
            double a = (i / (double) total) * Math.PI * 2 - Math.PI / 2;
            int px = cx + (int) Math.round(Math.cos(a) * radius);
            int py = cy + (int) Math.round(Math.sin(a) * radius);
            int col = i < filled ? color : 0x33222222;
            gg.fill(px, py, px + 1, py + 1, col);
        }
    }

    private static void drawLockFlash(GuiGraphics gg, int w, int h, float offX, float offY) {
        Minecraft mc = Minecraft.getInstance();
        long gameTime = mc.level != null ? mc.level.getGameTime() : 0L;
        long elapsed = gameTime - lockFlashGameTime;
        if (elapsed < 0 || elapsed > LOCK_FLASH_TICKS) {
            return;
        }
        float p = 1f - (elapsed / (float) LOCK_FLASH_TICKS);
        int cx = Math.round(w * 0.5f + offX);
        int cy = Math.round(h * 0.5f + offY);
        int ringR = (int) (4 + (1f - p) * 14);
        int thick = 1;
        int col = (((int) (p * 140)) << 24) | 0xAAFFCC;
        gg.fill(cx - ringR, cy - ringR, cx + ringR, cy - ringR + thick, col);
        gg.fill(cx - ringR, cy + ringR - thick, cx + ringR, cy + ringR, col);
        gg.fill(cx - ringR, cy - ringR, cx - ringR + thick, cy + ringR, col);
        gg.fill(cx + ringR - thick, cy - ringR, cx + ringR, cy + ringR, col);

        Component msg = Component.translatable("create_radar_mobile_radars.overlay.lock_acquired");
        int tw = mc.font.width(msg);
        int textA = ((int) (p * 160)) << 24;
        gg.drawString(mc.font, msg, cx - tw / 2, cy + ringR + 3, textA | 0x99DDAA, false);
    }

    private static void drawBlockedFlash(GuiGraphics gg, int w, int h, long gameTime) {
        long elapsed = gameTime - blockedFlashGameTime;
        if (elapsed < 0 || elapsed > BLOCKED_FLASH_TICKS) {
            return;
        }
        float p = 1f - (elapsed / (float) BLOCKED_FLASH_TICKS);
        int a = (int) (p * 60);
        gg.fill(0, 0, w, h, (a << 24) | 0xFF2200);

        Minecraft mc = Minecraft.getInstance();
        Component msg = Component.translatable("create_radar_mobile_radars.overlay.cooldown_blocked");
        int tw = mc.font.width(msg);
        int color = (((int) (p * 255)) << 24) | 0xFFAA88;
        gg.drawString(mc.font, msg, w / 2 - tw / 2, h / 2 + 28, color, false);
    }

    private static void drawHudInfo(GuiGraphics gg, Minecraft mc, Player player, int w, int h) {
        Font font = mc.font;
        int tracks = snapshot.tracks() == null ? 0 : snapshot.tracks().size();
        String status = snapshot.canRenderLiveRadar() ? "LIVE" : (snapshot.antennaPresent() ? "RANGE" : "OFFLINE");
        String zoom = zoomCurrent < 0.99f
                ? String.format(java.util.Locale.ROOT, " %sx", String.format(java.util.Locale.ROOT, "%.1f", 1.0f / zoomCurrent))
                : "";

        Component header = Component.translatable(
                "create_radar_mobile_radars.overlay.radar_vision_header",
                status,
                aimMode.displayName(),
                tracks + zoom);
        gg.drawString(font, header, 6, 6, 0xCC33FFBB, false);

        RadarTrack hovered = RadarAimController.hoveredTrack();
        if (hovered != null) {
            Vec3 eye = player.getEyePosition(0);
            double d = RadarAimController.hoveredDistance(eye);
            if (d < 0) {
                d = eye.distanceTo(hovered.position());
            }
            Component hoverLine = Component.translatable(
                    "create_radar_mobile_radars.overlay.hover_info",
                    RadarTrackLabels.displayName(mc, hovered),
                    String.format(java.util.Locale.ROOT, "%.1fm", d));
            int hw = font.width(hoverLine);
            int hy = Math.round(h * 0.5f + crosshairOffsetY) + 12;
            gg.drawString(font, hoverLine, w / 2 - hw / 2, hy, 0xBBEEDD88, false);
        }

        if (!snapshot.canRenderLiveRadar()) {
            Component msg =
                    snapshot.antennaPresent() && snapshot.radarPos() != null
                            ? Component.translatable(
                                    "create_radar_mobile_radars.screen.portable_radar.out_of_range")
                            : Component.translatable("create_radar.monitor.offline");
            int tw = font.width(msg);
            gg.drawString(font, msg, w / 2 - tw / 2, h / 2, 0xAAFFCCAA, false);
        }

        gg.drawString(
                font,
                Component.translatable("create_radar_mobile_radars.overlay.radar_goggles_controls", aimMode.displayName()),
                6,
                h - 12,
                0x9944AA77,
                false);
    }

}
