# 开源调研引用

> 调研日期：2026-10-11。以下项目是本管线分层设计的思考源头；引用其公开机制，不声称任何上游项目存在本项目缺陷。

| 项目 | 链接 | 机制一句话 | 本项目借鉴点 | 采用与否 |
|---|---|---|---|---|
| Instructor | https://github.com/567-labs/instructor（12.2k★，MIT） | Pydantic 模型做输出契约，验证失败把错误摘要拼进重试 prompt，自动修复式重试 | **差异化反馈重试**：重试不是原样重发，而是携带首次失败的结构化反馈（评审意见 #1 的机制出处） | 机制借鉴，未引依赖（Java 栈） |
| json_repair | https://github.com/mangiucugna/json_repair（5.1k★，MIT） | 对 LLM 畸形 JSON 做"修得好就修"的宽松解析（引号/逗号/截断/注释） | **LenientRepair 规则集**：键/值定界引号、trailing comma、行注释；刻意裁剪掉"硬补截断"（截断宁可失败重试，不编造后半段） | 机制借鉴，Java 侧自实现 ~60 行（依赖体积不划算） |
| outlines | https://github.com/dottxt-ai/outlines | 约束解码：把 JSON Schema 编译成 FSM，在采样期只允许合法 token | **"约束要在解码期做"的认知**：任何后验解析都晚于模型犯错 | 未采用：需自托管推理，本项目调 API（DeepSeek/GLM），解码期不可控 |
| XGrammar | https://github.com/mlc-ai/xgrammar（MLSys 2025） | 上下文无关文法→token 掩码的高效约束解码引擎 | 同上；其论文明确了"逐 token 掩码"是结构保证的唯一根治位 | 未采用，理由同 outlines |
| DeepSeek JSON Output | https://api-docs.deepseek.com/guides/json_mode | `response_format={'type':'json_object'}` 保证输出是**合法 JSON**，但不保证符合你的 schema；prompt 必须含 "json" 字样 | **"API 能力是地板不是解药"**：它消灭 L1/L2 的大部分，但 L3-L5 仍需应用层防御。生产接入时必开 | 设计输入（演练环境用语料回放等价模拟） |
| networknt/json-schema-validator | https://github.com/networknt/json-schema-validator | Java 生态最活跃的 JSON Schema 校验器（支持 draft 2020-12） | **SchemaGate 直接使用**：L3 结构层的标准实现，不自造校验轮子 | 直接采用（Maven 依赖） |
| OpenAI Structured Outputs | https://platform.openai.com/docs/guides/structured-outputs | 严格 schema 约束解码（供应商侧） | 结构保证的最高形态；单价与供应商锁定是种子轮预算下的现实约束 | 未采用（成本+锁定），留作模型路由的候选升级位 |

## 结论

分层防御的本质是**把"结构保证"放到位、把"事实保证"留在应用层**：

- 解码期约束（outlines/XGrammar/response_format）只能保证**形状**对——L1/L2/L3；
- L4（分数漂移）与 L5（事实捏造）没有任何解码期手段能保证——必须靠锚点 diff（FidelityGate）与评估集（EvalRunner）；
- 这就是为什么本管线在"假设模型侧已有 JSON mode"的前提下，仍保留全部五层应用侧防御。
