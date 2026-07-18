package com.lab.backend.billing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderStatusTest {

    @Test
    void pipelineMovesStrictlyForward() {
        assertTrue(OrderStatus.PENDING.canTransitionTo(OrderStatus.COLLECTED));
        assertTrue(OrderStatus.COLLECTED.canTransitionTo(OrderStatus.IN_PROGRESS));
        assertTrue(OrderStatus.IN_PROGRESS.canTransitionTo(OrderStatus.COMPLETED));
        assertTrue(OrderStatus.COMPLETED.canTransitionTo(OrderStatus.VERIFIED));
    }

    @Test
    void skippingAndBackwardsTransitionsAreRejected() {
        assertFalse(OrderStatus.PENDING.canTransitionTo(OrderStatus.IN_PROGRESS));
        assertFalse(OrderStatus.COLLECTED.canTransitionTo(OrderStatus.PENDING));
        assertFalse(OrderStatus.VERIFIED.canTransitionTo(OrderStatus.PENDING));
        assertFalse(OrderStatus.COMPLETED.canTransitionTo(OrderStatus.COLLECTED));
    }
}
