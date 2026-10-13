## Q6 强制故障协议：attempt-0 本地注入 → attempt-1 真实 LLM 反馈 A/B

- 协议前置：attempt-0 修复解析 成功(!)（预期 fail→重试）

| 臂 | run | parse | schema | fidelity | 终态 | ms |
|---|---|---|---|---|---|---|
| A | 1 | ok | ok | ok | OK | 6632 |
| A | 2 | ok | ok | ok | OK | 11905 |
| A | 3 | ok | ok | ok | OK | 9777 |
| A | 4 | ok | ok | ok | OK | 5451 |
| A | 5 | ok | ok | ok | OK | 9437 |
| A | 6 | ok | ok | ok | OK | 4514 |
| A | 7 | ok | ok | ok | OK | 4431 |
| A | 8 | ok | ok | ok | OK | 8455 |
| B | 1 | ok | ok | ok | OK | 7003 |
| B | 2 | ok | ok | ok | OK | 8438 |
| B | 3 | ok | ok | ok | OK | 8263 |
| B | 4 | ok | ok | ok | OK | 7237 |
| B | 5 | ok | ok | ok | OK | 6615 |
| B | 6 | ok | ok | ok | OK | 9938 |
| B | 7 | ok | ok | ok | OK | 10327 |
| B | 8 | ok | ok | ok | OK | 4786 |
