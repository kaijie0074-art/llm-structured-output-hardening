# 基线 vs 加固对照（由 scripts/reproduce.sh 于 2026-10-13 实跑生成，人工整理成表）

## 基线（v0.1.0-baseline：直接 Jackson readValue）

- 15 语料：**9 崩溃**（JsonParseException/MismatchedInput/JsonMapping），6 "解析成功"
- 黄金集 30 份事后审计：解析"成功"中 **4 份静默脏数据**（越界/漂移/捏造），零告警入库

## 加固（v1.0.0）

- 黄金集 30 份：**OK 27 / REJECTED_FABRICATION 3 / FAILED 0**
- 分层触发：{RETRIED=3, FIDELITY_REJECTED=3, SCHEMA_REJECTED=1, LENIENT_REPAIRED=5, DRIFT_SUSPECT=1}
- promptVersion 全量落库 prompt-v1.3.0

## 评估三指标

| 指标 | 值 | 阈值 |
|---|---|---|
| 结构成功率 | 30/30 = 100% | =100% |
| 锚点拦截率 | 3/3 | =100% |
| clean σ | 0.51 | <5 |
