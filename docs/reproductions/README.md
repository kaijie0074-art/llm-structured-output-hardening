# 复现材料

## 快速复现（无需任何 API key）

```bash
mvn -q compile dependency:build-classpath -Dmdep.outputFile=target/cp.txt
bash scripts/reproduce.sh
```

或分步：

```bash
# 基线缺陷态（v0.1.0 逻辑）：直接 Jackson readValue
java -cp "target/classes:$(cat target/cp.txt)" com.resumeai.Main baseline fixtures/llm-corpus fixtures/golden-resumes

# 加固态：分层防御全开
java -cp "target/classes:$(cat target/cp.txt)" com.resumeai.Main hardened fixtures/llm-corpus fixtures/golden-resumes

# 黄金集评估
java -cp "target/classes:$(cat target/cp.txt)" com.resumeai.Main eval fixtures/golden-resumes
```

## 语料说明

`fixtures/llm-corpus/` 共 15 个合成语料（无任何真实模型输出/用户数据）。
多尝试语料用 `===ATTEMPT n===` 分块：attempt 0 为首次输出（含故障），attempt 1 为重试输出。

> **口径更正（2026-10-14，研究 Q1）**：下图 2026-10-10 的旧记录把 06/07（标记式语料）
> 误计为崩溃——当时 `split("===ATTEMPT")[0]` 对"标记开头"的语料取到空串。
> 修正提取逻辑（`Main.firstAttempt`）后当前复现口径为：**崩溃 7**（01–05、14、15）
> **+ 静默脏 5**（06 路径越界、07 字段改名、09–11 捏造）——静默脏数据比旧记录
> **多 1 份**，"解析成功 ≠ 数据正确"的问题比历史认知更严重。
> 详见 docs/research/deep-dive-20261014.md。

## 基线态实测输出（2026-10-10 复现，v0.1.0）

```
语料崩溃（用户看到"优化失败请重试"）: 9
  04-single-quote-keys.txt -> JsonParseException
  07-field-renamed.txt -> MismatchedInputException
  03-trailing-comma.txt -> JsonMappingException
  06-path-index-error.txt -> MismatchedInputException
  14-retry-recoverable.txt -> MismatchedInputException
  01-fence-wrapped.txt -> JsonParseException
  02-chinese-quotes.txt -> JsonParseException
  15-truncated-unrecoverable.txt -> JsonMappingException
  05-leading-prose.txt -> JsonParseException
解析"成功": 6
事后审计：解析"成功"的输出中，schema/锚点/漂移不合格（静默脏数据）: 4
```

**解读**：60% 用户请求直接失败；剩余"成功"里 4 份脏数据无告警入库——其中含捏造证书/公司/量化的合规级风险数据。

## 加固态实测输出（2026-10-12，v1.0.0）

```
总数: 30 | OK: 27 | REJECTED_FABRICATION: 3 | FAILED: 0
needsReview（人工复核队列）: 3
分层触发计数: {RETRIED=3, FIDELITY_REJECTED=3, SCHEMA_REJECTED=1, LENIENT_REPAIRED=5, DRIFT_SUSPECT=1}
```

## 测试级复现

```bash
mvn test
# PipelineTest.语料场景终态 —— 15 场景表驱动断言
# PipelineTest.基线直接解析在畸形语料上崩溃 —— 断言基线行为确实崩
# EvalThresholdTest.黄金集三指标达标 —— 回归闸门
```

## 已知边界

- 确定性回归对历史故障样本库（fixtures/llm-corpus，程序合成的 L1-L5 已知故障形态）断言防御层行为；真实模型（HttpLlmClient）的故障分布会漂移，
  依赖 eval 黄金集 + 线上 needsReview 队列持续回收新形态进样本库（见 docs/design.md 第 5 节）。
- `LenientRepair` 只修"定界位置"的引号；字符串值内部的中文引号是合法内容，不修（测试覆盖）。
