package com.receiptapp.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.Map;

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
    public ClaudeOcrResult analyzeReceipt(String imagePath) {
        try {
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
                    
                    【重要なルール】
                    - storeName: レシート最上部の店舗名のみ（住所・電話番号は含めない）
                    - receiptDate: 「取引日」「購入日」「お取扱日」の日付を使用
                      ※ クレジットカードの有効期限は絶対に使わない
                      ※ 2桁年(例:26年)は2000年代として解釈(2026年)
                    - amount: 「合計」「合　計」の金額(税込)のみ
                      ※ お預り・お釣り・クレジット取扱合計は使わない
                      ※ カンマを除いた数値のみ
                    - accountItem: 以下から1つ選択
                      消耗品費, 会議費, 接待交際費, 旅費交通費, 通信費,
                      広告宣伝費, 福利厚生費, 水道光熱費, 地代家賃, 雑費
                    
                    - 不明な項目はnullを設定
                    - JSONのみ返答、説明文不要
                    """;

            String requestJson = objectMapper.writeValueAsString(Map.of(
                    "model", "claude-haiku-4-5-20251001",
                    "max_tokens", 300,
                    "messages", List.of(Map.of(
                            "role", "user",
                            "content", List.of(
                                    Map.of(
                                            "type", "image",
                                            "source", Map.of(
                                                    "type", "base64",
                                                    "media_type", "image/jpeg",
                                                    "data", base64Image
                                            )
                                    ),
                                    Map.of(
                                            "type", "text",
                                            "text", prompt
                                    )
                            )
                    ))
            ));

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.anthropic.com/v1/messages"))
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> response = client.send(
                    request, HttpResponse.BodyHandlers.ofString()
            );

            System.out.println("Claude OCR status: " + response.statusCode());

            if (response.statusCode() != 200) {
                System.err.println("Claude OCR エラー: " + response.body());
                return ClaudeOcrResult.empty();
            }

            JsonNode root = objectMapper.readTree(response.body());
            String text = root.path("content").get(0)
                    .path("text").asText().trim();

            // JSON部分を抽出（```json ... ``` で囲まれている場合も対応）
            String json = text
                    .replaceAll("```json", "")
                    .replaceAll("```", "")
                    .trim();

            JsonNode result = objectMapper.readTree(json);

            return new ClaudeOcrResult(
                    result.path("storeName").asText(null),
                    result.path("receiptDate").asText(null),
                    result.path("amount").isNull() ? null : result.path("amount").asInt(),
                    validateAccountItem(result.path("accountItem").asText(null))
            );

        } catch (Exception e) {
            System.err.println("Claude OCR 例外: " + e.getMessage());
            return ClaudeOcrResult.empty();
        }
    }

    public String suggestAccountItem(String storeName, Integer amount) {
        try {
            String prompt = String.format("""
                            以下の領収書情報から最も適切な勘定科目を1つだけ答えてください。
                            
                            店名: %s
                            金額: %d円
                            
                            選択肢:
                            %s
                            
                            回答は選択肢の中から1つだけ、余分な説明なしで答えてください。
                            """,
                    storeName,
                    amount != null ? amount : 0,
                    String.join("\n", ACCOUNT_ITEMS)
            );

            Map<String, Object> requestBody = Map.of(
                    "model", "claude-haiku-4-5-20251001",
                    "max_tokens", 50,
                    "messages", List.of(
                            Map.of("role", "user", "content", prompt)
                    )
            );

            String responseBody = webClientBuilder.build()
                    .post()
                    .uri("https://api.anthropic.com/v1/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header("content-type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(responseBody);
            String suggested = root
                    .path("content").get(0)
                    .path("text").asText()
                    .trim();

            // 選択肢に含まれるものだけ返す（安全チェック）
            return ACCOUNT_ITEMS.stream()
                    .filter(suggested::contains)
                    .findFirst()
                    .orElse("消耗品費");

        } catch (Exception e) {
            // API失敗時はデフォルト値
            return "消耗品費";
        }
    }

    private String validateAccountItem(String item) {
        if (item == null) return "消耗品費";
        return ACCOUNT_ITEMS.stream()
                .filter(a -> a.equals(item))
                .findFirst()
                .orElse("消耗品費");
    }

    // OCR結果DTO
    public record ClaudeOcrResult(
            String storeName,
            String receiptDate,
            Integer amount,
            String accountItem
    ) {
        public static ClaudeOcrResult empty() {
            return new ClaudeOcrResult(null, null, null, "消耗品費");
        }
    }
}