## Q3/Q4 同一请求 16 次采样的非确定性（生产解析路径）

### temp=0.0（8 次）

| run | 结构哈希 | 修复规则 | sections | before | after | Δ | fidelity | 自然损坏 |
|---|---|---|---|---|---|---|---|---|
| 1 | f=[work_exps[0].description, work_exps[1].description],b=70,a=80 |  | 2 | 70 | 80 | 10 | ok | 围栏 |
| 2 | f=[work_exps[0].description],b=0,a=10 | 无需 | 1 | 0 | 10 | 10 | ok | 无 |
| 3 | f=[work_exps[0].description, work_exps[1].description],b=70,a=80 |  | 2 | 70 | 80 | 10 | ok | 围栏 |
| 4 | f=[work_exps[0].description],b=100,a=100 | QUOTED_KEY | 1 | 100 | 100 | 0 | ok | 围栏+中文引号键+全角标点 |
| 5 | f=[work_exps[0].description, work_exps[1].description],b=50,a=70 |  | 2 | 50 | 70 | 20 | ok | 围栏 |
| 6 | f=[work_exps[0].description],b=50,a=80 | 无需 | 1 | 50 | 80 | 30 | ok | 无 |
| 7 | f=[work_exps[0].description],b=50,a=80 |  | 1 | 50 | 80 | 30 | ok | 围栏 |
| 8 | f=[work_exps[0].description],b=0,a=70 |  | 1 | 0 | 70 | 70 | ok | 围栏 |

- 唯一原文变体: 8/8
- 唯一结构哈希: 6/8 {f=[work_exps[0].description, work_exps[1].description],b=50,a=70=1, f=[work_exps[0].description, work_exps[1].description],b=70,a=80=2, f=[work_exps[0].description],b=0,a=10=1, f=[work_exps[0].description],b=0,a=70=1, f=[work_exps[0].description],b=100,a=100=1, f=[work_exps[0].description],b=50,a=80=2}
- score_before 值集: [70, 0, 70, 100, 50, 50, 50, 0]（极差 100）
- score_after 值集: [80, 10, 80, 100, 70, 80, 80, 70]（极差 90）
- 自然损坏形态分布: {围栏=5, 围栏+中文引号键+全角标点=1, 无=2}

### temp=0.2（生产配置）（8 次）

| run | 结构哈希 | 修复规则 | sections | before | after | Δ | fidelity | 自然损坏 |
|---|---|---|---|---|---|---|---|---|
| 1 | f=[work_exps[0].description],b=100,a=100 | 无需 | 1 | 100 | 100 | 0 | ok | 无 |
| 2 | f=[work_exps[0].description, work_exps[1].description],b=50,a=75 |  | 2 | 50 | 75 | 25 | ok | 围栏 |
| 3 | f=[work_exps[0].description, work_exps[1].description],b=0,a=100 |  | 2 | 0 | 100 | 100 | ok | 围栏 |
| 4 | f=[work_exps[0].description, work_exps[1].description],b=0,a=50 |  | 2 | 0 | 50 | 50 | ok | 围栏 |
| 5 | f=[work_exps[0].description, work_exps[1].description],b=70,a=80 |  | 2 | 70 | 80 | 10 | ok | 围栏 |
| 6 | f=[work_exps[0].description, work_exps[1].description, educations[0].degree],b=60,a=80 |  | 3 | 60 | 80 | 20 | ok | 围栏 |
| 7 | f=[work_exps[0].description, work_exps[1].description],b=70,a=85 |  | 2 | 70 | 85 | 15 | ok | 围栏 |
| 8 | f=[work_exps[0].description],b=60,a=80 |  | 1 | 60 | 80 | 20 | ok | 围栏 |

- 唯一原文变体: 8/8
- 唯一结构哈希: 8/8 {f=[work_exps[0].description, work_exps[1].description, educations[0].degree],b=60,a=80=1, f=[work_exps[0].description, work_exps[1].description],b=0,a=100=1, f=[work_exps[0].description, work_exps[1].description],b=0,a=50=1, f=[work_exps[0].description, work_exps[1].description],b=50,a=75=1, f=[work_exps[0].description, work_exps[1].description],b=70,a=80=1, f=[work_exps[0].description, work_exps[1].description],b=70,a=85=1, f=[work_exps[0].description],b=100,a=100=1, f=[work_exps[0].description],b=60,a=80=1}
- score_before 值集: [100, 50, 0, 0, 70, 60, 70, 60]（极差 100）
- score_after 值集: [100, 75, 100, 50, 80, 80, 85, 80]（极差 50）
- 自然损坏形态分布: {围栏=7, 无=1}

