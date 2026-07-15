package com.pathmind.ui.sidebar;

import com.pathmind.data.NodeGraphData;
import com.pathmind.data.NodeGraphPersistence;
import com.pathmind.data.PresetManager;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeCatalog;
import com.pathmind.nodes.NodeCategory;
import com.pathmind.nodes.NodeType;
import com.pathmind.routines.RoutineBuilderModel;
import com.pathmind.ui.animation.AnimatedValue;
import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.ui.tooltip.TooltipRenderer;
import com.pathmind.util.BaritoneDependencyChecker;
import com.pathmind.util.UiUtilsDependencyChecker;
import com.pathmind.util.DrawContextBridge;
import com.pathmind.util.ScrollbarHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages the sidebar with categorized draggable nodes.
 * Uses a nested, beveled panel style with category tabs and grouped node lists.
 */
public class Sidebar {
    // Outer sidebar dimensions
    private static final int OUTER_SIDEBAR_WIDTH = 180;
    private static final int INNER_SIDEBAR_WIDTH = 40;
    private static final int TAB_SIZE = 24;
    private static final int TAB_SPACING = 8;
    private static final int TOP_PADDING = 8;
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
    private static final int NODE_LINE_SPACING = 1;

    private final Map<NodeCategory, List<NodeType>> categoryNodes;
    private final Map<NodeCategory, List<NodeGroup>> groupedCategoryNodes;
    private final Map<NodeCategory, Boolean> categoryExpanded;
    private final Map<NodeCategory, AnimatedValue> tabHoverAnimations;
    private final AnimatedValue categoryOpenAnimation;
    private final boolean baritoneAvailable;
    private final boolean uiUtilsAvailable;
    private NodeType hoveredNodeType = null;
    private CustomNodeEntry hoveredCustomNode = null;
    private NodeCategory hoveredCategory = null;
    private NodeCategory selectedCategory = null;
    private final List<CustomNodeEntry> customNodes = new ArrayList<>();
    private List<NodeGraphData.RoutineDefinitionData> routines = List.of();
    private String activeRoutineId = "";
    private NodeGraphData.RoutineDefinitionData hoveredRoutine = null;
    private NodeGraphData.RoutineInputData hoveredRoutineInput = null;
    private boolean hoveredCreateRoutine = false;
    private boolean createRoutineRequested = false;
    private boolean addRoutineInputRequested = false;
    private boolean hoveredAddRoutineInput = false;
    private String openRoutineRequested = null;
    private String routineInputActionId = null;
    private int routineInputAction = 0;
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private boolean scrollDragging = false;
    private int scrollDragOffset = 0;
    private int currentSidebarHeight = 400; // Store current sidebar height
    private int currentSidebarStartY = 0;
    private int currentInnerSidebarWidth = INNER_SIDEBAR_WIDTH;
    private int currentRenderedWidth = INNER_SIDEBAR_WIDTH;
    
    public Sidebar() {
        this(BaritoneDependencyChecker.isBaritonePresent(), UiUtilsDependencyChecker.isUiUtilsPresent());
    }

