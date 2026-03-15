package com.receiptapp.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column
    private String password; // ★ Googleログインはnull可

    @Column(unique = true)
    private String googleId; // ★ 追加

    @Column
    private String name; // ★ 追加（表示名）

    // プラン: free / pro / business
    @Column(nullable = false)
    private String planId = "free";

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}