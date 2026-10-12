package com.resumeai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 单条字段级改写：field 为 JSON path 形式（如 work_exp[0].description）。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Section {

    private String field;
    private String original;
    private String rewritten;
    /** 缺失时默认空串：reason 是软字段，不因缺失拒绝整次结果。 */
    private String reason = "";

    public String getField() { return field; }
    public void setField(String field) { this.field = field; }

    public String getOriginal() { return original; }
    public void setOriginal(String original) { this.original = original; }

    public String getRewritten() { return rewritten; }
    public void setRewritten(String rewritten) { this.rewritten = rewritten; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason == null ? "" : reason; }
}
