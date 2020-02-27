package com.github.klikli_dev.occultism.common;

import com.github.klikli_dev.occultism.Occultism;
import com.github.klikli_dev.occultism.common.block.CandleBlock;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class OccultismBlocks {

    public static DeferredRegister<Block> BLOCKS = new DeferredRegister(ForgeRegistries.BLOCKS, Occultism.MODID);
    //region Fields
    public static final RegistryObject<Block> CANDLE_WHITE = BLOCKS.register("candle_white", () -> new CandleBlock(
            Block.Properties.create(Material.MISCELLANEOUS).sound(SoundType.CLOTH).doesNotBlockMovement()
                    .hardnessAndResistance(0.1f, 0).lightValue(12)));
    //endregion Fields

    public static boolean hasCustomItemBlock(Block block){
        return false;
    }
}
