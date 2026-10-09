# 贡献指南

## 跑测试

```bash
mvn test
```

33 用例是合并门槛：15 语料场景表驱动 + 分层单测 + 重试纪律 + 评估阈值。

## 改 prompt 的流程（L6 层纪律）

1. 改 `OptimizePipeline.PROMPT_VERSION`（递增版本号）
2. 改前后各跑一次 `java -cp ... com.resumeai.Main eval fixtures/golden-resumes`
3. 对比 `evidence/eval-report.md` 三指标：结构成功率必须 100%、锚点拦截必须全量、漂移 σ 不得劣化超过 20%
4. PR 里贴两份报告的对比

## 新增故障语料

从线上 needsReview 队列确认的新故障形态 → `fixtures/llm-corpus/` 加 `NN-场景名.txt` →
`PipelineTest.语料场景终态` 的 @CsvSource 加一行（场景, 期望终态）→ 黄金集生成器对应分配。

## 分支规范

- `main`：受保护，只接受 PR
- `fix/<问题编号>-<slug>` / `feat/<slug>`：一个 PR 一个主题

## 提交规范

`feat|fix|test|docs|refactor: 中文摘要`，一个 commit 一件事。修复须能对应到 docs/repair-references.md 的行。
