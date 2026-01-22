package com.pathmind.ui.sidebar;

import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeCategory;
import com.pathmind.nodes.NodeType;
import com.pathmind.util.BaritoneDependencyChecker;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.pathmind.util.DrawContextBridge;

/**
 * Manages the sidebar with categorized draggable nodes.
 * Features a nested sidebar design with colored tabs like sticky notes.
 */
public class Sidebar {
    // Outer sidebar dimensions
    private static final int OUTER_SIDEBAR_WIDTH = 180;
    private static final int INNER_SIDEBAR_WIDTH = 40;
    private static final int TAB_SIZE = 24;
    private static final int TAB_SPACING = 8;
    private static final int TOP_PADDING = 8;
    private static final int TAB_COLUMNS = 2;
    private static final int TAB_COLUMN_MARGIN = 8;
    private static final int TAB_COLUMN_SPACING = 8;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLLBAR_MARGIN = 4;
    private static final int SCROLLBAR_MIN_KNOB_HEIGHT = 20;
    
    // Node display dimensions
    private static final int NODE_HEIGHT = 18;
    private static final int PADDING = 4;
    private static final int CATEGORY_HEADER_HEIGHT = 20;
    private static final int CATEGORY_HEADER_LINE_SPACING = 2;
    private static final int GROUP_HEADER_HEIGHT = 16;
    private static final int GROUP_HEADER_LINE_SPACING = 2;
    
    // Colors
    private static final int DARK_GREY_ALT = 0xFF2A2A2A;
    private static final int DARKER_GREY = 0xFF1F1F1F;
    private static final int WHITE_MUTED = 0xFFE0E0E0;
    private static final int GREY_LINE = 0xFF666666;
    private static final int HOVER_COLOR = 0xFF404040;
    
    private final Map<NodeCategory, List<NodeType>> categoryNodes;
    private final Map<NodeCategory, List<NodeGroup>> groupedCategoryNodes;
    private final Map<NodeCategory, Boolean> categoryExpanded;
    private final boolean baritoneAvailable;
    private NodeType hoveredNodeType = null;
    private NodeCategory hoveredCategory = null;
    private NodeCategory selectedCategory = null;
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private int currentSidebarHeight = 400; // Store current sidebar height
    private int currentInnerSidebarWidth = INNER_SIDEBAR_WIDTH;
    
    public Sidebar() {
        this(BaritoneDependencyChecker.isBaritoneApiPresent());
    }

    public Sidebar(boolean baritoneAvailable) {
        this.categoryExpanded = new HashMap<>();
        this.categoryNodes = new HashMap<>();
        this.groupedCategoryNodes = new HashMap<>();
        this.baritoneAvailable = baritoneAvailable;
        
        // Initialize categories as expanded by default
        for (NodeCategory category : NodeCategory.values()) {
            categoryExpanded.put(category, true);
        }

        // Organize nodes by category
        initializeCategoryNodes();
        calculateMaxScroll(400); // Default height for initialization
    }
    
    private void initializeCategoryNodes() {
        for (NodeCategory category : NodeCategory.values()) {
            List<NodeType> nodes = new ArrayList<>();

            if (category == NodeCategory.PARAMETERS) {
                List<NodeGroup> parameterGroups = createParameterGroups();
                groupedCategoryNodes.put(category, parameterGroups);
                for (NodeGroup group : parameterGroups) {
                    nodes.addAll(group.getNodes());
                }
            } else if (category == NodeCategory.SENSORS) {
                List<NodeGroup> sensorGroups = createSensorGroups();
                groupedCategoryNodes.put(category, sensorGroups);
                for (NodeGroup group : sensorGroups) {
                    nodes.addAll(group.getNodes());
                }
            } else {
                for (NodeType nodeType : NodeType.values()) {
                    if (nodeType == NodeType.PARAM_PLACE_TARGET) {
                        continue;
                    }
                    if (nodeType.getCategory() == category && shouldIncludeNode(nodeType)) {
                        nodes.add(nodeType);
                    }
                }
            }

            categoryNodes.put(category, nodes);
        }
    }

