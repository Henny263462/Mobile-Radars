package dev.henny.mobile_radars.item;

import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlock;
import dev.henny.mobile_radars.util.ClientHooks;
import dev.henny.mobile_radars.util.GogglesState;
import dev.henny.mobile_radars.util.NetworkFilterBinding;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Gekoppelte Radar-Brille: Rechtsklick klappt die Optik vor die Augen, erneuter Rechtsklick löst das Radar-Ziel.
 */
public class RadarGogglesItem extends Item {

    public RadarGogglesItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(
            ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, lines, tooltipFlag);
        NetworkFilterBinding.Bound bound = NetworkFilterBinding.read(stack);
        if (bound != null) {
            lines.add(
                    Component.translatable(
                                    "item.create_radar_mobile_radars.radar_goggles.tooltip_bound",
                                    bound.filtererPos().getX(),
                                    bound.filtererPos().getY(),
                                    bound.filtererPos().getZ())
                            .withStyle(ChatFormatting.GRAY));
            String stateKey = GogglesState.isDeployed(stack)
                    ? "item.create_radar_mobile_radars.radar_goggles.tooltip_deployed"
                    : "item.create_radar_mobile_radars.radar_goggles.tooltip_stowed";
            lines.add(Component.translatable(stateKey)
                    .withStyle(GogglesState.isDeployed(stack) ? ChatFormatting.GREEN : ChatFormatting.DARK_AQUA));
        } else {
            lines.add(Component.translatable("item.create_radar_mobile_radars.radar_goggles.tooltip_unbound")
                    .withStyle(ChatFormatting.DARK_GRAY));
            lines.add(Component.translatable("item.create_radar_mobile_radars.radar_goggles.tooltip_help")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.isShiftKeyDown()) {
            if (GogglesState.isDeployed(stack)) {
                GogglesState.setDeployed(stack, false);
                if (level.isClientSide()) {
                    ClientHooks.gogglesStow(player, hand, stack);
                }
                if (!level.isClientSide()) {
                    player.displayClientMessage(
                            Component.translatable("create_radar_mobile_radars.message.goggles_stowed")
                                    .withStyle(ChatFormatting.GRAY),
                            true);
                }
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
            }
            if (!level.isClientSide()) {
                GogglesState.setDeployed(stack, false);
                NetworkFilterBinding.clear(stack);
                player.displayClientMessage(
                        Component.translatable("create_radar_mobile_radars.message.binding_cleared")
                                .withStyle(ChatFormatting.YELLOW),
                        true);
            }
            if (level.isClientSide()) {
                ClientHooks.gogglesUnbind(player);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        NetworkFilterBinding.Bound bound = NetworkFilterBinding.read(stack);
        if (bound == null) {
            if (!level.isClientSide()) {
                player.displayClientMessage(
                        Component.translatable("create_radar_mobile_radars.message.tablet_bind_filter_first")
                                .withStyle(ChatFormatting.RED),
                        true);
            }
            return InteractionResultHolder.fail(stack);
        }

        if (!GogglesState.isDeployed(stack)) {
            GogglesState.setDeployed(stack, true);
            if (!level.isClientSide()) {
                player.displayClientMessage(
                        Component.translatable("create_radar_mobile_radars.message.goggles_deployed")
                                .withStyle(ChatFormatting.GREEN),
                        true);
            }
            if (level.isClientSide()) {
                ClientHooks.gogglesDeploy(player, hand, stack);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        if (level.isClientSide()) {
            ClientHooks.gogglesClearTarget(player, hand, stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public net.minecraft.world.InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (player == null) {
            return net.minecraft.world.InteractionResult.FAIL;
        }
        ItemStack stack = ctx.getItemInHand();

        if (!(level.getBlockState(ctx.getClickedPos()).getBlock() instanceof NetworkFiltererBlock)) {
            return net.minecraft.world.InteractionResult.PASS;
        }

        if (!level.isClientSide()) {
            GogglesState.setDeployed(stack, false);
            NetworkFilterBinding.bind(stack, level.dimension(), ctx.getClickedPos());
            player.displayClientMessage(
                    Component.translatable("create_radar_mobile_radars.message.goggles_bound")
                            .withStyle(ChatFormatting.GREEN),
                    true);
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide());
    }
}
