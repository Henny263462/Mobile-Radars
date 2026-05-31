package dev.henny.mobile_radars.network;

import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.behavior.networks.config.DetectionConfig;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import com.happysg.radar.block.radar.behavior.IRadar;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import dev.henny.mobile_radars.cfg.MobileRadarsConfig;
import dev.henny.mobile_radars.item.MobileRadarTabletItem;
import dev.henny.mobile_radars.item.RadarGogglesItem;
import dev.henny.mobile_radars.util.ClientHooks;
import dev.henny.mobile_radars.util.GogglesState;
import dev.henny.mobile_radars.util.NetworkFilterBinding;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static dev.henny.mobile_radars.CreateRadarMobileRadars.MODID;

public final class MobileRadarsNetworking {

    private MobileRadarsNetworking() {
    }

    private static ItemStack portableItemStack(ServerPlayer player, int handOrdinal) {
        InteractionHand hand = handOrdinal == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() instanceof MobileRadarTabletItem) {
            return stack;
        }
        if (stack.getItem() instanceof RadarGogglesItem && GogglesState.isDeployed(stack)) {
            return stack;
        }
        return ItemStack.EMPTY;
    }

    static PortableHudSnapshot computeSnapshot(ServerPlayer player, ServerLevel level, BlockPos filterPos) {
        NetworkData.Group group = NetworkData.get(level).getGroup(level.dimension(), filterPos);
        if (group == null || group.radarPos == null) {
            return PortableHudSnapshot.emptyOffline();
        }

        @Nullable IRadar radar = level.getBlockEntity(group.radarPos) instanceof IRadar ir ? ir : null;

        Vec3 antenna = PhysicsHandler.getWorldVec(level, group.radarPos);
        double limit = MobileRadarsConfig.portableMaxRangeBlocks();
        boolean inRange = player.position().distanceTo(antenna) <= limit;

        CompoundTag detection = group.detectionTag == null ? new CompoundTag() : group.detectionTag.copy();

        boolean sweepRunning = radar != null && radar.isRunning();

        boolean antennaPresent = true;

        List<RadarTrack> tracks;
        if (inRange && sweepRunning && radar != null) {
            DetectionConfig dc = DetectionConfig.fromTag(detection);
            tracks = radar.getTracks().stream().filter(dc::test).toList();
        } else {
            tracks = List.of();
        }

        @Nullable String selected = (inRange && sweepRunning && radar != null) ? group.selectedTargetId : null;

        return new PortableHudSnapshot(antennaPresent, inRange, sweepRunning, group.radarPos, detection, tracks, selected);
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(PortableSyncRequest.TYPE, PortableSyncRequest.STREAM_CODEC, PortableSyncRequest::handle);
        registrar.playToClient(PortableSyncReply.TYPE, PortableSyncReply.STREAM_CODEC, PortableSyncReply::handle);
        registrar.playToServer(PortableSelect.TYPE, PortableSelect.STREAM_CODEC, PortableSelect::handle);
    }

    public record PortableSyncRequest(BlockPos filterPos, ResourceKey<net.minecraft.world.level.Level> dimension, int handOrdinal)
            implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<PortableSyncRequest> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MODID, "portable_sync"));

        public static final StreamCodec<RegistryFriendlyByteBuf, PortableSyncRequest> STREAM_CODEC =
                StreamCodec.ofMember(PortableSyncRequest::encode, PortableSyncRequest::decode);

        private void encode(RegistryFriendlyByteBuf buf) {
            buf.writeBlockPos(filterPos);
            buf.writeResourceLocation(dimension.location());
            buf.writeByte(handOrdinal);
        }

        private static PortableSyncRequest decode(RegistryFriendlyByteBuf buf) {
            BlockPos fp = buf.readBlockPos();
            ResourceLocation rl = buf.readResourceLocation();
            ResourceKey<net.minecraft.world.level.Level> key = ResourceKey.create(Registries.DIMENSION, rl);
            return new PortableSyncRequest(fp, key, buf.readUnsignedByte());
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void handle(PortableSyncRequest pkt, IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                if (!(ctx.player() instanceof ServerPlayer player))
                    return;
                ServerLevel level = player.serverLevel();
                if (!level.dimension().equals(pkt.dimension())) {
                    PacketDistributor.sendToPlayer(player, PortableSyncReply.empty());
                    return;
                }
                ItemStack stack = portableItemStack(player, pkt.handOrdinal());
                if (stack.isEmpty()) {
                    PacketDistributor.sendToPlayer(player, PortableSyncReply.empty());
                    return;
                }
                NetworkFilterBinding.Bound binding = NetworkFilterBinding.read(stack);
                if (binding == null || !binding.filtererPos().equals(pkt.filterPos()) || !binding.dimension().equals(pkt.dimension())) {
                    PacketDistributor.sendToPlayer(player, PortableSyncReply.empty());
                    return;
                }
                PortableHudSnapshot snap = computeSnapshot(player, level, pkt.filterPos());
                PacketDistributor.sendToPlayer(player, new PortableSyncReply(snap));
            });
        }

    }

    public record PortableSyncReply(PortableHudSnapshot snapshot) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<PortableSyncReply> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MODID, "portable_sync_reply"));

        public static final StreamCodec<RegistryFriendlyByteBuf, PortableSyncReply> STREAM_CODEC =
                StreamCodec.ofMember(PortableSyncReply::encode, PortableSyncReply::decode);

        static PortableSyncReply empty() {
            return new PortableSyncReply(PortableHudSnapshot.emptyOffline());
        }

        private void encode(RegistryFriendlyByteBuf buf) {
            PortableHudCodec.writeSnapshot(buf, snapshot);
        }

        private static PortableSyncReply decode(RegistryFriendlyByteBuf buf) {
            return new PortableSyncReply(PortableHudCodec.readSnapshot(buf));
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void handle(PortableSyncReply pkt, IPayloadContext ctx) {
            ctx.enqueueWork(() -> ClientHooks.applyPortableSnapshot(pkt.snapshot()));
        }
    }

    public record PortableSelect(BlockPos filterPos, ResourceKey<net.minecraft.world.level.Level> dimension, int handOrdinal, @Nullable String trackId)
            implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<PortableSelect> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MODID, "portable_select"));

        public static final StreamCodec<RegistryFriendlyByteBuf, PortableSelect> STREAM_CODEC =
                StreamCodec.ofMember(PortableSelect::encode, PortableSelect::decode);

        private void encode(RegistryFriendlyByteBuf buf) {
            buf.writeBlockPos(filterPos);
            buf.writeResourceLocation(dimension.location());
            buf.writeByte(handOrdinal);
            buf.writeBoolean(trackId != null);
            if (trackId != null)
                buf.writeUtf(trackId);
        }

        private static PortableSelect decode(RegistryFriendlyByteBuf buf) {
            BlockPos fp = buf.readBlockPos();
            ResourceKey<net.minecraft.world.level.Level> key = ResourceKey.create(Registries.DIMENSION, buf.readResourceLocation());
            int h = buf.readUnsignedByte();
            String id = buf.readBoolean() ? buf.readUtf() : null;
            return new PortableSelect(fp, key, h, id);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void handle(PortableSelect pkt, IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                if (!(ctx.player() instanceof ServerPlayer player))
                    return;
                ServerLevel level = player.serverLevel();
                if (!level.dimension().equals(pkt.dimension()))
                    return;

                ItemStack stack = portableItemStack(player, pkt.handOrdinal());
                if (stack.isEmpty()) {
                    return;
                }
                NetworkFilterBinding.Bound binding = NetworkFilterBinding.read(stack);
                if (binding == null || !binding.filtererPos().equals(pkt.filterPos()) || !binding.dimension().equals(pkt.dimension()))
                    return;

                PortableHudSnapshot snap = computeSnapshot(player, level, pkt.filterPos());
                if (!snap.canRenderLiveRadar())
                    return;

                if (!(level.getBlockEntity(pkt.filterPos()) instanceof NetworkFiltererBlockEntity filterer))
                    return;

                if (pkt.trackId() == null) {
                    filterer.receiveSelectedTargetFromMonitor(null, Collections.emptyList());
                } else {
                    RadarTrack chosen = resolveTrack(pkt.trackId(), snap.tracks());
                    filterer.receiveSelectedTargetFromMonitor(chosen, Collections.emptyList());
                }
                PacketDistributor.sendToPlayer(player, new PortableSyncReply(computeSnapshot(player, level, pkt.filterPos())));
            });
        }

        private static RadarTrack resolveTrack(@Nullable String id, List<RadarTrack> pool) {
            if (id == null) {
                return null;
            }
            for (RadarTrack t : pool) {
                if (id.equals(t.id()) || id.equals(t.getId())) {
                    return t;
                }
            }
            return null;
        }
    }

    private static final class PortableHudCodec {

        static void writeSnapshot(RegistryFriendlyByteBuf buf, PortableHudSnapshot s) {
            buf.writeBoolean(s.antennaPresent());
            buf.writeBoolean(s.inRange());
            buf.writeBoolean(s.sweepRunning());
            buf.writeBoolean(s.radarPos() != null);
            if (s.radarPos() != null) {
                buf.writeBlockPos(s.radarPos());
            }
            buf.writeNbt(s.detectionTag());
            buf.writeVarInt(s.tracks().size());
            for (RadarTrack t : s.tracks()) {
                buf.writeNbt(t.serializeNBT());
            }
            buf.writeBoolean(s.selectedId() != null);
            if (s.selectedId() != null) {
                buf.writeUtf(s.selectedId());
            }
        }

        static PortableHudSnapshot readSnapshot(RegistryFriendlyByteBuf buf) {
            boolean antenna = buf.readBoolean();
            boolean inRange = buf.readBoolean();
            boolean sweep = buf.readBoolean();
            BlockPos rp = buf.readBoolean() ? buf.readBlockPos() : null;
            CompoundTag det = buf.readNbt();
            if (det == null)
                det = new CompoundTag();
            int sz = buf.readVarInt();
            List<RadarTrack> tracks = new ArrayList<>(sz);
            for (int i = 0; i < sz; i++) {
                CompoundTag tag = buf.readNbt();
                if (tag != null) {
                    tracks.add(RadarTrack.deserializeNBT(tag));
                }
            }
            String sel = buf.readBoolean() ? buf.readUtf() : null;
            return new PortableHudSnapshot(antenna, inRange, sweep, rp, det, tracks, sel);
        }

        private PortableHudCodec() {
        }
    }
}
