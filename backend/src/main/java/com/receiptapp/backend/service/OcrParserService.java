package com.receiptapp.backend.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OcrParserService {

    // 店名抽出
    public String parseStoreName(List<String> lines) {

        // ★ 既知のコンビニ・チェーン名（英語表記）
        List<String> knownChains = List.of(
                "FamilyMart", "LAWSON", "7-Eleven", "7-ELEVEN",
                "MINISTOP", "ministop", "DAILY"
        );

        // 先頭8行を対象
        List<String> candidates = lines.subList(0, Math.min(8, lines.size()));

        // ★ コンビニチェーン名チェック（英語ブランド名 + 次行の店舗名を結合）
        for (int i = 0; i < candidates.size(); i++) {
            String line = candidates.get(i).trim();
            for (String chain : knownChains) {
                if (line.contains(chain) && i + 1 < candidates.size()) {
                    String nextLine = candidates.get(i + 1).trim();
                    // 次行が住所・電話番号でなければ結合
                    if (!nextLine.matches(".*\\d{2,4}[-－]\\d{3,4}[-－]\\d{4}.*")
                            && !nextLine.matches(".*[都道府県市区町村].*")) {
                        return chain + " " + nextLine;
                    }
                    return chain;
                }
            }
        }

        List<String> regExpList = List.of(
                "/^(tel|fax|TEL|FAX)/i",
                "/\\d{2,4}-\\d{3,4}-\\d{4}/",
                "/^\\d{4}[\\/\\-]\\d{1,2}[\\/\\-]\\d{1,2}/",
                "/^(領収書|receipt|invoice|レシート)/i",
                "/^(合計|小計|税込|税抜|消費税|内税|外税|お釣|現金|クレジット|Verifone)/i",
                "/^[\\d¥￥,.\\s]+$/",
                "/^[-=*＊＝]{3,}/",
                "/ありがとう/",
                ".*毎度[、\\s]*ありがとうございま(す|した).*",
                "/^(毎度|いつも|またのお越し|ご利用|ご来店)/",
                "\".*[都道府県市区町村].*\\d+.*\"",
                "/丁目|番地/",
                "/^T\\d{13,}/",
                "/^(登録番号|レシート|伝票|店舗|端末)/",
                "/マネージャー|マネジャー|店長|スタッフ|担当/",
                "/^\\d{1,2}:\\d{2}/",
                "/\\d\\s*(点|名|名様)$/",
                "/^\\d{4,}$/",
                "/^※/",
                "/軽減税率/",
                "/税率対象/",
                "/ポイント|会員|お客様番号/"
        );

        // 日本語スコアリングで店名候補を選定
        String bestLine = null;
        int bestScore = 0;

        for (int i = 0; i < candidates.size(); i++) {
            String line = candidates.get(i).trim();

            // スキップ条件
            var skip = false;
            if (line.isEmpty()) continue;
            for (var regExp : regExpList) {
                if (Pattern.matches(regExp, line)) {
                    skip = true;
                    break;
                }
            }
            if (skip) continue;

            int score = 0;

            // 日本語文字の割合でスコアリング
            long japaneseCount = line.chars()
                    .filter(c -> (c >= 0x3040 && c <= 0x309F)   // ひらがな
                            || (c >= 0x30A0 && c <= 0x30FF)   // カタカナ
                            || (c >= 0x4E00 && c <= 0x9FFF))  // 漢字
                    .count();
            score += (int)(japaneseCount * 3);

            // 短すぎる・長すぎる行はペナルティ
            if (line.length() < 2) score -= 10;
            if (line.length() > 20) score -= 5;

            // 先頭行ほど優先
            score -= i * 2;

            if (score > bestScore) {
                bestScore = score;
                bestLine = line;

                // ★ 次行が短い日本語なら結合（例:「名物 銀河の」+「チャンポン」）
                if (i + 1 < candidates.size()) {
                    String nextLine = candidates.get(i + 1).trim();
                    boolean nextIsShortJapanese = nextLine.length() <= 10
                            && nextLine.chars().anyMatch(c ->
                            (c >= 0x3040 && c <= 0x309F)
                                    || (c >= 0x30A0 && c <= 0x30FF)
                                    || (c >= 0x4E00 && c <= 0x9FFF));
                    boolean nextIsNotAddress = !nextLine.matches(".*[都道府県市区町村].*");
                    boolean nextIsNotPhone   = !nextLine.matches(".*\\d{2,4}[-－]\\d{3,4}[-－]\\d{4}.*");

                    if (nextIsShortJapanese && nextIsNotAddress && nextIsNotPhone) {
                        bestLine = line + " " + nextLine;
                        break;
                    }
                }
            }
        }

        return bestLine;
    }

    // 日付抽出
    public LocalDate parseDate(List<String> lines) {
        Pattern p = Pattern.compile("(\\d{2,4})年\\s*(\\d{1,2})月\\s*(\\d{1,2})日");
        for (String line : lines) {
            Matcher m = p.matcher(line);
            if (m.find()) {
                int year = Integer.parseInt(m.group(1));
                if (year < 100) year += 2000; // ★ 2桁年 → 2026等に変換
                int month = Integer.parseInt(m.group(2));
                int day   = Integer.parseInt(m.group(3));
                return LocalDate.of(year, month, day);
            }
        }
        return null;
    }

    // 金額抽出
    public Integer parseAmount(List<String> lines) {

        Pattern yenPattern    = Pattern.compile("[¥￥](\\d{1,3}(?:,\\d{3})*|\\d+)");
        Pattern circlePattern = Pattern.compile("^(\\d{1,3}(?:,\\d{3})*|\\d+)\\s*円$");

        // ★ スキップワード（フォールバックでも使用）
        Set<String> skipWords = Set.of(
                "現金", "お釣", "釣り", "お預り", "預り",
                "クレジット", "電子マネー", "チャージ", "ポイント"
        );

        // 合計行を探す（全角スペース対応を強化）
        int totalLineIndex = -1;
        for (int i = lines.size() - 1; i >= 0; i--) {
            String t = lines.get(i).trim();
            // ★ Unicodeの全角スペース(\u3000)も明示的に対応
            String normalized = t.replace("\u3000", " ").replaceAll("\\s+", "");
            if (normalized.startsWith("合計")
                    || t.equals("合計")
                    || t.matches("^合[\\s\u3000]*計.*")) {
                totalLineIndex = i;
                break;
            }
        }

        if (totalLineIndex >= 0) {
            // 同一行チェック
            String totalLine = lines.get(totalLineIndex);
            Integer val = extractAmount(totalLine, yenPattern, circlePattern);
            if (val != null) return val;

            // 合計行の後を探す（スキップワード適用）
            for (int i = totalLineIndex + 1; i < Math.min(totalLineIndex + 5, lines.size()); i++) {
                String line = lines.get(i).trim();
                if (skipWords.stream().anyMatch(line::contains)) continue;
                val = extractAmount(line, yenPattern, circlePattern);
                if (val != null) return val;
            }
        }

        // 小計行でも試みる
        int subTotalIndex = -1;
        for (int i = lines.size() - 1; i >= 0; i--) {
            String t = lines.get(i).trim()
                    .replace("\u3000", " ").replaceAll("\\s+", "");
            if (t.startsWith("小計")) {
                subTotalIndex = i;
                break;
            }
        }

        if (subTotalIndex >= 0) {
            String subLine = lines.get(subTotalIndex);
            Integer val = extractAmount(subLine, yenPattern, circlePattern);
            if (val != null) return val;

            for (int i = subTotalIndex + 1; i < Math.min(subTotalIndex + 5, lines.size()); i++) {
                String line = lines.get(i).trim();
                if (skipWords.stream().anyMatch(line::contains)) continue;
                val = extractAmount(line, yenPattern, circlePattern);
                if (val != null) return val;
            }
        }

        // ★ フォールバック: スキップワードを除いた最大金額
        return lines.stream()
                .filter(l -> skipWords.stream().noneMatch(l::contains)) // ← お預り等を除外
                .map(l -> extractAmount(l, yenPattern, circlePattern))
                .filter(v -> v != null && v > 0)
                .max(Integer::compareTo)
                .orElse(null);
    }

    // ★ ¥形式・円形式の両方から金額を抽出するヘルパー
    private Integer extractAmount(String line, Pattern yenPattern, Pattern circlePattern) {
        Matcher m1 = yenPattern.matcher(line);
        if (m1.find()) {
            return Integer.parseInt(m1.group(1).replace(",", ""));
        }
        Matcher m2 = circlePattern.matcher(line.trim());
        if (m2.find()) {
            return Integer.parseInt(m2.group(1).replace(",", ""));
        }
        return null;
    }
}