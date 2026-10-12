package com.resumeai.pipeline;

/**
 * L1 词法层防御：剥离 ```json 代码块围栏与前后缀寒暄文字。
 *
 * 只认首个 '{' 到最后一个 '}' 的跨度——比正则匹配围栏更稳：
 * 模型有时输出 ```JSON、```json5 甚至漏掉收尾围栏，按大括号跨度截取全部覆盖。
 * 若原文不含 '{' 则原样返回，交给上层报语法失败。
 */
public final class FenceStripper {

    private FenceStripper() {}

    public static String strip(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return raw;
        }
        return raw.substring(start, end + 1);
    }
}
