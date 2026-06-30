package com.pathmind.nodes;

import java.util.concurrent.CompletableFuture;

final class NodeCommandDispatcher {
    private NodeCommandDispatcher() {
    }

    static boolean hasExplicitRoute(NodeType type) {
        if (type == null) {
            return false;
        }
        return switch (type) {
            case SET_VARIABLE, CHANGE_VARIABLE,
                GOTO, TRAVEL, GOAL, COLLECT, BUILD, EXPLORE, FOLLOW,
                CONTROL_REPEAT, CONTROL_REPEAT_UNTIL, CONTROL_WAIT_UNTIL, CONTROL_FOREVER,
                CONTROL_IF, CONTROL_IF_ELSE, CONTROL_FORK, CONTROL_JOIN_ANY, CONTROL_JOIN_ALL,
                FARM, STOP, START_CHAIN, RUN_PRESET, CUSTOM_NODE, TEMPLATE, STOP_CHAIN, STOP_ALL,
                PLACE, CRAFT, OPEN_INVENTORY, CLOSE_GUI, WRITE_BOOK, WRITE_SIGN, UI_UTILS,
                WAIT, MESSAGE, HOTBAR, DROP_ITEM, DROP_SLOT, CLICK_SLOT, CLICK_SCREEN, MOVE_ITEM,
                USE, BREAK, PLACE_HAND, LOOK, WALK, JUMP, PRESS_KEY, CRAWL, CROUCH, SPRINT, FLY,
                INTERACT, TRADE, SWING, EQUIP_ARMOR, EQUIP_HAND,
                SENSOR_TOUCHING_BLOCK, SENSOR_TOUCHING_ENTITY, SENSOR_AT_COORDINATES, SENSOR_IS_DAYTIME,
                SENSOR_IS_RAINING, SENSOR_HEALTH_BELOW, SENSOR_HUNGER_BELOW, SENSOR_ITEM_IN_INVENTORY,
                SENSOR_ITEM_IN_SLOT, SENSOR_VILLAGER_TRADE, SENSOR_IN_STOCK, SENSOR_IS_SWIMMING,
                SENSOR_IS_IN_LAVA, SENSOR_IS_UNDERWATER, SENSOR_IS_FALLING, SENSOR_IS_RENDERED,
                SENSOR_IS_VISIBLE, SENSOR_KEY_PRESSED, SENSOR_CHAT_MESSAGE, SENSOR_JOINED_SERVER,
                SENSOR_FABRIC_EVENT, SENSOR_ATTRIBUTE_DETECTION, SENSOR_GUI_FILLED, SENSOR_TARGETED_BLOCK,
                SENSOR_TARGETED_ENTITY, SENSOR_LOOK_DIRECTION, SENSOR_CURRENT_HAND,
                SENSOR_TARGETED_BLOCK_FACE, SENSOR_FIND_TRADE,
                CREATE_LIST, ADD_TO_LIST, REMOVE_FIRST_FROM_LIST, REMOVE_LAST_FROM_LIST,
                REMOVE_LIST_ITEM, REMOVE_FROM_LIST,
                PATH, INVERT, COME, SURFACE, TUNNEL -> true;
            default -> false;
        };
    }

