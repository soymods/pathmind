package com.pathmind.ui.onboarding;

import com.pathmind.ui.animation.AnimatedValue;
import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.control.PathmindWorkspaceChrome;
import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.DrawContextBridge;
import com.pathmind.util.TextRenderUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * First-run guided overlay for the visual editor.
 */
public final class FirstRunTutorialOverlay {
    public interface TargetBoundsProvider {
        int[] getBounds(Target target);
    }

    public interface CompletionHandler {
        void complete();
    }

    public interface StepChangeHandler {
        void stepChanged(Target target);
    }

    public enum Target {
        NONE,
        PRESETS,
        SIDEBAR,
        WORKSPACE,
        EXAMPLE_START,
        EXAMPLE_INTRO,
        EXAMPLE_LOOK,
        EXAMPLE_WALK,
        EXAMPLE_ACTIONS,
        RUN_CONTROLS,
        VALIDATION,
        MARKETPLACE
    }

    private static final int PANEL_WIDTH = 286;
    private static final int PANEL_MIN_HEIGHT = 112;
    private static final int PANEL_PADDING = 12;
    private static final int PANEL_GAP = 12;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 6;
    private static final int SKIP_WIDTH = 44;
    private static final int BACK_WIDTH = 44;
    private static final int NEXT_WIDTH = 56;
    private static final int SPOTLIGHT_PADDING = 8;
    private static final int SPOTLIGHT_MIN_SIZE = 22;
    private static final int OVERLAY_COLOR = 0xB80A0A0A;

    private final AnimatedValue entranceAnimation = new AnimatedValue(0f, AnimationHelper::easeOutCubic);
    private final AnimatedValue stepAnimation = new AnimatedValue(1f, AnimationHelper::easeOutCubic);
    private final List<Step> steps = List.of(
        new Step(Target.NONE, "pathmind.tutorial.welcome.title", "pathmind.tutorial.welcome.body"),
        new Step(Target.PRESETS, "pathmind.tutorial.presets.title", "pathmind.tutorial.presets.body"),
        new Step(Target.SIDEBAR, "pathmind.tutorial.sidebar.title", "pathmind.tutorial.sidebar.body"),
        new Step(Target.WORKSPACE, "pathmind.tutorial.workspace.title", "pathmind.tutorial.workspace.body"),
        new Step(Target.EXAMPLE_START, "pathmind.tutorial.exampleStart.title", "pathmind.tutorial.exampleStart.body"),
        new Step(Target.EXAMPLE_INTRO, "pathmind.tutorial.exampleIntro.title", "pathmind.tutorial.exampleIntro.body"),
        new Step(Target.EXAMPLE_LOOK, "pathmind.tutorial.exampleLook.title", "pathmind.tutorial.exampleLook.body"),
        new Step(Target.EXAMPLE_WALK, "pathmind.tutorial.exampleWalk.title", "pathmind.tutorial.exampleWalk.body"),
        new Step(Target.EXAMPLE_ACTIONS, "pathmind.tutorial.exampleActions.title", "pathmind.tutorial.exampleActions.body"),
        new Step(Target.RUN_CONTROLS, "pathmind.tutorial.run.title", "pathmind.tutorial.run.body"),
        new Step(Target.VALIDATION, "pathmind.tutorial.validation.title", "pathmind.tutorial.validation.body"),
        new Step(Target.MARKETPLACE, "pathmind.tutorial.marketplace.title", "pathmind.tutorial.marketplace.body")
    );
    private List<Step> activeSteps = steps;
    private StepChangeHandler stepChangeHandler;

    private boolean visible;
    private int stepIndex;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int skipX;
    private int skipY;
    private int backX;
    private int backY;
    private int nextX;
    private int nextY;

    public void show() {
        show(null);
    }

    public void show(StepChangeHandler stepChangeHandler) {
        this.stepChangeHandler = stepChangeHandler;
        visible = true;
        stepIndex = 0;
        activeSteps = steps;
        entranceAnimation.setValue(0f);
        entranceAnimation.animateTo(1f, 240, AnimationHelper::easeOutCubic);
        stepAnimation.setValue(1f);
        notifyStepChanged();
    }

    public boolean isVisible() {
        return visible;
    }

