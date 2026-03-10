package com.receiptapp.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "receipts")
public class Receipt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    // 撮影したユーザー
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 画像ファイルパス（サーバー保存先）
    @Column(nullable = false)
    private String imagePath;

    // OCR解析ステータス: pending / analyzing / done / error
    @Column(nullable = false)
    private String status = "pending";

    // OCR結果
    private String storeName;   // 店名
    private String receiptDate; // 日付 (2025-01-25)
    private String amount;      // 金額 (2500)

    // 仕訳情報（CSV出力用）
    private String accountItem; // 勘定科目 (接待交際費 etc)
    private String memo;        // 摘要

    // コスト記録（Vision API課金管理用）
    private Double apiCostUsd;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime analyzedAt;
}