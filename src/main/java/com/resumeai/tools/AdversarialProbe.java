package com.resumeai.tools;

import com.resumeai.pipeline.ParserChain;

/**
 * 研究工具（Q2，2026-10-14 深潜）：更恶意的输出损坏对解析链的穿透测试。
 *
 * 在同一份合法 OptimizeResult JSON 上施加 12 类对抗性损坏，跑当前 ParserChain
 * （L1 围栏剥离 + L2 语法修复 + strict/lenient 两级解析），记录穿透矩阵：
 *   PASS       修复后解析成功且 sections 完整（防御生效）
 *   SCHEMA_OK  解析成功但 sections 丢失（L1/L2 无法修复语义，交 L3 拒绝→重试）
 *   BREACH     解析失败（损坏穿透 L1/L2 → 触发重试，2 次失败则 FAILED）
 *
 * 源码全部用 \\uXXXX 转义构造变体（避免源文件本身引入不可见字符）。
 * 运行：java -cp target/classes:$(cat target/cp.txt) com.resumeai.tools.AdversarialProbe
 */
public final class AdversarialProbe {

    /** 合法基线输出（内容与 fixtures 语料一致口径）。 */
    private static final String VALID =
            "{\"sections\":[{\"field\":\"work_exps[0].description\",\"original\":\"参与订单系统开发\","
            + "\"rewritten\":\"负责订单模块核心链路开发与迭代\",\"reason\":\"动词强化，事实不变\"}],"
            + "\"score_before\":62,\"score_after\":78}";

    private static final String SEC = "{\"field\":\"f\",\"original\":\"o\",\"rewritten\":\"r\",\"reason\":\"x\"}";

    private record Variant(String name, String input, String note) {}

    static Variant[] variants() {
        return new Variant[] {
            new Variant("00-valid", VALID, "合法基线（对照）"),
            // ---- 注释注入 ----
            new Variant("01-inline-comment",
                    "{\"sections\":[" + SEC + "], // 只保留一个元素\n\"score_before\":62,\"score_after\":78}",
                    "行内尾注释（// 在逗号后同一行）"),
            new Variant("02-block-comment",
                    "{\"sections\":[" + SEC + "] /* 压缩演示 */, \"score_before\":62,\"score_after\":78}",
                    "块注释插在成员之间"),
            new Variant("03-comment-in-value",
                    "{\"sections\":[" + SEC.replace("\"r\"", "\"负责 /* 核心 */ 链路\"")
                    + "],\"score_before\":62,\"score_after\":78}",
                    "字符串值内部的注释——是合法内容，必须原样保留"),
            // ---- 混合语言键 ----
            new Variant("04-cn-key",
                    "{\"章节\":[" + SEC + "],\"score_before\":62,\"score_after\":78}",
                    "中文键替代 sections（未知字段，Jackson 忽略 → sections=null → L3 拒）"),
            new Variant("05-homoglyph-key",
                    "{\"secti\\u03BFns\":[" + SEC + "],\"score_before\":62,\"score_after\":78}".replace("\\u03BF", "\u03BF"),
                    "希腊同形字母 omicron (U+03BF) 替换键名中的 o → L3 拒"),
            // ---- Unicode 同形/全角结构字符 ----
            new Variant("06-fullwidth-braces",
                    "\uFF5B\"sections\":[" + SEC + "],\"score_before\":62,\"score_after\":78\uFF5D",
                    "全角大括号（U+FF5B/U+FF5D）"),
            new Variant("07-fullwidth-quotes",
                    "\uFF5B\uFF02sections\uFF02:[{\uFF02field\uFF02:\uFF02f\uFF02,\uFF02original\uFF02:\uFF02o\uFF02,"
                    + "\uFF02rewritten\uFF02:\uFF02r\uFF02,\uFF02reason\uFF02:\uFF02x\uFF02}],"
                    + "\uFF02score_before\uFF02:62,\uFF02score_after\uFF02:78\uFF5D",
                    "全角大括号+全角双引号（U+FF5B/U+FF02 组合）作定界符"),
            new Variant("08-zero-width-in-key",
                    "{\"sections\u200B\":[" + SEC + "],\"score_before\":62,\"score_after\":78}",
                    "键名尾部零宽空格（U+200B）"),
            new Variant("09-zero-width-structural",
                    "{\"sections\"\u200B:[" + SEC + "],\"score_before\":62,\"score_after\":78}",
                    "结构位置（闭引号与冒号之间）零宽空格"),
            new Variant("10-bom",
                    "\uFEFF{\"sections\":[" + SEC + "],\"score_before\":62,\"score_after\":78}",
                    "BOM（U+FEFF）前缀"),
            new Variant("11-nbsp",
                    "{\"sections\":[{\"field\":\"f\",\u00A0\"original\":\"o\",\"rewritten\":\"r\",\"reason\":\"x\"}],\"score_before\":62,\"score_after\":78}",
                    "成员间不间断空格（U+00A0）替代普通空格"),
            new Variant("12-fullwidth-colon-comma",
                    "{\"sections\"\uFF1A[" + SEC.replace(",", "\uFF0C") + "]\uFF0C\"score_before\":62\uFF0C\"score_after\":78}",
                    "全角冒号（U+FF1A）与全角逗号（U+FF0C）"),
        };
    }

    public static void main(String[] args) {
        ParserChain chain = new ParserChain();
        System.out.println("## Q2 对抗性损坏穿透矩阵（当前防御，单遍无重试）\n");
        System.out.println("| 变体 | L1/L2 后果 | 说明 |");
        System.out.println("|---|---|---|");
        for (Variant v : variants()) {
            String outcome;
            try {
                var attempt = chain.parse(v.input());
                if (attempt.result != null) {
                    boolean sectionsLost = attempt.result.getSections() == null
                            || attempt.result.getSections().isEmpty();
                    outcome = sectionsLost
                            ? "SCHEMA_OK(sections 丢失→L3 拒)"
                            : (attempt.repaired ? "PASS(修复后解析)" : "PASS(直接解析)");
                } else {
                    outcome = "BREACH(解析失败)";
                }
            } catch (Exception e) {
                outcome = "BREACH(异常:" + e.getClass().getSimpleName() + ")";
            }
            System.out.printf("| %s | %s | %s |%n", v.name(), outcome, v.note());
        }
    }

    private AdversarialProbe() {}
}
