package com.resumeai.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeai.fidelity.FidelityGate;
import com.resumeai.llm.HttpLlmClient;
import com.resumeai.model.ResumeSnapshot;
import com.resumeai.pipeline.ParserChain;
import com.resumeai.pipeline.SchemaGate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 研究工具（Q6，2026-10-14 深潜）：重试反馈模板 A/B——强制故障协议。
 *
 * 背景：自然失败率 ~3%，A/B 双比例检验（80% 功效）需每臂 769+ 样本（见
 * evidence/research/q6-power-analysis），配额不可行。因此采用**强制故障协议**：
 * attempt-0 不调用 API，本地确定性注入历史故障形态（围栏+中文引号+尾逗号），
 * 保证首次失败；attempt-1 走真实 LLM（生产 HttpLlmClient 同一路径），
 * 唯一变量是反馈模板：
 *   A = 生产现行模板（泛化修正指令）
 *   B = 结构化模板（逐条列出故障 + 定界符规则 + 最小正确示例）
 * 每臂 N=8，指标：attempt-1 输出 parse+schema+fidelity 全通过率。
 *
 * 运行（需 LSOH_API_KEY/LSOH_BASE_URL）：
 *   java -cp target/classes:$(cat target/cp.txt) com.resumeai.tools.FeedbackABProbe
 */
public final class FeedbackABProbe {

    static final String FEEDBACK_A =
            "上一次输出不是合法 JSON（截断或语法错误）。请只输出符合 schema 的 JSON，"
            + "不要任何解释文字、代码块围栏或注释，字符串一律用英文双引号。";

    static final String FEEDBACK_B =
            "上一次输出有 3 类语法错误，逐条修正后重新输出完整 JSON：\n"
            + "1) 外层被 ```json 代码块围栏包裹——去掉围栏，首字符必须是 {；\n"
            + "2) 键与字符串值用了中文引号 “ ”——一律改为英文双引号 \"；\n"
            + "3) 最后一个成员后有多余逗号——删除。\n"
            + "正确形态示例（仅示意结构，内容按原任务）：\n"
            + "{\"sections\":[{\"field\":\"work_exps[0].description\",\"original\":\"…\","
            + "\"rewritten\":\"…\",\"reason\":\"…\"}],\"score_before\":62,\"score_after\":78}";

    /** attempt-0 的确定性故障输出：围栏 + 中文引号 + 尾逗号（历史高频形态）。 */
    static String corruptedAttempt0() {
        return "```json\n"
                + "“sections”: [\n"
                + "  {“field”: “work_exps[0].description”, “original”: “参与订单系统开发，服务 200 家商户，日均处理 3000 单”, “rewritten”: “主导订单模块核心链路开发与迭代，稳定服务 200 家商户，日均处理 3000 单”, “reason”: “动词强化，量化事实保持不变”}\n"
                + "],\n"
                + "“score_before”: 62,\n"
                + "“score_after”: 78,\n"
                + "```";
    }

    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var root = mapper.readTree(Path.of("fixtures/golden-resumes/resume-001.json").toFile());
        ResumeSnapshot snap = mapper.treeToValue(root, ResumeSnapshot.class);
        String requirement = "请优化以下简历（保持字段结构不变，只允许润色措辞，严禁新增不存在的经历/证书/公司/分数）：\n"
                + mapper.writeValueAsString(snap);

        // 前置校验：注入的 attempt-0 必须确实被当前防御判为失败（否则协议无效）
        var pre = new ParserChain().parse(corruptedAttempt0());
        System.out.println("## Q6 强制故障协议：attempt-0 本地注入 → attempt-1 真实 LLM 反馈 A/B\n");
        System.out.printf("- 协议前置：attempt-0 修复解析 %s（预期 fail→重试）%n%n",
                pre.result != null ? "成功(!)" : "失败");

        HttpLlmClient llm = new HttpLlmClient();
        ParserChain chain = new ParserChain();
        SchemaGate gate = new SchemaGate();
        FidelityGate fidelity = new FidelityGate();

        System.out.println("| 臂 | run | parse | schema | fidelity | 终态 | ms |");
        System.out.println("|---|---|---|---|---|---|---|");
        for (String arm : new String[]{"A", "B"}) {
            String feedback = arm.equals("A") ? FEEDBACK_A : FEEDBACK_B;
            for (int run = 1; run <= 8; run++) {
                long t0 = System.nanoTime();
                String raw = callWithRetry(llm, requirement, arm, run, feedback);
                long ms = (System.nanoTime() - t0) / 1_000_000;
                String parse, schema, fid, verdict;
                var attempt = chain.parse(raw);
                if (attempt.result == null) {
                    parse = "fail"; schema = "-"; fid = "-"; verdict = "FAILED";
                } else if (gate.validate(attempt.tree) != null) {
                    parse = "ok"; schema = "fail"; fid = "-"; verdict = "FAILED";
                } else {
                    List<String> v = fidelity.check(snap, attempt.result);
                    parse = "ok"; schema = "ok";
                    fid = v.isEmpty() ? "ok" : "viol:" + v.get(0).split(":")[0];
                    verdict = v.isEmpty() ? "OK" : "REJECTED";
                }
                System.out.printf("| %s | %d | %s | %s | %s | %s | %d |%n", arm, run, parse, schema, fid, verdict, ms);
                Thread.sleep(2000); // 串行节流，共享订阅配额
            }
        }
    }

    /** 429/瞬断退避重试（最多 3 次，sleep 60s）。 */
    static String callWithRetry(HttpLlmClient llm, String requirement, String arm, int run, String feedback)
            throws InterruptedException {
        for (int i = 0; ; i++) {
            try {
                return llm.complete("research-q6-" + arm + run, requirement, 1, feedback);
            } catch (IllegalStateException e) {
                if (i >= 2 || !e.getMessage().contains("429")) throw e;
                System.err.printf("(%s run%d 429，sleep 60)%n", arm, run);
                Thread.sleep(60_000);
            }
        }
    }

    private FeedbackABProbe() {}
}
