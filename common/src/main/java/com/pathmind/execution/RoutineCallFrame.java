package com.pathmind.execution;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Immutable invocation-local input scope linked to its caller execution. */
final class RoutineCallFrame {
    private final int executionId;
    private final int parentExecutionId;
    private final String routineId;
    private final Map<String, ExecutionManager.RuntimeVariable> inputs;

    RoutineCallFrame(int executionId, int parentExecutionId, String routineId,
                     Map<String, ExecutionManager.RuntimeVariable> inputs) {
        this.executionId = executionId;
        this.parentExecutionId = parentExecutionId;
        this.routineId = routineId == null ? "" : routineId;
        this.inputs = Collections.unmodifiableMap(new HashMap<>(inputs == null ? Map.of() : inputs));
    }

    int executionId() { return executionId; }
    int parentExecutionId() { return parentExecutionId; }
    String routineId() { return routineId; }
    Map<String, ExecutionManager.RuntimeVariable> inputs() { return inputs; }
    ExecutionManager.RuntimeVariable get(String inputId) { return inputs.get(inputId); }

    static ExecutionManager.RuntimeVariable resolve(Map<Integer, RoutineCallFrame> frames,
                                                     int executionId, String inputId) {
        if (frames == null || inputId == null || inputId.isBlank()) return null;
        RoutineCallFrame frame = frames.get(executionId);
        while (frame != null) {
            ExecutionManager.RuntimeVariable value = frame.get(inputId);
            if (value != null) return value;
            frame = frames.get(frame.parentExecutionId());
        }
        return null;
    }
}
