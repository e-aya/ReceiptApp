package com.receiptapp.backend.service;

import com.receiptapp.backend.dto.ReceiptResponse;
import com.receiptapp.backend.entity.Receipt;
import com.receiptapp.backend.entity.User;
import com.receiptapp.backend.repository.ReceiptRepository;
import com.receiptapp.backend.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptService {

    private final ReceiptRepository receiptRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final UsageLimitService usageLimitService;
    private final ClaudeService claudeService;
    private final VisionService visionService;
    private final OcrParserService ocrParserService;

    public ReceiptResponse upload(String userId, MultipartFile file)
            throws IOException {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("ユーザーが見つかりません"));

        usageLimitService.checkAndIncrement(user);

        String imagePath = storageService.save(file);

        Receipt receipt = new Receipt();
        receipt.setUser(user);
        receipt.setImagePath(imagePath);
        receipt.setStatus("pending");
        receiptRepository.save(receipt);

        return toResponse(receipt);
    }

    public List<ReceiptResponse> getList(String userId) {
        return receiptRepository
                .findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ReceiptResponse analyze(String receiptId) throws Exception {

        Receipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new RuntimeException("領収書が見つかりません"));

        receipt.setStatus("analyzing");
        receiptRepository.save(receipt);

        try {
            // ★ まずClaudeで解析
            log.info("Claude APIで解析開始: {}", receiptId);
            ClaudeService.ClaudeOcrResult result =
                    claudeService.analyzeReceipt(receipt.getImagePath());

            receipt.setStoreName(result.storeName());
            receipt.setReceiptDate(
                    result.receiptDate() != null
                            ? fixYear(java.time.LocalDate.parse(result.receiptDate()))
                            : null
            );
            receipt.setAmount(result.amount());
            receipt.setAccountItem(result.accountItem());
            receipt.setStatus("done");
            receipt.setAnalyzedAt(java.time.LocalDateTime.now());
            log.info("Claude API解析成功: {}", receiptId);

        } catch (Exception claudeEx) {
            // ★ Claude失敗 → Vision APIにフォールバック
            log.warn("Claude API失敗、Vision APIにフォールバック: {}", claudeEx.getMessage());
            try {
                String rawText = visionService.extractText(receipt.getImagePath());
                List<String> lines = Arrays.asList(rawText.split("\n"));

                receipt.setStoreName(ocrParserService.parseStoreName(lines));
                receipt.setReceiptDate(
                        ocrParserService.parseDate(lines) != null
                                ? fixYear(ocrParserService.parseDate(lines))
                                : null
                );
                receipt.setAmount(ocrParserService.parseAmount(lines));
                receipt.setAccountItem("消耗品費"); // Vision APIは仕訳不可のためデフォルト
                receipt.setStatus("done");
                receipt.setAnalyzedAt(java.time.LocalDateTime.now());
                log.info("Vision APIフォールバック成功: {}", receiptId);

            } catch (Exception visionEx) {
                // ★ 両方失敗
                log.error("Vision APIも失敗: {}", visionEx.getMessage());
                receipt.setStatus("error");
                receiptRepository.save(receipt);
                throw visionEx;
            }
        }

        receiptRepository.save(receipt);
        return toResponse(receipt);
    }

    private ReceiptResponse toResponse(Receipt r) {
        ReceiptResponse res = new ReceiptResponse();
        res.setId(r.getId());
        res.setStatus(r.getStatus());
        res.setStoreName(r.getStoreName());
        // ★ LocalDate → ISO文字列 "2026-03-08" に変換してフロントへ
        res.setReceiptDate(
                r.getReceiptDate() != null ? r.getReceiptDate().toString() : null
        );
        res.setAmount(r.getAmount());  // Integer そのまま
        res.setAccountItem(r.getAccountItem());
        res.setMemo(r.getMemo());
        res.setCreatedAt(r.getCreatedAt());
        return res;
    }

    // ★ 年補正メソッドを追加
    private java.time.LocalDate fixYear(java.time.LocalDate date) {
        int year = date.getYear();
        // 平成解釈(1989-2018)または昭和解釈された場合
        // 平成XX年 = 1988+XX → 2桁年XX → 2000+XX = 平成年+12
        if (year >= 1989 && year < 2020) {
            int correctedYear = year + 12; // 例: 2014→2026
            return date.withYear(correctedYear);
        }
        return date;
    }
}