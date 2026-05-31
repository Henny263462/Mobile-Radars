package dev.henny.mobile_radars.client;

import com.happysg.radar.block.radar.behavior.IRadar;
import dev.henny.mobile_radars.network.MobileRadarsNetworking;
import dev.henny.mobile_radars.network.PortableHudSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import javax.annotation.Nullable;

import net.neoforged.neoforge.network.PacketDistributor;

/** Handheld HUD similar to {@link com.happysg.radar.block.monitor.MonitorScreen} (flattened-facing). */
public final class PortableRadarScreen extends Screen {

    private final InteractionHand hand;
    private final dev.henny.mobile_radars.util.NetworkFilterBinding.Bound bound;

    private int tick;
    private int uiSize;
    private float uiScale;
    private int left;
    private int top;
    private @Nullable PortableHudSnapshot snapshot;
    private @Nullable String hoveredId;

    private static final float TRACK_POSITION_SCALE = 0.75f;
    private static final int TARGET_UI_PX = 900;


    public PortableRadarScreen(InteractionHand hand, dev.henny.mobile_radars.util.NetworkFilterBinding.Bound bound) {
        super(Component.translatable("create_radar_mobile_radars.screen.portable_radar.title"));
        this.hand = hand;
        this.bound = bound;
    }

    public void applySnapshot(PortableHudSnapshot snap) {
        this.snapshot = snap;
    }

    @Override
    protected void init() {
        super.init();
        requestSync();
        recalcUi();
    }

    @Override
    public void tick() {
        super.tick();
        tick++;
        if (tick % 10 == 0) {
            requestSync();
        }
    }

    private void requestSync() {
        int ho = hand == InteractionHand.OFF_HAND ? 1 : 0;
        PacketDistributor.sendToServer(new MobileRadarsNetworking.PortableSyncRequest(bound.filtererPos(), bound.dimension(), ho));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void resize(Minecraft mc, int w, int h) {
        super.resize(mc, w, h);
        recalcUi();
    }

    private void recalcUi() {
        Minecraft mc = Minecraft.getInstance();
        double s = mc.getWindow().getGuiScale();
        if (s <= 0) s = 1;
        uiSize = (int) Math.round(TARGET_UI_PX / s);
        int max = Math.min(this.width, this.height) - 20;
        uiSize = Mth.clamp(uiSize, 120, Math.max(120, max));
        uiScale = uiSize / 512f;
        left = (this.width - uiSize) / 2;
        top = (this.height - uiSize) / 2;
    }

    @Override
    public void renderBackground(GuiGraphics gg, int mouseX, int mouseY, float partialTicks) {
    }

    private void drawBackdrop(GuiGraphics gg) {
        gg.blit(
                net.minecraft.resources.ResourceLocation.parse("create_radar:textures/gui/monitor_gui.png"),
                left, top,
                0, 0,
                uiSize, uiSize,
                uiSize, uiSize);
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTicks) {
        Minecraft mc = Minecraft.getInstance();

        drawBackdrop(gg);

        if (snapshot == null) {
            gg.drawCenteredString(mc.font,
                    Component.translatable("create_radar_mobile_radars.screen.portable_radar.loading"),
                    width / 2, height / 2 - 4, 0xFFFFFF);
            super.render(gg, mouseX, mouseY, partialTicks);
            return;
        }

        if (!snapshot.antennaPresent() || snapshot.radarPos() == null) {
            gg.drawCenteredString(mc.font,
                    Component.translatable("create_radar.monitor.offline"),
                    width / 2, height / 2 - 4, 0xFFFFFF);
            super.render(gg, mouseX, mouseY, partialTicks);
            return;
        }

        if (!snapshot.inRange()) {
            gg.drawCenteredString(mc.font,
                    Component.translatable("create_radar_mobile_radars.screen.portable_radar.out_of_range"),
                    width / 2, height / 2 - 4, 0xFFFFFF);
            super.render(gg, mouseX, mouseY, partialTicks);
            return;
        }

        if (!snapshot.sweepRunning()) {
            gg.drawCenteredString(mc.font,
                    Component.translatable("create_radar.monitor.offline"),
                    width / 2, height / 2 - 4, 0xFFFFFF);
            super.render(gg, mouseX, mouseY, partialTicks);
            return;
        }

        if (mc.level == null || !(mc.level.getBlockEntity(snapshot.radarPos()) instanceof IRadar radar)) {
            gg.drawCenteredString(mc.font,
                    Component.translatable("create_radar.monitor.offline"),
                    width / 2, height / 2 - 4, 0xFFFFFF);
            super.render(gg, mouseX, mouseY, partialTicks);
            return;
        }

        PortableRadarHud.drawTracks(gg, radar, snapshot, hoveredId, left, top, uiSize, uiScale, mc.font,
                TRACK_POSITION_SCALE, Direction.NORTH);

        gg.drawCenteredString(mc.font,
                Component.translatable("create_radar.monitor.click_hint"),
                width / 2,
                top + uiSize + 6,
                0xA0A0A0);

        updateHover(mouseX, mouseY, radar, snapshot);

        super.render(gg, mouseX, mouseY, partialTicks);
    }

    private void updateHover(int mouseX, int mouseY, IRadar radar, PortableHudSnapshot snap) {
        hoveredId = PortableRadarHud.pickHoveredTrack(
                mouseX, mouseY, radar, snap, left, top, uiSize, uiScale, TRACK_POSITION_SCALE, Direction.NORTH);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0)
            return super.mouseClicked(mouseX, mouseY, button);

        if (snapshot == null || !snapshot.canRenderLiveRadar())
            return super.mouseClicked(mouseX, mouseY, button);

        if (mouseX < left || mouseX >= left + uiSize || mouseY < top || mouseY >= top + uiSize)
            return super.mouseClicked(mouseX, mouseY, button);

        int ho = hand == InteractionHand.OFF_HAND ? 1 : 0;
        @Nullable String id = hoveredId;
        PacketDistributor.sendToServer(new MobileRadarsNetworking.PortableSelect(bound.filtererPos(), bound.dimension(), ho, id));
        return true;
    }
}
