## Q1 六层防御边际贡献（15 语料首段输出，单遍无重试）

| 配置 | 解析OK | 静默脏(schema漏) | schemaOK | 捏造漏(fidelity漏) | 最终放行 |
|---|---|---|---|---|---|
| strictOnly | 8 | 0 | 7 | 0 | 3 |
| noL1 | 11 | 0 | 10 | 0 | 6 |
| noL2 | 10 | 0 | 9 | 0 | 5 |
| fullParse | 13 | 0 | 12 | 0 | 8 |
| noL3 | 13 | 1 | 13 | 0 | 9 |
| noL5 | 13 | 0 | 12 | 4 | 12 |
| full | 13 | 0 | 12 | 0 | 8 |

### 明细（仅失败/拦截行）

| scenario | 配置 | 解析 | schema | fidelity |
|---|---|---|---|---|
| 01-fence-wrapped | strictOnly | 解析失败 | - | - |
| 01-fence-wrapped | noL1 | 解析失败 | - | - |
| 02-chinese-quotes | strictOnly | 解析失败 | - | - |
| 02-chinese-quotes | noL2 | 解析失败 | - | - |
| 03-trailing-comma | strictOnly | 解析失败 | - | - |
| 03-trailing-comma | noL2 | 解析失败 | - | - |
| 04-single-quote-keys | strictOnly | 解析失败 | - | - |
| 04-single-quote-keys | noL2 | 解析失败 | - | - |
| 05-leading-prose | strictOnly | 解析失败 | - | - |
| 05-leading-prose | noL1 | 解析失败 | - | - |
| 06-path-index-error | strictOnly | 解析OK | schemaOK | 捏造拦截: FIELD_TARGET_INVALID |
| 06-path-index-error | noL1 | 解析OK | schemaOK | 捏造拦截: FIELD_TARGET_INVALID |
| 06-path-index-error | noL2 | 解析OK | schemaOK | 捏造拦截: FIELD_TARGET_INVALID |
| 06-path-index-error | fullParse | 解析OK | schemaOK | 捏造拦截: FIELD_TARGET_INVALID |
| 06-path-index-error | noL3 | 解析OK | schemaOK | 捏造拦截: FIELD_TARGET_INVALID |
| 06-path-index-error | full | 解析OK | schemaOK | 捏造拦截: FIELD_TARGET_INVALID |
| 07-field-renamed | strictOnly | 解析OK | schema拒($.sections[0]: 未找到所需属性“rewritten”) | - |
| 07-field-renamed | noL1 | 解析OK | schema拒($.sections[0]: 未找到所需属性“rewritten”) | - |
| 07-field-renamed | noL2 | 解析OK | schema拒($.sections[0]: 未找到所需属性“rewritten”) | - |
| 07-field-renamed | fullParse | 解析OK | schema拒($.sections[0]: 未找到所需属性“rewritten”) | - |
| 07-field-renamed | noL5 | 解析OK | schema拒($.sections[0]: 未找到所需属性“rewritten”) | - |
| 07-field-renamed | full | 解析OK | schema拒($.sections[0]: 未找到所需属性“rewritten”) | - |
| 09-fabricated-certificate | strictOnly | 解析OK | schemaOK | 捏造拦截: NEW_CERTIFICATE |
| 09-fabricated-certificate | noL1 | 解析OK | schemaOK | 捏造拦截: NEW_CERTIFICATE |
| 09-fabricated-certificate | noL2 | 解析OK | schemaOK | 捏造拦截: NEW_CERTIFICATE |
| 09-fabricated-certificate | fullParse | 解析OK | schemaOK | 捏造拦截: NEW_CERTIFICATE |
| 09-fabricated-certificate | noL3 | 解析OK | schemaOK | 捏造拦截: NEW_CERTIFICATE |
| 09-fabricated-certificate | full | 解析OK | schemaOK | 捏造拦截: NEW_CERTIFICATE |
| 10-fabricated-company | strictOnly | 解析OK | schemaOK | 捏造拦截: NEW_COMPANY |
| 10-fabricated-company | noL1 | 解析OK | schemaOK | 捏造拦截: NEW_COMPANY |
| 10-fabricated-company | noL2 | 解析OK | schemaOK | 捏造拦截: NEW_COMPANY |
| 10-fabricated-company | fullParse | 解析OK | schemaOK | 捏造拦截: NEW_COMPANY |
| 10-fabricated-company | noL3 | 解析OK | schemaOK | 捏造拦截: NEW_COMPANY |
| 10-fabricated-company | full | 解析OK | schemaOK | 捏造拦截: NEW_COMPANY |
| 11-fabricated-metric | strictOnly | 解析OK | schemaOK | 捏造拦截: NEW_METRIC |
| 11-fabricated-metric | noL1 | 解析OK | schemaOK | 捏造拦截: NEW_METRIC |
| 11-fabricated-metric | noL2 | 解析OK | schemaOK | 捏造拦截: NEW_METRIC |
| 11-fabricated-metric | fullParse | 解析OK | schemaOK | 捏造拦截: NEW_METRIC |
| 11-fabricated-metric | noL3 | 解析OK | schemaOK | 捏造拦截: NEW_METRIC |
| 11-fabricated-metric | full | 解析OK | schemaOK | 捏造拦截: NEW_METRIC |
| 14-retry-recoverable | strictOnly | 解析失败 | - | - |
| 14-retry-recoverable | noL1 | 解析失败 | - | - |
| 14-retry-recoverable | noL2 | 解析失败 | - | - |
| 14-retry-recoverable | fullParse | 解析失败 | - | - |
| 14-retry-recoverable | noL3 | 解析失败 | - | - |
| 14-retry-recoverable | noL5 | 解析失败 | - | - |
| 14-retry-recoverable | full | 解析失败 | - | - |
| 15-truncated-unrecoverable | strictOnly | 解析失败 | - | - |
| 15-truncated-unrecoverable | noL1 | 解析失败 | - | - |
| 15-truncated-unrecoverable | noL2 | 解析失败 | - | - |
| 15-truncated-unrecoverable | fullParse | 解析失败 | - | - |
| 15-truncated-unrecoverable | noL3 | 解析失败 | - | - |
| 15-truncated-unrecoverable | noL5 | 解析失败 | - | - |
| 15-truncated-unrecoverable | full | 解析失败 | - | - |
