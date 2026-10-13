# wayfinder-map：llm-structured-output-hardening

## 里程碑

| # | 里程碑 | 判据 | 完成 | 证据 |
|---|---|---|---|---|
| 1 | 基线复现 | 15 语料直接 parse，崩溃数与脏数据可量化 | ✅ 10-10 | docs/reproductions/README.md（9 崩/4 脏） |
| 2 | 开源调研 | 每层故障至少 1 个上游机制参照 | ✅ 10-11 | docs/open-source-references.md |
| 3 | 分层修复 | 15 场景表驱动全绿 | ✅ 10-12 | PipelineTest.语料场景终态 |
| 4 | 评估闭环 | 黄金集 30 份三指标达标 | ✅ 10-12 | evidence/latest-run.json |
| 5 | 评审迭代 | 6 意见落地 + 采纳记录 | ✅ 10-13 | docs/reviews/ |
| 6 | 验收合并 | 验收清单 9/10 项过 | ✅ 10-13 | docs/acceptance.md |

## 过程缺陷台账（修复于 M3/M4）

1. schema 树用未修复文本重建 → ParseAttempt 携带同源树
2. 空违规集合 allMatch=true → 27/30 无谓重试 → 前置 isEmpty 判断
3. 语料块标记结束式/预告式错位 → RecordedLlmClient 重写
4. ResumeSnapshot snake_case 未映射 → @JsonProperty
5. "3 万"空格未归一 → NUMBER 正则容忍

## 剩余风险（v1.1 方向）

- 英文锚点词表（AnchorExtractor）
- 真实模型接入后的失败模式分布（needsReview 回收机制承接）
