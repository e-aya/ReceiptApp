package com.receiptapp.backend.controller;

import com.receiptapp.backend.dto.ReceiptResponse;
import com.receiptapp.backend.entity.Plan;
import com.receiptapp.backend.entity.User;
import com.receiptapp.backend.service.ReceiptService;
import com.receiptapp.backend.service.UsageLimitException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/receipts")
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptService receiptService;
    private final com.receiptapp.backend.repository.UsageLogRepository usageLogRepository;
    private final com.receiptapp.backend.repository.PlanRepository planRepository;
    private final com.receiptapp.backend.repository.UserRepository userRepository;

    // 画像アップロード
    @PostMapping("/upload")
    public ResponseEntity<?> upload(
            @RequestParam("userId") String userId,
            @RequestParam("image") MultipartFile image
    ) {
        try {
            ReceiptResponse response = receiptService.upload(userId, image);
            return ResponseEntity.ok(response);

        } catch (UsageLimitException e) {
            // 429: 使用量上限超過
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of(
                            "error",         "USAGE_LIMIT_EXCEEDED",
                            "message",       e.getMessage(),
                            "currentPlan",   e.getCurrentPlanId(),
                            "limit",         e.getLimit()
                    ));

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "画像の保存に失敗しました"));
        }
    }

    // 領収書一覧取得
    @GetMapping
    public ResponseEntity<List<ReceiptResponse>> getList(
            @RequestParam("userId") String userId
    ) {
        return ResponseEntity.ok(receiptService.getList(userId));
    }

    // OCR解析実行
    @PostMapping("/{id}/analyze")
    public ResponseEntity<?> analyze(@PathVariable String id) {
        try {
            ReceiptResponse response = receiptService.analyze(id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // 今月の使用量取得
    @GetMapping("/usage")
    public ResponseEntity<?> getUsage(@RequestParam("userId") String userId,
                                      jakarta.servlet.http.HttpServletRequest req) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("ユーザーが見つかりません"));

            // 今月の使用回数
            java.time.LocalDateTime startOfMonth =
                    java.time.LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
            long usedCount = usageLogRepository.countByUserAndCreatedAtAfter(user, startOfMonth);

            // プランの上限取得
            Plan plan = planRepository.findById(user.getPlanId()).orElse(null);
            long limit = plan != null ? plan.getMonthlyLimit() : 30;

            return ResponseEntity.ok(Map.of(
                    "usedCount", usedCount,
                    "limit", limit,
                    "remaining", Math.max(0, limit - usedCount),
                    "planId", user.getPlanId()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}