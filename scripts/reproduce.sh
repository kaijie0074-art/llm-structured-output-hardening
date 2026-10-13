#!/usr/bin/env bash
# 复现脚本：基线缺陷态（历史故障样本） vs 加固态（真实 LLM）
set -euo pipefail
cd "$(dirname "$0")/.."

if [ ! -f target/cp.txt ]; then
  mvn -q compile dependency:build-classpath -Dmdep.outputFile=target/cp.txt >/dev/null
fi
CP="target/classes:$(cat target/cp.txt)"

echo "### 基线（v0.1.0 缺陷态：直接 Jackson readValue，历史故障样本库）"
java -cp "$CP" com.resumeai.Main baseline fixtures/llm-corpus fixtures/golden-resumes 2>/dev/null
echo

HAS_KEY="${LSOH_API_KEY:-${OPENAI_API_KEY:-}}"
if [ -z "$HAS_KEY" ]; then
  echo "### 加固与黄金集评估需要真实 LLM 凭证"
  echo "export LSOH_API_KEY=...（或 OPENAI_API_KEY；可选 LSOH_BASE_URL/LSOH_MODEL）后重跑："
  echo "  java -cp <classpath> com.resumeai.Main hardened fixtures/golden-resumes"
  echo "  java -cp <classpath> com.resumeai.Main eval fixtures/golden-resumes"
  echo "确定性回归（对历史故障样本的防御层行为）：mvn test"
  exit 0
fi
echo "### 加固（分层防御，真实 LLM）"
java -cp "$CP" com.resumeai.Main hardened fixtures/golden-resumes 2>/dev/null
echo
echo "### 黄金集评估（真实 LLM，三指标）"
java -cp "$CP" com.resumeai.Main eval fixtures/golden-resumes 2>/dev/null | sed -n '1,12p'
