package com.receiptapp.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

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
}