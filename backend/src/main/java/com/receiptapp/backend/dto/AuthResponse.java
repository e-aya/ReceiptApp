package com.receiptapp.backend.dto;

public record AuthResponse(
        String token,
        String userId,
        String email,
        String name,
        String planId
) {}