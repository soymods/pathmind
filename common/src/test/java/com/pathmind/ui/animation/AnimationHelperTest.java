package com.pathmind.ui.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnimationHelperTest {
    @Test
    void multiplyAlphaPreservesRgbAndScalesExistingAlpha() {
        assertEquals(0x40123456, AnimationHelper.multiplyAlpha(0x80123456, 0.5f));
        assertEquals(0x00123456, AnimationHelper.multiplyAlpha(0x80123456, -1f));
        assertEquals(0x80123456, AnimationHelper.multiplyAlpha(0x80123456, 2f));
    }
}
