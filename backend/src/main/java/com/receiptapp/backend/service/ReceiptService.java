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
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReceiptService {

    private final ReceiptRepository receiptRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final UsageLimitService usageLimitService;
    private final VisionService visionService;
    private final ClaudeService claudeService;
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
            String rawText = visionService.extractText(receipt.getImagePath());
            List<String> lines = Arrays.asList(rawText.split("\n"));

            receipt.setStoreName(ocrParserService.parseStoreName(lines));
            receipt.setReceiptDate(ocrParserService.parseDate(lines));
            receipt.setAmount(ocrParserService.parseAmount(lines));

            // ★ AI自動仕訳
            String accountItem = claudeService.suggestAccountItem(
                    receipt.getStoreName(),
                    receipt.getAmount()
            );
            receipt.setAccountItem(accountItem);

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
}