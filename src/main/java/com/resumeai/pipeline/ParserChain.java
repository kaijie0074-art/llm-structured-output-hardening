package com.resumeai.pipeline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeai.model.OptimizeResult;

/**
 * 解析链：strict Jackson → (失败) → FenceStripper + LenientRepair 再试。
 *
 * ObjectMapper 关键配置（L3 层吸收）：
 * - FAIL_ON_UNKNOWN_PROPERTIES=false：模型新增/改名字段不致崩；
 *   "必需字段缺失"的兜底职责交给 SchemaGate，解析层只管"能读多少读多少"。
 *
 * 返回 null 表示两层都解析失败（调用方决定是否重试 LLM，上限 1 次）。
 */
public final class ParserChain {

    private final ObjectMapper strictMapper;
    private final ObjectMapper lenientMapper;

    public ParserChain() {
        this.strictMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.lenientMapper = strictMapper.copy();
    }

    /**
     * @return 解析结果；result=null 表示两层都失败（截断/不可修复语法）。
     *         tree 与 result 同源（同一份修复后文本），供 SchemaGate 复用，
         *         避免调用方用未修复文本二次 readTree 造成误判（评审意见前的实测 bug，见回归记录）。
     */
    public ParseAttempt parse(String raw) {
        // 1) strict：原样直接解析
        try {
            OptimizeResult r = strictMapper.readValue(raw, OptimizeResult.class);
            com.fasterxml.jackson.databind.JsonNode tree = strictMapper.readTree(raw);
            return new ParseAttempt(r, tree, false);
        } catch (JsonProcessingException ignored) {
            // fall through
        }
        // 2) 词法+语法修复后再解析
        String stripped = FenceStripper.strip(raw);
        String repaired = LenientRepair.repair(stripped);
        if (LenientRepair.looksTruncated(repaired)) {
            return new ParseAttempt(null, null, false); // 截断类不可修复，判失败走重试
        }
        try {
            OptimizeResult r = lenientMapper.readValue(repaired, OptimizeResult.class);
            com.fasterxml.jackson.databind.JsonNode tree = lenientMapper.readTree(repaired);
            return new ParseAttempt(r, tree, true);
        } catch (JsonProcessingException e) {
            return new ParseAttempt(null, null, false);
        }
    }

    /** 基线模式：无任何防御的直接解析（用于复现崩溃统计）。 */
    public OptimizeResult parseBaseline(String raw) throws JsonProcessingException {
        return strictMapper.readValue(raw, OptimizeResult.class);
    }

    public static final class ParseAttempt {
        public final OptimizeResult result;
        public final com.fasterxml.jackson.databind.JsonNode tree;
        public final boolean repaired;

        ParseAttempt(OptimizeResult result, com.fasterxml.jackson.databind.JsonNode tree, boolean repaired) {
            this.result = result;
            this.tree = tree;
            this.repaired = repaired;
        }
    }
}