    public Sidebar(boolean baritoneAvailable, boolean uiUtilsAvailable) {
        this.categoryExpanded = new HashMap<>();
        this.categoryNodes = new HashMap<>();
        this.groupedCategoryNodes = new HashMap<>();
        this.tabHoverAnimations = new HashMap<>();
        this.categoryOpenAnimation = new AnimatedValue(0f, AnimationHelper::easeOutCubic);
        this.baritoneAvailable = baritoneAvailable;
        this.uiUtilsAvailable = uiUtilsAvailable;
        
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
            List<NodeGroup> groups = new ArrayList<>();
            for (NodeCatalog.SidebarGroup group : NodeCatalog.sidebarGroups(category, baritoneAvailable, uiUtilsAvailable)) {
                groups.add(new NodeGroup(group.displayCategory(), group.titleKey(), group.nodes()));
            }
            List<NodeType> nodes = new ArrayList<>();
            for (NodeGroup group : groups) {
                nodes.addAll(group.getNodes());
            }
            groupedCategoryNodes.put(category, groups);
            categoryNodes.put(category, nodes);
        }
        refreshCustomNodes();
    }

    private void refreshCustomNodes() {
        customNodes.clear();
        for (String presetName : PresetManager.getAvailablePresets()) {
            if (presetName == null || presetName.isBlank()) {
                continue;
            }
            customNodes.add(new CustomNodeEntry(presetName.trim()));
        }
    }

    public boolean isNodeAvailable(NodeType nodeType) {
        return NodeCatalog.shouldDisplayInSidebar(nodeType, baritoneAvailable, uiUtilsAvailable);
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
        calculateMaxScroll(sidebarHeight, 0, null, null);
    }

    private void calculateMaxScroll(int sidebarHeight, int headerHeight, List<GroupHeaderInfo> groupHeaders, List<NodeRowInfo> customNodeRows) {
        int totalHeight = 0;
        
        // Add space for category header and nodes (content starts at top)
        if (selectedCategory != null) {
            totalHeight += Math.max(CATEGORY_HEADER_HEIGHT, headerHeight);
            
            if (selectedCategory == NodeCategory.ROUTINES) {
                totalHeight += NODE_HEIGHT;
                totalHeight += routines.size() * NODE_HEIGHT;
                NodeGraphData.RoutineDefinitionData active = findRoutine(activeRoutineId);
                if (active != null) totalHeight += (active.getInputs().size() + 1) * NODE_HEIGHT;
            } else if (selectedCategory == NodeCategory.CUSTOM && customNodeRows != null) {
                for (NodeRowInfo info : customNodeRows) {
                    totalHeight += info.height();
                }
            } else if (groupHeaders != null && !groupHeaders.isEmpty()) {
                for (GroupHeaderInfo info : groupHeaders) {
                    totalHeight += info.getHeight();
                    for (NodeRowInfo row : info.getNodeRows()) {
                        totalHeight += row.height();
                    }
                }
            } else if (hasGroupedContent(selectedCategory)) {
                for (NodeGroup group : getGroupsForCategory(selectedCategory)) {
                    if (group.isEmpty()) {
                        continue;
                    }
                    totalHeight += GROUP_HEADER_HEIGHT;
                }
            } else {
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
    
    public void render(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY,
                       int sidebarStartY, int sidebarHeight, boolean interactionsEnabled, boolean showTooltips) {
        refreshCustomNodes();
        int effectiveMouseX = interactionsEnabled ? mouseX : Integer.MIN_VALUE;
        int effectiveMouseY = interactionsEnabled ? mouseY : Integer.MIN_VALUE;
        // Store current sidebar height so scroll can be recalculated
        this.currentSidebarStartY = sidebarStartY;
        this.currentSidebarHeight = sidebarHeight;
        categoryOpenAnimation.animateTo(selectedCategory != null ? 1f : 0f, UITheme.TRANSITION_ANIM_MS);
        categoryOpenAnimation.tick();
        float openProgress = categoryOpenAnimation.getValue();
        
        NodeCategory[] categories = NodeCategory.values();
        int totalVisibleTabs = 0;
        for (NodeCategory category : categories) {
            if (hasNodesInCategory(category)) {
                totalVisibleTabs++;
            }
        }

        int availableTabHeight = Math.max(TAB_SIZE, sidebarHeight - TOP_PADDING * 2);
        int tabSize = TAB_SIZE;
        int tabSpacing = TAB_SPACING;
        if (totalVisibleTabs > 0) {
            int defaultRequiredHeight = totalVisibleTabs * TAB_SIZE + (totalVisibleTabs - 1) * TAB_SPACING;
            if (defaultRequiredHeight > availableTabHeight) {
                float scale = availableTabHeight / (float) defaultRequiredHeight;
                tabSize = Math.max(12, Math.round(TAB_SIZE * scale));
                tabSpacing = Math.max(2, Math.round(TAB_SPACING * scale));

                int scaledRequiredHeight = totalVisibleTabs * tabSize + (totalVisibleTabs - 1) * tabSpacing;
                if (scaledRequiredHeight > availableTabHeight) {
                    tabSpacing = 2;
                    int maxSizeFromHeight = (availableTabHeight - (totalVisibleTabs - 1) * tabSpacing) / totalVisibleTabs;
                    tabSize = Math.max(8, maxSizeFromHeight);
                }
            }
        }

        currentInnerSidebarWidth = INNER_SIDEBAR_WIDTH;

        int totalWidth = currentInnerSidebarWidth
            + Math.round((OUTER_SIDEBAR_WIDTH - currentInnerSidebarWidth) * openProgress);
        if (totalWidth < currentInnerSidebarWidth) {
            totalWidth = currentInnerSidebarWidth;
        } else if (totalWidth > OUTER_SIDEBAR_WIDTH) {
            totalWidth = OUTER_SIDEBAR_WIDTH;
        }
        currentRenderedWidth = totalWidth;
        int contentTextX = currentInnerSidebarWidth + 8;
        int contentTextRight = totalWidth - SCROLLBAR_MARGIN - SCROLLBAR_WIDTH - 4;
        int maxContentWidth = Math.max(1, contentTextRight - contentTextX);
        int nodeLabelX = currentInnerSidebarWidth + 8 + 12 + 4;
        int nodeTextWidth = Math.max(1, contentTextRight - nodeLabelX);

        List<String> headerLines = null;
        int headerHeight = 0;
        final int headerLineHeight = textRenderer.fontHeight + CATEGORY_HEADER_LINE_SPACING;
        final int groupLineHeight = textRenderer.fontHeight + GROUP_HEADER_LINE_SPACING;
        final int nodeLineHeight = textRenderer.fontHeight + NODE_LINE_SPACING;
        if (selectedCategory != null) {
            headerLines = wrapText(selectedCategory.getDisplayName(), textRenderer, maxContentWidth);
            headerHeight = Math.max(CATEGORY_HEADER_HEIGHT, headerLines.size() * headerLineHeight);
        }

        List<GroupHeaderInfo> groupHeaders = null;
        List<NodeRowInfo> customNodeRows = null;
        if (selectedCategory != null && hasGroupedContent(selectedCategory)) {
            groupHeaders = new ArrayList<>();
            for (NodeGroup group : getGroupsForCategory(selectedCategory)) {
                if (group.isEmpty()) {
                    continue;
                }
                List<String> lines = wrapText(group.getTitle(), textRenderer, maxContentWidth);
                int height = Math.max(GROUP_HEADER_HEIGHT, lines.size() * groupLineHeight);
                groupHeaders.add(new GroupHeaderInfo(group, lines, height, buildNodeRows(group.getNodes(), textRenderer, nodeTextWidth, nodeLineHeight)));
            }
        } else if (selectedCategory == NodeCategory.CUSTOM) {
            customNodeRows = buildCustomNodeRows(textRenderer, nodeTextWidth, nodeLineHeight);
        }

        calculateMaxScroll(sidebarHeight, headerHeight, groupHeaders, customNodeRows);
        
        // Outer sidebar background
        int outerColor = totalWidth > currentInnerSidebarWidth ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_SIDEBAR;
        UIStyleHelper.drawPanel(context, 0, sidebarStartY, totalWidth, sidebarHeight, outerColor, UITheme.BORDER_SUBTLE);

        // Inner sidebar background (for tabs)
        context.fill(0, sidebarStartY, currentInnerSidebarWidth, sidebarStartY + sidebarHeight, UITheme.BACKGROUND_SIDEBAR);
        context.drawVerticalLine(currentInnerSidebarWidth, sidebarStartY, sidebarStartY + sidebarHeight, UITheme.BORDER_SUBTLE);

        // Tabs stay static (don't scroll with content)
        int currentY = sidebarStartY + TOP_PADDING;

        // Render colored tabs
        hoveredCategory = null;
        hoveredCustomNode = null;
        hoveredRoutine = null;
        hoveredRoutineInput = null;
        hoveredCreateRoutine = false;
        hoveredAddRoutineInput = false;
        int tabX = Math.max(0, (currentInnerSidebarWidth - tabSize) / 2);
        int visibleTabIndex = 0;
        for (int i = 0; i < categories.length; i++) {
            NodeCategory category = categories[i];

            // Skip if category has no nodes
            if (!hasNodesInCategory(category)) {
                continue;
            }

            int tabY = currentY + visibleTabIndex * (tabSize + tabSpacing);
            visibleTabIndex++;

            // Check if tab is hovered
            boolean tabHovered = effectiveMouseX >= tabX && effectiveMouseX <= tabX + tabSize &&
                               effectiveMouseY >= tabY && effectiveMouseY < tabY + tabSize;

            // Check if tab is selected
            boolean tabSelected = category == selectedCategory;

            // Get or create hover animation for this tab
            AnimatedValue hoverAnim = tabHoverAnimations.computeIfAbsent(category, k -> AnimatedValue.forHover());
            hoverAnim.animateTo(tabHovered ? 1f : 0f, UITheme.HOVER_ANIM_MS);
            hoverAnim.tick();
            float hoverProgress = hoverAnim.getValue();

            // Tab background color with smooth hover transition
            int baseColor = category.getColor();
            int normalColor = tabSelected ? darkenColor(baseColor, 0.75f) : baseColor;
            int hoverColor = lightenColor(baseColor, 1.2f);
            int tabColor = tabSelected ? normalColor : AnimationHelper.lerpColor(normalColor, hoverColor, hoverProgress);

            // Render square tab
            int outlineColor = darkenColor(baseColor, 0.7f);
            UIStyleHelper.drawBeveledPanel(context, tabX, tabY, tabSize, tabSize, tabColor, outlineColor, UITheme.PANEL_INNER_BORDER);

            // Render centered icon
            String icon = category.getIcon();
            int iconX = tabX + (tabSize - textRenderer.getWidth(icon)) / 2;
            int iconY = tabY + (tabSize - textRenderer.fontHeight) / 2 + 1;

            context.drawTextWithShadow(textRenderer, icon, iconX, iconY, UITheme.TEXT_HEADER);

            // Update hover state
            if (tabHovered) {
                hoveredCategory = category;
            }
        }
        
        // Render category name and nodes for selected category
        if (selectedCategory != null && openProgress > 0.001f) {
            int contentTop = sidebarStartY + PADDING;
            int contentBottom = sidebarStartY + sidebarHeight - PADDING;
            // Start content area at the very top of the sidebar, right after the title bar
            int contentY = contentTop - scrollOffset;
            int sidebarBottom = sidebarStartY + sidebarHeight;
            int nodeBackgroundLeft = currentInnerSidebarWidth + 1; // Keep divider line visible by offsetting fills
            ScrollbarHelper.Metrics scrollMetrics = getCategoryScrollMetrics();
            int nodeBackgroundRight = scrollMetrics != null && scrollMetrics.maxScroll() > 0
                ? scrollMetrics.trackLeft() - 2
                : totalWidth;
            int contentClipLeft = nodeBackgroundLeft;
            int contentClipRight = Math.min(totalWidth, contentTextRight + 2);
            if (contentClipRight <= contentClipLeft) {
                contentClipRight = contentClipLeft + 1;
            }

            context.enableScissor(contentClipLeft, contentTop, contentClipRight, contentBottom);
            
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

            if (selectedCategory == NodeCategory.ROUTINES) {
                int indicatorX = currentInnerSidebarWidth + 8;
                int labelX = indicatorX + 16;
                hoveredCreateRoutine = effectiveMouseX >= nodeBackgroundLeft && effectiveMouseX <= nodeBackgroundRight
                    && effectiveMouseY >= contentY && effectiveMouseY < contentY + NODE_HEIGHT;
                if (hoveredCreateRoutine) context.fill(nodeBackgroundLeft, contentY, nodeBackgroundRight, contentY + NODE_HEIGHT, UITheme.BACKGROUND_TERTIARY);
                context.drawTextWithShadow(textRenderer, Text.literal("+"), indicatorX + 3, contentY + 5, NodeCategory.ROUTINES.getColor());
                context.drawTextWithShadow(textRenderer, Text.translatable("pathmind.routine.create"), labelX, contentY + 5, UITheme.TEXT_PRIMARY);
                contentY += NODE_HEIGHT;
                for (NodeGraphData.RoutineDefinitionData routine : routines) {
                    boolean hovered = effectiveMouseX >= nodeBackgroundLeft && effectiveMouseX <= nodeBackgroundRight
                        && effectiveMouseY >= contentY && effectiveMouseY < contentY + NODE_HEIGHT;
                    if (hovered) {
                        hoveredRoutine = routine;
                        context.fill(nodeBackgroundLeft, contentY, nodeBackgroundRight, contentY + NODE_HEIGHT, UITheme.BACKGROUND_TERTIARY);
                    }
                    int routineSquareSize = 12;
                    int routineSquareY = contentY + (NODE_HEIGHT - routineSquareSize) / 2;
                    UIStyleHelper.drawBeveledPanel(context, indicatorX, routineSquareY, routineSquareSize, routineSquareSize,
                        NodeCategory.ROUTINES.getColor(), UITheme.BORDER_SUBTLE, UITheme.PANEL_INNER_BORDER);
                    context.drawTextWithShadow(textRenderer, Text.literal(routine.getName()), indicatorX + routineSquareSize + 5, contentY + 5,
                        routine.getId().equals(activeRoutineId) ? NodeCategory.ROUTINES.getColor() : UITheme.TEXT_PRIMARY);
                    context.drawTextWithShadow(textRenderer, Text.literal("›"), nodeBackgroundRight - 10, contentY + 5,
                        hovered ? UITheme.TEXT_HEADER : UITheme.TEXT_TERTIARY);
                    contentY += NODE_HEIGHT;
                    if (routine.getId().equals(activeRoutineId)) {
                        hoveredAddRoutineInput = effectiveMouseX >= nodeBackgroundLeft && effectiveMouseX <= nodeBackgroundRight
                            && effectiveMouseY >= contentY && effectiveMouseY < contentY + NODE_HEIGHT;
                        if (hoveredAddRoutineInput) context.fill(nodeBackgroundLeft, contentY, nodeBackgroundRight, contentY + NODE_HEIGHT, UITheme.BACKGROUND_TERTIARY);
                        context.drawTextWithShadow(textRenderer, Text.literal("    + Add input"), indicatorX, contentY + 5,
                            hoveredAddRoutineInput ? UITheme.TEXT_HEADER : UITheme.TEXT_SECONDARY);
                        contentY += NODE_HEIGHT;
                        for (NodeGraphData.RoutineInputData input : routine.getInputs()) {
                            boolean inputHovered = effectiveMouseX >= nodeBackgroundLeft && effectiveMouseX <= nodeBackgroundRight
                                && effectiveMouseY >= contentY && effectiveMouseY < contentY + NODE_HEIGHT;
                            if (inputHovered) {
                                hoveredRoutineInput = input;
                                context.fill(nodeBackgroundLeft, contentY, nodeBackgroundRight, contentY + NODE_HEIGHT, UITheme.BACKGROUND_TERTIARY);
                            }
                            context.drawTextWithShadow(textRenderer, Text.literal("    [" + input.getLabel() + "]"), indicatorX, contentY + 5,
                                inputHovered ? UITheme.TEXT_HEADER : UITheme.TEXT_SECONDARY);
                            context.drawTextWithShadow(textRenderer, Text.literal("↑ ↓ ×"), Math.max(labelX, nodeBackgroundRight - 34), contentY + 5,
                                inputHovered ? NodeCategory.ROUTINES.getColor() : UITheme.TEXT_TERTIARY);
                            contentY += NODE_HEIGHT;
                        }
                    }
                }
            } else if (selectedCategory == NodeCategory.CUSTOM) {
                if (customNodeRows != null) {
                    for (NodeRowInfo customNode : customNodeRows) {
                        int rowHeight = customNode.height();
                    if (contentY >= sidebarBottom) {
                        break;
                    }
                    boolean nodeHovered = effectiveMouseX >= nodeBackgroundLeft && effectiveMouseX <= nodeBackgroundRight
                        && effectiveMouseY >= contentY && effectiveMouseY < contentY + rowHeight;
                    if (nodeHovered) {
                        hoveredCustomNode = customNode.customNode();
                        context.fill(nodeBackgroundLeft, contentY, nodeBackgroundRight, contentY + rowHeight, UITheme.BACKGROUND_TERTIARY);
                    }

                    int indicatorSize = 12;
                    int indicatorX = currentInnerSidebarWidth + 8;
                    int indicatorY = contentY + Math.max(3, (rowHeight - indicatorSize) / 2);
                    UIStyleHelper.drawBeveledPanel(context, indicatorX, indicatorY, indicatorSize, indicatorSize,
                        NodeType.TEMPLATE.getColor(), UITheme.BORDER_SUBTLE, UITheme.PANEL_INNER_BORDER);

                        int lineY = contentY + 4;
                        for (String line : customNode.lines()) {
                            context.drawTextWithShadow(
                                textRenderer,
                                Text.literal(line),
                                indicatorX + indicatorSize + 4,
                                lineY,
                                UITheme.TEXT_PRIMARY
                            );
                            lineY += nodeLineHeight;
                        }
                        contentY += rowHeight;
                    }
                }
            } else if (hasGroupedContent(selectedCategory)) {
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
                                getSidebarGroupHeaderColor(group.getDisplayCategory())
                            );
                            groupTextY += groupLineHeight;
                        }

                        contentY += groupInfo.getHeight();

                        List<NodeRowInfo> groupNodes = groupInfo.getNodeRows();
                        for (int nodeIndex = 0; nodeIndex < groupNodes.size(); nodeIndex++) {
                            NodeRowInfo row = groupNodes.get(nodeIndex);
                            NodeType nodeType = row.nodeType();
                            int rowHeight = row.height();
                            if (contentY >= sidebarBottom) {
                                break outer;
                            }

                            boolean nodeHovered = effectiveMouseX >= nodeBackgroundLeft && effectiveMouseX <= nodeBackgroundRight &&
                                                effectiveMouseY >= contentY && effectiveMouseY < contentY + rowHeight;

                            if (nodeHovered) {
                                hoveredNodeType = nodeType;
                                context.fill(nodeBackgroundLeft, contentY, nodeBackgroundRight, contentY + rowHeight, UITheme.BACKGROUND_TERTIARY);
                            }

                            int indicatorSize = 12;
                            int indicatorX = currentInnerSidebarWidth + 8;
                            int indicatorY = contentY + Math.max(3, (rowHeight - indicatorSize) / 2);
                            UIStyleHelper.drawBeveledPanel(
                                context,
                                indicatorX,
                                indicatorY,
                                indicatorSize,
                                indicatorSize,
                                getSidebarNodeIndicatorColor(group.getDisplayCategory(), nodeType, nodeIndex),
                                UITheme.BORDER_SUBTLE,
                                UITheme.PANEL_INNER_BORDER
                            );

                            int lineY = contentY + 4;
                            for (String line : row.lines()) {
                                context.drawTextWithShadow(
                                    textRenderer,
                                    Text.literal(line),
                                    indicatorX + indicatorSize + 4,
                                    lineY,
                                    getSidebarNodeTextColor(group.getDisplayCategory(), nodeHovered)
                                );
                                lineY += nodeLineHeight;
                            }

                            contentY += rowHeight;
                        }
                    }
                }
            } else {
                // Render nodes in selected category
                List<NodeRowInfo> nodes = buildNodeRowsForCategory(selectedCategory, textRenderer, nodeTextWidth, nodeLineHeight);
                if (nodes != null) {
                    for (int nodeIndex = 0; nodeIndex < nodes.size(); nodeIndex++) {
                        NodeRowInfo row = nodes.get(nodeIndex);
                        NodeType nodeType = row.nodeType();
                        int rowHeight = row.height();
                        if (contentY >= sidebarBottom) break; // Don't render beyond sidebar
                        
                        boolean nodeHovered = effectiveMouseX >= nodeBackgroundLeft && effectiveMouseX <= nodeBackgroundRight &&
                                            effectiveMouseY >= contentY && effectiveMouseY < contentY + rowHeight;

                        if (nodeHovered) {
                            hoveredNodeType = nodeType;
                            context.fill(nodeBackgroundLeft, contentY, nodeBackgroundRight, contentY + rowHeight, UITheme.BACKGROUND_TERTIARY);
                        }

                        int indicatorSize = 12;
                        int indicatorX = currentInnerSidebarWidth + 8; // Align with category title
                        int indicatorY = contentY + Math.max(3, (rowHeight - indicatorSize) / 2);
                        UIStyleHelper.drawBeveledPanel(
                            context,
                            indicatorX,
                            indicatorY,
                            indicatorSize,
                            indicatorSize,
                            getSidebarNodeIndicatorColor(selectedCategory, nodeType, nodeIndex),
                            UITheme.BORDER_SUBTLE,
                            UITheme.PANEL_INNER_BORDER
                        );

                        int lineY = contentY + 4;
                        for (String line : row.lines()) {
                            context.drawTextWithShadow(
                                textRenderer,
                                Text.literal(line),
                                indicatorX + indicatorSize + 4, // Position after the indicator with some spacing
                                lineY,
                                getSidebarNodeTextColor(selectedCategory, nodeHovered)
                            );
                            lineY += nodeLineHeight;
                        }
                        
                        contentY += rowHeight;
                    }
                }
            }
            context.disableScissor();
            ScrollbarHelper.renderCutoffDividers(
                context,
                nodeBackgroundLeft,
                totalWidth - 1,
                contentTop,
                contentBottom,
                scrollOffset,
                maxScroll,
                UITheme.BORDER_SUBTLE
            );
            renderCategoryScrollbar(context, totalWidth, contentTop, contentBottom);
            DrawContextBridge.flush(context);
        }

        if (interactionsEnabled && showTooltips && hoveredCustomNode != null) {
            TooltipRenderer.render(
                context,
                textRenderer,
                hoveredCustomNode.getDescription(),
                mouseX,
                mouseY,
                MinecraftClient.getInstance().getWindow().getScaledWidth(),
                MinecraftClient.getInstance().getWindow().getScaledHeight()
            );
        } else if (interactionsEnabled && showTooltips && hoveredNodeType != null) {
            TooltipRenderer.render(
                context,
                textRenderer,
                hoveredNodeType.getDescription(),
                mouseX,
                mouseY,
                MinecraftClient.getInstance().getWindow().getScaledWidth(),
                MinecraftClient.getInstance().getWindow().getScaledHeight()
            );
        }
        
        // Reset hover states if mouse is not in sidebar
        if (effectiveMouseX < 0 || effectiveMouseX > currentRenderedWidth) {
            hoveredNodeType = null;
            hoveredCustomNode = null;
            hoveredCategory = null;
        }
    }
    
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX < 0 || mouseX > currentRenderedWidth) {
            return false;
        }

        if (button == 0) { // Left click
            ScrollbarHelper.Metrics scrollMetrics = getCategoryScrollMetrics();
            if (scrollMetrics != null
                && mouseX >= scrollMetrics.trackLeft() - 3 && mouseX <= scrollMetrics.trackRight() + 3
                && mouseY >= scrollMetrics.trackTop() && mouseY <= scrollMetrics.trackBottom()) {
                scrollDragging = true;
                scrollDragOffset = (int) mouseY - scrollMetrics.thumbTop();
                return true;
            }
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
            if (hoveredCreateRoutine) {
                createRoutineRequested = true;
                return true;
            }
            if (hoveredRoutine != null) {
                if (!activeRoutineId.isBlank() || mouseX >= currentRenderedWidth - 22) {
                    openRoutineRequested = hoveredRoutine.getId();
                }
                return true;
            }
            if (hoveredAddRoutineInput) {
                addRoutineInputRequested = true;
                return true;
            }
            if (hoveredRoutineInput != null && button == 0) {
                int distanceFromRight = currentRenderedWidth - (int) mouseX;
                if (distanceFromRight <= 14) routineInputAction = 3;
                else if (distanceFromRight <= 26) routineInputAction = 2;
                else if (distanceFromRight <= 38) routineInputAction = 1;
                if (routineInputAction != 0) {
                    routineInputActionId = hoveredRoutineInput.getId();
                    return true;
                }
            }
            if (hoveredNodeType != null || hoveredCustomNode != null || hoveredRoutineInput != null
                || (hoveredRoutine != null && activeRoutineId.isBlank() && openRoutineRequested == null)) {
                return true; // Signal that dragging should start
            }
        }
        
        return false;
    }
    
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (mouseX >= 0 && mouseX <= currentRenderedWidth) {
            scrollOffset = ScrollbarHelper.applyWheel(scrollOffset, amount, 20, maxScroll);
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (button != 0 || !scrollDragging) {
            return false;
        }
        ScrollbarHelper.Metrics scrollMetrics = getCategoryScrollMetrics();
        if (scrollMetrics == null) {
            return true;
        }
        scrollOffset = ScrollbarHelper.scrollFromThumb(scrollMetrics, (int) mouseY - scrollDragOffset);
        return true;
    }

    public boolean mouseReleased(int button) {
        if (button == 0 && scrollDragging) {
            scrollDragging = false;
            return true;
        }
        return false;
    }
    
    public boolean isHoveringNode() {
        return hoveredNodeType != null || hoveredCustomNode != null || hoveredRoutineInput != null
            || (hoveredRoutine != null && activeRoutineId.isBlank() && openRoutineRequested == null);
    }
    
    public Node createNodeFromSidebar(int x, int y) {
        if (hoveredRoutine != null && activeRoutineId.isBlank() && openRoutineRequested == null) {
            return Node.createRoutineCall(hoveredRoutine.getId(), hoveredRoutine.getName(), x, y);
        }
        if (hoveredRoutineInput != null) {
            NodeGraphData.RoutineDefinitionData routine = findRoutine(activeRoutineId);
            return routine == null ? null : new RoutineBuilderModel(routine).createInputReporter(hoveredRoutineInput.getId(), x, y);
        }
        if (hoveredCustomNode != null) {
            return hoveredCustomNode.createNode(x, y);
        }
        if (hoveredNodeType != null) {
            return Node.createForEditor(hoveredNodeType, x, y);
        }
        return null;
    }
    
    public int getWidth() {
        return currentRenderedWidth;
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
     * Returns the width currently rendered (including category open animation).
     */
    public int getRenderedWidth() {
        return currentRenderedWidth;
    }

    /**
     * Returns true if the specified category has any nodes.
     */
    public boolean hasNodesInCategory(NodeCategory category) {
        if (category == NodeCategory.ROUTINES) {
            return true;
        }
        if (category == NodeCategory.CUSTOM) {
            return !customNodes.isEmpty();
        }
        List<NodeType> nodes = categoryNodes.get(category);
        return nodes != null && !nodes.isEmpty();
    }

    /**
     * Returns the list of nodes for the specified category (non-grouped).
     */
    public List<NodeType> getNodesForCategory(NodeCategory category) {
        if (category == NodeCategory.CUSTOM) {
            return java.util.Collections.emptyList();
        }
        List<NodeType> nodes = categoryNodes.get(category);
        return nodes != null ? nodes : java.util.Collections.emptyList();
    }

    /**
     * Returns preset names that can be placed as custom node instances.
     */
    public List<String> getCustomNodePresetNames() {
        List<String> names = new ArrayList<>();
        for (CustomNodeEntry customNode : customNodes) {
            names.add(customNode.getLabel());
        }
        return names;
    }

    /**
     * Returns the grouped nodes for the specified category (SENSORS, PARAMETERS).
     * Returns null if the category doesn't have groups.
     */
    public List<NodeGroup> getGroupedNodesForCategory(NodeCategory category) {
        return groupedCategoryNodes.get(category);
    }

    public boolean isHoveringCustomNode() {
        return hoveredCustomNode != null;
    }

    public void setRoutineContext(List<NodeGraphData.RoutineDefinitionData> routines, String activeRoutineId) {
        this.routines = routines == null ? List.of() : routines;
        this.activeRoutineId = activeRoutineId == null ? "" : activeRoutineId;
    }

    public boolean consumeCreateRoutineRequested() {
        boolean requested = createRoutineRequested;
        createRoutineRequested = false;
        return requested;
    }

    public String consumeOpenRoutineRequested() {
        String requested = openRoutineRequested;
        openRoutineRequested = null;
        return requested;
    }

    public boolean consumeAddRoutineInputRequested() {
        boolean requested = addRoutineInputRequested;
        addRoutineInputRequested = false;
        return requested;
    }

    /** Returns -1 for up, 1 for down, 2 for remove, or 0 when no action is queued. */
    public int consumeRoutineInputAction() {
        int result = routineInputAction == 1 ? -1 : routineInputAction == 2 ? 1 : routineInputAction == 3 ? 2 : 0;
        routineInputAction = 0;
        return result;
    }

    public String consumeRoutineInputActionId() {
        String result = routineInputActionId;
        routineInputActionId = null;
        return result;
    }

    private NodeGraphData.RoutineDefinitionData findRoutine(String id) {
        if (id == null) return null;
        for (NodeGraphData.RoutineDefinitionData routine : routines) {
            if (routine != null && id.equals(routine.getId())) return routine;
        }
        return null;
    }

    public NodeType getHoveredNodeType() {
        if (hoveredCustomNode != null) return NodeType.CUSTOM_NODE;
        if (hoveredRoutineInput != null) return NodeType.ROUTINE_INPUT;
        if (hoveredRoutine != null && activeRoutineId.isBlank()) return NodeType.ROUTINE_CALL;
        return hoveredNodeType;
    }

    private static final class CustomNodeEntry {
        private final String presetName;

        private CustomNodeEntry(String presetName) {
            this.presetName = presetName;
        }

        private String getLabel() {
            return presetName;
        }

        private String getDescription() {
            return "Reusable custom node from preset \"" + presetName + "\".";
        }

        private Node createNode(int x, int y) {
            Node node = new Node(NodeType.CUSTOM_NODE, x, y);
            if (node.getParameter("Preset") != null) {
                node.getParameter("Preset").setStringValue(presetName);
            }
            NodeGraphData data = NodeGraphPersistence.loadNodeGraphForPreset(presetName);
            NodeGraphData.CustomNodeDefinition definition = NodeGraphPersistence.resolveCustomNodeDefinition(presetName, data);
            node.setTemplateName(definition != null ? definition.getName() : presetName);
            node.setTemplateVersion(definition != null && definition.getVersion() != null ? definition.getVersion() : 0);
            node.setTemplateGraphData(data);
            node.recalculateDimensions();
            return node;
        }
    }
    
    /**
     * Darkens a color by the specified factor
     */
    private int darkenColor(int color, float factor) {
        return AnimationHelper.darken(color, factor);
    }

    /**
     * Lightens a color by the specified factor
     */
    private int lightenColor(int color, float factor) {
        return AnimationHelper.brighten(color, factor);
    }

    private int getSidebarCategoryAccent(NodeCategory category) {
        return category != null ? category.getColor() : UITheme.BORDER_DEFAULT;
    }

    private int getSidebarGroupHeaderColor(NodeCategory category) {
        return AnimationHelper.lerpColor(UITheme.TEXT_TERTIARY, getSidebarCategoryAccent(category), 0.55f);
    }

    private int getSidebarNodeIndicatorColor(NodeCategory category, NodeType nodeType, int indexInGroup) {
        if (nodeType == null) {
            return getSidebarCategoryAccent(category);
        }
        if (NodeCatalog.usesExplicitGraphColor(nodeType) && NodeCatalog.category(nodeType) == category) {
            return NodeCatalog.graphColor(nodeType, baritoneAvailable, uiUtilsAvailable);
        }
        if (indexInGroup == 0) {
            return NodeCatalog.category(nodeType) == category
                ? NodeCatalog.graphColor(nodeType, baritoneAvailable, uiUtilsAvailable)
                : getSidebarCategoryAccent(category);
        }
        return getSidebarCategoryAccent(category);
    }

    private int getSidebarNodeTextColor(NodeCategory category, boolean hovered) {
        int baseColor = AnimationHelper.lerpColor(UITheme.TEXT_PRIMARY, getSidebarCategoryAccent(category), 0.14f);
        return hovered ? AnimationHelper.lerpColor(baseColor, UITheme.TEXT_HEADER, 0.18f) : baseColor;
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

    private List<NodeRowInfo> buildNodeRowsForCategory(NodeCategory category, TextRenderer textRenderer, int maxWidth, int lineHeight) {
        List<NodeType> nodes = categoryNodes.get(category);
        if (nodes == null || textRenderer == null) {
            return java.util.Collections.emptyList();
        }
        return buildNodeRows(nodes, textRenderer, maxWidth, lineHeight);
    }

    private List<NodeRowInfo> buildCustomNodeRows(TextRenderer textRenderer, int maxWidth, int lineHeight) {
        if (textRenderer == null) {
            return java.util.Collections.emptyList();
        }
        List<NodeRowInfo> rows = new ArrayList<>();
        for (CustomNodeEntry customNode : customNodes) {
            List<String> lines = wrapText(customNode.getLabel(), textRenderer, maxWidth);
            rows.add(new NodeRowInfo(null, customNode, lines, getWrappedNodeRowHeight(lines.size(), lineHeight)));
        }
        return rows;
    }

    private List<NodeRowInfo> buildNodeRows(List<NodeType> nodes, TextRenderer textRenderer, int maxWidth, int lineHeight) {
        List<NodeRowInfo> rows = new ArrayList<>();
        for (NodeType nodeType : nodes) {
            List<String> lines = wrapText(nodeType.getDisplayName(), textRenderer, maxWidth);
            rows.add(new NodeRowInfo(nodeType, null, lines, getWrappedNodeRowHeight(lines.size(), lineHeight)));
        }
        return rows;
    }

    private int getWrappedNodeRowHeight(int lineCount, int lineHeight) {
        return Math.max(NODE_HEIGHT, Math.max(1, lineCount) * lineHeight + 7);
    }

    private void renderCategoryScrollbar(DrawContext context, int totalWidth, int contentTop, int contentBottom) {
        if (maxScroll <= 0 || contentBottom <= contentTop) {
            return;
        }
        ScrollbarHelper.renderSettingsStyle(
            context,
            ScrollbarHelper.metrics(totalWidth - SCROLLBAR_MARGIN - UITheme.SCROLLBAR_WIDTH, contentTop, UITheme.SCROLLBAR_WIDTH,
                Math.max(1, contentBottom - contentTop), maxScroll, scrollOffset, SCROLLBAR_MIN_KNOB_HEIGHT),
            UITheme.BACKGROUND_SIDEBAR,
            UITheme.BORDER_DEFAULT,
            UITheme.BORDER_DEFAULT
        );
    }

    private ScrollbarHelper.Metrics getCategoryScrollMetrics() {
        if (selectedCategory == null || maxScroll <= 0) {
            return null;
        }
        int contentTop = currentSidebarStartY + PADDING;
        int contentBottom = currentSidebarStartY + currentSidebarHeight - PADDING;
        int viewportHeight = Math.max(1, contentBottom - contentTop);
        int trackLeft = currentRenderedWidth - SCROLLBAR_MARGIN - UITheme.SCROLLBAR_WIDTH;
        return ScrollbarHelper.metrics(trackLeft, contentTop, UITheme.SCROLLBAR_WIDTH, viewportHeight, maxScroll, scrollOffset, SCROLLBAR_MIN_KNOB_HEIGHT);
    }

    private static final class GroupHeaderInfo {
        private final NodeGroup group;
        private final List<String> lines;
        private final int height;
        private final List<NodeRowInfo> nodeRows;

        private GroupHeaderInfo(NodeGroup group, List<String> lines, int height, List<NodeRowInfo> nodeRows) {
            this.group = group;
            this.lines = lines;
            this.height = height;
            this.nodeRows = nodeRows;
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

        public List<NodeRowInfo> getNodeRows() {
            return nodeRows;
        }
    }

    private record NodeRowInfo(NodeType nodeType, CustomNodeEntry customNode, List<String> lines, int height) {}

    public static class NodeGroup {
        private final NodeCategory displayCategory;
        private final String titleKey;
        private final List<NodeType> nodes;

        NodeGroup(NodeCategory displayCategory, String titleKey, List<NodeType> nodeTypes) {
            this.displayCategory = displayCategory;
            this.titleKey = titleKey;
            this.nodes = new ArrayList<>();
            if (nodeTypes != null) {
                this.nodes.addAll(nodeTypes);
            }
        }

        public NodeCategory getDisplayCategory() {
            return displayCategory;
        }

        public String getTitle() {
            return Text.translatable(titleKey).getString();
        }

        public List<NodeType> getNodes() {
            return nodes;
        }

        public boolean isEmpty() {
            return nodes.isEmpty();
        }
    }
}
