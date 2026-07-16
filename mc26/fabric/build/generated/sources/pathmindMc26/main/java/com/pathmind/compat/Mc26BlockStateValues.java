package com.pathmind.compat;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** Adapts the 26.x streamed block-state values to Pathmind's stable map view. */
public final class Mc26BlockStateValues {
    private Mc26BlockStateValues() {
    }

    public static Map<Property<?>, Comparable<?>> asMap(BlockState blockState) {
        Map<Property<?>, Comparable<?>> values = new LinkedHashMap<>();
        blockState.getValues().forEach(value -> values.put(value.property(), value.value()));
        return values;
    }
}
