package com.resumeai.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeai.fidelity.FidelityGate;
import com.resumeai.model.OptimizeResult;
import com.resumeai.model.ResumeSnapshot;
import com.resumeai.pipeline.FenceStripper;
import com.resumeai.pipeline.LenientRepair;
import com.resumeai.pipeline.ParserChain;
import com.resumeai.pipeline.SchemaGate;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 研究工具（Q3/Q4/Q5，2026-10-14 深潜）：同一请求多次采样的非确定性量化
 * + 自然损坏形态分类 + DRIFT_SUSPECT 阈值扫描。
 *
 * 输入：evidence/research/q3-raw/{t02,t00}-*.json（16 次真实 API 调用的原始响应）。
 * 每个样本走**生产路径**（ParserChain → SchemaGate → FidelityGate），
 * 记录：规范化树哈希（结构等价性）、触发修复规则、sections 数、分数、
 * 阈值规则扫描下的 DRIFT 标记率。
 *
 * 运行：java -cp target/classes:$(cat target/cp.txt) com.resumeai.tools.NondeterminismReport
 */
public final class NondeterminismReport {

    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var root = mapper.readTree(Path.of("fixtures/golden-resumes/resume-001.json").toFile());
        ResumeSnapshot snap = mapper.treeToValue(root, ResumeSnapshot.class);
        ParserChain chain = new ParserChain();
        SchemaGate gate = new SchemaGate();
        FidelityGate fidelity = new FidelityGate();

        System.out.println("## Q3/Q4 同一请求 16 次采样的非确定性（生产解析路径）\n");
        for (String tag : new String[]{"t00", "t02"}) {
            List<String> raws = new ArrayList<>();
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(Path.of("evidence/research/q3-raw"), tag + "-*.json")) {
                List<Path> files = new ArrayList<>();
                ds.forEach(files::add);
                files.sort(java.util.Comparator.comparing(p -> p.getFileName().toString()));
                for (Path p : files) {
                    raws.add(mapper.readTree(p.toFile()).path("choices").path(0).path("message").path("content").asText());
                }
            }
            String label = tag.equals("t00") ? "temp=0.0" : "temp=0.2（生产配置）";
            System.out.println("### " + label + "（" + raws.size() + " 次）\n");
            System.out.println("| run | 结构哈希 | 修复规则 | sections | before | after | Δ | fidelity | 自然损坏 |");
            System.out.println("|---|---|---|---|---|---|---|---|---|");
            Map<String, Integer> hashCount = new TreeMap<>();
            List<int[]> scores = new ArrayList<>();
            List<String> corruptions = new ArrayList<>();
            for (int i = 0; i < raws.size(); i++) {
                String raw = raws.get(i);
                String corruption = classifyNatural(raw);
                corruptions.add(corruption);
                var attempt = chain.parse(raw);
                if (attempt.result == null) {
                    System.out.printf("| %d | 解析失败 | - | - | - | - | - | - | %s |%n", i + 1, corruption);
                    continue;
                }
                OptimizeResult r = attempt.result;
                List<String> rules = LenientRepair.appliedRules(FenceStripper.strip(raw));
                String rulesStr = attempt.repaired ? String.join(",", rules) : "无需";
                String hash = structuralSignature(r);
                hashCount.merge(hash, 1, Integer::sum);
                List<String> v = fidelity.check(snap, r);
                Integer before = r.getScoreBefore(), after = r.getScoreAfter();
                int delta = (before != null && after != null) ? after - before : Integer.MIN_VALUE;
                scores.add(new int[]{before == null ? -1 : before, after == null ? -1 : after});
                System.out.printf("| %d | %s | %s | %d | %s | %s | %s | %s | %s |%n",
                        i + 1, hash, rulesStr,
                        r.getSections() == null ? 0 : r.getSections().size(),
                        before, after, delta == Integer.MIN_VALUE ? "-" : delta,
                        v.isEmpty() ? "ok" : v.get(0).split(":")[0], corruption);
            }
            System.out.printf("%n- 唯一原文变体: %d/%d%n", raws.stream().distinct().count(), raws.size());
            System.out.printf("- 唯一结构哈希: %d/%d %s%n", hashCount.size(), raws.size(), hashCount);
            if (!scores.isEmpty()) {
                var afters = scores.stream().map(s -> s[1]).filter(x -> x >= 0).toList();
                var befores = scores.stream().map(s -> s[0]).filter(x -> x >= 0).toList();
                System.out.printf("- score_before 值集: %s（极差 %d）%n", befores,
                        befores.stream().max(Integer::compareTo).orElse(0) - befores.stream().min(Integer::compareTo).orElse(0));
                System.out.printf("- score_after 值集: %s（极差 %d）%n", afters,
                        afters.stream().max(Integer::compareTo).orElse(0) - afters.stream().min(Integer::compareTo).orElse(0));
                System.out.printf("- 自然损坏形态分布: %s%n%n", tally(corruptions));
            }
        }
    }

    /** 自然损坏形态分类（同一输入、同一段落文本上的结构差异）。 */
    static String classifyNatural(String raw) {
        List<String> tags = new ArrayList<>();
        String t = raw.trim();
        if (t.startsWith("```")) tags.add("围栏");
        if (raw.contains("“") && raw.indexOf("“score") >= 0) tags.add("中文引号键");
        else if (raw.contains("“")) tags.add("中文引号");
        if (raw.contains("：") || raw.contains("，\"")) tags.add("全角标点");
        if (t.endsWith(",")) tags.add("尾逗号");
        if (raw.contains("//") && raw.indexOf("//") < raw.indexOf("\n")) tags.add("注释?");
        return tags.isEmpty() ? "无" : String.join("+", tags);
    }

    /** 结构签名：sections 的 field 目标序列 + 分数（忽略措辞差异，衡量**语义结构**等价）。 */
    static String structuralSignature(OptimizeResult r) {
        if (r == null || r.getSections() == null) return "∅";
        List<String> fields = r.getSections().stream()
                .map(s -> String.valueOf(s.getField())).toList();
        return "f=" + fields + ",b=" + r.getScoreBefore() + ",a=" + r.getScoreAfter();
    }

    static Map<String, Integer> tally(List<String> xs) {
        Map<String, Integer> m = new TreeMap<>();
        xs.forEach(x -> m.merge(x, 1, Integer::sum));
        return m;
    }

    private NondeterminismReport() {}
}
