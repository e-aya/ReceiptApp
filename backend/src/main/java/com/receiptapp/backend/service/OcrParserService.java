package com.receiptapp.backend.service;

import org.springframework.stereotype.Service;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OcrParserService {

    // 店名抽出
    public String parseStoreName(String[] lines) {
        int limit = Math.min(8, lines.length);
        String best = null;
        int bestScore = Integer.MIN_VALUE;
        int bestIdx = -1;

        for (int i = 0; i < limit; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            if (shouldSkip(line)) continue;
            if (!containsJapanese(line)) continue;

            int score = calcScore(line, i);
            if (score > bestScore) {
                bestScore = score;
                best = line;
                bestIdx = i;
            }
        }

        if (best == null) return null;

        // ★ 次の行が日本語かつスキップ対象でない場合は結合
        // 例: "名物 銀河の" + "チャンポン" → "名物 銀河のチャンポン"
        if (bestIdx + 1 < limit) {
            String nextLine = lines[bestIdx + 1].trim();
            if (!nextLine.isEmpty()
                    && !shouldSkip(nextLine)
                    && containsJapanese(nextLine)
                    && nextLine.length() <= 12  // 短い行のみ結合
            ) {
                best = best + nextLine;
            }
        }

        // 先頭の記号を除去
        return best.replaceAll(
                "^[^\\u3040-\\u309f\\u30a0-\\u30ff\\u4e00-\\u9faf\\w]+", ""
        ).trim();
    }

    // 日付抽出
    public String parseDate(String[] lines) {
        Pattern[] patterns = {
                Pattern.compile("(\\d{4})[/\\-](\\d{1,3})[/\\-](\\d{1,2})"),
                Pattern.compile("(\\d{4})年\\s*(\\d{1,2})月\\s*(\\d{1,2})日"),
        };

        for (String line : lines) {
            for (Pattern p : patterns) {
                Matcher m = p.matcher(line);
                if (m.find()) {
                    String year  = m.group(1);
                    String month = fixTwoDigit(m.group(2));
                    String day   = fixTwoDigit(m.group(3));
                    // 年の妥当性チェック
                    int y = Integer.parseInt(year);
                    if (y < 2000 || y > 2100) continue;
                    return year + "-" + month + "-" + day;
                }
            }
        }
        return null;
    }

    // 金額抽出
    public String parseAmount(String[] lines) {

        Pattern yenPattern = Pattern.compile("[¥￥]\\s*([\\d,，.．]+)");

        java.util.function.Function<String, String> cleanNum = s ->
                s.replaceAll("[,，.．]", "");

        // 除外キーワード（合計以外の支払い行）
        java.util.function.Predicate<String> isExcluded = line -> {
            String n = line.replaceAll("\\s", "");
            return n.contains("現金") || n.contains("お釣")
                    || n.contains("おつり") || n.contains("クレジット")
                    || n.contains("ポイント") || n.contains("電子マネー")
                    || n.startsWith("(") || n.startsWith("（");
        };

        // ★ Step1: 「合計」行を探す（最後に出現したものを優先）
        int totalIdx = -1;
        for (int i = 0; i < lines.length; i++) {
            String n = lines[i].replace("　", "").replaceAll("\\s+", "");
            // 「合計」の完全一致 or 行頭・行末
            if (n.equals("合計") || n.equals("合计")
                    || n.matches("^合[　\\s]*計.*")
                    || n.matches(".*[^内対象]合計$")) {
                totalIdx = i;
            }
        }

        // ★ Step2: 合計行と同一行に金額があるか確認
        if (totalIdx >= 0) {
            Matcher m = yenPattern.matcher(lines[totalIdx]);
            if (m.find()) {
                String num = cleanNum.apply(m.group(1));
                if (num.matches("\\d{3,6}")) return num;
            }
        }

        // ★ Step3: 合計行の「後」で最初の¥付き金額を返す
        if (totalIdx >= 0) {
            for (int i = totalIdx + 1; i < lines.length; i++) {
                String line = lines[i].trim();
                if (isExcluded.test(line)) continue;
                Matcher m = yenPattern.matcher(line);
                if (m.find()) {
                    String num = cleanNum.apply(m.group(1));
                    if (num.matches("\\d{3,6}")) return num;
                }
            }
        }

        // ★ Step4: 「小計」でも同様に試みる
        int subTotalIdx = -1;
        for (int i = 0; i < lines.length; i++) {
            String n = lines[i].replace("　", "").replaceAll("\\s+", "");
            if (n.equals("小計") || n.matches("^小[　\\s]*計.*")) {
                subTotalIdx = i;
            }
        }
        if (subTotalIdx >= 0) {
            for (int i = subTotalIdx + 1; i < lines.length; i++) {
                String line = lines[i].trim();
                if (isExcluded.test(line)) continue;
                Matcher m = yenPattern.matcher(line);
                if (m.find()) {
                    String num = cleanNum.apply(m.group(1));
                    if (num.matches("\\d{3,6}")) return num;
                }
            }
        }

        // ★ Step5: フォールバック（¥付きで3桁以上の最大金額）
        String maxAmount = null;
        int maxVal = 0;
        for (String line : lines) {
            if (isExcluded.test(line)) continue;
            if (line.matches(".*\\d{2,4}-\\d{3,4}-\\d{4}.*")) continue;
            if (line.matches(".*\\d{4}[/\\-]\\d{1,2}.*")) continue;
            Matcher m = yenPattern.matcher(line);
            while (m.find()) {
                String num = cleanNum.apply(m.group(1));
                if (num.matches("\\d{3,6}")) {
                    int val = Integer.parseInt(num);
                    if (val > maxVal) {
                        maxVal = val;
                        maxAmount = num;
                    }
                }
            }
        }
        return maxAmount;
    }

    // スキップすべき行か判定
    private boolean shouldSkip(String line) {
        return line.matches(".*\\d{2,4}-\\d{3,4}-\\d{4}.*")
                || line.matches(".*[都道府県市区町村].*")
                || line.matches(".*丁目.*|.*番地.*")
                || line.matches("(?i)^(tel|fax).*")
                || line.matches(".*ありがとう.*")
                || line.matches(".*毎度.*")
                || line.matches("^[¥￥\\d,，.\\s]+$")
                || line.startsWith("※")
                || line.contains("軽減税率")
                || line.matches(".*T\\d{10,}.*")
                || line.matches(".*登録番号.*");
    }

    // 日本語を含むか
    private boolean containsJapanese(String s) {
        return s.matches(
                ".*[\\u3040-\\u309f\\u30a0-\\u30ff\\u4e00-\\u9faf].*"
        );
    }

    // スコア計算
    private int calcScore(String text, int idx) {
        int score = 0;
        long jpCount = text.chars()
                .filter(c -> c >= 0x3040 && c <= 0x9faf).count();
        score += jpCount * 2;
        score -= idx;
        if (text.matches(".*[店屋館亭堂食麺飯菜酒処拉].*")) score += 5;
        if (text.length() > 20) score -= 5;
        return score;
    }

    // 1〜2桁に補正（OCR誤読対応）
    private String fixTwoDigit(String s) {
        s = s.trim();
        try {
            int val = Integer.parseInt(s);
            if (val <= 31) return String.format("%02d", val);
            // 3桁の場合: 先頭0なら末尾2桁、そうでなければ先頭2桁
            if (s.startsWith("0")) {
                return s.substring(s.length() - 2);
            } else {
                return s.substring(0, 2);
            }
        } catch (NumberFormatException e) {
            return s;
        }
    }
}