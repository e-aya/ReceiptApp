package com.receiptapp.backend.service;

import com.receiptapp.backend.entity.Plan;
import com.receiptapp.backend.entity.User;
import com.receiptapp.backend.entity.UsageLog;
import com.receiptapp.backend.repository.PlanRepository;
import com.receiptapp.backend.repository.UsageLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.YearMonth;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UsageLimitService {

    private final UsageLogRepository usageLogRepository;
    private final PlanRepository planRepository;

    @Transactional
    public void checkAndIncrement(User user) {
        Plan plan = planRepository.findById(user.getPlanId())
                .orElseThrow(() -> new RuntimeException("プランが見つかりません"));

        // 無制限プランはスキップ
        if (plan.getMonthlyLimit() == null) return;

        String yearMonth = YearMonth.now().toString(); // "2025-03"

        // カウントアップ
        usageLogRepository.upsertIncrement(user.getId(), yearMonth);

        // 現在のカウントを取得
        Optional<UsageLog> log = usageLogRepository
                .findByUserIdAndYearMonth(user.getId(), yearMonth);

        int currentCount = log.map(UsageLog::getCount).orElse(0);

        // 上限チェック
        if (currentCount > plan.getMonthlyLimit()) {
            // カウントを戻す
            log.ifPresent(this::accept);
            throw new UsageLimitException(
                    plan.getMonthlyLimit(),
                    user.getPlanId()
            );
        }
    }

    private void accept(UsageLog l) {
        l.setCount(l.getCount() - 1);
        usageLogRepository.save(l);
    }
}