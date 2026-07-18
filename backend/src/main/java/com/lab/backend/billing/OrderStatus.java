package com.lab.backend.billing;

public enum OrderStatus {
    PENDING, COLLECTED, IN_PROGRESS, COMPLETED, VERIFIED;

    /** The sample-tracking pipeline is strictly forward-moving. */
    public boolean canTransitionTo(OrderStatus next) {
        return next.ordinal() == this.ordinal() + 1;
    }
}
