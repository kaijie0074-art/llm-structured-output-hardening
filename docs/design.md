# 设计：分层防御管线（design.md）

## 总体

```
LLM 原始输出
  → [L1] FenceStripper      剥围栏/前后缀（大括号跨度截取）
  → [L2] LenientRepair      修定界引号/尾逗号/行注释；截断直接判失败
  → [L3] ParserChain        strict Jackson → 修复后再试（FAIL_ON_UNKNOWN_PROPERTIES=false）
       SchemaGate           networknt JSON Schema 硬校验（draft 2020-12）
  →      结构失败？→ 受控重试恰好 1 次（携带差异化反馈）
  → [L5] FidelityGate       锚点 diff：证书/公司/日期/量化；field 目标存在性
         捏造违规 → REJECTED_FABRICATION（不重试）+ needsReview
         结构违规（FIELD_TARGET_INVALID）→ 走重试
  → [L4] maybeFlagDrift     分数漂移打标（不拦截）
  → ResultStore             prompt_version 全链路落库
```

## 关键决策与理由

### D1：为什么 schema 校验放在 Jackson 之后而不是替代它
Jackson 负责把 JSON 树读出来（容忍未知字段），schema 负责对树做**业务结构**断言（必填/范围/数量）。
两者合并（如直接用 json-schema2pojo 生成类）会让"字段改名"直接崩在反序列化——那是基线的死法。

### D2：为什么捏造违规不重试
重试的经济学：结构失败重试**收敛**（反馈指明约束，模型能改对）；捏造被拒后重试，模型倾向"换一种编法"而不是"承认改不了"——把"主导"换成"牵头主导"，锚点 diff 还是拦，但用户多等一次。直接进 needsReview 人工队列，成本更低。
（此判断在评审中被李工质询过，见评审意见 #6 的往来，最终维持并补充了 needsReview 的 SLA 说明。）

### D3：为什么 LenientRepair 不硬补截断
json_repair 支持补齐缺失的收界符。本项目拒绝：截断点之后的**内容**是模型没说完的话，任何补法都是猜——猜对是运气，猜错是脏数据。截断必须失败→重试→仍截断则 FAILED，让用户重试而非静默拿半份结果。

### D4：量化数字的白名单规则（FidelityGate）
- 原文已有的数字（含同源倍数关系 <10x）放行——"润色措辞、保留口径"合法；
- 新数字 ≥100 且与原文无同源关系 → NEW_METRIC 拦截；
- 同源但放大 ≥10x → METRIC_INFLATED 拦截；
- <100 的小数字（"优化了 3 个模块"）放行——无事实风险，拦了伤体验。
- 常量 `INFLATE_FACTOR=10`、`METRIC_FLOOR=100` 的依据见 FidelityGate 注释（评审意见 #3 落实项）。

### 拒绝的替代方案

| 方案 | 拒绝理由 |
|---|---|
| 全部走多模态 LLM 直接判捏造 | 判断不可复现、不可测试；锚点 diff 是确定性的、可单测的 |
| 每次输出都过一次 LLM-as-judge | judge 本身漂移（同模型自评系统性虚高）；作为软指标可留，作为闸门不行 |
| 无限重试直到解析成功 | 与线程池雪崩问题直接耦合（见姊妹仓库 spring-llm-async-bulkhead）；上限 1 次是纪律 |
| 用 OpenAI Structured Outputs 一劳永逸 | 只解决 L1-L3；L4/L5 无解；且供应商锁定+单价 |

## 评估体系（L6）

- 黄金集：30 份合成简历（`tools/GenerateGoldenSet`，固定种子 20261013，本科/大专分层），scenario 字段挂 15 语料
- 三指标：结构成功率（=100% 要求 FAILED=0）、锚点拦截率（3/3）、clean 分数漂移 σ（<5）
- `prompt_version` 每条落库：改 prompt 出问题可追溯到版本

## 线上闭环（生产接入说明）

1. 真实 LlmClient 接 DeepSeek/GLM，必开 `response_format=json_object`
2. REJECTED_FABRICATION → needsReview 人工队列（产品侧一个简单工作台）
3. 每周把 needsReview 里确认的新故障形态固化成新语料 → 回归测试加行
