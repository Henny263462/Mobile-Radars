package dev.henny.mobile_radars.item;

import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlock;
import dev.henny.mobile_radars.util.ClientHooks;
import dev.henny.mobile_radars.util.NetworkFilterBinding;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/** Am Netzwerk-Trunk koppeln; Rechtsklick in der Luft öffnet den tragbaren Radar-Schirm. */
public class MobileRadarTabletItem extends Item {

    public MobileRadarTabletItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, lines, tooltipFlag);
        NetworkFilterBinding.Bound bound = NetworkFilterBinding.read(stack);
        if (bound != null) {
            lines.add(Component.translatable(
                            "item.create_radar_mobile_radars.mobile_radar_tablet.tooltip_bound",
                            bound.filtererPos().getX(),
                            bound.filtererPos().getY(),
                            bound.filtererPos().getZ())
                    .withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("item.create_radar_mobile_radars.mobile_radar_tablet.tooltip_unbound")
                    .withStyle(ChatFormatting.DARK_GRAY));
            lines.add(Component.translatable("item.create_radar_mobile_radars.mobile_radar_tablet.tooltip_help")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (player == null) {
            return InteractionResult.FAIL;
        }
        ItemStack stack = ctx.getItemInHand();

        if (!(level.getBlockState(ctx.getClickedPos()).getBlock() instanceof NetworkFiltererBlock)) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide()) {
            NetworkFilterBinding.bind(stack, level.dimension(), ctx.getClickedPos());
            player.displayClientMessage(
                    Component.translatable("create_radar_mobile_radars.message.tablet_bound").withStyle(ChatFormatting.GREEN),
                    true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                NetworkFilterBinding.clear(stack);
                player.displayClientMessage(
                        Component.translatable("create_radar_mobile_radars.message.binding_cleared")
                                .withStyle(ChatFormatting.YELLOW),
                        true);
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

        if (level.isClientSide()) {
            ClientHooks.openPortableTablet(hand, bound);
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
