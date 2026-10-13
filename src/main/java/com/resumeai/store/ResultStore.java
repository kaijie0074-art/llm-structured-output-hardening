package com.resumeai.store;

import com.resumeai.model.PipelineOutcome;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 落库前的内存结果仓（进程内聚合用；持久化替换为 MySQL 表 + prompt_version 列）。
 *
 * 评审意见 #4：暴露统计接口，供 /metrics 与看板消费，不用翻日志。
 */
public class ResultStore {

    public static final String PROMPT_VERSION = "prompt-v1.3.0";

    private final Map<Long, PipelineOutcome> rows = new ConcurrentHashMap<>();
    private final AtomicLong idGen = new AtomicLong();

    public long save(PipelineOutcome outcome) {
        long id = idGen.incrementAndGet();
        rows.put(id, outcome);
        return id;
    }

    public int countByStatus(PipelineOutcome.Status status) {
        return (int) rows.values().stream().filter(r -> r.getStatus() == status).count();
    }

    public int countNeedsReview() {
        return (int) rows.values().stream().filter(PipelineOutcome::isNeedsReview).count();
    }

    /** 各防御层的触发计数（观察哪层在干活）。 */
    public Map<String, Long> layerCounts() {
        return rows.values().stream()
                .flatMap(r -> r.getLayers().stream())
                .collect(Collectors.groupingBy(l -> l, Collectors.counting()));
    }

    /** 按 promptVersion 分组的总量（L6 回归层：出问题能追溯到版本）。 */
    public Map<String, Long> countsByPromptVersion() {
        return rows.values().stream()
                .collect(Collectors.groupingBy(
                        r -> r.getResult() != null && r.getResult().getPromptVersion() != null
                                ? r.getResult().getPromptVersion() : "unknown",
                        Collectors.counting()));
    }

    public List<PipelineOutcome> all() {
        return new ArrayList<>(rows.values());
    }

    public int size() { return rows.size(); }
}
