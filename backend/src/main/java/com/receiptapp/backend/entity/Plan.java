package com.receiptapp.backend.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "plans")
public class Plan {

    @Id
    private String id; // "free" / "pro" / "business"

    @Column(nullable = false)
    private String name;

    // 月間上限枚数（nullなら無制限）
    private Integer monthlyLimit;

    // 月額料金（円）
    @Column(nullable = false)
    private Integer priceJpy;
}