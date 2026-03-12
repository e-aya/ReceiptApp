package com.receiptapp.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "receipts")
public class Receipt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String imagePath;

    @Column(nullable = false)
    private String status = "pending";

    // OCR結果
    private String    storeName;   // 店名
    private LocalDate receiptDate; // ★ String → LocalDate
    private Integer   amount;      // ★ String → Integer

    // 仕訳情報
    private String accountItem;
    private String memo;

    private Double apiCostUsd;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime analyzedAt;
}