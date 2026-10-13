# llm-structured-output-hardening

**LLM 结构化输出可靠性 · 分层防御管线**——针对"简历字段级 AI 改写"链路（上传 → LLM 输出 JSON → 反序列化 → 落库），把 LLM 输出漂移的六层故障（词法/语法/结构/语义/**事实捏造**/回归）全部变成可测试、可拦截、可评估的确定性工程问题。

> LLM 为真实 API（OpenAI 兼容协议，默认智谱，可切 DeepSeek 等任意兼容服务，`LSOH_API_KEY`/`LSOH_BASE_URL`/`LSOH_MODEL`）；不含真实用户简历，黄金集为程序合成（固定种子）；上游开源项目（instructor、json_repair、outlines、XGrammar、networknt 等）仅作机制引用，见 [docs/open-source-references.md](docs/open-source-references.md)。fixtures/llm-corpus 为历史故障输出样本库（程序合成），用于防御层的确定性回归与基线缺陷复现。

## 为什么值得看

基线实现（`v0.1.0-baseline`：直接 Jackson `readValue`）的实拍数据：

- 15 个故障语料 **9 个解析崩溃**——用户看到"优化失败请重试"
- 解析"成功"的输出里 **4 份静默脏数据**——包括捏造的证书/公司/"日均 3 万单"，直接落库渲染给用户，构成合规级风险（AI 帮学生简历造假）

加固后（v1.0.0）：**崩溃 0、静默脏数据 0、捏造 100% 拦截进人工复核队列、黄金集 30 份结构成功率 100%**（对历史故障样本库的确定性回归口径；真实 LLM 在线评估跑 `Main eval`，需凭证）。

## 快速开始

要求：JDK 17+、Maven 3.8+。

```bash
mvn test                # 33 用例：15 场景表驱动（历史故障样本）+ 分层单测 + 重试纪律 + 评估阈值
bash scripts/reproduce.sh   # 基线缺陷复现（修正口径：7 崩 + 5 静默脏，见 docs/reproductions/README.md）；加固/评估段配置凭证后跑真实 LLM
bash scripts/check_artifacts.sh  # 工件自检
```

## 分层防御一览

```
LLM 输出 → L1 FenceStripper → L2 LenientRepair → L3 ParserChain+SchemaGate
         →（结构失败：带差异化反馈重试恰好 1 次）
         → L5 FidelityGate（锚点保真：捏造=拒绝落库；越界=可重试结构失败）
         → L4 DriftFlag → ResultStore（prompt_version 全链路落库）
```

| 层 | 拦什么 | 关键类 |
|---|---|---|
| L1 | ```json 围栏、寒暄前后缀 | `FenceStripper` |
| L2 | 中文/单引号定界、尾逗号、行注释；**截断不硬补** | `LenientRepair` |
| L3 | 必填缺失、字段改名、类型/范围越界 | `ParserChain` + `SchemaGate` |
| L5 | **捏造证书/公司/量化数字（≥10x 跳变）、field 越界** | `FidelityGate` + `AnchorExtractor` |
| L4 | 分数漂移打标（不拦截） | `OptimizePipeline` |
| L6 | 改 prompt 无回归 → 30 份黄金集三指标闸门 | `EvalRunner` + `EvalThresholdTest` |

## 文档路径

- [问题报告 RSO-001](docs/issue-reports/RSO-001-structured-output-drift.md) —— 现象/复现/根因/验收标准
- [复现材料](docs/reproductions/README.md) —— 一条命令复现基线崩溃与静默脏数据
- [设计](docs/design.md) —— 分层理由 + 拒绝的替代方案（为什么不硬补截断、为什么捏造不重试）
- [开源调研引用](docs/open-source-references.md) / [修复引用](docs/repair-references.md)
- [回归记录](docs/regression-records/结构化输出加固-回归记录-20261013.md) —— 基线 vs 加固全指标 + 过程实测缺陷留档
- [评审意见](docs/reviews/R1-评审意见-20261013.md) / [采纳记录](docs/reviews/采纳记录.md) / [人工评审](docs/reviews/人工评审-代码走查-20261013.md)
- [负责人验收](docs/acceptance.md) / [覆盖场景说明](docs/test-coverage.md)
- [CHANGELOG](CHANGELOG.md) / [任务地图](work/wayfinder-map.md)

## 工程闭环（这个仓库演示的完整流程）

问题报告 → 确定性语料复现 → 开源调研 → 分层修复 → 表驱动测试 → 黄金集评估 → PR → 评审 6 意见（全采纳+1 拒绝备案）→ 代码走查 → 负责人验收 → 合并 → v1.0.0 Release。全部工件在仓库内可追溯。

## License

MIT。
