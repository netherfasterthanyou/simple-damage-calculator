package com.comfort;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraft.world.level.LevelAccessor;


@Mod.EventBusSubscriber(modid = ComfortCraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FasterLeafDecay {

    
    @SubscribeEvent
    public static void onLogBroken(BlockEvent.BreakEvent event) {
        
        if (event.getLevel().isClientSide()) {
            return;
        }

        LevelAccessor level = event.getLevel();
        BlockPos brokenPos = event.getPos();
        BlockState brokenState = event.getState();

        
        if (brokenState.is(BlockTags.LOGS)) {
            ComfortCraft.LOGGER.debug("ComfortCraft: Log broken at " + brokenPos + ". Checking for adjacent leaves to accelerate decay.");

            for (Direction direction : Direction.values()) {
                BlockPos neighborPos = brokenPos.relative(direction);
                BlockState neighborState = level.getBlockState(neighborPos);

                
                if (neighborState.is(BlockTags.LEAVES)) {
                    ComfortCraft.LOGGER.debug("ComfortCraft: Found leaf block at " + neighborPos + ". Scheduling immediate update.");
                    
                    level.scheduleTick(neighborPos, neighborState.getBlock(), 1);
                }
            }
        }
    }
}