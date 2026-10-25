package com.desastres.item;

import com.desastres.config.DisasterConfig;
import com.desastres.disaster.Fx;
import com.desastres.disaster.LaserBeams;
import com.desastres.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Pistola laser. Clic derecho = disparar. Agachado + clic derecho = cambiar el color del rayo (verde / azul).
 * La energia es la durabilidad: cada disparo gasta 1. Con la energia a 0 se recarga sola consumiendo una
 * Celda de Energia del inventario. En creativo no consume energia.
 */
public class LaserPistolItem extends Item {
    private static final String TAG_BLUE = "Blue";
    private static final int COOLDOWN_TICKS = 8;

    public LaserPistolItem(Properties properties) {
        super(properties);
    }

    private static boolean isBlue(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getBoolean(TAG_BLUE);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(level instanceof ServerLevel server)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        if (player.isShiftKeyDown()) {
            boolean blue = !isBlue(stack);
            stack.getOrCreateTag().putBoolean(TAG_BLUE, blue);
            player.displayClientMessage(Component.translatable(blue
                    ? "message.disasters.laser_blue" : "message.disasters.laser_green"), true);
            return InteractionResultHolder.sidedSuccess(stack, false);
        }

        boolean creative = player.getAbilities().instabuild;
        if (!creative && stack.getDamageValue() >= stack.getMaxDamage()) {
            if (reload(player, stack)) {
                player.displayClientMessage(Component.translatable("message.disasters.laser_reloaded"), true);
                Fx.sound(server, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, 0.8F, 1.6F);
                player.getCooldowns().addCooldown(this, 20);
            } else {
                player.displayClientMessage(Component.translatable("message.disasters.laser_empty"), true);
                Fx.sound(server, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, 0.8F, 1.2F);
                player.getCooldowns().addCooldown(this, 10);
            }
            return InteractionResultHolder.sidedSuccess(stack, false);
        }
        if (!creative) {
            stack.setDamageValue(stack.getDamageValue() + 1);
        }

        double range = DisasterConfig.LASER_PISTOL_RANGE.get();
        float damage = DisasterConfig.LASER_PISTOL_DAMAGE.get().floatValue();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 right = look.cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize();
        Vec3 muzzle = eye.add(look.scale(0.6D)).add(right.scale(0.25D)).add(0.0D, -0.18D, 0.0D);
        // Apunta a donde mira el jugador (la mira), no en paralelo al canon
        Vec3 dir = eye.add(look.scale(range)).subtract(muzzle).normalize();

        LaserBeams.fire(server, player, player, muzzle, dir, range, damage,
                isBlue(stack) ? LaserBeams.BLUE : LaserBeams.GREEN, false, server.damageSources().playerAttack(player));
        Fx.sound(server, muzzle.x, muzzle.y, muzzle.z, SoundEvents.BEACON_POWER_SELECT, 0.9F, 2.0F);
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    private static boolean reload(Player player, ItemStack gun) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(ModItems.ENERGY_CELL.get())) {
                s.shrink(1);
                gun.setDamageValue(0);
                return true;
            }
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int left = stack.getMaxDamage() - stack.getDamageValue();
        tooltip.add(Component.translatable("item.disasters.laser_pistol.energy", left, stack.getMaxDamage())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(isBlue(stack) ? "message.disasters.laser_blue" : "message.disasters.laser_green")
                .withStyle(isBlue(stack) ? ChatFormatting.AQUA : ChatFormatting.GREEN));
        tooltip.add(Component.translatable("item.disasters.laser_pistol.help").withStyle(ChatFormatting.DARK_GRAY));
    }
}
