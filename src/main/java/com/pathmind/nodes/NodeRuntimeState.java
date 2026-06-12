package com.pathmind.nodes;

import java.util.Random;

/**
 * Runtime-only state for a node.
 *
 * <p>Execution behavior still lives in Node during this migration; this class
 * keeps transient execution data out of the GUI-heavy node model.
 */
final class NodeRuntimeState {
    int nextOutputSocket;
    int repeatRemainingIterations;
    boolean repeatActive;
    boolean repeatExecuteAttachedAction;
    boolean lastSensorResult;
    RuntimeParameterData runtimeParameterData;
    Node owningStartNode;
    Node activeRepeatUntilGuard;
    int startNodeNumber;
    Random randomGenerator;
    String randomSeedCache;
    double fallingPeakY = Double.NaN;
    boolean fallingPeakInitialized;
    long lastFallingDetectedAtMs = Long.MIN_VALUE;

    void resetControlState() {
        repeatRemainingIterations = 0;
        repeatActive = false;
        repeatExecuteAttachedAction = false;
        lastSensorResult = false;
        nextOutputSocket = 0;
        fallingPeakY = Double.NaN;
        fallingPeakInitialized = false;
        lastFallingDetectedAtMs = Long.MIN_VALUE;
    }
}
