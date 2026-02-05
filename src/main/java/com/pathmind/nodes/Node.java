package com.pathmind.nodes;

import net.minecraft.text.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
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
import com.pathmind.execution.ExecutionManager;
import com.pathmind.execution.PreciseCompletionTracker;
import com.pathmind.util.BaritoneDependencyChecker;
import com.pathmind.util.BaritoneApiProxy;
import com.pathmind.util.BlockSelection;
import com.pathmind.util.ChatMessageTracker;
import com.pathmind.util.EntityStateOptions;
import com.pathmind.util.InventorySlotModeHelper;
import com.pathmind.util.PlayerInventoryBridge;
import com.pathmind.util.RecipeCompatibilityBridge;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.registry.Registries;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.BookUpdateC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Box;
import net.minecraft.world.RaycastContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WritableBookContentComponent;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.RegistryWrapper;
import java.util.Arrays;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Collections;
import java.util.Comparator;
import java.util.regex.Pattern;
import java.util.Random;
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
    public static final int NO_OUTPUT = -1;
    private final String id;
    private final NodeType type;
    private NodeMode mode;
    private int x, y;
    private static final int MIN_WIDTH = 92;
    private static final int MIN_HEIGHT = 44;
    private static final int EVENT_FUNCTION_MIN_HEIGHT = 36;
    private static final int CHAR_PIXEL_WIDTH = 6;
    private static final int HEADER_HEIGHT = 18;
    private static final int PARAM_LINE_HEIGHT = 20;
    private static final int PARAM_PADDING_TOP = 2;
    private static final int PARAM_PADDING_BOTTOM = 4;
    private static final int MAX_PARAMETER_LABEL_LENGTH = 20;
    private static final int BODY_PADDING_NO_PARAMS = 10;
    private static final int START_END_SIZE = 36;
    private static final String CHAT_MESSAGE_PREFIX = "\u00A74[\u00A7cPathmind\u00A74] \u00A77";
    private static final long CRAFTING_ACTION_DELAY_MS = 75L;
    private static final int CRAFTING_OUTPUT_POLL_LIMIT = 5;
    private static final int SENSOR_SLOT_MARGIN_HORIZONTAL = 8;
    private static final int SENSOR_SLOT_INNER_PADDING = 4;
    private static final int SENSOR_SLOT_MIN_CONTENT_WIDTH = 60;
    private static final int SENSOR_SLOT_MIN_CONTENT_HEIGHT = 28;
    private static final int ACTION_SLOT_MARGIN_HORIZONTAL = 8;
    private static final int ACTION_SLOT_INNER_PADDING = 4;
    private static final int ACTION_SLOT_MIN_CONTENT_WIDTH = 80;
    private static final int ACTION_SLOT_MIN_CONTENT_HEIGHT = 32;
    private static final int PARAMETER_SLOT_MARGIN_HORIZONTAL = 8;
    private static final int PARAMETER_SLOT_INNER_PADDING = 4;
    private static final int PARAMETER_SLOT_MIN_CONTENT_WIDTH = 88;
    private static final int PARAMETER_SLOT_MIN_CONTENT_HEIGHT = 32;
    private static final int PARAMETER_SLOT_LABEL_HEIGHT = 12;
    private static final int OPERATOR_SLOT_GAP = 8;
    private static final int MINIMAL_NODE_TAB_WIDTH = 6;
    private static final int PARAMETER_FIELD_PADDING = 12;
    private static final int PLAYER_ARMOR_SLOT_COUNT = 4;
    private static final int PLAYER_OFFHAND_INVENTORY_INDEX = PlayerInventory.MAIN_SIZE + PLAYER_ARMOR_SLOT_COUNT;
    private static final int PARAMETER_SLOT_BOTTOM_PADDING = 6;
    private static final int SLOT_AREA_PADDING_TOP = 0;
    private static final int SLOT_AREA_PADDING_BOTTOM = 6;
    private static final int SLOT_VERTICAL_SPACING = 6;
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
    private static final int AMOUNT_FIELD_TOP_MARGIN = 6;
    private static final int AMOUNT_FIELD_LABEL_HEIGHT = 10;
    private static final int AMOUNT_FIELD_HEIGHT = 16;
    private static final int AMOUNT_FIELD_TEXT_PADDING = 3;
    private static final int AMOUNT_FIELD_BOTTOM_MARGIN = 6;
    private static final int AMOUNT_TOGGLE_WIDTH = 18;
    private static final int AMOUNT_TOGGLE_HEIGHT = 10;
    private static final int AMOUNT_TOGGLE_SPACING = 6;
    private static final int AMOUNT_SIGN_TOGGLE_WIDTH = 22;
    private static final int AMOUNT_SIGN_TOGGLE_HEIGHT = 16;
    private static final int MESSAGE_FIELD_MARGIN_HORIZONTAL = 6;
    private static final int MESSAGE_FIELD_TOP_MARGIN = 6;
    private static final int MESSAGE_FIELD_LABEL_HEIGHT = 10;
    private static final int MESSAGE_FIELD_HEIGHT = 16;
    private static final int MESSAGE_FIELD_VERTICAL_GAP = 6;
    private static final int MESSAGE_FIELD_BOTTOM_MARGIN = 6;
    private static final int MESSAGE_FIELD_MIN_CONTENT_WIDTH = 120;
    private static final int MESSAGE_FIELD_TEXT_PADDING = 3;
    private static final int MESSAGE_BUTTON_SIZE = 10;
    private static final int MESSAGE_BUTTON_PADDING = 4;
    private static final int MESSAGE_BUTTON_SPACING = 4;
    private static final int SCHEMATIC_FIELD_TOP_MARGIN = 6;
    private static final int SCHEMATIC_FIELD_LABEL_HEIGHT = 10;
    private static final int SCHEMATIC_FIELD_HEIGHT = 16;
    private static final int SCHEMATIC_FIELD_BOTTOM_MARGIN = 6;
    private static final int STOP_TARGET_FIELD_MARGIN_HORIZONTAL = 8;
    private static final int STOP_TARGET_FIELD_TOP_MARGIN = 6;
    private static final int STOP_TARGET_FIELD_LABEL_HEIGHT = 0;
    private static final int STOP_TARGET_FIELD_HEIGHT = 16;
    private static final int STOP_TARGET_FIELD_TEXT_PADDING = 3;
    private static final int STOP_TARGET_FIELD_BOTTOM_MARGIN = 6;
    private static final int STOP_TARGET_FIELD_MIN_WIDTH = 48;
    private static final int BOOK_TEXT_BUTTON_MARGIN_HORIZONTAL = 6;
    private static final int BOOK_TEXT_TOP_MARGIN = 6;
    private static final int BOOK_TEXT_BUTTON_HEIGHT = 16;
    private static final int BOOK_TEXT_BUTTON_MIN_WIDTH = 70;
    private static final int BOOK_TEXT_LABEL_HEIGHT = 10;
    private static final int BOOK_TEXT_PAGE_FIELD_HEIGHT = 16;
    private static final int BOOK_TEXT_FIELD_SPACING = 6;
    private static final int BOOK_TEXT_BOTTOM_MARGIN = 6;
    private static final int POPUP_EDIT_BUTTON_MARGIN_HORIZONTAL = 6;
    private static final int POPUP_EDIT_BUTTON_TOP_MARGIN = 4;
    private static final int POPUP_EDIT_BUTTON_HEIGHT = 16;
    private static final int POPUP_EDIT_BUTTON_MIN_WIDTH = 70;
    private static final int POPUP_EDIT_BUTTON_BOTTOM_MARGIN = 6;
    private static final int EVENT_NAME_FIELD_MARGIN_HORIZONTAL = 6;
    private static final int EVENT_NAME_FIELD_TOP_MARGIN = 6;
    private static final int EVENT_NAME_FIELD_HEIGHT = 16;
    private static final int EVENT_NAME_FIELD_BOTTOM_MARGIN = 6;
    private static final int BOOK_PAGE_MAX_CHARS = 256;
    private static final double PARAMETER_SEARCH_RADIUS = 64.0;
    private static final double DEFAULT_REACH_DISTANCE_SQUARED = 25.0D;
    private static final double DEFAULT_DIRECTION_DISTANCE = 16.0;
    private static final Pattern UNSAFE_RESOURCE_ID_PATTERN = Pattern.compile("[^a-z0-9_:/.-]");
    private static final Object GOTO_BREAK_LOCK = new Object();
    private static final AtomicInteger ACTIVE_GOTO_BLOCKING_REQUESTS = new AtomicInteger(0);
    private static Boolean gotoBreakOriginalValue = null;
    private static final ScheduledExecutorService MESSAGE_SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Pathmind-Message-Scheduler");
        t.setDaemon(true);
        return t;
    });
    private int width;
    private int height;
    private int nextOutputSocket = 0;
    private int repeatRemainingIterations = 0;
    private boolean repeatActive = false;
    private boolean lastSensorResult = false;
    private boolean selected = false;
    private boolean dragging = false;
    private int dragOffsetX, dragOffsetY;
    private final List<NodeParameter> parameters;
    private Node attachedSensor;
    private Node parentControl;
    private Node attachedActionNode;
    private Node parentActionControl;
    private final Map<Integer, Node> attachedParameters;
    private Node parentParameterHost;
    private int parentParameterSlotIndex;
    private boolean socketsHidden;
    private boolean booleanToggleValue = true;
    private RuntimeParameterData runtimeParameterData;
    private transient Node owningStartNode;
    private int startNodeNumber;
    private final List<String> messageLines;
    private String bookText;
    private int messageFieldContentWidthOverride;
    private int parameterFieldWidthOverride;
    private int coordinateFieldWidthOverride;
    private int amountFieldWidthOverride;
    private int stopTargetFieldWidthOverride;
    private transient Random randomGenerator;
    private transient String randomSeedCache;

    public Node(NodeType type, int x, int y) {
        this.id = java.util.UUID.randomUUID().toString();
        this.type = type;
        this.mode = NodeMode.getDefaultModeForNodeType(type);
        this.x = x;
        this.y = y;
        this.parameters = new ArrayList<>();
        this.attachedSensor = null;
        this.parentControl = null;
        this.attachedActionNode = null;
        this.parentActionControl = null;
        this.attachedParameters = new HashMap<>();
        this.parentParameterHost = null;
        this.parentParameterSlotIndex = -1;
        this.socketsHidden = false;
        this.owningStartNode = null;
        this.startNodeNumber = 0;
        this.messageLines = new ArrayList<>();
        if (type == NodeType.MESSAGE) {
            this.messageLines.add("Hello World");
        }
        this.bookText = "";
        this.messageFieldContentWidthOverride = 0;
        this.parameterFieldWidthOverride = 0;
        this.coordinateFieldWidthOverride = 0;
        this.amountFieldWidthOverride = 0;
        this.stopTargetFieldWidthOverride = 0;
        initializeParameters();
        recalculateDimensions();
        resetControlState();
    }

    private static final class RuntimeParameterData {
        private BlockPos targetBlockPos;
        private Vec3d targetVector;
        private Entity targetEntity;
        private Item targetItem;
        private String targetBlockId;
        private List<String> targetBlockIds;
        private String targetPlayerName;
        private String targetItemId;
        private String targetTradeKey;
        private String targetEntityId;
        private String message;
        private Double durationSeconds;
        private Boolean booleanValue;
        private String handName;
        private Integer slotIndex;
        private SlotSelectionType slotSelectionType;
        private String schematicName;
        private Double rangeValue;
        private Float resolvedYaw;
        private Float resolvedPitch;
    }

    private static final class PlacementFailure extends RuntimeException {
        PlacementFailure(String message) {
            super(message);
        }
    }

    private enum ParameterHandlingResult {
        CONTINUE,
        COMPLETE
    }

    private enum ParameterUsage {
        POSITION,
        LOOK_ORIENTATION
    }
    
    private static final Set<String> MOVE_ITEM_SOURCE_KEYS = createParameterKeySet("SourceSlot", "FirstSlot");
    private static final Set<String> MOVE_ITEM_TARGET_KEYS = createParameterKeySet("TargetSlot", "SecondSlot");
    private static final Set<String> PLACE_POSITION_BLOCK_KEYS = createParameterKeySet("Block", "Blocks", "BlockId");

    private static String normalizeParameterKey(String key) {
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

    private void sendNodeErrorMessage(net.minecraft.client.MinecraftClient client, String message) {
        if (client == null || message == null || message.isEmpty()) {
            return;
        }
        if (!com.pathmind.data.SettingsManager.shouldShowChatErrors()) {
            return;
        }

        client.execute(() -> sendNodeErrorMessageOnClientThread(client, message));
    }

    private void sendNodeErrorMessageOnClientThread(net.minecraft.client.MinecraftClient client, String message) {
        if (client == null || client.player == null || message == null || message.isEmpty()) {
            return;
        }

        client.player.sendMessage(Text.literal(CHAT_MESSAGE_PREFIX + message), false);
    }

    private void sendNodeInfoMessage(net.minecraft.client.MinecraftClient client, String message) {
        if (client == null || message == null || message.isEmpty()) {
            return;
        }

        client.execute(() -> sendNodeInfoMessageOnClientThread(client, message));
    }

    private void sendNodeInfoMessageOnClientThread(net.minecraft.client.MinecraftClient client, String message) {
        if (client == null || client.player == null || message == null || message.isEmpty()) {
            return;
        }

        client.player.sendMessage(Text.literal(CHAT_MESSAGE_PREFIX + message), false);
    }

    /**
     * Gets the Baritone instance for the current player
     * @return Baritone instance or null if not available
     */
    private Object getBaritone() {
        try {
            return BaritoneApiProxy.getPrimaryBaritone();
        } catch (Exception e) {
            System.err.println("Failed to get Baritone instance: " + e.getMessage());
            return null;
        }
    }

    private boolean isBaritoneApiAvailable() {
        return BaritoneDependencyChecker.isBaritoneApiPresent();
    }

    private boolean isBaritoneModAvailable() {
        return BaritoneDependencyChecker.isBaritonePresent();
    }

    public String getId() {
        return id;
    }

    public NodeType getType() {
        return type;
    }
    
    public NodeMode getMode() {
        return mode;
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
        return x;
    }

    public int getY() {
        return y;
    }

    public void setPosition(int x, int y) {
        setPositionSilently(x, y);
        if (attachedSensor != null) {
            updateAttachedSensorPosition();
        }
        if (attachedActionNode != null) {
            updateAttachedActionPosition();
        }
        updateAttachedParameterPositions();
    }

    private void setPositionSilently(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isDragging() {
        return dragging;
    }

    public void setDragging(boolean dragging) {
        this.dragging = dragging;
    }

    public int getDragOffsetX() {
        return dragOffsetX;
    }

    public void setDragOffsetX(int dragOffsetX) {
        this.dragOffsetX = dragOffsetX;
    }

    public int getDragOffsetY() {
        return dragOffsetY;
    }

    public void setDragOffsetY(int dragOffsetY) {
        this.dragOffsetY = dragOffsetY;
    }

    public boolean containsPoint(int pointX, int pointY) {
        return pointX >= x && pointX <= x + getWidth() && pointY >= y && pointY <= y + getHeight();
    }

    public Text getDisplayName() {
        return Text.literal(type.getDisplayName());
    }

    public boolean isSensorNode() {
        return isSensorType(type);
    }

    public boolean isParameterNode() {
        return type.getCategory() == NodeCategory.PARAMETERS
            || type == NodeType.VARIABLE
            || type == NodeType.OPERATOR_RANDOM;
    }

    public boolean shouldRenderInlineParameters() {
        return type == NodeType.UI_UTILS;
    }

    public static boolean isSensorType(NodeType nodeType) {
        if (nodeType == null) {
            return false;
        }
        switch (nodeType) {
            case SENSOR_TOUCHING_BLOCK:
            case SENSOR_TOUCHING_ENTITY:
            case SENSOR_AT_COORDINATES:
            case SENSOR_BLOCK_AHEAD:
            case SENSOR_IS_DAYTIME:
            case SENSOR_IS_RAINING:
            case SENSOR_HEALTH_BELOW:
            case SENSOR_HUNGER_BELOW:
            case SENSOR_ITEM_IN_INVENTORY:
            case SENSOR_ITEM_IN_SLOT:
            case SENSOR_VILLAGER_TRADE:
            case SENSOR_IS_SWIMMING:
            case SENSOR_IS_IN_LAVA:
            case SENSOR_IS_UNDERWATER:
            case SENSOR_IS_ON_GROUND:
            case SENSOR_IS_FALLING:
            case SENSOR_IS_RENDERED:
            case SENSOR_KEY_PRESSED:
            case SENSOR_CHAT_MESSAGE:
            case OPERATOR_EQUALS:
            case OPERATOR_NOT:
            case SENSOR_GUI_FILLED:
                return true;
            default:
                return false;
        }
    }

    public static boolean isParameterType(NodeType nodeType) {
        return nodeType != null
            && (nodeType.getCategory() == NodeCategory.PARAMETERS
                || nodeType == NodeType.VARIABLE
                || nodeType == NodeType.OPERATOR_RANDOM
                || nodeType == NodeType.SENSOR_POSITION_OF);
    }

    public boolean canAcceptSensor() {
        switch (type) {
            case CONTROL_IF:
            case CONTROL_IF_ELSE:
            case CONTROL_REPEAT_UNTIL:
                return true;
            default:
                return false;
        }
    }

    public boolean hasSensorSlot() {
        return canAcceptSensor();
    }

    public boolean canAcceptParameter() {
        if (type == NodeType.SENSOR_POSITION_OF) {
            return true;
        }
        if (type == NodeType.OPEN_INVENTORY || type == NodeType.CLOSE_GUI) {
            return false;
        }
        if (type == NodeType.STOP_CHAIN || type == NodeType.STOP_ALL || type == NodeType.START_CHAIN) {
            return false;
        }
        if (type == NodeType.WRITE_BOOK) {
            // Write Book handles its own text/page UI and shouldn't expose a parameter slot
            return false;
        }
        if (type == NodeType.UI_UTILS) {
            return false;
        }
        if (type == NodeType.MESSAGE) {
            return false;
        }
        if (hasBooleanToggle()) {
            return false;
        }
        return !isParameterNode()
            && type != NodeType.START
            && type != NodeType.EVENT_CALL
            && type != NodeType.EVENT_FUNCTION
            && type != NodeType.SWING
            && type != NodeType.JUMP
            && type.getCategory() != NodeCategory.LOGIC;
    }

    public boolean hasParameterSlot() {
        return canAcceptParameter();
    }

    public boolean isStopControlNode() {
        return type == NodeType.STOP_CHAIN || type == NodeType.STOP_ALL;
    }

    public boolean usesMinimalNodePresentation() {
        return isStopControlNode()
            || type == NodeType.START_CHAIN
            || type == NodeType.SWING
            || type == NodeType.JUMP
            || type == NodeType.OPERATOR_EQUALS
            || type == NodeType.OPERATOR_NOT
            || type == NodeType.OPEN_INVENTORY
            || type == NodeType.CLOSE_GUI;
    }

    public boolean canAcceptParameterAt(int slotIndex) {
        if (!canAcceptParameter()) {
            return false;
        }
        return slotIndex >= 0 && slotIndex < getParameterSlotCount();
    }

    public boolean canAcceptParameterNode(Node parameterNode, int slotIndex) {
        if (parameterNode == null
            || (!parameterNode.isParameterNode()
                && parameterNode.getType() != NodeType.SENSOR_POSITION_OF
                && parameterNode.getType() != NodeType.VARIABLE)) {
            return false;
        }
        if (!canAcceptParameterAt(slotIndex)) {
            return false;
        }
        return isParameterSupported(parameterNode, slotIndex);
    }

    private boolean isParameterSlotRequired(int slotIndex) {
        if (!canAcceptParameterAt(slotIndex)) {
            return false;
        }
        if (type == NodeType.SET_VARIABLE) {
            return slotIndex == 0 || slotIndex == 1;
        }
        if (type == NodeType.CHANGE_VARIABLE) {
            return slotIndex == 0;
        }
        if (type == NodeType.OPERATOR_EQUALS || type == NodeType.OPERATOR_NOT) {
            return slotIndex == 0 || slotIndex == 1;
        }
        if (type == NodeType.SENSOR_CHAT_MESSAGE) {
            return slotIndex == 0 || slotIndex == 1;
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
            return blockParameter == null || !parameterProvidesCoordinates(blockParameter);
        }
        if (type == NodeType.PLACE_HAND) {
            return false;
        }
        if (type == NodeType.WRITE_BOOK) {
            return false;
        }
        return slotIndex == 0;
    }

    private boolean isParameterCompatibleWithSlot(Node parameter, int slotIndex) {
        if (parameter == null) {
            return false;
        }
        if (parameter.getType() == NodeType.VARIABLE
            && type != NodeType.SET_VARIABLE
            && type != NodeType.CHANGE_VARIABLE
            && type != NodeType.OPERATOR_EQUALS
            && type != NodeType.OPERATOR_NOT) {
            return true;
        }
        if (type == NodeType.SET_VARIABLE) {
            NodeType parameterType = parameter.getType();
            int otherSlotIndex = slotIndex == 0 ? 1 : 0;
            Node otherParameter = getAttachedParameter(otherSlotIndex);
            boolean otherIsVariable = otherParameter != null && otherParameter.getType() == NodeType.VARIABLE;
            boolean otherIsValue = otherParameter != null && otherParameter.getType() != NodeType.VARIABLE;

            if (parameterType == NodeType.VARIABLE) {
                return !otherIsVariable;
            }
            if (otherIsValue) {
                return false;
            }
            return (parameter.isParameterNode() || parameterType == NodeType.SENSOR_POSITION_OF);
        }
        if (type == NodeType.CHANGE_VARIABLE) {
            NodeType parameterType = parameter.getType();
            return slotIndex == 0 && parameterType == NodeType.VARIABLE;
        }
        if (type == NodeType.OPERATOR_EQUALS || type == NodeType.OPERATOR_NOT) {
            NodeType parameterType = parameter.getType();
            int otherSlotIndex = slotIndex == 0 ? 1 : 0;
            Node otherParameter = getAttachedParameter(otherSlotIndex);
            boolean otherIsVariable = otherParameter != null && otherParameter.getType() == NodeType.VARIABLE;
            if (parameterType == NodeType.VARIABLE) {
                return !otherIsVariable;
            }
            return true;
        }
        if (type == NodeType.WALK) {
            NodeType parameterType = parameter.getType();
            if (slotIndex == 0) {
                return parameterType == NodeType.PARAM_ROTATION;
            }
            return parameterType == NodeType.PARAM_DURATION || parameterType == NodeType.PARAM_DISTANCE;
        }
        if (type != NodeType.PLACE && type != NodeType.PLACE_HAND) {
            return true;
        }
        NodeType parameterType = parameter.getType();
        if (slotIndex == 0) {
            switch (parameterType) {
                case PARAM_BLOCK:
                case PARAM_INVENTORY_SLOT:
                case PARAM_PLACE_TARGET:
                    return true;
                default:
                    return false;
            }
        }
        return slotIndex == 1 ? parameterProvidesCoordinates(parameterType) : true;
    }

    private boolean isParameterSupported(Node parameter, int slotIndex) {
        if (parameter == null) {
            return false;
        }
        if (type == NodeType.SENSOR_POSITION_OF) {
            if (slotIndex != 0) {
                return false;
            }
            NodeType parameterType = parameter.getType();
            return parameterType == NodeType.PARAM_ENTITY
                || parameterType == NodeType.PARAM_BLOCK
                || parameterType == NodeType.PARAM_ITEM;
        }
        if (parameter.getType() == NodeType.VARIABLE
            && type != NodeType.SET_VARIABLE
            && type != NodeType.CHANGE_VARIABLE
            && type != NodeType.OPERATOR_EQUALS
            && type != NodeType.OPERATOR_NOT) {
            return true;
        }
        if (type == NodeType.SET_VARIABLE) {
            NodeType parameterType = parameter.getType();
            int otherSlotIndex = slotIndex == 0 ? 1 : 0;
            Node otherParameter = getAttachedParameter(otherSlotIndex);
            boolean otherIsVariable = otherParameter != null && otherParameter.getType() == NodeType.VARIABLE;
            boolean otherIsValue = otherParameter != null && otherParameter.getType() != NodeType.VARIABLE;

            if (parameterType == NodeType.VARIABLE) {
                return !otherIsVariable;
            }
            if (otherIsValue) {
                return false;
            }
            return (parameter.isParameterNode() || parameterType == NodeType.SENSOR_POSITION_OF);
        }
        if (type == NodeType.CHANGE_VARIABLE) {
            NodeType parameterType = parameter.getType();
            return slotIndex == 0 && parameterType == NodeType.VARIABLE;
        }
        if (type == NodeType.OPERATOR_EQUALS || type == NodeType.OPERATOR_NOT) {
            NodeType parameterType = parameter.getType();
            int otherSlotIndex = slotIndex == 0 ? 1 : 0;
            Node otherParameter = getAttachedParameter(otherSlotIndex);
            boolean otherIsVariable = otherParameter != null && otherParameter.getType() == NodeType.VARIABLE;
            if (parameterType == NodeType.VARIABLE) {
                return !otherIsVariable;
            }
            return true;
        }
        if (type == NodeType.SENSOR_CHAT_MESSAGE) {
            NodeType parameterType = parameter.getType();
            if (slotIndex == 0) {
                return parameterType == NodeType.PARAM_PLAYER;
            }
            return parameterType == NodeType.PARAM_MESSAGE;
        }
        if (type == NodeType.SENSOR_ITEM_IN_SLOT) {
            NodeType parameterType = parameter.getType();
            if (slotIndex == 0) {
                return parameterType == NodeType.PARAM_ITEM;
            }
            return parameterType == NodeType.PARAM_INVENTORY_SLOT;
        }
        if (!isParameterCompatibleWithSlot(parameter, slotIndex)) {
            return false;
        }
        NodeType parameterType = parameter.getType();
        if (type == NodeType.INTERACT && parameterType == NodeType.PARAM_ITEM) {
            return false;
        }
        if (type == NodeType.INTERACT && parameterType == NodeType.PARAM_ENTITY) {
            return true;
        }
        if (type == NodeType.USE) {
            return parameterType == NodeType.PARAM_ITEM || parameterType == NodeType.PARAM_INVENTORY_SLOT;
        }
        if (type == NodeType.TRADE) {
            return parameterType == NodeType.PARAM_VILLAGER_TRADE;
        }
        if (type == NodeType.MOVE_ITEM && slotIndex >= 0 && slotIndex <= 1 && parameterType == NodeType.PARAM_ITEM) {
            return true;
        }
        if (type == NodeType.MOVE_ITEM && slotIndex == 1 && parameterType == NodeType.PARAM_GUI) {
            return true;
        }
        if ((type == NodeType.PLACE || type == NodeType.PLACE_HAND)
            && slotIndex == 0
            && parameterType == NodeType.PARAM_INVENTORY_SLOT) {
            return true;
        }
        if (supportsDirectSensorParameter(parameterType)) {
            return true;
        }
        return canApplyParameterValues(parameter) || canHandleParameterRuntime(parameter, slotIndex);
    }

    public boolean canAcceptActionNode() {
        switch (type) {
            case CONTROL_REPEAT:
            case CONTROL_REPEAT_UNTIL:
            case CONTROL_FOREVER:
                return true;
            default:
                return false;
        }
    }

    public boolean hasActionSlot() {
        return canAcceptActionNode();
    }

    public boolean hasAttachedSensor() {
        return attachedSensor != null;
    }

    public Node getAttachedSensor() {
        return attachedSensor;
    }

    public boolean isAttachedToControl() {
        return parentControl != null;
    }

    public Node getParentControl() {
        return parentControl;
    }

    public String getAttachedSensorId() {
        return attachedSensor != null ? attachedSensor.getId() : null;
    }

    public String getParentControlId() {
        return parentControl != null ? parentControl.getId() : null;
    }

    public boolean hasAttachedParameter() {
        return !attachedParameters.isEmpty();
    }

    public Node getAttachedParameter() {
        return getAttachedParameter(0);
    }

    public Node getAttachedParameter(int slotIndex) {
        if (slotIndex < 0) {
            return null;
        }
        return attachedParameters.get(slotIndex);
    }

    public Node getParentParameterHost() {
        return parentParameterHost;
    }

    public int getParentParameterSlotIndex() {
        return parentParameterSlotIndex;
    }

    public Map<Integer, Node> getAttachedParameters() {
        return Collections.unmodifiableMap(attachedParameters);
    }

    public String getAttachedParameterId() {
        Node parameter = getAttachedParameter();
        return parameter != null ? parameter.getId() : null;
    }

    public String getParentParameterHostId() {
        return parentParameterHost != null ? parentParameterHost.getId() : null;
    }

    public void setOwningStartNode(Node startNode) {
        this.owningStartNode = startNode;
    }

    public Node getOwningStartNode() {
        return owningStartNode;
    }

    public int getStartNodeNumber() {
        return startNodeNumber;
    }

    public void setStartNodeNumber(int startNodeNumber) {
        this.startNodeNumber = startNodeNumber;
    }

    public boolean hasAttachedActionNode() {
        return attachedActionNode != null;
    }

    public Node getAttachedActionNode() {
        return attachedActionNode;
    }

    public boolean isAttachedToActionControl() {
        return parentActionControl != null;
    }

    public Node getParentActionControl() {
        return parentActionControl;
    }

    public String getAttachedActionId() {
        return attachedActionNode != null ? attachedActionNode.getId() : null;
    }

    public String getParentActionControlId() {
        return parentActionControl != null ? parentActionControl.getId() : null;
    }

    public int getInputSocketCount() {
        if (type == NodeType.START || type == NodeType.EVENT_FUNCTION || isSensorNode() || isParameterNode()) {
            return 0;
        }
        return 1;
    }

    public int getOutputSocketCount() {
        if (isSensorNode() || isParameterNode()) {
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
        int socketHeight = 12;
        if (type == NodeType.START || type == NodeType.EVENT_FUNCTION || usesMinimalNodePresentation()) {
            // Center sockets on compact nodes without traditional headers
            return y + getHeight() / 2;
        } else {
            int headerHeight = 14;
            int contentStartY = y + headerHeight + 6; // Start sockets below header with some padding
            return contentStartY + socketIndex * socketHeight;
        }
    }
    
    public int getSocketX(boolean isInput) {
        return isInput ? x - 4 : x + getWidth() + 4;
    }
    
    public void setNextOutputSocket(int socketIndex) {
        this.nextOutputSocket = socketIndex < 0 ? NO_OUTPUT : Math.max(0, socketIndex);
    }

    public int consumeNextOutputSocket() {
        int value = this.nextOutputSocket;
        this.nextOutputSocket = 0;
        return value;
    }
    
    public boolean isSocketClicked(int mouseX, int mouseY, int socketIndex, boolean isInput) {
        if (socketsHidden) {
            return false;
        }
        int socketX = getSocketX(isInput);
        int socketY = getSocketY(socketIndex, isInput);
        int socketRadius = 6; // Smaller size for more space

        return Math.abs(mouseX - socketX) <= socketRadius && Math.abs(mouseY - socketY) <= socketRadius;
    }

    public int getSensorSlotLeft() {
        return x + SENSOR_SLOT_MARGIN_HORIZONTAL;
    }

    private int getSlotAreaStartY() {
        int top = y + HEADER_HEIGHT;
        if (isParameterNode()) {
            if ((hasParameters() || supportsModeSelection()) && type != NodeType.PARAM_BOOLEAN) {
                int lineCount = parameters.size();
                if (supportsModeSelection()) {
                    lineCount++;
                }
                top += PARAM_PADDING_TOP + lineCount * PARAM_LINE_HEIGHT + PARAM_PADDING_BOTTOM;
                if (hasPopupEditButton()) {
                    top += getPopupEditButtonDisplayHeight();
                }
            } else {
                top += BODY_PADDING_NO_PARAMS;
            }
        } else if (hasParameterSlot()) {
            int slotCount = getParameterSlotCount();
            for (int i = 0; i < slotCount; i++) {
                top += PARAMETER_SLOT_LABEL_HEIGHT + getParameterSlotHeight(i) + PARAMETER_SLOT_BOTTOM_PADDING;
            }
            if (hasCoordinateInputFields()) {
                top += getCoordinateFieldDisplayHeight();
            }
            if (hasSensorSlot() || hasActionSlot()) {
                top += SLOT_AREA_PADDING_TOP;
            }
        } else if (hasAmountInputField() && type != NodeType.CONTROL_REPEAT) {
            top += getAmountFieldDisplayHeight();
            if (hasSensorSlot() || hasActionSlot()) {
                top += SLOT_AREA_PADDING_TOP;
            }
        } else if (hasSensorSlot() || hasActionSlot()) {
            top += SLOT_AREA_PADDING_TOP;
        } else if (hasBooleanToggle()) {
            top += getBooleanToggleAreaHeight();
        } else {
            top += BODY_PADDING_NO_PARAMS;
        }
        return top;
    }

    public int getSensorSlotTop() {
        return getSlotAreaStartY();
    }

    public int getSensorSlotWidth() {
        int minWidth = SENSOR_SLOT_MIN_CONTENT_WIDTH + 2 * SENSOR_SLOT_INNER_PADDING;
        int widthWithMargins = this.width - 2 * SENSOR_SLOT_MARGIN_HORIZONTAL;
        return Math.max(minWidth, widthWithMargins);
    }

    public int getSensorSlotHeight() {
        int sensorContentHeight = attachedSensor != null ? attachedSensor.getHeight() : SENSOR_SLOT_MIN_CONTENT_HEIGHT;
        return sensorContentHeight + 2 * SENSOR_SLOT_INNER_PADDING;
    }

    public boolean isPointInsideSensorSlot(int pointX, int pointY) {
        if (!hasSensorSlot()) {
            return false;
        }
        int slotLeft = getSensorSlotLeft();
        int slotTop = getSensorSlotTop();
        int slotWidth = getSensorSlotWidth();
        int slotHeight = getSensorSlotHeight();
        return pointX >= slotLeft && pointX <= slotLeft + slotWidth &&
               pointY >= slotTop && pointY <= slotTop + slotHeight;
    }

    public int getParameterSlotCount() {
        if (!hasParameterSlot()) {
            return 0;
        }
        if (type == NodeType.SET_VARIABLE) {
            return 2;
        }
        if (type == NodeType.OPERATOR_EQUALS || type == NodeType.OPERATOR_NOT) {
            return 2;
        }
        if (type == NodeType.PLACE || type == NodeType.PLACE_HAND) {
            return 2;
        }
        if (type == NodeType.MOVE_ITEM) {
            return 2;
        }
        if (type == NodeType.WALK) {
            return 2;
        }
        if (type == NodeType.SENSOR_CHAT_MESSAGE) {
            return 2;
        }
        if (type == NodeType.SENSOR_ITEM_IN_SLOT) {
            return 2;
        }
        return 1;
    }

    public int getParameterSlotLeft() {
        return x + PARAMETER_SLOT_MARGIN_HORIZONTAL;
    }

    public int getParameterSlotLeft(int slotIndex) {
        if (type == NodeType.OPERATOR_EQUALS || type == NodeType.OPERATOR_NOT) {
            int slotWidth = getParameterSlotWidth(slotIndex);
            int baseLeft = x + PARAMETER_SLOT_MARGIN_HORIZONTAL;
            if (usesMinimalNodePresentation()) {
                int contentLeft = x + MINIMAL_NODE_TAB_WIDTH + PARAMETER_SLOT_MARGIN_HORIZONTAL;
                int contentWidth = Math.max(0, width - MINIMAL_NODE_TAB_WIDTH - 2 * PARAMETER_SLOT_MARGIN_HORIZONTAL);
                int groupWidth = slotWidth * 2 + OPERATOR_SLOT_GAP;
                int startX = contentLeft + Math.max(0, (contentWidth - groupWidth) / 2);
                if (slotIndex <= 0) {
                    return startX;
                }
                return startX + slotWidth + OPERATOR_SLOT_GAP;
            }
            if (slotIndex <= 0) {
                return baseLeft;
            }
            return baseLeft + slotWidth + OPERATOR_SLOT_GAP;
        }
        return getParameterSlotLeft();
    }

    public int getParameterSlotTop(int slotIndex) {
        int top = y + HEADER_HEIGHT + PARAMETER_SLOT_LABEL_HEIGHT;
        if (hasSchematicDropdownField()) {
            top += getSchematicFieldDisplayHeight();
        }
        if (type == NodeType.OPERATOR_EQUALS || type == NodeType.OPERATOR_NOT) {
            if (usesMinimalNodePresentation()) {
                int slotHeight = getParameterSlotHeight(slotIndex);
                return y + Math.max(0, (height - slotHeight) / 2);
            }
            return top;
        }
        for (int i = 0; i < slotIndex; i++) {
            top += getParameterSlotHeight(i) + PARAMETER_SLOT_BOTTOM_PADDING + PARAMETER_SLOT_LABEL_HEIGHT;
        }
        return top;
    }

    @Deprecated
    public int getParameterSlotTop() {
        return getParameterSlotTop(0);
    }

    public String getParameterSlotLabel(int slotIndex) {
        if (type == NodeType.OPERATOR_EQUALS || type == NodeType.OPERATOR_NOT) {
            return "";
        }
        if (type == NodeType.SET_VARIABLE) {
            Node slot0 = getAttachedParameter(0);
            Node slot1 = getAttachedParameter(1);
            boolean slot0Variable = slot0 != null && slot0.getType() == NodeType.VARIABLE;
            boolean slot1Variable = slot1 != null && slot1.getType() == NodeType.VARIABLE;
            if (slot0Variable && !slot1Variable) {
                return slotIndex == 0 ? "Output" : "Input";
            }
            if (slot1Variable && !slot0Variable) {
                return slotIndex == 1 ? "Output" : "Input";
            }
            return slotIndex == 0 ? "Input" : "Output";
        }
        if (type == NodeType.CHANGE_VARIABLE) {
            return "Variable";
        }
        if (type == NodeType.BUILD) {
            return "Position";
        }
        if (type == NodeType.PLACE || type == NodeType.PLACE_HAND) {
            return slotIndex == 0 ? "Source" : "Position";
        }
        if (type == NodeType.MOVE_ITEM) {
            return slotIndex == 0 ? "Source Slot" : "Target Slot";
        }
        if (type == NodeType.WALK) {
            return slotIndex == 0 ? "Direction" : "Duration/Distance";
        }
        if (type == NodeType.SENSOR_CHAT_MESSAGE) {
            return slotIndex == 0 ? "User" : "Message";
        }
        if (type == NodeType.SENSOR_ITEM_IN_SLOT) {
            return slotIndex == 0 ? "Item" : "Slot";
        }
        if (type == NodeType.SENSOR_POSITION_OF) {
            return "Target";
        }
        if (type == NodeType.SENSOR_VILLAGER_TRADE) {
            return "Villager Trade";
        }
        if (type == NodeType.TRADE) {
            return "Villager Trade";
        }
        return "Parameter";
    }

    public int getParameterSlotWidth() {
        int widthWithMargins = this.width - 2 * PARAMETER_SLOT_MARGIN_HORIZONTAL;
        return Math.max(PARAMETER_SLOT_MIN_CONTENT_WIDTH, widthWithMargins);
    }

    public int getParameterSlotWidth(int slotIndex) {
        if (type == NodeType.OPERATOR_EQUALS || type == NodeType.OPERATOR_NOT) {
            int widthWithMargins = this.width - 2 * PARAMETER_SLOT_MARGIN_HORIZONTAL;
            if (usesMinimalNodePresentation()) {
                widthWithMargins = Math.max(0, this.width - MINIMAL_NODE_TAB_WIDTH - 2 * PARAMETER_SLOT_MARGIN_HORIZONTAL);
            }
            int minCombinedWidth = PARAMETER_SLOT_MIN_CONTENT_WIDTH * 2 + OPERATOR_SLOT_GAP;
            int effectiveWidth = Math.max(minCombinedWidth, widthWithMargins);
            int available = effectiveWidth - OPERATOR_SLOT_GAP;
            return Math.max(PARAMETER_SLOT_MIN_CONTENT_WIDTH, available / 2);
        }
        return getParameterSlotWidth();
    }

    public int getParameterSlotHeight(int slotIndex) {
        Node parameter = getAttachedParameter(slotIndex);
        int contentHeight = parameter != null ? parameter.getHeight() : PARAMETER_SLOT_MIN_CONTENT_HEIGHT;
        return contentHeight + 2 * PARAMETER_SLOT_INNER_PADDING;
    }

    @Deprecated
    public int getParameterSlotHeight() {
        return getParameterSlotHeight(0);
    }

    private int getParameterSlotsBottom() {
        int slotCount = getParameterSlotCount();
        if (slotCount <= 0) {
            return y + HEADER_HEIGHT;
        }
        if (type == NodeType.OPERATOR_EQUALS || type == NodeType.OPERATOR_NOT) {
            int leftHeight = getParameterSlotHeight(0);
            int rightHeight = getParameterSlotHeight(1);
            int maxHeight = Math.max(leftHeight, rightHeight);
            return getParameterSlotTop(0) + maxHeight;
        }
        int lastIndex = slotCount - 1;
        return getParameterSlotTop(lastIndex) + getParameterSlotHeight(lastIndex);
    }

    public boolean hasCoordinateInputFields() {
        return false;
    }

    public int getCoordinateFieldDisplayHeight() {
        if (!hasCoordinateInputFields()) {
            return 0;
        }
        return COORDINATE_FIELD_TOP_MARGIN + COORDINATE_FIELD_LABEL_HEIGHT + COORDINATE_FIELD_HEIGHT + COORDINATE_FIELD_BOTTOM_MARGIN;
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
        return Math.max(COORDINATE_FIELD_WIDTH, coordinateFieldWidthOverride);
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
        return (getCoordinateFieldWidth() * 3) + (COORDINATE_FIELD_SPACING * 2);
    }

    public boolean hasAmountInputField() {
        if (type == NodeType.COLLECT && (mode == null || mode == NodeMode.COLLECT_SINGLE)) {
            return true;
        }
        if (type == NodeType.CRAFT && (mode == null || mode == NodeMode.CRAFT_PLAYER_GUI || mode == NodeMode.CRAFT_CRAFTING_TABLE)) {
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
        if (type == NodeType.SENSOR_CHAT_MESSAGE) {
            return true;
        }
        if (type == NodeType.TRADE) {
            return true;
        }
        if (type == NodeType.CHANGE_VARIABLE) {
            return true;
        }
        return false;
    }

    public boolean hasSchematicDropdownField() {
        return type == NodeType.BUILD;
    }

    public boolean hasStopTargetInputField() {
        return type == NodeType.STOP_CHAIN || type == NodeType.START_CHAIN;
    }

    public int getAmountFieldDisplayHeight() {
        if (!hasAmountInputField()) {
            return 0;
        }
        return AMOUNT_FIELD_TOP_MARGIN + AMOUNT_FIELD_LABEL_HEIGHT + AMOUNT_FIELD_HEIGHT + AMOUNT_FIELD_BOTTOM_MARGIN;
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
        return getAmountFieldLabelTop() + AMOUNT_FIELD_LABEL_HEIGHT;
    }

    public int getAmountFieldLabelHeight() {
        return AMOUNT_FIELD_LABEL_HEIGHT;
    }

    public String getAmountFieldLabel() {
        if (type == NodeType.SENSOR_CHAT_MESSAGE) {
            return "Seconds";
        }
        if (type == NodeType.CONTROL_REPEAT) {
            return "Times";
        }
        return "Amount";
    }

    public String getAmountParameterKey() {
        if (type == NodeType.MOVE_ITEM) {
            return "Count";
        }
        if (type == NodeType.CONTROL_REPEAT) {
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
            width = Math.max(40, width - (AMOUNT_TOGGLE_WIDTH + AMOUNT_TOGGLE_SPACING));
        }
        if (hasAmountSignToggle()) {
            width = Math.max(40, width - (AMOUNT_SIGN_TOGGLE_WIDTH + AMOUNT_TOGGLE_SPACING));
        }
        return Math.max(width, amountFieldWidthOverride);
    }

    public int getAmountFieldLeft() {
        if (hasAmountSignToggle()) {
            return getParameterSlotLeft() + AMOUNT_SIGN_TOGGLE_WIDTH + AMOUNT_TOGGLE_SPACING;
        }
        return getParameterSlotLeft();
    }

    public boolean hasAmountToggle() {
        return type == NodeType.SENSOR_ITEM_IN_INVENTORY
            || type == NodeType.SENSOR_ITEM_IN_SLOT
            || type == NodeType.SENSOR_CHAT_MESSAGE
            || type == NodeType.TRADE;
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
        return getAmountFieldLeft() + getAmountFieldWidth() + AMOUNT_TOGGLE_SPACING;
    }

    public int getAmountToggleTop() {
        return getAmountFieldInputTop() + (getAmountFieldHeight() - AMOUNT_TOGGLE_HEIGHT) / 2;
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
        return getAmountFieldInputTop() + (getAmountFieldHeight() - AMOUNT_SIGN_TOGGLE_HEIGHT) / 2;
    }

    public int getAmountSignToggleWidth() {
        return AMOUNT_SIGN_TOGGLE_WIDTH;
    }

    public int getAmountSignToggleHeight() {
        return AMOUNT_SIGN_TOGGLE_HEIGHT;
    }

    public boolean isAmountSignPositive() {
        NodeParameter param = getParameter("Increase");
        if (param == null || param.getStringValue() == null || param.getStringValue().isEmpty()) {
            return true;
        }
        return param.getBoolValue();
    }

    public void setAmountSignPositive(boolean positive) {
        NodeParameter param = getParameter("Increase");
        if (param != null) {
            param.setStringValueFromUser(Boolean.toString(positive));
        }
    }

    private void ensureAmountToggleParameters() {
        if (!hasAmountToggle()) {
            return;
        }
        if (getParameter("Amount") == null) {
            parameters.add(new NodeParameter("Amount", ParameterType.INTEGER, "1"));
        }
        if (getParameter("UseAmount") == null) {
            parameters.add(new NodeParameter("UseAmount", ParameterType.BOOLEAN, "false"));
        }
    }

    public int getSchematicFieldDisplayHeight() {
        if (!hasSchematicDropdownField()) {
            return 0;
        }
        return SCHEMATIC_FIELD_TOP_MARGIN + SCHEMATIC_FIELD_LABEL_HEIGHT + SCHEMATIC_FIELD_HEIGHT + SCHEMATIC_FIELD_BOTTOM_MARGIN;
    }

    public int getSchematicFieldLabelTop() {
        return y + HEADER_HEIGHT + SCHEMATIC_FIELD_TOP_MARGIN;
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
        return STOP_TARGET_FIELD_TOP_MARGIN + STOP_TARGET_FIELD_HEIGHT + STOP_TARGET_FIELD_BOTTOM_MARGIN;
    }

    public int getStopTargetFieldLabelTop() {
        return getParameterSlotsBottom() + STOP_TARGET_FIELD_TOP_MARGIN;
    }

    public int getStopTargetFieldInputTop() {
        return getStopTargetFieldLabelTop() + STOP_TARGET_FIELD_LABEL_HEIGHT;
    }

    public int getStopTargetFieldLabelHeight() {
        return STOP_TARGET_FIELD_LABEL_HEIGHT;
    }

    public int getStopTargetFieldHeight() {
        return STOP_TARGET_FIELD_HEIGHT;
    }

    public int getStopTargetFieldWidth() {
        return Math.max(STOP_TARGET_FIELD_MIN_WIDTH, stopTargetFieldWidthOverride);
    }

    public int getStopTargetFieldLeft() {
        return x + Math.max(STOP_TARGET_FIELD_MARGIN_HORIZONTAL, (width - getStopTargetFieldWidth()) / 2);
    }

    public boolean isPointInsideParameterSlot(int pointX, int pointY) {
        return getParameterSlotIndexAt(pointX, pointY) >= 0;
    }

    public int getParameterSlotIndexAt(int pointX, int pointY) {
        if (!hasParameterSlot()) {
            return -1;
        }
        int slotCount = getParameterSlotCount();
        for (int i = 0; i < slotCount; i++) {
            int slotLeft = getParameterSlotLeft(i);
            int slotWidth = getParameterSlotWidth(i);
            int slotTop = getParameterSlotTop(i);
            int slotHeight = getParameterSlotHeight(i);
            if (pointX >= slotLeft && pointX <= slotLeft + slotWidth &&
                pointY >= slotTop && pointY <= slotTop + slotHeight) {
                return i;
            }
        }
        return -1;
    }

    public void updateAttachedParameterPositions() {
        for (Integer slotIndex : attachedParameters.keySet()) {
            updateAttachedParameterPosition(slotIndex);
        }
    }

    private void updateAttachedParameterPosition(int slotIndex) {
        Node parameter = getAttachedParameter(slotIndex);
        if (parameter == null) {
            return;
        }
        int slotX = getParameterSlotLeft(slotIndex) + PARAMETER_SLOT_INNER_PADDING;
        int slotY = getParameterSlotTop(slotIndex) + PARAMETER_SLOT_INNER_PADDING;
        int availableWidth = getParameterSlotWidth(slotIndex) - 2 * PARAMETER_SLOT_INNER_PADDING;
        int availableHeight = getParameterSlotHeight(slotIndex) - 2 * PARAMETER_SLOT_INNER_PADDING;
        int parameterX = slotX + Math.max(0, (availableWidth - parameter.getWidth()) / 2);
        int parameterY = slotY + Math.max(0, (availableHeight - parameter.getHeight()) / 2);
        if (parameter.hasAttachedParameter() || parameter.hasAttachedSensor() || parameter.hasAttachedActionNode()) {
            parameter.setPosition(parameterX, parameterY);
        } else {
            parameter.setPositionSilently(parameterX, parameterY);
        }
    }

    public int getActionSlotLeft() {
        return x + ACTION_SLOT_MARGIN_HORIZONTAL;
    }

    public int getActionSlotTop() {
        int top = getSlotAreaStartY();
        if (hasSensorSlot()) {
            top += getSensorSlotHeight();
            if (hasActionSlot()) {
                top += SLOT_VERTICAL_SPACING;
            }
        }
        return top;
    }

    public int getActionSlotWidth() {
        int minWidth = ACTION_SLOT_MIN_CONTENT_WIDTH + 2 * ACTION_SLOT_INNER_PADDING;
        int widthWithMargins = this.width - 2 * ACTION_SLOT_MARGIN_HORIZONTAL;
        return Math.max(minWidth, widthWithMargins);
    }

    public int getActionSlotHeight() {
        int contentHeight = attachedActionNode != null ? attachedActionNode.getHeight() : ACTION_SLOT_MIN_CONTENT_HEIGHT;
        return contentHeight + 2 * ACTION_SLOT_INNER_PADDING;
    }

    public boolean isPointInsideActionSlot(int pointX, int pointY) {
        if (!hasActionSlot()) {
            return false;
        }
        int slotLeft = getActionSlotLeft();
        int slotTop = getActionSlotTop();
        int slotWidth = getActionSlotWidth();
        int slotHeight = getActionSlotHeight();
        return pointX >= slotLeft && pointX <= slotLeft + slotWidth &&
               pointY >= slotTop && pointY <= slotTop + slotHeight;
    }

    public void updateAttachedSensorPosition() {
        if (attachedSensor == null) {
            return;
        }
        int slotX = getSensorSlotLeft() + SENSOR_SLOT_INNER_PADDING;
        int slotY = getSensorSlotTop() + SENSOR_SLOT_INNER_PADDING;
        int availableWidth = getSensorSlotWidth() - 2 * SENSOR_SLOT_INNER_PADDING;
        int availableHeight = getSensorSlotHeight() - 2 * SENSOR_SLOT_INNER_PADDING;
        int sensorX = slotX + Math.max(0, (availableWidth - attachedSensor.getWidth()) / 2);
        int sensorY = slotY + Math.max(0, (availableHeight - attachedSensor.getHeight()) / 2);
        attachedSensor.setPosition(sensorX, sensorY);
    }

    public void updateAttachedActionPosition() {
        if (attachedActionNode == null) {
            return;
        }
        int slotX = getActionSlotLeft() + ACTION_SLOT_INNER_PADDING;
        int slotY = getActionSlotTop() + ACTION_SLOT_INNER_PADDING;
        int availableWidth = getActionSlotWidth() - 2 * ACTION_SLOT_INNER_PADDING;
        int availableHeight = getActionSlotHeight() - 2 * ACTION_SLOT_INNER_PADDING;
        int nodeX = slotX + Math.max(0, (availableWidth - attachedActionNode.getWidth()) / 2);
        int nodeY = slotY + Math.max(0, (availableHeight - attachedActionNode.getHeight()) / 2);
        attachedActionNode.setPosition(nodeX, nodeY);
    }

    public boolean attachSensor(Node sensor) {
        if (!canAcceptSensor() || sensor == null || !sensor.isSensorNode() || sensor == this) {
            return false;
        }

        if (sensor.parentControl == this && attachedSensor == sensor) {
            updateAttachedSensorPosition();
            return true;
        }

        if (sensor.parentControl != null) {
            sensor.parentControl.detachSensor();
        }

        if (attachedSensor != null && attachedSensor != sensor) {
            Node previousSensor = attachedSensor;
            previousSensor.parentControl = null;
            previousSensor.setDragging(false);
            previousSensor.setSelected(false);
            previousSensor.setPositionSilently(this.x + this.width + SENSOR_SLOT_MARGIN_HORIZONTAL, this.y);
        }

        attachedSensor = sensor;
        sensor.parentControl = this;
        sensor.setDragging(false);
        sensor.setSelected(false);

        recalculateDimensions();
        updateAttachedSensorPosition();
        return true;
    }

    public void detachSensor() {
        if (attachedSensor != null) {
            Node sensor = attachedSensor;
            sensor.parentControl = null;
            attachedSensor = null;
            recalculateDimensions();
        }
    }

    public boolean attachParameter(Node parameter) {
        return attachParameter(parameter, 0);
    }

    public boolean attachParameter(Node parameter, int slotIndex) {
        if (parameter == null
            || (!parameter.isParameterNode() && parameter.getType() != NodeType.SENSOR_POSITION_OF)
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

        if (parameter.parentParameterHost == this && parameter.parentParameterSlotIndex == slotIndex) {
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

        Node previousHost = parameter.parentParameterHost;
        int previousSlot = parameter.parentParameterSlotIndex;

        if (previousHost != null && (previousHost != this || previousSlot != slotIndex)) {
            previousHost.detachParameter(previousSlot);
        }

        Node existing = attachedParameters.get(slotIndex);
        if (existing != null && existing != parameter) {
            detachParameter(slotIndex);
        }

        attachedParameters.put(slotIndex, parameter);
        parameter.parentParameterHost = this;
        parameter.parentParameterSlotIndex = slotIndex;
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
        Node parameter = attachedParameters.remove(slotIndex);
        if (parameter == null) {
            return;
        }
        parameter.parentParameterHost = null;
        parameter.parentParameterSlotIndex = -1;
        parameter.setSocketsHidden(false);
        parameter.recalculateDimensions();
        parameter.setPositionSilently(this.x + this.width + PARAMETER_SLOT_MARGIN_HORIZONTAL, this.y);

        refreshAttachedParameterValues();
        recalculateDimensions();
        updateAttachedParameterPositions();
        updateParentControlLayout();
    }

    private void updateParentControlLayout() {
        if (parentControl != null) {
            parentControl.recalculateDimensions();
            parentControl.updateAttachedSensorPosition();
        }
    }

    private void notifyParentParameterHostOfResize() {
        if (parentParameterHost == null || parentParameterSlotIndex < 0) {
            return;
        }
        parentParameterHost.onAttachedParameterResized(parentParameterSlotIndex);
    }

    private void onAttachedParameterResized(int slotIndex) {
        recalculateDimensions();
        updateParentControlLayout();
    }

    private void notifyParentActionControlOfResize() {
        if (parentActionControl == null) {
            return;
        }
        parentActionControl.onAttachedActionResized();
    }

    private void onAttachedActionResized() {
        recalculateDimensions();
        updateAttachedActionPosition();
    }

    private void notifyParentControlOfResize() {
        if (parentControl == null) {
            return;
        }
        parentControl.onAttachedSensorResized();
    }

    private void onAttachedSensorResized() {
        recalculateDimensions();
        updateAttachedSensorPosition();
    }

    private boolean applyParameterValuesFromMap(Map<String, String> values) {
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

    private boolean canApplyParameterValues(Node parameter) {
        if (parameter == null || parameter.getParameters().isEmpty() || this.parameters.isEmpty()) {
            return false;
        }
        Map<String, String> exported = parameter.exportParameterValues();
        if (exported.isEmpty()) {
            return false;
        }
        for (NodeParameter target : this.parameters) {
            String key = target.getName();
            if (exported.containsKey(key)
                || exported.containsKey(normalizeParameterKey(key))
                || exported.containsKey(key.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
    
    private Map<String, String> adjustParameterValuesForSlot(Map<String, String> values, int slotIndex) {
        return adjustParameterValuesForSlot(values, slotIndex, null);
    }

    private Map<String, String> adjustParameterValuesForSlot(Map<String, String> values, int slotIndex, Node parameterNode) {
        if (values == null || values.isEmpty() || slotIndex < 0) {
            return values;
        }
        switch (type) {
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
                    if (parameterType == NodeType.PARAM_BLOCK || parameterType == NodeType.PARAM_PLACE_TARGET) {
                        return filterParameterMap(values, PLACE_POSITION_BLOCK_KEYS);
                    }
                }
                break;
            default:
                break;
        }
        return values;
    }
    
    private Map<String, String> filterParameterMap(Map<String, String> values, Set<String> keysToRemove) {
        if (values == null || values.isEmpty() || keysToRemove == null || keysToRemove.isEmpty()) {
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
        if (attachedParameters.isEmpty()) {
            return;
        }
        List<Integer> slotIndices = new ArrayList<>(attachedParameters.keySet());
        Collections.sort(slotIndices);
        for (Integer slotIndex : slotIndices) {
            Node parameter = attachedParameters.get(slotIndex);
            if (parameter == null) {
                continue;
            }
            Map<String, String> exported = parameter.exportParameterValues();
            if (!exported.isEmpty()) {
                Map<String, String> adjusted = adjustParameterValuesForSlot(exported, slotIndex, parameter);
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
            case GOAL:
            case BUILD:
            case EXPLORE:
            case FOLLOW:
            case PATH:
            case INTERACT:
                if (type == NodeType.GOTO || type == NodeType.GOAL) {
                    return EnumSet.of(ParameterUsage.POSITION, ParameterUsage.LOOK_ORIENTATION);
                }
                return EnumSet.of(ParameterUsage.POSITION);
            case LOOK:
                return EnumSet.of(ParameterUsage.LOOK_ORIENTATION, ParameterUsage.POSITION);
            case WALK:
                if (slotIndex == 0) {
                    return EnumSet.of(ParameterUsage.LOOK_ORIENTATION);
                }
                return EnumSet.noneOf(ParameterUsage.class);
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

    private boolean parameterSupportsUsage(NodeType parameterType, ParameterUsage usage) {
        if (parameterType == null || usage == null) {
            return false;
        }
        switch (usage) {
            case POSITION:
                return parameterProvidesCoordinates(parameterType);
            case LOOK_ORIENTATION:
                return parameterType == NodeType.PARAM_ROTATION || parameterProvidesCoordinates(parameterType);
            default:
                return false;
        }
    }

    public boolean canAcceptActionNode(Node node) {
        if (!canAcceptActionNode() || node == null || node == this || node.isSensorNode()) {
            return false;
        }
        if (node.getType() == NodeType.EVENT_FUNCTION) {
            return false;
        }
        if (attachedActionNode != null && attachedActionNode != node) {
            return false;
        }
        return true;
    }

    public boolean attachActionNode(Node node) {
        if (!canAcceptActionNode(node)) {
            return false;
        }

        if (node.parentActionControl == this && attachedActionNode == node) {
            updateAttachedActionPosition();
            return true;
        }

        if (node.parentActionControl != null) {
            node.parentActionControl.detachActionNode();
        }

        if (attachedActionNode != null && attachedActionNode != node) {
            Node previous = attachedActionNode;
            previous.parentActionControl = null;
            previous.setDragging(false);
            previous.setSelected(false);
            previous.setPositionSilently(this.x + this.width + ACTION_SLOT_MARGIN_HORIZONTAL, this.y);
        }

        attachedActionNode = node;
        node.parentActionControl = this;
        node.setDragging(false);
        node.setSelected(false);
        node.setSocketsHidden(true);

        recalculateDimensions();
        updateAttachedActionPosition();
        return true;
    }

    public void detachActionNode() {
        if (attachedActionNode != null) {
            Node node = attachedActionNode;
            node.parentActionControl = null;
            node.setSocketsHidden(false);
            attachedActionNode = null;
            recalculateDimensions();
        }
    }

    public void setSocketsHidden(boolean hidden) {
        this.socketsHidden = hidden;
    }

    public boolean shouldRenderSockets() {
        return !socketsHidden;
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
        // Handle generalized nodes with modes
        if (mode != null) {
            switch (mode) {
                // GOTO modes
                case GOTO_XYZ:
                    parameters.add(new NodeParameter("X", ParameterType.INTEGER, "0"));
                    parameters.add(new NodeParameter("Y", ParameterType.INTEGER, "0"));
                    parameters.add(new NodeParameter("Z", ParameterType.INTEGER, "0"));
                    break;
                case GOTO_XZ:
                    parameters.add(new NodeParameter("X", ParameterType.INTEGER, "0"));
                    parameters.add(new NodeParameter("Z", ParameterType.INTEGER, "0"));
                    break;
                case GOTO_Y:
                    parameters.add(new NodeParameter("Y", ParameterType.INTEGER, "64"));
                    break;
                case GOTO_BLOCK:
                    parameters.add(new NodeParameter("Block", ParameterType.STRING, "stone"));
                    break;
                // GOAL modes
                case GOAL_XYZ:
                    parameters.add(new NodeParameter("X", ParameterType.INTEGER, "0"));
                    parameters.add(new NodeParameter("Y", ParameterType.INTEGER, "0"));
                    parameters.add(new NodeParameter("Z", ParameterType.INTEGER, "0"));
                    break;
                case GOAL_XZ:
                    parameters.add(new NodeParameter("X", ParameterType.INTEGER, "0"));
                    parameters.add(new NodeParameter("Z", ParameterType.INTEGER, "0"));
                    break;
                case GOAL_Y:
                    parameters.add(new NodeParameter("Y", ParameterType.INTEGER, "64"));
                    break;
                case GOAL_CURRENT:
                case GOAL_CLEAR:
                    // No parameters needed
                    break;
                    
                // COLLECT modes
                case COLLECT_SINGLE:
                    parameters.add(new NodeParameter("Block", ParameterType.BLOCK_TYPE, "stone"));
                    parameters.add(new NodeParameter("Amount", ParameterType.INTEGER, "1"));
                    break;
                case COLLECT_MULTIPLE:
                    parameters.add(new NodeParameter("Blocks", ParameterType.STRING, "stone,dirt"));
                    break;
                    
                // BUILD modes
                case BUILD_PLAYER:
                    parameters.add(new NodeParameter("Schematic", ParameterType.STRING, ""));
                    break;
                case BUILD_XYZ:
                    parameters.add(new NodeParameter("Schematic", ParameterType.STRING, ""));
                    parameters.add(new NodeParameter("X", ParameterType.INTEGER, "0"));
                    parameters.add(new NodeParameter("Y", ParameterType.INTEGER, "0"));
                    parameters.add(new NodeParameter("Z", ParameterType.INTEGER, "0"));
                    break;
                    
                // EXPLORE modes
                case EXPLORE_CURRENT:
                    // No parameters needed
                    break;
                case EXPLORE_XYZ:
                    parameters.add(new NodeParameter("X", ParameterType.INTEGER, "0"));
                    parameters.add(new NodeParameter("Z", ParameterType.INTEGER, "0"));
                    break;
                case EXPLORE_FILTER:
                    parameters.add(new NodeParameter("Filter", ParameterType.STRING, "explore.txt"));
                    break;
                    
                // FOLLOW modes
                case FOLLOW_PLAYER:
                    parameters.add(new NodeParameter("Player", ParameterType.STRING, "Any"));
                    break;
                case FOLLOW_PLAYERS:
                case FOLLOW_ENTITIES:
                    // No parameters needed
                    break;
                case FOLLOW_ENTITY_TYPE:
                    parameters.add(new NodeParameter("Entity", ParameterType.STRING, "cow"));
                    break;

                // CRAFT modes
                case CRAFT_PLAYER_GUI:
                case CRAFT_CRAFTING_TABLE:
                    parameters.add(new NodeParameter("Item", ParameterType.STRING, "stick"));
                    parameters.add(new NodeParameter("Amount", ParameterType.INTEGER, "1"));
                    break;

                // FARM modes
                case FARM_RANGE:
                    parameters.add(new NodeParameter("Range", ParameterType.INTEGER, "10"));
                    break;
                case FARM_WAYPOINT:
                    parameters.add(new NodeParameter("Waypoint", ParameterType.STRING, "farm"));
                    parameters.add(new NodeParameter("Range", ParameterType.INTEGER, "10"));
                    break;
                    
                // STOP modes
                case STOP_NORMAL:
                case STOP_CANCEL:
                case STOP_FORCE:
                    // No parameters needed
                    break;
                // UI Utils modes
                case UI_UTILS_SET_SEND_PACKETS:
                case UI_UTILS_SET_DELAY_PACKETS:
                case UI_UTILS_SET_ENABLED:
                case UI_UTILS_SET_BYPASS_RESOURCE_PACK:
                case UI_UTILS_SET_FORCE_DENY_RESOURCE_PACK:
                    parameters.add(new NodeParameter("Enabled", ParameterType.BOOLEAN, "true"));
                    break;
                case UI_UTILS_FABRICATE_CLICK_SLOT:
                    parameters.add(new NodeParameter("SyncId", ParameterType.INTEGER, "-1"));
                    parameters.add(new NodeParameter("Revision", ParameterType.INTEGER, "-1"));
                    parameters.add(new NodeParameter("Slot", ParameterType.INTEGER, "0"));
                    parameters.add(new NodeParameter("Button", ParameterType.INTEGER, "0"));
                    parameters.add(new NodeParameter("Action", ParameterType.STRING, "PICKUP"));
                    parameters.add(new NodeParameter("TimesToSend", ParameterType.INTEGER, "1"));
                    parameters.add(new NodeParameter("Delay", ParameterType.BOOLEAN, "false"));
                    break;
                case UI_UTILS_FABRICATE_BUTTON_CLICK:
                    parameters.add(new NodeParameter("SyncId", ParameterType.INTEGER, "-1"));
                    parameters.add(new NodeParameter("ButtonId", ParameterType.INTEGER, "0"));
                    parameters.add(new NodeParameter("TimesToSend", ParameterType.INTEGER, "1"));
                    parameters.add(new NodeParameter("Delay", ParameterType.BOOLEAN, "false"));
                    break;

                default:
                    // No parameters needed
                    break;
            }
            return;
        }
        
        // Handle node types that don't use modes
        switch (type) {
            case PLACE:
                parameters.add(new NodeParameter("Block", ParameterType.BLOCK_TYPE, "stone"));
                parameters.add(new NodeParameter("X", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("Y", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("Z", ParameterType.INTEGER, "0"));
                break;
            case WAIT:
                parameters.add(new NodeParameter("Duration", ParameterType.DOUBLE, "1.0"));
                parameters.add(new NodeParameter("MinimumDurationSeconds", ParameterType.DOUBLE, "0.0"));
                parameters.add(new NodeParameter("RandomVarianceSeconds", ParameterType.DOUBLE, "0.0"));
                break;
            case START_CHAIN:
                parameters.add(new NodeParameter("StartNumber", ParameterType.INTEGER, ""));
                break;
            case STOP_CHAIN:
                parameters.add(new NodeParameter("StartNumber", ParameterType.INTEGER, ""));
                break;
            case HOTBAR:
                parameters.add(new NodeParameter("Slot", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("Item", ParameterType.STRING, ""));
                break;
            case DROP_ITEM:
                parameters.add(new NodeParameter("All", ParameterType.BOOLEAN, "false"));
                parameters.add(new NodeParameter("Count", ParameterType.INTEGER, "1"));
                parameters.add(new NodeParameter("IntervalSeconds", ParameterType.DOUBLE, "0.0"));
                break;
            case DROP_SLOT:
                parameters.add(new NodeParameter("Slot", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("Count", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("EntireStack", ParameterType.BOOLEAN, "true"));
                break;
            case MOVE_ITEM:
                parameters.add(new NodeParameter("SourceSlot", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("TargetSlot", ParameterType.INTEGER, "9"));
                parameters.add(new NodeParameter("Count", ParameterType.INTEGER, "0"));
                break;
            case EQUIP_ARMOR:
                parameters.add(new NodeParameter("SourceSlot", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("ArmorSlot", ParameterType.STRING, "head"));
                break;
            case EQUIP_HAND:
                parameters.add(new NodeParameter("SourceSlot", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("Hand", ParameterType.STRING, "main"));
                break;
            case WRITE_BOOK:
                parameters.add(new NodeParameter("Page", ParameterType.INTEGER, "1"));
                break;
            case USE:
                parameters.add(new NodeParameter("Hand", ParameterType.STRING, "main"));
                parameters.add(new NodeParameter("UseDurationSeconds", ParameterType.DOUBLE, "0.0"));
                parameters.add(new NodeParameter("RepeatCount", ParameterType.INTEGER, "1"));
                parameters.add(new NodeParameter("UseIntervalSeconds", ParameterType.DOUBLE, "0.0"));
                parameters.add(new NodeParameter("StopIfUnavailable", ParameterType.BOOLEAN, "true"));
                parameters.add(new NodeParameter("UseUntilEmpty", ParameterType.BOOLEAN, "false"));
                parameters.add(new NodeParameter("AllowBlockInteraction", ParameterType.BOOLEAN, "true"));
                parameters.add(new NodeParameter("AllowEntityInteraction", ParameterType.BOOLEAN, "true"));
                parameters.add(new NodeParameter("SwingAfterUse", ParameterType.BOOLEAN, "true"));
                parameters.add(new NodeParameter("SneakWhileUsing", ParameterType.BOOLEAN, "false"));
                parameters.add(new NodeParameter("RestoreSneakState", ParameterType.BOOLEAN, "true"));
                break;
            case INTERACT:
                parameters.add(new NodeParameter("Hand", ParameterType.STRING, "main"));
                parameters.add(new NodeParameter("Block", ParameterType.BLOCK_TYPE, ""));
                parameters.add(new NodeParameter("PreferEntity", ParameterType.BOOLEAN, "true"));
                parameters.add(new NodeParameter("PreferBlock", ParameterType.BOOLEAN, "true"));
                parameters.add(new NodeParameter("FallbackToItemUse", ParameterType.BOOLEAN, "true"));
                parameters.add(new NodeParameter("SwingOnSuccess", ParameterType.BOOLEAN, "true"));
                parameters.add(new NodeParameter("SneakWhileInteracting", ParameterType.BOOLEAN, "false"));
                parameters.add(new NodeParameter("RestoreSneakState", ParameterType.BOOLEAN, "true"));
                break;
            case PLACE_HAND:
                parameters.add(new NodeParameter("Hand", ParameterType.STRING, "main"));
                parameters.add(new NodeParameter("SneakWhilePlacing", ParameterType.BOOLEAN, "false"));
                parameters.add(new NodeParameter("SwingOnPlace", ParameterType.BOOLEAN, "true"));
                parameters.add(new NodeParameter("RequireBlockHit", ParameterType.BOOLEAN, "true"));
                parameters.add(new NodeParameter("RestoreSneakState", ParameterType.BOOLEAN, "true"));
                break;
            case TRADE:
                parameters.add(new NodeParameter("Amount", ParameterType.INTEGER, "1"));
                parameters.add(new NodeParameter("UseAmount", ParameterType.BOOLEAN, "false"));
                break;
            case LOOK:
                parameters.add(new NodeParameter("Yaw", ParameterType.DOUBLE, "0.0"));
                parameters.add(new NodeParameter("Pitch", ParameterType.DOUBLE, "0.0"));
                break;
            case WALK:
                parameters.add(new NodeParameter("Duration", ParameterType.DOUBLE, "1.0"));
                parameters.add(new NodeParameter("Distance", ParameterType.DOUBLE, "0.0"));
                break;
            case CROUCH:
                parameters.add(new NodeParameter("Active", ParameterType.BOOLEAN, "true"));
                parameters.add(new NodeParameter("ToggleKey", ParameterType.BOOLEAN, "false"));
                parameters.add(new NodeParameter("DurationSeconds", ParameterType.DOUBLE, "0.0"));
                break;
            case SPRINT:
                parameters.add(new NodeParameter("Active", ParameterType.BOOLEAN, "true"));
                parameters.add(new NodeParameter("AllowFlying", ParameterType.BOOLEAN, "false"));
                break;
            case CONTROL_REPEAT:
                parameters.add(new NodeParameter("Count", ParameterType.INTEGER, "10"));
                break;
            case CONTROL_IF:
                break;
            case CONTROL_IF_ELSE:
                break;
            case EVENT_FUNCTION:
                parameters.add(new NodeParameter("Name", ParameterType.STRING, "function"));
                break;
            case EVENT_CALL:
                parameters.add(new NodeParameter("Name", ParameterType.STRING, "function"));
                break;
            case VARIABLE:
                parameters.add(new NodeParameter("Variable", ParameterType.STRING, "variable"));
                break;
            case OPERATOR_RANDOM:
                parameters.add(new NodeParameter("Min", ParameterType.DOUBLE, "0.0"));
                parameters.add(new NodeParameter("Max", ParameterType.DOUBLE, "1.0"));
                parameters.add(new NodeParameter("Seed", ParameterType.STRING, "Any"));
                break;
            case CHANGE_VARIABLE:
                parameters.add(new NodeParameter("Amount", ParameterType.INTEGER, "1"));
                parameters.add(new NodeParameter("Increase", ParameterType.BOOLEAN, "true"));
                break;
            case SENSOR_TOUCHING_BLOCK:
            case SENSOR_TOUCHING_ENTITY:
            case SENSOR_AT_COORDINATES:
            case SENSOR_BLOCK_AHEAD:
            case SENSOR_IS_DAYTIME:
            case SENSOR_IS_RAINING:
            case SENSOR_ITEM_IN_INVENTORY:
                parameters.add(new NodeParameter("Amount", ParameterType.INTEGER, "1"));
                parameters.add(new NodeParameter("UseAmount", ParameterType.BOOLEAN, "false"));
                break;
            case SENSOR_ITEM_IN_SLOT:
                parameters.add(new NodeParameter("Amount", ParameterType.INTEGER, "1"));
                parameters.add(new NodeParameter("UseAmount", ParameterType.BOOLEAN, "false"));
                break;
            case SENSOR_VILLAGER_TRADE:
                break;
            case SENSOR_IS_SWIMMING:
            case SENSOR_IS_IN_LAVA:
            case SENSOR_IS_UNDERWATER:
            case SENSOR_IS_ON_GROUND:
            case SENSOR_KEY_PRESSED:
                break;
            case SENSOR_HEALTH_BELOW:
                parameters.add(new NodeParameter("Amount", ParameterType.DOUBLE, "10.0"));
                break;
            case SENSOR_HUNGER_BELOW:
                parameters.add(new NodeParameter("Amount", ParameterType.INTEGER, "10"));
                break;
            case SENSOR_IS_FALLING:
                parameters.add(new NodeParameter("Distance", ParameterType.DOUBLE, "2.0"));
                break;
            case SENSOR_IS_RENDERED:
                parameters.add(new NodeParameter("Resource", ParameterType.STRING, "stone"));
                break;
            case SENSOR_CHAT_MESSAGE:
                parameters.add(new NodeParameter("Amount", ParameterType.DOUBLE, "10.0"));
                parameters.add(new NodeParameter("UseAmount", ParameterType.BOOLEAN, "true"));
                break;
            case PARAM_COORDINATE:
                parameters.add(new NodeParameter("X", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("Y", ParameterType.INTEGER, "64"));
                parameters.add(new NodeParameter("Z", ParameterType.INTEGER, "0"));
                break;
            case PARAM_BLOCK:
                parameters.add(new NodeParameter("Block", ParameterType.STRING, "stone"));
                parameters.add(new NodeParameter("State", ParameterType.STRING, ""));
                break;
            case PARAM_ITEM:
                parameters.add(new NodeParameter("Item", ParameterType.STRING, "stick"));
                break;
            case PARAM_VILLAGER_TRADE:
                parameters.add(new NodeParameter("Profession", ParameterType.STRING, "librarian"));
                parameters.add(new NodeParameter("Item", ParameterType.STRING, "book"));
                break;
            case PARAM_ENTITY:
                parameters.add(new NodeParameter("Entity", ParameterType.STRING, "cow"));
                parameters.add(new NodeParameter("State", ParameterType.STRING, ""));
                break;
            case PARAM_PLAYER:
                parameters.add(new NodeParameter("Player", ParameterType.STRING, "Any"));
                break;
            case PARAM_MESSAGE:
                parameters.add(new NodeParameter("Text", ParameterType.STRING, "Any"));
                break;
            case PARAM_WAYPOINT:
                parameters.add(new NodeParameter("Waypoint", ParameterType.STRING, "home"));
                parameters.add(new NodeParameter("Range", ParameterType.INTEGER, "10"));
                break;
            case PARAM_SCHEMATIC:
                parameters.add(new NodeParameter("Schematic", ParameterType.STRING, ""));
                parameters.add(new NodeParameter("X", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("Y", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("Z", ParameterType.INTEGER, "0"));
                break;
            case PARAM_INVENTORY_SLOT:
                parameters.add(new NodeParameter("Slot", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("Mode", ParameterType.STRING, "player_inventory"));
                break;
            case PARAM_DURATION:
                parameters.add(new NodeParameter("Duration", ParameterType.DOUBLE, "1.0"));
                break;
            case PARAM_AMOUNT:
                parameters.add(new NodeParameter("Amount", ParameterType.DOUBLE, "1.0"));
                break;
            case PARAM_BOOLEAN:
                parameters.add(new NodeParameter("Toggle", ParameterType.BOOLEAN, "true"));
                break;
            case PARAM_HAND:
                parameters.add(new NodeParameter("Hand", ParameterType.STRING, "main"));
                break;
            case PARAM_GUI:
                parameters.add(new NodeParameter("GUI", ParameterType.STRING, "Any"));
                break;
            case PARAM_KEY:
                parameters.add(new NodeParameter("Key", ParameterType.STRING, "GLFW_KEY_SPACE"));
                break;
            case PARAM_RANGE:
                parameters.add(new NodeParameter("Range", ParameterType.INTEGER, "6"));
                break;
            case PARAM_DISTANCE:
                parameters.add(new NodeParameter("Distance", ParameterType.DOUBLE, "2.0"));
                break;
            case PARAM_ROTATION:
                parameters.add(new NodeParameter("Yaw", ParameterType.DOUBLE, "0.0"));
                parameters.add(new NodeParameter("Pitch", ParameterType.DOUBLE, "0.0"));
                parameters.add(new NodeParameter("YawOffset", ParameterType.DOUBLE, "0.0"));
                parameters.add(new NodeParameter("PitchOffset", ParameterType.DOUBLE, "0.0"));
                parameters.add(new NodeParameter("Distance", ParameterType.DOUBLE, Double.toString(DEFAULT_DIRECTION_DISTANCE)));
                break;
            case PARAM_PLACE_TARGET:
                parameters.add(new NodeParameter("Block", ParameterType.BLOCK_TYPE, "stone"));
                parameters.add(new NodeParameter("X", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("Y", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("Z", ParameterType.INTEGER, "0"));
                break;
            case PARAM_CLOSEST:
                parameters.add(new NodeParameter("Range", ParameterType.INTEGER, "5"));
                break;
            default:
                // No parameters needed
                break;
        }
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
            if (stateParam != null && stateParam.getStringValue() != null && !stateParam.getStringValue().isEmpty()) {
                stateParam.setStringValue("");
            }
        }

        if (!attachedParameters.isEmpty()) {
            for (Node parameterNode : attachedParameters.values()) {
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

    private boolean shouldShowStateParameter() {
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
            if (identifier == null || !Registries.ENTITY_TYPE.containsId(identifier)) {
                return false;
            }
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            return !EntityStateOptions.getOptions(Registries.ENTITY_TYPE.get(identifier), client != null ? client.world : null).isEmpty();
        }
        return false;
    }

    public String getParameterDisplayName(NodeParameter parameter) {
        if (parameter == null) {
            return "";
        }
        String name = parameter.getName();
        if (type == NodeType.PARAM_MESSAGE && "Text".equalsIgnoreCase(name)) {
            return "Message";
        }
        if (type == NodeType.PARAM_PLAYER && "Player".equalsIgnoreCase(name)) {
            return "User";
        }
        if (type == NodeType.PARAM_VILLAGER_TRADE
            && ("Item".equalsIgnoreCase(name) || "Trade".equalsIgnoreCase(name))) {
            return "Trade";
        }
        return name;
    }

    public String getParameterDisplayValue(NodeParameter parameter) {
        if (parameter == null) {
            return "";
        }
        String value = parameter.getDisplayValue();
        if (type == NodeType.PARAM_GUI && "GUI".equalsIgnoreCase(parameter.getName())) {
            return GuiSelectionMode.getDisplayNameOrFallback(value);
        }
        if (type == NodeType.PARAM_VILLAGER_TRADE
            && ("Item".equalsIgnoreCase(parameter.getName()) || "Trade".equalsIgnoreCase(parameter.getName()))) {
            return formatVillagerTradeDisplayValue(value);
        }
        return value;
    }

    private String formatVillagerTradeDisplayValue(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (!value.contains("|") || !value.contains("@")) {
            return value;
        }
        String[] parts = value.split("\\|");
        TradeKeyPart first = parts.length > 0 ? parseTradeKeyPart(parts[0]) : null;
        TradeKeyPart second = parts.length > 1 ? parseTradeKeyPart(parts[1]) : null;
        TradeKeyPart sell = parts.length > 2 ? parseTradeKeyPart(parts[2]) : null;
        if (sell == null || !sell.isValid()) {
            return value;
        }
        StringBuilder builder = new StringBuilder();
        if (first != null && first.isValid()) {
            builder.append(first.format());
        }
        if (second != null && second.isValid()) {
            if (builder.length() > 0) {
                builder.append(" + ");
            }
            builder.append(second.format());
        }
        if (builder.length() > 0) {
            builder.append(" -> ");
        }
        builder.append(sell.format());
        return builder.toString();
    }

    private TradeKeyPart parseTradeKeyPart(String part) {
        if (part == null || part.isEmpty() || "none@0".equals(part)) {
            return TradeKeyPart.empty();
        }
        int atIndex = part.indexOf('@');
        if (atIndex <= 0) {
            return TradeKeyPart.empty();
        }
        String itemId = part.substring(0, atIndex);
        String countRaw = part.substring(atIndex + 1);
        int count = 1;
        try {
            count = Math.max(1, Integer.parseInt(countRaw));
        } catch (NumberFormatException ignored) {
            count = 1;
        }
        Identifier identifier = Identifier.tryParse(itemId);
        if (identifier == null || !Registries.ITEM.containsId(identifier)) {
            return TradeKeyPart.empty();
        }
        return new TradeKeyPart(Registries.ITEM.get(identifier).getName().getString(), count);
    }

    private static final class TradeKeyPart {
        private static final TradeKeyPart EMPTY = new TradeKeyPart("", 0);
        private final String name;
        private final int count;

        private TradeKeyPart(String name, int count) {
            this.name = name;
            this.count = count;
        }

        private static TradeKeyPart empty() {
            return EMPTY;
        }

        private boolean isValid() {
            return name != null && !name.isEmpty() && count > 0;
        }

        private String format() {
            if (count > 1) {
                return count + "x " + name;
            }
            return name;
        }
    }

    public String getParameterLabel(NodeParameter parameter) {
        if (parameter == null) {
            return "";
        }
        if ("State".equalsIgnoreCase(parameter.getName()) && !shouldShowStateParameter()) {
            return "";
        }
        String name = getParameterDisplayName(parameter);
        if (type == NodeType.PARAM_BOOLEAN && "Toggle".equalsIgnoreCase(name)) {
            return "";
        }
        String text = name + ": " + parameter.getDisplayValue();
        if (text.length() <= MAX_PARAMETER_LABEL_LENGTH) {
            return text;
        }
        int maxContentLength = Math.max(0, MAX_PARAMETER_LABEL_LENGTH - 3);
        return text.substring(0, maxContentLength) + "...";
    }

    private int getVisibleParameterLineCount() {
        if (type == NodeType.PARAM_BOOLEAN) {
            return 0;
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

        switch (type) {
            case PARAM_DURATION: {
                String duration = values.get("Duration");
                if (duration != null) {
                    values.put("IntervalSeconds", duration);
                    values.put(normalizeParameterKey("IntervalSeconds"), duration);
                    values.put("WaitSeconds", duration);
                    values.put(normalizeParameterKey("WaitSeconds"), duration);
                    values.put("DurationSeconds", duration);
                    values.put(normalizeParameterKey("DurationSeconds"), duration);
                }
                break;
            }
            case PARAM_AMOUNT: {
                String amount = values.get("Amount");
                if (amount != null) {
                    values.put("Count", amount);
                    values.put(normalizeParameterKey("Count"), amount);
                    values.put("Threshold", amount);
                    values.put(normalizeParameterKey("Threshold"), amount);
                    values.put("Value", amount);
                    values.put(normalizeParameterKey("Value"), amount);
                }
                break;
            }
            case OPERATOR_RANDOM: {
                double min = getDoubleParameter("Min", 0.0);
                double max = getDoubleParameter("Max", 1.0);
                double randomValue = generateRandomValue(min, max);
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
            case SENSOR_POSITION_OF: {
                Node parameterNode = getAttachedParameterOfType(
                    NodeType.PARAM_ENTITY,
                    NodeType.PARAM_BLOCK,
                    NodeType.PARAM_ITEM
                );
                if (parameterNode == null) {
                    break;
                }
                Optional<Vec3d> resolved = resolvePositionTarget(parameterNode, null, null);
                if (resolved.isEmpty()) {
                    break;
                }
                Vec3d position = resolved.get();
                int x = MathHelper.floor(position.x);
                int y = MathHelper.floor(position.y);
                int z = MathHelper.floor(position.z);
                String xValue = Integer.toString(x);
                String yValue = Integer.toString(y);
                String zValue = Integer.toString(z);
                values.put("X", xValue);
                values.put(normalizeParameterKey("X"), xValue);
                values.put("Y", yValue);
                values.put(normalizeParameterKey("Y"), yValue);
                values.put("Z", zValue);
                values.put(normalizeParameterKey("Z"), zValue);
                break;
            }
            case PARAM_ITEM: {
                String items = values.get("Items");
                String item = values.get("Item");
                if ((items == null || items.isEmpty()) && item != null && !item.isEmpty()) {
                    values.put("Items", item);
                    values.put(normalizeParameterKey("Items"), item);
                }
                if ((item == null || item.isEmpty()) && items != null && !items.isEmpty()) {
                    for (String entry : items.split(",")) {
                        String trimmed = entry == null ? null : entry.trim();
                        if (trimmed == null || trimmed.isEmpty()) {
                            continue;
                        }
                        item = trimmed;
                        break;
                    }
                    if (item != null && !item.isEmpty()) {
                        values.put("Item", item);
                        values.put(normalizeParameterKey("Item"), item);
                    }
                }
                String amount = values.get("Amount");
                if (amount != null) {
                    values.put("Count", amount);
                    values.put(normalizeParameterKey("Count"), amount);
                }
                break;
            }
            case PARAM_VILLAGER_TRADE: {
                String item = values.get("Item");
                if (item != null && !item.isEmpty()) {
                    values.put("Items", item);
                    values.put(normalizeParameterKey("Items"), item);
                }
                break;
            }
            case PARAM_INVENTORY_SLOT: {
                String slot = values.get("Slot");
                if (slot != null) {
                    values.put("SourceSlot", slot);
                    values.put(normalizeParameterKey("SourceSlot"), slot);
                    values.put("TargetSlot", slot);
                    values.put(normalizeParameterKey("TargetSlot"), slot);
                    values.put("FirstSlot", slot);
                    values.put(normalizeParameterKey("FirstSlot"), slot);
                    values.put("SecondSlot", slot);
                    values.put(normalizeParameterKey("SecondSlot"), slot);
                }
                break;
            }
            case PARAM_PLAYER: {
                String player = values.get("Player");
                if (player != null) {
                    values.put("Name", player);
                    values.put(normalizeParameterKey("Name"), player);
                }
                break;
            }
            case PARAM_WAYPOINT: {
                String waypoint = values.get("Waypoint");
                if (waypoint != null) {
                    values.put("Name", waypoint);
                    values.put(normalizeParameterKey("Name"), waypoint);
                }
                break;
            }
            case PARAM_BOOLEAN: {
                String toggle = values.get("Toggle");
                if (toggle == null) {
                    toggle = values.get(normalizeParameterKey("Toggle"));
                }
                if (toggle != null) {
                    values.put("Active", toggle);
                    values.put(normalizeParameterKey("Active"), toggle);
                    values.put("Enabled", toggle);
                    values.put(normalizeParameterKey("Enabled"), toggle);
                }
                break;
            }
            case PARAM_BLOCK: {
                String block = values.get("Block");
                String blocks = values.get("Blocks");
                if ((blocks == null || blocks.isEmpty()) && block != null && !block.isEmpty()) {
                    values.put("Blocks", block);
                    values.put(normalizeParameterKey("Blocks"), block);
                }
                if ((block == null || block.isEmpty()) && blocks != null && !blocks.isEmpty()) {
                    for (String entry : blocks.split(",")) {
                        String trimmed = entry == null ? null : entry.trim();
                        if (trimmed == null || trimmed.isEmpty()) {
                            continue;
                        }
                        block = trimmed;
                        break;
                    }
                    if (block != null && !block.isEmpty()) {
                        values.put("Block", block);
                        values.put(normalizeParameterKey("Block"), block);
                    }
                }
                break;
            }
            case PARAM_MESSAGE: {
                String text = values.get("Text");
                if (text != null) {
                    values.put("Message", text);
                    values.put(normalizeParameterKey("Message"), text);
                }
                break;
            }
            case PARAM_ENTITY: {
                String range = values.get("Range");
                if (range != null) {
                    values.put("Distance", range);
                    values.put(normalizeParameterKey("Distance"), range);
                }
                break;
            }
            case PARAM_HAND: {
                String hand = values.get("Hand");
                if (hand != null) {
                    values.put("SourceHand", hand);
                    values.put(normalizeParameterKey("SourceHand"), hand);
                    values.put("TargetHand", hand);
                    values.put(normalizeParameterKey("TargetHand"), hand);
                    values.put("SelectedHand", hand);
                    values.put(normalizeParameterKey("SelectedHand"), hand);
                }
                break;
            }
            case PARAM_RANGE: {
                String range = values.get("Range");
                if (range != null) {
                    values.put("Distance", range);
                    values.put(normalizeParameterKey("Distance"), range);
                    values.put("Radius", range);
                    values.put(normalizeParameterKey("Radius"), range);
                }
                break;
            }
            case PARAM_PLACE_TARGET: {
                String blockId = values.get("Block");
                if (blockId != null) {
                    values.put("BlockId", blockId);
                    values.put(normalizeParameterKey("BlockId"), blockId);
                }
                break;
            }
            case PARAM_CLOSEST: {
                String range = values.get("Range");
                if (range != null) {
                    values.put("Distance", range);
                    values.put(normalizeParameterKey("Distance"), range);
                }
                break;
            }
            case PARAM_ROTATION: {
                String yaw = values.get("Yaw");
                if (yaw != null) {
                    values.put("YawOffset", yaw);
                    values.put(normalizeParameterKey("YawOffset"), yaw);
                }
                String pitch = values.get("Pitch");
                if (pitch != null) {
                    values.put("PitchOffset", pitch);
                    values.put(normalizeParameterKey("PitchOffset"), pitch);
                }
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
            case PARAM_BOOLEAN:
            case SENSOR_IS_SWIMMING:
            case SENSOR_IS_IN_LAVA:
            case SENSOR_IS_UNDERWATER:
            case SENSOR_IS_ON_GROUND:
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
            NodeParameter parameter = getParameter("Toggle");
            if (parameter == null) {
                return true;
            }
            String rawValue = parameter.getStringValue();
            if (rawValue == null || rawValue.isEmpty()) {
                rawValue = parameter.getDefaultValue();
            }
            return Boolean.parseBoolean(rawValue);
        }
        return booleanToggleValue;
    }

    public void setBooleanToggleValue(boolean value) {
        if (type == NodeType.PARAM_BOOLEAN) {
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
        return x + BOOLEAN_TOGGLE_MARGIN_HORIZONTAL;
    }

    public int getBooleanToggleTop() {
        if (type == NodeType.PARAM_BOOLEAN) {
            return y + HEADER_HEIGHT + getParameterDisplayHeight() + BOOLEAN_TOGGLE_TOP_MARGIN;
        }
        return y + HEADER_HEIGHT + BOOLEAN_TOGGLE_TOP_MARGIN;
    }

    public int getBooleanToggleWidth() {
        return Math.max(48, width - 2 * BOOLEAN_TOGGLE_MARGIN_HORIZONTAL);
    }

    public int getBooleanToggleHeight() {
        return BOOLEAN_TOGGLE_HEIGHT;
    }

    public int getBooleanToggleAreaHeight() {
        return BOOLEAN_TOGGLE_TOP_MARGIN + BOOLEAN_TOGGLE_HEIGHT + BOOLEAN_TOGGLE_BOTTOM_MARGIN;
    }

    public boolean supportsModeSelection() {
        NodeMode[] modes = NodeMode.getModesForNodeType(type);
        return modes != null && modes.length > 0;
    }

    public boolean hasMessageInputFields() {
        return type == NodeType.MESSAGE;
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
        messageFieldContentWidthOverride = 0;
        recalculateDimensions();
    }

    public void addMessageLine(String value) {
        if (!hasMessageInputFields()) {
            return;
        }
        messageLines.add(value == null ? "" : value);
        messageFieldContentWidthOverride = 0;
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
        messageFieldContentWidthOverride = 0;
        recalculateDimensions();
        return true;
    }

    public int getMessageFieldDisplayHeight() {
        if (!hasMessageInputFields()) {
            return 0;
        }
        int count = getMessageFieldCount();
        int blockHeight = MESSAGE_FIELD_LABEL_HEIGHT + MESSAGE_FIELD_HEIGHT + MESSAGE_FIELD_VERTICAL_GAP;
        return MESSAGE_FIELD_TOP_MARGIN + (count * blockHeight) - MESSAGE_FIELD_VERTICAL_GAP + MESSAGE_FIELD_BOTTOM_MARGIN;
    }

    public int getMessageFieldLabelTop(int index) {
        return y + HEADER_HEIGHT + MESSAGE_FIELD_TOP_MARGIN + index * (MESSAGE_FIELD_LABEL_HEIGHT + MESSAGE_FIELD_HEIGHT + MESSAGE_FIELD_VERTICAL_GAP);
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
        return Math.max(MESSAGE_FIELD_MIN_CONTENT_WIDTH, width - 2 * MESSAGE_FIELD_MARGIN_HORIZONTAL);
    }

    public void setMessageFieldTextWidth(int textWidth) {
        if (!hasMessageInputFields()) {
            return;
        }
        int paddedWidth = Math.max(MESSAGE_FIELD_MIN_CONTENT_WIDTH, textWidth + (MESSAGE_FIELD_TEXT_PADDING * 2));
        messageFieldContentWidthOverride = paddedWidth;
    }

    public void setParameterFieldWidthOverride(int fieldWidth) {
        if (!isParameterNode()) {
            return;
        }
        parameterFieldWidthOverride = Math.max(0, fieldWidth);
    }

    public void setCoordinateFieldTextWidth(int textWidth) {
        if (!hasCoordinateInputFields()) {
            return;
        }
        int paddedWidth = Math.max(COORDINATE_FIELD_WIDTH, textWidth + (COORDINATE_FIELD_TEXT_PADDING * 2));
        coordinateFieldWidthOverride = paddedWidth;
    }

    public void setAmountFieldTextWidth(int textWidth) {
        if (!hasAmountInputField()) {
            return;
        }
        int paddedWidth = Math.max(PARAMETER_SLOT_MIN_CONTENT_WIDTH, textWidth + (AMOUNT_FIELD_TEXT_PADDING * 2));
        amountFieldWidthOverride = paddedWidth;
    }

    public void setStopTargetFieldTextWidth(int textWidth) {
        if (!hasStopTargetInputField()) {
            return;
        }
        int paddedWidth = Math.max(STOP_TARGET_FIELD_MIN_WIDTH, textWidth + (STOP_TARGET_FIELD_TEXT_PADDING * 2));
        stopTargetFieldWidthOverride = paddedWidth;
    }

    public int getMessageFieldLeft() {
        return x + MESSAGE_FIELD_MARGIN_HORIZONTAL;
    }

    public int getMessageAddButtonLeft() {
        return x + width - MESSAGE_BUTTON_PADDING - MESSAGE_BUTTON_SIZE;
    }

    public int getMessageRemoveButtonLeft() {
        return getMessageAddButtonLeft() - MESSAGE_BUTTON_SPACING - MESSAGE_BUTTON_SIZE;
    }

    public int getMessageButtonTop() {
        return y + 3;
    }

    public int getMessageButtonSize() {
        return MESSAGE_BUTTON_SIZE;
    }

    public int getMessageButtonsWidth() {
        return (MESSAGE_BUTTON_SIZE * 2) + MESSAGE_BUTTON_SPACING + (MESSAGE_BUTTON_PADDING * 2);
    }

    // Book text methods for WRITE_BOOK node
    public boolean hasBookTextInput() {
        return type == NodeType.WRITE_BOOK;
    }

    public String getBookText() {
        return bookText != null ? bookText : "";
    }

    public void setBookText(String text) {
        if (text == null) {
            this.bookText = "";
        } else if (text.length() > BOOK_PAGE_MAX_CHARS) {
            this.bookText = text.substring(0, BOOK_PAGE_MAX_CHARS);
        } else {
            this.bookText = text;
        }
    }

    public int getBookTextMaxChars() {
        return BOOK_PAGE_MAX_CHARS;
    }

    public int getBookTextDisplayHeight() {
        if (!hasBookTextInput()) {
            return 0;
        }
        // Height for: Edit Text button + spacing + Page label + Page field
        return BOOK_TEXT_TOP_MARGIN + BOOK_TEXT_BUTTON_HEIGHT + BOOK_TEXT_FIELD_SPACING
               + BOOK_TEXT_LABEL_HEIGHT + BOOK_TEXT_PAGE_FIELD_HEIGHT + BOOK_TEXT_BOTTOM_MARGIN;
    }

    public int getBookTextButtonTop() {
        return y + HEADER_HEIGHT + BOOK_TEXT_TOP_MARGIN;
    }

    public int getBookTextButtonLeft() {
        return x + BOOK_TEXT_BUTTON_MARGIN_HORIZONTAL;
    }

    public int getBookTextButtonWidth() {
        return Math.max(BOOK_TEXT_BUTTON_MIN_WIDTH, width - 2 * BOOK_TEXT_BUTTON_MARGIN_HORIZONTAL);
    }

    public int getBookTextButtonHeight() {
        return BOOK_TEXT_BUTTON_HEIGHT;
    }

    public int getBookTextPageLabelTop() {
        return getBookTextButtonTop() + BOOK_TEXT_BUTTON_HEIGHT + BOOK_TEXT_FIELD_SPACING;
    }

    public int getBookTextPageFieldTop() {
        return getBookTextPageLabelTop() + BOOK_TEXT_LABEL_HEIGHT;
    }

    public int getBookTextPageFieldLeft() {
        return x + BOOK_TEXT_BUTTON_MARGIN_HORIZONTAL;
    }

    public int getBookTextPageFieldWidth() {
        return width - 2 * BOOK_TEXT_BUTTON_MARGIN_HORIZONTAL;
    }

    public int getBookTextPageFieldHeight() {
        return BOOK_TEXT_PAGE_FIELD_HEIGHT;
    }

    public boolean hasPopupEditButton() {
        if (!isParameterNode() || type == NodeType.PARAM_SCHEMATIC || type == NodeType.PARAM_BOOLEAN) {
            return false;
        }
        return type == NodeType.PARAM_INVENTORY_SLOT
            || type == NodeType.PARAM_KEY
            || type == NodeType.PARAM_VILLAGER_TRADE;
    }

    public int getPopupEditButtonLeft() {
        return x + POPUP_EDIT_BUTTON_MARGIN_HORIZONTAL;
    }

    public int getPopupEditButtonTop() {
        if (isParameterNode()) {
            return y + HEADER_HEIGHT + getParameterDisplayHeight() + POPUP_EDIT_BUTTON_TOP_MARGIN;
        }
        return y + HEADER_HEIGHT;
    }

    public int getPopupEditButtonWidth() {
        return Math.max(POPUP_EDIT_BUTTON_MIN_WIDTH, width - 2 * POPUP_EDIT_BUTTON_MARGIN_HORIZONTAL);
    }

    public int getPopupEditButtonHeight() {
        return POPUP_EDIT_BUTTON_HEIGHT;
    }

    public int getPopupEditButtonDisplayHeight() {
        if (!hasPopupEditButton()) {
            return 0;
        }
        return POPUP_EDIT_BUTTON_TOP_MARGIN + POPUP_EDIT_BUTTON_HEIGHT + POPUP_EDIT_BUTTON_BOTTOM_MARGIN;
    }

    public int getEventNameFieldLeft() {
        return x + EVENT_NAME_FIELD_MARGIN_HORIZONTAL;
    }

    public int getEventNameFieldTop() {
        return y + HEADER_HEIGHT + EVENT_NAME_FIELD_TOP_MARGIN;
    }

    public int getEventNameFieldWidth() {
        return width - 2 * EVENT_NAME_FIELD_MARGIN_HORIZONTAL;
    }

    public int getEventNameFieldHeight() {
        return EVENT_NAME_FIELD_HEIGHT;
    }

    /**
     * Recalculate node dimensions based on current content
     */
    public void recalculateDimensions() {
        if (type == NodeType.START) {
            this.width = START_END_SIZE;
            this.height = START_END_SIZE;
            return;
        }

        int maxTextLength = Math.max(type.getDisplayName().length(), 1);
        if (isParameterNode() || shouldRenderInlineParameters()) {
            for (NodeParameter param : parameters) {
                String paramText = getParameterLabel(param);
                if (paramText.length() > maxTextLength) {
                    maxTextLength = paramText.length();
                }
            }

            if (supportsModeSelection()) {
                String modeLabel = getModeDisplayLabel();
                if (!modeLabel.isEmpty()) {
                    maxTextLength = Math.max(maxTextLength, modeLabel.length());
                }
            }
        }

        int computedWidth = maxTextLength * CHAR_PIXEL_WIDTH + 24; // padding and border allowance
        if (isParameterNode() || shouldRenderInlineParameters()) {
            int maxParameterWidth = 0;
            for (NodeParameter param : parameters) {
                if (param == null) {
                    continue;
                }
                String label = getParameterDisplayName(param);
                String value = getParameterDisplayValue(param);
                int labelLength = label != null ? label.length() : 0;
                int valueLength = value != null ? value.length() : 0;
                int estimatedWidth = (labelLength + valueLength) * CHAR_PIXEL_WIDTH + PARAMETER_FIELD_PADDING;
                maxParameterWidth = Math.max(maxParameterWidth, estimatedWidth);
            }
            if (supportsModeSelection()) {
                String modeLabel = getModeDisplayLabel();
                if (!modeLabel.isEmpty()) {
                    maxParameterWidth = Math.max(maxParameterWidth, modeLabel.length() * CHAR_PIXEL_WIDTH + PARAMETER_FIELD_PADDING);
                }
            }
            int requiredFieldWidth = maxParameterWidth;
            if (parameterFieldWidthOverride > 0) {
                requiredFieldWidth = Math.max(requiredFieldWidth, parameterFieldWidthOverride);
            }
            if (requiredFieldWidth > 0) {
                computedWidth = Math.max(computedWidth, requiredFieldWidth + 10);
            }
        }
        if (hasParameterSlot()) {
            int parameterContentWidth = PARAMETER_SLOT_MIN_CONTENT_WIDTH;
            if (!attachedParameters.isEmpty()) {
                for (Node parameterNode : attachedParameters.values()) {
                    if (parameterNode != null) {
                        parameterContentWidth = Math.max(parameterContentWidth, parameterNode.getWidth());
                    }
                }
            }
            if (type == NodeType.OPERATOR_EQUALS || type == NodeType.OPERATOR_NOT) {
                int slotWidth = parameterContentWidth + 2 * PARAMETER_SLOT_INNER_PADDING;
                int requiredWidth = (slotWidth * 2) + OPERATOR_SLOT_GAP + 2 * PARAMETER_SLOT_MARGIN_HORIZONTAL;
                computedWidth = Math.max(computedWidth, requiredWidth);
            } else {
                int requiredWidth = parameterContentWidth + 2 * (PARAMETER_SLOT_INNER_PADDING + PARAMETER_SLOT_MARGIN_HORIZONTAL);
                computedWidth = Math.max(computedWidth, requiredWidth);
                if (hasCoordinateInputFields()) {
                    int coordinateWidth = getCoordinateFieldTotalWidth() + 2 * PARAMETER_SLOT_MARGIN_HORIZONTAL;
                    computedWidth = Math.max(computedWidth, coordinateWidth);
                }
                if (hasAmountInputField()) {
                    int amountContentWidth = Math.max(PARAMETER_SLOT_MIN_CONTENT_WIDTH, amountFieldWidthOverride);
                    if (hasAmountToggle()) {
                        amountContentWidth += AMOUNT_TOGGLE_WIDTH + AMOUNT_TOGGLE_SPACING;
                    }
                    if (hasAmountSignToggle()) {
                        amountContentWidth += AMOUNT_SIGN_TOGGLE_WIDTH + AMOUNT_TOGGLE_SPACING;
                    }
                    int amountWidth = amountContentWidth + 2 * PARAMETER_SLOT_MARGIN_HORIZONTAL;
                    computedWidth = Math.max(computedWidth, amountWidth);
                }
            }
        }
        if (hasCoordinateInputFields()) {
            int coordinateWidth = getCoordinateFieldTotalWidth() + 2 * PARAMETER_SLOT_MARGIN_HORIZONTAL;
            computedWidth = Math.max(computedWidth, coordinateWidth);
        }
        if (hasAmountInputField()) {
            int amountContentWidth = Math.max(PARAMETER_SLOT_MIN_CONTENT_WIDTH, amountFieldWidthOverride);
            if (hasAmountToggle()) {
                amountContentWidth += AMOUNT_TOGGLE_WIDTH + AMOUNT_TOGGLE_SPACING;
            }
            if (hasAmountSignToggle()) {
                amountContentWidth += AMOUNT_SIGN_TOGGLE_WIDTH + AMOUNT_TOGGLE_SPACING;
            }
            int amountWidth = amountContentWidth + 2 * PARAMETER_SLOT_MARGIN_HORIZONTAL;
            computedWidth = Math.max(computedWidth, amountWidth);
        }
        if (hasSensorSlot()) {
            int sensorContentWidth = SENSOR_SLOT_MIN_CONTENT_WIDTH;
            if (attachedSensor != null) {
                sensorContentWidth = Math.max(sensorContentWidth, attachedSensor.getWidth());
            }
            int requiredWidth = sensorContentWidth + 2 * (SENSOR_SLOT_INNER_PADDING + SENSOR_SLOT_MARGIN_HORIZONTAL);
            computedWidth = Math.max(computedWidth, requiredWidth);
        }
        if (hasActionSlot()) {
            int actionContentWidth = ACTION_SLOT_MIN_CONTENT_WIDTH;
            if (attachedActionNode != null) {
                actionContentWidth = Math.max(actionContentWidth, attachedActionNode.getWidth());
            }
            int requiredWidth = actionContentWidth + 2 * (ACTION_SLOT_INNER_PADDING + ACTION_SLOT_MARGIN_HORIZONTAL);
            computedWidth = Math.max(computedWidth, requiredWidth);
        }
        if (hasStopTargetInputField()) {
            int requiredWidth = Math.max(STOP_TARGET_FIELD_MIN_WIDTH, stopTargetFieldWidthOverride)
                + 2 * STOP_TARGET_FIELD_MARGIN_HORIZONTAL;
            computedWidth = Math.max(computedWidth, requiredWidth);
        }
        if (hasMessageInputFields()) {
            int maxMessageLength = 0;
            for (String line : messageLines) {
                if (line != null) {
                    maxMessageLength = Math.max(maxMessageLength, line.length());
                }
            }
            int messageContentWidth = Math.max(
                MESSAGE_FIELD_MIN_CONTENT_WIDTH,
                maxMessageLength * CHAR_PIXEL_WIDTH + (MESSAGE_FIELD_TEXT_PADDING * 2)
            );
            if (messageFieldContentWidthOverride > 0) {
                messageContentWidth = Math.max(messageContentWidth, messageFieldContentWidthOverride);
            }
            int messageFieldWidth = messageContentWidth + 2 * MESSAGE_FIELD_MARGIN_HORIZONTAL;
            int buttonWidth = (MESSAGE_BUTTON_SIZE * 2) + MESSAGE_BUTTON_SPACING + (MESSAGE_BUTTON_PADDING * 2);
            computedWidth = Math.max(computedWidth, Math.max(messageFieldWidth, buttonWidth));
        }
        if (hasBookTextInput()) {
            int bookTextWidth = BOOK_TEXT_BUTTON_MIN_WIDTH + 2 * BOOK_TEXT_BUTTON_MARGIN_HORIZONTAL;
            computedWidth = Math.max(computedWidth, bookTextWidth);
        }
        if (hasPopupEditButton()) {
            int editButtonWidth = POPUP_EDIT_BUTTON_MIN_WIDTH + 2 * POPUP_EDIT_BUTTON_MARGIN_HORIZONTAL;
            computedWidth = Math.max(computedWidth, editButtonWidth);
        }
        int minWidth = usesMinimalNodePresentation() ? 70 : MIN_WIDTH;
        this.width = Math.max(minWidth, computedWidth);

        int contentHeight = HEADER_HEIGHT;
        boolean hasSlots = hasSensorSlot() || hasActionSlot();

        if (isParameterNode()) {
            int parameterLineCount = getVisibleParameterLineCount();

            if (parameterLineCount > 0) {
                contentHeight += PARAM_PADDING_TOP + (parameterLineCount * PARAM_LINE_HEIGHT) + PARAM_PADDING_BOTTOM;
                if (hasPopupEditButton()) {
                    contentHeight += getPopupEditButtonDisplayHeight();
                }
                if (hasBooleanToggle()) {
                    contentHeight += getBooleanToggleAreaHeight();
                }
                if (hasSlots) {
                    contentHeight += SLOT_AREA_PADDING_TOP;
                }
            } else if (hasSlots) {
                contentHeight += SLOT_AREA_PADDING_TOP;
            } else if (type == NodeType.PARAM_BOOLEAN) {
                contentHeight += getBooleanToggleAreaHeight();
            } else {
                contentHeight += BODY_PADDING_NO_PARAMS;
            }
        } else if (shouldRenderInlineParameters()) {
            int parameterLineCount = getVisibleParameterLineCount();
            if (parameterLineCount > 0) {
                contentHeight += PARAM_PADDING_TOP + (parameterLineCount * PARAM_LINE_HEIGHT) + PARAM_PADDING_BOTTOM;
            } else {
                contentHeight += BODY_PADDING_NO_PARAMS;
            }
        } else if (type == NodeType.EVENT_FUNCTION || type == NodeType.EVENT_CALL) {
            contentHeight += EVENT_NAME_FIELD_TOP_MARGIN + EVENT_NAME_FIELD_HEIGHT + EVENT_NAME_FIELD_BOTTOM_MARGIN;
        } else if (hasParameterSlot()) {
            if (type == NodeType.OPERATOR_EQUALS || type == NodeType.OPERATOR_NOT) {
                int leftHeight = getParameterSlotHeight(0);
                int rightHeight = getParameterSlotHeight(1);
                int maxHeight = Math.max(leftHeight, rightHeight);
                contentHeight += PARAMETER_SLOT_LABEL_HEIGHT + maxHeight + PARAMETER_SLOT_BOTTOM_PADDING;
                if (hasSlots) {
                    contentHeight += SLOT_AREA_PADDING_TOP;
                }
            } else {
                if (hasSchematicDropdownField()) {
                    contentHeight += getSchematicFieldDisplayHeight();
                }
                int slotCount = getParameterSlotCount();
                for (int i = 0; i < slotCount; i++) {
                    contentHeight += PARAMETER_SLOT_LABEL_HEIGHT + getParameterSlotHeight(i) + PARAMETER_SLOT_BOTTOM_PADDING;
                }
                if (hasCoordinateInputFields()) {
                    contentHeight += getCoordinateFieldDisplayHeight();
                }
                if (hasAmountInputField()) {
                    contentHeight += getAmountFieldDisplayHeight();
                }
                if (hasMessageInputFields()) {
                    contentHeight += getMessageFieldDisplayHeight();
                }
                if (hasSlots) {
                    contentHeight += SLOT_AREA_PADDING_TOP;
                }
            }
        } else if (hasAmountInputField()) {
            if (type != NodeType.CONTROL_REPEAT) {
                contentHeight += getAmountFieldDisplayHeight();
            }
            if (hasSlots) {
                contentHeight += SLOT_AREA_PADDING_TOP;
            }
        } else if (hasSlots) {
            contentHeight += SLOT_AREA_PADDING_TOP;
        } else if (type == NodeType.MESSAGE) {
            contentHeight += getMessageFieldDisplayHeight();
        } else if (type == NodeType.WRITE_BOOK) {
            contentHeight += getBookTextDisplayHeight();
        } else if (hasStopTargetInputField()) {
            contentHeight += getStopTargetFieldDisplayHeight();
        } else if (hasBooleanToggle()) {
            contentHeight += getBooleanToggleAreaHeight();
        } else {
            contentHeight += BODY_PADDING_NO_PARAMS;
        }

        if (hasSensorSlot()) {
            contentHeight += getSensorSlotHeight();
        }

        if (hasActionSlot()) {
            if (hasSensorSlot()) {
                contentHeight += SLOT_VERTICAL_SPACING;
            }
            contentHeight += getActionSlotHeight();
        }

        if (hasSlots) {
            contentHeight += SLOT_AREA_PADDING_BOTTOM;
        }
        if (type == NodeType.CONTROL_REPEAT && hasAmountInputField()) {
            contentHeight += getAmountFieldDisplayHeight();
        }

        int computedHeight = Math.max(MIN_HEIGHT, contentHeight);
        int minHeight = usesMinimalNodePresentation() ? 32 : MIN_HEIGHT;
        computedHeight = Math.max(minHeight, contentHeight);

        if (type == NodeType.EVENT_FUNCTION || type == NodeType.VARIABLE) {
            this.height = Math.max(EVENT_FUNCTION_MIN_HEIGHT, contentHeight);
        } else {
            this.height = computedHeight;
        }

        // Function nodes used to be forced into a square layout. That made them as tall
        // as they were wide and left a large amount of empty space around their input
        // field. We now keep them compact by clamping their height to the minimal
        // content they need instead of expanding to match their width.

        if (attachedSensor != null) {
            updateAttachedSensorPosition();
        }
        if (attachedActionNode != null) {
            updateAttachedActionPosition();
        }
        updateAttachedParameterPositions();
        notifyParentParameterHostOfResize();
        notifyParentActionControlOfResize();
        notifyParentControlOfResize();
    }

    /**
     * Get the height needed to display parameters
     */
    public int getParameterDisplayHeight() {
        if (!hasParameters() && !supportsModeSelection()) {
            return 0;
        }
        if (type == NodeType.PARAM_BOOLEAN) {
            return 0;
        }
        int parameterLineCount = getVisibleParameterLineCount();
        if (parameterLineCount <= 0) {
            return 0;
        }
        return PARAM_PADDING_TOP + (parameterLineCount * PARAM_LINE_HEIGHT) + PARAM_PADDING_BOTTOM;
    }

    public String getModeDisplayLabel() {
        if (!supportsModeSelection()) {
            return "";
        }
        NodeMode nodeMode = getMode();
        String modeName = nodeMode != null ? nodeMode.getDisplayName() : "Select Mode";
        return "Mode: " + modeName;
    }

    /**
     * Execute this node asynchronously.
     * Returns a CompletableFuture that completes when the node's command is finished.
     */
    public CompletableFuture<Void> execute() {
        CompletableFuture<Void> future = new CompletableFuture<>();

        // Execute on the main Minecraft thread
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();

        if (hasParameterSlot()) {
            int requiredSlotCount = getParameterSlotCount();
            for (int i = 0; i < requiredSlotCount; i++) {
                if (isParameterSlotRequired(i) && getAttachedParameter(i) == null) {
                    String label = getParameterSlotLabel(i);
                    sendNodeErrorMessage(client, type.getDisplayName() + " requires a " + label.toLowerCase(Locale.ROOT) + " parameter before it can run.");
                    future.complete(null);
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
                    executeNodeCommand(future);
                } catch (Exception e) {
                    System.err.println("Error executing node " + type + ": " + e.getMessage());
                    e.printStackTrace();
                    future.completeExceptionally(e);
                }
            });
        } else {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
        }

        return future;
    }

    private ParameterHandlingResult preprocessAttachedParameter(EnumSet<ParameterUsage> usages, CompletableFuture<Void> future) {
        if (!attachedParameters.isEmpty()) {
            java.util.List<Integer> slotIndices = new java.util.ArrayList<>(attachedParameters.keySet());
            java.util.Collections.sort(slotIndices);
            ParameterHandlingResult result = ParameterHandlingResult.CONTINUE;
            boolean resetRuntime = true;
            for (int slotIndex : slotIndices) {
                ParameterHandlingResult slotResult = preprocessParameterSlot(slotIndex, usages, future, resetRuntime);
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
            ParameterHandlingResult slotResult = preprocessParameterSlot(i, usages, future, resetRuntime);
            resetRuntime = false;
            if (slotResult == ParameterHandlingResult.COMPLETE) {
                result = ParameterHandlingResult.COMPLETE;
                break;
            }
        }
        return result;
    }

    private ParameterHandlingResult preprocessParameterSlot(int slotIndex, EnumSet<ParameterUsage> usages, CompletableFuture<Void> future, boolean resetRuntimeData) {
        if (!canAcceptParameterAt(slotIndex)) {
            return ParameterHandlingResult.CONTINUE;
        }
        if (resetRuntimeData) {
            runtimeParameterData = null;
        }
        Node parameterNode = getAttachedParameter(slotIndex);
        return preprocessParameterNode(parameterNode, slotIndex, usages, future);
    }

    private ParameterHandlingResult preprocessParameterNode(Node parameterNode, int slotIndex, EnumSet<ParameterUsage> usages, CompletableFuture<Void> future) {
        if (parameterNode == null) {
            return ParameterHandlingResult.CONTINUE;
        }
        if (runtimeParameterData == null) {
            runtimeParameterData = new RuntimeParameterData();
        }

        boolean handled = false;
        if (parameterNode.getType() == NodeType.VARIABLE) {
            parameterNode = resolveVariableValueNode(parameterNode, slotIndex, future);
            if (parameterNode == null) {
                return ParameterHandlingResult.COMPLETE;
            }
        }

        if (!reportEmptyParametersForNode(parameterNode, future)) {
            return ParameterHandlingResult.COMPLETE;
        }

        Map<String, String> exported = parameterNode.exportParameterValues();
        Map<String, String> adjustedValues = adjustParameterValuesForSlot(exported, slotIndex, parameterNode);
        if (!exported.isEmpty()) {
            handled = applyParameterValuesFromMap(adjustedValues);
        }

        if (usages.contains(ParameterUsage.POSITION)) {
            Optional<Vec3d> targetVec = resolvePositionTarget(parameterNode, runtimeParameterData, future);
            if (targetVec.isPresent()) {
                handled = true;
                runtimeParameterData.targetVector = targetVec.get();
                applyVectorToCoordinateParameters(targetVec.get());
            } else if (future != null && future.isDone()) {
                return ParameterHandlingResult.COMPLETE;
            }
        }

        if (usages.contains(ParameterUsage.LOOK_ORIENTATION)) {
            boolean oriented = resolveLookOrientation(parameterNode, runtimeParameterData, future);
            if (oriented) {
                handled = true;
            } else if (future != null && future.isDone()) {
                return ParameterHandlingResult.COMPLETE;
            }
        }

        if (!handled && type == NodeType.MOVE_ITEM && parameterNode.getType() == NodeType.PARAM_ITEM) {
            if (resolveMoveItemSlotFromItemParameter(parameterNode, slotIndex, future)) {
                handled = true;
            } else {
                return ParameterHandlingResult.COMPLETE;
            }
        }
        if (!handled && type == NodeType.MOVE_ITEM && parameterNode.getType() == NodeType.PARAM_GUI) {
            handled = true;
        }
        if (!handled && type == NodeType.USE) {
            if (resolveUseParameterSelection(parameterNode, future)) {
                handled = true;
            } else {
                return ParameterHandlingResult.COMPLETE;
            }
        }
        if (!handled && type == NodeType.TRADE
            && parameterNode.getType() == NodeType.PARAM_VILLAGER_TRADE) {
            String tradeKey = resolveTradeKeyFromParameter(parameterNode);
            if (tradeKey != null && !tradeKey.isEmpty()) {
                runtimeParameterData.targetTradeKey = tradeKey;
                runtimeParameterData.targetItemId = getTradeKeySellItemId(tradeKey);
                handled = true;
            } else {
                List<String> itemIds = resolveItemIdsFromParameter(parameterNode);
                if (!itemIds.isEmpty()) {
                    runtimeParameterData.targetItemId = itemIds.get(0);
                    handled = true;
                }
            }
        }

        // Special case: block parameters in slot 0 of PLACE/PLACE_HAND nodes are valid
        // even when usages is empty (they provide block type, not position)
        if (!handled && usages.isEmpty() && (type == NodeType.PLACE || type == NodeType.PLACE_HAND)) {
            NodeType parameterType = parameterNode.getType();
            if (parameterType == NodeType.PARAM_BLOCK
                && parameterNode.parentParameterSlotIndex == 0) {
                handled = true;
            }
            if (parameterType == NodeType.PARAM_INVENTORY_SLOT && parameterNode.parentParameterSlotIndex == 0) {
                handled = true;
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

    private Node resolveVariableValueNode(Node variableNode, int slotIndex, CompletableFuture<Void> future) {
        if (variableNode == null) {
            return null;
        }
        String variableName = getParameterString(variableNode, "Variable");
        if (variableName == null || variableName.trim().isEmpty()) {
            sendVariableError("Variable name cannot be empty.", future);
            return null;
        }

        ExecutionManager manager = ExecutionManager.getInstance();
        Node startNode = getOwningStartNode();
        if (startNode == null && getParentControl() != null) {
            startNode = getParentControl().getOwningStartNode();
        }
        ExecutionManager.RuntimeVariable runtimeVariable = manager.getRuntimeVariable(startNode, variableName.trim());
        if (runtimeVariable == null) {
            sendVariableError("Variable \"" + variableName.trim() + "\" is not set.", future);
            return null;
        }

        NodeType valueType = runtimeVariable.getType();
        if (valueType == null) {
            sendVariableError("Variable \"" + variableName.trim() + "\" has no value.", future);
            return null;
        }

        Node snapshot = new Node(valueType, 0, 0);
        snapshot.setSocketsHidden(true);
        Map<String, String> values = runtimeVariable.getValues();
        if (values != null && !values.isEmpty()) {
            snapshot.applyParameterValuesFromMap(values);
        }

        if (!isParameterSupported(snapshot, slotIndex)) {
            sendVariableError("Variable \"" + variableName.trim() + "\" cannot be used with " + type.getDisplayName() + ".", future);
            return null;
        }

        return snapshot;
    }

    private void sendVariableError(String message, CompletableFuture<Void> future) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client != null) {
            sendNodeErrorMessage(client, message);
        }
        if (future != null && !future.isDone()) {
            future.complete(null);
        }
    }

    private Optional<Vec3d> resolvePositionTarget(Node parameterNode, RuntimeParameterData data, CompletableFuture<Void> future) {
        if (data != null && data.targetVector != null) {
            return Optional.of(data.targetVector);
        }

        NodeType parameterType = parameterNode.getType();
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();

        switch (parameterType) {
            case PARAM_COORDINATE: {
                int x = parseNodeInt(parameterNode, "X", 0);
                int y = parseNodeInt(parameterNode, "Y", 0);
                int z = parseNodeInt(parameterNode, "Z", 0);
                BlockPos pos = new BlockPos(x, y, z);
                if (data != null) {
                    data.targetBlockPos = pos;
                }
                return Optional.of(Vec3d.ofCenter(pos));
            }
            case PARAM_SCHEMATIC: {
                int x = parseNodeInt(parameterNode, "X", 0);
                int y = parseNodeInt(parameterNode, "Y", 0);
                int z = parseNodeInt(parameterNode, "Z", 0);
                BlockPos pos = new BlockPos(x, y, z);
                if (data != null) {
                    data.targetBlockPos = pos;
                    data.schematicName = getParameterString(parameterNode, "Schematic");
                }
                return Optional.of(Vec3d.ofCenter(pos));
            }
            case PARAM_PLACE_TARGET: {
                int x = parseNodeInt(parameterNode, "X", 0);
                int y = parseNodeInt(parameterNode, "Y", 0);
                int z = parseNodeInt(parameterNode, "Z", 0);
                BlockPos pos = new BlockPos(x, y, z);
                if (data != null) {
                    data.targetBlockPos = pos;
                    data.targetBlockId = getBlockParameterValue(parameterNode);
                }
                return Optional.of(Vec3d.ofCenter(pos));
            }
            case PARAM_ITEM: {
                if (client == null || client.player == null) {
                    return Optional.empty();
                }
                List<String> itemIds = resolveItemIdsFromParameter(parameterNode);
                if (itemIds.isEmpty()) {
                    sendParameterSearchFailure("No item selected on parameter for " + type.getDisplayName() + ".", future);
                    return Optional.empty();
                }
                double range = parseNodeDouble(parameterNode, "Range", PARAMETER_SEARCH_RADIUS);
                boolean hasValidCandidate = false;
                for (String candidateId : itemIds) {
                    Identifier identifier = Identifier.tryParse(candidateId);
                    if (identifier == null || !Registries.ITEM.containsId(identifier)) {
                        continue;
                    }
                    hasValidCandidate = true;
                    Item item = Registries.ITEM.get(identifier);
                    Optional<BlockPos> match = findNearestDroppedItem(client, item, range);
                    if (match.isEmpty()) {
                        continue;
                    }
                    if (data != null) {
                        data.targetBlockPos = match.get();
                        data.targetItem = item;
                        data.targetItemId = candidateId;
                    }
                    return Optional.of(Vec3d.ofCenter(match.get()));
                }
                if (!hasValidCandidate) {
                    String reference = itemIds.get(0);
                    sendParameterSearchFailure("Unknown item \"" + reference + "\" for " + type.getDisplayName() + ".", future);
                    return Optional.empty();
                }
                String joined = String.join(", ", itemIds);
                sendParameterSearchFailure("No dropped " + joined + " found for " + type.getDisplayName() + ".", future);
                return Optional.empty();
            }
            case PARAM_ENTITY: {
                if (client == null || client.player == null) {
                    return Optional.empty();
                }
                List<String> entityIds = resolveEntityIdsFromParameter(parameterNode);
                if (entityIds.isEmpty()) {
                    sendParameterSearchFailure("No entity selected on parameter for " + type.getDisplayName() + ".", future);
                    return Optional.empty();
                }
                String state = getEntityParameterState(parameterNode);
                double range = parseNodeDouble(parameterNode, "Range", PARAMETER_SEARCH_RADIUS);
                Entity nearest = null;
                String nearestId = null;
                double nearestDistance = Double.MAX_VALUE;
                for (String candidateId : entityIds) {
                    Identifier identifier = Identifier.tryParse(candidateId);
                    if (identifier == null || !Registries.ENTITY_TYPE.containsId(identifier)) {
                        continue;
                    }
                    EntityType<?> entityType = Registries.ENTITY_TYPE.get(identifier);
                    Optional<Entity> entity = findNearestEntity(client, entityType, range, state);
                    if (entity.isEmpty()) {
                        continue;
                    }
                    double distance = entity.get().squaredDistanceTo(client.player);
                    if (distance < nearestDistance) {
                        nearest = entity.get();
                        nearestId = identifier.toString();
                        nearestDistance = distance;
                    }
                }
                if (nearest == null) {
                    sendParameterSearchFailure("No nearby entity found for " + type.getDisplayName() + ".", future);
                    return Optional.empty();
                }
                if (data != null) {
                    data.targetEntity = nearest;
                    data.targetEntityId = nearestId;
                    data.targetBlockPos = nearest.getBlockPos();
                }
                return Optional.ofNullable(EntityCompatibilityBridge.getPos(nearest));
            }
            case PARAM_PLAYER: {
                if (client == null || client.player == null || client.world == null) {
                    return Optional.empty();
                }
                String playerName = getParameterString(parameterNode, "Player");
                Optional<AbstractClientPlayerEntity> player;
                if (isAnyPlayerValue(playerName)) {
                    player = findNearestPlayer(client, client.player);
                } else {
                    player = client.world.getPlayers().stream()
                        .filter(p -> playerName.equalsIgnoreCase(
                            GameProfileCompatibilityBridge.getName(p.getGameProfile())))
                        .findFirst();
                }
                if (player.isEmpty()) {
                    String message = isAnyPlayerValue(playerName)
                        ? "No players nearby for " + type.getDisplayName() + "."
                        : "Player \"" + playerName + "\" is not nearby for " + type.getDisplayName() + ".";
                    sendParameterSearchFailure(message, future);
                    return Optional.empty();
                }
                String resolvedName = GameProfileCompatibilityBridge.getName(player.get().getGameProfile());
                if (data != null) {
                    data.targetPlayerName = resolvedName != null ? resolvedName : playerName;
                    data.targetBlockPos = player.get().getBlockPos();
                }
                return Optional.ofNullable(EntityCompatibilityBridge.getPos(player.get()));
            }
            case PARAM_BLOCK: {
                if (client == null || client.player == null || client.world == null) {
                    return Optional.empty();
                }
                List<BlockSelection> blocks = resolveBlocksFromParameter(parameterNode);
                if (blocks.isEmpty()) {
                    sendParameterSearchFailure("No blocks defined on parameter for " + type.getDisplayName() + ".", future);
                    return Optional.empty();
                }
                double range = parseNodeDouble(parameterNode, "Range", PARAMETER_SEARCH_RADIUS);
                Optional<BlockPos> match = findNearestBlock(client, blocks, range);
                if (match.isEmpty()) {
                    sendParameterSearchFailure("No matching block from parameter found for " + type.getDisplayName() + ".", future);
                    return Optional.empty();
                }
                if (data != null) {
                    data.targetBlockPos = match.get();
                    data.targetBlockIds = new ArrayList<>();
                    for (BlockSelection selection : blocks) {
                        Identifier id = selection.getBlockId();
                        if (id != null) {
                            data.targetBlockIds.add(selection.asString());
                        }
                    }
                }
                return Optional.of(Vec3d.ofCenter(match.get()));
            }
            case PARAM_ROTATION: {
                if (type != NodeType.GOTO && type != NodeType.GOAL) {
                    return Optional.empty();
                }
                if (mode != NodeMode.GOTO_XYZ && mode != NodeMode.GOTO_XZ
                    && mode != NodeMode.GOAL_XYZ && mode != NodeMode.GOAL_XZ) {
                    return Optional.empty();
                }
                if (client == null || client.player == null) {
                    return Optional.empty();
                }
                Vec3d origin = EntityCompatibilityBridge.getPos(client.player);
                if (origin == null) {
                    return Optional.empty();
                }
                Float yawParam = parseNodeFloat(parameterNode, "Yaw");
                Float pitchParam = parseNodeFloat(parameterNode, "Pitch");
                float yaw = yawParam != null ? yawParam : client.player.getYaw();
                float pitch = pitchParam != null ? pitchParam : client.player.getPitch();
                Float yawOffset = parseNodeFloat(parameterNode, "YawOffset");
                Float pitchOffset = parseNodeFloat(parameterNode, "PitchOffset");
                if (yawOffset != null) {
                    yaw += yawOffset;
                }
                if (pitchOffset != null) {
                    pitch += pitchOffset;
                }
                double distance = Math.max(0.0, parseNodeDouble(parameterNode, "Distance", DEFAULT_DIRECTION_DISTANCE));
                double yawRad = Math.toRadians(yaw);
                double pitchRad = Math.toRadians(pitch);
                double xDir = -Math.sin(yawRad) * Math.cos(pitchRad);
                double yDir = -Math.sin(pitchRad);
                double zDir = Math.cos(yawRad) * Math.cos(pitchRad);
                Vec3d target = origin.add(xDir * distance, yDir * distance, zDir * distance);
                if (data != null) {
                    data.targetVector = target;
                    data.targetBlockPos = new BlockPos(MathHelper.floor(target.x), MathHelper.floor(target.y), MathHelper.floor(target.z));
                    data.resolvedYaw = yaw;
                    data.resolvedPitch = pitch;
                }
                return Optional.of(target);
            }
            default: {
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
                break;
            }
            case PARAM_CLOSEST: {
                if (client == null || client.player == null || client.world == null) {
                    return Optional.empty();
                }
                int range = Math.max(1, parseNodeInt(parameterNode, "Range", 5));
                Optional<BlockPos> open = findNearestOpenBlock(client, range);
                if (open.isEmpty()) {
                    sendParameterSearchFailure("No open block found within range for " + type.getDisplayName() + ".", future);
                    return Optional.empty();
                }
                if (data != null) {
                    data.targetBlockPos = open.get();
                }
                return Optional.of(Vec3d.ofCenter(open.get()));
            }
        }

        return Optional.empty();
    }

    private void applyVectorToCoordinateParameters(Vec3d targetVec) {
        if (targetVec == null) {
            return;
        }
        int x = MathHelper.floor(targetVec.x);
        int y = MathHelper.floor(targetVec.y);
        int z = MathHelper.floor(targetVec.z);
        if (runtimeParameterData != null) {
            runtimeParameterData.targetBlockPos = new BlockPos(x, y, z);
        }
        setParameterValueAndPropagate("X", Integer.toString(x));
        setParameterValueAndPropagate("Y", Integer.toString(y));
        setParameterValueAndPropagate("Z", Integer.toString(z));
    }

    private boolean isPlayerAtCoordinates(Integer targetX, Integer targetY, Integer targetZ) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
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

    private boolean resolveLookOrientation(Node parameterNode, RuntimeParameterData data, CompletableFuture<Void> future) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }

        Float yawParam = parseNodeFloat(parameterNode, "Yaw");
        Float pitchParam = parseNodeFloat(parameterNode, "Pitch");
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
            return true;
        }

        Vec3d target = null;
        if (data != null && data.targetEntity != null && data.targetEntity.isAlive()) {
            target = data.targetEntity.getBoundingBox().getCenter();
        }
        if (target == null && data != null) {
            target = data.targetVector;
        }
        if (target == null) {
            Optional<Vec3d> resolved = resolvePositionTarget(parameterNode, data, future);
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
        float yaw = (float) (MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0D));
        float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z))));
        float clampedPitch = MathHelper.clamp(pitch, -90.0F, 90.0F);

        setParameterIfPresent("Yaw", formatFloat(yaw));
        setParameterIfPresent("Pitch", formatFloat(clampedPitch));

        if (data != null) {
            data.resolvedYaw = yaw;
            data.resolvedPitch = clampedPitch;
        }
        return true;
    }

    private void orientPlayerTowardsRuntimeTarget(net.minecraft.client.MinecraftClient client, RuntimeParameterData data) {
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
                yaw = (float) (MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0D));
                pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z))));
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
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        if (parameterNode != null
            && (this.type == NodeType.PLACE || this.type == NodeType.PLACE_HAND)) {
            NodeType parameterType = parameterNode.getType();
            // Allow PARAM_CLOSEST in any slot
            if (parameterType == NodeType.PARAM_CLOSEST) {
                return;
            }
            // Allow block parameters in slot 0 (they provide block type, not position)
            if (parameterType == NodeType.PARAM_BLOCK
                && parameterNode.parentParameterSlotIndex == 0) {
                return;
            }
        }
        sendNodeErrorMessage(client, "Parameter \"" + parameterNode.getType().getDisplayName() + "\" cannot be used with \"" + this.type.getDisplayName() + "\".");
    }

    private void sendParameterSearchFailure(String message, CompletableFuture<Void> future) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client != null) {
            sendNodeErrorMessage(client, message);
        }
        if (future != null && !future.isDone()) {
            future.complete(null);
        }
    }

    private boolean reportEmptyParametersForNode(Node target, CompletableFuture<Void> future) {
        if (target == null) {
            return true;
        }
        List<String> emptyNames = new ArrayList<>();
        collectEmptyUserEditedParameters(target, emptyNames);
        if (emptyNames.isEmpty()) {
            return true;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client != null) {
            String joined = String.join(", ", emptyNames);
            String subject = target.getType() != null ? target.getType().getDisplayName() + " node" : "node";
            String message = emptyNames.size() == 1
                ? joined + " cannot be empty on " + subject + "."
                : "Parameters " + joined + " cannot be empty on " + subject + ".";
            sendNodeErrorMessage(client, message);
        }
        if (future != null && !future.isDone()) {
            future.complete(null);
        }
        return false;
    }

    private void collectEmptyUserEditedParameters(Node target, List<String> emptyNames) {
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

    private boolean reportEmptyParametersForAttachedParameters(CompletableFuture<Void> future) {
        if (attachedParameters.isEmpty()) {
            return true;
        }
        for (Node parameterNode : attachedParameters.values()) {
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

    private static int parseNodeInt(Node node, String name, int defaultValue) {
        if (node != null && node.getType() == NodeType.OPERATOR_RANDOM) {
            double min = node.getDoubleParameter("Min", 0.0);
            double max = node.getDoubleParameter("Max", 1.0);
            return (int) Math.round(node.generateRandomValue(min, max));
        }
        String value = getParameterString(node, name);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static double parseNodeDouble(Node node, String name, double defaultValue) {
        if (node != null && node.getType() == NodeType.OPERATOR_RANDOM) {
            double min = node.getDoubleParameter("Min", 0.0);
            double max = node.getDoubleParameter("Max", 1.0);
            return node.generateRandomValue(min, max);
        }
        String value = getParameterString(node, name);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean parseNodeBoolean(Node node, String name, boolean defaultValue) {
        String value = getParameterString(node, name);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private static Float parseNodeFloat(Node node, String name) {
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

    private Random getRandomGenerator() {
        String seed = getParameterString(this, "Seed");
        if (seed == null || seed.trim().isEmpty() || isAnySeedValue(seed)) {
            randomGenerator = null;
            randomSeedCache = null;
            return null;
        }
        String trimmed = seed.trim();
        if (randomGenerator == null || randomSeedCache == null || !randomSeedCache.equals(trimmed)) {
            long hashedSeed = hashSeedString(trimmed);
            randomGenerator = new Random(hashedSeed);
            randomSeedCache = trimmed;
        }
        return randomGenerator;
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

    private static boolean isAnySelectionValue(String value) {
        if (value == null) {
            return true;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty()
            || "any".equalsIgnoreCase(trimmed)
            || "any state".equalsIgnoreCase(trimmed);
    }

    private List<BlockSelection> resolveBlocksFromParameter(Node parameterNode) {
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

    private void addBlockSelection(List<BlockSelection> selections, String rawValue) {
        if (rawValue == null || rawValue.isEmpty()) {
            return;
        }
        if (isAnySelectionValue(rawValue)) {
            return;
        }
        BlockSelection.parse(rawValue).ifPresent(selection -> {
            if (selection.getBlock() != null) {
                boolean exists = selections.stream().anyMatch(existing -> existing.asString().equals(selection.asString()));
                if (!exists) {
                    selections.add(selection);
                }
            }
        });
    }

    private List<String> resolveItemIdsFromParameter(Node parameterNode) {
        List<String> itemIds = new ArrayList<>();
        if (parameterNode == null) {
            return itemIds;
        }
        String listValue = getParameterString(parameterNode, "Items");
        for (String entry : splitMultiValueList(listValue)) {
            addItemIdentifier(itemIds, entry);
        }
        for (String entry : splitMultiValueList(getParameterString(parameterNode, "Item"))) {
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

    private String buildTradeKey(ItemStack firstBuy, ItemStack secondBuy, ItemStack sell) {
        return buildTradeKeyPart(firstBuy) + "|"
            + buildTradeKeyPart(secondBuy) + "|"
            + buildTradeKeyPart(sell);
    }

    private String buildTradeKeyPart(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "none@0";
        }
        Identifier id = Registries.ITEM.getId(stack.getItem());
        String itemId = id != null ? id.toString() : "unknown";
        return itemId + "@" + stack.getCount();
    }

    private List<String> resolveEntityIdsFromParameter(Node parameterNode) {
        List<String> entityIds = new ArrayList<>();
        if (parameterNode == null) {
            return entityIds;
        }
        for (String entry : splitMultiValueList(getParameterString(parameterNode, "Entity"))) {
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

    private List<String> splitMultiValueList(String rawValue) {
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

    private List<String> resolveCollectTargets(CompletableFuture<Void> future) {
        List<String> blockIds = new ArrayList<>();

        if (runtimeParameterData != null) {
            if (runtimeParameterData.targetBlockIds != null) {
                for (String id : runtimeParameterData.targetBlockIds) {
                    addBlockIds(blockIds, id);
                }
            } else if (runtimeParameterData.targetBlockId != null) {
                addBlockIds(blockIds, runtimeParameterData.targetBlockId);
            }
        }

        addBlockIds(blockIds, getStringParameter("Block", null));
        addBlockIds(blockIds, getStringParameter("Blocks", null));

        if (blockIds.isEmpty()) {
            sendParameterSearchFailure("No block types specified for " + type.getDisplayName() + ".", future);
            return Collections.emptyList();
        }

        List<String> targets = new ArrayList<>();
        for (String idString : blockIds) {
            Identifier identifier = Identifier.tryParse(idString);
            if (identifier == null || !Registries.BLOCK.containsId(identifier)) {
                sendParameterSearchFailure("Unknown block \"" + idString + "\" for " + type.getDisplayName() + ".", future);
                return Collections.emptyList();
            }
            targets.add(identifier.toString());
        }

        return targets;
    }

    private void addBlockIds(List<String> blockIds, String rawValue) {
        if (rawValue == null) {
            return;
        }
        for (String entry : rawValue.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Identifier identifier = BlockSelection.extractBlockIdentifier(trimmed);
            if (identifier != null) {
                String normalized = identifier.toString();
                if (!blockIds.contains(normalized)) {
                    blockIds.add(normalized);
                }
            } else if (!blockIds.contains(trimmed)) {
                blockIds.add(trimmed);
            }
        }
    }

    private Optional<BlockPos> findNearestBlock(net.minecraft.client.MinecraftClient client, List<BlockSelection> selections, double range) {
        if (client == null || client.player == null || client.world == null || selections == null || selections.isEmpty()) {
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
                    mutable.set(playerPos.getX() + dx, playerPos.getY() + dy, playerPos.getZ() + dz);
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

    private Optional<BlockPos> findNearestOpenBlock(net.minecraft.client.MinecraftClient client, int range) {
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
                    mutable.set(playerPos.getX() + dx, playerPos.getY() + dy, playerPos.getZ() + dz);
                    if (!client.world.getWorldBorder().contains(mutable)) {
                        continue;
                    }
                    if (!isBlockReplaceable(client.world, mutable)) {
                        continue;
                    }
                    if (!hasPlacementSupport(client.world, mutable)) {
                        continue;
                    }
                    Box blockBox = new Box(mutable.getX(), mutable.getY(), mutable.getZ(), mutable.getX() + 1, mutable.getY() + 1, mutable.getZ() + 1);
                    if (!client.world.getOtherEntities(null, blockBox).isEmpty()) {
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
                System.out.println("START node - passing through");
                future.complete(null);
                break;
            case EVENT_FUNCTION:
                System.out.println("Function node - awaiting body execution");
                future.complete(null);
                break;
            case EVENT_CALL:
                System.out.println("Call Function node - dispatching handlers");
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
            case CONTROL_FOREVER:
                executeControlForever(future);
                break;
            case CONTROL_IF:
                executeControlIf(future);
                break;
            case CONTROL_IF_ELSE:
                executeControlIfElse(future);
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
            case UI_UTILS:
                executeUiUtilsCommand(future);
                break;
            case SCREEN_CONTROL:
                executeScreenControlCommand(future);
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
            case MOVE_ITEM:
                executeMoveItemCommand(future);
                break;
            case USE:
                executeUseCommand(future);
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
            case CROUCH:
                executeCrouchCommand(future);
                break;
            case SPRINT:
                executeSprintCommand(future);
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
            case SENSOR_BLOCK_AHEAD:
            case SENSOR_IS_DAYTIME:
            case SENSOR_IS_RAINING:
            case SENSOR_HEALTH_BELOW:
            case SENSOR_HUNGER_BELOW:
            case SENSOR_ITEM_IN_INVENTORY:
            case SENSOR_ITEM_IN_SLOT:
            case SENSOR_VILLAGER_TRADE:
            case SENSOR_IS_SWIMMING:
            case SENSOR_IS_IN_LAVA:
            case SENSOR_IS_UNDERWATER:
            case SENSOR_IS_ON_GROUND:
            case SENSOR_IS_FALLING:
            case SENSOR_IS_RENDERED:
            case SENSOR_KEY_PRESSED:
            case SENSOR_CHAT_MESSAGE:
                completeSensorEvaluation(future);
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
                System.out.println("Unknown node type: " + type);
                future.complete(null);
                break;
        }
    }

    private void executeSetVariableCommand(CompletableFuture<Void> future) {
        Node slot0 = getAttachedParameter(0);
        Node slot1 = getAttachedParameter(1);
        Node variableNode = null;
        Node valueNode = null;

        if (slot0 != null && slot0.getType() == NodeType.VARIABLE) {
            variableNode = slot0;
            valueNode = slot1;
        } else if (slot1 != null && slot1.getType() == NodeType.VARIABLE) {
            variableNode = slot1;
            valueNode = slot0;
        }

        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (variableNode == null || valueNode == null || valueNode.getType() == NodeType.VARIABLE) {
            if (client != null) {
                sendNodeErrorMessage(client, "Set requires an input value and an output variable.");
            }
            future.complete(null);
            return;
        }

        String variableName = getParameterString(variableNode, "Variable");
        if (variableName == null || variableName.trim().isEmpty()) {
            if (client != null) {
                sendNodeErrorMessage(client, "Variable name cannot be empty.");
            }
            future.complete(null);
            return;
        }

        ExecutionManager manager = ExecutionManager.getInstance();
        Node startNode = getOwningStartNode();
        if (startNode == null) {
            if (client != null) {
                sendNodeErrorMessage(client, "No active node tree available for variable assignment.");
            }
            future.complete(null);
            return;
        }

        Map<String, String> values = valueNode.exportParameterValues();
        NodeType valueType = valueNode.getType();
        if (valueType == NodeType.SENSOR_POSITION_OF) {
            valueType = NodeType.PARAM_COORDINATE;
        }
        ExecutionManager.RuntimeVariable value = new ExecutionManager.RuntimeVariable(valueType, values);
        manager.setRuntimeVariable(startNode, variableName.trim(), value);
        future.complete(null);
    }

    private void executeChangeVariableCommand(CompletableFuture<Void> future) {
        Node variableNode = getAttachedParameter(0);

        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (variableNode == null) {
            if (client != null) {
                sendNodeErrorMessage(client, "Change Variable requires a variable.");
            }
            future.complete(null);
            return;
        }

        String variableName = getParameterString(variableNode, "Variable");
        if (variableName == null || variableName.trim().isEmpty()) {
            if (client != null) {
                sendNodeErrorMessage(client, "Variable name cannot be empty.");
            }
            future.complete(null);
            return;
        }

        ExecutionManager manager = ExecutionManager.getInstance();
        Node startNode = getOwningStartNode();
        if (startNode == null) {
            if (client != null) {
                sendNodeErrorMessage(client, "No active node tree available for variable change.");
            }
            future.complete(null);
            return;
        }

        ExecutionManager.RuntimeVariable current = manager.getRuntimeVariable(startNode, variableName.trim());
        if (current == null) {
            if (client != null) {
                sendNodeErrorMessage(client, "Variable \"" + variableName.trim() + "\" is not set.");
            }
            future.complete(null);
            return;
        }

        NodeType valueType = current.getType();
        if (valueType == null) {
            if (client != null) {
                sendNodeErrorMessage(client, "Variable \"" + variableName.trim() + "\" has no value.");
            }
            future.complete(null);
            return;
        }

        Node snapshot = new Node(valueType, 0, 0);
        snapshot.setSocketsHidden(true);
        Map<String, String> values = current.getValues();
        if (values != null && !values.isEmpty()) {
            snapshot.applyParameterValuesFromMap(values);
        }

        int step = Math.abs(getIntParameter("Amount", 1));
        if (step == 0) {
            future.complete(null);
            return;
        }
        if (!isAmountSignPositive()) {
            step = -step;
        }

        if (!applyNumericIncrement(snapshot, step)) {
            if (client != null) {
                sendNodeErrorMessage(client, "Change Variable supports variables with a single numeric value.");
            }
            future.complete(null);
            return;
        }

        Map<String, String> updatedValues = snapshot.exportParameterValues();
        ExecutionManager.RuntimeVariable updated = new ExecutionManager.RuntimeVariable(valueType, updatedValues);
        manager.setRuntimeVariable(startNode, variableName.trim(), updated);
        future.complete(null);
    }

    private boolean applyNumericIncrement(Node snapshot, int step) {
        if (snapshot == null) {
            return false;
        }
        NodeParameter numericParam = null;
        for (NodeParameter param : snapshot.getParameters()) {
            if (param == null) {
                continue;
            }
            if (param.getType() == ParameterType.INTEGER || param.getType() == ParameterType.DOUBLE) {
                if (numericParam != null) {
                    return false;
                }
                numericParam = param;
            }
        }
        if (numericParam == null) {
            return false;
        }
        if (numericParam.getType() == ParameterType.INTEGER) {
            numericParam.setIntValue(numericParam.getIntValue() + step);
        } else {
            numericParam.setDoubleValue(numericParam.getDoubleValue() + step);
        }
        return true;
    }

    
    // Command execution methods that wait for Baritone completion
    private void executeGotoCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.of(ParameterUsage.POSITION), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        if (mode == null) {
            future.completeExceptionally(new RuntimeException("No mode set for GOTO node"));
            return;
        }

        if (!isBaritoneApiAvailable() && isBaritoneModAvailable()) {
            if (executeGotoCommandFallback(future)) {
                return;
            }
        }

        Object baritone = getBaritone();
        if (baritone == null) {
            System.err.println("Baritone not available for goto command");
            future.completeExceptionally(new RuntimeException("Baritone not available"));
            return;
        }

        resetBaritonePathing(baritone);
        Object customGoalProcess = BaritoneApiProxy.getCustomGoalProcess(baritone);

        if (tryExecuteGotoUsingAttachedParameter(baritone, customGoalProcess, future)) {
            return;
        }

        switch (mode) {
            case GOTO_XYZ:
                int x = 0, y = 64, z = 0;
                NodeParameter xParam = getParameter("X");
                NodeParameter yParam = getParameter("Y");
                NodeParameter zParam = getParameter("Z");

                if (xParam != null) x = xParam.getIntValue();
                if (yParam != null) y = yParam.getIntValue();
                if (zParam != null) z = zParam.getIntValue();

                if (isPlayerAtCoordinates(x, y, z)) {
                    future.complete(null);
                    return;
                }

                System.out.println("Executing goto to: " + x + ", " + y + ", " + z);
                startGotoTaskWithBreakGuard(future);
                Object goal = BaritoneApiProxy.createGoalBlock(x, y, z);
                BaritoneApiProxy.setGoalAndPath(customGoalProcess, goal);
                break;
                
            case GOTO_XZ:
                int x2 = 0, z2 = 0;
                NodeParameter xParam2 = getParameter("X");
                NodeParameter zParam2 = getParameter("Z");
                
                if (xParam2 != null) x2 = xParam2.getIntValue();
                if (zParam2 != null) z2 = zParam2.getIntValue();

                if (isPlayerAtCoordinates(x2, null, z2)) {
                    future.complete(null);
                    return;
                }

                System.out.println("Executing goto to: " + x2 + ", " + z2);
                startGotoTaskWithBreakGuard(future);
                Object goal2 = BaritoneApiProxy.createGoalBlock(x2, 0, z2); // Y will be determined by pathfinding
                BaritoneApiProxy.setGoalAndPath(customGoalProcess, goal2);
                break;
                
            case GOTO_Y:
                int y3 = 64;
                NodeParameter yParam3 = getParameter("Y");
                if (yParam3 != null) y3 = yParam3.getIntValue();
                
                System.out.println("Executing goto to Y level: " + y3);
                startGotoTaskWithBreakGuard(future);
                // For Y-only movement, we need to get current X,Z and set goal there
                net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                if (client != null && client.player != null) {
                    if (isPlayerAtCoordinates(null, y3, null)) {
                        future.complete(null);
                        return;
                    }
                    int currentX = (int) client.player.getX();
                    int currentZ = (int) client.player.getZ();
                    Object goal3 = BaritoneApiProxy.createGoalBlock(currentX, y3, currentZ);
                    BaritoneApiProxy.setGoalAndPath(customGoalProcess, goal3);
                }
                break;
                
            case GOTO_BLOCK:
                String block = "stone";
                NodeParameter blockParam = getParameter("Block");
                if (blockParam != null) {
                    block = blockParam.getStringValue();
                }

                System.out.println("Executing goto to block: " + block);
                Object getToBlockProcess = BaritoneApiProxy.getGetToBlockProcess(baritone);
                if (getToBlockProcess == null) {
                    future.completeExceptionally(new RuntimeException("GetToBlock process not available"));
                    break;
                }

                startGotoTaskWithBreakGuard(future);
                BaritoneApiProxy.getToBlock(getToBlockProcess, BaritoneApiProxy.createBlockOptionalMeta(block));
                break;
                
            default:
                future.completeExceptionally(new RuntimeException("Unknown GOTO mode: " + mode));
                break;
        }
    }

    private void startGotoTaskWithBreakGuard(CompletableFuture<Void> future) {
        disableBaritoneBlockBreakingDuringGoto(future);
        PreciseCompletionTracker.getInstance().startTrackingTask(PreciseCompletionTracker.TASK_GOTO, future);
    }

    private void disableBaritoneBlockBreakingDuringGoto(CompletableFuture<Void> future) {
        if (future == null) {
            return;
        }
        Object settings = BaritoneApiProxy.getSettings();
        if (settings == null) {
            return;
        }

        synchronized (GOTO_BREAK_LOCK) {
            if (ACTIVE_GOTO_BLOCKING_REQUESTS.getAndIncrement() == 0) {
                gotoBreakOriginalValue = BaritoneApiProxy.getAllowBreak(settings);
                BaritoneApiProxy.setAllowBreak(settings, false);
            }
        }

        future.whenComplete((result, throwable) -> restoreBaritoneBlockBreakingAfterGoto());
    }

    private static void restoreBaritoneBlockBreakingAfterGoto() {
        Object settings = BaritoneApiProxy.getSettings();
        if (settings == null) {
            return;
        }

        synchronized (GOTO_BREAK_LOCK) {
            int remaining = ACTIVE_GOTO_BLOCKING_REQUESTS.decrementAndGet();
            if (remaining <= 0) {
                ACTIVE_GOTO_BLOCKING_REQUESTS.set(0);
                Boolean originalValue = gotoBreakOriginalValue;
                gotoBreakOriginalValue = null;
                BaritoneApiProxy.setAllowBreak(settings, originalValue != null ? originalValue : Boolean.TRUE);
            }
        }
    }

    private boolean tryExecuteGotoUsingAttachedParameter(Object baritone, Object customGoalProcess, CompletableFuture<Void> future) {
        RuntimeParameterData parameterData = runtimeParameterData;
        if (parameterData != null && parameterData.targetEntity != null) {
            return gotoSpecificEntity(parameterData.targetEntity, customGoalProcess, future);
        }

        Node parameterNode = getAttachedParameter();
        if (parameterNode == null) {
            return false;
        }
        if (parameterNode.getType() == NodeType.VARIABLE) {
            parameterNode = resolveVariableValueNode(parameterNode, 0, future);
            if (parameterNode == null) {
                return true;
            }
        }

        switch (parameterNode.getType()) {
            case PARAM_ITEM:
                return gotoNearestDroppedItem(parameterNode, customGoalProcess, future);
            case PARAM_ENTITY:
                return gotoNearestEntity(parameterNode, customGoalProcess, future);
            case PARAM_PLAYER:
                return gotoNamedPlayer(parameterNode, customGoalProcess, future);
            case PARAM_BLOCK:
                return gotoBlockFromParameter(parameterNode, baritone, future);
            default:
                return false;
        }
    }

    private boolean executeGotoCommandFallback(CompletableFuture<Void> future) {
        if (!isBaritoneModAvailable()) {
            return false;
        }

        boolean[] handled = new boolean[1];
        BlockPos target = resolveGotoFallbackTargetFromAttachedParameter(future, handled);
        if (handled[0]) {
            if (target == null) {
                return true;
            }
            String command = String.format("#goto %d %d %d", target.getX(), target.getY(), target.getZ());
            executeCommand(command);
            future.complete(null);
            return true;
        }

        switch (mode) {
            case GOTO_XYZ: {
                int x = 0, y = 64, z = 0;
                NodeParameter xParam = getParameter("X");
                NodeParameter yParam = getParameter("Y");
                NodeParameter zParam = getParameter("Z");

                if (xParam != null) x = xParam.getIntValue();
                if (yParam != null) y = yParam.getIntValue();
                if (zParam != null) z = zParam.getIntValue();

                if (isPlayerAtCoordinates(x, y, z)) {
                    future.complete(null);
                    return true;
                }
                String command = String.format("#goto %d %d %d", x, y, z);
                executeCommand(command);
                future.complete(null);
                return true;
            }
            case GOTO_XZ: {
                int x = 0, z = 0;
                NodeParameter xParam = getParameter("X");
                NodeParameter zParam = getParameter("Z");

                if (xParam != null) x = xParam.getIntValue();
                if (zParam != null) z = zParam.getIntValue();

                if (isPlayerAtCoordinates(x, null, z)) {
                    future.complete(null);
                    return true;
                }
                String command = String.format("#goto %d %d", x, z);
                executeCommand(command);
                future.complete(null);
                return true;
            }
            case GOTO_Y: {
                int y = 64;
                NodeParameter yParam = getParameter("Y");
                if (yParam != null) y = yParam.getIntValue();

                if (isPlayerAtCoordinates(null, y, null)) {
                    future.complete(null);
                    return true;
                }
                String command = String.format("#goto %d", y);
                executeCommand(command);
                future.complete(null);
                return true;
            }
            case GOTO_BLOCK: {
                String blockId = getStringParameter("Block", null);
                BlockPos pos = resolveGotoFallbackTargetFromBlockId(blockId, future);
                if (pos == null) {
                    return true;
                }
                String command = String.format("#goto %d %d %d", pos.getX(), pos.getY(), pos.getZ());
                executeCommand(command);
                future.complete(null);
                return true;
            }
            default:
                return false;
        }
    }

    private BlockPos resolveGotoFallbackTargetFromAttachedParameter(CompletableFuture<Void> future, boolean[] handled) {
        if (handled != null && handled.length > 0) {
            handled[0] = false;
        }

        RuntimeParameterData parameterData = runtimeParameterData;
        if (parameterData != null && parameterData.targetEntity != null) {
            if (handled != null && handled.length > 0) {
                handled[0] = true;
            }
            return parameterData.targetEntity.getBlockPos();
        }

        Node parameterNode = getAttachedParameter();
        if (parameterNode == null) {
            return null;
        }
        if (parameterNode.getType() == NodeType.VARIABLE) {
            parameterNode = resolveVariableValueNode(parameterNode, 0, future);
            if (parameterNode == null) {
                if (handled != null && handled.length > 0) {
                    handled[0] = true;
                }
                future.complete(null);
                return null;
            }
        }

        if (handled != null && handled.length > 0) {
            handled[0] = true;
        }

        switch (parameterNode.getType()) {
            case PARAM_ITEM: {
                net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                if (client == null || client.player == null || client.world == null) {
                    return null;
                }
                List<String> itemIds = resolveItemIdsFromParameter(parameterNode);
                if (itemIds.isEmpty()) {
                    sendNodeErrorMessage(client, "No item selected for " + type.getDisplayName() + ".");
                    future.complete(null);
                    return null;
                }
                double searchRange = parseDoubleOrDefault(getParameterString(parameterNode, "Range"), PARAMETER_SEARCH_RADIUS);
                Optional<BlockPos> matchedPosition = Optional.empty();
                Item matchedItem = null;
                String matchedItemId = null;

                for (String candidateId : itemIds) {
                    Identifier identifier = Identifier.tryParse(candidateId);
                    if (identifier == null || !Registries.ITEM.containsId(identifier)) {
                        continue;
                    }
                    Item candidateItem = Registries.ITEM.get(identifier);
                    Optional<BlockPos> target = findNearestDroppedItem(client, candidateItem, searchRange);
                    if (target.isPresent()) {
                        matchedPosition = target;
                        matchedItem = candidateItem;
                        matchedItemId = candidateId;
                        break;
                    }
                }

                if (matchedPosition.isEmpty()) {
                    String reference = String.join(", ", itemIds);
                    sendNodeErrorMessage(client, "No dropped " + reference + " found nearby for " + type.getDisplayName() + ".");
                    future.complete(null);
                    return null;
                }

                if (runtimeParameterData != null) {
                    runtimeParameterData.targetBlockPos = matchedPosition.get();
                    runtimeParameterData.targetItem = matchedItem;
                    runtimeParameterData.targetItemId = matchedItemId;
                }

                return matchedPosition.get();
            }
            case PARAM_ENTITY: {
                net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                if (client == null || client.player == null || client.world == null) {
                    return null;
                }
                List<String> entityIds = resolveEntityIdsFromParameter(parameterNode);
                if (entityIds.isEmpty()) {
                    sendNodeErrorMessage(client, "No entity selected for " + type.getDisplayName() + ".");
                    future.complete(null);
                    return null;
                }
                String state = getEntityParameterState(parameterNode);
                double range = parseDoubleOrDefault(getParameterString(parameterNode, "Range"), PARAMETER_SEARCH_RADIUS);
                Entity nearest = null;
                double nearestDistance = Double.MAX_VALUE;
                for (String candidateId : entityIds) {
                    Identifier identifier = Identifier.tryParse(candidateId);
                    if (identifier == null || !Registries.ENTITY_TYPE.containsId(identifier)) {
                        continue;
                    }
                    EntityType<?> entityType = Registries.ENTITY_TYPE.get(identifier);
                    Optional<Entity> target = findNearestEntity(client, entityType, range, state);
                    if (target.isEmpty()) {
                        continue;
                    }
                    double distance = target.get().squaredDistanceTo(client.player);
                    if (distance < nearestDistance) {
                        nearest = target.get();
                        nearestDistance = distance;
                    }
                }
                if (nearest == null) {
                    sendNodeErrorMessage(client, "No matching entity found nearby for " + type.getDisplayName() + ".");
                    future.complete(null);
                    return null;
                }
                if (runtimeParameterData != null) {
                    runtimeParameterData.targetBlockPos = nearest.getBlockPos();
                    runtimeParameterData.targetEntity = nearest;
                }
                return nearest.getBlockPos();
            }
            case PARAM_PLAYER: {
                net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                if (client == null || client.player == null || client.world == null) {
                    return null;
                }
                String playerName = getParameterString(parameterNode, "Player");
                Optional<AbstractClientPlayerEntity> match;
                if (isAnyPlayerValue(playerName)) {
                    match = findNearestPlayer(client, client.player);
                } else {
                    match = client.world.getPlayers().stream()
                        .filter(p -> playerName.equalsIgnoreCase(
                            GameProfileCompatibilityBridge.getName(p.getGameProfile())))
                        .findFirst();
                }

                if (match.isEmpty()) {
                    String message = isAnyPlayerValue(playerName)
                        ? "No players nearby for " + type.getDisplayName() + "."
                        : "Player \"" + playerName + "\" is not nearby for " + type.getDisplayName() + ".";
                    sendNodeErrorMessage(client, message);
                    future.complete(null);
                    return null;
                }

                if (runtimeParameterData != null) {
                    runtimeParameterData.targetBlockPos = match.get().getBlockPos();
                    runtimeParameterData.targetEntity = match.get();
                }
                return match.get().getBlockPos();
            }
            case PARAM_BLOCK: {
                String blockId = getBlockParameterValue(parameterNode);
                BlockPos pos = resolveGotoFallbackTargetFromBlockId(blockId, future);
                if (pos != null && runtimeParameterData != null) {
                    runtimeParameterData.targetBlockPos = pos;
                }
                return pos;
            }
            default:
                if (handled != null && handled.length > 0) {
                    handled[0] = false;
                }
                return null;
        }
    }

    private BlockPos resolveGotoFallbackTargetFromBlockId(String blockId, CompletableFuture<Void> future) {
        if (blockId == null || blockId.isEmpty()) {
            return null;
        }

        List<String> blockIds = splitMultiValueList(blockId);
        if (!blockIds.isEmpty()) {
            blockId = blockIds.get(0);
        }

        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return null;
        }

        String sanitized = sanitizeResourceId(blockId);
        String normalized = (sanitized != null && !sanitized.isEmpty())
            ? normalizeResourceId(sanitized, "minecraft")
            : null;

        if (normalized == null || normalized.isEmpty()) {
            sendNodeErrorMessage(client, "Cannot navigate to block: no block selected.");
            future.complete(null);
            return null;
        }

        Identifier identifier = Identifier.tryParse(normalized);
        if (identifier == null || !Registries.BLOCK.containsId(identifier)) {
            sendNodeErrorMessage(client, "Cannot navigate to block \"" + blockId + "\": unknown identifier.");
            future.complete(null);
            return null;
        }

        Block targetBlock = Registries.BLOCK.get(identifier);
        List<BlockSelection> selections = new ArrayList<>();
        BlockSelection.parse(blockId).ifPresent(selections::add);
        Optional<BlockPos> nearest = findNearestBlock(client, selections, PARAMETER_SEARCH_RADIUS);
        if (nearest.isEmpty()) {
            sendNodeErrorMessage(client, "No " + normalized + " found nearby for " + type.getDisplayName() + ".");
            future.complete(null);
            return null;
        }

        setParameterValueAndPropagate("Block", normalized);

        if (client.player != null) {
            BlockPos playerBlockPos = client.player.getBlockPos();
            BlockPos targetPos = nearest.get();
            if (playerBlockPos.equals(targetPos)) {
                future.complete(null);
                return null;
            }
            if (targetBlock != null && client.world.getBlockState(targetPos).isOf(targetBlock)) {
                double distanceSq = client.player.squaredDistanceTo(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
                if (distanceSq <= 2.25D) {
                    future.complete(null);
                    return null;
                }
            }
        }

        return nearest.get();
    }

    private boolean gotoSpecificEntity(Entity targetEntity, Object customGoalProcess, CompletableFuture<Void> future) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return false;
        }
        if (targetEntity == null || targetEntity.isRemoved()) {
            return false;
        }
        if (customGoalProcess == null) {
            sendNodeErrorMessage(client, "Cannot navigate to entity: goal process unavailable.");
            future.complete(null);
            return true;
        }

        BlockPos pos = targetEntity.getBlockPos();
        startGotoTaskWithBreakGuard(future);
        Object goal = BaritoneApiProxy.createGoalBlock(pos.getX(), pos.getY(), pos.getZ());
        BaritoneApiProxy.setGoalAndPath(customGoalProcess, goal);
        return true;
    }

    private boolean gotoNearestDroppedItem(Node parameterNode, Object customGoalProcess, CompletableFuture<Void> future) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return false;
        }

        List<String> itemIds = resolveItemIdsFromParameter(parameterNode);
        if (itemIds.isEmpty()) {
            return false;
        }

        double searchRange = parseDoubleOrDefault(getParameterString(parameterNode, "Range"), PARAMETER_SEARCH_RADIUS);
        Optional<BlockPos> matchedPosition = Optional.empty();
        Item matchedItem = null;
        String matchedItemId = null;

        for (String candidateId : itemIds) {
            Identifier identifier = Identifier.tryParse(candidateId);
            if (identifier == null || !Registries.ITEM.containsId(identifier)) {
                continue;
            }
            Item candidateItem = Registries.ITEM.get(identifier);
            Optional<BlockPos> target = findNearestDroppedItem(client, candidateItem, searchRange);
            if (target.isPresent()) {
                matchedPosition = target;
                matchedItem = candidateItem;
                matchedItemId = candidateId;
                break;
            }
        }

        if (matchedPosition.isEmpty()) {
            String reference = String.join(", ", itemIds);
            sendNodeErrorMessage(client, "No dropped " + reference + " found nearby for " + type.getDisplayName() + ".");
            future.complete(null);
            return true;
        }

        if (customGoalProcess == null) {
            sendNodeErrorMessage(client, "Cannot navigate to dropped item: goal process unavailable.");
            future.complete(null);
            return true;
        }

        BlockPos pos = matchedPosition.get();
        if (runtimeParameterData != null) {
            runtimeParameterData.targetBlockPos = pos;
            runtimeParameterData.targetItem = matchedItem;
            runtimeParameterData.targetItemId = matchedItemId;
        }

        startGotoTaskWithBreakGuard(future);
        Object goal = BaritoneApiProxy.createGoalBlock(pos.getX(), pos.getY(), pos.getZ());
        BaritoneApiProxy.setGoalAndPath(customGoalProcess, goal);
        return true;
    }

    private boolean gotoNearestEntity(Node parameterNode, Object customGoalProcess, CompletableFuture<Void> future) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return false;
        }

        List<String> entityIds = resolveEntityIdsFromParameter(parameterNode);
        if (entityIds.isEmpty()) {
            return false;
        }
        String state = getEntityParameterState(parameterNode);
        double range = parseDoubleOrDefault(getParameterString(parameterNode, "Range"), PARAMETER_SEARCH_RADIUS);
        Entity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (String candidateId : entityIds) {
            Identifier identifier = Identifier.tryParse(candidateId);
            if (identifier == null || !Registries.ENTITY_TYPE.containsId(identifier)) {
                continue;
            }
            EntityType<?> entityType = Registries.ENTITY_TYPE.get(identifier);
            Optional<Entity> target = findNearestEntity(client, entityType, range, state);
            if (target.isEmpty()) {
                continue;
            }
            double distance = target.get().squaredDistanceTo(client.player);
            if (distance < nearestDistance) {
                nearest = target.get();
                nearestDistance = distance;
            }
        }
        if (nearest == null) {
            sendNodeErrorMessage(client, "No matching entity found nearby for " + type.getDisplayName() + ".");
            future.complete(null);
            return true;
        }

        if (customGoalProcess == null) {
            sendNodeErrorMessage(client, "Cannot navigate to entity: goal process unavailable.");
            future.complete(null);
            return true;
        }

        BlockPos pos = nearest.getBlockPos();
        startGotoTaskWithBreakGuard(future);
        Object goal = BaritoneApiProxy.createGoalBlock(pos.getX(), pos.getY(), pos.getZ());
        BaritoneApiProxy.setGoalAndPath(customGoalProcess, goal);
        return true;
    }

    private boolean gotoNamedPlayer(Node parameterNode, Object customGoalProcess, CompletableFuture<Void> future) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return false;
        }

        String playerName = getParameterString(parameterNode, "Player");
        Optional<AbstractClientPlayerEntity> match;
        if (isAnyPlayerValue(playerName)) {
            match = findNearestPlayer(client, client.player);
        } else {
            match = client.world.getPlayers().stream()
                .filter(p -> playerName.equalsIgnoreCase(
                    GameProfileCompatibilityBridge.getName(p.getGameProfile())))
                .findFirst();
        }

        if (match.isEmpty()) {
            String message = isAnyPlayerValue(playerName)
                ? "No players nearby for " + type.getDisplayName() + "."
                : "Player \"" + playerName + "\" is not nearby for " + type.getDisplayName() + ".";
            sendNodeErrorMessage(client, message);
            future.complete(null);
            return true;
        }

        if (customGoalProcess == null) {
            sendNodeErrorMessage(client, "Cannot navigate to player: goal process unavailable.");
            future.complete(null);
            return true;
        }

        BlockPos pos = match.get().getBlockPos();
        startGotoTaskWithBreakGuard(future);
        Object goal = BaritoneApiProxy.createGoalBlock(pos.getX(), pos.getY(), pos.getZ());
        BaritoneApiProxy.setGoalAndPath(customGoalProcess, goal);
        return true;
    }

    private boolean gotoBlockFromParameter(Node parameterNode, Object baritone, CompletableFuture<Void> future) {
        String blockId = getBlockParameterValue(parameterNode);
        if (blockId == null || blockId.isEmpty()) {
            return false;
        }
        List<String> blockIds = splitMultiValueList(blockId);
        if (!blockIds.isEmpty()) {
            blockId = blockIds.get(0);
        }

        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        RuntimeParameterData parameterData = runtimeParameterData;
        BlockPos targetPos = parameterData != null ? parameterData.targetBlockPos : null;
        String sanitized = sanitizeResourceId(blockId);
        String normalized = (sanitized != null && !sanitized.isEmpty())
            ? normalizeResourceId(sanitized, "minecraft")
            : null;
        Block targetBlock = null;

        if (client != null && client.world != null) {
            if (normalized == null || normalized.isEmpty()) {
                sendNodeErrorMessage(client, "Cannot navigate to block: no block selected.");
                future.complete(null);
                return true;
            }

            Identifier identifier = Identifier.tryParse(normalized);
            if (identifier == null || !Registries.BLOCK.containsId(identifier)) {
                sendNodeErrorMessage(client, "Cannot navigate to block \"" + blockId + "\": unknown identifier.");
                future.complete(null);
                return true;
            }

            targetBlock = Registries.BLOCK.get(identifier);
            if (targetPos == null) {
                List<BlockSelection> selections = new ArrayList<>();
                BlockSelection.parse(blockId).ifPresent(selections::add);
                Optional<BlockPos> nearest = findNearestBlock(client, selections, PARAMETER_SEARCH_RADIUS);
                if (nearest.isEmpty()) {
                    sendNodeErrorMessage(client, "No " + normalized + " found nearby for " + type.getDisplayName() + ".");
                    future.complete(null);
                    return true;
                }
                targetPos = nearest.get();
            }

            setParameterValueAndPropagate("Block", normalized);

            if (client.player != null && targetPos != null && targetBlock != null
                && client.world.getBlockState(targetPos).isOf(targetBlock)) {
                BlockPos playerBlockPos = client.player.getBlockPos();
                if (playerBlockPos.equals(targetPos)) {
                    future.complete(null);
                    return true;
                }
                double distanceSq = client.player.squaredDistanceTo(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
                if (distanceSq <= 2.25D) { // already within ~1.5 blocks, treat as complete
                    future.complete(null);
                    return true;
                }
            }
        }

        if (targetPos != null) {
            Object customGoalProcess = baritone != null ? BaritoneApiProxy.getCustomGoalProcess(baritone) : null;
            if (customGoalProcess == null) {
                if (client != null) {
                    sendNodeErrorMessage(client, "Cannot navigate to block: goal process unavailable.");
                }
                future.complete(null);
                return true;
            }

            startGotoTaskWithBreakGuard(future);
            Object goal = BaritoneApiProxy.createGoalNear(targetPos, 1);
            BaritoneApiProxy.setGoalAndPath(customGoalProcess, goal);
            return true;
        }

        Object getToBlockProcess = baritone != null ? BaritoneApiProxy.getGetToBlockProcess(baritone) : null;
        if (getToBlockProcess == null) {
            if (client != null) {
                sendNodeErrorMessage(client, "Cannot navigate to block: block search process unavailable.");
            }
            future.complete(null);
            return true;
        }

        startGotoTaskWithBreakGuard(future);
        String targetId = (normalized != null && !normalized.isEmpty()) ? normalized : blockId;
        BaritoneApiProxy.getToBlock(getToBlockProcess, BaritoneApiProxy.createBlockOptionalMeta(targetId));
        return true;
    }
    
    private void executeCollectCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        if (mode == null) {
            future.completeExceptionally(new RuntimeException("No mode set for COLLECT node"));
            return;
        }

        List<String> targets = resolveCollectTargets(future);
        if (targets.isEmpty()) {
            return;
        }

        if (!isBaritoneApiAvailable() && isBaritoneModAvailable()) {
            switch (mode) {
                case COLLECT_SINGLE: {
                    int amount = Math.max(1, getIntParameter("Amount", 1));
                    if (hasRequiredBlockAlready(targets.get(0), amount)) {
                        future.complete(null);
                        return;
                    }
                    String command = "#mine " + targets.get(0);
                    if (amount > 1) {
                        command += " " + amount;
                    }
                    executeCommand(command);
                    future.complete(null);
                    return;
                }
                case COLLECT_MULTIPLE: {
                    String command = "#mine " + String.join(" ", targets);
                    executeCommand(command);
                    future.complete(null);
                    return;
                }
                default:
                    future.completeExceptionally(new RuntimeException("Unknown COLLECT mode: " + mode));
                    return;
            }
        }

        Object baritone = getBaritone();
        if (baritone == null) {
            future.completeExceptionally(new RuntimeException("Baritone not available"));
            return;
        }
        Object mineProcess = BaritoneApiProxy.getMineProcess(baritone);
        if (mineProcess == null) {
            future.completeExceptionally(new RuntimeException("Mine process not available"));
            return;
        }

        switch (mode) {
            case COLLECT_SINGLE: {
                int amount = Math.max(1, getIntParameter("Amount", 1));
                if (hasRequiredBlockAlready(targets.get(0), amount)) {
                    future.complete(null);
                    return;
                }
                System.out.println("Executing mine by name for " + amount + "x " + targets);
                resetBaritonePathing(baritone, mineProcess);
                PreciseCompletionTracker.getInstance().startTrackingTask(PreciseCompletionTracker.TASK_COLLECT, future);
                // Dispatch Baritone calls off the render thread so the client never blocks
                CompletableFuture.runAsync(() -> {
                    try {
                        BaritoneApiProxy.mineByName(mineProcess, amount, targets.toArray(new String[0]));
                        System.out.println("Collect (single) dispatched mine command; active=" + BaritoneApiProxy.isProcessActive(mineProcess));
                    } catch (Exception e) {
                        System.err.println("Failed to start mine command: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
                break;
            }
            case COLLECT_MULTIPLE: {
                System.out.println("Executing mine by name for blocks: " + targets);
                resetBaritonePathing(baritone, mineProcess);
                PreciseCompletionTracker.getInstance().startTrackingTask(PreciseCompletionTracker.TASK_COLLECT, future);
                // Dispatch Baritone calls off the render thread so the client never blocks
                CompletableFuture.runAsync(() -> {
                    try {
                        BaritoneApiProxy.mineByName(mineProcess, targets.toArray(new String[0]));
                        System.out.println("Collect (multi) dispatched mine command; active=" + BaritoneApiProxy.isProcessActive(mineProcess));
                    } catch (Exception e) {
                        System.err.println("Failed to start mine command: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
                break;
            }
            default:
                future.completeExceptionally(new RuntimeException("Unknown COLLECT mode: " + mode));
                break;
        }
    }

    private void resetBaritonePathing(Object baritone, Object mineProcess) {
        if (baritone == null) {
            return;
        }

        try {
            if (mineProcess != null) {
                if (BaritoneApiProxy.isProcessActive(mineProcess)) {
                    BaritoneApiProxy.cancelMine(mineProcess);
                }
                // Ensure any queued mine targets from previous runs are cleared
                BaritoneApiProxy.onLostControl(mineProcess);
            }

            Object pathingBehavior = BaritoneApiProxy.getPathingBehavior(baritone);
            if (pathingBehavior != null && (BaritoneApiProxy.isPathing(pathingBehavior) || BaritoneApiProxy.hasPath(pathingBehavior))) {
                BaritoneApiProxy.cancelEverything(pathingBehavior);
            }

            Object goalProcess = BaritoneApiProxy.getCustomGoalProcess(baritone);
            if (goalProcess != null) {
                BaritoneApiProxy.setGoal(goalProcess, null);
                BaritoneApiProxy.onLostControl(goalProcess);
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
            System.err.println("Node: Failed to reset Baritone pathing before mining: " + e.getMessage());
        }
    }

    private void resetBaritonePathing(Object baritone) {
        if (baritone == null) {
            return;
        }
        resetBaritonePathing(baritone, BaritoneApiProxy.getMineProcess(baritone));
    }

    private boolean hasRequiredBlockAlready(String blockId, int required) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || blockId == null) {
            return false;
        }
        Identifier identifier = BlockSelection.extractBlockIdentifier(blockId);
        if (identifier == null || !Registries.BLOCK.containsId(identifier)) {
            return false;
        }
        Block block = Registries.BLOCK.get(identifier);
        Item item = block.asItem();
        if (item == null || item == Items.AIR) {
            return false;
        }
        int count = client.player.getInventory().count(item);
        if (count >= required) {
            sendNodeInfoMessage(client, "Already have " + count + " " + blockId + ", skipping mine.");
            return true;
        }
        return false;
    }
    
    private void executeCraftCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        String itemId = "stick";
        int quantity = 1;

        NodeParameter itemParam = getParameter("Item");
        NodeParameter amountParam = getParameter("Amount");

        if (itemParam != null) {
            itemId = itemParam.getStringValue();
        }
        if (amountParam != null) {
            quantity = amountParam.getIntValue();
        }
        String requestedItemLabel = itemId;

        if (itemId != null && !itemId.isEmpty()) {
            String sanitized = sanitizeResourceId(itemId);
            if (sanitized != null && !sanitized.isEmpty()) {
                String normalized = normalizeResourceId(sanitized, "minecraft");
                if (normalized != null && !normalized.isEmpty()) {
                    itemId = normalized;
                    if (!normalized.equals(requestedItemLabel)) {
                        setParameterValueAndPropagate("Item", normalized);
                    }
                }
            }
        }

        NodeMode craftMode = mode != null ? mode : NodeMode.CRAFT_PLAYER_GUI;

        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();

        Identifier identifier = Identifier.tryParse(itemId);
        if (identifier == null || !Registries.ITEM.containsId(identifier)) {
            String errorLabel = (requestedItemLabel != null && !requestedItemLabel.isEmpty()) ? requestedItemLabel : itemId;
            sendNodeErrorMessage(client, "Cannot craft \"" + errorLabel + "\": unknown item identifier.");
            future.complete(null);
            return;
        }

        Item targetItem = Registries.ITEM.get(identifier);
        if (client == null || client.player == null || client.world == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        if (!isCraftingScreenAvailable(client, craftMode)) {
            String unavailableMessage = craftMode == NodeMode.CRAFT_CRAFTING_TABLE
                    ? "Cannot craft: open a crafting table GUI before running this node."
                    : "Cannot craft: open your inventory or a crafting table GUI before running this node.";
            sendNodeErrorMessage(client, unavailableMessage);
            future.complete(null);
            return;
        }

        String itemDisplayName = targetItem.getName().getString();

        ScreenHandler handler = client.player.currentScreenHandler;
        if (!isCompatibleCraftingHandler(handler, craftMode)) {
            sendNodeErrorMessage(client, "Cannot craft " + itemDisplayName + ": the crafting screen closed.");
            future.complete(null);
            return;
        }

        final NodeMode effectiveCraftMode;
        if (craftMode == NodeMode.CRAFT_PLAYER_GUI && handler instanceof CraftingScreenHandler) {
            effectiveCraftMode = NodeMode.CRAFT_CRAFTING_TABLE;
        } else {
            effectiveCraftMode = craftMode;
        }

        Object serverRegistryManager = client.getServer() != null
            ? client.getServer().getRegistryManager()
            : null;
        net.minecraft.world.World clientWorld = EntityCompatibilityBridge.getWorld(client.player);
        if (clientWorld == null) {
            clientWorld = client.world;
        }
        Object clientRegistryManager = clientWorld != null ? clientWorld.getRegistryManager() : null;

        java.util.concurrent.atomic.AtomicBoolean requiresCraftingTable = new java.util.concurrent.atomic.AtomicBoolean(false);
        RecipeEntry<CraftingRecipe> recipeEntry = findCraftingRecipe(client, targetItem, effectiveCraftMode, requiresCraftingTable);
        Object displayEntry = null;
        if (recipeEntry == null) {
            displayEntry = findCraftingDisplayEntry(client, targetItem, effectiveCraftMode, requiresCraftingTable, clientWorld);
            if (displayEntry == null) {
                String message;
                if (effectiveCraftMode == NodeMode.CRAFT_PLAYER_GUI && requiresCraftingTable.get()) {
                    message = "Cannot craft " + itemDisplayName + ": recipe requires a crafting table.";
                } else {
                    message = "Cannot craft " + itemDisplayName + ": no matching recipe found.";
                }
                sendNodeErrorMessage(client, message);
                future.complete(null);
                return;
            }
        }

        ItemStack outputTemplate;
        if (recipeEntry != null) {
            outputTemplate = getRecipeOutput(recipeEntry.value(), serverRegistryManager);
            if (outputTemplate.isEmpty() && clientRegistryManager != serverRegistryManager) {
                outputTemplate = getRecipeOutput(recipeEntry.value(), clientRegistryManager);
            }
        } else {
            outputTemplate = getDisplayOutput(RecipeCompatibilityBridge.getDisplayFromEntry(displayEntry), clientWorld);
        }
        if (outputTemplate == null || outputTemplate.isEmpty()) {
            sendNodeErrorMessage(client, "Cannot craft " + itemDisplayName + ": the recipe produced no output.");
            future.complete(null);
            return;
        }

        int desiredCount = Math.max(1, quantity);
        int perCraftOutput = Math.max(1, outputTemplate.getCount());
        int craftsRequested = Math.max(1, (int) Math.ceil(desiredCount / (double) perCraftOutput));

        Object ingredientRegistryManager = clientWorld;
        List<GridIngredient> gridIngredients;
        if (recipeEntry != null) {
            gridIngredients = resolveGridIngredients(recipeEntry.value(), effectiveCraftMode, ingredientRegistryManager);
        } else {
            gridIngredients = resolveDisplayGridIngredients(RecipeCompatibilityBridge.getDisplayFromEntry(displayEntry), effectiveCraftMode, ingredientRegistryManager);
        }
        if (gridIngredients.isEmpty()) {
            sendNodeErrorMessage(client, "Cannot craft " + itemDisplayName + ": the recipe has no ingredients.");
            future.complete(null);
            return;
        }

        int[] craftingGridSlots = getCraftingGridSlots(effectiveCraftMode);

        CompletableFuture
            .supplyAsync(() -> {
                try {
                    return craftRecipeUsingScreen(client, effectiveCraftMode, recipeEntry, targetItem, craftsRequested, desiredCount, itemDisplayName, gridIngredients, craftingGridSlots, ingredientRegistryManager);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new java.util.concurrent.CompletionException(e);
                }
            })
            .whenComplete((summary, throwable) -> {
                if (throwable != null) {
                    Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
                    if (!(cause instanceof InterruptedException)) {
                        sendNodeErrorMessageOnClientThread(client, "Cannot craft " + itemDisplayName + ": " + cause.getMessage());
                    }
                    future.complete(null);
                    return;
                }

                if (summary.failureMessage != null) {
                    sendNodeErrorMessageOnClientThread(client, summary.failureMessage);
                }

                future.complete(null);
            });
    }

    private void executeScreenControlCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        NodeMode screenMode = mode != null ? mode : NodeMode.SCREEN_OPEN_CHAT;
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();

        if (client == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        try {
            runOnClientThread(client, () -> {
                switch (screenMode) {
                    case SCREEN_OPEN_CHAT:
                        client.setScreen(ChatScreenCompatibilityBridge.create(""));
                        break;
                    case SCREEN_CLOSE_CURRENT:
                        if (client.player != null) {
                            client.player.closeHandledScreen();
                        }
                        client.setScreen(null);
                        break;
                    default:
                        throw new IllegalStateException("Unknown screen control mode: " + screenMode);
                }
            });
            future.complete(null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.completeExceptionally(e);
        } catch (RuntimeException e) {
            sendNodeErrorMessage(client, e.getMessage());
            future.complete(null);
        }
    }

    private void executeUiUtilsCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        NodeMode uiMode = mode != null ? mode : NodeMode.UI_UTILS_CLOSE_WITHOUT_PACKET;
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();

        if (client == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        if (!com.pathmind.util.UiUtilsProxy.isAvailable()) {
            sendNodeErrorMessage(client, "UI Utils is not installed.");
            future.complete(null);
            return;
        }

        try {
            runOnClientThread(client, () -> {
                boolean modernBackend = com.pathmind.util.UiUtilsProxy.isModernBackend();
                switch (uiMode) {
                    case UI_UTILS_CLOSE_WITHOUT_PACKET:
                        if (modernBackend) {
                            executeUiUtilsCommandOrThrow("close");
                        } else {
                            client.setScreen(null);
                        }
                        break;
                    case UI_UTILS_CLOSE_SIGN_WITHOUT_PACKET:
                        if (modernBackend) {
                            throw new RuntimeException("UI Utils version does not support close sign without packet.");
                        }
                        com.pathmind.util.UiUtilsProxy.setShouldEditSign(false);
                        client.setScreen(null);
                        break;
                    case UI_UTILS_DESYNC:
                        if (modernBackend) {
                            executeUiUtilsCommandOrThrow("desync");
                            break;
                        }
                        if (client.getNetworkHandler() == null || client.player == null) {
                            throw new RuntimeException("Cannot de-sync without a connected player.");
                        }
                        client.getNetworkHandler().sendPacket(
                            new net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket(
                                client.player.currentScreenHandler.syncId
                            )
                        );
                        break;
                    case UI_UTILS_SET_SEND_PACKETS: {
                        if (modernBackend) {
                            throw new RuntimeException("UI Utils version does not support send packet toggles.");
                        }
                        boolean enabled = parseNodeBoolean(this, "Enabled", true);
                        if (!com.pathmind.util.UiUtilsProxy.setSendPackets(enabled)) {
                            throw new RuntimeException("Failed to update UI Utils send packets setting.");
                        }
                        break;
                    }
                    case UI_UTILS_SET_DELAY_PACKETS: {
                        if (modernBackend) {
                            throw new RuntimeException("UI Utils version does not support delayed packets.");
                        }
                        boolean enabled = parseNodeBoolean(this, "Enabled", true);
                        Boolean wasEnabled = com.pathmind.util.UiUtilsProxy.getDelayPackets();
                        if (!com.pathmind.util.UiUtilsProxy.setDelayPackets(enabled)) {
                            throw new RuntimeException("Failed to update UI Utils delay packets setting.");
                        }
                        if (!enabled && Boolean.TRUE.equals(wasEnabled)) {
                            flushUiUtilsDelayedPackets(client, true);
                        }
                        break;
                    }
                    case UI_UTILS_FLUSH_DELAYED_PACKETS:
                        if (modernBackend) {
                            throw new RuntimeException("UI Utils version does not support delayed packets.");
                        }
                        flushUiUtilsDelayedPackets(client, true);
                        break;
                    case UI_UTILS_SAVE_GUI:
                        if (modernBackend) {
                            executeUiUtilsCommandOrThrow("screen save default");
                            break;
                        }
                        if (client.player == null) {
                            throw new RuntimeException("Cannot save GUI without an active player.");
                        }
                        if (!com.pathmind.util.UiUtilsProxy.setStoredScreen(client.currentScreen, client.player.currentScreenHandler)) {
                            throw new RuntimeException("Failed to save GUI.");
                        }
                        break;
                    case UI_UTILS_RESTORE_GUI: {
                        if (modernBackend) {
                            executeUiUtilsCommandOrThrow("screen load default");
                            break;
                        }
                        if (client.player == null) {
                            throw new RuntimeException("Cannot restore GUI without an active player.");
                        }
                        net.minecraft.client.gui.screen.Screen storedScreen = com.pathmind.util.UiUtilsProxy.getStoredScreen();
                        net.minecraft.screen.ScreenHandler storedHandler = com.pathmind.util.UiUtilsProxy.getStoredScreenHandler();
                        if (storedScreen == null || storedHandler == null) {
                            throw new RuntimeException("No saved GUI is available.");
                        }
                        client.setScreen(storedScreen);
                        client.player.currentScreenHandler = storedHandler;
                        break;
                    }
                    case UI_UTILS_DISCONNECT:
                        if (modernBackend) {
                            executeUiUtilsCommandOrThrow("disconnect");
                            break;
                        }
                        if (client.getNetworkHandler() == null) {
                            throw new RuntimeException("Cannot disconnect without a network handler.");
                        }
                        client.getNetworkHandler().getConnection().disconnect(Text.of("Disconnecting (UI-UTILS)"));
                        break;
                    case UI_UTILS_DISCONNECT_AND_SEND:
                        if (modernBackend) {
                            throw new RuntimeException("UI Utils version does not support disconnect-and-send.");
                        }
                        if (client.getNetworkHandler() == null) {
                            throw new RuntimeException("Cannot disconnect without a network handler.");
                        }
                        com.pathmind.util.UiUtilsProxy.setDelayPackets(false);
                        flushUiUtilsDelayedPackets(client, false);
                        client.getNetworkHandler().getConnection().disconnect(Text.of("Disconnecting (UI-UTILS)"));
                        break;
                    case UI_UTILS_COPY_TITLE_JSON:
                        if (client.currentScreen == null) {
                            throw new RuntimeException("No GUI is open to copy.");
                        }
                        copyGuiTitleJson(client);
                        break;
                    case UI_UTILS_FABRICATE_CLICK_SLOT:
                        if (modernBackend) {
                            executeUiUtilsCommandOrThrow(buildModernClickCommand());
                            break;
                        }
                        fabricateClickSlotPacket(client);
                        break;
                    case UI_UTILS_FABRICATE_BUTTON_CLICK:
                        if (modernBackend) {
                            executeUiUtilsCommandOrThrow(buildModernButtonCommand());
                            break;
                        }
                        fabricateButtonClickPacket(client);
                        break;
                    case UI_UTILS_SET_ENABLED: {
                        if (modernBackend) {
                            throw new RuntimeException("UI Utils version does not support enable toggles.");
                        }
                        boolean enabled = parseNodeBoolean(this, "Enabled", true);
                        if (!com.pathmind.util.UiUtilsProxy.setEnabled(enabled)) {
                            throw new RuntimeException("Failed to update UI Utils enabled state.");
                        }
                        break;
                    }
                    case UI_UTILS_SET_BYPASS_RESOURCE_PACK: {
                        if (modernBackend) {
                            throw new RuntimeException("UI Utils version does not support bypass resource pack toggles.");
                        }
                        boolean enabled = parseNodeBoolean(this, "Enabled", true);
                        if (!com.pathmind.util.UiUtilsProxy.setBypassResourcePack(enabled)) {
                            throw new RuntimeException("Failed to update UI Utils resource pack bypass.");
                        }
                        break;
                    }
                    case UI_UTILS_SET_FORCE_DENY_RESOURCE_PACK: {
                        if (modernBackend) {
                            throw new RuntimeException("UI Utils version does not support force-deny toggles.");
                        }
                        boolean enabled = parseNodeBoolean(this, "Enabled", true);
                        if (!com.pathmind.util.UiUtilsProxy.setResourcePackForceDeny(enabled)) {
                            throw new RuntimeException("Failed to update UI Utils force deny setting.");
                        }
                        break;
                    }
                    default:
                        throw new IllegalStateException("Unknown UI Utils mode: " + uiMode);
                }
            });
            future.complete(null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.completeExceptionally(e);
        } catch (RuntimeException e) {
            sendNodeErrorMessage(client, e.getMessage());
            future.complete(null);
        }
    }

    private void executeUiUtilsCommandOrThrow(String command) {
        if (command == null || command.isBlank()) {
            throw new RuntimeException("UI Utils command is empty.");
        }
        if (!com.pathmind.util.UiUtilsProxy.executeCommand(command)) {
            throw new RuntimeException("UI Utils command failed: " + command);
        }
    }

    private String buildModernClickCommand() {
        int syncId = parseNodeInt(this, "SyncId", -1);
        int revision = parseNodeInt(this, "Revision", -1);
        int slot = parseNodeInt(this, "Slot", 0);
        int button = parseNodeInt(this, "Button", 0);
        String actionLabel = getParameterString(this, "Action");
        int timesToSend = Math.max(1, parseNodeInt(this, "TimesToSend", 1));

        SlotActionType action = parseSlotActionType(actionLabel);
        if (action == null) {
            throw new RuntimeException("Invalid slot action type.");
        }

        StringBuilder command = new StringBuilder();
        command.append("click ")
            .append(slot)
            .append(' ')
            .append(button)
            .append(' ')
            .append(action.name());

        if (syncId >= 0) {
            command.append(" --syncId ").append(syncId);
        }
        if (revision >= 0) {
            command.append(" --revision ").append(revision);
        }
        if (timesToSend > 1) {
            command.append(" --times ").append(timesToSend);
        }
        return command.toString();
    }

    private String buildModernButtonCommand() {
        int syncId = parseNodeInt(this, "SyncId", -1);
        int buttonId = parseNodeInt(this, "ButtonId", 0);
        int timesToSend = Math.max(1, parseNodeInt(this, "TimesToSend", 1));

        StringBuilder command = new StringBuilder();
        command.append("button ").append(buttonId);
        if (syncId >= 0) {
            command.append(" --syncId ").append(syncId);
        }
        if (timesToSend > 1) {
            command.append(" --times ").append(timesToSend);
        }
        return command.toString();
    }

    private void flushUiUtilsDelayedPackets(net.minecraft.client.MinecraftClient client, boolean notifyPlayer) {
        if (client == null || client.getNetworkHandler() == null) {
            throw new RuntimeException("Minecraft network handler not available.");
        }
        java.util.List<?> packets = com.pathmind.util.UiUtilsProxy.getDelayedPackets();
        int count = packets != null ? packets.size() : 0;
        if (!com.pathmind.util.UiUtilsProxy.flushDelayedPackets(client)) {
            throw new RuntimeException("Failed to send delayed packets.");
        }
        if (notifyPlayer && count > 0 && client.player != null) {
            client.player.sendMessage(Text.of("Sent " + count + " packets."), false);
        }
    }

    private void copyGuiTitleJson(net.minecraft.client.MinecraftClient client) {
        String json = new com.google.gson.Gson().toJson(
            net.minecraft.text.TextCodecs.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, client.currentScreen.getTitle()).getOrThrow()
        );
        client.keyboard.setClipboard(json);
    }

    private void fabricateClickSlotPacket(net.minecraft.client.MinecraftClient client) {
        if (client == null || client.getNetworkHandler() == null) {
            throw new RuntimeException("Cannot send packets without a network handler.");
        }
        int syncId = parseNodeInt(this, "SyncId", -1);
        int revision = parseNodeInt(this, "Revision", -1);
        int slot = parseNodeInt(this, "Slot", 0);
        int button = parseNodeInt(this, "Button", 0);
        String actionLabel = getParameterString(this, "Action");
        int timesToSend = Math.max(1, parseNodeInt(this, "TimesToSend", 1));
        boolean delay = parseNodeBoolean(this, "Delay", false);

        if (client.player == null || client.player.currentScreenHandler == null) {
            throw new RuntimeException("No active screen handler for fabricated packet.");
        }
        if (syncId < 0) {
            syncId = client.player.currentScreenHandler.syncId;
        }
        if (revision < 0) {
            revision = client.player.currentScreenHandler.getRevision();
        }

        SlotActionType action = parseSlotActionType(actionLabel);
        if (action == null) {
            throw new RuntimeException("Invalid slot action type.");
        }

        net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket packet =
            createClickSlotPacket(syncId, revision, slot, button, action);

        sendFabricatedPacket(client, packet, delay, timesToSend);
    }

    private void fabricateButtonClickPacket(net.minecraft.client.MinecraftClient client) {
        if (client == null || client.getNetworkHandler() == null) {
            throw new RuntimeException("Cannot send packets without a network handler.");
        }
        int syncId = parseNodeInt(this, "SyncId", -1);
        int buttonId = parseNodeInt(this, "ButtonId", 0);
        int timesToSend = Math.max(1, parseNodeInt(this, "TimesToSend", 1));
        boolean delay = parseNodeBoolean(this, "Delay", false);

        if (client.player == null || client.player.currentScreenHandler == null) {
            throw new RuntimeException("No active screen handler for fabricated packet.");
        }
        if (syncId < 0) {
            syncId = client.player.currentScreenHandler.syncId;
        }

        net.minecraft.network.packet.c2s.play.ButtonClickC2SPacket packet =
            new net.minecraft.network.packet.c2s.play.ButtonClickC2SPacket(syncId, buttonId);

        sendFabricatedPacket(client, packet, delay, timesToSend);
    }

    private net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket createClickSlotPacket(
        int syncId,
        int revision,
        int slot,
        int button,
        net.minecraft.screen.slot.SlotActionType action
    ) {
        net.minecraft.item.ItemStack stack = net.minecraft.item.ItemStack.EMPTY;
        it.unimi.dsi.fastutil.ints.Int2ObjectMap<net.minecraft.item.ItemStack> changed =
            new it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap<>();

        java.lang.reflect.Constructor<?>[] constructors =
            net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket.class.getConstructors();

        int[] numbers = new int[] {syncId, revision, slot, button};
        for (java.lang.reflect.Constructor<?> constructor : constructors) {
            Class<?>[] params = constructor.getParameterTypes();
            if (params.length != 7) {
                continue;
            }
            Object[] args = new Object[7];
            int numberIndex = 0;
            boolean ok = true;
            for (int i = 0; i < params.length; i++) {
                Class<?> param = params[i];
                if (param == int.class || param == Integer.class) {
                    if (numberIndex >= numbers.length) {
                        ok = false;
                        break;
                    }
                    args[i] = numbers[numberIndex++];
                } else if (param == short.class || param == Short.class) {
                    if (numberIndex >= numbers.length) {
                        ok = false;
                        break;
                    }
                    args[i] = (short) numbers[numberIndex++];
                } else if (param == byte.class || param == Byte.class) {
                    if (numberIndex >= numbers.length) {
                        ok = false;
                        break;
                    }
                    args[i] = (byte) numbers[numberIndex++];
                } else if (param.isAssignableFrom(net.minecraft.screen.slot.SlotActionType.class)) {
                    args[i] = action;
                } else if (param.isAssignableFrom(net.minecraft.item.ItemStack.class)) {
                    args[i] = stack;
                } else if (param.isAssignableFrom(it.unimi.dsi.fastutil.ints.Int2ObjectMap.class)) {
                    args[i] = changed;
                } else {
                    ok = false;
                    break;
                }
            }
            if (!ok || numberIndex != numbers.length) {
                continue;
            }
            try {
                return (net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket) constructor.newInstance(args);
            } catch (ReflectiveOperationException ignored) {
                // Try next constructor
            }
        }

        throw new RuntimeException("Unsupported ClickSlotC2SPacket constructor.");
    }

    private void sendFabricatedPacket(net.minecraft.client.MinecraftClient client, net.minecraft.network.packet.Packet<?> packet, boolean delay, int timesToSend) {
        if (client == null || client.getNetworkHandler() == null) {
            throw new RuntimeException("Cannot send packets without a network handler.");
        }
        for (int i = 0; i < timesToSend; i++) {
            client.getNetworkHandler().sendPacket(packet);
            if (!delay) {
                com.pathmind.util.UiUtilsProxy.tryWriteAndFlush(client.getNetworkHandler().getConnection(), packet);
            }
        }
    }

    private SlotActionType parseSlotActionType(String value) {
        if (value == null || value.isBlank()) {
            return SlotActionType.PICKUP;
        }
        switch (value.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "PICKUP":
                return SlotActionType.PICKUP;
            case "QUICK_MOVE":
                return SlotActionType.QUICK_MOVE;
            case "SWAP":
                return SlotActionType.SWAP;
            case "CLONE":
                return SlotActionType.CLONE;
            case "THROW":
                return SlotActionType.THROW;
            case "QUICK_CRAFT":
                return SlotActionType.QUICK_CRAFT;
            case "PICKUP_ALL":
                return SlotActionType.PICKUP_ALL;
            default:
                return null;
        }
    }

    private void executePlayerGuiCommand(CompletableFuture<Void> future, NodeMode desiredMode) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        NodeMode playerGuiMode = desiredMode != null ? desiredMode : (mode != null ? mode : NodeMode.PLAYER_GUI_OPEN);
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();

        if (client == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        try {
            runOnClientThread(client, () -> {
                switch (playerGuiMode) {
                    case PLAYER_GUI_OPEN:
                        if (client.player == null || client.player.networkHandler == null) {
                            throw new RuntimeException("Cannot open the player GUI without an active player.");
                        }

                        client.player.networkHandler.sendPacket(new ClientCommandC2SPacket(
                                client.player,
                                ClientCommandC2SPacket.Mode.OPEN_INVENTORY
                        ));

                        if (!(client.currentScreen instanceof InventoryScreen)) {
                            client.setScreen(new InventoryScreen(client.player));
                        }
                        break;
                case PLAYER_GUI_CLOSE:
                    if (client.player == null) {
                        throw new RuntimeException("Cannot close the player GUI without an active player.");
                    }

                    if (client.currentScreen != null) {
                        client.player.closeHandledScreen();
                        client.setScreen(null);
                    }
                    break;
                default:
                        throw new IllegalStateException("Unknown player GUI mode: " + playerGuiMode);
                }
            });
            future.complete(null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.completeExceptionally(e);
        } catch (RuntimeException e) {
            sendNodeErrorMessage(client, e.getMessage());
            future.complete(null);
        }
    }

    private boolean isCraftingScreenAvailable(net.minecraft.client.MinecraftClient client, NodeMode craftMode) {
        if (client == null) {
            return false;
        }

        if (craftMode == NodeMode.CRAFT_CRAFTING_TABLE) {
            return client.currentScreen instanceof CraftingScreen;
        }

        if (craftMode == NodeMode.CRAFT_PLAYER_GUI) {
            return client.currentScreen instanceof InventoryScreen || client.currentScreen instanceof CraftingScreen;
        }

        return false;
    }

    private boolean isCompatibleCraftingHandler(ScreenHandler handler, NodeMode craftMode) {
        if (handler == null) {
            return false;
        }

        if (craftMode == NodeMode.CRAFT_CRAFTING_TABLE) {
            return handler instanceof CraftingScreenHandler;
        }

        if (craftMode == NodeMode.CRAFT_PLAYER_GUI) {
            return handler instanceof PlayerScreenHandler || handler instanceof CraftingScreenHandler;
        }

        return false;
    }

    private RecipeEntry<CraftingRecipe> findCraftingRecipe(net.minecraft.client.MinecraftClient client,
                                                           Item targetItem,
                                                           NodeMode craftMode,
                                                           java.util.concurrent.atomic.AtomicBoolean requiresCraftingTable) {
        List<Object> managers = getRecipeManagers(client);
        if (managers.isEmpty()) {
            return null;
        }

        int totalEntries = 0;
        int craftingEntries = 0;
        int emptyOutputs = 0;
        int matchingOutputs = 0;
        List<String> sampleOutputs = new ArrayList<>();
        boolean debugLogged = false;
        Object serverRegistryManager = client.getServer() != null ? client.getServer().getRegistryManager() : null;
        net.minecraft.world.World clientWorld = EntityCompatibilityBridge.getWorld(client.player);
        Object clientRegistryManager = clientWorld != null ? clientWorld.getRegistryManager() : null;
        List<String> managerTypes = new ArrayList<>();
        for (Object manager : managers) {
            if (manager == null) {
                continue;
            }
            managerTypes.add(manager.getClass().getName());
            for (RecipeEntry<?> entry : getCraftingRecipeEntries(manager)) {
                totalEntries++;
                if (!(entry.value() instanceof CraftingRecipe craftingRecipe)) {
                    continue;
                }
                craftingEntries++;

                ItemStack result = getRecipeOutput(craftingRecipe, serverRegistryManager);
                if (result.isEmpty() && clientRegistryManager != serverRegistryManager) {
                    result = getRecipeOutput(craftingRecipe, clientRegistryManager);
                }
                if (result.isEmpty()) {
                    emptyOutputs++;
                    if (!debugLogged) {
                        logRecipeOutputDebug(craftingRecipe, serverRegistryManager, clientRegistryManager);
                        debugLogged = true;
                    }
                } else if (sampleOutputs.size() < 5) {
                    Identifier itemId = Registries.ITEM.getId(result.getItem());
                    if (itemId != null) {
                        sampleOutputs.add(itemId.toString());
                    }
                }
                if (!result.isOf(targetItem)) {
                    continue;
                }
                matchingOutputs++;

                if (craftMode == NodeMode.CRAFT_PLAYER_GUI && !recipeFitsPlayerGrid(craftingRecipe)) {
                    if (requiresCraftingTable != null) {
                        requiresCraftingTable.set(true);
                    }
                    continue;
                }

                @SuppressWarnings("unchecked")
                RecipeEntry<CraftingRecipe> castEntry = (RecipeEntry<CraftingRecipe>) entry;
                return castEntry;
            }
        }

        logCraftDebug(targetItem, craftMode, managers.size(), totalEntries, craftingEntries, emptyOutputs, matchingOutputs, sampleOutputs, managerTypes);
        return null;
    }

    private Object findCraftingDisplayEntry(net.minecraft.client.MinecraftClient client,
                                            Item targetItem,
                                            NodeMode craftMode,
                                            java.util.concurrent.atomic.AtomicBoolean requiresCraftingTable,
                                            Object registryManager) {
        if (client == null || client.player == null) {
            return null;
        }
        if (!(client.player.getRecipeBook() instanceof ClientRecipeBook clientRecipeBook)) {
            return null;
        }
        List<RecipeResultCollection> collections = clientRecipeBook.getOrderedResults();
        if (collections == null || collections.isEmpty()) {
            return null;
        }
        for (RecipeResultCollection collection : collections) {
            if (collection == null) {
                continue;
            }
            List<?> entries = RecipeCompatibilityBridge.getAllRecipesFromCollection(collection);
            if (entries == null || entries.isEmpty()) {
                continue;
            }
            for (Object entry : entries) {
                if (entry == null) {
                    continue;
                }
                Object display = RecipeCompatibilityBridge.getDisplayFromEntry(entry);
                if (!RecipeCompatibilityBridge.isCraftingDisplay(display)) {
                    continue;
                }
                ItemStack output = getDisplayOutput(display, registryManager);
                if (output == null || output.isEmpty() || !output.isOf(targetItem)) {
                    continue;
                }
                if (craftMode == NodeMode.CRAFT_PLAYER_GUI && !displayFitsPlayerGrid(display, registryManager)) {
                    if (requiresCraftingTable != null) {
                        requiresCraftingTable.set(true);
                    }
                    continue;
                }
                return entry;
            }
        }
        return null;
    }

    private boolean displayFitsPlayerGrid(Object display, Object registryManager) {
        if (RecipeCompatibilityBridge.isShapedCraftingDisplay(display)) {
            return RecipeCompatibilityBridge.getShapedWidth(display) <= 2
                && RecipeCompatibilityBridge.getShapedHeight(display) <= 2;
        }
        if (display != null && display.getClass().getName().contains("ShapelessCraftingRecipeDisplay")) {
            List<?> slots = RecipeCompatibilityBridge.getDisplayIngredientSlots(display);
            int count = countDisplayIngredients(slots, registryManager);
            return count > 0 && count <= 4;
        }
        return false;
    }

    private int countDisplayIngredients(List<?> slots, Object registryManager) {
        if (slots == null || slots.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Object slot : slots) {
            Ingredient ingredient = RecipeCompatibilityBridge.extractDisplayIngredient(slot, registryManager);
            if (RecipeCompatibilityBridge.isIngredientEmpty(ingredient, registryManager)) {
                continue;
            }
            count++;
        }
        return count;
    }

    private void logRecipeOutputDebug(CraftingRecipe recipe, Object serverRegistryManager, Object clientRegistryManager) {
        String recipeClass = recipe != null ? recipe.getClass().getName() : "null";
        String serverMgr = serverRegistryManager != null ? serverRegistryManager.getClass().getName() : "null";
        String clientMgr = clientRegistryManager != null ? clientRegistryManager.getClass().getName() : "null";
        boolean serverLookup = resolveWrapperLookup(serverRegistryManager) != null;
        boolean clientLookup = resolveWrapperLookup(clientRegistryManager) != null;
        List<String> outputMethods = new ArrayList<>();
        List<String> craftMethods = new ArrayList<>();
        List<String> allMethodsSample = new ArrayList<>();
        if (recipe != null) {
            int sampleLimit = 8;
            for (java.lang.reflect.Method method : getAllMethods(recipe.getClass())) {
                if (ItemStack.class.isAssignableFrom(method.getReturnType()) && method.getParameterCount() == 1) {
                    Class<?> paramType = method.getParameterTypes()[0];
                    outputMethods.add(method.getName() + "(" + paramType.getName() + ")");
                }
                if ("craft".equals(method.getName()) && method.getParameterCount() == 2) {
                    Class<?>[] params = method.getParameterTypes();
                    craftMethods.add(method.getName() + "(" + params[0].getName() + "," + params[1].getName() + ")");
                }
                if (allMethodsSample.size() < sampleLimit) {
                    allMethodsSample.add(method.getName() + Arrays.toString(method.getParameterTypes()) + " -> " + method.getReturnType().getName());
                }
            }
        }
        System.out.println(
            "Pathmind craft output debug: recipeClass=" + recipeClass
                + " serverRegistry=" + serverMgr
                + " clientRegistry=" + clientMgr
                + " serverLookup=" + serverLookup
                + " clientLookup=" + clientLookup
                + " outputMethods=" + outputMethods
                + " craftMethods=" + craftMethods
                + " methodsSample=" + allMethodsSample
        );
    }

    private List<java.lang.reflect.Method> getAllMethods(Class<?> type) {
        List<java.lang.reflect.Method> methods = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        collectMethodsRecursive(type, methods, seen);
        return methods;
    }

    private void collectMethodsRecursive(Class<?> type, List<java.lang.reflect.Method> methods, java.util.Set<String> seen) {
        if (type == null || type == Object.class) {
            return;
        }
        try {
            for (java.lang.reflect.Method method : type.getDeclaredMethods()) {
                String key = method.getName() + Arrays.toString(method.getParameterTypes()) + method.getReturnType().getName();
                if (seen.add(key)) {
                    method.setAccessible(true);
                    methods.add(method);
                }
            }
        } catch (SecurityException ignored) {
            // Skip inaccessible class declarations.
        }
        for (Class<?> iface : type.getInterfaces()) {
            collectMethodsRecursive(iface, methods, seen);
        }
        collectMethodsRecursive(type.getSuperclass(), methods, seen);
    }

    private void logCraftDebug(Item targetItem,
                               NodeMode craftMode,
                               int managerCount,
                               int totalEntries,
                               int craftingEntries,
                               int emptyOutputs,
                               int matchingOutputs,
                               List<String> sampleOutputs,
                               List<String> managerTypes) {
        String targetId = targetItem != null ? Registries.ITEM.getId(targetItem).toString() : "unknown";
        System.out.println(
            "Pathmind craft debug: target=" + targetId
                + " mode=" + craftMode
                + " managers=" + managerCount
                + " managerTypes=" + managerTypes
                + " entries=" + totalEntries
                + " craftingEntries=" + craftingEntries
                + " emptyOutputs=" + emptyOutputs
                + " matchingOutputs=" + matchingOutputs
                + " sampleOutputs=" + sampleOutputs
        );
    }

    private List<RecipeEntry<?>> getCraftingRecipeEntries(Object manager) {
        if (manager == null) {
            return List.of();
        }

        List<RecipeEntry<?>> entries = new ArrayList<>();
        RecipeType<?> craftingType = RecipeType.CRAFTING;
        List<String> preferredNames = List.of("listAllOfType", "getAllOfType", "getAllRecipes", "getRecipes");
        for (String name : preferredNames) {
            try {
                java.lang.reflect.Method method = manager.getClass().getMethod(name, RecipeType.class);
                method.setAccessible(true);
                Object result = method.invoke(manager, craftingType);
                if (collectRecipeEntries(result, entries)) {
                    return entries;
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next candidate.
            }
        }

        for (java.lang.reflect.Method method : manager.getClass().getMethods()) {
            if (method.getParameterCount() != 1 || !RecipeType.class.isAssignableFrom(method.getParameterTypes()[0])) {
                continue;
            }
            Class<?> returnType = method.getReturnType();
            if (!Iterable.class.isAssignableFrom(returnType) && !java.util.Map.class.isAssignableFrom(returnType)) {
                continue;
            }
            try {
                method.setAccessible(true);
                Object result = method.invoke(manager, craftingType);
                if (collectRecipeEntries(result, entries)) {
                    return entries;
                }
            } catch (ReflectiveOperationException ignored) {
                // Keep scanning.
            }
        }

        entries.addAll(getRecipeEntries(manager));
        if (entries.isEmpty()) {
            collectRecipeEntriesFromFields(manager, entries, 0, new java.util.IdentityHashMap<>());
        }
        return entries;
    }

    private List<RecipeEntry<?>> getRecipeEntries(Object manager) {
        List<RecipeEntry<?>> entries = new ArrayList<>();
        if (manager == null) {
            return entries;
        }

        List<String> preferredNames = List.of("values", "getRecipes", "getAllRecipes", "getAll");
        for (String name : preferredNames) {
            try {
                java.lang.reflect.Method method = manager.getClass().getMethod(name);
                method.setAccessible(true);
                Object result = method.invoke(manager);
                if (collectRecipeEntries(result, entries)) {
                    return entries;
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next candidate.
            }
        }

        for (java.lang.reflect.Method method : manager.getClass().getMethods()) {
            if (method.getParameterCount() != 0) {
                continue;
            }
            Class<?> returnType = method.getReturnType();
            if (!Iterable.class.isAssignableFrom(returnType) && !java.util.Map.class.isAssignableFrom(returnType)) {
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
            collectRecipeEntriesFromFields(manager, entries, 0, new java.util.IdentityHashMap<>());
        }
        return entries;
    }

    private boolean collectRecipeEntries(Object result, List<RecipeEntry<?>> entries) {
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
            java.lang.reflect.Method valuesMethod = result.getClass().getMethod("values");
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
            java.lang.reflect.Method iteratorMethod = result.getClass().getMethod("iterator");
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

    private void collectRecipeEntriesFromFields(Object manager,
                                                List<RecipeEntry<?>> entries,
                                                int depth,
                                                java.util.IdentityHashMap<Object, Boolean> seen) {
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
                if (!field.canAccess(manager)) {
                    try {
                        field.setAccessible(true);
                    } catch (RuntimeException ignored) {
                        continue;
                    }
                }
                Object value = field.get(manager);
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
                        collectRecipeEntriesFromFields(nested, entries, depth + 1, seen);
                    }
                } else if (value instanceof Iterable<?> iterable) {
                    for (Object nested : iterable) {
                        collectRecipeEntriesFromFields(nested, entries, depth + 1, seen);
                    }
                } else {
                    collectRecipeEntriesFromFields(value, entries, depth + 1, seen);
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
        return name.startsWith("java.")
            || name.startsWith("javax.")
            || name.startsWith("jdk.")
            || name.startsWith("sun.")
            || name.startsWith("com.sun.");
    }

    private List<Object> getRecipeManagers(net.minecraft.client.MinecraftClient client) {
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
                java.lang.reflect.Method method = client.getNetworkHandler().getClass().getMethod("getRecipeManager");
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

    private ItemStack getRecipeOutput(CraftingRecipe recipe, Object registryManager) {
        if (recipe == null) {
            return ItemStack.EMPTY;
        }
        RegistryWrapper.WrapperLookup lookup = resolveWrapperLookup(registryManager);
        Object registryArg = registryManager;
        List<ItemStack> emptyGrid = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
        CraftingRecipeInput input = CraftingRecipeInput.create(3, 3, emptyGrid);
        for (java.lang.reflect.Method method : getAllMethods(recipe.getClass())) {
            if (!ItemStack.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            if (method.getParameterCount() == 1) {
                try {
                    Class<?> paramType = method.getParameterTypes()[0];
                    Object arg = null;
                    if (registryArg != null && paramType.isInstance(registryArg)) {
                        arg = registryArg;
                    } else if (lookup != null && paramType.isInstance(lookup)) {
                        arg = lookup;
                    }
                    if (arg == null) {
                        continue;
                    }
                    Object result = method.invoke(recipe, arg);
                    if (result instanceof ItemStack stack) {
                        return stack;
                    }
                } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
                    // Keep scanning.
                }
            }
        }
        for (java.lang.reflect.Method method : getAllMethods(recipe.getClass())) {
            if (!ItemStack.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            if (method.getParameterCount() != 2) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (!params[0].isInstance(input)) {
                continue;
            }
            Object arg = null;
            if (lookup != null && params[1].isInstance(lookup)) {
                arg = lookup;
            } else if (registryArg != null && params[1].isInstance(registryArg)) {
                arg = registryArg;
            }
            if (arg == null) {
                continue;
            }
            try {
                Object result = method.invoke(recipe, input, arg);
                if (result instanceof ItemStack stack) {
                    return stack;
                }
            } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
                // Keep scanning.
            }
        }
        for (java.lang.reflect.Method method : getAllMethods(recipe.getClass())) {
            if (method.getParameterCount() != 0) {
                continue;
            }
            if (!ItemStack.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            if (method.getDeclaringClass() == Object.class) {
                continue;
            }
            try {
                Object result = method.invoke(recipe);
                if (result instanceof ItemStack stack) {
                    return stack;
                }
            } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
                // Keep scanning.
            }
        }
        return ItemStack.EMPTY;
    }

    private ItemStack getDisplayOutput(Object display, Object registryManager) {
        if (display == null) {
            return ItemStack.EMPTY;
        }
        Object result = RecipeCompatibilityBridge.getResultSlotDisplay(display);
        if (result == null) {
            return ItemStack.EMPTY;
        }
        return RecipeCompatibilityBridge.getSlotDisplayFirst(result, registryManager);
    }

    private List<GridIngredient> resolveDisplayGridIngredients(Object display, NodeMode craftMode, Object registryManager) {
        if (RecipeCompatibilityBridge.isShapedCraftingDisplay(display)) {
            return resolveShapedDisplayGridIngredients(display, craftMode, registryManager);
        }
        if (display != null && display.getClass().getName().contains("ShapelessCraftingRecipeDisplay")) {
            return resolveShapelessDisplayGridIngredients(display, craftMode, registryManager);
        }
        return Collections.emptyList();
    }

    private List<GridIngredient> resolveShapedDisplayGridIngredients(Object display,
                                                                     NodeMode craftMode,
                                                                     Object registryManager) {
        List<GridIngredient> result = new ArrayList<>();
        if (display == null) {
            return result;
        }
        int recipeWidth = Math.max(RecipeCompatibilityBridge.getShapedWidth(display), 1);
        int recipeHeight = Math.max(RecipeCompatibilityBridge.getShapedHeight(display), 1);
        if (craftMode == NodeMode.CRAFT_PLAYER_GUI && (recipeWidth > 2 || recipeHeight > 2)) {
            return result;
        }
        int gridWidth = craftMode == NodeMode.CRAFT_CRAFTING_TABLE ? 3 : 2;
        int width = Math.min(recipeWidth, gridWidth);
        int height = Math.min(recipeHeight, gridWidth);
        List<?> slots = RecipeCompatibilityBridge.getDisplayIngredientSlots(display);
        if (slots == null || slots.isEmpty()) {
            return result;
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = x + (y * recipeWidth);
                if (index < 0 || index >= slots.size()) {
                    continue;
                }
                Ingredient ingredient = RecipeCompatibilityBridge.extractDisplayIngredient(slots.get(index), registryManager);
                if (RecipeCompatibilityBridge.isIngredientEmpty(ingredient, registryManager)) {
                    continue;
                }
                int slotIndex = 1 + x + (y * gridWidth);
                result.add(new GridIngredient(slotIndex, ingredient, false));
            }
        }
        return result;
    }

    private List<GridIngredient> resolveShapelessDisplayGridIngredients(Object display,
                                                                        NodeMode craftMode,
                                                                        Object registryManager) {
        List<GridIngredient> result = new ArrayList<>();
        if (display == null) {
            return result;
        }
        List<?> slots = RecipeCompatibilityBridge.getDisplayIngredientSlots(display);
        if (slots == null || slots.isEmpty()) {
            return result;
        }
        int gridLimit = craftMode == NodeMode.CRAFT_CRAFTING_TABLE ? 9 : 4;
        int placed = 0;
        for (Object slot : slots) {
            if (placed >= gridLimit) {
                break;
            }
            Ingredient ingredient = RecipeCompatibilityBridge.extractDisplayIngredient(slot, registryManager);
            if (RecipeCompatibilityBridge.isIngredientEmpty(ingredient, registryManager)) {
                continue;
            }
            result.add(new GridIngredient(1 + placed, ingredient, false));
            placed++;
        }
        return result;
    }


    private RegistryWrapper.WrapperLookup resolveWrapperLookup(Object registryManager) {
        if (registryManager == null) {
            return null;
        }
        if (registryManager instanceof RegistryWrapper.WrapperLookup wrapper) {
            return wrapper;
        }
        for (String methodName : new String[]{"getWrapperLookup", "getRegistryLookup", "getLookup"}) {
            try {
                java.lang.reflect.Method method = registryManager.getClass().getMethod(methodName);
                method.setAccessible(true);
                Object result = method.invoke(registryManager);
                if (result instanceof RegistryWrapper.WrapperLookup wrapper) {
                    return wrapper;
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next candidate.
            }
        }
        for (java.lang.reflect.Method method : registryManager.getClass().getMethods()) {
            if (method.getParameterCount() != 0) {
                continue;
            }
            if (!RegistryWrapper.WrapperLookup.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            try {
                method.setAccessible(true);
                Object result = method.invoke(registryManager);
                if (result instanceof RegistryWrapper.WrapperLookup wrapper) {
                    return wrapper;
                }
            } catch (ReflectiveOperationException ignored) {
                // Keep scanning.
            }
        }
        return null;
    }

    private boolean recipeFitsPlayerGrid(CraftingRecipe recipe) {
        if (recipe == null) {
            return false;
        }

        if (recipe instanceof ShapedRecipe shapedRecipe) {
            return shapedRecipe.getWidth() <= 2 && shapedRecipe.getHeight() <= 2;
        }

        Object placement = RecipeCompatibilityBridge.getIngredientPlacement(recipe);
        if (placement == null) {
            List<?> ingredients = RecipeCompatibilityBridge.getRecipeIngredients(recipe);
            if (ingredients == null || ingredients.isEmpty()) {
                ingredients = readRecipeIngredients(recipe);
            }
            if (ingredients == null || ingredients.isEmpty()) {
                return false;
            }
            int nonEmpty = 0;
            for (Object entry : ingredients) {
                Ingredient ingredient = unwrapRecipeIngredient(entry);
                if (RecipeCompatibilityBridge.isIngredientEmpty(ingredient)) {
                    continue;
                }
                nonEmpty++;
            }
            return nonEmpty <= 4;
        }

        if (RecipeCompatibilityBridge.hasNoPlacement(placement)) {
            return RecipeCompatibilityBridge.getPlacementIngredients(placement).size() <= 4;
        }

        IntList slots = RecipeCompatibilityBridge.toPlacementSlots(placement);
        if (slots == null || slots.isEmpty()) {
            return RecipeCompatibilityBridge.getPlacementIngredients(placement).size() <= 4;
        }

        int minX = 3;
        int minY = 3;
        int maxX = -1;
        int maxY = -1;
        for (int slot : slots) {
            int x = slot % 3;
            int y = slot / 3;
            if (x < minX) {
                minX = x;
            }
            if (y < minY) {
                minY = y;
            }
            if (x > maxX) {
                maxX = x;
            }
            if (y > maxY) {
                maxY = y;
            }
        }

        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        return width <= 2 && height <= 2;
    }

    private CraftingSummary craftRecipeUsingScreen(net.minecraft.client.MinecraftClient client,
                                                   NodeMode craftMode,
                                                   RecipeEntry<CraftingRecipe> recipeEntry,
                                                   Item targetItem,
                                                   int craftsRequested,
                                                   int desiredCount,
                                                   String itemDisplayName,
                                                   List<GridIngredient> gridIngredients,
                                                   int[] gridSlots,
                                                   Object registryManager) throws InterruptedException {
        int totalProduced = 0;
        String failureMessage = null;

        for (int attempt = 0; attempt < craftsRequested; attempt++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }

            if (!isCraftingScreenAvailable(client, craftMode)) {
                failureMessage = craftMode == NodeMode.CRAFT_CRAFTING_TABLE
                        ? "Cannot craft " + itemDisplayName + ": open a crafting table GUI before running this node."
                        : "Cannot craft " + itemDisplayName + ": open your inventory or a crafting table GUI before running this node.";
                break;
            }

            ScreenHandler handler = client.player != null ? client.player.currentScreenHandler : null;
            if (!isCompatibleCraftingHandler(handler, craftMode)) {
                failureMessage = "Cannot craft " + itemDisplayName + ": the crafting screen closed.";
                break;
            }

            CraftingAttemptResult attemptResult = performCraftingAttempt(client, targetItem, itemDisplayName, gridIngredients, gridSlots, craftMode, registryManager);
            if (attemptResult.errorMessage != null) {
                failureMessage = attemptResult.errorMessage;
                if (attemptResult.produced > 0) {
                    totalProduced += attemptResult.produced;
                }
                break;
            }

            if (attemptResult.produced <= 0) {
                failureMessage = "Cannot craft " + itemDisplayName + ": missing required ingredients.";
                break;
            }

            totalProduced += attemptResult.produced;

            if (totalProduced >= desiredCount) {
                break;
            }
        }

        if (totalProduced <= 0 && failureMessage == null) {
            failureMessage = "Cannot craft " + itemDisplayName + ": missing required ingredients.";
        }

        return new CraftingSummary(totalProduced, failureMessage);
    }

    private CraftingAttemptResult performCraftingAttempt(net.minecraft.client.MinecraftClient client,
                                                         Item targetItem,
                                                         String itemDisplayName,
                                                         List<GridIngredient> gridIngredients,
                                                         int[] gridSlots,
                                                         NodeMode craftMode,
                                                         Object registryManager) throws InterruptedException {
        java.util.concurrent.atomic.AtomicReference<String> errorRef = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicInteger producedRef = new java.util.concurrent.atomic.AtomicInteger();

        runOnClientThread(client, () -> {
            ClientPlayerInteractionManager interactionManager = client.interactionManager;
            if (interactionManager == null) {
                errorRef.set("Cannot craft " + itemDisplayName + ": interaction manager unavailable.");
                return;
            }

            ScreenHandler handler = client.player != null ? client.player.currentScreenHandler : null;
            if (handler == null) {
                errorRef.set("Cannot craft " + itemDisplayName + ": the crafting screen closed.");
                return;
            }

            clearCraftingGrid(client, interactionManager, handler, gridSlots, craftMode);
        });

        if (errorRef.get() != null) {
            return new CraftingAttemptResult(0, errorRef.get());
        }

        for (GridIngredient ingredient : gridIngredients) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }

            if (ingredient == null) {
                continue;
            }
            if (!ingredient.allowEmpty() && RecipeCompatibilityBridge.isIngredientEmpty(ingredient.ingredient(), registryManager)) {
                continue;
            }

            java.util.concurrent.atomic.AtomicBoolean placed = new java.util.concurrent.atomic.AtomicBoolean(false);

            runOnClientThread(client, () -> {
                ClientPlayerInteractionManager interactionManager = client.interactionManager;
                if (interactionManager == null) {
                    errorRef.set("Cannot craft " + itemDisplayName + ": interaction manager unavailable.");
                    return;
                }

                ScreenHandler handler = client.player != null ? client.player.currentScreenHandler : null;
                if (handler == null) {
                    errorRef.set("Cannot craft " + itemDisplayName + ": the crafting screen closed.");
                    return;
                }

                int sourceSlot = findIngredientSourceSlot(handler, ingredient.ingredient(), registryManager, ingredient.allowEmpty());
                if (sourceSlot == -1) {
                    errorRef.set("Cannot craft " + itemDisplayName + ": missing required ingredients.");
                    return;
                }

                int targetSlot = mapLogicalSlotToHandlerSlot(handler, craftMode, ingredient.slotIndex());
                if (targetSlot < 0) {
                    errorRef.set("Cannot craft " + itemDisplayName + ": crafting grid slot unavailable.");
                    return;
                }

                interactionManager.clickSlot(handler.syncId, sourceSlot, 0, SlotActionType.PICKUP, client.player);
                interactionManager.clickSlot(handler.syncId, targetSlot, 1, SlotActionType.PICKUP, client.player);
                interactionManager.clickSlot(handler.syncId, sourceSlot, 0, SlotActionType.PICKUP, client.player);
                placed.set(true);
            });

            if (errorRef.get() != null) {
                return new CraftingAttemptResult(producedRef.get(), errorRef.get());
            }

            if (!placed.get()) {
                return new CraftingAttemptResult(producedRef.get(), "Cannot craft " + itemDisplayName + ": failed to place ingredients.");
            }

            Thread.sleep(CRAFTING_ACTION_DELAY_MS);
        }

        for (int poll = 0; poll < CRAFTING_OUTPUT_POLL_LIMIT && producedRef.get() <= 0 && errorRef.get() == null; poll++) {
            runOnClientThread(client, () -> {
                ClientPlayerInteractionManager interactionManager = client.interactionManager;
                if (interactionManager == null) {
                    errorRef.set("Cannot craft " + itemDisplayName + ": interaction manager unavailable.");
                    return;
                }

                ScreenHandler handler = client.player != null ? client.player.currentScreenHandler : null;
                if (handler == null) {
                    errorRef.set("Cannot craft " + itemDisplayName + ": the crafting screen closed.");
                    return;
                }

                Slot outputSlot;
                try {
                    outputSlot = handler.getSlot(0);
                } catch (IndexOutOfBoundsException e) {
                    errorRef.set("Cannot craft " + itemDisplayName + ": crafting output unavailable.");
                    return;
                }

                ItemStack resultStack = outputSlot.getStack();
                if (resultStack.isEmpty() || !resultStack.isOf(targetItem)) {
                    return;
                }

                producedRef.set(resultStack.getCount());
                interactionManager.clickSlot(handler.syncId, 0, 0, SlotActionType.QUICK_MOVE, client.player);
            });

            if (producedRef.get() > 0 || errorRef.get() != null) {
                break;
            }

            Thread.sleep(CRAFTING_ACTION_DELAY_MS);
        }

        if (producedRef.get() > 0) {
            runOnClientThread(client, () -> {
                ClientPlayerInteractionManager interactionManager = client.interactionManager;
                if (interactionManager == null) {
                    errorRef.set("Cannot craft " + itemDisplayName + ": interaction manager unavailable.");
                    return;
                }

                ScreenHandler handler = client.player != null ? client.player.currentScreenHandler : null;
                if (handler == null) {
                    errorRef.set("Cannot craft " + itemDisplayName + ": the crafting screen closed.");
                    return;
                }

                clearCraftingGrid(client, interactionManager, handler, gridSlots, craftMode);
            });

            if (errorRef.get() != null) {
                return new CraftingAttemptResult(producedRef.get(), errorRef.get());
            }

            Thread.sleep(CRAFTING_ACTION_DELAY_MS);
            return new CraftingAttemptResult(producedRef.get(), null);
        }

        if (errorRef.get() != null) {
            return new CraftingAttemptResult(producedRef.get(), errorRef.get());
        }

        return new CraftingAttemptResult(0, "Cannot craft " + itemDisplayName + ": missing required ingredients.");
    }

    private void clearCraftingGrid(net.minecraft.client.MinecraftClient client,
                                   ClientPlayerInteractionManager interactionManager,
                                   ScreenHandler handler,
                                   int[] gridSlots,
                                   NodeMode craftMode) {
        if (client.player == null || interactionManager == null || handler == null || gridSlots == null) {
            return;
        }

        int[] actualSlots = mapGridSlotsForHandler(handler, craftMode, gridSlots);

        for (int slotIndex : actualSlots) {
            try {
                Slot slot = handler.getSlot(slotIndex);
                if (slot != null && slot.hasStack()) {
                    interactionManager.clickSlot(handler.syncId, slotIndex, 0, SlotActionType.QUICK_MOVE, client.player);
                }
            } catch (IndexOutOfBoundsException ignored) {
                // Ignore missing grid slots for the current handler.
            }
        }
    }

    private int findIngredientSourceSlot(ScreenHandler handler,
                                         Ingredient ingredient,
                                         Object registryManager,
                                         boolean allowEmpty) {
        if (handler == null || ingredient == null) {
            return -1;
        }
        if (!allowEmpty && RecipeCompatibilityBridge.isIngredientEmpty(ingredient, registryManager)) {
            return -1;
        }

        List<ItemStack> candidates = RecipeCompatibilityBridge.getIngredientStacks(ingredient, registryManager);
        List<Slot> slots = handler.slots;
        for (int slotIdx = 0; slotIdx < slots.size(); slotIdx++) {
            Slot slot = slots.get(slotIdx);
            if (!(slot.inventory instanceof PlayerInventory)) {
                continue;
            }

            int inventoryIndex = slot.getIndex();
            if (inventoryIndex < 0 || inventoryIndex >= PlayerInventory.MAIN_SIZE) {
                continue;
            }

            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) {
                continue;
            }

            if (ingredient.test(stack) || matchesCandidateStack(candidates, stack)) {
                return slotIdx;
            }
        }

        return -1;
    }

    private boolean matchesCandidateStack(List<ItemStack> candidates, ItemStack stack) {
        if (candidates == null || candidates.isEmpty()) {
            return false;
        }
        for (ItemStack candidate : candidates) {
            if (candidate != null && candidate.getItem() == stack.getItem()) {
                return true;
            }
        }
        return false;
    }

    private int[] mapGridSlotsForHandler(ScreenHandler handler, NodeMode craftMode, int[] logicalSlots) {
        if (handler == null || logicalSlots == null) {
            return new int[0];
        }
        int[] mapped = new int[logicalSlots.length];
        int count = 0;
        for (int logical : logicalSlots) {
            int actual = mapLogicalSlotToHandlerSlot(handler, craftMode, logical);
            if (actual >= 0) {
                mapped[count++] = actual;
            }
        }
        if (count == mapped.length) {
            return mapped;
        }
        int[] compact = new int[count];
        for (int i = 0; i < count; i++) {
            compact[i] = mapped[i];
        }
        return compact;
    }

    private int mapLogicalSlotToHandlerSlot(ScreenHandler handler, NodeMode craftMode, int logicalSlot) {
        if (handler == null) {
            return -1;
        }

        if (craftMode == NodeMode.CRAFT_PLAYER_GUI && handler instanceof CraftingScreenHandler) {
            return switch (logicalSlot) {
                case 1 -> 1;
                case 2 -> 2;
                case 3 -> 4;
                case 4 -> 5;
                default -> -1;
            };
        }

        return logicalSlot;
    }

    private int mapPlayerInventorySlot(ScreenHandler handler, int inventorySlot) {
        if (handler == null) {
            return -1;
        }
        List<Slot> slots = handler.slots;
        for (int slotIdx = 0; slotIdx < slots.size(); slotIdx++) {
            Slot slot = slots.get(slotIdx);
            if (slot.inventory instanceof PlayerInventory && slot.getIndex() == inventorySlot) {
                return slotIdx;
            }
        }
        return -1;
    }

    private List<GridIngredient> resolveGridIngredients(CraftingRecipe recipe, NodeMode craftMode, Object registryManager) {
        List<GridIngredient> result = new ArrayList<>();
        if (recipe == null) {
            return result;
        }

        if (recipe instanceof ShapedRecipe shapedRecipe) {
            if (craftMode == NodeMode.CRAFT_PLAYER_GUI) {
                return resolvePlayerGridIngredients(shapedRecipe, registryManager);
            }
            return resolveCraftingTableGridIngredients(shapedRecipe, registryManager);
        }

        Object placement = RecipeCompatibilityBridge.getIngredientPlacement(recipe);
        if (placement == null) {
            return resolveFallbackGridIngredients(recipe, craftMode, registryManager);
        }

        List<Ingredient> ingredients = RecipeCompatibilityBridge.getPlacementIngredients(placement);
        if (ingredients == null || ingredients.isEmpty()) {
            return resolveFallbackGridIngredients(recipe, craftMode, registryManager);
        }

        IntList slots = RecipeCompatibilityBridge.toPlacementSlots(placement);
        int gridLimit = craftMode == NodeMode.CRAFT_CRAFTING_TABLE ? 9 : 4;

        if (RecipeCompatibilityBridge.hasNoPlacement(placement) || slots == null || slots.isEmpty()) {
            int limit = Math.min(ingredients.size(), gridLimit);
            for (int i = 0; i < limit; i++) {
                Ingredient ingredient = ingredients.get(i);
                if (RecipeCompatibilityBridge.isIngredientEmpty(ingredient, registryManager)) {
                    continue;
                }
                result.add(new GridIngredient(1 + i, ingredient, false));
            }
            if (result.isEmpty()) {
                logEmptyPlacementIngredients(ingredients, registryManager);
            }
            return result;
        }

        int limit = Math.min(ingredients.size(), slots.size());
        for (int i = 0; i < limit; i++) {
            Ingredient ingredient = ingredients.get(i);
            if (RecipeCompatibilityBridge.isIngredientEmpty(ingredient, registryManager)) {
                continue;
            }

            int logicalSlot = slots.getInt(i);
            int resolvedSlot;
            if (craftMode == NodeMode.CRAFT_PLAYER_GUI) {
                int localX = logicalSlot % 3;
                int localY = logicalSlot / 3;

                if (localX >= 2 || localY >= 2) {
                    continue;
                }

                resolvedSlot = 1 + localX + (localY * 2);
            } else {
                resolvedSlot = 1 + logicalSlot;
            }

            if (resolvedSlot > gridLimit) {
                continue;
            }

            result.add(new GridIngredient(resolvedSlot, ingredient, false));
    }

        if (result.isEmpty()) {
            logEmptyPlacementIngredients(ingredients, registryManager);
        }
        return result;
    }

    private List<GridIngredient> resolveFallbackGridIngredients(CraftingRecipe recipe, NodeMode craftMode, Object registryManager) {
        List<GridIngredient> result = new ArrayList<>();
        if (recipe == null) {
            return result;
        }
        List<?> ingredients = RecipeCompatibilityBridge.getRecipeIngredients(recipe);
        if (ingredients == null || ingredients.isEmpty()) {
            ingredients = readRecipeIngredients(recipe);
        }
        if (ingredients == null || ingredients.isEmpty()) {
            logMissingRecipeIngredients(recipe);
            return result;
        }
        int gridLimit = craftMode == NodeMode.CRAFT_CRAFTING_TABLE ? 9 : 4;

        List<Ingredient> displayIngredients = RecipeCompatibilityBridge.extractDisplayIngredients(ingredients, registryManager);
        if (!displayIngredients.isEmpty()) {
            int limit = Math.min(displayIngredients.size(), gridLimit);
            for (int i = 0; i < limit; i++) {
                Ingredient ingredient = displayIngredients.get(i);
                if (RecipeCompatibilityBridge.isIngredientEmpty(ingredient, registryManager)) {
                    continue;
                }
                result.add(new GridIngredient(1 + i, ingredient, false));
            }
            if (result.isEmpty()) {
                logEmptyPlacementIngredients(displayIngredients, registryManager);
            }
            return result;
        }

        int limit = Math.min(ingredients.size(), gridLimit);
        boolean loggedUnknown = false;
        boolean loggedSummary = false;
        int loggedEmpty = 0;
        for (int i = 0; i < limit; i++) {
            Object entry = ingredients.get(i);
            Ingredient ingredient = unwrapRecipeIngredient(entry);
            if (RecipeCompatibilityBridge.isIngredientEmpty(ingredient, registryManager)) {
                if (!loggedSummary) {
                    System.out.println(
                        "Pathmind craft debug: ingredient list type="
                            + ingredients.getClass().getName()
                            + " size=" + ingredients.size()
                    );
                    loggedSummary = true;
                }
                if (loggedEmpty < 3) {
                    int matches = RecipeCompatibilityBridge.getIngredientStacks(ingredient, registryManager).size();
                    String entryType = entry != null ? entry.getClass().getName() : "null";
                    String ingredientType = ingredient != null ? ingredient.getClass().getName() : "null";
                    System.out.println(
                        "Pathmind craft debug: empty ingredient entryType="
                            + entryType + " ingredientType=" + ingredientType + " matches=" + matches
                    );
                    loggedEmpty++;
                }
                if (!loggedUnknown && ingredient == null && entry != null) {
                    System.out.println("Pathmind craft debug: unresolved ingredient entry type=" + entry.getClass().getName());
                    logUnresolvedIngredientEntry(entry);
                    loggedUnknown = true;
                }
                continue;
            }
            result.add(new GridIngredient(1 + i, ingredient, false));
        }
        return result;
    }

    private List<?> readRecipeIngredients(CraftingRecipe recipe) {
        if (recipe == null) {
            return Collections.emptyList();
        }
        List<?> bestList = null;
        int bestScore = -1;
        int bestSize = 0;
        for (java.lang.reflect.Method method : getAllMethods(recipe.getClass())) {
            if (method.getParameterCount() != 0) {
                continue;
            }
            try {
                Class<?> returnType = method.getReturnType();
                Object result = method.invoke(recipe);
                if (result instanceof Ingredient[] ingredientArray) {
                    return java.util.Arrays.asList(ingredientArray);
                }
                if (result instanceof Object[] objectArray) {
                    return java.util.Arrays.asList(objectArray);
                }
                if (!java.util.List.class.isAssignableFrom(returnType)) {
                    continue;
                }
                if (result instanceof List<?> list) {
                    int score = 0;
                    for (Object entry : list) {
                        Ingredient ingredient = unwrapRecipeIngredient(entry);
                        if (!RecipeCompatibilityBridge.isIngredientEmpty(ingredient)) {
                            score++;
                        }
                    }
                    int size = list.size();
                    if (score > bestScore || (score == bestScore && size > bestSize)) {
                        bestScore = score;
                        bestSize = size;
                        bestList = list;
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Ignore and continue scanning other methods.
            }
        }
        if (bestList != null) {
            return bestList;
        }
        return Collections.emptyList();
    }

    private void logMissingRecipeIngredients(CraftingRecipe recipe) {
        if (recipe == null) {
            return;
        }
        try {
            StringBuilder builder = new StringBuilder();
            builder.append("Pathmind craft debug: missing ingredients for recipeClass=")
                .append(recipe.getClass().getName());
            int logged = 0;
            for (java.lang.reflect.Method method : getAllMethods(recipe.getClass())) {
                if (method.getParameterCount() != 0) {
                    continue;
                }
                Class<?> returnType = method.getReturnType();
                if (!java.util.List.class.isAssignableFrom(returnType)
                    && !returnType.isArray()) {
                    continue;
                }
                Object result = null;
                try {
                    result = method.invoke(recipe);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    continue;
                }
                if (result == null) {
                    continue;
                }
                if (logged < 6) {
                    builder.append(" method=")
                        .append(method.getName())
                        .append(" type=")
                        .append(result.getClass().getName())
                        .append(" size=")
                        .append(result instanceof java.util.List<?> list ? list.size() : java.lang.reflect.Array.getLength(result));
                    logged++;
                }
            }
            System.out.println(builder.toString());
        } catch (RuntimeException ignored) {
            // Avoid breaking crafting flow on debug failure.
        }
    }

    private void logEmptyPlacementIngredients(List<Ingredient> ingredients, Object registryManager) {
        if (ingredients == null || ingredients.isEmpty()) {
            return;
        }
        System.out.println(
            "Pathmind craft debug: placement ingredients all empty size=" + ingredients.size()
                + " listType=" + ingredients.getClass().getName()
        );
        int logged = 0;
        for (Ingredient ingredient : ingredients) {
            if (logged >= 3) {
                break;
            }
            int matches = RecipeCompatibilityBridge.getIngredientStacks(ingredient, registryManager).size();
            String ingredientType = ingredient != null ? ingredient.getClass().getName() : "null";
            System.out.println("Pathmind craft debug: placement ingredient type=" + ingredientType + " matches=" + matches);
            logged++;
        }
    }

    private void logIngredientListIfEmpty(String source, List<?> ingredients, Object registryManager) {
        if (ingredients == null || ingredients.isEmpty()) {
            System.out.println("Pathmind craft debug: " + source + " ingredient list empty");
            return;
        }
        System.out.println(
            "Pathmind craft debug: " + source + " ingredient list type=" + ingredients.getClass().getName()
                + " size=" + ingredients.size()
        );
        int logged = 0;
        for (Object entry : ingredients) {
            if (logged >= 3) {
                break;
            }
            Ingredient ingredient = unwrapRecipeIngredient(entry);
            int matches = RecipeCompatibilityBridge.getIngredientStacks(ingredient, registryManager).size();
            String entryType = entry != null ? entry.getClass().getName() : "null";
            String ingredientType = ingredient != null ? ingredient.getClass().getName() : "null";
            System.out.println(
                "Pathmind craft debug: " + source + " entryType=" + entryType
                    + " ingredientType=" + ingredientType + " matches=" + matches
            );
            if (ingredient == null && entry != null) {
                logUnresolvedIngredientEntry(entry);
            }
            logged++;
        }
    }

    private void logUnresolvedIngredientEntry(Object entry) {
        if (entry == null) {
            return;
        }
        try {
            StringBuilder builder = new StringBuilder();
            Class<?> entryClass = entry.getClass();
            builder.append("Pathmind craft debug: unresolved entry details class=")
                .append(entryClass.getName());
            if (entryClass.isRecord()) {
                builder.append(" recordComponents=");
                java.lang.reflect.RecordComponent[] components = entryClass.getRecordComponents();
                for (int i = 0; i < Math.min(components.length, 4); i++) {
                    java.lang.reflect.RecordComponent component = components[i];
                    builder.append(component.getName()).append(":").append(component.getType().getName()).append(" ");
                }
            }
            int loggedMethods = 0;
            for (java.lang.reflect.Method method : entryClass.getMethods()) {
                if (method.getParameterCount() != 0) {
                    continue;
                }
                if (loggedMethods >= 6) {
                    break;
                }
                builder.append(" method=").append(method.getName())
                    .append("->").append(method.getReturnType().getName());
                loggedMethods++;
            }
            int loggedFields = 0;
            for (java.lang.reflect.Field field : entryClass.getDeclaredFields()) {
                if (loggedFields >= 4) {
                    break;
                }
                builder.append(" field=").append(field.getName())
                    .append(":").append(field.getType().getName());
                loggedFields++;
            }
            System.out.println(builder.toString());
        } catch (RuntimeException ignored) {
            // Avoid breaking crafting flow on debug failure.
        }
    }

    private List<GridIngredient> resolvePlayerGridIngredients(ShapedRecipe recipe, Object registryManager) {
        List<GridIngredient> result = new ArrayList<>();
        List<?> ingredients = recipe.getIngredients();
        if (ingredients == null || ingredients.isEmpty()) {
            logIngredientListIfEmpty("playerGrid", ingredients, registryManager);
            return result;
        }

        int width = Math.min(recipe.getWidth(), 2);
        int height = Math.min(recipe.getHeight(), 2);
        int recipeWidth = Math.max(recipe.getWidth(), 1);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = x + (y * recipeWidth);
                if (index < 0 || index >= ingredients.size()) {
                    continue;
                }

                Ingredient ingredient = unwrapRecipeIngredient(ingredients.get(index));
                int slotIndex = 1 + x + (y * 2);
                if (ingredient == null || RecipeCompatibilityBridge.isIngredientEmpty(ingredient, registryManager)) {
                    continue;
                }
                result.add(new GridIngredient(slotIndex, ingredient, false));
            }
        }

        if (result.isEmpty()) {
            logIngredientListIfEmpty("playerGrid", ingredients, registryManager);
        }
        return result;
    }

    private List<GridIngredient> resolveCraftingTableGridIngredients(ShapedRecipe recipe, Object registryManager) {
        List<GridIngredient> result = new ArrayList<>();
        List<?> ingredients = recipe.getIngredients();
        if (ingredients == null || ingredients.isEmpty()) {
            logIngredientListIfEmpty("craftingTableGrid", ingredients, registryManager);
            return result;
        }

        int width = Math.min(recipe.getWidth(), 3);
        int height = Math.min(recipe.getHeight(), 3);
        int recipeWidth = Math.max(recipe.getWidth(), 1);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = x + (y * recipeWidth);
                if (index < 0 || index >= ingredients.size()) {
                    continue;
                }

                Ingredient ingredient = unwrapRecipeIngredient(ingredients.get(index));
                int slotIndex = 1 + x + (y * 3);
                if (ingredient == null || RecipeCompatibilityBridge.isIngredientEmpty(ingredient, registryManager)) {
                    continue;
                }
                result.add(new GridIngredient(slotIndex, ingredient, false));
            }
        }

        if (result.isEmpty()) {
            logIngredientListIfEmpty("craftingTableGrid", ingredients, registryManager);
        }
        return result;
    }

    private Ingredient unwrapRecipeIngredient(Object entry) {
        if (entry instanceof Ingredient ingredientValue) {
            return ingredientValue;
        }
        if (entry instanceof RegistryEntry<?> registryEntry) {
            Object value = registryEntry.value();
            if (value instanceof Ingredient registryIngredient) {
                return registryIngredient;
            }
        }
        Ingredient candidate = RecipeCompatibilityBridge.tryCreateIngredientFromEntry(entry);
        if (candidate != null) {
            return candidate;
        }
        if (entry instanceof Optional<?> optional) {
            Object value = optional.orElse(null);
            if (value instanceof Ingredient optionalIngredient) {
                return optionalIngredient;
            }
        }
        if (entry != null) {
            Ingredient resolved = resolveIngredientFromEntry(entry, "ingredient");
            if (resolved != null) {
                return resolved;
            }
            resolved = resolveIngredientFromEntry(entry, "value");
            if (resolved != null) {
                return resolved;
            }
            resolved = resolveIngredientFromEntry(entry, "getIngredient");
            if (resolved != null) {
                return resolved;
            }
        }
        return null;
    }

    private Ingredient resolveIngredientFromEntry(Object entry, String methodName) {
        try {
            java.lang.reflect.Method method = entry.getClass().getMethod(methodName);
            if (Ingredient.class.isAssignableFrom(method.getReturnType())) {
                method.setAccessible(true);
                Object value = method.invoke(entry);
                return value instanceof Ingredient ingredient ? ingredient : null;
            }
        } catch (NoSuchMethodException ignored) {
            // Try declared methods/fields next.
        } catch (IllegalAccessException | java.lang.reflect.InvocationTargetException ignored) {
            return null;
        }
        try {
            java.lang.reflect.Method method = entry.getClass().getDeclaredMethod(methodName);
            if (!Ingredient.class.isAssignableFrom(method.getReturnType())) {
                return null;
            }
            method.setAccessible(true);
            Object value = method.invoke(entry);
            return value instanceof Ingredient ingredient ? ingredient : null;
        } catch (NoSuchMethodException ignored) {
            // Try fields next.
        } catch (IllegalAccessException | java.lang.reflect.InvocationTargetException ignored) {
            return null;
        }
        try {
            java.lang.reflect.Field field = entry.getClass().getDeclaredField(methodName);
            if (!Ingredient.class.isAssignableFrom(field.getType())) {
                return null;
            }
            field.setAccessible(true);
            Object value = field.get(entry);
            return value instanceof Ingredient ingredient ? ingredient : null;
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
            return null;
        }
    }

    private int[] getCraftingGridSlots(NodeMode craftMode) {
        if (craftMode == NodeMode.CRAFT_CRAFTING_TABLE) {
            return new int[] {1, 2, 3, 4, 5, 6, 7, 8, 9};
        }
        return new int[] {1, 2, 3, 4};
    }

    private static class CraftingSummary {
        final int produced;
        final String failureMessage;

        CraftingSummary(int produced, String failureMessage) {
            this.produced = produced;
            this.failureMessage = failureMessage;
        }
    }

    private static class GridIngredient {
        private final int slotIndex;
        private final Ingredient ingredient;
        private final boolean allowEmpty;

        GridIngredient(int slotIndex, Ingredient ingredient, boolean allowEmpty) {
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

    private static class CraftingAttemptResult {
        final int produced;
        final String errorMessage;

        CraftingAttemptResult(int produced, String errorMessage) {
            this.produced = produced;
            this.errorMessage = errorMessage;
        }
    }

    private void executePlaceCommand(CompletableFuture<Void> future) {
        Node blockParameterNode = getAttachedParameter(0);
        Node coordinateParameterNode = getAttachedParameter(1);
        boolean coordinateHandledByBlockParam = coordinateParameterNode == null && parameterProvidesCoordinates(blockParameterNode);

        if (blockParameterNode != null) {
            EnumSet<ParameterUsage> blockUsages = coordinateHandledByBlockParam
                ? EnumSet.of(ParameterUsage.POSITION)
                : EnumSet.noneOf(ParameterUsage.class);
            if (preprocessParameterSlot(0, blockUsages, future, true) == ParameterHandlingResult.COMPLETE) {
                return;
            }
        } else {
            runtimeParameterData = null;
        }

        if (coordinateParameterNode != null) {
            EnumSet<ParameterUsage> coordinateUsages = parameterProvidesCoordinates(coordinateParameterNode)
                ? EnumSet.of(ParameterUsage.POSITION)
                : EnumSet.noneOf(ParameterUsage.class);
            if (preprocessParameterSlot(1, coordinateUsages, future, blockParameterNode == null) == ParameterHandlingResult.COMPLETE) {
                return;
            }
        }

        boolean inheritPlacementCoordinates = coordinateHandledByBlockParam
            || parameterProvidesCoordinates(coordinateParameterNode);
        String block = "stone";
        int x = 0, y = 0, z = 0;

        NodeParameter blockParam = getParameter("Block");
        NodeParameter xParam = getParameter("X");
        NodeParameter yParam = getParameter("Y");
        NodeParameter zParam = getParameter("Z");
        Hand hand = resolveHand(getParameter("Hand"), Hand.MAIN_HAND);

        if (blockParam != null) block = blockParam.getStringValue();
        if (xParam != null) x = xParam.getIntValue();
        if (yParam != null) y = yParam.getIntValue();
        if (zParam != null) z = zParam.getIntValue();

        RuntimeParameterData parameterData = runtimeParameterData;
        if (parameterData != null) {
            if (parameterData.targetBlockId != null && !parameterData.targetBlockId.isEmpty()) {
                block = parameterData.targetBlockId;
                setParameterValueAndPropagate("Block", block);
            }
            if (inheritPlacementCoordinates && parameterData.targetBlockPos != null) {
                BlockPos resolved = parameterData.targetBlockPos;
                x = resolved.getX();
                y = resolved.getY();
                z = resolved.getZ();
            }
        }

        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || client.player.networkHandler == null || client.interactionManager == null || client.world == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        if (blockParameterNode != null && blockParameterNode.getType() == NodeType.PARAM_INVENTORY_SLOT) {
            String resolvedBlockId = resolveBlockIdFromInventorySlotParameter(client, blockParameterNode);
            if (resolvedBlockId == null || resolvedBlockId.isEmpty()) {
                if (future != null && !future.isDone()) {
                    future.complete(null);
                }
                return;
            }
            block = resolvedBlockId;
            setParameterValueAndPropagate("Block", block);
        }

        String originalBlockId = block;
        block = normalizeResourceId(block, "minecraft");
        if (!Objects.equals(originalBlockId, block)) {
            setParameterValueAndPropagate("Block", block);
        }

        if (block == null || block.isEmpty()) {
            sendNodeErrorMessage(client, "Cannot place block: no block selected.");
            future.complete(null);
            return;
        }

        if (blockParameterNode != null && isBlockPlacementParameter(blockParameterNode)) {
            try {
                ensureBlockInHand(client, block, Hand.MAIN_HAND);
            } catch (PlacementFailure e) {
                sendNodeErrorMessage(client, e.getMessage());
                future.complete(null);
                return;
            }
        }

        System.out.println("Placing block '" + block + "' at " + x + ", " + y + ", " + z);

        BlockPos targetPos = new BlockPos(x, y, z);
        if (parameterData != null) {
            parameterData.targetBlockPos = targetPos;
            if (parameterData.targetBlockId == null || parameterData.targetBlockId.isEmpty()) {
                parameterData.targetBlockId = block;
            }
        }
        double reachSquared = getPlacementReachSquared(client);

        Block desiredBlock = resolveBlockForPlacement(block);
        if (desiredBlock == null) {
            sendNodeErrorMessage(client, "Cannot place block: unknown block \"" + block + "\".");
            future.complete(null);
            return;
        }

        final BlockPos placementPos = targetPos;
        final Block resolvedBlock = desiredBlock;
        final String resolvedBlockId = block;
        final Hand resolvedHand = hand;
        final double resolvedReachSquared = reachSquared;

        new Thread(() -> {
            try {
                BlockHitResult placementHitResult = supplyFromClient(client, () ->
                    preparePlacementHitResult(client, placementPos, resolvedBlockId, resolvedHand, resolvedReachSquared)
                );
                runOnClientThread(client, () -> {
                    if (client.world.getBlockState(placementPos).isOf(resolvedBlock)) {
                        return;
                    }

                    ActionResult result = client.interactionManager.interactBlock(client.player, resolvedHand, placementHitResult);
                    if (!result.isAccepted()) {
                        throw new PlacementFailure("Cannot place block at " + formatBlockPos(placementPos) + ": placement rejected (" + result + ").");
                    }
                    if (client.player != null) {
                        client.player.swingHand(resolvedHand);
                        if (client.player.networkHandler != null) {
                            client.player.networkHandler.sendPacket(new HandSwingC2SPacket(resolvedHand));
                        }
                    }
                });
                boolean placed = waitForBlockPlacement(client, placementPos, resolvedBlock);
                if (!placed) {
                    sendNodeErrorMessage(client, "Attempted to place block \"" + resolvedBlockId + "\" at " + formatBlockPos(placementPos) + " but it did not appear. Make sure the space is clear and within reach.");
                }
                future.complete(null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.completeExceptionally(e);
            } catch (PlacementFailure e) {
                sendNodeErrorMessage(client, e.getMessage());
                future.complete(null);
            } catch (RuntimeException e) {
                sendNodeErrorMessage(client, "Failed to place block \"" + resolvedBlockId + "\": " + e.getMessage());
                future.complete(null);
            }
        }, "Pathmind-Place").start();
    }

    private boolean parameterProvidesCoordinates(Node parameterNode) {
        if (parameterNode == null) {
            return false;
        }
        return parameterProvidesCoordinates(parameterNode.getType());
    }

    private boolean parameterProvidesCoordinates(NodeType parameterType) {
        if (parameterType == null) {
            return false;
        }
        switch (parameterType) {
            case PARAM_COORDINATE:
            case PARAM_SCHEMATIC:
            case PARAM_PLACE_TARGET:
            case PARAM_ITEM:
            case PARAM_ENTITY:
            case PARAM_PLAYER:
            case PARAM_BLOCK:
            case PARAM_WAYPOINT:
            case PARAM_CLOSEST:
                return true;
            default:
                return false;
        }
    }

    private boolean supportsDirectSensorParameter(NodeType parameterType) {
        if (!isSensorNode() || parameterType == null) {
            return false;
        }
        switch (type) {
            case SENSOR_TOUCHING_BLOCK:
            case SENSOR_BLOCK_AHEAD:
                return parameterType == NodeType.PARAM_BLOCK || parameterType == NodeType.PARAM_PLACE_TARGET;
            case SENSOR_TOUCHING_ENTITY:
                return parameterType == NodeType.PARAM_ENTITY;
            case SENSOR_AT_COORDINATES:
                return parameterType == NodeType.PARAM_COORDINATE || parameterType == NodeType.PARAM_PLACE_TARGET;
            case SENSOR_ITEM_IN_INVENTORY:
                return parameterType == NodeType.PARAM_ITEM;
            case SENSOR_VILLAGER_TRADE:
                return parameterType == NodeType.PARAM_VILLAGER_TRADE;
            case SENSOR_KEY_PRESSED:
                return parameterType == NodeType.PARAM_KEY;
            case SENSOR_IS_RENDERED:
                switch (parameterType) {
                    case PARAM_BLOCK:
                    case PARAM_ITEM:
                    case PARAM_ENTITY:
                    case PARAM_PLAYER:
                    case PARAM_PLACE_TARGET:
                        return true;
                    default:
                        return false;
                }
            case SENSOR_CHAT_MESSAGE:
                return parameterType == NodeType.PARAM_PLAYER || parameterType == NodeType.PARAM_MESSAGE;
            default:
                return false;
        }
    }

    private boolean isBlockPlacementParameter(Node parameterNode) {
        if (parameterNode == null) {
            return false;
        }
        NodeType parameterType = parameterNode.getType();
        return parameterType == NodeType.PARAM_BLOCK
            || parameterType == NodeType.PARAM_PLACE_TARGET;
    }

    private boolean blockParameterProvidesPlacementCoordinates(Node parameterNode) {
        return parameterNode != null && parameterNode.getType() == NodeType.PARAM_PLACE_TARGET;
    }

    private String resolveBlockIdFromInventorySlotParameter(net.minecraft.client.MinecraftClient client,
                                                           Node parameterNode) {
        if (client == null || client.player == null || parameterNode == null) {
            return null;
        }
        SlotSelectionType selectionType = resolveInventorySlotSelectionType(parameterNode);
        if (selectionType == SlotSelectionType.GUI_CONTAINER) {
            sendNodeErrorMessage(client, type.getDisplayName() + " can only use player inventory slots.");
            return null;
        }
        PlayerInventory inventory = client.player.getInventory();
        int slotValue = clampInventorySlot(inventory, parseNodeInt(parameterNode, "Slot", 0));
        ItemStack stack = inventory.getStack(slotValue);
        if (stack.isEmpty()) {
            sendNodeErrorMessage(client, "Selected slot for " + type.getDisplayName() + " is empty.");
            return null;
        }
        if (!(stack.getItem() instanceof BlockItem)) {
            sendNodeErrorMessage(client, "Selected slot for " + type.getDisplayName() + " does not contain a block.");
            return null;
        }
        if (runtimeParameterData == null) {
            runtimeParameterData = new RuntimeParameterData();
        }
        runtimeParameterData.slotIndex = slotValue;
        runtimeParameterData.slotSelectionType = SlotSelectionType.PLAYER_INVENTORY;
        if (!ensureStackSelectedInMainHand(client, inventory, slotValue, stack)) {
            sendNodeErrorMessage(client, "Failed to prepare selected block for " + type.getDisplayName() + ".");
            return null;
        }
        Identifier id = Registries.ITEM.getId(stack.getItem());
        return id != null ? id.toString() : null;
    }

    private String resolveBlockIdFromParameterNode(Node parameterNode) {
        if (parameterNode == null) {
            return null;
        }
        NodeType parameterType = parameterNode.getType();
        switch (parameterType) {
            case PARAM_BLOCK:
                for (String entry : splitMultiValueList(getBlockParameterValue(parameterNode))) {
                    return entry;
                }
                return null;
            case PARAM_PLACE_TARGET:
                return getParameterString(parameterNode, "Block");
            default:
                return null;
        }
    }

    private String normalizePlacementBlockId(String blockId) {
        if (blockId == null) {
            return null;
        }
        String sanitized = sanitizeResourceId(blockId);
        if (sanitized == null || sanitized.isEmpty()) {
            return "";
        }
        return normalizeResourceId(sanitized, "minecraft");
    }

    private String getBlockIdFromHand(net.minecraft.client.MinecraftClient client, Hand hand) {
        if (client == null || client.player == null) {
            return null;
        }
        ItemStack stack = client.player.getStackInHand(hand);
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem)) {
            return null;
        }
        Identifier id = Registries.ITEM.getId(stack.getItem());
        return id != null ? id.toString() : null;
    }
    
    private void executeBuildCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.of(ParameterUsage.POSITION), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        if (mode == null) {
            future.completeExceptionally(new RuntimeException("No mode set for BUILD node"));
            return;
        }
        
        String schematic = "house.schematic";
        NodeParameter schematicParam = getParameter("Schematic");
        if (schematicParam != null) {
            schematic = schematicParam.getStringValue();
        }
        
        String command;
        Vec3d targetVector = runtimeParameterData != null ? runtimeParameterData.targetVector : null;
        if (targetVector != null) {
            int x = (int) Math.floor(targetVector.x);
            int y = (int) Math.floor(targetVector.y);
            int z = (int) Math.floor(targetVector.z);
            command = String.format("#build %s %d %d %d", schematic, x, y, z);
            System.out.println("Executing build at parameter coordinates: " + command);
        } else {
            switch (mode) {
                case BUILD_PLAYER:
                    command = String.format("#build %s", schematic);
                    System.out.println("Executing build at player location: " + command);
                    break;
                    
                case BUILD_XYZ:
                    int x = 0, y = 0, z = 0;
                    NodeParameter xParam = getParameter("X");
                    NodeParameter yParam = getParameter("Y");
                    NodeParameter zParam = getParameter("Z");
                    
                    if (xParam != null) x = xParam.getIntValue();
                    if (yParam != null) y = yParam.getIntValue();
                    if (zParam != null) z = zParam.getIntValue();
                    
                    command = String.format("#build %s %d %d %d", schematic, x, y, z);
                    System.out.println("Executing build at coordinates: " + command);
                    break;
                    
                default:
                    future.completeExceptionally(new RuntimeException("Unknown BUILD mode: " + mode));
                    return;
            }
        }

        if (!isBaritoneApiAvailable() && isBaritoneModAvailable()) {
            executeCommand(command);
            future.complete(null);
            return;
        }

        Object baritone = getBaritone();
        if (baritone == null) {
            System.err.println("Baritone not available for build command");
            future.completeExceptionally(new RuntimeException("Baritone not available"));
            return;
        }

        PreciseCompletionTracker.getInstance().startTrackingTask(PreciseCompletionTracker.TASK_BUILD, future);
        executeCommand(command);
    }
    
    private void executeExploreCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.of(ParameterUsage.POSITION), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        if (mode == null) {
            future.completeExceptionally(new RuntimeException("No mode set for EXPLORE node"));
            return;
        }

        if (!isBaritoneApiAvailable() && isBaritoneModAvailable()) {
            switch (mode) {
                case EXPLORE_CURRENT: {
                    String command = "#explore";
                    executeCommand(command);
                    future.complete(null);
                    return;
                }
                case EXPLORE_XYZ: {
                    int x = 0, z = 0;
                    NodeParameter xParam = getParameter("X");
                    NodeParameter zParam = getParameter("Z");

                    if (xParam != null) x = xParam.getIntValue();
                    if (zParam != null) z = zParam.getIntValue();

                    String command = String.format("#explore %d %d", x, z);
                    executeCommand(command);
                    future.complete(null);
                    return;
                }
                case EXPLORE_FILTER: {
                    String filter = "explore.txt";
                    NodeParameter filterParam = getParameter("Filter");
                    if (filterParam != null) {
                        filter = filterParam.getStringValue();
                    }
                    executeCommand("#explore " + filter);
                    future.complete(null);
                    return;
                }
                default:
                    future.completeExceptionally(new RuntimeException("Unknown EXPLORE mode: " + mode));
                    return;
            }
        }
        
        Object baritone = getBaritone();
        if (baritone == null) {
            System.err.println("Baritone not available for explore command");
            future.completeExceptionally(new RuntimeException("Baritone not available"));
            return;
        }
        
        resetBaritonePathing(baritone);
        Object exploreProcess = BaritoneApiProxy.getExploreProcess(baritone);
        PreciseCompletionTracker.getInstance().startTrackingTask(PreciseCompletionTracker.TASK_EXPLORE, future);
        
        switch (mode) {
            case EXPLORE_CURRENT:
                System.out.println("Executing explore from current position");
                BaritoneApiProxy.explore(exploreProcess, 0, 0); // 0,0 means from current position
                break;
                
            case EXPLORE_XYZ:
                int x = 0, z = 0;
                NodeParameter xParam = getParameter("X");
                NodeParameter zParam = getParameter("Z");
                
                if (xParam != null) x = xParam.getIntValue();
                if (zParam != null) z = zParam.getIntValue();
                
                System.out.println("Executing explore at: " + x + ", " + z);
                BaritoneApiProxy.explore(exploreProcess, x, z);
                break;
                
            case EXPLORE_FILTER:
                String filter = "explore.txt";
                NodeParameter filterParam = getParameter("Filter");
                if (filterParam != null) {
                    filter = filterParam.getStringValue();
                }
                
                System.out.println("Executing explore with filter: " + filter);
                // For filter-based exploration, we need to use a different approach
                executeCommand("#explore " + filter);
                future.complete(null); // Command-based exploration completes immediately
                return;
                
            default:
                future.completeExceptionally(new RuntimeException("Unknown EXPLORE mode: " + mode));
                return;
        }
    }
    
    private void executeFollowCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.of(ParameterUsage.POSITION), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        if (mode == null) {
            future.completeExceptionally(new RuntimeException("No mode set for FOLLOW node"));
            return;
        }
        
        String command;
        switch (mode) {
            case FOLLOW_PLAYER:
                String player = "Any";
                NodeParameter playerParam = getParameter("Player");
                if (playerParam != null) {
                    player = playerParam.getStringValue();
                }

                if (isAnyPlayerValue(player)) {
                    command = "#follow players";
                    System.out.println("Executing follow any players: " + command);
                } else {
                    command = "#follow player " + player;
                    System.out.println("Executing follow player: " + command);
                }
                break;
                
            case FOLLOW_PLAYERS:
                command = "#follow players";
                System.out.println("Executing follow any players: " + command);
                break;
                
            case FOLLOW_ENTITIES:
                command = "#follow entities";
                System.out.println("Executing follow any entities: " + command);
                break;
                
            case FOLLOW_ENTITY_TYPE:
                String entity = "cow";
                NodeParameter entityParam = getParameter("Entity");
                if (entityParam != null) {
                    entity = entityParam.getStringValue();
                }

                command = "#follow entity " + entity;
                System.out.println("Executing follow entity type: " + command);
                break;
                
            default:
                future.completeExceptionally(new RuntimeException("Unknown FOLLOW mode: " + mode));
                return;
        }
        
        executeCommand(command);
        future.complete(null); // Follow commands complete immediately
    }
    
    private void executeWaitCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        double baseDuration = Math.max(0.0, getDoubleParameter("Duration", 1.0));
        double minimum = Math.max(0.0, getDoubleParameter("MinimumDurationSeconds", 0.0));
        double variance = Math.max(0.0, getDoubleParameter("RandomVarianceSeconds", 0.0));

        double effectiveDuration = Math.max(baseDuration, minimum);
        if (variance > 0.0) {
            double randomOffset = (Math.random() * 2.0 - 1.0) * variance;
            effectiveDuration = Math.max(minimum, Math.max(0.0, effectiveDuration + randomOffset));
        }

        final double waitSeconds = effectiveDuration;
        System.out.println("Waiting for " + waitSeconds + " seconds (configured duration=" + baseDuration + ")");

        new Thread(() -> {
            try {
                ExecutionManager manager = ExecutionManager.getInstance();
                String nodeId = getId();
                long waitMs = (long) (waitSeconds * 1000);

                while (true) {
                    Node active = manager.getActiveNode();
                    if (active == null || nodeId == null || !nodeId.equals(active.getId())) {
                        future.complete(null);
                        return;
                    }
                    if (manager.getActiveNodeDuration() >= waitMs) {
                        future.complete(null);
                        return;
                    }
                    Thread.sleep(25L);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.completeExceptionally(e);
            }
        }, "Pathmind-Wait").start();
    }
    
    private void executeControlRepeat(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        int count = Math.max(0, getIntParameter("Count", 1));
        if (!repeatActive) {
            repeatRemainingIterations = count;
            repeatActive = true;
        }
        if (repeatRemainingIterations > 0) {
            repeatRemainingIterations--;
            setNextOutputSocket(0);
        } else {
            repeatRemainingIterations = 0;
            repeatActive = false;
            setNextOutputSocket(1);
        }
        future.complete(null);
    }
    
    private void executeControlRepeatUntil(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        boolean conditionMet = evaluateConditionFromParameters();
        if (conditionMet) {
            repeatRemainingIterations = 0;
            repeatActive = false;
            setNextOutputSocket(1);
        } else {
            repeatActive = true;
            setNextOutputSocket(0);
        }
        future.complete(null);
    }
    
    private void executeControlForever(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        repeatActive = true;
        setNextOutputSocket(0);
        future.complete(null);
    }

    private void executeControlIf(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        boolean condition = evaluateConditionFromParameters();
        setNextOutputSocket(condition ? 0 : NO_OUTPUT);
        future.complete(null);
    }

    private void executeControlIfElse(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        boolean condition = evaluateConditionFromParameters();
        setNextOutputSocket(condition ? 0 : 1);
        future.complete(null);
    }

    private void executeMessageCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        List<String> lines = getMessageLines();
        if (lines == null || lines.isEmpty()) {
            lines = Collections.singletonList("Hello World");
        }

        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client != null && client.player != null && client.player.networkHandler != null) {
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
                MESSAGE_SCHEDULER.schedule(() -> {
                    MinecraftClient.getInstance().execute(() -> {
                        if (MinecraftClient.getInstance().player != null
                            && MinecraftClient.getInstance().player.networkHandler != null) {
                            boolean isCommand = sendText.startsWith("/");
                            if (isCommand) {
                                String cmd = sendText.length() > 1 ? sendText.substring(1) : "";
                                if (!cmd.isEmpty()) {
                                    MinecraftClient.getInstance().player.networkHandler.sendChatCommand(cmd);
                                }
                            } else {
                                MinecraftClient.getInstance().player.networkHandler.sendChatMessage(sendText);
                            }
                        }
                    });
                }, scheduledDelay, TimeUnit.MILLISECONDS);
                sent[0]++;
            }
            long completionDelay = Math.max(0, (sent[0] - 1) * delayMs + delayMs);
            MESSAGE_SCHEDULER.schedule(() -> {
                future.complete(null);
            }, completionDelay, TimeUnit.MILLISECONDS);
        } else {
            System.err.println("Unable to send chat message: client or player not available");
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
        if (startNode == null) {
            return raw;
        }
        ExecutionManager manager = ExecutionManager.getInstance();
        StringBuilder output = new StringBuilder(raw.length());
        int index = 0;
        while (index < raw.length()) {
            char current = raw.charAt(index);
            if (current == '~') {
                int nameStart = index + 1;
                if (nameStart < raw.length() && isInlineVariableChar(raw.charAt(nameStart))) {
                    int end = nameStart + 1;
                    while (end < raw.length() && isInlineVariableChar(raw.charAt(end))) {
                        end++;
                    }
                    String name = raw.substring(nameStart, end);
                    ExecutionManager.RuntimeVariable variable = resolveRuntimeVariableForName(manager, startNode, name);
                    if (variable != null) {
                        String replacement = formatRuntimeVariableValue(variable);
                        if (replacement != null && !replacement.isEmpty()) {
                            output.append(replacement);
                            index = end;
                            continue;
                        }
                    }
                    output.append(raw, index, end);
                    index = end;
                    continue;
                }
            }
            output.append(current);
            index++;
        }
        return output.toString();
    }

    private ExecutionManager.RuntimeVariable resolveRuntimeVariableForName(ExecutionManager manager, Node startNode, String name) {
        if (manager == null || name == null || name.trim().isEmpty()) {
            return null;
        }
        if (startNode != null) {
            ExecutionManager.RuntimeVariable direct = manager.getRuntimeVariable(startNode, name.trim());
            if (direct != null) {
                return direct;
            }
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
            if (!entryName.trim().equals(name.trim())) {
                continue;
            }
            if (match != null) {
                return null;
            }
            match = entry.getVariable();
        }
        return match;
    }

    private String formatRuntimeVariableValue(ExecutionManager.RuntimeVariable variable) {
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
            case PARAM_DURATION:
                return getRuntimeValue(values, "duration");
            case PARAM_RANGE:
            case PARAM_CLOSEST:
                return getRuntimeValue(values, "range");
            case PARAM_AMOUNT:
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
                return formatCoordinateValues(values);
            default:
                break;
        }
        for (String value : values.values()) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private String formatCoordinateValues(Map<String, String> values) {
        String x = getRuntimeValue(values, "x");
        String y = getRuntimeValue(values, "y");
        String z = getRuntimeValue(values, "z");
        if (x.isEmpty() || y.isEmpty() || z.isEmpty()) {
            return "";
        }
        return x + " " + y + " " + z;
    }

    private String formatRotationValues(Map<String, String> values) {
        String yaw = getRuntimeValue(values, "yaw");
        String pitch = getRuntimeValue(values, "pitch");
        if (yaw.isEmpty() || pitch.isEmpty()) {
            return "";
        }
        return yaw + " " + pitch;
    }

    private String getRuntimeValue(Map<String, String> values, String key) {
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
        return "";
    }

    private boolean isInlineVariableChar(char character) {
        return Character.isLetterOrDigit(character) || character == '_' || character == '-';
    }

    private boolean isOpenGuiFilled() {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
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

    private void executeWriteBookCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }

        String text = getBookText();
        int pageNumber = getIntParameter("Page", 1);
        // Convert to 0-indexed page
        int pageIndex = Math.max(0, pageNumber - 1);

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            sendNodeErrorMessage(client, "Client or player not available");
            future.completeExceptionally(new RuntimeException("Client or player not available"));
            return;
        }

        // Check if a book edit screen is open
        if (!(client.currentScreen instanceof BookEditScreen)) {
            sendNodeErrorMessage(client, "No book and quill screen is open");
            future.completeExceptionally(new RuntimeException("No book and quill screen is open"));
            return;
        }

        BookEditScreen bookScreen = (BookEditScreen) client.currentScreen;

        client.execute(() -> {
            try {
                // Use reflection to access the book screen's internal state
                // Get the pages list
                java.util.List<Object> pages = null;
                int currentPage = 0;

                // Try to find the pages field
                java.util.List<Object> emptyCandidate = null;
                java.util.List<Field> stringListFields = new java.util.ArrayList<>();
                Field pagesField = null;
                try {
                    pagesField = bookScreen.getClass().getDeclaredField("pages");
                    pagesField.setAccessible(true);
                    Object value = pagesField.get(bookScreen);
                    if (value instanceof java.util.List) {
                    @SuppressWarnings("unchecked")
                    java.util.List<Object> list = (java.util.List<Object>) value;
                    pages = list;
                    }
                } catch (NoSuchFieldException ignored) {
                    // Fallback to heuristic search below
                }
                for (Field field : bookScreen.getClass().getDeclaredFields()) {
                    if (pages != null) {
                        break;
                    }
                    if (field.getType() != java.util.List.class) {
                        continue;
                    }
                    field.setAccessible(true);
                    Object value = field.get(bookScreen);
                    if (!(value instanceof java.util.List)) {
                        continue;
                    }
                    @SuppressWarnings("unchecked")
                    java.util.List<Object> list = (java.util.List<Object>) value;
                    stringListFields.add(field);
                    if (!list.isEmpty()) {
                        pages = list;
                        break;
                    }
                    String fieldName = field.getName().toLowerCase();
                    if (fieldName.contains("page")) {
                        pages = list;
                        break;
                    }
                    if (emptyCandidate == null) {
                        emptyCandidate = list;
                    }
                }
                if (pages == null && emptyCandidate != null) {
                    pages = emptyCandidate;
                }

                if (pages == null) {
                    System.err.println("BookEditScreen pages list not found. Fields:");
                    for (Field field : bookScreen.getClass().getDeclaredFields()) {
                        System.err.println(" - " + field.getName() + " : " + field.getType());
                    }
                    sendNodeErrorMessage(client, "Could not access book pages");
                    future.completeExceptionally(new RuntimeException("Could not access book pages"));
                    return;
                }

                // Ensure we have enough pages
                Method appendNewPageMethod = null;
                Method countPagesMethod = null;
                Method setPageTextMethod = null;
                Method updatePageMethod = null;
                Method writeNbtDataMethod = null;
                System.out.println("WRITE_BOOK: screen=" + bookScreen.getClass().getName()
                    + " pageIndex=" + pageIndex);
                for (Method method : bookScreen.getClass().getDeclaredMethods()) {
                    String methodName = method.getName().toLowerCase();
                    if (method.getParameterCount() == 0 && method.getReturnType() == void.class) {
                        if (appendNewPageMethod == null
                            && (methodName.contains("appendnewpage") || methodName.contains("method_2436"))) {
                            method.setAccessible(true);
                            appendNewPageMethod = method;
                        }
                    }
                    if (method.getParameterCount() == 0 && method.getReturnType() == int.class) {
                        if (countPagesMethod == null
                            && (methodName.contains("countpages") || methodName.contains("method_17046"))) {
                            method.setAccessible(true);
                            countPagesMethod = method;
                        }
                    }
                    if (method.getParameterCount() == 0 && method.getReturnType() == void.class) {
                        if (updatePageMethod == null
                            && (methodName.contains("updatepage") || methodName.contains("method_71537"))) {
                            method.setAccessible(true);
                            updatePageMethod = method;
                        }
                        if (writeNbtDataMethod == null
                            && (methodName.contains("writenbtdata") || methodName.contains("method_37433"))) {
                            method.setAccessible(true);
                            writeNbtDataMethod = method;
                        }
                    }
                    if (method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == String.class
                        && method.getReturnType() == void.class) {
                        if (setPageTextMethod == null
                            && (methodName.contains("setpage") || methodName.contains("pagetext") || methodName.contains("method_71539"))) {
                            method.setAccessible(true);
                            setPageTextMethod = method;
                        }
                    }
                }

                if (pagesField != null) {
                    Object value = pagesField.get(bookScreen);
                    if (value instanceof java.util.List) {
                        @SuppressWarnings("unchecked")
                        java.util.List<Object> list = (java.util.List<Object>) value;
                        System.out.println("WRITE_BOOK: pagesField list size before=" + list.size());
                        if (list.isEmpty()) {
                            try {
                                list.add(setPageTextMethod != null ? RawFilteredPair.of("") : "");
                            } catch (UnsupportedOperationException ignored) {
                                // replace with mutable list if backing list is immutable
                                list = null;
                            }
                        }
                        if (list == null) {
                            java.util.List<Object> seeded = new java.util.ArrayList<>();
                            seeded.add(setPageTextMethod != null ? RawFilteredPair.of("") : "");
                            pagesField.set(bookScreen, seeded);
                            pages = seeded;
                        } else {
                            pages = list;
                        }
                    }
                } else if (pages.isEmpty()) {
                    pages.add(setPageTextMethod != null ? RawFilteredPair.of("") : "");
                }
                System.out.println("WRITE_BOOK: pages size after seed=" + pages.size());

                boolean useRawFilteredPairs = false;
                if (!pages.isEmpty()) {
                    useRawFilteredPairs = !(pages.get(0) instanceof String);
                } else if (setPageTextMethod != null) {
                    useRawFilteredPairs = true;
                }
                if (!pages.isEmpty()) {
                    Object first = pages.get(0);
                    System.out.println("WRITE_BOOK: pages element type=" + (first == null ? "null" : first.getClass().getName()));
                }
                System.out.println("WRITE_BOOK: useRawFilteredPairs=" + useRawFilteredPairs
                    + " setPageText=" + (setPageTextMethod != null)
                    + " updatePage=" + (updatePageMethod != null)
                    + " writeNbtData=" + (writeNbtDataMethod != null));

                int pageCount = pages.size();
                if (countPagesMethod != null) {
                    try {
                        pageCount = (int) countPagesMethod.invoke(bookScreen);
                    } catch (Exception ignored) {
                        pageCount = pages.size();
                    }
                }

                while (pageCount <= pageIndex) {
                    if (appendNewPageMethod != null) {
                        int beforeSize = pages.size();
                        appendNewPageMethod.invoke(bookScreen);
                        if (pagesField != null) {
                            Object value = pagesField.get(bookScreen);
                            if (value instanceof java.util.List) {
                                @SuppressWarnings("unchecked")
                                java.util.List<Object> list = (java.util.List<Object>) value;
                                pages = list;
                            }
                        }
                        if (countPagesMethod != null) {
                            pageCount = (int) countPagesMethod.invoke(bookScreen);
                        } else {
                            pageCount = pages.size();
                        }
                        if (pages.size() == beforeSize && pageCount == beforeSize) {
                            pages.add(useRawFilteredPairs ? RawFilteredPair.of("") : "");
                            pageCount = pages.size();
                        }
                    } else {
                        pages.add(useRawFilteredPairs ? RawFilteredPair.of("") : "");
                        pageCount = pages.size();
                    }
                }

                // Set the current page before applying text
                for (Field field : bookScreen.getClass().getDeclaredFields()) {
                    if (field.getType() == int.class) {
                        String fieldName = field.getName().toLowerCase();
                        if (fieldName.contains("page") || fieldName.contains("current")) {
                            field.setAccessible(true);
                            field.setInt(bookScreen, pageIndex);
                            break;
                        }
                    }
                }

                // Set the text on the specified page
                String truncatedText = text;
                if (truncatedText.length() > BOOK_PAGE_MAX_CHARS) {
                    truncatedText = truncatedText.substring(0, BOOK_PAGE_MAX_CHARS);
                }
                System.out.println("WRITE_BOOK: text length=" + truncatedText.length()
                    + " preview=\"" + (truncatedText.length() > 40 ? truncatedText.substring(0, 40) + "..." : truncatedText) + "\"");
                boolean setViaMethod = false;
                if (setPageTextMethod != null && pageIndex >= 0 && pageIndex < pages.size()) {
                    try {
                        setPageTextMethod.invoke(bookScreen, truncatedText);
                        setViaMethod = true;
                    } catch (Exception ignored) {
                        // Ignore UI refresh errors
                    }
                }
                if (!setViaMethod && pageIndex >= 0 && pageIndex < pages.size()) {
                    pages.set(pageIndex, useRawFilteredPairs ? RawFilteredPair.of(truncatedText) : truncatedText);
                    if (pagesField != null) {
                        java.util.List<Object> copy = new java.util.ArrayList<>(pages);
                        pagesField.set(bookScreen, copy);
                        pages = copy;
                    }
                } else if (setViaMethod && pagesField != null) {
                    Object value = pagesField.get(bookScreen);
                    if (value instanceof java.util.List) {
                        @SuppressWarnings("unchecked")
                        java.util.List<Object> list = (java.util.List<Object>) value;
                        pages = list;
                    }
                }

                java.util.List<String> pageStrings = new java.util.ArrayList<>();
                for (Object page : pages) {
                    if (page instanceof String) {
                        pageStrings.add((String) page);
                    } else if (page instanceof RawFilteredPair) {
                        @SuppressWarnings("unchecked")
                        RawFilteredPair<String> pair = (RawFilteredPair<String>) page;
                        pageStrings.add(pair.get(false));
                    } else {
                        pageStrings.add("");
                    }
                }

                // Update any page-related fields to ensure the UI refreshes
                TextFieldWidget editBox = null;
                for (Field field : bookScreen.getClass().getDeclaredFields()) {
                    String fieldName = field.getName().toLowerCase();
                    if (field.getType() == java.util.List.class) {
                        if (!fieldName.contains("page")) {
                            continue;
                        }
                        field.setAccessible(true);
                        Object value = field.get(bookScreen);
                        if (value instanceof java.util.List) {
                            @SuppressWarnings("unchecked")
                            java.util.List<Object> list = (java.util.List<Object>) value;
                            try {
                                list.clear();
                                list.addAll(pages);
                            } catch (UnsupportedOperationException ignored) {
                                // Skip immutable lists
                            }
                        }
                        continue;
                    }
                    if (field.getType() == String[].class && fieldName.contains("page")) {
                        field.setAccessible(true);
                        field.set(bookScreen, pageStrings.toArray(new String[0]));
                        continue;
                    }
                    if (field.getType() == String.class
                        && fieldName.contains("page")
                        && (fieldName.contains("text") || fieldName.contains("content"))) {
                        field.setAccessible(true);
                        field.set(bookScreen, truncatedText);
                        continue;
                    }
                    if (field.getType() == TextFieldWidget.class
                        && (fieldName.contains("page") || fieldName.contains("text"))) {
                        field.setAccessible(true);
                        Object value = field.get(bookScreen);
                        if (value instanceof TextFieldWidget) {
                            editBox = (TextFieldWidget) value;
                            editBox.setText(truncatedText);
                        }
                    }
                }
                if (editBox == null) {
                    for (Field field : bookScreen.getClass().getDeclaredFields()) {
                        if (field.getType() == TextFieldWidget.class) {
                            field.setAccessible(true);
                            Object value = field.get(bookScreen);
                            if (value instanceof TextFieldWidget) {
                                editBox = (TextFieldWidget) value;
                                editBox.setText(truncatedText);
                                break;
                            }
                        }
                    }
                }

                // Keep the screen's backing ItemStack in sync so UI updates immediately
                ItemStack screenStack = null;
                for (Field field : bookScreen.getClass().getDeclaredFields()) {
                    if (field.getType() == ItemStack.class) {
                        field.setAccessible(true);
                        Object value = field.get(bookScreen);
                        if (value instanceof ItemStack) {
                            screenStack = (ItemStack) value;
                            break;
                        }
                    }
                }
                if (screenStack != null && screenStack.isOf(Items.WRITABLE_BOOK)) {
                    try {
                        java.util.List<RawFilteredPair<String>> componentPages = new java.util.ArrayList<>();
                        for (String page : pageStrings) {
                            componentPages.add(RawFilteredPair.of(page));
                        }
                        screenStack.set(DataComponentTypes.WRITABLE_BOOK_CONTENT,
                            new WritableBookContentComponent(componentPages));
                    } catch (Exception ignored) {
                        // Ignore component sync errors
                    }
                }

                // Write updated pages into the book stack if possible
                if (writeNbtDataMethod != null) {
                    try {
                        writeNbtDataMethod.invoke(bookScreen);
                    } catch (Exception ignored) {
                        // Ignore persistence errors to avoid stopping execution
                    }
                }

                // Send book update to server and client so text becomes visible immediately.
                Hand hand = Hand.MAIN_HAND;
                for (Field field : bookScreen.getClass().getDeclaredFields()) {
                    if (field.getType() == Hand.class) {
                        field.setAccessible(true);
                        Object value = field.get(bookScreen);
                        if (value instanceof Hand) {
                            hand = (Hand) value;
                            break;
                        }
                    }
                }
                ItemStack main = client.player.getMainHandStack();
                ItemStack offhand = client.player.getOffHandStack();
                if (!main.isOf(Items.WRITABLE_BOOK) && offhand.isOf(Items.WRITABLE_BOOK)) {
                    hand = Hand.OFF_HAND;
                }
                ItemStack heldBook = client.player.getStackInHand(hand);
                if (heldBook != null && heldBook.isOf(Items.WRITABLE_BOOK)) {
                    // Keep stack component in sync so the UI reflects the change immediately
                    try {
                        java.util.List<RawFilteredPair<String>> componentPages = new java.util.ArrayList<>();
                        for (String page : pageStrings) {
                            componentPages.add(RawFilteredPair.of(page));
                        }
                        heldBook.set(DataComponentTypes.WRITABLE_BOOK_CONTENT,
                            new WritableBookContentComponent(componentPages));
                    } catch (Exception ignored) {
                        // Fallback to packet-only update
                    }

                    // Tell server (and client) via standard packet (works in dev env)
                    int slot = hand == Hand.MAIN_HAND
                        ? PlayerInventoryBridge.getSelectedSlot(client.player.getInventory())
                        : PlayerInventory.MAIN_SIZE + PLAYER_ARMOR_SLOT_COUNT;
                    client.getNetworkHandler().sendPacket(
                        new BookUpdateC2SPacket(slot, pageStrings, java.util.Optional.empty())
                    );

                    // Reopen the book screen to force a full UI refresh of the edited text
                    ItemStack reopenStack = screenStack != null ? screenStack : heldBook;
                    if (reopenStack != null && reopenStack.isOf(Items.WRITABLE_BOOK)) {
                        WritableBookContentComponent content = reopenStack.get(DataComponentTypes.WRITABLE_BOOK_CONTENT);
                        if (content == null) {
                            content = WritableBookContentComponent.DEFAULT;
                        }
                        final ItemStack reopenStackFinal = reopenStack;
                        final WritableBookContentComponent contentFinal = content;
                        final Hand reopenHand = hand;
                        final PlayerEntity playerFinal = client.player;
                        client.execute(() -> {
                            if (playerFinal != null) {
                                net.minecraft.client.gui.screen.Screen bookEditScreen = createBookEditScreen(playerFinal, reopenStackFinal, reopenHand, contentFinal);
                                if (bookEditScreen != null) {
                                    client.setScreen(bookEditScreen);
                                }
                            }
                        });
                    }
                }

                // Flag book screen as dirty if such a field exists
                for (Field field : bookScreen.getClass().getDeclaredFields()) {
                    if (field.getType() == boolean.class) {
                        String fieldName = field.getName().toLowerCase();
                        if (fieldName.contains("dirty") || fieldName.contains("modified")) {
                            field.setAccessible(true);
                            field.setBoolean(bookScreen, true);
                            break;
                        }
                    }
                }

                // Safely invoke updatePage if it exists (only after pages are populated)
                if (updatePageMethod != null) {
                    try {
                        if (!pages.isEmpty()) {
                            updatePageMethod.invoke(bookScreen);
                        }
                    } catch (Exception ignored) {
                        // Ignore UI refresh errors to avoid stopping execution
                    }
                }

                // One more refresh on the next tick in case the edit box wasn't ready yet
                final Method setPageTextMethodFinal = setPageTextMethod;
                final Method updatePageMethodFinal = updatePageMethod;
                final java.util.List<Object> pagesFinal = pages;
                final String truncatedTextFinal = truncatedText;
                final int pageIndexFinal = pageIndex;
                final BookEditScreen bookScreenFinal = bookScreen;
                client.execute(() -> {
                    try {
                        if (setPageTextMethodFinal != null && pageIndexFinal >= 0 && pageIndexFinal < pagesFinal.size()) {
                            setPageTextMethodFinal.invoke(bookScreenFinal, truncatedTextFinal);
                        }
                        if (updatePageMethodFinal != null && !pagesFinal.isEmpty()) {
                            updatePageMethodFinal.invoke(bookScreenFinal);
                        }
                        // Force edit box text refresh on the next tick
                        TextFieldWidget delayedEditBox = null;
                        try {
                            Field editBoxField = bookScreenFinal.getClass().getDeclaredField("editBox");
                            editBoxField.setAccessible(true);
                            Object value = editBoxField.get(bookScreenFinal);
                            if (value instanceof TextFieldWidget) {
                                delayedEditBox = (TextFieldWidget) value;
                            }
                        } catch (NoSuchFieldException ignored) {
                            // fall back to scanning fields below
                        }
                        if (delayedEditBox == null) {
                            for (Field field : bookScreenFinal.getClass().getDeclaredFields()) {
                                if (field.getType() == TextFieldWidget.class) {
                                    field.setAccessible(true);
                                    Object value = field.get(bookScreenFinal);
                                    if (value instanceof TextFieldWidget) {
                                        delayedEditBox = (TextFieldWidget) value;
                                        break;
                                    }
                                }
                            }
                        }
                        if (delayedEditBox != null) {
                            delayedEditBox.setText(truncatedTextFinal);
                            bookScreenFinal.setFocused(delayedEditBox);
                        }
                    } catch (Exception ignored) {
                        // Ignore delayed UI refresh errors
                    }
                });

                future.complete(null);
            } catch (Exception e) {
                e.printStackTrace();
                String message = e.getMessage();
                if (message == null || message.isBlank()) {
                    message = e.getClass().getSimpleName();
                }
                sendNodeErrorMessage(client, "Error writing to book: " + message);
                future.completeExceptionally(e);
            }
        });
    }

    private static net.minecraft.client.gui.screen.Screen createBookEditScreen(
            PlayerEntity player, ItemStack stack, Hand hand, WritableBookContentComponent content) {
        try {
            // Try 4-arg constructor (newer MC versions)
            java.lang.reflect.Constructor<?> ctor = BookEditScreen.class.getConstructor(
                PlayerEntity.class, ItemStack.class, Hand.class, WritableBookContentComponent.class);
            return (net.minecraft.client.gui.screen.Screen) ctor.newInstance(player, stack, hand, content);
        } catch (NoSuchMethodException ignored) {
            // Fall through to 3-arg constructor
        } catch (ReflectiveOperationException e) {
            return null;
        }
        try {
            // Try 3-arg constructor (MC 1.21)
            java.lang.reflect.Constructor<?> ctor = BookEditScreen.class.getConstructor(
                PlayerEntity.class, ItemStack.class, Hand.class);
            return (net.minecraft.client.gui.screen.Screen) ctor.newInstance(player, stack, hand);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private void executeGoalCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.of(ParameterUsage.POSITION), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        if (mode == null) {
            future.completeExceptionally(new RuntimeException("No mode set for GOAL node"));
            return;
        }

        if (!isBaritoneApiAvailable() && isBaritoneModAvailable()) {
            if (runtimeParameterData != null && runtimeParameterData.targetBlockPos != null) {
                BlockPos target = runtimeParameterData.targetBlockPos;
                String command = String.format("#goal %d %d %d", target.getX(), target.getY(), target.getZ());
                executeCommand(command);
                future.complete(null);
                return;
            }
            switch (mode) {
                case GOAL_XYZ: {
                    int x = 0, y = 64, z = 0;
                    NodeParameter xParam = getParameter("X");
                    NodeParameter yParam = getParameter("Y");
                    NodeParameter zParam = getParameter("Z");

                    if (xParam != null) x = xParam.getIntValue();
                    if (yParam != null) y = yParam.getIntValue();
                    if (zParam != null) z = zParam.getIntValue();

                    String command = String.format("#goal %d %d %d", x, y, z);
                    executeCommand(command);
                    future.complete(null);
                    return;
                }
                case GOAL_XZ: {
                    int x = 0, z = 0;
                    NodeParameter xParam = getParameter("X");
                    NodeParameter zParam = getParameter("Z");

                    if (xParam != null) x = xParam.getIntValue();
                    if (zParam != null) z = zParam.getIntValue();

                    String command = String.format("#goal %d %d", x, z);
                    executeCommand(command);
                    future.complete(null);
                    return;
                }
                case GOAL_Y: {
                    int y = 64;
                    NodeParameter yParam = getParameter("Y");
                    if (yParam != null) y = yParam.getIntValue();

                    String command = String.format("#goal %d", y);
                    executeCommand(command);
                    future.complete(null);
                    return;
                }
                case GOAL_CURRENT: {
                    net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                    if (client != null && client.player != null) {
                        int currentX = (int) client.player.getX();
                        int currentY = (int) client.player.getY();
                        int currentZ = (int) client.player.getZ();
                        String command = String.format("#goal %d %d %d", currentX, currentY, currentZ);
                        executeCommand(command);
                    }
                    future.complete(null);
                    return;
                }
                case GOAL_CLEAR: {
                    executeCommand("#goal clear");
                    future.complete(null);
                    return;
                }
                default:
                    future.completeExceptionally(new RuntimeException("Unknown GOAL mode: " + mode));
                    return;
            }
        }

        Object baritone = getBaritone();
        if (baritone == null) {
            System.err.println("Baritone not available for goal command");
            future.completeExceptionally(new RuntimeException("Baritone not available"));
            return;
        }
        
        Object customGoalProcess = BaritoneApiProxy.getCustomGoalProcess(baritone);
        
        switch (mode) {
            case GOAL_XYZ:
                int x = 0, y = 64, z = 0;
                NodeParameter xParam = getParameter("X");
                NodeParameter yParam = getParameter("Y");
                NodeParameter zParam = getParameter("Z");
                
                if (xParam != null) x = xParam.getIntValue();
                if (yParam != null) y = yParam.getIntValue();
                if (zParam != null) z = zParam.getIntValue();
                
                System.out.println("Setting goal to: " + x + ", " + y + ", " + z);
                Object goal = BaritoneApiProxy.createGoalBlock(x, y, z);
                BaritoneApiProxy.setGoal(customGoalProcess, goal);
                break;
                
            case GOAL_XZ:
                int x2 = 0, z2 = 0;
                NodeParameter xParam2 = getParameter("X");
                NodeParameter zParam2 = getParameter("Z");
                
                if (xParam2 != null) x2 = xParam2.getIntValue();
                if (zParam2 != null) z2 = zParam2.getIntValue();
                
                System.out.println("Setting goal to: " + x2 + ", " + z2);
                Object goal2 = BaritoneApiProxy.createGoalBlock(x2, 0, z2); // Y will be determined by pathfinding
                BaritoneApiProxy.setGoal(customGoalProcess, goal2);
                break;
                
            case GOAL_Y:
                int y3 = 64;
                NodeParameter yParam3 = getParameter("Y");
                if (yParam3 != null) y3 = yParam3.getIntValue();
                
                System.out.println("Setting goal to Y level: " + y3);
                // For Y-only goal, we need to get current X,Z and set goal there
                net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                if (client != null && client.player != null) {
                    int currentX = (int) client.player.getX();
                    int currentZ = (int) client.player.getZ();
                    Object goal3 = BaritoneApiProxy.createGoalBlock(currentX, y3, currentZ);
                    BaritoneApiProxy.setGoal(customGoalProcess, goal3);
                }
                break;
                
            case GOAL_CURRENT:
                System.out.println("Setting goal to current position");
                net.minecraft.client.MinecraftClient client2 = net.minecraft.client.MinecraftClient.getInstance();
                if (client2 != null && client2.player != null) {
                    int currentX = (int) client2.player.getX();
                    int currentY = (int) client2.player.getY();
                    int currentZ = (int) client2.player.getZ();
                    Object goal4 = BaritoneApiProxy.createGoalBlock(currentX, currentY, currentZ);
                    BaritoneApiProxy.setGoal(customGoalProcess, goal4);
                }
                break;
                
            case GOAL_CLEAR:
                System.out.println("Clearing current goal");
                BaritoneApiProxy.setGoal(customGoalProcess, null);
                break;
                
            default:
                future.completeExceptionally(new RuntimeException("Unknown GOAL mode: " + mode));
                return;
        }
        
        // Goal setting is immediate, no need to wait
        future.complete(null);
    }
    
    private void executePathCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.of(ParameterUsage.POSITION), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }

        System.out.println("Executing path command");

        if (!isBaritoneApiAvailable() && isBaritoneModAvailable()) {
            if (runtimeParameterData != null && runtimeParameterData.targetBlockPos != null) {
                BlockPos target = runtimeParameterData.targetBlockPos;
                String goalCommand = String.format("#goal %d %d %d", target.getX(), target.getY(), target.getZ());
                executeCommand(goalCommand);
            }
            executeCommand("#path");
            future.complete(null);
            return;
        }

        Object baritone = getBaritone();
        if (baritone != null) {
            resetBaritonePathing(baritone);
            // Start precise tracking of this task
            PreciseCompletionTracker.getInstance().startTrackingTask(PreciseCompletionTracker.TASK_PATH, future);

            // Start the Baritone pathing task
            Object customGoalProcess = BaritoneApiProxy.getCustomGoalProcess(baritone);
            if (runtimeParameterData != null && runtimeParameterData.targetBlockPos != null) {
                BlockPos target = runtimeParameterData.targetBlockPos;
                BaritoneApiProxy.setGoal(customGoalProcess, BaritoneApiProxy.createGoalBlock(target.getX(), target.getY(), target.getZ()));
            }
            BaritoneApiProxy.path(customGoalProcess);

            // The future will be completed by the PreciseCompletionTracker when the path actually reaches the goal
        } else {
            System.err.println("Baritone not available for path command");
            future.completeExceptionally(new RuntimeException("Baritone not available"));
        }
    }
    
    private void executeStopCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        if (mode == null) {
            future.completeExceptionally(new RuntimeException("No mode set for STOP node"));
            return;
        }

        if (!isBaritoneApiAvailable() && isBaritoneModAvailable()) {
            PreciseCompletionTracker.getInstance().cancelAllTasks();
            String command;
            switch (mode) {
                case STOP_NORMAL:
                    command = "#stop";
                    break;
                case STOP_CANCEL:
                case STOP_FORCE:
                    command = "#cancel";
                    break;
                default:
                    future.completeExceptionally(new RuntimeException("Unknown STOP mode: " + mode));
                    return;
            }
            executeCommand(command);
            future.complete(null);
            return;
        }

        Object baritone = getBaritone();
        if (baritone == null) {
            System.err.println("Baritone not available for stop command");
            future.completeExceptionally(new RuntimeException("Baritone not available"));
            return;
        }
        
        switch (mode) {
            case STOP_NORMAL:
                System.out.println("Executing stop command");
                // Cancel all pending tasks first
                PreciseCompletionTracker.getInstance().cancelAllTasks();
                // Stop all Baritone processes
                BaritoneApiProxy.cancelEverything(BaritoneApiProxy.getPathingBehavior(baritone));
                break;
                
            case STOP_CANCEL:
                System.out.println("Executing cancel command");
                // Cancel all pending tasks first
                PreciseCompletionTracker.getInstance().cancelAllTasks();
                // Stop all Baritone processes
                BaritoneApiProxy.cancelEverything(BaritoneApiProxy.getPathingBehavior(baritone));
                break;
                
            case STOP_FORCE:
                System.out.println("Executing force cancel command");
                // Force cancel all tasks
                PreciseCompletionTracker.getInstance().cancelAllTasks();
                // Force stop all Baritone processes
                BaritoneApiProxy.cancelEverything(BaritoneApiProxy.getPathingBehavior(baritone));
                break;
                
            default:
                future.completeExceptionally(new RuntimeException("Unknown STOP mode: " + mode));
                return;
        }
        
        // Complete immediately since stop is immediate
        future.complete(null);
    }

    private void executeStopChainNode(CompletableFuture<Void> future) {
        Node owningStart = getOwningStartNode();
        ExecutionManager manager = ExecutionManager.getInstance();
        int targetNumber = getIntParameter("StartNumber", 0);

        if (targetNumber > 0) {
            boolean stopped = manager.requestStopForStartNumber(targetNumber);
            if (!stopped) {
                System.out.println("Stop node could not find START node " + targetNumber + ". Stopping all node trees.");
                manager.requestStopAll();
            }
            future.complete(null);
            return;
        }

        if (owningStart == null) {
            System.out.println("Stop node executed without owning START node. Stopping all node trees.");
            manager.requestStopAll();
        } else {
            System.out.println("Executing stop node for START node " + owningStart.getId());
            boolean stopped = manager.requestStopForStart(owningStart);
            if (!stopped) {
                System.out.println("Stop node could not cancel its owning START chain. Stopping all node trees.");
                manager.requestStopAll();
            }
        }

        future.complete(null);
    }

    private void executeStartChainNode(CompletableFuture<Void> future) {
        int targetNumber = getIntParameter("StartNumber", 0);
        if (targetNumber <= 0) {
            System.out.println("Activate node executed without a valid START number.");
            future.complete(null);
            return;
        }

        ExecutionManager manager = ExecutionManager.getInstance();
        boolean started = manager.requestStartForStartNumber(targetNumber);
        if (!started) {
            System.out.println("Activate node could not find START node " + targetNumber + ".");
        }

        future.complete(null);
    }

    private void executeStopAllNode(CompletableFuture<Void> future) {
        System.out.println("Executing stop all node");
        ExecutionManager.getInstance().requestStopAll();
        future.complete(null);
    }
    
    private void executeInvertCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        String command = "#invert";
        System.out.println("Executing command: " + command);

        executeCommand(command);
        future.complete(null); // Invert commands complete immediately
    }

    private void executeComeCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        String command = "#come";
        System.out.println("Executing command: " + command);

        executeCommand(command);
        future.complete(null); // These commands complete immediately
    }

    private void executeSurfaceCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        String command = "#surface";
        System.out.println("Executing command: " + command);

        executeCommand(command);
        future.complete(null); // These commands complete immediately
    }

    private void executeTunnelCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        String command = "#tunnel";
        System.out.println("Executing command: " + command);

        executeCommand(command);
        future.complete(null); // These commands complete immediately
    }
    
    private void executeFarmCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        if (mode == null) {
            future.completeExceptionally(new RuntimeException("No mode set for FARM node"));
            return;
        }

        if (!isBaritoneApiAvailable() && isBaritoneModAvailable()) {
            switch (mode) {
                case FARM_RANGE: {
                    int range = 10;
                    NodeParameter rangeParam = getParameter("Range");
                    if (rangeParam != null) {
                        range = rangeParam.getIntValue();
                    }
                    executeCommand("#farm " + range);
                    future.complete(null);
                    return;
                }
                case FARM_WAYPOINT: {
                    String waypoint = "farm";
                    int waypointRange = 10;
                    NodeParameter waypointParam = getParameter("Waypoint");
                    NodeParameter waypointRangeParam = getParameter("Range");

                    if (waypointParam != null) {
                        waypoint = waypointParam.getStringValue();
                    }
                    if (waypointRangeParam != null) {
                        waypointRange = waypointRangeParam.getIntValue();
                    }
                    executeCommand("#farm " + waypoint + " " + waypointRange);
                    future.complete(null);
                    return;
                }
                default:
                    future.completeExceptionally(new RuntimeException("Unknown FARM mode: " + mode));
                    return;
            }
        }

        Object baritone = getBaritone();
        if (baritone == null) {
            System.err.println("Baritone not available for farm command");
            future.completeExceptionally(new RuntimeException("Baritone not available"));
            return;
        }
        
        Object farmProcess = BaritoneApiProxy.getFarmProcess(baritone);
        PreciseCompletionTracker.getInstance().startTrackingTask(PreciseCompletionTracker.TASK_FARM, future);
        
        switch (mode) {
            case FARM_RANGE:
                int range = 10;
                NodeParameter rangeParam = getParameter("Range");
                if (rangeParam != null) {
                    range = rangeParam.getIntValue();
                }
                
                System.out.println("Executing farm within range: " + range);
                BaritoneApiProxy.farm(farmProcess, range);
                break;
                
            case FARM_WAYPOINT:
                String waypoint = "farm";
                int waypointRange = 10;
                NodeParameter waypointParam = getParameter("Waypoint");
                NodeParameter waypointRangeParam = getParameter("Range");
                
                if (waypointParam != null) {
                    waypoint = waypointParam.getStringValue();
                }
                if (waypointRangeParam != null) {
                    waypointRange = waypointRangeParam.getIntValue();
                }
                
                System.out.println("Executing farm around waypoint: " + waypoint + " with range: " + waypointRange);
                // For waypoint-based farming, we need to use a different approach
                executeCommand("#farm " + waypoint + " " + waypointRange);
                future.complete(null); // Command-based farming completes immediately
                return;
                
            default:
                future.completeExceptionally(new RuntimeException("Unknown FARM mode: " + mode));
                return;
        }
    }
    
    private void executeHotbarCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || client.player.networkHandler == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        PlayerInventory inventory = client.player.getInventory();
        String itemId = getStringParameter("Item", "").trim();
        int slot;

        if (!itemId.isEmpty()) {
            List<String> itemIds = splitMultiValueList(itemId);
            int foundSlot = -1;
            for (String candidateId : itemIds) {
                String sanitized = sanitizeResourceId(candidateId);
                String normalized = sanitized != null && !sanitized.isEmpty()
                    ? normalizeResourceId(sanitized, "minecraft")
                    : candidateId;
                Identifier identifier = Identifier.tryParse(normalized);
                if (identifier == null || !Registries.ITEM.containsId(identifier)) {
                    continue;
                }
                Item targetItem = Registries.ITEM.get(identifier);
                foundSlot = findHotbarSlotWithItem(inventory, targetItem);
                if (foundSlot != -1) {
                    break;
                }
            }
            if (foundSlot == -1) {
                sendNodeErrorMessage(client, "No matching item found in your hotbar.");
                future.complete(null);
                return;
            }
            slot = foundSlot;
        } else {
            slot = MathHelper.clamp(getIntParameter("Slot", 0), 0, 8);
        }

        PlayerInventoryBridge.setSelectedSlot(client.player.getInventory(), slot);
        client.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        future.complete(null);
    }
    
    private void executeDropItemCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        boolean dropAll = getBooleanParameter("All", false);
        int count = Math.max(1, getIntParameter("Count", 1));
        double interval = Math.max(0.0, getDoubleParameter("IntervalSeconds", 0.0));

        if (dropAll) {
            count = 1; // Dropping all ignores repeat count
        }

        final int dropIterations = count;
        final boolean dropEntireStack = dropAll;

        new Thread(() -> {
            try {
                for (int i = 0; i < dropIterations; i++) {
                    runOnClientThread(client, () -> {
                        client.player.dropSelectedItem(dropEntireStack);
                        client.player.getInventory().markDirty();
                        client.player.playerScreenHandler.sendContentUpdates();
                    });

                    if (interval > 0.0 && i < dropIterations - 1) {
                        Thread.sleep((long) (interval * 1000));
                    }
                }
                future.complete(null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.completeExceptionally(e);
            }
        }, "Pathmind-DropItem").start();
    }
    
    private void executeDropSlotCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }
        
        PlayerInventory inventory = client.player.getInventory();
        int slot = clampInventorySlot(inventory, getIntParameter("Slot", 0));
        boolean entireStack = getBooleanParameter("EntireStack", true);
        int requestedCount = getIntParameter("Count", 0);
        
        ItemStack stack = inventory.getStack(slot);
        if (stack.isEmpty()) {
            future.complete(null);
            return;
        }
        
        ItemStack removed;
        if (entireStack || requestedCount <= 0 || requestedCount >= stack.getCount()) {
            removed = inventory.removeStack(slot);
        } else {
            removed = inventory.removeStack(slot, requestedCount);
        }
        
        if (!removed.isEmpty()) {
            client.player.dropItem(removed, true);
        }
        
        inventory.markDirty();
        client.player.playerScreenHandler.sendContentUpdates();
        future.complete(null);
    }
    
    private void executeMoveItemCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        ClientPlayerInteractionManager interactionManager = client.interactionManager;
        ScreenHandler handler = client.player.currentScreenHandler;
        if (interactionManager == null || handler == null) {
            future.completeExceptionally(new RuntimeException("Interaction manager unavailable"));
            return;
        }

        if (!handler.getCursorStack().isEmpty()) {
            sendNodeErrorMessage(client, "Cannot move items while the cursor is holding another stack.");
            future.complete(null);
            return;
        }

        PlayerInventory inventory = client.player.getInventory();
        int requestedSourceSlot = getIntParameter("SourceSlot", 0);
        int requestedTargetSlot = getIntParameter("TargetSlot", 0);

        SlotSelectionType sourceSelection = resolveInventorySlotSelectionType(0);
        SlotSelectionType targetSelection = resolveInventorySlotSelectionType(1);
        boolean shiftClickTarget = false;
        Node targetParameterNode = getAttachedParameter(1);
        GuiSelectionMode targetGuiMode = null;
        if (targetParameterNode != null && targetParameterNode.getType() == NodeType.PARAM_GUI) {
            shiftClickTarget = true;
            targetGuiMode = GuiSelectionMode.fromId(getParameterString(targetParameterNode, "GUI"));
        }

        if (shiftClickTarget && targetGuiMode != null) {
            Node sourceParameterNode = getAttachedParameter(0);
            SlotSelectionType desiredSourceSelection = targetGuiMode == GuiSelectionMode.PLAYER_INVENTORY
                ? SlotSelectionType.GUI_CONTAINER
                : SlotSelectionType.PLAYER_INVENTORY;
            if (sourceParameterNode != null && sourceParameterNode.getType() == NodeType.PARAM_ITEM) {
                if (!resolveMoveItemSlotFromItemParameter(sourceParameterNode, 0, desiredSourceSelection, future)) {
                    return;
                }
                requestedSourceSlot = getIntParameter("SourceSlot", 0);
            }
            sourceSelection = desiredSourceSelection;
        }

        SlotResolution sourceResolution = resolveInventorySlot(handler, inventory, requestedSourceSlot, sourceSelection);
        SlotResolution targetResolution = shiftClickTarget
            ? null
            : resolveInventorySlot(handler, inventory, requestedTargetSlot, targetSelection);

        if (sourceResolution == null || (!shiftClickTarget && targetResolution == null)) {
            future.complete(null);
            return;
        }

        if (!shiftClickTarget && sourceResolution.handlerSlotIndex == targetResolution.handlerSlotIndex) {
            future.complete(null);
            return;
        }

        ItemStack sourceStack = sourceResolution.slot.getStack();
        if (sourceStack.isEmpty()) {
            future.complete(null);
            return;
        }

        int requestedCount = getIntParameter("Count", 0);
        int available = sourceStack.getCount();
        int moveCount = requestedCount <= 0 ? available : Math.min(requestedCount, available);
        if (moveCount <= 0) {
            future.complete(null);
            return;
        }

        boolean moveEntireStack = moveCount >= available;
        if (shiftClickTarget) {
            interactionManager.clickSlot(
                handler.syncId,
                sourceResolution.handlerSlotIndex,
                0,
                SlotActionType.QUICK_MOVE,
                client.player
            );
        } else {
            performInventoryTransfer(
                interactionManager,
                handler,
                client.player,
                sourceResolution.handlerSlotIndex,
                targetResolution.handlerSlotIndex,
                moveCount,
                moveEntireStack
            );
        }

        inventory.markDirty();
        client.player.playerScreenHandler.sendContentUpdates();
        future.complete(null);
    }

    private void performInventoryTransfer(ClientPlayerInteractionManager interactionManager, ScreenHandler handler,
                                          PlayerEntity player, int sourceSlot, int targetSlot, int moveCount, boolean moveEntireStack) {
        if (moveEntireStack) {
            interactionManager.clickSlot(handler.syncId, sourceSlot, 0, SlotActionType.PICKUP, player);
            interactionManager.clickSlot(handler.syncId, targetSlot, 0, SlotActionType.PICKUP, player);
            interactionManager.clickSlot(handler.syncId, sourceSlot, 0, SlotActionType.PICKUP, player);
            return;
        }

        interactionManager.clickSlot(handler.syncId, sourceSlot, 0, SlotActionType.PICKUP, player);
        int moved = 0;
        while (moved < moveCount) {
            int beforeCursor = handler.getCursorStack().getCount();
            interactionManager.clickSlot(handler.syncId, targetSlot, 1, SlotActionType.PICKUP, player);
            int afterCursor = handler.getCursorStack().getCount();
            if (afterCursor >= beforeCursor) {
                break;
            }
            moved++;
        }
        interactionManager.clickSlot(handler.syncId, sourceSlot, 0, SlotActionType.PICKUP, player);
    }

    private SlotSelectionType resolveInventorySlotSelectionType(int parameterSlotIndex) {
        Node parameterNode = getAttachedParameter(parameterSlotIndex);
        return resolveInventorySlotSelectionType(parameterNode);
    }

    private SlotSelectionType resolveInventorySlotSelectionType(Node parameterNode) {
        if (parameterNode == null) {
            return SlotSelectionType.PLAYER_INVENTORY;
        }

        // For item parameters, check if a container GUI is open
        if (parameterNode.getType() == NodeType.PARAM_ITEM) {
            // Check if there's a mode specified on the item parameter
            String modeValue = getParameterString(parameterNode, "Mode");
            if (modeValue != null && !modeValue.isEmpty()) {
                Boolean isPlayer = InventorySlotModeHelper.extractPlayerSelectionFlag(modeValue);
                if (isPlayer != null) {
                    return isPlayer ? SlotSelectionType.PLAYER_INVENTORY : SlotSelectionType.GUI_CONTAINER;
                }
                String modeId = InventorySlotModeHelper.extractModeId(modeValue);
                if (modeId != null && !modeId.isEmpty() && !"player_inventory".equals(modeId)) {
                    return SlotSelectionType.GUI_CONTAINER;
                }
            }

            // If no mode specified, detect based on open screen
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            if (client != null && client.player != null) {
                ScreenHandler handler = client.player.currentScreenHandler;
                // If a container is open (not just the player inventory screen)
                if (handler != null && !(handler instanceof PlayerScreenHandler)) {
                    return SlotSelectionType.GUI_CONTAINER;
                }
            }
            return SlotSelectionType.PLAYER_INVENTORY;
        }

        if (parameterNode.getType() == NodeType.PARAM_GUI) {
            return SlotSelectionType.GUI_CONTAINER;
        }

        if (parameterNode.getType() != NodeType.PARAM_INVENTORY_SLOT) {
            return SlotSelectionType.PLAYER_INVENTORY;
        }

        String modeValue = getParameterString(parameterNode, "Mode");
        Boolean isPlayer = InventorySlotModeHelper.extractPlayerSelectionFlag(modeValue);
        if (isPlayer != null) {
            return isPlayer ? SlotSelectionType.PLAYER_INVENTORY : SlotSelectionType.GUI_CONTAINER;
        }
        String modeId = InventorySlotModeHelper.extractModeId(modeValue);
        if (modeId != null && !modeId.isEmpty() && !"player_inventory".equals(modeId)) {
            return SlotSelectionType.GUI_CONTAINER;
        }
        return SlotSelectionType.PLAYER_INVENTORY;
    }

    private SlotResolution resolveInventorySlot(ScreenHandler handler, PlayerInventory inventory, int slotValue, SlotSelectionType selectionType) {
        if (handler == null) {
            return null;
        }
        if (selectionType == SlotSelectionType.GUI_CONTAINER) {
            if (slotValue < 0 || slotValue >= handler.slots.size()) {
                return null;
            }
            Slot slot = handler.getSlot(slotValue);
            if (slot == null) {
                return null;
            }
            return new SlotResolution(slot, slotValue);
        }
        if (inventory == null) {
            return null;
        }
        int clamped = clampInventorySlot(inventory, slotValue);
        int handlerSlot = mapPlayerInventorySlot(handler, clamped);
        if (handlerSlot < 0 || handlerSlot >= handler.slots.size()) {
            return null;
        }
        Slot slot = handler.getSlot(handlerSlot);
        if (slot == null) {
            return null;
        }
        return new SlotResolution(slot, handlerSlot);
    }

    private enum SlotSelectionType {
        PLAYER_INVENTORY,
        GUI_CONTAINER
    }

    private static final class SlotResolution {
        final Slot slot;
        final int handlerSlotIndex;

        SlotResolution(Slot slot, int handlerSlotIndex) {
            this.slot = slot;
            this.handlerSlotIndex = handlerSlotIndex;
        }
    }

    private boolean resolveMoveItemSlotFromItemParameter(Node parameterNode, int slotIndex, CompletableFuture<Void> future) {
        SlotSelectionType selectionType = resolveInventorySlotSelectionType(slotIndex);
        return resolveMoveItemSlotFromItemParameter(parameterNode, slotIndex, selectionType, future);
    }

    private boolean resolveMoveItemSlotFromItemParameter(Node parameterNode, int slotIndex,
                                                         SlotSelectionType selectionType, CompletableFuture<Void> future) {
        if (slotIndex < 0 || slotIndex > 1) {
            return false;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            if (future != null && !future.isDone()) {
                future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            }
            return false;
        }

        List<String> itemIds = resolveItemIdsFromParameter(parameterNode);
        boolean anySelection = itemIds.isEmpty()
            && (isAnySelectionValue(getParameterString(parameterNode, "Item"))
                || isAnySelectionValue(getParameterString(parameterNode, "Items")));
        if (itemIds.isEmpty() && !anySelection) {
            sendParameterSearchFailure("No item selected on parameter for " + type.getDisplayName() + ".", future);
            return false;
        }

        ScreenHandler handler = client.player.currentScreenHandler;

        int foundSlot = -1;
        if (anySelection) {
            if (selectionType == SlotSelectionType.GUI_CONTAINER && handler != null) {
                for (int i = 0; i < handler.slots.size(); i++) {
                    Slot slot = handler.getSlot(i);
                    if (slot != null && !slot.getStack().isEmpty()) {
                        foundSlot = i;
                        break;
                    }
                }
            } else if (client.player != null) {
                foundSlot = findFirstNonEmptySlot(client.player.getInventory());
            }
        }
        for (String candidateId : itemIds) {
            Identifier identifier = Identifier.tryParse(candidateId);
            if (identifier == null || !Registries.ITEM.containsId(identifier)) {
                continue;
            }
            Item candidateItem = Registries.ITEM.get(identifier);

            if (selectionType == SlotSelectionType.GUI_CONTAINER && handler != null) {
                // Search through all handler slots
                for (int i = 0; i < handler.slots.size(); i++) {
                    Slot slot = handler.getSlot(i);
                    if (slot != null && !slot.getStack().isEmpty() && slot.getStack().isOf(candidateItem)) {
                        foundSlot = i;
                        break;
                    }
                }
            } else {
                // Search through player inventory
                int slot = findFirstSlotWithItem(client.player.getInventory(), candidateItem);
                if (slot >= 0) {
                    foundSlot = slot;
                }
            }

            if (foundSlot >= 0) {
                break;
            }
        }

        if (foundSlot < 0) {
            String reference = anySelection ? "item" : String.join(", ", itemIds);
            String locationDesc = (selectionType == SlotSelectionType.GUI_CONTAINER) ? "container" : "inventory";
            sendParameterSearchFailure("No " + reference + " found in " + locationDesc + " for " + type.getDisplayName() + ".", future);
            return false;
        }

        String targetParameter = slotIndex == 0 ? "SourceSlot" : "TargetSlot";
        setParameterValueAndPropagate(targetParameter, Integer.toString(foundSlot));
        if (slotIndex == 0) {
            if (runtimeParameterData == null) {
                runtimeParameterData = new RuntimeParameterData();
            }
            runtimeParameterData.slotIndex = foundSlot;
        }
        return true;
    }

    private boolean resolveUseParameterSelection(Node parameterNode, CompletableFuture<Void> future) {
        if (parameterNode == null) {
            return false;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            if (future != null && !future.isDone()) {
                future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            }
            return false;
        }
        if (runtimeParameterData == null) {
            runtimeParameterData = new RuntimeParameterData();
        }

        PlayerInventory inventory = client.player.getInventory();
        NodeType parameterType = parameterNode.getType();
        switch (parameterType) {
            case PARAM_ITEM: {
                List<String> itemIds = resolveItemIdsFromParameter(parameterNode);
                if (itemIds.isEmpty()) {
                    sendParameterSearchFailure("No item selected on parameter for " + type.getDisplayName() + ".", future);
                    return false;
                }
                ItemSearchResult result = findUseItemSlot(inventory, itemIds);
                if (result == null) {
                    String reference = String.join(", ", itemIds);
                    sendParameterSearchFailure("No " + reference + " found in inventory for " + type.getDisplayName() + ".", future);
                    return false;
                }
                runtimeParameterData.slotIndex = result.slotIndex();
                runtimeParameterData.slotSelectionType = SlotSelectionType.PLAYER_INVENTORY;
                runtimeParameterData.targetItem = result.item();
                runtimeParameterData.targetItemId = result.itemId();
                return true;
            }
            case PARAM_INVENTORY_SLOT: {
                SlotSelectionType selectionType = resolveInventorySlotSelectionType(parameterNode);
                if (selectionType == SlotSelectionType.GUI_CONTAINER) {
                    sendNodeErrorMessage(client, "Use node can only use player inventory slots.");
                    if (future != null && !future.isDone()) {
                        future.complete(null);
                    }
                    return false;
                }
                int slotValue = clampInventorySlot(inventory, parseNodeInt(parameterNode, "Slot", 0));
                runtimeParameterData.slotIndex = slotValue;
                runtimeParameterData.slotSelectionType = SlotSelectionType.PLAYER_INVENTORY;
                return true;
            }
            default:
                return false;
        }
    }

    private record ItemSearchResult(int slotIndex, Item item, String itemId) {
    }

    private ItemSearchResult findUseItemSlot(PlayerInventory inventory, List<String> itemIds) {
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

    private int findAccessibleSlotWithItem(PlayerInventory inventory, Item item) {
        if (inventory == null || item == null) {
            return -1;
        }
        for (int slot = 0; slot < PlayerInventory.MAIN_SIZE && slot < inventory.size(); slot++) {
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

    private int findFirstSlotWithItem(PlayerInventory inventory, Item item) {
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

    private int findFirstNonEmptySlot(PlayerInventory inventory) {
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
    
    private void executeUseCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || client.interactionManager == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        Hand hand = resolveHand(getParameter("Hand"), Hand.MAIN_HAND);
        int configuredCount = Math.max(0, getIntParameter("RepeatCount", 1));
        boolean useUntilEmpty = getBooleanParameter("UseUntilEmpty", false);
        boolean stopIfUnavailable = getBooleanParameter("StopIfUnavailable", true);
        double durationSeconds = Math.max(0.0, getDoubleParameter("UseDurationSeconds", 0.0));
        double intervalSeconds = Math.max(0.0, getDoubleParameter("UseIntervalSeconds", 0.0));
        boolean allowBlock = getBooleanParameter("AllowBlockInteraction", true);
        boolean allowEntity = getBooleanParameter("AllowEntityInteraction", true);
        boolean swingAfterUse = getBooleanParameter("SwingAfterUse", true);
        boolean sneakWhileUsing = getBooleanParameter("SneakWhileUsing", false);
        boolean restoreSneak = getBooleanParameter("RestoreSneakState", true);

        if (!useUntilEmpty && configuredCount == 0) {
            future.complete(null);
            return;
        }

        RuntimeParameterData parameterData = runtimeParameterData;
        if (parameterData != null && parameterData.slotIndex != null) {
            if (!prepareSelectedItemForUse(client, parameterData, hand, future)) {
                return;
            }
        }

        final int maxIterations = configuredCount == 0 ? Integer.MAX_VALUE : configuredCount;

        new Thread(() -> {
            try {
                boolean previousSneak = false;

                if (sneakWhileUsing) {
                    previousSneak = supplyFromClient(client, () -> client.player.isSneaking());
                }

                int iteration = 0;
                while (iteration < maxIterations) {
                    ItemStack stack = supplyFromClient(client, () -> client.player.getStackInHand(hand).copy());
                    if ((stack == null || stack.isEmpty()) && stopIfUnavailable) {
                        break;
                    }

                    if (sneakWhileUsing) {
                        runOnClientThread(client, () -> {
                            client.player.setSneaking(true);
                            if (client.options != null && client.options.sneakKey != null) {
                                client.options.sneakKey.setPressed(true);
                            }
                        });
                    }

                    runOnClientThread(client, () -> {
                        boolean performed = false;
                        HitResult target = client.crosshairTarget;
                        if (allowEntity && target instanceof EntityHitResult entityHit) {
                            ActionResult entityResult = client.interactionManager.interactEntity(client.player, entityHit.getEntity(), hand);
                            performed = entityResult.isAccepted();
                        }
                        if (!performed && allowBlock && target instanceof BlockHitResult blockHit) {
                            ActionResult blockResult = client.interactionManager.interactBlock(client.player, hand, blockHit);
                            performed = blockResult.isAccepted();
                        }
                        if (!performed) {
                            client.interactionManager.interactItem(client.player, hand);
                        }

                        if (durationSeconds > 0.0 && client.options != null && client.options.useKey != null) {
                            client.options.useKey.setPressed(true);
                        }

                        if (swingAfterUse) {
                            client.player.swingHand(hand);
                            if (client.player.networkHandler != null) {
                                client.player.networkHandler.sendPacket(new HandSwingC2SPacket(hand));
                            }
                        }
                    });

                    if (durationSeconds > 0.0) {
                        Thread.sleep((long) (durationSeconds * 1000));
                        runOnClientThread(client, () -> {
                            if (client.options != null && client.options.useKey != null) {
                                client.options.useKey.setPressed(false);
                            }
                        });
                    }

                    if (sneakWhileUsing && restoreSneak) {
                        boolean sneakState = previousSneak;
                        runOnClientThread(client, () -> {
                            client.player.setSneaking(sneakState);
                            if (client.options != null && client.options.sneakKey != null) {
                                client.options.sneakKey.setPressed(sneakState);
                            }
                        });
                    }

                    if (useUntilEmpty) {
                        ItemStack afterUse = supplyFromClient(client, () -> client.player.getStackInHand(hand).copy());
                        if (afterUse == null || afterUse.isEmpty()) {
                            break;
                        }
                    }

                    iteration++;
                    if (iteration >= maxIterations) {
                        break;
                    }

                    if (intervalSeconds > 0.0) {
                        Thread.sleep((long) (intervalSeconds * 1000));
                    }
                }

                future.complete(null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.completeExceptionally(e);
            }
        }, "Pathmind-Use").start();
    }

    private boolean prepareSelectedItemForUse(net.minecraft.client.MinecraftClient client,
                                              RuntimeParameterData parameterData,
                                              Hand hand,
                                              CompletableFuture<Void> future) {
        if (client == null || client.player == null || parameterData == null || parameterData.slotIndex == null) {
            return true;
        }
        if (parameterData.slotSelectionType == SlotSelectionType.GUI_CONTAINER) {
            sendNodeErrorMessage(client, "Use node cannot use items from GUI/container slots.");
            if (future != null && !future.isDone()) {
                future.complete(null);
            }
            return false;
        }
        PlayerInventory inventory = client.player.getInventory();
        int clampedSlot = clampInventorySlot(inventory, parameterData.slotIndex);
        boolean armorSlot = clampedSlot >= PlayerInventory.MAIN_SIZE
            && clampedSlot < PlayerInventory.MAIN_SIZE + PLAYER_ARMOR_SLOT_COUNT;
        if (armorSlot) {
            sendNodeErrorMessage(client, "Use node cannot activate armor slots.");
            if (future != null && !future.isDone()) {
                future.complete(null);
            }
            return false;
        }

        ItemStack stack = inventory.getStack(clampedSlot);
        if (stack.isEmpty()) {
            sendNodeErrorMessage(client, "Selected slot for " + type.getDisplayName() + " is empty.");
            if (future != null && !future.isDone()) {
                future.complete(null);
            }
            return false;
        }

        boolean prepared;
        if (hand == Hand.OFF_HAND) {
            prepared = ensureStackEquippedInOffhand(client, inventory, clampedSlot, stack);
        } else {
            prepared = ensureStackSelectedInMainHand(client, inventory, clampedSlot, stack);
        }

        if (!prepared) {
            sendNodeErrorMessage(client, "Failed to prepare selected item for " + type.getDisplayName() + ".");
            if (future != null && !future.isDone()) {
                future.complete(null);
            }
        }
        return prepared;
    }

    private boolean ensureStackSelectedInMainHand(net.minecraft.client.MinecraftClient client,
                                                  PlayerInventory inventory,
                                                  int slotIndex,
                                                  ItemStack stack) {
        if (client == null || client.player == null || inventory == null || stack == null) {
            return false;
        }
        int hotbarSize = PlayerInventory.getHotbarSize();
        int targetSlot = slotIndex;
        if (slotIndex >= hotbarSize) {
            targetSlot = moveInventoryStackToHotbar(client, inventory, slotIndex, stack.getItem());
            if (targetSlot == -1) {
                return false;
            }
        }
        try {
            PlayerInventoryBridge.setSelectedSlot(inventory, targetSlot);
        } catch (IllegalStateException ignored) {
            // Fall back to the packet-only update when inventory accessors are unavailable.
        }
        if (client.player.networkHandler != null) {
            client.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(targetSlot));
        }
        return true;
    }

    private boolean ensureStackEquippedInOffhand(net.minecraft.client.MinecraftClient client,
                                                 PlayerInventory inventory,
                                                 int slotIndex,
                                                 ItemStack stack) {
        if (client == null || client.player == null || inventory == null || stack == null) {
            return false;
        }
        int offhandIndex = getOffhandInventoryIndex(inventory);
        if (offhandIndex < 0) {
            return false;
        }
        if (slotIndex == offhandIndex) {
            return true;
        }
        if (slotIndex >= PlayerInventory.MAIN_SIZE) {
            return false;
        }
        ClientPlayerInteractionManager interactionManager = client.interactionManager;
        ScreenHandler handler = client.player.playerScreenHandler;
        if (interactionManager == null || handler == null) {
            return false;
        }
        int sourceHandlerSlot = mapPlayerInventorySlot(handler, slotIndex);
        int offhandHandlerSlot = mapPlayerInventorySlot(handler, offhandIndex);
        if (sourceHandlerSlot < 0 || offhandHandlerSlot < 0) {
            return false;
        }

        interactionManager.clickSlot(handler.syncId, sourceHandlerSlot, 0, SlotActionType.PICKUP, client.player);
        interactionManager.clickSlot(handler.syncId, offhandHandlerSlot, 0, SlotActionType.PICKUP, client.player);
        interactionManager.clickSlot(handler.syncId, sourceHandlerSlot, 0, SlotActionType.PICKUP, client.player);

        ItemStack offhandStack = client.player.getOffHandStack();
        return !offhandStack.isEmpty() && offhandStack.isOf(stack.getItem());
    }

    private void executePlaceHandCommand(CompletableFuture<Void> future) {
        Node blockParameterNode = getAttachedParameter(0);
        Node coordinateParameterNode = getAttachedParameter(1);
        boolean blockProvidesCoordinates = blockParameterProvidesPlacementCoordinates(blockParameterNode);
        boolean coordinateProvidesCoordinates = parameterProvidesCoordinates(coordinateParameterNode);
        boolean coordinateHandledByBlockParam = coordinateParameterNode == null && blockProvidesCoordinates;

        if (blockParameterNode != null) {
            EnumSet<ParameterUsage> blockUsages = coordinateHandledByBlockParam
                ? EnumSet.of(ParameterUsage.POSITION)
                : EnumSet.noneOf(ParameterUsage.class);
            if (preprocessParameterSlot(0, blockUsages, future, true) == ParameterHandlingResult.COMPLETE) {
                return;
            }
        } else {
            runtimeParameterData = null;
        }

        if (coordinateParameterNode != null) {
            EnumSet<ParameterUsage> coordinateUsages = coordinateProvidesCoordinates
                ? EnumSet.of(ParameterUsage.POSITION)
                : EnumSet.noneOf(ParameterUsage.class);
            if (preprocessParameterSlot(1, coordinateUsages, future, blockParameterNode == null) == ParameterHandlingResult.COMPLETE) {
                return;
            }
        }

        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || client.interactionManager == null || client.world == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        String inventorySlotBlockId = null;
        if (blockParameterNode != null && blockParameterNode.getType() == NodeType.PARAM_INVENTORY_SLOT) {
            inventorySlotBlockId = resolveBlockIdFromInventorySlotParameter(client, blockParameterNode);
            if (inventorySlotBlockId == null || inventorySlotBlockId.isEmpty()) {
                future.complete(null);
                return;
            }
        }

        Hand hand = resolveHand(getParameter("Hand"), Hand.MAIN_HAND);
        boolean sneakWhilePlacing = getBooleanParameter("SneakWhilePlacing", false);
        boolean restoreSneak = getBooleanParameter("RestoreSneakState", true);
        boolean swingOnPlace = getBooleanParameter("SwingOnPlace", true);
        boolean requireBlockHit = getBooleanParameter("RequireBlockHit", true);

        RuntimeParameterData parameterData = runtimeParameterData;
        BlockPos directedPlacementPos = null;
        if (parameterData != null && (coordinateProvidesCoordinates || coordinateHandledByBlockParam)) {
            directedPlacementPos = parameterData.targetBlockPos;
        }

        String parameterBlockId = resolveBlockIdFromParameterNode(blockParameterNode);
        if ((parameterBlockId == null || parameterBlockId.isEmpty()) && inventorySlotBlockId != null) {
            parameterBlockId = inventorySlotBlockId;
        }
        if ((parameterBlockId == null || parameterBlockId.isEmpty()) && parameterData != null) {
            if (parameterData.targetBlockId != null && !parameterData.targetBlockId.isEmpty()) {
                parameterBlockId = parameterData.targetBlockId;
            } else if (parameterData.targetBlockIds != null && !parameterData.targetBlockIds.isEmpty()) {
                parameterBlockId = parameterData.targetBlockIds.get(0);
            }
        }
        parameterBlockId = normalizePlacementBlockId(parameterBlockId);
        if (parameterBlockId != null && parameterBlockId.isEmpty()) {
            parameterBlockId = null;
        }

        if (directedPlacementPos != null) {
            handleDirectedPlaceHandPlacement(client, hand, parameterBlockId, directedPlacementPos, sneakWhilePlacing, restoreSneak, swingOnPlace, future);
            return;
        }

        if (parameterBlockId != null && !parameterBlockId.isEmpty()) {
            try {
                ensureBlockInHand(client, parameterBlockId, hand);
            } catch (PlacementFailure e) {
                sendNodeErrorMessage(client, e.getMessage());
                future.complete(null);
                return;
            }
        }

        boolean previousSneak = client.player.isSneaking();
        if (sneakWhilePlacing) {
            client.player.setSneaking(true);
            if (client.options != null && client.options.sneakKey != null) {
                client.options.sneakKey.setPressed(true);
            }
        }

        boolean placed = false;
        HitResult target = client.crosshairTarget;
        if (target instanceof BlockHitResult blockHit) {
            ActionResult result = client.interactionManager.interactBlock(client.player, hand, blockHit);
            placed = result.isAccepted();
            if (!placed && !requireBlockHit) {
                ActionResult fallback = client.interactionManager.interactItem(client.player, hand);
                placed = fallback.isAccepted();
            }
        } else if (!requireBlockHit) {
            ActionResult fallback = client.interactionManager.interactItem(client.player, hand);
            placed = fallback.isAccepted();
        }

        if (swingOnPlace && placed) {
            client.player.swingHand(hand);
            if (client.player.networkHandler != null) {
                client.player.networkHandler.sendPacket(new HandSwingC2SPacket(hand));
            }
        }

        if (sneakWhilePlacing && restoreSneak) {
            client.player.setSneaking(previousSneak);
            if (client.options != null && client.options.sneakKey != null) {
                client.options.sneakKey.setPressed(previousSneak);
            }
        }

        future.complete(null);
    }

    private void handleDirectedPlaceHandPlacement(
        net.minecraft.client.MinecraftClient client,
        Hand hand,
        String parameterBlockId,
        BlockPos targetPos,
        boolean sneakWhilePlacing,
        boolean restoreSneak,
        boolean swingOnPlace,
        CompletableFuture<Void> future
    ) {
        if (client == null || client.player == null || client.world == null || client.interactionManager == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        String blockIdToUse = parameterBlockId;
        if (blockIdToUse == null || blockIdToUse.isEmpty()) {
            blockIdToUse = getBlockIdFromHand(client, hand);
        }
        if (blockIdToUse == null || blockIdToUse.isEmpty()) {
            sendNodeErrorMessage(client, "Cannot place block: no block selected.");
            future.complete(null);
            return;
        }

        Block desiredBlock = resolveBlockForPlacement(blockIdToUse);
        if (desiredBlock == null) {
            sendNodeErrorMessage(client, "Cannot place block: unknown block \"" + blockIdToUse + "\".");
            future.complete(null);
            return;
        }

        double reachSquared = getPlacementReachSquared(client);
        final BlockPos placementPos = targetPos;
        final Block resolvedBlock = desiredBlock;
        final String resolvedBlockId = blockIdToUse;
        final Hand resolvedHand = hand;
        final boolean shouldSwing = swingOnPlace;
        final boolean shouldSneak = sneakWhilePlacing;
        final boolean shouldRestoreSneak = restoreSneak;

        new Thread(() -> {
            try {
                BlockHitResult placementHitResult = supplyFromClient(client, () ->
                    preparePlacementHitResult(client, placementPos, resolvedBlockId, resolvedHand, reachSquared)
                );
                runOnClientThread(client, () -> {
                    boolean initialSneak = client.player.isSneaking();
                    if (shouldSneak) {
                        client.player.setSneaking(true);
                        if (client.options != null && client.options.sneakKey != null) {
                            client.options.sneakKey.setPressed(true);
                        }
                    }
                    try {
                        if (client.world.getBlockState(placementPos).isOf(resolvedBlock)) {
                            return;
                        }
                        ActionResult result = client.interactionManager.interactBlock(client.player, resolvedHand, placementHitResult);
                        if (!result.isAccepted()) {
                            throw new PlacementFailure("Cannot place block at " + formatBlockPos(placementPos) + ": placement rejected (" + result + ").");
                        }
                        if (shouldSwing) {
                            client.player.swingHand(resolvedHand);
                            if (client.player.networkHandler != null) {
                                client.player.networkHandler.sendPacket(new HandSwingC2SPacket(resolvedHand));
                            }
                        }
                    } finally {
                        if (shouldSneak && shouldRestoreSneak) {
                            client.player.setSneaking(initialSneak);
                            if (client.options != null && client.options.sneakKey != null) {
                                client.options.sneakKey.setPressed(initialSneak);
                            }
                        }
                    }
                });
                boolean placed = waitForBlockPlacement(client, placementPos, resolvedBlock);
                if (!placed) {
                    sendNodeErrorMessage(client, "Attempted to place block \"" + resolvedBlockId + "\" at " + formatBlockPos(placementPos) + " but it did not appear. Make sure the space is clear and within reach.");
                }
                future.complete(null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.completeExceptionally(e);
            } catch (PlacementFailure e) {
                sendNodeErrorMessage(client, e.getMessage());
                future.complete(null);
            } catch (RuntimeException e) {
                sendNodeErrorMessage(client, "Failed to place block \"" + resolvedBlockId + "\": " + e.getMessage());
                future.complete(null);
            }
        }, "Pathmind-PlaceHand").start();
    }

    private void ensureBlockInHand(net.minecraft.client.MinecraftClient client, String blockId, Hand hand) {
        if (blockId == null || blockId.isEmpty()) {
            return;
        }

        Identifier identifier = BlockSelection.extractBlockIdentifier(blockId);
        if (identifier == null || !Registries.ITEM.containsId(identifier)) {
            throw new PlacementFailure("Cannot place block \"" + blockId + "\": unknown block item.");
        }

        Item targetItem = Registries.ITEM.get(identifier);
        ItemStack current = client.player.getStackInHand(hand);
        if (!current.isEmpty() && current.isOf(targetItem)) {
            return;
        }

        PlayerInventory inventory = client.player.getInventory();
        int slot = findHotbarSlotWithItem(inventory, targetItem);
        if (slot == -1) {
            int inventorySlot = findMainInventorySlotWithItem(inventory, targetItem);
            if (inventorySlot == -1) {
                boolean elsewhere = inventory.contains(new ItemStack(targetItem));
                if (elsewhere) {
                    throw new PlacementFailure("Cannot place block \"" + blockId + "\": the only available items are equipped or otherwise unavailable.");
                }
                throw new PlacementFailure("Cannot place block \"" + blockId + "\": none available in your inventory.");
            }

            slot = moveInventoryStackToHotbar(client, inventory, inventorySlot, targetItem);
            if (slot == -1) {
                throw new PlacementFailure("Cannot place block \"" + blockId + "\": failed to move it into your hotbar.");
            }
        }

        if (hand == Hand.MAIN_HAND) {
            try {
                PlayerInventoryBridge.setSelectedSlot(inventory, slot);
            } catch (IllegalStateException ignored) {
                // Fall back to the packet-only update when inventory accessors are unavailable.
            }
            if (client.player.networkHandler != null) {
                client.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            }
            return;
        }

        ItemStack offhandStack = client.player.getOffHandStack();
        if (!offhandStack.isEmpty() && offhandStack.isOf(targetItem)) {
            return;
        }

        PlayerInventoryBridge.setSelectedSlot(inventory, slot);
        if (client.player.networkHandler != null) {
            client.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }
    }

    private boolean waitForBlockPlacement(net.minecraft.client.MinecraftClient client, BlockPos targetPos, Block desiredBlock) throws InterruptedException {
        if (client == null || targetPos == null || desiredBlock == null) {
            return false;
        }
        for (int attempt = 0; attempt < 20; attempt++) {
            boolean matches = supplyFromClient(client, () -> {
                if (client.world == null) {
                    return false;
                }
                return client.world.getBlockState(targetPos).isOf(desiredBlock);
            });
            if (matches) {
                return true;
            }
            Thread.sleep(50L);
        }
        return false;
    }

    private int findHotbarSlotWithItem(PlayerInventory inventory, Item targetItem) {
        int hotbarSize = PlayerInventory.getHotbarSize();
        for (int slot = 0; slot < hotbarSize; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!stack.isEmpty() && stack.isOf(targetItem)) {
                return slot;
            }
        }
        return -1;
    }

    private int findMainInventorySlotWithItem(PlayerInventory inventory, Item targetItem) {
        if (inventory == null || targetItem == null) {
            return -1;
        }
        int hotbarSize = PlayerInventory.getHotbarSize();
        for (int slot = hotbarSize; slot < PlayerInventory.MAIN_SIZE; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!stack.isEmpty() && stack.isOf(targetItem)) {
                return slot;
            }
        }
        return -1;
    }

    private int findEmptyHotbarSlot(PlayerInventory inventory) {
        if (inventory == null) {
            return -1;
        }
        int hotbarSize = PlayerInventory.getHotbarSize();
        for (int slot = 0; slot < hotbarSize; slot++) {
            if (inventory.getStack(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    private int moveInventoryStackToHotbar(net.minecraft.client.MinecraftClient client, PlayerInventory inventory, int inventorySlot, Item targetItem) {
        if (client == null || client.player == null || client.interactionManager == null) {
            return -1;
        }
        ScreenHandler handler = client.player.currentScreenHandler;
        if (handler == null) {
            return -1;
        }

        int targetHotbarSlot = findEmptyHotbarSlot(inventory);
        if (targetHotbarSlot == -1) {
            try {
                targetHotbarSlot = PlayerInventoryBridge.getSelectedSlot(inventory);
            } catch (IllegalStateException ignored) {
                targetHotbarSlot = 0;
            }
        }

        int handlerSlot = mapPlayerInventorySlot(handler, inventorySlot);
        if (handlerSlot < 0) {
            return -1;
        }

        client.interactionManager.clickSlot(handler.syncId, handlerSlot, targetHotbarSlot, SlotActionType.SWAP, client.player);

        ItemStack hotbarStack = inventory.getStack(targetHotbarSlot);
        if (hotbarStack.isEmpty() || !hotbarStack.isOf(targetItem)) {
            return -1;
        }
        return targetHotbarSlot;
    }

    private BlockHitResult preparePlacementHitResult(net.minecraft.client.MinecraftClient client, BlockPos targetPos, String blockId, Hand hand, double reachSquared) {
        if (client.player == null || client.world == null) {
            throw new PlacementFailure("Cannot place block at " + formatBlockPos(targetPos) + ": client world is unavailable.");
        }

        Vec3d eyePos = client.player.getEyePos();
        Vec3d targetCenter = Vec3d.ofCenter(targetPos);
        if (eyePos.squaredDistanceTo(targetCenter) > reachSquared) {
            throw new PlacementFailure("Cannot place block at " + formatBlockPos(targetPos) + ": target is out of reach.");
        }

        if (!isBlockReplaceable(client.world, targetPos)) {
            BlockState occupied = client.world.getBlockState(targetPos);
            throw new PlacementFailure(
                "Cannot place block at " + formatBlockPos(targetPos) + ": target space contains " + describeBlockState(occupied) + "."
            );
        }

        BlockHitResult surface = createPlacementHitResult(client, targetPos, eyePos, reachSquared);
        if (surface == null) {
            throw new PlacementFailure("Cannot place block at " + formatBlockPos(targetPos) + ": no nearby surface to place against.");
        }

        ensureBlockInHand(client, blockId, hand);

        ItemStack stack = client.player.getStackInHand(hand);
        if (stack.isEmpty()) {
            throw new PlacementFailure("Cannot place block \"" + blockId + "\": the selected hand is empty.");
        }

        Item heldItem = stack.getItem();
        if (!(heldItem instanceof BlockItem blockItem)) {
            throw new PlacementFailure("Cannot place block \"" + blockId + "\": the selected item cannot be placed as a block.");
        }

        if (!canPlaceBlockAt(client, hand, stack, blockItem, surface)) {
            throw new PlacementFailure(
                "Cannot place block at " + formatBlockPos(targetPos) + ": the location is obstructed or lacks support."
            );
        }

        return surface;
    }

    private BlockHitResult createPlacementHitResult(net.minecraft.client.MinecraftClient client, BlockPos targetPos, Vec3d eyePos, double reachSquared) {
        if (client.player == null || client.world == null) {
            return null;
        }

        BlockHitResult bestResult = null;
        double bestDistance = Double.MAX_VALUE;

        for (Direction direction : Direction.values()) {
            BlockPos supportPos = targetPos.offset(direction);
            BlockState supportState = client.world.getBlockState(supportPos);
            if (supportState.isAir()) {
                continue;
            }
            if (supportState.getCollisionShape(client.world, supportPos).isEmpty()) {
                continue;
            }

            Direction placementSide = direction.getOpposite();
            Vec3d faceCenter = Vec3d.ofCenter(supportPos).add(
                placementSide.getOffsetX() * 0.5D,
                placementSide.getOffsetY() * 0.5D,
                placementSide.getOffsetZ() * 0.5D
            );

            Vec3d faceAxisA;
            Vec3d faceAxisB;
            switch (placementSide.getAxis()) {
                case X -> {
                    faceAxisA = FACE_AXIS_Y;
                    faceAxisB = FACE_AXIS_Z;
                }
                case Y -> {
                    faceAxisA = FACE_AXIS_X;
                    faceAxisB = FACE_AXIS_Z;
                }
                default -> {
                    faceAxisA = FACE_AXIS_X;
                    faceAxisB = FACE_AXIS_Y;
                }
            }

            Vec3d placementNormal = Vec3d.of(placementSide.getVector());

            for (double offsetA : FACE_OFFSET_SAMPLES) {
                for (double offsetB : FACE_OFFSET_SAMPLES) {
                    Vec3d samplePoint = faceCenter
                        .add(faceAxisA.multiply(offsetA))
                        .add(faceAxisB.multiply(offsetB));
                    double distance = eyePos.squaredDistanceTo(samplePoint);
                    if (distance > reachSquared) {
                        continue;
                    }

                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestResult = new BlockHitResult(
                            samplePoint.subtract(placementNormal.multiply(0.001D)),
                            placementSide,
                            supportPos,
                            false
                        );
                    }
                }
            }
        }
        return bestResult;
    }

    private static final double[] FACE_OFFSET_SAMPLES = {0.0D, 0.32D, -0.32D, 0.48D, -0.48D};
    private static final Vec3d FACE_AXIS_X = new Vec3d(1.0D, 0.0D, 0.0D);
    private static final Vec3d FACE_AXIS_Y = new Vec3d(0.0D, 1.0D, 0.0D);
    private static final Vec3d FACE_AXIS_Z = new Vec3d(0.0D, 0.0D, 1.0D);

    private boolean canPlaceBlockAt(net.minecraft.client.MinecraftClient client, Hand hand, ItemStack stack, BlockItem blockItem, BlockHitResult hitResult) {
        if (client.player == null || client.world == null) {
            return false;
        }

        ItemPlacementContext placementContext = new ItemPlacementContext(client.player, hand, stack.copy(), hitResult);
        if (!placementContext.canPlace()) {
            return false;
        }

        Block block = blockItem.getBlock();
        BlockState placementState = block.getPlacementState(placementContext);
        if (placementState == null) {
            return false;
        }

        return placementState.canPlaceAt(client.world, placementContext.getBlockPos());
    }

    private String sanitizeResourceId(String value) {
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
        String sanitized = UNSAFE_RESOURCE_ID_PATTERN.matcher(lower).replaceAll("");
        int firstColon = sanitized.indexOf(':');
        if (firstColon != -1) {
            int nextColon = sanitized.indexOf(':', firstColon + 1);
            if (nextColon != -1) {
                sanitized = sanitized.substring(0, firstColon + 1) + sanitized.substring(firstColon + 1).replace(':', '_');
            }
        }
        return sanitized;
    }

    private String normalizeResourceId(String value, String defaultNamespace) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (!trimmed.contains(":")) {
            return defaultNamespace + ":" + trimmed;
        }
        return trimmed;
    }

    private static String formatBlockPos(BlockPos pos) {
        if (pos == null) {
            return "(unknown)";
        }
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private Block resolveBlockForPlacement(String blockId) {
        if (blockId == null || blockId.isEmpty()) {
            return null;
        }

        Identifier identifier = BlockSelection.extractBlockIdentifier(blockId);
        if (identifier == null || !Registries.BLOCK.containsId(identifier)) {
            return null;
        }

        return Registries.BLOCK.get(identifier);
    }

    private double getPlacementReachSquared(net.minecraft.client.MinecraftClient client) {
        return DEFAULT_REACH_DISTANCE_SQUARED;
    }

    private String describeBlockState(BlockState state) {
        if (state == null) {
            return "an unknown block";
        }
        Identifier id = Registries.BLOCK.getId(state.getBlock());
        return id != null ? id.toString() : "an unknown block";
    }

    private boolean isBlockReplaceable(net.minecraft.world.World world, BlockPos targetPos) {
        BlockState state = world.getBlockState(targetPos);
        if (state.isAir()) {
            return true;
        }

        if (!state.getFluidState().isEmpty()) {
            return true;
        }

        return state.getCollisionShape(world, targetPos).isEmpty();
    }

    private boolean hasPlacementSupport(net.minecraft.world.World world, BlockPos targetPos) {
        if (world == null || targetPos == null) {
            return false;
        }
        for (Direction direction : Direction.values()) {
            BlockPos supportPos = targetPos.offset(direction);
            BlockState supportState = world.getBlockState(supportPos);
            if (!supportState.isAir() && !supportState.getCollisionShape(world, supportPos).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void executeLookCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.of(ParameterUsage.LOOK_ORIENTATION, ParameterUsage.POSITION), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }
        
        float yaw = (float) getDoubleParameter("Yaw", client.player.getYaw());
        float pitch = MathHelper.clamp((float) getDoubleParameter("Pitch", client.player.getPitch()), -90.0F, 90.0F);
        client.player.setYaw(yaw);
        client.player.setPitch(pitch);
        client.player.setHeadYaw(yaw);
        future.complete(null);
    }

    private void executeWalkCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.of(ParameterUsage.LOOK_ORIENTATION), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        double durationSeconds = Math.max(0.0, getDoubleParameter("Duration", 1.0));
        double distance = Math.max(0.0, getDoubleParameter("Distance", 0.0));
        boolean useDistance = distance > 0.0;

        if (!useDistance && durationSeconds <= 0.0) {
            future.complete(null);
            return;
        }

        new Thread(() -> {
            try {
                runOnClientThread(client, () -> {
                    orientPlayerTowardsRuntimeTarget(client, runtimeParameterData);
                    if (client.options != null && client.options.forwardKey != null) {
                        client.options.forwardKey.setPressed(true);
                    }
                });

                if (useDistance) {
                    Vec3d startPos = supplyFromClient(client,
                        () -> client.player != null ? EntityCompatibilityBridge.getPos(client.player) : null);
                    if (startPos != null) {
                        double targetDistanceSquared = distance * distance;
                        while (true) {
                            Thread.sleep(50L);
                            Vec3d currentPos = supplyFromClient(client,
                                () -> client.player != null ? EntityCompatibilityBridge.getPos(client.player) : null);
                            if (currentPos == null) {
                                break;
                            }
                            double dx = currentPos.x - startPos.x;
                            double dz = currentPos.z - startPos.z;
                            if ((dx * dx + dz * dz) >= targetDistanceSquared) {
                                break;
                            }
                        }
                    }
                } else {
                    Thread.sleep((long) (durationSeconds * 1000));
                }

                future.complete(null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.completeExceptionally(e);
            } finally {
                try {
                    runOnClientThread(client, () -> {
                        if (client.options != null && client.options.forwardKey != null) {
                            client.options.forwardKey.setPressed(false);
                        }
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "Pathmind-Walk").start();
    }

    private void executeJumpCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        client.player.jump();
        future.complete(null);
    }
    
    private void executeCrouchCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }
        
        boolean active = getBooleanParameter("Active", true);
        boolean toggleKey = getBooleanParameter("ToggleKey", false);
        double durationSeconds = Math.max(0.0, getDoubleParameter("DurationSeconds", 0.0));

        if (durationSeconds <= 0.0) {
            applyCrouchState(client, active, toggleKey);
            future.complete(null);
            return;
        }

        boolean previousSneak = client.player.isSneaking();
        new Thread(() -> {
            try {
                runOnClientThread(client, () -> applyCrouchState(client, active, toggleKey));
                if (durationSeconds > 0.0) {
                    Thread.sleep((long) (durationSeconds * 1000));
                }
                if (previousSneak != active) {
                    runOnClientThread(client, () -> applyCrouchState(client, previousSneak, toggleKey));
                }
                future.complete(null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.completeExceptionally(e);
            }
        }, "Pathmind-Crouch").start();
    }

    private void applyCrouchState(net.minecraft.client.MinecraftClient client, boolean active, boolean toggleKey) {
        if (client == null || client.player == null) {
            return;
        }
        client.player.setSneaking(active);
        if (client.options != null && client.options.sneakKey != null) {
            if (toggleKey) {
                client.options.sneakKey.setPressed(true);
                client.options.sneakKey.setPressed(false);
            } else {
                client.options.sneakKey.setPressed(active);
            }
        }
    }

    private void executeSprintCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }
        
        boolean active = getBooleanParameter("Active", true);
        boolean allowFlying = getBooleanParameter("AllowFlying", false);

        if (!allowFlying && client.player.getAbilities() != null && client.player.getAbilities().flying) {
            future.complete(null);
            return;
        }

        boolean previous = client.player.isSprinting();
        client.player.setSprinting(active);
        if (client.player.networkHandler != null && previous != active) {
            ClientCommandC2SPacket.Mode mode = active ? ClientCommandC2SPacket.Mode.START_SPRINTING : ClientCommandC2SPacket.Mode.STOP_SPRINTING;
            client.player.networkHandler.sendPacket(new ClientCommandC2SPacket(client.player, mode));
        }
        future.complete(null);
    }
    
    private void executeInteractCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.of(ParameterUsage.POSITION), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || client.interactionManager == null || client.world == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }
        
        Hand hand = resolveHand(getParameter("Hand"), Hand.MAIN_HAND);
        boolean preferEntity = getBooleanParameter("PreferEntity", true);
        boolean preferBlock = getBooleanParameter("PreferBlock", true);
        boolean fallbackToItem = getBooleanParameter("FallbackToItemUse", true);
        boolean swingOnSuccess = getBooleanParameter("SwingOnSuccess", true);
        boolean sneakWhileInteracting = getBooleanParameter("SneakWhileInteracting", false);
        boolean restoreSneak = getBooleanParameter("RestoreSneakState", true);

        boolean previousSneak = client.player.isSneaking();
        if (sneakWhileInteracting) {
            client.player.setSneaking(true);
            if (client.options != null && client.options.sneakKey != null) {
                client.options.sneakKey.setPressed(true);
            }
        }

        Runnable restoreSneakState = () -> {
            if (sneakWhileInteracting && restoreSneak) {
                client.player.setSneaking(previousSneak);
                if (client.options != null && client.options.sneakKey != null) {
                    client.options.sneakKey.setPressed(previousSneak);
                }
            }
        };

        RuntimeParameterData parameterData = runtimeParameterData;
        BlockPos parameterTargetPos = parameterData != null ? parameterData.targetBlockPos : null;

        NodeParameter blockParameter = getParameter("Block");
        String configuredBlockId = null;
        String requestedBlockLabel = null;
        if (parameterData != null) {
            if (parameterData.targetBlockId != null && !parameterData.targetBlockId.isEmpty()) {
                configuredBlockId = parameterData.targetBlockId;
            } else if (parameterData.targetBlockIds != null && !parameterData.targetBlockIds.isEmpty()) {
                configuredBlockId = parameterData.targetBlockIds.get(0);
            }
        }
        if (blockParameter != null) {
            String value = blockParameter.getStringValue();
            if (value != null && !value.trim().isEmpty()) {
                configuredBlockId = value.trim();
                requestedBlockLabel = value.trim();
            }
        }
        if (requestedBlockLabel == null) {
            requestedBlockLabel = configuredBlockId;
        }

        String configuredBlockSelection = configuredBlockId;

        Block targetBlock = null;
        if (configuredBlockId != null && !configuredBlockId.isEmpty()) {
            String sanitized = sanitizeResourceId(configuredBlockId);
            String normalized = normalizeResourceId(sanitized, "minecraft");
            Identifier identifier = Identifier.tryParse(normalized);
            if (identifier == null || !Registries.BLOCK.containsId(identifier)) {
                restoreSneakState.run();
                String label = requestedBlockLabel != null && !requestedBlockLabel.isEmpty() ? requestedBlockLabel : configuredBlockId;
                sendNodeErrorMessage(client, "Cannot interact with \"" + label + "\": unknown block identifier.");
                future.complete(null);
                return;
            }
            targetBlock = Registries.BLOCK.get(identifier);
            configuredBlockId = identifier.toString();
            setParameterValueAndPropagate("Block", configuredBlockId);
        }

        // Check for entity parameter
        String configuredEntityId = null;
        Entity targetEntity = null;
        if (parameterData != null) {
            if (parameterData.targetEntity != null) {
                targetEntity = parameterData.targetEntity;
            }
            if (parameterData.targetEntityId != null && !parameterData.targetEntityId.isEmpty()) {
                configuredEntityId = parameterData.targetEntityId;
            }
        }

        if (targetEntity == null && configuredEntityId != null && !configuredEntityId.isEmpty()) {
            String sanitizedEntity = sanitizeResourceId(configuredEntityId);
            String normalizedEntity = normalizeResourceId(sanitizedEntity, "minecraft");
            Identifier entityIdentifier = Identifier.tryParse(normalizedEntity);

            if (entityIdentifier == null || !Registries.ENTITY_TYPE.containsId(entityIdentifier)) {
                restoreSneakState.run();
                sendNodeErrorMessage(client, "Cannot interact with \"" + configuredEntityId + "\": unknown entity identifier.");
                future.complete(null);
                return;
            }

            EntityType<?> entityType = Registries.ENTITY_TYPE.get(entityIdentifier);
            Optional<Entity> nearestEntity = findNearestEntity(client, entityType, PARAMETER_SEARCH_RADIUS);

            if (!nearestEntity.isPresent()) {
                restoreSneakState.run();
                String entityName = configuredEntityId.replace("minecraft:", "").replace("_", " ");
                sendNodeErrorMessage(client, "No " + entityName + " nearby to interact with.");
                future.complete(null);
                return;
            }

            targetEntity = nearestEntity.get();
        }

        if (targetEntity != null) {
            // Check distance
            if (targetEntity.squaredDistanceTo(client.player.getEyePos()) > DEFAULT_REACH_DISTANCE_SQUARED) {
                restoreSneakState.run();
                String entityName = configuredEntityId != null
                    ? configuredEntityId.replace("minecraft:", "").replace("_", " ")
                    : String.valueOf(Registries.ENTITY_TYPE.getId(targetEntity.getType()))
                        .replace("minecraft:", "")
                        .replace("_", " ");
                sendNodeErrorMessage(client, entityName + " is too far away to interact with.");
                future.complete(null);
                return;
            }
        }

        HitResult target = client.crosshairTarget;
        ActionResult result = ActionResult.PASS;
        boolean attemptedInteraction = false;

        // If an entity parameter is specified, interact with it first
        if (targetEntity != null) {
            result = client.interactionManager.interactEntity(client.player, targetEntity, hand);
            attemptedInteraction = true;
        }

        if (!attemptedInteraction && (targetBlock != null || parameterTargetPos != null)) {
            BlockPos targetPos = parameterTargetPos;
            if (targetPos == null && targetBlock != null) {
                String selectionSource = configuredBlockSelection != null && !configuredBlockSelection.isEmpty()
                    ? configuredBlockSelection
                    : configuredBlockId;
                List<BlockSelection> selections = new ArrayList<>();
                if (selectionSource != null && !selectionSource.isEmpty()) {
                    BlockSelection.parse(selectionSource).ifPresent(selections::add);
                }
                Optional<BlockPos> nearest = findNearestBlock(client, selections, PARAMETER_SEARCH_RADIUS);
                if (nearest.isPresent()) {
                    targetPos = nearest.get();
                }
            }
            if (targetPos == null) {
                String name = targetBlock != null ? targetBlock.getName().getString()
                    : (requestedBlockLabel != null && !requestedBlockLabel.isEmpty() ? requestedBlockLabel : "block");
                restoreSneakState.run();
                sendNodeErrorMessage(client, name + " is not nearby for " + type.getDisplayName() + ".");
                future.complete(null);
                return;
            }

            BlockState state = client.world.getBlockState(targetPos);
            if (state.isAir()) {
                String name = targetBlock != null ? targetBlock.getName().getString()
                    : (requestedBlockLabel != null && !requestedBlockLabel.isEmpty() ? requestedBlockLabel : "block");
                restoreSneakState.run();
                sendNodeErrorMessage(client, name + " is missing for " + type.getDisplayName() + ".");
                future.complete(null);
                return;
            }

            if (targetBlock == null) {
                targetBlock = state.getBlock();
                Identifier stateId = Registries.BLOCK.getId(targetBlock);
                if (stateId != null) {
                    setParameterValueAndPropagate("Block", stateId.toString());
                }
            }

            if (targetBlock != null && !state.isOf(targetBlock)) {
                String name = targetBlock.getName().getString();
                restoreSneakState.run();
                sendNodeErrorMessage(client, name + " is not nearby for " + type.getDisplayName() + ".");
                future.complete(null);
                return;
            }

            String blockDisplayName = targetBlock.getName().getString();

            Vec3d eyePos = client.player.getEyePos();
            Vec3d hitVec = Vec3d.ofCenter(targetPos);
            if (eyePos.squaredDistanceTo(hitVec) > DEFAULT_REACH_DISTANCE_SQUARED) {
                restoreSneakState.run();
                sendNodeErrorMessage(client, blockDisplayName + " is too far away to interact with.");
                future.complete(null);
                return;
            }

            Direction facing = Direction.getFacing(hitVec.x - eyePos.x, hitVec.y - eyePos.y, hitVec.z - eyePos.z);
            BlockHitResult manualHit = new BlockHitResult(hitVec, facing == null ? Direction.UP : facing, targetPos, false);
            target = manualHit;
            result = client.interactionManager.interactBlock(client.player, hand, manualHit);
            attemptedInteraction = true;
        }

        if (!attemptedInteraction && preferEntity && target instanceof EntityHitResult entityHit) {
            result = client.interactionManager.interactEntity(client.player, entityHit.getEntity(), hand);
            attemptedInteraction = true;
        }

        if ((!attemptedInteraction || !result.isAccepted()) && preferBlock && target instanceof BlockHitResult blockHit) {
            result = client.interactionManager.interactBlock(client.player, hand, blockHit);
            attemptedInteraction = true;
        }

        if ((!attemptedInteraction || (!result.isAccepted() && result != ActionResult.PASS)) && fallbackToItem) {
            result = client.interactionManager.interactItem(client.player, hand);
        }

        if (swingOnSuccess && (result.isAccepted() || result == ActionResult.PASS)) {
            client.player.swingHand(hand);
            if (client.player.networkHandler != null) {
                client.player.networkHandler.sendPacket(new HandSwingC2SPacket(hand));
            }
        }

        restoreSneakState.run();
        future.complete(null);
    }

    private void executeTradeCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }

        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || client.interactionManager == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        // Check if a merchant screen is open
        net.minecraft.client.gui.screen.Screen currentScreen = client.currentScreen;
        if (!(currentScreen instanceof net.minecraft.client.gui.screen.ingame.MerchantScreen)) {
            sendNodeErrorMessage(client, "No villager trading screen is open.");
            future.complete(null);
            return;
        }

        net.minecraft.client.gui.screen.ingame.MerchantScreen merchantScreen =
            (net.minecraft.client.gui.screen.ingame.MerchantScreen) currentScreen;

        // Get the screen handler from merchant screen
        net.minecraft.screen.MerchantScreenHandler screenHandler = merchantScreen.getScreenHandler();
        if (screenHandler == null) {
            sendNodeErrorMessage(client, "Cannot access merchant screen handler.");
            future.complete(null);
            return;
        }

        // Get the trade offers
        net.minecraft.village.TradeOfferList tradeOffers = screenHandler.getRecipes();
        if (tradeOffers == null || tradeOffers.isEmpty()) {
            sendNodeErrorMessage(client, "No trades available from this villager.");
            future.complete(null);
            return;
        }

        // Get the desired item from attached parameter
        String desiredItemId = null;
        String desiredTradeKey = null;
        RuntimeParameterData parameterData = runtimeParameterData;
        if (parameterData != null && parameterData.targetItemId != null && !parameterData.targetItemId.isEmpty()) {
            desiredItemId = parameterData.targetItemId;
        }
        if (parameterData != null && parameterData.targetTradeKey != null && !parameterData.targetTradeKey.isEmpty()) {
            desiredTradeKey = parameterData.targetTradeKey;
        }

        if (desiredItemId == null || desiredItemId.isEmpty()) {
            sendNodeErrorMessage(client, "No trade specified. Attach a PARAM_VILLAGER_TRADE to select what to buy.");
            future.complete(null);
            return;
        }

        // Normalize the item ID
        String sanitized = sanitizeResourceId(desiredItemId);
        String normalized = normalizeResourceId(sanitized, "minecraft");
        net.minecraft.util.Identifier identifier = net.minecraft.util.Identifier.tryParse(normalized);

        if (identifier == null || !net.minecraft.registry.Registries.ITEM.containsId(identifier)) {
            sendNodeErrorMessage(client, "Unknown item: " + desiredItemId);
            future.complete(null);
            return;
        }

        net.minecraft.item.Item desiredItem = net.minecraft.registry.Registries.ITEM.get(identifier);

        // Find a trade that matches the selected trade key (if provided), otherwise match by item
        int tradeIndex = -1;
        if (desiredTradeKey != null && !desiredTradeKey.isEmpty()) {
            for (int i = 0; i < tradeOffers.size(); i++) {
                net.minecraft.village.TradeOffer offer = tradeOffers.get(i);
                if (offer == null || offer.isDisabled()) {
                    continue;
                }
                String offerKey = buildTradeKey(
                    offer.getDisplayedFirstBuyItem(),
                    offer.getDisplayedSecondBuyItem(),
                    offer.getSellItem()
                );
                if (desiredTradeKey.equals(offerKey) && canAffordTrade(client.player, screenHandler, offer)) {
                    tradeIndex = i;
                    break;
                }
            }
        }
        if (tradeIndex == -1) {
            for (int i = 0; i < tradeOffers.size(); i++) {
                net.minecraft.village.TradeOffer offer = tradeOffers.get(i);
                if (offer != null && !offer.isDisabled() && offer.getSellItem().getItem() == desiredItem) {
                    if (canAffordTrade(client.player, screenHandler, offer)) {
                        tradeIndex = i;
                        break;
                    }
                }
            }
        }

        if (tradeIndex == -1) {
            String message = desiredTradeKey != null && !desiredTradeKey.isEmpty()
                ? "No available trade found for the selected trade."
                : "No available trade found for " + desiredItemId + " or missing required items.";
            sendNodeErrorMessage(client, message);
            future.complete(null);
            return;
        }

        final net.minecraft.village.TradeOffer selectedOffer = tradeOffers.get(tradeIndex);

        // Get the quantity to trade (amount is desired output items, not number of trades)
        boolean useAmount = isAmountInputEnabled();
        int desiredAmount = useAmount
            ? Math.max(1, getIntParameter("Amount", 1))
            : Math.max(1, selectedOffer.getSellItem().getCount());
        final int finalTradeIndex = tradeIndex;
        int sellCount = Math.max(1, selectedOffer.getSellItem().getCount());
        int tradesToExecute = Math.max(1, (int) Math.ceil(desiredAmount / (double) sellCount));

        // Debug: Log the trade quantity
        System.out.println("[TRADE DEBUG] Trading " + tradesToExecute + " times for " + desiredItemId);

        // Execute trades with proper server synchronization
        new Thread(() -> {
            try {
                for (int tradeCount = 0; tradeCount < tradesToExecute; tradeCount++) {
                    // Check if we can still afford the trade
                    if (!canAffordTrade(client.player, screenHandler, selectedOffer)) {
                        sendNodeErrorMessage(client, "Not enough items to complete the trade.");
                        break;
                    }

                    // Check if trade is still available (not disabled/sold out)
                    if (selectedOffer.isDisabled()) {
                        sendNodeErrorMessage(client, "Trade is no longer available.");
                        break;
                    }

                    // Execute trade on main thread with proper packet sequence
                    runOnClientThread(client, () -> {
                        // Select the trade on client
                        screenHandler.setRecipeIndex(finalTradeIndex);
                        screenHandler.switchTo(finalTradeIndex);

                        // Send packet to server to select the trade
                        if (client.player.networkHandler != null) {
                            client.player.networkHandler.sendPacket(
                                new net.minecraft.network.packet.c2s.play.SelectMerchantTradeC2SPacket(finalTradeIndex)
                            );
                        }
                    });

                    // Wait for server to process the trade selection
                    Thread.sleep(50);

                    // Complete the trade by clicking output slot
                    runOnClientThread(client, () -> {
                        final int outputSlot = 2;
                        // Use PICKUP to take exactly one trade's worth (not QUICK_MOVE which does multiple)
                        client.interactionManager.clickSlot(
                            screenHandler.syncId,
                            outputSlot,
                            0,
                            net.minecraft.screen.slot.SlotActionType.PICKUP,
                            client.player
                        );

                        // If cursor has item, place it in first empty inventory slot
                        if (!screenHandler.getCursorStack().isEmpty()) {
                            for (int slot = 3; slot < screenHandler.slots.size(); slot++) {
                                if (screenHandler.getSlot(slot).getStack().isEmpty()) {
                                    client.interactionManager.clickSlot(
                                        screenHandler.syncId,
                                        slot,
                                        0,
                                        net.minecraft.screen.slot.SlotActionType.PICKUP,
                                        client.player
                                    );
                                    break;
                                }
                            }
                        }
                    });

                    // Delay between trades to allow server synchronization
                    if (tradeCount < tradesToExecute - 1) {
                        Thread.sleep(250);
                    }
                }

                future.complete(null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.completeExceptionally(e);
            }
        }, "Pathmind-Trade").start();
    }

    private boolean canAffordTrade(net.minecraft.entity.player.PlayerEntity player,
                                   net.minecraft.screen.MerchantScreenHandler screenHandler,
                                   net.minecraft.village.TradeOffer offer) {
        if (player == null || offer == null || screenHandler == null) {
            return false;
        }

        net.minecraft.entity.player.PlayerInventory inventory = player.getInventory();

        // In Minecraft 1.21+, getFirstBuyItem() returns TradedItem, use itemStack() to get ItemStack
        net.minecraft.village.TradedItem firstTradedItem = offer.getFirstBuyItem();
        net.minecraft.item.ItemStack firstBuyItem = firstTradedItem.itemStack();

        // Check first required item
        if (!firstBuyItem.isEmpty()) {
            int required = firstBuyItem.getCount();
            int available = countAvailableForTrade(inventory, screenHandler, firstBuyItem);
            if (available < required) {
                return false;
            }
        }

        // Check second required item (if exists)
        // getSecondBuyItem() returns Optional<TradedItem> in Minecraft 1.21+
        java.util.Optional<net.minecraft.village.TradedItem> secondBuyItemOpt = offer.getSecondBuyItem();
        if (secondBuyItemOpt.isPresent()) {
            net.minecraft.village.TradedItem tradedItem = secondBuyItemOpt.get();
            net.minecraft.item.ItemStack secondBuyItem = tradedItem.itemStack();
            int required = secondBuyItem.getCount();
            int available = countAvailableForTrade(inventory, screenHandler, secondBuyItem);
            if (available < required) {
                return false;
            }
        }

        return true;
    }

    private int countAvailableForTrade(net.minecraft.entity.player.PlayerInventory inventory,
                                       net.minecraft.screen.MerchantScreenHandler screenHandler,
                                       net.minecraft.item.ItemStack requiredStack) {
        int available = 0;
        for (int i = 0; i < inventory.size(); i++) {
            net.minecraft.item.ItemStack stack = inventory.getStack(i);
            if (net.minecraft.item.ItemStack.areItemsEqual(stack, requiredStack)) {
                available += stack.getCount();
            }
        }

        // Include items already moved into merchant input slots (0 and 1).
        for (int slotIndex = 0; slotIndex <= 1; slotIndex++) {
            net.minecraft.item.ItemStack stack = screenHandler.getSlot(slotIndex).getStack();
            if (net.minecraft.item.ItemStack.areItemsEqual(stack, requiredStack)) {
                available += stack.getCount();
            }
        }

        net.minecraft.item.ItemStack cursorStack = screenHandler.getCursorStack();
        if (net.minecraft.item.ItemStack.areItemsEqual(cursorStack, requiredStack)) {
            available += cursorStack.getCount();
        }

        return available;
    }

    private void executeSwingCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        Hand hand = resolveHand(getParameter("Hand"), Hand.MAIN_HAND);
        int count = Math.max(1, getIntParameter("Count", 1));
        double intervalSeconds = Math.max(0.0, getDoubleParameter("IntervalSeconds", 0.0));

        new Thread(() -> {
            try {
                for (int i = 0; i < count; i++) {
                    runOnClientThread(client, () -> {
                        if (hand == Hand.MAIN_HAND && client.interactionManager != null) {
                            HitResult target = client.crosshairTarget;
                            if (target instanceof EntityHitResult entityHit) {
                                client.interactionManager.attackEntity(client.player, entityHit.getEntity());
                            }
                        }
                        client.player.swingHand(hand);
                        if (client.player.networkHandler != null) {
                            client.player.networkHandler.sendPacket(new HandSwingC2SPacket(hand));
                        }
                    });

                    if (intervalSeconds > 0.0 && i < count - 1) {
                        Thread.sleep((long) (intervalSeconds * 1000));
                    }
                }
                future.complete(null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.completeExceptionally(e);
            }
        }, "Pathmind-Swing").start();
    }
    
    private void executeEquipArmorCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }
        
        PlayerInventory inventory = client.player.getInventory();
        int sourceSlot = clampInventorySlot(inventory, getIntParameter("SourceSlot", 0));
        EquipmentSlot equipmentSlot = parseEquipmentSlot(getParameter("ArmorSlot"), EquipmentSlot.HEAD);
        
        ItemStack sourceStack = inventory.getStack(sourceSlot);
        if (sourceStack.isEmpty()) {
            future.complete(null);
            return;
        }
        
        ItemStack current = client.player.getEquippedStack(equipmentSlot);
        inventory.setStack(sourceSlot, current);
        client.player.equipStack(equipmentSlot, sourceStack);
        inventory.markDirty();
        client.player.playerScreenHandler.sendContentUpdates();
        future.complete(null);
    }
    
    private void executeEquipHandCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }
        
        PlayerInventory inventory = client.player.getInventory();
        int sourceSlot = clampInventorySlot(inventory, getIntParameter("SourceSlot", 0));
        Hand hand = resolveHand(getParameter("Hand"), Hand.MAIN_HAND);
        
        ItemStack sourceStack = inventory.getStack(sourceSlot);
        if (sourceStack.isEmpty()) {
            future.complete(null);
            return;
        }
        
        ItemStack handStack = client.player.getStackInHand(hand);
        client.player.setStackInHand(hand, sourceStack);
        inventory.setStack(sourceSlot, handStack);
        inventory.markDirty();
        client.player.playerScreenHandler.sendContentUpdates();
        future.complete(null);
    }
    
    private void completeSensorEvaluation(CompletableFuture<Void> future) {
        boolean result = evaluateSensor();
        setNextOutputSocket(result ? 0 : 1);
        future.complete(null);
    }

    private void runOnClientThread(net.minecraft.client.MinecraftClient client, Runnable task) throws InterruptedException {
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

    private <T> T supplyFromClient(net.minecraft.client.MinecraftClient client, java.util.function.Supplier<T> supplier) throws InterruptedException {
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

    private int clampInventorySlot(PlayerInventory inventory, int slot) {
        return MathHelper.clamp(slot, 0, inventory.size() - 1);
    }

    private int getOffhandInventoryIndex(PlayerInventory inventory) {
        if (inventory == null || inventory.size() <= 0) {
            return -1;
        }
        int index = PLAYER_OFFHAND_INVENTORY_INDEX;
        if (index >= inventory.size()) {
            return inventory.size() - 1;
        }
        return index;
    }

    private EquipmentSlot parseEquipmentSlot(NodeParameter parameter, EquipmentSlot defaultSlot) {
        if (parameter == null || parameter.getStringValue() == null) {
            return defaultSlot;
        }
        String value = parameter.getStringValue().trim().toLowerCase(Locale.ROOT);
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

    private int getIntParameter(String name, int defaultValue) {
        NodeParameter param = getParameter(name);
        if (param == null) {
            return defaultValue;
        }
        if (param.getType() == ParameterType.INTEGER) {
            return param.getIntValue();
        }
        try {
            return Integer.parseInt(param.getStringValue());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean isAnyPlayerValue(String value) {
        return value == null || value.trim().isEmpty() || "any".equalsIgnoreCase(value.trim());
    }

    private static boolean isAnyMessageValue(String value) {
        return value == null || value.trim().isEmpty() || "any".equalsIgnoreCase(value.trim());
    }

    private static Optional<AbstractClientPlayerEntity> findNearestPlayer(
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
            double distance = reference != null ? player.squaredDistanceTo(reference) : 0.0;
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
    
    private String getStringParameter(String name, String defaultValue) {
        NodeParameter param = getParameter(name);
        if (param == null) {
            return defaultValue;
        }
        String value = param.getStringValue();
        return value != null ? value : defaultValue;
    }

    private static String getParameterString(Node node, String name) {
        if (node == null || name == null) {
            return null;
        }
        NodeParameter parameter = node.getParameter(name);
        if (parameter == null) {
            return null;
        }
        return parameter.getStringValue();
    }

    private String getBlockParameterValue(Node node) {
        if (node == null) {
            return null;
        }
        String blockId = getParameterString(node, "Block");
        if (blockId == null || blockId.isEmpty()) {
            return null;
        }
        String state = getParameterString(node, "State");
        if (state == null || state.isEmpty()) {
            return blockId;
        }
        Optional<String> combined = BlockSelection.combine(blockId, state);
        if (combined.isPresent()) {
            return combined.get();
        }
        notifyInvalidBlockStateSelection(blockId, state);
        return null;
    }

    private String getEntityParameterState(Node node) {
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
        String normalized = sanitized != null && !sanitized.isEmpty()
            ? normalizeResourceId(sanitized, "minecraft")
            : primaryEntity;
        Identifier identifier = Identifier.tryParse(normalized);
        if (identifier == null || !Registries.ENTITY_TYPE.containsId(identifier)) {
            return trimmedState;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (!EntityStateOptions.isStateSupported(Registries.ENTITY_TYPE.get(identifier), client != null ? client.world : null, trimmedState)) {
            notifyInvalidEntityStateSelection(primaryEntity, trimmedState);
            return trimmedState;
        }
        return trimmedState;
    }

    private double getDoubleParameter(String name, double defaultValue) {
        NodeParameter param = getParameter(name);
        if (param == null) {
            return defaultValue;
        }
        if (param.getType() == ParameterType.DOUBLE) {
            return param.getDoubleValue();
        }
        try {
            return Double.parseDouble(param.getStringValue());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    private boolean getBooleanParameter(String name, boolean defaultValue) {
        NodeParameter param = getParameter(name);
        if (param == null) {
            return defaultValue;
        }
        if (param.getType() == ParameterType.BOOLEAN) {
            return param.getBoolValue();
        }
        String value = param.getStringValue();
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    private static double parseDoubleOrDefault(String value, double defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private void notifyInvalidBlockStateSelection(String blockId, String state) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        String blockLabel = (blockId == null || blockId.isEmpty()) ? "the selected block" : blockId;
        String stateLabel = state == null || state.isEmpty() ? "(unspecified state)" : state;
        sendNodeErrorMessage(client, "State \"" + stateLabel + "\" is not valid for " + blockLabel + " on " + type.getDisplayName() + ".");
    }

    private void notifyInvalidEntityStateSelection(String entityId, String state) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        String entityLabel = (entityId == null || entityId.isEmpty()) ? "the selected entity" : entityId;
        String stateLabel = state == null || state.isEmpty() ? "(unspecified state)" : state;
        sendNodeErrorMessage(client, "State \"" + stateLabel + "\" is not valid for " + entityLabel + " on " + type.getDisplayName() + ".");
    }

    private Optional<BlockPos> findNearestDroppedItem(net.minecraft.client.MinecraftClient client, Item item, double range) {
        if (client == null || client.player == null || client.world == null || item == null) {
            return Optional.empty();
        }
        double searchRadius = Math.max(1.0, range);
        Box searchBox = client.player.getBoundingBox().expand(searchRadius);
        List<ItemEntity> entities = client.world.getEntitiesByClass(ItemEntity.class, searchBox,
            entity -> entity != null && !entity.isRemoved() && !entity.getStack().isEmpty() && entity.getStack().isOf(item));
        if (entities.isEmpty()) {
            return Optional.empty();
        }
        ItemEntity nearest = Collections.min(entities, Comparator.comparingDouble(entity -> entity.squaredDistanceTo(client.player)));
        return Optional.of(nearest.getBlockPos());
    }

    private Optional<Entity> findNearestEntity(net.minecraft.client.MinecraftClient client, EntityType<?> entityType, double range) {
        return findNearestEntity(client, entityType, range, "");
    }

    private Optional<Entity> findNearestEntity(net.minecraft.client.MinecraftClient client, EntityType<?> entityType, double range, String state) {
        if (client == null || client.player == null || client.world == null || entityType == null) {
            return Optional.empty();
        }
        double searchRadius = Math.max(1.0, range);
        Box searchBox = client.player.getBoundingBox().expand(searchRadius);
        List<Entity> matches = client.world.getOtherEntities(
            client.player,
            searchBox,
            entity -> entity.getType() == entityType && EntityStateOptions.matchesState(entity, state)
        );
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        Entity nearest = Collections.min(matches, Comparator.comparingDouble(entity -> entity.squaredDistanceTo(client.player)));
        return Optional.of(nearest);
    }
    
    private Hand resolveHand(NodeParameter parameter, Hand defaultHand) {
        if (parameter == null || parameter.getStringValue() == null) {
            return defaultHand;
        }
        String value = parameter.getStringValue().trim().toLowerCase(Locale.ROOT);
        if (value.equals("off") || value.equals("offhand") || value.equals("off_hand") || value.equals("off-hand")) {
            return Hand.OFF_HAND;
        }
        return Hand.MAIN_HAND;
    }

    private void resetControlState() {
        this.repeatRemainingIterations = 0;
        this.repeatActive = false;
        this.lastSensorResult = false;
        this.nextOutputSocket = 0;
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
        if (attachedParameters.isEmpty()) {
            return null;
        }
        List<Integer> slotIndices = new ArrayList<>(attachedParameters.keySet());
        Collections.sort(slotIndices);
        for (Integer slotIndex : slotIndices) {
            Node parameter = attachedParameters.get(slotIndex);
            if (parameter == null || !parameter.isParameterNode()) {
                continue;
            }
            NodeType parameterType = parameter.getType();
            for (NodeType allowed : allowedTypes) {
                if (parameterType == allowed) {
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
            this.lastSensorResult = false;
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
            case SENSOR_TOUCHING_BLOCK: {
                String blockId = getStringParameter("Block", "stone");
                Node parameterNode = getAttachedParameterOfType(NodeType.PARAM_BLOCK, NodeType.PARAM_PLACE_TARGET);
                if (parameterNode != null) {
                    List<BlockSelection> selections = resolveBlocksFromParameter(parameterNode);
                    if (!selections.isEmpty()) {
                        result = isTouchingBlock(selections);
                        break;
                    }
                }
                result = evaluateSensorCondition(SensorConditionType.TOUCHING_BLOCK, blockId, null, 0, 0, 0);
                break;
            }
            case SENSOR_TOUCHING_ENTITY: {
                String entityId = getStringParameter("Entity", "zombie");
                Node parameterNode = getAttachedParameterOfType(NodeType.PARAM_ENTITY);
                if (parameterNode != null) {
                    String nodeEntity = getParameterString(parameterNode, "Entity");
                    if (nodeEntity != null && !nodeEntity.isEmpty()) {
                        entityId = nodeEntity;
                    }
                    String state = getEntityParameterState(parameterNode);
                    result = isTouchingEntity(entityId, state);
                    break;
                }
                result = evaluateSensorCondition(SensorConditionType.TOUCHING_ENTITY, null, entityId, 0, 0, 0);
                break;
            }
            case SENSOR_AT_COORDINATES: {
                int x = getIntParameter("X", 0);
                int y = getIntParameter("Y", 64);
                int z = getIntParameter("Z", 0);
                Node parameterNode = getAttachedParameterOfType(NodeType.PARAM_COORDINATE, NodeType.PARAM_PLACE_TARGET);
                if (parameterNode != null) {
                    x = parseNodeInt(parameterNode, "X", x);
                    y = parseNodeInt(parameterNode, "Y", y);
                    z = parseNodeInt(parameterNode, "Z", z);
                }
                result = evaluateSensorCondition(SensorConditionType.AT_COORDINATES, null, null, x, y, z);
                break;
            }
            case SENSOR_BLOCK_AHEAD: {
                Node parameterNode = getAttachedParameterOfType(NodeType.PARAM_BLOCK, NodeType.PARAM_PLACE_TARGET);
                if (parameterNode != null) {
                    List<BlockSelection> selections = resolveBlocksFromParameter(parameterNode);
                    if (!selections.isEmpty()) {
                        result = isBlockAhead(selections);
                        break;
                    }
                }
                String blockId = getStringParameter("Block", "stone");
                result = isBlockAhead(blockId);
                break;
            }
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
                double amount = MathHelper.clamp(getDoubleParameter("Amount", 10.0), 0.0, 40.0);
                Node amountParameter = getAttachedParameterOfType(NodeType.PARAM_AMOUNT, NodeType.OPERATOR_RANDOM);
                if (amountParameter != null) {
                    amount = MathHelper.clamp(parseNodeDouble(amountParameter, "Amount", amount), 0.0, 40.0);
                }
                result = isHealthBelow(amount);
                break;
            }
            case SENSOR_HUNGER_BELOW: {
                int amount = MathHelper.clamp(getIntParameter("Amount", 10), 0, 20);
                Node amountParameter = getAttachedParameterOfType(NodeType.PARAM_AMOUNT, NodeType.OPERATOR_RANDOM);
                if (amountParameter != null) {
                    double parsed = parseNodeDouble(amountParameter, "Amount", amount);
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
                Node attached = getAttachedParameter();
                if (attached != null && attached.isParameterNode()) {
                    if (attached.getType() == NodeType.PARAM_AMOUNT || attached.getType() == NodeType.OPERATOR_RANDOM) {
                        amountNode = attached;
                    } else if (attached.getType() == NodeType.PARAM_ITEM) {
                        parameterNode = attached;
                    } else {
                        sendIncompatibleParameterMessage(attached);
                    }
                }
                if (amountNode != null) {
                    double parsed = parseNodeDouble(amountNode, "Amount", requiredAmount);
                    requiredAmount = Math.max(1, (int) Math.round(parsed));
                }
                if (parameterNode != null) {
                    List<String> nodeItems = resolveItemIdsFromParameter(parameterNode);
                    if (!nodeItems.isEmpty()) {
                        boolean hasAny = false;
                        for (String candidate : nodeItems) {
                            if (useAmount ? hasItemAmountInInventory(candidate, requiredAmount) : hasItemInInventory(candidate)) {
                                hasAny = true;
                                break;
                            }
                        }
                        result = hasAny;
                        break;
                    }
                }
                result = useAmount ? hasItemAmountInInventory(itemId, requiredAmount) : hasItemInInventory(itemId);
                break;
            }
            case SENSOR_ITEM_IN_SLOT: {
                Node itemNode = getAttachedParameter(0);
                Node slotNode = getAttachedParameter(1);
                if (itemNode == null || slotNode == null) {
                    net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                    if (client != null) {
                        sendNodeErrorMessage(client, type.getDisplayName() + " requires an item and slot parameter.");
                    }
                    result = false;
                    break;
                }
                if (itemNode.getType() != NodeType.PARAM_ITEM) {
                    sendIncompatibleParameterMessage(itemNode);
                    result = false;
                    break;
                }
                if (slotNode.getType() != NodeType.PARAM_INVENTORY_SLOT) {
                    sendIncompatibleParameterMessage(slotNode);
                    result = false;
                    break;
                }
                List<String> itemIds = resolveItemIdsFromParameter(itemNode);
                if (itemIds.isEmpty()) {
                    net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                    if (client != null) {
                        sendNodeErrorMessage(client, "No item specified for " + type.getDisplayName() + ".");
                    }
                    result = false;
                    break;
                }
                net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                if (client == null || client.player == null) {
                    result = false;
                    break;
                }
                PlayerInventory inventory = client.player.getInventory();
                ScreenHandler handler = client.player.currentScreenHandler;
                int slotValue = parseNodeInt(slotNode, "Slot", 0);
                SlotSelectionType selectionType = resolveInventorySlotSelectionType(slotNode);
                SlotResolution resolved = resolveInventorySlot(handler, inventory, slotValue, selectionType);
                if (resolved == null || resolved.slot == null) {
                    sendNodeErrorMessage(client, type.getDisplayName() + " requires a valid slot selection.");
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
                result = matchesItem && (!useAmount || stack.getCount() >= requiredAmount);
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
            case SENSOR_IS_ON_GROUND:
                result = isOnGround();
                break;
            case SENSOR_IS_FALLING: {
                double distance = Math.max(0.0, getDoubleParameter("Distance", 2.0));
                result = isFalling(distance);
                break;
            }
            case SENSOR_KEY_PRESSED: {
                String key = getStringParameter("Key", "space");
                Node parameterNode = getAttachedParameterOfType(NodeType.PARAM_KEY);
                if (parameterNode != null) {
                    String parameterKey = getParameterString(parameterNode, "Key");
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
                Node parameterNode = getAttachedParameterOfType(
                    NodeType.PARAM_BLOCK,
                    NodeType.PARAM_ITEM,
                    NodeType.PARAM_ENTITY,
                    NodeType.PARAM_PLAYER,
                    NodeType.PARAM_PLACE_TARGET
                );
                if (parameterNode != null) {
                    NodeType parameterType = parameterNode.getType();
                    switch (parameterType) {
                        case PARAM_ITEM: {
                            List<String> nodeItems = resolveItemIdsFromParameter(parameterNode);
                            if (!nodeItems.isEmpty()) {
                                resourceId = String.join(",", nodeItems);
                            }
                            break;
                        }
                        case PARAM_ENTITY: {
                            String nodeEntity = getParameterString(parameterNode, "Entity");
                            if (nodeEntity != null && !nodeEntity.isEmpty()) {
                                String state = getEntityParameterState(parameterNode);
                                result = isEntityRendered(nodeEntity, state);
                                handled = true;
                                break;
                            }
                            break;
                        }
                        case PARAM_PLAYER: {
                            String nodePlayer = getParameterString(parameterNode, "Player");
                            if (nodePlayer != null && !nodePlayer.isEmpty()) {
                                resourceId = nodePlayer;
                            }
                            break;
                        }
                        default: {
                            String nodeBlock = getBlockParameterValue(parameterNode);
                            if (nodeBlock != null && !nodeBlock.isEmpty()) {
                                resourceId = nodeBlock;
                            }
                            break;
                        }
                    }
                }
                if (!handled) {
                    result = isResourceRendered(resourceId);
                }
                break;
            }
            case SENSOR_VILLAGER_TRADE: {
                Node parameterNode = getAttachedParameterOfType(NodeType.PARAM_VILLAGER_TRADE);
                if (parameterNode == null) {
                    result = false;
                    break;
                }
                String tradeKey = resolveTradeKeyFromParameter(parameterNode);
                List<String> itemIds = resolveItemIdsFromParameter(parameterNode);
                if ((itemIds == null || itemIds.isEmpty()) && tradeKey != null && !tradeKey.isEmpty()
                    && !tradeKey.contains("|")) {
                    itemIds = new ArrayList<>();
                    addItemIdentifier(itemIds, tradeKey);
                }
                if ((tradeKey == null || tradeKey.isEmpty()) && itemIds.isEmpty()) {
                    net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                    if (client != null) {
                        sendNodeErrorMessage(client, "No trade selected for " + type.getDisplayName() + ".");
                    }
                    result = false;
                    break;
                }
                net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                if (client == null) {
                    result = false;
                    break;
                }
                net.minecraft.client.gui.screen.Screen currentScreen = client.currentScreen;
                if (!(currentScreen instanceof net.minecraft.client.gui.screen.ingame.MerchantScreen)) {
                    if (client != null) {
                        sendNodeErrorMessage(client, "No villager trading screen is open.");
                    }
                    result = false;
                    break;
                }
                net.minecraft.client.gui.screen.ingame.MerchantScreen merchantScreen =
                    (net.minecraft.client.gui.screen.ingame.MerchantScreen) currentScreen;
                net.minecraft.screen.MerchantScreenHandler screenHandler = merchantScreen.getScreenHandler();
                if (screenHandler == null) {
                    result = false;
                    break;
                }
                net.minecraft.village.TradeOfferList tradeOffers = screenHandler.getRecipes();
                if (tradeOffers == null || tradeOffers.isEmpty()) {
                    result = false;
                    break;
                }
                boolean found = false;
                for (int i = 0; i < tradeOffers.size(); i++) {
                    net.minecraft.village.TradeOffer offer = tradeOffers.get(i);
                    if (offer == null || offer.isDisabled()) {
                        continue;
                    }
                    ItemStack sellStack = offer.getSellItem();
                    if (tradeKey != null && !tradeKey.isEmpty()) {
                        String offerKey = buildTradeKey(
                            offer.getDisplayedFirstBuyItem(),
                            offer.getDisplayedSecondBuyItem(),
                            offer.getSellItem()
                        );
                        if (tradeKey.equals(offerKey)) {
                            found = true;
                            break;
                        }
                    }
                    if (!itemIds.isEmpty() && stackMatchesAnyItem(sellStack, itemIds)) {
                        found = true;
                        break;
                    }
                }
                result = found;
                break;
            }
            case SENSOR_CHAT_MESSAGE: {
                Node playerNode = getAttachedParameter(0);
                Node messageNode = getAttachedParameter(1);
                if (playerNode == null || messageNode == null) {
                    net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                    if (client != null) {
                        sendNodeErrorMessage(client, type.getDisplayName() + " requires a user and message parameter.");
                    }
                    result = false;
                    break;
                }
                if (playerNode.getType() != NodeType.PARAM_PLAYER) {
                    sendIncompatibleParameterMessage(playerNode);
                    result = false;
                    break;
                }
                if (messageNode.getType() != NodeType.PARAM_MESSAGE) {
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
                boolean anyMessage = isAnyMessageValue(messageText);
                boolean useAmount = isAmountInputEnabled();
                double seconds = useAmount
                    ? Math.max(0.0, getDoubleParameter("Amount", 10.0))
                    : ChatMessageTracker.getMaxRetentionSeconds();
                result = ChatMessageTracker.hasRecentMessage(playerName, messageText, seconds, anyPlayer, anyMessage);
                break;
            }
            default:
                result = false;
                break;
        }
        result = adjustBooleanToggleResult(result);
        this.lastSensorResult = result;
        return result;
    }

    private boolean ensureRequiredSensorParameterAttached() {
        if (!isSensorNode() || !attachedParameters.isEmpty() || !sensorRequiresParameterNode()) {
            return true;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client != null) {
            sendNodeErrorMessage(client, type.getDisplayName() + " requires a parameter node.");
        }
        return false;
    }

    private boolean sensorRequiresParameterNode() {
        return switch (type) {
            case SENSOR_TOUCHING_BLOCK,
                 SENSOR_TOUCHING_ENTITY,
                 SENSOR_AT_COORDINATES,
                 SENSOR_BLOCK_AHEAD,
                 SENSOR_ITEM_IN_INVENTORY,
                 SENSOR_ITEM_IN_SLOT,
                 SENSOR_VILLAGER_TRADE,
                 SENSOR_CHAT_MESSAGE -> true;
            default -> false;
        };
    }

    private boolean evaluateOperatorEquals() {
        Optional<Boolean> result = evaluateOperatorComparison();
        return result.orElse(false);
    }

    private boolean evaluateOperatorNot() {
        Optional<Boolean> result = evaluateOperatorComparison();
        return result.map(value -> !value).orElse(false);
    }

    private Optional<Boolean> evaluateOperatorComparison() {
        Node left = getAttachedParameter(0);
        Node right = getAttachedParameter(1);
        if (left == null || right == null) {
            return Optional.empty();
        }
        boolean leftIsVariable = left.getType() == NodeType.VARIABLE;
        boolean rightIsVariable = right.getType() == NodeType.VARIABLE;
        if (leftIsVariable || rightIsVariable) {
            return compareVariableNodes(left, right);
        }
        return compareParameterNodes(left, right);
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
            if (leftName == null || leftName.trim().isEmpty() || rightName == null || rightName.trim().isEmpty()) {
                return Optional.empty();
            }
            ExecutionManager.RuntimeVariable leftVar = manager.getRuntimeVariable(startNode, leftName.trim());
            ExecutionManager.RuntimeVariable rightVar = manager.getRuntimeVariable(startNode, rightName.trim());
            if (leftVar == null || rightVar == null) {
                return Optional.empty();
            }
            if (leftVar.getType() != rightVar.getType()) {
                return Optional.empty();
            }
            Map<String, String> leftValues = leftVar.getValues();
            Map<String, String> rightValues = rightVar.getValues();
            if (leftValues == null || rightValues == null) {
                return Optional.empty();
            }
            return Optional.of(leftValues.equals(rightValues));
        }
        Node variableNode = leftIsVariable ? left : right;
        Node valueNode = leftIsVariable ? right : left;
        String variableName = getParameterString(variableNode, "Variable");
        if (variableName == null || variableName.trim().isEmpty()) {
            return Optional.empty();
        }
        ExecutionManager.RuntimeVariable variable = manager.getRuntimeVariable(startNode, variableName.trim());
        if (variable == null) {
            return Optional.empty();
        }
        NodeType valueType = valueNode.getType();
        if (valueType == NodeType.SENSOR_POSITION_OF) {
            valueType = NodeType.PARAM_COORDINATE;
        }
        if (variable.getType() != valueType) {
            return Optional.empty();
        }
        Map<String, String> currentValues = valueNode.exportParameterValues();
        Map<String, String> storedValues = variable.getValues();
        if (currentValues == null || storedValues == null) {
            return Optional.empty();
        }
        return Optional.of(storedValues.equals(currentValues));
    }

    private Optional<Boolean> compareParameterNodes(Node left, Node right) {
        if (left == null || right == null) {
            return Optional.empty();
        }
        Optional<Double> leftNumber = resolveComparableNumber(left);
        Optional<Double> rightNumber = resolveComparableNumber(right);
        if (leftNumber.isPresent() && rightNumber.isPresent()) {
            return Optional.of(Double.compare(leftNumber.get(), rightNumber.get()) == 0);
        }
        if (leftNumber.isPresent() || rightNumber.isPresent()) {
            return Optional.empty();
        }
        Map<String, String> leftValues = left.exportParameterValues();
        Map<String, String> rightValues = right.exportParameterValues();
        if (leftValues == null || rightValues == null || leftValues.isEmpty() || rightValues.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(leftValues.equals(rightValues));
    }

    private Optional<Double> resolveComparableNumber(Node node) {
        if (node == null) {
            return Optional.empty();
        }
        switch (node.getType()) {
            case PARAM_AMOUNT:
            case OPERATOR_RANDOM:
                return Optional.of(parseNodeDouble(node, "Amount", 0.0));
            case PARAM_INVENTORY_SLOT:
                return resolveInventorySlotCount(node).map(count -> (double) count);
            default:
                return Optional.empty();
        }
    }

    private Optional<Integer> resolveInventorySlotCount(Node slotNode) {
        if (slotNode == null || slotNode.getType() != NodeType.PARAM_INVENTORY_SLOT) {
            return Optional.empty();
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return Optional.empty();
        }
        PlayerInventory inventory = client.player.getInventory();
        ScreenHandler handler = client.player.currentScreenHandler;
        int slotValue = parseNodeInt(slotNode, "Slot", 0);
        SlotSelectionType selectionType = resolveInventorySlotSelectionType(slotNode);
        SlotResolution resolved = resolveInventorySlot(handler, inventory, slotValue, selectionType);
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

    private boolean evaluateConditionFromParameters() {
        if (attachedSensor != null) {
            boolean result = attachedSensor.evaluateSensor();
            this.lastSensorResult = result;
            return result;
        }

        // Legacy fallback when no sensor is attached
        String condition = getStringParameter("Condition", "Touching Block");
        String blockId = getStringParameter("Block", "stone");
        String entityId = getStringParameter("Entity", "zombie");
        int x = getIntParameter("X", 0);
        int y = getIntParameter("Y", 64);
        int z = getIntParameter("Z", 0);
        boolean result = evaluateSensorCondition(SensorConditionType.fromLabel(condition), blockId, entityId, x, y, z);
        this.lastSensorResult = result;
        return result;
    }
    
    private boolean evaluateSensorCondition(SensorConditionType type, String blockId, String entityId, int x, int y, int z) {
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
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        net.minecraft.world.World world = EntityCompatibilityBridge.getWorld(client.player);
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
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || entityId == null || entityId.isEmpty()) {
            return false;
        }
        net.minecraft.world.World world = EntityCompatibilityBridge.getWorld(client.player);
        if (world == null) {
            return false;
        }
        for (String candidateId : splitMultiValueList(entityId)) {
            String sanitized = sanitizeResourceId(candidateId);
            String normalized = sanitized != null && !sanitized.isEmpty()
                ? normalizeResourceId(sanitized, "minecraft")
                : candidateId;
            Identifier identifier = Identifier.tryParse(normalized);
            if (identifier == null || !Registries.ENTITY_TYPE.containsId(identifier)) {
                continue;
            }
            EntityType<?> entityType = Registries.ENTITY_TYPE.get(identifier);
            List<Entity> entities = world.getOtherEntities(
                client.player,
                client.player.getBoundingBox().expand(0.15),
                entity -> entity.getType() == entityType && EntityStateOptions.matchesState(entity, state)
            );
            if (!entities.isEmpty()) {
                return true;
            }
        }
        return false;
    }
    
    private boolean isAtCoordinates(int x, int y, int z) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        BlockPos playerPos = client.player.getBlockPos();
        return playerPos.getX() == x && playerPos.getY() == y && playerPos.getZ() == z;
    }

    private boolean isBlockAhead(String blockId) {
        return isBlockAhead(parseBlockSelectionList(blockId));
    }

    private boolean isBlockAhead(List<BlockSelection> selections) {
        if (selections == null || selections.isEmpty()) {
            return false;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        net.minecraft.world.World world = EntityCompatibilityBridge.getWorld(client.player);
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
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        net.minecraft.world.World world = EntityCompatibilityBridge.getWorld(client.player);
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

    private boolean matchesAnyBlock(List<BlockSelection> selections, BlockState state) {
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

    private boolean isLightLevelBelow(int threshold) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        net.minecraft.world.World world = EntityCompatibilityBridge.getWorld(client.player);
        if (world == null) {
            return false;
        }
        BlockPos pos = client.player.getBlockPos();
        return world.getLightLevel(pos) < threshold;
    }

    private boolean isDaytime() {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return false;
        }
        long time = client.world.getTimeOfDay() % 24000L;
        return time < 12000L;
    }

    private boolean isRaining() {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null) {
            return false;
        }
        return client.world.isRaining() || client.world.hasRain(client.player.getBlockPos());
    }

    private boolean isKeyPressed(String keyName) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return false;
        }
        Integer keyCode = resolveKeyCode(keyName);
        if (keyCode == null) {
            return false;
        }
        return InputCompatibilityBridge.isKeyPressed(client, keyCode);
    }

    private Integer resolveKeyCode(String keyName) {
        if (keyName == null) {
            return null;
        }
        String trimmed = keyName.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(trimmed);
        } catch (NumberFormatException ignored) {
        }
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
        InputUtil.Key normalizedKey = InputUtil.fromTranslationKey("key.keyboard." + normalized);
        int normalizedCode = normalizedKey.getCode();
        if (normalizedCode != GLFW.GLFW_KEY_UNKNOWN) {
            return normalizedCode;
        }
        return null;
    }

    private Integer resolveGlfwKeyCode(String keyName) {
        String normalized = keyName.trim().toUpperCase(Locale.ROOT).replace(" ", "_");
        if (!normalized.startsWith("GLFW_KEY_")) {
            normalized = "GLFW_KEY_" + normalized;
        }
        try {
            Field field = GLFW.class.getField(normalized);
            if (field.getType() == int.class) {
                return field.getInt(null);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return null;
    }

    private boolean isHealthBelow(double amount) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        return client.player.getHealth() < amount;
    }

    private boolean isHungerBelow(int amount) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        return client.player.getHungerManager().getFoodLevel() < amount;
    }

    private boolean isEntityNearby(String entityId, double range) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || entityId == null || entityId.isEmpty()) {
            return false;
        }
        net.minecraft.world.World world = EntityCompatibilityBridge.getWorld(client.player);
        if (world == null) {
            return false;
        }
        Box searchBox = client.player.getBoundingBox().expand(range);
        for (String candidateId : splitMultiValueList(entityId)) {
            String sanitized = sanitizeResourceId(candidateId);
            String normalized = sanitized != null && !sanitized.isEmpty()
                ? normalizeResourceId(sanitized, "minecraft")
                : candidateId;
            Identifier identifier = Identifier.tryParse(normalized);
            if (identifier == null || !Registries.ENTITY_TYPE.containsId(identifier)) {
                continue;
            }
            EntityType<?> entityType = Registries.ENTITY_TYPE.get(identifier);
            List<Entity> entities = world.getOtherEntities(
                client.player,
                searchBox,
                entity -> entity.getType() == entityType
            );
            if (!entities.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasItemInInventory(String itemId) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || itemId == null || itemId.isEmpty()) {
            return false;
        }
        for (String candidateId : splitMultiValueList(itemId)) {
            String sanitized = sanitizeResourceId(candidateId);
            String normalized = sanitized != null && !sanitized.isEmpty()
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

    private boolean hasItemAmountInInventory(String itemId, int requiredAmount) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || itemId == null || itemId.isEmpty()) {
            return false;
        }
        int needed = Math.max(1, requiredAmount);
        for (String candidateId : splitMultiValueList(itemId)) {
            String sanitized = sanitizeResourceId(candidateId);
            String normalized = sanitized != null && !sanitized.isEmpty()
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
        if (stack == null || stack.isEmpty() || itemIds == null || itemIds.isEmpty()) {
            return false;
        }
        for (String candidateId : itemIds) {
            String sanitized = sanitizeResourceId(candidateId);
            String normalized = sanitized != null && !sanitized.isEmpty()
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
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null || resourceId == null || resourceId.isEmpty()) {
            return false;
        }
        String trimmed = resourceId.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        if (trimmed.indexOf(',') >= 0) {
            String[] parts = trimmed.split(",");
            for (String part : parts) {
                if (part != null && !part.trim().isEmpty() && isResourceRendered(part.trim())) {
                    return true;
                }
            }
            return false;
        }
        return isSingleResourceRendered(client, trimmed);
    }

    private boolean isEntityRendered(String entityId, String state) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null || entityId == null || entityId.isEmpty()) {
            return false;
        }
        for (String candidateId : splitMultiValueList(entityId)) {
            String sanitized = sanitizeResourceId(candidateId);
            String normalized = sanitized != null && !sanitized.isEmpty()
                ? normalizeResourceId(sanitized, "minecraft")
                : candidateId;
            Identifier identifier = Identifier.tryParse(normalized);
            if (identifier == null || !Registries.ENTITY_TYPE.containsId(identifier)) {
                continue;
            }
            EntityType<?> entityType = Registries.ENTITY_TYPE.get(identifier);
            if (isEntityRendered(client, entityType, state)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSingleResourceRendered(net.minecraft.client.MinecraftClient client, String resourceId) {
        if (client == null || client.player == null || client.world == null || resourceId == null || resourceId.isEmpty()) {
            return false;
        }
        Optional<BlockSelection> selectionOptional = BlockSelection.parse(resourceId);
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
                EntityType<?> entityType = Registries.ENTITY_TYPE.get(identifier);
                return isEntityRendered(client, entityType, "");
            }
        }
        return isPlayerRendered(client, resourceId);
    }

    private boolean isBlockRendered(net.minecraft.client.MinecraftClient client, Block block) {
        return isBlockRendered(client, block, null);
    }

    private boolean isBlockRendered(net.minecraft.client.MinecraftClient client, BlockSelection selection) {
        if (selection == null) {
            return false;
        }
        Block block = selection.getBlock();
        if (block == null) {
            return false;
        }
        return isBlockRendered(client, block, selection);
    }

    private boolean isBlockRendered(net.minecraft.client.MinecraftClient client, Block block, BlockSelection selection) {
        if (client == null || client.player == null || client.world == null || block == null) {
            return false;
        }

        HitResult hitResult = client.crosshairTarget;
        if (hitResult instanceof BlockHitResult blockHit) {
            BlockPos hitPos = blockHit.getBlockPos();
            BlockState state = client.world.getBlockState(hitPos);
            boolean matches = selection != null ? selection.matches(state) : state.isOf(block);
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
                    mutable.set(playerPos.getX() + dx, playerPos.getY() + dy, playerPos.getZ() + dz);
                    BlockState state = client.world.getBlockState(mutable);
                    boolean matches = selection != null ? selection.matches(state) : state.isOf(block);
                    if (matches && isBlockVisible(client, mutable)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isBlockVisible(net.minecraft.client.MinecraftClient client, BlockPos pos) {
        if (client == null || client.player == null || client.world == null) {
            return false;
        }
        Vec3d cameraPos = CameraCompatibilityBridge.getPos(client.gameRenderer.getCamera());
        Vec3d target = Vec3d.ofCenter(pos);
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
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
    }

    private boolean isItemRendered(net.minecraft.client.MinecraftClient client, Item item) {
        if (client == null || client.player == null || client.world == null || item == null) {
            return false;
        }

        if (client.player.getMainHandStack().isOf(item) || client.player.getOffHandStack().isOf(item)) {
            return true;
        }

        HitResult hitResult = client.crosshairTarget;
        if (hitResult instanceof EntityHitResult entityHit) {
            Entity targetEntity = entityHit.getEntity();
            if (targetEntity instanceof ItemEntity itemEntity && !itemEntity.getStack().isEmpty() && itemEntity.getStack().isOf(item)) {
                return true;
            }
        }

        double renderDistance = Math.max(8.0, client.options.getViewDistance().getValue() * 4.0);
        Box searchBox = client.player.getBoundingBox().expand(renderDistance);
        List<ItemEntity> candidates = client.world.getEntitiesByClass(
            ItemEntity.class,
            searchBox,
            entity -> entity != null && !entity.isRemoved() && !entity.getStack().isEmpty()
                && entity.getStack().isOf(item) && client.player.canSee(entity)
        );
        return !candidates.isEmpty();
    }

    private boolean isEntityRendered(net.minecraft.client.MinecraftClient client, EntityType<?> entityType, String state) {
        if (client == null || client.player == null || client.world == null || entityType == null) {
            return false;
        }

        HitResult hitResult = client.crosshairTarget;
        if (hitResult instanceof EntityHitResult entityHit
            && entityHit.getEntity() != null
            && entityHit.getEntity().getType() == entityType
            && EntityStateOptions.matchesState(entityHit.getEntity(), state)) {
            return true;
        }

        double renderDistance = Math.max(8.0, client.options.getViewDistance().getValue() * 4.0);
        Box searchBox = client.player.getBoundingBox().expand(renderDistance);
        List<Entity> matches = client.world.getOtherEntities(
            client.player,
            searchBox,
            entity -> entity != null
                && entity.isAlive()
                && entity.getType() == entityType
                && EntityStateOptions.matchesState(entity, state)
        );
        return !matches.isEmpty();
    }

    private boolean isPlayerRendered(net.minecraft.client.MinecraftClient client, String playerName) {
        if (client == null || client.player == null || client.world == null || playerName == null || playerName.isEmpty()) {
            return false;
        }

        String trimmed = playerName.trim();
        if (trimmed.isEmpty()) {
            return false;
        }

        HitResult hitResult = client.crosshairTarget;
        if (hitResult instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof AbstractClientPlayerEntity targetPlayer) {
            if (trimmed.equalsIgnoreCase(
                GameProfileCompatibilityBridge.getName(targetPlayer.getGameProfile()))) {
                return true;
            }
        }

        double renderDistance = Math.max(8.0, client.options.getViewDistance().getValue() * 4.0);
        for (AbstractClientPlayerEntity playerEntity : client.world.getPlayers()) {
            if (playerEntity == null || !playerEntity.isAlive()) {
                continue;
            }
            if (!trimmed.equalsIgnoreCase(
                GameProfileCompatibilityBridge.getName(playerEntity.getGameProfile()))) {
                continue;
            }
            if (playerEntity.squaredDistanceTo(client.player) > renderDistance * renderDistance) {
                continue;
            }
            return true;
        }

        return false;
    }

    private boolean isSwimming() {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        return client != null && client.player != null && client.player.isSwimming();
    }

    private boolean isInLava() {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        return client != null && client.player != null && client.player.isInLava();
    }

    private boolean isUnderwater() {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        return client != null && client.player != null && client.player.isSubmergedInWater();
    }

    private boolean isOnGround() {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        return client != null && client.player != null && client.player.isOnGround();
    }

    private boolean isFalling(double distance) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        return client.player.fallDistance >= distance && !client.player.isOnGround();
    }
    
    private void executeCommand(String command) {
        try {
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            if (client != null && client.player != null) {
                client.player.networkHandler.sendChatMessage(command);
                System.out.println("Sent command to Minecraft: " + command);
            } else {
                System.out.println("Cannot execute command - client or player is null");
            }
        } catch (Exception e) {
            System.err.println("Error executing command: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    
}
