package com.receiptapp.backend.service;

import lombok.Getter;

@Getter
public class UsageLimitException extends RuntimeException {
    private final int limit;
    private final String currentPlanId;

    public UsageLimitException(int limit, String currentPlanId) {
        super("月間利用上限（" + limit + "枚）に達しました");
        this.limit = limit;
        this.currentPlanId = currentPlanId;
    }
}