    private List<NodeGroup> createParameterGroups() {
        List<NodeGroup> groups = new ArrayList<>();
        groups.add(new NodeGroup(
            "Spatial Data",
            baritoneAvailable,
            NodeType.PARAM_COORDINATE,
            NodeType.PARAM_ROTATION,
            NodeType.PARAM_RANGE,
            NodeType.PARAM_DISTANCE,
            NodeType.PARAM_CLOSEST
        ));
        groups.add(new NodeGroup(
            "Targets & Objects",
            baritoneAvailable,
            NodeType.PARAM_BLOCK,
            NodeType.PARAM_ITEM,
            NodeType.PARAM_ENTITY,
            NodeType.PARAM_PLAYER,
            NodeType.PARAM_WAYPOINT,
            NodeType.PARAM_SCHEMATIC
        ));
        groups.add(new NodeGroup(
            "Inventory & Equipment",
            baritoneAvailable,
            NodeType.PARAM_INVENTORY_SLOT,
            NodeType.PARAM_HAND
        ));
        groups.add(new NodeGroup(
            "Input",
            baritoneAvailable,
            NodeType.PARAM_KEY
        ));
        groups.add(new NodeGroup(
            "Utility Data",
            baritoneAvailable,
            NodeType.PARAM_DURATION,
            NodeType.PARAM_AMOUNT,
            NodeType.PARAM_BOOLEAN
        ));
        return groups;
    }

    private List<NodeGroup> createSensorGroups() {
        List<NodeGroup> groups = new ArrayList<>();
        groups.add(new NodeGroup(
            "Player State",
            baritoneAvailable,
            NodeType.SENSOR_IS_SWIMMING,
            NodeType.SENSOR_IS_IN_LAVA,
            NodeType.SENSOR_IS_UNDERWATER,
            NodeType.SENSOR_IS_ON_GROUND,
            NodeType.SENSOR_IS_FALLING
        ));
        groups.add(new NodeGroup(
            "Input",
            baritoneAvailable,
            NodeType.SENSOR_KEY_PRESSED
        ));
        groups.add(new NodeGroup(
            "Position & Blocks",
            baritoneAvailable,
            NodeType.SENSOR_AT_COORDINATES,
            NodeType.SENSOR_TOUCHING_BLOCK,
            NodeType.SENSOR_BLOCK_AHEAD,
            NodeType.SENSOR_BLOCK_BELOW
        ));
        groups.add(new NodeGroup(
            "Entities & Visibility",
            baritoneAvailable,
            NodeType.SENSOR_TOUCHING_ENTITY,
            NodeType.SENSOR_ENTITY_NEARBY,
            NodeType.SENSOR_IS_RENDERED
        ));
        groups.add(new NodeGroup(
            "Inventory & Items",
            baritoneAvailable,
            NodeType.SENSOR_ITEM_IN_INVENTORY
        ));
        groups.add(new NodeGroup(
            "Player Stats",
            baritoneAvailable,
            NodeType.SENSOR_HEALTH_BELOW,
            NodeType.SENSOR_HUNGER_BELOW
        ));
        groups.add(new NodeGroup(
            "Environment & Weather",
            baritoneAvailable,
            NodeType.SENSOR_IS_DAYTIME,
            NodeType.SENSOR_IS_RAINING,
            NodeType.SENSOR_LIGHT_LEVEL_BELOW
        ));
        return groups;
    }

    private boolean shouldIncludeNode(NodeType nodeType) {
        if (nodeType == null || !nodeType.isDraggableFromSidebar()) {
            return false;
        }
        if (baritoneAvailable) {
            return true;
        }
        return !nodeType.requiresBaritone();
    }

