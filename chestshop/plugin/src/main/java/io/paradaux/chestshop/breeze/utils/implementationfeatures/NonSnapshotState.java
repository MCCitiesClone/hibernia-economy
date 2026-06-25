package io.paradaux.chestshop.breeze.utils.implementationfeatures;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

import java.util.function.BiFunction;

public class NonSnapshotState {

    public static final BiFunction<Block, Boolean, BlockState> PROVIDER = Block::getState;

}
