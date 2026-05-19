package com.pathmind.nodes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pathmind.data.NodeGraphData;
import com.pathmind.data.NodeGraphPersistence;
import com.pathmind.data.PresetManager;
import com.pathmind.execution.ExecutionManager;
import com.pathmind.execution.PathmindNavigator;
import com.pathmind.execution.PreciseCompletionTracker;
import com.pathmind.ui.overlay.NodeErrorNotificationOverlay;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.BaritoneApiProxy;
import com.pathmind.util.BaritoneDependencyChecker;
import com.pathmind.util.BlockSelection;
import com.pathmind.util.CameraCompatibilityBridge;
import com.pathmind.util.ChatMessageTracker;
import com.pathmind.util.ChatScreenCompatibilityBridge;
import com.pathmind.util.EntityCompatibilityBridge;
import com.pathmind.util.EntityStateOptions;
import com.pathmind.util.FabricEventTracker;
import com.pathmind.util.GameProfileCompatibilityBridge;
import com.pathmind.util.GuiSelectionMode;
import com.pathmind.util.InputCompatibilityBridge;
import com.pathmind.util.InventorySlotModeHelper;
import com.pathmind.util.PlayerInventoryBridge;
import com.pathmind.util.RecipeCompatibilityBridge;
import com.pathmind.util.ServerJoinTracker;
import it.unimi.dsi.fastutil.ints.IntList;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WritableBookContentComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.BookUpdateC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a single node in the Pathmind visual editor.
 * Similar to Blender's shader nodes, each node has inputs, outputs, and parameters.
 */
public class Node {

    private static final Logger LOGGER = LoggerFactory.getLogger(Node.class);
    private static final Method DO_ATTACK_METHOD = resolveDoAttackMethod();
    private static final Method SYNC_SELECTED_SLOT_METHOD =
        resolveSyncSelectedSlotMethod();
    static final Gson LIST_ENTRY_GSON = new Gson();
    static final String LIST_ENTRY_SERIALIZED_PREFIX = "pm_list:";
    public static final int NO_OUTPUT = -1;
    private final String id;
    private final NodeType type;
    private NodeMode mode;
    private final NodeLayoutState layoutState;
    private final NodeInteractionState interactionState;
    private final NodeAttachments attachments;
    private final NodeRuntimeState runtimeState;
    static final int MIN_WIDTH = 92;
    static final int MIN_HEIGHT = 44;
    static final int EVENT_FUNCTION_MIN_HEIGHT = 36;
    static final int CHAR_PIXEL_WIDTH = 6;
    static final int HEADER_HEIGHT = 18;
    static final int PARAM_LINE_HEIGHT = 20;
    static final int PARAM_PADDING_TOP = 2;
    static final int PARAM_PADDING_BOTTOM = 4;
    private static final int MAX_PARAMETER_LABEL_LENGTH = 20;
    private static final String DIRECTION_MODE_EXACT = "exact";
    private static final String DIRECTION_MODE_CARDINAL = "cardinal";
    private static final String BOOLEAN_MODE_LITERAL = "literal";
    private static final String BOOLEAN_MODE_VARIABLE = "variable";
    private static final String RECIPE_CACHE_FILE_NAME = "recipe_cache.json";
    private static final int RECIPE_CACHE_VERSION = 2;
    private static final int RECIPE_WARMUP_RECIPE_BATCH_SIZE = 8;
    private static final int RECIPE_WARMUP_DISPLAY_BATCH_SIZE = 4;
    private static final int RECIPE_WARMUP_SAVE_INTERVAL = 64;
    private static final Gson RECIPE_CACHE_GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();
    private static final Object RECIPE_CACHE_LOCK = new Object();
    private static volatile CachedRecipeBook cachedRecipeBook;
    private static volatile RecipeCacheWarmupState recipeCacheWarmupState;
    static final int BODY_PADDING_NO_PARAMS = 10;
    static final int START_END_SIZE = 36;
    private static final String CHAT_MESSAGE_PREFIX =
        "\u00A74[\u00A7cPathmind\u00A74] \u00A77";
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
    static final int OPERATOR_SLOT_GAP = 14;
    static final int MINIMAL_NODE_TAB_WIDTH = 6;
    static final int PARAMETER_FIELD_PADDING = 12;
    static final int PLAYER_ARMOR_SLOT_COUNT = 4;
    private static final int PLAYER_OFFHAND_INVENTORY_INDEX =
        PlayerInventory.MAIN_SIZE + PLAYER_ARMOR_SLOT_COUNT;
    static final int PARAMETER_SLOT_BOTTOM_PADDING = 6;
    static final int SLOT_AREA_PADDING_TOP = 0;
    static final int SLOT_AREA_PADDING_BOTTOM = 6;
    static final int SLOT_VERTICAL_SPACING = 6;
    private static final int BOOLEAN_TOGGLE_MARGIN_HORIZONTAL = 6;
    private static final int BOOLEAN_TOGGLE_TOP_MARGIN = 8;
    private static final int BOOLEAN_TOGGLE_HEIGHT = 16;
    private static final int BOOLEAN_TOGGLE_BOTTOM_MARGIN = 8;
    private static final int COORDINATE_FIELD_WIDTH = 44;
    private static final int COORDINATE_FIELD_HEIGHT = 16;
    private static final int COORDINATE_FIELD_TEXT_PADDING = 3;
    private static final int COORDINATE_FIELD_SPACING = 6;
    private static final int COORDINATE_FIELD_TOP_MARGIN = 6;
    private static final int COORDINATE_FIELD_LABEL_HEIGHT = 10;
    private static final int COORDINATE_FIELD_BOTTOM_MARGIN = 6;
    private static final int SCREEN_PICK_BUTTON_TOP_MARGIN = 6;
    private static final int SCREEN_PICK_BUTTON_HEIGHT = 16;
    private static final int SCREEN_PICK_BUTTON_MIN_WIDTH = 70;
    private static final int SCREEN_PICK_BUTTON_BOTTOM_MARGIN = 6;
    private static final int AMOUNT_FIELD_TOP_MARGIN = 6;
    private static final int AMOUNT_FIELD_LABEL_HEIGHT = 10;
    private static final int AMOUNT_FIELD_HEIGHT = 16;
    private static final int AMOUNT_FIELD_TEXT_PADDING = 3;
    private static final int AMOUNT_FIELD_BOTTOM_MARGIN = 6;
    private static final int WAIT_AMOUNT_FIELD_GAP = 4;
    static final int AMOUNT_TOGGLE_WIDTH = 18;
    private static final int AMOUNT_TOGGLE_HEIGHT = 10;
    static final int AMOUNT_TOGGLE_SPACING = 6;
    static final int AMOUNT_SIGN_TOGGLE_WIDTH = 28;
    private static final int AMOUNT_SIGN_TOGGLE_HEIGHT = 16;
    private static final int RANDOM_ROUNDING_FIELD_TOP_MARGIN = 6;
    private static final int RANDOM_ROUNDING_FIELD_LABEL_HEIGHT = 10;
    private static final int RANDOM_ROUNDING_FIELD_HEIGHT = 16;
    private static final int RANDOM_ROUNDING_FIELD_BOTTOM_MARGIN = 6;
    static final int RANDOM_ROUNDING_TOGGLE_WIDTH = 18;
    private static final int RANDOM_ROUNDING_TOGGLE_HEIGHT = 10;
    static final int RANDOM_ROUNDING_TOGGLE_SPACING = 6;
    static final int MESSAGE_FIELD_MARGIN_HORIZONTAL = 6;
    private static final int MESSAGE_FIELD_TOP_MARGIN = 6;
    private static final int MESSAGE_FIELD_LABEL_HEIGHT = 10;
    private static final int MESSAGE_FIELD_HEIGHT = 16;
    private static final int MESSAGE_FIELD_VERTICAL_GAP = 6;
    private static final int MESSAGE_FIELD_BOTTOM_MARGIN = 6;
    static final int MESSAGE_FIELD_MIN_CONTENT_WIDTH = 120;
    static final int MESSAGE_FIELD_TEXT_PADDING = 3;
    static final int MESSAGE_BUTTON_SIZE = 10;
    static final int MESSAGE_BUTTON_PADDING = 4;
    static final int MESSAGE_BUTTON_SPACING = 4;
    private static final int MESSAGE_SCOPE_MARGIN_HORIZONTAL = 6;
    private static final int MESSAGE_SCOPE_TOP_MARGIN = 6;
    private static final int MESSAGE_SCOPE_LABEL_HEIGHT = 10;
    private static final int MESSAGE_SCOPE_TOGGLE_HEIGHT = 16;
    private static final int MESSAGE_SCOPE_BOTTOM_MARGIN = 6;
    private static final int SCHEMATIC_FIELD_TOP_MARGIN = 6;
    private static final int SCHEMATIC_FIELD_LABEL_HEIGHT = 10;
    private static final int SCHEMATIC_FIELD_HEIGHT = 16;
    private static final int SCHEMATIC_FIELD_BOTTOM_MARGIN = 6;
    static final int STOP_TARGET_FIELD_MARGIN_HORIZONTAL = 8;
    private static final int STOP_TARGET_FIELD_TOP_MARGIN = 6;
    private static final int STOP_TARGET_FIELD_LABEL_HEIGHT = 0;
    private static final int STOP_TARGET_FIELD_HEIGHT = 16;
    private static final int STOP_TARGET_FIELD_TEXT_PADDING = 3;
    private static final int STOP_TARGET_FIELD_BOTTOM_MARGIN = 6;
    static final int STOP_TARGET_FIELD_MIN_WIDTH = 48;
    static final int VARIABLE_FIELD_MARGIN_HORIZONTAL = 8;
    private static final int VARIABLE_FIELD_TOP_MARGIN = 6;
    private static final int VARIABLE_FIELD_LABEL_HEIGHT = 0;
    private static final int VARIABLE_FIELD_HEIGHT = 16;
    private static final int VARIABLE_FIELD_TEXT_PADDING = 3;
    private static final int VARIABLE_FIELD_BOTTOM_MARGIN = 6;
    static final int VARIABLE_FIELD_MIN_WIDTH = 80;
    private static final int MODE_FIELD_TOP_MARGIN = 6;
    private static final int MODE_FIELD_LABEL_HEIGHT = 0;
    private static final int MODE_FIELD_HEIGHT = 16;
    private static final int MODE_FIELD_BOTTOM_MARGIN = 6;
    static final int BOOK_TEXT_BUTTON_MARGIN_HORIZONTAL = 6;
    private static final int BOOK_TEXT_TOP_MARGIN = 6;
    private static final int BOOK_TEXT_BUTTON_HEIGHT = 16;
    static final int BOOK_TEXT_BUTTON_MIN_WIDTH = 70;
    private static final int BOOK_TEXT_LABEL_HEIGHT = 10;
    private static final int BOOK_TEXT_PAGE_FIELD_HEIGHT = 16;
    private static final int BOOK_TEXT_FIELD_SPACING = 6;
    private static final int BOOK_TEXT_BOTTOM_MARGIN = 6;
    static final int SIGN_LINE_MAX_CHARS = 15;
    static final int SIGN_MAX_LINES = 4;
    static final int SIGN_MAX_CHARS = 63;
    static final int POPUP_EDIT_BUTTON_MARGIN_HORIZONTAL = 6;
    private static final int POPUP_EDIT_BUTTON_TOP_MARGIN = 4;
    private static final int POPUP_EDIT_BUTTON_HEIGHT = 16;
    static final int POPUP_EDIT_BUTTON_MIN_WIDTH = 70;
    private static final int POPUP_EDIT_BUTTON_BOTTOM_MARGIN = 6;
    static final int TEMPLATE_NODE_WIDTH = 160;
    static final int TEMPLATE_NODE_HEIGHT = 108;
    private static final int EVENT_NAME_FIELD_MARGIN_HORIZONTAL = 6;
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
    private static final Method CLIENT_WORLD_GET_ENTITY_BY_UUID =
        resolveClientWorldGetEntityByUuid();
    static final double DEFAULT_REACH_DISTANCE_SQUARED = 25.0D;
    static final double DEFAULT_DIRECTION_DISTANCE = 16.0;
    static final long SNEAK_SYNC_DELAY_MS = 75L;
    private static final Pattern UNSAFE_RESOURCE_ID_PATTERN = Pattern.compile(
        "[^a-z0-9_:/.-]"
    );
    private static final Object GOTO_BREAK_LOCK = new Object();
    private static final AtomicInteger ACTIVE_GOTO_BREAK_BLOCKING_REQUESTS =
        new AtomicInteger(0);
    private static final AtomicInteger ACTIVE_GOTO_PLACE_BLOCKING_REQUESTS =
        new AtomicInteger(0);
    private static final AtomicInteger ACTIVE_BARITONE_CACHE_OVERRIDE_REQUESTS =
        new AtomicInteger(0);
    private static final AtomicInteger ACTIVE_BARITONE_EXPLORE_OVERRIDE_REQUESTS =
        new AtomicInteger(0);
    private static final AtomicInteger ACTIVE_BARITONE_PATH_HISTORY_OVERRIDE_REQUESTS =
        new AtomicInteger(0);
    private static final AtomicInteger ACTIVE_BARITONE_CACHED_SCAN_OVERRIDE_REQUESTS =
        new AtomicInteger(0);
    private static Boolean gotoBreakOriginalValue = null;
    private static Boolean gotoPlaceOriginalValue = null;
    private static Boolean baritoneChunkCachingOriginalValue = null;
    private static Boolean baritonePathThroughCachedOnlyOriginalValue = null;
    private static Boolean baritoneExploreForBlocksOriginalValue = null;
    private static Boolean baritoneSplicePathOriginalValue = null;
    private static Integer baritoneMaxPathHistoryLengthOriginalValue = null;
    private static Integer baritonePathHistoryCutoffAmountOriginalValue = null;
    private static Integer baritoneMaxCachedWorldScanCountOriginalValue = null;
    static final ScheduledExecutorService MESSAGE_SCHEDULER =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Pathmind-Message-Scheduler");
            t.setDaemon(true);
            return t;
        });
    private final List<NodeParameter> parameters;
    private boolean booleanToggleValue = true;
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
    private boolean customNodeInstance;
    private NodeGraphData templateGraphData;

    private boolean usesTemplateBacking() {
        return type == NodeType.TEMPLATE || type == NodeType.CUSTOM_NODE;
    }

    public Node(NodeType type, int x, int y) {
        this.id = java.util.UUID.randomUUID().toString();
        this.type = type;
        this.mode = NodeMode.getDefaultModeForNodeType(type);
        this.layoutState = new NodeLayoutState(
            x,
            y,
            STICKY_NOTE_MIN_WIDTH + 32,
            STICKY_NOTE_MIN_HEIGHT + 20
        );
        this.interactionState = new NodeInteractionState();
        this.attachments = new NodeAttachments();
        this.runtimeState = new NodeRuntimeState();
        this.parameters = new ArrayList<>();
        this.messageLines = new ArrayList<>();
        if (type == NodeType.MESSAGE) {
            this.messageLines.add("Hello World");
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
        this.customNodeInstance = type == NodeType.CUSTOM_NODE;
        this.templateGraphData = null;
        initializeParameters();
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
        COMPLETE,
    }

    enum ParameterUsage {
        POSITION,
        LOOK_ORIENTATION,
    }

    private static final Set<String> MOVE_ITEM_SOURCE_KEYS =
        createParameterKeySet("SourceSlot", "FirstSlot", "Count", "Amount");
    private static final Set<String> MOVE_ITEM_TARGET_KEYS =
        createParameterKeySet("TargetSlot", "SecondSlot", "Count", "Amount");
    private static final Set<String> PLACE_POSITION_BLOCK_KEYS =
        createParameterKeySet("Block", "Blocks", "BlockId");
    private static final Set<String> HOTBAR_INVENTORY_SLOT_ITEM_KEYS =
        createParameterKeySet("Item", "Items", "Count", "Amount");
    private static final String PARAM_ID_BOOLEAN_MODE = "boolean_mode";
    private static final String PARAM_ID_BOOLEAN_TOGGLE = "boolean_toggle";
    private static final String PARAM_ID_BOOLEAN_VARIABLE = "boolean_variable";
    private static final String PARAM_ID_CREATE_LIST_USE_RADIUS =
        "create_list_use_radius";
    private static final String PARAM_ID_CREATE_LIST_RADIUS =
        "create_list_radius";
    private static final String PARAM_ID_CREATE_LIST_USE_BLOCK_CAP =
        "create_list_use_block_cap";
    private static final String PARAM_ID_CREATE_LIST_MAX_BLOCKS =
        "create_list_max_blocks";
    private static final String PARAM_ID_RANDOM_ROUNDING =
        "random_rounding_mode";
    private static final String PARAM_ID_RANDOM_USE_ROUNDING =
        "random_use_rounding";
    private static final String PARAM_ID_CHANGE_VARIABLE_AMOUNT =
        "change_variable_amount";
    private static final String PARAM_ID_CHANGE_VARIABLE_OPERATION =
        "change_variable_operation";
    private static final String PARAM_ID_TRADE_NUMBER = "trade_number";
    private static final String PARAM_ID_TRADE_COUNT = "trade_count";
    private static final String PARAM_ID_DIRECTION_MODE = "direction_mode";
    private static final String PARAM_ID_DIRECTION_CARDINAL =
        "direction_cardinal";
    private static final String PARAM_ID_DIRECTION_YAW = "direction_yaw";
    private static final String PARAM_ID_DIRECTION_PITCH = "direction_pitch";
    private static final String PARAM_ID_DIRECTION_YAW_OFFSET =
        "direction_yaw_offset";
    private static final String PARAM_ID_DIRECTION_PITCH_OFFSET =
        "direction_pitch_offset";
    private static final String PARAM_ID_DIRECTION_DISTANCE =
        "direction_distance";
    private static final String PARAM_ID_ROTATION_YAW = "rotation_yaw";
    private static final String PARAM_ID_ROTATION_PITCH = "rotation_pitch";
    private static final String PARAM_ID_ROTATION_YAW_OFFSET =
        "rotation_yaw_offset";
    private static final String PARAM_ID_ROTATION_PITCH_OFFSET =
        "rotation_pitch_offset";
    private static final String PARAM_ID_ROTATION_DISTANCE =
        "rotation_distance";
    private static final String PARAM_ID_LOOK_YAW = "look_yaw";
    private static final String PARAM_ID_LOOK_PITCH = "look_pitch";
    private static final String PARAM_ID_INVENTORY_SLOT_INDEX =
        "inventory_slot_index";
    private static final String PARAM_ID_INVENTORY_SLOT_MODE =
        "inventory_slot_mode";
    private static final String PARAM_ID_HOTBAR_SLOT = "hotbar_slot";
    private static final String PARAM_ID_CLICK_SLOT_INDEX = "click_slot_index";
    private static final String PARAM_ID_DROP_SLOT_INDEX = "drop_slot_index";
    private static final String PARAM_ID_MOVE_ITEM_SOURCE_SLOT =
        "move_item_source_slot";
    private static final String PARAM_ID_MOVE_ITEM_TARGET_SLOT =
        "move_item_target_slot";
    private static final String PARAM_ID_EQUIP_ARMOR_SOURCE_SLOT =
        "equip_armor_source_slot";
    private static final String PARAM_ID_EQUIP_ARMOR_SLOT = "equip_armor_slot";
    private static final String PARAM_ID_EQUIP_HAND_SOURCE_SLOT =
        "equip_hand_source_slot";
    private static final String PARAM_ID_EQUIP_HAND_HAND = "equip_hand_hand";
    private static final String PARAM_ID_UI_CLICK_SYNC_ID = "ui_click_sync_id";
    private static final String PARAM_ID_UI_CLICK_REVISION =
        "ui_click_revision";
    private static final String PARAM_ID_UI_CLICK_SLOT = "ui_click_slot";
    private static final String PARAM_ID_UI_CLICK_BUTTON = "ui_click_button";
    private static final String PARAM_ID_UI_CLICK_ACTION = "ui_click_action";
    private static final String PARAM_ID_UI_CLICK_TIMES = "ui_click_times";
    private static final String PARAM_ID_UI_CLICK_DELAY = "ui_click_delay";
    private static final String PARAM_ID_UI_BUTTON_SYNC_ID =
        "ui_button_sync_id";
    private static final String PARAM_ID_UI_BUTTON_ID = "ui_button_id";
    private static final String PARAM_ID_UI_BUTTON_TIMES = "ui_button_times";
    private static final String PARAM_ID_UI_BUTTON_DELAY = "ui_button_delay";

    static String normalizeParameterKey(String key) {
        if (key == null) {
            return "";
        }
        return key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static Set<String> createParameterKeySet(String... keys) {
        Set<String> keySet = new HashSet<>();
        if (keys == null) {
            return keySet;
        }
        for (String key : keys) {
            if (key == null || key.isEmpty()) {
                continue;
            }
            keySet.add(key);
            keySet.add(key.toLowerCase(Locale.ROOT));
            keySet.add(normalizeParameterKey(key));
        }
        return keySet;
    }

    private static NodeParameter createParameter(
        String id,
        String name,
        ParameterType type,
        String defaultValue
    ) {
        return new NodeParameter(id, name, type, defaultValue);
    }

    /** Sends a HUD notification error to the player (e.g. for invalid numeric/variable input). */
    public void sendNodeErrorMessageToPlayer(String message) {
        net.minecraft.client.MinecraftClient client =
            net.minecraft.client.MinecraftClient.getInstance();
        if (client != null) {
            sendNodeErrorMessage(client, message);
        }
    }

    void sendNodeErrorMessage(
        net.minecraft.client.MinecraftClient client,
        String message
    ) {
        if (client == null || message == null || message.isEmpty()) {
            return;
        }

        client.execute(() ->
            sendNodeErrorMessageOnClientThread(client, message)
        );
    }

    private void sendNodeErrorMessageOnClientThread(
        net.minecraft.client.MinecraftClient client,
        String message
    ) {
        if (client == null || message == null || message.isEmpty()) {
            return;
        }

        NodeErrorNotificationOverlay.getInstance().show(
            message,
            type != null ? type.getColor() : UITheme.STATE_ERROR
        );
    }

    void sendNodeInfoMessage(
        net.minecraft.client.MinecraftClient client,
        String message
    ) {
        if (client == null || message == null || message.isEmpty()) {
            return;
        }

        client.execute(() ->
            sendNodeInfoMessageOnClientThread(client, message)
        );
    }

    private void sendNodeInfoMessageOnClientThread(
        net.minecraft.client.MinecraftClient client,
        String message
    ) {
        if (
            client == null ||
            client.player == null ||
            message == null ||
            message.isEmpty()
        ) {
            return;
        }

        client.player.sendMessage(
            Text.literal(CHAT_MESSAGE_PREFIX + message),
            false
        );
    }

    /**
     * Gets the Baritone instance for the current player
     * @return Baritone instance or null if not available
     */
    Object getBaritone() {
        try {
            return BaritoneApiProxy.getPrimaryBaritone();
        } catch (Exception e) {
            System.err.println(
                "Failed to get Baritone instance: " + e.getMessage()
            );
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
        if (type == NodeType.LIST_ITEM) {
            NodeType resolved = getResolvedValueType();
            if (resolved != NodeType.LIST_ITEM) {
                return NodeTraitRegistry.getProvidedTraits(resolved);
            }
        }
        EnumSet<NodeValueTrait> traits = NodeTraitRegistry.getProvidedTraits(
            type
        );
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
        initializeParameters();

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

    private void setPositionSilently(int x, int y) {
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

    public Text getDisplayName() {
        return Text.literal(type.getDisplayName());
    }

    public boolean isSensorNode() {
        return isSensorType(type);
    }

    public boolean isStickyNote() {
        return type == NodeType.STICKY_NOTE;
    }

    private boolean isBooleanNotOperator() {
        return type == NodeType.OPERATOR_BOOLEAN_NOT;
    }

    boolean isComparisonOperator() {
        return (
            type == NodeType.OPERATOR_EQUALS ||
            type == NodeType.OPERATOR_NOT ||
            type == NodeType.OPERATOR_BOOLEAN_OR ||
            type == NodeType.OPERATOR_BOOLEAN_AND ||
            type == NodeType.OPERATOR_BOOLEAN_XOR ||
            type == NodeType.OPERATOR_GREATER ||
            type == NodeType.OPERATOR_LESS
        );
    }

    public boolean isParameterNode() {
        return NodeTraitRegistry.isParameterNode(type);
    }

    public boolean shouldRenderInlineParameters() {
        return (
            type == NodeType.UI_UTILS ||
            type == NodeType.SENSOR_FABRIC_EVENT ||
            type == NodeType.SENSOR_ATTRIBUTE_DETECTION ||
            type == NodeType.TRADE ||
            type == NodeType.REMOVE_LIST_ITEM
        );
    }

    boolean isInlineParameterNode() {
        return (
            isParameterNode() &&
            type != NodeType.OPERATOR_MOD &&
            type != NodeType.PARAM_DURATION &&
            type != NodeType.SENSOR_POSITION_OF &&
            type != NodeType.SENSOR_DISTANCE_BETWEEN &&
            type != NodeType.SENSOR_SLOT_ITEM_COUNT
        );
    }

    public static boolean isSensorType(NodeType nodeType) {
        return NodeTraitRegistry.isBooleanSensor(nodeType);
    }

    public static boolean isParameterType(NodeType nodeType) {
        return NodeTraitRegistry.isParameterNode(nodeType);
    }

    public boolean canAcceptSensor() {
        return NodeCompatibility.canHostSlot(type, NodeSlotType.SENSOR);
    }

    public boolean hasSensorSlot() {
        return canAcceptSensor();
    }

    public boolean canAcceptParameter() {
        if (!NodeCompatibility.canHostSlot(type, NodeSlotType.PARAMETER)) {
            return false;
        }
        if (usesVillagerTradeNumberField()) {
            return false;
        }
        if (!NodeTraitRegistry.canHostParameter(type)) {
            return false;
        }
        if (
            isParameterNode() &&
            type != NodeType.OPERATOR_MOD &&
            type != NodeType.PARAM_BLOCK_FACE &&
            type != NodeType.SENSOR_POSITION_OF &&
            type != NodeType.SENSOR_DISTANCE_BETWEEN &&
            type != NodeType.SENSOR_SLOT_ITEM_COUNT
        ) {
            return false;
        }
        return true;
    }

    public boolean hasParameterSlot() {
        return canAcceptParameter();
    }

    public boolean isStopControlNode() {
        return type == NodeType.STOP_CHAIN || type == NodeType.STOP_ALL;
    }

    public boolean usesMinimalNodePresentation() {
        return (
            isStopControlNode() ||
            type == NodeType.START_CHAIN ||
            type == NodeType.RUN_PRESET ||
            type == NodeType.CRAWL ||
            type == NodeType.CROUCH ||
            type == NodeType.SPRINT ||
            type == NodeType.FLY ||
            type == NodeType.JUMP ||
            type == NodeType.CONTROL_FORK ||
            type == NodeType.CONTROL_JOIN_ANY ||
            type == NodeType.CONTROL_JOIN_ALL ||
            type == NodeType.SENSOR_TARGETED_BLOCK_FACE ||
            type == NodeType.SENSOR_TARGETED_BLOCK ||
            type == NodeType.SENSOR_TARGETED_ENTITY ||
            type == NodeType.SENSOR_LOOK_DIRECTION ||
            type == NodeType.SENSOR_CURRENT_HAND ||
            type == NodeType.SENSOR_IS_ON_GROUND ||
            isComparisonOperator() ||
            type == NodeType.OPEN_INVENTORY ||
            type == NodeType.CLOSE_GUI
        );
    }

    public boolean canAcceptParameterAt(int slotIndex) {
        if (!canAcceptParameter()) {
            return false;
        }
        return slotIndex >= 0 && slotIndex < getParameterSlotCount();
    }

    public boolean canAcceptParameterNode(Node parameterNode, int slotIndex) {
        return NodeCompatibility.canAttachToSlot(
            this,
            parameterNode,
            NodeSlotType.PARAMETER,
            slotIndex
        );
    }

    private boolean isParameterSlotRequired(int slotIndex) {
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
            if (
                blockParameter != null &&
                blockParameter.getType() == NodeType.VARIABLE
            ) {
                return false;
            }
            // Only block placement targets provide coordinates for slot 1 conflicts.
            return (
                blockParameter == null ||
                !blockParameterProvidesPlacementCoordinates(blockParameter)
            );
        }
        if (type == NodeType.PLACE_HAND) {
            return false;
        }
        return slotIndex == 0;
    }

    private boolean isParameterSupported(Node parameter, int slotIndex) {
        return NodeCompatibility.canAttachToSlot(
            this,
            parameter,
            NodeSlotType.PARAMETER,
            slotIndex
        );
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
        return attachments.getAttachedSensor() != null
            ? attachments.getAttachedSensor().getId()
            : null;
    }

    public String getParentControlId() {
        return attachments.getParentControl() != null
            ? attachments.getParentControl().getId()
            : null;
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
        return attachments.getParentParameterHost() != null
            ? attachments.getParentParameterHost().getId()
            : null;
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
            Node hostStart = attachments
                .getParentParameterHost()
                .resolveExecutionStartNode();
            if (hostStart != null) {
                return hostStart;
            }
        }
        if (attachments.getParentControl() != null) {
            Node controlStart = attachments
                .getParentControl()
                .resolveExecutionStartNode();
            if (controlStart != null) {
                return controlStart;
            }
        }
        if (attachments.getParentActionControl() != null) {
            Node actionStart = attachments
                .getParentActionControl()
                .resolveExecutionStartNode();
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

    public boolean isGotoAllowBreakWhileExecuting() {
        if (type != NodeType.GOTO && type != NodeType.TRAVEL) {
            return false;
        }
        com.pathmind.data.SettingsManager.Settings settings =
            com.pathmind.data.SettingsManager.getCurrent();
        return (
            settings.gotoAllowBreakWhileExecuting != null &&
            settings.gotoAllowBreakWhileExecuting
        );
    }

    public void setGotoAllowBreakWhileExecuting(
        boolean gotoAllowBreakWhileExecuting
    ) {
        if (type != NodeType.GOTO && type != NodeType.TRAVEL) {
            return;
        }
        this.gotoAllowBreakWhileExecuting = gotoAllowBreakWhileExecuting;
    }

    public boolean isGotoAllowPlaceWhileExecuting() {
        if (type != NodeType.GOTO && type != NodeType.TRAVEL) {
            return false;
        }
        com.pathmind.data.SettingsManager.Settings settings =
            com.pathmind.data.SettingsManager.getCurrent();
        return (
            settings.gotoAllowPlaceWhileExecuting != null &&
            settings.gotoAllowPlaceWhileExecuting
        );
    }

    public void setGotoAllowPlaceWhileExecuting(
        boolean gotoAllowPlaceWhileExecuting
    ) {
        if (type != NodeType.GOTO && type != NodeType.TRAVEL) {
            return;
        }
        this.gotoAllowPlaceWhileExecuting = gotoAllowPlaceWhileExecuting;
    }

    public boolean isKeyPressedActivatesInGuis() {
        if (type != NodeType.SENSOR_KEY_PRESSED) {
            return true;
        }
        com.pathmind.data.SettingsManager.Settings settings =
            com.pathmind.data.SettingsManager.getCurrent();
        return (
            settings.keyPressedActivatesInGuis == null ||
            settings.keyPressedActivatesInGuis
        );
    }

    public void setKeyPressedActivatesInGuis(
        boolean keyPressedActivatesInGuis
    ) {
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
        return attachments.getAttachedActionNode() != null
            ? attachments.getAttachedActionNode().getId()
            : null;
    }

    public String getParentActionControlId() {
        return attachments.getParentActionControl() != null
            ? attachments.getParentActionControl().getId()
            : null;
    }

    public void setActiveRepeatUntilGuard(Node guard) {
        this.runtimeState.activeRepeatUntilGuard = guard;
    }

    public int getInputSocketCount() {
        if (
            type == NodeType.START ||
            type == NodeType.EVENT_FUNCTION ||
            isSensorNode() ||
            isParameterNode() ||
            isStickyNote()
        ) {
            return 0;
        }
        if (
            type == NodeType.CONTROL_JOIN_ANY ||
            type == NodeType.CONTROL_JOIN_ALL
        ) {
            return 2;
        }
        return 1;
    }

    public int getOutputSocketCount() {
        if (isSensorNode() || isParameterNode() || isStickyNote()) {
            return 0;
        }
        if (type == NodeType.STOP_ALL) {
            return 0;
        }
        if (type == NodeType.CONTROL_FOREVER) {
            return 0;
        }
        if (type == NodeType.CONTROL_IF_ELSE) {
            return 2;
        }
        if (type == NodeType.CONTROL_FORK) {
            return 2;
        }
        return 1;
    }

    public int getOutputSocketColor(int socketIndex) {
        if (type == NodeType.CONTROL_IF_ELSE) {
            if (socketIndex == 0) {
                return 0xFF4CAF50; // Green for true branch
            } else if (socketIndex == 1) {
                return 0xFFF44336; // Red for false branch
            }
        }
        return getType().getColor();
    }

    public int getSocketY(int socketIndex, boolean isInput) {
        return NodeGeometry.socketY(
            getY(),
            getHeight(),
            socketIndex,
            isInput ? getInputSocketCount() : getOutputSocketCount(),
            12,
            type == NodeType.START || type == NodeType.EVENT_FUNCTION,
            usesMinimalNodePresentation(),
            14,
            6
        );
    }

    public int getSocketX(boolean isInput) {
        return NodeGeometry.socketX(getX(), getWidth(), isInput, 4);
    }

    public void setNextOutputSocket(int socketIndex) {
        this.runtimeState.nextOutputSocket =
            socketIndex < 0 ? NO_OUTPUT : Math.max(0, socketIndex);
    }

    public int consumeNextOutputSocket() {
        int value = this.runtimeState.nextOutputSocket;
        this.runtimeState.nextOutputSocket = 0;
        return value;
    }

    public boolean shouldExecuteRepeatAttachedAction() {
        return (
            type == NodeType.CONTROL_REPEAT &&
            runtimeState.repeatExecuteAttachedAction
        );
    }

    public boolean isSocketClicked(
        int mouseX,
        int mouseY,
        int socketIndex,
        boolean isInput
    ) {
        if (interactionState.areSocketsHidden()) {
            return false;
        }
        int socketX = getSocketX(isInput);
        int socketY = getSocketY(socketIndex, isInput);
        int socketRadius = 6; // Smaller size for more space

        return NodeGeometry.isPointNear(
            socketX,
            socketY,
            socketRadius,
            mouseX,
            mouseY
        );
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
        if (isComparisonOperator()) {
            return "";
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

    private int getParameterSlotsBottom() {
        return NodeSlotLayout.parameterSlotsBottom(this);
    }

    public boolean hasCoordinateInputFields() {
        return type == NodeType.CLICK_SCREEN;
    }

    public String[] getCoordinateFieldAxes() {
        if (type == NodeType.CLICK_SCREEN) {
            return new String[] {"X", "Y"};
        }
        return new String[] {"X", "Y", "Z"};
    }

    public int getCoordinateFieldDisplayHeight() {
        if (!hasCoordinateInputFields()) {
            return 0;
        }
        int height =
            COORDINATE_FIELD_TOP_MARGIN +
            COORDINATE_FIELD_LABEL_HEIGHT +
            COORDINATE_FIELD_HEIGHT;
        if (hasScreenCoordinatePickerButton()) {
            height +=
                SCREEN_PICK_BUTTON_TOP_MARGIN +
                SCREEN_PICK_BUTTON_HEIGHT +
                SCREEN_PICK_BUTTON_BOTTOM_MARGIN;
        } else {
            height += COORDINATE_FIELD_BOTTOM_MARGIN;
        }
        return height;
    }

    public boolean showsModeFieldAboveParameterSlot() {
        return (
            type == NodeType.SENSOR_POSITION_OF &&
            supportsModeSelection() &&
            !isInlineParameterNode() &&
            !shouldRenderInlineParameters() &&
            type != NodeType.WAIT &&
            type != NodeType.PARAM_DURATION
        );
    }

    public int getModeFieldDisplayHeight() {
        if (!showsModeFieldAboveParameterSlot()) {
            return 0;
        }
        return (
            MODE_FIELD_TOP_MARGIN +
            MODE_FIELD_LABEL_HEIGHT +
            MODE_FIELD_HEIGHT +
            MODE_FIELD_BOTTOM_MARGIN
        );
    }

    public int getModeFieldTop() {
        int top = getY() + HEADER_HEIGHT;
        if (hasSchematicDropdownField()) {
            top += getSchematicFieldDisplayHeight();
        }
        if (hasVariableInputField()) {
            top += getVariableFieldDisplayHeight();
        }
        return top + MODE_FIELD_TOP_MARGIN + MODE_FIELD_LABEL_HEIGHT;
    }

    public int getModeFieldLeft() {
        return getParameterSlotLeft();
    }

    public int getModeFieldWidth() {
        return getParameterSlotWidth();
    }

    public int getModeFieldHeight() {
        return MODE_FIELD_HEIGHT;
    }

    public String getModeFieldLabelText() {
        if (type == NodeType.SENSOR_POSITION_OF) {
            return "Axis:";
        }
        return "Mode:";
    }

    public boolean isSensorPositionSingleAxisMode() {
        if (type != NodeType.SENSOR_POSITION_OF) {
            return false;
        }
        return (
            mode == NodeMode.SENSOR_POSITION_X ||
            mode == NodeMode.SENSOR_POSITION_Y ||
            mode == NodeMode.SENSOR_POSITION_Z
        );
    }

    public String getSensorPositionComponentKey() {
        if (type != NodeType.SENSOR_POSITION_OF) {
            return "";
        }
        if (mode == NodeMode.SENSOR_POSITION_X) {
            return "X";
        }
        if (mode == NodeMode.SENSOR_POSITION_Y) {
            return "Y";
        }
        if (mode == NodeMode.SENSOR_POSITION_Z) {
            return "Z";
        }
        return "";
    }

    public boolean isSensorLookSingleAxisMode() {
        if (type != NodeType.SENSOR_LOOK_DIRECTION) {
            return false;
        }
        return (
            mode == NodeMode.SENSOR_LOOK_YAW ||
            mode == NodeMode.SENSOR_LOOK_PITCH
        );
    }

    public String getSensorLookComponentKey() {
        if (type != NodeType.SENSOR_LOOK_DIRECTION) {
            return "";
        }
        if (mode == NodeMode.SENSOR_LOOK_YAW) {
            return "Yaw";
        }
        if (mode == NodeMode.SENSOR_LOOK_PITCH) {
            return "Pitch";
        }
        return "";
    }

    public NodeType getResolvedValueType() {
        return switch (type) {
            case LIST_ITEM -> {
                ExecutionManager.RuntimeList runtimeList = resolveRuntimeList(
                    this
                );
                NodeType elementType =
                    runtimeList != null ? runtimeList.getElementType() : null;
                yield elementType == NodeType.PARAM_GUI
                    ? NodeType.PARAM_INVENTORY_SLOT
                    : elementType != null
                        ? elementType
                        : NodeType.LIST_ITEM;
            }
            case SENSOR_POSITION_OF -> isSensorPositionSingleAxisMode()
                ? NodeType.PARAM_AMOUNT
                : NodeType.PARAM_COORDINATE;
            case SENSOR_DISTANCE_BETWEEN -> NodeType.PARAM_DISTANCE;
            case SENSOR_TARGETED_BLOCK_FACE -> NodeType.PARAM_BLOCK_FACE;
            case SENSOR_TARGETED_BLOCK -> NodeType.PARAM_BLOCK;
            case SENSOR_TARGETED_ENTITY -> NodeType.PARAM_ENTITY;
            case SENSOR_LOOK_DIRECTION -> isSensorLookSingleAxisMode()
                ? NodeType.PARAM_AMOUNT
                : NodeType.PARAM_ROTATION;
            case SENSOR_CURRENT_HAND -> NodeType.PARAM_INVENTORY_SLOT;
            case SENSOR_IS_ON_GROUND -> NodeType.PARAM_DISTANCE;
            case SENSOR_SLOT_ITEM_COUNT -> NodeType.PARAM_AMOUNT;
            case LIST_LENGTH -> NodeType.PARAM_AMOUNT;
            default -> type;
        };
    }

    public int getCoordinateFieldLabelTop() {
        return getParameterSlotsBottom() + COORDINATE_FIELD_TOP_MARGIN;
    }

    public int getCoordinateFieldInputTop() {
        return getCoordinateFieldLabelTop() + COORDINATE_FIELD_LABEL_HEIGHT;
    }

    public int getCoordinateFieldLabelHeight() {
        return COORDINATE_FIELD_LABEL_HEIGHT;
    }

    public int getCoordinateFieldHeight() {
        return COORDINATE_FIELD_HEIGHT;
    }

    public int getCoordinateFieldWidth() {
        return Math.max(
            COORDINATE_FIELD_WIDTH,
            layoutState.getCoordinateFieldWidthOverride()
        );
    }

    public int getCoordinateFieldSpacing() {
        return COORDINATE_FIELD_SPACING;
    }

    public int getCoordinateFieldStartX() {
        int slotLeft = getParameterSlotLeft();
        int slotWidth = getParameterSlotWidth();
        int totalFieldWidth = getCoordinateFieldTotalWidth();
        if (totalFieldWidth >= slotWidth) {
            return slotLeft;
        }
        return slotLeft + (slotWidth - totalFieldWidth) / 2;
    }

    public int getCoordinateFieldTotalWidth() {
        int axisCount = getCoordinateFieldAxes().length;
        return (
            (getCoordinateFieldWidth() * axisCount) +
            (COORDINATE_FIELD_SPACING * Math.max(0, axisCount - 1))
        );
    }

    public boolean hasScreenCoordinatePickerButton() {
        return type == NodeType.CLICK_SCREEN;
    }

    public int getScreenCoordinatePickerButtonTop() {
        return (
            getCoordinateFieldInputTop() +
            COORDINATE_FIELD_HEIGHT +
            SCREEN_PICK_BUTTON_TOP_MARGIN
        );
    }

    public int getScreenCoordinatePickerButtonLeft() {
        return getX() + POPUP_EDIT_BUTTON_MARGIN_HORIZONTAL;
    }

    public int getScreenCoordinatePickerButtonWidth() {
        return Math.max(
            SCREEN_PICK_BUTTON_MIN_WIDTH,
            getWidth() - 2 * POPUP_EDIT_BUTTON_MARGIN_HORIZONTAL
        );
    }

    public int getScreenCoordinatePickerButtonHeight() {
        return SCREEN_PICK_BUTTON_HEIGHT;
    }

    public boolean hasAmountInputField() {
        if (
            type == NodeType.COLLECT &&
            (mode == null || mode == NodeMode.COLLECT_SINGLE)
        ) {
            return true;
        }
        if (
            type == NodeType.CRAFT &&
            (mode == null ||
                mode == NodeMode.CRAFT_PLAYER_GUI ||
                mode == NodeMode.CRAFT_CRAFTING_TABLE)
        ) {
            return true;
        }
        if (type == NodeType.MOVE_ITEM) {
            return true;
        }
        if (type == NodeType.CONTROL_REPEAT) {
            return true;
        }
        if (type == NodeType.SENSOR_ITEM_IN_INVENTORY) {
            return true;
        }
        if (type == NodeType.SENSOR_ITEM_IN_SLOT) {
            return true;
        }
        if (type == NodeType.SENSOR_HEALTH_BELOW) {
            return true;
        }
        if (type == NodeType.SENSOR_HUNGER_BELOW) {
            return true;
        }
        if (type == NodeType.SENSOR_CHAT_MESSAGE) {
            return true;
        }
        if (type == NodeType.SENSOR_VILLAGER_TRADE) {
            return true;
        }
        if (type == NodeType.SENSOR_IN_STOCK) {
            return true;
        }
        if (type == NodeType.CHANGE_VARIABLE) {
            return true;
        }
        if (type == NodeType.WAIT) {
            return true;
        }
        if (type == NodeType.PARAM_DURATION) {
            return true;
        }
        if (type == NodeType.USE) {
            return true;
        }
        if (type == NodeType.SWING) {
            return true;
        }
        if (type == NodeType.DROP_ITEM) {
            return true;
        }
        return false;
    }

    public boolean hasRandomRoundingField() {
        return type == NodeType.OPERATOR_RANDOM;
    }

    public boolean hasSchematicDropdownField() {
        return type == NodeType.BUILD;
    }

    public boolean hasStopTargetInputField() {
        return (
            type == NodeType.STOP_CHAIN ||
            type == NodeType.START_CHAIN ||
            type == NodeType.RUN_PRESET ||
            type == NodeType.TEMPLATE ||
            type == NodeType.CUSTOM_NODE
        );
    }

    public boolean hasVariableInputField() {
        return (
            type == NodeType.CREATE_LIST ||
            type == NodeType.ADD_TO_LIST ||
            type == NodeType.REMOVE_FIRST_FROM_LIST ||
            type == NodeType.REMOVE_LAST_FROM_LIST ||
            type == NodeType.REMOVE_LIST_ITEM ||
            type == NodeType.REMOVE_FROM_LIST ||
            type == NodeType.LIST_LENGTH
        );
    }

    public String getStopTargetFieldParameterKey() {
        if (
            type == NodeType.RUN_PRESET ||
            type == NodeType.TEMPLATE ||
            type == NodeType.CUSTOM_NODE
        ) {
            return "Preset";
        }
        return "StartNumber";
    }

    public String getVariableFieldParameterKey() {
        return switch (type) {
            case
                CREATE_LIST,
                ADD_TO_LIST,
                REMOVE_FIRST_FROM_LIST,
                REMOVE_LAST_FROM_LIST,
                REMOVE_LIST_ITEM,
                REMOVE_FROM_LIST,
                LIST_LENGTH -> "List";
            default -> "Variable";
        };
    }

    public int getAmountFieldDisplayHeight() {
        if (!hasAmountInputField()) {
            return 0;
        }
        if (type == NodeType.WAIT || type == NodeType.PARAM_DURATION) {
            return (
                AMOUNT_FIELD_TOP_MARGIN +
                getAmountFieldLabelHeight() +
                WAIT_AMOUNT_FIELD_GAP +
                AMOUNT_FIELD_HEIGHT +
                AMOUNT_FIELD_BOTTOM_MARGIN
            );
        }
        return (
            AMOUNT_FIELD_TOP_MARGIN +
            AMOUNT_FIELD_LABEL_HEIGHT +
            AMOUNT_FIELD_HEIGHT +
            AMOUNT_FIELD_BOTTOM_MARGIN
        );
    }

    public int getAmountFieldLabelTop() {
        if (type == NodeType.CONTROL_REPEAT) {
            int top = getActionSlotTop() + getActionSlotHeight();
            return top + SLOT_AREA_PADDING_BOTTOM + AMOUNT_FIELD_TOP_MARGIN;
        }
        int top = getParameterSlotsBottom();
        if (hasCoordinateInputFields()) {
            top += getCoordinateFieldDisplayHeight();
        }
        return top + AMOUNT_FIELD_TOP_MARGIN;
    }

    public int getAmountFieldInputTop() {
        if (type == NodeType.WAIT || type == NodeType.PARAM_DURATION) {
            return (
                getAmountFieldLabelTop() +
                getAmountFieldLabelHeight() +
                WAIT_AMOUNT_FIELD_GAP
            );
        }
        return getAmountFieldLabelTop() + AMOUNT_FIELD_LABEL_HEIGHT;
    }

    public int getAmountFieldLabelHeight() {
        if (type == NodeType.WAIT || type == NodeType.PARAM_DURATION) {
            return AMOUNT_FIELD_HEIGHT;
        }
        return AMOUNT_FIELD_LABEL_HEIGHT;
    }

    public String getAmountFieldLabel() {
        if (usesVillagerTradeNumberField()) {
            return "Number";
        }
        if (type == NodeType.USE || type == NodeType.SWING) {
            return "Hold Duration";
        }
        if (type == NodeType.SENSOR_CHAT_MESSAGE) {
            return "Seconds";
        }
        if (type == NodeType.SENSOR_HEALTH_BELOW) {
            return "Health";
        }
        if (type == NodeType.SENSOR_HUNGER_BELOW) {
            return "Hunger";
        }
        if (type == NodeType.WAIT) {
            NodeMode waitMode = mode != null ? mode : NodeMode.WAIT_SECONDS;
            switch (waitMode) {
                case WAIT_TICKS:
                    return "Ticks";
                case WAIT_MINUTES:
                    return "Minutes";
                case WAIT_HOURS:
                    return "Hours";
                case WAIT_SECONDS:
                default:
                    return "Seconds";
            }
        }
        if (type == NodeType.PARAM_DURATION) {
            NodeMode waitMode = mode != null ? mode : NodeMode.WAIT_SECONDS;
            switch (waitMode) {
                case WAIT_TICKS:
                    return "Ticks";
                case WAIT_MINUTES:
                    return "Minutes";
                case WAIT_HOURS:
                    return "Hours";
                case WAIT_SECONDS:
                default:
                    return "Seconds";
            }
        }
        if (type == NodeType.CONTROL_REPEAT) {
            return "Times";
        }
        return "Amount";
    }

    public String getAmountParameterKey() {
        if (usesVillagerTradeNumberField()) {
            return "Number";
        }
        if (type == NodeType.MOVE_ITEM) {
            return "Count";
        }
        if (type == NodeType.CONTROL_REPEAT) {
            return "Count";
        }
        if (type == NodeType.WAIT) {
            return "Duration";
        }
        if (type == NodeType.PARAM_DURATION) {
            return "Duration";
        }
        if (type == NodeType.USE) {
            return "UseDurationSeconds";
        }
        if (type == NodeType.SWING) {
            return "Duration";
        }
        if (type == NodeType.DROP_ITEM) {
            return "Count";
        }
        return "Amount";
    }

    public int getAmountFieldHeight() {
        return AMOUNT_FIELD_HEIGHT;
    }

    public int getAmountFieldWidth() {
        int width = getParameterSlotWidth();
        if (hasAmountToggle()) {
            width = Math.max(
                40,
                width - (AMOUNT_TOGGLE_WIDTH + AMOUNT_TOGGLE_SPACING)
            );
        }
        if (hasAmountSignToggle()) {
            width = Math.max(
                40,
                width - (AMOUNT_SIGN_TOGGLE_WIDTH + AMOUNT_TOGGLE_SPACING)
            );
        }
        return Math.max(width, layoutState.getAmountFieldWidthOverride());
    }

    public int getAmountFieldLeft() {
        if (hasAmountSignToggle()) {
            return (
                getParameterSlotLeft() +
                AMOUNT_SIGN_TOGGLE_WIDTH +
                AMOUNT_TOGGLE_SPACING
            );
        }
        return getParameterSlotLeft();
    }

    public boolean hasAmountToggle() {
        return (
            type == NodeType.SENSOR_ITEM_IN_INVENTORY ||
            type == NodeType.SENSOR_ITEM_IN_SLOT ||
            type == NodeType.SENSOR_CHAT_MESSAGE ||
            type == NodeType.USE ||
            type == NodeType.SWING ||
            type == NodeType.DROP_ITEM
        );
    }

    public boolean hasAmountSignToggle() {
        return type == NodeType.CHANGE_VARIABLE;
    }

    public boolean isAmountInputEnabled() {
        ensureAmountToggleParameters();
        if (!hasAmountInputField()) {
            return false;
        }
        if (hasAmountToggle()) {
            NodeParameter useParam = getParameter("UseAmount");
            return useParam != null && useParam.getBoolValue();
        }
        return true;
    }

    public void setAmountInputEnabled(boolean enabled) {
        ensureAmountToggleParameters();
        if (hasAmountToggle()) {
            NodeParameter useParam = getParameter("UseAmount");
            if (useParam != null) {
                useParam.setStringValue(Boolean.toString(enabled));
            }
        }
    }

    public int getAmountToggleLeft() {
        return (
            getAmountFieldLeft() + getAmountFieldWidth() + AMOUNT_TOGGLE_SPACING
        );
    }

    public int getAmountToggleTop() {
        return (
            getAmountFieldInputTop() +
            (getAmountFieldHeight() - AMOUNT_TOGGLE_HEIGHT) / 2
        );
    }

    public int getAmountToggleWidth() {
        return AMOUNT_TOGGLE_WIDTH;
    }

    public int getAmountToggleHeight() {
        return AMOUNT_TOGGLE_HEIGHT;
    }

    public int getAmountSignToggleLeft() {
        return getParameterSlotLeft();
    }

    public int getAmountSignToggleTop() {
        return (
            getAmountFieldInputTop() +
            (getAmountFieldHeight() - AMOUNT_SIGN_TOGGLE_HEIGHT) / 2
        );
    }

    public int getAmountSignToggleWidth() {
        return AMOUNT_SIGN_TOGGLE_WIDTH;
    }

    public int getAmountSignToggleHeight() {
        return AMOUNT_SIGN_TOGGLE_HEIGHT;
    }

    public int getRandomRoundingFieldDisplayHeight() {
        if (!hasRandomRoundingField()) {
            return 0;
        }
        return (
            RANDOM_ROUNDING_FIELD_TOP_MARGIN +
            RANDOM_ROUNDING_FIELD_LABEL_HEIGHT +
            RANDOM_ROUNDING_FIELD_HEIGHT +
            RANDOM_ROUNDING_FIELD_BOTTOM_MARGIN
        );
    }

    public int getRandomRoundingFieldLabelTop() {
        return (
            getY() +
            HEADER_HEIGHT +
            getParameterDisplayHeight() +
            RANDOM_ROUNDING_FIELD_TOP_MARGIN
        );
    }

    public int getRandomRoundingFieldInputTop() {
        return (
            getRandomRoundingFieldLabelTop() +
            RANDOM_ROUNDING_FIELD_LABEL_HEIGHT
        );
    }

    public int getRandomRoundingFieldLabelHeight() {
        return RANDOM_ROUNDING_FIELD_LABEL_HEIGHT;
    }

    public int getRandomRoundingFieldHeight() {
        return RANDOM_ROUNDING_FIELD_HEIGHT;
    }

    public int getRandomRoundingFieldWidth() {
        int width = Math.max(20, getWidth() - 10);
        if (hasRandomRoundingToggle()) {
            width = Math.max(
                40,
                width -
                    (RANDOM_ROUNDING_TOGGLE_WIDTH +
                        RANDOM_ROUNDING_TOGGLE_SPACING)
            );
        }
        return width;
    }

    public int getRandomRoundingFieldLeft() {
        return getX() + 5;
    }

    public boolean hasRandomRoundingToggle() {
        return type == NodeType.OPERATOR_RANDOM;
    }

    public int getRandomRoundingToggleLeft() {
        return (
            getRandomRoundingFieldLeft() +
            getRandomRoundingFieldWidth() +
            RANDOM_ROUNDING_TOGGLE_SPACING
        );
    }

    public int getRandomRoundingToggleTop() {
        return (
            getRandomRoundingFieldInputTop() +
            (getRandomRoundingFieldHeight() - RANDOM_ROUNDING_TOGGLE_HEIGHT) / 2
        );
    }

    public int getRandomRoundingToggleWidth() {
        return RANDOM_ROUNDING_TOGGLE_WIDTH;
    }

    public int getRandomRoundingToggleHeight() {
        return RANDOM_ROUNDING_TOGGLE_HEIGHT;
    }

    public boolean isRandomRoundingEnabled() {
        ensureRandomRoundingParameters();
        NodeParameter useParam = getParameter("UseRounding");
        return useParam != null && useParam.getBoolValue();
    }

    public void setRandomRoundingEnabled(boolean enabled) {
        ensureRandomRoundingParameters();
        NodeParameter useParam = getParameter("UseRounding");
        if (useParam != null) {
            useParam.setStringValue(Boolean.toString(enabled));
        }
    }

    public String getRandomRoundingMode() {
        ensureRandomRoundingParameters();
        NodeParameter modeParam = getParameter("Rounding");
        String value = modeParam != null ? modeParam.getStringValue() : null;
        if (value == null || value.trim().isEmpty()) {
            return "round";
        }
        return normalizeRoundingMode(value);
    }

    public String getRandomRoundingModeDisplay() {
        String mode = getRandomRoundingMode();
        return switch (mode) {
            case "floor" -> "Floor";
            case "ceil" -> "Ceil";
            default -> "Round";
        };
    }

    public void setRandomRoundingMode(String mode) {
        ensureRandomRoundingParameters();
        NodeParameter modeParam = getParameter("Rounding");
        String normalized = normalizeRoundingMode(mode);
        if (modeParam == null) {
            parameters.add(
                createParameter(
                    PARAM_ID_RANDOM_ROUNDING,
                    "Rounding",
                    ParameterType.STRING,
                    normalized
                )
            );
        } else {
            modeParam.setStringValueFromUser(normalized);
        }
    }

    public String getAmountOperation() {
        NodeParameter param = getParameter("Operation");
        String value = param != null ? param.getStringValue() : null;
        if (value == null || value.trim().isEmpty()) {
            NodeParameter legacy = getParameter("Increase");
            if (legacy != null) {
                String op = legacy.getBoolValue() ? "+" : "-";
                if (param == null) {
                    parameters.add(
                        createParameter(
                            PARAM_ID_CHANGE_VARIABLE_OPERATION,
                            "Operation",
                            ParameterType.STRING,
                            op
                        )
                    );
                } else {
                    param.setStringValue(op);
                }
                return op;
            }
            return "+";
        }
        return normalizeOperation(value);
    }

    public void setAmountOperation(String operation) {
        String normalized = normalizeOperation(operation);
        NodeParameter param = getParameter("Operation");
        if (param == null) {
            parameters.add(
                createParameter(
                    PARAM_ID_CHANGE_VARIABLE_OPERATION,
                    "Operation",
                    ParameterType.STRING,
                    normalized
                )
            );
        } else {
            param.setStringValueFromUser(normalized);
        }
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
        switch (lowered) {
            case "+":
            case "add":
            case "plus":
                return "+";
            case "-":
            case "subtract":
            case "minus":
                return "-";
            case "*":
            case "x":
            case "multiply":
            case "times":
                return "*";
            case "/":
            case "divide":
                return "/";
            case "%":
            case "mod":
            case "modulo":
                return "%";
            default:
                return "+";
        }
    }

    private void ensureAmountToggleParameters() {
        NodeParameterRepair.ensureAmountToggleParameters(this);
    }

    private boolean usesVillagerTradeNumberField() {
        return (
            type == NodeType.TRADE ||
            type == NodeType.SENSOR_VILLAGER_TRADE ||
            type == NodeType.SENSOR_IN_STOCK
        );
    }

    public void ensureVillagerTradeNumberParameter() {
        NodeParameterRepair.ensureVillagerTradeNumberParameter(this);
    }

    public void ensureCreateListRadiusParameters() {
        NodeParameterRepair.ensureCreateListRadiusParameters(this);
    }

    public void repairSerializedParameters() {
        ensureBooleanParameters();
        ensureVillagerTradeNumberParameter();
        ensureCreateListRadiusParameters();
        ensureAmountToggleParameters();
        ensureRandomRoundingParameters();
        ensureCombinedDirectionParameters();
        normalizeAttributeDetectionParameters();
    }

    private boolean shouldUseLegacyVillagerTradeSelection() {
        if (!usesVillagerTradeNumberField()) {
            return false;
        }
        Node attached = resolveSensorParameterNode(getAttachedParameter(), 0);
        if (
            attached == null ||
            !providesTrait(attached, NodeValueTrait.VILLAGER_TRADE)
        ) {
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

    private void ensureRandomRoundingParameters() {
        NodeParameterRepair.ensureRandomRoundingParameters(this);
    }

    boolean isRandomRoundingParameter(NodeParameter parameter) {
        if (parameter == null || type != NodeType.OPERATOR_RANDOM) {
            return false;
        }
        String name = parameter.getName();
        return (
            "Rounding".equalsIgnoreCase(name) ||
            "UseRounding".equalsIgnoreCase(name)
        );
    }

    private String normalizeRoundingMode(String value) {
        if (value == null) {
            return "round";
        }
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            return "round";
        }
        if (trimmed.startsWith("flo") || "down".equals(trimmed)) {
            return "floor";
        }
        if (trimmed.startsWith("cei") || "up".equals(trimmed)) {
            return "ceil";
        }
        return "round";
    }

    public int getSchematicFieldDisplayHeight() {
        if (!hasSchematicDropdownField()) {
            return 0;
        }
        return (
            SCHEMATIC_FIELD_TOP_MARGIN +
            SCHEMATIC_FIELD_LABEL_HEIGHT +
            SCHEMATIC_FIELD_HEIGHT +
            SCHEMATIC_FIELD_BOTTOM_MARGIN
        );
    }

    public int getSchematicFieldLabelTop() {
        return getY() + HEADER_HEIGHT + SCHEMATIC_FIELD_TOP_MARGIN;
    }

    public int getSchematicFieldInputTop() {
        return getSchematicFieldLabelTop() + SCHEMATIC_FIELD_LABEL_HEIGHT;
    }

    public int getSchematicFieldLabelHeight() {
        return SCHEMATIC_FIELD_LABEL_HEIGHT;
    }

    public int getSchematicFieldHeight() {
        return SCHEMATIC_FIELD_HEIGHT;
    }

    public int getSchematicFieldWidth() {
        return getParameterSlotWidth();
    }

    public int getSchematicFieldLeft() {
        return getParameterSlotLeft();
    }

    public int getStopTargetFieldDisplayHeight() {
        if (!hasStopTargetInputField()) {
            return 0;
        }
        if (type == NodeType.TEMPLATE || type == NodeType.CUSTOM_NODE) {
            return 24;
        }
        return (
            STOP_TARGET_FIELD_TOP_MARGIN +
            STOP_TARGET_FIELD_HEIGHT +
            STOP_TARGET_FIELD_BOTTOM_MARGIN
        );
    }

    public int getStopTargetFieldLabelTop() {
        if (type == NodeType.TEMPLATE || type == NodeType.CUSTOM_NODE) {
            return getY() + HEADER_HEIGHT + 4;
        }
        return getParameterSlotsBottom() + STOP_TARGET_FIELD_TOP_MARGIN;
    }

    public int getStopTargetFieldInputTop() {
        return getStopTargetFieldLabelTop() + STOP_TARGET_FIELD_LABEL_HEIGHT;
    }

    public int getStopTargetFieldLabelHeight() {
        return STOP_TARGET_FIELD_LABEL_HEIGHT;
    }

    public int getStopTargetFieldHeight() {
        if (type == NodeType.TEMPLATE || type == NodeType.CUSTOM_NODE) {
            return 16;
        }
        return STOP_TARGET_FIELD_HEIGHT;
    }

    public int getStopTargetFieldWidth() {
        if (type == NodeType.TEMPLATE || type == NodeType.CUSTOM_NODE) {
            return Math.max(72, getWidth() - 12);
        }
        return Math.max(
            STOP_TARGET_FIELD_MIN_WIDTH,
            layoutState.getStopTargetFieldWidthOverride()
        );
    }

    public int getStopTargetFieldLeft() {
        if (type == NodeType.TEMPLATE || type == NodeType.CUSTOM_NODE) {
            return getX() + 6;
        }
        return (
            getX() +
            Math.max(
                STOP_TARGET_FIELD_MARGIN_HORIZONTAL,
                (getWidth() - getStopTargetFieldWidth()) / 2
            )
        );
    }

    public int getVariableFieldDisplayHeight() {
        if (!hasVariableInputField()) {
            return 0;
        }
        return (
            VARIABLE_FIELD_TOP_MARGIN +
            VARIABLE_FIELD_HEIGHT +
            VARIABLE_FIELD_BOTTOM_MARGIN
        );
    }

    public int getVariableFieldLabelTop() {
        return getY() + HEADER_HEIGHT + VARIABLE_FIELD_TOP_MARGIN;
    }

    public int getVariableFieldInputTop() {
        return getVariableFieldLabelTop() + VARIABLE_FIELD_LABEL_HEIGHT;
    }

    public int getVariableFieldLabelHeight() {
        return VARIABLE_FIELD_LABEL_HEIGHT;
    }

    public int getVariableFieldHeight() {
        return VARIABLE_FIELD_HEIGHT;
    }

    public int getVariableFieldWidth() {
        return Math.max(
            VARIABLE_FIELD_MIN_WIDTH,
            layoutState.getVariableFieldWidthOverride()
        );
    }

    public int getVariableFieldLeft() {
        return (
            getX() +
            Math.max(
                VARIABLE_FIELD_MARGIN_HORIZONTAL,
                (getWidth() - getVariableFieldWidth()) / 2
            )
        );
    }

    public boolean isPointInsideParameterSlot(int pointX, int pointY) {
        return getParameterSlotIndexAt(pointX, pointY) >= 0;
    }

    public int getParameterSlotIndexAt(int pointX, int pointY) {
        return NodeSlotLayout.parameterSlotIndexAt(this, pointX, pointY);
    }

    public void updateAttachedParameterPositions() {
        for (Integer slotIndex : attachments.getAttachedParameterSlotIndices()) {
            updateAttachedParameterPosition(slotIndex);
        }
    }

    private void updateAttachedParameterPosition(int slotIndex) {
        Node parameter = getAttachedParameter(slotIndex);
        if (parameter == null) {
            return;
        }
        int parameterWidth = parameter.getWidth();
        int parameterX = NodeGeometry.centeredChildX(
            getParameterSlotLeft(slotIndex),
            PARAMETER_SLOT_INNER_PADDING,
            getParameterSlotWidth(slotIndex),
            parameterWidth,
            parameter.usesMinimalNodePresentation() ? MINIMAL_NODE_TAB_WIDTH : 0
        );
        int parameterY = NodeGeometry.centeredChildY(
            getParameterSlotTop(slotIndex),
            PARAMETER_SLOT_INNER_PADDING,
            getParameterSlotHeight(slotIndex),
            parameter.getHeight()
        );
        if (
            parameter.hasAttachedParameter() ||
            parameter.hasAttachedSensor() ||
            parameter.hasAttachedActionNode()
        ) {
            parameter.setPosition(parameterX, parameterY);
        } else {
            parameter.setPositionSilently(parameterX, parameterY);
        }
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
        if (attachments.getAttachedSensor() == null) {
            return;
        }
        int sensorX = NodeGeometry.centeredChildX(
            getSensorSlotLeft(),
            SENSOR_SLOT_INNER_PADDING,
            getSensorSlotWidth(),
            attachments.getAttachedSensor().getWidth(),
            0
        );
        int sensorY = NodeGeometry.centeredChildY(
            getSensorSlotTop(),
            SENSOR_SLOT_INNER_PADDING,
            getSensorSlotHeight(),
            attachments.getAttachedSensor().getHeight()
        );
        attachments.getAttachedSensor().setPosition(sensorX, sensorY);
    }

    public void updateAttachedActionPosition() {
        if (attachments.getAttachedActionNode() == null) {
            return;
        }
        int nodeX = NodeGeometry.centeredChildX(
            getActionSlotLeft(),
            ACTION_SLOT_INNER_PADDING,
            getActionSlotWidth(),
            attachments.getAttachedActionNode().getWidth(),
            0
        );
        int nodeY = NodeGeometry.centeredChildY(
            getActionSlotTop(),
            ACTION_SLOT_INNER_PADDING,
            getActionSlotHeight(),
            attachments.getAttachedActionNode().getHeight()
        );
        attachments.getAttachedActionNode().setPosition(nodeX, nodeY);
    }

    public boolean attachSensor(Node sensor) {
        if (
            !canAcceptSensor() ||
            sensor == null ||
            !sensor.isSensorNode() ||
            sensor == this
        ) {
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
            previousSensor.setPositionSilently(
                getX() + getWidth() + SENSOR_SLOT_MARGIN_HORIZONTAL,
                getY()
            );
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
        if (
            parameter == null ||
            (!parameter.isParameterNode() && !parameter.isSensorNode()) ||
            parameter == this
        ) {
            return false;
        }
        if (
            (type == NodeType.PLACE || type == NodeType.PLACE_HAND) &&
            slotIndex == 1 &&
            parameter.getType() != null
        ) {
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

        if (!isParameterSupported(parameter, slotIndex)) {
            sendIncompatibleParameterMessage(parameter);
            return false;
        }

        Node previousHost = parameter.attachments.getParentParameterHost();
        int previousSlot = parameter.attachments.getParentParameterSlotIndex();

        if (
            previousHost != null &&
            (previousHost != this || previousSlot != slotIndex)
        ) {
            previousHost.detachParameter(previousSlot);
        }

        Node replaced = attachments.getAttachedParameter(slotIndex);
        if (replaced != null && replaced != parameter) {
            replaced = attachments.detachParameter(slotIndex);
            if (replaced != null) {
                replaced.setSocketsHidden(false);
                replaced.recalculateDimensions();
                replaced.setPositionSilently(
                    getX() + getWidth() + PARAMETER_SLOT_MARGIN_HORIZONTAL,
                    getY()
                );
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
        parameter.setPositionSilently(
            getX() + getWidth() + PARAMETER_SLOT_MARGIN_HORIZONTAL,
            getY()
        );

        refreshAttachedParameterValues();
        recalculateDimensions();
        updateAttachedParameterPositions();
        updateParentControlLayout();
    }

    private void updateParentControlLayout() {
        if (attachments.getParentControl() != null) {
            attachments.getParentControl().recalculateDimensions();
            attachments.getParentControl().updateAttachedSensorPosition();
        }
    }

    private void notifyParentParameterHostOfResize() {
        if (
            attachments.getParentParameterHost() == null ||
            attachments.getParentParameterSlotIndex() < 0
        ) {
            return;
        }
        attachments
            .getParentParameterHost()
            .onAttachedParameterResized(
                attachments.getParentParameterSlotIndex()
            );
    }

    private void onAttachedParameterResized(int slotIndex) {
        recalculateDimensions();
        updateParentControlLayout();
    }

    private void notifyParentActionControlOfResize() {
        if (attachments.getParentActionControl() == null) {
            return;
        }
        attachments.getParentActionControl().onAttachedActionResized();
    }

    private void onAttachedActionResized() {
        recalculateDimensions();
        updateAttachedActionPosition();
    }

    private void notifyParentControlOfResize() {
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
        if (values == null || values.isEmpty()) {
            return false;
        }
        boolean applied = false;
        for (NodeParameter target : parameters) {
            String key = target.getName();
            String value = values.get(key);
            if (value == null) {
                value = values.get(normalizeParameterKey(key));
            }
            if (value == null) {
                value = values.get(key.toLowerCase(Locale.ROOT));
            }
            if (value == null && "Resource".equalsIgnoreCase(key)) {
                value = values.get("Block");
                if (value == null) {
                    value = values.get(normalizeParameterKey("Block"));
                }
                if (value == null) {
                    value = values.get("Blocks");
                }
                if (value == null) {
                    value = values.get(normalizeParameterKey("Blocks"));
                }
                if (value == null) {
                    value = values.get("Item");
                }
                if (value == null) {
                    value = values.get(normalizeParameterKey("Item"));
                }
                if (value == null) {
                    value = values.get("Entity");
                }
                if (value == null) {
                    value = values.get(normalizeParameterKey("Entity"));
                }
                if (value == null) {
                    value = values.get("Player");
                }
                if (value == null) {
                    value = values.get(normalizeParameterKey("Player"));
                }
            }
            if (value != null) {
                target.setStringValue(value);
                applied = true;
            }
        }
        return applied;
    }

    private Map<String, String> adjustParameterValuesForSlot(
        Map<String, String> values,
        int slotIndex
    ) {
        return adjustParameterValuesForSlot(values, slotIndex, null);
    }

    private Map<String, String> adjustParameterValuesForSlot(
        Map<String, String> values,
        int slotIndex,
        Node parameterNode
    ) {
        if (values == null || values.isEmpty() || slotIndex < 0) {
            return values;
        }
        switch (type) {
            case HOTBAR:
                if (
                    parameterNode != null &&
                    parameterNode.getType() == NodeType.PARAM_INVENTORY_SLOT
                ) {
                    Map<String, String> adjusted = new HashMap<>(
                        filterParameterMap(
                            values,
                            HOTBAR_INVENTORY_SLOT_ITEM_KEYS
                        )
                    );
                    adjusted.put("Item", "");
                    adjusted.put(normalizeParameterKey("Item"), "");
                    return adjusted;
                }
                break;
            case CONTROL_REPEAT: {
                if (parameterNode != null) {
                    if (!values.containsKey("Count")) {
                        String fallback = values.get("Amount");
                        if (fallback == null) {
                            fallback = values.get("Duration");
                        }
                        if (fallback == null) {
                            fallback = values.get("Value");
                        }
                        if (fallback != null) {
                            Map<String, String> adjusted = new HashMap<>(
                                values
                            );
                            adjusted.put("Count", fallback);
                            adjusted.put(
                                normalizeParameterKey("Count"),
                                fallback
                            );
                            return adjusted;
                        }
                    }
                }
                break;
            }
            case MOVE_ITEM:
                if (slotIndex == 0) {
                    return filterParameterMap(values, MOVE_ITEM_TARGET_KEYS);
                } else if (slotIndex == 1) {
                    return filterParameterMap(values, MOVE_ITEM_SOURCE_KEYS);
                }
                break;
            case PLACE:
            case PLACE_HAND:
                if (slotIndex == 1 && parameterNode != null) {
                    NodeType parameterType = parameterNode.getType();
                    if (
                        parameterType == NodeType.PARAM_BLOCK ||
                        parameterType == NodeType.PARAM_PLACE_TARGET
                    ) {
                        return filterParameterMap(
                            values,
                            PLACE_POSITION_BLOCK_KEYS
                        );
                    }
                }
                break;
            default:
                break;
        }
        return values;
    }

    private Map<String, String> filterParameterMap(
        Map<String, String> values,
        Set<String> keysToRemove
    ) {
        if (
            values == null ||
            values.isEmpty() ||
            keysToRemove == null ||
            keysToRemove.isEmpty()
        ) {
            return values;
        }
        boolean needsFiltering = false;
        for (String key : keysToRemove) {
            if (values.containsKey(key)) {
                needsFiltering = true;
                break;
            }
        }
        if (!needsFiltering) {
            return values;
        }
        Map<String, String> filtered = new HashMap<>(values);
        for (String key : keysToRemove) {
            filtered.remove(key);
        }
        return filtered;
    }

    private void refreshAttachedParameterValues() {
        if (isParameterNode()) {
            return;
        }
        Map<String, String> existingValues = exportParameterValues();
        resetParametersToDefaults();
        if (!existingValues.isEmpty()) {
            applyParameterValuesFromMap(existingValues);
        }
        if (!attachments.hasAttachedParameters()) {
            return;
        }
        List<Integer> slotIndices = new ArrayList<>(
            attachments.getAttachedParameterSlotIndices()
        );
        Collections.sort(slotIndices);
        for (Integer slotIndex : slotIndices) {
            Node parameter = attachments.getAttachedParameter(slotIndex);
            if (parameter == null) {
                continue;
            }
            Map<String, String> exported = parameter.exportParameterValues();
            if (!exported.isEmpty()) {
                Map<String, String> adjusted = adjustParameterValuesForSlot(
                    exported,
                    slotIndex,
                    parameter
                );
                applyParameterValuesFromMap(adjusted);
            }
        }
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
        switch (type) {
            case GOTO:
            case TRAVEL:
            case GOAL:
            case BUILD:
            case EXPLORE:
            case FOLLOW:
            case PATH:
            case INTERACT:
                if (
                    type == NodeType.GOTO ||
                    type == NodeType.TRAVEL ||
                    type == NodeType.GOAL
                ) {
                    return EnumSet.of(
                        ParameterUsage.POSITION,
                        ParameterUsage.LOOK_ORIENTATION
                    );
                }
                return EnumSet.of(ParameterUsage.POSITION);
            case LOOK:
                return EnumSet.of(
                    ParameterUsage.LOOK_ORIENTATION,
                    ParameterUsage.POSITION
                );
            case WALK:
                if (slotIndex == 0) {
                    return EnumSet.of(ParameterUsage.LOOK_ORIENTATION);
                }
                return EnumSet.noneOf(ParameterUsage.class);
            case BREAK:
                return EnumSet.of(ParameterUsage.POSITION);
            case PLACE:
                if (slotIndex == 0 || slotIndex == 1) {
                    return EnumSet.of(ParameterUsage.POSITION);
                }
                break;
            case PLACE_HAND:
                if (slotIndex == 0 || slotIndex == 1) {
                    return EnumSet.of(ParameterUsage.POSITION);
                }
                break;
            default:
                break;
        }
        return EnumSet.noneOf(ParameterUsage.class);
    }

    private boolean parameterSupportsUsage(
        NodeType parameterType,
        ParameterUsage usage
    ) {
        if (parameterType == null || usage == null) {
            return false;
        }
        switch (usage) {
            case POSITION:
                return parameterProvidesCoordinates(parameterType);
            case LOOK_ORIENTATION:
                if (parameterProvidesCoordinates(parameterType)) {
                    return true;
                }
                EnumSet<NodeValueTrait> traits =
                    NodeTraitRegistry.getProvidedTraits(parameterType);
                return (
                    traits.contains(NodeValueTrait.DIRECTION) ||
                    traits.contains(NodeValueTrait.ROTATION) ||
                    (type == NodeType.LOOK &&
                        traits.contains(NodeValueTrait.NUMBER))
                );
            default:
                return false;
        }
    }

    public boolean canAcceptActionNode(Node node) {
        return NodeCompatibility.canAttachToSlot(
            this,
            node,
            NodeSlotType.ACTION,
            0
        );
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
            previous.setPositionSilently(
                getX() + getWidth() + ACTION_SLOT_MARGIN_HORIZONTAL,
                getY()
            );
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
     * Initialize default parameters for each node type and mode
     */
    private void resetParametersToDefaults() {
        if (isParameterNode()) {
            return;
        }
        parameters.clear();
        initializeParameters();
    }

    private void initializeParameters() {
        NodeParameterDefaults.initialize(parameters, type, mode);
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
        for (NodeParameter param : parameters) {
            if (param.getName().equals(name)) {
                return param;
            }
        }
        if (
            "Duration".equals(name) &&
            (type == NodeType.WAIT || type == NodeType.PARAM_DURATION)
        ) {
            String defaultValue = type == NodeType.PARAM_DURATION ? "" : "0.0";
            NodeParameter duration = new NodeParameter(
                "Duration",
                ParameterType.DOUBLE,
                defaultValue
            );
            parameters.add(duration);
            return duration;
        }
        return null;
    }

    public void setParameterValueAndPropagate(String name, String value) {
        if (name == null || value == null) {
            return;
        }

        NodeParameter parameter = getParameter(name);
        if (parameter != null) {
            parameter.setStringValue(value);
        }

        if (type == NodeType.PARAM_ENTITY && "Entity".equalsIgnoreCase(name)) {
            NodeParameter stateParam = getParameter("State");
            if (
                stateParam != null &&
                stateParam.getStringValue() != null &&
                !stateParam.getStringValue().isEmpty()
            ) {
                stateParam.setStringValue("");
            }
        }

        if (attachments.hasAttachedParameters()) {
            for (Node parameterNode : attachments.getAttachedParameterNodes()) {
                if (parameterNode == null || !parameterNode.isParameterNode()) {
                    continue;
                }
                NodeParameter attachedParam = parameterNode.getParameter(name);
                if (attachedParam != null) {
                    attachedParam.setStringValue(value);
                    parameterNode.recalculateDimensions();
                }
            }
        }
    }

    boolean shouldShowStateParameter() {
        if (type == NodeType.PARAM_BLOCK) {
            String blockValue = getParameterString(this, "Block");
            if (blockValue == null || blockValue.isEmpty()) {
                return false;
            }
            String stripped = BlockSelection.stripState(blockValue);
            if (stripped == null || stripped.isEmpty()) {
                return false;
            }
            String sanitized = sanitizeResourceId(stripped);
            if (sanitized == null || sanitized.isEmpty()) {
                return false;
            }
            String normalized = normalizeResourceId(sanitized, "minecraft");
            return !BlockSelection.getStateOptions(normalized).isEmpty();
        }
        if (type == NodeType.PARAM_ENTITY) {
            String entityValue = getParameterString(this, "Entity");
            if (entityValue == null || entityValue.isEmpty()) {
                return false;
            }
            String primary = entityValue;
            List<String> parts = splitMultiValueList(entityValue);
            if (!parts.isEmpty()) {
                primary = parts.get(0);
            }
            String sanitized = sanitizeResourceId(primary);
            if (sanitized == null || sanitized.isEmpty()) {
                return false;
            }
            String normalized = normalizeResourceId(sanitized, "minecraft");
            Identifier identifier = Identifier.tryParse(normalized);
            if (
                identifier == null ||
                !Registries.ENTITY_TYPE.containsId(identifier)
            ) {
                return false;
            }
            net.minecraft.client.MinecraftClient client =
                net.minecraft.client.MinecraftClient.getInstance();
            return !EntityStateOptions.getOptions(
                Registries.ENTITY_TYPE.get(identifier),
                client != null ? client.world : null
            ).isEmpty();
        }
        return false;
    }

    int getMaxParameterLabelLength() {
        return MAX_PARAMETER_LABEL_LENGTH;
    }

    public String getParameterDisplayName(NodeParameter parameter) {
        return NodeAttributeParameters.getParameterDisplayName(this, parameter);
    }

    public String getParameterDisplayValue(NodeParameter parameter) {
        return NodeAttributeParameters.getParameterDisplayValue(
            this,
            parameter
        );
    }

    public boolean isDirectionModeExact() {
        return NodeDirectionParameters.isDirectionModeExact(
            this,
            DIRECTION_MODE_EXACT,
            DIRECTION_MODE_CARDINAL,
            DEFAULT_DIRECTION_DISTANCE
        );
    }

    public boolean isDirectionModeCardinal() {
        return type == NodeType.PARAM_DIRECTION && !isDirectionModeExact();
    }

    public void setDirectionModeExact(boolean exact) {
        NodeDirectionParameters.setDirectionModeExact(
            this,
            exact,
            DIRECTION_MODE_EXACT,
            DIRECTION_MODE_CARDINAL,
            DEFAULT_DIRECTION_DISTANCE
        );
    }

    private void ensureCombinedDirectionParameters() {
        NodeDirectionParameters.ensureCombinedDirectionParameters(
            this,
            DIRECTION_MODE_EXACT,
            DIRECTION_MODE_CARDINAL,
            DEFAULT_DIRECTION_DISTANCE
        );
    }

    public boolean isBooleanModeLiteral() {
        return NodeBooleanParameters.isBooleanModeLiteral(
            this,
            BOOLEAN_MODE_LITERAL,
            BOOLEAN_MODE_VARIABLE
        );
    }

    public boolean isBooleanModeVariable() {
        return type == NodeType.PARAM_BOOLEAN && !isBooleanModeLiteral();
    }

    public void setBooleanModeLiteral(boolean literalMode) {
        NodeBooleanParameters.setBooleanModeLiteral(
            this,
            literalMode,
            BOOLEAN_MODE_LITERAL,
            BOOLEAN_MODE_VARIABLE
        );
    }

    void ensureBooleanParameters() {
        NodeBooleanParameters.ensureBooleanParameters(
            this,
            BOOLEAN_MODE_LITERAL
        );
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
        if (type == NodeType.PARAM_DIRECTION) {
            int count = 1;
            for (NodeParameter param : parameters) {
                if (param == null) {
                    continue;
                }
                String label = getParameterLabel(param);
                if (label != null && !label.isEmpty()) {
                    count++;
                }
            }
            return count;
        }
        if (type == NodeType.PARAM_BOOLEAN) {
            ensureBooleanParameters();
            int count = 1;
            for (NodeParameter param : parameters) {
                if (param == null) {
                    continue;
                }
                String label = getParameterLabel(param);
                if (label != null && !label.isEmpty()) {
                    count++;
                }
            }
            return count;
        }
        int count = 0;
        for (NodeParameter param : parameters) {
            if (param == null) {
                continue;
            }
            String label = getParameterLabel(param);
            if (label != null && !label.isEmpty()) {
                count++;
            }
        }
        if (supportsModeSelection()) {
            count++;
        }
        return count;
    }

    public Map<String, String> exportParameterValues() {
        Map<String, String> values = new HashMap<>();
        for (NodeParameter parameter : parameters) {
            String key = parameter.getName();
            String value = parameter.getStringValue();
            values.put(key, value);
            values.put(normalizeParameterKey(key), value);
        }

        NodeBehaviorDefinition behaviorDefinition =
            NodeBehaviorDefinitionRegistry.get(type);
        if (
            behaviorDefinition != null &&
            behaviorDefinition.hasParameterBehavior()
        ) {
            return behaviorDefinition.exportValues(this, values);
        }

        switch (type) {
            case LIST_LENGTH: {
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
                break;
            }
            case LIST_ITEM: {
                Node resolved = resolveListItemValueNode(
                    this,
                    null,
                    false,
                    null
                );
                if (resolved != null) {
                    return resolved.exportParameterValues();
                }
                break;
            }
            case OPERATOR_RANDOM: {
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
                break;
            }
            case OPERATOR_MOD: {
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
                break;
            }
            case SENSOR_POSITION_OF: {
                Node parameterNode = getAttachedParameter(0);
                if (parameterNode == null) {
                    break;
                }
                Optional<Vec3d> resolved = resolvePositionTarget(
                    parameterNode,
                    null,
                    null
                );
                if (resolved.isEmpty()) {
                    break;
                }
                Vec3d position = resolved.get();
                int x = MathHelper.floor(position.x);
                int y = MathHelper.floor(position.y);
                int z = MathHelper.floor(position.z);
                if (isSensorPositionSingleAxisMode()) {
                    String componentKey = getSensorPositionComponentKey();
                    String componentValue = switch (componentKey) {
                        case "X" -> Integer.toString(x);
                        case "Y" -> Integer.toString(y);
                        case "Z" -> Integer.toString(z);
                        default -> "";
                    };
                    if (!componentValue.isEmpty()) {
                        values.put("Amount", componentValue);
                        values.put(
                            normalizeParameterKey("Amount"),
                            componentValue
                        );
                        values.put("Count", componentValue);
                        values.put(
                            normalizeParameterKey("Count"),
                            componentValue
                        );
                        values.put("Threshold", componentValue);
                        values.put(
                            normalizeParameterKey("Threshold"),
                            componentValue
                        );
                        values.put("Value", componentValue);
                        values.put(
                            normalizeParameterKey("Value"),
                            componentValue
                        );
                    }
                } else {
                    String xValue = Integer.toString(x);
                    String yValue = Integer.toString(y);
                    String zValue = Integer.toString(z);
                    values.put("X", xValue);
                    values.put(normalizeParameterKey("X"), xValue);
                    values.put("Y", yValue);
                    values.put(normalizeParameterKey("Y"), yValue);
                    values.put("Z", zValue);
                    values.put(normalizeParameterKey("Z"), zValue);
                }
                break;
            }
            case SENSOR_DISTANCE_BETWEEN: {
                Node parameterNodeA = getAttachedParameter(0);
                Node parameterNodeB = getAttachedParameter(1);
                if (parameterNodeA == null || parameterNodeB == null) {
                    break;
                }
                if (
                    !providesTrait(parameterNodeA, NodeValueTrait.ENTITY) &&
                    !providesTrait(parameterNodeA, NodeValueTrait.COORDINATE) &&
                    !providesTrait(parameterNodeA, NodeValueTrait.BLOCK) &&
                    !providesTrait(parameterNodeA, NodeValueTrait.ITEM) &&
                    !providesTrait(parameterNodeA, NodeValueTrait.PLAYER)
                ) {
                    break;
                }
                if (
                    !providesTrait(parameterNodeB, NodeValueTrait.ENTITY) &&
                    !providesTrait(parameterNodeB, NodeValueTrait.COORDINATE) &&
                    !providesTrait(parameterNodeB, NodeValueTrait.BLOCK) &&
                    !providesTrait(parameterNodeB, NodeValueTrait.ITEM) &&
                    !providesTrait(parameterNodeB, NodeValueTrait.PLAYER)
                ) {
                    break;
                }
                Optional<Vec3d> resolvedA = resolveDistanceBetweenTarget(
                    parameterNodeA
                );
                Optional<Vec3d> resolvedB = resolveDistanceBetweenTarget(
                    parameterNodeB
                );
                if (resolvedA.isEmpty() || resolvedB.isEmpty()) {
                    break;
                }
                double distance = Math.sqrt(
                    resolvedA.get().squaredDistanceTo(resolvedB.get())
                );
                String distanceValue = Double.toString(distance);
                values.put("Distance", distanceValue);
                values.put(normalizeParameterKey("Distance"), distanceValue);
                break;
            }
            case SENSOR_TARGETED_BLOCK: {
                Optional<BlockState> targetState = getTargetedBlockState();
                if (targetState.isEmpty()) {
                    break;
                }
                BlockState state = targetState.get();
                Identifier id = Registries.BLOCK.getId(state.getBlock());
                if (id == null) {
                    break;
                }
                String blockId = "minecraft".equals(id.getNamespace())
                    ? id.getPath()
                    : id.toString();
                String stateValue = BlockSelection.describeState(state);
                values.put("Block", blockId);
                values.put(normalizeParameterKey("Block"), blockId);
                if (stateValue == null) {
                    stateValue = "";
                }
                values.put("State", stateValue);
                values.put(normalizeParameterKey("State"), stateValue);
                break;
            }
            case SENSOR_TARGETED_ENTITY: {
                Optional<Entity> targetedEntity = getTargetedEntity();
                if (targetedEntity.isEmpty()) {
                    break;
                }
                Entity entity = targetedEntity.get();
                Identifier id = Registries.ENTITY_TYPE.getId(entity.getType());
                if (id == null) {
                    break;
                }
                String entityId = "minecraft".equals(id.getNamespace())
                    ? id.getPath()
                    : id.toString();
                values.put("Entity", entityId);
                values.put(normalizeParameterKey("Entity"), entityId);
                String stateValue = EntityStateOptions.describe(entity);
                if (stateValue == null) {
                    stateValue = "";
                }
                values.put("State", stateValue);
                values.put(normalizeParameterKey("State"), stateValue);
                break;
            }
            case SENSOR_LOOK_DIRECTION: {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null && client.player != null) {
                    float yaw = client.player.getYaw();
                    float pitch = client.player.getPitch();
                    String yawValue = formatFloat(yaw);
                    String pitchValue = formatFloat(pitch);
                    if (isSensorLookSingleAxisMode()) {
                        String componentKey = getSensorLookComponentKey();
                        String componentValue = "Yaw".equals(componentKey)
                            ? yawValue
                            : "Pitch".equals(componentKey)
                                ? pitchValue
                                : "";
                        if (!componentValue.isEmpty()) {
                            values.put("Amount", componentValue);
                            values.put(
                                normalizeParameterKey("Amount"),
                                componentValue
                            );
                            values.put("Count", componentValue);
                            values.put(
                                normalizeParameterKey("Count"),
                                componentValue
                            );
                            values.put("Threshold", componentValue);
                            values.put(
                                normalizeParameterKey("Threshold"),
                                componentValue
                            );
                            values.put("Value", componentValue);
                            values.put(
                                normalizeParameterKey("Value"),
                                componentValue
                            );
                        }
                    } else {
                        values.put("Yaw", yawValue);
                        values.put(normalizeParameterKey("Yaw"), yawValue);
                        values.put("Pitch", pitchValue);
                        values.put(normalizeParameterKey("Pitch"), pitchValue);
                    }
                }
                break;
            }
            case SENSOR_CURRENT_HAND: {
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
                break;
            }
            case SENSOR_IS_ON_GROUND: {
                Optional<Double> distanceFromGround = getDistanceFromGround();
                if (distanceFromGround.isEmpty()) {
                    break;
                }
                String distanceValue = Double.toString(
                    distanceFromGround.get()
                );
                values.put("Distance", distanceValue);
                values.put(normalizeParameterKey("Distance"), distanceValue);
                values.put("Value", distanceValue);
                values.put(normalizeParameterKey("Value"), distanceValue);
                break;
            }
            case SENSOR_TARGETED_BLOCK_FACE: {
                Optional<Direction> targetFace = getTargetedBlockFace();
                if (targetFace.isEmpty()) {
                    break;
                }
                String faceValue = targetFace
                    .get()
                    .toString()
                    .toLowerCase(Locale.ROOT);
                values.put("Side", faceValue);
                values.put(normalizeParameterKey("Side"), faceValue);
                values.put("Face", faceValue);
                values.put(normalizeParameterKey("Face"), faceValue);
                values.put("Text", faceValue);
                values.put(normalizeParameterKey("Text"), faceValue);
                values.put("Message", faceValue);
                values.put(normalizeParameterKey("Message"), faceValue);
                break;
            }
            case SENSOR_SLOT_ITEM_COUNT: {
                Node slotNode = resolveSensorParameterNode(
                    getAttachedParameter(0),
                    0
                );
                int count = 0;
                if (
                    slotNode != null &&
                    providesTrait(slotNode, NodeValueTrait.INVENTORY_SLOT)
                ) {
                    count = Math.max(
                        0,
                        resolveInventorySlotCount(slotNode).orElse(0)
                    );
                }
                String countValue = Integer.toString(count);
                values.put("Amount", countValue);
                values.put(normalizeParameterKey("Amount"), countValue);
                values.put("Count", countValue);
                values.put(normalizeParameterKey("Count"), countValue);
                values.put("Value", countValue);
                values.put(normalizeParameterKey("Value"), countValue);
                break;
            }
            default:
                break;
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
        switch (type) {
            case SENSOR_IS_SWIMMING:
            case SENSOR_IS_IN_LAVA:
            case SENSOR_IS_UNDERWATER:
            case SENSOR_IS_FALLING:
            case SENSOR_IS_DAYTIME:
            case SENSOR_IS_RAINING:
            case SENSOR_GUI_FILLED:
                return true;
            default:
                return false;
        }
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
        return getX() + BOOLEAN_TOGGLE_MARGIN_HORIZONTAL;
    }

    public int getBooleanToggleTop() {
        return getY() + HEADER_HEIGHT + BOOLEAN_TOGGLE_TOP_MARGIN;
    }

    public int getBooleanToggleWidth() {
        return Math.max(48, getWidth() - 2 * BOOLEAN_TOGGLE_MARGIN_HORIZONTAL);
    }

    public int getBooleanToggleHeight() {
        return BOOLEAN_TOGGLE_HEIGHT;
    }

    public int getBooleanToggleAreaHeight() {
        return (
            BOOLEAN_TOGGLE_TOP_MARGIN +
            BOOLEAN_TOGGLE_HEIGHT +
            BOOLEAN_TOGGLE_BOTTOM_MARGIN
        );
    }

    public boolean supportsModeSelection() {
        if (type == NodeType.SENSOR_LOOK_DIRECTION) {
            return false;
        }
        NodeMode[] modes = NodeMode.getModesForNodeType(type);
        return modes != null && modes.length > 0;
    }

    public boolean hasMessageInputFields() {
        return type == NodeType.MESSAGE;
    }

    public String getStickyNoteText() {
        return isStickyNote()
            ? (stickyNoteText == null ? "" : stickyNoteText)
            : "";
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
            Math.max(STICKY_NOTE_MIN_HEIGHT, height)
        );
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
        return Math.max(
            1,
            getHeight() -
                STICKY_NOTE_HEADER_HEIGHT -
                STICKY_NOTE_TEXT_MARGIN * 2
        );
    }

    public int getStickyNoteResizeHandleSize() {
        return STICKY_NOTE_HANDLE_SIZE;
    }

    public String getTemplateName() {
        if (!usesTemplateBacking()) {
            return "";
        }
        return (templateName == null || templateName.isEmpty())
            ? "Template"
            : templateName;
    }

    public void setTemplateName(String templateName) {
        if (!usesTemplateBacking()) {
            return;
        }
        this.templateName = (templateName == null || templateName.isBlank())
            ? "Template"
            : templateName.trim();
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

    public boolean isCustomNodeInstance() {
        return (
            type == NodeType.CUSTOM_NODE ||
            (type == NodeType.TEMPLATE && customNodeInstance)
        );
    }

    public void setCustomNodeInstance(boolean customNodeInstance) {
        if (!usesTemplateBacking()) {
            return;
        }
        this.customNodeInstance = customNodeInstance;
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
        if (!hasMessageInputFields() || index < 0) {
            return;
        }
        while (index >= messageLines.size()) {
            messageLines.add("Hello World");
        }
        messageLines.set(index, value == null ? "" : value);
    }

    public void setMessageLines(List<String> lines) {
        messageLines.clear();
        if (lines != null) {
            for (String line : lines) {
                messageLines.add(line == null ? "" : line);
            }
        }
        if (messageLines.isEmpty()) {
            messageLines.add("Hello World");
        }
        layoutState.clearMessageFieldContentWidthOverride();
        recalculateDimensions();
    }

    public void addMessageLine(String value) {
        if (!hasMessageInputFields()) {
            return;
        }
        messageLines.add(value == null ? "" : value);
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
        if (messageLines.isEmpty()) {
            messageLines.add("Hello World");
        }
        layoutState.clearMessageFieldContentWidthOverride();
        recalculateDimensions();
        return true;
    }

    public boolean isMessageClientSide() {
        return type == NodeType.MESSAGE && messageClientSide;
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

    public int getMessageFieldDisplayHeight() {
        if (!hasMessageInputFields()) {
            return 0;
        }
        int count = getMessageFieldCount();
        int blockHeight =
            MESSAGE_FIELD_LABEL_HEIGHT +
            MESSAGE_FIELD_HEIGHT +
            MESSAGE_FIELD_VERTICAL_GAP;
        return (
            MESSAGE_FIELD_TOP_MARGIN +
            (count * blockHeight) -
            MESSAGE_FIELD_VERTICAL_GAP +
            MESSAGE_FIELD_BOTTOM_MARGIN +
            getMessageScopeToggleDisplayHeight()
        );
    }

    public int getMessageFieldLabelTop(int index) {
        return (
            getY() +
            HEADER_HEIGHT +
            MESSAGE_FIELD_TOP_MARGIN +
            index *
                (MESSAGE_FIELD_LABEL_HEIGHT +
                    MESSAGE_FIELD_HEIGHT +
                    MESSAGE_FIELD_VERTICAL_GAP)
        );
    }

    public int getMessageFieldInputTop(int index) {
        return getMessageFieldLabelTop(index) + MESSAGE_FIELD_LABEL_HEIGHT;
    }

    public int getMessageFieldLabelHeight() {
        return MESSAGE_FIELD_LABEL_HEIGHT;
    }

    public int getMessageFieldHeight() {
        return MESSAGE_FIELD_HEIGHT;
    }

    public int getMessageFieldWidth() {
        return Math.max(
            MESSAGE_FIELD_MIN_CONTENT_WIDTH,
            getWidth() - 2 * MESSAGE_FIELD_MARGIN_HORIZONTAL
        );
    }

    public void setMessageFieldTextWidth(int textWidth) {
        if (!hasMessageInputFields()) {
            return;
        }
        int paddedWidth = Math.max(
            MESSAGE_FIELD_MIN_CONTENT_WIDTH,
            textWidth + (MESSAGE_FIELD_TEXT_PADDING * 2)
        );
        layoutState.setMessageFieldContentWidthOverride(paddedWidth);
    }

    public void setParameterFieldWidthOverride(int fieldWidth) {
        if (!isParameterNode()) {
            return;
        }
        layoutState.setParameterFieldWidthOverride(Math.max(0, fieldWidth));
    }

    public void setCoordinateFieldTextWidth(int textWidth) {
        if (!hasCoordinateInputFields()) {
            return;
        }
        int paddedWidth = Math.max(
            COORDINATE_FIELD_WIDTH,
            textWidth + (COORDINATE_FIELD_TEXT_PADDING * 2)
        );
        layoutState.setCoordinateFieldWidthOverride(paddedWidth);
    }

    public void setAmountFieldTextWidth(int textWidth) {
        if (!hasAmountInputField()) {
            return;
        }
        int paddedWidth = Math.max(
            PARAMETER_SLOT_MIN_CONTENT_WIDTH,
            textWidth + (AMOUNT_FIELD_TEXT_PADDING * 2)
        );
        layoutState.setAmountFieldWidthOverride(paddedWidth);
    }

    public void setStopTargetFieldTextWidth(int textWidth) {
        if (!hasStopTargetInputField()) {
            return;
        }
        int paddedWidth = Math.max(
            STOP_TARGET_FIELD_MIN_WIDTH,
            textWidth + (STOP_TARGET_FIELD_TEXT_PADDING * 2)
        );
        layoutState.setStopTargetFieldWidthOverride(paddedWidth);
    }

    public void setVariableFieldTextWidth(int textWidth) {
        if (!hasVariableInputField()) {
            return;
        }
        int paddedWidth = Math.max(
            VARIABLE_FIELD_MIN_WIDTH,
            textWidth + (VARIABLE_FIELD_TEXT_PADDING * 2)
        );
        layoutState.setVariableFieldWidthOverride(paddedWidth);
    }

    public int getMessageFieldLeft() {
        return getX() + MESSAGE_FIELD_MARGIN_HORIZONTAL;
    }

    public int getMessageAddButtonLeft() {
        return (
            getX() + getWidth() - MESSAGE_BUTTON_PADDING - MESSAGE_BUTTON_SIZE
        );
    }

    public int getMessageRemoveButtonLeft() {
        return (
            getMessageAddButtonLeft() -
            MESSAGE_BUTTON_SPACING -
            MESSAGE_BUTTON_SIZE
        );
    }

    public int getMessageButtonTop() {
        return getY() + 3;
    }

    public int getMessageButtonSize() {
        return MESSAGE_BUTTON_SIZE;
    }

    public int getMessageButtonsWidth() {
        return (
            (MESSAGE_BUTTON_SIZE * 2) +
            MESSAGE_BUTTON_SPACING +
            (MESSAGE_BUTTON_PADDING * 2)
        );
    }

    public int getMessageScopeToggleDisplayHeight() {
        if (!hasMessageInputFields()) {
            return 0;
        }
        return (
            MESSAGE_SCOPE_TOP_MARGIN +
            MESSAGE_SCOPE_LABEL_HEIGHT +
            MESSAGE_SCOPE_TOGGLE_HEIGHT +
            MESSAGE_SCOPE_BOTTOM_MARGIN
        );
    }

    public int getMessageScopeLabelTop() {
        return (
            getMessageFieldInputTop(getMessageFieldCount() - 1) +
            MESSAGE_FIELD_HEIGHT +
            MESSAGE_FIELD_BOTTOM_MARGIN +
            MESSAGE_SCOPE_TOP_MARGIN
        );
    }

    public int getMessageScopeToggleTop() {
        return getMessageScopeLabelTop() + MESSAGE_SCOPE_LABEL_HEIGHT;
    }

    public int getMessageScopeLabelHeight() {
        return MESSAGE_SCOPE_LABEL_HEIGHT;
    }

    public int getMessageScopeToggleLeft() {
        return getX() + MESSAGE_SCOPE_MARGIN_HORIZONTAL;
    }

    public int getMessageScopeToggleWidth() {
        return Math.max(
            MESSAGE_FIELD_MIN_CONTENT_WIDTH,
            getWidth() - 2 * MESSAGE_SCOPE_MARGIN_HORIZONTAL
        );
    }

    public int getMessageScopeToggleHeight() {
        return MESSAGE_SCOPE_TOGGLE_HEIGHT;
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
        return type == NodeType.WRITE_SIGN
            ? SIGN_MAX_CHARS
            : BOOK_PAGE_MAX_CHARS;
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
        return type == NodeType.WRITE_SIGN
            ? "Edit Sign Text"
            : "Edit Book Text";
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
            String first = (pages == null || pages.isEmpty())
                ? ""
                : pages.get(0);
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
        bookText = bookPages.get(0);
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
        if (!hasBookTextInput()) {
            return 0;
        }
        if (hasBookTextPageInput()) {
            return (
                BOOK_TEXT_TOP_MARGIN +
                BOOK_TEXT_BUTTON_HEIGHT +
                BOOK_TEXT_FIELD_SPACING +
                BOOK_TEXT_LABEL_HEIGHT +
                BOOK_TEXT_PAGE_FIELD_HEIGHT +
                BOOK_TEXT_BOTTOM_MARGIN
            );
        }
        return (
            BOOK_TEXT_TOP_MARGIN +
            BOOK_TEXT_BUTTON_HEIGHT +
            BOOK_TEXT_BOTTOM_MARGIN
        );
    }

    public int getBookTextButtonTop() {
        return getY() + HEADER_HEIGHT + BOOK_TEXT_TOP_MARGIN;
    }

    public int getBookTextButtonLeft() {
        return getX() + BOOK_TEXT_BUTTON_MARGIN_HORIZONTAL;
    }

    public int getBookTextButtonWidth() {
        return Math.max(
            BOOK_TEXT_BUTTON_MIN_WIDTH,
            getWidth() - 2 * BOOK_TEXT_BUTTON_MARGIN_HORIZONTAL
        );
    }

    public int getBookTextButtonHeight() {
        return BOOK_TEXT_BUTTON_HEIGHT;
    }

    public int getBookTextPageLabelTop() {
        return (
            getBookTextButtonTop() +
            BOOK_TEXT_BUTTON_HEIGHT +
            BOOK_TEXT_FIELD_SPACING
        );
    }

    public int getBookTextPageFieldTop() {
        return getBookTextPageLabelTop() + BOOK_TEXT_LABEL_HEIGHT;
    }

    public int getBookTextPageFieldLeft() {
        return getX() + BOOK_TEXT_BUTTON_MARGIN_HORIZONTAL;
    }

    public int getBookTextPageFieldWidth() {
        return getWidth() - 2 * BOOK_TEXT_BUTTON_MARGIN_HORIZONTAL;
    }

    public int getBookTextPageFieldHeight() {
        return BOOK_TEXT_PAGE_FIELD_HEIGHT;
    }

    public boolean hasPopupEditButton() {
        if (
            !isParameterNode() ||
            type == NodeType.PARAM_SCHEMATIC ||
            type == NodeType.PARAM_BOOLEAN
        ) {
            return false;
        }
        return (
            type == NodeType.PARAM_INVENTORY_SLOT ||
            type == NodeType.PARAM_KEY ||
            type == NodeType.PARAM_VILLAGER_TRADE
        );
    }

    public int getPopupEditButtonLeft() {
        return getX() + POPUP_EDIT_BUTTON_MARGIN_HORIZONTAL;
    }

    public int getPopupEditButtonTop() {
        if (
            isParameterNode() &&
            type != NodeType.SENSOR_POSITION_OF &&
            type != NodeType.SENSOR_DISTANCE_BETWEEN
        ) {
            return (
                getY() +
                HEADER_HEIGHT +
                getParameterDisplayHeight() +
                POPUP_EDIT_BUTTON_TOP_MARGIN
            );
        }
        return getY() + HEADER_HEIGHT;
    }

    public int getPopupEditButtonWidth() {
        return Math.max(
            POPUP_EDIT_BUTTON_MIN_WIDTH,
            getWidth() - 2 * POPUP_EDIT_BUTTON_MARGIN_HORIZONTAL
        );
    }

    public int getPopupEditButtonHeight() {
        return POPUP_EDIT_BUTTON_HEIGHT;
    }

    public int getPopupEditButtonDisplayHeight() {
        if (!hasPopupEditButton()) {
            return 0;
        }
        return (
            POPUP_EDIT_BUTTON_TOP_MARGIN +
            POPUP_EDIT_BUTTON_HEIGHT +
            POPUP_EDIT_BUTTON_BOTTOM_MARGIN
        );
    }

    public int getEventNameFieldLeft() {
        return getX() + EVENT_NAME_FIELD_MARGIN_HORIZONTAL;
    }

    public int getEventNameFieldTop() {
        return getY() + HEADER_HEIGHT + EVENT_NAME_FIELD_TOP_MARGIN;
    }

    public int getEventNameFieldWidth() {
        return getWidth() - 2 * EVENT_NAME_FIELD_MARGIN_HORIZONTAL;
    }

    public int getEventNameFieldHeight() {
        return EVENT_NAME_FIELD_HEIGHT;
    }

    /**
     * Recalculate node dimensions based on current content
     */
    public void recalculateDimensions() {
        boolean shouldUpdateAttachments = NodeDimensionCalculator.recalculate(
            this,
            layoutState
        );
        if (!shouldUpdateAttachments) {
            return;
        }

        if (attachments.getAttachedSensor() != null) {
            updateAttachedSensorPosition();
        }
        if (attachments.getAttachedActionNode() != null) {
            updateAttachedActionPosition();
        }
        updateAttachedParameterPositions();
        notifyParentParameterHostOfResize();
        notifyParentActionControlOfResize();
        notifyParentControlOfResize();
    }

    boolean showsSensorSlotHeader() {
        return (
            type == NodeType.CONTROL_IF ||
            type == NodeType.CONTROL_IF_ELSE ||
            type == NodeType.CONTROL_REPEAT_UNTIL ||
            type == NodeType.CONTROL_WAIT_UNTIL
        );
    }

    boolean showsActionSlotHeader() {
        return (
            type == NodeType.CONTROL_REPEAT ||
            type == NodeType.CONTROL_REPEAT_UNTIL ||
            type == NodeType.CONTROL_FOREVER
        );
    }

    /**
     * Get the height needed to display parameters
     */
    public int getParameterDisplayHeight() {
        if (!hasParameters() && !supportsModeSelection()) {
            return 0;
        }
        int parameterLineCount = getVisibleParameterLineCount();
        if (parameterLineCount <= 0) {
            return 0;
        }
        return (
            PARAM_PADDING_TOP +
            (parameterLineCount * PARAM_LINE_HEIGHT) +
            PARAM_PADDING_BOTTOM
        );
    }

    String getParameterWidthLabel(NodeParameter parameter) {
        if (parameter == null) {
            return "";
        }
        if (type != NodeType.PARAM_DIRECTION) {
            return getParameterLabel(parameter);
        }
        String parameterName = parameter.getName();
        if (
            "Mode".equalsIgnoreCase(parameterName) ||
            "Direction".equalsIgnoreCase(parameterName)
        ) {
            return "";
        }
        if (
            "Yaw".equalsIgnoreCase(parameterName) ||
            "Pitch".equalsIgnoreCase(parameterName) ||
            "YawOffset".equalsIgnoreCase(parameterName) ||
            "PitchOffset".equalsIgnoreCase(parameterName) ||
            "Distance".equalsIgnoreCase(parameterName)
        ) {
            return (
                getParameterDisplayName(parameter) +
                ": " +
                parameter.getDisplayValue()
            );
        }
        return getParameterLabel(parameter);
    }

    String getParameterWidthDisplayValue(NodeParameter parameter) {
        if (parameter == null) {
            return "";
        }
        if (type != NodeType.PARAM_DIRECTION) {
            return getParameterDisplayValue(parameter);
        }
        String parameterName = parameter.getName();
        if (
            "Yaw".equalsIgnoreCase(parameterName) ||
            "Pitch".equalsIgnoreCase(parameterName) ||
            "YawOffset".equalsIgnoreCase(parameterName) ||
            "PitchOffset".equalsIgnoreCase(parameterName) ||
            "Distance".equalsIgnoreCase(parameterName)
        ) {
            return getParameterDisplayValue(parameter);
        }
        return getParameterDisplayValue(parameter);
    }

    public String getModeDisplayLabel() {
        if (!supportsModeSelection()) {
            return "";
        }
        NodeMode nodeMode = getMode();
        String modeName =
            nodeMode != null ? nodeMode.getDisplayName() : "Select Mode";
        return "Mode: " + modeName;
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

        // Execute on the main Minecraft thread
        net.minecraft.client.MinecraftClient client =
            net.minecraft.client.MinecraftClient.getInstance();

        if (hasParameterSlot()) {
            int requiredSlotCount = getParameterSlotCount();
            for (int i = 0; i < requiredSlotCount; i++) {
                if (
                    isParameterSlotRequired(i) &&
                    getAttachedParameter(i) == null
                ) {
                    String label = getParameterSlotLabel(i);
                    NodeExecutionCompletion.fail(
                        this,
                        client,
                        future,
                        type.getDisplayName() +
                            " requires a " +
                            label.toLowerCase(Locale.ROOT) +
                            " parameter before it can run."
                    );
                    return future;
                }
            }
        }

        if (!reportEmptyParametersForNode(this, future)) {
            return future;
        }

        if (client != null) {
            client.execute(() -> {
                try {
                    ExecutionManager.getInstance().runWithExecutionContext(
                        executionId,
                        () -> executeNodeCommand(future)
                    );
                } catch (Exception e) {
                    LOGGER.warn(
                        "Error executing node {}: {}",
                        type,
                        e.getMessage(),
                        e
                    );
                    NodeExecutionCompletion.completeExceptionally(future, e);
                }
            });
        } else {
            NodeExecutionCompletion.completeExceptionally(
                future,
                new RuntimeException("Minecraft client not available")
            );
        }

        return future;
    }

    ParameterHandlingResult preprocessAttachedParameter(
        EnumSet<ParameterUsage> usages,
        CompletableFuture<Void> future
    ) {
        if (attachments.hasAttachedParameters()) {
            java.util.List<Integer> slotIndices = new java.util.ArrayList<>(
                attachments.getAttachedParameterSlotIndices()
            );
            java.util.Collections.sort(slotIndices);
            ParameterHandlingResult result = ParameterHandlingResult.CONTINUE;
            boolean resetRuntime = true;
            for (int slotIndex : slotIndices) {
                ParameterHandlingResult slotResult = preprocessParameterSlot(
                    slotIndex,
                    usages,
                    future,
                    resetRuntime
                );
                resetRuntime = false;
                if (slotResult == ParameterHandlingResult.COMPLETE) {
                    result = ParameterHandlingResult.COMPLETE;
                    break;
                }
            }
            return result;
        }

        int slotCount = getParameterSlotCount();
        ParameterHandlingResult result = ParameterHandlingResult.CONTINUE;
        boolean resetRuntime = true;
        for (int i = 0; i < slotCount; i++) {
            ParameterHandlingResult slotResult = preprocessParameterSlot(
                i,
                usages,
                future,
                resetRuntime
            );
            resetRuntime = false;
            if (slotResult == ParameterHandlingResult.COMPLETE) {
                result = ParameterHandlingResult.COMPLETE;
                break;
            }
        }
        return result;
    }

    ParameterHandlingResult preprocessParameterSlot(
        int slotIndex,
        EnumSet<ParameterUsage> usages,
        CompletableFuture<Void> future,
        boolean resetRuntimeData
    ) {
        if (!canAcceptParameterAt(slotIndex)) {
            return ParameterHandlingResult.CONTINUE;
        }
        if (resetRuntimeData) {
            runtimeState.runtimeParameterData = null;
        }
        Node parameterNode = getAttachedParameter(slotIndex);
        return preprocessParameterNode(
            parameterNode,
            slotIndex,
            usages,
            future
        );
    }

    private ParameterHandlingResult preprocessParameterNode(
        Node parameterNode,
        int slotIndex,
        EnumSet<ParameterUsage> usages,
        CompletableFuture<Void> future
    ) {
        if (parameterNode == null) {
            return ParameterHandlingResult.CONTINUE;
        }
        if (parameterNode.hasParameterSlot()) {
            int requiredSlotCount = parameterNode.getParameterSlotCount();
            for (int i = 0; i < requiredSlotCount; i++) {
                if (
                    parameterNode.isParameterSlotRequired(i) &&
                    parameterNode.getAttachedParameter(i) == null
                ) {
                    if (future != null && !future.isDone()) {
                        String label = parameterNode.getParameterSlotLabel(i);
                        NodeExecutionCompletion.failWithCurrentClient(
                            this,
                            future,
                            parameterNode.getType().getDisplayName() +
                                " requires a " +
                                label.toLowerCase(Locale.ROOT) +
                                " parameter before it can run."
                        );
                    }
                    return ParameterHandlingResult.COMPLETE;
                }
            }
        }
        if (runtimeState.runtimeParameterData == null) {
            runtimeState.runtimeParameterData = new RuntimeParameterData();
        }

        boolean handled = false;
        if (parameterNode.getType() == NodeType.VARIABLE) {
            parameterNode = resolveVariableValueNode(
                parameterNode,
                slotIndex,
                future
            );
            if (parameterNode == null) {
                return ParameterHandlingResult.COMPLETE;
            }
        }

        if (!reportEmptyParametersForNode(parameterNode, future)) {
            return ParameterHandlingResult.COMPLETE;
        }

        Map<String, String> exported = parameterNode.exportParameterValues();
        Map<String, String> adjustedValues = adjustParameterValuesForSlot(
            exported,
            slotIndex,
            parameterNode
        );
        if (!exported.isEmpty()) {
            handled = applyParameterValuesFromMap(adjustedValues);
        }
        if (type == NodeType.WAIT) {
            String durationValue = adjustedValues.get("Duration");
            if (durationValue == null) {
                durationValue = adjustedValues.get(
                    normalizeParameterKey("Duration")
                );
            }
            if (durationValue == null) {
                durationValue = adjustedValues.get("DurationSeconds");
            }
            if (durationValue == null) {
                durationValue = adjustedValues.get(
                    normalizeParameterKey("DurationSeconds")
                );
            }
            if (durationValue == null) {
                durationValue = adjustedValues.get("WaitSeconds");
            }
            if (durationValue == null) {
                durationValue = adjustedValues.get(
                    normalizeParameterKey("WaitSeconds")
                );
            }
            if (durationValue == null) {
                durationValue = adjustedValues.get("IntervalSeconds");
            }
            if (durationValue == null) {
                durationValue = adjustedValues.get(
                    normalizeParameterKey("IntervalSeconds")
                );
            }
            if (durationValue != null && !durationValue.trim().isEmpty()) {
                String trimmedDuration = durationValue.trim();
                Double parsedDurationSeconds = parseDoubleOrNull(
                    trimmedDuration
                );
                if (
                    runtimeState.runtimeParameterData != null &&
                    parsedDurationSeconds != null
                ) {
                    runtimeState.runtimeParameterData.durationSeconds =
                        Math.max(0.0, parsedDurationSeconds);
                }
                if (!handled) {
                    setParameterValueAndPropagate("Duration", trimmedDuration);
                    handled = true;
                }
            } else if (
                providesTrait(parameterNode, NodeValueTrait.DURATION) ||
                providesTrait(parameterNode, NodeValueTrait.NUMBER)
            ) {
                handled = true;
            }
        }

        if (parameterNode.getType() == NodeType.LIST_ITEM) {
            Entity resolved = resolveListItemEntity(
                parameterNode,
                runtimeState.runtimeParameterData,
                future
            );
            if (resolved != null) {
                handled = true;
            } else if (future != null && future.isDone()) {
                return ParameterHandlingResult.COMPLETE;
            }
        }

        if (usages.contains(ParameterUsage.POSITION)) {
            Optional<Vec3d> targetVec = resolvePositionTarget(
                parameterNode,
                runtimeState.runtimeParameterData,
                future
            );
            if (targetVec.isPresent()) {
                handled = true;
                runtimeState.runtimeParameterData.targetVector =
                    targetVec.get();
                applyVectorToCoordinateParameters(targetVec.get());
            } else if (future != null && future.isDone()) {
                return ParameterHandlingResult.COMPLETE;
            }
        }

        if (usages.contains(ParameterUsage.LOOK_ORIENTATION)) {
            boolean oriented = resolveLookOrientation(
                parameterNode,
                runtimeState.runtimeParameterData,
                future
            );
            if (oriented) {
                handled = true;
            } else if (future != null && future.isDone()) {
                return ParameterHandlingResult.COMPLETE;
            }
        }

        if (
            !handled &&
            type == NodeType.MOVE_ITEM &&
            providesTrait(parameterNode, NodeValueTrait.ITEM)
        ) {
            if (
                resolveMoveItemSlotFromItemParameter(
                    parameterNode,
                    slotIndex,
                    future
                )
            ) {
                handled = true;
            } else {
                return ParameterHandlingResult.COMPLETE;
            }
        }
        if (
            !handled &&
            type == NodeType.MOVE_ITEM &&
            providesTrait(parameterNode, NodeValueTrait.GUI)
        ) {
            handled = true;
        }
        if (
            !handled &&
            isDropNodeType() &&
            (providesTrait(parameterNode, NodeValueTrait.ITEM) ||
                providesTrait(parameterNode, NodeValueTrait.INVENTORY_SLOT))
        ) {
            if (resolveDropParameterSelection(parameterNode, future)) {
                handled = true;
            } else {
                return ParameterHandlingResult.COMPLETE;
            }
        }
        if (!handled && type == NodeType.USE) {
            if (resolveUseParameterSelection(parameterNode, future)) {
                handled = true;
            } else {
                return ParameterHandlingResult.COMPLETE;
            }
        }
        // Special case: block parameters in slot 0 of PLACE/PLACE_HAND nodes are valid
        // even when usages is empty (they provide block type, not position)
        if (
            !handled &&
            usages.isEmpty() &&
            (type == NodeType.PLACE || type == NodeType.PLACE_HAND)
        ) {
            NodeType parameterType = parameterNode.getType();
            if (
                parameterType == NodeType.PARAM_BLOCK &&
                parameterNode.attachments.getParentParameterSlotIndex() == 0
            ) {
                handled = true;
            }
            if (
                parameterType == NodeType.PARAM_INVENTORY_SLOT &&
                parameterNode.attachments.getParentParameterSlotIndex() == 0
            ) {
                handled = true;
            }
        }
        if (!handled && type == NodeType.PRESS_KEY) {
            if (providesTrait(parameterNode, NodeValueTrait.KEY)) {
                String buttonValue = getParameterString(parameterNode, "Key");
                if (buttonValue != null && !buttonValue.isBlank()) {
                    runtimeState.runtimeParameterData.resolvedButtonValue =
                        buttonValue;
                    runtimeState.runtimeParameterData.resolvedButtonIsMouse =
                        false;
                }
                handled = true;
            } else if (
                providesTrait(parameterNode, NodeValueTrait.MOUSE_BUTTON)
            ) {
                String buttonValue = getParameterString(
                    parameterNode,
                    "MouseButton"
                );
                if (buttonValue != null && !buttonValue.isBlank()) {
                    runtimeState.runtimeParameterData.resolvedButtonValue =
                        buttonValue;
                    runtimeState.runtimeParameterData.resolvedButtonIsMouse =
                        true;
                }
                handled = true;
            }
        }
        if (
            !handled &&
            type == NodeType.BREAK &&
            providesTrait(parameterNode, NodeValueTrait.BLOCK)
        ) {
            handled = true;
        }

        if (!handled && (type == NodeType.GOTO || type == NodeType.TRAVEL)) {
            NodeType parameterType = parameterNode.getType();
            if (
                parameterType == NodeType.PARAM_ENTITY ||
                parameterType == NodeType.PARAM_PLAYER ||
                parameterType == NodeType.PARAM_ITEM ||
                parameterType == NodeType.PARAM_BLOCK
            ) {
                return ParameterHandlingResult.CONTINUE;
            }
        }

        if (!handled) {
            if (future != null && !future.isDone()) {
                sendIncompatibleParameterMessage(parameterNode);
                future.complete(null);
            }
            return ParameterHandlingResult.COMPLETE;
        }

        return ParameterHandlingResult.CONTINUE;
    }

    Node resolveVariableValueNode(
        Node variableNode,
        int slotIndex,
        CompletableFuture<Void> future
    ) {
        if (variableNode == null) {
            return null;
        }
        String variableName = getParameterString(variableNode, "Variable");
        if (variableName == null || variableName.trim().isEmpty()) {
            sendVariableError("Variable name cannot be empty.", future);
            return null;
        }

        ExecutionManager manager = ExecutionManager.getInstance();
        Node startNode = resolveExecutionStartNode();
        ExecutionManager.RuntimeVariable runtimeVariable =
            manager.getRuntimeVariable(startNode, variableName.trim());
        if (runtimeVariable == null) {
            runtimeVariable = manager.getRuntimeVariableFromAnyActiveChain(
                variableName.trim()
            );
        }
        if (runtimeVariable == null) {
            sendVariableError(
                "Variable \"" + variableName.trim() + "\" is not set.",
                future
            );
            return null;
        }

        NodeType valueType = runtimeVariable.getType();
        if (valueType == null) {
            sendVariableError(
                "Variable \"" + variableName.trim() + "\" has no value.",
                future
            );
            return null;
        }

        Node snapshot = createRuntimeVariableSnapshot(runtimeVariable);
        if (snapshot == null) {
            sendVariableError(
                "Variable \"" + variableName.trim() + "\" has no value.",
                future
            );
            return null;
        }

        boolean variableSupported = isParameterSupported(snapshot, slotIndex);
        if (
            !variableSupported &&
            (type == NodeType.OPERATOR_GREATER ||
                type == NodeType.OPERATOR_LESS)
        ) {
            variableSupported = resolveComparableNumber(snapshot).isPresent();
        }

        if (!variableSupported) {
            sendVariableError(
                "Variable \"" +
                    variableName.trim() +
                    "\" cannot be used with " +
                    type.getDisplayName() +
                    ".",
                future
            );
            return null;
        }

        return snapshot;
    }

    private void sendVariableError(
        String message,
        CompletableFuture<Void> future
    ) {
        NodeExecutionCompletion.failWithCurrentClient(this, future, message);
    }

    Optional<Vec3d> resolvePositionTarget(
        Node parameterNode,
        RuntimeParameterData data,
        CompletableFuture<Void> future
    ) {
        if (
            parameterNode != null &&
            parameterNode.getType() == NodeType.LIST_ITEM
        ) {
            Node resolved = resolveListItemValueNode(
                parameterNode,
                future,
                false,
                data
            );
            if (resolved != null) {
                return resolvePositionTarget(resolved, data, future);
            }
        }
        if (
            parameterNode != null &&
            parameterNode.getType() == NodeType.SENSOR_POSITION_OF
        ) {
            Node resolved = parameterNode.getAttachedParameterOfType(
                NodeType.PARAM_ENTITY,
                NodeType.PARAM_BLOCK,
                NodeType.PARAM_ITEM,
                NodeType.PARAM_PLAYER
            );
            if (resolved != null) {
                return resolvePositionTarget(resolved, data, future);
            }
            return Optional.empty();
        }
        if (
            parameterNode != null &&
            parameterNode.getType() == NodeType.SENSOR_TARGETED_ENTITY
        ) {
            Optional<Entity> resolved = getTargetedEntity();
            if (resolved.isEmpty()) {
                return Optional.empty();
            }
            Entity entity = resolved.get();
            if (data != null) {
                Identifier id = Registries.ENTITY_TYPE.getId(entity.getType());
                data.targetEntity = entity;
                data.targetEntityId = id != null ? id.toString() : null;
                data.targetBlockPos = entity.getBlockPos();
            }
            Vec3d pos = EntityCompatibilityBridge.getPos(entity);
            return pos != null
                ? Optional.of(pos)
                : Optional.of(Vec3d.ofCenter(entity.getBlockPos()));
        }
        if (
            parameterNode != null &&
            parameterNode.getType() == NodeType.SENSOR_TARGETED_BLOCK
        ) {
            Optional<BlockPos> resolved = getTargetedBlockPos();
            if (resolved.isEmpty()) {
                return Optional.empty();
            }
            if (data != null) {
                data.targetBlockPos = resolved.get();
            }
            return Optional.of(Vec3d.ofCenter(resolved.get()));
        }
        if (data != null && data.targetVector != null) {
            return Optional.of(data.targetVector);
        }
        if (
            data != null &&
            data.targetBlockPos != null &&
            parameterNode.getType() == NodeType.LIST_ITEM
        ) {
            return Optional.of(Vec3d.ofCenter(data.targetBlockPos));
        }

        NodeType parameterType = parameterNode.getType();

        NodeBehaviorDefinition behaviorDefinition =
            NodeBehaviorDefinitionRegistry.get(parameterType);
        if (
            behaviorDefinition != null &&
            behaviorDefinition.hasRuntimeBehavior()
        ) {
            return behaviorDefinition.resolvePositionTarget(
                this,
                parameterNode,
                data,
                future
            );
        }

        String xValue = getParameterString(parameterNode, "X");
        String yValue = getParameterString(parameterNode, "Y");
        String zValue = getParameterString(parameterNode, "Z");
        if (xValue != null && yValue != null && zValue != null) {
            try {
                int x = Integer.parseInt(xValue.trim());
                int y = Integer.parseInt(yValue.trim());
                int z = Integer.parseInt(zValue.trim());
                BlockPos pos = new BlockPos(x, y, z);
                if (data != null) {
                    data.targetBlockPos = pos;
                }
                return Optional.of(Vec3d.ofCenter(pos));
            } catch (NumberFormatException ignored) {
                // fall through to empty optional
            }
        }

        return Optional.empty();
    }

    Optional<Vec3d> resolveDistanceBetweenTarget(Node parameterNode) {
        if (parameterNode == null) {
            return Optional.empty();
        }
        if (parameterNode.getType() != NodeType.PARAM_ENTITY) {
            return resolvePositionTarget(parameterNode, null, null);
        }

        net.minecraft.client.MinecraftClient client =
            net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return Optional.empty();
        }

        String state = getEntityParameterState(parameterNode);
        double range = parseNodeDouble(parameterNode, "Range", 256.0);
        double searchRadius = Math.max(1.0, range);
        List<String> entityIds = resolveEntityIdsFromParameter(parameterNode);
        if (entityIds.isEmpty()) {
            Entity nearestAny = null;
            double nearestAnyDistance = Double.MAX_VALUE;
            Box anySearchBox = client.player
                .getBoundingBox()
                .expand(searchRadius);
            for (Entity entity : client.world.getOtherEntities(
                client.player,
                anySearchBox
            )) {
                if (entity == null || entity.isRemoved()) {
                    continue;
                }
                if (!EntityStateOptions.matchesState(entity, state)) {
                    continue;
                }
                double distance = entity.squaredDistanceTo(client.player);
                if (nearestAny == null || distance < nearestAnyDistance) {
                    nearestAny = entity;
                    nearestAnyDistance = distance;
                }
            }
            if (nearestAny == null) {
                return Optional.empty();
            }
            Vec3d pos = EntityCompatibilityBridge.getPos(nearestAny);
            if (pos != null) {
                return Optional.of(pos);
            }
            return Optional.of(Vec3d.ofCenter(nearestAny.getBlockPos()));
        }

        Box searchBox = client.player.getBoundingBox().expand(searchRadius);

        java.util.Set<Identifier> targetIds = new java.util.HashSet<>();
        for (String candidateId : entityIds) {
            Identifier id = Identifier.tryParse(candidateId);
            if (id != null) {
                targetIds.add(id);
            }
        }
        if (targetIds.isEmpty()) {
            return Optional.empty();
        }

        Entity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Entity entity : client.world.getOtherEntities(
            client.player,
            searchBox
        )) {
            if (entity == null || entity.isRemoved()) {
                continue;
            }
            Identifier candidateId = Registries.ENTITY_TYPE.getId(
                entity.getType()
            );
            if (candidateId == null || !targetIds.contains(candidateId)) {
                continue;
            }
            if (!EntityStateOptions.matchesState(entity, state)) {
                continue;
            }
            double distance = entity.squaredDistanceTo(client.player);
            if (nearest == null || distance < nearestDistance) {
                nearest = entity;
                nearestDistance = distance;
            }
        }

        if (nearest == null) {
            return Optional.empty();
        }
        Vec3d pos = EntityCompatibilityBridge.getPos(nearest);
        if (pos != null) {
            return Optional.of(pos);
        }
        return Optional.of(Vec3d.ofCenter(nearest.getBlockPos()));
    }

    private void applyVectorToCoordinateParameters(Vec3d targetVec) {
        if (targetVec == null) {
            return;
        }
        int x = MathHelper.floor(targetVec.x);
        int y = MathHelper.floor(targetVec.y);
        int z = MathHelper.floor(targetVec.z);
        if (runtimeState.runtimeParameterData != null) {
            runtimeState.runtimeParameterData.targetBlockPos = new BlockPos(
                x,
                y,
                z
            );
        }
        setParameterValueAndPropagate("X", Integer.toString(x));
        setParameterValueAndPropagate("Y", Integer.toString(y));
        setParameterValueAndPropagate("Z", Integer.toString(z));
    }

    boolean isPlayerAtCoordinates(
        Integer targetX,
        Integer targetY,
        Integer targetZ
    ) {
        net.minecraft.client.MinecraftClient client =
            net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        BlockPos playerPos = client.player.getBlockPos();
        if (targetX != null && playerPos.getX() != targetX) {
            return false;
        }
        if (targetY != null && playerPos.getY() != targetY) {
            return false;
        }
        if (targetZ != null && playerPos.getZ() != targetZ) {
            return false;
        }
        return true;
    }

    private boolean resolveLookOrientation(
        Node parameterNode,
        RuntimeParameterData data,
        CompletableFuture<Void> future
    ) {
        net.minecraft.client.MinecraftClient client =
            net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }

        if (
            parameterNode != null &&
            parameterNode.getType() == NodeType.LIST_ITEM
        ) {
            Node resolved = resolveListItemValueNode(
                parameterNode,
                future,
                false,
                data
            );
            if (resolved != null) {
                return resolveLookOrientation(resolved, data, future);
            }
        }

        if (
            parameterNode != null &&
            parameterNode.getType() == NodeType.PARAM_BLOCK_FACE
        ) {
            Node targetNode = parameterNode.getAttachedParameter(0);
            if (targetNode == null) {
                return false;
            }
            if (targetNode.getType() == NodeType.VARIABLE) {
                targetNode = resolveVariableValueNode(targetNode, 0, future);
                if (targetNode == null) {
                    return false;
                }
            }

            String faceName = getParameterString(parameterNode, "Face");
            if (faceName == null || faceName.trim().isEmpty()) {
                faceName = getParameterString(parameterNode, "Side");
            }
            Direction targetFace = parseDirectionValue(faceName);
            if (targetFace == null) {
                targetFace = Direction.NORTH;
            }

            // Resolve the nested target independently so any temporary vector state on the outer
            // runtime context cannot override the actual block/coordinate target.
            RuntimeParameterData targetData = new RuntimeParameterData();
            Optional<Vec3d> resolvedTarget = resolvePositionTarget(
                targetNode,
                targetData,
                future
            );
            if (resolvedTarget.isEmpty()) {
                return false;
            }

            BlockPos targetBlockPos = targetData.targetBlockPos;
            if (targetBlockPos == null) {
                Vec3d targetVec = resolvedTarget.get();
                targetBlockPos = new BlockPos(
                    MathHelper.floor(targetVec.x),
                    MathHelper.floor(targetVec.y),
                    MathHelper.floor(targetVec.z)
                );
                if (data != null) {
                    data.targetBlockPos = targetBlockPos;
                }
            }

            Vec3d faceCenter = Vec3d.ofCenter(targetBlockPos).add(
                targetFace.getOffsetX() * 0.5D,
                targetFace.getOffsetY() * 0.5D,
                targetFace.getOffsetZ() * 0.5D
            );
            Vec3d eyes = client.player.getEyePos();
            Vec3d delta = faceCenter.subtract(eyes);
            if (delta.lengthSquared() < 1.0E-6) {
                return false;
            }

            float yaw = (float) (MathHelper.wrapDegrees(
                    Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0D
                ));
            float pitch = (float) (-Math.toDegrees(
                    Math.atan2(
                        delta.y,
                        Math.sqrt(delta.x * delta.x + delta.z * delta.z)
                    )
                ));
            float clampedPitch = MathHelper.clamp(pitch, -90.0F, 90.0F);

            setParameterIfPresent("Yaw", formatFloat(yaw));
            setParameterIfPresent("Pitch", formatFloat(clampedPitch));

            if (data != null) {
                data.targetBlockPos = targetBlockPos;
                data.targetVector = faceCenter;
                data.resolvedYaw = yaw;
                data.resolvedPitch = clampedPitch;
            }
            return true;
        }

        boolean allowDirectRotation =
            parameterNode.getType() != NodeType.PARAM_DIRECTION ||
            parameterNode.isDirectionModeExact();
        Float yawParam = allowDirectRotation
            ? parseNodeFloat(parameterNode, "Yaw")
            : null;
        Float pitchParam = allowDirectRotation
            ? parseNodeFloat(parameterNode, "Pitch")
            : null;
        if (
            allowDirectRotation &&
            yawParam == null &&
            pitchParam == null &&
            providesTrait(parameterNode, NodeValueTrait.ROTATION)
        ) {
            Map<String, String> exported =
                parameterNode.exportParameterValues();
            yawParam = parseFloatOrNull(exported.get("Yaw"));
            pitchParam = parseFloatOrNull(exported.get("Pitch"));
            if (data != null) {
                Double distance = parseDoubleOrNull(exported.get("Distance"));
                if (distance == null) {
                    distance = parseDoubleOrNull(exported.get("LookDistance"));
                }
                if (distance == null) {
                    distance = parseDoubleOrNull(exported.get("Range"));
                }
                if (distance != null && distance > 0.0) {
                    data.resolvedLookDistance = distance;
                }
            }
        }
        if (yawParam != null || pitchParam != null) {
            if (yawParam != null) {
                setParameterIfPresent("Yaw", formatFloat(yawParam));
                if (data != null) {
                    data.resolvedYaw = yawParam;
                }
            }
            if (pitchParam != null) {
                float clamped = MathHelper.clamp(pitchParam, -90.0F, 90.0F);
                setParameterIfPresent("Pitch", formatFloat(clamped));
                if (data != null) {
                    data.resolvedPitch = clamped;
                }
            }
            if (data != null) {
                double distance = parseNodeDouble(
                    parameterNode,
                    "Distance",
                    -1.0
                );
                if (distance > 0.0) {
                    data.resolvedLookDistance = distance;
                }
            }
            return true;
        }

        if (
            type == NodeType.LOOK &&
            providesTrait(parameterNode, NodeValueTrait.NUMBER)
        ) {
            float yaw = (float) MathHelper.wrapDegrees(
                client.player.getYaw() +
                    parseNodeDouble(parameterNode, "Amount", 0.0)
            );
            setParameterIfPresent("Yaw", formatFloat(yaw));
            if (data != null) {
                data.resolvedYaw = yaw;
                data.resolvedPitch = client.player.getPitch();
            }
            return true;
        }

        if (
            providesTrait(parameterNode, NodeValueTrait.DIRECTION) &&
            (parameterNode.getType() != NodeType.PARAM_DIRECTION ||
                parameterNode.isDirectionModeCardinal())
        ) {
            String direction = getParameterString(parameterNode, "Direction");
            if (direction == null || direction.isEmpty()) {
                direction = getParameterString(parameterNode, "Side");
            }
            if (direction == null || direction.isEmpty()) {
                direction = getParameterString(parameterNode, "Face");
            }
            if (direction == null || direction.isEmpty()) {
                Map<String, String> exported =
                    parameterNode.exportParameterValues();
                direction = exported.get("Direction");
                if (direction == null || direction.isEmpty()) {
                    direction = exported.get("Side");
                }
                if (direction == null || direction.isEmpty()) {
                    direction = exported.get("Face");
                }
            }
            if (direction != null) {
                String normalized = direction.trim().toLowerCase(Locale.ROOT);
                Float yaw = null;
                Float pitch = null;
                switch (normalized) {
                    case "north":
                        yaw = 180.0F;
                        break;
                    case "south":
                        yaw = 0.0F;
                        break;
                    case "west":
                        yaw = 90.0F;
                        break;
                    case "east":
                        yaw = -90.0F;
                        break;
                    case "up":
                        yaw = client.player.getYaw();
                        pitch = -90.0F;
                        break;
                    case "down":
                        yaw = client.player.getYaw();
                        pitch = 90.0F;
                        break;
                    default:
                        break;
                }
                if (yaw != null) {
                    setParameterIfPresent("Yaw", formatFloat(yaw));
                    if (data != null) {
                        data.resolvedYaw = yaw;
                    }
                }
                if (pitch != null) {
                    float clamped = MathHelper.clamp(pitch, -90.0F, 90.0F);
                    setParameterIfPresent("Pitch", formatFloat(clamped));
                    if (data != null) {
                        data.resolvedPitch = clamped;
                    }
                }
                if (yaw != null || pitch != null) {
                    if (data != null) {
                        double distance = parseNodeDouble(
                            parameterNode,
                            "Distance",
                            -1.0
                        );
                        if (distance > 0.0) {
                            data.resolvedLookDistance = distance;
                        }
                    }
                    return true;
                }
            }
        }

        Vec3d target = null;
        if (
            data != null &&
            data.targetEntity != null &&
            data.targetEntity.isAlive()
        ) {
            target = data.targetEntity.getBoundingBox().getCenter();
        }
        if (target == null && data != null) {
            target = data.targetVector;
        }
        if (target == null) {
            Optional<Vec3d> resolved = resolvePositionTarget(
                parameterNode,
                data,
                future
            );
            if (resolved.isEmpty()) {
                return false;
            }
            target = resolved.get();
        }

        Vec3d eyes = client.player.getEyePos();
        Vec3d delta = target.subtract(eyes);
        if (delta.lengthSquared() < 1.0E-6) {
            return false;
        }
        float yaw = (float) (MathHelper.wrapDegrees(
                Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0D
            ));
        float pitch = (float) (-Math.toDegrees(
                Math.atan2(
                    delta.y,
                    Math.sqrt(delta.x * delta.x + delta.z * delta.z)
                )
            ));
        float clampedPitch = MathHelper.clamp(pitch, -90.0F, 90.0F);

        setParameterIfPresent("Yaw", formatFloat(yaw));
        setParameterIfPresent("Pitch", formatFloat(clampedPitch));

        if (data != null) {
            data.resolvedYaw = yaw;
            data.resolvedPitch = clampedPitch;
        }
        return true;
    }

    private Direction parseDirectionValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "north" -> Direction.NORTH;
            case "south" -> Direction.SOUTH;
            case "east" -> Direction.EAST;
            case "west" -> Direction.WEST;
            case "up" -> Direction.UP;
            case "down" -> Direction.DOWN;
            default -> null;
        };
    }

    void orientPlayerTowardsRuntimeTarget(
        net.minecraft.client.MinecraftClient client,
        RuntimeParameterData data
    ) {
        if (client == null || client.player == null || data == null) {
            return;
        }

        float yaw = client.player.getYaw();
        float pitch = client.player.getPitch();
        boolean applyYaw = false;
        boolean applyPitch = false;

        Vec3d targetVector = null;
        if (data.targetEntity != null && data.targetEntity.isAlive()) {
            targetVector = data.targetEntity.getBoundingBox().getCenter();
        }
        if (targetVector == null && data.targetVector != null) {
            targetVector = data.targetVector;
        }
        if (targetVector == null && data.targetBlockPos != null) {
            targetVector = Vec3d.ofCenter(data.targetBlockPos);
        }

        if (targetVector != null) {
            Vec3d eyes = client.player.getEyePos();
            Vec3d delta = targetVector.subtract(eyes);
            if (delta.lengthSquared() > 1.0E-6) {
                yaw = (float) (MathHelper.wrapDegrees(
                        Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0D
                    ));
                pitch = (float) (-Math.toDegrees(
                        Math.atan2(
                            delta.y,
                            Math.sqrt(delta.x * delta.x + delta.z * delta.z)
                        )
                    ));
                pitch = MathHelper.clamp(pitch, -90.0F, 90.0F);
                applyYaw = true;
                applyPitch = true;
            }
        }

        if (!applyYaw && data.resolvedYaw != null) {
            yaw = data.resolvedYaw;
            applyYaw = true;
        }
        if (!applyPitch && data.resolvedPitch != null) {
            pitch = MathHelper.clamp(data.resolvedPitch, -90.0F, 90.0F);
            applyPitch = true;
        }

        if (!applyYaw && !applyPitch) {
            return;
        }

        client.player.setYaw(yaw);
        client.player.setPitch(pitch);
        client.player.setHeadYaw(yaw);

        if (applyYaw) {
            data.resolvedYaw = yaw;
        }
        if (applyPitch) {
            data.resolvedPitch = pitch;
        }
    }

    private void sendIncompatibleParameterMessage(Node parameterNode) {
        net.minecraft.client.MinecraftClient client =
            net.minecraft.client.MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        if (
            parameterNode != null &&
            (this.type == NodeType.PLACE || this.type == NodeType.PLACE_HAND)
        ) {
            NodeType parameterType = parameterNode.getType();
            // Allow PARAM_CLOSEST in any slot
            if (parameterType == NodeType.PARAM_CLOSEST) {
                return;
            }
            // Allow block parameters in slot 0 (they provide block type, not position)
            if (
                parameterType == NodeType.PARAM_BLOCK &&
                parameterNode.attachments.getParentParameterSlotIndex() == 0
            ) {
                return;
            }
        }
        sendNodeErrorMessage(
            client,
            "Parameter \"" +
                parameterNode.getType().getDisplayName() +
                "\" cannot be used with \"" +
                this.type.getDisplayName() +
                "\"."
        );
    }

    void sendParameterSearchFailure(
        String message,
        CompletableFuture<Void> future
    ) {
        // Only surface search failures during execution contexts (future != null).
        // UI/preview calls (future == null) should not spam chat.
        if (future != null) {
            NodeExecutionCompletion.failWithCurrentClient(
                this,
                future,
                message
            );
        }
    }

    private boolean reportEmptyParametersForNode(
        Node target,
        CompletableFuture<Void> future
    ) {
        if (target == null) {
            return true;
        }
        List<String> emptyNames = new ArrayList<>();
        collectEmptyUserEditedParameters(target, emptyNames);
        if (emptyNames.isEmpty()) {
            return true;
        }
        String joined = String.join(", ", emptyNames);
        String subject =
            target.getType() != null
                ? target.getType().getDisplayName() + " node"
                : "node";
        String message =
            emptyNames.size() == 1
                ? joined + " cannot be empty on " + subject + "."
                : "Parameters " +
                  joined +
                  " cannot be empty on " +
                  subject +
                  ".";
        NodeExecutionCompletion.failWithCurrentClient(this, future, message);
        return false;
    }

    private void collectEmptyUserEditedParameters(
        Node target,
        List<String> emptyNames
    ) {
        if (target == null || emptyNames == null) {
            return;
        }
        for (NodeParameter parameter : target.getParameters()) {
            if (parameter == null || !parameter.isUserEdited()) {
                continue;
            }
            String value = parameter.getStringValue();
            if (value != null && !value.trim().isEmpty()) {
                continue;
            }
            String defaultValue = parameter.getDefaultValue();
            if (defaultValue != null && !defaultValue.isEmpty()) {
                emptyNames.add(parameter.getName());
            }
        }
    }

    private boolean reportEmptyParametersForAttachedParameters(
        CompletableFuture<Void> future
    ) {
        if (!attachments.hasAttachedParameters()) {
            return true;
        }
        for (Node parameterNode : attachments.getAttachedParameterNodes()) {
            if (parameterNode == null || !parameterNode.isParameterNode()) {
                continue;
            }
            if (!reportEmptyParametersForNode(parameterNode, future)) {
                return false;
            }
        }
        return true;
    }

    private void setParameterIfPresent(String name, String value) {
        if (name == null || value == null) {
            return;
        }
        NodeParameter parameter = getParameter(name);
        if (parameter != null) {
            parameter.setStringValue(value);
        }
    }

    private static String formatFloat(float value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    static int parseNodeInt(Node node, String name, int defaultValue) {
        if (node != null && node.getType() == NodeType.OPERATOR_RANDOM) {
            double min = node.getDoubleParameter("Min", 0.0);
            double max = node.getDoubleParameter("Max", 1.0);
            return (int) Math.round(
                node.generateRandomValueWithRounding(min, max)
            );
        }
        if (node != null && node.getType() == NodeType.OPERATOR_MOD) {
            return (int) Math.round(
                node.resolveModValue().orElse((double) defaultValue)
            );
        }
        if (node != null && node.getType() == NodeType.LIST_LENGTH) {
            return node.resolveListLengthValue(node).orElse(defaultValue);
        }
        if (node != null && node.getType() == NodeType.VARIABLE) {
            String variableName = getParameterString(node, "Variable");
            Node resolved = node.resolveVariableValueNode(node, 0, null);
            if (resolved == null) {
                return defaultValue;
            }
            if (resolved.getType() == NodeType.PARAM_INVENTORY_SLOT) {
                return parseNodeInt(resolved, name, defaultValue);
            }
            Optional<Double> value = node.resolveComparableNumber(resolved);
            if (value.isPresent()) {
                return (int) Math.round(value.get());
            }
            net.minecraft.client.MinecraftClient client =
                net.minecraft.client.MinecraftClient.getInstance();
            if (
                client != null &&
                variableName != null &&
                !variableName.trim().isEmpty()
            ) {
                node.sendNodeErrorMessage(
                    client,
                    "Variable \"" +
                        variableName.trim() +
                        "\" is not a numeric value."
                );
            }
            return defaultValue;
        }
        String value = getParameterString(node, name);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        Double evaluated = evaluateNumericExpression(value);
        if (evaluated != null) {
            return (int) Math.round(evaluated);
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            net.minecraft.client.MinecraftClient client =
                net.minecraft.client.MinecraftClient.getInstance();
            if (client != null) {
                node.sendNodeErrorMessage(
                    client,
                    "Please enter a number, arithmetic expression, or variable (~variable_name)."
                );
            }
            return defaultValue;
        }
    }

    static double parseNodeDouble(Node node, String name, double defaultValue) {
        if (node != null && node.getType() == NodeType.OPERATOR_RANDOM) {
            double min = node.getDoubleParameter("Min", 0.0);
            double max = node.getDoubleParameter("Max", 1.0);
            return node.generateRandomValueWithRounding(min, max);
        }
        if (node != null && node.getType() == NodeType.OPERATOR_MOD) {
            return node.resolveModValue().orElse(defaultValue);
        }
        if (node != null && node.getType() == NodeType.LIST_LENGTH) {
            Optional<Integer> length = node.resolveListLengthValue(node);
            if (length.isPresent()) {
                return length.get();
            }
        }
        if (node != null && node.getType() == NodeType.VARIABLE) {
            String variableName = getParameterString(node, "Variable");
            Node resolved = node.resolveVariableValueNode(node, 0, null);
            if (resolved == null) {
                return defaultValue;
            }
            if (resolved.getType() == NodeType.PARAM_INVENTORY_SLOT) {
                return parseNodeDouble(resolved, name, defaultValue);
            }
            Optional<Double> value = node.resolveComparableNumber(resolved);
            if (value.isPresent()) {
                return value.get();
            }
            net.minecraft.client.MinecraftClient client =
                net.minecraft.client.MinecraftClient.getInstance();
            if (
                client != null &&
                variableName != null &&
                !variableName.trim().isEmpty()
            ) {
                node.sendNodeErrorMessage(
                    client,
                    "Variable \"" +
                        variableName.trim() +
                        "\" is not a numeric value."
                );
            }
            return defaultValue;
        }
        String value = getParameterString(node, name);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        Double evaluated = evaluateNumericExpression(value);
        if (evaluated != null) {
            return evaluated;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            net.minecraft.client.MinecraftClient client =
                net.minecraft.client.MinecraftClient.getInstance();
            if (client != null) {
                node.sendNodeErrorMessage(
                    client,
                    "Please enter a number, arithmetic expression, or variable (~variable_name)."
                );
            }
            return defaultValue;
        }
    }

    static boolean parseNodeBoolean(
        Node node,
        String name,
        boolean defaultValue
    ) {
        if (node != null && node.getType() == NodeType.VARIABLE) {
            Node resolved = node.resolveVariableValueNode(node, 0, null);
            return node.resolveBooleanFromNode(resolved).orElse(defaultValue);
        }
        String value = getParameterString(node, name);
        if (value == null || value.isEmpty() || node == null) {
            return defaultValue;
        }
        return node
            .resolveBooleanValueFromRaw(value, false)
            .orElse(defaultValue);
    }

    static Float parseNodeFloat(Node node, String name) {
        String value = getParameterString(node, name);
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Float parseFloatOrNull(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Integer parseIntOrNull(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Double parseDoubleOrNull(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private double generateRandomValue(double min, double max) {
        if (Double.isNaN(min) || Double.isNaN(max)) {
            return 0.0;
        }
        double lower = min;
        double upper = max;
        if (lower > upper) {
            double swap = lower;
            lower = upper;
            upper = swap;
        }
        if (lower == upper) {
            return lower;
        }
        Random generator = getRandomGenerator();
        double range = upper - lower;
        if (generator == null) {
            return lower + Math.random() * range;
        }
        return lower + generator.nextDouble() * range;
    }

    private double generateRandomValueWithRounding(double min, double max) {
        double value = generateRandomValue(min, max);
        if (!isRandomRoundingEnabled()) {
            return value;
        }
        String mode = getRandomRoundingMode();
        return switch (mode) {
            case "floor" -> Math.floor(value);
            case "ceil" -> Math.ceil(value);
            default -> (double) Math.round(value);
        };
    }

    private Optional<Double> resolveModValue() {
        Node left = getAttachedParameter(0);
        Node right = getAttachedParameter(1);
        if (left == null || right == null) {
            return Optional.empty();
        }
        Optional<Double> leftNumber = resolveComparableNumberWithVariables(
            left,
            0
        );
        Optional<Double> rightNumber = resolveComparableNumberWithVariables(
            right,
            1
        );
        if (leftNumber.isEmpty() || rightNumber.isEmpty()) {
            return Optional.empty();
        }
        double divisor = rightNumber.get();
        if (divisor == 0.0) {
            return Optional.empty();
        }
        return Optional.of(leftNumber.get() % divisor);
    }

    private Random getRandomGenerator() {
        String seed = getParameterString(this, "Seed");
        if (seed == null || seed.trim().isEmpty() || isAnySeedValue(seed)) {
            runtimeState.randomGenerator = null;
            runtimeState.randomSeedCache = null;
            return null;
        }
        String trimmed = seed.trim();
        if (
            runtimeState.randomGenerator == null ||
            runtimeState.randomSeedCache == null ||
            !runtimeState.randomSeedCache.equals(trimmed)
        ) {
            long hashedSeed = hashSeedString(trimmed);
            runtimeState.randomGenerator = new Random(hashedSeed);
            runtimeState.randomSeedCache = trimmed;
        }
        return runtimeState.randomGenerator;
    }

    private static long hashSeedString(String seed) {
        if (seed == null) {
            return 0L;
        }
        long hash = 1125899906842597L;
        for (int i = 0; i < seed.length(); i++) {
            hash = 31L * hash + seed.charAt(i);
        }
        return hash;
    }

    private boolean isAnySeedValue(String seed) {
        if (seed == null) {
            return true;
        }
        String trimmed = seed.trim();
        return trimmed.isEmpty() || "any".equalsIgnoreCase(trimmed);
    }

    static boolean isAnySelectionValue(String value) {
        if (value == null) {
            return true;
        }
        String trimmed = value.trim();
        return (
            trimmed.isEmpty() ||
            "any".equalsIgnoreCase(trimmed) ||
            "any state".equalsIgnoreCase(trimmed)
        );
    }

    String sanitizeResourceId(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String lower = trimmed.toLowerCase(Locale.ROOT).replace(' ', '_');
        int bracketIndex = lower.indexOf('[');
        if (bracketIndex >= 0) {
            lower = lower.substring(0, bracketIndex);
        }
        String sanitized = UNSAFE_RESOURCE_ID_PATTERN.matcher(lower).replaceAll(
            ""
        );
        int firstColon = sanitized.indexOf(':');
        if (firstColon != -1) {
            int nextColon = sanitized.indexOf(':', firstColon + 1);
            if (nextColon != -1) {
                sanitized =
                    sanitized.substring(0, firstColon + 1) +
                    sanitized.substring(firstColon + 1).replace(':', '_');
            }
        }
        return sanitized;
    }

    String normalizeResourceId(String value, String defaultNamespace) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (isAnySelectionValue(trimmed)) {
            return "";
        }
        if (!trimmed.contains(":")) {
            return defaultNamespace + ":" + trimmed;
        }
        return trimmed;
    }

    List<BlockSelection> resolveBlocksFromParameter(Node parameterNode) {
        List<BlockSelection> selections = new ArrayList<>();
        String primary = getBlockParameterValue(parameterNode);
        String listValue = getParameterString(parameterNode, "Blocks");
        for (String entry : splitMultiValueList(listValue)) {
            addBlockSelection(selections, entry);
        }
        for (String entry : splitMultiValueList(primary)) {
            addBlockSelection(selections, entry);
        }
        return selections;
    }

    private void addBlockSelection(
        List<BlockSelection> selections,
        String rawValue
    ) {
        if (rawValue == null || rawValue.isEmpty()) {
            return;
        }
        if (isAnySelectionValue(rawValue)) {
            return;
        }
        BlockSelection.parse(rawValue).ifPresent(selection -> {
            if (selection.getBlock() != null) {
                boolean exists = selections
                    .stream()
                    .anyMatch(existing ->
                        existing.asString().equals(selection.asString())
                    );
                if (!exists) {
                    selections.add(selection);
                }
            }
        });
    }

    List<String> resolveItemIdsFromParameter(Node parameterNode) {
        List<String> itemIds = new ArrayList<>();
        if (parameterNode == null) {
            return itemIds;
        }
        String listValue = getParameterString(parameterNode, "Items");
        for (String entry : splitMultiValueList(listValue)) {
            addItemIdentifier(itemIds, entry);
        }
        for (String entry : splitMultiValueList(
            getParameterString(parameterNode, "Item")
        )) {
            addItemIdentifier(itemIds, entry);
        }
        return itemIds;
    }

    private String resolveTradeKeyFromParameter(Node parameterNode) {
        if (parameterNode == null) {
            return "";
        }
        String trade = getParameterString(parameterNode, "Trade");
        if (trade != null && !trade.isEmpty()) {
            return trade;
        }
        String legacy = getParameterString(parameterNode, "Item");
        return legacy != null ? legacy : "";
    }

    private String getTradeKeySellItemId(String tradeKey) {
        if (tradeKey == null || tradeKey.isEmpty()) {
            return "";
        }
        if (tradeKey.contains("|") && tradeKey.contains("@")) {
            String[] parts = tradeKey.split("\\|");
            if (parts.length > 0) {
                String sellPart = parts[parts.length - 1];
                int atIndex = sellPart.indexOf('@');
                if (atIndex > 0) {
                    return sellPart.substring(0, atIndex);
                }
            }
        }
        return tradeKey;
    }

    private String buildTradeKey(
        ItemStack firstBuy,
        ItemStack secondBuy,
        ItemStack sell
    ) {
        return (
            buildTradeKeyPart(firstBuy) +
            "|" +
            buildTradeKeyPart(secondBuy) +
            "|" +
            buildTradeKeyPart(sell)
        );
    }

    private String buildTradeKeyPart(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "none@0";
        }
        Identifier id = Registries.ITEM.getId(stack.getItem());
        String itemId = id != null ? id.toString() : "unknown";
        return itemId + "@" + stack.getCount();
    }

    private int findTradeIndexFromLegacySelection(
        net.minecraft.village.TradeOfferList tradeOffers,
        boolean requireInStock,
        boolean requireAffordable
    ) {
        List<Integer> matches = findTradeIndexesFromLegacySelection(
            tradeOffers,
            requireInStock,
            requireAffordable
        );
        return matches.isEmpty() ? -1 : matches.get(0);
    }

    private boolean hasMultipleVillagerTradeSelections(Node parameterNode) {
        if (
            parameterNode == null ||
            !providesTrait(parameterNode, NodeValueTrait.VILLAGER_TRADE)
        ) {
            return false;
        }
        java.util.Set<String> selections = new java.util.LinkedHashSet<>();
        for (String entry : splitMultiValueList(
            getParameterString(parameterNode, "Trade")
        )) {
            if (entry != null && !entry.isEmpty()) {
                selections.add(entry);
            }
        }
        for (String entry : splitMultiValueList(
            getParameterString(parameterNode, "Item")
        )) {
            if (entry != null && !entry.isEmpty()) {
                selections.add(entry);
            }
        }
        return selections.size() > 1;
    }

    private List<Integer> findTradeIndexesFromLegacySelection(
        net.minecraft.village.TradeOfferList tradeOffers,
        boolean requireInStock,
        boolean requireAffordable
    ) {
        if (tradeOffers == null || tradeOffers.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> desiredItemIds = new ArrayList<>();
        List<String> desiredTradeKeys = new ArrayList<>();
        RuntimeParameterData parameterData = runtimeState.runtimeParameterData;
        if (
            parameterData != null &&
            parameterData.targetItemId != null &&
            !parameterData.targetItemId.isEmpty()
        ) {
            desiredItemIds.add(parameterData.targetItemId);
        }
        if (
            parameterData != null &&
            parameterData.targetTradeKey != null &&
            !parameterData.targetTradeKey.isEmpty()
        ) {
            desiredTradeKeys.add(parameterData.targetTradeKey);
        }

        Node parameterNode = resolveSensorParameterNode(
            getAttachedParameter(),
            0
        );
        if (
            parameterNode != null &&
            providesTrait(parameterNode, NodeValueTrait.VILLAGER_TRADE)
        ) {
            for (String entry : splitMultiValueList(
                getParameterString(parameterNode, "Trade")
            )) {
                if (entry.contains("|") && entry.contains("@")) {
                    if (!desiredTradeKeys.contains(entry)) {
                        desiredTradeKeys.add(entry);
                    }
                    String sellItemId = getTradeKeySellItemId(entry);
                    if (
                        !sellItemId.isEmpty() &&
                        !desiredItemIds.contains(sellItemId)
                    ) {
                        desiredItemIds.add(sellItemId);
                    }
                }
            }
            for (String entry : splitMultiValueList(
                getParameterString(parameterNode, "Item")
            )) {
                if (entry.contains("|") && entry.contains("@")) {
                    if (!desiredTradeKeys.contains(entry)) {
                        desiredTradeKeys.add(entry);
                    }
                    String sellItemId = getTradeKeySellItemId(entry);
                    if (
                        !sellItemId.isEmpty() &&
                        !desiredItemIds.contains(sellItemId)
                    ) {
                        desiredItemIds.add(sellItemId);
                    }
                } else if (
                    !entry.isEmpty() && !desiredItemIds.contains(entry)
                ) {
                    desiredItemIds.add(entry);
                }
            }
        }

        net.minecraft.client.MinecraftClient client =
            net.minecraft.client.MinecraftClient.getInstance();
        net.minecraft.screen.MerchantScreenHandler screenHandler = null;
        if (
            client != null &&
            client.currentScreen instanceof
                net.minecraft.client.gui.screen.ingame.MerchantScreen merchantScreen
        ) {
            screenHandler = merchantScreen.getScreenHandler();
        }

        List<Integer> matches = new ArrayList<>();
        java.util.Set<Integer> seenMatches = new java.util.LinkedHashSet<>();
        List<String> orderedSelections = new ArrayList<>();
        orderedSelections.addAll(desiredTradeKeys);
        for (String itemId : desiredItemIds) {
            if (!orderedSelections.contains(itemId)) {
                orderedSelections.add(itemId);
            }
        }

        for (String desired : orderedSelections) {
            for (int i = 0; i < tradeOffers.size(); i++) {
                net.minecraft.village.TradeOffer offer = tradeOffers.get(i);
                if (
                    !isLegacyTradeSelectionMatch(
                        client,
                        screenHandler,
                        offer,
                        desired,
                        requireInStock,
                        requireAffordable
                    )
                ) {
                    continue;
                }
                if (seenMatches.add(i)) {
                    matches.add(i);
                }
            }
        }

        if (!matches.isEmpty()) {
            return matches;
        }

        for (int i = 0; i < tradeOffers.size(); i++) {
            net.minecraft.village.TradeOffer offer = tradeOffers.get(i);
            if (
                isLegacyTradeSelectionMatch(
                    client,
                    screenHandler,
                    offer,
                    null,
                    requireInStock,
                    requireAffordable
                )
            ) {
                matches.add(i);
            }
        }

        return matches;
    }

    private boolean isLegacyTradeSelectionMatch(
        net.minecraft.client.MinecraftClient client,
        net.minecraft.screen.MerchantScreenHandler screenHandler,
        net.minecraft.village.TradeOffer offer,
        String desiredSelection,
        boolean requireInStock,
        boolean requireAffordable
    ) {
        if (offer == null) {
            return false;
        }
        if (requireInStock && offer.isDisabled()) {
            return false;
        }
        if (
            requireAffordable &&
            (client == null ||
                client.player == null ||
                screenHandler == null ||
                !canAffordTrade(client.player, screenHandler, offer))
        ) {
            return false;
        }
        if (desiredSelection == null || desiredSelection.isEmpty()) {
            return true;
        }
        String offerKey = buildTradeKey(
            offer.getDisplayedFirstBuyItem(),
            offer.getDisplayedSecondBuyItem(),
            offer.getSellItem()
        );
        if (desiredSelection.contains("|") && desiredSelection.contains("@")) {
            return desiredSelection.equals(offerKey);
        }
        return desiredSelection.equals(getTradeKeySellItemId(offerKey));
    }

    List<String> resolveEntityIdsFromParameter(Node parameterNode) {
        List<String> entityIds = new ArrayList<>();
        if (parameterNode == null) {
            return entityIds;
        }
        for (String entry : splitMultiValueList(
            getParameterString(parameterNode, "Entity")
        )) {
            addEntityIdentifier(entityIds, entry);
        }
        return entityIds;
    }

    private void addItemIdentifier(List<String> itemIds, String rawValue) {
        if (rawValue == null || rawValue.isEmpty()) {
            return;
        }
        if (isAnySelectionValue(rawValue)) {
            return;
        }
        String sanitized = sanitizeResourceId(rawValue);
        if (sanitized == null || sanitized.isEmpty()) {
            return;
        }
        String normalized = normalizeResourceId(sanitized, "minecraft");
        if (!itemIds.contains(normalized)) {
            itemIds.add(normalized);
        }
    }

    private void addEntityIdentifier(List<String> entityIds, String rawValue) {
        if (rawValue == null || rawValue.isEmpty()) {
            return;
        }
        if (isAnySelectionValue(rawValue)) {
            return;
        }
        String sanitized = sanitizeResourceId(rawValue);
        if (sanitized == null || sanitized.isEmpty()) {
            return;
        }
        String normalized = normalizeResourceId(sanitized, "minecraft");
        if (!entityIds.contains(normalized)) {
            entityIds.add(normalized);
        }
    }

    List<String> splitMultiValueList(String rawValue) {
        if (rawValue == null) {
            return Collections.emptyList();
        }
        String trimmed = rawValue.trim();
        if (trimmed.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int bracketDepth = 0;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '[') {
                bracketDepth++;
            } else if (c == ']') {
                bracketDepth = Math.max(0, bracketDepth - 1);
            }
            if ((c == ',' || c == ';') && bracketDepth == 0) {
                String entry = current.toString().trim();
                if (!entry.isEmpty()) {
                    parts.add(entry);
                }
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        String entry = current.toString().trim();
        if (!entry.isEmpty()) {
            parts.add(entry);
        }
        return parts;
    }

    Optional<BlockPos> findNearestBlock(
        net.minecraft.client.MinecraftClient client,
        List<BlockSelection> selections,
        double range
    ) {
        if (
            client == null ||
            client.player == null ||
            client.world == null ||
            selections == null ||
            selections.isEmpty()
        ) {
            return Optional.empty();
        }
        int radius = Math.max(1, Math.min((int) Math.ceil(range), 64));
        BlockPos playerPos = client.player.getBlockPos();
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        BlockPos bestPos = null;
        double bestDistance = Double.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    mutable.set(
                        playerPos.getX() + dx,
                        playerPos.getY() + dy,
                        playerPos.getZ() + dz
                    );
                    BlockState state = client.world.getBlockState(mutable);
                    if (state.isAir()) {
                        continue;
                    }
                    boolean matches = false;
                    for (BlockSelection selection : selections) {
                        if (selection.matches(state)) {
                            matches = true;
                            break;
                        }
                    }
                    if (!matches) {
                        continue;
                    }
                    double distance = mutable.getSquaredDistance(playerPos);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestPos = mutable.toImmutable();
                    }
                }
            }
        }

        return Optional.ofNullable(bestPos);
    }

    List<BlockPos> findBlocksWithinRange(
        net.minecraft.client.MinecraftClient client,
        List<BlockSelection> selections,
        double range
    ) {
        if (
            client == null ||
            client.player == null ||
            client.world == null ||
            selections == null ||
            selections.isEmpty()
        ) {
            return Collections.emptyList();
        }
        int radius = Math.max(1, (int) Math.ceil(range));
        BlockPos playerPos = client.player.getBlockPos();
        List<BlockPos> matches = new ArrayList<>();
        int minChunkX = Math.floorDiv(playerPos.getX() - radius, 16);
        int maxChunkX = Math.floorDiv(playerPos.getX() + radius, 16);
        int minChunkZ = Math.floorDiv(playerPos.getZ() - radius, 16);
        int maxChunkZ = Math.floorDiv(playerPos.getZ() + radius, 16);
        int minY = client.world.getBottomY();
        int maxY = minY + client.world.getHeight() - 1;
        double maxDistanceSq = range * range;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!client.world.isChunkLoaded(chunkX, chunkZ)) {
                    continue;
                }
                BlockPos.Mutable mutable = new BlockPos.Mutable();
                int startX = chunkX << 4;
                int startZ = chunkZ << 4;
                for (int localX = 0; localX < 16; localX++) {
                    for (int localZ = 0; localZ < 16; localZ++) {
                        int worldX = startX + localX;
                        int worldZ = startZ + localZ;
                        for (int y = minY; y <= maxY; y++) {
                            mutable.set(worldX, y, worldZ);
                            if (
                                mutable.getSquaredDistance(playerPos) >
                                maxDistanceSq
                            ) {
                                continue;
                            }
                            BlockState state = client.world.getBlockState(
                                mutable
                            );
                            if (state.isAir()) {
                                continue;
                            }
                            if (matchesAnyBlock(selections, state)) {
                                matches.add(mutable.toImmutable());
                            }
                        }
                    }
                }
            }
        }

        matches.sort(
            Comparator.comparingDouble(pos -> pos.getSquaredDistance(playerPos))
        );
        return matches;
    }

    Optional<BlockPos> findNearestAnyBlock(
        net.minecraft.client.MinecraftClient client,
        double range
    ) {
        if (client == null || client.player == null || client.world == null) {
            return Optional.empty();
        }
        int radius = Math.max(1, Math.min((int) Math.ceil(range), 64));
        BlockPos playerPos = client.player.getBlockPos();
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        BlockPos bestPos = null;
        double bestDistance = Double.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    mutable.set(
                        playerPos.getX() + dx,
                        playerPos.getY() + dy,
                        playerPos.getZ() + dz
                    );
                    BlockState state = client.world.getBlockState(mutable);
                    if (state.isAir()) {
                        continue;
                    }
                    double distance = mutable.getSquaredDistance(playerPos);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestPos = mutable.toImmutable();
                    }
                }
            }
        }

        return Optional.ofNullable(bestPos);
    }

    Optional<BlockPos> findNearestOpenBlock(
        net.minecraft.client.MinecraftClient client,
        int range
    ) {
        if (client == null || client.player == null || client.world == null) {
            return Optional.empty();
        }
        int radius = Math.max(1, Math.min(range, 32));
        BlockPos playerPos = client.player.getBlockPos();
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        BlockPos bestPos = null;
        double bestDistance = Double.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    mutable.set(
                        playerPos.getX() + dx,
                        playerPos.getY() + dy,
                        playerPos.getZ() + dz
                    );
                    if (!client.world.getWorldBorder().contains(mutable)) {
                        continue;
                    }
                    if (!isBlockReplaceable(client.world, mutable)) {
                        continue;
                    }
                    if (!hasPlacementSupport(client.world, mutable)) {
                        continue;
                    }
                    Box blockBox = new Box(
                        mutable.getX(),
                        mutable.getY(),
                        mutable.getZ(),
                        mutable.getX() + 1,
                        mutable.getY() + 1,
                        mutable.getZ() + 1
                    );
                    if (
                        !client.world.getOtherEntities(null, blockBox).isEmpty()
                    ) {
                        continue;
                    }
                    double distance = mutable.getSquaredDistance(playerPos);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestPos = mutable.toImmutable();
                    }
                }
            }
        }

        return Optional.ofNullable(bestPos);
    }

    /**
     * Execute the actual command for this node type.
     * This method should be overridden by specific node implementations if needed.
     */
    private void executeNodeCommand(CompletableFuture<Void> future) {
        switch (type) {
            case START:
                // START node doesn't execute any command, just passes through
                future.complete(null);
                break;
            case EVENT_FUNCTION:
                future.complete(null);
                break;
            case EVENT_CALL:
                future.complete(null);
                break;
            case SET_VARIABLE:
                executeSetVariableCommand(future);
                break;
            case CHANGE_VARIABLE:
                executeChangeVariableCommand(future);
                break;
            // Generalized nodes
            case GOTO:
                executeGotoCommand(future);
                break;
            case TRAVEL:
                executeTravelCommand(future);
                break;
            case GOAL:
                executeGoalCommand(future);
                break;
            case COLLECT:
                executeCollectCommand(future);
                break;
            case BUILD:
                executeBuildCommand(future);
                break;
            case EXPLORE:
                executeExploreCommand(future);
                break;
            case FOLLOW:
                executeFollowCommand(future);
                break;
            case CONTROL_REPEAT:
                executeControlRepeat(future);
                break;
            case CONTROL_REPEAT_UNTIL:
                executeControlRepeatUntil(future);
                break;
            case CONTROL_WAIT_UNTIL:
                executeControlWaitUntil(future);
                break;
            case CONTROL_FOREVER:
                executeControlForever(future);
                break;
            case CONTROL_IF:
                executeControlIf(future);
                break;
            case CONTROL_IF_ELSE:
                executeControlIfElse(future);
                break;
            case CONTROL_FORK:
                executeControlFork(future);
                break;
            case CONTROL_JOIN_ANY:
                executeControlJoinAny(future);
                break;
            case CONTROL_JOIN_ALL:
                executeControlJoinAll(future);
                break;
            case FARM:
                executeFarmCommand(future);
                break;
            case STOP:
                executeStopCommand(future);
                break;
            case START_CHAIN:
                executeStartChainNode(future);
                break;
            case RUN_PRESET:
                executeRunPresetNode(future);
                break;
            case CUSTOM_NODE:
            case TEMPLATE:
                executeRunPresetNode(future);
                break;
            case STOP_CHAIN:
                executeStopChainNode(future);
                break;
            case STOP_ALL:
                executeStopAllNode(future);
                break;
            case PLACE:
                executePlaceCommand(future);
                break;
            case CRAFT:
                executeCraftCommand(future);
                break;
            case OPEN_INVENTORY:
                executePlayerGuiCommand(future, NodeMode.PLAYER_GUI_OPEN);
                break;
            case CLOSE_GUI:
                executePlayerGuiCommand(future, NodeMode.PLAYER_GUI_CLOSE);
                break;
            case WRITE_BOOK:
                executeWriteBookCommand(future);
                break;
            case WRITE_SIGN:
                executeWriteSignCommand(future);
                break;
            case UI_UTILS:
                executeUiUtilsCommand(future);
                break;
            case WAIT:
                executeWaitCommand(future);
                break;
            case MESSAGE:
                executeMessageCommand(future);
                break;
            case HOTBAR:
                executeHotbarCommand(future);
                break;
            case DROP_ITEM:
                executeDropItemCommand(future);
                break;
            case DROP_SLOT:
                executeDropSlotCommand(future);
                break;
            case CLICK_SLOT:
                executeClickSlotCommand(future);
                break;
            case CLICK_SCREEN:
                executeClickScreenCommand(future);
                break;
            case MOVE_ITEM:
                executeMoveItemCommand(future);
                break;
            case USE:
                executeUseCommand(future);
                break;
            case BREAK:
                executeBreakCommand(future);
                break;
            case PLACE_HAND:
                executePlaceHandCommand(future);
                break;
            case LOOK:
                executeLookCommand(future);
                break;
            case WALK:
                executeWalkCommand(future);
                break;
            case JUMP:
                executeJumpCommand(future);
                break;
            case PRESS_KEY:
                executePressKeyCommand(future);
                break;
            case CRAWL:
                executeCrawlCommand(future);
                break;
            case CROUCH:
                executeCrouchCommand(future);
                break;
            case SPRINT:
                executeSprintCommand(future);
                break;
            case FLY:
                executeFlyCommand(future);
                break;
            case INTERACT:
                executeInteractCommand(future);
                break;
            case TRADE:
                executeTradeCommand(future);
                break;
            case SWING:
                executeSwingCommand(future);
                break;
            case EQUIP_ARMOR:
                executeEquipArmorCommand(future);
                break;
            case EQUIP_HAND:
                executeEquipHandCommand(future);
                break;
            case SENSOR_TOUCHING_BLOCK:
            case SENSOR_TOUCHING_ENTITY:
            case SENSOR_AT_COORDINATES:
            case SENSOR_IS_DAYTIME:
            case SENSOR_IS_RAINING:
            case SENSOR_HEALTH_BELOW:
            case SENSOR_HUNGER_BELOW:
            case SENSOR_ITEM_IN_INVENTORY:
            case SENSOR_ITEM_IN_SLOT:
            case SENSOR_VILLAGER_TRADE:
            case SENSOR_IN_STOCK:
            case SENSOR_IS_SWIMMING:
            case SENSOR_IS_IN_LAVA:
            case SENSOR_IS_UNDERWATER:
            case SENSOR_IS_FALLING:
            case SENSOR_IS_RENDERED:
            case SENSOR_IS_VISIBLE:
            case SENSOR_KEY_PRESSED:
            case SENSOR_CHAT_MESSAGE:
            case SENSOR_JOINED_SERVER:
            case SENSOR_FABRIC_EVENT:
            case SENSOR_ATTRIBUTE_DETECTION:
            case SENSOR_TARGETED_BLOCK:
            case SENSOR_TARGETED_ENTITY:
            case SENSOR_LOOK_DIRECTION:
            case SENSOR_CURRENT_HAND:
            case SENSOR_TARGETED_BLOCK_FACE:
                completeSensorEvaluation(future);
                break;
            case CREATE_LIST:
                executeCreateListCommand(future);
                break;
            case ADD_TO_LIST:
                executeAddToListCommand(future);
                break;
            case REMOVE_FIRST_FROM_LIST:
                executeRemoveFromListCommand(future, RemoveListMode.FIRST);
                break;
            case REMOVE_LAST_FROM_LIST:
                executeRemoveFromListCommand(future, RemoveListMode.LAST);
                break;
            case REMOVE_LIST_ITEM:
                executeRemoveFromListCommand(future, RemoveListMode.INDEX);
                break;
            case REMOVE_FROM_LIST:
                executeRemoveFromListCommand(future, RemoveListMode.VALUE);
                break;
            // Legacy nodes
            case PATH:
                executePathCommand(future);
                break;
            case INVERT:
                executeInvertCommand(future);
                break;
            case COME:
                executeComeCommand(future);
                break;
            case SURFACE:
                executeSurfaceCommand(future);
                break;
            case TUNNEL:
                executeTunnelCommand(future);
                break;
            default:
                future.complete(null);
                break;
        }
    }

    private static Method resolveClientWorldGetEntityByUuid() {
        try {
            Method method =
                net.minecraft.client.world.ClientWorld.class.getMethod(
                    "getEntity",
                    java.util.UUID.class
                );
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private NodeCollectCommandExecutor collectCommandExecutor() {
        return new NodeCollectCommandExecutor(this);
    }

    private void executeCollectCommand(CompletableFuture<Void> future) {
        collectCommandExecutor().executeCollectCommand(future);
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

            Object pathingBehavior = BaritoneApiProxy.getPathingBehavior(
                baritone
            );
            if (pathingBehavior != null) {
                if (
                    BaritoneApiProxy.isPathing(pathingBehavior) ||
                    BaritoneApiProxy.hasPath(pathingBehavior)
                ) {
                }
                BaritoneApiProxy.forceCancel(pathingBehavior);
            }

            Object goalProcess = BaritoneApiProxy.getCustomGoalProcess(
                baritone
            );
            if (goalProcess != null) {
                BaritoneApiProxy.setGoal(goalProcess, null);
                if (BaritoneApiProxy.isProcessActive(goalProcess)) {
                    BaritoneApiProxy.onLostControl(goalProcess);
                }
            }

            Object getToBlockProcess = BaritoneApiProxy.getGetToBlockProcess(
                baritone
            );
            if (
                getToBlockProcess != null &&
                BaritoneApiProxy.isProcessActive(getToBlockProcess)
            ) {
                BaritoneApiProxy.onLostControl(getToBlockProcess);
            }

            Object exploreProcess = BaritoneApiProxy.getExploreProcess(
                baritone
            );
            if (
                exploreProcess != null &&
                BaritoneApiProxy.isProcessActive(exploreProcess)
            ) {
                BaritoneApiProxy.onLostControl(exploreProcess);
            }

            Object farmProcess = BaritoneApiProxy.getFarmProcess(baritone);
            if (
                farmProcess != null &&
                BaritoneApiProxy.isProcessActive(farmProcess)
            ) {
                BaritoneApiProxy.onLostControl(farmProcess);
            }
        } catch (Exception e) {}
    }

    void resetBaritonePathing(Object baritone) {
        if (baritone == null) {
            return;
        }
        resetBaritonePathing(
            baritone,
            BaritoneApiProxy.getMineProcess(baritone)
        );
    }

    private NodeCraftCommandExecutor craftCommandExecutor() {
        return new NodeCraftCommandExecutor(this);
    }

    private void executeCraftCommand(CompletableFuture<Void> future) {
        craftCommandExecutor().executeCraftCommand(future);
    }

    boolean isCraftingScreenAvailable(
        net.minecraft.client.MinecraftClient client,
        NodeMode craftMode
    ) {
        return craftCommandExecutor().isCraftingScreenAvailable(
            client,
            craftMode
        );
    }

    boolean isCompatibleCraftingHandler(
        ScreenHandler handler,
        NodeMode craftMode
    ) {
        return craftCommandExecutor().isCompatibleCraftingHandler(
            handler,
            craftMode
        );
    }

    int getRequestedCraftQuantity() {
        return craftCommandExecutor().getRequestedCraftQuantity();
    }

    boolean displayFitsPlayerGrid(Object display, Object registryManager) {
        return craftCommandExecutor().displayFitsPlayerGrid(
            display,
            registryManager
        );
    }

    void cacheRecipeForMode(
        CachedRecipeBook book,
        Item targetItem,
        CraftingRecipe recipe,
        int outputCount,
        NodeMode mode,
        Object registryManager
    ) {
        craftCommandExecutor().cacheRecipeForMode(
            book,
            targetItem,
            recipe,
            outputCount,
            mode,
            registryManager
        );
    }

    void cacheDisplayForMode(
        CachedRecipeBook book,
        Item targetItem,
        int outputCount,
        Object display,
        NodeMode mode,
        Object registryManager
    ) {
        craftCommandExecutor().cacheDisplayForMode(
            book,
            targetItem,
            outputCount,
            display,
            mode,
            registryManager
        );
    }

    List<java.lang.reflect.Method> getAllMethods(Class<?> type) {
        return craftCommandExecutor().getAllMethods(type);
    }

    List<RecipeEntry<?>> getCraftingRecipeEntries(Object manager) {
        return craftCommandExecutor().getCraftingRecipeEntries(manager);
    }

    ItemStack getRecipeOutput(CraftingRecipe recipe, Object registryManager) {
        return craftCommandExecutor().getRecipeOutput(recipe, registryManager);
    }

    ItemStack getDisplayOutput(Object display, Object registryManager) {
        return craftCommandExecutor().getDisplayOutput(
            display,
            registryManager
        );
    }

    boolean recipeFitsPlayerGrid(CraftingRecipe recipe) {
        return craftCommandExecutor().recipeFitsPlayerGrid(recipe);
    }

    int mapPlayerInventorySlot(ScreenHandler handler, int inventorySlot) {
        return craftCommandExecutor().mapPlayerInventorySlot(
            handler,
            inventorySlot
        );
    }

    private NodeWorldActionCommandExecutor worldActionCommandExecutor() {
        return new NodeWorldActionCommandExecutor(this);
    }

    private NodeMovementCommandExecutor movementCommandExecutor() {
        return new NodeMovementCommandExecutor(this);
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

    private enum RemoveListMode {
        FIRST,
        LAST,
        INDEX,
        VALUE,
    }

    private void executeSetVariableCommand(CompletableFuture<Void> future) {
        variableListCommandExecutor().executeSetVariableCommand(future);
    }

    private void executeChangeVariableCommand(CompletableFuture<Void> future) {
        variableListCommandExecutor().executeChangeVariableCommand(future);
    }

    private void executeAddToListCommand(CompletableFuture<Void> future) {
        variableListCommandExecutor().executeAddToListCommand(future);
    }

    private void executeRemoveFromListCommand(
        CompletableFuture<Void> future,
        RemoveListMode mode
    ) {
        variableListCommandExecutor().executeRemoveFromListCommand(
            future,
            NodeVariableListCommandExecutor.RemoveListMode.valueOf(mode.name())
        );
    }

    private void executeCreateListCommand(CompletableFuture<Void> future) {
        variableListCommandExecutor().executeCreateListCommand(future);
    }

    Node resolveListItemValueNode(
        Node listNode,
        CompletableFuture<Void> future,
        boolean reportErrors,
        RuntimeParameterData data
    ) {
        return variableListCommandExecutor().resolveListItemValueNode(
            listNode,
            future,
            reportErrors,
            data
        );
    }

    private void executeLookCommand(CompletableFuture<Void> future) {
        movementCommandExecutor().executeLookCommand(future);
    }

    private void executeWalkCommand(CompletableFuture<Void> future) {
        movementCommandExecutor().executeWalkCommand(future);
    }

    private void executeJumpCommand(CompletableFuture<Void> future) {
        movementCommandExecutor().executeJumpCommand(future);
    }

    private void executePressKeyCommand(CompletableFuture<Void> future) {
        movementCommandExecutor().executePressKeyCommand(future);
    }

    private void executeCrouchCommand(CompletableFuture<Void> future) {
        movementCommandExecutor().executeCrouchCommand(future);
    }

    private void executeCrawlCommand(CompletableFuture<Void> future) {
        movementCommandExecutor().executeCrawlCommand(future);
    }

    private void executeSprintCommand(CompletableFuture<Void> future) {
        movementCommandExecutor().executeSprintCommand(future);
    }

    private void executeFlyCommand(CompletableFuture<Void> future) {
        movementCommandExecutor().executeFlyCommand(future);
    }

    private void executeInteractCommand(CompletableFuture<Void> future) {
        entityActionCommandExecutor().executeInteractCommand(future);
    }

    private void executeBreakCommand(CompletableFuture<Void> future) {
        entityActionCommandExecutor().executeBreakCommand(future);
    }

    private void executeTradeCommand(CompletableFuture<Void> future) {
        entityActionCommandExecutor().executeTradeCommand(future);
    }

    private void executeSwingCommand(CompletableFuture<Void> future) {
        entityActionCommandExecutor().executeSwingCommand(future);
    }

    private void executeEquipArmorCommand(CompletableFuture<Void> future) {
        entityActionCommandExecutor().executeEquipArmorCommand(future);
    }

    private void executeEquipHandCommand(CompletableFuture<Void> future) {
        entityActionCommandExecutor().executeEquipHandCommand(future);
    }

    boolean canAffordTrade(
        net.minecraft.entity.player.PlayerEntity player,
        net.minecraft.screen.MerchantScreenHandler screenHandler,
        net.minecraft.village.TradeOffer offer
    ) {
        return entityActionCommandExecutor().canAffordTrade(
            player,
            screenHandler,
            offer
        );
    }

    static int getRequiredFirstBuyCountForTests(
        net.minecraft.village.TradeOffer offer
    ) {
        return NodeEntityActionCommandExecutor.getRequiredFirstBuyCountForTests(
            offer
        );
    }

    static int getRequiredSecondBuyCountForTests(
        net.minecraft.village.TradeOffer offer
    ) {
        return NodeEntityActionCommandExecutor.getRequiredSecondBuyCountForTests(
            offer
        );
    }

    static int resolveRequiredTradeCountForTests(
        int displayedCount,
        int originalCount
    ) {
        return NodeEntityActionCommandExecutor.resolveRequiredTradeCountForTests(
            displayedCount,
            originalCount
        );
    }

    static boolean isCreateListCollectionTarget(NodeType parameterType) {
        return NodeVariableListCommandExecutor.isCreateListCollectionTarget(
            parameterType
        );
    }

    private static Method resolveDoAttackMethod() {
        return NodeEntityActionCommandExecutor.resolveDoAttackMethod();
    }

    private static Method resolveSyncSelectedSlotMethod() {
        return NodeEntityActionCommandExecutor.resolveSyncSelectedSlotMethod();
    }

    static void syncSelectedHotbarSlot(MinecraftClient client) {
        NodeEntityActionCommandExecutor.syncSelectedHotbarSlot(client);
    }

    static void performMainHandAttack(MinecraftClient client) {
        NodeEntityActionCommandExecutor.performMainHandAttack(client);
    }

    private void executeUseCommand(CompletableFuture<Void> future) {
        worldActionCommandExecutor().executeUseCommand(future);
    }

    private void executePlaceHandCommand(CompletableFuture<Void> future) {
        worldActionCommandExecutor().executePlaceHandCommand(future);
    }

    private void executePlaceCommand(CompletableFuture<Void> future) {
        worldActionCommandExecutor().executePlaceCommand(future);
    }

    private void executeBuildCommand(CompletableFuture<Void> future) {
        worldActionCommandExecutor().executeBuildCommand(future);
    }

    private void executeExploreCommand(CompletableFuture<Void> future) {
        worldActionCommandExecutor().executeExploreCommand(future);
    }

    private void executeFollowCommand(CompletableFuture<Void> future) {
        worldActionCommandExecutor().executeFollowCommand(future);
    }

    boolean parameterProvidesCoordinates(Node parameterNode) {
        return worldActionCommandExecutor().parameterProvidesCoordinates(
            parameterNode
        );
    }

    boolean parameterProvidesCoordinates(NodeType parameterType) {
        return worldActionCommandExecutor().parameterProvidesCoordinates(
            parameterType
        );
    }

    boolean blockParameterProvidesPlacementCoordinates(Node parameterNode) {
        return worldActionCommandExecutor().blockParameterProvidesPlacementCoordinates(
            parameterNode
        );
    }

    boolean ensureStackSelectedInMainHand(
        net.minecraft.client.MinecraftClient client,
        PlayerInventory inventory,
        int slotIndex,
        ItemStack stack
    ) {
        return worldActionCommandExecutor().ensureStackSelectedInMainHand(
            client,
            inventory,
            slotIndex,
            stack
        );
    }

    void ensureBlockInHand(
        net.minecraft.client.MinecraftClient client,
        String blockId,
        Hand hand
    ) {
        worldActionCommandExecutor().ensureBlockInHand(client, blockId, hand);
    }

    boolean waitForBlockPlacement(
        net.minecraft.client.MinecraftClient client,
        BlockPos targetPos,
        Block desiredBlock
    ) throws InterruptedException {
        return worldActionCommandExecutor().waitForBlockPlacement(
            client,
            targetPos,
            desiredBlock
        );
    }

    BlockHitResult preparePlacementHitResult(
        net.minecraft.client.MinecraftClient client,
        BlockPos targetPos,
        String blockId,
        Hand hand,
        double reachSquared
    ) {
        return worldActionCommandExecutor().preparePlacementHitResult(
            client,
            targetPos,
            blockId,
            hand,
            reachSquared
        );
    }

    static String formatBlockPos(BlockPos pos) {
        return NodeWorldActionCommandExecutor.formatBlockPos(pos);
    }

    Block resolveBlockForPlacement(String blockId) {
        return worldActionCommandExecutor().resolveBlockForPlacement(blockId);
    }

    double getPlacementReachSquared(
        net.minecraft.client.MinecraftClient client
    ) {
        return worldActionCommandExecutor().getPlacementReachSquared(client);
    }

    boolean isBlockReplaceable(
        net.minecraft.world.World world,
        BlockPos targetPos
    ) {
        return worldActionCommandExecutor().isBlockReplaceable(
            world,
            targetPos
        );
    }

    boolean hasPlacementSupport(
        net.minecraft.world.World world,
        BlockPos targetPos
    ) {
        return worldActionCommandExecutor().hasPlacementSupport(
            world,
            targetPos
        );
    }

    int findHotbarSlotWithItem(PlayerInventory inventory, Item targetItem) {
        return worldActionCommandExecutor().findHotbarSlotWithItem(
            inventory,
            targetItem
        );
    }

    private NodeGuiCommandExecutor guiCommandExecutor() {
        return new NodeGuiCommandExecutor(this);
    }

    private void executeUiUtilsCommand(CompletableFuture<Void> future) {
        guiCommandExecutor().executeUiUtilsCommand(future);
    }

    private void executePlayerGuiCommand(
        CompletableFuture<Void> future,
        NodeMode desiredMode
    ) {
        guiCommandExecutor().executePlayerGuiCommand(future, desiredMode);
    }

    int normalizeCachedRecipeSlotIndex(
        int slotIndex,
        boolean legacyZeroBasedSlots
    ) {
        if (!legacyZeroBasedSlots) {
            return slotIndex;
        }
        return slotIndex + 1;
    }

    static List<Integer> normalizeCachedRecipeSlotIndexesForTests(
        List<Integer> slotIndexes
    ) {
        if (slotIndexes == null || slotIndexes.isEmpty()) {
            return List.of();
        }
        boolean legacyZeroBasedSlots = false;
        for (Integer slotIndex : slotIndexes) {
            if (slotIndex != null && slotIndex.intValue() == 0) {
                legacyZeroBasedSlots = true;
                break;
            }
        }
        List<Integer> normalized = new ArrayList<>(slotIndexes.size());
        for (Integer slotIndex : slotIndexes) {
            if (slotIndex == null) {
                continue;
            }
            normalized.add(
                legacyZeroBasedSlots
                    ? slotIndex.intValue() + 1
                    : slotIndex.intValue()
            );
        }
        return normalized;
    }

    static List<Integer> planIngredientSourceSlotsForTests(
        List<TestIngredientStack> inventoryStacks,
        List<String> ingredientKeys
    ) {
        List<NodeCraftCommandExecutor.TestIngredientStack> craftStacks =
            new ArrayList<>();
        if (inventoryStacks != null) {
            for (TestIngredientStack stack : inventoryStacks) {
                craftStacks.add(
                    stack == null
                        ? new NodeCraftCommandExecutor.TestIngredientStack(
                              "",
                              0
                          )
                        : new NodeCraftCommandExecutor.TestIngredientStack(
                              stack.key(),
                              stack.count()
                          )
                );
            }
        }
        return NodeCraftCommandExecutor.planIngredientSourceSlotsForTests(
            craftStacks,
            ingredientKeys
        );
    }

    static record TestIngredientStack(String key, int count) {}

    String getCachedRecipeSignature(CachedRecipe recipe) {
        if (recipe == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        if (recipe.grid != null) {
            for (CachedGridIngredient ingredient : recipe.grid) {
                if (ingredient == null) {
                    continue;
                }
                List<String> ids =
                    ingredient.itemIds != null
                        ? new ArrayList<>(ingredient.itemIds)
                        : List.of();
                parts.add(ingredient.slotIndex + "=" + String.join("|", ids));
            }
        }
        Collections.sort(parts);
        return (
            recipe.mode +
            ":" +
            recipe.outputCount +
            ":" +
            String.join(",", parts)
        );
    }

    List<ItemStack> resolveIngredientStacksByTesting(Ingredient ingredient) {
        List<ItemStack> stacks = new ArrayList<>();
        if (ingredient == null) {
            return stacks;
        }
        for (Item item : Registries.ITEM) {
            if (item == null || item == Items.AIR) {
                continue;
            }
            ItemStack stack = new ItemStack(item);
            try {
                if (ingredient.test(stack)) {
                    stacks.add(stack);
                }
            } catch (RuntimeException ignored) {
                // Skip items that trip custom ingredient checks.
            }
        }
        return stacks;
    }

    Ingredient buildIngredientFromItemIds(List<String> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return null;
        }
        List<Item> items = new ArrayList<>();
        for (String idString : itemIds) {
            Identifier id = Identifier.tryParse(idString);
            if (id == null || !Registries.ITEM.containsId(id)) {
                continue;
            }
            items.add(Registries.ITEM.get(id));
        }
        if (items.isEmpty()) {
            return null;
        }
        return Ingredient.ofItems(items.toArray(new Item[0]));
    }

    CachedRecipeBook loadRecipeCache(
        net.minecraft.client.MinecraftClient client
    ) {
        synchronized (RECIPE_CACHE_LOCK) {
            if (cachedRecipeBook != null) {
                return cachedRecipeBook;
            }
            Path path = getRecipeCachePath(client);
            if (path == null || !Files.exists(path)) {
                cachedRecipeBook = new CachedRecipeBook();
                return cachedRecipeBook;
            }
            try {
                String json = Files.readString(path, StandardCharsets.UTF_8);
                CachedRecipeBook loaded = RECIPE_CACHE_GSON.fromJson(
                    json,
                    CachedRecipeBook.class
                );
                if (
                    loaded == null ||
                    loaded.schemaVersion != RECIPE_CACHE_VERSION
                ) {
                    cachedRecipeBook = new CachedRecipeBook();
                    return cachedRecipeBook;
                }
                if (loaded.recipesByOutput == null) {
                    loaded.recipesByOutput = new HashMap<>();
                }
                cachedRecipeBook = loaded;
                return cachedRecipeBook;
            } catch (Exception e) {
                cachedRecipeBook = new CachedRecipeBook();
                return cachedRecipeBook;
            }
        }
    }

    void saveRecipeCache(
        net.minecraft.client.MinecraftClient client,
        CachedRecipeBook book
    ) {
        if (client == null || book == null) {
            return;
        }
        synchronized (RECIPE_CACHE_LOCK) {
            Path path = getRecipeCachePath(client);
            if (path == null) {
                return;
            }
            try {
                boolean existed = Files.exists(path);
                Path parent = path.getParent();
                if (parent != null && !Files.exists(parent)) {
                    Files.createDirectories(parent);
                }
                book.schemaVersion = RECIPE_CACHE_VERSION;
                try {
                    book.gameVersion = client.getGameVersion();
                } catch (RuntimeException ignored) {
                    book.gameVersion = null;
                }
                String json = RECIPE_CACHE_GSON.toJson(book);
                Files.writeString(path, json, StandardCharsets.UTF_8);
                if (!existed) {
                    LOGGER.debug(
                        "Pathmind recipe cache created at {}",
                        path.toAbsolutePath()
                    );
                }
            } catch (Exception ignored) {}
        }
    }

    private static Path getRecipeCachePath(
        net.minecraft.client.MinecraftClient client
    ) {
        Path base = getPathmindDirectory(client);
        if (base == null) {
            return null;
        }
        return base.resolve(RECIPE_CACHE_FILE_NAME);
    }

    private static Path getPathmindDirectory(
        net.minecraft.client.MinecraftClient client
    ) {
        Path minecraftDirectory = null;
        if (client != null && client.runDirectory != null) {
            minecraftDirectory = client.runDirectory.toPath();
        } else {
            FabricLoader loader = FabricLoader.getInstance();
            if (loader != null) {
                minecraftDirectory = loader.getGameDir();
            }
        }
        if (minecraftDirectory == null) {
            minecraftDirectory = Paths.get(
                System.getProperty("user.home"),
                ".minecraft"
            );
        }
        return minecraftDirectory.resolve("pathmind");
    }

    List<RecipeEntry<?>> getRecipeEntries(Object manager) {
        List<RecipeEntry<?>> entries = new ArrayList<>();
        if (manager == null) {
            return entries;
        }

        List<String> preferredNames = List.of(
            "values",
            "getRecipes",
            "getAllRecipes",
            "getAll"
        );
        for (String name : preferredNames) {
            try {
                java.lang.reflect.Method method = manager
                    .getClass()
                    .getMethod(name);
                method.setAccessible(true);
                Object result = method.invoke(manager);
                if (collectRecipeEntries(result, entries)) {
                    return entries;
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next candidate.
            }
        }

        for (java.lang.reflect.Method method : manager
            .getClass()
            .getMethods()) {
            if (method.getParameterCount() != 0) {
                continue;
            }
            Class<?> returnType = method.getReturnType();
            if (
                !Iterable.class.isAssignableFrom(returnType) &&
                !java.util.Map.class.isAssignableFrom(returnType)
            ) {
                continue;
            }
            try {
                method.setAccessible(true);
                Object result = method.invoke(manager);
                if (collectRecipeEntries(result, entries)) {
                    return entries;
                }
            } catch (ReflectiveOperationException ignored) {
                // Keep scanning.
            }
        }

        if (entries.isEmpty()) {
            collectRecipeEntriesFromFields(
                manager,
                entries,
                0,
                new java.util.IdentityHashMap<>()
            );
        }
        return entries;
    }

    boolean collectRecipeEntries(Object result, List<RecipeEntry<?>> entries) {
        if (result == null || entries == null) {
            return false;
        }
        if (result instanceof RecipeEntry<?> entry) {
            entries.add(entry);
            return true;
        }
        if (result instanceof Iterable<?> iterable) {
            boolean added = false;
            for (Object item : iterable) {
                if (item instanceof RecipeEntry<?> recipeEntry) {
                    entries.add(recipeEntry);
                    added = true;
                } else if (item instanceof java.util.Map<?, ?> map) {
                    if (collectRecipeEntries(map.values(), entries)) {
                        added = true;
                    }
                } else if (item instanceof Iterable<?> nested) {
                    if (collectRecipeEntries(nested, entries)) {
                        added = true;
                    }
                }
            }
            return added;
        }
        if (result instanceof java.util.Iterator<?> iterator) {
            boolean added = false;
            while (iterator.hasNext()) {
                Object item = iterator.next();
                if (item instanceof RecipeEntry<?> recipeEntry) {
                    entries.add(recipeEntry);
                    added = true;
                } else if (item instanceof java.util.Map<?, ?> map) {
                    if (collectRecipeEntries(map.values(), entries)) {
                        added = true;
                    }
                } else if (item instanceof Iterable<?> nested) {
                    if (collectRecipeEntries(nested, entries)) {
                        added = true;
                    }
                }
            }
            return added;
        }
        if (result instanceof java.util.Map<?, ?> map) {
            return collectRecipeEntries(map.values(), entries);
        }
        // Handle container-like objects with values() or iterator() accessors.
        try {
            java.lang.reflect.Method valuesMethod = result
                .getClass()
                .getMethod("values");
            if (valuesMethod.getParameterCount() == 0) {
                valuesMethod.setAccessible(true);
                Object values = valuesMethod.invoke(result);
                if (collectRecipeEntries(values, entries)) {
                    return true;
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // Ignore missing values() method.
        }
        try {
            java.lang.reflect.Method iteratorMethod = result
                .getClass()
                .getMethod("iterator");
            if (iteratorMethod.getParameterCount() == 0) {
                iteratorMethod.setAccessible(true);
                Object iter = iteratorMethod.invoke(result);
                if (collectRecipeEntries(iter, entries)) {
                    return true;
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // Ignore missing iterator() method.
        }
        return false;
    }

    void collectRecipeEntriesFromFields(
        Object manager,
        List<RecipeEntry<?>> entries,
        int depth,
        java.util.IdentityHashMap<Object, Boolean> seen
    ) {
        if (manager == null || entries == null) {
            return;
        }
        if (isJdkType(manager.getClass())) {
            return;
        }
        if (depth > 3) {
            return;
        }
        if (seen.put(manager, Boolean.TRUE) != null) {
            return;
        }
        for (java.lang.reflect.Field field : getAllFields(manager.getClass())) {
            try {
                Object accessTarget = java.lang.reflect.Modifier.isStatic(
                    field.getModifiers()
                )
                    ? null
                    : manager;
                if (!field.canAccess(accessTarget)) {
                    try {
                        field.setAccessible(true);
                    } catch (RuntimeException ignored) {
                        continue;
                    }
                }
                Object value = field.get(accessTarget);
                if (value == null || seen.containsKey(value)) {
                    continue;
                }
                if (isJdkType(value.getClass())) {
                    continue;
                }
                if (collectRecipeEntries(value, entries)) {
                    continue;
                }
                // Dive into nested containers (common in recipe managers).
                if (value instanceof java.util.Map<?, ?> map) {
                    Object crafting = map.get(RecipeType.CRAFTING);
                    if (collectRecipeEntries(crafting, entries)) {
                        continue;
                    }
                    for (Object nested : map.values()) {
                        collectRecipeEntriesFromFields(
                            nested,
                            entries,
                            depth + 1,
                            seen
                        );
                    }
                } else if (value instanceof Iterable<?> iterable) {
                    for (Object nested : iterable) {
                        collectRecipeEntriesFromFields(
                            nested,
                            entries,
                            depth + 1,
                            seen
                        );
                    }
                } else {
                    collectRecipeEntriesFromFields(
                        value,
                        entries,
                        depth + 1,
                        seen
                    );
                }
            } catch (IllegalAccessException ignored) {
                // Skip inaccessible fields.
            }
        }
    }

    private List<java.lang.reflect.Field> getAllFields(Class<?> type) {
        List<java.lang.reflect.Field> fields = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            if (isJdkType(current)) {
                break;
            }
            for (java.lang.reflect.Field field : current.getDeclaredFields()) {
                fields.add(field);
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    private boolean isJdkType(Class<?> type) {
        if (type == null) {
            return true;
        }
        Package pkg = type.getPackage();
        String name = pkg != null ? pkg.getName() : "";
        return (
            name.startsWith("java.") ||
            name.startsWith("javax.") ||
            name.startsWith("jdk.") ||
            name.startsWith("sun.") ||
            name.startsWith("com.sun.")
        );
    }

    List<Object> getRecipeManagers(
        net.minecraft.client.MinecraftClient client
    ) {
        List<Object> managers = new ArrayList<>();
        if (client == null) {
            return managers;
        }
        MinecraftServer server = client.getServer();
        if (server != null) {
            RecipeManager manager = server.getRecipeManager();
            if (manager != null && !managers.contains(manager)) {
                managers.add(manager);
            }
        }
        if (client.getNetworkHandler() != null) {
            try {
                java.lang.reflect.Method method = client
                    .getNetworkHandler()
                    .getClass()
                    .getMethod("getRecipeManager");
                method.setAccessible(true);
                Object result = method.invoke(client.getNetworkHandler());
                if (result != null && !managers.contains(result)) {
                    managers.add(result);
                }
            } catch (ReflectiveOperationException ignored) {
                // Ignore network handlers without recipe managers.
            }
        }
        if (client.world != null) {
            try {
                RecipeManager manager = client.world.getRecipeManager();
                if (manager != null && !managers.contains(manager)) {
                    managers.add(manager);
                }
            } catch (RuntimeException ignored) {
                // Ignore client worlds without a recipe manager.
            }
        }
        return managers;
    }

    static class CraftingSummary {

        final int produced;
        final String failureMessage;

        CraftingSummary(int produced, String failureMessage) {
            this.produced = produced;
            this.failureMessage = failureMessage;
        }
    }

    static class CachedRecipeBook {

        int schemaVersion = RECIPE_CACHE_VERSION;
        String gameVersion;
        Map<String, List<CachedRecipe>> recipesByOutput = new HashMap<>();
    }

    static class CachedRecipe {

        String mode;
        int outputCount;
        List<CachedGridIngredient> grid = new ArrayList<>();
    }

    static class CachedGridIngredient {

        int slotIndex;
        List<String> itemIds = new ArrayList<>();
    }

    private static class RecipeCacheWarmupState {

        private final Path cachePath;
        private final CachedRecipeBook book;
        private final Object registryManager;
        private final Object serverRegistryManager;
        private final List<RecipeEntry<?>> craftingEntries;
        private final List<RecipeResultCollection> recipeCollections;
        private final int totalDisplayEntries;
        private int recipeIndex;
        private int collectionIndex;
        private int displayIndex;
        private boolean dirty;
        private int unsavedChanges;

        RecipeCacheWarmupState(
            Path cachePath,
            CachedRecipeBook book,
            Object registryManager,
            Object serverRegistryManager,
            List<RecipeEntry<?>> craftingEntries,
            List<RecipeResultCollection> recipeCollections,
            int totalDisplayEntries
        ) {
            this.cachePath = cachePath;
            this.book = book;
            this.registryManager = registryManager;
            this.serverRegistryManager = serverRegistryManager;
            this.craftingEntries =
                craftingEntries != null ? craftingEntries : List.of();
            this.recipeCollections =
                recipeCollections != null ? recipeCollections : List.of();
            this.totalDisplayEntries = Math.max(0, totalDisplayEntries);
        }

        boolean matches(net.minecraft.client.MinecraftClient client) {
            return Objects.equals(cachePath, getRecipeCachePath(client));
        }

        int getCompletedUnits() {
            return Math.min(
                getTotalUnits(),
                recipeIndex + getCompletedDisplayEntries()
            );
        }

        int getTotalUnits() {
            return craftingEntries.size() + totalDisplayEntries;
        }

        private int getCompletedDisplayEntries() {
            int completed = 0;
            for (
                int i = 0;
                i < collectionIndex && i < recipeCollections.size();
                i++
            ) {
                RecipeResultCollection collection = recipeCollections.get(i);
                List<?> entries =
                    collection != null
                        ? RecipeCompatibilityBridge.getAllRecipesFromCollection(
                              collection
                          )
                        : null;
                completed += entries != null ? entries.size() : 0;
            }
            completed += displayIndex;
            return Math.min(completed, totalDisplayEntries);
        }
    }

    public record RecipeCacheWarmupProgress(int completed, int total) {
        public float fraction() {
            if (total <= 0) {
                return 0.0f;
            }
            return Math.max(0.0f, Math.min(1.0f, completed / (float) total));
        }
    }

    public static boolean warmRecipeCache(
        net.minecraft.client.MinecraftClient client
    ) {
        if (client == null || client.getServer() == null) {
            return false;
        }
        Node node = new Node(NodeType.CRAFT, 0, 0);
        return node.warmRecipeCacheInternal(client);
    }

    public static boolean hasUsableRecipeCache(
        net.minecraft.client.MinecraftClient client
    ) {
        if (client == null) {
            return false;
        }
        Node node = new Node(NodeType.CRAFT, 0, 0);
        return node.hasUsableRecipeCacheInternal(client);
    }

    public static void resetRecipeCacheWarmup() {
        synchronized (RECIPE_CACHE_LOCK) {
            cachedRecipeBook = null;
            recipeCacheWarmupState = null;
        }
    }

    public static boolean clearRecipeCache(
        net.minecraft.client.MinecraftClient client
    ) {
        synchronized (RECIPE_CACHE_LOCK) {
            cachedRecipeBook = null;
            recipeCacheWarmupState = null;

            Path path = getRecipeCachePath(client);
            if (path == null) {
                return false;
            }

            try {
                return Files.deleteIfExists(path);
            } catch (IOException e) {
                LOGGER.warn(
                    "Failed to clear Pathmind cache file at {}",
                    path.toAbsolutePath(),
                    e
                );
                return false;
            }
        }
    }

    public static boolean isRecipeCacheWarmupInProgress(
        net.minecraft.client.MinecraftClient client
    ) {
        RecipeCacheWarmupState state = recipeCacheWarmupState;
        return state != null && state.matches(client);
    }

    public static RecipeCacheWarmupProgress getRecipeCacheWarmupProgress(
        net.minecraft.client.MinecraftClient client
    ) {
        RecipeCacheWarmupState state = recipeCacheWarmupState;
        if (state == null || !state.matches(client)) {
            return null;
        }
        int total = state.getTotalUnits();
        if (total <= 0) {
            return null;
        }
        return new RecipeCacheWarmupProgress(state.getCompletedUnits(), total);
    }

    private boolean warmRecipeCacheInternal(
        net.minecraft.client.MinecraftClient client
    ) {
        if (client == null || client.getServer() == null) {
            return false;
        }
        RecipeCacheWarmupState state = recipeCacheWarmupState;
        if (state == null || !state.matches(client)) {
            state = createRecipeCacheWarmupState(client);
            recipeCacheWarmupState = state;
        }
        if (state == null) {
            return false;
        }

        int recipesProcessed = 0;
        while (
            recipesProcessed < RECIPE_WARMUP_RECIPE_BATCH_SIZE &&
            state.recipeIndex < state.craftingEntries.size()
        ) {
            RecipeEntry<?> entry = state.craftingEntries.get(
                state.recipeIndex++
            );
            processWarmupRecipeEntry(state, entry);
            recipesProcessed++;
        }

        int displaysProcessed = 0;
        while (
            displaysProcessed < RECIPE_WARMUP_DISPLAY_BATCH_SIZE &&
            state.collectionIndex < state.recipeCollections.size()
        ) {
            RecipeResultCollection collection = state.recipeCollections.get(
                state.collectionIndex
            );
            List<?> entries =
                collection != null
                    ? RecipeCompatibilityBridge.getAllRecipesFromCollection(
                          collection
                      )
                    : null;
            if (
                entries == null ||
                entries.isEmpty() ||
                state.displayIndex >= entries.size()
            ) {
                state.collectionIndex++;
                state.displayIndex = 0;
                continue;
            }
            Object entry = entries.get(state.displayIndex++);
            processWarmupDisplayEntry(state, entry);
            displaysProcessed++;
        }

        if (
            state.dirty && state.unsavedChanges >= RECIPE_WARMUP_SAVE_INTERVAL
        ) {
            saveRecipeCache(client, state.book);
            state.unsavedChanges = 0;
            state.dirty = false;
        }

        if (
            state.recipeIndex < state.craftingEntries.size() ||
            state.collectionIndex < state.recipeCollections.size()
        ) {
            return false;
        }

        if (state.dirty || state.unsavedChanges > 0) {
            saveRecipeCache(client, state.book);
            state.unsavedChanges = 0;
            state.dirty = false;
        }
        recipeCacheWarmupState = null;
        return (
            state.book.recipesByOutput != null &&
            !state.book.recipesByOutput.isEmpty()
        );
    }

    private boolean hasUsableRecipeCacheInternal(
        net.minecraft.client.MinecraftClient client
    ) {
        Path path = getRecipeCachePath(client);
        if (path == null || !Files.exists(path)) {
            return false;
        }
        CachedRecipeBook book = loadRecipeCache(client);
        return isRecipeCacheUsable(book);
    }

    static boolean isRecipeCacheUsableForTests(
        Map<String, List<Map<String, Object>>> rawRecipesByOutput
    ) {
        CachedRecipeBook book = new CachedRecipeBook();
        book.recipesByOutput = new HashMap<>();
        if (rawRecipesByOutput != null) {
            for (Map.Entry<
                String,
                List<Map<String, Object>>
            > entry : rawRecipesByOutput.entrySet()) {
                List<CachedRecipe> recipes = new ArrayList<>();
                if (entry.getValue() != null) {
                    for (Map<String, Object> rawRecipe : entry.getValue()) {
                        if (rawRecipe == null) {
                            continue;
                        }
                        CachedRecipe recipe = new CachedRecipe();
                        Object mode = rawRecipe.get("mode");
                        if (mode instanceof String modeString) {
                            recipe.mode = modeString;
                        }
                        Object outputCount = rawRecipe.get("outputCount");
                        if (outputCount instanceof Number number) {
                            recipe.outputCount = number.intValue();
                        }
                        Object rawGrid = rawRecipe.get("grid");
                        if (rawGrid instanceof List<?> gridList) {
                            for (Object rawIngredient : gridList) {
                                if (
                                    !(rawIngredient instanceof
                                            Map<?, ?> ingredientMap)
                                ) {
                                    continue;
                                }
                                CachedGridIngredient ingredient =
                                    new CachedGridIngredient();
                                Object slotIndex = ingredientMap.get(
                                    "slotIndex"
                                );
                                if (slotIndex instanceof Number number) {
                                    ingredient.slotIndex = number.intValue();
                                }
                                Object itemIds = ingredientMap.get("itemIds");
                                if (itemIds instanceof List<?> ids) {
                                    for (Object id : ids) {
                                        if (id instanceof String idString) {
                                            ingredient.itemIds.add(idString);
                                        }
                                    }
                                }
                                recipe.grid.add(ingredient);
                            }
                        }
                        recipes.add(recipe);
                    }
                }
                book.recipesByOutput.put(entry.getKey(), recipes);
            }
        }
        return isRecipeCacheUsable(book);
    }

    private static boolean isRecipeCacheUsable(CachedRecipeBook book) {
        if (
            book == null ||
            book.schemaVersion != RECIPE_CACHE_VERSION ||
            book.recipesByOutput == null ||
            book.recipesByOutput.isEmpty()
        ) {
            return false;
        }
        for (Map.Entry<
            String,
            List<CachedRecipe>
        > entry : book.recipesByOutput.entrySet()) {
            if (
                entry == null ||
                entry.getKey() == null ||
                entry.getKey().trim().isEmpty()
            ) {
                continue;
            }
            List<CachedRecipe> recipes = entry.getValue();
            if (recipes == null || recipes.isEmpty()) {
                continue;
            }
            for (CachedRecipe recipe : recipes) {
                if (
                    recipe == null ||
                    recipe.mode == null ||
                    recipe.mode.trim().isEmpty() ||
                    recipe.outputCount <= 0
                ) {
                    continue;
                }
                if (recipe.grid == null || recipe.grid.isEmpty()) {
                    continue;
                }
                boolean hasIngredient = false;
                for (CachedGridIngredient ingredient : recipe.grid) {
                    if (
                        ingredient == null ||
                        ingredient.itemIds == null ||
                        ingredient.itemIds.isEmpty()
                    ) {
                        continue;
                    }
                    boolean hasValidId = false;
                    for (String itemId : ingredient.itemIds) {
                        if (itemId != null && !itemId.trim().isEmpty()) {
                            hasValidId = true;
                            break;
                        }
                    }
                    if (hasValidId) {
                        hasIngredient = true;
                        break;
                    }
                }
                if (hasIngredient) {
                    return true;
                }
            }
        }
        return false;
    }

    private RecipeCacheWarmupState createRecipeCacheWarmupState(
        net.minecraft.client.MinecraftClient client
    ) {
        if (client == null || client.getServer() == null) {
            return null;
        }
        if (hasUsableRecipeCacheInternal(client)) {
            return null;
        }
        RecipeManager manager = client.getServer().getRecipeManager();
        if (manager == null) {
            return null;
        }
        CachedRecipeBook book = loadRecipeCache(client);
        if (book == null) {
            return null;
        }
        List<RecipeEntry<?>> craftingEntries = getCraftingRecipeEntries(
            manager
        );
        Object registryManager = client.world;
        if (registryManager == null) {
            registryManager = client.getServer().getRegistryManager();
        }
        List<RecipeResultCollection> collections = List.of();
        if (
            client.player != null &&
            client.player.getRecipeBook() instanceof
                ClientRecipeBook clientRecipeBook
        ) {
            List<RecipeResultCollection> orderedResults =
                clientRecipeBook.getOrderedResults();
            if (orderedResults != null && !orderedResults.isEmpty()) {
                collections = new ArrayList<>(orderedResults);
            }
        }
        boolean hasExistingCache =
            book.recipesByOutput != null && !book.recipesByOutput.isEmpty();
        if (
            craftingEntries.isEmpty() &&
            collections.isEmpty() &&
            !hasExistingCache
        ) {
            return null;
        }
        int totalDisplayEntries = countRecipeDisplayEntries(collections);
        return new RecipeCacheWarmupState(
            getRecipeCachePath(client),
            book,
            registryManager,
            client.getServer().getRegistryManager(),
            new ArrayList<>(craftingEntries),
            collections,
            totalDisplayEntries
        );
    }

    private int countRecipeDisplayEntries(
        List<RecipeResultCollection> collections
    ) {
        if (collections == null || collections.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (RecipeResultCollection collection : collections) {
            List<?> entries =
                collection != null
                    ? RecipeCompatibilityBridge.getAllRecipesFromCollection(
                          collection
                      )
                    : null;
            if (entries != null) {
                total += entries.size();
            }
        }
        return total;
    }

    private void processWarmupRecipeEntry(
        RecipeCacheWarmupState state,
        RecipeEntry<?> entry
    ) {
        if (
            state == null ||
            entry == null ||
            !(entry.value() instanceof CraftingRecipe craftingRecipe)
        ) {
            return;
        }
        ItemStack output = getRecipeOutput(
            craftingRecipe,
            state.serverRegistryManager
        );
        if (
            (output == null || output.isEmpty()) &&
            state.registryManager != state.serverRegistryManager
        ) {
            output = getRecipeOutput(craftingRecipe, state.registryManager);
        }
        if (output == null || output.isEmpty()) {
            return;
        }
        cacheRecipeForMode(
            state.book,
            output.getItem(),
            craftingRecipe,
            output.getCount(),
            NodeMode.CRAFT_CRAFTING_TABLE,
            state.registryManager
        );
        if (recipeFitsPlayerGrid(craftingRecipe)) {
            cacheRecipeForMode(
                state.book,
                output.getItem(),
                craftingRecipe,
                output.getCount(),
                NodeMode.CRAFT_PLAYER_GUI,
                state.registryManager
            );
        }
        state.dirty = true;
        state.unsavedChanges++;
    }

    private void processWarmupDisplayEntry(
        RecipeCacheWarmupState state,
        Object entry
    ) {
        if (state == null || entry == null) {
            return;
        }
        Object display = RecipeCompatibilityBridge.getDisplayFromEntry(entry);
        if (!RecipeCompatibilityBridge.isCraftingDisplay(display)) {
            return;
        }
        ItemStack output = getDisplayOutput(display, state.registryManager);
        if (output == null || output.isEmpty()) {
            return;
        }
        cacheDisplayForMode(
            state.book,
            output.getItem(),
            output.getCount(),
            display,
            NodeMode.CRAFT_CRAFTING_TABLE,
            state.registryManager
        );
        if (displayFitsPlayerGrid(display, state.registryManager)) {
            cacheDisplayForMode(
                state.book,
                output.getItem(),
                output.getCount(),
                display,
                NodeMode.CRAFT_PLAYER_GUI,
                state.registryManager
            );
        }
        state.dirty = true;
        state.unsavedChanges++;
    }

    static class GridIngredient {

        private final int slotIndex;
        private final Ingredient ingredient;
        private final boolean allowEmpty;

        GridIngredient(
            int slotIndex,
            Ingredient ingredient,
            boolean allowEmpty
        ) {
            this.slotIndex = slotIndex;
            this.ingredient = ingredient;
            this.allowEmpty = allowEmpty;
        }

        int slotIndex() {
            return slotIndex;
        }

        Ingredient ingredient() {
            return ingredient;
        }

        boolean allowEmpty() {
            return allowEmpty;
        }
    }

    static class CraftingAttemptResult {

        final int produced;
        final String errorMessage;

        CraftingAttemptResult(int produced, String errorMessage) {
            this.produced = produced;
            this.errorMessage = errorMessage;
        }
    }

    private void executeWaitCommand(CompletableFuture<Void> future) {
        if (
            preprocessAttachedParameter(
                EnumSet.noneOf(ParameterUsage.class),
                future
            ) == ParameterHandlingResult.COMPLETE
        ) {
            return;
        }
        double baseDuration = Math.max(
            0.0,
            getDoubleParameter("Duration", 1.0)
        );
        Double attachedDurationSeconds =
            runtimeState.runtimeParameterData != null
                ? runtimeState.runtimeParameterData.durationSeconds
                : null;

        final double waitSeconds;
        NodeMode waitMode = mode != null ? mode : NodeMode.WAIT_SECONDS;
        if (attachedDurationSeconds != null) {
            waitSeconds = attachedDurationSeconds;
        } else {
            double unitSeconds;
            switch (waitMode) {
                case WAIT_TICKS:
                    unitSeconds = 0.05;
                    break;
                case WAIT_MINUTES:
                    unitSeconds = 60.0;
                    break;
                case WAIT_HOURS:
                    unitSeconds = 3600.0;
                    break;
                case WAIT_SECONDS:
                default:
                    unitSeconds = 1.0;
                    break;
            }
            waitSeconds = baseDuration * unitSeconds;
        }
        ExecutionManager manager = ExecutionManager.getInstance();
        Integer executionId = manager.getCurrentExecutionId();

        new Thread(() -> {
            try {
                String nodeId = getId();
                long waitMs = (long) (waitSeconds * 1000);

                while (true) {
                    if (shouldAbortForRepeatUntilGuard()) {
                        future.complete(null);
                        return;
                    }
                    if (!manager.isExecutionActiveOnNode(executionId, nodeId)) {
                        future.complete(null);
                        return;
                    }
                    if (
                        manager.getExecutionNodeDuration(executionId) >= waitMs
                    ) {
                        future.complete(null);
                        return;
                    }
                    Thread.sleep(CONTROL_POLL_INTERVAL_MS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.completeExceptionally(e);
            }
        }, "Pathmind-Wait").start();
    }

    private void executeControlRepeat(CompletableFuture<Void> future) {
        if (
            preprocessAttachedParameter(
                EnumSet.noneOf(ParameterUsage.class),
                future
            ) == ParameterHandlingResult.COMPLETE
        ) {
            return;
        }
        int count = Math.max(0, getIntParameter("Count", 1));
        if (!runtimeState.repeatActive) {
            runtimeState.repeatRemainingIterations = count;
            runtimeState.repeatActive = true;
        }
        if (runtimeState.repeatRemainingIterations > 0) {
            runtimeState.repeatRemainingIterations--;
            runtimeState.repeatExecuteAttachedAction = true;
            setNextOutputSocket(0);
        } else {
            runtimeState.repeatRemainingIterations = 0;
            runtimeState.repeatActive = false;
            runtimeState.repeatExecuteAttachedAction = false;
            setNextOutputSocket(0);
        }
        future.complete(null);
    }

    private void executeControlRepeatUntil(CompletableFuture<Void> future) {
        if (
            preprocessAttachedParameter(
                EnumSet.noneOf(ParameterUsage.class),
                future
            ) == ParameterHandlingResult.COMPLETE
        ) {
            return;
        }
        boolean conditionMet = evaluateConditionFromParameters();
        if (conditionMet) {
            runtimeState.repeatRemainingIterations = 0;
            runtimeState.repeatActive = false;
            setNextOutputSocket(1);
        } else {
            runtimeState.repeatActive = true;
            setNextOutputSocket(0);
        }
        future.complete(null);
    }

    private void executeControlWaitUntil(CompletableFuture<Void> future) {
        if (
            preprocessAttachedParameter(
                EnumSet.noneOf(ParameterUsage.class),
                future
            ) == ParameterHandlingResult.COMPLETE
        ) {
            return;
        }
        if (evaluateConditionFromParameters()) {
            setNextOutputSocket(0);
            future.complete(null);
            return;
        }
        ExecutionManager manager = ExecutionManager.getInstance();
        Integer executionId = manager.getCurrentExecutionId();

        new Thread(() -> {
            try {
                String nodeId = getId();
                while (true) {
                    if (shouldAbortForRepeatUntilGuard()) {
                        future.complete(null);
                        return;
                    }
                    if (!manager.isExecutionActiveOnNode(executionId, nodeId)) {
                        future.complete(null);
                        return;
                    }
                    if (evaluateConditionFromParameters()) {
                        setNextOutputSocket(0);
                        future.complete(null);
                        return;
                    }
                    Thread.sleep(CONTROL_POLL_INTERVAL_MS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.completeExceptionally(e);
            }
        }, "Pathmind-Wait-Until").start();
    }

    private void executeControlForever(CompletableFuture<Void> future) {
        if (
            preprocessAttachedParameter(
                EnumSet.noneOf(ParameterUsage.class),
                future
            ) == ParameterHandlingResult.COMPLETE
        ) {
            return;
        }
        runtimeState.repeatActive = true;
        setNextOutputSocket(0);
        future.complete(null);
    }

    private void executeControlIf(CompletableFuture<Void> future) {
        if (
            preprocessAttachedParameter(
                EnumSet.noneOf(ParameterUsage.class),
                future
            ) == ParameterHandlingResult.COMPLETE
        ) {
            return;
        }
        boolean condition = evaluateConditionFromParameters();
        setNextOutputSocket(condition ? 0 : NO_OUTPUT);
        future.complete(null);
    }

    private void executeControlIfElse(CompletableFuture<Void> future) {
        if (
            preprocessAttachedParameter(
                EnumSet.noneOf(ParameterUsage.class),
                future
            ) == ParameterHandlingResult.COMPLETE
        ) {
            return;
        }
        boolean condition = evaluateConditionFromParameters();
        setNextOutputSocket(condition ? 0 : 1);
        future.complete(null);
    }

    private void executeControlFork(CompletableFuture<Void> future) {
        future.complete(null);
    }

    private void executeControlJoinAny(CompletableFuture<Void> future) {
        future.complete(null);
    }

    private void executeControlJoinAll(CompletableFuture<Void> future) {
        future.complete(null);
    }

    private void executeMessageCommand(CompletableFuture<Void> future) {
        if (
            preprocessAttachedParameter(
                EnumSet.noneOf(ParameterUsage.class),
                future
            ) == ParameterHandlingResult.COMPLETE
        ) {
            return;
        }
        List<String> lines = getMessageLines();
        if (lines == null || lines.isEmpty()) {
            lines = Collections.singletonList("Hello World");
        }

        net.minecraft.client.MinecraftClient client =
            net.minecraft.client.MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            long delayMs = 120L;
            int[] sent = {0};
            for (int i = 0; i < lines.size(); i++) {
                String raw = lines.get(i);
                String text = raw == null ? "" : raw.trim();
                text = resolveRuntimeVariablesInText(text);
                if (text.isEmpty()) {
                    continue;
                }
                String sendText = text;
                long scheduledDelay = sent[0] * delayMs;
                MESSAGE_SCHEDULER.schedule(
                    () -> {
                        MinecraftClient.getInstance().execute(() -> {
                            MinecraftClient currentClient =
                                MinecraftClient.getInstance();
                            if (currentClient.player != null) {
                                if (isMessageClientSide()) {
                                    currentClient.player.sendMessage(
                                        Text.literal(sendText),
                                        false
                                    );
                                } else if (
                                    currentClient.player.networkHandler != null
                                ) {
                                    boolean isCommand = sendText.startsWith(
                                        "/"
                                    );
                                    if (isCommand) {
                                        String cmd =
                                            sendText.length() > 1
                                                ? sendText.substring(1)
                                                : "";
                                        if (!cmd.isEmpty()) {
                                            currentClient.player.networkHandler.sendChatCommand(
                                                cmd
                                            );
                                        }
                                    } else {
                                        currentClient.player.networkHandler.sendChatMessage(
                                            sendText
                                        );
                                    }
                                }
                            }
                        });
                    },
                    scheduledDelay,
                    TimeUnit.MILLISECONDS
                );
                sent[0]++;
            }
            long completionDelay = Math.max(
                0,
                (sent[0] - 1) * delayMs + delayMs
            );
            MESSAGE_SCHEDULER.schedule(
                () -> {
                    future.complete(null);
                },
                completionDelay,
                TimeUnit.MILLISECONDS
            );
        } else {
            System.err.println(
                "Unable to send message: client or player not available"
            );
            future.complete(null);
        }
    }

    private String resolveRuntimeVariablesInText(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        Node startNode = getOwningStartNode();
        if (startNode == null && getParentControl() != null) {
            startNode = getParentControl().getOwningStartNode();
        }
        ExecutionManager manager = ExecutionManager.getInstance();
        StringBuilder output = new StringBuilder(raw.length());
        int index = 0;
        boolean containsStructuredReplacement = false;
        while (index < raw.length()) {
            char current = raw.charAt(index);
            if (current == '~') {
                RuntimeVariableInlineMatch match =
                    findInlineRuntimeVariableReference(
                        raw,
                        index,
                        manager,
                        startNode
                    );
                if (match != null) {
                    String replacement = formatRuntimeVariableValue(
                        match.variable
                    );
                    if (replacement != null && !replacement.isEmpty()) {
                        output.append(replacement);
                        if (
                            replacement.indexOf(' ') >= 0 ||
                            replacement.indexOf('\t') >= 0 ||
                            replacement.indexOf('\n') >= 0
                        ) {
                            containsStructuredReplacement = true;
                        }
                        index = match.endIndex;
                        continue;
                    }
                    output.append(raw, index, match.endIndex);
                    index = match.endIndex;
                    continue;
                }
            }
            output.append(current);
            index++;
        }
        String resolved = output.toString();
        if (containsStructuredReplacement) {
            return resolved;
        }
        Double evaluated = evaluateNumericExpression(resolved);
        if (evaluated != null) {
            return formatEvaluatedNumericText(evaluated);
        }
        return resolved;
    }

    private static String formatEvaluatedNumericText(double value) {
        if (!Double.isFinite(value)) {
            return Double.toString(value);
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private RuntimeVariableInlineMatch findInlineRuntimeVariableReference(
        String raw,
        int tildeIndex,
        ExecutionManager manager,
        Node startNode
    ) {
        if (
            raw == null ||
            manager == null ||
            tildeIndex < 0 ||
            tildeIndex >= raw.length() ||
            raw.charAt(tildeIndex) != '~'
        ) {
            return null;
        }
        int nameStart = tildeIndex + 1;
        if (nameStart >= raw.length()) {
            return null;
        }
        RuntimeVariableInlineMatch bestMatch = null;
        Set<String> candidateNames = collectRuntimeVariableNamesForParsing(
            manager,
            startNode
        );
        for (String candidateName : candidateNames) {
            if (candidateName == null || candidateName.isEmpty()) {
                continue;
            }
            if (
                !raw.regionMatches(
                    nameStart,
                    candidateName,
                    0,
                    candidateName.length()
                )
            ) {
                continue;
            }
            int endIndex = nameStart + candidateName.length();
            if (endIndex < raw.length()) {
                char boundary = raw.charAt(endIndex);
                if (
                    !Character.isWhitespace(boundary) &&
                    !isInlineMathOperator(boundary)
                ) {
                    continue;
                }
            }
            ExecutionManager.RuntimeVariable variable =
                resolveRuntimeVariableForName(
                    manager,
                    startNode,
                    candidateName
                );
            if (variable == null) {
                continue;
            }
            if (
                bestMatch == null ||
                candidateName.length() > bestMatch.name.length()
            ) {
                bestMatch = new RuntimeVariableInlineMatch(
                    candidateName,
                    endIndex,
                    variable
                );
            }
        }
        return bestMatch;
    }

    private Set<String> collectRuntimeVariableNamesForParsing(
        ExecutionManager manager,
        Node startNode
    ) {
        Set<String> names = new LinkedHashSet<>();
        if (manager == null) {
            return names;
        }
        names.addAll(manager.getKnownRuntimeVariableNames());
        if (startNode != null) {
            for (ExecutionManager.RuntimeVariableEntry entry : manager.getRuntimeVariableEntries()) {
                if (
                    entry == null ||
                    entry.getStartNodeId() == null ||
                    !startNode.getId().equals(entry.getStartNodeId())
                ) {
                    continue;
                }
                String name = entry.getName();
                if (name != null && !name.trim().isEmpty()) {
                    names.add(name.trim());
                }
            }
        }
        return names;
    }

    private ExecutionManager.RuntimeVariable resolveRuntimeVariableForName(
        ExecutionManager manager,
        Node startNode,
        String name
    ) {
        if (manager == null || name == null || name.trim().isEmpty()) {
            return null;
        }
        String trimmedName = name.trim();
        if (startNode != null) {
            ExecutionManager.RuntimeVariable direct =
                manager.getRuntimeVariable(startNode, trimmedName);
            if (direct != null) {
                return direct;
            }
        }
        ExecutionManager.RuntimeVariable anyActive =
            manager.getRuntimeVariableFromAnyActiveChain(trimmedName);
        if (anyActive != null) {
            return anyActive;
        }
        ExecutionManager.RuntimeVariable match = null;
        for (ExecutionManager.RuntimeVariableEntry entry : manager.getRuntimeVariableEntries()) {
            if (entry == null) {
                continue;
            }
            String entryName = entry.getName();
            if (entryName == null) {
                continue;
            }
            if (!entryName.trim().equals(trimmedName)) {
                continue;
            }
            if (match != null) {
                return null;
            }
            match = entry.getVariable();
        }
        return match;
    }

    private String formatRuntimeVariableValue(
        ExecutionManager.RuntimeVariable variable
    ) {
        if (variable == null) {
            return "";
        }
        Map<String, String> values = variable.getValues();
        if (values == null || values.isEmpty()) {
            return "";
        }
        NodeType valueType = variable.getType();
        if (valueType == null) {
            return "";
        }
        switch (valueType) {
            case PARAM_BLOCK:
            case PARAM_PLACE_TARGET:
                return getRuntimeValue(values, "block");
            case PARAM_ITEM:
            case PARAM_VILLAGER_TRADE:
                return getRuntimeValue(values, "item");
            case PARAM_ENTITY:
                return getRuntimeValue(values, "entity");
            case PARAM_PLAYER:
                return getRuntimeValue(values, "player");
            case PARAM_WAYPOINT:
                return getRuntimeValue(values, "waypoint");
            case PARAM_SCHEMATIC:
                return getRuntimeValue(values, "schematic");
            case PARAM_INVENTORY_SLOT:
                return getRuntimeValue(values, "slot");
            case SENSOR_CURRENT_HAND:
                return getRuntimeValue(values, "slot");
            case SENSOR_IS_ON_GROUND:
                return getRuntimeValue(values, "distance");
            case PARAM_DURATION:
                return getRuntimeValue(values, "duration");
            case PARAM_RANGE:
            case PARAM_CLOSEST:
                return getRuntimeValue(values, "range");
            case PARAM_DISTANCE:
                return getRuntimeValue(values, "distance");
            case PARAM_BLOCK_FACE: {
                String face = getRuntimeValue(values, "face");
                if (!face.isEmpty()) {
                    return face;
                }
                face = getRuntimeValue(values, "side");
                if (!face.isEmpty()) {
                    return face;
                }
                return getRuntimeValue(values, "direction");
            }
            case PARAM_DIRECTION: {
                String yaw = getRuntimeValue(values, "yaw");
                String pitch = getRuntimeValue(values, "pitch");
                if (!yaw.isEmpty() && !pitch.isEmpty()) {
                    return yaw + " " + pitch;
                }
                String direction = getRuntimeValue(values, "direction");
                if (!direction.isEmpty()) {
                    return direction;
                }
                direction = getRuntimeValue(values, "side");
                if (!direction.isEmpty()) {
                    return direction;
                }
                return getRuntimeValue(values, "face");
            }
            case PARAM_AMOUNT:
                return getRuntimeValue(values, "amount");
            case LIST_LENGTH: {
                String length = getRuntimeValue(values, "count");
                if (!length.isEmpty()) {
                    return length;
                }
                length = getRuntimeValue(values, "value");
                if (!length.isEmpty()) {
                    return length;
                }
                return getRuntimeValue(values, "amount");
            }
            case SENSOR_SLOT_ITEM_COUNT:
                return getRuntimeValue(values, "amount");
            case OPERATOR_RANDOM:
                String value = getRuntimeValue(values, "value");
                if (!value.isEmpty()) {
                    return value;
                }
                return getRuntimeValue(values, "amount");
            case PARAM_BOOLEAN:
                return getRuntimeValue(values, "toggle");
            case PARAM_HAND:
                return getRuntimeValue(values, "hand");
            case PARAM_COORDINATE:
                return formatCoordinateValues(values);
            case PARAM_ROTATION:
                return formatRotationValues(values);
            case VARIABLE:
                return getRuntimeValue(values, "variable");
            case SENSOR_POSITION_OF:
                if (isSensorPositionSingleAxisMode()) {
                    String amount = getRuntimeValue(values, "amount");
                    if (!amount.isEmpty()) {
                        return amount;
                    }
                    amount = getRuntimeValue(values, "value");
                    if (!amount.isEmpty()) {
                        return amount;
                    }
                }
                return formatCoordinateValues(values);
            case SENSOR_DISTANCE_BETWEEN:
                return getRuntimeValue(values, "distance");
            case SENSOR_TARGETED_BLOCK: {
                String block = getRuntimeValue(values, "block");
                if (!block.isEmpty()) {
                    String state = getRuntimeValue(values, "state");
                    if (!state.isEmpty()) {
                        return block + "[" + state + "]";
                    }
                    return block;
                }
                break;
            }
            case SENSOR_TARGETED_ENTITY: {
                String entity = getRuntimeValue(values, "entity");
                if (!entity.isEmpty()) {
                    String state = getRuntimeValue(values, "state");
                    if (!state.isEmpty()) {
                        return entity + "[" + state + "]";
                    }
                    return entity;
                }
                break;
            }
            case SENSOR_LOOK_DIRECTION: {
                String yaw = getRuntimeValue(values, "yaw");
                String pitch = getRuntimeValue(values, "pitch");
                if (!yaw.isEmpty() && !pitch.isEmpty()) {
                    return yaw + " " + pitch;
                }
                String amount = getRuntimeValue(values, "amount");
                if (!amount.isEmpty()) {
                    return amount;
                }
                String direction = getRuntimeValue(values, "direction");
                if (!direction.isEmpty()) {
                    return direction;
                }
                direction = getRuntimeValue(values, "side");
                if (!direction.isEmpty()) {
                    return direction;
                }
                return getRuntimeValue(values, "face");
            }
            case SENSOR_TARGETED_BLOCK_FACE: {
                String side = getRuntimeValue(values, "side");
                if (!side.isEmpty()) {
                    return side;
                }
                side = getRuntimeValue(values, "face");
                if (!side.isEmpty()) {
                    return side;
                }
                return getRuntimeValue(values, "text");
            }
            default:
                break;
        }
        return formatCanonicalValueMap(values);
    }

    String formatCoordinateValues(Map<String, String> values) {
        String x = getRuntimeValue(values, "x");
        String y = getRuntimeValue(values, "y");
        String z = getRuntimeValue(values, "z");
        if (x.isEmpty() || y.isEmpty() || z.isEmpty()) {
            return "";
        }
        return x + " " + y + " " + z;
    }

    String formatRotationValues(Map<String, String> values) {
        String yaw = getRuntimeValue(values, "yaw");
        String pitch = getRuntimeValue(values, "pitch");
        if (yaw.isEmpty() || pitch.isEmpty()) {
            return "";
        }
        return yaw + " " + pitch;
    }

    String getRuntimeValue(Map<String, String> values, String key) {
        if (values == null || key == null) {
            return "";
        }
        String direct = values.get(key);
        if (direct != null && !direct.trim().isEmpty()) {
            return direct.trim();
        }
        String lowerKey = key.toLowerCase(Locale.ROOT);
        if (!lowerKey.equals(key)) {
            String lower = values.get(lowerKey);
            if (lower != null && !lower.trim().isEmpty()) {
                return lower.trim();
            }
        }
        String normalizedKey = normalizeParameterKey(key);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry == null || entry.getKey() == null) {
                continue;
            }
            if (!normalizeParameterKey(entry.getKey()).equals(normalizedKey)) {
                continue;
            }
            String candidate = entry.getValue();
            if (candidate != null && !candidate.trim().isEmpty()) {
                return candidate.trim();
            }
        }
        return "";
    }

    private boolean isInlineVariableChar(char character) {
        return (
            Character.isLetterOrDigit(character) ||
            character == '_' ||
            character == '-'
        );
    }

    private boolean isOpenGuiFilled() {
        net.minecraft.client.MinecraftClient client =
            net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        ScreenHandler handler = client.player.currentScreenHandler;
        if (handler == null) {
            return false;
        }
        boolean hasContainerSlots = false;
        for (Slot slot : handler.slots) {
            if (slot == null) {
                continue;
            }
            if (slot.inventory instanceof PlayerInventory) {
                continue;
            }
            hasContainerSlots = true;
            ItemStack stack = slot.getStack();
            if (stack == null || stack.isEmpty()) {
                return false;
            }
        }
        return hasContainerSlots;
    }

    private NodeTextIoCommandExecutor textIoCommandExecutor() {
        return new NodeTextIoCommandExecutor(this);
    }

    private void executeWriteBookCommand(CompletableFuture<Void> future) {
        textIoCommandExecutor().executeWriteBookCommand(future);
    }

    private void executeWriteSignCommand(CompletableFuture<Void> future) {
        textIoCommandExecutor().executeWriteSignCommand(future);
    }

    NodeNavigationCommandExecutor navigationCommandExecutor() {
        return new NodeNavigationCommandExecutor(this);
    }

    private NodeFlowCommandExecutor flowCommandExecutor() {
        return new NodeFlowCommandExecutor(this);
    }

    private void executeGotoCommand(CompletableFuture<Void> future) {
        navigationCommandExecutor().executeGotoCommand(future);
    }

    private void executeTravelCommand(CompletableFuture<Void> future) {
        navigationCommandExecutor().executeTravelCommand(future);
    }

    private void executeGoalCommand(CompletableFuture<Void> future) {
        navigationCommandExecutor().executeGoalCommand(future);
    }

    private void executePathCommand(CompletableFuture<Void> future) {
        navigationCommandExecutor().executePathCommand(future);
    }

    private void executeStopCommand(CompletableFuture<Void> future) {
        navigationCommandExecutor().executeStopCommand(future);
    }

    private void executeStopChainNode(CompletableFuture<Void> future) {
        flowCommandExecutor().executeStopChainNode(future);
    }

    private void executeStartChainNode(CompletableFuture<Void> future) {
        flowCommandExecutor().executeStartChainNode(future);
    }

    private void executeRunPresetNode(CompletableFuture<Void> future) {
        flowCommandExecutor().executeRunPresetNode(future);
    }

    private void executeStopAllNode(CompletableFuture<Void> future) {
        flowCommandExecutor().executeStopAllNode(future);
    }

    private void executeInvertCommand(CompletableFuture<Void> future) {
        navigationCommandExecutor().executeInvertCommand(future);
    }

    private void executeComeCommand(CompletableFuture<Void> future) {
        navigationCommandExecutor().executeComeCommand(future);
    }

    private void executeSurfaceCommand(CompletableFuture<Void> future) {
        navigationCommandExecutor().executeSurfaceCommand(future);
    }

    private void executeTunnelCommand(CompletableFuture<Void> future) {
        navigationCommandExecutor().executeTunnelCommand(future);
    }

    private void executeFarmCommand(CompletableFuture<Void> future) {
        navigationCommandExecutor().executeFarmCommand(future);
    }

    BlockPos resolveGotoFallbackTargetFromBlockId(
        String blockId,
        CompletableFuture<Void> future
    ) {
        return navigationCommandExecutor().resolveGotoFallbackTargetFromBlockId(
            blockId,
            future
        );
    }

    private NodeInventoryCommandExecutor inventoryCommandExecutor() {
        return new NodeInventoryCommandExecutor(this);
    }

    private void executeHotbarCommand(CompletableFuture<Void> future) {
        inventoryCommandExecutor().executeHotbarCommand(future);
    }

    private void executeDropItemCommand(CompletableFuture<Void> future) {
        inventoryCommandExecutor().executeDropItemCommand(future);
    }

    private void executeDropSlotCommand(CompletableFuture<Void> future) {
        inventoryCommandExecutor().executeDropSlotCommand(future);
    }

    private void executeClickSlotCommand(CompletableFuture<Void> future) {
        inventoryCommandExecutor().executeClickSlotCommand(future);
    }

    private void executeClickScreenCommand(CompletableFuture<Void> future) {
        inventoryCommandExecutor().executeClickScreenCommand(future);
    }

    private void executeMoveItemCommand(CompletableFuture<Void> future) {
        inventoryCommandExecutor().executeMoveItemCommand(future);
    }

    boolean resolveMoveItemSlotFromItemParameter(
        Node parameterNode,
        int slotIndex,
        CompletableFuture<Void> future
    ) {
        return inventoryCommandExecutor().resolveMoveItemSlotFromItemParameter(
            parameterNode,
            slotIndex,
            future
        );
    }

    boolean resolveDropParameterSelection(
        Node parameterNode,
        CompletableFuture<Void> future
    ) {
        return inventoryCommandExecutor().resolveDropParameterSelection(
            parameterNode,
            future
        );
    }

    boolean isDropNodeType() {
        return type == NodeType.DROP_ITEM || type == NodeType.DROP_SLOT;
    }

    SlotSelectionType resolveInventorySlotSelectionType(Node parameterNode) {
        return inventoryCommandExecutor().resolveInventorySlotSelectionType(
            parameterNode
        );
    }

    SlotResolution resolveInventorySlot(
        ScreenHandler handler,
        PlayerInventory inventory,
        int slotValue,
        SlotSelectionType selectionType
    ) {
        return inventoryCommandExecutor().resolveInventorySlot(
            handler,
            inventory,
            slotValue,
            selectionType
        );
    }

    private boolean resolveUseParameterSelection(
        Node parameterNode,
        CompletableFuture<Void> future
    ) {
        if (parameterNode == null) {
            return false;
        }
        net.minecraft.client.MinecraftClient client =
            net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            if (future != null && !future.isDone()) {
                future.completeExceptionally(
                    new RuntimeException("Minecraft client not available")
                );
            }
            return false;
        }
        if (runtimeState.runtimeParameterData == null) {
            runtimeState.runtimeParameterData = new RuntimeParameterData();
        }

        PlayerInventory inventory = client.player.getInventory();
        EnumSet<NodeValueTrait> traits = parameterNode.getProvidedTraits();
        boolean isListItem = parameterNode.getType() == NodeType.LIST_ITEM;
        boolean treatAsItem =
            traits.contains(NodeValueTrait.ITEM) ||
            (isListItem &&
                runtimeState.runtimeParameterData != null &&
                runtimeState.runtimeParameterData.targetItemId != null);
        if (traits.contains(NodeValueTrait.BLOCK)) {
            {
                String rawBlock = getParameterString(parameterNode, "Block");
                boolean anySelection = isAnySelectionValue(rawBlock);
                List<BlockSelection> selections = resolveBlocksFromParameter(
                    parameterNode
                );
                if (selections.isEmpty() && !anySelection) {
                    sendParameterSearchFailure(
                        "No block selected on parameter for " +
                            type.getDisplayName() +
                            ".",
                        future
                    );
                    return false;
                }

                ItemSearchResult result = null;
                if (anySelection || selections.isEmpty()) {
                    result = findFirstBlockItemSlot(inventory);
                } else {
                    result = findUseBlockSlot(inventory, selections);
                }

                if (result == null) {
                    String reference = anySelection
                        ? "block"
                        : selections
                              .stream()
                              .map(BlockSelection::getBlockIdString)
                              .filter(id -> id != null && !id.isEmpty())
                              .findFirst()
                              .orElse("block");
                    sendParameterSearchFailure(
                        "No " +
                            reference +
                            " found in inventory for " +
                            type.getDisplayName() +
                            ".",
                        future
                    );
                    return false;
                }
                runtimeState.runtimeParameterData.slotIndex =
                    result.slotIndex();
                runtimeState.runtimeParameterData.slotSelectionType =
                    SlotSelectionType.PLAYER_INVENTORY;
                runtimeState.runtimeParameterData.targetItem = result.item();
                runtimeState.runtimeParameterData.targetItemId =
                    result.itemId();
                return true;
            }
        }
        if (treatAsItem) {
            List<String> itemIds;
            if (
                isListItem &&
                runtimeState.runtimeParameterData != null &&
                runtimeState.runtimeParameterData.targetItemId != null
            ) {
                itemIds = java.util.Collections.singletonList(
                    runtimeState.runtimeParameterData.targetItemId
                );
            } else {
                itemIds = resolveItemIdsFromParameter(parameterNode);
            }
            if (itemIds.isEmpty()) {
                sendParameterSearchFailure(
                    "No item selected on parameter for " +
                        type.getDisplayName() +
                        ".",
                    future
                );
                return false;
            }
            ItemSearchResult result = findUseItemSlot(inventory, itemIds);
            if (result == null) {
                String reference = String.join(", ", itemIds);
                sendParameterSearchFailure(
                    "No " +
                        reference +
                        " found in inventory for " +
                        type.getDisplayName() +
                        ".",
                    future
                );
                return false;
            }
            runtimeState.runtimeParameterData.slotIndex = result.slotIndex();
            runtimeState.runtimeParameterData.slotSelectionType =
                SlotSelectionType.PLAYER_INVENTORY;
            runtimeState.runtimeParameterData.targetItem = result.item();
            runtimeState.runtimeParameterData.targetItemId = result.itemId();
            return true;
        }
        if (traits.contains(NodeValueTrait.INVENTORY_SLOT)) {
            SlotSelectionType selectionType = resolveInventorySlotSelectionType(
                parameterNode
            );
            if (selectionType == SlotSelectionType.GUI_CONTAINER) {
                sendNodeErrorMessage(
                    client,
                    "Use node can only use player inventory slots."
                );
                if (future != null && !future.isDone()) {
                    future.complete(null);
                }
                return false;
            }
            int slotValue = clampInventorySlot(
                inventory,
                parseNodeInt(parameterNode, "Slot", 0)
            );
            runtimeState.runtimeParameterData.slotIndex = slotValue;
            runtimeState.runtimeParameterData.slotSelectionType =
                SlotSelectionType.PLAYER_INVENTORY;
            return true;
        }
        sendIncompatibleParameterMessage(parameterNode);
        return false;
    }

    private record ItemSearchResult(int slotIndex, Item item, String itemId) {}

    private ItemSearchResult findUseItemSlot(
        PlayerInventory inventory,
        List<String> itemIds
    ) {
        if (inventory == null || itemIds == null || itemIds.isEmpty()) {
            return null;
        }
        for (String candidateId : itemIds) {
            Identifier identifier = Identifier.tryParse(candidateId);
            if (identifier == null || !Registries.ITEM.containsId(identifier)) {
                continue;
            }
            Item candidateItem = Registries.ITEM.get(identifier);
            int slot = findAccessibleSlotWithItem(inventory, candidateItem);
            if (slot >= 0) {
                return new ItemSearchResult(slot, candidateItem, candidateId);
            }
        }
        return null;
    }

    private ItemSearchResult findUseBlockSlot(
        PlayerInventory inventory,
        List<BlockSelection> selections
    ) {
        if (inventory == null || selections == null || selections.isEmpty()) {
            return null;
        }
        for (BlockSelection selection : selections) {
            if (selection == null || selection.getBlock() == null) {
                continue;
            }
            Item candidateItem = selection.getBlock().asItem();
            if (candidateItem == null || candidateItem == Items.AIR) {
                continue;
            }
            int slot = findAccessibleSlotWithItem(inventory, candidateItem);
            if (slot >= 0) {
                Identifier id = Registries.ITEM.getId(candidateItem);
                String itemId =
                    id != null ? id.toString() : selection.getBlockIdString();
                return new ItemSearchResult(slot, candidateItem, itemId);
            }
        }
        return null;
    }

    private ItemSearchResult findFirstBlockItemSlot(PlayerInventory inventory) {
        if (inventory == null) {
            return null;
        }
        int limit = Math.min(PlayerInventory.MAIN_SIZE, inventory.size());
        for (int slot = 0; slot < limit; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty()) {
                continue;
            }
            Item item = stack.getItem();
            if (item instanceof BlockItem) {
                Identifier id = Registries.ITEM.getId(item);
                String itemId = id != null ? id.toString() : "";
                return new ItemSearchResult(slot, item, itemId);
            }
        }
        int offhandIndex = getOffhandInventoryIndex(inventory);
        if (offhandIndex >= 0 && offhandIndex < inventory.size()) {
            ItemStack offhandStack = inventory.getStack(offhandIndex);
            if (!offhandStack.isEmpty()) {
                Item item = offhandStack.getItem();
                if (item instanceof BlockItem) {
                    Identifier id = Registries.ITEM.getId(item);
                    String itemId = id != null ? id.toString() : "";
                    return new ItemSearchResult(offhandIndex, item, itemId);
                }
            }
        }
        return null;
    }

    private int findAccessibleSlotWithItem(
        PlayerInventory inventory,
        Item item
    ) {
        if (inventory == null || item == null) {
            return -1;
        }
        for (
            int slot = 0;
            slot < PlayerInventory.MAIN_SIZE && slot < inventory.size();
            slot++
        ) {
            ItemStack stack = inventory.getStack(slot);
            if (!stack.isEmpty() && stack.isOf(item)) {
                return slot;
            }
        }
        int offhandIndex = getOffhandInventoryIndex(inventory);
        if (offhandIndex >= 0 && offhandIndex < inventory.size()) {
            ItemStack offhandStack = inventory.getStack(offhandIndex);
            if (!offhandStack.isEmpty() && offhandStack.isOf(item)) {
                return offhandIndex;
            }
        }
        return -1;
    }

    int findFirstSlotWithItem(PlayerInventory inventory, Item item) {
        if (inventory == null || item == null) {
            return -1;
        }
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty() && stack.isOf(item)) {
                return i;
            }
        }
        return -1;
    }

    int findFirstNonEmptySlot(PlayerInventory inventory) {
        if (inventory == null) {
            return -1;
        }
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private void applyCrouchState(
        net.minecraft.client.MinecraftClient client,
        boolean active
    ) {
        applySneakState(client, active);
    }

    public boolean isRepeatUntilConditionMetForPolling() {
        if (type != NodeType.CONTROL_REPEAT_UNTIL) {
            return false;
        }
        return (
            preprocessAttachedParameter(
                EnumSet.noneOf(ParameterUsage.class),
                null
            ) != ParameterHandlingResult.COMPLETE &&
            evaluateConditionFromParameters()
        );
    }

    boolean shouldAbortForRepeatUntilGuard() {
        ExecutionManager manager = ExecutionManager.getInstance();
        if (manager != null && manager.isStopRequested()) {
            return true;
        }
        Node guard = runtimeState.activeRepeatUntilGuard;
        return (
            guard != null &&
            guard != this &&
            guard.isRepeatUntilConditionMetForPolling()
        );
    }

    void applySneakState(
        net.minecraft.client.MinecraftClient client,
        boolean active
    ) {
        if (client == null || client.player == null) {
            return;
        }
        client.player.setSneaking(active);
        if (client.options != null && client.options.sneakKey != null) {
            client.options.sneakKey.setPressed(active);
        }
    }

    void waitForSneakSync(
        net.minecraft.client.MinecraftClient client,
        boolean previousState,
        boolean desiredState
    ) throws InterruptedException {
        if (
            client == null ||
            client.isOnThread() ||
            previousState == desiredState
        ) {
            return;
        }
        Thread.sleep(SNEAK_SYNC_DELAY_MS);
    }

    BlockHitResult raycastBlockFromOrientation(
        net.minecraft.client.MinecraftClient client,
        float yaw,
        float pitch,
        double distance
    ) {
        if (client == null || client.player == null || client.world == null) {
            return null;
        }
        Vec3d eyePos = client.player.getEyePos();
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        Vec3d direction = new Vec3d(
            -Math.sin(yawRad) * Math.cos(pitchRad),
            -Math.sin(pitchRad),
            Math.cos(yawRad) * Math.cos(pitchRad)
        );
        double reachDistance = Math.sqrt(DEFAULT_REACH_DISTANCE_SQUARED);
        double rayDistance =
            distance > 0.0 ? Math.min(distance, reachDistance) : reachDistance;
        Vec3d end = eyePos.add(direction.multiply(rayDistance));
        HitResult hit = client.world.raycast(
            new RaycastContext(
                eyePos,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                client.player
            )
        );
        if (
            hit instanceof BlockHitResult blockHit &&
            hit.getType() == HitResult.Type.BLOCK
        ) {
            return blockHit;
        }
        return null;
    }

    private void completeSensorEvaluation(CompletableFuture<Void> future) {
        boolean result = evaluateSensor();
        setNextOutputSocket(result ? 0 : 1);
        future.complete(null);
    }

    void runOnClientThread(
        net.minecraft.client.MinecraftClient client,
        Runnable task
    ) throws InterruptedException {
        if (client == null || client.isOnThread()) {
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

    <T> T supplyFromClient(
        net.minecraft.client.MinecraftClient client,
        java.util.function.Supplier<T> supplier
    ) throws InterruptedException {
        if (client == null || client.isOnThread()) {
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

    int clampInventorySlot(PlayerInventory inventory, int slot) {
        return MathHelper.clamp(slot, 0, inventory.size() - 1);
    }

    int getOffhandInventoryIndex(PlayerInventory inventory) {
        if (inventory == null || inventory.size() <= 0) {
            return -1;
        }
        int index = PLAYER_OFFHAND_INVENTORY_INDEX;
        if (index >= inventory.size()) {
            return inventory.size() - 1;
        }
        return index;
    }

    private EquipmentSlot parseEquipmentSlot(
        NodeParameter parameter,
        EquipmentSlot defaultSlot
    ) {
        if (parameter == null || parameter.getStringValue() == null) {
            return defaultSlot;
        }
        String value = parameter
            .getStringValue()
            .trim()
            .toLowerCase(Locale.ROOT);
        switch (value) {
            case "head":
            case "helmet":
                return EquipmentSlot.HEAD;
            case "chest":
            case "chestplate":
                return EquipmentSlot.CHEST;
            case "legs":
            case "leggings":
                return EquipmentSlot.LEGS;
            case "feet":
            case "boots":
                return EquipmentSlot.FEET;
            default:
                return defaultSlot;
        }
    }

    public int getIntParameter(String name, int defaultValue) {
        NodeParameter param = getParameter(name);
        if (param == null) {
            return defaultValue;
        }
        String rawValue = param.getStringValue();
        String resolvedValue = resolveRuntimeVariablesInText(rawValue);
        if (resolvedValue == null || resolvedValue.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(resolvedValue.trim());
        } catch (NumberFormatException ignored) {
            try {
                return (int) Math.round(
                    Double.parseDouble(resolvedValue.trim())
                );
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
    }

    static boolean isAnyPlayerValue(String value) {
        return value != null && "any".equalsIgnoreCase(value.trim());
    }

    static boolean isSelfPlayerValue(String value) {
        if (value == null) {
            return true;
        }
        String trimmed = value.trim();
        return (
            trimmed.isEmpty() ||
            "self".equalsIgnoreCase(trimmed) ||
            "me".equalsIgnoreCase(trimmed) ||
            "local".equalsIgnoreCase(trimmed)
        );
    }

    private static boolean isAnyMessageValue(String value) {
        return (
            value == null ||
            value.trim().isEmpty() ||
            "any".equalsIgnoreCase(value.trim())
        );
    }

    static Optional<AbstractClientPlayerEntity> findNearestPlayer(
        net.minecraft.client.MinecraftClient client,
        AbstractClientPlayerEntity reference
    ) {
        if (client == null || client.world == null) {
            return Optional.empty();
        }
        AbstractClientPlayerEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
            if (player == null) {
                continue;
            }
            if (reference != null && player == reference) {
                continue;
            }
            double distance =
                reference != null ? player.squaredDistanceTo(reference) : 0.0;
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
        NodeParameter param = getParameter(name);
        if (param == null) {
            return defaultValue;
        }
        String value = param.getStringValue();
        if (value == null) {
            return defaultValue;
        }
        String resolved = resolveRuntimeVariablesInText(value);
        return resolved != null ? resolved : defaultValue;
    }

    static String getParameterString(Node node, String name) {
        if (node == null || name == null) {
            return null;
        }
        NodeParameter parameter = node.getParameter(name);
        String value = parameter != null ? parameter.getStringValue() : null;
        if (value == null) {
            Map<String, String> exported = node.exportParameterValues();
            if (exported != null && !exported.isEmpty()) {
                value = exported.get(name);
                if (value == null) {
                    value = exported.get(normalizeParameterKey(name));
                }
            }
        }
        if (value == null) {
            return null;
        }
        return node.resolveRuntimeVariablesInText(value);
    }

    /** Returns the raw parameter value without resolving ~variable references (for error messages). */
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

    private static boolean isInlineMathOperator(char c) {
        return c == '+' || c == '-' || c == '*' || c == '/' || c == '^';
    }

    String getBlockParameterValue(Node node) {
        if (node == null) {
            return null;
        }
        String blockId = getParameterString(node, "Block");
        if (
            blockId == null || blockId.isEmpty() || isAnySelectionValue(blockId)
        ) {
            return null;
        }
        String state = getParameterString(node, "State");
        if (state == null || state.isEmpty() || isAnySelectionValue(state)) {
            return blockId;
        }
        Optional<String> combined = BlockSelection.combine(blockId, state);
        if (combined.isPresent()) {
            return combined.get();
        }
        notifyInvalidBlockStateSelection(blockId, state);
        return null;
    }

    String getEntityParameterState(Node node) {
        if (node == null) {
            return "";
        }
        String state = getParameterString(node, "State");
        if (state == null) {
            return "";
        }
        String trimmedState = state.trim();
        if (trimmedState.isEmpty()) {
            return "";
        }
        String entityRaw = getParameterString(node, "Entity");
        if (entityRaw == null || entityRaw.trim().isEmpty()) {
            return "";
        }
        String primaryEntity = entityRaw;
        List<String> parts = splitMultiValueList(entityRaw);
        if (!parts.isEmpty()) {
            primaryEntity = parts.get(0);
        }
        String sanitized = sanitizeResourceId(primaryEntity);
        String normalized =
            sanitized != null && !sanitized.isEmpty()
                ? normalizeResourceId(sanitized, "minecraft")
                : primaryEntity;
        Identifier identifier = Identifier.tryParse(normalized);
        if (
            identifier == null || !Registries.ENTITY_TYPE.containsId(identifier)
        ) {
            return trimmedState;
        }
        net.minecraft.client.MinecraftClient client =
            net.minecraft.client.MinecraftClient.getInstance();
        if (
            !EntityStateOptions.isStateSupported(
                Registries.ENTITY_TYPE.get(identifier),
                client != null ? client.world : null,
                trimmedState
            )
        ) {
            notifyInvalidEntityStateSelection(primaryEntity, trimmedState);
            return trimmedState;
        }
        return trimmedState;
    }

    double getDoubleParameter(String name, double defaultValue) {
        NodeParameter param = getParameter(name);
        if (param == null) {
            return defaultValue;
        }
        String rawValue = param.getStringValue();
        String resolvedValue = resolveRuntimeVariablesInText(rawValue);
        if (resolvedValue == null || resolvedValue.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(resolvedValue.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    boolean getBooleanParameter(String name, boolean defaultValue) {
        NodeParameter param = getParameter(name);
        if (param == null) {
            return defaultValue;
        }
        return resolveBooleanValueFromRaw(param.getStringValue(), false).orElse(
            defaultValue
        );
    }

    private Optional<Boolean> resolveBooleanValueFromRaw(
        String rawValue,
        boolean allowBareVariableName
    ) {
        if (rawValue == null) {
            return Optional.empty();
        }
        String trimmedRaw = rawValue.trim();
        if (trimmedRaw.isEmpty()) {
            return Optional.empty();
        }

        String resolvedValue = resolveRuntimeVariablesInText(trimmedRaw);
        Optional<Boolean> parsedResolved = parseFlexibleBoolean(resolvedValue);
        if (parsedResolved.isPresent()) {
            return parsedResolved;
        }

        if (!allowBareVariableName) {
            return Optional.empty();
        }

        String variableName = trimmedRaw.startsWith("~")
            ? trimmedRaw.substring(1).trim()
            : trimmedRaw;
        if (variableName.isEmpty()) {
            return Optional.empty();
        }
        ExecutionManager manager = ExecutionManager.getInstance();
        Node startNode = getOwningStartNode();
        if (startNode == null && getParentControl() != null) {
            startNode = getParentControl().getOwningStartNode();
        }
        ExecutionManager.RuntimeVariable variable =
            resolveRuntimeVariableForName(manager, startNode, variableName);
        return parseRuntimeVariableBoolean(variable);
    }

    private Optional<Boolean> parseRuntimeVariableBoolean(
        ExecutionManager.RuntimeVariable variable
    ) {
        if (variable == null) {
            return Optional.empty();
        }
        if (variable.getType() == NodeType.PARAM_BOOLEAN) {
            return parseFlexibleBoolean(
                getRuntimeValue(variable.getValues(), "toggle")
            );
        }
        return parseFlexibleBoolean(formatRuntimeVariableValue(variable));
    }

    Optional<Boolean> resolveBooleanNodeValue(Node node) {
        if (node == null || node.getType() != NodeType.PARAM_BOOLEAN) {
            return Optional.empty();
        }
        node.ensureBooleanParameters();
        if (node.isBooleanModeVariable()) {
            NodeParameter variableParameter = node.getParameter("Variable");
            String variableValue =
                variableParameter != null
                    ? variableParameter.getStringValue()
                    : null;
            return node.resolveBooleanValueFromRaw(variableValue, true);
        }
        NodeParameter toggleParameter = node.getParameter("Toggle");
        String value =
            toggleParameter != null ? toggleParameter.getStringValue() : null;
        if (
            (value == null || value.trim().isEmpty()) && toggleParameter != null
        ) {
            value = toggleParameter.getDefaultValue();
        }
        return node.resolveBooleanValueFromRaw(value, false);
    }

    private static Optional<Boolean> parseFlexibleBoolean(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        if ("true".equals(normalized) || "1".equals(normalized)) {
            return Optional.of(true);
        }
        if ("false".equals(normalized) || "0".equals(normalized)) {
            return Optional.of(false);
        }
        return Optional.empty();
    }

    static double parseDoubleOrDefault(String value, double defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        Double evaluated = evaluateNumericExpression(value);
        if (evaluated != null) {
            return evaluated;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static Double evaluateNumericExpression(String value) {
        if (value == null) {
            return null;
        }
        NumericExpressionParser parser = new NumericExpressionParser(value);
        return parser.parse();
    }

    private static final class NumericExpressionParser {

        private final String input;
        private int index;

        private NumericExpressionParser(String input) {
            this.input = input == null ? "" : input;
        }

        private Double parse() {
            skipWhitespace();
            Double result = parseExpression();
            if (result == null) {
                return null;
            }
            skipWhitespace();
            return index == input.length() ? result : null;
        }

        private Double parseExpression() {
            Double value = parseTerm();
            if (value == null) {
                return null;
            }
            while (true) {
                skipWhitespace();
                if (consume('+')) {
                    Double rhs = parseTerm();
                    if (rhs == null) {
                        return null;
                    }
                    value += rhs;
                } else if (consume('-')) {
                    Double rhs = parseTerm();
                    if (rhs == null) {
                        return null;
                    }
                    value -= rhs;
                } else {
                    return value;
                }
            }
        }

        private Double parseTerm() {
            Double value = parsePower();
            if (value == null) {
                return null;
            }
            while (true) {
                skipWhitespace();
                if (consume('*')) {
                    Double rhs = parsePower();
                    if (rhs == null) {
                        return null;
                    }
                    value *= rhs;
                } else if (consume('/')) {
                    Double rhs = parsePower();
                    if (rhs == null || rhs == 0.0D) {
                        return null;
                    }
                    value /= rhs;
                } else {
                    return value;
                }
            }
        }

        private Double parsePower() {
            Double base = parseFactor();
            if (base == null) {
                return null;
            }
            skipWhitespace();
            if (!consume('^')) {
                return base;
            }
            Double exponent = parsePower();
            if (exponent == null) {
                return null;
            }
            return Math.pow(base, exponent);
        }

        private Double parseFactor() {
            skipWhitespace();
            if (consume('+')) {
                return parseFactor();
            }
            if (consume('-')) {
                Double value = parseFactor();
                return value != null ? -value : null;
            }
            return parseNumber();
        }

        private Double parseNumber() {
            skipWhitespace();
            int start = index;
            boolean sawDigit = false;
            boolean sawDecimal = false;
            while (index < input.length()) {
                char current = input.charAt(index);
                if (Character.isDigit(current)) {
                    sawDigit = true;
                    index++;
                    continue;
                }
                if (current == '.') {
                    if (sawDecimal) {
                        break;
                    }
                    sawDecimal = true;
                    index++;
                    continue;
                }
                break;
            }
            if (!sawDigit) {
                index = start;
                return null;
            }
            try {
                return Double.parseDouble(input.substring(start, index));
            } catch (NumberFormatException e) {
                index = start;
                return null;
            }
        }

        private void skipWhitespace() {
            while (
                index < input.length() &&
                Character.isWhitespace(input.charAt(index))
            ) {
                index++;
            }
        }

        private boolean consume(char expected) {
            if (index >= input.length() || input.charAt(index) != expected) {
                return false;
            }
            index++;
            return true;
        }
    }

    private static final class RuntimeVariableInlineMatch {

        private final String name;
        private final int endIndex;
        private final ExecutionManager.RuntimeVariable variable;

        private RuntimeVariableInlineMatch(
            String name,
            int endIndex,
            ExecutionManager.RuntimeVariable variable
        ) {
            this.name = name;
            this.endIndex = endIndex;
            this.variable = variable;
        }
    }

    private void notifyInvalidBlockStateSelection(
        String blockId,
        String state
    ) {
        net.minecraft.client.MinecraftClient client =
            net.minecraft.client.MinecraftClient.getInstance();
        String blockLabel = (blockId == null || blockId.isEmpty())
            ? "the selected block"
            : blockId;
        String stateLabel =
            state == null || state.isEmpty() ? "(unspecified state)" : state;
        sendNodeErrorMessage(
            client,
            "State \"" +
                stateLabel +
                "\" is not valid for " +
                blockLabel +
                " on " +
                type.getDisplayName() +
                "."
        );
    }

    private void notifyInvalidEntityStateSelection(
        String entityId,
        String state
    ) {
        net.minecraft.client.MinecraftClient client =
            net.minecraft.client.MinecraftClient.getInstance();
        String entityLabel = (entityId == null || entityId.isEmpty())
            ? "the selected entity"
            : entityId;
        String stateLabel =
            state == null || state.isEmpty() ? "(unspecified state)" : state;
        sendNodeErrorMessage(
            client,
            "State \"" +
                stateLabel +
                "\" is not valid for " +
                entityLabel +
                " on " +
                type.getDisplayName() +
                "."
        );
    }

    Optional<BlockPos> findNearestDroppedItem(
        net.minecraft.client.MinecraftClient client,
        Item item,
        double range
    ) {
        if (
            client == null ||
            client.player == null ||
            client.world == null ||
            item == null
        ) {
            return Optional.empty();
        }
        double searchRadius = Math.max(1.0, range);
        Box searchBox = client.player.getBoundingBox().expand(searchRadius);
        List<ItemEntity> entities = client.world.getEntitiesByClass(
            ItemEntity.class,
            searchBox,
            entity ->
                entity != null &&
                !entity.isRemoved() &&
                !entity.getStack().isEmpty() &&
                entity.getStack().isOf(item)
        );
        if (entities.isEmpty()) {
            return Optional.empty();
        }
        ItemEntity nearest = Collections.min(
            entities,
            Comparator.comparingDouble(entity ->
                entity.squaredDistanceTo(client.player)
            )
        );
        return Optional.of(nearest.getBlockPos());
    }

    Optional<Entity> findNearestEntity(
        net.minecraft.client.MinecraftClient client,
        EntityType<?> entityType,
        double range
    ) {
        return findNearestEntity(client, entityType, range, "");
    }

    Optional<Entity> findNearestEntity(
        net.minecraft.client.MinecraftClient client,
        EntityType<?> entityType,
        double range,
        String state
    ) {
        if (
            client == null ||
            client.player == null ||
            client.world == null ||
            entityType == null
        ) {
            return Optional.empty();
        }
        double searchRadius = Math.max(1.0, range);
        Box searchBox = client.player.getBoundingBox().expand(searchRadius);
        Identifier targetTypeId = Registries.ENTITY_TYPE.getId(entityType);
        List<Entity> matches = client.world.getOtherEntities(
            client.player,
            searchBox,
            entity -> {
                if (entity == null) {
                    return false;
                }
                EntityType<?> candidateType = entity.getType();
                boolean sameType = candidateType == entityType;
                if (!sameType && targetTypeId != null) {
                    Identifier candidateId = Registries.ENTITY_TYPE.getId(
                        candidateType
                    );
                    sameType = targetTypeId.equals(candidateId);
                }
                return (
                    sameType && EntityStateOptions.matchesState(entity, state)
                );
            }
        );
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        Entity nearest = Collections.min(
            matches,
            Comparator.comparingDouble(entity ->
                entity.squaredDistanceTo(client.player)
            )
        );
        return Optional.of(nearest);
    }

    Entity resolveListItemEntity(
        Node listNode,
        RuntimeParameterData data,
        CompletableFuture<Void> future
    ) {
        if (listNode == null) {
            return null;
        }
        net.minecraft.client.MinecraftClient client =
            net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return null;
        }

        String listName = getParameterString(listNode, "List");
        if (listName == null || listName.trim().isEmpty()) {
            sendNodeErrorMessage(client, "List name cannot be empty.");
            if (future != null && !future.isDone()) {
                future.complete(null);
            }
            return null;
        }

        ExecutionManager.RuntimeList list = resolveRuntimeList(listNode);
        if (list == null || list.getEntries().isEmpty()) {
            sendNodeErrorMessage(
                client,
                "List \"" + listName.trim() + "\" is empty or missing."
            );
            if (future != null && !future.isDone()) {
                future.complete(null);
            }
            return null;
        }

        int index = parseNodeInt(listNode, "Index", 1);
        if (index <= 0) {
            sendNodeErrorMessage(
                client,
                "List item index must be 1 or greater."
            );
            if (future != null && !future.isDone()) {
                future.complete(null);
            }
            return null;
        }

        int listIndex = index - 1;
        if (listIndex >= list.getEntries().size()) {
            sendNodeErrorMessage(
                client,
                "List \"" + listName.trim() + "\" has no item " + index + "."
            );
            if (future != null && !future.isDone()) {
                future.complete(null);
            }
            return null;
        }

        String entry = list.getEntries().get(listIndex);
        if (entry == null || entry.isEmpty()) {
            sendNodeErrorMessage(
                client,
                "List \"" + listName.trim() + "\" has no item " + index + "."
            );
            if (future != null && !future.isDone()) {
                future.complete(null);
            }
            return null;
        }

        if (entry.startsWith(LIST_ENTRY_SERIALIZED_PREFIX)) {
            Node snapshot = resolveListItemValueNode(
                listNode,
                future,
                true,
                data
            );
            if (snapshot == null) {
                return null;
            }
            NodeType snapshotType = snapshot.getType();
            if (
                snapshotType != NodeType.PARAM_ENTITY &&
                snapshotType != NodeType.PARAM_PLAYER &&
                snapshotType != NodeType.PARAM_ITEM
            ) {
                return null;
            }
            RuntimeParameterData resolvedData =
                data != null ? data : new RuntimeParameterData();
            Optional<Vec3d> resolved = resolvePositionTarget(
                snapshot,
                resolvedData,
                future
            );
            if (resolved.isEmpty()) {
                return null;
            }
            return resolvedData.targetEntity;
        }

        if (list.getElementType() == NodeType.PARAM_GUI) {
            if (
                getParameter("Slot") == null &&
                getParameter("SourceSlot") == null &&
                getParameter("TargetSlot") == null
            ) {
                sendNodeErrorMessage(
                    client,
                    "List \"" +
                        listName.trim() +
                        "\" contains GUI slots and cannot be used by " +
                        type.getDisplayName() +
                        "."
                );
                if (future != null && !future.isDone()) {
                    future.complete(null);
                }
                return null;
            }
            ListSlotEntry slotEntry = parseListSlotEntry(entry);
            if (slotEntry == null) {
                sendNodeErrorMessage(
                    client,
                    "List \"" +
                        listName.trim() +
                        "\" item " +
                        index +
                        " is not a valid GUI slot."
                );
                if (future != null && !future.isDone()) {
                    future.complete(null);
                }
                return null;
            }
            if (data != null) {
                data.slotIndex = slotEntry.slotIndex;
                data.slotSelectionType = slotEntry.selectionType;
            }
            applyListSlotSelection(
                slotEntry.slotIndex,
                listNode.attachments.getParentParameterSlotIndex()
            );
            return client.player;
        }

        try {
            java.util.UUID uuid = java.util.UUID.fromString(entry);
            Entity entity = resolveEntityByUuid(client, uuid);
            if (entity == null || entity.isRemoved()) {
                sendNodeErrorMessage(
                    client,
                    "List \"" +
                        listName.trim() +
                        "\" item " +
                        index +
                        " is not available."
                );
                if (future != null && !future.isDone()) {
                    future.complete(null);
                }
                return null;
            }
            if (data != null) {
                data.targetEntity = entity;
                Identifier entityId = Registries.ENTITY_TYPE.getId(
                    entity.getType()
                );
                if (entityId != null) {
                    data.targetEntityId = entityId.toString();
                }
            }

            NodeType elementType = list.getElementType();
            if (
                elementType == NodeType.PARAM_ITEM &&
                entity instanceof ItemEntity itemEntity
            ) {
                ItemStack stack = itemEntity.getStack();
                if (stack != null && !stack.isEmpty()) {
                    Item item = stack.getItem();
                    Identifier itemId = Registries.ITEM.getId(item);
                    if (itemId != null) {
                        if (data != null) {
                            data.targetItem = item;
                            data.targetItemId = itemId.toString();
                        }
                        setParameterValueAndPropagate(
                            "Item",
                            itemId.toString()
                        );
                    }
                }
            } else if (
                elementType == NodeType.PARAM_PLAYER &&
                entity instanceof AbstractClientPlayerEntity player
            ) {
                String name = GameProfileCompatibilityBridge.getName(
                    player.getGameProfile()
                );
                if (name != null && !name.trim().isEmpty()) {
                    setParameterValueAndPropagate("Player", name);
                }
            } else if (elementType == NodeType.PARAM_ENTITY) {
                Identifier typeId = Registries.ENTITY_TYPE.getId(
                    entity.getType()
                );
                if (typeId != null) {
                    setParameterValueAndPropagate("Entity", typeId.toString());
                }
            }

            return entity;
        } catch (IllegalArgumentException ex) {
            NodeType elementType = list.getElementType();
            String trimmedEntry = entry.trim();

            if (elementType == NodeType.PARAM_ENTITY) {
                Identifier identifier = Identifier.tryParse(trimmedEntry);
                if (
                    identifier != null &&
                    Registries.ENTITY_TYPE.containsId(identifier)
                ) {
                    EntityType<?> entityType = Registries.ENTITY_TYPE.get(
                        identifier
                    );
                    Optional<Entity> nearest = findNearestEntity(
                        client,
                        entityType,
                        PARAMETER_SEARCH_RADIUS,
                        ""
                    );
                    if (nearest.isPresent()) {
                        Entity entity = nearest.get();
                        if (data != null) {
                            data.targetEntity = entity;
                            data.targetEntityId = identifier.toString();
                        }
                        setParameterValueAndPropagate(
                            "Entity",
                            identifier.toString()
                        );
                        return entity;
                    }
                }
            }

            sendNodeErrorMessage(
                client,
                "List \"" +
                    listName.trim() +
                    "\" item " +
                    index +
                    " is not available."
            );
            if (future != null && !future.isDone()) {
                future.complete(null);
            }
            return null;
        }
    }

    ExecutionManager.RuntimeList resolveRuntimeList(Node listNode) {
        if (listNode == null) {
            return null;
        }
        String listName = getParameterString(listNode, "List");
        if (listName == null || listName.trim().isEmpty()) {
            return null;
        }
        ExecutionManager manager = ExecutionManager.getInstance();
        Node startNode = resolveExecutionStartNode();
        return manager.getRuntimeList(startNode, listName.trim());
    }

    private Optional<Integer> resolveListLengthValue(Node listNode) {
        if (listNode == null) {
            return Optional.empty();
        }
        String listName = getParameterString(listNode, "List");
        if (listName == null || listName.trim().isEmpty()) {
            return Optional.empty();
        }
        ExecutionManager.RuntimeList list = resolveRuntimeList(listNode);
        if (list == null) {
            return Optional.of(0);
        }
        return Optional.of(Math.max(0, list.getEntries().size()));
    }

    ListSlotEntry resolveListItemSlotEntry(
        Node listNode,
        boolean reportErrors,
        CompletableFuture<Void> future
    ) {
        net.minecraft.client.MinecraftClient client =
            net.minecraft.client.MinecraftClient.getInstance();
        if (listNode == null) {
            return null;
        }
        ExecutionManager.RuntimeList list = resolveRuntimeList(listNode);
        String listName = getParameterString(listNode, "List");
        String safeListName = listName == null ? "" : listName.trim();
        if (
            list == null ||
            list.getEntries().isEmpty() ||
            list.getElementType() != NodeType.PARAM_GUI
        ) {
            return null;
        }

        int index = parseNodeInt(listNode, "Index", 1);
        if (index <= 0 || index > list.getEntries().size()) {
            if (reportErrors && client != null) {
                sendNodeErrorMessage(
                    client,
                    "List \"" + safeListName + "\" has no item " + index + "."
                );
            }
            if (reportErrors && future != null && !future.isDone()) {
                future.complete(null);
            }
            return null;
        }
        String entry = list.getEntries().get(index - 1);
        ListSlotEntry parsed = parseListSlotEntry(entry);
        if (parsed == null) {
            if (reportErrors && client != null) {
                sendNodeErrorMessage(
                    client,
                    "List \"" +
                        safeListName +
                        "\" item " +
                        index +
                        " is not a valid GUI slot."
                );
            }
            if (reportErrors && future != null && !future.isDone()) {
                future.complete(null);
            }
        }
        return parsed;
    }

    ListSlotEntry parseListSlotEntry(String entry) {
        if (entry == null) {
            return null;
        }
        String trimmed = entry.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith(LIST_SLOT_GUI_PREFIX)) {
            Integer slotIndex = parseIntOrNull(
                trimmed.substring(LIST_SLOT_GUI_PREFIX.length())
            );
            return slotIndex == null
                ? null
                : new ListSlotEntry(slotIndex, SlotSelectionType.GUI_CONTAINER);
        }
        if (trimmed.startsWith(LIST_SLOT_PLAYER_PREFIX)) {
            Integer slotIndex = parseIntOrNull(
                trimmed.substring(LIST_SLOT_PLAYER_PREFIX.length())
            );
            return slotIndex == null
                ? null
                : new ListSlotEntry(
                      slotIndex,
                      SlotSelectionType.PLAYER_INVENTORY
                  );
        }
        return null;
    }

    private void applyListSlotSelection(int slotIndex, int parameterSlotIndex) {
        if (getParameter("Slot") != null) {
            setParameterValueAndPropagate("Slot", Integer.toString(slotIndex));
            return;
        }
        if (
            getParameter("SourceSlot") != null &&
            (parameterSlotIndex <= 0 || getParameter("TargetSlot") == null)
        ) {
            setParameterValueAndPropagate(
                "SourceSlot",
                Integer.toString(slotIndex)
            );
        }
        if (getParameter("TargetSlot") != null && parameterSlotIndex == 1) {
            setParameterValueAndPropagate(
                "TargetSlot",
                Integer.toString(slotIndex)
            );
        }
    }

    Entity resolveEntityByUuid(
        net.minecraft.client.MinecraftClient client,
        java.util.UUID uuid
    ) {
        if (client == null || client.world == null || uuid == null) {
            return null;
        }
        if (CLIENT_WORLD_GET_ENTITY_BY_UUID != null) {
            try {
                Object result = CLIENT_WORLD_GET_ENTITY_BY_UUID.invoke(
                    client.world,
                    uuid
                );
                if (result instanceof Entity entity) {
                    return entity;
                }
            } catch (
                IllegalAccessException
                | java.lang.reflect.InvocationTargetException ignored
            ) {
                // fall through to manual search
            }
        }

        if (client.player != null && uuid.equals(client.player.getUuid())) {
            return client.player;
        }
        for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
            if (player != null && uuid.equals(player.getUuid())) {
                return player;
            }
        }

        double searchRadius = 96.0;
        if (client.options != null) {
            int viewDistance = client.options.getViewDistance().getValue();
            searchRadius = Math.max(searchRadius, viewDistance * 16.0);
        }
        Box searchBox =
            client.player != null
                ? client.player.getBoundingBox().expand(searchRadius)
                : new Box(
                      -searchRadius,
                      -searchRadius,
                      -searchRadius,
                      searchRadius,
                      searchRadius,
                      searchRadius
                  );
        List<Entity> matches = client.world.getOtherEntities(
            client.player,
            searchBox,
            entity -> entity != null && uuid.equals(entity.getUuid())
        );
        return matches.isEmpty() ? null : matches.get(0);
    }

    private List<Entity> findEntitiesByType(
        net.minecraft.client.MinecraftClient client,
        EntityType<?> entityType,
        double range,
        String state
    ) {
        if (
            client == null ||
            client.player == null ||
            client.world == null ||
            entityType == null
        ) {
            return Collections.emptyList();
        }
        double searchRadius = Math.max(1.0, range);
        Box searchBox = client.player.getBoundingBox().expand(searchRadius);
        return client.world.getOtherEntities(
            client.player,
            searchBox,
            entity ->
                entity.getType() == entityType &&
                EntityStateOptions.matchesState(entity, state)
        );
    }

    List<ItemEntity> findItemsByType(
        net.minecraft.client.MinecraftClient client,
        Item item,
        double range
    ) {
        if (
            client == null ||
            client.player == null ||
            client.world == null ||
            item == null
        ) {
            return Collections.emptyList();
        }
        double searchRadius = Math.max(1.0, range);
        Box searchBox = client.player.getBoundingBox().expand(searchRadius);
        return client.world.getEntitiesByClass(
            ItemEntity.class,
            searchBox,
            entity ->
                entity != null &&
                !entity.isRemoved() &&
                !entity.getStack().isEmpty() &&
                entity.getStack().isOf(item)
        );
    }

    private Optional<BlockState> getTargetedBlockState() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return Optional.empty();
        }
        Optional<BlockHitResult> hit = getCurrentBlockHitResult();
        if (hit.isEmpty()) {
            return Optional.empty();
        }
        BlockPos pos = hit.get().getBlockPos();
        if (pos == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(client.world.getBlockState(pos));
    }

    private Optional<BlockPos> getTargetedBlockPos() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return Optional.empty();
        }
        return getCurrentBlockHitResult().map(BlockHitResult::getBlockPos);
    }

    private Optional<Entity> getTargetedEntity() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return Optional.empty();
        }
        HitResult hit = client.crosshairTarget;
        if (
            !(hit instanceof EntityHitResult entityHit) ||
            hit.getType() != HitResult.Type.ENTITY
        ) {
            return Optional.empty();
        }
        Entity entity = entityHit.getEntity();
        if (entity == null || entity.isRemoved()) {
            return Optional.empty();
        }
        return Optional.of(entity);
    }

    private Optional<Direction> getLookDirection() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return Optional.empty();
        }
        Vec3d look = client.player.getRotationVec(1.0F);
        return Optional.of(Direction.getFacing(look.x, look.y, look.z));
    }

    private Optional<Integer> getCurrentHotbarSlot() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(
                PlayerInventoryBridge.getSelectedSlot(
                    client.player.getInventory()
                )
            );
        } catch (IllegalStateException ignored) {
            return Optional.empty();
        }
    }

    private Optional<Direction> getTargetedBlockFace() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return Optional.empty();
        }
        Optional<BlockHitResult> hit = getCurrentBlockHitResult();
        if (hit.isEmpty()) {
            return Optional.empty();
        }
        Direction face = hit.get().getSide();
        return face == null ? Optional.empty() : Optional.of(face);
    }

    Optional<BlockHitResult> getCurrentBlockHitResult() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return Optional.empty();
        }

        BlockHitResult freshHit = raycastBlockFromOrientation(
            client,
            client.player.getYaw(),
            client.player.getPitch(),
            Math.sqrt(DEFAULT_REACH_DISTANCE_SQUARED)
        );
        if (freshHit != null) {
            return Optional.of(freshHit);
        }

        HitResult cachedHit = client.crosshairTarget;
        if (
            cachedHit instanceof BlockHitResult blockHit &&
            cachedHit.getType() == HitResult.Type.BLOCK
        ) {
            return Optional.of(blockHit);
        }
        return Optional.empty();
    }

    Hand resolveHand(NodeParameter parameter, Hand defaultHand) {
        if (parameter == null || parameter.getStringValue() == null) {
            return defaultHand;
        }
        String value = parameter
            .getStringValue()
            .trim()
            .toLowerCase(Locale.ROOT);
        if (
            value.equals("off") ||
            value.equals("offhand") ||
            value.equals("off_hand") ||
            value.equals("off-hand")
        ) {
            return Hand.OFF_HAND;
        }
        return Hand.MAIN_HAND;
    }

    private void resetControlState() {
        runtimeState.resetControlState();
    }

    private enum SensorConditionType {
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

    private Node getAttachedParameterOfType(NodeType... allowedTypes) {
        if (!attachments.hasAttachedParameters()) {
            return null;
        }
        List<Integer> slotIndices = new ArrayList<>(
            attachments.getAttachedParameterSlotIndices()
        );
        Collections.sort(slotIndices);
        for (Integer slotIndex : slotIndices) {
            Node parameter = attachments.getAttachedParameter(slotIndex);
            if (parameter == null || !parameter.isParameterNode()) {
                continue;
            }
            NodeType parameterType = parameter.getType();
            NodeType resolvedType =
                parameterType == NodeType.LIST_ITEM
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

    private Node resolveSensorParameterNode(Node parameterNode, int slotIndex) {
        if (parameterNode == null) {
            return null;
        }
        if (parameterNode.getType() == NodeType.VARIABLE) {
            return resolveVariableValueNode(parameterNode, slotIndex, null);
        }
        return parameterNode;
    }

    public boolean evaluateSensor() {
        if (!isSensorNode()) {
            return false;
        }

        if (!reportEmptyParametersForNode(this, null)) {
            return false;
        }
        if (!reportEmptyParametersForAttachedParameters(null)) {
            return false;
        }
        if (!ensureRequiredSensorParameterAttached()) {
            this.runtimeState.lastSensorResult = false;
            return false;
        }

        boolean result = false;
        switch (type) {
            case OPERATOR_EQUALS:
                result = evaluateOperatorEquals();
                break;
            case OPERATOR_NOT:
                result = evaluateOperatorNot();
                break;
            case OPERATOR_BOOLEAN_NOT:
                result = evaluateOperatorBooleanNot();
                break;
            case OPERATOR_BOOLEAN_OR:
                result = evaluateOperatorBooleanOr();
                break;
            case OPERATOR_BOOLEAN_AND:
                result = evaluateOperatorBooleanAnd();
                break;
            case OPERATOR_BOOLEAN_XOR:
                result = evaluateOperatorBooleanXor();
                break;
            case OPERATOR_GREATER:
                result = evaluateOperatorGreater();
                break;
            case OPERATOR_LESS:
                result = evaluateOperatorLess();
                break;
            case SENSOR_TOUCHING_BLOCK: {
                String blockId = getStringParameter("Block", "stone");
                Node parameterNode = resolveSensorParameterNode(
                    getAttachedParameter(),
                    0
                );
                if (parameterNode != null) {
                    if (!providesTrait(parameterNode, NodeValueTrait.BLOCK)) {
                        sendIncompatibleParameterMessage(parameterNode);
                        break;
                    }
                    List<BlockSelection> selections =
                        resolveBlocksFromParameter(parameterNode);
                    if (!selections.isEmpty()) {
                        result = isTouchingBlock(selections);
                        break;
                    }
                }
                result = evaluateSensorCondition(
                    SensorConditionType.TOUCHING_BLOCK,
                    blockId,
                    null,
                    0,
                    0,
                    0
                );
                break;
            }
            case SENSOR_TOUCHING_ENTITY: {
                String entityId = getStringParameter("Entity", "zombie");
                Node parameterNode = resolveSensorParameterNode(
                    getAttachedParameter(),
                    0
                );
                if (parameterNode != null) {
                    if (!providesTrait(parameterNode, NodeValueTrait.ENTITY)) {
                        sendIncompatibleParameterMessage(parameterNode);
                        break;
                    }
                    String nodeEntity = getParameterString(
                        parameterNode,
                        "Entity"
                    );
                    if (nodeEntity != null && !nodeEntity.isEmpty()) {
                        entityId = nodeEntity;
                    }
                    String state = getEntityParameterState(parameterNode);
                    result = isTouchingEntity(entityId, state);
                    break;
                }
                result = evaluateSensorCondition(
                    SensorConditionType.TOUCHING_ENTITY,
                    null,
                    entityId,
                    0,
                    0,
                    0
                );
                break;
            }
            case SENSOR_AT_COORDINATES: {
                int x = getIntParameter("X", 0);
                int y = getIntParameter("Y", 64);
                int z = getIntParameter("Z", 0);
                Node parameterNode = resolveSensorParameterNode(
                    getAttachedParameter(),
                    0
                );
                if (parameterNode != null) {
                    if (
                        !providesTrait(parameterNode, NodeValueTrait.COORDINATE)
                    ) {
                        sendIncompatibleParameterMessage(parameterNode);
                        break;
                    }
                    Optional<Vec3d> resolved = resolvePositionTarget(
                        parameterNode,
                        null,
                        null
                    );
                    if (resolved.isPresent()) {
                        Vec3d vec = resolved.get();
                        x = MathHelper.floor(vec.x);
                        y = MathHelper.floor(vec.y);
                        z = MathHelper.floor(vec.z);
                    } else {
                        x = parseNodeInt(parameterNode, "X", x);
                        y = parseNodeInt(parameterNode, "Y", y);
                        z = parseNodeInt(parameterNode, "Z", z);
                    }
                }
                result = evaluateSensorCondition(
                    SensorConditionType.AT_COORDINATES,
                    null,
                    null,
                    x,
                    y,
                    z
                );
                break;
            }
            case SENSOR_TARGETED_BLOCK:
                result = getTargetedBlockState().isPresent();
                break;
            case SENSOR_TARGETED_ENTITY:
                result = getTargetedEntity().isPresent();
                break;
            case SENSOR_LOOK_DIRECTION:
                result = getLookDirection().isPresent();
                break;
            case SENSOR_CURRENT_HAND:
                result = getCurrentHotbarSlot().isPresent();
                break;
            case SENSOR_TARGETED_BLOCK_FACE:
                result = getTargetedBlockFace().isPresent();
                break;
            case SENSOR_IS_DAYTIME:
                result = isDaytime();
                break;
            case SENSOR_IS_RAINING:
                result = isRaining();
                break;
            case SENSOR_GUI_FILLED:
                result = isOpenGuiFilled();
                break;
            case SENSOR_HEALTH_BELOW: {
                double amount = MathHelper.clamp(
                    getDoubleParameter("Amount", 10.0),
                    0.0,
                    40.0
                );
                Node amountParameter = getAttachedParameterOfType(
                    NodeType.PARAM_AMOUNT,
                    NodeType.OPERATOR_RANDOM,
                    NodeType.OPERATOR_MOD
                );
                if (amountParameter != null) {
                    amount = MathHelper.clamp(
                        parseNodeDouble(amountParameter, "Amount", amount),
                        0.0,
                        40.0
                    );
                }
                result = isHealthBelow(amount);
                break;
            }
            case SENSOR_HUNGER_BELOW: {
                int amount = MathHelper.clamp(
                    getIntParameter("Amount", 10),
                    0,
                    20
                );
                Node amountParameter = getAttachedParameterOfType(
                    NodeType.PARAM_AMOUNT,
                    NodeType.OPERATOR_RANDOM,
                    NodeType.OPERATOR_MOD
                );
                if (amountParameter != null) {
                    double parsed = parseNodeDouble(
                        amountParameter,
                        "Amount",
                        amount
                    );
                    amount = MathHelper.clamp((int) Math.round(parsed), 0, 20);
                }
                result = isHungerBelow(amount);
                break;
            }
            case SENSOR_ITEM_IN_INVENTORY: {
                String itemId = getStringParameter("Item", "stone");
                boolean useAmount = isAmountInputEnabled();
                int requiredAmount = Math.max(1, getIntParameter("Amount", 1));
                Node amountNode = null;
                Node parameterNode = null;
                Node attached = resolveSensorParameterNode(
                    getAttachedParameter(),
                    0
                );
                if (attached != null) {
                    if (providesTrait(attached, NodeValueTrait.NUMBER)) {
                        amountNode = attached;
                    } else if (providesTrait(attached, NodeValueTrait.ITEM)) {
                        parameterNode = attached;
                    } else {
                        sendIncompatibleParameterMessage(attached);
                    }
                }
                if (amountNode != null) {
                    double parsed = parseNodeDouble(
                        amountNode,
                        "Amount",
                        requiredAmount
                    );
                    requiredAmount = Math.max(1, (int) Math.round(parsed));
                }
                if (parameterNode != null) {
                    List<String> nodeItems = resolveItemIdsFromParameter(
                        parameterNode
                    );
                    if (!nodeItems.isEmpty()) {
                        boolean hasAny = false;
                        for (String candidate : nodeItems) {
                            if (
                                useAmount
                                    ? hasItemAmountInInventory(
                                          candidate,
                                          requiredAmount
                                      )
                                    : hasItemInInventory(candidate)
                            ) {
                                hasAny = true;
                                break;
                            }
                        }
                        result = hasAny;
                        break;
                    }
                }
                result = useAmount
                    ? hasItemAmountInInventory(itemId, requiredAmount)
                    : hasItemInInventory(itemId);
                break;
            }
            case SENSOR_ITEM_IN_SLOT: {
                Node itemNode = resolveSensorParameterNode(
                    getAttachedParameter(0),
                    0
                );
                Node slotNode = resolveSensorParameterNode(
                    getAttachedParameter(1),
                    1
                );
                if (itemNode == null || slotNode == null) {
                    net.minecraft.client.MinecraftClient client =
                        net.minecraft.client.MinecraftClient.getInstance();
                    if (client != null) {
                        sendNodeErrorMessage(
                            client,
                            type.getDisplayName() +
                                " requires an item and slot parameter."
                        );
                    }
                    result = false;
                    break;
                }
                if (!providesTrait(itemNode, NodeValueTrait.ITEM)) {
                    sendIncompatibleParameterMessage(itemNode);
                    result = false;
                    break;
                }
                if (!providesTrait(slotNode, NodeValueTrait.INVENTORY_SLOT)) {
                    sendIncompatibleParameterMessage(slotNode);
                    result = false;
                    break;
                }
                List<String> itemIds = resolveItemIdsFromParameter(itemNode);
                if (itemIds.isEmpty()) {
                    net.minecraft.client.MinecraftClient client =
                        net.minecraft.client.MinecraftClient.getInstance();
                    if (client != null) {
                        sendNodeErrorMessage(
                            client,
                            "No item specified for " +
                                type.getDisplayName() +
                                "."
                        );
                    }
                    result = false;
                    break;
                }
                net.minecraft.client.MinecraftClient client =
                    net.minecraft.client.MinecraftClient.getInstance();
                if (client == null || client.player == null) {
                    result = false;
                    break;
                }
                PlayerInventory inventory = client.player.getInventory();
                ScreenHandler handler = client.player.currentScreenHandler;
                int slotValue = parseNodeInt(slotNode, "Slot", 0);
                SlotSelectionType selectionType =
                    resolveInventorySlotSelectionType(slotNode);
                SlotResolution resolved = resolveInventorySlot(
                    handler,
                    inventory,
                    slotValue,
                    selectionType
                );
                if (resolved == null || resolved.slot == null) {
                    sendNodeErrorMessage(
                        client,
                        type.getDisplayName() +
                            " requires a valid slot selection."
                    );
                    result = false;
                    break;
                }
                ItemStack stack = resolved.slot.getStack();
                if (stack == null || stack.isEmpty()) {
                    result = false;
                    break;
                }
                boolean useAmount = isAmountInputEnabled();
                int requiredAmount = Math.max(1, getIntParameter("Amount", 1));
                boolean matchesItem = stackMatchesAnyItem(stack, itemIds);
                result =
                    matchesItem &&
                    (!useAmount || stack.getCount() >= requiredAmount);
                break;
            }
            case SENSOR_SLOT_ITEM_COUNT: {
                Node slotNode = resolveSensorParameterNode(
                    getAttachedParameter(0),
                    0
                );
                if (
                    slotNode == null ||
                    !providesTrait(slotNode, NodeValueTrait.INVENTORY_SLOT)
                ) {
                    net.minecraft.client.MinecraftClient client =
                        net.minecraft.client.MinecraftClient.getInstance();
                    if (client != null) {
                        sendNodeErrorMessage(
                            client,
                            type.getDisplayName() +
                                " requires an inventory slot parameter."
                        );
                    }
                    result = false;
                    break;
                }
                result = resolveInventorySlotCount(slotNode).isPresent();
                break;
            }
            case SENSOR_IS_SWIMMING:
                result = isSwimming();
                break;
            case SENSOR_IS_IN_LAVA:
                result = isInLava();
                break;
            case SENSOR_IS_UNDERWATER:
                result = isUnderwater();
                break;
            case SENSOR_IS_FALLING: {
                double distance = Math.max(
                    0.0,
                    getDoubleParameter("Distance", 2.0)
                );
                result = isFalling(distance);
                break;
            }
            case SENSOR_KEY_PRESSED: {
                String key = getStringParameter("Key", "space");
                Node parameterNode = resolveSensorParameterNode(
                    getAttachedParameter(),
                    0
                );
                if (parameterNode != null) {
                    if (!providesTrait(parameterNode, NodeValueTrait.KEY)) {
                        sendIncompatibleParameterMessage(parameterNode);
                        break;
                    }
                    String parameterKey = getParameterString(
                        parameterNode,
                        "Key"
                    );
                    if (parameterKey != null && !parameterKey.isEmpty()) {
                        key = parameterKey;
                    }
                }
                result = isKeyPressed(key);
                break;
            }
            case SENSOR_IS_RENDERED: {
                String resourceId = getStringParameter("Resource", "stone");
                boolean handled = false;
                Node parameterNode = resolveSensorParameterNode(
                    getAttachedParameter(),
                    0
                );
                if (parameterNode != null) {
                    if (providesTrait(parameterNode, NodeValueTrait.ITEM)) {
                        List<String> nodeItems = resolveItemIdsFromParameter(
                            parameterNode
                        );
                        if (!nodeItems.isEmpty()) {
                            resourceId = String.join(",", nodeItems);
                        }
                    } else if (
                        providesTrait(parameterNode, NodeValueTrait.ENTITY)
                    ) {
                        String nodeEntity = getParameterString(
                            parameterNode,
                            "Entity"
                        );
                        if (nodeEntity != null && !nodeEntity.isEmpty()) {
                            String state = getEntityParameterState(
                                parameterNode
                            );
                            result = isEntityRendered(nodeEntity, state);
                            handled = true;
                        }
                    } else if (
                        providesTrait(parameterNode, NodeValueTrait.PLAYER)
                    ) {
                        String nodePlayer = getParameterString(
                            parameterNode,
                            "Player"
                        );
                        if (nodePlayer != null && !nodePlayer.isEmpty()) {
                            resourceId = nodePlayer;
                        }
                    } else if (
                        providesTrait(parameterNode, NodeValueTrait.BLOCK)
                    ) {
                        String nodeBlock = getBlockParameterValue(
                            parameterNode
                        );
                        if (nodeBlock != null && !nodeBlock.isEmpty()) {
                            resourceId = nodeBlock;
                        }
                    } else {
                        sendIncompatibleParameterMessage(parameterNode);
                    }
                }
                if (!handled) {
                    result = isResourceRendered(resourceId);
                }
                break;
            }
            case SENSOR_IS_VISIBLE: {
                String resourceId = getStringParameter("Resource", "stone");
                boolean handled = false;
                Node parameterNode = resolveSensorParameterNode(
                    getAttachedParameter(),
                    0
                );
                if (parameterNode != null) {
                    if (providesTrait(parameterNode, NodeValueTrait.ITEM)) {
                        List<String> nodeItems = resolveItemIdsFromParameter(
                            parameterNode
                        );
                        if (!nodeItems.isEmpty()) {
                            resourceId = String.join(",", nodeItems);
                        }
                    } else if (
                        providesTrait(parameterNode, NodeValueTrait.ENTITY)
                    ) {
                        String nodeEntity = getParameterString(
                            parameterNode,
                            "Entity"
                        );
                        if (nodeEntity != null && !nodeEntity.isEmpty()) {
                            String state = getEntityParameterState(
                                parameterNode
                            );
                            result = isEntityVisible(nodeEntity, state);
                            handled = true;
                        }
                    } else if (
                        providesTrait(parameterNode, NodeValueTrait.PLAYER)
                    ) {
                        String nodePlayer = getParameterString(
                            parameterNode,
                            "Player"
                        );
                        if (nodePlayer != null && !nodePlayer.isEmpty()) {
                            resourceId = nodePlayer;
                        }
                    } else if (
                        providesTrait(parameterNode, NodeValueTrait.BLOCK)
                    ) {
                        String nodeBlock = getBlockParameterValue(
                            parameterNode
                        );
                        if (nodeBlock != null && !nodeBlock.isEmpty()) {
                            resourceId = nodeBlock;
                        }
                    } else {
                        sendIncompatibleParameterMessage(parameterNode);
                    }
                }
                if (!handled) {
                    result = isResourceVisible(resourceId);
                }
                break;
            }
            case SENSOR_VILLAGER_TRADE: {
                ensureVillagerTradeNumberParameter();
                net.minecraft.client.MinecraftClient client =
                    net.minecraft.client.MinecraftClient.getInstance();
                if (client == null) {
                    result = false;
                    break;
                }
                net.minecraft.client.gui.screen.Screen currentScreen =
                    client.currentScreen;
                if (
                    !(currentScreen instanceof
                            net.minecraft.client.gui.screen.ingame.MerchantScreen)
                ) {
                    sendNodeErrorMessage(
                        client,
                        "No villager trading screen is open."
                    );
                    result = false;
                    break;
                }
                net.minecraft.client.gui.screen.ingame.MerchantScreen merchantScreen =
                    (net.minecraft.client.gui.screen.ingame.MerchantScreen) currentScreen;
                net.minecraft.screen.MerchantScreenHandler screenHandler =
                    merchantScreen.getScreenHandler();
                if (screenHandler == null) {
                    result = false;
                    break;
                }
                net.minecraft.village.TradeOfferList tradeOffers =
                    screenHandler.getRecipes();
                if (tradeOffers == null || tradeOffers.isEmpty()) {
                    result = false;
                    break;
                }
                if (shouldUseLegacyVillagerTradeSelection()) {
                    result =
                        findTradeIndexFromLegacySelection(
                            tradeOffers,
                            false,
                            false
                        ) >= 0;
                    break;
                }
                int selectedTradeNumber = getConfiguredVillagerTradeNumber();
                int tradeIndex = selectedTradeNumber - 1;
                result =
                    tradeIndex >= 0 &&
                    tradeIndex < tradeOffers.size() &&
                    tradeOffers.get(tradeIndex) != null;
                break;
            }
            case SENSOR_IN_STOCK: {
                ensureVillagerTradeNumberParameter();
                net.minecraft.client.MinecraftClient client =
                    net.minecraft.client.MinecraftClient.getInstance();
                if (client == null) {
                    result = false;
                    break;
                }
                net.minecraft.client.gui.screen.Screen currentScreen =
                    client.currentScreen;
                if (
                    !(currentScreen instanceof
                            net.minecraft.client.gui.screen.ingame.MerchantScreen)
                ) {
                    if (client != null) {
                        sendNodeErrorMessage(
                            client,
                            "No villager trading screen is open."
                        );
                    }
                    result = false;
                    break;
                }
                net.minecraft.client.gui.screen.ingame.MerchantScreen merchantScreen =
                    (net.minecraft.client.gui.screen.ingame.MerchantScreen) currentScreen;
                net.minecraft.screen.MerchantScreenHandler screenHandler =
                    merchantScreen.getScreenHandler();
                if (screenHandler == null) {
                    result = false;
                    break;
                }
                net.minecraft.village.TradeOfferList tradeOffers =
                    screenHandler.getRecipes();
                if (tradeOffers == null || tradeOffers.isEmpty()) {
                    result = false;
                    break;
                }
                if (shouldUseLegacyVillagerTradeSelection()) {
                    result =
                        findTradeIndexFromLegacySelection(
                            tradeOffers,
                            true,
                            false
                        ) >= 0;
                    break;
                }
                int selectedTradeNumber = getConfiguredVillagerTradeNumber();
                int tradeIndex = selectedTradeNumber - 1;
                result =
                    tradeIndex >= 0 &&
                    tradeIndex < tradeOffers.size() &&
                    tradeOffers.get(tradeIndex) != null &&
                    !tradeOffers.get(tradeIndex).isDisabled();
                break;
            }
            case SENSOR_CHAT_MESSAGE: {
                net.minecraft.client.MinecraftClient client =
                    net.minecraft.client.MinecraftClient.getInstance();
                Node playerNode = resolveSensorParameterNode(
                    getAttachedParameter(0),
                    0
                );
                Node messageNode = resolveSensorParameterNode(
                    getAttachedParameter(1),
                    1
                );
                if (playerNode == null || messageNode == null) {
                    if (client != null) {
                        sendNodeErrorMessage(
                            client,
                            type.getDisplayName() +
                                " requires a user and message parameter."
                        );
                    }
                    result = false;
                    break;
                }
                if (!providesTrait(playerNode, NodeValueTrait.PLAYER)) {
                    sendIncompatibleParameterMessage(playerNode);
                    result = false;
                    break;
                }
                if (!providesTrait(messageNode, NodeValueTrait.MESSAGE)) {
                    sendIncompatibleParameterMessage(messageNode);
                    result = false;
                    break;
                }
                String playerName = getParameterString(playerNode, "Player");
                String messageText = getParameterString(messageNode, "Text");
                if (messageText == null || messageText.isEmpty()) {
                    messageText = getParameterString(messageNode, "Message");
                }
                boolean anyPlayer = isAnyPlayerValue(playerName);
                if (
                    !anyPlayer &&
                    isSelfPlayerValue(playerName) &&
                    client != null &&
                    client.player != null
                ) {
                    playerName = GameProfileCompatibilityBridge.getName(
                        client.player.getGameProfile()
                    );
                }
                boolean anyMessage = isAnyMessageValue(messageText);
                boolean useAmount = isAmountInputEnabled();
                double seconds = useAmount
                    ? Math.max(0.0, getDoubleParameter("Amount", 10.0))
                    : ChatMessageTracker.getMaxRetentionSeconds();
                result = ChatMessageTracker.hasRecentMessage(
                    playerName,
                    messageText,
                    seconds,
                    anyPlayer,
                    anyMessage
                );
                break;
            }
            case SENSOR_JOINED_SERVER: {
                net.minecraft.client.MinecraftClient client =
                    net.minecraft.client.MinecraftClient.getInstance();
                Node playerNode = resolveSensorParameterNode(
                    getAttachedParameter(0),
                    0
                );
                if (playerNode == null) {
                    if (client != null) {
                        sendNodeErrorMessage(
                            client,
                            type.getDisplayName() +
                                " requires a user parameter."
                        );
                    }
                    result = false;
                    break;
                }
                if (!providesTrait(playerNode, NodeValueTrait.PLAYER)) {
                    sendIncompatibleParameterMessage(playerNode);
                    result = false;
                    break;
                }
                String playerName = getParameterString(playerNode, "Player");
                boolean anyPlayer = isAnyPlayerValue(playerName);
                if (
                    !anyPlayer &&
                    isSelfPlayerValue(playerName) &&
                    client != null &&
                    client.player != null
                ) {
                    playerName = GameProfileCompatibilityBridge.getName(
                        client.player.getGameProfile()
                    );
                }
                result = ServerJoinTracker.hasRecentJoin(
                    playerName,
                    ServerJoinTracker.getRetentionSeconds(),
                    anyPlayer
                );
                break;
            }
            case SENSOR_FABRIC_EVENT: {
                String eventName = getParameterString(this, "Event");
                if (eventName == null || eventName.trim().isEmpty()) {
                    result = false;
                    break;
                }
                double seconds = FabricEventTracker.getMaxRetentionSeconds();
                String trimmed = eventName.trim();
                if (trimmed.isEmpty() || "Any".equalsIgnoreCase(trimmed)) {
                    result = FabricEventTracker.hasAnyRecentEvent(seconds);
                    break;
                }
                result = FabricEventTracker.hasRecentEvent(trimmed, seconds);
                break;
            }
            case SENSOR_ATTRIBUTE_DETECTION:
                result = evaluateAttributeDetectionSensor();
                break;
            default:
                result = false;
                break;
        }
        result = adjustBooleanToggleResult(result);
        this.runtimeState.lastSensorResult = result;
        return result;
    }

    private boolean ensureRequiredSensorParameterAttached() {
        if (
            !isSensorNode() ||
            attachments.hasAttachedParameters() ||
            !sensorRequiresParameterNode()
        ) {
            return true;
        }
        net.minecraft.client.MinecraftClient client =
            net.minecraft.client.MinecraftClient.getInstance();
        if (client != null) {
            sendNodeErrorMessage(
                client,
                type.getDisplayName() + " requires a parameter node."
            );
        }
        return false;
    }

    private boolean sensorRequiresParameterNode() {
        return NodeTraitRegistry.isSensorParameterRequired(type);
    }

    private boolean evaluateAttributeDetectionSensor() {
        normalizeAttributeDetectionParameters();
        Node parameterNode = resolveSensorParameterNode(
            getAttachedParameter(0),
            0
        );
        net.minecraft.client.MinecraftClient client =
            net.minecraft.client.MinecraftClient.getInstance();
        if (parameterNode == null) {
            if (client != null) {
                sendNodeErrorMessage(
                    client,
                    type.getDisplayName() + " requires a target parameter."
                );
            }
            return false;
        }

        AttributeDetectionConfig.TargetKind targetKind =
            AttributeDetectionConfig.inferTargetKind(parameterNode.getType());
        if (targetKind == null) {
            sendIncompatibleParameterMessage(parameterNode);
            return false;
        }

        AttributeDetectionConfig.AttributeOption attribute =
            AttributeDetectionConfig.getAttribute(
                getParameterString(this, "Attribute")
            );
        if (attribute == null || !attribute.supports(targetKind)) {
            attribute = AttributeDetectionConfig.getDefaultAttribute(
                targetKind
            );
        }

        String expectedValue = getParameterString(this, "Value");
        if (expectedValue == null) {
            expectedValue = "";
        }

        return switch (targetKind) {
            case ENTITY, PLAYER -> evaluateEntityAttributeDetection(
                parameterNode,
                attribute,
                expectedValue
            );
            case ITEM -> evaluateItemAttributeDetection(
                parameterNode,
                attribute,
                expectedValue
            );
        };
    }

    private boolean evaluateEntityAttributeDetection(
        Node parameterNode,
        AttributeDetectionConfig.AttributeOption attribute,
        String expectedValue
    ) {
        RuntimeParameterData data = new RuntimeParameterData();
        Optional<Vec3d> resolved = resolvePositionTarget(
            parameterNode,
            data,
            null
        );
        if (resolved.isEmpty() || data.targetEntity == null) {
            return false;
        }
        Entity entity = data.targetEntity;
        return switch (attribute) {
            case NAME -> evaluateStringAttribute(
                entity.getName().getString(),
                expectedValue
            );
            case CUSTOM_NAME -> evaluateStringAttribute(
                getEntityCustomName(entity),
                expectedValue
            );
            case HAS_CUSTOM_NAME -> evaluateBooleanAttribute(
                entity.hasCustomName(),
                expectedValue
            );
            case TYPE -> evaluateStringAttribute(
                getEntityTypeId(entity),
                expectedValue
            );
            case UUID -> evaluateStringAttribute(
                entity.getUuidAsString(),
                expectedValue
            );
            case HEALTH -> entity instanceof LivingEntity livingEntity &&
                evaluateNumericAttribute(
                    livingEntity.getHealth(),
                    expectedValue
                );
            case MAX_HEALTH -> entity instanceof LivingEntity livingEntity &&
                evaluateNumericAttribute(
                    livingEntity.getMaxHealth(),
                    expectedValue
                );
            case X -> evaluateNumericAttribute(entity.getX(), expectedValue);
            case Y -> evaluateNumericAttribute(entity.getY(), expectedValue);
            case Z -> evaluateNumericAttribute(entity.getZ(), expectedValue);
            case YAW -> evaluateNumericAttribute(
                entity.getYaw(),
                expectedValue
            );
            case PITCH -> evaluateNumericAttribute(
                entity.getPitch(),
                expectedValue
            );
            case IS_ALIVE -> evaluateBooleanAttribute(
                entity.isAlive(),
                expectedValue
            );
            case IS_ON_GROUND -> evaluateBooleanAttribute(
                entity.isOnGround(),
                expectedValue
            );
            case IS_ON_FIRE -> evaluateBooleanAttribute(
                entity.isOnFire(),
                expectedValue
            );
            case IS_SNEAKING -> evaluateBooleanAttribute(
                entity.isSneaking(),
                expectedValue
            );
            case IS_SPRINTING -> evaluateBooleanAttribute(
                entity.isSprinting(),
                expectedValue
            );
            case IS_SWIMMING -> evaluateBooleanAttribute(
                entity.isSwimming(),
                expectedValue
            );
            case IS_BABY -> evaluateBooleanAttribute(
                EntityStateOptions.matchesState(entity, "age=baby"),
                expectedValue
            );
            case TAG -> evaluateTagAttribute(
                entity.getCommandTags(),
                expectedValue
            );
            default -> false;
        };
    }

    private boolean evaluateItemAttributeDetection(
        Node parameterNode,
        AttributeDetectionConfig.AttributeOption attribute,
        String expectedValue
    ) {
        Optional<ItemEntity> resolved = resolveItemEntityParameter(
            parameterNode
        );
        if (resolved.isEmpty()) {
            return false;
        }
        ItemEntity itemEntity = resolved.get();
        ItemStack stack = itemEntity.getStack();
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return switch (attribute) {
            case NAME -> evaluateStringAttribute(
                stack.getName().getString(),
                expectedValue
            );
            case CUSTOM_NAME -> evaluateStringAttribute(
                getItemCustomName(stack),
                expectedValue
            );
            case HAS_CUSTOM_NAME -> evaluateBooleanAttribute(
                stack.get(DataComponentTypes.CUSTOM_NAME) != null,
                expectedValue
            );
            case ITEM_ID -> evaluateStringAttribute(
                getItemId(stack),
                expectedValue
            );
            case COUNT -> evaluateNumericAttribute(
                stack.getCount(),
                expectedValue
            );
            case MAX_COUNT -> evaluateNumericAttribute(
                stack.getMaxCount(),
                expectedValue
            );
            case DAMAGE -> evaluateNumericAttribute(
                stack.getDamage(),
                expectedValue
            );
            case MAX_DAMAGE -> evaluateNumericAttribute(
                stack.getMaxDamage(),
                expectedValue
            );
            case X -> evaluateNumericAttribute(
                itemEntity.getX(),
                expectedValue
            );
            case Y -> evaluateNumericAttribute(
                itemEntity.getY(),
                expectedValue
            );
            case Z -> evaluateNumericAttribute(
                itemEntity.getZ(),
                expectedValue
            );
            case IS_STACKABLE -> evaluateBooleanAttribute(
                stack.isStackable(),
                expectedValue
            );
            case IS_ENCHANTED -> evaluateBooleanAttribute(
                stack.hasEnchantments(),
                expectedValue
            );
            case IS_DAMAGEABLE -> evaluateBooleanAttribute(
                stack.isDamageable(),
                expectedValue
            );
            default -> false;
        };
    }

    private Optional<ItemEntity> resolveItemEntityParameter(
        Node parameterNode
    ) {
        if (
            parameterNode == null ||
            parameterNode.getType() != NodeType.PARAM_ITEM
        ) {
            return Optional.empty();
        }
        net.minecraft.client.MinecraftClient client =
            net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return Optional.empty();
        }
        List<String> itemIds = resolveItemIdsFromParameter(parameterNode);
        if (itemIds.isEmpty()) {
            return Optional.empty();
        }
        double range = parseNodeDouble(
            parameterNode,
            "Range",
            PARAMETER_SEARCH_RADIUS
        );
        ItemEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (String candidateId : itemIds) {
            Identifier identifier = Identifier.tryParse(candidateId);
            if (identifier == null || !Registries.ITEM.containsId(identifier)) {
                continue;
            }
            Item item = Registries.ITEM.get(identifier);
            Optional<ItemEntity> candidate = findNearestDroppedItemEntity(
                client,
                item,
                range
            );
            if (candidate.isEmpty()) {
                continue;
            }
            double distance = candidate.get().squaredDistanceTo(client.player);
            if (nearest == null || distance < nearestDistance) {
                nearest = candidate.get();
                nearestDistance = distance;
            }
        }
        return Optional.ofNullable(nearest);
    }

    private Optional<ItemEntity> findNearestDroppedItemEntity(
        net.minecraft.client.MinecraftClient client,
        Item item,
        double range
    ) {
        if (
            client == null ||
            client.player == null ||
            client.world == null ||
            item == null
        ) {
            return Optional.empty();
        }
        double searchRadius = Math.max(1.0, range);
        Box searchBox = client.player.getBoundingBox().expand(searchRadius);
        List<ItemEntity> entities = client.world.getEntitiesByClass(
            ItemEntity.class,
            searchBox,
            entity ->
                entity != null &&
                !entity.isRemoved() &&
                !entity.getStack().isEmpty() &&
                entity.getStack().isOf(item)
        );
        if (entities.isEmpty()) {
            return Optional.empty();
        }
        ItemEntity nearest = Collections.min(
            entities,
            Comparator.comparingDouble(entity ->
                entity.squaredDistanceTo(client.player)
            )
        );
        return Optional.of(nearest);
    }

    private boolean evaluateStringAttribute(
        String actualValue,
        String expectedValue
    ) {
        String actual = actualValue == null ? "" : actualValue.trim();
        String expected = expectedValue == null ? "" : expectedValue.trim();
        String actualLower = actual.toLowerCase(Locale.ROOT);
        String expectedLower = expected.toLowerCase(Locale.ROOT);
        return !expectedLower.isEmpty() && actualLower.contains(expectedLower);
    }

    private boolean evaluateTagAttribute(
        Set<String> actualTags,
        String expectedValue
    ) {
        if (actualTags == null || actualTags.isEmpty()) {
            return false;
        }
        String expected =
            expectedValue == null
                ? ""
                : expectedValue.trim().toLowerCase(Locale.ROOT);
        if (expected.isEmpty()) {
            return false;
        }
        boolean matched = false;
        for (String tag : actualTags) {
            if (tag == null) {
                continue;
            }
            String candidate = tag.trim().toLowerCase(Locale.ROOT);
            if (candidate.isEmpty()) {
                continue;
            }
            matched = candidate.contains(expected);
            if (matched) {
                break;
            }
        }
        return matched;
    }

    private boolean evaluateNumericAttribute(
        double actualValue,
        String expectedValue
    ) {
        Double expected = parseDoubleOrNull(expectedValue);
        if (expected == null) {
            return false;
        }
        return actualValue >= expected;
    }

    private boolean evaluateBooleanAttribute(
        boolean actualValue,
        String expectedValue
    ) {
        boolean expected = parseBooleanLike(expectedValue);
        return actualValue == expected;
    }

    private boolean parseBooleanLike(String value) {
        return NodeAttributeParameters.parseBooleanLike(value);
    }

    private String getEntityCustomName(Entity entity) {
        if (entity == null || entity.getCustomName() == null) {
            return "";
        }
        return entity.getCustomName().getString();
    }

    private String getEntityTypeId(Entity entity) {
        if (entity == null) {
            return "";
        }
        Identifier id = Registries.ENTITY_TYPE.getId(entity.getType());
        if (id == null) {
            return "";
        }
        return "minecraft".equals(id.getNamespace())
            ? id.getPath()
            : id.toString();
    }

    private String getItemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        Identifier id = Registries.ITEM.getId(stack.getItem());
        if (id == null) {
            return "";
        }
        return "minecraft".equals(id.getNamespace())
            ? id.getPath()
            : id.toString();
    }

    private String getItemCustomName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        Text customName = stack.get(DataComponentTypes.CUSTOM_NAME);
        return customName != null ? customName.getString() : "";
    }

    private boolean evaluateOperatorEquals() {
        Optional<Boolean> result = evaluateOperatorComparison();
        return result.orElse(false);
    }

    private boolean evaluateOperatorNot() {
        Optional<Boolean> result = evaluateOperatorComparison();
        return result.map(value -> !value).orElse(false);
    }

    private boolean evaluateOperatorBooleanNot() {
        Optional<Boolean> result = evaluateOperatorBooleanOperand();
        return result.map(value -> !value).orElse(false);
    }

    private boolean evaluateOperatorBooleanOr() {
        Node left = getAttachedParameter(0);
        Node right = getAttachedParameter(1);
        if (left == null || right == null) {
            return false;
        }
        Optional<Boolean> leftValue = resolveBooleanOperandWithVariables(
            left,
            0
        );
        Optional<Boolean> rightValue = resolveBooleanOperandWithVariables(
            right,
            1
        );
        if (leftValue.isEmpty() || rightValue.isEmpty()) {
            return false;
        }
        return leftValue.get() || rightValue.get();
    }

    private boolean evaluateOperatorBooleanAnd() {
        Node left = getAttachedParameter(0);
        Node right = getAttachedParameter(1);
        if (left == null || right == null) {
            return false;
        }
        Optional<Boolean> leftValue = resolveBooleanOperandWithVariables(
            left,
            0
        );
        Optional<Boolean> rightValue = resolveBooleanOperandWithVariables(
            right,
            1
        );
        if (leftValue.isEmpty() || rightValue.isEmpty()) {
            return false;
        }
        return leftValue.get() && rightValue.get();
    }

    private boolean evaluateOperatorBooleanXor() {
        Node left = getAttachedParameter(0);
        Node right = getAttachedParameter(1);
        if (left == null || right == null) {
            return false;
        }
        Optional<Boolean> leftValue = resolveBooleanOperandWithVariables(
            left,
            0
        );
        Optional<Boolean> rightValue = resolveBooleanOperandWithVariables(
            right,
            1
        );
        if (leftValue.isEmpty() || rightValue.isEmpty()) {
            return false;
        }
        return leftValue.get() ^ rightValue.get();
    }

    private boolean evaluateOperatorGreater() {
        Optional<Boolean> result = evaluateOperatorOrdering(true);
        return result.orElse(false);
    }

    private boolean evaluateOperatorLess() {
        Optional<Boolean> result = evaluateOperatorOrdering(false);
        return result.orElse(false);
    }

    private Optional<Boolean> evaluateOperatorComparison() {
        Node left = getAttachedParameter(0);
        Node right = getAttachedParameter(1);
        return compareComparisonOperands(left, right);
    }

    private Optional<Boolean> evaluateOperatorOrdering(boolean greater) {
        Node left = getAttachedParameter(0);
        Node right = getAttachedParameter(1);
        if (left == null || right == null) {
            return Optional.empty();
        }
        Optional<Double> leftNumber = resolveComparableNumberWithVariables(
            left,
            0
        );
        Optional<Double> rightNumber = resolveComparableNumberWithVariables(
            right,
            1
        );
        if (leftNumber.isEmpty() || rightNumber.isEmpty()) {
            return Optional.empty();
        }
        boolean inclusive = getBooleanParameter("Inclusive", false);
        double l = leftNumber.get();
        double r = rightNumber.get();
        if (greater) {
            return Optional.of(inclusive ? l >= r : l > r);
        }
        return Optional.of(inclusive ? l <= r : l < r);
    }

    private Optional<Boolean> evaluateOperatorBooleanOperand() {
        Node operand = getAttachedParameter(0);
        return resolveBooleanOperandWithVariables(operand, 0);
    }

    private Optional<Boolean> compareComparisonOperands(Node left, Node right) {
        if (left == null || right == null) {
            return Optional.empty();
        }
        if (isComparisonGroupOperator(left)) {
            return compareGroupOperand(left, right);
        }
        if (isComparisonGroupOperator(right)) {
            return compareGroupOperand(right, left);
        }
        if (left.getType() == NodeType.VARIABLE) {
            left = resolveVariableValueNode(left, 0, null);
        }
        if (right.getType() == NodeType.VARIABLE) {
            right = resolveVariableValueNode(right, 1, null);
        }
        if (left == null || right == null) {
            return Optional.empty();
        }
        return compareParameterNodes(left, right);
    }

    private Optional<Boolean> compareGroupOperand(
        Node groupNode,
        Node comparisonNode
    ) {
        if (!isComparisonGroupOperator(groupNode) || comparisonNode == null) {
            return Optional.empty();
        }
        boolean requireAllMatches =
            groupNode.getType() == NodeType.OPERATOR_BOOLEAN_AND;
        boolean sawComparableOption = false;
        for (
            int slotIndex = 0;
            slotIndex < groupNode.getParameterSlotCount();
            slotIndex++
        ) {
            Node option = groupNode.getAttachedParameter(slotIndex);
            if (option == null) {
                continue;
            }
            Optional<Boolean> comparison = compareComparisonOperands(
                option,
                comparisonNode
            );
            if (comparison.isEmpty()) {
                continue;
            }
            sawComparableOption = true;
            if (requireAllMatches) {
                if (!comparison.get()) {
                    return Optional.of(false);
                }
            } else if (comparison.get()) {
                return Optional.of(true);
            }
        }
        if (!sawComparableOption) {
            return Optional.empty();
        }
        return Optional.of(requireAllMatches);
    }

    private boolean isComparisonGroupOperator(Node node) {
        if (node == null) {
            return false;
        }
        return (
            node.getType() == NodeType.OPERATOR_BOOLEAN_OR ||
            node.getType() == NodeType.OPERATOR_BOOLEAN_AND
        );
    }

    private Optional<Boolean> resolveBooleanOperandWithVariables(
        Node operand,
        int slotIndex
    ) {
        if (operand == null) {
            return Optional.empty();
        }
        if (
            operand.isSensorNode() &&
            NodeTraitRegistry.isBooleanSensor(operand.getType())
        ) {
            return Optional.of(operand.evaluateSensor());
        }
        if (operand.getType() == NodeType.VARIABLE) {
            Node resolved = resolveVariableValueNode(operand, slotIndex, null);
            if (resolved == null) {
                return Optional.empty();
            }
            return resolveBooleanFromNode(resolved);
        }
        return resolveBooleanFromNode(operand);
    }

    private Optional<Boolean> resolveBooleanFromNode(Node node) {
        if (node == null) {
            return Optional.empty();
        }
        if (node.getType() == NodeType.PARAM_BOOLEAN) {
            node.ensureBooleanParameters();
            if (node.isBooleanModeVariable()) {
                NodeParameter variableParameter = node.getParameter("Variable");
                String variableValue =
                    variableParameter != null
                        ? variableParameter.getStringValue()
                        : null;
                return node.resolveBooleanValueFromRaw(variableValue, true);
            }
            NodeParameter parameter = node.getParameter("Toggle");
            String value =
                parameter != null ? parameter.getStringValue() : null;
            if (
                (value == null || value.trim().isEmpty()) && parameter != null
            ) {
                value = parameter.getDefaultValue();
            }
            return node.resolveBooleanValueFromRaw(value, false);
        }
        return Optional.empty();
    }

    private Optional<Boolean> compareVariableNodes(Node left, Node right) {
        if (left == null || right == null) {
            return Optional.empty();
        }
        boolean leftIsVariable = left.getType() == NodeType.VARIABLE;
        boolean rightIsVariable = right.getType() == NodeType.VARIABLE;
        ExecutionManager manager = ExecutionManager.getInstance();
        Node startNode = getOwningStartNode();
        if (startNode == null && getParentControl() != null) {
            startNode = getParentControl().getOwningStartNode();
        }
        if (leftIsVariable && rightIsVariable) {
            String leftName = getParameterString(left, "Variable");
            String rightName = getParameterString(right, "Variable");
            if (
                leftName == null ||
                leftName.trim().isEmpty() ||
                rightName == null ||
                rightName.trim().isEmpty()
            ) {
                return Optional.empty();
            }
            ExecutionManager.RuntimeVariable leftVar =
                manager.getRuntimeVariable(startNode, leftName.trim());
            ExecutionManager.RuntimeVariable rightVar =
                manager.getRuntimeVariable(startNode, rightName.trim());
            if (leftVar == null || rightVar == null) {
                return Optional.empty();
            }
            Node leftSnapshot = createRuntimeVariableSnapshot(leftVar);
            Node rightSnapshot = createRuntimeVariableSnapshot(rightVar);
            if (leftSnapshot == null || rightSnapshot == null) {
                return Optional.empty();
            }
            return compareParameterNodes(leftSnapshot, rightSnapshot);
        }
        Node variableNode = leftIsVariable ? left : right;
        Node valueNode = leftIsVariable ? right : left;
        String variableName = getParameterString(variableNode, "Variable");
        if (variableName == null || variableName.trim().isEmpty()) {
            return Optional.empty();
        }
        ExecutionManager.RuntimeVariable variable = manager.getRuntimeVariable(
            startNode,
            variableName.trim()
        );
        if (variable == null) {
            return Optional.empty();
        }
        Node variableSnapshot = createRuntimeVariableSnapshot(variable);
        if (variableSnapshot == null) {
            return Optional.empty();
        }
        return compareParameterNodes(variableSnapshot, valueNode);
    }

    private Node createRuntimeVariableSnapshot(
        ExecutionManager.RuntimeVariable runtimeVariable
    ) {
        if (runtimeVariable == null || runtimeVariable.getType() == null) {
            return null;
        }
        NodeType runtimeType = runtimeVariable.getType();
        NodeType snapshotType =
            runtimeType == NodeType.LIST_LENGTH
                ? NodeType.PARAM_AMOUNT
                : runtimeType;
        Node snapshot = new Node(snapshotType, 0, 0);
        snapshot.setSocketsHidden(true);
        Map<String, String> values = runtimeVariable.getValues();
        if (values != null && !values.isEmpty()) {
            snapshot.applyParameterValuesFromMap(values);
        }
        return snapshot;
    }

    Optional<Boolean> compareParameterNodes(Node left, Node right) {
        if (left == null || right == null) {
            return Optional.empty();
        }
        Optional<Boolean> leftBoolean = resolveComparableBoolean(left);
        Optional<Boolean> rightBoolean = resolveComparableBoolean(right);
        if (leftBoolean.isPresent() && rightBoolean.isPresent()) {
            return Optional.of(leftBoolean.get().equals(rightBoolean.get()));
        }
        if (leftBoolean.isPresent() || rightBoolean.isPresent()) {
            return Optional.empty();
        }
        Map<String, String> leftValues = left.exportParameterValues();
        Map<String, String> rightValues = right.exportParameterValues();
        Optional<Boolean> emptyTargetedBlockComparison =
            compareEmptyTargetedBlockValues(
                left,
                leftValues,
                right,
                rightValues
            );
        if (emptyTargetedBlockComparison.isPresent()) {
            return emptyTargetedBlockComparison;
        }
        if (
            leftValues != null &&
            !leftValues.isEmpty() &&
            rightValues != null &&
            !rightValues.isEmpty()
        ) {
            Optional<Boolean> blockComparison = compareBlockSelectionValues(
                leftValues,
                rightValues
            );
            if (blockComparison.isPresent()) {
                return blockComparison;
            }
            Optional<Boolean> entityComparison = compareEntitySelectionValues(
                leftValues,
                rightValues
            );
            if (entityComparison.isPresent()) {
                return entityComparison;
            }
            Optional<Boolean> inventorySlotComparison =
                compareInventorySlotValues(
                    left,
                    leftValues,
                    right,
                    rightValues
                );
            if (inventorySlotComparison.isPresent()) {
                return inventorySlotComparison;
            }
            Optional<Boolean> itemComparison = compareItemSelectionValues(
                leftValues,
                rightValues
            );
            if (itemComparison.isPresent()) {
                return itemComparison;
            }
        }
        Optional<Double> leftNumber = resolveComparableNumber(left);
        Optional<Double> rightNumber = resolveComparableNumber(right);
        if (leftNumber.isPresent() && rightNumber.isPresent()) {
            return Optional.of(
                Double.compare(leftNumber.get(), rightNumber.get()) == 0
            );
        }
        if (leftNumber.isPresent() || rightNumber.isPresent()) {
            return Optional.empty();
        }
        Optional<String> leftString = resolveComparableString(left);
        Optional<String> rightString = resolveComparableString(right);
        if (leftString.isPresent() && rightString.isPresent()) {
            String l = leftString.get();
            String r = rightString.get();
            return Optional.of(l.equalsIgnoreCase(r));
        }
        if (leftString.isPresent() || rightString.isPresent()) {
            return Optional.empty();
        }
        if (
            leftValues == null ||
            rightValues == null ||
            leftValues.isEmpty() ||
            rightValues.isEmpty()
        ) {
            return Optional.empty();
        }
        return Optional.of(
            canonicalizeValueMap(leftValues).equals(
                canonicalizeValueMap(rightValues)
            )
        );
    }

    private Map<String, String> canonicalizeValueMap(
        Map<String, String> values
    ) {
        Map<String, String> canonical = new TreeMap<>();
        if (values == null || values.isEmpty()) {
            return canonical;
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry == null || entry.getKey() == null) {
                continue;
            }
            String normalizedKey = normalizeParameterKey(entry.getKey());
            if (normalizedKey.isEmpty()) {
                continue;
            }
            String value =
                entry.getValue() == null ? "" : entry.getValue().trim();
            if (value.isEmpty()) {
                continue;
            }
            canonical.putIfAbsent(normalizedKey, value);
        }
        return canonical;
    }

    private String formatCanonicalValueMap(Map<String, String> values) {
        Map<String, String> canonical = canonicalizeValueMap(values);
        if (canonical.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : canonical.entrySet()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }

    private Optional<Boolean> resolveComparableBoolean(Node node) {
        if (node == null) {
            return Optional.empty();
        }
        if (node.getType() == NodeType.LIST_ITEM) {
            Node resolved = resolveListItemValueNode(node, null, false, null);
            return resolved != null
                ? resolveComparableBoolean(resolved)
                : Optional.empty();
        }
        if (
            node.isSensorNode() &&
            NodeTraitRegistry.isBooleanSensor(node.getType())
        ) {
            return Optional.of(node.evaluateSensor());
        }
        return resolveBooleanFromNode(node);
    }

    private Optional<Boolean> compareEmptyTargetedBlockValues(
        Node left,
        Map<String, String> leftValues,
        Node right,
        Map<String, String> rightValues
    ) {
        if (left == null || right == null) {
            return Optional.empty();
        }
        boolean leftMissingTargetedBlock =
            left.getType() == NodeType.SENSOR_TARGETED_BLOCK &&
            (leftValues == null || leftValues.isEmpty());
        boolean rightMissingTargetedBlock =
            right.getType() == NodeType.SENSOR_TARGETED_BLOCK &&
            (rightValues == null || rightValues.isEmpty());

        if (leftMissingTargetedBlock && rightMissingTargetedBlock) {
            return Optional.of(true);
        }
        if (leftMissingTargetedBlock && isBlockComparableNode(right)) {
            return Optional.of(false);
        }
        if (rightMissingTargetedBlock && isBlockComparableNode(left)) {
            return Optional.of(false);
        }
        boolean leftMissingTargetedEntity =
            left.getType() == NodeType.SENSOR_TARGETED_ENTITY &&
            (leftValues == null || leftValues.isEmpty());
        boolean rightMissingTargetedEntity =
            right.getType() == NodeType.SENSOR_TARGETED_ENTITY &&
            (rightValues == null || rightValues.isEmpty());
        if (leftMissingTargetedEntity && rightMissingTargetedEntity) {
            return Optional.of(true);
        }
        if (leftMissingTargetedEntity && isEntityComparableNode(right)) {
            return Optional.of(false);
        }
        if (rightMissingTargetedEntity && isEntityComparableNode(left)) {
            return Optional.of(false);
        }
        return Optional.empty();
    }

    private boolean isBlockComparableNode(Node node) {
        if (node == null) {
            return false;
        }
        if (
            node.getType() == NodeType.PARAM_BLOCK ||
            node.getType() == NodeType.SENSOR_TARGETED_BLOCK
        ) {
            return true;
        }
        Map<String, String> values = node.exportParameterValues();
        return values != null && !getRuntimeValue(values, "block").isEmpty();
    }

    private boolean isEntityComparableNode(Node node) {
        if (node == null) {
            return false;
        }
        if (
            node.getType() == NodeType.PARAM_ENTITY ||
            node.getType() == NodeType.SENSOR_TARGETED_ENTITY
        ) {
            return true;
        }
        Map<String, String> values = node.exportParameterValues();
        return values != null && !getRuntimeValue(values, "entity").isEmpty();
    }

    private Optional<Boolean> compareBlockSelectionValues(
        Map<String, String> leftValues,
        Map<String, String> rightValues
    ) {
        String leftBlock = getRuntimeValue(leftValues, "block");
        String rightBlock = getRuntimeValue(rightValues, "block");
        if (leftBlock.isEmpty() || rightBlock.isEmpty()) {
            return Optional.empty();
        }
        boolean leftWildcard = isAnySelectionValue(leftBlock);
        boolean rightWildcard = isAnySelectionValue(rightBlock);
        String leftCombined = normalizeBlockSelection(leftBlock, "");
        String rightCombined = normalizeBlockSelection(rightBlock, "");
        if (
            !leftWildcard &&
            !rightWildcard &&
            (leftCombined.isEmpty() || rightCombined.isEmpty())
        ) {
            return Optional.empty();
        }
        if (
            !leftWildcard &&
            !rightWildcard &&
            !leftCombined.equalsIgnoreCase(rightCombined)
        ) {
            return Optional.of(false);
        }
        String leftState = getRuntimeValue(leftValues, "state");
        String rightState = getRuntimeValue(rightValues, "state");
        return Optional.of(statesMatch(leftState, rightState));
    }

    private Optional<Boolean> compareEntitySelectionValues(
        Map<String, String> leftValues,
        Map<String, String> rightValues
    ) {
        String leftEntity = getRuntimeValue(leftValues, "entity");
        String rightEntity = getRuntimeValue(rightValues, "entity");
        if (leftEntity.isEmpty() || rightEntity.isEmpty()) {
            return Optional.empty();
        }
        boolean leftWildcard = isAnySelectionValue(leftEntity);
        boolean rightWildcard = isAnySelectionValue(rightEntity);
        String leftCombined = normalizeEntitySelection(leftEntity, "");
        String rightCombined = normalizeEntitySelection(rightEntity, "");
        if (
            !leftWildcard &&
            !rightWildcard &&
            (leftCombined.isEmpty() || rightCombined.isEmpty())
        ) {
            return Optional.empty();
        }
        if (
            !leftWildcard &&
            !rightWildcard &&
            !leftCombined.equalsIgnoreCase(rightCombined)
        ) {
            return Optional.of(false);
        }
        String leftState = getRuntimeValue(leftValues, "state");
        String rightState = getRuntimeValue(rightValues, "state");
        return Optional.of(statesMatch(leftState, rightState));
    }

    private Optional<Boolean> compareItemSelectionValues(
        Map<String, String> leftValues,
        Map<String, String> rightValues
    ) {
        List<String> leftItems = resolveComparableItemSelections(leftValues);
        List<String> rightItems = resolveComparableItemSelections(rightValues);
        if (leftItems.isEmpty() || rightItems.isEmpty()) {
            return Optional.empty();
        }
        if (!selectionsOverlap(leftItems, rightItems)) {
            return Optional.of(false);
        }

        Optional<Integer> leftCount = resolveComparableItemCount(leftValues);
        Optional<Integer> rightCount = resolveComparableItemCount(rightValues);
        if (leftCount.isPresent() && rightCount.isPresent()) {
            return Optional.of(
                leftCount.get().intValue() == rightCount.get().intValue()
            );
        }
        return Optional.of(true);
    }

    private Optional<Boolean> compareInventorySlotValues(
        Node left,
        Map<String, String> leftValues,
        Node right,
        Map<String, String> rightValues
    ) {
        boolean leftIsSlot = isInventorySlotComparableNode(left, leftValues);
        boolean rightIsSlot = isInventorySlotComparableNode(right, rightValues);
        if (!leftIsSlot && !rightIsSlot) {
            return Optional.empty();
        }

        if (leftIsSlot && rightIsSlot) {
            Integer leftSlot = resolveComparableSlotIndex(leftValues);
            Integer rightSlot = resolveComparableSlotIndex(rightValues);
            if (leftSlot == null || rightSlot == null) {
                return Optional.empty();
            }
            return Optional.of(
                leftSlot.intValue() == rightSlot.intValue() &&
                    resolveComparableSlotSelectionType(leftValues) ==
                        resolveComparableSlotSelectionType(rightValues)
            );
        }

        Map<String, String> slotValues = leftIsSlot ? leftValues : rightValues;
        Map<String, String> itemValues = leftIsSlot ? rightValues : leftValues;
        List<String> itemSelections = resolveComparableItemSelections(
            itemValues
        );
        if (itemSelections.isEmpty()) {
            return Optional.empty();
        }

        // Prefer the slot's already-exported item/count snapshot when available so
        // LIST_ITEM(gui) comparisons do not depend on a second live handler lookup.
        List<String> slotSelections = resolveComparableItemSelections(
            slotValues
        );
        if (!slotSelections.isEmpty()) {
            if (!selectionsOverlap(slotSelections, itemSelections)) {
                return Optional.of(false);
            }

            Optional<Integer> slotCount = resolveComparableItemCount(
                slotValues
            );
            Optional<Integer> requiredCount = resolveComparableItemCount(
                itemValues
            );
            if (slotCount.isPresent() && requiredCount.isPresent()) {
                return Optional.of(
                    slotCount.get().intValue() == requiredCount.get().intValue()
                );
            }
            return Optional.of(true);
        }

        ItemStack stack = resolveComparableInventorySlotStack(slotValues);
        if (stack == null || stack.isEmpty()) {
            return Optional.of(false);
        }
        if (!stackMatchesAnyItem(stack, itemSelections)) {
            return Optional.of(false);
        }

        Optional<Integer> requiredCount = resolveComparableItemCount(
            itemValues
        );
        if (requiredCount.isPresent()) {
            return Optional.of(
                stack.getCount() == requiredCount.get().intValue()
            );
        }
        return Optional.of(true);
    }

    private boolean isInventorySlotComparableNode(
        Node node,
        Map<String, String> values
    ) {
        if (node != null && node.getType() == NodeType.PARAM_INVENTORY_SLOT) {
            return true;
        }
        return resolveComparableSlotIndex(values) != null;
    }

    private Integer resolveComparableSlotIndex(Map<String, String> values) {
        return InventorySlotValueResolver.resolveComparableSlotIndex(values);
    }

    private SlotSelectionType resolveComparableSlotSelectionType(
        Map<String, String> values
    ) {
        return InventorySlotValueResolver.resolveComparableSlotSelectionType(
            values
        );
    }

    private ItemStack resolveComparableInventorySlotStack(
        Map<String, String> values
    ) {
        return InventorySlotValueResolver.resolveComparableInventorySlotStack(
            values
        );
    }

    private List<String> resolveComparableItemSelections(
        Map<String, String> values
    ) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> itemIds = new ArrayList<>();
        for (String entry : splitMultiValueList(
            getRuntimeValue(values, "items")
        )) {
            addItemIdentifier(itemIds, entry);
        }
        for (String entry : splitMultiValueList(
            getRuntimeValue(values, "item")
        )) {
            addItemIdentifier(itemIds, entry);
        }
        return itemIds;
    }

    private Optional<Integer> resolveComparableItemCount(
        Map<String, String> values
    ) {
        if (values == null || values.isEmpty()) {
            return Optional.empty();
        }
        Integer count = parseIntOrNull(getRuntimeValue(values, "count"));
        if (count != null) {
            return Optional.of(count);
        }
        Integer amount = parseIntOrNull(getRuntimeValue(values, "amount"));
        return amount != null ? Optional.of(amount) : Optional.empty();
    }

    private boolean selectionsOverlap(
        List<String> leftValues,
        List<String> rightValues
    ) {
        if (
            leftValues == null ||
            rightValues == null ||
            leftValues.isEmpty() ||
            rightValues.isEmpty()
        ) {
            return false;
        }
        for (String left : leftValues) {
            String normalizedLeft = normalizeComparableItemSelection(left);
            if (normalizedLeft.isEmpty()) {
                continue;
            }
            for (String right : rightValues) {
                String normalizedRight = normalizeComparableItemSelection(
                    right
                );
                if (
                    !normalizedRight.isEmpty() &&
                    normalizedLeft.equalsIgnoreCase(normalizedRight)
                ) {
                    return true;
                }
            }
        }
        return false;
    }

    private String normalizeComparableItemSelection(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = sanitizeResourceId(value);
        if (sanitized == null || sanitized.isEmpty()) {
            return "";
        }
        return normalizeResourceId(sanitized, "minecraft");
    }

    private boolean statesMatch(String leftState, String rightState) {
        boolean leftWildcard = isAnySelectionValue(leftState);
        boolean rightWildcard = isAnySelectionValue(rightState);
        if (leftWildcard || rightWildcard) {
            return true;
        }
        Set<String> leftParts = splitSelectionParts(leftState);
        Set<String> rightParts = splitSelectionParts(rightState);
        if (leftParts.isEmpty() || rightParts.isEmpty()) {
            return true;
        }
        return (
            leftParts.containsAll(rightParts) ||
            rightParts.containsAll(leftParts)
        );
    }

    private Set<String> splitSelectionParts(String rawState) {
        if (isAnySelectionValue(rawState)) {
            return Collections.emptySet();
        }
        Set<String> parts = new LinkedHashSet<>();
        if (rawState == null) {
            return parts;
        }
        for (String part : rawState.split(",")) {
            if (part == null) {
                continue;
            }
            String trimmed = part.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty()) {
                parts.add(trimmed);
            }
        }
        return parts;
    }

    private String normalizeEntitySelection(String entity, String state) {
        if (entity == null || entity.trim().isEmpty()) {
            return "";
        }
        String normalizedEntity = normalizeResourceId(entity, "minecraft");
        if (normalizedEntity == null || normalizedEntity.isEmpty()) {
            return "";
        }
        String trimmedState = state == null ? "" : state.trim();
        if (trimmedState.isEmpty()) {
            return normalizedEntity;
        }
        return (
            normalizedEntity + "[" + trimmedState.toLowerCase(Locale.ROOT) + "]"
        );
    }

    private String normalizeBlockSelection(String block, String state) {
        if (block == null || block.trim().isEmpty()) {
            return "";
        }
        String normalizedBlock = normalizeResourceId(block, "minecraft");
        if (normalizedBlock == null || normalizedBlock.isEmpty()) {
            return "";
        }
        String trimmedState = state == null ? "" : state.trim();
        if (trimmedState.isEmpty()) {
            return normalizedBlock;
        }
        return BlockSelection.combine(normalizedBlock, trimmedState).orElse(
            normalizedBlock + "[" + trimmedState + "]"
        );
    }

    private Optional<String> resolveComparableString(Node node) {
        if (node == null) {
            return Optional.empty();
        }
        if (node.getType() == NodeType.LIST_ITEM) {
            Node resolved = resolveListItemValueNode(node, null, false, null);
            return resolved != null
                ? resolveComparableString(resolved)
                : Optional.empty();
        }
        NodeBehaviorDefinition behaviorDefinition =
            NodeBehaviorDefinitionRegistry.get(node.getType());
        return behaviorDefinition != null
            ? behaviorDefinition.resolveComparableString(this, node)
            : Optional.empty();
    }

    private Optional<Double> resolveComparableNumber(Node node) {
        if (node == null) {
            return Optional.empty();
        }
        if (node.getType() == NodeType.LIST_ITEM) {
            Node resolved = resolveListItemValueNode(node, null, false, null);
            return resolved != null
                ? resolveComparableNumber(resolved)
                : Optional.empty();
        }
        NodeBehaviorDefinition behaviorDefinition =
            NodeBehaviorDefinitionRegistry.get(node.getType());
        return behaviorDefinition != null
            ? behaviorDefinition.resolveComparableNumber(this, node)
            : Optional.empty();
    }

    private Optional<Double> resolveComparableNumberWithVariables(
        Node node,
        int slotIndex
    ) {
        if (node == null) {
            return Optional.empty();
        }
        if (node.getType() == NodeType.VARIABLE) {
            Node resolved = resolveVariableValueNode(node, slotIndex, null);
            if (resolved == null) {
                return Optional.empty();
            }
            return resolveComparableNumber(resolved);
        }
        return resolveComparableNumber(node);
    }

    Optional<Integer> resolveInventorySlotCount(Node slotNode) {
        if (
            slotNode == null ||
            !providesTrait(slotNode, NodeValueTrait.INVENTORY_SLOT)
        ) {
            return Optional.empty();
        }
        net.minecraft.client.MinecraftClient client =
            net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return Optional.empty();
        }
        PlayerInventory inventory = client.player.getInventory();
        ScreenHandler handler = client.player.currentScreenHandler;
        int slotValue = parseNodeInt(slotNode, "Slot", 0);
        SlotSelectionType selectionType = resolveInventorySlotSelectionType(
            slotNode
        );
        SlotResolution resolved = resolveInventorySlot(
            handler,
            inventory,
            slotValue,
            selectionType
        );
        if (resolved == null || resolved.slot == null) {
            return Optional.empty();
        }
        ItemStack stack = resolved.slot.getStack();
        if (stack == null || stack.isEmpty()) {
            return Optional.of(0);
        }
        return Optional.of(stack.getCount());
    }

    private boolean adjustBooleanToggleResult(boolean rawResult) {
        if (!hasBooleanToggle()) {
            return rawResult;
        }
        return booleanToggleValue == rawResult;
    }

    boolean evaluateConditionFromParameters() {
        if (attachments.getAttachedSensor() != null) {
            boolean result = attachments.getAttachedSensor().evaluateSensor();
            this.runtimeState.lastSensorResult = result;
            return result;
        }

        // Legacy fallback when no sensor is attached
        String condition = getStringParameter("Condition", "Touching Block");
        String blockId = getStringParameter("Block", "stone");
        String entityId = getStringParameter("Entity", "zombie");
        int x = getIntParameter("X", 0);
        int y = getIntParameter("Y", 64);
        int z = getIntParameter("Z", 0);
        boolean result = evaluateSensorCondition(
            SensorConditionType.fromLabel(condition),
            blockId,
            entityId,
            x,
            y,
            z
        );
        this.runtimeState.lastSensorResult = result;
        return result;
    }

    private boolean evaluateSensorCondition(
        SensorConditionType type,
        String blockId,
        String entityId,
        int x,
        int y,
        int z
    ) {
        if (type == null) {
            type = SensorConditionType.TOUCHING_BLOCK;
        }
        switch (type) {
            case TOUCHING_BLOCK:
                return isTouchingBlock(blockId);
            case TOUCHING_ENTITY:
                return isTouchingEntity(entityId);
            case AT_COORDINATES:
                return isAtCoordinates(x, y, z);
            default:
                return false;
        }
    }

    private boolean isTouchingBlock(String blockId) {
        return isTouchingBlock(parseBlockSelectionList(blockId));
    }

    private boolean isTouchingBlock(List<BlockSelection> selections) {
        if (selections == null || selections.isEmpty()) {
            return false;
        }
        net.minecraft.client.MinecraftClient client =
            net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        net.minecraft.world.World world = EntityCompatibilityBridge.getWorld(
            client.player
        );
        if (world == null) {
            return false;
        }
        Box box = client.player.getBoundingBox().expand(0.05);
        int minX = MathHelper.floor(box.minX);
        int maxX = MathHelper.floor(box.maxX);
        int minY = MathHelper.floor(box.minY);
        int maxY = MathHelper.floor(box.maxY);
        int minZ = MathHelper.floor(box.minZ);
        int maxZ = MathHelper.floor(box.maxZ);
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    mutable.set(bx, by, bz);
                    BlockState state = world.getBlockState(mutable);
                    if (matchesAnyBlock(selections, state)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isTouchingEntity(String entityId) {
        return isTouchingEntity(entityId, "");
    }

    private boolean isTouchingEntity(String entityId, String state) {
        net.minecraft.client.MinecraftClient client =
            net.minecraft.client.MinecraftClient.getInstance();
        if (
            client == null ||
            client.player == null ||
            entityId == null ||
            entityId.isEmpty()
        ) {
            return false;
        }
        net.minecraft.world.World world = EntityCompatibilityBridge.getWorld(
            client.player
        );
        if (world == null) {
            return false;
        }
        for (String candidateId : splitMultiValueList(entityId)) {
            String sanitized = sanitizeResourceId(candidateId);
            String normalized =
                sanitized != null && !sanitized.isEmpty()
                    ? normalizeResourceId(sanitized, "minecraft")
                    : candidateId;
            Identifier identifier = Identifier.tryParse(normalized);
            if (
                identifier == null ||
                !Registries.ENTITY_TYPE.containsId(identifier)
            ) {
                continue;
            }
            EntityType<?> entityType = Registries.ENTITY_TYPE.get(identifier);
            List<Entity> entities = world.getOtherEntities(
                client.player,
                client.player.getBoundingBox().expand(0.15),
                entity ->
                    entity.getType() == entityType &&
                    EntityStateOptions.matchesState(entity, state)
            );
            if (!entities.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean isAtCoordinates(int x, int y, int z) {
        net.minecraft.client.MinecraftClient client =
            net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        BlockPos playerPos = client.player.getBlockPos();
        return (
            playerPos.getX() == x &&
            playerPos.getY() == y &&
            playerPos.getZ() == z
        );
    }

    private boolean isBlockAhead(String blockId) {
        return isBlockAhead(parseBlockSelectionList(blockId));
    }

    private boolean isBlockAhead(List<BlockSelection> selections) {
        if (selections == null || selections.isEmpty()) {
            return false;
        }
        net.minecraft.client.MinecraftClient client =
            net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        net.minecraft.world.World world = EntityCompatibilityBridge.getWorld(
            client.player
        );
        if (world == null) {
            return false;
        }
        Direction facing = client.player.getHorizontalFacing();
        BlockPos targetPos = client.player.getBlockPos().offset(facing);
        BlockState state = world.getBlockState(targetPos);
        return matchesAnyBlock(selections, state);
    }

    private boolean isBlockBelow(String blockId) {
        return isBlockBelow(parseBlockSelectionList(blockId));
    }

    private boolean isBlockBelow(List<BlockSelection> selections) {
        if (selections == null || selections.isEmpty()) {
            return false;
        }
        net.minecraft.client.MinecraftClient client =
            net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        net.minecraft.world.World world = EntityCompatibilityBridge.getWorld(
            client.player
        );
        if (world == null) {
            return false;
        }
        BlockPos below = client.player.getBlockPos().down();
        BlockState state = world.getBlockState(below);
        return matchesAnyBlock(selections, state);
    }

    private List<BlockSelection> parseBlockSelectionList(String blockId) {
        if (blockId == null || blockId.isEmpty()) {
            return Collections.emptyList();
        }
        List<BlockSelection> selections = new ArrayList<>();
        for (String entry : splitMultiValueList(blockId)) {
            BlockSelection.parse(entry).ifPresent(selections::add);
        }
        return selections;
    }

    private boolean matchesAnyBlock(
        List<BlockSelection> selections,
        BlockState state
    ) {
        if (selections == null || selections.isEmpty() || state == null) {
            return false;
        }
        for (BlockSelection selection : selections) {
            if (selection != null && selection.matches(state)) {
                return true;
            }
        }
        return false;
    }

    private boolean isDaytime() {
        net.minecraft.client.MinecraftClient client =
            net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return false;
        }
        long time = client.world.getTimeOfDay() % 24000L;
        return time < 12000L;
    }

    private boolean isRaining() {
        net.minecraft.client.MinecraftClient client =
            net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null) {
            return false;
        }
        return (
            client.world.isRaining() ||
            client.world.hasRain(client.player.getBlockPos())
        );
    }

    private boolean isKeyPressed(String keyName) {
        net.minecraft.client.MinecraftClient client =
            net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return false;
        }
        if (!isKeyPressedActivatesInGuis() && client.currentScreen != null) {
            return false;
        }
        Integer keyCode = resolveKeyCode(keyName);
        if (keyCode == null) {
            return false;
        }
        return InputCompatibilityBridge.isKeyPressed(client, keyCode);
    }

    Integer resolveKeyCode(String keyName) {
        if (keyName == null) {
            return null;
        }
        String trimmed = keyName.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(trimmed);
        } catch (NumberFormatException ignored) {}
        Integer glfwCode = resolveGlfwKeyCode(trimmed);
        if (glfwCode != null) {
            return glfwCode;
        }
        InputUtil.Key key = InputUtil.fromTranslationKey(trimmed);
        int code = key.getCode();
        if (code != GLFW.GLFW_KEY_UNKNOWN) {
            return code;
        }
        String normalized = trimmed.toLowerCase(Locale.ROOT).replace(" ", "_");
        InputUtil.Key normalizedKey = InputUtil.fromTranslationKey(
            "key.keyboard." + normalized
        );
        int normalizedCode = normalizedKey.getCode();
        if (normalizedCode != GLFW.GLFW_KEY_UNKNOWN) {
            return normalizedCode;
        }
        return null;
    }

    Integer resolveMouseButtonCode(String buttonName) {
        if (buttonName == null) {
            return null;
        }
        String trimmed = buttonName.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(trimmed);
        } catch (NumberFormatException ignored) {}
        return switch (trimmed.toUpperCase(Locale.ROOT)) {
            case
                "GLFW_MOUSE_BUTTON_LEFT",
                "MOUSE_LEFT",
                "LEFT" -> GLFW.GLFW_MOUSE_BUTTON_LEFT;
            case
                "GLFW_MOUSE_BUTTON_RIGHT",
                "MOUSE_RIGHT",
                "RIGHT" -> GLFW.GLFW_MOUSE_BUTTON_RIGHT;
            case
                "GLFW_MOUSE_BUTTON_MIDDLE",
                "MOUSE_MIDDLE",
                "MIDDLE" -> GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
            case
                "GLFW_MOUSE_BUTTON_4",
                "MOUSE_4",
                "BUTTON_4" -> GLFW.GLFW_MOUSE_BUTTON_4;
            case
                "GLFW_MOUSE_BUTTON_5",
                "MOUSE_5",
                "BUTTON_5" -> GLFW.GLFW_MOUSE_BUTTON_5;
            case
                "GLFW_MOUSE_BUTTON_6",
                "MOUSE_6",
                "BUTTON_6" -> GLFW.GLFW_MOUSE_BUTTON_6;
            case
                "GLFW_MOUSE_BUTTON_7",
                "MOUSE_7",
                "BUTTON_7" -> GLFW.GLFW_MOUSE_BUTTON_7;
            case
                "GLFW_MOUSE_BUTTON_8",
                "MOUSE_8",
                "BUTTON_8" -> GLFW.GLFW_MOUSE_BUTTON_8;
            default -> null;
        };
    }

    private Integer resolveGlfwKeyCode(String keyName) {
        String normalized = keyName
            .trim()
            .toUpperCase(Locale.ROOT)
            .replace(" ", "_");
        if (!normalized.startsWith("GLFW_KEY_")) {
            normalized = "GLFW_KEY_" + normalized;
        }
        try {
            Field field = GLFW.class.getField(normalized);
            if (field.getType() == int.class) {
                return field.getInt(null);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
        return null;
    }

    private boolean isHealthBelow(double amount) {
        net.minecraft.client.MinecraftClient client =
            net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        return client.player.getHealth() < amount;
    }

    private boolean isHungerBelow(int amount) {
        net.minecraft.client.MinecraftClient client =
            net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        return client.player.getHungerManager().getFoodLevel() < amount;
    }

    private boolean hasItemInInventory(String itemId) {
        net.minecraft.client.MinecraftClient client =
            net.minecraft.client.MinecraftClient.getInstance();
        if (
            client == null ||
            client.player == null ||
            itemId == null ||
            itemId.isEmpty()
        ) {
            return false;
        }
        for (String candidateId : splitMultiValueList(itemId)) {
            String sanitized = sanitizeResourceId(candidateId);
            String normalized =
                sanitized != null && !sanitized.isEmpty()
                    ? normalizeResourceId(sanitized, "minecraft")
                    : candidateId;
            Identifier identifier = Identifier.tryParse(normalized);
            if (identifier == null || !Registries.ITEM.containsId(identifier)) {
                continue;
            }
            net.minecraft.item.Item item = Registries.ITEM.get(identifier);
            if (client.player.getInventory().count(item) > 0) {
                return true;
            }
        }
        return false;
    }

    private boolean hasItemAmountInInventory(
        String itemId,
        int requiredAmount
    ) {
        net.minecraft.client.MinecraftClient client =
            net.minecraft.client.MinecraftClient.getInstance();
        if (
            client == null ||
            client.player == null ||
            itemId == null ||
            itemId.isEmpty()
        ) {
            return false;
        }
        int needed = Math.max(1, requiredAmount);
        for (String candidateId : splitMultiValueList(itemId)) {
            String sanitized = sanitizeResourceId(candidateId);
            String normalized =
                sanitized != null && !sanitized.isEmpty()
                    ? normalizeResourceId(sanitized, "minecraft")
                    : candidateId;
            Identifier identifier = Identifier.tryParse(normalized);
            if (identifier == null || !Registries.ITEM.containsId(identifier)) {
                continue;
            }
            net.minecraft.item.Item item = Registries.ITEM.get(identifier);
            if (client.player.getInventory().count(item) >= needed) {
                return true;
            }
        }
        return false;
    }

    private boolean stackMatchesAnyItem(ItemStack stack, List<String> itemIds) {
        if (
            stack == null ||
            stack.isEmpty() ||
            itemIds == null ||
            itemIds.isEmpty()
        ) {
            return false;
        }
        for (String candidateId : itemIds) {
            String sanitized = sanitizeResourceId(candidateId);
            String normalized =
                sanitized != null && !sanitized.isEmpty()
                    ? normalizeResourceId(sanitized, "minecraft")
                    : candidateId;
            Identifier identifier = Identifier.tryParse(normalized);
            if (identifier == null || !Registries.ITEM.containsId(identifier)) {
                continue;
            }
            net.minecraft.item.Item item = Registries.ITEM.get(identifier);
            if (stack.isOf(item)) {
                return true;
            }
        }
        return false;
    }

    private boolean isResourceRendered(String resourceId) {
        net.minecraft.client.MinecraftClient client =
            net.minecraft.client.MinecraftClient.getInstance();
        if (
            client == null ||
            client.player == null ||
            client.world == null ||
            resourceId == null ||
            resourceId.isEmpty()
        ) {
            return false;
        }
        String trimmed = resourceId.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        if (trimmed.indexOf(',') >= 0) {
            String[] parts = trimmed.split(",");
            for (String part : parts) {
                if (
                    part != null &&
                    !part.trim().isEmpty() &&
                    isResourceRendered(part.trim())
                ) {
                    return true;
                }
            }
            return false;
        }
        return isSingleResourceRendered(client, trimmed);
    }

    private boolean isEntityRendered(String entityId, String state) {
        net.minecraft.client.MinecraftClient client =
            net.minecraft.client.MinecraftClient.getInstance();
        if (
            client == null ||
            client.player == null ||
            client.world == null ||
            entityId == null ||
            entityId.isEmpty()
        ) {
            return false;
        }
        if (isAnySelectionValue(entityId)) {
            double renderDistance = Math.max(
                8.0,
                client.options.getViewDistance().getValue() * 4.0
            );
            Box searchBox = client.player
                .getBoundingBox()
                .expand(renderDistance);
            List<Entity> matches = client.world.getOtherEntities(
                client.player,
                searchBox,
                entity ->
                    entity != null &&
                    entity.isAlive() &&
                    EntityStateOptions.matchesState(entity, state)
            );
            return !matches.isEmpty();
        }
        for (String candidateId : splitMultiValueList(entityId)) {
            String sanitized = sanitizeResourceId(candidateId);
            String normalized =
                sanitized != null && !sanitized.isEmpty()
                    ? normalizeResourceId(sanitized, "minecraft")
                    : candidateId;
            Identifier identifier = Identifier.tryParse(normalized);
            if (
                identifier == null ||
                !Registries.ENTITY_TYPE.containsId(identifier)
            ) {
                continue;
            }
            EntityType<?> entityType = Registries.ENTITY_TYPE.get(identifier);
            if (isEntityRendered(client, entityType, state)) {
                return true;
            }
        }
        return false;
    }

    private boolean isResourceVisible(String resourceId) {
        net.minecraft.client.MinecraftClient client =
            net.minecraft.client.MinecraftClient.getInstance();
        if (
            client == null ||
            client.player == null ||
            client.world == null ||
            resourceId == null ||
            resourceId.isEmpty()
        ) {
            return false;
        }
        String trimmed = resourceId.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        if (trimmed.indexOf(',') >= 0) {
            String[] parts = trimmed.split(",");
            for (String part : parts) {
                if (
                    part != null &&
                    !part.trim().isEmpty() &&
                    isResourceVisible(part.trim())
                ) {
                    return true;
                }
            }
            return false;
        }
        return isSingleResourceVisible(client, trimmed);
    }

    private boolean isEntityVisible(String entityId, String state) {
        net.minecraft.client.MinecraftClient client =
            net.minecraft.client.MinecraftClient.getInstance();
        if (
            client == null ||
            client.player == null ||
            client.world == null ||
            entityId == null ||
            entityId.isEmpty()
        ) {
            return false;
        }
        for (String candidateId : splitMultiValueList(entityId)) {
            String sanitized = sanitizeResourceId(candidateId);
            String normalized =
                sanitized != null && !sanitized.isEmpty()
                    ? normalizeResourceId(sanitized, "minecraft")
                    : candidateId;
            Identifier identifier = Identifier.tryParse(normalized);
            if (
                identifier == null ||
                !Registries.ENTITY_TYPE.containsId(identifier)
            ) {
                continue;
            }
            EntityType<?> entityType = Registries.ENTITY_TYPE.get(identifier);
            if (isEntityVisible(client, entityType, state)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSingleResourceVisible(
        net.minecraft.client.MinecraftClient client,
        String resourceId
    ) {
        if (
            client == null ||
            client.player == null ||
            client.world == null ||
            resourceId == null ||
            resourceId.isEmpty()
        ) {
            return false;
        }
        Optional<BlockSelection> selectionOptional = BlockSelection.parse(
            resourceId
        );
        if (selectionOptional.isPresent()) {
            BlockSelection selection = selectionOptional.get();
            Block block = selection.getBlock();
            return block != null && isBlockVisible(client, block, selection);
        }
        String normalized = resourceId.contains(":")
            ? resourceId.toLowerCase(Locale.ROOT)
            : resourceId;
        Identifier identifier = Identifier.tryParse(normalized);
        if (identifier != null) {
            if (Registries.BLOCK.containsId(identifier)) {
                Block block = Registries.BLOCK.get(identifier);
                return isBlockVisible(client, block);
            }
            if (Registries.ITEM.containsId(identifier)) {
                Item item = Registries.ITEM.get(identifier);
                return isItemVisible(client, item);
            }
            if (Registries.ENTITY_TYPE.containsId(identifier)) {
                EntityType<?> entityType = Registries.ENTITY_TYPE.get(
                    identifier
                );
                return isEntityVisible(client, entityType, "");
            }
        }
        return isPlayerVisible(client, resourceId);
    }

    private boolean isSingleResourceRendered(
        net.minecraft.client.MinecraftClient client,
        String resourceId
    ) {
        if (
            client == null ||
            client.player == null ||
            client.world == null ||
            resourceId == null ||
            resourceId.isEmpty()
        ) {
            return false;
        }
        Optional<BlockSelection> selectionOptional = BlockSelection.parse(
            resourceId
        );
        if (selectionOptional.isPresent()) {
            return isBlockRendered(client, selectionOptional.get());
        }
        String normalized = resourceId.contains(":")
            ? resourceId.toLowerCase(Locale.ROOT)
            : resourceId;
        Identifier identifier = Identifier.tryParse(normalized);
        if (identifier != null) {
            if (Registries.BLOCK.containsId(identifier)) {
                Block block = Registries.BLOCK.get(identifier);
                return isBlockRendered(client, block);
            }
            if (Registries.ITEM.containsId(identifier)) {
                Item item = Registries.ITEM.get(identifier);
                return isItemRendered(client, item);
            }
            if (Registries.ENTITY_TYPE.containsId(identifier)) {
                EntityType<?> entityType = Registries.ENTITY_TYPE.get(
                    identifier
                );
                return isEntityRendered(client, entityType, "");
            }
        }
        return isPlayerRendered(client, resourceId);
    }

    private boolean isBlockRendered(
        net.minecraft.client.MinecraftClient client,
        Block block
    ) {
        return isBlockRendered(client, block, null);
    }

    private boolean isBlockRendered(
        net.minecraft.client.MinecraftClient client,
        BlockSelection selection
    ) {
        if (selection == null) {
            return false;
        }
        Block block = selection.getBlock();
        if (block == null) {
            return false;
        }
        return isBlockRendered(client, block, selection);
    }

    private boolean isBlockRendered(
        net.minecraft.client.MinecraftClient client,
        Block block,
        BlockSelection selection
    ) {
        if (
            client == null ||
            client.player == null ||
            client.world == null ||
            block == null
        ) {
            return false;
        }

        HitResult hitResult = client.crosshairTarget;
        if (hitResult instanceof BlockHitResult blockHit) {
            BlockPos hitPos = blockHit.getBlockPos();
            BlockState state = client.world.getBlockState(hitPos);
            boolean matches =
                selection != null
                    ? selection.matches(state)
                    : state.isOf(block);
            if (matches) {
                return true;
            }
        }

        BlockPos playerPos = client.player.getBlockPos();
        int viewDistance = client.options.getViewDistance().getValue();
        int horizontalRadius = MathHelper.clamp(viewDistance * 4, 8, 48);
        int verticalRadius = MathHelper.clamp(viewDistance * 2, 6, 32);
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int dx = -horizontalRadius; dx <= horizontalRadius; dx++) {
            for (int dy = -verticalRadius; dy <= verticalRadius; dy++) {
                for (int dz = -horizontalRadius; dz <= horizontalRadius; dz++) {
                    mutable.set(
                        playerPos.getX() + dx,
                        playerPos.getY() + dy,
                        playerPos.getZ() + dz
                    );
                    BlockState state = client.world.getBlockState(mutable);
                    boolean matches =
                        selection != null
                            ? selection.matches(state)
                            : state.isOf(block);
                    if (matches && isBlockVisible(client, mutable, false)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isBlockVisible(
        net.minecraft.client.MinecraftClient client,
        BlockPos pos
    ) {
        return isBlockVisible(client, pos, true);
    }

    private boolean isBlockVisible(
        net.minecraft.client.MinecraftClient client,
        Block block
    ) {
        return isBlockVisible(client, block, null);
    }

    private boolean isBlockVisible(
        net.minecraft.client.MinecraftClient client,
        BlockSelection selection
    ) {
        if (selection == null) {
            return false;
        }
        Block block = selection.getBlock();
        if (block == null) {
            return false;
        }
        return isBlockVisible(client, block, selection);
    }

    private boolean isBlockVisible(
        net.minecraft.client.MinecraftClient client,
        Block block,
        BlockSelection selection
    ) {
        if (
            client == null ||
            client.player == null ||
            client.world == null ||
            block == null
        ) {
            return false;
        }

        HitResult hitResult = client.crosshairTarget;
        if (hitResult instanceof BlockHitResult blockHit) {
            BlockPos hitPos = blockHit.getBlockPos();
            BlockState state = client.world.getBlockState(hitPos);
            boolean matches =
                selection != null
                    ? selection.matches(state)
                    : state.isOf(block);
            if (matches && isBlockVisible(client, hitPos, true)) {
                return true;
            }
        }

        BlockPos playerPos = client.player.getBlockPos();
        int viewDistance = client.options.getViewDistance().getValue();
        int horizontalRadius = MathHelper.clamp(viewDistance * 4, 8, 48);
        int verticalRadius = MathHelper.clamp(viewDistance * 2, 6, 32);
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int dx = -horizontalRadius; dx <= horizontalRadius; dx++) {
            for (int dy = -verticalRadius; dy <= verticalRadius; dy++) {
                for (int dz = -horizontalRadius; dz <= horizontalRadius; dz++) {
                    mutable.set(
                        playerPos.getX() + dx,
                        playerPos.getY() + dy,
                        playerPos.getZ() + dz
                    );
                    BlockState state = client.world.getBlockState(mutable);
                    boolean matches =
                        selection != null
                            ? selection.matches(state)
                            : state.isOf(block);
                    if (matches && isBlockVisible(client, mutable, true)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isBlockVisible(
        net.minecraft.client.MinecraftClient client,
        BlockPos pos,
        boolean requireInFieldOfView
    ) {
        if (client == null || client.player == null || client.world == null) {
            return false;
        }
        Vec3d cameraPos = CameraCompatibilityBridge.getPos(
            client.gameRenderer.getCamera()
        );
        Vec3d target = Vec3d.ofCenter(pos);
        if (
            requireInFieldOfView && !isPointInPlayerFieldOfView(client, target)
        ) {
            return false;
        }
        RaycastContext context = new RaycastContext(
            cameraPos,
            target,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            client.player
        );
        BlockHitResult hit = client.world.raycast(context);
        if (hit == null) {
            return false;
        }
        if (hit.getType() == HitResult.Type.MISS) {
            return true;
        }
        return (
            hit.getType() == HitResult.Type.BLOCK &&
            hit.getBlockPos().equals(pos)
        );
    }

    private boolean isItemRendered(
        net.minecraft.client.MinecraftClient client,
        Item item
    ) {
        if (
            client == null ||
            client.player == null ||
            client.world == null ||
            item == null
        ) {
            return false;
        }

        if (
            client.player.getMainHandStack().isOf(item) ||
            client.player.getOffHandStack().isOf(item)
        ) {
            return true;
        }

        HitResult hitResult = client.crosshairTarget;
        if (hitResult instanceof EntityHitResult entityHit) {
            Entity targetEntity = entityHit.getEntity();
            if (
                targetEntity instanceof ItemEntity itemEntity &&
                !itemEntity.getStack().isEmpty() &&
                itemEntity.getStack().isOf(item)
            ) {
                return true;
            }
        }

        double renderDistance = Math.max(
            8.0,
            client.options.getViewDistance().getValue() * 4.0
        );
        Box searchBox = client.player.getBoundingBox().expand(renderDistance);
        List<ItemEntity> candidates = client.world.getEntitiesByClass(
            ItemEntity.class,
            searchBox,
            entity ->
                entity != null &&
                !entity.isRemoved() &&
                !entity.getStack().isEmpty() &&
                entity.getStack().isOf(item) &&
                client.player.canSee(entity)
        );
        return !candidates.isEmpty();
    }

    private boolean isItemVisible(
        net.minecraft.client.MinecraftClient client,
        Item item
    ) {
        if (
            client == null ||
            client.player == null ||
            client.world == null ||
            item == null
        ) {
            return false;
        }
        double renderDistance = Math.max(
            8.0,
            client.options.getViewDistance().getValue() * 4.0
        );
        Box searchBox = client.player.getBoundingBox().expand(renderDistance);
        List<ItemEntity> candidates = client.world.getEntitiesByClass(
            ItemEntity.class,
            searchBox,
            entity ->
                entity != null &&
                !entity.isRemoved() &&
                !entity.getStack().isEmpty() &&
                entity.getStack().isOf(item) &&
                client.player.canSee(entity) &&
                isEntityInPlayerFieldOfView(client, entity)
        );
        return !candidates.isEmpty();
    }

    private boolean isEntityRendered(
        net.minecraft.client.MinecraftClient client,
        EntityType<?> entityType,
        String state
    ) {
        if (
            client == null ||
            client.player == null ||
            client.world == null ||
            entityType == null
        ) {
            return false;
        }

        HitResult hitResult = client.crosshairTarget;
        if (
            hitResult instanceof EntityHitResult entityHit &&
            entityHit.getEntity() != null &&
            entityHit.getEntity().getType() == entityType &&
            EntityStateOptions.matchesState(entityHit.getEntity(), state)
        ) {
            return true;
        }

        double renderDistance = Math.max(
            8.0,
            client.options.getViewDistance().getValue() * 4.0
        );
        Box searchBox = client.player.getBoundingBox().expand(renderDistance);
        List<Entity> matches = client.world.getOtherEntities(
            client.player,
            searchBox,
            entity ->
                entity != null &&
                entity.isAlive() &&
                entity.getType() == entityType &&
                EntityStateOptions.matchesState(entity, state)
        );
        return !matches.isEmpty();
    }

    private boolean isEntityVisible(
        net.minecraft.client.MinecraftClient client,
        EntityType<?> entityType,
        String state
    ) {
        if (
            client == null ||
            client.player == null ||
            client.world == null ||
            entityType == null
        ) {
            return false;
        }

        HitResult hitResult = client.crosshairTarget;
        if (
            hitResult instanceof EntityHitResult entityHit &&
            entityHit.getEntity() != null &&
            entityHit.getEntity().getType() == entityType &&
            EntityStateOptions.matchesState(entityHit.getEntity(), state) &&
            client.player.canSee(entityHit.getEntity()) &&
            isEntityInPlayerFieldOfView(client, entityHit.getEntity())
        ) {
            return true;
        }

        double renderDistance = Math.max(
            8.0,
            client.options.getViewDistance().getValue() * 4.0
        );
        Box searchBox = client.player.getBoundingBox().expand(renderDistance);
        List<Entity> matches = client.world.getOtherEntities(
            client.player,
            searchBox,
            entity ->
                entity != null &&
                entity.isAlive() &&
                entity.getType() == entityType &&
                EntityStateOptions.matchesState(entity, state) &&
                client.player.canSee(entity) &&
                isEntityInPlayerFieldOfView(client, entity)
        );
        return !matches.isEmpty();
    }

    private boolean isPlayerRendered(
        net.minecraft.client.MinecraftClient client,
        String playerName
    ) {
        if (
            client == null ||
            client.player == null ||
            client.world == null ||
            playerName == null ||
            playerName.isEmpty()
        ) {
            return false;
        }

        String trimmed = playerName.trim();
        if (trimmed.isEmpty()) {
            return false;
        }

        HitResult hitResult = client.crosshairTarget;
        if (
            hitResult instanceof EntityHitResult entityHit &&
            entityHit.getEntity() instanceof
                AbstractClientPlayerEntity targetPlayer
        ) {
            if (
                trimmed.equalsIgnoreCase(
                    GameProfileCompatibilityBridge.getName(
                        targetPlayer.getGameProfile()
                    )
                )
            ) {
                return true;
            }
        }

        double renderDistance = Math.max(
            8.0,
            client.options.getViewDistance().getValue() * 4.0
        );
        for (AbstractClientPlayerEntity playerEntity : client.world.getPlayers()) {
            if (playerEntity == null || !playerEntity.isAlive()) {
                continue;
            }
            if (
                !trimmed.equalsIgnoreCase(
                    GameProfileCompatibilityBridge.getName(
                        playerEntity.getGameProfile()
                    )
                )
            ) {
                continue;
            }
            if (
                playerEntity.squaredDistanceTo(client.player) >
                renderDistance * renderDistance
            ) {
                continue;
            }
            return true;
        }

        return false;
    }

    private boolean isPlayerVisible(
        net.minecraft.client.MinecraftClient client,
        String playerName
    ) {
        if (
            client == null ||
            client.player == null ||
            client.world == null ||
            playerName == null ||
            playerName.isEmpty()
        ) {
            return false;
        }

        String trimmed = playerName.trim();
        if (trimmed.isEmpty()) {
            return false;
        }

        HitResult hitResult = client.crosshairTarget;
        if (
            hitResult instanceof EntityHitResult entityHit &&
            entityHit.getEntity() instanceof
                AbstractClientPlayerEntity targetPlayer
        ) {
            if (
                trimmed.equalsIgnoreCase(
                    GameProfileCompatibilityBridge.getName(
                        targetPlayer.getGameProfile()
                    )
                ) &&
                client.player.canSee(targetPlayer) &&
                isEntityInPlayerFieldOfView(client, targetPlayer)
            ) {
                return true;
            }
        }

        double renderDistance = Math.max(
            8.0,
            client.options.getViewDistance().getValue() * 4.0
        );
        for (AbstractClientPlayerEntity playerEntity : client.world.getPlayers()) {
            if (playerEntity == null || !playerEntity.isAlive()) {
                continue;
            }
            if (
                !trimmed.equalsIgnoreCase(
                    GameProfileCompatibilityBridge.getName(
                        playerEntity.getGameProfile()
                    )
                )
            ) {
                continue;
            }
            if (
                playerEntity.squaredDistanceTo(client.player) >
                renderDistance * renderDistance
            ) {
                continue;
            }
            if (
                !client.player.canSee(playerEntity) ||
                !isEntityInPlayerFieldOfView(client, playerEntity)
            ) {
                continue;
            }
            return true;
        }

        return false;
    }

    private boolean isEntityInPlayerFieldOfView(
        net.minecraft.client.MinecraftClient client,
        Entity entity
    ) {
        if (entity == null) {
            return false;
        }
        Vec3d target = entity.getBoundingBox().getCenter();
        return isPointInPlayerFieldOfView(client, target);
    }

    private boolean isPointInPlayerFieldOfView(
        net.minecraft.client.MinecraftClient client,
        Vec3d target
    ) {
        if (client == null || client.player == null || target == null) {
            return false;
        }
        Vec3d eyePos = client.player.getEyePos();
        Vec3d toTarget = target.subtract(eyePos);
        if (toTarget.lengthSquared() <= 1.0E-6D) {
            return true;
        }
        Vec3d forward = client.player.getRotationVec(1.0F);
        if (forward.lengthSquared() <= 1.0E-6D) {
            return false;
        }
        Vec3d forwardNorm = forward.normalize();
        Vec3d worldUp = new Vec3d(0.0, 1.0, 0.0);
        Vec3d right = forwardNorm.crossProduct(worldUp);
        if (right.lengthSquared() <= 1.0E-6D) {
            right = new Vec3d(1.0, 0.0, 0.0);
        } else {
            right = right.normalize();
        }
        Vec3d up = right.crossProduct(forwardNorm).normalize();

        Vec3d targetNorm = toTarget.normalize();
        double z = targetNorm.dotProduct(forwardNorm);
        if (z <= 0.0) {
            return false;
        }
        double x = targetNorm.dotProduct(right);
        double y = targetNorm.dotProduct(up);

        int width =
            client.getWindow() != null
                ? client.getWindow().getFramebufferWidth()
                : 0;
        int height =
            client.getWindow() != null
                ? client.getWindow().getFramebufferHeight()
                : 0;
        double aspect = (width > 0 && height > 0)
            ? (double) width / (double) height
            : (16.0 / 9.0);

        double verticalFovDegrees = MathHelper.clamp(
            client.options.getFov().getValue(),
            30.0,
            170.0
        );
        double verticalHalfRadians = Math.toRadians(verticalFovDegrees / 2.0);
        double horizontalHalfRadians = Math.atan(
            Math.tan(verticalHalfRadians) * aspect
        );

        double horizontalAngle = Math.atan2(Math.abs(x), z);
        double verticalAngle = Math.atan2(Math.abs(y), z);
        return (
            horizontalAngle <= horizontalHalfRadians &&
            verticalAngle <= verticalHalfRadians
        );
    }

    private boolean isSwimming() {
        net.minecraft.client.MinecraftClient client =
            net.minecraft.client.MinecraftClient.getInstance();
        return (
            client != null &&
            client.player != null &&
            client.player.isSwimming()
        );
    }

    private boolean isInLava() {
        net.minecraft.client.MinecraftClient client =
            net.minecraft.client.MinecraftClient.getInstance();
        return (
            client != null && client.player != null && client.player.isInLava()
        );
    }

    private boolean isUnderwater() {
        net.minecraft.client.MinecraftClient client =
            net.minecraft.client.MinecraftClient.getInstance();
        return (
            client != null &&
            client.player != null &&
            client.player.isSubmergedInWater()
        );
    }

    private Optional<Double> getDistanceFromGround() {
        net.minecraft.client.MinecraftClient client =
            net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return Optional.empty();
        }

        Box box = client.player.getBoundingBox();
        double bottomY = box.minY;
        double bottomLimit = client.world.getBottomY() - 1.0;
        double inset = 1.0E-3;
        Vec3d[] samplePoints = new Vec3d[] {
            new Vec3d(
                (box.minX + box.maxX) * 0.5,
                bottomY + 0.01,
                (box.minZ + box.maxZ) * 0.5
            ),
            new Vec3d(box.minX + inset, bottomY + 0.01, box.minZ + inset),
            new Vec3d(box.minX + inset, bottomY + 0.01, box.maxZ - inset),
            new Vec3d(box.maxX - inset, bottomY + 0.01, box.minZ + inset),
            new Vec3d(box.maxX - inset, bottomY + 0.01, box.maxZ - inset),
        };

        Double nearestDistance = null;
        for (Vec3d start : samplePoints) {
            HitResult hit = client.world.raycast(
                new RaycastContext(
                    start,
                    new Vec3d(start.x, bottomLimit, start.z),
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    client.player
                )
            );
            if (
                !(hit instanceof BlockHitResult blockHit) ||
                hit.getType() != HitResult.Type.BLOCK
            ) {
                continue;
            }
            double distance = Math.max(0.0, bottomY - blockHit.getPos().y);
            if (nearestDistance == null || distance < nearestDistance) {
                nearestDistance = distance;
            }
        }

        if (nearestDistance == null) {
            return Optional.empty();
        }
        if (nearestDistance < 1.0E-3) {
            nearestDistance = 0.0;
        }
        return Optional.of(nearestDistance);
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
        if (onGround || swimming || submergedInWater || climbing || flying) {
            return false;
        }
        if (
            lastDetectedAtMs != Long.MIN_VALUE &&
            nowMs - lastDetectedAtMs <= FALLING_SENSOR_RETENTION_MS
        ) {
            return true;
        }
        if (downwardVelocity >= -1.0E-3) {
            return false;
        }
        if (
            groundClearance >= FALLING_SENSOR_MIN_CLEARANCE &&
            (fallDistance > 1.0E-3 || peakY - currentY > 1.0E-3)
        ) {
            return true;
        }
        if (fallDistance >= requiredDistance) {
            return true;
        }
        return peakY - currentY >= requiredDistance;
    }

    private boolean isFalling(double distance) {
        net.minecraft.client.MinecraftClient client =
            net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        double currentY = client.player.getY();
        if (client.player.isOnGround()) {
            runtimeState.fallingPeakY = currentY;
            runtimeState.fallingPeakInitialized = true;
            runtimeState.lastFallingDetectedAtMs = Long.MIN_VALUE;
            return false;
        }
        if (
            client.player.isSwimming() ||
            client.player.isSubmergedInWater() ||
            client.player.isClimbing() ||
            client.player.getAbilities().flying
        ) {
            runtimeState.fallingPeakY = currentY;
            runtimeState.fallingPeakInitialized = false;
            runtimeState.lastFallingDetectedAtMs = Long.MIN_VALUE;
            return false;
        }

        if (!runtimeState.fallingPeakInitialized) {
            runtimeState.fallingPeakY = currentY;
            runtimeState.fallingPeakInitialized = true;
        } else if (currentY > runtimeState.fallingPeakY) {
            runtimeState.fallingPeakY = currentY;
        }
        double groundClearance = getDistanceFromGround().orElse(
            Double.POSITIVE_INFINITY
        );

        boolean falling = isFallingState(
            false,
            false,
            false,
            false,
            false,
            client.player.getVelocity().y,
            client.player.fallDistance,
            runtimeState.fallingPeakY,
            currentY,
            groundClearance,
            distance,
            now,
            runtimeState.lastFallingDetectedAtMs
        );
        if (falling) {
            runtimeState.lastFallingDetectedAtMs = now;
        }
        return falling;
    }

    void executeCommand(String command) {
        try {
            net.minecraft.client.MinecraftClient client =
                net.minecraft.client.MinecraftClient.getInstance();
            if (client != null && client.player != null) {
                client.player.networkHandler.sendChatMessage(command);
            }
        } catch (Exception e) {
            LOGGER.warn("Error executing command: {}", e.getMessage(), e);
        }
    }
}
