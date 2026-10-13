## Q2 对抗性损坏穿透矩阵（当前防御，单遍无重试）

| 变体 | L1/L2 后果 | 说明 |
|---|---|---|
| 00-valid | PASS(直接解析) | 合法基线（对照） |
| 01-inline-comment | PASS(修复后解析) | 行内尾注释（// 在逗号后同一行） |
| 02-block-comment | PASS(修复后解析) | 块注释插在成员之间 |
| 03-comment-in-value | PASS(直接解析) | 字符串值内部的注释——是合法内容，必须原样保留 |
| 04-cn-key | SCHEMA_OK(sections 丢失→L3 拒) | 中文键替代 sections（未知字段，Jackson 忽略 → sections=null → L3 拒） |
| 05-homoglyph-key | SCHEMA_OK(sections 丢失→L3 拒) | 希腊同形字母 omicron (U+03BF) 替换键名中的 o → L3 拒 |
| 06-fullwidth-braces | PASS(修复后解析) | 全角大括号（U+FF5B/U+FF5D） |
| 07-fullwidth-quotes | PASS(修复后解析) | 全角大括号+全角双引号（U+FF5B/U+FF02 组合）作定界符 |
| 08-zero-width-in-key | SCHEMA_OK(sections 丢失→L3 拒) | 键名尾部零宽空格（U+200B） |
| 09-zero-width-structural | PASS(修复后解析) | 结构位置（闭引号与冒号之间）零宽空格 |
| 10-bom | PASS(修复后解析) | BOM（U+FEFF）前缀 |
| 11-nbsp | PASS(修复后解析) | 成员间不间断空格（U+00A0）替代普通空格 |
| 12-fullwidth-colon-comma | PASS(修复后解析) | 全角冒号（U+FF1A）与全角逗号（U+FF0C） |

## 修复前穿透矩阵（对照，2026-10-14 修复前采集）

## Q2 对抗性损坏穿透矩阵（修复前防御）

| 变体 | L1/L2 后果 | 说明 |
|---|---|---|
| 00-valid | PASS(直接解析) | 合法基线（对照） |
| 01-inline-comment | BREACH(解析失败) | 行内尾注释（// 在逗号后同一行） |
| 02-block-comment | BREACH(解析失败) | 块注释插在成员之间 |
| 03-comment-in-value | PASS(直接解析) | 字符串值内部的注释——是合法内容，必须原样保留 |
| 04-cn-key | SCHEMA_OK(sections 丢失→L3 拒) | 中文键替代 sections（未知字段，Jackson 忽略 → sections=null → L3 拒） |
| 05-homoglyph-key | SCHEMA_OK(sections 丢失→L3 拒) | 希腊同形字母 omicron (U+03BF) 替换键名中的 o → L3 拒 |
| 06-fullwidth-braces | SCHEMA_OK(sections 丢失→L3 拒) | 全角大括号（U+FF5B/U+FF5D） |
| 07-fullwidth-quotes | BREACH(解析失败) | 全角双引号（U+FF02）作定界符 |
| 08-zero-width-in-key | SCHEMA_OK(sections 丢失→L3 拒) | 键名尾部零宽空格（U+200B） |
| 09-zero-width-structural | BREACH(解析失败) | 结构位置（冒号前）零宽空格 |
| 10-bom | PASS(修复后解析) | BOM（U+FEFF）前缀 |
| 11-nbsp | BREACH(解析失败) | 成员间不间断空格（U+00A0）替代普通空格 |
| 12-fullwidth-colon-comma | BREACH(解析失败) | 全角冒号（U+FF1A）与全角逗号（U+FF0C） |
