package com.receiptapp.backend.service;

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
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class VisionService {

    @Value("${google.vision.api-key}")
    private String apiKey;

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    // Vision API を呼び出してテキストを抽出
    public String extractText(String imagePath) throws Exception {

        // 画像をBase64エンコード
        byte[] imageBytes = Files.readAllBytes(Paths.get(imagePath));
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);

        // リクエストボディ作成
        Map<String, Object> requestBody = Map.of(
                "requests", List.of(Map.of(
                        "image", Map.of("content", base64Image),
                        "features", List.of(Map.of(
                                "type", "DOCUMENT_TEXT_DETECTION"  // レシートに最適
                        )),
                        "imageContext", Map.of(
                                "languageHints", List.of("ja")     // 日本語優先
                        )
                ))
        );

        // Vision API 呼び出し
        String url = "https://vision.googleapis.com/v1/images:annotate?key=" + apiKey;

        String responseBody = webClientBuilder.build()
                .post()
                .uri(url)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        // テキスト抽出
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode textNode = root
                .path("responses")
                .path(0)
                .path("fullTextAnnotation")
                .path("text");

        if (textNode.isMissingNode()) {
            log.warn("Vision API からテキストが取得できませんでした");
            return "";
        }

        String extractedText = textNode.asText();
        // ★ 全行をログ出力（デバッグ用）
        String[] debugLines = extractedText.split("\n");
        for (int i = 0; i < debugLines.length; i++) {
            log.info("[OCR行{}]: '{}'", i, debugLines[i]);
        }
        return extractedText;
    }
}