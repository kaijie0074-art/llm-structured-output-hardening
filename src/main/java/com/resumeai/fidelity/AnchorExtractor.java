package com.resumeai.fidelity;

import com.resumeai.model.OptimizeResult;
import com.resumeai.model.ResumeSnapshot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 事实锚点抽取：从原文快照与改写结果两侧抽同一套锚点，供 FidelityGate 做差集。
 *
 * 锚点类型（不可变事实，只许格式规整不许增删）：
 * - 证书：CET-4/CET-6/CPA/PMP/司法考试 等证书词表 + 通用"大写缩写+考试/证书"模式
 * - 公司：以 公司/集团/事务所/银行/研究院 等 org 后缀词结尾的名词短语
 * - 日期区间：yyyy.MM - yyyy.MM / yyyy年M月—yyyy年M月 模式
 * - 量化数字：非年份的阿拉伯数字（"万"归一化），用于量级跳变检测
 */
public final class AnchorExtractor {

    private AnchorExtractor() {}

    /** 证书词表（简历语境高频，可按业务扩充）。 */
    private static final Set<String> CERTIFICATE_TOKENS = Set.of(
            "CET-4", "CET4", "CET-6", "CET6", "CPA", "PMP", "FRM", "CFA",
            "司法考试", "法律职业资格", "教师资格证", "计算机二级", "计算机三级",
            "普通话二甲", "驾驶证", "雅思", "托福", "GRE");

    private static final Pattern ORG_SUFFIX = Pattern.compile(
            "[\\u4e00-\\u9fa5A-Za-z0-9]{2,20}?(?:科技有限公司|信息技术有限公司|网络有限公司|集团|事务所|银行|研究院|研究所|公司)");

    private static final Pattern DATE_RANGE = Pattern.compile(
            "(19|20)\\d{2}[./年-]\\s?\\d{1,2}\\s?[月./-]\\s?[—~至-]?\\s?(19|20)\\d{2}[./年-]\\s?\\d{1,2}\\s?月?");

    /** 阿拉伯数字（含 千分位/万单位）。年份单独排除。 */
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:,\\d{3})*(?:\\.\\d+)?\\s*万?");

    private static final Pattern YEAR = Pattern.compile("(19|20)\\d{2}\\s*[年.]?");

    public static Set<String> certificates(String text) {
        Set<String> found = new HashSet<>();
        if (text == null) return found;
        for (String token : CERTIFICATE_TOKENS) {
            if (text.contains(token)) found.add(normalizeCert(token));
        }
        return found;
    }

    public static Set<String> companies(String text) {
        Set<String> found = new HashSet<>();
        if (text == null) return found;
        Matcher m = ORG_SUFFIX.matcher(text);
        while (m.find()) {
            found.add(m.group());
        }
        return found;
    }

    public static Set<String> dateRanges(String text) {
        Set<String> found = new HashSet<>();
        if (text == null) return found;
        Matcher m = DATE_RANGE.matcher(text);
        while (m.find()) {
            found.add(m.group().replaceAll("\\s", ""));
        }
        return found;
    }

    /**
     * 量化数字集合（"万"归一到数量级），排除年份。
     * 例："日均 3 万单" → 30000；"2023.09" → 排除（年份/日期已在区间锚点管）。
     */
    public static Set<Long> metrics(String text) {
        Set<Long> found = new HashSet<>();
        if (text == null) return found;
        Set<String> years = new HashSet<>();
        Matcher ym = YEAR.matcher(text);
        while (ym.find()) years.add(ym.group().replaceAll("\\s", ""));

        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            String rawNum = m.group();
            // 排除属于年份/日期上下文的数字
            if (years.contains(rawNum.replace("万", ""))) continue;
            if (isInsideDate(text, m.start(), m.end())) continue;
            long value = parseCnNumber(rawNum);
            found.add(value);
        }
        return found;
    }

    private static boolean isInsideDate(String text, int start, int end) {
        int ctxStart = Math.max(0, start - 4);
        int ctxEnd = Math.min(text.length(), end + 4);
        String ctx = text.substring(ctxStart, ctxEnd);
        return ctx.matches(".*(19|20)\\d{2}\\s*[./年-].*") && ctx.contains(String.valueOf(text.substring(start, end)));
    }

    static long parseCnNumber(String raw) {
        boolean wan = raw.endsWith("万");
        String digits = wan ? raw.substring(0, raw.length() - 1) : raw;
        digits = digits.replace(",", "").trim();
        try {
            double v = Double.parseDouble(digits);
            return Math.round(wan ? v * 10_000 : v);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static String normalizeCert(String token) {
        return token.replace("-", "").replace(" ", "").toUpperCase();
    }

    /** 改写侧聚合：把 sections 的 rewritten 拼起来抽锚点（reason 是说明文字不参与，防误伤）。 */
    public static Set<String> rewrittenTextAnchors(OptimizeResult result, AnchorKind kind) {
        StringBuilder sb = new StringBuilder();
        result.getSections().forEach(s -> sb.append(s.getRewritten()).append('\n'));
        return switch (kind) {
            case CERTIFICATE -> certificates(sb.toString());
            case COMPANY -> companies(sb.toString());
            case DATE_RANGE -> dateRanges(sb.toString());
        };
    }

    /** 原文侧聚合。 */
    public static Set<String> snapshotAnchors(ResumeSnapshot snapshot, AnchorKind kind) {
        StringBuilder sb = new StringBuilder();
        snapshot.getWorkExps().forEach(w -> sb.append(w.getCompany()).append(' ').append(w.getDateRange()).append('\n'));
        snapshot.getEducations().forEach(e -> sb.append(e.getSchool()).append(' ').append(e.getDateRange()).append('\n'));
        snapshot.getCertificates().forEach(c -> sb.append(c).append('\n'));
        return switch (kind) {
            case CERTIFICATE -> certificates(sb.toString());
            case COMPANY -> companies(sb.toString());
            case DATE_RANGE -> dateRanges(sb.toString());
        };
    }

    public enum AnchorKind { CERTIFICATE, COMPANY, DATE_RANGE }
}
