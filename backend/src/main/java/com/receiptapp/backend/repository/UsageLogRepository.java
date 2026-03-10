package com.receiptapp.backend.repository;

import com.receiptapp.backend.entity.UsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface UsageLogRepository extends JpaRepository<UsageLog, Long> {

    Optional<UsageLog> findByUserIdAndYearMonth(String userId, String yearMonth);

    @Modifying
    @Query(
            value = """
            INSERT INTO usage_logs (user_id, year_month, count)
            VALUES (:userId, :yearMonth, 1)
            ON CONFLICT (user_id, year_month)
            DO UPDATE SET count = usage_logs.count + 1
            """,
            nativeQuery = true  // ★ これが必要
    )
    void upsertIncrement(
            @Param("userId") String userId,
            @Param("yearMonth") String yearMonth
    );
}