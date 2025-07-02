// src/main/java/com/comfortcraft/comfortcraft/features/InstantPathCreation.java
package com.comfort;

import net.minecraft.core.BlockPos; // For block positions
import net.minecraft.world.InteractionHand; // For checking which hand is used
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item; // For checking item type
import net.minecraft.world.item.ShovelItem; // NEW IMPORT: For checking if item is an instance of ShovelItem
import net.minecraft.world.level.Level; // For accessing the world
import net.minecraft.world.level.block.Blocks; // For block references (DIRT, GRASS_BLOCK, DIRT_PATH)
import net.minecraft.world.level.block.state.BlockState; // For block states
import net.minecraftforge.event.entity.player.PlayerInteractEvent; // Event for player interaction
import net.minecraftforge.eventbus.api.SubscribeEvent; // Annotation for event subscribers
import net.minecraftforge.fml.common.Mod; // Annotation for mod event subscribers

@Mod.EventBusSubscriber(modid = ComfortCraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class InstantPathCreation {

    /**
     * This method listens for when a player right-clicks on a block.
     * If the player is holding a shovel (any modded or vanilla shovel), is SNEAKING,
     * and right-clicks on a dirt or grass block, it will attempt to convert a small area
     * around the clicked block into dirt paths.
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // Ensure this logic only runs on the server side, is a successful interaction, and player is sneaking
        // Get the entity from the event and check if it's a Player
        if (event.getLevel().isClientSide || event.getHand() != InteractionHand.MAIN_HAND || event.isCanceled()) {
            return; // Exit if client-side, off-hand, or cancelled
        }

        if (!(event.getEntity() instanceof Player)) {
            return; // Exit if not a player
        }

        Player player = (Player) event.getEntity();
        if (!player.isShiftKeyDown()) {
            return; // Exit if player is not sneaking (shift)
        }

        Level level = event.getLevel(); // Get the world
        BlockPos clickedPos = event.getPos(); // Get the position of the clicked block
        BlockState clickedState = level.getBlockState(clickedPos); // Get the state of the clicked block
        Item heldItem = event.getItemStack().getItem(); // Get the item the player is holding

        // Check if the player is holding any item that is an instance of ShovelItem
        boolean isShovel = heldItem instanceof ShovelItem;

        // Check if the clicked block is dirt or grass AND the player is holding a shovel
        if (isShovel && (clickedState.is(Blocks.DIRT) || clickedState.is(Blocks.GRASS_BLOCK))) {
            ComfortCraft.LOGGER.debug("ComfortCraft: Player sneaking and right-clicked with shovel on dirt/grass at " + clickedPos + ". Attempting instant path creation.");

            // Define the radius for path creation (e.g., 1 for a 3x3 area)
            int radius = 1;

            // Iterate through a square area around the clicked block
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos currentPos = clickedPos.offset(x, 0, z); // Get the position in the current iteration
                    BlockState currentState = level.getBlockState(currentPos); // Get the state of that block

                    // Check if the current block is dirt or grass AND the block above is air (to prevent pathing under blocks)
                    if ((currentState.is(Blocks.DIRT) || currentState.is(Blocks.GRASS_BLOCK)) &&
                        level.getBlockState(currentPos.above()).isAir()) {
                        // Set the block to a dirt path
                        level.setBlock(currentPos, Blocks.DIRT_PATH.defaultBlockState(), 3); // 3 for BlockFlags.BLOCK_UPDATE + BlockFlags.NOTIFY_NEIGHBORS
                        ComfortCraft.LOGGER.debug("ComfortCraft: Converted " + currentPos + " to dirt path.");
                    }
                }
            }
            // Mark the event as handled to prevent vanilla shovel behavior (single block path)
            event.setCanceled(true);
            // Swing the player's arm visually (optional, but good for feedback)
            player.swing(event.getHand()); // Use the 'player' variable obtained from casting
        }
    }
}