    private boolean hasGroupedContent(NodeCategory category) {
        List<NodeGroup> groups = groupedCategoryNodes.get(category);
        if (groups == null) {
            return false;
        }
        for (NodeGroup group : groups) {
            if (!group.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private List<NodeGroup> getGroupsForCategory(NodeCategory category) {
        List<NodeGroup> groups = groupedCategoryNodes.get(category);
        return groups != null ? groups : java.util.Collections.emptyList();
    }
    
    private void calculateMaxScroll(int sidebarHeight) {
        calculateMaxScroll(sidebarHeight, 0, null);
    }

    private void calculateMaxScroll(int sidebarHeight, int headerHeight, List<GroupHeaderInfo> groupHeaders) {
        int totalHeight = 0;
        
        // Add space for category header and nodes (content starts at top)
        if (selectedCategory != null) {
            totalHeight += Math.max(CATEGORY_HEADER_HEIGHT, headerHeight);
            
            if (groupHeaders != null && !groupHeaders.isEmpty()) {
                for (GroupHeaderInfo info : groupHeaders) {
                    totalHeight += info.getHeight();
                    totalHeight += info.getGroup().getNodes().size() * NODE_HEIGHT;
                }
            } else if (hasGroupedContent(selectedCategory)) {
                for (NodeGroup group : getGroupsForCategory(selectedCategory)) {
                    if (group.isEmpty()) {
                        continue;
                    }
                    totalHeight += GROUP_HEADER_HEIGHT;
                    totalHeight += group.getNodes().size() * NODE_HEIGHT;
                }
            } else {
                // Add space for nodes in selected category
                List<NodeType> nodes = categoryNodes.get(selectedCategory);
                if (nodes != null) {
                    totalHeight += nodes.size() * NODE_HEIGHT;
                }
            }
        }
        
        // Add padding
        totalHeight += PADDING * 2;
        
        // Calculate max scroll with proper room for scrolling
        maxScroll = Math.max(0, totalHeight - sidebarHeight + 100); // Extra 100px for better scrolling
    }
    
    public void render(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, int sidebarStartY, int sidebarHeight) {
        // Store current sidebar height so scroll can be recalculated
        this.currentSidebarHeight = sidebarHeight;
        
        NodeCategory[] categories = NodeCategory.values();
        int totalVisibleTabs = 0;
        for (NodeCategory category : categories) {
            if (!categoryNodes.get(category).isEmpty()) {
                totalVisibleTabs++;
            }
        }

        int availableTabHeight = Math.max(TAB_SIZE, sidebarHeight - TOP_PADDING * 2);
        int rowsFromHeight = Math.max(1, (availableTabHeight + TAB_SPACING) / (TAB_SIZE + TAB_SPACING));
        int minRowsNeeded = totalVisibleTabs > 0 ? (int) Math.ceil(totalVisibleTabs / (double) TAB_COLUMNS) : 1;
        int rowsPerColumn = Math.max(rowsFromHeight, minRowsNeeded);
        int columnsUsed = totalVisibleTabs > 0 ? (int) Math.ceil(totalVisibleTabs / (double) rowsPerColumn) : 1;
        columnsUsed = Math.min(columnsUsed, TAB_COLUMNS);

        if (columnsUsed > 1) {
            currentInnerSidebarWidth = INNER_SIDEBAR_WIDTH * 2 - 6;
        } else {
            currentInnerSidebarWidth = INNER_SIDEBAR_WIDTH;
        }

        int totalWidth = getWidth();
        int contentTextX = currentInnerSidebarWidth + 8;
        int contentTextRight = totalWidth - SCROLLBAR_MARGIN - SCROLLBAR_WIDTH - 4;
        int maxContentWidth = Math.max(1, contentTextRight - contentTextX);

        List<String> headerLines = null;
        int headerHeight = 0;
        final int headerLineHeight = textRenderer.fontHeight + CATEGORY_HEADER_LINE_SPACING;
        final int groupLineHeight = textRenderer.fontHeight + GROUP_HEADER_LINE_SPACING;
        if (selectedCategory != null) {
            headerLines = wrapText(selectedCategory.getDisplayName(), textRenderer, maxContentWidth);
            headerHeight = Math.max(CATEGORY_HEADER_HEIGHT, headerLines.size() * headerLineHeight);
        }

        List<GroupHeaderInfo> groupHeaders = null;
        if (selectedCategory != null && hasGroupedContent(selectedCategory)) {
            groupHeaders = new ArrayList<>();
            for (NodeGroup group : getGroupsForCategory(selectedCategory)) {
                if (group.isEmpty()) {
                    continue;
                }
                List<String> lines = wrapText(group.getTitle(), textRenderer, maxContentWidth);
                int height = Math.max(GROUP_HEADER_HEIGHT, lines.size() * groupLineHeight);
                groupHeaders.add(new GroupHeaderInfo(group, lines, height));
            }
        }

        calculateMaxScroll(sidebarHeight, headerHeight, groupHeaders);
        
        // Outer sidebar background
        int outerColor = totalWidth > currentInnerSidebarWidth ? DARK_GREY_ALT : DARKER_GREY;
        context.fill(0, sidebarStartY, totalWidth, sidebarStartY + sidebarHeight, outerColor);
        context.drawVerticalLine(totalWidth, sidebarStartY, sidebarStartY + sidebarHeight, GREY_LINE);
        
        // Inner sidebar background (for tabs)
        context.fill(0, sidebarStartY, currentInnerSidebarWidth, sidebarStartY + sidebarHeight, DARKER_GREY);
        context.drawVerticalLine(currentInnerSidebarWidth, sidebarStartY, sidebarStartY + sidebarHeight, GREY_LINE);

        // Tabs stay static (don't scroll with content)
        int currentY = sidebarStartY + TOP_PADDING;

        // Render colored tabs
        hoveredCategory = null;
        // rowsPerColumn already calculated above

        int visibleTabIndex = 0; // Track visible tabs separately from array index
        for (int i = 0; i < categories.length; i++) {
            NodeCategory category = categories[i];

            // Skip if category has no nodes
            if (categoryNodes.get(category).isEmpty()) {
                continue;
            }

            int column = visibleTabIndex / rowsPerColumn;
            int row = visibleTabIndex % rowsPerColumn;
            int tabY = currentY + row * (TAB_SIZE + TAB_SPACING);
            int tabX = TAB_COLUMN_MARGIN + column * (TAB_SIZE + TAB_COLUMN_SPACING);
            visibleTabIndex++; // Increment only for visible tabs
            
            // Check if tab is hovered
            boolean tabHovered = mouseX >= tabX && mouseX <= tabX + TAB_SIZE && 
                               mouseY >= tabY && mouseY < tabY + TAB_SIZE;
            
            // Check if tab is selected
            boolean tabSelected = category == selectedCategory;
            
            // Tab background color
            int baseColor = category.getColor();
            int tabColor = baseColor;
            if (tabSelected) {
                // Darken the category color for selected state
                tabColor = darkenColor(baseColor, 0.75f);
            } else if (tabHovered) {
                tabColor = lightenColor(baseColor, 1.2f);
            }
            
            // Render square tab
            context.fill(tabX, tabY, tabX + TAB_SIZE, tabY + TAB_SIZE, tabColor);
            
            // Tab outline slightly darker than base color
            int outlineColor = darkenColor(baseColor, 0.8f);
            DrawContextBridge.drawBorder(context, tabX, tabY, TAB_SIZE, TAB_SIZE, outlineColor);
            
            // Render centered icon with bigger appearance
            String icon = category.getIcon();
            int iconX = tabX + (TAB_SIZE - textRenderer.getWidth(icon)) / 2;
            int iconY = tabY + (TAB_SIZE - textRenderer.fontHeight) / 2 + 1;
            
            context.drawTextWithShadow(textRenderer, icon, iconX, iconY, 0xFFFFFFFF);
            
            // Update hover state
            if (tabHovered) {
                hoveredCategory = category;
            }
        }
        
        // Render category name and nodes for selected category
        if (selectedCategory != null) {
            int contentTop = sidebarStartY + PADDING;
            int contentBottom = sidebarStartY + sidebarHeight - PADDING;
            // Start content area at the very top of the sidebar, right after the title bar
            int contentY = contentTop - scrollOffset;
            int sidebarBottom = sidebarStartY + sidebarHeight;
            int nodeBackgroundLeft = currentInnerSidebarWidth + 1; // Keep divider line visible by offsetting fills
            
            // Category header
            int headerTextX = contentTextX;
            int headerTextY = contentY + 4;
            if (headerLines != null && !headerLines.isEmpty()) {
                for (String line : headerLines) {
                    context.drawTextWithShadow(
                        textRenderer,
                        Text.literal(line),
                        headerTextX,
                        headerTextY,
                        selectedCategory.getColor()
                    );
                    headerTextY += headerLineHeight;
                }
            }

            contentY += headerHeight;
            
            hoveredNodeType = null;

            if (hasGroupedContent(selectedCategory)) {
                outer:
                if (groupHeaders != null) {
                    for (GroupHeaderInfo groupInfo : groupHeaders) {
                        NodeGroup group = groupInfo.getGroup();

                        if (contentY >= sidebarBottom) {
                            break;
                        }

                        int groupTextY = contentY + 2;
                        for (String line : groupInfo.getLines()) {
                            context.drawTextWithShadow(
                                textRenderer,
                                Text.literal(line),
                                contentTextX,
                                groupTextY,
                                GREY_LINE
                            );
                            groupTextY += groupLineHeight;
                        }

                        contentY += groupInfo.getHeight();

                        for (NodeType nodeType : group.getNodes()) {
                            if (contentY >= sidebarBottom) {
                                break outer;
                            }

                            boolean nodeHovered = mouseX >= nodeBackgroundLeft && mouseX <= totalWidth &&
                                                mouseY >= contentY && mouseY < contentY + NODE_HEIGHT;

                            if (nodeHovered) {
                                hoveredNodeType = nodeType;
                                context.fill(nodeBackgroundLeft, contentY, totalWidth, contentY + NODE_HEIGHT, HOVER_COLOR);
                            }

                            int indicatorSize = 12;
                            int indicatorX = currentInnerSidebarWidth + 8;
                            int indicatorY = contentY + 3;
                            context.fill(indicatorX, indicatorY, indicatorX + indicatorSize, indicatorY + indicatorSize, nodeType.getColor());
                            DrawContextBridge.drawBorder(context, indicatorX, indicatorY, indicatorSize, indicatorSize, 0xFF000000);

                            context.drawTextWithShadow(
                                textRenderer,
                                Text.literal(nodeType.getDisplayName()),
                                indicatorX + indicatorSize + 4,
                                contentY + 4,
                                WHITE_MUTED
                            );

                            contentY += NODE_HEIGHT;
                        }
                    }
                }
            } else {
                // Render nodes in selected category
                List<NodeType> nodes = categoryNodes.get(selectedCategory);
                if (nodes != null) {
                    for (NodeType nodeType : nodes) {
                        if (contentY >= sidebarBottom) break; // Don't render beyond sidebar
                        
                        boolean nodeHovered = mouseX >= nodeBackgroundLeft && mouseX <= totalWidth &&
                                            mouseY >= contentY && mouseY < contentY + NODE_HEIGHT;

                        if (nodeHovered) {
                            hoveredNodeType = nodeType;
                            context.fill(nodeBackgroundLeft, contentY, totalWidth, contentY + NODE_HEIGHT, HOVER_COLOR);
                        }

                        int indicatorSize = 12;
                        int indicatorX = currentInnerSidebarWidth + 8; // Align with category title
                        int indicatorY = contentY + 3;
                        context.fill(indicatorX, indicatorY, indicatorX + indicatorSize, indicatorY + indicatorSize, nodeType.getColor());
                        DrawContextBridge.drawBorder(context, indicatorX, indicatorY, indicatorSize, indicatorSize, 0xFF000000);
                        
                        context.drawTextWithShadow(
                            textRenderer,
                            Text.literal(nodeType.getDisplayName()),
                            indicatorX + indicatorSize + 4, // Position after the indicator with some spacing
                            contentY + 4,
                            WHITE_MUTED
                        );
                        
                        contentY += NODE_HEIGHT;
                    }
                }
            }

            renderCategoryScrollbar(context, totalWidth, contentTop, contentBottom);
        }
        
        // Reset hover states if mouse is not in sidebar
        if (mouseX < 0 || mouseX > getWidth()) {
            hoveredNodeType = null;
            hoveredCategory = null;
        }
    }
    
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX < 0 || mouseX > getWidth()) {
            return false;
        }
        
        if (button == 0) { // Left click
            // Check tab clicks
            if (mouseX >= 0 && mouseX <= currentInnerSidebarWidth && hoveredCategory != null) {
                if (selectedCategory != null && hoveredCategory == selectedCategory) {
                    selectedCategory = null;
                } else {
                    selectedCategory = hoveredCategory;
                }
                // Clear any hovered node when switching or collapsing categories
                hoveredNodeType = null;
                // Reset scroll to top when changing categories
                scrollOffset = 0;
                calculateMaxScroll(currentSidebarHeight);
                return true;
            }
            
            // Check node clicks for dragging
            if (hoveredNodeType != null) {
                return true; // Signal that dragging should start
            }
        }
        
        return false;
    }
    
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (mouseX >= 0 && mouseX <= getWidth()) {
            scrollOffset += (int)(-amount * 20); // Scroll speed
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
            return true;
        }
        return false;
    }
    
