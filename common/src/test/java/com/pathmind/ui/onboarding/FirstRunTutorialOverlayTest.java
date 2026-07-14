package com.pathmind.ui.onboarding;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
