package com.resumeai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeai.eval.EvalRunner;
import com.resumeai.llm.HttpLlmClient;
import com.resumeai.model.OptimizeResult;
import com.resumeai.model.PipelineOutcome;
import com.resumeai.model.ResumeSnapshot;
import com.resumeai.pipeline.OptimizePipeline;
import com.resumeai.pipeline.ParserChain;
import com.resumeai.store.ResultStore;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CLI 入口：
 *
 *   baseline <corpusDir>              基线模式：直接 Jackson readValue 复现历史故障样本的解析崩溃与静默脏数据
 *   hardened <goldenDir>              加固模式：真实 LLM 全管线，各防御层拦截计数
 *   eval <goldenDir>                  黄金集评估（真实 LLM）：三指标 + 分层报告，写 evidence/
 *
 * hardened/eval 走真实 LLM（OpenAI 兼容协议）：LSOH_API_KEY（或 OPENAI_API_KEY），
 * 可选 LSOH_BASE_URL / LSOH_MODEL。
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("用法: baseline <corpusDir> [goldenDir] | hardened <goldenDir> | eval <goldenDir>");
            System.err.println("hardened/eval 需要真实 LLM 凭证：LSOH_API_KEY（或 OPENAI_API_KEY），可选 LSOH_BASE_URL/LSOH_MODEL");
            System.exit(2);
        }
        try {
            switch (args[0]) {
                case "baseline" -> baseline(Path.of(args[1]), args.length > 2 ? Path.of(args[2]) : null);
                case "hardened" -> hardened(Path.of(args[1]));
                case "eval" -> eval(Path.of(args[1]));
                default -> System.exit(2);
            }
        } catch (IllegalStateException e) {
            System.err.println("错误：" + e.getMessage());
            System.exit(2);
        }
    }

    /** 基线：第一版实现（docs/issue-reports/RSO-001）的解析缺陷复现。 */
    static void baseline(Path corpusDir, Path goldenDir) throws Exception {
        ParserChain chain = new ParserChain();
        ObjectMapper mapper = new ObjectMapper();
        int crashed = 0, parsed = 0;
        List<String> crashList = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(corpusDir, "*.txt")) {
            for (Path p : ds) {
                String raw = Files.readString(p).split("===ATTEMPT")[0].trim();
                try {
                    OptimizeResult r = chain.parseBaseline(raw);
                    parsed++;
                } catch (Exception e) {
                    crashed++;
                    crashList.add(p.getFileName() + " -> " + e.getClass().getSimpleName());
                }
            }
        }
        System.out.println("== baseline（直接 Jackson readValue，无任何防御）==");
        System.out.println("语料崩溃（用户看到\"优化失败请重试\"）: " + crashed);
        crashList.forEach(c -> System.out.println("  " + c));
        System.out.println("解析\"成功\": " + parsed);
        System.out.println("注意：解析成功 ≠ 数据正确 —— 其中的 path 越界/字段漂移/捏造内容全部静默入库（见 eval 报告的事后审计列）。");

        if (goldenDir != null) {
            // 事后审计：对 parse"成功"的历史故障样本做 schema/锚点/漂移静态检查，量化"静默脏数据"规模
            int dirty = 0;
            var schemaGate = new com.resumeai.pipeline.SchemaGate();
            var fidelityGate = new com.resumeai.fidelity.FidelityGate();
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(goldenDir, "*.json")) {
                for (Path p : ds) {
                    var root = mapper.readTree(p.toFile());
                    String scenario = root.path("scenario").asText("12-clean");
                    Path corpusFile = corpusDir.resolve(scenario + ".txt");
                    if (!Files.exists(corpusFile)) continue;
                    String raw = Files.readString(corpusFile).split("===ATTEMPT")[0].trim();
                    var parse = chain.parse(raw);
                    if (parse.result == null) continue; // 崩的不算静默
                    String schemaError = schemaGate.validate(parse.tree);
                    if (schemaError != null) { dirty++; continue; }
                    ResumeSnapshot snap = mapper.treeToValue(root, ResumeSnapshot.class);
                    if (!fidelityGate.check(snap, parse.result).isEmpty()) dirty++;
                }
            }
            System.out.println("事后审计：解析\"成功\"的输出中，schema/锚点不合格（静默脏数据）: " + dirty);
        }
    }

    /** 加固模式：真实 LLM 全管线 + 分层拦截计数。 */
    static void hardened(Path goldenDir) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        OptimizePipeline pipeline = new OptimizePipeline(new com.resumeai.llm.HttpLlmClient());
        ResultStore store = new ResultStore();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(goldenDir, "*.json")) {
            List<Path> files = new ArrayList<>();
            for (Path p : ds) files.add(p);
            for (Path p : files) {
                var root = mapper.readTree(p.toFile());
                String scenario = root.path("scenario").asText("12-clean");
                ResumeSnapshot snap = mapper.treeToValue(root, ResumeSnapshot.class);
                store.save(pipeline.run(scenario, snap));
            }
        }
        System.out.println("== hardened（分层防御全开，真实 LLM）==");
        System.out.println("总数: " + store.size()
                + " | OK: " + store.countByStatus(PipelineOutcome.Status.OK)
                + " | REJECTED_FABRICATION: " + store.countByStatus(PipelineOutcome.Status.REJECTED_FABRICATION)
                + " | FAILED: " + store.countByStatus(PipelineOutcome.Status.FAILED));
        System.out.println("needsReview（人工复核队列）: " + store.countNeedsReview());
        System.out.println("分层触发计数: " + store.layerCounts());
        System.out.println("按 promptVersion: " + store.countsByPromptVersion());
    }

    /** 黄金集评估：写 evidence/eval-report.md 与 latest-run.json。 */
    static void eval(Path goldenDir) throws Exception {
        EvalRunner runner = new EvalRunner();
        EvalRunner.EvalReport report = runner.run(goldenDir);
        ObjectMapper mapper = new ObjectMapper();

        StringBuilder md = new StringBuilder();
        md.append("# 黄金集评估报告（EvalRunner 自动生成）\n\n");
        md.append(String.format("| 指标 | 值 |%n|---|---|%n"));
        md.append(String.format("| 总样本 | %d |%n", report.total()));
        md.append(String.format("| 结构成功率 (OK+REJECTED)/total | %d/%d = %.1f%% |%n",
                report.ok() + report.rejected(), report.total(),
                100.0 * (report.ok() + report.rejected()) / Math.max(1, report.total())));
        md.append(String.format("| FAILED（必须=0） | %d |%n", report.failed()));
        md.append(String.format("| 锚点拦截率（捏造语料被拒） | %d/%d |%n", report.fabricationCaught(), report.fabricationTotal()));
        md.append(String.format("| clean 语料 score_after 标准差 | %.2f |%n", report.cleanScoreStd()));
        md.append("\n## 分层（stratum）× 结果分布（评审意见 #5）\n\n| stratum | 结果 |\n|---|---|\n");
        report.byStratum().forEach((k, v) -> md.append(String.format("| %s | %s |%n", k, v.toString())));
        md.append("\n## 明细\n\n```\n");
        report.summaries().forEach(s -> md.append(s).append('\n'));
        md.append("```\n");

        Path evidenceDir = goldenDir.resolve("../../evidence").normalize();
        Files.createDirectories(evidenceDir);
        Files.writeString(evidenceDir.resolve("eval-report.md"), md.toString());

        var json = Map.of(
                "total", report.total(),
                "ok", report.ok(),
                "rejected", report.rejected(),
                "failed", report.failed(),
                "fabricationCaught", report.fabricationCaught(),
                "fabricationTotal", report.fabricationTotal(),
                "cleanScoreStd", report.cleanScoreStd(),
                "byStratum", report.byStratum());
        mapper.writerWithDefaultPrettyPrinter().writeValue(evidenceDir.resolve("latest-run.json").toFile(), json);

        System.out.println(md);
        System.out.println("已写入: " + evidenceDir.resolve("eval-report.md"));
        System.out.println("已写入: " + evidenceDir.resolve("latest-run.json"));
    }
}
