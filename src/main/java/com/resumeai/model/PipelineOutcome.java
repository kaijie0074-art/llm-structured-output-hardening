package com.resumeai.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 管线终态。三种 status 语义严格区分（docs/design.md）：
 * - OK：结构合法且锚点保真，可落库；
 * - REJECTED_FABRICATION：结构合法但检出捏造实体，拒绝落库并打 needsReview（合规生死线）；
 * - FAILED：结构层面两次尝试（初始+重试 1 次）后仍不可解析，绝不无限重试（与线程池雪崩问题解耦）。
 */
public class PipelineOutcome {

    public enum Status { OK, REJECTED_FABRICATION, FAILED }

    private final String scenarioId;
    private final Status status;
    private final String rejectReason;
    private final OptimizeResult result;
    /** 各防御层的拦截/处理标记：FENCE_STRIPPED / LENIENT_REPAIRED / SCHEMA_REJECTED / FIELD_TARGET_INVALID / RETRIED / FIDELITY_REJECTED / DRIFT_SUSPECT */
    private final List<String> layers = new ArrayList<>();
    private final int llmAttempts;
    private final long elapsedMs;

    public PipelineOutcome(String scenarioId, Status status, String rejectReason,
                           OptimizeResult result, int llmAttempts, long elapsedMs) {
        this(scenarioId, status, rejectReason, result, llmAttempts, elapsedMs, java.util.List.of());
    }

    public PipelineOutcome(String scenarioId, Status status, String rejectReason,
                           OptimizeResult result, int llmAttempts, long elapsedMs, List<String> layers) {
        this.scenarioId = scenarioId;
        this.status = status;
        this.rejectReason = rejectReason;
        this.result = result;
        this.llmAttempts = llmAttempts;
        this.elapsedMs = elapsedMs;
        this.layers.addAll(layers);
    }

    public String getScenarioId() { return scenarioId; }
    public Status getStatus() { return status; }
    public String getRejectReason() { return rejectReason; }
    public OptimizeResult getResult() { return result; }
    public List<String> getLayers() { return layers; }
    public int getLlmAttempts() { return llmAttempts; }
    public long getElapsedMs() { return elapsedMs; }

    public boolean isNeedsReview() { return status == Status.REJECTED_FABRICATION; }

    /** 落库/出报表用的单行摘要（含 promptVersion，评审意见 #6：日志必须可追溯版本）。 */
    public String summarize() {
        String pv = result != null && result.getPromptVersion() != null ? result.getPromptVersion() : "-";
        return String.format("scenario=%s status=%s attempts=%d layers=%s promptVersion=%s reason=%s",
                scenarioId, status, llmAttempts, layers, pv, rejectReason == null ? "-" : rejectReason);
    }
}