    public void render(GuiGraphics context, Font textRenderer, int mouseX, int mouseY,
                       int screenWidth, int screenHeight, int accentColor,
                       TargetBoundsProvider boundsProvider) {
        if (!visible) {
            return;
        }

        entranceAnimation.tick();
        stepAnimation.tick();
        activeSteps = getAvailableSteps(boundsProvider);
        stepIndex = Mth.clamp(stepIndex, 0, activeSteps.size() - 1);
        float entrance = entranceAnimation.getValue();
        float stepProgress = stepAnimation.getValue();
        float contentAlpha = entrance * Mth.clamp(0.55f + stepProgress * 0.45f, 0f, 1f);
        Step step = activeSteps.get(stepIndex);
        boolean hasSpotlight = step.target() != Target.NONE;
        int[] spotlight = getSpotlightBounds(step.target(), boundsProvider, screenWidth, screenHeight);
        renderScrim(context, screenWidth, screenHeight, spotlight, entrance, hasSpotlight);
        if (hasSpotlight) {
            renderSpotlight(context, spotlight, accentColor, entrance);
        }

        panelWidth = Math.min(PANEL_WIDTH, Math.max(1, screenWidth - 24));
        int textWidth = Math.max(1, panelWidth - PANEL_PADDING * 2);
        List<String> bodyLines = TextRenderUtil.wrapWords(
            textRenderer,
            Component.translatable(step.bodyKey()).getString(),
            textWidth
        );
        int bodyHeight = bodyLines.size() * (textRenderer.lineHeight + 3);
        panelHeight = Math.max(PANEL_MIN_HEIGHT, PANEL_PADDING * 2 + 12 + textRenderer.lineHeight + bodyHeight + BUTTON_HEIGHT + 16);
        int[] panelPos = hasSpotlight
            ? choosePanelPosition(screenWidth, screenHeight, spotlight, panelWidth, panelHeight)
            : new int[]{
                Mth.clamp(screenWidth / 2 - panelWidth / 2, 12, Math.max(12, screenWidth - panelWidth - 12)),
                Mth.clamp(screenHeight / 2 - panelHeight / 2, 28, Math.max(28, screenHeight - panelHeight - 12))
            };
        panelX = panelPos[0];
        panelY = Math.round(panelPos[1] + (1f - entrance) * 8f + (1f - stepProgress) * 5f);

        int panelBg = AnimationHelper.withAlpha(UITheme.BACKGROUND_SECONDARY, entrance);
        int panelBorder = AnimationHelper.withAlpha(accentColor, entrance);
        int panelInner = AnimationHelper.withAlpha(UITheme.PANEL_INNER_BORDER, entrance);
        if (hasSpotlight && !rectanglesIntersect(panelX, panelY, panelWidth, panelHeight, spotlight[0], spotlight[1], spotlight[2], spotlight[3])) {
            renderConnector(context, panelX, panelY, panelWidth, panelHeight, spotlight, accentColor, entrance);
        }
        UIStyleHelper.drawBeveledPanel(context, panelX, panelY, panelWidth, panelHeight, panelBg, panelBorder, panelInner);

        int titleColor = AnimationHelper.withAlpha(UITheme.TEXT_HEADER, contentAlpha);
        int bodyColor = AnimationHelper.withAlpha(UITheme.TEXT_SECONDARY, contentAlpha);
        String count = (stepIndex + 1) + "/" + activeSteps.size();
        int titleWidth = Math.max(1, textWidth - textRenderer.width(count) - 8);
        String title = TextRenderUtil.trimWithEllipsis(
            textRenderer,
            Component.translatable(step.titleKey()).getString(),
            titleWidth
        );
        context.drawString(textRenderer, Component.literal(title), panelX + PANEL_PADDING, panelY + PANEL_PADDING, titleColor);
        context.drawString(textRenderer, Component.literal(count),
            panelX + panelWidth - PANEL_PADDING - textRenderer.width(count),
            panelY + PANEL_PADDING,
            AnimationHelper.withAlpha(accentColor, contentAlpha));

        int textY = panelY + PANEL_PADDING + textRenderer.lineHeight + 10;
        for (String line : bodyLines) {
            context.drawString(textRenderer, Component.literal(line), panelX + PANEL_PADDING, textY, bodyColor);
            textY += textRenderer.lineHeight + 3;
        }

        renderProgressDots(context, panelX + PANEL_PADDING, panelY + panelHeight - PANEL_PADDING - 5, accentColor, contentAlpha);
        renderButtons(context, textRenderer, mouseX, mouseY, accentColor, contentAlpha);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, CompletionHandler completionHandler) {
        if (!visible || button != 0) {
            return visible;
        }
        int x = (int) mouseX;
        int y = (int) mouseY;
        if (PathmindWorkspaceChrome.contains(x, y, skipX, skipY, SKIP_WIDTH, BUTTON_HEIGHT)) {
            complete(completionHandler);
            return true;
        }
        if (stepIndex > 0 && PathmindWorkspaceChrome.contains(x, y, backX, backY, BACK_WIDTH, BUTTON_HEIGHT)) {
            setStep(stepIndex - 1);
            return true;
        }
        if (PathmindWorkspaceChrome.contains(x, y, nextX, nextY, NEXT_WIDTH, BUTTON_HEIGHT)) {
            if (stepIndex >= activeSteps.size() - 1) {
                complete(completionHandler);
            } else {
                setStep(stepIndex + 1);
            }
            return true;
        }
        return true;
    }

