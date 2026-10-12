package com.resumeai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * LLM 改写结果的反序列化目标。
 *
 * 设计取舍（对应 docs/design.md 第 2 层）：
 * - FAIL_ON_UNKNOWN_PROPERTIES=false 由 ObjectMapper 统一配置，此处 @JsonIgnoreProperties 双保险，
 *   吸收 L3 层"字段改名/新增字段"的解析侧冲击（改名导致的缺字段交给 SchemaGate 兜底）。
 * - 数值字段用包装类型，解析缺失时为 null 而不是 0，避免"0 分静默入库"这类脏数据。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OptimizeResult {

    private List<Section> sections = new ArrayList<>();

    @JsonProperty("score_before")
    private Integer scoreBefore;

    @JsonProperty("score_after")
    private Integer scoreAfter;

    /** 由管线写入，不信任模型回传；落库用于问题追溯（对应 L6 回归层）。 */
    private String promptVersion;

    public List<Section> getSections() { return sections; }
    public void setSections(List<Section> sections) { this.sections = sections; }

    public Integer getScoreBefore() { return scoreBefore; }
    public void setScoreBefore(Integer scoreBefore) { this.scoreBefore = scoreBefore; }

    public Integer getScoreAfter() { return scoreAfter; }
    public void setScoreAfter(Integer scoreAfter) { this.scoreAfter = scoreAfter; }

    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
}