    static void execute(Node node, CompletableFuture<Void> future) {
        switch (node.getType()) {
            case SET_VARIABLE -> new NodeVariableListCommandExecutor(node).executeSetVariableCommand(future);
            case CHANGE_VARIABLE -> new NodeVariableListCommandExecutor(node).executeChangeVariableCommand(future);

            case GOTO -> new NodeNavigationCommandExecutor(node).executeGotoCommand(future);
            case TRAVEL -> new NodeNavigationCommandExecutor(node).executeTravelCommand(future);
            case GOAL -> new NodeNavigationCommandExecutor(node).executeGoalCommand(future);
            case COLLECT -> new NodeCollectCommandExecutor(node).executeCollectCommand(future);
            case BUILD -> new NodeWorldActionCommandExecutor(node).executeBuildCommand(future);
            case EXPLORE -> new NodeWorldActionCommandExecutor(node).executeExploreCommand(future);
            case FOLLOW -> new NodeWorldActionCommandExecutor(node).executeFollowCommand(future);
            case CONTROL_REPEAT -> new NodeFlowCommandExecutor(node).executeControlRepeat(future);
            case CONTROL_REPEAT_UNTIL -> new NodeFlowCommandExecutor(node).executeControlRepeatUntil(future);
            case CONTROL_WAIT_UNTIL -> new NodeFlowCommandExecutor(node).executeControlWaitUntil(future);
            case CONTROL_FOREVER -> new NodeFlowCommandExecutor(node).executeControlForever(future);
            case CONTROL_IF -> new NodeFlowCommandExecutor(node).executeControlIf(future);
            case CONTROL_IF_ELSE -> new NodeFlowCommandExecutor(node).executeControlIfElse(future);
            case CONTROL_FORK -> new NodeFlowCommandExecutor(node).executeControlFork(future);
            case CONTROL_JOIN_ANY -> new NodeFlowCommandExecutor(node).executeControlJoinAny(future);
            case CONTROL_JOIN_ALL -> new NodeFlowCommandExecutor(node).executeControlJoinAll(future);
            case FARM -> new NodeNavigationCommandExecutor(node).executeFarmCommand(future);
            case STOP -> new NodeNavigationCommandExecutor(node).executeStopCommand(future);
            case START_CHAIN -> new NodeFlowCommandExecutor(node).executeStartChainNode(future);
            case RUN_PRESET, CUSTOM_NODE, TEMPLATE -> new NodeFlowCommandExecutor(node).executeRunPresetNode(future);
            case STOP_CHAIN -> new NodeFlowCommandExecutor(node).executeStopChainNode(future);
            case STOP_ALL -> new NodeFlowCommandExecutor(node).executeStopAllNode(future);
            case PLACE -> new NodeWorldActionCommandExecutor(node).executePlaceCommand(future);
            case CRAFT -> new NodeCraftCommandExecutor(node).executeCraftCommand(future);
            case OPEN_INVENTORY -> new NodeGuiCommandExecutor(node).executePlayerGuiCommand(future, NodeMode.PLAYER_GUI_OPEN);
            case CLOSE_GUI -> new NodeGuiCommandExecutor(node).executePlayerGuiCommand(future, NodeMode.PLAYER_GUI_CLOSE);
            case WRITE_BOOK -> new NodeTextIoCommandExecutor(node).executeWriteBookCommand(future);
            case WRITE_SIGN -> new NodeTextIoCommandExecutor(node).executeWriteSignCommand(future);
            case UI_UTILS -> new NodeGuiCommandExecutor(node).executeUiUtilsCommand(future);
            case WAIT -> new NodeFlowCommandExecutor(node).executeWaitCommand(future);
            case MESSAGE -> new NodeTextIoCommandExecutor(node).executeMessageCommand(future);
            case HOTBAR -> new NodeInventoryCommandExecutor(node).executeHotbarCommand(future);
            case DROP_ITEM -> new NodeInventoryCommandExecutor(node).executeDropItemCommand(future);
            case DROP_SLOT -> new NodeInventoryCommandExecutor(node).executeDropSlotCommand(future);
            case CLICK_SLOT -> new NodeInventoryCommandExecutor(node).executeClickSlotCommand(future);
            case CLICK_SCREEN -> new NodeInventoryCommandExecutor(node).executeClickScreenCommand(future);
            case MOVE_ITEM -> new NodeInventoryCommandExecutor(node).executeMoveItemCommand(future);
            case USE -> new NodeWorldActionCommandExecutor(node).executeUseCommand(future);
            case BREAK -> new NodeEntityActionCommandExecutor(node).executeBreakCommand(future);
            case PLACE_HAND -> new NodeWorldActionCommandExecutor(node).executePlaceHandCommand(future);
            case LOOK -> new NodeMovementCommandExecutor(node).executeLookCommand(future);
            case WALK -> new NodeMovementCommandExecutor(node).executeWalkCommand(future);
            case JUMP -> new NodeMovementCommandExecutor(node).executeJumpCommand(future);
            case PRESS_KEY -> new NodeMovementCommandExecutor(node).executePressKeyCommand(future);
            case CRAWL -> new NodeMovementCommandExecutor(node).executeCrawlCommand(future);
            case CROUCH -> new NodeMovementCommandExecutor(node).executeCrouchCommand(future);
            case SPRINT -> new NodeMovementCommandExecutor(node).executeSprintCommand(future);
            case FLY -> new NodeMovementCommandExecutor(node).executeFlyCommand(future);
            case INTERACT -> new NodeEntityActionCommandExecutor(node).executeInteractCommand(future);
            case TRADE -> new NodeEntityActionCommandExecutor(node).executeTradeCommand(future);
            case SWING -> new NodeEntityActionCommandExecutor(node).executeSwingCommand(future);
            case EQUIP_ARMOR -> new NodeEntityActionCommandExecutor(node).executeEquipArmorCommand(future);
            case EQUIP_HAND -> new NodeEntityActionCommandExecutor(node).executeEquipHandCommand(future);
            case SENSOR_TOUCHING_BLOCK, SENSOR_TOUCHING_ENTITY, SENSOR_AT_COORDINATES, SENSOR_IS_DAYTIME,
                SENSOR_IS_RAINING, SENSOR_HEALTH_BELOW, SENSOR_HUNGER_BELOW, SENSOR_ITEM_IN_INVENTORY,
                SENSOR_ITEM_IN_SLOT, SENSOR_VILLAGER_TRADE, SENSOR_IN_STOCK, SENSOR_IS_SWIMMING,
                SENSOR_IS_IN_LAVA, SENSOR_IS_UNDERWATER, SENSOR_IS_FALLING, SENSOR_IS_RENDERED,
                SENSOR_IS_VISIBLE, SENSOR_KEY_PRESSED, SENSOR_CHAT_MESSAGE, SENSOR_JOINED_SERVER,
                SENSOR_FABRIC_EVENT, SENSOR_ATTRIBUTE_DETECTION, SENSOR_GUI_FILLED, SENSOR_TARGETED_BLOCK,
                SENSOR_TARGETED_ENTITY, SENSOR_LOOK_DIRECTION, SENSOR_CURRENT_HAND,
                SENSOR_TARGETED_BLOCK_FACE -> new NodeSensorCommandExecutor(node).executeSensorEvaluation(future);
            case CREATE_LIST -> new NodeVariableListCommandExecutor(node).executeCreateListCommand(future);
            case ADD_TO_LIST -> new NodeVariableListCommandExecutor(node).executeAddToListCommand(future);
            case REMOVE_FIRST_FROM_LIST -> new NodeVariableListCommandExecutor(node).executeRemoveFromListCommand(
                future,
                NodeVariableListCommandExecutor.RemoveListMode.FIRST
            );
            case REMOVE_LAST_FROM_LIST -> new NodeVariableListCommandExecutor(node).executeRemoveFromListCommand(
                future,
                NodeVariableListCommandExecutor.RemoveListMode.LAST
            );
            case REMOVE_LIST_ITEM -> new NodeVariableListCommandExecutor(node).executeRemoveFromListCommand(
                future,
                NodeVariableListCommandExecutor.RemoveListMode.INDEX
            );
            case REMOVE_FROM_LIST -> new NodeVariableListCommandExecutor(node).executeRemoveFromListCommand(
                future,
                NodeVariableListCommandExecutor.RemoveListMode.VALUE
            );

            // Legacy nodes
            case PATH -> new NodeNavigationCommandExecutor(node).executePathCommand(future);
            case INVERT -> new NodeNavigationCommandExecutor(node).executeInvertCommand(future);
            case COME -> new NodeNavigationCommandExecutor(node).executeComeCommand(future);
            case SURFACE -> new NodeNavigationCommandExecutor(node).executeSurfaceCommand(future);
            case TUNNEL -> new NodeNavigationCommandExecutor(node).executeTunnelCommand(future);

            default -> future.complete(null);
        }
    }
}