    public boolean keyPressed(int keyCode, CompletionHandler completionHandler) {
        if (!visible) {
            return false;
        }
        if (keyCode == 256) {
            complete(completionHandler);
            return true;
        }
        if (keyCode == 257 || keyCode == 335 || keyCode == 262) {
            if (stepIndex >= activeSteps.size() - 1) {
                complete(completionHandler);
            } else {
                setStep(stepIndex + 1);
            }
            return true;
        }
        if ((keyCode == 263 || keyCode == 259) && stepIndex > 0) {
            setStep(stepIndex - 1);
            return true;
        }
        return true;
    }

    private void complete(CompletionHandler completionHandler) {
        visible = false;
        stepChangeHandler = null;
        if (completionHandler != null) {
            completionHandler.complete();
        }
    }

    private void setStep(int index) {
        stepIndex = Mth.clamp(index, 0, activeSteps.size() - 1);
        stepAnimation.setValue(0f);
        stepAnimation.animateTo(1f, 180, AnimationHelper::easeOutCubic);
        notifyStepChanged();
    }

    private void notifyStepChanged() {
        if (stepChangeHandler != null && !activeSteps.isEmpty()) {
            stepChangeHandler.stepChanged(activeSteps.get(stepIndex).target());
        }
    }

    List<Target> getStepTargets() {
        return steps.stream().map(Step::target).toList();
    }

    private List<Step> getAvailableSteps(TargetBoundsProvider boundsProvider) {
        if (boundsProvider == null) {
            return steps;
        }
        List<Step> available = new ArrayList<>();
        for (Step step : steps) {
            if (step.target() == Target.NONE || isTargetAvailable(step.target(), boundsProvider)) {
                available.add(step);
            }
        }
        return available.isEmpty() ? List.of(steps.get(0)) : available;
    }

    private boolean isTargetAvailable(Target target, TargetBoundsProvider boundsProvider) {
        int[] bounds = boundsProvider.getBounds(target);
        return bounds != null && bounds.length >= 4 && bounds[2] > 0 && bounds[3] > 0;
    }

    private int[] getSpotlightBounds(Target target, TargetBoundsProvider boundsProvider, int screenWidth, int screenHeight) {
        if (target == Target.NONE || boundsProvider == null) {
            return new int[]{screenWidth / 2 - 1, screenHeight / 2 - 1, 2, 2};
        }
        int[] bounds = boundsProvider.getBounds(target);
        if (bounds == null || bounds.length < 4 || bounds[2] <= 0 || bounds[3] <= 0) {
            return new int[]{screenWidth / 2 - 1, screenHeight / 2 - 1, 2, 2};
        }
        int x = Mth.clamp(bounds[0] - SPOTLIGHT_PADDING, 0, screenWidth);
        int y = Mth.clamp(bounds[1] - SPOTLIGHT_PADDING, 0, screenHeight);
        int right = Mth.clamp(bounds[0] + Math.max(bounds[2], SPOTLIGHT_MIN_SIZE) + SPOTLIGHT_PADDING, 0, screenWidth);
        int bottom = Mth.clamp(bounds[1] + Math.max(bounds[3], SPOTLIGHT_MIN_SIZE) + SPOTLIGHT_PADDING, 0, screenHeight);
        return new int[]{x, y, Math.max(SPOTLIGHT_MIN_SIZE, right - x), Math.max(SPOTLIGHT_MIN_SIZE, bottom - y)};
    }

