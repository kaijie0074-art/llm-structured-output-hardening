# 真实 LLM 评估实跑记录 · 2026-10-13（复跑修订版）

## 上一轮 FAILED 1 例的复跑结论

首轮 eval（`live-llm-eval-20261013.md`）07-field-renamed 2 次未收敛 FAILED（96.7%）。本轮同配置复跑：

- 单对复跑（07-field-renamed）：**2 次尝试收敛**（LENIENT_REPAIRED → SCHEMA_REJECTED → 反馈重试 → LENIENT_REPAIRED 成功）
- 全量 eval 复跑：**30/30 = 100.0%，FAILED=0**（EvalRunner 已落盘 `evidence/eval-report.md` + `latest-run.json`）

结论：残余失败为真实模型输出非确定性的逐轮漂移，反馈-重试机制按设计工作；管线两轮全量行为一致——**失败时显式 FAILED 而非静默放行**。

## 三轮全量汇总（模型 glm-4-flash，端点 Coding Plan）

| 轮次 | 结构成功率 | FAILED | 分层特征 |
|---|---|---|---|
| prompt 修复前 | 0/30 | 30 | SCHEMA_REJECTED=59（prompt 与协议脱节） |
| prompt 修复后第 1 轮 | 29/30 | 1 | 07 例 2 次未收敛（如实记录） |
| prompt 修复后第 2 轮 | **30/30** | 0 | clean score σ=16.79，漂移监控持续在位 |

锚点拦截率 0/3：模型在明确指令下未发生捏造（无物可拦），非漏拦。
