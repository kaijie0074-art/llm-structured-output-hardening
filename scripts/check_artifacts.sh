#!/usr/bin/env bash
# 工件完整性自检：evidence 指标达标（配置了真实 LLM 凭证时）、无 TODO/FIXME 残留
set -euo pipefail
cd "$(dirname "$0")/.."

HAS_KEY="${LSOH_API_KEY:-${OPENAI_API_KEY:-}}"

fail=0
if [ -n "$HAS_KEY" ]; then
  [ -f evidence/latest-run.json ] || { echo "缺 evidence/latest-run.json（有凭证时应先跑 eval）"; fail=1; }
  [ -f evidence/eval-report.md ] || { echo "缺 evidence/eval-report.md"; fail=1; }
  if [ -f evidence/latest-run.json ]; then
    python3 - <<'EOF'
import json, sys
d = json.load(open('evidence/latest-run.json'))
assert d['failed'] == 0, f"FAILED 必须为 0，实际 {d['failed']}"
assert d['ok'] + d['rejected'] == d['total'], f"结构成功率必须 100%"
print('evidence 指标达标:', {k: d[k] for k in ('total','ok','rejected','failed','cleanScoreStd')})
EOF
  fi
else
  echo "[跳过] evidence 检查：未配置 LSOH_API_KEY/OPENAI_API_KEY，真实 LLM 评估不运行"
fi

if grep -rn "TODO\|FIXME" src/main/java src/test/java --include="*.java" | grep -v "// 预留"; then
  echo "存在 TODO/FIXME 残留"; fail=1
fi

[ "$fail" = 0 ] && echo "check_artifacts: PASS"
exit $fail
