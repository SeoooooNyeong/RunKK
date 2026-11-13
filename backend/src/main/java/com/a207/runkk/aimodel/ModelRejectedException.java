package com.a207.runkk.aimodel;

public class ModelRejectedException extends RuntimeException {
    private final Reason reason;

    public ModelRejectedException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        MODEL_NOT_FOUND,
        NO_PREVIOUS_MODEL,
        MODEL_REGISTRY_UNAVAILABLE
    }
}
