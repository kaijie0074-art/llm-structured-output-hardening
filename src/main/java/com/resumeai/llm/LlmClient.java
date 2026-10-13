package com.resumeai.llm;

/**
 * LLM 客户端抽象。生产环境使用 HttpLlmClient（OpenAI 兼容协议，环境变量凭证）；
 * 历史故障样本注入见测试源集 RecordedLlmClient（fixtures/llm-corpus）。
 */
public interface LlmClient {

    /**
     * @param scenarioId 场景标签（审计/样本定位用；真实客户端仅记录日志）
     * @param requirement 完整需求文本（简历快照 + 优化指令 + 输出格式要求）
     * @param attempt    第几次调用，从 0 开始。管线最多调用 2 次（初始+重试 1 次）
     * @param feedback   重试时携带的差异化反馈（评审意见 #1：禁止原样重发），
     *                   首次调用传 null
     * @return 模型原始输出文本
     */
    String complete(String scenarioId, String requirement, int attempt, String feedback);
}
