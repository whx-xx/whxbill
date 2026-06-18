package com.whxbill.backend.modules.ocr.controller;

import com.whxbill.backend.common.api.ApiResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ocr")
public class OcrController {

    private static final Pattern SIGNED_AMOUNT = Pattern.compile("([+-])\\s*(?:¥|￥)?\\s*(\\d+(?:\\.\\d{1,2})?)");
    private static final Pattern ANY_AMOUNT = Pattern.compile("(?:¥|￥)?\\s*(\\d+(?:\\.\\d{1,2})?)");
    private static final Pattern ORDER_NO = Pattern.compile("[A-Z0-9]{12,}");

    @PostMapping("/draft")
    @PreAuthorize("hasAuthority('bill:create')")
    public ApiResponse<Map<String, Object>> draft(@RequestBody Map<String, String> payload) {
        String rawText = payload.getOrDefault("text", "");
        List<String> lines = normalizeLines(rawText);

        String productName = firstNonBlank(
            findValue(lines, "商品", "商品名称", "交易商品", "项目"),
            inferProduct(lines)
        );
        String merchantFullName = firstNonBlank(findValue(lines, "商户全称", "商户名称", "商户"), "");
        String paymentMethod = firstNonBlank(findValue(lines, "支付方式", "付款方式", "收支账户", "账户"), "");
        String billTime = normalizeDateTime(firstNonBlank(findValue(lines, "支付时间", "交易时间", "创建时间", "时间"), ""));
        String transactionNo = firstNonBlank(findValue(lines, "交易单号", "交易号", "流水号"), findOrderNo(lines, 0));
        String merchantOrderNo = firstNonBlank(findValue(lines, "商户单号", "商家单号", "订单号"), findOrderNo(lines, 1));

        AmountGuess amountGuess = guessAmount(lines);
        String suggestedType = guessType(lines, amountGuess.signedAmount());
        String merchantName = firstNonBlank(productName, merchantFullName, firstBusinessLine(lines), "OCR 草稿");

        Map<String, Object> result = new HashMap<>();
        result.put("merchantName", merchantName);
        result.put("productName", productName);
        result.put("merchantFullName", merchantFullName);
        result.put("paymentMethod", paymentMethod);
        result.put("accountName", paymentMethod);
        result.put("billTime", billTime);
        result.put("transactionNo", transactionNo);
        result.put("merchantOrderNo", merchantOrderNo);
        result.put("amount", amountGuess.amount());
        result.put("signedAmount", amountGuess.signedAmount());
        result.put("suggestedType", suggestedType);
        result.put("text", String.join("\n", lines));
        result.put("confidenceTips", buildTips(productName, amountGuess.amount(), billTime, paymentMethod));
        return ApiResponse.success(result);
    }

    private List<String> normalizeLines(String text) {
        return text.lines()
            .map(line -> line
                .replace('\u00A0', ' ')
                .replaceAll("(?<=[\\p{IsHan}])\\s+(?=[\\p{IsHan}])", "")
                .replaceAll("\\s{2,}", " ")
                .replace("：", ":")
                .replace("—", "-")
                .trim())
            .filter(line -> !line.isBlank())
            .toList();
    }

    private String findValue(List<String> lines, String... labels) {
        for (int index = 0; index < lines.size(); index++) {
            String compact = lines.get(index).replaceAll("\\s+", "");
            for (String label : labels) {
                int position = compact.indexOf(label);
                if (position < 0) continue;
                String value = compact.substring(position + label.length())
                    .replaceFirst("^[:：\\-]+", "")
                    .trim();
                if (!value.isBlank()) return value;
                if (index + 1 < lines.size()) return lines.get(index + 1).trim();
            }
        }
        return "";
    }

