package com.pathmind.nodes;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

final class RuntimeParameterData {
    BlockPos targetBlockPos;
    Vec3d targetVector;
    Entity targetEntity;
    Item targetItem;
    String targetBlockId;
    List<String> targetBlockIds;
    String targetPlayerName;
    String targetItemId;
    String targetTradeKey;
    String targetEntityId;
    String message;
    Double durationSeconds;
    Integer slotIndex;
    SlotSelectionType slotSelectionType;
    String schematicName;
    Float resolvedYaw;
    Float resolvedPitch;
    Double resolvedLookDistance;
    String resolvedButtonValue;
    boolean resolvedButtonIsMouse;
}
