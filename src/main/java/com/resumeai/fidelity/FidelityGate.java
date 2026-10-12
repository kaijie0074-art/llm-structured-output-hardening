package com.resumeai.fidelity;

import com.resumeai.model.OptimizeResult;
import com.resumeai.model.ResumeSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * L5 事实层防御：锚点保真校验（本管线的合规生死线）。
 *
 * 规则（docs/design.md 第 3 层）：
 * 1. FIELD_TARGET_INVALID：section.field 的 path 必须指向快照里真实存在的经历/教育段
 *    （防 L3 的 path 越界与 L5 的"给不存在的经历编内容"）；
 * 2. NEW_CERTIFICATE / NEW_COMPANY：改写文本中出现原文没有的证书/公司 → 捏造；
 * 3. NEW_METRIC / METRIC_INFLATED：改写中出现原文没有的量化数字，或同义改写中数字
 *    量级跳变 ≥{@link #INFLATE_FACTOR} 倍 → 捏造量化。
 *
 * 量级常量依据（评审意见 #3 要求写明）：简历量化口径的合理润色不会跨数量级
 * （"约 3000 单"→"约 3000 单/日"合法；"3000"→"30000"即口径造假），10 倍为行业
 * 常用异常分界，保守起见同时要求"原文不存在该值"才判违规。
 */
public final class FidelityGate {

    /** 量级跳变判定倍数：≥10 倍视为口径造假。 */
    public static final int INFLATE_FACTOR = 10;
    /** 数值低于该值的"新数字"不判违规（如"优化了 3 个模块"）：业务上无事实风险。 */
    public static final long METRIC_FLOOR = 100;

    private static final Pattern FIELD_PATH = Pattern.compile(
            "(work_exps?|work_exp|educations?)\\[(\\d+)\\]\\.\\w+");

    public List<String> check(ResumeSnapshot snapshot, OptimizeResult result) {
        List<String> violations = new ArrayList<>();

        // 1) field 目标存在性
        int workCount = snapshot.getWorkExps().size();
        int eduCount = snapshot.getEducations().size();
        for (var section : result.getSections()) {
            String field = section.getField();
            if (field == null) continue;
            var m = FIELD_PATH.matcher(field);
            if (m.matches()) {
                int idx = Integer.parseInt(m.group(2));
                boolean isWork = m.group(1).startsWith("work");
                int bound = isWork ? workCount : eduCount;
                if (idx >= bound) {
                    violations.add("FIELD_TARGET_INVALID:" + field + " 越界（共 " + bound + " 段）");
                }
            }
        }

        // 2) 证书/公司差集
        Set<String> newCerts = AnchorExtractor.rewrittenTextAnchors(result, AnchorExtractor.AnchorKind.CERTIFICATE);
        newCerts.removeAll(AnchorExtractor.snapshotAnchors(snapshot, AnchorExtractor.AnchorKind.CERTIFICATE));
        for (String c : newCerts) violations.add("NEW_CERTIFICATE:" + c);

        Set<String> newCompanies = AnchorExtractor.rewrittenTextAnchors(result, AnchorExtractor.AnchorKind.COMPANY);
        newCompanies.removeAll(AnchorExtractor.snapshotAnchors(snapshot, AnchorExtractor.AnchorKind.COMPANY));
        for (String c : newCompanies) violations.add("NEW_COMPANY:" + c);

        // 3) 量化数字差集与量级跳变
        Set<Long> originalMetrics = new java.util.HashSet<>();
        snapshot.getWorkExps().forEach(w -> originalMetrics.addAll(AnchorExtractor.metrics(w.getDescription())));
        snapshot.getEducations().forEach(e -> { /* 教育段数字多为绩点/年份，保守不查 */ });
        // 也把原文经历的数字并入"允许范围"（口径同源）
        snapshot.getWorkExps().forEach(w -> originalMetrics.addAll(AnchorExtractor.metrics(w.getDateRange())));

        Set<Long> rewrittenMetrics = new java.util.HashSet<>();
        result.getSections().forEach(s -> rewrittenMetrics.addAll(AnchorExtractor.metrics(s.getRewritten())));

        for (Long v : rewrittenMetrics) {
            if (v <= 0) continue;
            if (originalMetrics.contains(v)) continue;
            if (v < METRIC_FLOOR) continue;
            boolean nearOriginal = originalMetrics.stream()
                    .anyMatch(orig -> orig > 0 && (v % orig == 0 || orig % v == 0)
                            && (Math.max(v, orig) / (double) Math.min(v, orig) < INFLATE_FACTOR));
            if (!nearOriginal) {
                violations.add("NEW_METRIC:" + v + "（原文无该量化且非同源口径）");
            } else if (originalMetrics.stream().anyMatch(orig -> orig > 0 && v / orig >= INFLATE_FACTOR)) {
                violations.add("METRIC_INFLATED:" + v);
            }
        }
        return violations;
    }
}
