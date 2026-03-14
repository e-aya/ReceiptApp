package com.receiptapp.backend.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClaudeService {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${claude.api-key}")
    private String apiKey;

    private static final List<String> ACCOUNT_ITEMS = List.of(
            "消耗品費", "会議費", "接待交際費", "旅費交通費",
            "通信費", "広告宣伝費", "福利厚生費",
            "水道光熱費", "地代家賃", "雑費"
    );

    /**
     * 画像からOCR + 仕訳を一括処理
     */
    public ClaudeOcrResult analyzeReceipt(String imagePath) throws Exception {
        // 画像をBase64エンコード
        byte[] imageBytes = Files.readAllBytes(Paths.get(imagePath));
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);

        String prompt = """
        この領収書画像を解析して、以下の情報をJSON形式で返してください。

        {
          "storeName": "店名",
          "receiptDate": "YYYY-MM-DD形式の日付",
          "amount": 金額(数値のみ),
          "accountItem": "勘定科目"
        }

        【storeName のルール】
        - レシート最上部のブランド名＋支店名を含める
          例: 「LAWSON 宗像日の里五丁目店」「資さん 宗像店」
        - 住所（市・区・町・丁目・番地など）・電話番号・URLは絶対に含めない
        - 濁点の誤読に注意（「た」と「だ」など慎重に確認）

        【receiptDate のルール】
        - 「取引日」「購入日」「お取扱日」の日付を使用
        - クレジットカードの有効期限（****/**形式）は絶対に使わない
        - 年号の解釈:
          ・4桁年(例:2026年) → そのまま使用
          ・2桁年(例:26年)   → 2000を足す（26→2026）
          ・「平成」は使わない。2桁年は必ず2000+XX年として解釈

        【amount のルール】
        - 「合計」「合　計」の税込金額のみ
        - お預り・お釣り・クレジット取扱合計・買上金額は使わない
        - カンマを除いた整数のみ

        【accountItem のルール】
        以下の定義を上から順に判定し最初に該当したものを選択:
        1. 消耗品費: コンビニ（LAWSON・FamilyMart・セブン等）は金額に関わらず必ず消耗品費。スーパー・ドラッグストア・日用品
        2. 旅費交通費: 電車・バス・タクシー・新幹線・宿泊
        3. 通信費: 携帯電話・インターネット・郵便
        4. 会議費: 合計1,000円以下の飲食店・カフェ・ファストフード
        5. 接待交際費: 合計1,000円超の飲食店・定食屋・レストラン・居酒屋
        6. 広告宣伝費: 広告・チラシ・販促品
        7. 福利厚生費: 従業員全員対象の飲食・健康診断
        8. 水道光熱費: 電気・ガス・水道
        9. 地代家賃: 家賃・駐車場代
        10. 雑費: 上記に該当しないもの

        - 不明な項目はnullを設定
        - JSONのみ返答、説明文不要
        """;

        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .build();

        MessageCreateParams params = MessageCreateParams.builder()
                .model(Model.CLAUDE_HAIKU_4_5)
                .maxTokens(300L)
                .addUserMessageOfBlockParams(List.of(
                        ContentBlockParam.ofImage(
                                ImageBlockParam.builder()
                                        .source(Base64ImageSource.builder()
                                                .mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
                                                .data(base64Image)
                                                .build())
                                        .build()
                        ),
                        ContentBlockParam.ofText(
                                TextBlockParam.builder()
                                        .text(prompt)
                                        .build()
                        )
                ))
                .build();

        Message message = client.messages().create(params);

        String text = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(TextBlock::text)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Claude APIからレスポンスが取得できませんでした"));

        log.info("Claude OCR response: {}", text);

        String json = text
                .replaceAll("(?s)```json\\s*", "")
                .replaceAll("```", "")
                .trim();

        JsonNode result = objectMapper.readTree(json);

        return new ClaudeOcrResult(
                result.path("storeName").asText(null),
                result.path("receiptDate").asText(null),
                result.path("amount").isNull() ? null : result.path("amount").asInt(),
                validateAccountItem(result.path("accountItem").asText(null))
        );
        // ★ 例外はthrowしてReceiptServiceでフォールバック
    }

    private String validateAccountItem(String item) {
        if (item == null) return "消耗品費";
        return ACCOUNT_ITEMS.stream().filter(a -> a.equals(item)).findFirst().orElse("消耗品費");
    }

    // OCR結果DTO
    public record ClaudeOcrResult(String storeName, String receiptDate, Integer amount, String accountItem) {
        public static ClaudeOcrResult empty() {
            return new ClaudeOcrResult(null, null, null, "消耗品費");
        }
    }
}