    private AmountGuess guessAmount(List<String> lines) {
        for (String line : lines) {
            Matcher matcher = SIGNED_AMOUNT.matcher(line.replace(" ", ""));
            if (matcher.find()) {
                BigDecimal value = new BigDecimal(matcher.group(2));
                BigDecimal signed = "-".equals(matcher.group(1)) ? value.negate() : value;
                return new AmountGuess(value.toPlainString(), signed.toPlainString());
            }
        }

        String amountLine = firstNonBlank(findValue(lines, "金额", "实付", "付款", "收款"), "");
        Matcher labeled = ANY_AMOUNT.matcher(amountLine);
        if (labeled.find()) {
            return new AmountGuess(labeled.group(1), labeled.group(1));
        }

        for (String line : lines) {
            Matcher matcher = ANY_AMOUNT.matcher(line);
            if (matcher.find() && !looksLikeDateOrOrder(line)) {
                return new AmountGuess(matcher.group(1), matcher.group(1));
            }
        }
        return new AmountGuess("0.00", "0.00");
    }

    private String guessType(List<String> lines, String signedAmount) {
        String text = String.join(" ", lines);
        try {
            if (new BigDecimal(signedAmount).compareTo(BigDecimal.ZERO) < 0) return "EXPENSE";
        } catch (NumberFormatException ignored) {
        }
        if (text.contains("支出") || text.contains("付款") || text.contains("已支付") || text.contains("支付成功")) {
            return "EXPENSE";
        }
        if (text.contains("收入") || text.contains("收款成功") || text.contains("已收款")
            || text.contains("到账") || text.contains("转入") || text.contains("收到转账")
            || text.contains("退款")) {
            return "INCOME";
        }
        return "EXPENSE";
    }

    private String normalizeDateTime(String value) {
        if (value.isBlank()) return "";
        String normalized = value
            .replace("年", "-")
            .replace("月", "-")
            .replace("日", " ")
            .replace(".", ":")
            .replaceAll("\\s+", " ")
            .trim();
        Matcher matcher = Pattern.compile("(\\d{4})-(\\d{1,2})-(\\d{1,2})\\s*(\\d{1,2})[:\\-](\\d{1,2})[:\\-](\\d{1,2})").matcher(normalized);
        if (!matcher.find()) return normalized;
        return "%s-%02d-%02d %02d:%02d:%02d".formatted(
            matcher.group(1),
            Integer.parseInt(matcher.group(2)),
            Integer.parseInt(matcher.group(3)),
            Integer.parseInt(matcher.group(4)),
            Integer.parseInt(matcher.group(5)),
            Integer.parseInt(matcher.group(6))
        );
    }

    private String inferProduct(List<String> lines) {
        for (String line : lines) {
            if (line.contains("支付成功") || line.contains("当前状态") || line.contains("商户") || line.contains("单号")) continue;
            if (SIGNED_AMOUNT.matcher(line).find()) continue;
            if (line.length() >= 2 && line.length() <= 24 && line.matches(".*[\\p{IsHan}].*")) {
                return line;
            }
        }
        return "";
    }

    private String firstBusinessLine(List<String> lines) {
        return lines.stream()
            .filter(line -> !line.matches(".*[A-Z0-9]{8,}.*"))
            .filter(line -> !line.contains("支付") && !line.contains("单号") && !line.contains("状态"))
            .findFirst()
            .orElse("");
    }

    private String findOrderNo(List<String> lines, int skip) {
        int seen = 0;
        for (String line : lines) {
            Matcher matcher = ORDER_NO.matcher(line.replace("\"", ""));
            if (!matcher.find()) continue;
            if (seen++ == skip) return matcher.group();
        }
        return "";
    }

    private boolean looksLikeDateOrOrder(String line) {
        String compact = line.replaceAll("\\s+", "");
        return compact.matches(".*\\d{4}年\\d{1,2}月\\d{1,2}日.*")
            || compact.matches(".*\\d{4}-\\d{1,2}-\\d{1,2}.*")
            || compact.matches(".*[A-Z0-9]{12,}.*");
    }

    private List<String> buildTips(String productName, String amount, String billTime, String paymentMethod) {
        List<String> tips = new ArrayList<>();
        if (productName.isBlank()) tips.add("未明确识别到商品名称");
        if ("0.00".equals(amount)) tips.add("未明确识别到金额");
        if (billTime.isBlank()) tips.add("未明确识别到支付时间");
        if (paymentMethod.isBlank()) tips.add("未明确识别到支付方式");
        return tips;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }

    private record AmountGuess(String amount, String signedAmount) {
    }
}
