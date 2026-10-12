package com.resumeai.llm;

/**
 * LLM 客户端抽象。演练环境使用 RecordedLlmClient 回放语料；
 * 生产环境替换为 DeepSeek/GLM HTTP 客户端（此时必须启用 response_format=json_object，
 * 见 docs/open-source-references.md：API 能力是地板不是解药）。
 */
public interface LlmClient {

    /**
     * @param scenarioId 语料场景名（回放定位用；生产实现可忽略）
     * @param attempt    第几次调用，从 0 开始。管线最多调用 2 次（初始+重试 1 次）
     * @param feedback   重试时携带的差异化反馈（评审意见 #1：禁止原样重发），
     *                   首次调用传 null
     * @return 模型原始输出文本
     */
    String complete(String scenarioId, int attempt, String feedback);
}
