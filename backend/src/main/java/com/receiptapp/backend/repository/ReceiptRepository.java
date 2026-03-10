package com.receiptapp.backend.repository;

import com.receiptapp.backend.entity.Receipt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReceiptRepository extends JpaRepository<Receipt, String> {
    List<Receipt> findByUserIdOrderByCreatedAtDesc(String userId);
}