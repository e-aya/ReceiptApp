package com.receiptapp.backend.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "usage_logs",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"user_id", "year_month"}
        )
)
public class UsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 対象年月 例: "2025-03"
    @Column(nullable = false, length = 7)
    private String yearMonth;

    // 月間利用回数
    @Column(nullable = false)
    private Integer count = 0;
}