    public NodeType getHoveredNodeType() {
        return hoveredNodeType;
    }
    
    public boolean isHoveringNode() {
        return hoveredNodeType != null;
    }
    
    public Node createNodeFromSidebar(int x, int y) {
        if (hoveredNodeType != null) {
            return new Node(hoveredNodeType, x, y);
        }
        return null;
    }
    
    public int getWidth() {
        return selectedCategory != null ? OUTER_SIDEBAR_WIDTH : currentInnerSidebarWidth;
    }

    /**
     * Returns the width of the sidebar when no category is expanded.
     * This is used for layout calculations that should remain stable even
     * when a category is opened (which visually overlays the workspace).
     */
    public static int getCollapsedWidth() {
        return INNER_SIDEBAR_WIDTH;
    }
    
    /**
     * Darkens a color by the specified factor
     */
    private int darkenColor(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = (int) (((color >> 16) & 0xFF) * factor);
        int g = (int) (((color >> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
    
    /**
     * Lightens a color by the specified factor
     */
    private int lightenColor(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) * factor));
        int g = Math.min(255, (int) (((color >> 8) & 0xFF) * factor));
        int b = Math.min(255, (int) ((color & 0xFF) * factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private List<String> wrapText(String text, TextRenderer textRenderer, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }

        if (maxWidth <= 0) {
            lines.add(text);
            return lines;
        }

        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }

            if (currentLine.length() > 0) {
                String candidate = currentLine + " " + word;
                if (textRenderer.getWidth(candidate) <= maxWidth) {
                    currentLine.append(" ").append(word);
                    continue;
                }
            }

            if (textRenderer.getWidth(word) <= maxWidth) {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                }
                currentLine = new StringBuilder(word);
            } else {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder();
                }

                String remaining = word;
                while (!remaining.isEmpty()) {
                    int breakIndex = findBreakIndex(remaining, textRenderer, maxWidth);
                    String part = remaining.substring(0, breakIndex);
                    lines.add(part);
                    remaining = remaining.substring(breakIndex);
                }
            }
        }

        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        if (lines.isEmpty()) {
            lines.add(text);
        }

