package com.pathmind.nodes;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.Test;

class NodeProximitySensorEvaluatorTest {
    private static final BlockPos BLOCK_POS = new BlockPos(4, 10, 7);

    @Test
    void touchingShapeIncludesStandingOnTop() {
        AABB feetContact = new AABB(4.2D, 10.999D, 7.2D, 4.8D, 12.8D, 7.8D);

        assertTrue(NodeProximitySensorEvaluator.touchesShape(feetContact, BLOCK_POS, Shapes.block()));
    }

    @Test
    void touchingShapeIncludesAnySide() {
        AABB sideContact = new AABB(4.999D, 10.2D, 7.2D, 5.6D, 11.8D, 7.8D);

        assertTrue(NodeProximitySensorEvaluator.touchesShape(sideContact, BLOCK_POS, Shapes.block()));
    }

    @Test
    void touchingShapeRejectsNearbyNonContact() {
        AABB separated = new AABB(5.01D, 10.2D, 7.2D, 5.6D, 11.8D, 7.8D);

        assertFalse(NodeProximitySensorEvaluator.touchesShape(separated, BLOCK_POS, Shapes.block()));
    }

    @Test
    void touchingShapeUsesActualPartialBlockGeometry() {
        AABB aboveBottomSlab = new AABB(4.2D, 10.51D, 7.2D, 4.8D, 12.0D, 7.8D);

        assertFalse(NodeProximitySensorEvaluator.touchesShape(
            aboveBottomSlab,
            BLOCK_POS,
            Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D)
        ));
    }
}
