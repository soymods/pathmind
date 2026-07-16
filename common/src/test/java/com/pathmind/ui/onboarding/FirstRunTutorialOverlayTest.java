package com.pathmind.ui.onboarding;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FirstRunTutorialOverlayTest {

    @Test
    void tutorialCombinesUiTourWithOrderedExampleOneWalkthrough() {
        FirstRunTutorialOverlay overlay = new FirstRunTutorialOverlay();

        assertEquals(List.of(
            FirstRunTutorialOverlay.Target.NONE,
            FirstRunTutorialOverlay.Target.PRESETS,
            FirstRunTutorialOverlay.Target.SIDEBAR,
            FirstRunTutorialOverlay.Target.WORKSPACE,
            FirstRunTutorialOverlay.Target.EXAMPLE_START,
            FirstRunTutorialOverlay.Target.EXAMPLE_INTRO,
            FirstRunTutorialOverlay.Target.EXAMPLE_LOOK,
            FirstRunTutorialOverlay.Target.EXAMPLE_WALK,
            FirstRunTutorialOverlay.Target.EXAMPLE_ACTIONS,
            FirstRunTutorialOverlay.Target.RUN_CONTROLS,
            FirstRunTutorialOverlay.Target.VALIDATION,
            FirstRunTutorialOverlay.Target.MARKETPLACE
        ), overlay.getStepTargets());
    }

    @Test
    void advancingTutorialNotifiesScreenAboutTheNewSpotlightTarget() {
        FirstRunTutorialOverlay overlay = new FirstRunTutorialOverlay();
        List<FirstRunTutorialOverlay.Target> visitedTargets = new ArrayList<>();

        overlay.show(visitedTargets::add);
        assertTrue(overlay.keyPressed(262, null));

        assertEquals(List.of(
            FirstRunTutorialOverlay.Target.NONE,
            FirstRunTutorialOverlay.Target.PRESETS
        ), visitedTargets);
    }

    @Test
    void panelPlacementAvoidsTheSpotlightWhenAnySideFits() {
        int[] spotlight = {430, 150, 120, 100};

        int[] panel = FirstRunTutorialOverlay.choosePanelPosition(900, 500, spotlight, 286, 150);

        assertFalse(intersects(panel[0], panel[1], 286, 150,
            spotlight[0] - 12, spotlight[1] - 12, spotlight[2] + 24, spotlight[3] + 24));
    }

    private static boolean intersects(int x1, int y1, int width1, int height1,
                                      int x2, int y2, int width2, int height2) {
        return x1 < x2 + width2 && x1 + width1 > x2
            && y1 < y2 + height2 && y1 + height1 > y2;
    }
}
