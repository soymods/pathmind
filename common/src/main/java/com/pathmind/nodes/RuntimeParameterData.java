package com.pathmind.nodes;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

final class RuntimeParameterData {
    BlockPos targetBlockPos;
    Vec3 targetVector;
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
