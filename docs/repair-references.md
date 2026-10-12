# 修复引用（问题 → 实现 → 测试对照）

| 问题编号 | 故障层 | 修复模块 | 关键 commit | 测试 |
|---|---|---|---|---|
| RSO-001-a | L1 词法 | `pipeline/FenceStripper`（大括号跨度截取，替代围栏正则） | fix: 词法层围栏剥离 | `PipelineTest.fenceStripper剥离围栏与前后缀` |
| RSO-001-b | L2 语法 | `pipeline/LenientRepair`（键/值定界引号、trailing comma、行注释；定界位置才修） | fix: 语法层宽松修复 | `lenientRepair*` 3 个用例（含"不误伤值内部中文引号"） |
| RSO-001-c | L3 结构 | `pipeline/SchemaGate`（networknt，draft 2020-12）+ `ParserChain`（FAIL_ON_UNKNOWN_PROPERTIES=false） | fix: 结构层 schema 硬校验 | `schema拦截缺必填字段与越界分数` |
| RSO-001-d | L3 结构 | 重试纪律：初始+重试恰好 ≤2 次调用，差异化反馈 | fix: 受控重试与结构违规分流 | `重试硬上限恰好1次`、`捏造违规不触发重试`、`干净输出不重试` |
| RSO-001-e | L4 语义 | `OptimizePipeline.maybeFlagDrift`（Δ>25 或 ≥90 → DRIFT_SUSPECT，只标不拦） | feat: 分数漂移监控 | `漂移样本被打标但不拦截` |
| RSO-001-f | L5 事实 | `fidelity/AnchorExtractor` + `FidelityGate`（证书/公司/日期/量化锚点 diff；量级跳变 ≥10x 拦截） | feat: 锚点保真校验（合规生死线） | `新增证书/公司/量级跳变被拦截`、`同源量化放行` |
| RSO-001-g | L6 回归 | `eval/EvalRunner` + 黄金集 30 份 + `EvalThresholdTest` + prompt_version 落库 | feat: 黄金集评估与版本追溯 | `EvalThresholdTest.黄金集三指标达标` |
| RSO-001-h | 过程 bug | ① 空违规集合被 `allMatch` 判为结构失败导致无谓重试；② 语料块标记语义错位（结束式 vs 预告式）；③ schema 校验用未修复文本二次 readTree | fix: 三个过程 bug（见回归记录"评审前实测缺陷"节） | 语料表驱动 15 场景全绿为最终证明 |

## PR 索引

- PR #1（fix/hardening → main）：承载上表全部修复；评审意见见 docs/reviews/R1-评审意见-20261013.md，采纳记录见同目录。
