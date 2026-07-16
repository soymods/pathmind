package com.pathmind.nodes;

import java.util.concurrent.CompletableFuture;

final class NodeSensorCommandExecutor {
    private final Node owner;

    NodeSensorCommandExecutor(Node owner) {
        this.owner = owner;
    }

    void executeSensorEvaluation(CompletableFuture<Void> future) {
        boolean result = owner.evaluateSensor();
        owner.setNextOutputSocket(result ? 0 : 1);
        future.complete(null);
    }
}
