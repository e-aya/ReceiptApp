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
                    
                    【storeName のルール】
                    - レシート最上部の店舗名のみ
                    - 支店名・号店名は含めてよい（例: LAWSON 宗像日の里五丁目店）
                    - 以下は絶対に含めない:
                      ・住所（市・区・町・丁目・番地など）
                      ・電話番号
                      ・URL
                    - 濁点の誤読に注意（「た」と「だ」など）
                      正しく読み取れない場合はひらがな・カタカナを慎重に確認
                    
                    【receiptDate のルール】
                            - 「取引日」「購入日」「お取扱日」の日付を使用
                            - クレジットカードの有効期限（****/**形式）は絶対に使わない
                            - 年号の解釈（これだけ覚えてください）:
                              ・4桁年(例:2026年) → そのまま使用
                              ・2桁年(例:26年)   → 2000を足す（26→2026、09→2009）
                              ・「平成」は絶対に使わない。現在は令和時代です。
                              ・26年 = 2026年（平成26年=2014年ではない）
                    
                    【amount のルール】
                    - 「合計」「合　計」の税込金額のみ
                    - お預り・お釣り・クレジット取扱合計・買上金額は使わない
                    - カンマを除いた整数のみ
                    
                    【accountItem のルール】
                    以下の定義を参考に最も適切な1つを選択:
                    - 消耗品費: コンビニ・スーパー・ドラッグストア・文房具・日用品
                    - 会議費: 社内打合せ・カフェ・ファストフード・コンビニ飲食
                        目安: 合計1,000円以下、または明らかに軽食
                    - 接待交際費: 定食屋・レストラン・居酒屋など食事を目的とした飲食
                        目安: 合計1,000円超の飲食店での食事
                        ※ うどん・ラーメン・定食は1,000円超なら接待交際費
                    - 旅費交通費: 電車・バス・タクシー・新幹線・宿泊
                    - 通信費: 携帯電話・インターネット・郵便
                    - 広告宣伝費: 広告・チラシ・販促品
                    - 福利厚生費: 従業員向けの飲食・健康診断（社内全員対象）
                    - 水道光熱費: 電気・ガス・水道
                    - 地代家賃: 家賃・駐車場代
                    - 雑費: 上記に該当しないもの
                    
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
                    """, storeName, amount != null ? amount : 0, String.join("\n", ACCOUNT_ITEMS));

            Map<String, Object> requestBody = Map.of("model", "claude-haiku-4-5-20251001", "max_tokens", 50, "messages", List.of(Map.of("role", "user", "content", prompt)));

            String responseBody = webClientBuilder.build().post().uri("https://api.anthropic.com/v1/messages").header("x-api-key", apiKey).header("anthropic-version", "2023-06-01").header("content-type", "application/json").bodyValue(requestBody).retrieve().bodyToMono(String.class).block();

            JsonNode root = objectMapper.readTree(responseBody);
            String suggested = root.path("content").get(0).path("text").asText().trim();

            // 選択肢に含まれるものだけ返す（安全チェック）
            return ACCOUNT_ITEMS.stream().filter(suggested::contains).findFirst().orElse("消耗品費");

        } catch (Exception e) {
            // API失敗時はデフォルト値
            return "消耗品費";
        }
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