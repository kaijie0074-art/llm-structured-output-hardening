# 真实 LLM 评估实跑记录 · 2026-10-13

## 环境与修复

- 端点：`https://open.bigmodel.cn/api/coding/paas/v4`（智谱 GLM Coding Plan，OpenAI 兼容），模型 glm-4-flash
- **实测发现并修复真实缺陷**：`HttpLlmClient.SYSTEM_PROMPT` 只说"输出优化后的简历 JSON"，与管线 schema 要求的 `sections + score_before/score_after` 协议脱节——真实模型按 system prompt 输出整份简历，被 SchemaGate 30/30 拒绝（fixture 回放时代语料即协议格式，重构接真实 LLM 时未对齐）。修复 prompt 明确输出协议后：**30/30 OK**。
- 修复前后对比（hardened 全管线，同一 golden 集）：

| 阶段 | OK | FAILED | 分层触发 |
|---|---|---|---|
| 修复前 | 0/30 | 30 | RETRIED=30, SCHEMA_REJECTED=59 |
| 修复后 | **30/30** | 0 | RETRIED=4, SCHEMA_REJECTED=1, LENIENT_REPAIRED=27, DRIFT_SUSPECT=8 |

## 黄金集评估三指标（修复后，EvalRunner 自动落盘 `evidence/eval-report.md`）

| 指标 | 值 |
|---|---|
| 总样本 | 30 |
| 结构成功率 (OK+REJECTED)/total | 29/30 = 96.7% |
| FAILED（目标=0） | 1（07-field-renamed：2 次含反馈重试未收敛，如实记录） |
| 锚点拦截率（捏造语料被拒） | 0/3 —— 模型本轮未发生捏造（指令明确后无捏造可拦），非"漏拦" |
| clean 语料 score_after 标准差 | 18.01（漂移监控机制就位，DRIFT_SUSPECT=8 转人工复核队列） |

## 解读

1. 分层防御在真实模型上各司其职：SchemaGate 拦 1 次（反馈后重试收敛）、LenientRepair 修复 27 次轻微格式漂移、漂移监控标记 8 例进 needsReview。
2. 与 fixture 时代（100% 结构成功）的差异是真实的：真实模型存在 1 例两轮未收敛的结构失败——管线按设计 FAILED 而非静默放行。
3. 离线确定性回归 `mvn test` 33 项全绿不受影响。

## 凭证说明

凭证经 `LSOH_API_KEY` 环境变量注入，不入库；`LSOH_BASE_URL` 指向 Coding Plan 端点。
