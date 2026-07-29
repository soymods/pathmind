package com.pathmind.nodes;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.Locale;
import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.pathmind.data.NodeGraphData;
import com.pathmind.routines.RoutineInputDefinition;
import com.pathmind.routines.RoutineValueKind;
import com.pathmind.execution.ExecutionManager;
import com.pathmind.ui.overlay.NodeErrorNotificationOverlay;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.BaritoneDependencyChecker;
import com.pathmind.util.BaritoneApiProxy;
import com.pathmind.util.BlockSelection;
import com.pathmind.util.PathmindI18n;
import com.pathmind.util.ClientMessageSender;
import com.pathmind.util.UiUtilsProxy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Represents a single node in the Pathmind visual editor.
 * Similar to Blender's shader nodes, each node has inputs, outputs, and parameters.
 */
public class Node {
    private static final Logger LOGGER = LoggerFactory.getLogger(Node.class);
    static final Gson LIST_ENTRY_GSON = new Gson();
    static final String LIST_ENTRY_SERIALIZED_PREFIX = "pm_list:";
    public static final int NO_OUTPUT = -1;
    private final String id;
    private final NodeType type;
    private NodeMode mode;
    private final NodeLayoutState layoutState;
    private final NodeInlineFieldLayout inlineFieldLayout;
    private final NodeInteractionState interactionState;
    private final NodeAttachments attachments;
    private final NodeRuntimeState runtimeState;
    private final NodeRuntimeParameterResolver runtimeParameterResolver;
    private final NodeWorldTargetResolver worldTargetResolver;
    private final NodeSensorCoordinator sensorCoordinator;
    private final NodeParameterValues parameterValues;
    private final NodeTextContent textContent;
    private final NodeExecutionCoordinator executionCoordinator;
    private final NodeAttachmentCoordinator attachmentCoordinator;
    private final NodeRoutineMetadata routineMetadata;
    private final NodeEditorMetadata editorMetadata;
    static final int MIN_WIDTH = 92;
    static final int MIN_HEIGHT = 44;
    static final int EVENT_FUNCTION_MIN_HEIGHT = 36;
    static final int CHAR_PIXEL_WIDTH = 6;
    static final int HEADER_HEIGHT = 18;
    static final int PARAM_LINE_HEIGHT = 20;
    static final int PARAM_PADDING_TOP = 2;
    static final int PARAM_PADDING_BOTTOM = 4;
    private static final int MAX_PARAMETER_LABEL_LENGTH = 20;
    static final String DIRECTION_MODE_EXACT = "exact";
    static final String DIRECTION_MODE_CARDINAL = "cardinal";
    private static final String BOOLEAN_MODE_LITERAL = "literal";
    private static final String BOOLEAN_MODE_VARIABLE = "variable";
    @Deprecated
    private static volatile CachedRecipeBook cachedRecipeBook;
    static final int BODY_PADDING_NO_PARAMS = 10;
    static final int START_END_SIZE = 36;
    private static final String CHAT_MESSAGE_PREFIX = "\u00A74[\u00A7cPathmind\u00A74] \u00A77";
    static final String LIST_SLOT_GUI_PREFIX = "gui:";
    static final String LIST_SLOT_PLAYER_PREFIX = "player:";
    private static final long CRAFTING_ACTION_DELAY_MS = 75L;
    static final long CONTROL_POLL_INTERVAL_MS = 10L;
    private static final long FALLING_SENSOR_RETENTION_MS = 1000L;
    private static final double FALLING_SENSOR_MIN_CLEARANCE = 0.6D;
    private static final int CRAFTING_OUTPUT_POLL_LIMIT = 20;
    static final int SENSOR_SLOT_MARGIN_HORIZONTAL = 8;
    static final int SENSOR_SLOT_INNER_PADDING = 4;
    static final int SENSOR_SLOT_MIN_CONTENT_WIDTH = 60;
    static final int SENSOR_SLOT_MIN_CONTENT_HEIGHT = 28;
    static final int ACTION_SLOT_MARGIN_HORIZONTAL = 8;
    static final int ACTION_SLOT_INNER_PADDING = 4;
    static final int ACTION_SLOT_MIN_CONTENT_WIDTH = 80;
    static final int ACTION_SLOT_MIN_CONTENT_HEIGHT = 32;
    static final int PARAMETER_SLOT_MARGIN_HORIZONTAL = 8;
    static final int PARAMETER_SLOT_INNER_PADDING = 4;
    static final int PARAMETER_SLOT_MIN_CONTENT_WIDTH = 88;
    static final int PARAMETER_SLOT_MIN_CONTENT_HEIGHT = 32;
    static final int PARAMETER_SLOT_LABEL_HEIGHT = 12;
    static final int OPERATOR_SLOT_GAP = 24;
    static final int MINIMAL_NODE_TAB_WIDTH = 6;
    static final int PARAMETER_FIELD_PADDING = 12;
    static final int PARAMETER_SLOT_BOTTOM_PADDING = 6;