    private void renderScrim(GuiGraphics context, int screenWidth, int screenHeight, int[] spotlight, float alpha, boolean hasSpotlight) {
        int color = AnimationHelper.withAlpha(OVERLAY_COLOR, ((OVERLAY_COLOR >>> 24) & 0xFF) / 255f * alpha);
        if (!hasSpotlight) {
            DrawContextBridge.fillOverlay(context, 0, 0, screenWidth, screenHeight, color);
            return;
        }
        int x = spotlight[0];
        int y = spotlight[1];
        int right = x + spotlight[2];
        int bottom = y + spotlight[3];
        DrawContextBridge.fillOverlay(context, 0, 0, screenWidth, y, color);
        DrawContextBridge.fillOverlay(context, 0, y, x, bottom, color);
        DrawContextBridge.fillOverlay(context, right, y, screenWidth, bottom, color);
        DrawContextBridge.fillOverlay(context, 0, bottom, screenWidth, screenHeight, color);
    }

    private void renderSpotlight(GuiGraphics context, int[] spotlight, int accentColor, float alpha) {
        float pulse = (float) ((Math.sin(System.currentTimeMillis() / 260.0) + 1.0) * 0.5);
        int color = AnimationHelper.withAlpha(AnimationHelper.lerpColor(accentColor, UITheme.TEXT_HEADER, pulse * 0.18f), alpha);
        int x = spotlight[0];
        int y = spotlight[1];
        int width = spotlight[2];
        int height = spotlight[3];
        DrawContextBridge.drawBorder(context, x, y, width, height, color);
        DrawContextBridge.drawBorder(context, x - 1, y - 1, width + 2, height + 2, AnimationHelper.withAlpha(color, 0.45f * alpha));
    }

    private int[] choosePanelPosition(int screenWidth, int screenHeight, int[] spotlight, int width, int height) {
        int targetCenterX = spotlight[0] + spotlight[2] / 2;
        int targetCenterY = spotlight[1] + spotlight[3] / 2;
        int preferredX = targetCenterX < screenWidth / 2 ? spotlight[0] + spotlight[2] + PANEL_GAP : spotlight[0] - width - PANEL_GAP;
        int preferredY = targetCenterY - height / 2;

        if (preferredX < 12 || preferredX + width > screenWidth - 12) {
            preferredX = Mth.clamp(screenWidth / 2 - width / 2, 12, Math.max(12, screenWidth - width - 12));
            preferredY = spotlight[1] > screenHeight / 2 ? spotlight[1] - height - PANEL_GAP : spotlight[1] + spotlight[3] + PANEL_GAP;
        }

        int x = Mth.clamp(preferredX, 12, Math.max(12, screenWidth - width - 12));
        int y = Mth.clamp(preferredY, 28, Math.max(28, screenHeight - height - 12));
        return new int[]{x, y};
    }

    private void renderConnector(GuiGraphics context, int x, int y, int width, int height, int[] spotlight, int accentColor, float alpha) {
        int sourceX = x + width / 2;
        int sourceY = y + height / 2;
        int targetCenterX = spotlight[0] + spotlight[2] / 2;
        int targetCenterY = spotlight[1] + spotlight[3] / 2;
        int[] targetAnchor = getRectEdgeAnchor(spotlight[0], spotlight[1], spotlight[2], spotlight[3], sourceX, sourceY);
        int targetX = targetAnchor[0];
        int targetY = targetAnchor[1];
        int color = AnimationHelper.withAlpha(accentColor, 0.7f * alpha);
        if (Math.abs(sourceX - targetCenterX) > Math.abs(sourceY - targetCenterY)) {
            int sourceEdgeX = sourceX < targetCenterX ? x + width : x;
            int midX = (sourceEdgeX + targetX) / 2;
            context.hLine(sourceEdgeX, midX, sourceY, color);
            context.vLine(midX, Math.min(sourceY, targetY), Math.max(sourceY, targetY), color);
            context.hLine(Math.min(midX, targetX), Math.max(midX, targetX), targetY, color);
        } else {
            int sourceEdgeY = sourceY < targetCenterY ? y + height : y;
            int midY = (sourceEdgeY + targetY) / 2;
            context.vLine(sourceX, Math.min(sourceEdgeY, midY), Math.max(sourceEdgeY, midY), color);
            context.hLine(Math.min(sourceX, targetX), Math.max(sourceX, targetX), midY, color);
            context.vLine(targetX, Math.min(midY, targetY), Math.max(midY, targetY), color);
        }
    }

