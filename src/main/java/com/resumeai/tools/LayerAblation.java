package com.resumeai.tools;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeai.fidelity.FidelityGate;
import com.resumeai.model.OptimizeResult;
import com.resumeai.model.ResumeSnapshot;
import com.resumeai.pipeline.FenceStripper;
import com.resumeai.pipeline.LenientRepair;
import com.resumeai.pipeline.SchemaGate;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 研究工具（Q1，2026-10-14 深潜）：六层防御边际贡献分解。
 *
 * 对 15 份历史故障语料的**首段输出**（attempt-0，确定性输入）分别以不同层配置
 * 跑单遍解析（无重试），量化每一层的边际贡献：
 *   strictOnly : 仅 strict Jackson（= 无 L1/L2）
 *   noL1       : 跳过围栏剥离，仍做语法修复
 *   noL2       : 做围栏剥离，跳过语法修复
 *   fullParse  : L1+L2 全开（解析层完整形态）
 *   noL3       : 解析成功后跳过 SchemaGate → 统计"静默脏数据"（schema 不合格却会入库）
 *   noL5       : schema 通过后跳过 FidelityGate → 统计漏掉的捏造/越界违规
 *   full       : L1+L2+L3+L5 全开（不含重试，重试贡献由 live 实验单独测）
 *
 * 解析语义与 ParserChain 严格一致：FAIL_ON_UNKNOWN_PROPERTIES=false（未知字段忽略，
 * "必填缺失"由 SchemaGate 兜底）。语料首段提取用 Main.firstAttempt（标记式语料
 * 的 attempt-0 在首标记**之后**）。
 *
 * 运行：java -cp target/classes:$(cat target/cp.txt) com.resumeai.tools.LayerAblation
 * 输出：markdown 表格（stdout），由研究者落盘 evidence/research/q1-layer-ablation.md。
 */
public final class LayerAblation {

    public static void main(String[] args) throws Exception {
        Path corpusDir = Path.of("fixtures/llm-corpus");
        Path goldenDir = Path.of("fixtures/golden-resumes");
        ObjectMapper parseMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        ObjectMapper mapper = new ObjectMapper();
        SchemaGate gate = new SchemaGate();
        FidelityGate fidelity = new FidelityGate();

        // scenario -> golden 快照
        Map<String, ResumeSnapshot> snapshots = new TreeMap<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(goldenDir, "*.json")) {
            for (Path p : ds) {
                var root = mapper.readTree(p.toFile());
                snapshots.put(root.path("scenario").asText(), mapper.treeToValue(root, ResumeSnapshot.class));
            }
        }

        List<String> scenarios = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(corpusDir, "*.txt")) {
            for (Path p : ds) scenarios.add(p.getFileName().toString().replace(".txt", ""));
        }
        scenarios.sort(null);

        String[] configs = {"strictOnly", "noL1", "noL2", "fullParse", "noL3", "noL5", "full"};
        // 桶：0=parseOK 1=schemaDirty(被noL3静默放行) 2=schemaOK 3=fidDirty(被noL5漏掉) 4=最终放行
        Map<String, int[]> stat = new TreeMap<>();
        for (String c : configs) stat.put(c, new int[5]);
        List<String> rowsDetail = new ArrayList<>();

        for (String sc : scenarios) {
            String raw = com.resumeai.Main.firstAttempt(Files.readString(corpusDir.resolve(sc + ".txt")));
            ResumeSnapshot snap = snapshots.get(sc);
            if (snap == null) snap = snapshots.values().iterator().next();

            for (String cfg : configs) {
                boolean fence = !cfg.equals("strictOnly") && !cfg.equals("noL1");
                boolean repair = !cfg.equals("strictOnly") && !cfg.equals("noL2");
                boolean schema = !cfg.equals("noL3");
                boolean fid = !cfg.equals("noL5");

                String text = raw;
                if (fence) text = FenceStripper.strip(text);
                if (repair) text = LenientRepair.repair(text);
                if (LenientRepair.looksTruncated(text) && repair) {
                    rowsDetail.add(String.format("| %s | %s | 截断判失败 | - | - |", sc, cfg));
                    continue;
                }

                OptimizeResult result;
                try {
                    result = parseMapper.readValue(text, OptimizeResult.class);
                } catch (Exception e) {
                    rowsDetail.add(String.format("| %s | %s | 解析失败 | - | - |", sc, cfg));
                    continue;
                }
                stat.get(cfg)[0]++; // parseOK
                String schemaErr = gate.validate(mapper.readTree(text));
                boolean schemaPass;
                if (schema) {
                    schemaPass = schemaErr == null;   // L3 开：schema 说了算
                } else {
                    schemaPass = true;                 // L3 关：静默放行，脏数据计数
                    if (schemaErr != null) stat.get(cfg)[1]++;
                }
                if (!schemaPass) {
                    rowsDetail.add(String.format("| %s | %s | 解析OK | schema拒(%s) | - |", sc, cfg, brief(schemaErr)));
                    continue;
                }
                stat.get(cfg)[2]++; // schemaOK
                List<String> violations = fidelity.check(snap, result);
                if (!fid && !violations.isEmpty()) stat.get(cfg)[3]++; // fidDirty 漏掉
                if (fid && !violations.isEmpty()) {
                    rowsDetail.add(String.format("| %s | %s | 解析OK | schemaOK | 捏造拦截: %s |", sc, cfg,
                            violations.get(0).split(":")[0]));
                    continue;
                }
                stat.get(cfg)[4]++; // 最终放行
                rowsDetail.add(String.format("| %s | %s | 解析OK | schemaOK | 通过 |", sc, cfg));
            }
        }

        System.out.println("## Q1 六层防御边际贡献（15 语料首段输出，单遍无重试）\n");
        System.out.println("| 配置 | 解析OK | 静默脏(schema漏) | schemaOK | 捏造漏(fidelity漏) | 最终放行 |");
        System.out.println("|---|---|---|---|---|---|");
        for (String cfg : configs) {
            int[] s = stat.get(cfg);
            System.out.printf("| %s | %d | %d | %d | %d | %d |%n", cfg, s[0], s[1], s[2], s[3], s[4]);
        }
        System.out.println("\n### 明细（仅失败/拦截行）\n");
        System.out.println("| scenario | 配置 | 解析 | schema | fidelity |");
        System.out.println("|---|---|---|---|---|");
        rowsDetail.stream().filter(r -> !r.endsWith("通过 |")).forEach(System.out::println);
    }

    private static String brief(String schemaErr) {
        if (schemaErr == null) return "-";
        return schemaErr.length() > 60 ? schemaErr.substring(0, 60) + "…" : schemaErr;
    }

    private LayerAblation() {}
}
