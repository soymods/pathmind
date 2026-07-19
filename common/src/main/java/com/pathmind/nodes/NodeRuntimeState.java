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
    boolean hasSensorResult;
    long lastSensorUpdatedAt;
    boolean lastJoinedServerRawResult;
    RuntimeParameterData runtimeParameterData;
    Node owningStartNode;
    Node activeRepeatUntilGuard;
    int startNodeNumber;
    StartLaunchMode startLaunchMode = StartLaunchMode.MANUAL;
    StartScreenTarget startScreenTarget = StartScreenTarget.ANY;
    String runtimeSourceNodeId;
    Random randomGenerator;
    String randomSeedCache;

    void resetControlState() {
        repeatRemainingIterations = 0;
        repeatActive = false;
        repeatExecuteAttachedAction = false;
        lastSensorResult = false;
        hasSensorResult = false;
        lastSensorUpdatedAt = 0L;
        lastJoinedServerRawResult = false;
        nextOutputSocket = 0;
    }
}
