package com.pathmind.ui.control;

/** Shared point-in-rectangle checks for UI controls and screens. */
public final class UiHitTest {
    private UiHitTest() {
    }

    /**
     * Returns whether a point is within an inclusive rectangular hit area.
     */
    public static boolean contains(int pointX, int pointY, int x, int y, int width, int height) {
        return width >= 0 && height >= 0
            && pointX >= x && pointX <= x + width
            && pointY >= y && pointY <= y + height;
    }

    /** Inclusive bounds check that preserves sub-pixel mouse coordinates. */
    public static boolean contains(double pointX, double pointY, int x, int y, int width, int height) {
        return width >= 0 && height >= 0
            && pointX >= x && pointX <= x + width
            && pointY >= y && pointY <= y + height;
    }

    /**
     * Returns whether a point is within a half-open rectangular hit area.
     * This is useful for adjacent rows where a shared edge must belong to only
     * one row.
     */
    public static boolean containsHalfOpen(int pointX, int pointY, int x, int y, int width, int height) {
        return width > 0 && height > 0
            && pointX >= x && pointX < x + width
            && pointY >= y && pointY < y + height;
    }

    public static boolean primaryClick(int pointX, int pointY, int button,
                                       int x, int y, int width, int height) {
        return button == 0 && contains(pointX, pointY, x, y, width, height);
    }
}
