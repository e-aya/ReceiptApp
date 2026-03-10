package com.receiptapp.backend.service;

import com.receiptapp.backend.dto.ReceiptResponse;
import com.receiptapp.backend.entity.Receipt;
import com.receiptapp.backend.entity.User;
import com.receiptapp.backend.repository.ReceiptRepository;
import com.receiptapp.backend.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReceiptService {

    private final ReceiptRepository receiptRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final UsageLimitService usageLimitService;
    private final VisionService visionService;
    private final OcrParserService ocrParserService;

    public ReceiptResponse upload(String userId, MultipartFile file)
            throws IOException {

        // ユーザー取得
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("ユーザーが見つかりません"));

        // 使用量チェック（上限超過でUsageLimitException）
        usageLimitService.checkAndIncrement(user);

        // 画像保存
        String imagePath = storageService.save(file);

        // DBに保存
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

    // ★ analyzeメソッドを追加
    @Transactional
    public ReceiptResponse analyze(String receiptId) throws Exception {

        Receipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new RuntimeException("領収書が見つかりません"));

        // ステータスを「解析中」に更新
        receipt.setStatus("analyzing");
        receiptRepository.save(receipt);

        try {
            // Vision API でテキスト抽出
            String rawText = visionService.extractText(receipt.getImagePath());
            String[] lines = rawText.split("\n");

            // 各項目をパース
            receipt.setStoreName(ocrParserService.parseStoreName(lines));
            receipt.setReceiptDate(ocrParserService.parseDate(lines));
            receipt.setAmount(ocrParserService.parseAmount(lines));
            receipt.setStatus("done");
            receipt.setAnalyzedAt(java.time.LocalDateTime.now());

        } catch (Exception e) {
            receipt.setStatus("error");
            receiptRepository.save(receipt);
            throw e;
        }

        receiptRepository.save(receipt);
        return toResponse(receipt);
    }

    private ReceiptResponse toResponse(Receipt r) {
        ReceiptResponse res = new ReceiptResponse();
        res.setId(r.getId());
        res.setStatus(r.getStatus());
        res.setStoreName(r.getStoreName());
        res.setReceiptDate(r.getReceiptDate());
        res.setAmount(r.getAmount());
        res.setAccountItem(r.getAccountItem());
        res.setMemo(r.getMemo());
        res.setCreatedAt(r.getCreatedAt());
        return res;
    }
}