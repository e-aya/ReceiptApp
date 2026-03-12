package com.receiptapp.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ReceiptResponse {
    private String        id;
    private String        status;
    private String        storeName;
    private String        receiptDate; // フロントへはISO文字列で返す
    private Integer       amount;      // ★ String → Integer
    private String        accountItem;
    private String        memo;
    private LocalDateTime createdAt;
}