package com.pathmind.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecipeCompatibilityBridgeTest {
    @Test
    void recipeCollectionSupportsMc26GetRecipesAccessor() {
        assertEquals(
            List.of("first", "second"),
            RecipeCompatibilityBridge.getAllRecipesFromCollection(new Mc26RecipeCollection())
        );
    }

    @Test
    void placementSupportsMc26ImpossibleAndSlotAccessors() {
        Mc26Placement placement = new Mc26Placement(false);

        assertFalse(RecipeCompatibilityBridge.hasNoPlacement(placement));
        assertEquals(IntArrayList.of(0, 1, 2), RecipeCompatibilityBridge.toPlacementSlots(placement));
        assertTrue(RecipeCompatibilityBridge.hasNoPlacement(new Mc26Placement(true)));
    }

    public static final class Mc26RecipeCollection {
        public List<String> getRecipes() {
            return List.of("first", "second");
        }
    }

    public static final class Mc26Placement {
        private final boolean impossible;

        Mc26Placement(boolean impossible) {
            this.impossible = impossible;
        }

        public boolean isImpossibleToPlace() {
            return impossible;
        }

        public IntArrayList slotsToIngredientIndex() {
            return IntArrayList.of(0, 1, 2);
        }
    }
}
