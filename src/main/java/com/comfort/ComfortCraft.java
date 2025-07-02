package com.comfort;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;




// The value here should match an entry in the META-INF/mods.toml file
@Mod(ComfortCraft.MOD_ID)
public class ComfortCraft {
    public static final String MOD_ID = "comfortcraft";
    private static final Logger LOGGER = LogUtils.getLogger();

    public ComfortCraft() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);

        LOGGER.info("ComfortCraft: Mod initialized!");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("ComfortCraft: Common setup complete!");
    }
    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ForgeEvents {
        @SubscribeEvent
        public static void onBlockBroken(BlockEvent.BreakEvent event) {
            // Ensure this logic only runs on the server side to prevent desyncs
            if (!event.getLevel().isClientSide()) {
                LevelAccessor level = event.getLevel();
                BlockPos pos = event.getPos();
                BlockState state = event.getState();
                net.minecraft.world.entity.player.Player player = event.getPlayer();

                // --- Auto-Replant Crops ---
                if (state.getBlock() instanceof CropBlock cropBlock) {
                    // Check if the crop is fully grown
                    IntegerProperty ageProperty = null;
                    for (var property : state.getProperties()) {
                        if (property instanceof IntegerProperty intProp && property.getName().equals("age")) {
                            ageProperty = intProp;
                            break;
                        }
                    }
                    if (ageProperty != null && state.getValue(ageProperty) == cropBlock.getMaxAge()) {
                        // Find the seed item corresponding to this crop
                        ItemStack seedItem = cropBlock.getCloneItemStack(level, pos, state);

                        // Check if the player has the seed in their inventory
                        if (player != null && player.getInventory().contains(seedItem)) {
                            // Consume one seed from the player's inventory
                            player.getInventory().removeItem(player.getInventory().findSlotMatchingItem(seedItem), 1);

                            // Replant the crop
                            level.setBlock(pos, cropBlock.defaultBlockState(), 3); // 3 for BlockFlags.BLOCK_UPDATE + BlockFlags.NOTIFY_NEIGHBORS
                            LOGGER.debug("ComfortCraft: Auto-replanted crop at " + pos);
                            event.setCanceled(true); // Cancel the original break event to prevent item drops from original block
                                                    // and allow our replant to take effect cleanly.
                        }
                    }
                }
                // --- Auto-Replant Saplings ---
                else if (state.getBlock() instanceof SaplingBlock) {
                    // Check if the block below is dirt or grass (suitable for saplings)
                    BlockState groundState = level.getBlockState(pos.below());
                    if (groundState.is(Blocks.DIRT) || groundState.is(Blocks.GRASS_BLOCK)) {
                        // Find the sapling item corresponding to this sapling block
                        ItemStack saplingItem = new ItemStack(state.getBlock()); // Create an ItemStack from the broken sapling block

                        // Check if the player has the sapling in their inventory
                        if (player != null && player.getInventory().contains(saplingItem)) {
                            // Consume one sapling from the player's inventory
                            player.getInventory().removeItem(player.getInventory().findSlotMatchingItem(saplingItem), 1);

                            // Replant the sapling
                            level.setBlock(pos, state.getBlock().defaultBlockState(), 3);
                            LOGGER.debug("ComfortCraft: Auto-replanted sapling at " + pos);
                            event.setCanceled(true); // Cancel the original break event
                        }
                    }
                }
            }
        }
    }
}