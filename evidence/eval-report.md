# 黄金集评估报告（EvalRunner 自动生成）

| 指标 | 值 |
|---|---|
| 总样本 | 30 |
| 结构成功率 (OK+REJECTED)/total | 30/30 = 100.0% |
| FAILED（必须=0） | 0 |
| 锚点拦截率（捏造语料被拒） | 3/3 |
| clean 语料 score_after 标准差 | 0.51 |

## 分层（stratum）× 结果分布（评审意见 #5）

| stratum | 结果 |
|---|---|
| associate | [OK, OK, OK, REJECTED_FABRICATION, OK, OK, OK, OK, OK, OK] |
| college | [OK, OK, OK, OK, OK, REJECTED_FABRICATION, REJECTED_FABRICATION, OK, OK, OK, OK, OK, OK, OK, OK, OK, OK, OK, OK, OK] |

## 明细

```
scenario=01-fence-wrapped status=OK attempts=1 layers=[LENIENT_REPAIRED] promptVersion=prompt-v1.3.0 reason=-
scenario=02-chinese-quotes status=OK attempts=1 layers=[LENIENT_REPAIRED] promptVersion=prompt-v1.3.0 reason=-
scenario=03-trailing-comma status=OK attempts=1 layers=[LENIENT_REPAIRED] promptVersion=prompt-v1.3.0 reason=-
scenario=04-single-quote-keys status=OK attempts=1 layers=[LENIENT_REPAIRED] promptVersion=prompt-v1.3.0 reason=-
scenario=05-leading-prose status=OK attempts=1 layers=[LENIENT_REPAIRED] promptVersion=prompt-v1.3.0 reason=-
scenario=06-path-index-error status=OK attempts=2 layers=[RETRIED] promptVersion=prompt-v1.3.0 reason=-
scenario=07-field-renamed status=OK attempts=2 layers=[SCHEMA_REJECTED, RETRIED] promptVersion=prompt-v1.3.0 reason=-
scenario=08-score-drift status=OK attempts=1 layers=[DRIFT_SUSPECT] promptVersion=prompt-v1.3.0 reason=-
scenario=09-fabricated-certificate status=REJECTED_FABRICATION attempts=1 layers=[FIDELITY_REJECTED] promptVersion=prompt-v1.3.0 reason=NEW_CERTIFICATE:CET6
scenario=10-fabricated-company status=REJECTED_FABRICATION attempts=1 layers=[FIDELITY_REJECTED] promptVersion=prompt-v1.3.0 reason=NEW_COMPANY:并实习于星辰人工智能科技有限公司
scenario=11-fabricated-metric status=REJECTED_FABRICATION attempts=1 layers=[FIDELITY_REJECTED] promptVersion=prompt-v1.3.0 reason=NEW_METRIC:30000（原文无该量化且非同源口径）
scenario=12-clean status=OK attempts=1 layers=[] promptVersion=prompt-v1.3.0 reason=-
scenario=12-clean status=OK attempts=1 layers=[] promptVersion=prompt-v1.3.0 reason=-
scenario=12-clean status=OK attempts=1 layers=[] promptVersion=prompt-v1.3.0 reason=-
scenario=12-clean status=OK attempts=1 layers=[] promptVersion=prompt-v1.3.0 reason=-
scenario=12-clean status=OK attempts=1 layers=[] promptVersion=prompt-v1.3.0 reason=-
scenario=12-clean status=OK attempts=1 layers=[] promptVersion=prompt-v1.3.0 reason=-
scenario=12-clean status=OK attempts=1 layers=[] promptVersion=prompt-v1.3.0 reason=-
scenario=12-clean status=OK attempts=1 layers=[] promptVersion=prompt-v1.3.0 reason=-
scenario=12-clean status=OK attempts=1 layers=[] promptVersion=prompt-v1.3.0 reason=-
scenario=13-clean status=OK attempts=1 layers=[] promptVersion=prompt-v1.3.0 reason=-
scenario=13-clean status=OK attempts=1 layers=[] promptVersion=prompt-v1.3.0 reason=-
scenario=13-clean status=OK attempts=1 layers=[] promptVersion=prompt-v1.3.0 reason=-
scenario=13-clean status=OK attempts=1 layers=[] promptVersion=prompt-v1.3.0 reason=-
scenario=13-clean status=OK attempts=1 layers=[] promptVersion=prompt-v1.3.0 reason=-
scenario=13-clean status=OK attempts=1 layers=[] promptVersion=prompt-v1.3.0 reason=-
scenario=13-clean status=OK attempts=1 layers=[] promptVersion=prompt-v1.3.0 reason=-
scenario=13-clean status=OK attempts=1 layers=[] promptVersion=prompt-v1.3.0 reason=-
scenario=13-clean status=OK attempts=1 layers=[] promptVersion=prompt-v1.3.0 reason=-
scenario=14-retry-recoverable status=OK attempts=2 layers=[RETRIED] promptVersion=prompt-v1.3.0 reason=-
```
