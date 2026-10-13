package com.resumeai.pipeline;

/**
 * L1 词法层防御：剥离 ```json 代码块围栏与前后缀寒暄文字。
 *
 * 先做结构归一化（全角 ｛｝ → {}、剥字符串外注释与零宽字符，见 LenientRepair），
 * 再取首个 '{' 到最后一个 '}' 的跨度——比正则匹配围栏更稳：模型有时输出
 * ```JSON、```json5 甚至漏掉收尾围栏，按大括号跨度截取全部覆盖。
 * 若原文不含 '{' 则原样返回，交给上层报语法失败。
 * （研究 Q2 修复：全角括号输出曾让跨度起点落到内层对象上，截出可解析的垃圾片段——
 * 靠 L3 兜住；归一化前置后 L1 不再截错。）
 */
public final class FenceStripper {

    private FenceStripper() {}

    public static String strip(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        String norm = LenientRepair.normalizeStructural(raw);
        int start = norm.indexOf('{');
        int end = norm.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return norm;
        }
        return norm.substring(start, end + 1);
    }
}
