package com.resumeai.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * L2 语法层防御：LLM 常见 JSON 畸形的轻量修复。
 *
 * 修复规则集参考 mangiucugna/json_repair 的公开机制（见 docs/open-source-references.md），
 * 本项目按"简历改写输出"的高频故障定制并裁剪，每条规则刻意保守：
 * - 只把**结构位置**上的中文引号/单引号当定界符处理；
 * - 字符串**值内部**的引号（无论中英文）一律不动（评审意见 #2：中文引号在简历
 *   语境高频出现在内容里，误伤会把正确内容改坏）。
 *
 * 规则顺序有依赖：先键定界符，后 trailing comma / 注释，最后截断标记。
 */
public final class LenientRepair {

    private LenientRepair() {}

    /** 键定界符：{ 或 , 后的 “键”/‘键’ + 冒号。 */
    private static final Pattern QUOTED_KEY = Pattern.compile(
            "([{,]\\s*)[“”'](\\w+)[“”']\\s*:");

    /** 值中文引号定界：冒号后 “…” → "…"。只动定界位置，内容里的中文引号不碰。 */
    private static final Pattern CN_QUOTED_VALUE = Pattern.compile("(:\\s*)“([^”]*)”");

    /** 值单引号定界：冒号后 '…' → "…"。 */
    private static final Pattern SN_QUOTED_VALUE = Pattern.compile("(:\\s*)'([^']*)'");

    /** trailing comma：} 或 ] 前的逗号+空白。 */
    private static final Pattern TRAILING_COMMA = Pattern.compile(",\\s*([}\\]])");

    /** // 行注释：只删整行只有注释的行，避免误删 URL "//"。 */
    private static final Pattern LINE_COMMENT = Pattern.compile(
            "^\\s*//.*$", Pattern.MULTILINE);

    /** 供测试与日志观察：返回命中的规则名列表。 */
    public static List<String> appliedRules(String before) {
        List<String> rules = new ArrayList<>();
        if (QUOTED_KEY.matcher(before).find()) rules.add("QUOTED_KEY");
        if (CN_QUOTED_VALUE.matcher(before).find()) rules.add("CN_QUOTED_VALUE");
        if (SN_QUOTED_VALUE.matcher(before).find()) rules.add("SN_QUOTED_VALUE");
        if (TRAILING_COMMA.matcher(before).find()) rules.add("TRAILING_COMMA");
        if (LINE_COMMENT.matcher(before).find()) rules.add("LINE_COMMENT");
        return rules;
    }

    /** 明显截断：JSON 以逗号/冒号/开括号结尾。截断不可修复，直接判失败走重试（不硬补）。 */
    public static boolean looksTruncated(String json) {
        String s = json.trim();
        if (s.isEmpty()) return true;
        char last = s.charAt(s.length() - 1);
        return last == ',' || last == ':' || last == '{' || last == '[';
    }

    public static String repair(String json) {
        String out = normalizeStructural(json);
        out = QUOTED_KEY.matcher(out).replaceAll("$1\"$2\":");
        out = CN_QUOTED_VALUE.matcher(out).replaceAll("$1\"$2\"");
        out = SN_QUOTED_VALUE.matcher(out).replaceAll("$1\"$2\"");
        out = TRAILING_COMMA.matcher(out).replaceAll("$1");
        out = LINE_COMMENT.matcher(out).replaceAll("");
        return out;
    }

    /**
     * 结构感知归一化（研究 Q2 新增）：只在**字符串外部**做三件事——
     * 1) 剥注释：行内 // 尾注释与成员间块注释（原 LINE_COMMENT 只处理整行注释，
     *    "a":1, // note 形态会穿透）；字符串内部的注释样式文本是合法内容，原样保留；
     * 2) 删不可见字符：U+200B/C/D 零宽与 U+FEFF BOM（除 BOM 前缀外无语义）；
     * 3) 全角结构字符映射：｛｝［］＂：，（U+FF5B 等）→ ASCII 等价物。
     * 字符串**内部**的一切字符（含全角内容、零宽、注释样式文本）不碰——内容保真优先。
     */
    static String normalizeStructural(String json) {
        StringBuilder out = new StringBuilder(json.length());
        boolean inString = false;
        char openQuote = 0;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (c == '\\' && i + 1 < json.length()) {
                    out.append(c).append(json.charAt(++i)); // 转义对整体保留
                } else if (c == openQuote) {
                    out.append(openQuote == '\uFF02' ? '"' : c); // 全角定界符闭串映射为 ASCII
                    inString = false; // 只有与开串同款定界符才关串（全角＂串内的 ASCII " 是内容，反之亦然）
                    openQuote = 0;
                } else {
                    out.append(c);
                }
                continue;
            }
            // ---- 字符串外部 ----
            if (c == '"') {
                out.append(c);
                inString = true;
                openQuote = '"';
                continue;
            }
            if (c == '\uFF02') { // 全角双引号作定界符
                out.append('"');
                inString = true;
                openQuote = '\uFF02';
                continue;
            }
            if (c == '/' && i + 1 < json.length() && json.charAt(i + 1) == '/') {
                int nl = json.indexOf('\n', i);
                i = (nl < 0) ? json.length() - 1 : nl - 1; // 跳到行尾（保留 \n 由下一轮追加）
                continue;
            }
            if (c == '/' && i + 1 < json.length() && json.charAt(i + 1) == '*') {
                int close = json.indexOf("*/", i + 2);
                i = (close < 0) ? json.length() - 1 : close + 1;
                continue;
            }
            switch (c) {
                case '\u200B', '\u200C', '\u200D', '\uFEFF' -> { /* 删除 */ }
                case '\u00A0' -> out.append(' ');
                case '\uFF5B' -> out.append('{');
                case '\uFF5D' -> out.append('}');
                case '\uFF3B' -> out.append('[');
                case '\uFF3D' -> out.append(']');
                case '\uFF1A' -> out.append(':');
                case '\uFF0C' -> out.append(',');
                default -> out.append(c);
            }
        }
        return out.toString();
    }

}
