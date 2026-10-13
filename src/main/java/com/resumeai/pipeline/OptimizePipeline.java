package com.resumeai.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeai.fidelity.FidelityGate;
import com.resumeai.llm.LlmClient;
import com.resumeai.model.OptimizeResult;
import com.resumeai.model.PipelineOutcome;
import com.resumeai.model.ResumeSnapshot;

import java.util.List;

/**
 * 完整管线：LLM → 解析链 → Schema → Fidelity，结构失败重试恰好 1 次。
 *
 * 重试语义（评审意见 #1，机制参照 instructor 的 validation-retry）：
 * - 只对"结构性失败"（解析失败 / schema 违规 / field 目标无效）重试；
 * - 重试必须携带差异化反馈（首次失败摘要 + 修正指令），禁止原样重发；
 * - 硬上限 1 次重试（2 次 LLM 调用），防与线程池问题耦合的无限重试风暴；
 * - FidelityGate 的捏造违规【不重试】：重试只会让模型换一种编法（参考 docs/design.md）。
 */
public class OptimizePipeline {

    public static final int MAX_LLM_CALLS = 2;
    public static final String PROMPT_VERSION = "prompt-v1.3.0";

    private final LlmClient llm;
    private final ParserChain parser = new ParserChain();
    private final SchemaGate schemaGate = new SchemaGate();
    private final FidelityGate fidelityGate = new FidelityGate();
    private final ObjectMapper treeMapper = new ObjectMapper();

    public OptimizePipeline(LlmClient llm) {
        this.llm = llm;
    }

    public PipelineOutcome run(String scenarioId, ResumeSnapshot snapshot) {
        String requirement = requirementFor(snapshot);
        long start = System.nanoTime();
        List<String> layers = new java.util.ArrayList<>();
        for (int attempt = 0; attempt < MAX_LLM_CALLS; attempt++) {
            String feedback = attempt == 0 ? null : lastFeedback;
            String raw = llm.complete(scenarioId, requirement, attempt, feedback);
            if (attempt > 0) layers.add("RETRIED");

            ParserChain.ParseAttempt parse = parser.parse(raw);
            if (parse.result == null) {
                lastFeedback = "上一次输出不是合法 JSON（截断或语法错误）。请只输出符合 schema 的 JSON，"
                        + "不要任何解释文字、代码块围栏或注释，字符串一律用英文双引号。";
                continue;
            }
            if (parse.repaired) layers.add("LENIENT_REPAIRED");
            else if (!raw.trim().equals(raw)) layers.add("FENCE_STRIPPED");

            // tree 与 result 同源（同一份修复后文本），避免二次 readTree 用未修复文本造成误判
            JsonNode tree = parse.tree;
            String schemaError = schemaGate.validate(tree);
            if (schemaError != null) {
                layers.add("SCHEMA_REJECTED");
                lastFeedback = "上一次输出违反 schema：" + schemaError
                        + "。必须包含 sections（每项含 field/original/rewritten）与 score_before/score_after（0-100 整数）。";
                continue;
            }

            // 结构合法，进入事实层
            OptimizeResult result = parse.result;
            result.setPromptVersion(PROMPT_VERSION);
            List<String> violations = fidelityGate.check(snapshot, result);
            // 结构类违规（field 目标无效）→ 视为可重试的结构失败；
            // 捏造类违规（新增实体）→ 不重试：重试只会让模型换一种编法
            boolean structural = !violations.isEmpty()
                    && violations.stream().allMatch(v -> v.startsWith("FIELD_TARGET_INVALID"));
            if (structural && attempt + 1 < MAX_LLM_CALLS) {
                lastFeedback = "上一次输出的 field 指向不存在的经历段：" + String.join("; ", violations)
                        + "。field 必须指向原简历中真实存在的 work_exps[i]/educations[i] 段。";
                continue;
            }
            if (!violations.isEmpty()) {
                layers.add("FIDELITY_REJECTED");
                long ms = (System.nanoTime() - start) / 1_000_000;
                return new PipelineOutcome(scenarioId, PipelineOutcome.Status.REJECTED_FABRICATION,
                        String.join("; ", violations), result, attempt + 1, ms, layers);
            }

            long ms = (System.nanoTime() - start) / 1_000_000;
            PipelineOutcome outcome = new PipelineOutcome(scenarioId, PipelineOutcome.Status.OK,
                    null, result, attempt + 1, ms, layers);
            maybeFlagDrift(outcome);
            return outcome;
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        return new PipelineOutcome(scenarioId, PipelineOutcome.Status.FAILED,
                "结构解析 " + MAX_LLM_CALLS + " 次均失败（含 1 次带反馈重试）", null, MAX_LLM_CALLS, ms, layers);
    }

    private String lastFeedback;

    /** 组装完整需求文本：简历快照 + 优化指令 + 输出格式要求。 */
    private String requirementFor(ResumeSnapshot snapshot) {
        try {
            String resumeJson = treeMapper.writeValueAsString(snapshot);
            return "请优化以下简历（保持字段结构不变，只允许润色措辞，严禁新增不存在的经历/证书/公司/分数）：\n"
                    + resumeJson;
        } catch (Exception e) {
            throw new IllegalStateException("简历快照序列化失败", e);
        }
    }

    /**
     * L4 语义层：分数漂移监控（不拦截，只打标）。
     * |after-before| > 50 → DRIFT_SUSPECT，供 eval 汇总与人工复核。
     *
     * 阈值依据（研究 Q5，2026-10-14，evidence/research/q5-drift-thresholds.md）：
     * 同一 benign 输入 19 次采样中，旧规则（|Δ|>25 或 after≥90）误报 47%——
     * after≥90 分支几乎全由模型自评噪声触发（模型自评 after 在 {10..100} 无锚波动，
     * temp=0/seed 均不收敛，见 Q4）。跳变幅度才是风险信号：Δ>50 误报降至 11%。
     */
    private void maybeFlagDrift(PipelineOutcome outcome) {
        OptimizeResult r = outcome.getResult();
        if (r.getScoreBefore() != null && r.getScoreAfter() != null) {
            int delta = Math.abs(r.getScoreAfter() - r.getScoreBefore());
            if (delta > 50) {
                outcome.getLayers().add("DRIFT_SUSPECT");
            }
        }
    }
}