        return lines;
    }

    private int findBreakIndex(String text, TextRenderer textRenderer, int maxWidth) {
        if (text.isEmpty() || maxWidth <= 0) {
            return Math.max(1, text.length());
        }

        int breakIndex = 1;
        while (breakIndex <= text.length() &&
            textRenderer.getWidth(text.substring(0, breakIndex)) <= maxWidth) {
            breakIndex++;
        }

        if (breakIndex > text.length()) {
            return text.length();
        }

        return Math.max(1, breakIndex - 1);
    }

    private void renderCategoryScrollbar(DrawContext context, int totalWidth, int contentTop, int contentBottom) {
        if (maxScroll <= 0 || contentBottom <= contentTop) {
            return;
        }

        int trackRight = totalWidth - SCROLLBAR_MARGIN;
        int trackLeft = trackRight - SCROLLBAR_WIDTH;
        int trackTop = contentTop;
        int trackBottom = contentBottom;
        int trackHeight = Math.max(1, trackBottom - trackTop);

        context.fill(trackLeft, trackTop, trackRight, trackBottom, 0xFF1A1A1A);
        DrawContextBridge.drawBorder(context, trackLeft, trackTop, SCROLLBAR_WIDTH, trackHeight, 0xFF444444);

        int visibleHeight = Math.max(1, contentBottom - contentTop);
        int totalScrollableHeight = Math.max(visibleHeight, visibleHeight + maxScroll);
        int knobHeight = Math.max(SCROLLBAR_MIN_KNOB_HEIGHT, (int) ((float) visibleHeight / totalScrollableHeight * trackHeight));
        int maxKnobTravel = Math.max(0, trackHeight - knobHeight);
        float scrollRatio = maxScroll > 0 ? (float) scrollOffset / maxScroll : 0;
        int knobOffset = maxKnobTravel <= 0 ? 0 : (int) (scrollRatio * maxKnobTravel);
        int knobTop = trackTop + knobOffset;

        context.fill(trackLeft + 1, knobTop, trackRight - 1, knobTop + knobHeight, 0xFF777777);
    }

    private static final class GroupHeaderInfo {
        private final NodeGroup group;
        private final List<String> lines;
        private final int height;

        private GroupHeaderInfo(NodeGroup group, List<String> lines, int height) {
            this.group = group;
            this.lines = lines;
            this.height = height;
        }

        public NodeGroup getGroup() {
            return group;
        }

        public List<String> getLines() {
            return lines;
        }

        public int getHeight() {
            return height;
        }
    }

    private static class NodeGroup {
        private final String title;
        private final List<NodeType> nodes;

        NodeGroup(String title, boolean includeBaritoneNodes, NodeType... nodeTypes) {
            this.title = title;
            this.nodes = new ArrayList<>();
            if (nodeTypes != null) {
                for (NodeType type : nodeTypes) {
                    if (type != null && type.isDraggableFromSidebar()
                        && (includeBaritoneNodes || !type.requiresBaritone())) {
                        this.nodes.add(type);
                    }
                }
            }
        }

        public String getTitle() {
            return title;
        }

        public List<NodeType> getNodes() {
            return nodes;
        }

        public boolean isEmpty() {
            return nodes.isEmpty();
        }
    }
}
