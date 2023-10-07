package com.baky.backtobed.item.custom;

import com.mojang.math.Vector3f;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MagicalReturner extends Item {
    // By using a scheduled executor, we can introduce a delay between teleporting the player and the ridden entity.
    // This helps prevent visual glitches or rendering issues that might occur if both are teleported instantly.
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

    public MagicalReturner(Properties properties) {
        super(properties);
    }

    public void appendHoverText(ItemStack itemStack, @Nullable Level level, List<Component> list, TooltipFlag tooltipFlag) {
        super.appendHoverText(itemStack, level, list, tooltipFlag);


        list.add(new TranslatableComponent("magical_returner.info1").withStyle(ChatFormatting.GRAY));
        list.add(new TranslatableComponent("magical_returner.info2").withStyle(ChatFormatting.GRAY));
        list.add(new TextComponent(""));

        if (Screen.hasShiftDown()) {
            list.add(new TranslatableComponent("magical_returner.pet_teleport1").withStyle(ChatFormatting.GREEN));
            list.add(new TranslatableComponent("magical_returner.pet_teleport2").withStyle(ChatFormatting.GREEN));
        } else {
            list.add(new TranslatableComponent("magical_returner.shift_press").withStyle(ChatFormatting.YELLOW));
        }
    }

    private void stopUsingItemWithCooldown(ServerPlayer player) {
        player.stopUsingItem();
        player.getCooldowns().addCooldown(this, 40);
    }

    @Override
    public void onUseTick(Level level, LivingEntity livingEntity, ItemStack itemStack, int i) {
        super.onUseTick(level, livingEntity, itemStack, i);

        if(!(livingEntity instanceof  ServerPlayer player) || level.isClientSide()) {
            return;
        }

        int duration = this.getUseDuration(itemStack) - i;

        if(duration < 40) {
            return;
        }

        if (player.getCommandSenderWorld().dimension() != Level.OVERWORLD) {
            player.sendMessage(new TranslatableComponent("magical_returner.teleport_for_overworld"), player.getUUID());
            stopUsingItemWithCooldown(player);
        }

        BlockPos respawnPos = player.getRespawnPosition();

        if(respawnPos == null) {
            player.sendMessage(new TranslatableComponent("magical_returner.no_respawn_point"), player.getUUID());
            stopUsingItemWithCooldown(player);
            return;
        }

        if(level.getBlockState(respawnPos).getBlock() instanceof BedBlock) {
            double destinationX = respawnPos.getX() + 0.5;
            double destinationY = respawnPos.getY() + 0.6D;
            double destinationZ = respawnPos.getZ() + 0.5;

            // Check if player is riding an entity
            if(player.getVehicle() != null) {
                // Get the entity being ridden
                Entity riddenEntity = player.getVehicle();

                // Stop the player from riding the entity
                player.stopRiding();

                // Delayed teleportation of the ridden entity
                executor.schedule(() -> {
                    assert riddenEntity != null;
                    riddenEntity.teleportTo(destinationX, destinationY, destinationZ);
                }, 100, TimeUnit.MILLISECONDS);

                player.teleportTo(destinationX, destinationY, destinationZ);
            }else {
                player.teleportTo(destinationX, destinationY, destinationZ);
            }

            level.playSound(null, respawnPos, SoundEvents.AMETHYST_CLUSTER_HIT, SoundSource.PLAYERS, 1.0F, 1.0F);

            DustColorTransitionOptions dustColorTransitionOptions = new DustColorTransitionOptions(new Vector3f(0.0f, 1.0f, 1.0f), new Vector3f(1.0f, 0.0f, 1.0f), 1.0f);

            ServerLevel serverLevel = (ServerLevel) level;

            serverLevel.sendParticles(player, dustColorTransitionOptions, true, respawnPos.getX() + 0.5, respawnPos.getY() + 0.6D, respawnPos.getZ() + 0.5, 85, 0.85D, 0.75D, .85D, 0.005D);

            stopUsingItemWithCooldown(player);
        }else {
            player.sendMessage(new TranslatableComponent("magical_returner.no_access_to_bed"), player.getUUID());
            stopUsingItemWithCooldown(player);
        }
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000; // This is how long the player can hold right-click (1 hour)
    }

    @Override
    public @NotNull UseAnim getUseAnimation(@NotNull ItemStack stack) {
        return UseAnim.BOW; // This makes the player perform the bow drawing animation while holding right-click
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level worldIn, Player playerIn, @NotNull InteractionHand handIn) {
        playerIn.startUsingItem(handIn);
        return InteractionResultHolder.consume(playerIn.getItemInHand(handIn));
    }
}
