package com.pathmind.nodes;

import com.pathmind.util.BaritoneApiProxy;

final class NodeBaritoneSupport {
    private NodeBaritoneSupport() {
    }

    static void resetPathing(Object baritone, Object mineProcess) {
        if (baritone == null) {
            return;
        }

        try {
            if (mineProcess != null) {
                BaritoneApiProxy.cancelMine(mineProcess);
                if (BaritoneApiProxy.isProcessActive(mineProcess)) {
                    BaritoneApiProxy.onLostControl(mineProcess);
                }
            }

            Object pathingBehavior = BaritoneApiProxy.getPathingBehavior(baritone);
            if (pathingBehavior != null) {
                if (BaritoneApiProxy.isPathing(pathingBehavior) || BaritoneApiProxy.hasPath(pathingBehavior)) {
                }
                BaritoneApiProxy.forceCancel(pathingBehavior);
            }

            Object goalProcess = BaritoneApiProxy.getCustomGoalProcess(baritone);
            if (goalProcess != null) {
                BaritoneApiProxy.setGoal(goalProcess, null);
                if (BaritoneApiProxy.isProcessActive(goalProcess)) {
                    BaritoneApiProxy.onLostControl(goalProcess);
                }
            }

            Object getToBlockProcess = BaritoneApiProxy.getGetToBlockProcess(baritone);
            if (getToBlockProcess != null && BaritoneApiProxy.isProcessActive(getToBlockProcess)) {
                BaritoneApiProxy.onLostControl(getToBlockProcess);
            }

            Object exploreProcess = BaritoneApiProxy.getExploreProcess(baritone);
            if (exploreProcess != null && BaritoneApiProxy.isProcessActive(exploreProcess)) {
                BaritoneApiProxy.onLostControl(exploreProcess);
            }

            Object farmProcess = BaritoneApiProxy.getFarmProcess(baritone);
            if (farmProcess != null && BaritoneApiProxy.isProcessActive(farmProcess)) {
                BaritoneApiProxy.onLostControl(farmProcess);
            }
        } catch (Exception e) {
        }
    }

    static void resetPathing(Object baritone) {
        if (baritone == null) {
            return;
        }
        resetPathing(baritone, BaritoneApiProxy.getMineProcess(baritone));
    }
}