    private int[] getRectEdgeAnchor(int x, int y, int width, int height, int fromX, int fromY) {
        int centerX = x + width / 2;
        int centerY = y + height / 2;
        int leftDistance = Math.abs(fromX - x);
        int rightDistance = Math.abs(fromX - (x + width));
        int topDistance = Math.abs(fromY - y);
        int bottomDistance = Math.abs(fromY - (y + height));

        int minHorizontal = Math.min(leftDistance, rightDistance);
        int minVertical = Math.min(topDistance, bottomDistance);
        if (minHorizontal <= minVertical) {
            return new int[]{fromX < centerX ? x : x + width, Mth.clamp(fromY, y, y + height)};
        }
        return new int[]{Mth.clamp(fromX, x, x + width), fromY < centerY ? y : y + height};
    }

    private boolean rectanglesIntersect(int ax, int ay, int aw, int ah, int bx, int by, int bw, int bh) {
        return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
    }

    private void renderProgressDots(GuiGraphics context, int x, int y, int accentColor, float alpha) {
        for (int i = 0; i < activeSteps.size(); i++) {
            int dotX = x + i * 8;
            int color = i == stepIndex ? accentColor : UITheme.BORDER_DEFAULT;
            context.fill(dotX, y, dotX + 4, y + 4, AnimationHelper.withAlpha(color, alpha));
        }
    }

    private void renderButtons(GuiGraphics context, Font textRenderer, int mouseX, int mouseY, int accentColor, float alpha) {
        skipX = panelX + PANEL_PADDING;
        skipY = panelY + panelHeight - PANEL_PADDING - BUTTON_HEIGHT;
        nextX = panelX + panelWidth - PANEL_PADDING - NEXT_WIDTH;
        nextY = skipY;
        backX = nextX - BUTTON_GAP - BACK_WIDTH;
        backY = skipY;

        renderButton(context, textRenderer, skipX, skipY, SKIP_WIDTH, BUTTON_HEIGHT, mouseX, mouseY,
            Component.translatable("pathmind.tutorial.skip").getString(), accentColor, false, alpha);
        if (stepIndex > 0) {
            renderButton(context, textRenderer, backX, backY, BACK_WIDTH, BUTTON_HEIGHT, mouseX, mouseY,
                Component.translatable("pathmind.tutorial.back").getString(), accentColor, false, alpha);
        }
        String nextLabel = stepIndex >= activeSteps.size() - 1
            ? Component.translatable("pathmind.tutorial.done").getString()
            : Component.translatable("pathmind.tutorial.next").getString();
        renderButton(context, textRenderer, nextX, nextY, NEXT_WIDTH, BUTTON_HEIGHT, mouseX, mouseY,
            nextLabel, accentColor, true, alpha);
    }

    private void renderButton(GuiGraphics context, Font textRenderer, int x, int y, int width, int height,
                              int mouseX, int mouseY, String label, int accentColor, boolean primary, float alpha) {
        boolean hovered = PathmindWorkspaceChrome.contains(mouseX, mouseY, x, y, width, height);
        UIStyleHelper.TextButtonPalette palette = UIStyleHelper.getTextButtonPalette(
            primary ? UIStyleHelper.TextButtonStyle.PRIMARY : UIStyleHelper.TextButtonStyle.DEFAULT,
            accentColor,
            hovered,
            false
        );
        UIStyleHelper.drawTextButtonFrame(context, x, y, width, height, new UIStyleHelper.TextButtonPalette(
            AnimationHelper.withAlpha(palette.backgroundColor(), alpha),
            AnimationHelper.withAlpha(palette.borderColor(), alpha),
            AnimationHelper.withAlpha(palette.innerBorderColor(), alpha),
            AnimationHelper.withAlpha(palette.textColor(), alpha)
        ));
        String trimmed = TextRenderUtil.trimWithEllipsis(textRenderer, label, width - 8);
        int textX = x + (width - textRenderer.width(trimmed)) / 2;
        int textY = y + (height - textRenderer.lineHeight) / 2 + 1;
        context.drawString(textRenderer, Component.literal(trimmed), textX, textY, AnimationHelper.withAlpha(palette.textColor(), alpha));
    }

    private record Step(Target target, String titleKey, String bodyKey) {
    }
}
