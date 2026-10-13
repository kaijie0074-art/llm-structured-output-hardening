package com.resumeai.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeai.llm.LlmClient;
import com.resumeai.model.PipelineOutcome;
import com.resumeai.model.ResumeSnapshot;
import com.resumeai.pipeline.OptimizePipeline;
import com.resumeai.store.ResultStore;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * L6 回归层：黄金集评估器。
 *
 * 三类硬指标（每次改 prompt 前后各跑一遍，对比见 docs/regression-records/）：
 * 1. 结构成功率 = (OK + REJECTED_FABRICATION) / 总数 —— FAILED 必须为 0；
 * 2. 锚点拦截率 = 捏造语料被 REJECTED 的比例 —— 必须 100%（合规生死线）；
 * 3. 分数漂移 = clean 语料 score_after 的标准差 —— 监控值，超阈值进入人工复核。
 *
 * 评审意见 #5：报告按黄金集 stratification 分组（本科/大专），防"整体均值掩盖短板"。
 */
public class EvalRunner {

    private final ObjectMapper mapper = new ObjectMapper();

    public record EvalReport(int total, int ok, int rejected, int failed,
                             int fabricationCaught, int fabricationTotal,
                             double cleanScoreStd, Map<String, List<String>> byStratum,
                             List<String> summaries) {}

    public EvalReport run(Path goldenDir) throws Exception {
        return run(goldenDir, new com.resumeai.llm.HttpLlmClient());
    }

    /** llm 由调用方注入：生产走 HttpLlmClient（真实 API），回归测试注入历史故障样本。 */
    public EvalReport run(Path goldenDir, LlmClient llm) throws Exception {
        List<ResumeSnapshot> resumes = new ArrayList<>();
        List<String> scenarios = new ArrayList<>();
        List<String> stratums = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(goldenDir, "*.json")) {
            for (Path p : ds) {
                var root = mapper.readTree(p.toFile());
                ResumeSnapshot snap = mapper.treeToValue(root, ResumeSnapshot.class);
                resumes.add(snap);
                scenarios.add(root.path("scenario").asText("12-clean"));
                stratums.add(root.path("stratum").asText("default"));
            }
        }
        resumes.sort(java.util.Comparator.comparing(r -> scenarios.get(resumes.indexOf(r))));
        // 上面 indexOf 在重复元素下不稳，改用索引排序加载
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < resumes.size(); i++) order.add(i);
        order.sort(java.util.Comparator.comparing(scenarios::get));

        OptimizePipeline pipeline = new OptimizePipeline(llm);
        ResultStore store = new ResultStore();

        List<Integer> cleanScores = new ArrayList<>();
        Map<String, List<String>> byStratum = new TreeMap<>();
        List<String> summaries = new ArrayList<>();
        int fabricationTotal = 0;
        int fabricationCaught = 0;

        for (int idx : order) {
            String scenario = scenarios.get(idx);
            PipelineOutcome outcome = pipeline.run(scenario, resumes.get(idx));
            store.save(outcome);
            summaries.add(outcome.summarize());
            byStratum.computeIfAbsent(stratums.get(idx), k -> new ArrayList<>()).add(outcome.getStatus().toString());

            if (scenario.startsWith("09-") || scenario.startsWith("10-") || scenario.startsWith("11-")) {
                fabricationTotal++;
                if (outcome.getStatus() == PipelineOutcome.Status.REJECTED_FABRICATION) fabricationCaught++;
            }
            if ((scenario.startsWith("12-") || scenario.startsWith("13-"))
                    && outcome.getResult() != null && outcome.getResult().getScoreAfter() != null) {
                cleanScores.add(outcome.getResult().getScoreAfter());
            }
        }

        return new EvalReport(
                store.size(),
                store.countByStatus(PipelineOutcome.Status.OK),
                store.countByStatus(PipelineOutcome.Status.REJECTED_FABRICATION),
                store.countByStatus(PipelineOutcome.Status.FAILED),
                fabricationCaught, fabricationTotal,
                std(cleanScores),
                byStratum,
                summaries);
    }

    static double std(List<Integer> xs) {
        if (xs.size() < 2) return 0;
        double mean = xs.stream().mapToInt(Integer::intValue).average().orElse(0);
        double var = xs.stream().mapToDouble(x -> (x - mean) * (x - mean)).sum() / (xs.size() - 1);
        return Math.sqrt(var);
    }
}
