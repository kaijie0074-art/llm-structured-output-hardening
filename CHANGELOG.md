# CHANGELOG

本项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号语义化。

## [1.1.0] - 2026-10-14

自主深潜研究（docs/research/deep-dive-20261014.md，6 问实验链，42/42 测试全绿）。

### 修复

- **基线口径**：`Main.firstAttempt` 修正标记式语料首段提取（旧 split 把 06/07 误计为崩溃；
  基线实为 7 崩 + 5 静默脏——静默脏比历史认知多 1 份）
- **L1**：FenceStripper 结构归一化前置（全角大括号输出不再截出可解析的垃圾片段）

### 增强

- **L2**：`LenientRepair.normalizeStructural`——字符串外剥行内/块注释、删零宽/BOM、
  NBSP→空格、全角 ｛｝＂：， 映射 ASCII；字符串内部内容零改动（对抗性损坏 6 类穿透→0）
- **L4**：DRIFT_SUSPECT 调优为 |Δ|>50（benign 误报 47%→11%，数据驱动）
- **LLM 客户端**：LSOH_THROTTLE_MS 节流 + 429 退避重试（共享订阅配额）

### 研究结论（瓶颈证据）

- 输出非确定性为服务端固有：temp=0 下 8/8 次输出互异，+seed 仍 4/4 互异，
  自评分数极差 90/100——逐位复现与低失败率 A/B（需 N≥769/臂）在应用层不可实现
- 研究后全量复跑：30/30=100%，FAILED=0（evidence/eval-report.md）

## [1.0.0] - 2026-10-13

合并 PR #1（fix/hardening → main），负责人验收通过（docs/acceptance.md）。

### 修复（相对 v0.1.0-baseline）

- **L1 词法**：FenceStripper 剥离代码块围栏与前后缀文本
- **L2 语法**：LenientRepair 修键/值定界引号（中/单引号）、trailing comma、行注释；截断不硬补
- **L3 结构**：SchemaGate（networknt，draft 2020-12）+ ParserChain 宽松反序列化
- **受控重试**：结构失败带差异化反馈重试恰好 1 次；干净输出零重试；捏造违规不重试
- **L5 事实**：FidelityGate 锚点保真（证书/公司/日期/量化；≥10x 量级跳变拦截），拒绝落库进 needsReview
- **L4 语义**：分数漂移打标（DRIFT_SUSPECT）不拦截
- **L6 回归**：30 份黄金集 + 三指标（结构成功率 100% / 锚点拦截 3/3 / clean σ=0.51）+ prompt_version 落库

### 评审与验收

- 评审 6 条意见全采纳、1 条提议拒绝备案（docs/reviews/）
- 过程实测缺陷 5 个全部修复留档（docs/regression-records/）
- 测试 33 用例全绿；CI 上线

## [0.1.0-baseline] - 2026-10-09

初始实现：直接 `objectMapper.readValue(body, OptimizeResult.class)`，无任何防御。

**已知缺陷**（RSO-001，见 docs/issue-reports/）：

- 15 语料 9 个解析崩溃（用户看到"优化失败请重试"）
- 解析"成功"中 4 份静默脏数据入库（含捏造证书/公司/量化）
- 无评估手段，改 prompt 不可观测
