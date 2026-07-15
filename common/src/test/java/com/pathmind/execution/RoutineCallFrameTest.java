package com.pathmind.execution;

import com.pathmind.nodes.NodeType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RoutineCallFrameTest {
    private static ExecutionManager.RuntimeVariable amount(String value) {
        return new ExecutionManager.RuntimeVariable(NodeType.PARAM_AMOUNT, Map.of("Amount", value));
    }

    @Test
    void simultaneousInvocationsKeepInputsIndependent() {
        RoutineCallFrame first = new RoutineCallFrame(10, 1, "routine", Map.of("input", amount("1")));
        RoutineCallFrame second = new RoutineCallFrame(11, 1, "routine", Map.of("input", amount("2")));
        Map<Integer, RoutineCallFrame> frames = Map.of(10, first, 11, second);

        assertEquals("1", RoutineCallFrame.resolve(frames, 10, "input").getValues().get("Amount"));
        assertEquals("2", RoutineCallFrame.resolve(frames, 11, "input").getValues().get("Amount"));
    }

    @Test
    void nestedFramesShadowCallerAndCanResolveOtherCallerInputs() {
        RoutineCallFrame parent = new RoutineCallFrame(10, 1, "outer",
            Map.of("shared", amount("outer"), "parent-only", amount("kept")));
        RoutineCallFrame child = new RoutineCallFrame(11, 10, "inner", Map.of("shared", amount("inner")));
        Map<Integer, RoutineCallFrame> frames = new HashMap<>();
        frames.put(10, parent);
        frames.put(11, child);

        assertEquals("inner", RoutineCallFrame.resolve(frames, 11, "shared").getValues().get("Amount"));
        assertEquals("kept", RoutineCallFrame.resolve(frames, 11, "parent-only").getValues().get("Amount"));
        assertNull(RoutineCallFrame.resolve(frames, 11, "missing"));
    }

    @Test
    void manyParallelFramesRemainIndependent() {
        Map<Integer, RoutineCallFrame> frames = IntStream.range(0, 1_000).boxed()
            .collect(Collectors.toMap(index -> index,
                index -> new RoutineCallFrame(index, -1, "routine", Map.of("input", amount(Integer.toString(index))))));

        for (int index = 0; index < 1_000; index++) {
            assertEquals(Integer.toString(index),
                RoutineCallFrame.resolve(frames, index, "input").getValues().get("Amount"));
        }
    }
}
