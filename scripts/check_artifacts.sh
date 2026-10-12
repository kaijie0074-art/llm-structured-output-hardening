#!/usr/bin/env bash
# 工件完整性自检：evidence 指标达标、无 TODO/FIXME 残留
set -euo pipefail
cd "$(dirname "$0")/.."

fail=0
[ -f evidence/latest-run.json ] || { echo "缺 evidence/latest-run.json"; fail=1; }
[ -f evidence/eval-report.md ] || { echo "缺 evidence/eval-report.md"; fail=1; }

if [ -f evidence/latest-run.json ]; then
  python3 - <<'EOF'
import json, sys
d = json.load(open('evidence/latest-run.json'))
assert d['failed'] == 0, f"FAILED 必须为 0，实际 {d['failed']}"
assert d['fabricationCaught'] == d['fabricationTotal'] == 3, f"锚点拦截必须 3/3，实际 {d['fabricationCaught']}/{d['fabricationTotal']}"
assert d['ok'] + d['rejected'] == d['total'] == 30, f"结构成功率必须 100%"
assert d['cleanScoreStd'] < 5.0, f"漂移标准差超标: {d['cleanScoreStd']}"
print('evidence 指标达标:', {k: d[k] for k in ('total','ok','rejected','failed','fabricationCaught','cleanScoreStd')})
EOF
fi

if grep -rn "TODO\|FIXME" src/main/java src/test/java --include="*.java" | grep -v "// 预留"; then
  echo "存在 TODO/FIXME 残留"; fail=1
fi

[ "$fail" = 0 ] && echo "check_artifacts: PASS"
exit $fail
