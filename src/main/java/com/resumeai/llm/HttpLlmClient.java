package com.resumeai.llm;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 真实 LLM 客户端（OpenAI 兼容协议 /chat/completions）。
 *
 * 环境变量：
 *   LSOH_API_KEY    必填（或 OPENAI_API_KEY 兜底）
 *   LSOH_BASE_URL   可选（或 OPENAI_BASE_URL 兜底；默认智谱开放平台）
 *   LSOH_MODEL      可选（或 OPENAI_MODEL 兜底；默认 glm-4-flash）
 *
 * 生产接入建议同时开启供应商侧 response_format=json_object（见
 * docs/open-source-references.md：API 能力是地板不是解药，L3-L5 仍需应用层防御）。
 */
public class HttpLlmClient implements LlmClient {

    public static final String SYSTEM_PROMPT =
            "你是简历优化器。只输出一个 JSON 对象，格式严格为："
            + "{\"sections\":[{\"field\":\"work_exps[0].description\",\"original\":\"原文字段原文\","
            + "\"rewritten\":\"润色后的文字\",\"reason\":\"修改理由\"}],"
            + "\"score_before\":整数,\"score_after\":整数}。"
            + "sections 数组覆盖所有需要润色的字段，field 必须指向原简历中真实存在的字段"
            + "（work_exps[i].description、educations[i].school 等），original 必须是该字段原文。"
            + "score_before/score_after 为 0-100 整数。不要任何解释文字、代码块围栏或注释。"
            + "只允许基于输入简历中真实存在的内容润色措辞，"
            + "严禁新增输入中不存在的经历、证书、公司或分数。";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String baseUrl;
    private final String apiKey;
    private final String model;

    public HttpLlmClient() {
        this.apiKey = env("LSOH_API_KEY", "OPENAI_API_KEY");
        this.baseUrl = env("LSOH_BASE_URL", "OPENAI_BASE_URL", "https://open.bigmodel.cn/api/paas/v4/");
        this.model = env("LSOH_MODEL", "OPENAI_MODEL", "glm-4-flash");
        if (this.apiKey == null || this.apiKey.isBlank()) {
            throw new IllegalStateException(
                    "需要真实 LLM API 凭证：请设置 LSOH_API_KEY（或 OPENAI_API_KEY），"
                    + "可选 LSOH_BASE_URL / LSOH_MODEL 指向任意 OpenAI 兼容服务。");
        }
    }

    private static String env(String primary, String fallback) {
        String v = System.getenv(primary);
        if (v != null && !v.isBlank()) return v;
        return System.getenv(fallback);
    }

    private static String env(String primary, String fallback, String def) {
        String v = System.getenv(primary);
        if (v != null && !v.isBlank()) return v;
        v = System.getenv(fallback);
        return (v != null && !v.isBlank()) ? v : def;
    }

    @Override
    public String complete(String scenarioId, String requirement, int attempt, String feedback) {
        String user = requirement
                + (feedback == null ? "" : "\n\n上一次输出存在以下问题，必须修正后重新输出完整 JSON：\n" + feedback);
        try {
            throttleIfConfigured();
            String body = mapper.writeValueAsString(Map.of(
                    "model", model,
                    "messages", java.util.List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", user)),
                    "temperature", 0.2));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl.replaceAll("/+$", "") + "/chat/completions"))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 429) {
                // 共享订阅配额：429 退避 60s 重试（最多 3 次），避免评估整轮作废
                for (int i = 0; i < 3 && resp.statusCode() == 429; i++) {
                    Thread.sleep(60_000);
                    resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                }
            }
            if (resp.statusCode() / 100 != 2) {
                throw new IllegalStateException("LLM API HTTP " + resp.statusCode() + ": " + resp.body());
            }
            JsonNode payload = mapper.readTree(resp.body());
            return payload.path("choices").path(0).path("message").path("content").asText().trim();
        } catch (IOException e) {
            throw new IllegalStateException("LLM API 调用失败（scenario=" + scenarioId + "）", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LLM API 调用被中断", e);
        }
    }

    /**
     * LSOH_THROTTLE_MS：调用间最小间隔（默认 0 不节流）。
     * 共享订阅配额场景（多代理/批评估）设 1500-2000 可显著降低 429。
     */
    private static void throttleIfConfigured() throws InterruptedException {
        String v = System.getenv("LSOH_THROTTLE_MS");
        if (v != null && !v.isBlank()) {
            long ms = Long.parseLong(v.trim());
            long since = System.nanoTime() - lastCallNanos;
            long remain = ms * 1_000_000 - since;
            if (remain > 0) Thread.sleep(remain / 1_000_000);
        }
        lastCallNanos = System.nanoTime();
    }

    private static volatile long lastCallNanos;
}
