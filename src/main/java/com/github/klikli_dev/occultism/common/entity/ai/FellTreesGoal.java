/*
 * MIT License
 *
 * Copyright 2020 klikli-dev
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT
 * OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package com.github.klikli_dev.occultism.common.entity.ai;

import com.github.klikli_dev.occultism.Occultism;
import com.github.klikli_dev.occultism.common.entity.spirit.SpiritEntity;
import com.github.klikli_dev.occultism.network.MessageSelectBlock;
import com.github.klikli_dev.occultism.network.OccultismPackets;
import com.github.klikli_dev.occultism.util.Math3DUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;

import java.util.*;
import java.util.stream.Collectors;

public class FellTreesGoal extends Goal {
    //region Fields
    protected final SpiritEntity entity;
    protected final BlockSorter targetSorter;
    protected BlockPos targetBlock = null;
    protected BlockPos moveTarget = null;
    protected int breakingTime;
    protected int previousBreakProgress;
    //endregion Fields

    //region Initialization
    public FellTreesGoal(SpiritEntity entity) {
        this.entity = entity;
        this.targetSorter = new BlockSorter(entity);
        this.setFlags(EnumSet.of(Flag.MOVE));
    }
    //endregion Initialization

    //region Overrides
    @Override
    public boolean canUse() {
        if (!this.entity.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) {
            return false; //if already holding an item we need to first store it.
        }
        this.resetTarget();
        return this.targetBlock != null;
    }

    @Override
    public boolean canContinueToUse() {
        //only continue execution if a tree is available and entity is not carrying anything.
        return this.targetBlock != null && this.entity.getItemInHand(InteractionHand.MAIN_HAND).isEmpty();
    }

    public void stop() {
        this.entity.getNavigation().stop();
        this.targetBlock = null;
        this.moveTarget = null;
        this.resetTarget();
    }

    @Override
    public void tick() {
        if (this.targetBlock != null) {

            this.entity.getNavigation().moveTo(
                    this.entity.getNavigation().createPath(this.moveTarget, 0), 1.0f);

            if (Occultism.DEBUG.debugAI) {
                OccultismPackets.sendToTracking(this.entity, new MessageSelectBlock(this.targetBlock, 5000, 0xffffff));
                OccultismPackets.sendToTracking(this.entity, new MessageSelectBlock(this.moveTarget, 5000, 0x00ff00));
            }

            if (isLog(this.entity.level, this.targetBlock)) {
                double distance = this.entity.position().distanceTo(Math3DUtil.center(this.moveTarget));
                if (distance < 2.5F) {
                    //start breaking when close
                    if (distance < 1F) {
                        //Stop moving if very close
                        this.entity.setDeltaMovement(0, 0, 0);
                        this.entity.getNavigation().stop();
                    }

                    this.updateBreakBlock();
                }
            } else {
                this.stop();
            }
        }
    }
    //endregion Overrides

    //region Static Methods
    public static final boolean isLog(Level level, BlockPos pos) {
        return BlockTags.LOGS.contains(level.getBlockState(pos).getBlock());
    }

    public static final boolean isLeaf(Level level, BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        return block instanceof LeavesBlock || BlockTags.LEAVES.contains(block);
    }
    //endregion Static Methods

    //region Methods
    public void updateBreakBlock() {
        this.breakingTime++;
        this.entity.swing(InteractionHand.MAIN_HAND);
        int i = (int) ((float) this.breakingTime / 160.0F * 10.0F);
        if (this.breakingTime % 10 == 0) {
            this.entity.playSound(SoundEvents.WOOD_HIT, 1, 1);
            this.entity.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1, 0.5F);
        }
        if (i != this.previousBreakProgress) {
            this.entity.level.destroyBlockProgress(this.entity.getId(), this.targetBlock, i);
            this.previousBreakProgress = i;
        }
        if (this.breakingTime == 160) {
            this.entity.playSound(SoundEvents.WOOD_BREAK, 1, 1);
            this.breakingTime = 0;
            this.previousBreakProgress = -1;
            this.fellTree();
            this.targetBlock = null;
            this.stop();
        }

    }

    private void resetTarget() {
        Level level = this.entity.level;
        List<BlockPos> allBlocks = new ArrayList<>();
        BlockPos workAreaCenter = this.entity.getWorkAreaCenter();

        //get work area, but only half height, we don't need full.
        int workAreaSize = this.entity.getWorkAreaSize().getValue();
        List<BlockPos> searchBlocks = BlockPos.betweenClosedStream(
                workAreaCenter.offset(-workAreaSize, -workAreaSize / 2, -workAreaSize),
                workAreaCenter.offset(workAreaSize, workAreaSize / 2, workAreaSize)).map(BlockPos::immutable).collect(
                Collectors.toList());
        for (BlockPos pos : searchBlocks) {
            if (isLog(level, pos)) {

                //find top of tree
                BlockPos topOfTree = new BlockPos(pos);
                while (!level.isEmptyBlock(topOfTree.above()) && topOfTree.getY() < level.getHeight()) {
                    topOfTree = topOfTree.above();
                }

                //find the stump of the tree
                if (isLeaf(level, topOfTree)) {
                    BlockPos logPos = this.getStump(topOfTree);
                    if (isLog(level, logPos))
                        allBlocks.add(logPos);
                }
            }
        }
        //set closest log as target
        if (!allBlocks.isEmpty()) {
            allBlocks.sort(this.targetSorter);
            this.targetBlock = allBlocks.get(0);

            //Find a nearby empty block to move to
            this.moveTarget = null;
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                BlockPos pos = this.targetBlock.relative(facing);
                if (this.entity.level.isEmptyBlock(pos)) {
                    this.moveTarget = pos;
                    break;
                }
            }

            //none found -> invalid target
            if (this.moveTarget == null) {
                this.targetBlock = null;
            }
        }
    }

    /**
     * Gets the stump for the given log.
     *
     * @param log the log
     * @return the stump block position.
     */
    private BlockPos getStump(BlockPos log) {
        if (log.getY() > 0) {
            //for all nearby logs and leaves, move one block down and recurse.
            for (BlockPos pos : BlockPos.betweenClosedStream(log.offset(-4, -4, -4), log.offset(4, 0, 4)).map(BlockPos::immutable)
                    .collect(
                            Collectors.toList())) {
                if (isLog(this.entity.level, pos.below()) || isLeaf(this.entity.level, pos.below())) {
                    return this.getStump(pos.below());
                }
            }
        }
        return log;
    }

    private void fellTree() {
        Level level = this.entity.level;
        BlockPos base = new BlockPos(this.targetBlock);
        Queue<BlockPos> blocks = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        blocks.add(base);

        while (!blocks.isEmpty()) {

            BlockPos pos = blocks.remove();
            if (!visited.add(pos)) {
                continue;
            }

            if (!isLog(level, pos)) {
                continue;
            }

            for (Direction facing : Direction.Plane.HORIZONTAL) {
                BlockPos pos2 = pos.relative(facing);
                if (!visited.contains(pos2)) {
                    blocks.add(pos2);
                }
            }

            for (int x = 0; x < 3; x++) {
                for (int z = 0; z < 3; z++) {
                    BlockPos pos2 = pos.offset(-1 + x, 1, -1 + z);
                    if (!visited.contains(pos2)) {
                        blocks.add(pos2);
                    }
                }
            }

            level.destroyBlock(pos, true);
        }

    }

    //endregion Methods

}
