#!/usr/bin/env bash
# 复现脚本：基线缺陷态 vs 加固态，输出对比统计（详见 docs/reproductions/README.md）
set -euo pipefail
cd "$(dirname "$0")/.."

if [ ! -f target/cp.txt ]; then
  mvn -q compile dependency:build-classpath -Dmdep.outputFile=target/cp.txt >/dev/null
fi
CP="target/classes:$(cat target/cp.txt)"

echo "### 基线（v0.1.0 缺陷态：直接 Jackson readValue）"
java -cp "$CP" com.resumeai.Main baseline fixtures/llm-corpus fixtures/golden-resumes 2>/dev/null
echo
echo "### 加固（v1.0.0：分层防御）"
java -cp "$CP" com.resumeai.Main hardened fixtures/llm-corpus fixtures/golden-resumes 2>/dev/null
echo
echo "### 黄金集评估（三指标）"
java -cp "$CP" com.resumeai.Main eval fixtures/golden-resumes 2>/dev/null | sed -n '1,12p'
