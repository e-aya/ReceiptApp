package com.receiptapp.backend.dto;

public record AuthRequest(String email, String password, String name) {}