package com.pathmind.nodes;

import com.pathmind.execution.PathmindNavigator;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.math.BigDecimal;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Locale;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.pathmind.data.NodeGraphData;
import com.pathmind.data.NodeGraphPersistence;
import com.pathmind.data.PresetManager;
import com.pathmind.routines.RoutineInputDefinition;
import com.pathmind.routines.RoutineValueKind;
import com.pathmind.execution.ExecutionManager;
import com.pathmind.execution.PreciseCompletionTracker;
import com.pathmind.ui.overlay.NodeErrorNotificationOverlay;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.BaritoneDependencyChecker;
import com.pathmind.util.BaritoneApiProxy;
import com.pathmind.util.BlockSelection;
import com.pathmind.util.EntityStateOptions;
import com.pathmind.util.InventorySlotModeHelper;
import com.pathmind.util.PlayerInventoryBridge;
import com.pathmind.util.PathmindI18n;
import com.pathmind.util.RecipeCompatibilityBridge;
import com.pathmind.util.ClientMessageSender;
import com.pathmind.util.UiUtilsProxy;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Collections;
import java.util.Comparator;
import java.util.TreeMap;
import java.util.Random;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.lwjgl.glfw.GLFW;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import com.pathmind.util.CameraCompatibilityBridge;
import com.pathmind.util.ChatScreenCompatibilityBridge;
import com.pathmind.util.EntityCompatibilityBridge;
import com.pathmind.util.GuiSelectionMode;
import com.pathmind.util.GameProfileCompatibilityBridge;
import com.pathmind.util.InputCompatibilityBridge;

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
    static final int PLAYER_ARMOR_SLOT_COUNT = 4;
    private static final int PLAYER_OFFHAND_INVENTORY_INDEX = Inventory.INVENTORY_SIZE + PLAYER_ARMOR_SLOT_COUNT;
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
    static final double DEFAULT_REACH_DISTANCE_SQUARED = 25.0D;
    private static final double DEFAULT_REACH_DISTANCE = Math.sqrt(DEFAULT_REACH_DISTANCE_SQUARED);
    static final double DEFAULT_DIRECTION_DISTANCE = 16.0;
    static final long SNEAK_SYNC_DELAY_MS = 75L;
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
    private final List<String> messageLines;
    private boolean messageClientSide;
    private String bookText;
    private final List<String> bookPages;
    private String stickyNoteText;
    private boolean gotoAllowBreakWhileExecuting;
    private boolean gotoAllowPlaceWhileExecuting;
    private boolean keyPressedActivatesInGuis;
    private String templateName;
    private int templateVersion;
    private NodeGraphData templateGraphData;
    private RuntimeValueScope runtimeValueScope;
    private String routineId;
    private String routineInputId;
    private final List<NodeGraphData.RoutineArgumentData> routineArguments;

    private boolean usesTemplateBacking() {
        return type == NodeType.TEMPLATE;
    }

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
        this.dynamicBooleanOperatorSlotCount = isExpandableBooleanOperatorType(type) ? 2 : 0;
        this.messageLines = new ArrayList<>();
        if (type == NodeType.MESSAGE || type == NodeType.CALCULATE) {
            this.messageLines.add(getDefaultMessageLineValue());
        }
        this.messageClientSide = false;
        this.bookText = "";
        this.bookPages = new ArrayList<>();
        this.stickyNoteText = "";
        this.gotoAllowBreakWhileExecuting = false;
        this.gotoAllowPlaceWhileExecuting = false;
        this.keyPressedActivatesInGuis = true;
        this.templateName = usesTemplateBacking() ? "Template" : "";
        this.templateVersion = 0;
        this.templateGraphData = null;
        this.runtimeValueScope = RuntimeValueScope.GLOBAL;
        this.routineId = "";
        this.routineInputId = "";
        this.routineArguments = new ArrayList<>();
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
        return routineId == null ? "" : routineId;
    }

    public String getRoutineInputId() {
        return routineInputId == null ? "" : routineInputId;
    }

    public void setRoutineIdentity(String routineId, String inputId) {
        this.routineId = routineId == null ? "" : routineId;
        this.routineInputId = inputId == null ? "" : inputId;
    }

    public static Node createRoutineEntry(String routineId, String label, int x, int y) {
        Node node = new Node(NodeType.ROUTINE_ENTRY, x, y);
        node.setRoutineIdentity(routineId, "");
        node.getParameter("Name").setStringValue(label == null ? "Routine" : label);
        node.recalculateDimensions();
        return node;
    }

    public static Node createRoutineInput(String routineId, RoutineInputDefinition input, int x, int y) {
        Node node = new Node(NodeType.ROUTINE_INPUT, x, y);
        node.setRoutineIdentity(routineId, input == null ? "" : input.getId());
        if (input != null) {
            node.getParameter("Label").setStringValue(input.getLabel());
            node.getParameter("ValueKind").setStringValue(input.getValueKind().name());
            node.getParameter("Default").setStringValue(input.getDefaultValue());
            node.getParameter("Required").setStringValue(Boolean.toString(input.isRequired()));
        }
        node.recalculateDimensions();
        return node;
    }

    public static Node createRoutineCall(String routineId, String name, int x, int y) {
        Node node = new Node(NodeType.ROUTINE_CALL, x, y);
        node.setRoutineIdentity(routineId, "");
        node.getParameter("Name").setStringValue(name == null || name.isBlank() ? "Routine" : name.trim());
        node.recalculateDimensions();
        return node;
    }

    public static Node createRoutineCall(NodeGraphData.RoutineDefinitionData routine, int x, int y) {
        Node node = createRoutineCall(routine == null ? "" : routine.getId(), routine == null ? "Routine" : routine.getName(), x, y);
        node.syncRoutineCallDefinition(routine);
        return node;
    }

    public void setRoutineArguments(List<NodeGraphData.RoutineArgumentData> arguments) {
        routineArguments.clear();
        if (arguments != null) {
            for (NodeGraphData.RoutineArgumentData argument : arguments) {
                if (argument != null && argument.getInputId() != null && !argument.getInputId().isBlank()) {
                    routineArguments.add(copyRoutineArgument(argument));
                }
            }
        }
        recalculateDimensions();
    }

    public List<NodeGraphData.RoutineArgumentData> getRoutineArguments() {
        return routineArguments.stream().map(Node::copyRoutineArgument).toList();
    }

    /** Refreshes the public signature while preserving bindings for inputs that still exist. */
    public void syncRoutineCallDefinition(NodeGraphData.RoutineDefinitionData routine) {
        if (type != NodeType.ROUTINE_CALL || routine == null || !getRoutineId().equals(routine.getId())) return;
        Map<String, Node> boundArguments = new java.util.LinkedHashMap<>();
        for (Map.Entry<Integer, Node> binding : new ArrayList<>(getAttachedParameters().entrySet())) {
            String inputId = getRoutineInputIdForSlot(binding.getKey());
            if (!inputId.isBlank() && binding.getValue() != null) boundArguments.put(inputId, binding.getValue());
            attachments.detachParameter(binding.getKey());
        }
        NodeParameter name = getParameter("Name");
        if (name != null) name.setStringValue(routine.getName() == null ? "Routine" : routine.getName());
        ArrayList<NodeGraphData.RoutineInputData> inputs = new ArrayList<>(routine.getInputs());
        inputs.sort(java.util.Comparator.comparingInt(input -> input.getOrder() == null ? Integer.MAX_VALUE : input.getOrder()));
        routineArguments.clear();
        for (NodeGraphData.RoutineInputData input : inputs) {
            if (input == null || input.getId() == null || input.getId().isBlank()) continue;
            NodeGraphData.RoutineArgumentData argument = new NodeGraphData.RoutineArgumentData();
            argument.setInputId(input.getId());
            argument.setLabel(input.getLabel());
            argument.setValueKind(input.getValueKind());
            argument.setRequired(input.getRequired());
            argument.setDefaultValue(input.getDefaultValue());
            argument.setOrphaned(false);
            routineArguments.add(argument);
        }
        for (Map.Entry<String, Node> binding : boundArguments.entrySet()) {
            int slot = getRoutineSlotForInputId(binding.getKey());
            if (slot < 0) continue;
            attachments.attachParameter(this, slot, binding.getValue());
            binding.getValue().setSocketsHidden(true);
            binding.getValue().setDragging(false);
        }
        recalculateDimensions();
    }

    private static NodeGraphData.RoutineArgumentData copyRoutineArgument(NodeGraphData.RoutineArgumentData source) {
        NodeGraphData.RoutineArgumentData copy = new NodeGraphData.RoutineArgumentData();
        copy.setInputId(source.getInputId());
        copy.setLabel(source.getLabel());
        copy.setValueKind(source.getValueKind());
        copy.setRequired(source.getRequired());
        copy.setDefaultValue(source.getDefaultValue());
        copy.setOrphaned(source.getOrphaned());
        return copy;
    }

    public String getRoutineInputIdForSlot(int slotIndex) {
        return slotIndex >= 0 && slotIndex < routineArguments.size() ? routineArguments.get(slotIndex).getInputId() : "";
    }

    public int getRoutineSlotForInputId(String inputId) {
        if (inputId == null || inputId.isBlank()) return -1;
        for (int i = 0; i < routineArguments.size(); i++) if (inputId.equals(routineArguments.get(i).getInputId())) return i;
        return -1;
    }

    public boolean isRoutineArgumentOrphaned(int slotIndex) {
        return slotIndex >= 0 && slotIndex < routineArguments.size()
            && Boolean.TRUE.equals(routineArguments.get(slotIndex).getOrphaned());
    }

    public String getRoutineArgumentDefaultValue(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= routineArguments.size()) return "";
        String value = routineArguments.get(slotIndex).getDefaultValue();
        return value == null ? "" : value;
    }

    public String getRoutineArgumentValueKind(int slotIndex) {
        return slotIndex >= 0 && slotIndex < routineArguments.size()
            ? RoutineValueKind.fromSerialized(routineArguments.get(slotIndex).getValueKind()).name() : RoutineValueKind.ANY.name();
    }

    public EnumSet<NodeValueTrait> getAcceptedTraitsForParameterSlot(int slotIndex) {
        if (type == NodeType.ROUTINE_CALL && slotIndex >= 0 && slotIndex < routineArguments.size()) {
            return EnumSet.of(NodeValueTrait.ANY);
        }
        return NodeTraitRegistry.getAcceptedTraits(type, slotIndex);
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
        if (type == NodeType.ROUTINE_CALL) return !routineArguments.isEmpty();
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
            || type == NodeType.ROUTINE_CALL && routineArguments.isEmpty()
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
            NodeGraphData.RoutineArgumentData argument = routineArguments.get(slotIndex);
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
        if (type == NodeType.ROUTINE_CALL) return routineArguments.size();
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
        if (type == NodeType.ROUTINE_CALL && slotIndex >= 0 && slotIndex < routineArguments.size()) {
            NodeGraphData.RoutineArgumentData argument = routineArguments.get(slotIndex);
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

    private void updateAttachedParameterPosition(int slotIndex) {
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
        if (!canAcceptSensor() || sensor == null || !sensor.isSensorNode() || sensor == this) {
            return false;
        }

        if (attachments.isSensorAttachedTo(this, sensor)) {
            updateAttachedSensorPosition();
            return true;
        }

        if (sensor.attachments.getParentControl() != null) {
            sensor.attachments.getParentControl().detachSensor();
        }

        Node previousSensor = attachments.attachSensor(this, sensor);
        if (previousSensor != null) {
            previousSensor.setDragging(false);
            previousSensor.setSelected(false);
            previousSensor.setPositionSilently(getX() + getWidth() + SENSOR_SLOT_MARGIN_HORIZONTAL, getY());
        }

        sensor.setDragging(false);
        sensor.setSelected(false);

        recalculateDimensions();
        updateAttachedSensorPosition();
        return true;
    }

    public void detachSensor() {
        Node sensor = attachments.detachSensor();
        if (sensor != null) {
            recalculateDimensions();
        }
    }

    public boolean attachParameter(Node parameter) {
        return attachParameter(parameter, 0);
    }

    public boolean attachParameter(Node parameter, int slotIndex) {
        return attachParameter(parameter, slotIndex, false);
    }

    /**
     * Strict attachment that enforces slot trait compatibility. Used for runtime value substitution
     * where an incompatible resolved value must be rejected. Editor and graph-restore paths use the
     * unrestricted two-arg form, so any node usable as a parameter can occupy any existing slot.
     */
    public boolean attachParameterStrict(Node parameter, int slotIndex) {
        return attachParameter(parameter, slotIndex, true);
    }

    private boolean attachParameter(Node parameter, int slotIndex, boolean enforceCompatibility) {
        if (parameter == null
            || !isUsableAsParameterType(parameter.getType())
            || parameter == this) {
            return false;
        }
        if ((type == NodeType.PLACE || type == NodeType.PLACE_HAND)
            && slotIndex == 1
            && parameter.getType() != null) {
            NodeType parameterType = parameter.getType();
            if (parameterType == NodeType.PARAM_INVENTORY_SLOT) {
                // Inventory-slot parameters should always occupy the first slot
                slotIndex = 0;
            }
        }
        if (!canAcceptParameterAt(slotIndex)) {
            return false;
        }

        if (parameter.attachments.isAttachedToParameterHost(this, slotIndex)) {
            parameter.recalculateDimensions();
            refreshAttachedParameterValues();
            recalculateDimensions();
            updateAttachedParameterPosition(slotIndex);
            updateParentControlLayout();
            return true;
        }

        if (enforceCompatibility && !isParameterSupported(parameter, slotIndex)) {
            sendIncompatibleParameterMessage(parameter);
            return false;
        }

        Node previousHost = parameter.attachments.getParentParameterHost();
        int previousSlot = parameter.attachments.getParentParameterSlotIndex();

        if (previousHost != null && (previousHost != this || previousSlot != slotIndex)) {
            previousHost.detachParameter(previousSlot);
        }

        Node replaced = attachments.getAttachedParameter(slotIndex);
        if (replaced != null && replaced != parameter) {
            replaced = attachments.detachParameter(slotIndex);
            if (replaced != null) {
                replaced.setSocketsHidden(false);
                replaced.recalculateDimensions();
                replaced.setPositionSilently(getX() + getWidth() + PARAMETER_SLOT_MARGIN_HORIZONTAL, getY());
            }
        }

        attachments.attachParameter(this, slotIndex, parameter);
        parameter.setDragging(false);
        parameter.setSelected(false);
        parameter.setSocketsHidden(true);
        parameter.recalculateDimensions();

        refreshAttachedParameterValues();

        recalculateDimensions();
        updateAttachedParameterPositions();
        updateParentControlLayout();

        return true;
    }

    public void detachParameter() {
        detachParameter(0);
    }

    public void detachParameter(int slotIndex) {
        Node parameter = attachments.detachParameter(slotIndex);
        if (parameter == null) {
            return;
        }
        parameter.setSocketsHidden(false);
        parameter.recalculateDimensions();
        parameter.setPositionSilently(getX() + getWidth() + PARAMETER_SLOT_MARGIN_HORIZONTAL, getY());

        refreshAttachedParameterValues();
        recalculateDimensions();
        updateAttachedParameterPositions();
        updateParentControlLayout();
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
        if (attachments.getParentControl() != null) {
            attachments.getParentControl().recalculateDimensions();
            attachments.getParentControl().updateAttachedSensorPosition();
        }
    }

    void notifyParentParameterHostOfResize() {
        if (attachments.getParentParameterHost() == null || attachments.getParentParameterSlotIndex() < 0) {
            return;
        }
        attachments.getParentParameterHost().onAttachedParameterResized(attachments.getParentParameterSlotIndex());
    }

    private void onAttachedParameterResized(int slotIndex) {
        recalculateDimensions();
        updateParentControlLayout();
    }

    void notifyParentActionControlOfResize() {
        if (attachments.getParentActionControl() == null) {
            return;
        }
        attachments.getParentActionControl().onAttachedActionResized();
    }

    private void onAttachedActionResized() {
        recalculateDimensions();
        updateAttachedActionPosition();
    }

    void notifyParentControlOfResize() {
        if (attachments.getParentControl() == null) {
            return;
        }
        attachments.getParentControl().onAttachedSensorResized();
    }

    private void onAttachedSensorResized() {
        recalculateDimensions();
        updateAttachedSensorPosition();
    }

    boolean applyParameterValuesFromMap(Map<String, String> values) {
        return parameterValues.applyParameterValuesFromMap(values);
    }

    Map<String, String> adjustParameterValuesForSlot(Map<String, String> values, int slotIndex, Node parameterNode) {
        return parameterValues.adjustParameterValuesForSlot(values, slotIndex, parameterNode);
    }

    private void refreshAttachedParameterValues() {
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
        return NodeCompatibility.canAttachToSlot(this, node, NodeSlotType.ACTION, 0);
    }

    public boolean attachActionNode(Node node) {
        if (!canAcceptActionNode(node)) {
            return false;
        }

        if (attachments.isActionNodeAttachedTo(this, node)) {
            updateAttachedActionPosition();
            return true;
        }

        if (node.attachments.getParentActionControl() != null) {
            node.attachments.getParentActionControl().detachActionNode();
        }

        Node previous = attachments.attachActionNode(this, node);
        if (previous != null) {
            previous.setDragging(false);
            previous.setSelected(false);
            previous.setPositionSilently(getX() + getWidth() + ACTION_SLOT_MARGIN_HORIZONTAL, getY());
        }

        node.setDragging(false);
        node.setSelected(false);
        node.setSocketsHidden(true);

        recalculateDimensions();
        updateAttachedActionPosition();
        return true;
    }

    public void detachActionNode() {
        Node node = attachments.detachActionNode();
        if (node != null) {
            node.setSocketsHidden(false);
            recalculateDimensions();
        }
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
        Map<String, String> values = new HashMap<>();
        for (NodeParameter parameter : parameters) {
            String key = parameter.getName();
            String value = parameter.getStringValue();
            values.put(key, value);
            values.put(normalizeParameterKey(key), value);
        }

        NodeBehaviorDefinition behaviorDefinition = NodeBehaviorDefinitionRegistry.get(type);
        if (behaviorDefinition != null && behaviorDefinition.hasParameterBehavior()) {
            return behaviorDefinition.exportValues(this, values);
        }

        switch (type) {
            case LIST_LENGTH -> {
                Optional<Integer> length = resolveListLengthValue(this);
                String amount = length.map(String::valueOf).orElse("0");
                values.put("Amount", amount);
                values.put(normalizeParameterKey("Amount"), amount);
                values.put("Count", amount);
                values.put(normalizeParameterKey("Count"), amount);
                values.put("Threshold", amount);
                values.put(normalizeParameterKey("Threshold"), amount);
                values.put("Value", amount);
                values.put(normalizeParameterKey("Value"), amount);
            }
            case LIST_ITEM -> {
                Node resolved = resolveListItemValueNode(this, null, false, null);
                if (resolved != null) {
                    return resolved.exportParameterValues();
                }
            }
            case OPERATOR_RANDOM -> {
                double min = getDoubleParameter("Min", 0.0);
                double max = getDoubleParameter("Max", 1.0);
                double randomValue = generateRandomValueWithRounding(min, max);
                String value = Double.toString(randomValue);
                values.put("Amount", value);
                values.put(normalizeParameterKey("Amount"), value);
                values.put("Count", value);
                values.put(normalizeParameterKey("Count"), value);
                values.put("Threshold", value);
                values.put(normalizeParameterKey("Threshold"), value);
                values.put("Value", value);
                values.put(normalizeParameterKey("Value"), value);
            }
            case OPERATOR_MOD -> {
                double modValue = resolveModValue().orElse(0.0);
                String value = Double.toString(modValue);
                values.put("Amount", value);
                values.put(normalizeParameterKey("Amount"), value);
                values.put("Count", value);
                values.put(normalizeParameterKey("Count"), value);
                values.put("Threshold", value);
                values.put(normalizeParameterKey("Threshold"), value);
                values.put("Value", value);
                values.put(normalizeParameterKey("Value"), value);
            }
            case SENSOR_POSITION_OF -> {
                Node parameterNode = getAttachedParameter(0);
                if (parameterNode == null) {
                    break;
                }
                Optional<Vec3> resolved = resolvePositionTarget(parameterNode, null, null);
                if (resolved.isEmpty()) {
                    break;
                }
                Vec3 position = resolved.get();
                if (isSensorPositionSingleAxisMode()) {
                    String componentKey = getSensorPositionComponentKey();
                    String componentValue = switch (componentKey) {
                        case "X" -> Double.toString(position.x);
                        case "Y" -> Double.toString(position.y);
                        case "Z" -> Double.toString(position.z);
                        default -> "";
                    };
                    if (!componentValue.isEmpty()) {
                        values.put("Amount", componentValue);
                        values.put(normalizeParameterKey("Amount"), componentValue);
                        values.put("Count", componentValue);
                        values.put(normalizeParameterKey("Count"), componentValue);
                        values.put("Threshold", componentValue);
                        values.put(normalizeParameterKey("Threshold"), componentValue);
                        values.put("Value", componentValue);
                        values.put(normalizeParameterKey("Value"), componentValue);
                    }
                } else {
                    String xValue = Double.toString(position.x);
                    String yValue = Double.toString(position.y);
                    String zValue = Double.toString(position.z);
                    values.put("X", xValue);
                    values.put(normalizeParameterKey("X"), xValue);
                    values.put("Y", yValue);
                    values.put(normalizeParameterKey("Y"), yValue);
                    values.put("Z", zValue);
                    values.put(normalizeParameterKey("Z"), zValue);
                }
            }
            case SENSOR_DISTANCE_BETWEEN -> {
                Node parameterNodeA = resolveSensorParameterNode(getAttachedParameter(0), 0);
                Node parameterNodeB = resolveSensorParameterNode(getAttachedParameter(1), 1);
                if (parameterNodeA == null || parameterNodeB == null) {
                    break;
                }
                if (!isDistanceBetweenSupportedTarget(parameterNodeA)) {
                    break;
                }
                if (!isDistanceBetweenSupportedTarget(parameterNodeB)) {
                    break;
                }
                Optional<Vec3> resolvedA = resolveDistanceBetweenTarget(parameterNodeA);
                Optional<Vec3> resolvedB = resolveDistanceBetweenTarget(parameterNodeB);
                if (resolvedA.isEmpty() || resolvedB.isEmpty()) {
                    break;
                }
                double distance = Math.sqrt(resolvedA.get().distanceToSqr(resolvedB.get()));
                String distanceValue = Double.toString(distance);
                values.put("Distance", distanceValue);
                values.put(normalizeParameterKey("Distance"), distanceValue);
            }
            case SENSOR_TARGETED_BLOCK -> {
                Optional<BlockState> targetState = getTargetedBlockState();
                if (targetState.isEmpty()) {
                    break;
                }
                BlockState state = targetState.get();
                Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                String blockId = "minecraft".equals(id.getNamespace()) ? id.getPath() : id.toString();
                String stateValue = BlockSelection.describeState(state);
                values.put("Block", blockId);
                values.put(normalizeParameterKey("Block"), blockId);
                values.put("State", stateValue);
                values.put(normalizeParameterKey("State"), stateValue);
            }
            case SENSOR_TARGETED_ENTITY -> {
                Optional<Entity> targetedEntity = getTargetedEntity();
                if (targetedEntity.isEmpty()) {
                    break;
                }
                Entity entity = targetedEntity.get();
                Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
                String entityId = "minecraft".equals(id.getNamespace()) ? id.getPath() : id.toString();
                values.put("Entity", entityId);
                values.put(normalizeParameterKey("Entity"), entityId);
                String stateValue = EntityStateOptions.describe(entity);
                values.put("State", stateValue);
                values.put(normalizeParameterKey("State"), stateValue);
            }
            case SENSOR_CURRENT_GUI -> {
                guiSensorEvaluator().exportCurrentGuiValues(values);
            }
            case SENSOR_LOOK_DIRECTION -> {
                Minecraft client = Minecraft.getInstance();
                if (client != null && client.player != null) {
                    float yaw = normalizeLookYaw(client.player.getYRot());
                    float pitch = client.player.getXRot();
                    String yawValue = formatFloat(yaw);
                    String pitchValue = formatFloat(pitch);
                    if (isSensorLookSingleAxisMode()) {
                        String componentKey = getSensorLookComponentKey();
                        String componentValue = "Yaw".equals(componentKey) ? yawValue : "Pitch".equals(componentKey) ? pitchValue : "";
                        if (!componentValue.isEmpty()) {
                            values.put("Amount", componentValue);
                            values.put(normalizeParameterKey("Amount"), componentValue);
                            values.put("Count", componentValue);
                            values.put(normalizeParameterKey("Count"), componentValue);
                            values.put("Threshold", componentValue);
                            values.put(normalizeParameterKey("Threshold"), componentValue);
                            values.put("Value", componentValue);
                            values.put(normalizeParameterKey("Value"), componentValue);
                        }
                    } else {
                        values.put("Yaw", yawValue);
                        values.put(normalizeParameterKey("Yaw"), yawValue);
                        values.put("Pitch", pitchValue);
                        values.put(normalizeParameterKey("Pitch"), pitchValue);
                    }
                }
            }
            case SENSOR_CURRENT_HAND -> {
                Optional<Integer> currentSlot = getCurrentHotbarSlot();
                if (currentSlot.isEmpty()) {
                    break;
                }
                String slotValue = Integer.toString(currentSlot.get());
                values.put("Slot", slotValue);
                values.put(normalizeParameterKey("Slot"), slotValue);
                values.put("SourceSlot", slotValue);
                values.put(normalizeParameterKey("SourceSlot"), slotValue);
                values.put("TargetSlot", slotValue);
                values.put(normalizeParameterKey("TargetSlot"), slotValue);
            }
            case SENSOR_IS_ON_GROUND -> {
                Optional<Double> distanceFromGround = getDistanceFromGround();
                if (distanceFromGround.isEmpty()) {
                    break;
                }
                String distanceValue = Double.toString(distanceFromGround.get());
                values.put("Distance", distanceValue);
                values.put(normalizeParameterKey("Distance"), distanceValue);
                values.put("Value", distanceValue);
                values.put(normalizeParameterKey("Value"), distanceValue);
            }
            case SENSOR_TARGETED_BLOCK_FACE -> {
                Optional<Direction> targetFace = getTargetedBlockFace();
                if (targetFace.isEmpty()) {
                    break;
                }
                String faceValue = targetFace.get().toString().toLowerCase(Locale.ROOT);
                values.put("Side", faceValue);
                values.put(normalizeParameterKey("Side"), faceValue);
                values.put("Face", faceValue);
                values.put(normalizeParameterKey("Face"), faceValue);
                values.put("Text", faceValue);
                values.put(normalizeParameterKey("Text"), faceValue);
                values.put("Message", faceValue);
                values.put(normalizeParameterKey("Message"), faceValue);
            }
            case SENSOR_SLOT_ITEM_COUNT -> {
                Node slotNode = resolveSensorParameterNode(getAttachedParameter(0), 0);
                int count = 0;
                if (slotNode != null && providesTrait(slotNode, NodeValueTrait.INVENTORY_SLOT)) {
                    count = Math.max(0, resolveInventorySlotCount(slotNode).orElse(0));
                }
                String countValue = Integer.toString(count);
                values.put("Amount", countValue);
                values.put(normalizeParameterKey("Amount"), countValue);
                values.put("Count", countValue);
                values.put(normalizeParameterKey("Count"), countValue);
                values.put("Value", countValue);
                values.put(normalizeParameterKey("Value"), countValue);
            }
            case SENSOR_FIND_TRADE -> {
                villagerTradeSensorEvaluator().exportTradeSlotValues(values);
            }
        }

        return values;
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
        return type == NodeType.MESSAGE || type == NodeType.CALCULATE;
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
        if (!usesTemplateBacking()) {
            return "";
        }
        return (templateName == null || templateName.isEmpty()) ? "Template" : templateName;
    }

    public void setTemplateName(String templateName) {
        if (!usesTemplateBacking()) {
            return;
        }
        this.templateName = (templateName == null || templateName.isBlank()) ? "Template" : templateName.trim();
    }

    public int getTemplateVersion() {
        return usesTemplateBacking() ? templateVersion : 0;
    }

    public void setTemplateVersion(int templateVersion) {
        if (!usesTemplateBacking()) {
            return;
        }
        this.templateVersion = Math.max(0, templateVersion);
    }

    public NodeGraphData getTemplateGraphData() {
        return usesTemplateBacking() ? templateGraphData : null;
    }

    public void setTemplateGraphData(NodeGraphData templateGraphData) {
        if (!usesTemplateBacking()) {
            return;
        }
        this.templateGraphData = templateGraphData;
    }

    public RuntimeValueScope getRuntimeValueScope() {
        return RuntimeValueScope.orGlobal(runtimeValueScope);
    }

    /** Creates a user-facing editor node. Named runtime values default to global scope. */
    public static Node createForEditor(NodeType type, int x, int y) {
        return new Node(type, x, y);
    }

    public void setRuntimeValueScope(RuntimeValueScope runtimeValueScope) {
        this.runtimeValueScope = RuntimeValueScope.orGlobal(runtimeValueScope);
    }

    public void toggleRuntimeValueScope() {
        runtimeValueScope = getRuntimeValueScope() == RuntimeValueScope.GLOBAL
            ? RuntimeValueScope.CHAIN
            : RuntimeValueScope.GLOBAL;
    }

    public boolean supportsRuntimeValueScope() {
        return RuntimeValueScope.appliesTo(type);
    }

    public int getMessageFieldCount() {
        return Math.max(1, messageLines.size());
    }

    public List<String> getMessageLines() {
        return messageLines;
    }

    public String getMessageLine(int index) {
        if (index < 0 || index >= getMessageFieldCount()) {
            return "";
        }
        if (index >= messageLines.size()) {
            return "";
        }
        String value = messageLines.get(index);
        return value == null ? "" : value;
    }

    public void setMessageLine(int index, String value) {
        if (!hasMessageInputFields() || index < 0 || index >= MAX_MESSAGE_LINES) {
            return;
        }
        while (index >= messageLines.size()) {
            messageLines.add(getDefaultMessageLineValue());
        }
        messageLines.set(index, sanitizeMessageLine(value));
    }

    public void setMessageLines(List<String> lines) {
        messageLines.clear();
        if (lines != null) {
            for (String line : lines) {
                if (messageLines.size() >= MAX_MESSAGE_LINES) {
                    break;
                }
                messageLines.add(sanitizeMessageLine(line));
            }
        }
        if (messageLines.isEmpty()) {
            messageLines.add(getDefaultMessageLineValue());
        }
        layoutState.clearMessageFieldContentWidthOverride();
        recalculateDimensions();
    }

    public void addMessageLine(String value) {
        if (!hasMessageInputFields()) {
            return;
        }
        if (messageLines.size() >= MAX_MESSAGE_LINES) {
            return;
        }
        String lineValue = sanitizeMessageLine(value);
        if (type == NodeType.CALCULATE && lineValue.isBlank()) {
            lineValue = getDefaultCalculationLineValue(messageLines.size());
        }
        messageLines.add(lineValue);
        layoutState.clearMessageFieldContentWidthOverride();
        recalculateDimensions();
    }

    public boolean removeMessageLine(int index) {
        if (!hasMessageInputFields() || messageLines.size() <= 1) {
            return false;
        }
        if (index < 0 || index >= messageLines.size()) {
            return false;
        }
        messageLines.remove(index);
			layoutState.clearMessageFieldContentWidthOverride();
        recalculateDimensions();
        return true;
    }

    public boolean isMessageClientSide() {
        return type == NodeType.MESSAGE && messageClientSide;
    }

    public boolean hasMessageScopeToggle() {
        return type == NodeType.MESSAGE;
    }

    public void setMessageClientSide(boolean messageClientSide) {
        if (type != NodeType.MESSAGE) {
            return;
        }
        this.messageClientSide = messageClientSide;
    }

    public void toggleMessageClientSide() {
        if (type != NodeType.MESSAGE) {
            return;
        }
        messageClientSide = !messageClientSide;
    }

    public String getMessageFieldLabelText(int index) {
        if (type == NodeType.CALCULATE) {
            return "Output " + getCalculationVariableLabel(index);
        }
        return getMessageFieldCount() > 1 ? "Message " + (index + 1) : "Message";
    }

    private String getDefaultMessageLineValue() {
        return type == NodeType.CALCULATE ? getDefaultCalculationLineValue(messageLines.size()) : "Hello World";
    }

    private String sanitizeMessageLine(String value) {
        String sanitized = value == null ? "" : value;
        if (sanitized.length() > MAX_MESSAGE_LINE_LENGTH) {
            return sanitized.substring(0, MAX_MESSAGE_LINE_LENGTH);
        }
        return sanitized;
    }

    private String getDefaultCalculationLineValue(int index) {
        return getCalculationVariableLabel(index) + " = 0";
    }

    private String getCalculationVariableLabel(int index) {
        int value = Math.max(0, index);
        StringBuilder builder = new StringBuilder();
        do {
            int remainder = value % 26;
            builder.insert(0, (char) ('A' + remainder));
            value = value / 26 - 1;
        } while (value >= 0);
        return builder.toString();
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
        return type == NodeType.WRITE_BOOK || type == NodeType.WRITE_SIGN;
    }

    public boolean hasBookTextPageInput() {
        return type == NodeType.WRITE_BOOK;
    }

    public String getBookText() {
        return getBookTextForPage(1);
    }

    public void setBookText(String text) {
        setBookTextForPage(1, text);
    }

    public int getBookTextMaxChars() {
        return type == NodeType.WRITE_SIGN ? SIGN_MAX_CHARS : BOOK_PAGE_MAX_CHARS;
    }

    public int getBookTextMaxCharsPerLine() {
        return type == NodeType.WRITE_SIGN ? SIGN_LINE_MAX_CHARS : 0;
    }

    public int getBookTextMaxLines() {
        return type == NodeType.WRITE_SIGN ? SIGN_MAX_LINES : 0;
    }

    public int getBookTextPopupWidth() {
        return type == NodeType.WRITE_SIGN ? 300 : 340;
    }

    public int getBookTextPopupHeight() {
        return type == NodeType.WRITE_SIGN ? 230 : 280;
    }

    public String getBookTextEditorTitle() {
        return type == NodeType.WRITE_SIGN ? "Edit Sign Text" : "Edit Book Text";
    }

    public String getBookTextForPage(int pageNumber) {
        if (type == NodeType.WRITE_SIGN) {
            return bookText != null ? bookText : "";
        }
        int pageIndex = Math.max(0, pageNumber - 1);
        if (pageIndex < bookPages.size()) {
            String value = bookPages.get(pageIndex);
            return value != null ? value : "";
        }
        if (pageIndex == 0 && bookText != null) {
            return bookText;
        }
        return "";
    }

    public void setBookTextForPage(int pageNumber, String text) {
        if (type == NodeType.WRITE_SIGN) {
            bookText = normalizeSignText(text);
            return;
        }
        int safePageNumber = Math.max(1, pageNumber);
        ensureBookPageCapacity(safePageNumber);
        String normalized = text == null ? "" : text;
        if (normalized.length() > BOOK_PAGE_MAX_CHARS) {
            normalized = normalized.substring(0, BOOK_PAGE_MAX_CHARS);
        }
        bookPages.set(safePageNumber - 1, normalized);
        if (safePageNumber == 1) {
            bookText = normalized;
        }
    }

    public List<String> getBookPages() {
        return new ArrayList<>(bookPages);
    }

    public void setBookPages(List<String> pages) {
        if (type == NodeType.WRITE_SIGN) {
            String first = (pages == null || pages.isEmpty()) ? "" : pages.get(0);
            bookText = normalizeSignText(first);
            return;
        }
        bookPages.clear();
        if (pages != null) {
            for (String page : pages) {
                String normalized = page == null ? "" : page;
                if (normalized.length() > BOOK_PAGE_MAX_CHARS) {
                    normalized = normalized.substring(0, BOOK_PAGE_MAX_CHARS);
                }
                bookPages.add(normalized);
            }
        }
        if (bookPages.isEmpty()) {
            bookPages.add("");
        }
        bookText = bookPages.getFirst();
    }

    private void ensureBookPageCapacity(int pageNumber) {
        int targetSize = Math.max(1, pageNumber);
        while (bookPages.size() < targetSize) {
            bookPages.add("");
        }
    }

    private String normalizeSignText(String raw) {
        String text = raw == null ? "" : raw;
        if (text.length() > SIGN_MAX_CHARS) {
            text = text.substring(0, SIGN_MAX_CHARS);
        }
        String[] split = text.split("\\n", -1);
        int lineCount = Math.min(SIGN_MAX_LINES, split.length);
        StringBuilder normalized = new StringBuilder();
        for (int i = 0; i < lineCount; i++) {
            String line = split[i] == null ? "" : split[i];
            if (line.length() > SIGN_LINE_MAX_CHARS) {
                line = line.substring(0, SIGN_LINE_MAX_CHARS);
            }
            if (i > 0) {
                normalized.append('\n');
            }
            normalized.append(line);
        }
        String result = normalized.toString();
        if (result.length() > SIGN_MAX_CHARS) {
            return result.substring(0, SIGN_MAX_CHARS);
        }
        return result;
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
        CompletableFuture<Void> future = new CompletableFuture<>();
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();

        if (hasParameterSlot()) {
            int requiredSlotCount = getParameterSlotCount();
            for (int i = 0; i < requiredSlotCount; i++) {
                if (isParameterSlotRequired(i) && getAttachedParameter(i) == null) {
                    String label = getParameterSlotLabel(i);
                    NodeExecutionCompletion.fail(this, client, future,
                        type.getDisplayName() + " requires a " + label.toLowerCase(Locale.ROOT) + " parameter before it can run.");
                    return future;
                }
            }
        }

        if (!reportEmptyParametersForNode(this, future)) {
            return future;
        }

        if (requiresInGameRuntime() && (client == null || client.player == null || client.level == null)) {
            NodeExecutionCompletion.fail(this, client, future,
                type.getDisplayName() + " requires an in-game world before it can run.");
            return future;
        }

        if (!requiresClientThreadExecution()) {
            try {
                ExecutionManager.getInstance().runWithExecutionContext(executionId,
                    () -> NodeCommandDispatcher.execute(this, future));
            } catch (Exception e) {
                LOGGER.warn("Error executing node {}: {}", type, e.getMessage(), e);
                NodeExecutionCompletion.completeExceptionally(future, e);
            }
            return future;
        }

        if (client != null) {
            client.execute(() -> {
                try {
                    ExecutionManager.getInstance().runWithExecutionContext(executionId,
                        () -> NodeCommandDispatcher.execute(this, future));
                } catch (Exception e) {
                    LOGGER.warn("Error executing node {}: {}", type, e.getMessage(), e);
                    NodeExecutionCompletion.completeExceptionally(future, e);
                }
            });
        } else {
            NodeExecutionCompletion.completeExceptionally(future, new RuntimeException("Minecraft client not available"));
        }

        return future;
    }

    private boolean requiresInGameRuntime() {
        if (type == null) {
            return false;
        }
        if (type.requiresBaritone()) {
            return true;
        }
        NodeCategory category = type.getCategory();
        if (category == NodeCategory.NAVIGATION || category == NodeCategory.WORLD || category == NodeCategory.PLAYER) {
            return true;
        }
        if (category == NodeCategory.SENSORS) {
            return type != NodeType.SENSOR_CHAT_MESSAGE
                && type != NodeType.SENSOR_JOINED_SERVER
                && type != NodeType.SENSOR_FABRIC_EVENT
                && type != NodeType.SENSOR_KEY_PRESSED;
        }
        if (category == NodeCategory.INTERFACE) {
            return type != NodeType.MESSAGE && type != NodeType.STICKY_NOTE;
        }
        if (category == NodeCategory.PARAMETERS) {
            return type == NodeType.PARAM_PLAYER
                || type == NodeType.PARAM_ENTITY
                || type == NodeType.PARAM_GUI
                || type == NodeType.PARAM_INVENTORY_SLOT
                || type == NodeType.PARAM_HAND
                || type == NodeType.PARAM_PLACE_TARGET
                || type == NodeType.PARAM_CLOSEST;
        }
        return false;
    }

    private boolean requiresClientThreadExecution() {
        return switch (type) {
            case EVENT_CALL,
                EVENT_FUNCTION,
                START,
                ROUTINE_ENTRY,
                ROUTINE_CALL,
                ROUTINE_INPUT,
                SET_VARIABLE,
                CALCULATE,
                CONTROL_REPEAT,
                CONTROL_FOREVER,
                START_CHAIN,
                RUN_PRESET,
                TEMPLATE,
                STOP_CHAIN,
                STOP_ALL -> false;
            default -> true;
        };
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

    private NodeVillagerTradeSensorEvaluator villagerTradeSensorEvaluator() {
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

    void resetBaritonePathing(Object baritone, Object mineProcess) {
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

    void resetBaritonePathing(Object baritone) {
        if (baritone == null) {
            return;
        }
        resetBaritonePathing(baritone, BaritoneApiProxy.getMineProcess(baritone));
    }

    private NodeCraftCommandExecutor craftCommandExecutor() {
        return new NodeCraftCommandExecutor(this);
    }

    boolean isCraftingScreenAvailable(net.minecraft.client.Minecraft client, NodeMode craftMode) {
        return craftCommandExecutor().isCraftingScreenAvailable(client, craftMode);
    }

    boolean isCompatibleCraftingHandler(AbstractContainerMenu handler, NodeMode craftMode) {
        return craftCommandExecutor().isCompatibleCraftingHandler(handler, craftMode);
    }

    int getRequestedCraftQuantity() {
        return craftCommandExecutor().getRequestedCraftQuantity();
    }

    boolean displayFitsPlayerGrid(Object display, Object registryManager) {
        return craftCommandExecutor().displayFitsPlayerGrid(display, registryManager);
    }

    void cacheRecipeForMode(NodeCraftCommandExecutor.CachedRecipeBook book,
                            Item targetItem,
                            CraftingRecipe recipe,
                            int outputCount,
                            NodeMode mode,
                            Object registryManager) {
        craftCommandExecutor().cacheRecipeForMode(book, targetItem, recipe, outputCount, mode, registryManager);
    }

    void cacheDisplayForMode(NodeCraftCommandExecutor.CachedRecipeBook book,
                             Item targetItem,
                             int outputCount,
                             Object display,
                             NodeMode mode,
                             Object registryManager) {
        craftCommandExecutor().cacheDisplayForMode(book, targetItem, outputCount, display, mode, registryManager);
    }

    List<java.lang.reflect.Method> getAllMethods(Class<?> type) {
        return craftCommandExecutor().getAllMethods(type);
    }

    List<RecipeHolder<?>> getCraftingRecipeEntries(Object manager) {
        return craftCommandExecutor().getCraftingRecipeEntries(manager);
    }

    ItemStack getRecipeOutput(CraftingRecipe recipe, Object registryManager) {
        return craftCommandExecutor().getRecipeOutput(recipe, registryManager);
    }

    ItemStack getDisplayOutput(Object display, Object registryManager) {
        return craftCommandExecutor().getDisplayOutput(display, registryManager);
    }

    boolean recipeFitsPlayerGrid(CraftingRecipe recipe) {
        return craftCommandExecutor().recipeFitsPlayerGrid(recipe);
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

    static double getBlockInteractionReach(net.minecraft.client.Minecraft client) {
        if (client == null || client.player == null) {
            return DEFAULT_REACH_DISTANCE;
        }
        return Math.max(0.0D, client.player.blockInteractionRange());
    }

    static double getBlockInteractionReachSquared(net.minecraft.client.Minecraft client) {
        double reach = getBlockInteractionReach(client);
        return reach * reach;
    }

    static double getEntityInteractionReachSquared(net.minecraft.client.Minecraft client) {
        double reach = DEFAULT_REACH_DISTANCE;
        if (client != null && client.player != null) {
            reach = Math.max(0.0D, client.player.entityInteractionRange());
        }
        return reach * reach;
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

    private NodeGuiSensorEvaluator guiSensorEvaluator() {
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
        applySneakState(client, active);
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

    void applySneakState(net.minecraft.client.Minecraft client, boolean active) {
        if (client == null || client.player == null) {
            return;
        }
        client.player.setShiftKeyDown(active);
        if (client.options != null && client.options.keyShift != null) {
            client.options.keyShift.setDown(active);
        }
    }

    void waitForSneakSync(net.minecraft.client.Minecraft client, boolean previousState, boolean desiredState) throws InterruptedException {
        if (client == null || client.isSameThread() || previousState == desiredState) {
            return;
        }
        Thread.sleep(SNEAK_SYNC_DELAY_MS);
    }

    BlockHitResult raycastBlockFromOrientation(net.minecraft.client.Minecraft client, float yaw, float pitch, double distance) {
        if (client == null || client.player == null || client.level == null) {
            return null;
        }
        Vec3 eyePos = client.player.getEyePosition();
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        Vec3 direction = new Vec3(
            -Math.sin(yawRad) * Math.cos(pitchRad),
            -Math.sin(pitchRad),
            Math.cos(yawRad) * Math.cos(pitchRad)
        );
        double reachDistance = getBlockInteractionReach(client);
        double rayDistance = distance > 0.0 ? Math.min(distance, reachDistance) : reachDistance;
        Vec3 end = eyePos.add(direction.scale(rayDistance));
        HitResult hit = client.level.clip(new ClipContext(
            eyePos,
            end,
            ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE,
            client.player
        ));
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            return blockHit;
        }
        return null;
    }
    
    void runOnClientThread(net.minecraft.client.Minecraft client, Runnable task) throws InterruptedException {
        if (client == null || client.isSameThread()) {
            task.run();
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<RuntimeException> error = new AtomicReference<>();
        client.execute(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                error.set(e);
            } finally {
                latch.countDown();
            }
        });
        latch.await();
        if (error.get() != null) {
            throw error.get();
        }
    }

    <T> T supplyFromClient(net.minecraft.client.Minecraft client, java.util.function.Supplier<T> supplier) throws InterruptedException {
        if (client == null || client.isSameThread()) {
            return supplier.get();
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<RuntimeException> error = new AtomicReference<>();
        client.execute(() -> {
            try {
                result.set(supplier.get());
            } catch (RuntimeException e) {
                error.set(e);
            } finally {
                latch.countDown();
            }
        });
        latch.await();
        if (error.get() != null) {
            throw error.get();
        }
        return result.get();
    }

    int clampInventorySlot(Inventory inventory, int slot) {
        return Mth.clamp(slot, 0, inventory.getContainerSize() - 1);
    }

    int getOffhandInventoryIndex(Inventory inventory) {
        if (inventory == null || inventory.getContainerSize() <= 0) {
            return -1;
        }
        int index = PLAYER_OFFHAND_INVENTORY_INDEX;
        if (index >= inventory.getContainerSize()) {
            return inventory.getContainerSize() - 1;
        }
        return index;
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
        if (!attachments.hasAttachedParameters()) {
            return null;
        }
        List<Integer> slotIndices = new ArrayList<>(attachments.getAttachedParameterSlotIndices());
        Collections.sort(slotIndices);
        for (Integer slotIndex : slotIndices) {
            Node parameter = attachments.getAttachedParameter(slotIndex);
            if (parameter == null || !parameter.isParameterNode()) {
                continue;
            }
            NodeType parameterType = parameter.getType();
            NodeType resolvedType = parameterType == NodeType.LIST_ITEM
                ? parameter.getResolvedValueType()
                : parameterType;
            for (NodeType allowed : allowedTypes) {
                if (parameterType == allowed || resolvedType == allowed) {
                    return parameter;
                }
            }
        }
        Node fallback = getAttachedParameter();
        if (fallback != null) {
            sendIncompatibleParameterMessage(fallback);
        }
        return null;
    }

    boolean providesTrait(Node node, NodeValueTrait trait) {
        if (node == null || trait == null) {
            return false;
        }
        EnumSet<NodeValueTrait> traits = node.getProvidedTraits();
        return traits.contains(trait);
    }

    Node resolveSensorParameterNode(Node parameterNode, int slotIndex) {
        if (parameterNode == null) {
            return null;
        }
        if (parameterNode.getType() == NodeType.VARIABLE) {
            return resolveVariableValueNode(parameterNode, slotIndex, null);
        }
        return parameterNode;
    }

    private NodeTargetSensorEvaluator targetSensorEvaluator() {
        return new NodeTargetSensorEvaluator(this);
    }

    private Optional<BlockState> getTargetedBlockState() {
        return targetSensorEvaluator().getTargetedBlockState();
    }

    Optional<BlockPos> getTargetedBlockPos() {
        return targetSensorEvaluator().getTargetedBlockPos();
    }

    Optional<Entity> getTargetedEntity() {
        return targetSensorEvaluator().getTargetedEntity();
    }

    private Optional<Direction> getLookDirection() {
        return targetSensorEvaluator().getLookDirection();
    }

    private Optional<Integer> getCurrentHotbarSlot() {
        return targetSensorEvaluator().getCurrentHotbarSlot();
    }

    private Optional<Direction> getTargetedBlockFace() {
        return targetSensorEvaluator().getTargetedBlockFace();
    }

    Optional<BlockHitResult> getCurrentBlockHitResult() {
        return targetSensorEvaluator().getCurrentBlockHitResult();
    }

    public boolean evaluateSensor() {
        return sensorCoordinator.evaluateSensor();
    }

    private NodeOperatorSensorEvaluator operatorSensorEvaluator() {
        return new NodeOperatorSensorEvaluator(this);
    }

    private NodePlayerStateSensorEvaluator playerStateSensorEvaluator() {
        return new NodePlayerStateSensorEvaluator(this);
    }

    private Optional<Double> getDistanceFromGround() {
        return playerStateSensorEvaluator().getDistanceFromGround();
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

    private boolean isFalling(double distance) {
        return playerStateSensorEvaluator().isFalling(distance);
    }

    Optional<Boolean> resolveBooleanFromNode(Node node) {
        return operatorSensorEvaluator().resolveBooleanFromNode(node);
    }

    public Node createRuntimeVariableSnapshot(ExecutionManager.RuntimeVariable runtimeVariable) {
        return operatorSensorEvaluator().createRuntimeVariableSnapshot(runtimeVariable);
    }

    /** Evaluates one attached argument into an immutable value snapshot for a routine call frame. */
    public ExecutionManager.RuntimeVariable captureAttachedRuntimeValue(int slotIndex, int executionId) {
        Node valueNode = getAttachedParameter(slotIndex);
        if (valueNode == null) return null;
        if (valueNode.getType() == NodeType.VARIABLE) {
            valueNode = resolveVariableValueNode(valueNode, slotIndex, null);
            if (valueNode == null) return null;
        } else if (valueNode.getType() == NodeType.ROUTINE_INPUT) {
            ExecutionManager.RuntimeVariable framed = ExecutionManager.getInstance()
                .getRoutineInputValue(executionId, valueNode.getRoutineInputId());
            if (framed != null) return framed;
        }
        if (valueNode.isSensorNode() && NodeCatalog.isBooleanSensor(valueNode.getType())) {
            String value = Boolean.toString(valueNode.evaluateSensor());
            Map<String, String> values = new HashMap<>();
            values.put("Toggle", value);
            values.put(normalizeParameterKey("Toggle"), value);
            return new ExecutionManager.RuntimeVariable(NodeType.PARAM_BOOLEAN, values);
        }
        NodeType valueType = valueNode.getResolvedValueType();
        if (valueType == null || valueType == NodeType.ROUTINE_INPUT) valueType = valueNode.getType();
        return new ExecutionManager.RuntimeVariable(valueType, valueNode.exportParameterValues());
    }

    Optional<Boolean> compareParameterNodes(Node left, Node right) {
        return operatorSensorEvaluator().compareParameterNodes(left, right);
    }

    String formatCanonicalValueMap(Map<String, String> values) {
        return operatorSensorEvaluator().formatCanonicalValueMap(values);
    }

    Optional<Double> resolveComparableNumber(Node node) {
        return operatorSensorEvaluator().resolveComparableNumber(node);
    }

    Optional<Double> resolveComparableNumberWithVariables(Node node, int slotIndex) {
        return operatorSensorEvaluator().resolveComparableNumberWithVariables(node, slotIndex);
    }

    Optional<Integer> resolveInventorySlotCount(Node slotNode) {
        if (slotNode == null || !providesTrait(slotNode, NodeValueTrait.INVENTORY_SLOT)) {
            return Optional.empty();
        }
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client == null || client.player == null) {
            return Optional.empty();
        }
        Inventory inventory = client.player.getInventory();
        AbstractContainerMenu handler = client.player.containerMenu;
        int slotValue = parseNodeInt(slotNode, "Slot", 0);
        SlotSelectionType selectionType = resolveInventorySlotSelectionType(slotNode);
        SlotResolution resolved = resolveInventorySlot(handler, inventory, slotValue, selectionType);
        if (resolved == null || resolved.slot == null) {
            return Optional.empty();
        }
        ItemStack stack = resolved.slot.getItem();
        if (stack == null || stack.isEmpty()) {
            return Optional.of(0);
        }
        return Optional.of(stack.getCount());
    }

    boolean evaluateConditionFromParameters() {
        return sensorCoordinator.evaluateConditionFromParameters();
    }

    private NodeProximitySensorEvaluator proximitySensorEvaluator() {
        return new NodeProximitySensorEvaluator(this);
    }

    private boolean evaluateSensorCondition(SensorConditionType type, String blockId, String entityId, int x, int y, int z) {
        return proximitySensorEvaluator().evaluateSensorCondition(type, blockId, entityId, x, y, z);
    }

    private boolean isTouchingBlock(String blockId) {
        return proximitySensorEvaluator().isTouchingBlock(blockId);
    }

    private boolean isTouchingBlock(List<BlockSelection> selections) {
        return proximitySensorEvaluator().isTouchingBlock(selections);
    }

    private boolean isTouchingEntity(String entityId) {
        return proximitySensorEvaluator().isTouchingEntity(entityId);
    }

    private boolean isTouchingEntity(String entityId, String state) {
        return proximitySensorEvaluator().isTouchingEntity(entityId, state);
    }

    private boolean isAtCoordinates(int x, int y, int z) {
        return proximitySensorEvaluator().isAtCoordinates(x, y, z);
    }

    private boolean isBlockAhead(String blockId) {
        return proximitySensorEvaluator().isBlockAhead(blockId);
    }

    private boolean isBlockAhead(List<BlockSelection> selections) {
        return proximitySensorEvaluator().isBlockAhead(selections);
    }

    private boolean isBlockBelow(String blockId) {
        return proximitySensorEvaluator().isBlockBelow(blockId);
    }

    private boolean isBlockBelow(List<BlockSelection> selections) {
        return proximitySensorEvaluator().isBlockBelow(selections);
    }

    private List<BlockSelection> parseBlockSelectionList(String blockId) {
        return proximitySensorEvaluator().parseBlockSelectionList(blockId);
    }

    boolean matchesAnyBlock(List<BlockSelection> selections, BlockState state) {
        return proximitySensorEvaluator().matchesAnyBlock(selections, state);
    }

    private NodeBasicSensorEvaluator basicSensorEvaluator() {
        return new NodeBasicSensorEvaluator(this);
    }

    private boolean isKeyPressed(String keyName) {
        return basicSensorEvaluator().isKeyPressed(keyName);
    }

    Integer resolveKeyCode(String keyName) {
        return basicSensorEvaluator().resolveKeyCode(keyName);
    }

    Integer resolveMouseButtonCode(String buttonName) {
        return basicSensorEvaluator().resolveMouseButtonCode(buttonName);
    }

    private boolean isHealthBelow(double amount) {
        return basicSensorEvaluator().isHealthBelow(amount);
    }

    private boolean isHungerBelow(int amount) {
        return basicSensorEvaluator().isHungerBelow(amount);
    }

    private NodeInventorySensorEvaluator inventorySensorEvaluator() {
        return new NodeInventorySensorEvaluator(this);
    }

    private boolean hasItemInInventory(String itemId) {
        return inventorySensorEvaluator().hasItemInInventory(itemId);
    }

    private boolean hasItemAmountInInventory(String itemId, int requiredAmount) {
        return inventorySensorEvaluator().hasItemAmountInInventory(itemId, requiredAmount);
    }

    boolean stackMatchesAnyItem(ItemStack stack, List<String> itemIds) {
        return inventorySensorEvaluator().stackMatchesAnyItem(stack, itemIds);
    }

    private NodeVisibilitySensorEvaluator visibilitySensorEvaluator() {
        return new NodeVisibilitySensorEvaluator(this);
    }

    private boolean isResourceRendered(String resourceId) {
        return visibilitySensorEvaluator().isResourceRendered(resourceId);
    }

    private boolean isEntityRendered(String entityId, String state) {
        return visibilitySensorEvaluator().isEntityRendered(entityId, state);
    }

    private boolean isResourceVisible(String resourceId) {
        return visibilitySensorEvaluator().isResourceVisible(resourceId);
    }

    private boolean isEntityVisible(String entityId, String state) {
        return visibilitySensorEvaluator().isEntityVisible(entityId, state);
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