    static String tr(String key, Object... args) {
        return PathmindI18n.tr(key, args);
    }
    static final int SLOT_AREA_PADDING_TOP = 0;
    static final int SLOT_AREA_PADDING_BOTTOM = 6;
    static final int SLOT_VERTICAL_SPACING = 6;
    static final int BOOLEAN_TOGGLE_MARGIN_HORIZONTAL = 6;
    static final int BOOLEAN_TOGGLE_TOP_MARGIN = 8;
    static final int BOOLEAN_TOGGLE_HEIGHT = 16;
    static final int BOOLEAN_TOGGLE_BOTTOM_MARGIN = 8;
    static final int COORDINATE_FIELD_WIDTH = 44;
    static final int COORDINATE_FIELD_HEIGHT = 16;
    static final int COORDINATE_FIELD_TEXT_PADDING = 3;
    static final int COORDINATE_FIELD_SPACING = 6;
    static final int COORDINATE_FIELD_TOP_MARGIN = 6;
    static final int COORDINATE_FIELD_LABEL_HEIGHT = 10;
    static final int COORDINATE_FIELD_BOTTOM_MARGIN = 6;
    static final int SCREEN_PICK_BUTTON_TOP_MARGIN = 6;
    static final int SCREEN_PICK_BUTTON_HEIGHT = 16;
    static final int SCREEN_PICK_BUTTON_MIN_WIDTH = 70;
    static final int SCREEN_PICK_BUTTON_BOTTOM_MARGIN = 6;
    static final int AMOUNT_FIELD_TOP_MARGIN = 6;
    static final int AMOUNT_FIELD_LABEL_HEIGHT = 10;
    static final int AMOUNT_FIELD_HEIGHT = 16;
    static final int AMOUNT_FIELD_TEXT_PADDING = 3;
    static final int AMOUNT_FIELD_BOTTOM_MARGIN = 6;
    static final int WAIT_AMOUNT_FIELD_GAP = 4;
    static final int AMOUNT_TOGGLE_WIDTH = 18;
    static final int AMOUNT_TOGGLE_HEIGHT = 10;
    static final int AMOUNT_TOGGLE_SPACING = 6;
    static final int RANDOM_ROUNDING_FIELD_TOP_MARGIN = 6;
    static final int RANDOM_ROUNDING_FIELD_LABEL_HEIGHT = 10;
    static final int RANDOM_ROUNDING_FIELD_HEIGHT = 16;
    static final int RANDOM_ROUNDING_FIELD_BOTTOM_MARGIN = 6;
    static final int RANDOM_ROUNDING_TOGGLE_WIDTH = 18;
    static final int RANDOM_ROUNDING_TOGGLE_HEIGHT = 10;
    static final int RANDOM_ROUNDING_TOGGLE_SPACING = 6;
    static final int MESSAGE_FIELD_MARGIN_HORIZONTAL = 6;
    static final int MESSAGE_FIELD_TOP_MARGIN = 6;
    static final int MESSAGE_FIELD_LABEL_HEIGHT = 10;
    static final int MESSAGE_FIELD_HEIGHT = 16;
    static final int MESSAGE_FIELD_VERTICAL_GAP = 6;
    static final int MESSAGE_FIELD_BOTTOM_MARGIN = 6;
    static final int MESSAGE_FIELD_MIN_CONTENT_WIDTH = 120;
    static final int MESSAGE_FIELD_TEXT_PADDING = 3;
    public static final int MAX_MESSAGE_LINES = 128;
    public static final int MAX_MESSAGE_LINE_LENGTH = 512;
    static final int MESSAGE_BUTTON_SIZE = 10;
    static final int MESSAGE_BUTTON_PADDING = 4;
    static final int MESSAGE_BUTTON_SPACING = 4;
    static final int MESSAGE_SCOPE_MARGIN_HORIZONTAL = 6;
    static final int MESSAGE_SCOPE_TOP_MARGIN = 6;
    static final int MESSAGE_SCOPE_LABEL_HEIGHT = 10;
    static final int MESSAGE_SCOPE_TOGGLE_HEIGHT = 16;
    static final int MESSAGE_SCOPE_BOTTOM_MARGIN = 6;
    static final int SCHEMATIC_FIELD_TOP_MARGIN = 6;
    static final int SCHEMATIC_FIELD_LABEL_HEIGHT = 10;
    static final int SCHEMATIC_FIELD_HEIGHT = 16;
    static final int SCHEMATIC_FIELD_BOTTOM_MARGIN = 6;
    static final int STOP_TARGET_FIELD_MARGIN_HORIZONTAL = 8;
    static final int STOP_TARGET_FIELD_TOP_MARGIN = 6;
    static final int STOP_TARGET_FIELD_LABEL_HEIGHT = 0;
    static final int STOP_TARGET_FIELD_HEIGHT = 16;
    static final int STOP_TARGET_FIELD_TEXT_PADDING = 3;
    static final int STOP_TARGET_FIELD_BOTTOM_MARGIN = 6;
    static final int STOP_TARGET_FIELD_MIN_WIDTH = 48;
    static final int RUN_PRESET_FIELD_MIN_WIDTH = 120;
    static final int VARIABLE_FIELD_MARGIN_HORIZONTAL = 8;
    static final int VARIABLE_FIELD_TOP_MARGIN = 6;
    static final int VARIABLE_FIELD_LABEL_HEIGHT = 0;
    static final int VARIABLE_FIELD_HEIGHT = 16;
    static final int VARIABLE_FIELD_TEXT_PADDING = 3;
    static final int VARIABLE_FIELD_BOTTOM_MARGIN = 6;
    static final int VARIABLE_FIELD_MIN_WIDTH = 80;
    static final int MODE_FIELD_TOP_MARGIN = 6;
    static final int MODE_FIELD_LABEL_HEIGHT = 0;
    static final int MODE_FIELD_HEIGHT = 16;
    static final int MODE_FIELD_BOTTOM_MARGIN = 6;
    static final int BOOK_TEXT_BUTTON_MARGIN_HORIZONTAL = 6;
    static final int BOOK_TEXT_TOP_MARGIN = 6;
    static final int BOOK_TEXT_BUTTON_HEIGHT = 16;
    static final int BOOK_TEXT_BUTTON_MIN_WIDTH = 70;
    static final int BOOK_TEXT_LABEL_HEIGHT = 10;
    static final int BOOK_TEXT_PAGE_FIELD_HEIGHT = 16;
    static final int BOOK_TEXT_FIELD_SPACING = 6;
    static final int BOOK_TEXT_BOTTOM_MARGIN = 6;
    static final int SIGN_LINE_MAX_CHARS = 15;
    static final int SIGN_MAX_LINES = 4;
    static final int SIGN_MAX_CHARS = 63;
    static final int POPUP_EDIT_BUTTON_MARGIN_HORIZONTAL = 6;
    static final int POPUP_EDIT_BUTTON_TOP_MARGIN = 4;
    static final int POPUP_EDIT_BUTTON_HEIGHT = 16;
    static final int POPUP_EDIT_BUTTON_MIN_WIDTH = 70;
    static final int POPUP_EDIT_BUTTON_BOTTOM_MARGIN = 6;
    static final int TEMPLATE_NODE_WIDTH = 160;
    static final int TEMPLATE_NODE_HEIGHT = 108;
    static final int EVENT_NAME_FIELD_MARGIN_HORIZONTAL = 6;
    static final int EVENT_NAME_FIELD_TOP_MARGIN = 6;
    static final int EVENT_NAME_FIELD_HEIGHT = 16;
    static final int EVENT_NAME_FIELD_BOTTOM_MARGIN = 6;
    static final int STICKY_NOTE_MIN_WIDTH = 120;
    static final int STICKY_NOTE_MIN_HEIGHT = 84;
    private static final int STICKY_NOTE_HEADER_HEIGHT = 18;
    private static final int STICKY_NOTE_TEXT_MARGIN = 8;
    private static final int STICKY_NOTE_HANDLE_SIZE = 8;
    static final int BOOK_PAGE_MAX_CHARS = 256;
    static final double PARAMETER_SEARCH_RADIUS = 64.0;
    static final double DEFAULT_DIRECTION_DISTANCE = 16.0;
    private static final Object GOTO_BREAK_LOCK = new Object();
    private static final AtomicInteger ACTIVE_GOTO_BREAK_BLOCKING_REQUESTS = new AtomicInteger(0);
    private static final AtomicInteger ACTIVE_GOTO_PLACE_BLOCKING_REQUESTS = new AtomicInteger(0);
    private static final AtomicInteger ACTIVE_BARITONE_CACHE_OVERRIDE_REQUESTS = new AtomicInteger(0);
    private static final AtomicInteger ACTIVE_BARITONE_EXPLORE_OVERRIDE_REQUESTS = new AtomicInteger(0);
    private static final AtomicInteger ACTIVE_BARITONE_PATH_HISTORY_OVERRIDE_REQUESTS = new AtomicInteger(0);
    private static final AtomicInteger ACTIVE_BARITONE_CACHED_SCAN_OVERRIDE_REQUESTS = new AtomicInteger(0);
    private static Boolean gotoBreakOriginalValue = null;
    private static Boolean gotoPlaceOriginalValue = null;
    private static Boolean baritoneChunkCachingOriginalValue = null;
    private static Boolean baritonePathThroughCachedOnlyOriginalValue = null;
    private static Boolean baritoneExploreForBlocksOriginalValue = null;
    private static Boolean baritoneSplicePathOriginalValue = null;
    private static Integer baritoneMaxPathHistoryLengthOriginalValue = null;
    private static Integer baritonePathHistoryCutoffAmountOriginalValue = null;
    private static Integer baritoneMaxCachedWorldScanCountOriginalValue = null;
    static final ScheduledExecutorService MESSAGE_SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Pathmind-Message-Scheduler");
        t.setDaemon(true);
        return t;
    });
    private final List<NodeParameter> parameters;
    private boolean booleanToggleValue = true;
    private int dynamicBooleanOperatorSlotCount;
    private String stickyNoteText;
    private boolean gotoAllowBreakWhileExecuting;
    private boolean gotoAllowPlaceWhileExecuting;
    private boolean keyPressedActivatesInGuis;
    public Node(NodeType type, int x, int y) {
        this.id = java.util.UUID.randomUUID().toString();
        this.type = type;
        this.mode = NodeMode.getDefaultModeForNodeType(type);
        this.layoutState = new NodeLayoutState(
            x,
            y,
            STICKY_NOTE_MIN_WIDTH + 32,
            STICKY_NOTE_MIN_HEIGHT + 20);
        this.inlineFieldLayout = new NodeInlineFieldLayout(this, layoutState);
        this.interactionState = new NodeInteractionState();
        this.attachments = new NodeAttachments();
        this.runtimeState = new NodeRuntimeState();
        this.runtimeParameterResolver = new NodeRuntimeParameterResolver(this);
        this.worldTargetResolver = new NodeWorldTargetResolver(this);
        this.sensorCoordinator = new NodeSensorCoordinator(this);
        this.parameters = new ArrayList<>();
        this.parameterValues = new NodeParameterValues(this);
        this.textContent = new NodeTextContent(type, () -> {
            layoutState.clearMessageFieldContentWidthOverride();
            recalculateDimensions();
        });
        this.executionCoordinator = new NodeExecutionCoordinator(this);
        this.attachmentCoordinator = new NodeAttachmentCoordinator(this);
        this.routineMetadata = new NodeRoutineMetadata(this);
        this.editorMetadata = new NodeEditorMetadata(type);
        this.dynamicBooleanOperatorSlotCount = isExpandableBooleanOperatorType(type) ? 2 : 0;
        this.stickyNoteText = "";
        this.gotoAllowBreakWhileExecuting = false;
        this.gotoAllowPlaceWhileExecuting = false;
        this.keyPressedActivatesInGuis = true;
        parameterValues.initializeParameters();
        recalculateDimensions();
        resetControlState();
    }

    static final class PlacementFailure extends RuntimeException {
        PlacementFailure(String message) {
            super(message);
        }
    }

    enum ParameterHandlingResult {
        CONTINUE,
        COMPLETE
    }

    enum ParameterUsage {
        POSITION,
        LOOK_ORIENTATION
    }
    
    private static final String PARAM_ID_BOOLEAN_MODE = "boolean_mode";
    private static final String PARAM_ID_BOOLEAN_TOGGLE = "boolean_toggle";
    private static final String PARAM_ID_BOOLEAN_VARIABLE = "boolean_variable";
    private static final String PARAM_ID_CREATE_LIST_USE_RADIUS = "create_list_use_radius";
    private static final String PARAM_ID_CREATE_LIST_RADIUS = "create_list_radius";
    private static final String PARAM_ID_CREATE_LIST_USE_BLOCK_CAP = "create_list_use_block_cap";
    private static final String PARAM_ID_CREATE_LIST_MAX_BLOCKS = "create_list_max_blocks";
    private static final String PARAM_ID_RANDOM_USE_ROUNDING = "random_use_rounding";
    private static final String PARAM_ID_TRADE_NUMBER = "trade_number";
    private static final String PARAM_ID_TRADE_COUNT = "trade_count";
    private static final String PARAM_ID_DIRECTION_MODE = "direction_mode";
    private static final String PARAM_ID_DIRECTION_CARDINAL = "direction_cardinal";
    private static final String PARAM_ID_DIRECTION_YAW = "direction_yaw";
    private static final String PARAM_ID_DIRECTION_PITCH = "direction_pitch";
    private static final String PARAM_ID_DIRECTION_YAW_OFFSET = "direction_yaw_offset";
    private static final String PARAM_ID_DIRECTION_PITCH_OFFSET = "direction_pitch_offset";
    private static final String PARAM_ID_DIRECTION_DISTANCE = "direction_distance";
    private static final String PARAM_ID_ROTATION_YAW = "rotation_yaw";
    private static final String PARAM_ID_ROTATION_PITCH = "rotation_pitch";
    private static final String PARAM_ID_ROTATION_YAW_OFFSET = "rotation_yaw_offset";
    private static final String PARAM_ID_ROTATION_PITCH_OFFSET = "rotation_pitch_offset";
    private static final String PARAM_ID_ROTATION_DISTANCE = "rotation_distance";
    private static final String PARAM_ID_LOOK_YAW = "look_yaw";
    private static final String PARAM_ID_LOOK_PITCH = "look_pitch";
    private static final String PARAM_ID_INVENTORY_SLOT_INDEX = "inventory_slot_index";
    private static final String PARAM_ID_INVENTORY_SLOT_MODE = "inventory_slot_mode";
    private static final String PARAM_ID_HOTBAR_SLOT = "hotbar_slot";
    private static final String PARAM_ID_CLICK_SLOT_INDEX = "click_slot_index";
    private static final String PARAM_ID_DROP_SLOT_INDEX = "drop_slot_index";
    private static final String PARAM_ID_MOVE_ITEM_SOURCE_SLOT = "move_item_source_slot";
    private static final String PARAM_ID_MOVE_ITEM_TARGET_SLOT = "move_item_target_slot";
    private static final String PARAM_ID_EQUIP_ARMOR_SOURCE_SLOT = "equip_armor_source_slot";
    private static final String PARAM_ID_EQUIP_ARMOR_SLOT = "equip_armor_slot";
    private static final String PARAM_ID_EQUIP_HAND_SOURCE_SLOT = "equip_hand_source_slot";
    private static final String PARAM_ID_EQUIP_HAND_HAND = "equip_hand_hand";
    private static final String PARAM_ID_UI_CLICK_SYNC_ID = "ui_click_sync_id";
    private static final String PARAM_ID_UI_CLICK_REVISION = "ui_click_revision";
    private static final String PARAM_ID_UI_CLICK_SLOT = "ui_click_slot";
    private static final String PARAM_ID_UI_CLICK_BUTTON = "ui_click_button";
    private static final String PARAM_ID_UI_CLICK_ACTION = "ui_click_action";
    private static final String PARAM_ID_UI_CLICK_TIMES = "ui_click_times";
    private static final String PARAM_ID_UI_CLICK_DELAY = "ui_click_delay";
    private static final String PARAM_ID_UI_BUTTON_SYNC_ID = "ui_button_sync_id";
    private static final String PARAM_ID_UI_BUTTON_ID = "ui_button_id";
    private static final String PARAM_ID_UI_BUTTON_TIMES = "ui_button_times";
    private static final String PARAM_ID_UI_BUTTON_DELAY = "ui_button_delay";

    static String normalizeParameterKey(String key) {
        if (key == null) {
            return "";
        }
        return key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
    
    private static NodeParameter createParameter(String id, String name, ParameterType type, String defaultValue) {
        return new NodeParameter(id, name, type, defaultValue);
    }

    /** Sends a HUD notification error to the player (e.g. for invalid numeric/variable input). */
    public void sendNodeErrorMessageToPlayer(String message) {
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client != null) {
            sendNodeErrorMessage(client, message);
        }
    }

    void sendNodeErrorMessage(net.minecraft.client.Minecraft client, String message) {
        if (client == null || message == null || message.isEmpty()) {
            return;
        }

        client.execute(() -> sendNodeErrorMessageOnClientThread(client, message));
    }

    private void sendNodeErrorMessageOnClientThread(net.minecraft.client.Minecraft client, String message) {
        if (client == null || message == null || message.isEmpty()) {
            return;
        }

        NodeErrorNotificationOverlay.getInstance().show(message, type != null ? getColor() : UITheme.STATE_ERROR);
    }

    void sendNodeInfoMessage(net.minecraft.client.Minecraft client, String message) {
        if (client == null || message == null || message.isEmpty()) {
            return;
        }

        client.execute(() -> sendNodeInfoMessageOnClientThread(client, message));
    }

    private void sendNodeInfoMessageOnClientThread(net.minecraft.client.Minecraft client, String message) {
        if (client == null || client.player == null || message == null || message.isEmpty()) {
            return;
        }

        client.player.displayClientMessage(Component.literal(CHAT_MESSAGE_PREFIX + message), false);
    }

    /**
     * Gets the Baritone instance for the current player
     * @return Baritone instance or null if not available
     */
    Object getBaritone() {
        try {
            return BaritoneApiProxy.getPrimaryBaritone();
        } catch (Exception e) {
            System.err.println("Failed to get Baritone instance: " + e.getMessage());
            return null;
        }
    }

    boolean isBaritoneApiAvailable() {
        return BaritoneDependencyChecker.isBaritoneApiPresent();
    }

    boolean isBaritoneModAvailable() {
        return BaritoneDependencyChecker.isBaritonePresent();
    }

    public String getId() {
        return id;
    }

    public String getRuntimeSourceNodeId() {
        return runtimeState.runtimeSourceNodeId != null && !runtimeState.runtimeSourceNodeId.isBlank()
            ? runtimeState.runtimeSourceNodeId
            : id;
    }

    public void setRuntimeSourceNodeId(String sourceNodeId) {
        runtimeState.runtimeSourceNodeId = sourceNodeId;
    }

    public NodeType getType() {
        return type;
    }

    NodeRuntimeState runtimeState() {
        return runtimeState;
    }
    
    public NodeMode getMode() {
        return mode;
    }

    public EnumSet<NodeValueTrait> getProvidedTraits() {
        if (type == NodeType.ROUTINE_INPUT) {
            NodeParameter valueKind = getParameter("ValueKind");
            RoutineValueKind kind = RoutineValueKind.fromSerialized(valueKind == null ? null : valueKind.getStringValue());
            return kind.getDefaultTraits().isEmpty()
                ? EnumSet.of(NodeValueTrait.ANY)
                : EnumSet.copyOf(kind.getDefaultTraits());
        }
        if (type == NodeType.LIST_ITEM) {
            NodeType resolved = getResolvedValueType();
            if (resolved != NodeType.LIST_ITEM) {
                return NodeTraitRegistry.getProvidedTraits(resolved);
            }
        }
        EnumSet<NodeValueTrait> traits = NodeTraitRegistry.getProvidedTraits(type);
        if (type == NodeType.SENSOR_POSITION_OF) {
            if (isSensorPositionSingleAxisMode()) {
                return EnumSet.of(NodeValueTrait.NUMBER);
            }
            return EnumSet.of(NodeValueTrait.COORDINATE);
        }
        return traits;
    }
    
    public void setMode(NodeMode mode) {
        // Preserve existing parameter values when mode doesn't change
        boolean modeChanged = this.mode != mode;
        Map<String, String> preservedValues = new HashMap<>();
        if (!modeChanged) {
            // Save current parameter values before clearing
            for (NodeParameter param : parameters) {
                preservedValues.put(param.getName(), param.getStringValue());
            }
        }
        
        this.mode = mode;
        // Reinitialize parameters when mode changes
        parameters.clear();
        parameterValues.initializeParameters();
        
        // Restore preserved values if mode didn't change
        if (!modeChanged && !preservedValues.isEmpty()) {
            for (NodeParameter param : parameters) {
                String preservedValue = preservedValues.get(param.getName());
                if (preservedValue != null) {
                    param.setStringValue(preservedValue);
                }
            }
        }
        
        recalculateDimensions();
        resetControlState();
    }

    public int getX() {
        return layoutState.getX();
    }

    public int getY() {
        return layoutState.getY();
    }

    public void setPosition(int x, int y) {
        setPositionSilently(x, y);
        if (attachments.getAttachedSensor() != null) {
            updateAttachedSensorPosition();
        }
        if (attachments.getAttachedActionNode() != null) {
            updateAttachedActionPosition();
        }
        updateAttachedParameterPositions();
    }

    void setPositionSilently(int x, int y) {
        layoutState.setPosition(x, y);
    }

    public int getWidth() {
        return layoutState.getWidth();
    }

    public int getHeight() {
        return layoutState.getHeight();
    }

    public boolean isSelected() {
        return interactionState.isSelected();
    }

    public void setSelected(boolean selected) {
        interactionState.setSelected(selected);
    }

    public boolean isDragging() {
        return interactionState.isDragging();
    }

    public void setDragging(boolean dragging) {
        interactionState.setDragging(dragging);
    }

    public int getDragOffsetX() {
        return interactionState.getDragOffsetX();
    }

    public void setDragOffsetX(int dragOffsetX) {
        interactionState.setDragOffsetX(dragOffsetX);
    }

    public int getDragOffsetY() {
        return interactionState.getDragOffsetY();
    }

    public void setDragOffsetY(int dragOffsetY) {
        interactionState.setDragOffsetY(dragOffsetY);
    }

    public boolean containsPoint(int pointX, int pointY) {
        return layoutState.containsPoint(pointX, pointY);
    }

    public Component getDisplayName() {
        if (type == NodeType.ROUTINE_ENTRY || type == NodeType.ROUTINE_CALL || type == NodeType.ROUTINE_INPUT) {
            NodeParameter label = getParameter(type == NodeType.ROUTINE_ENTRY ? "Name" : "Label");
            if (type == NodeType.ROUTINE_CALL) label = getParameter("Name");
            if (label != null && !label.getStringValue().isBlank()) {
                return Component.literal(label.getStringValue());
            }
        }
        return Component.literal(type.getDisplayName());
    }

    public boolean isProtectedRoutineEntry() {
        return type == NodeType.ROUTINE_ENTRY;
    }

    public String getRoutineId() {
        return routineMetadata.getRoutineId();
    }

    public String getRoutineInputId() {
        return routineMetadata.getRoutineInputId();
    }

    public void setRoutineIdentity(String routineId, String inputId) {
        routineMetadata.setRoutineIdentity(routineId, inputId);
    }

    public static Node createRoutineEntry(String routineId, String label, int x, int y) {
        return NodeRoutineMetadata.createRoutineEntry(routineId, label, x, y);
    }

    public static Node createRoutineInput(String routineId, RoutineInputDefinition input, int x, int y) {
        return NodeRoutineMetadata.createRoutineInput(routineId, input, x, y);
    }

    public static Node createRoutineCall(String routineId, String name, int x, int y) {
        return NodeRoutineMetadata.createRoutineCall(routineId, name, x, y);
    }

    public static Node createRoutineCall(NodeGraphData.RoutineDefinitionData routine, int x, int y) {
        return NodeRoutineMetadata.createRoutineCall(routine, x, y);
    }

    public void setRoutineArguments(List<NodeGraphData.RoutineArgumentData> arguments) {
        routineMetadata.setRoutineArguments(arguments);
    }

    public List<NodeGraphData.RoutineArgumentData> getRoutineArguments() {
        return routineMetadata.getRoutineArguments();
    }

    public void syncRoutineCallDefinition(NodeGraphData.RoutineDefinitionData routine) {
        routineMetadata.syncRoutineCallDefinition(routine);
    }

    public String getRoutineInputIdForSlot(int slotIndex) {
        return routineMetadata.getRoutineInputIdForSlot(slotIndex);
    }

    public int getRoutineSlotForInputId(String inputId) {
        return routineMetadata.getRoutineSlotForInputId(inputId);
    }

    public boolean isRoutineArgumentOrphaned(int slotIndex) {
        return routineMetadata.isRoutineArgumentOrphaned(slotIndex);
    }

    public String getRoutineArgumentDefaultValue(int slotIndex) {
        return routineMetadata.getRoutineArgumentDefaultValue(slotIndex);
    }

    public String getRoutineArgumentValueKind(int slotIndex) {
        return routineMetadata.getRoutineArgumentValueKind(slotIndex);
    }

    public EnumSet<NodeValueTrait> getAcceptedTraitsForParameterSlot(int slotIndex) {
        return routineMetadata.getAcceptedTraitsForParameterSlot(slotIndex);
    }
    public boolean isSensorNode() {
        return NodeCatalog.isBooleanSensor(type);
    }

    public boolean isStickyNote() {
        return type == NodeType.STICKY_NOTE;
    }

    private boolean isBooleanNotOperator() {
        return type == NodeType.OPERATOR_BOOLEAN_NOT;
    }

    boolean isComparisonOperator() {
        return NodeCatalog.isBooleanSensor(type) && NodeCatalog.category(type) == NodeCategory.DATA;
    }

    private static boolean isExpandableBooleanOperatorType(NodeType type) {
        return type == NodeType.OPERATOR_BOOLEAN_OR || type == NodeType.OPERATOR_BOOLEAN_AND;
    }

    public boolean isExpandableBooleanOperator() {
        return isExpandableBooleanOperatorType(type);
    }

    public boolean isParameterNode() {
        return NodeCatalog.isParameterNode(type);
    }

    public boolean shouldRenderInlineParameters() {
        return NodeCatalog.shouldRenderInlineParameters(type);
    }

    boolean isInlineParameterNode() {
        return NodeCatalog.isInlineParameterNode(type);
    }

    public static boolean isSensorType(NodeType nodeType) {
        return NodeCatalog.isBooleanSensor(nodeType);
    }

    public static boolean isParameterType(NodeType nodeType) {
        return NodeCatalog.isParameterNode(nodeType);
    }

    /**
     * Whether a node may be dragged into / used as a parameter: parameter nodes, sensors, and
     * any node that provides a value trait (e.g. Calculate, operators). Slot trait-matching is
     * intentionally not enforced beyond this — any usable value node may attach to any slot.
     */
    public static boolean isUsableAsParameterType(NodeType nodeType) {
        return isParameterType(nodeType)
            || isSensorType(nodeType)
            || nodeType == NodeType.SENSOR_POSITION_OF
            || nodeType == NodeType.SENSOR_DISTANCE_BETWEEN
            || nodeType == NodeType.SENSOR_TARGETED_BLOCK_FACE
            || nodeType == NodeType.SENSOR_TARGETED_BLOCK
            || nodeType == NodeType.SENSOR_TARGETED_ENTITY
            || nodeType == NodeType.SENSOR_CURRENT_GUI
            || nodeType == NodeType.SENSOR_LOOK_DIRECTION
            || !NodeTraitRegistry.getProvidedTraits(nodeType).isEmpty();
    }

    public boolean canAcceptSensor() {
        return NodeCompatibility.canHostSlot(type, NodeSlotType.SENSOR);
    }

    public boolean hasSensorSlot() {
        return canAcceptSensor();
    }

    public boolean canAcceptParameter() {
        if (type == NodeType.ROUTINE_CALL) return routineMetadata.hasRoutineArguments();
        if (!NodeCompatibility.canHostSlot(type, NodeSlotType.PARAMETER)
                || NodeParameterRepair.usesVillagerTradeNumberField(type)
                || !NodeTraitRegistry.canHostParameter(type)) {
            return false;
        }
			return !isParameterNode()
					|| type == NodeType.OPERATOR_MOD
					|| type == NodeType.PARAM_BLOCK_FACE
					|| type == NodeType.SENSOR_POSITION_OF
					|| type == NodeType.SENSOR_DISTANCE_BETWEEN
					|| type == NodeType.SENSOR_SLOT_ITEM_COUNT;
		}

    public boolean hasParameterSlot() {
        return canAcceptParameter();
    }

    public boolean isStopControlNode() {
        return type == NodeType.STOP_CHAIN || type == NodeType.STOP_ALL;
    }

    public boolean usesMinimalNodePresentation() {
        return NodeCatalog.usesMinimalNodePresentation(type)
            || type == NodeType.ROUTINE_CALL && !routineMetadata.hasRoutineArguments()
            || type == NodeType.ROUTINE_ENTRY;
    }

    public boolean canAcceptParameterAt(int slotIndex) {
        if (!canAcceptParameter()) {
            return false;
        }
        return slotIndex >= 0 && slotIndex < getParameterSlotCount();
    }

    public boolean canAcceptParameterNode(Node parameterNode, int slotIndex) {
        // Editor attachment is unrestricted by slot trait: any node usable as a parameter may be
        // dropped into any existing parameter slot. Runtime value validation stays strict elsewhere.
        return parameterNode != null
            && parameterNode != this
            && canAcceptParameterAt(slotIndex)
            && isUsableAsParameterType(parameterNode.getType());
    }

    public boolean isParameterSlotRequired(int slotIndex) {
        if (!canAcceptParameterAt(slotIndex)) {
            return false;
        }
        if (NodeTraitRegistry.isParameterSlotAlwaysRequired(type, slotIndex)) {
            return true;
        }
        if (type == NodeType.PLACE) {
            if (slotIndex == 0) {
                return false;
            }
            Node coordinateParameter = getAttachedParameter(slotIndex);
            if (coordinateParameter != null) {
                return true;
            }
            Node blockParameter = getAttachedParameter(0);
            if (blockParameter != null && blockParameter.getType() == NodeType.VARIABLE) {
                return false;
            }
            // Only block placement targets provide coordinates for slot 1 conflicts.
            return blockParameter == null || !blockParameterProvidesPlacementCoordinates(blockParameter);
        }
        if (type == NodeType.PLACE_HAND) {
            return false;
        }
        if (type == NodeType.EVENT_FUNCTION) {
            return false;
        }
        if (type == NodeType.ROUTINE_CALL) {
            NodeGraphData.RoutineArgumentData argument = routineMetadata.getRoutineArgument(slotIndex);
            return !Boolean.TRUE.equals(argument.getOrphaned())
                && Boolean.TRUE.equals(argument.getRequired())
                && (argument.getDefaultValue() == null || argument.getDefaultValue().isBlank());
        }
        // Requiredness is catalog-owned. Any slot that reaches this point was explicitly
        // declared optional and has no node-specific rule making it conditional.
        return false;
    }


    boolean isParameterSupported(Node parameter, int slotIndex) {
        return NodeCompatibility.canAttachToSlot(this, parameter, NodeSlotType.PARAMETER, slotIndex);
    }

    public boolean canAcceptActionNode() {
        return NodeCompatibility.canHostSlot(type, NodeSlotType.ACTION);
    }

    public boolean hasActionSlot() {
        return canAcceptActionNode();
    }

    public boolean hasAttachedSensor() {
        return attachments.getAttachedSensor() != null;
    }

    public Node getAttachedSensor() {
        return attachments.getAttachedSensor();
    }

    public boolean isAttachedToControl() {
        return attachments.getParentControl() != null;
    }

    public Node getParentControl() {
        return attachments.getParentControl();
    }

    public String getAttachedSensorId() {
        return attachments.getAttachedSensor() != null ? attachments.getAttachedSensor().getId() : null;
    }

    public String getParentControlId() {
        return attachments.getParentControl() != null ? attachments.getParentControl().getId() : null;
    }

    public boolean hasAttachedParameter() {
        return attachments.hasAttachedParameters();
    }

    public Node getAttachedParameter() {
        return getAttachedParameter(0);
    }

    public Node getAttachedParameter(int slotIndex) {
        if (slotIndex < 0) {
            return null;
        }
        return attachments.getAttachedParameter(slotIndex);
    }

    List<Integer> getAttachedParameterSlotIndices() {
        return new ArrayList<>(attachments.getAttachedParameterSlotIndices());
    }

    Iterable<Node> getAttachedParameterNodes() {
        return attachments.getAttachedParameterNodes();
    }

    public Node getParentParameterHost() {
        return attachments.getParentParameterHost();
    }

    public int getParentParameterSlotIndex() {
        return attachments.getParentParameterSlotIndex();
    }

    public Map<Integer, Node> getAttachedParameters() {
        return attachments.getAttachedParametersView();
    }

    NodeAttachments getAttachments() {
        return attachments;
    }

    NodeRuntimeState getRuntimeState() {
        return runtimeState;
    }

    public String getAttachedParameterId() {
        Node parameter = getAttachedParameter();
        return parameter != null ? parameter.getId() : null;
    }

    public String getParentParameterHostId() {
        return attachments.getParentParameterHost() != null ? attachments.getParentParameterHost().getId() : null;
    }

    public void setOwningStartNode(Node startNode) {
        runtimeState.owningStartNode = startNode;
    }

    public Node getOwningStartNode() {
        return runtimeState.owningStartNode;
    }

    Node resolveExecutionStartNode() {
        if (runtimeState.owningStartNode != null) {
            return runtimeState.owningStartNode;
        }
        if (attachments.getParentParameterHost() != null) {
            Node hostStart = attachments.getParentParameterHost().resolveExecutionStartNode();
            if (hostStart != null) {
                return hostStart;
            }
        }
        if (attachments.getParentControl() != null) {
            Node controlStart = attachments.getParentControl().resolveExecutionStartNode();
            if (controlStart != null) {
                return controlStart;
            }
        }
        if (attachments.getParentActionControl() != null) {
            Node actionStart = attachments.getParentActionControl().resolveExecutionStartNode();
            if (actionStart != null) {
                return actionStart;
            }
        }
        return null;
    }

    public int getStartNodeNumber() {
        return runtimeState.startNodeNumber;
    }

    public void setStartNodeNumber(int startNodeNumber) {
        runtimeState.startNodeNumber = startNodeNumber;
    }

    public StartLaunchMode getStartLaunchMode() {
        return runtimeState.startLaunchMode == null ? StartLaunchMode.MANUAL : runtimeState.startLaunchMode;
    }

    public void setStartLaunchMode(StartLaunchMode startLaunchMode) {
        runtimeState.startLaunchMode = startLaunchMode == null ? StartLaunchMode.MANUAL : startLaunchMode;
    }

    public StartScreenTarget getStartScreenTarget() {
        return runtimeState.startScreenTarget == null ? StartScreenTarget.ANY : runtimeState.startScreenTarget;
    }

    public void setStartScreenTarget(StartScreenTarget startScreenTarget) {
        runtimeState.startScreenTarget = startScreenTarget == null ? StartScreenTarget.ANY : startScreenTarget;
    }

    public boolean isGotoAllowBreakWhileExecuting() {
        if (type != NodeType.GOTO && type != NodeType.TRAVEL) {
            return false;
        }
        com.pathmind.data.SettingsManager.Settings settings = com.pathmind.data.SettingsManager.getCurrent();
        return settings.gotoAllowBreakWhileExecuting != null && settings.gotoAllowBreakWhileExecuting;
    }

    public void setGotoAllowBreakWhileExecuting(boolean gotoAllowBreakWhileExecuting) {
        if (type != NodeType.GOTO && type != NodeType.TRAVEL) {
            return;
        }
        this.gotoAllowBreakWhileExecuting = gotoAllowBreakWhileExecuting;
    }

    public boolean isGotoAllowPlaceWhileExecuting() {
        if (type != NodeType.GOTO && type != NodeType.TRAVEL) {
            return false;
        }
        com.pathmind.data.SettingsManager.Settings settings = com.pathmind.data.SettingsManager.getCurrent();
        return settings.gotoAllowPlaceWhileExecuting != null && settings.gotoAllowPlaceWhileExecuting;
    }

    public void setGotoAllowPlaceWhileExecuting(boolean gotoAllowPlaceWhileExecuting) {
        if (type != NodeType.GOTO && type != NodeType.TRAVEL) {
            return;
        }
        this.gotoAllowPlaceWhileExecuting = gotoAllowPlaceWhileExecuting;
    }

    public boolean isKeyPressedActivatesInGuis() {
        if (type != NodeType.SENSOR_KEY_PRESSED) {
            return true;
        }
        com.pathmind.data.SettingsManager.Settings settings = com.pathmind.data.SettingsManager.getCurrent();
        return settings.keyPressedActivatesInGuis == null || settings.keyPressedActivatesInGuis;
    }

    public void setKeyPressedActivatesInGuis(boolean keyPressedActivatesInGuis) {
        if (type != NodeType.SENSOR_KEY_PRESSED) {
            return;
        }
        this.keyPressedActivatesInGuis = keyPressedActivatesInGuis;
    }

    public boolean hasAttachedActionNode() {
        return attachments.getAttachedActionNode() != null;
    }

    public Node getAttachedActionNode() {
        return attachments.getAttachedActionNode();
    }

    public boolean isAttachedToActionControl() {
        return attachments.getParentActionControl() != null;
    }

    public Node getParentActionControl() {
        return attachments.getParentActionControl();
    }

    public String getAttachedActionId() {
        return attachments.getAttachedActionNode() != null ? attachments.getAttachedActionNode().getId() : null;
    }

    public String getParentActionControlId() {
        return attachments.getParentActionControl() != null ? attachments.getParentActionControl().getId() : null;
    }

    public void setActiveRepeatUntilGuard(Node guard) {
        this.runtimeState.activeRepeatUntilGuard = guard;
    }

    /**
     * Returns the latest sensor reading relevant to this running node.
     *
     * <p>Control nodes report their attached sensor directly. Actions running inside a
     * Repeat Until body report the guard's attached sensor, so the HUD keeps showing
     * the condition while the action is being interrupted.</p>
     */
    public SensorRuntimeReading getRuntimeSensorReading() {
        Node sensor = null;
        if (isSensorNode()) {
            sensor = this;
        } else if (attachments.getAttachedSensor() != null) {
            sensor = attachments.getAttachedSensor();
        } else {
            Node guard = runtimeState.activeRepeatUntilGuard;
            if (guard != null && guard.attachments.getAttachedSensor() != null) {
                sensor = guard.attachments.getAttachedSensor();
            }
        }
        if (sensor == null || !sensor.runtimeState.hasSensorResult) {
            return null;
        }
        return new SensorRuntimeReading(
            sensor.getType().getDisplayName(),
            sensor.runtimeState.lastSensorResult,
            sensor.runtimeState.lastSensorUpdatedAt
        );
    }

    public record SensorRuntimeReading(String sensorName, boolean result, long updatedAt) {
    }

    public int getInputSocketCount() {
        if (type == NodeType.START || type == NodeType.EVENT_FUNCTION || type == NodeType.ROUTINE_ENTRY || isSensorNode() || isParameterNode() || isStickyNote()) {
            return 0;
        }
        if (type == NodeType.CONTROL_JOIN_ANY || type == NodeType.CONTROL_JOIN_ALL) {
            return 2;
        }
        return 1;
    }

    public int getOutputSocketCount() {
        if (isSensorNode() || isParameterNode() || isStickyNote()) {
            return 0;
        }
        return switch (type) {
            case NodeType.STOP_ALL, NodeType.CONTROL_FOREVER -> 0;
            case NodeType.CONTROL_IF_ELSE, NodeType.CONTROL_FORK -> 2;
            default -> 1;
        };
    }

    public int getOutputSocketColor(int socketIndex) {
        if (type == NodeType.CONTROL_IF_ELSE) {
            if (socketIndex == 0) {
                return 0xFF4CAF50; // Green for true branch
            } else if (socketIndex == 1) {
                return 0xFFF44336; // Red for false branch
            }
        }
        return getColor();
    }

    public int getColor() {
        if (type == null) {
            return UITheme.BORDER_DEFAULT;
        }
        return NodeCatalog.graphColor(
            type,
            BaritoneDependencyChecker.isBaritoneApiPresent(),
            UiUtilsProxy.isAvailable());
    }

    public int getSocketY(int socketIndex, boolean isInput) {
        return NodeSlotLayout.socketY(this, socketIndex, isInput);
    }
    
    public int getSocketX(boolean isInput) {
        return NodeSlotLayout.socketX(this, isInput);
    }
    
    public void setNextOutputSocket(int socketIndex) {
        this.runtimeState.nextOutputSocket = socketIndex < 0 ? NO_OUTPUT : Math.max(0, socketIndex);
    }

    public int consumeNextOutputSocket() {
        int value = this.runtimeState.nextOutputSocket;
        this.runtimeState.nextOutputSocket = 0;
        return value;
    }

    public boolean shouldExecuteRepeatAttachedAction() {
        return type == NodeType.CONTROL_REPEAT && runtimeState.repeatExecuteAttachedAction;
    }

    public int getRepeatLoopCount() {
        if (type != NodeType.CONTROL_REPEAT) {
            return 0;
        }
        return Math.max(0, getIntParameter("Count", 1));
    }

    public void clearLoopRuntimeState() {
        runtimeState.repeatRemainingIterations = 0;
        runtimeState.repeatActive = false;
        runtimeState.repeatExecuteAttachedAction = false;
    }
    
    public boolean isSocketClicked(int mouseX, int mouseY, int socketIndex, boolean isInput) {
        return NodeSlotLayout.isSocketClicked(this, mouseX, mouseY, socketIndex, isInput);
    }

    public int getSensorSlotLeft() {
        return NodeSlotLayout.sensorSlotLeft(this);
    }

    private int getSlotAreaStartY() {
        return NodeSlotLayout.slotAreaStartY(this);
    }

    public int getSensorSlotTop() {
        return NodeSlotLayout.sensorSlotTop(this);
    }

    public int getSensorSlotWidth() {
        return NodeSlotLayout.sensorSlotWidth(this);
    }

    public int getSensorSlotHeight() {
        return NodeSlotLayout.sensorSlotHeight(this);
    }

    public boolean isPointInsideSensorSlot(int pointX, int pointY) {
        return NodeSlotLayout.isPointInsideSensorSlot(this, pointX, pointY);
    }

    public int getParameterSlotCount() {
        if (!hasParameterSlot()) {
            return 0;
        }
        if (isExpandableBooleanOperator()) {
            return Math.max(2, dynamicBooleanOperatorSlotCount);
        }
        if (type == NodeType.ROUTINE_CALL) return routineMetadata.getRoutineArgumentCount();
        return NodeTraitRegistry.getParameterSlotCount(type);
    }

    public int getParameterSlotLeft() {
        return NodeSlotLayout.parameterSlotLeft(this);
    }

    public int getParameterSlotLeft(int slotIndex) {
        return NodeSlotLayout.parameterSlotLeft(this, slotIndex);
    }

    public int getParameterSlotTop(int slotIndex) {
        return NodeSlotLayout.parameterSlotTop(this, slotIndex);
    }

    @Deprecated
    public int getParameterSlotTop() {
        return getParameterSlotTop(0);
    }

    public String getParameterSlotLabel(int slotIndex) {
        if (isComparisonOperator() && !isExpandableBooleanOperator()) {
            return "";
        }
        if (type == NodeType.ROUTINE_CALL && slotIndex >= 0 && slotIndex < routineMetadata.getRoutineArgumentCount()) {
            NodeGraphData.RoutineArgumentData argument = routineMetadata.getRoutineArgument(slotIndex);
            String label = argument.getLabel() == null || argument.getLabel().isBlank() ? "Input" : argument.getLabel();
            return Boolean.TRUE.equals(argument.getOrphaned()) ? "Removed: " + label : label;
        }
        return NodeTraitRegistry.getParameterSlotLabel(type, slotIndex);
    }

    public int getParameterSlotWidth() {
        return NodeSlotLayout.parameterSlotWidth(this);
    }

    public int getParameterSlotWidth(int slotIndex) {
        return NodeSlotLayout.parameterSlotWidth(this, slotIndex);
    }

    public int getParameterSlotHeight(int slotIndex) {
        return NodeSlotLayout.parameterSlotHeight(this, slotIndex);
    }

    @Deprecated
    public int getParameterSlotHeight() {
        return getParameterSlotHeight(0);
    }

    int getParameterSlotsBottom() {
        return NodeSlotLayout.parameterSlotsBottom(this);
    }

    public boolean hasCoordinateInputFields() {
        return inlineFieldLayout.hasCoordinateInputFields();
    }

    public String[] getCoordinateFieldAxes() {
        return inlineFieldLayout.getCoordinateFieldAxes();
    }

    public int getCoordinateFieldDisplayHeight() {
        return inlineFieldLayout.getCoordinateFieldDisplayHeight();
    }

    public boolean showsModeFieldAboveParameterSlot() {
        return inlineFieldLayout.showsModeFieldAboveParameterSlot();
    }

    public int getModeFieldDisplayHeight() {
        return inlineFieldLayout.getModeFieldDisplayHeight();
    }

    public int getModeFieldTop() {
        return inlineFieldLayout.getModeFieldTop();
    }

    public int getModeFieldLeft() {
        return inlineFieldLayout.getModeFieldLeft();
    }

    public int getModeFieldWidth() {
        return inlineFieldLayout.getModeFieldWidth();
    }

    public int getModeFieldHeight() {
        return inlineFieldLayout.getModeFieldHeight();
    }

    public String getModeFieldLabelText() {
        return inlineFieldLayout.getModeFieldLabelText();
    }

    public boolean isSensorPositionSingleAxisMode() {
        if (type != NodeType.SENSOR_POSITION_OF) {
            return false;
        }
        return mode == NodeMode.SENSOR_POSITION_X
            || mode == NodeMode.SENSOR_POSITION_Y
            || mode == NodeMode.SENSOR_POSITION_Z;
    }

    public String getSensorPositionComponentKey() {
        if (type != NodeType.SENSOR_POSITION_OF) {
            return "";
        }
        return switch (mode) {
            case NodeMode.SENSOR_POSITION_X -> "X";
            case NodeMode.SENSOR_POSITION_Y -> "Y";
            case NodeMode.SENSOR_POSITION_Z -> "Z";
            default -> "";
        };
    }

    public boolean isSensorLookSingleAxisMode() {
        if (type != NodeType.SENSOR_LOOK_DIRECTION) {
            return false;
        }
        return mode == NodeMode.SENSOR_LOOK_YAW
            || mode == NodeMode.SENSOR_LOOK_PITCH;
    }

    public String getSensorLookComponentKey() {
        if (type != NodeType.SENSOR_LOOK_DIRECTION) {
            return "";
        }
        return switch (mode) {
            case NodeMode.SENSOR_LOOK_YAW -> "Yaw";
            case NodeMode.SENSOR_LOOK_PITCH -> "Pitch";
            default -> "";
        };
    }

    public NodeType getResolvedValueType() {
        return switch (type) {
            case LIST_ITEM -> {
                ExecutionManager.RuntimeList runtimeList = resolveRuntimeList(this);
                NodeType elementType = runtimeList != null ? runtimeList.getElementType() : null;
                yield elementType == NodeType.PARAM_GUI
                    ? NodeType.PARAM_INVENTORY_SLOT
                    : elementType != null
                        ? elementType
                    : NodeType.LIST_ITEM;
            }
            case SENSOR_POSITION_OF -> isSensorPositionSingleAxisMode() ? NodeType.PARAM_AMOUNT : NodeType.PARAM_COORDINATE;
            case SENSOR_DISTANCE_BETWEEN, SENSOR_IS_ON_GROUND -> NodeType.PARAM_DISTANCE;
            case SENSOR_TARGETED_BLOCK_FACE -> NodeType.PARAM_BLOCK_FACE;
            case SENSOR_TARGETED_BLOCK -> NodeType.PARAM_BLOCK;
            case SENSOR_TARGETED_ENTITY -> NodeType.PARAM_ENTITY;
            case SENSOR_LOOK_DIRECTION -> isSensorLookSingleAxisMode() ? NodeType.PARAM_AMOUNT : NodeType.PARAM_ROTATION;
            case SENSOR_CURRENT_HAND -> NodeType.PARAM_INVENTORY_SLOT;
            case SENSOR_CURRENT_GUI -> NodeType.PARAM_GUI;
            case SENSOR_SLOT_ITEM_COUNT, LIST_LENGTH, OPERATOR_RANDOM, OPERATOR_MOD -> NodeType.PARAM_AMOUNT;
            case SENSOR_FIND_TRADE -> NodeType.PARAM_VILLAGER_TRADE;
            case CALCULATE -> NodeType.PARAM_AMOUNT;
            default -> type;
        };
    }

    public int getCoordinateFieldLabelTop() {
        return inlineFieldLayout.getCoordinateFieldLabelTop();
    }

    public int getCoordinateFieldInputTop() {
        return inlineFieldLayout.getCoordinateFieldInputTop();
    }

    public int getCoordinateFieldLabelHeight() {
        return inlineFieldLayout.getCoordinateFieldLabelHeight();
    }

    public int getCoordinateFieldHeight() {
        return inlineFieldLayout.getCoordinateFieldHeight();
    }

    public int getCoordinateFieldWidth() {
        return inlineFieldLayout.getCoordinateFieldWidth();
    }

    public int getCoordinateFieldSpacing() {
        return inlineFieldLayout.getCoordinateFieldSpacing();
    }

    public int getCoordinateFieldStartX() {
        return inlineFieldLayout.getCoordinateFieldStartX();
    }

    public int getCoordinateFieldTotalWidth() {
        return inlineFieldLayout.getCoordinateFieldTotalWidth();
    }

    public boolean hasScreenCoordinatePickerButton() {
        return inlineFieldLayout.hasScreenCoordinatePickerButton();
    }

    public int getScreenCoordinatePickerButtonTop() {
        return inlineFieldLayout.getScreenCoordinatePickerButtonTop();
    }

    public int getScreenCoordinatePickerButtonLeft() {
        return inlineFieldLayout.getScreenCoordinatePickerButtonLeft();
    }

    public int getScreenCoordinatePickerButtonWidth() {
        return inlineFieldLayout.getScreenCoordinatePickerButtonWidth();
    }

    public int getScreenCoordinatePickerButtonHeight() {
        return inlineFieldLayout.getScreenCoordinatePickerButtonHeight();
    }

    public boolean hasAmountInputField() {
        return inlineFieldLayout.hasAmountInputField();
    }

    public boolean hasRandomRoundingField() {
        return inlineFieldLayout.hasRandomRoundingField();
    }

    public boolean hasSchematicDropdownField() {
        return inlineFieldLayout.hasSchematicDropdownField();
    }

    public boolean hasStopTargetInputField() {
        return inlineFieldLayout.hasStopTargetInputField();
    }

    public boolean hasVariableInputField() {
        return inlineFieldLayout.hasVariableInputField();
    }

    public String getStopTargetFieldParameterKey() {
        if (type == NodeType.RUN_PRESET || type == NodeType.TEMPLATE) {
            return "Preset";
        }
        return "StartNumber";
    }

    public String getVariableFieldParameterKey() {
        return switch (type) {
            case CREATE_LIST, ADD_TO_LIST, REMOVE_FIRST_FROM_LIST, REMOVE_LAST_FROM_LIST, REMOVE_LIST_ITEM, REMOVE_FROM_LIST, LIST_LENGTH -> "List";
            default -> "Variable";
        };
    }

    public int getAmountFieldDisplayHeight() {
        return inlineFieldLayout.getAmountFieldDisplayHeight();
    }

    public int getAmountFieldLabelTop() {
        return inlineFieldLayout.getAmountFieldLabelTop();
    }

    public int getAmountFieldInputTop() {
        return inlineFieldLayout.getAmountFieldInputTop();
    }

    public int getAmountFieldLabelHeight() {
        return inlineFieldLayout.getAmountFieldLabelHeight();
    }

    public String getAmountFieldLabel() {
        return inlineFieldLayout.getAmountFieldLabel();
    }

    public String getAmountParameterKey() {
        return inlineFieldLayout.getAmountParameterKey();
    }

    public int getAmountFieldHeight() {
        return inlineFieldLayout.getAmountFieldHeight();
    }

    public int getAmountFieldWidth() {
        return inlineFieldLayout.getAmountFieldWidth();
    }

    public int getAmountFieldLeft() {
        return inlineFieldLayout.getAmountFieldLeft();
    }

    public boolean hasAmountToggle() {
        return inlineFieldLayout.hasAmountToggle();
    }

    public boolean isAmountInputEnabled() {
        return NodeParameterRepair.isAmountInputEnabled(this);
    }

    public void setAmountInputEnabled(boolean enabled) {
        NodeParameterRepair.setAmountInputEnabled(this, enabled);
    }

    public int getAmountToggleLeft() {
        return inlineFieldLayout.getAmountToggleLeft();
    }

    public int getAmountToggleTop() {
        return inlineFieldLayout.getAmountToggleTop();
    }

    public int getAmountToggleWidth() {
        return inlineFieldLayout.getAmountToggleWidth();
    }

    public int getAmountToggleHeight() {
        return inlineFieldLayout.getAmountToggleHeight();
    }

    public int getRandomRoundingFieldDisplayHeight() {
        return inlineFieldLayout.getRandomRoundingFieldDisplayHeight();
    }

    public int getRandomRoundingFieldLabelTop() {
        return inlineFieldLayout.getRandomRoundingFieldLabelTop();
    }

    public int getRandomRoundingFieldInputTop() {
        return inlineFieldLayout.getRandomRoundingFieldInputTop();
    }

    public int getRandomRoundingFieldLabelHeight() {
        return inlineFieldLayout.getRandomRoundingFieldLabelHeight();
    }

    public int getRandomRoundingFieldHeight() {
        return inlineFieldLayout.getRandomRoundingFieldHeight();
    }

    public int getRandomRoundingFieldWidth() {
        return inlineFieldLayout.getRandomRoundingFieldWidth();
    }

    public int getRandomRoundingFieldLeft() {
        return inlineFieldLayout.getRandomRoundingFieldLeft();
    }

    public boolean hasRandomRoundingToggle() {
        return inlineFieldLayout.hasRandomRoundingToggle();
    }

    public int getRandomRoundingToggleLeft() {
        return inlineFieldLayout.getRandomRoundingToggleLeft();
    }

    public int getRandomRoundingToggleTop() {
        return inlineFieldLayout.getRandomRoundingToggleTop();
    }

    public int getRandomRoundingToggleWidth() {
        return inlineFieldLayout.getRandomRoundingToggleWidth();
    }

    public int getRandomRoundingToggleHeight() {
        return inlineFieldLayout.getRandomRoundingToggleHeight();
    }

    public boolean isRandomRoundingEnabled() {
        return NodeParameterRepair.isRandomRoundingEnabled(this);
    }

    public void setRandomRoundingEnabled(boolean enabled) {
        NodeParameterRepair.setRandomRoundingEnabled(this, enabled);
    }

    public String getRandomRoundingMode() {
        return NodeParameterRepair.getRandomRoundingMode(this);
    }

    public String getRandomRoundingModeDisplay() {
        return NodeParameterRepair.getRandomRoundingModeDisplay(this);
    }

    public void setRandomRoundingMode(String mode) {
        NodeParameterRepair.setRandomRoundingMode(this, mode);
    }

    String normalizeOperation(String value) {
        if (value == null) {
            return "+";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "+";
        }
        String lowered = trimmed.toLowerCase(Locale.ROOT);
        return switch (lowered) {
            case "+", "add", "plus" -> "+";
            case "-", "subtract", "minus" -> "-";
            case "*", "x", "multiply", "times" -> "*";
            case "/", "divide" -> "/";
            case "%", "mod", "modulo" -> "%";
            default -> "+";
        };
    }

    public void ensureVillagerTradeNumberParameter() {
        NodeParameterRepair.ensureVillagerTradeNumberParameter(this);
    }

    public void ensureCreateListRadiusParameters() {
        NodeParameterRepair.ensureCreateListRadiusParameters(this);
    }

    public void repairSerializedParameters() {
        NodeParameterRepair.repairSerializedParameters(this);
    }

    boolean shouldUseLegacyVillagerTradeSelection() {
        if (!NodeParameterRepair.usesVillagerTradeNumberField(type)) {
            return false;
        }
        Node attached = resolveSensorParameterNode(getAttachedParameter(), 0);
        if (attached == null || !providesTrait(attached, NodeValueTrait.VILLAGER_TRADE)) {
            return false;
        }
        NodeParameter numberParam = getParameter("Number");
        return numberParam == null || !numberParam.isUserEdited();
    }

    int getConfiguredVillagerTradeNumber() {
        ensureVillagerTradeNumberParameter();
        return Math.max(1, getIntParameter("Number", 1));
    }

    int getConfiguredVillagerTradeCount() {
        ensureVillagerTradeNumberParameter();
        return Math.max(1, getIntParameter("Count", 1));
    }

    boolean isRandomRoundingParameter(NodeParameter parameter) {
        return NodeParameterRepair.isRandomRoundingParameter(this, parameter);
    }

    public int getSchematicFieldDisplayHeight() {
        return inlineFieldLayout.getSchematicFieldDisplayHeight();
    }

    public int getSchematicFieldLabelTop() {
        return inlineFieldLayout.getSchematicFieldLabelTop();
    }

    public int getSchematicFieldInputTop() {
        return inlineFieldLayout.getSchematicFieldInputTop();
    }

    public int getSchematicFieldLabelHeight() {
        return inlineFieldLayout.getSchematicFieldLabelHeight();
    }

    public int getSchematicFieldHeight() {
        return inlineFieldLayout.getSchematicFieldHeight();
    }

    public int getSchematicFieldWidth() {
        return inlineFieldLayout.getSchematicFieldWidth();
    }

    public int getSchematicFieldLeft() {
        return inlineFieldLayout.getSchematicFieldLeft();
    }

    public int getStopTargetFieldDisplayHeight() {
        return inlineFieldLayout.getStopTargetFieldDisplayHeight();
    }

    public int getStopTargetFieldLabelTop() {
        return inlineFieldLayout.getStopTargetFieldLabelTop();
    }

    public int getStopTargetFieldInputTop() {
        return inlineFieldLayout.getStopTargetFieldInputTop();
    }

    public int getStopTargetFieldLabelHeight() {
        return inlineFieldLayout.getStopTargetFieldLabelHeight();
    }

    public int getStopTargetFieldHeight() {
        return inlineFieldLayout.getStopTargetFieldHeight();
    }

    public int getStopTargetFieldWidth() {
        return inlineFieldLayout.getStopTargetFieldWidth();
    }

    public int getStopTargetFieldLeft() {
        return inlineFieldLayout.getStopTargetFieldLeft();
    }

    public int getVariableFieldDisplayHeight() {
        return inlineFieldLayout.getVariableFieldDisplayHeight();
    }

    public int getVariableFieldLabelTop() {
        return inlineFieldLayout.getVariableFieldLabelTop();
    }

    public int getVariableFieldInputTop() {
        return inlineFieldLayout.getVariableFieldInputTop();
    }

    public int getVariableFieldLabelHeight() {
        return inlineFieldLayout.getVariableFieldLabelHeight();
    }

    public int getVariableFieldHeight() {
        return inlineFieldLayout.getVariableFieldHeight();
    }

    public int getVariableFieldWidth() {
        return inlineFieldLayout.getVariableFieldWidth();
    }

    public int getVariableFieldLeft() {
        return inlineFieldLayout.getVariableFieldLeft();
    }

    public boolean isPointInsideParameterSlot(int pointX, int pointY) {
        return getParameterSlotIndexAt(pointX, pointY) >= 0;
    }

    public int getParameterSlotIndexAt(int pointX, int pointY) {
        return NodeSlotLayout.parameterSlotIndexAt(this, pointX, pointY);
    }

    public void updateAttachedParameterPositions() {
        NodeSlotLayout.updateAttachedParameterPositions(this);
    }

    void updateAttachedParameterPosition(int slotIndex) {
        NodeSlotLayout.updateAttachedParameterPosition(this, slotIndex);
    }

    public int getActionSlotLeft() {
        return NodeSlotLayout.actionSlotLeft(this);
    }

    public int getActionSlotTop() {
        return NodeSlotLayout.actionSlotTop(this);
    }

    public int getActionSlotWidth() {
        return NodeSlotLayout.actionSlotWidth(this);
    }

    public int getActionSlotHeight() {
        return NodeSlotLayout.actionSlotHeight(this);
    }

    public boolean isPointInsideActionSlot(int pointX, int pointY) {
        return NodeSlotLayout.isPointInsideActionSlot(this, pointX, pointY);
    }

    public void updateAttachedSensorPosition() {
        NodeSlotLayout.updateAttachedSensorPosition(this);
    }

    public void updateAttachedActionPosition() {
        NodeSlotLayout.updateAttachedActionPosition(this);
    }

    public boolean attachSensor(Node sensor) {
        return attachmentCoordinator.attachSensor(sensor);
    }

    public void detachSensor() {
        attachmentCoordinator.detachSensor();
    }

    public boolean attachParameter(Node parameter) {
        return attachParameter(parameter, 0);
    }

    public boolean attachParameter(Node parameter, int slotIndex) {
        return attachmentCoordinator.attachParameter(parameter, slotIndex, false);
    }

    /**
     * Strict attachment that enforces slot trait compatibility. Used for runtime value substitution
     * where an incompatible resolved value must be rejected. Editor and graph-restore paths use the
     * unrestricted two-arg form, so any node usable as a parameter can occupy any existing slot.
     */
    public boolean attachParameterStrict(Node parameter, int slotIndex) {
        return attachmentCoordinator.attachParameter(parameter, slotIndex, true);
    }

    public void detachParameter() {
        detachParameter(0);
    }

    public void detachParameter(int slotIndex) {
        attachmentCoordinator.detachParameter(slotIndex);
    }

    public boolean addBooleanOperatorSlot() {
        if (!isExpandableBooleanOperator()) {
            return false;
        }
        dynamicBooleanOperatorSlotCount = Math.min(32, getParameterSlotCount() + 1);
        recalculateDimensions();
        updateAttachedParameterPositions();
        updateParentControlLayout();
        return true;
    }

    public boolean removeBooleanOperatorSlot() {
        if (!isExpandableBooleanOperator() || getParameterSlotCount() <= 2) {
            return false;
        }
        int removedSlot = getParameterSlotCount() - 1;
        detachParameter(removedSlot);
        dynamicBooleanOperatorSlotCount = Math.max(2, removedSlot);
        recalculateDimensions();
        updateAttachedParameterPositions();
        updateParentControlLayout();
        return true;
    }

    public void setBooleanOperatorSlotCount(Integer slotCount) {
        if (!isExpandableBooleanOperator()) {
            return;
        }
        dynamicBooleanOperatorSlotCount = Math.max(2, Math.min(32, slotCount == null ? 2 : slotCount));
        recalculateDimensions();
        updateAttachedParameterPositions();
    }

    private void updateParentControlLayout() {
        attachmentCoordinator.updateParentControlLayout();
    }

    void notifyParentParameterHostOfResize() {
        attachmentCoordinator.notifyParentParameterHostOfResize();
    }

    void onAttachedParameterResized(int slotIndex) {
        attachmentCoordinator.onAttachedParameterResized(slotIndex);
    }

    void notifyParentActionControlOfResize() {
        attachmentCoordinator.notifyParentActionControlOfResize();
    }

    void onAttachedActionResized() {
        attachmentCoordinator.onAttachedActionResized();
    }

    void notifyParentControlOfResize() {
        attachmentCoordinator.notifyParentControlOfResize();
    }

    void onAttachedSensorResized() {
        attachmentCoordinator.onAttachedSensorResized();
    }

    boolean applyParameterValuesFromMap(Map<String, String> values) {
        return parameterValues.applyParameterValuesFromMap(values);
    }

    Map<String, String> adjustParameterValuesForSlot(Map<String, String> values, int slotIndex, Node parameterNode) {
        return parameterValues.adjustParameterValuesForSlot(values, slotIndex, parameterNode);
    }

    void refreshAttachedParameterValues() {
        parameterValues.refreshAttachedParameterValues();
    }

    private boolean canHandleParameterRuntime(Node parameter, int slotIndex) {
        if (parameter == null || !parameter.isParameterNode()) {
            return false;
        }
        EnumSet<ParameterUsage> usages = getSupportedParameterUsages(slotIndex);
        if (usages.isEmpty()) {
            return false;
        }
        NodeType parameterType = parameter.getType();
        for (ParameterUsage usage : usages) {
            if (parameterSupportsUsage(parameterType, usage)) {
                return true;
            }
        }
        return false;
    }

    private EnumSet<ParameterUsage> getSupportedParameterUsages(int slotIndex) {
        if (!canAcceptParameterAt(slotIndex)) {
            return EnumSet.noneOf(ParameterUsage.class);
        }
        return switch (type) {
            case GOTO, TRAVEL, GOAL, BUILD, EXPLORE, FOLLOW, PATH, INTERACT -> {
                if (type == NodeType.GOTO || type == NodeType.TRAVEL || type == NodeType.GOAL) {
                    yield EnumSet.of(ParameterUsage.POSITION, ParameterUsage.LOOK_ORIENTATION);
                }
                yield EnumSet.of(ParameterUsage.POSITION);
            }
            case LOOK -> EnumSet.of(ParameterUsage.LOOK_ORIENTATION, ParameterUsage.POSITION);
            case WALK -> {
                if (slotIndex == 0) {
                    yield EnumSet.of(ParameterUsage.LOOK_ORIENTATION);
                }
                yield EnumSet.noneOf(ParameterUsage.class);
            }
            case BREAK -> EnumSet.of(ParameterUsage.POSITION);
            case PLACE, PLACE_HAND -> {
                if (slotIndex == 0 || slotIndex == 1) {
                    yield EnumSet.of(ParameterUsage.POSITION);
                }
                yield EnumSet.noneOf(ParameterUsage.class);
            }
            default -> EnumSet.noneOf(ParameterUsage.class);
        };
    }

    private boolean parameterSupportsUsage(NodeType parameterType, ParameterUsage usage) {
        if (parameterType == null || usage == null) {
            return false;
        }
        return switch (usage) {
            case POSITION -> parameterProvidesCoordinates(parameterType);
            case LOOK_ORIENTATION -> {
                if (parameterProvidesCoordinates(parameterType)) {
                    yield true;
                }
                EnumSet<NodeValueTrait> traits = NodeTraitRegistry.getProvidedTraits(parameterType);
                yield traits.contains(NodeValueTrait.DIRECTION)
                    || traits.contains(NodeValueTrait.ROTATION)
                    || (type == NodeType.LOOK && traits.contains(NodeValueTrait.NUMBER));
            }
            default -> false;
        };
    }

    public boolean canAcceptActionNode(Node node) {
        return attachmentCoordinator.canAcceptActionNode(node);
    }

    public boolean attachActionNode(Node node) {
        return attachmentCoordinator.attachActionNode(node);
    }

    public void detachActionNode() {
        attachmentCoordinator.detachActionNode();
    }

    public void setSocketsHidden(boolean hidden) {
        interactionState.setSocketsHidden(hidden);
    }

    public boolean shouldRenderSockets() {
        return !interactionState.areSocketsHidden();
    }

    /**
     * Get all parameters for this node
     */
    public List<NodeParameter> getParameters() {
        return parameters;
    }

    /**
     * Get a specific parameter by name
     */
    public NodeParameter getParameter(String name) {
        return parameterValues.getParameter(name);
    }

    public void setParameterValueAndPropagate(String name, String value) {
        parameterValues.setParameterValueAndPropagate(name, value);
    }

    boolean shouldShowStateParameter() {
        return NodeAttributeParameters.shouldShowStateParameter(this);
    }

    int getMaxParameterLabelLength() {
        return MAX_PARAMETER_LABEL_LENGTH;
    }

    public String getParameterDisplayName(NodeParameter parameter) {
        return NodeAttributeParameters.getParameterDisplayName(this, parameter);
    }

    public String getParameterDisplayValue(NodeParameter parameter) {
        return NodeAttributeParameters.getParameterDisplayValue(this, parameter);
    }

    public boolean isDirectionModeExact() {
        return NodeDirectionParameters.isDirectionModeExact(this, DIRECTION_MODE_EXACT, DIRECTION_MODE_CARDINAL, DEFAULT_DIRECTION_DISTANCE);
    }

    public boolean isDirectionModeCardinal() {
        return type == NodeType.PARAM_DIRECTION && !isDirectionModeExact();
    }

    public void setDirectionModeExact(boolean exact) {
        NodeDirectionParameters.setDirectionModeExact(this, exact, DIRECTION_MODE_EXACT, DIRECTION_MODE_CARDINAL, DEFAULT_DIRECTION_DISTANCE);
    }

    public boolean isBooleanModeLiteral() {
        return NodeBooleanParameters.isBooleanModeLiteral(this, BOOLEAN_MODE_LITERAL, BOOLEAN_MODE_VARIABLE);
    }

    public boolean isBooleanModeVariable() {
        return type == NodeType.PARAM_BOOLEAN && !isBooleanModeLiteral();
    }

    public void setBooleanModeLiteral(boolean literalMode) {
        NodeBooleanParameters.setBooleanModeLiteral(this, literalMode, BOOLEAN_MODE_LITERAL, BOOLEAN_MODE_VARIABLE);
    }

    void ensureBooleanParameters() {
        NodeBooleanParameters.ensureBooleanParameters(this, BOOLEAN_MODE_LITERAL);
    }

    public boolean isAttributeDetectionSensor() {
        return NodeAttributeParameters.isAttributeDetectionSensor(this);
    }

    public void normalizeAttributeDetectionParameters() {
        NodeAttributeParameters.normalizeAttributeDetectionParameters(this);
    }

    public String getParameterLabel(NodeParameter parameter) {
        return NodeAttributeParameters.getParameterLabel(this, parameter);
    }

    int getVisibleParameterLineCount() {
        return NodeAttributeParameters.getVisibleParameterLineCount(this);
    }

    public Map<String, String> exportParameterValues() {
        return NodeParameterValueExporter.exportParameterValues(this);
    }

    /**
     * Check if this node has parameters (Start nodes don't)
     */
    public boolean hasParameters() {
        return !parameters.isEmpty();
    }

    public boolean hasBooleanToggle() {
        return NodeCatalog.hasBooleanToggle(type);
    }

    public boolean getBooleanToggleValue() {
        if (type == NodeType.PARAM_BOOLEAN) {
            return resolveBooleanNodeValue(this).orElse(true);
        }
        return booleanToggleValue;
    }

    public void setBooleanToggleValue(boolean value) {
        if (type == NodeType.PARAM_BOOLEAN) {
            ensureBooleanParameters();
            NodeParameter parameter = getParameter("Toggle");
            if (parameter != null) {
                parameter.setStringValueFromUser(String.valueOf(value));
            }
            return;
        }
        this.booleanToggleValue = value;
    }

    public void toggleBooleanToggleValue() {
        if (type == NodeType.PARAM_BOOLEAN) {
            setBooleanToggleValue(!getBooleanToggleValue());
            return;
        }
        this.booleanToggleValue = !this.booleanToggleValue;
    }

    public int getBooleanToggleLeft() {
        return inlineFieldLayout.getBooleanToggleLeft();
    }

    public int getBooleanToggleTop() {
        return inlineFieldLayout.getBooleanToggleTop();
    }

    public int getBooleanToggleWidth() {
        return inlineFieldLayout.getBooleanToggleWidth();
    }

    public int getBooleanToggleHeight() {
        return inlineFieldLayout.getBooleanToggleHeight();
    }

    public int getBooleanToggleAreaHeight() {
        return inlineFieldLayout.getBooleanToggleAreaHeight();
    }

    public boolean supportsModeSelection() {
        NodeMode[] modes = NodeMode.getModesForNodeType(type);
        return modes.length > 0;
    }

    public boolean hasMessageInputFields() {
        return textContent.hasMessageInputFields();
    }

    public String getStickyNoteText() {
        return isStickyNote() ? (stickyNoteText == null ? "" : stickyNoteText) : "";
    }

    public void setStickyNoteText(String stickyNoteText) {
        if (!isStickyNote()) {
            return;
        }
        this.stickyNoteText = stickyNoteText == null ? "" : stickyNoteText;
        recalculateDimensions();
    }

    public int getStickyNoteWidthOverride() {
        return isStickyNote() ? layoutState.getStickyNoteWidthOverride() : 0;
    }

    public int getStickyNoteHeightOverride() {
        return isStickyNote() ? layoutState.getStickyNoteHeightOverride() : 0;
    }

    public void setStickyNoteSize(int width, int height) {
        if (!isStickyNote()) {
            return;
        }
        layoutState.setStickyNoteSize(
            Math.max(STICKY_NOTE_MIN_WIDTH, width),
            Math.max(STICKY_NOTE_MIN_HEIGHT, height));
        recalculateDimensions();
    }

    public int getStickyNoteHeaderHeight() {
        return isStickyNote() ? STICKY_NOTE_HEADER_HEIGHT : 0;
    }

    public int getStickyNoteBodyLeft() {
        return getX() + STICKY_NOTE_TEXT_MARGIN;
    }

    public int getStickyNoteBodyTop() {
        return getY() + STICKY_NOTE_HEADER_HEIGHT + STICKY_NOTE_TEXT_MARGIN;
    }

    public int getStickyNoteBodyWidth() {
        return Math.max(1, getWidth() - STICKY_NOTE_TEXT_MARGIN * 2);
    }

    public int getStickyNoteBodyHeight() {
        return Math.max(1, getHeight() - STICKY_NOTE_HEADER_HEIGHT - STICKY_NOTE_TEXT_MARGIN * 2);
    }

    public int getStickyNoteResizeHandleSize() {
        return STICKY_NOTE_HANDLE_SIZE;
    }

    public String getTemplateName() {
        return editorMetadata.getTemplateName();
    }

    public void setTemplateName(String templateName) {
        editorMetadata.setTemplateName(templateName);
    }

    public int getTemplateVersion() {
        return editorMetadata.getTemplateVersion();
    }

    public void setTemplateVersion(int templateVersion) {
        editorMetadata.setTemplateVersion(templateVersion);
    }

    public NodeGraphData getTemplateGraphData() {
        return editorMetadata.getTemplateGraphData();
    }

    public void setTemplateGraphData(NodeGraphData templateGraphData) {
        editorMetadata.setTemplateGraphData(templateGraphData);
    }

    public RuntimeValueScope getRuntimeValueScope() {
        return editorMetadata.getRuntimeValueScope();
    }

    /** Creates a user-facing editor node. Named runtime values default to global scope. */
    public static Node createForEditor(NodeType type, int x, int y) {
        return new Node(type, x, y);
    }

    public void setRuntimeValueScope(RuntimeValueScope runtimeValueScope) {
        editorMetadata.setRuntimeValueScope(runtimeValueScope);
    }

    public void toggleRuntimeValueScope() {
        editorMetadata.toggleRuntimeValueScope();
    }

    public boolean supportsRuntimeValueScope() {
        return editorMetadata.supportsRuntimeValueScope();
    }

    public int getMessageFieldCount() {
        return textContent.getMessageFieldCount();
    }

    public List<String> getMessageLines() {
        return textContent.getMessageLines();
    }

    public String getMessageLine(int index) {
        return textContent.getMessageLine(index);
    }

    public void setMessageLine(int index, String value) {
        textContent.setMessageLine(index, value);
    }

    public void setMessageLines(List<String> lines) {
        textContent.setMessageLines(lines);
    }

    public void addMessageLine(String value) {
        textContent.addMessageLine(value);
    }

    public boolean removeMessageLine(int index) {
        return textContent.removeMessageLine(index);
    }

    public boolean isMessageClientSide() {
        return textContent.isMessageClientSide();
    }

    public boolean hasMessageScopeToggle() {
        return textContent.hasMessageScopeToggle();
    }

    public void setMessageClientSide(boolean messageClientSide) {
        textContent.setMessageClientSide(messageClientSide);
    }

    public void toggleMessageClientSide() {
        textContent.toggleMessageClientSide();
    }

    public String getMessageFieldLabelText(int index) {
        return textContent.getMessageFieldLabelText(index);
    }

    public int getMessageFieldDisplayHeight() {
        return inlineFieldLayout.getMessageFieldDisplayHeight();
    }

    public int getMessageFieldLabelTop(int index) {
        return inlineFieldLayout.getMessageFieldLabelTop(index);
    }

    public int getMessageFieldInputTop(int index) {
        return inlineFieldLayout.getMessageFieldInputTop(index);
    }

    public int getMessageFieldLabelHeight() {
        return inlineFieldLayout.getMessageFieldLabelHeight();
    }

    public int getMessageFieldHeight() {
        return inlineFieldLayout.getMessageFieldHeight();
    }

    public int getMessageFieldWidth() {
        return inlineFieldLayout.getMessageFieldWidth();
    }

    public void setMessageFieldTextWidth(int textWidth) {
        inlineFieldLayout.setMessageFieldTextWidth(textWidth);
    }

    public void setParameterFieldWidthOverride(int fieldWidth) {
        inlineFieldLayout.setParameterFieldWidthOverride(fieldWidth);
    }

    public void setCoordinateFieldTextWidth(int textWidth) {
        inlineFieldLayout.setCoordinateFieldTextWidth(textWidth);
    }

    public void setAmountFieldTextWidth(int textWidth) {
        inlineFieldLayout.setAmountFieldTextWidth(textWidth);
    }

    public void setStopTargetFieldTextWidth(int textWidth) {
        inlineFieldLayout.setStopTargetFieldTextWidth(textWidth);
    }

    public void setVariableFieldTextWidth(int textWidth) {
        inlineFieldLayout.setVariableFieldTextWidth(textWidth);
    }

    public int getMessageFieldLeft() {
        return inlineFieldLayout.getMessageFieldLeft();
    }

    public int getMessageAddButtonLeft() {
        return inlineFieldLayout.getMessageAddButtonLeft();
    }

    public int getMessageRemoveButtonLeft() {
        return inlineFieldLayout.getMessageRemoveButtonLeft();
    }

    public int getMessageButtonTop() {
        return inlineFieldLayout.getMessageButtonTop();
    }

    public int getMessageButtonSize() {
        return inlineFieldLayout.getMessageButtonSize();
    }

    public int getMessageButtonsWidth() {
        return inlineFieldLayout.getMessageButtonsWidth();
    }

    public int getBooleanOperatorAddButtonLeft() {
        return inlineFieldLayout.getBooleanOperatorAddButtonLeft();
    }

    public int getBooleanOperatorRemoveButtonLeft() {
        return inlineFieldLayout.getBooleanOperatorRemoveButtonLeft();
    }

    public int getBooleanOperatorButtonTop() {
        return inlineFieldLayout.getBooleanOperatorButtonTop();
    }

    public int getBooleanOperatorButtonSize() {
        return inlineFieldLayout.getBooleanOperatorButtonSize();
    }

    public int getMessageScopeToggleDisplayHeight() {
        return inlineFieldLayout.getMessageScopeToggleDisplayHeight();
    }

    public int getMessageScopeLabelTop() {
        return inlineFieldLayout.getMessageScopeLabelTop();
    }

    public int getMessageScopeToggleTop() {
        return inlineFieldLayout.getMessageScopeToggleTop();
    }

    public int getMessageScopeLabelHeight() {
        return inlineFieldLayout.getMessageScopeLabelHeight();
    }

    public int getMessageScopeToggleLeft() {
        return inlineFieldLayout.getMessageScopeToggleLeft();
    }

    public int getMessageScopeToggleWidth() {
        return inlineFieldLayout.getMessageScopeToggleWidth();
    }

    public int getMessageScopeToggleHeight() {
        return inlineFieldLayout.getMessageScopeToggleHeight();
    }

    // Text input methods for WRITE_BOOK and WRITE_SIGN nodes
    public boolean hasBookTextInput() {
        return textContent.hasBookTextInput();
    }

    public boolean hasBookTextPageInput() {
        return textContent.hasBookTextPageInput();
    }

    public String getBookText() {
        return textContent.getBookText();
    }

    public void setBookText(String text) {
        textContent.setBookText(text);
    }

    public int getBookTextMaxChars() {
        return textContent.getBookTextMaxChars();
    }

    public int getBookTextMaxCharsPerLine() {
        return textContent.getBookTextMaxCharsPerLine();
    }

    public int getBookTextMaxLines() {
        return textContent.getBookTextMaxLines();
    }

    public int getBookTextPopupWidth() {
        return textContent.getBookTextPopupWidth();
    }

    public int getBookTextPopupHeight() {
        return textContent.getBookTextPopupHeight();
    }

    public String getBookTextEditorTitle() {
        return textContent.getBookTextEditorTitle();
    }

    public String getBookTextForPage(int pageNumber) {
        return textContent.getBookTextForPage(pageNumber);
    }

    public void setBookTextForPage(int pageNumber, String text) {
        textContent.setBookTextForPage(pageNumber, text);
    }

    public List<String> getBookPages() {
        return textContent.getBookPages();
    }

    public void setBookPages(List<String> pages) {
        textContent.setBookPages(pages);
    }

    public int getBookTextDisplayHeight() {
        return inlineFieldLayout.getBookTextDisplayHeight();
    }

    public int getBookTextButtonTop() {
        return inlineFieldLayout.getBookTextButtonTop();
    }

    public int getBookTextButtonLeft() {
        return inlineFieldLayout.getBookTextButtonLeft();
    }

    public int getBookTextButtonWidth() {
        return inlineFieldLayout.getBookTextButtonWidth();
    }

    public int getBookTextButtonHeight() {
        return inlineFieldLayout.getBookTextButtonHeight();
    }

    public int getBookTextPageLabelTop() {
        return inlineFieldLayout.getBookTextPageLabelTop();
    }

    public int getBookTextPageFieldTop() {
        return inlineFieldLayout.getBookTextPageFieldTop();
    }

    public int getBookTextPageFieldLeft() {
        return inlineFieldLayout.getBookTextPageFieldLeft();
    }

    public int getBookTextPageFieldWidth() {
        return inlineFieldLayout.getBookTextPageFieldWidth();
    }

    public int getBookTextPageFieldHeight() {
        return inlineFieldLayout.getBookTextPageFieldHeight();
    }

    public boolean hasPopupEditButton() {
        return NodeCatalog.hasPopupEditButton(type);
    }

    public int getPopupEditButtonLeft() {
        return inlineFieldLayout.getPopupEditButtonLeft();
    }

    public int getPopupEditButtonTop() {
        return inlineFieldLayout.getPopupEditButtonTop();
    }

    public int getPopupEditButtonWidth() {
        return inlineFieldLayout.getPopupEditButtonWidth();
    }

    public int getPopupEditButtonHeight() {
        return inlineFieldLayout.getPopupEditButtonHeight();
    }

    public int getPopupEditButtonDisplayHeight() {
        return inlineFieldLayout.getPopupEditButtonDisplayHeight();
    }

    public int getEventNameFieldLeft() {
        return inlineFieldLayout.getEventNameFieldLeft();
    }

    public int getEventNameFieldTop() {
        return inlineFieldLayout.getEventNameFieldTop();
    }

    public int getEventNameFieldWidth() {
        return inlineFieldLayout.getEventNameFieldWidth();
    }

    public int getEventNameFieldHeight() {
        return inlineFieldLayout.getEventNameFieldHeight();
    }

    /**
     * Recalculate node dimensions based on current content
     */
    public void recalculateDimensions() {
        NodeDimensionCalculator.recalculate(this, layoutState);
    }

    boolean showsSensorSlotHeader() {
        return type == NodeType.CONTROL_IF
            || type == NodeType.CONTROL_IF_ELSE
            || type == NodeType.CONTROL_REPEAT_UNTIL
            || type == NodeType.CONTROL_WAIT_UNTIL;
    }

    boolean showsActionSlotHeader() {
        return type == NodeType.CONTROL_REPEAT
            || type == NodeType.CONTROL_REPEAT_UNTIL
            || type == NodeType.CONTROL_FOREVER;
    }

    /**
     * Get the height needed to display parameters
     */
    public int getParameterDisplayHeight() {
        return NodeDimensionCalculator.parameterDisplayHeight(this);
    }

    String getParameterWidthLabel(NodeParameter parameter) {
        return NodeDimensionCalculator.parameterWidthLabel(this, parameter);
    }

    String getParameterWidthDisplayValue(NodeParameter parameter) {
        return NodeDimensionCalculator.parameterWidthDisplayValue(this, parameter);
    }

    public String getModeDisplayLabel() {
        return NodeDimensionCalculator.modeDisplayLabel(this);
    }

    /**
     * Execute this node asynchronously.
     * Returns a CompletableFuture that completes when the node's command is finished.
     */
    public CompletableFuture<Void> execute() {
        return execute(-1);
    }

    public CompletableFuture<Void> execute(int executionId) {
        return executionCoordinator.execute(executionId);
    }

    ParameterHandlingResult preprocessAttachedParameter(EnumSet<ParameterUsage> usages, CompletableFuture<Void> future) {
        return runtimeParameterResolver.preprocessAttachedParameter(usages, future);
    }

    ParameterHandlingResult preprocessParameterSlot(int slotIndex, EnumSet<ParameterUsage> usages, CompletableFuture<Void> future, boolean resetRuntimeData) {
        return runtimeParameterResolver.preprocessParameterSlot(
            slotIndex, usages, future, resetRuntimeData);
    }

    Node resolveVariableValueNode(Node variableNode, int slotIndex, CompletableFuture<Void> future) {
        return runtimeParameterResolver.resolveVariableValueNode(
            variableNode, slotIndex, future);
    }

    Map<String, String> remapSingleAxisLookValues(Map<String, String> values, Node parameterNode) {
        return runtimeParameterResolver.remapSingleAxisLookValues(values, parameterNode);
    }

    Optional<Vec3> resolvePositionTarget(Node parameterNode, RuntimeParameterData data, CompletableFuture<Void> future) {
        return runtimeParameterResolver.resolvePositionTarget(
            parameterNode, data, future);
    }

    Optional<Vec3> resolveDistanceBetweenTarget(Node parameterNode) {
        return runtimeParameterResolver.resolveDistanceBetweenTarget(parameterNode);
    }

    boolean isDistanceBetweenSupportedTarget(Node parameterNode) {
        return runtimeParameterResolver.isDistanceBetweenSupportedTarget(parameterNode);
    }

    void applyVectorToCoordinateParameters(Vec3 targetVec) {
        runtimeParameterResolver.applyVectorToCoordinateParameters(targetVec);
    }

    boolean isPlayerAtCoordinates(Integer targetX, Integer targetY, Integer targetZ) {
        return runtimeParameterResolver.isPlayerAtCoordinates(
            targetX, targetY, targetZ);
    }

    boolean resolveLookOrientation(Node parameterNode, RuntimeParameterData data, CompletableFuture<Void> future) {
        return runtimeParameterResolver.resolveLookOrientation(
            parameterNode, data, future);
    }

    void orientPlayerTowardsRuntimeTarget(net.minecraft.client.Minecraft client, RuntimeParameterData data) {
        runtimeParameterResolver.orientPlayerTowardsRuntimeTarget(client, data);
    }

    void sendIncompatibleParameterMessage(Node parameterNode) {
        runtimeParameterResolver.sendIncompatibleParameterMessage(parameterNode);
    }

    void sendParameterSearchFailure(String message, CompletableFuture<Void> future) {
        runtimeParameterResolver.sendParameterSearchFailure(message, future);
    }

    boolean reportEmptyParametersForNode(Node target, CompletableFuture<Void> future) {
        return runtimeParameterResolver.reportEmptyParametersForNode(target, future);
    }

    boolean reportEmptyParametersForAttachedParameters(CompletableFuture<Void> future) {
        return runtimeParameterResolver.reportEmptyParametersForAttachedParameters(future);
    }

    void setParameterIfPresent(String name, String value) {
        runtimeParameterResolver.setParameterIfPresent(name, value);
    }

    static String formatFloat(float value) {
        return NodeRuntimeParameterResolver.formatFloat(value);
    }

    static float normalizeLookYaw(float yaw) {
        return NodeRuntimeParameterResolver.normalizeLookYaw(yaw);
    }

    static int parseNodeInt(Node node, String name, int defaultValue) {
        return NodeRuntimeParameterResolver.parseNodeInt(node, name, defaultValue);
    }

    static double parseNodeDouble(Node node, String name, double defaultValue) {
        return NodeRuntimeParameterResolver.parseNodeDouble(node, name, defaultValue);
    }

    static boolean parseNodeBoolean(Node node, String name, boolean defaultValue) {
        return NodeRuntimeParameterResolver.parseNodeBoolean(node, name, defaultValue);
    }

    static Float parseNodeFloat(Node node, String name) {
        return NodeRuntimeParameterResolver.parseNodeFloat(node, name);
    }

    private static Float parseFloatOrNull(String value) {
        return NodeRuntimeParameterResolver.parseFloatOrNull(value);
    }

    static Integer parseIntOrNull(String value) {
        return NodeRuntimeParameterResolver.parseIntOrNull(value);
    }

    static Double parseDoubleOrNull(String value) {
        return NodeRuntimeParameterResolver.parseDoubleOrNull(value);
    }

    double generateRandomValueWithRounding(double min, double max) {
        return runtimeParameterResolver.generateRandomValueWithRounding(min, max);
    }

    Optional<Double> resolveModValue() {
        return runtimeParameterResolver.resolveModValue();
    }

    static boolean isAnySelectionValue(String value) {
        return NodeWorldTargetResolver.isAnySelectionValue(value);
    }

    String sanitizeResourceId(String value) {
        return worldTargetResolver.sanitizeResourceId(value);
    }

    String normalizeResourceId(String value, String defaultNamespace) {
        return worldTargetResolver.normalizeResourceId(value, defaultNamespace);
    }

    List<BlockSelection> resolveBlocksFromParameter(Node parameterNode) {
        return worldTargetResolver.resolveBlocksFromParameter(parameterNode);
    }

    List<String> resolveItemIdsFromParameter(Node parameterNode) {
        return worldTargetResolver.resolveItemIdsFromParameter(parameterNode);
    }

    private String resolveTradeKeyFromParameter(Node parameterNode) {
        return worldTargetResolver.resolveTradeKeyFromParameter(parameterNode);
    }

    NodeVillagerTradeSensorEvaluator villagerTradeSensorEvaluator() {
        return new NodeVillagerTradeSensorEvaluator(this);
    }

    private int findTradeIndexFromLegacySelection(net.minecraft.world.item.trading.MerchantOffers tradeOffers,
                                                  boolean requireInStock,
                                                  boolean requireAffordable) {
        return villagerTradeSensorEvaluator().findTradeIndexFromLegacySelection(
            tradeOffers,
            requireInStock,
            requireAffordable
        );
    }

    List<String> resolveEntityIdsFromParameter(Node parameterNode) {
        return worldTargetResolver.resolveEntityIdsFromParameter(parameterNode);
    }

    void addItemIdentifier(List<String> itemIds, String rawValue) {
        worldTargetResolver.addItemIdentifier(itemIds, rawValue);
    }

    List<String> splitMultiValueList(String rawValue) {
        return worldTargetResolver.splitMultiValueList(rawValue);
    }

    Optional<BlockPos> findNearestBlock(net.minecraft.client.Minecraft client, List<BlockSelection> selections, double range) {
        return worldTargetResolver.findNearestBlock(client, selections, range);
    }

    List<BlockPos> findBlocksWithinRange(net.minecraft.client.Minecraft client, List<BlockSelection> selections, double range) {
        return worldTargetResolver.findBlocksWithinRange(client, selections, range);
    }

    List<BlockPos> findBlocksWithinRange(net.minecraft.client.Minecraft client, List<BlockSelection> selections, double range, int maxResults) {
        return worldTargetResolver.findBlocksWithinRange(client, selections, range, maxResults);
    }

    Optional<BlockPos> findNearestAnyBlock(net.minecraft.client.Minecraft client, double range) {
        return worldTargetResolver.findNearestAnyBlock(client, range);
    }

    Optional<BlockPos> findNearestOpenBlock(net.minecraft.client.Minecraft client, int range) {
        return worldTargetResolver.findNearestOpenBlock(client, range);
    }

    private NodeCraftCommandExecutor craftCommandExecutor() {
        return new NodeCraftCommandExecutor(this);
    }

    int getRequestedCraftQuantity() {
        return craftCommandExecutor().getRequestedCraftQuantity();
    }

    int mapPlayerInventorySlot(AbstractContainerMenu handler, int inventorySlot) {
        return craftCommandExecutor().mapPlayerInventorySlot(handler, inventorySlot);
    }

    public static boolean warmRecipeCache(Minecraft client) {
        return NodeCraftCommandExecutor.warmRecipeCache(client);
    }

    public static boolean requestRecipeCacheWarmup(Minecraft client) {
        return NodeCraftCommandExecutor.requestRecipeCacheWarmup(client);
    }

    public static boolean isRecipeCacheWarmupRequested() {
        return NodeCraftCommandExecutor.isRecipeCacheWarmupRequested();
    }

    public static boolean hasUsableRecipeCache(Minecraft client) {
        return NodeCraftCommandExecutor.hasUsableRecipeCache(client);
    }

    public static void resetRecipeCacheWarmup() {
        cachedRecipeBook = null;
        NodeCraftCommandExecutor.resetRecipeCacheWarmup();
    }

    public static boolean clearRecipeCache(Minecraft client) {
        cachedRecipeBook = null;
        return NodeCraftCommandExecutor.clearRecipeCache(client);
    }

    public static boolean isRecipeCacheWarmupInProgress(Minecraft client) {
        return NodeCraftCommandExecutor.isRecipeCacheWarmupInProgress(client);
    }

    public static RecipeCacheWarmupProgress getRecipeCacheWarmupProgress(Minecraft client) {
        NodeCraftCommandExecutor.RecipeCacheWarmupProgress progress =
            NodeCraftCommandExecutor.getRecipeCacheWarmupProgress(client);
        return progress == null ? null : new RecipeCacheWarmupProgress(progress.completed(), progress.total());
    }

    static boolean isRecipeCacheUsableForTests(Map<String, List<Map<String, Object>>> rawRecipesByOutput) {
        return NodeCraftCommandExecutor.isRecipeCacheUsableForTests(rawRecipesByOutput);
    }

    static List<Integer> normalizeCachedRecipeSlotIndexesForTests(List<Integer> slotIndexes) {
        return NodeCraftCommandExecutor.normalizeCachedRecipeSlotIndexesForTests(slotIndexes);
    }

    static List<Integer> planIngredientSourceSlotsForTests(List<TestIngredientStack> inventoryStacks,
                                                           List<String> ingredientKeys) {
        List<NodeCraftCommandExecutor.TestIngredientStack> executorStacks = new ArrayList<>();
        if (inventoryStacks != null) {
            for (TestIngredientStack stack : inventoryStacks) {
                executorStacks.add(stack == null
                    ? new NodeCraftCommandExecutor.TestIngredientStack("", 0)
                    : new NodeCraftCommandExecutor.TestIngredientStack(stack.key(), stack.count()));
            }
        }
        return NodeCraftCommandExecutor.planIngredientSourceSlotsForTests(executorStacks, ingredientKeys);
    }

    @Deprecated
    static class CachedRecipeBook extends NodeCraftCommandExecutor.CachedRecipeBook {
    }

    static record TestIngredientStack(String key, int count) {
    }

    public record RecipeCacheWarmupProgress(int completed, int total) {
        public float fraction() {
            if (total <= 0) {
                return 0.0f;
            }
            return Math.min(1.0f, Math.max(0.0f, completed / (float) total));
        }
    }

    private NodeWorldActionCommandExecutor worldActionCommandExecutor() {
        return new NodeWorldActionCommandExecutor(this);
    }

    private NodeEntityActionCommandExecutor entityActionCommandExecutor() {
        return new NodeEntityActionCommandExecutor(this);
    }

    private NodeVariableListCommandExecutor variableListCommandExecutor() {
        return new NodeVariableListCommandExecutor(this);
    }

    static final class ListValueEntry {
        final NodeType elementType;
        final String entry;

        ListValueEntry(NodeType elementType, String entry) {
            this.elementType = elementType;
            this.entry = entry;
        }
    }

    Node resolveListItemValueNode(Node listNode, CompletableFuture<Void> future, boolean reportErrors, RuntimeParameterData data) {
        return variableListCommandExecutor().resolveListItemValueNode(listNode, future, reportErrors, data);
    }

    private void executeSetVariableCommand(CompletableFuture<Void> future) {
        new NodeVariableListCommandExecutor(this).executeSetVariableCommand(future);
    }

    boolean canAffordTrade(net.minecraft.world.entity.player.Player player,
                           net.minecraft.world.inventory.MerchantMenu screenHandler,
                           net.minecraft.world.item.trading.MerchantOffer offer) {
        return entityActionCommandExecutor().canAffordTrade(player, screenHandler, offer);
    }

    static int getRequiredFirstBuyCountForTests(net.minecraft.world.item.trading.MerchantOffer offer) {
        return NodeEntityActionCommandExecutor.getRequiredFirstBuyCountForTests(offer);
    }

    static int getRequiredSecondBuyCountForTests(net.minecraft.world.item.trading.MerchantOffer offer) {
        return NodeEntityActionCommandExecutor.getRequiredSecondBuyCountForTests(offer);
    }

    static int resolveRequiredTradeCountForTests(int displayedCount, int originalCount) {
        return NodeEntityActionCommandExecutor.resolveRequiredTradeCountForTests(displayedCount, originalCount);
    }

    static boolean isCreateListCollectionTarget(NodeType parameterType) {
        return NodeVariableListCommandExecutor.isCreateListCollectionTarget(parameterType);
    }

    static void syncSelectedHotbarSlot(Minecraft client) {
        NodeEntityActionCommandExecutor.syncSelectedHotbarSlot(client);
    }

    static void performMainHandAttack(Minecraft client) {
        NodeEntityActionCommandExecutor.performMainHandAttack(client);
    }

    boolean parameterProvidesCoordinates(Node parameterNode) {
        return worldActionCommandExecutor().parameterProvidesCoordinates(parameterNode);
    }

    boolean parameterProvidesCoordinates(NodeType parameterType) {
        return worldActionCommandExecutor().parameterProvidesCoordinates(parameterType);
    }

    boolean blockParameterProvidesPlacementCoordinates(Node parameterNode) {
        return worldActionCommandExecutor().blockParameterProvidesPlacementCoordinates(parameterNode);
    }

    boolean ensureStackSelectedInMainHand(net.minecraft.client.Minecraft client,
                                          Inventory inventory,
                                          int slotIndex,
                                          ItemStack stack) {
        return worldActionCommandExecutor().ensureStackSelectedInMainHand(client, inventory, slotIndex, stack);
    }

    void ensureBlockInHand(net.minecraft.client.Minecraft client, String blockId, InteractionHand hand) {
        worldActionCommandExecutor().ensureBlockInHand(client, blockId, hand);
    }

    boolean waitForBlockPlacement(net.minecraft.client.Minecraft client, BlockPos targetPos, Block desiredBlock) throws InterruptedException {
        return worldActionCommandExecutor().waitForBlockPlacement(client, targetPos, desiredBlock);
    }

    BlockHitResult preparePlacementHitResult(net.minecraft.client.Minecraft client, BlockPos targetPos, String blockId, InteractionHand hand, double reachSquared) {
        return worldActionCommandExecutor().preparePlacementHitResult(client, targetPos, blockId, hand, reachSquared);
    }

    static String formatBlockPos(BlockPos pos) {
        return NodeWorldActionCommandExecutor.formatBlockPos(pos);
    }

    Block resolveBlockForPlacement(String blockId) {
        return worldActionCommandExecutor().resolveBlockForPlacement(blockId);
    }

    double getPlacementReachSquared(net.minecraft.client.Minecraft client) {
        return worldActionCommandExecutor().getPlacementReachSquared(client);
    }

    boolean isBlockReplaceable(net.minecraft.world.level.Level world, BlockPos targetPos) {
        return worldActionCommandExecutor().isBlockReplaceable(world, targetPos);
    }

    boolean hasPlacementSupport(net.minecraft.world.level.Level world, BlockPos targetPos) {
        return worldActionCommandExecutor().hasPlacementSupport(world, targetPos);
    }

    int findHotbarSlotWithItem(Inventory inventory, Item targetItem) {
        return worldActionCommandExecutor().findHotbarSlotWithItem(inventory, targetItem);
    }

    private boolean isInlineVariableChar(char character) {
        return Character.isLetterOrDigit(character) || character == '_' || character == '-';
    }

    NodeGuiSensorEvaluator guiSensorEvaluator() {
        return new NodeGuiSensorEvaluator(this);
    }

    private NodeTextIoCommandExecutor textIoCommandExecutor() {
        return new NodeTextIoCommandExecutor(this);
    }

    String resolveRuntimeVariablesInText(String raw) {
        return textIoCommandExecutor().resolveRuntimeVariablesInText(raw);
    }

    String formatRuntimeVariableValue(ExecutionManager.RuntimeVariable variable) {
        return textIoCommandExecutor().formatRuntimeVariableValue(variable);
    }

    ExecutionManager.RuntimeVariable resolveRuntimeVariableForName(ExecutionManager manager, Node startNode, String name) {
        return textIoCommandExecutor().resolveRuntimeVariableForName(manager, startNode, name);
    }

    String formatCoordinateValues(Map<String, String> values) {
        return textIoCommandExecutor().formatCoordinateValues(values);
    }

    String formatRotationValues(Map<String, String> values) {
        return textIoCommandExecutor().formatRotationValues(values);
    }

    String getRuntimeValue(Map<String, String> values, String key) {
        return textIoCommandExecutor().getRuntimeValue(values, key);
    }

    NodeNavigationCommandExecutor navigationCommandExecutor() {
        return new NodeNavigationCommandExecutor(this);
    }

    private void executeControlRepeat(CompletableFuture<Void> future) {
        new NodeFlowCommandExecutor(this).executeControlRepeat(future);
    }

    BlockPos resolveGotoFallbackTargetFromBlockId(String blockId, CompletableFuture<Void> future) {
        return navigationCommandExecutor().resolveGotoFallbackTargetFromBlockId(blockId, future);
    }

    private NodeInventoryCommandExecutor inventoryCommandExecutor() {
        return new NodeInventoryCommandExecutor(this);
    }

    boolean resolveMoveItemSlotFromItemParameter(Node parameterNode, int slotIndex, CompletableFuture<Void> future) {
        return inventoryCommandExecutor().resolveMoveItemSlotFromItemParameter(parameterNode, slotIndex, future);
    }

    boolean resolveDropParameterSelection(Node parameterNode, CompletableFuture<Void> future) {
        return inventoryCommandExecutor().resolveDropParameterSelection(parameterNode, future);
    }

    boolean isDropNodeType() {
        return type == NodeType.DROP_ITEM || type == NodeType.DROP_SLOT;
    }

    SlotSelectionType resolveInventorySlotSelectionType(Node parameterNode) {
        return inventoryCommandExecutor().resolveInventorySlotSelectionType(parameterNode);
    }

    SlotResolution resolveInventorySlot(AbstractContainerMenu handler, Inventory inventory, int slotValue, SlotSelectionType selectionType) {
        return inventoryCommandExecutor().resolveInventorySlot(handler, inventory, slotValue, selectionType);
    }

    boolean resolveUseParameterSelection(Node parameterNode, CompletableFuture<Void> future) {
        return inventoryCommandExecutor().resolveUseParameterSelection(parameterNode, future);
    }

    private void applyCrouchState(net.minecraft.client.Minecraft client, boolean active) {
        NodeClientRuntimeSupport.applySneakState(client, active);
    }

    public boolean isRepeatUntilConditionMetForPolling() {
        if (type != NodeType.CONTROL_REPEAT_UNTIL) {
            return false;
        }
        return preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), null) != ParameterHandlingResult.COMPLETE
            && evaluateConditionFromParameters();
    }

    boolean shouldAbortForRepeatUntilGuard() {
        ExecutionManager manager = ExecutionManager.getInstance();
        if (manager != null && manager.isStopRequested()) {
            return true;
        }
        Node guard = runtimeState.activeRepeatUntilGuard;
        return guard != null && guard != this && guard.isRepeatUntilConditionMetForPolling();
    }

    private EquipmentSlot parseEquipmentSlot(NodeParameter parameter, EquipmentSlot defaultSlot) {
        if (parameter == null || parameter.getStringValue() == null) {
            return defaultSlot;
        }
        String value = parameter.getStringValue().trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "head", "helmet" -> EquipmentSlot.HEAD;
            case "chest", "chestplate" -> EquipmentSlot.CHEST;
            case "legs", "leggings" ->  EquipmentSlot.LEGS;
            case "feet", "boots" ->  EquipmentSlot.FEET;
            default -> defaultSlot;
        };
    }

    int getIntParameter(String name, int defaultValue) {
        return runtimeParameterResolver.getIntParameter(name, defaultValue);
    }

    static boolean isAnyPlayerValue(String value) {
        return value != null && "any".equalsIgnoreCase(value.trim());
    }

    static boolean isSelfPlayerValue(String value) {
        if (value == null) {
            return true;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty()
            || "self".equalsIgnoreCase(trimmed)
            || "me".equalsIgnoreCase(trimmed)
            || "local".equalsIgnoreCase(trimmed);
    }

    static boolean isAnyMessageValue(String value) {
        return value == null || value.trim().isEmpty() || "any".equalsIgnoreCase(value.trim());
    }

    static Optional<AbstractClientPlayer> findNearestPlayer(
        net.minecraft.client.Minecraft client,
        AbstractClientPlayer reference
    ) {
        if (client == null || client.level == null) {
            return Optional.empty();
        }
        AbstractClientPlayer best = null;
        double bestDistance = Double.MAX_VALUE;
        for (AbstractClientPlayer player : client.level.players()) {
            if (player == null) {
                continue;
            }
            if (reference != null && player == reference) {
                continue;
            }
            double distance = reference != null ? player.distanceToSqr(reference) : 0.0;
            if (best == null || distance < bestDistance) {
                best = player;
                bestDistance = distance;
            }
        }
        if (best == null && reference != null) {
            best = reference;
        }
        return Optional.ofNullable(best);
    }
    
    String getStringParameter(String name, String defaultValue) {
        return runtimeParameterResolver.getStringParameter(name, defaultValue);
    }

    static String getParameterString(Node node, String name) {
        return NodeRuntimeParameterResolver.getParameterString(node, name);
    }

    /** Returns the raw parameter value without resolving $variable references (for error messages). */
    private static String getParameterStringRaw(Node node, String name) {
        if (node == null || name == null) {
            return null;
        }
        NodeParameter parameter = node.getParameter(name);
        if (parameter == null) {
            return null;
        }
        return parameter.getStringValue();
    }

    private static boolean isRawInlineVariableName(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '-') {
                return false;
            }
        }
        return true;
    }

    static boolean isInlineMathOperator(char c) {
        return c == '+' || c == '-' || c == '*' || c == '/' || c == '^';
    }

    String getBlockParameterValue(Node node) {
        return worldTargetResolver.getBlockParameterValue(node);
    }

    String getEntityParameterState(Node node) {
        return worldTargetResolver.getEntityParameterState(node);
    }

    double getDoubleParameter(String name, double defaultValue) {
        return runtimeParameterResolver.getDoubleParameter(name, defaultValue);
    }
    
    boolean getBooleanParameter(String name, boolean defaultValue) {
        return runtimeParameterResolver.getBooleanParameter(name, defaultValue);
    }

    Optional<Boolean> resolveBooleanValueFromRaw(String rawValue, boolean allowBareVariableName) {
        return runtimeParameterResolver.resolveBooleanValueFromRaw(
            rawValue, allowBareVariableName);
    }

    Optional<Boolean> resolveBooleanNodeValue(Node node) {
        return runtimeParameterResolver.resolveBooleanNodeValue(node);
    }

    static double parseDoubleOrDefault(String value, double defaultValue) {
        return NodeRuntimeParameterResolver.parseDoubleOrDefault(value, defaultValue);
    }

    static Double evaluateNumericExpression(String value) {
        return NodeRuntimeParameterResolver.evaluateNumericExpression(value);
    }

    void notifyInvalidBlockStateSelection(String blockId, String state) {
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        String blockLabel = (blockId == null || blockId.isEmpty()) ? tr("pathmind.error.selectedBlock") : blockId;
        String stateLabel = state == null || state.isEmpty() ? tr("pathmind.error.unspecifiedState") : state;
        sendNodeErrorMessage(client, tr("pathmind.error.invalidBlockState", stateLabel, blockLabel, type.getDisplayName()));
    }

    void notifyInvalidEntityStateSelection(String entityId, String state) {
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        String entityLabel = (entityId == null || entityId.isEmpty()) ? tr("pathmind.error.selectedEntity") : entityId;
        String stateLabel = state == null || state.isEmpty() ? tr("pathmind.error.unspecifiedState") : state;
        sendNodeErrorMessage(client, tr("pathmind.error.invalidEntityState", stateLabel, entityLabel, type.getDisplayName()));
    }

    Optional<BlockPos> findNearestDroppedItem(net.minecraft.client.Minecraft client, Item item, double range) {
        return worldTargetResolver.findNearestDroppedItem(client, item, range);
    }

    Optional<Entity> findNearestEntity(net.minecraft.client.Minecraft client, EntityType<?> entityType, double range) {
        return worldTargetResolver.findNearestEntity(client, entityType, range);
    }

    Optional<Entity> findNearestEntity(net.minecraft.client.Minecraft client, EntityType<?> entityType, double range, String state) {
        return worldTargetResolver.findNearestEntity(client, entityType, range, state);
    }

    Entity resolveListItemEntity(Node listNode, RuntimeParameterData data, CompletableFuture<Void> future) {
        return worldTargetResolver.resolveListItemEntity(listNode, data, future);
    }

    ExecutionManager.RuntimeList resolveRuntimeList(Node listNode) {
        return worldTargetResolver.resolveRuntimeList(listNode);
    }

    Optional<Integer> resolveListLengthValue(Node listNode) {
        return worldTargetResolver.resolveListLengthValue(listNode);
    }

    ListSlotEntry resolveListItemSlotEntry(Node listNode, boolean reportErrors, CompletableFuture<Void> future) {
        return worldTargetResolver.resolveListItemSlotEntry(listNode, reportErrors, future);
    }

    ListSlotEntry parseListSlotEntry(String entry) {
        return worldTargetResolver.parseListSlotEntry(entry);
    }

    Entity resolveEntityByUuid(net.minecraft.client.Minecraft client, java.util.UUID uuid) {
        return worldTargetResolver.resolveEntityByUuid(client, uuid);
    }

    private List<Entity> findEntitiesByType(net.minecraft.client.Minecraft client, EntityType<?> entityType, double range, String state) {
        return worldTargetResolver.findEntitiesByType(client, entityType, range, state);
    }

    List<ItemEntity> findItemsByType(net.minecraft.client.Minecraft client, Item item, double range) {
        return worldTargetResolver.findItemsByType(client, item, range);
    }
    
    InteractionHand resolveHand(NodeParameter parameter, InteractionHand defaultHand) {
        if (parameter == null || parameter.getStringValue() == null) {
            return defaultHand;
        }
        String value = parameter.getStringValue().trim().toLowerCase(Locale.ROOT);
        if (value.equals("off") || value.equals("offhand") || value.equals("off_hand") || value.equals("off-hand")) {
            return InteractionHand.OFF_HAND;
        }
        return InteractionHand.MAIN_HAND;
    }

    private void resetControlState() {
        runtimeState.resetControlState();
    }
    
    enum SensorConditionType {
        TOUCHING_BLOCK("Touching Block"),
        TOUCHING_ENTITY("Touching Entity"),
        AT_COORDINATES("At Coordinates");

        private final String label;

        SensorConditionType(String label) {
            this.label = label;
        }

        static SensorConditionType fromLabel(String label) {
            if (label == null) {
                return TOUCHING_BLOCK;
            }
            String trimmed = label.trim();
            for (SensorConditionType type : values()) {
                if (type.label.equalsIgnoreCase(trimmed)) {
                    return type;
                }
            }
            return TOUCHING_BLOCK;
        }
    }

    Node getAttachedParameterOfType(NodeType... allowedTypes) {
        return sensorCoordinator.getAttachedParameterOfType(allowedTypes);
    }

    boolean providesTrait(Node node, NodeValueTrait trait) {
        return sensorCoordinator.providesTrait(node, trait);
    }

    Node resolveSensorParameterNode(Node parameterNode, int slotIndex) {
        return sensorCoordinator.resolveSensorParameterNode(parameterNode, slotIndex);
    }

    Optional<BlockState> getTargetedBlockState() {
        return sensorCoordinator.getTargetedBlockState();
    }

    Optional<BlockPos> getTargetedBlockPos() {
        return sensorCoordinator.getTargetedBlockPos();
    }

    Optional<Entity> getTargetedEntity() {
        return sensorCoordinator.getTargetedEntity();
    }

    Optional<Integer> getCurrentHotbarSlot() {
        return sensorCoordinator.getCurrentHotbarSlot();
    }

    Optional<Direction> getTargetedBlockFace() {
        return sensorCoordinator.getTargetedBlockFace();
    }

    Optional<BlockHitResult> getCurrentBlockHitResult() {
        return sensorCoordinator.getCurrentBlockHitResult();
    }

    public boolean evaluateSensor() {
        return sensorCoordinator.evaluateSensor();
    }

    Optional<Double> getDistanceFromGround() {
        return sensorCoordinator.getDistanceFromGround();
    }

    static boolean isFallingState(
        boolean onGround,
        boolean swimming,
        boolean submergedInWater,
        boolean climbing,
        boolean flying,
        double downwardVelocity,
        double fallDistance,
        double peakY,
        double currentY,
        double groundClearance,
        double requiredDistance,
        long nowMs,
        long lastDetectedAtMs
    ) {
        return NodePlayerStateSensorEvaluator.isFallingState(
            onGround,
            swimming,
            submergedInWater,
            climbing,
            flying,
            downwardVelocity,
            fallDistance,
            peakY,
            currentY,
            groundClearance,
            requiredDistance,
            nowMs,
            lastDetectedAtMs
        );
    }

    Optional<Boolean> resolveBooleanFromNode(Node node) {
        return sensorCoordinator.resolveBooleanFromNode(node);
    }

    public Node createRuntimeVariableSnapshot(ExecutionManager.RuntimeVariable runtimeVariable) {
        return sensorCoordinator.createRuntimeVariableSnapshot(runtimeVariable);
    }

    /** Evaluates one attached argument into an immutable value snapshot for a routine call frame. */
    public ExecutionManager.RuntimeVariable captureAttachedRuntimeValue(int slotIndex, int executionId) {
        return routineMetadata.captureAttachedRuntimeValue(slotIndex, executionId);
    }

    Optional<Boolean> compareParameterNodes(Node left, Node right) {
        return sensorCoordinator.compareParameterNodes(left, right);
    }

    String formatCanonicalValueMap(Map<String, String> values) {
        return sensorCoordinator.formatCanonicalValueMap(values);
    }

    Optional<Double> resolveComparableNumber(Node node) {
        return sensorCoordinator.resolveComparableNumber(node);
    }

    Optional<Double> resolveComparableNumberWithVariables(Node node, int slotIndex) {
        return sensorCoordinator.resolveComparableNumberWithVariables(node, slotIndex);
    }

    Optional<Integer> resolveInventorySlotCount(Node slotNode) {
        return sensorCoordinator.resolveInventorySlotCount(slotNode);
    }

    boolean evaluateConditionFromParameters() {
        return sensorCoordinator.evaluateConditionFromParameters();
    }

    boolean matchesAnyBlock(List<BlockSelection> selections, BlockState state) {
        return sensorCoordinator.matchesAnyBlock(selections, state);
    }

    Integer resolveKeyCode(String keyName) {
        return sensorCoordinator.resolveKeyCode(keyName);
    }

    Integer resolveMouseButtonCode(String buttonName) {
        return sensorCoordinator.resolveMouseButtonCode(buttonName);
    }

    boolean stackMatchesAnyItem(ItemStack stack, List<String> itemIds) {
        return sensorCoordinator.stackMatchesAnyItem(stack, itemIds);
    }
    
    void executeCommand(String command) {
        try {
            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
            ClientMessageSender.send(client, command);
        } catch (Exception e) {
            LOGGER.warn("Error executing command: {}", e.getMessage(), e);
        }
    }
    
    
}
