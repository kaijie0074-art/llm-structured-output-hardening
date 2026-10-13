package com.resumeai;

import com.resumeai.fidelity.AnchorExtractor;
import com.resumeai.fidelity.FidelityGate;
import com.resumeai.llm.RecordedLlmClient;
import com.resumeai.model.OptimizeResult;
import com.resumeai.model.PipelineOutcome;
import com.resumeai.model.ResumeSnapshot;
import com.resumeai.model.Section;
import com.resumeai.pipeline.FenceStripper;
import com.resumeai.pipeline.LenientRepair;
import com.resumeai.pipeline.OptimizePipeline;
import com.resumeai.pipeline.ParserChain;
import com.resumeai.pipeline.SchemaGate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 覆盖场景见 docs/test-coverage.md。
 * 语料驱动的表驱动测试是主回归面：15 个场景的期望终态逐一断言。
 */
class PipelineTest {

    private final Path corpus = Path.of("fixtures/llm-corpus");
    private final Path golden = Path.of("fixtures/golden-resumes");

    private ResumeSnapshot snapshot() {
        ResumeSnapshot s = new ResumeSnapshot();
        ResumeSnapshot.WorkExperience w = new ResumeSnapshot.WorkExperience();
        w.setCompany("某某咨询有限公司");
        w.setTitle("后端开发实习生");
        w.setDescription("参与订单系统开发，服务 200 家商户，日均处理 3000 单");
        w.setDateRange("2023.09-2024.06");
        s.getWorkExps().add(w);
        ResumeSnapshot.Education e = new ResumeSnapshot.Education();
        e.setSchool("某某大学");
        e.setDegree("电子商务专业本科在读");
        e.setDateRange("2022.09-2026.06");
        s.getEducations().add(e);
        s.getCertificates().add("计算机二级");
        return s;
    }

    // ---------- L1/L2：词法与语法层 ----------

    @Test
    void fenceStripper剥离围栏与前后缀() {
        String raw = "好的：\n```json\n{\"a\":1}\n```\n以上";
        assertEquals("{\"a\":1}", FenceStripper.strip(raw));
        assertEquals("{\"a\":1}", FenceStripper.strip("{\"a\":1}"));
    }

    @Test
    void lenientRepair修复中文引号值() {
        String in = "{\"field”: \"work_exps[0].description\"}".replace("\"work_exps[0].description\"", "“work_exps[0].description”");
        String out = LenientRepair.repair(in);
        assertTrue(out.contains("\"work_exps[0].description\""), out);
    }

    @Test
    void lenientRepair不误伤字符串内容里的中文引号() {
        // 评审意见 #2：值内部的中文引号是合法内容（引用原文），必须原样保留
        String in = "{\"rewritten\": \"他说“参与”了项目\"}";
        String out = LenientRepair.repair(in);
        assertTrue(out.contains("“参与”"), "值内部中文引号被误伤: " + out);
    }

    @Test
    void lenientRepair修复单引号与尾逗号() {
        String in = "{'a': 'x', 'b': 'y',}";
        String out = LenientRepair.repair(in);
        assertEquals("{\"a\": \"x\", \"b\": \"y\"}", out);
    }

    @Test
    void 截断JSON被识别且不硬补() {
        assertTrue(LenientRepair.looksTruncated("{\"sections\": [{\"field\": \"a\","));
        assertTrue(LenientRepair.looksTruncated("{\"sections\":"));
        assertFalse(LenientRepair.looksTruncated("{\"a\": 1}"));
    }

    // ---------- 研究 Q1 修复：语料首段提取口径 ----------

    @Test
    void firstAttempt正确提取标记式语料的首块() {
        // 标记开头：attempt-0 在首标记**之后**（旧 split 误取空串 → 假崩溃计数）
        String marker = "===ATTEMPT 0===\n{\"a\":1}\n===ATTEMPT 1===\n{\"a\":2}";
        assertEquals("{\"a\":1}", com.resumeai.Main.firstAttempt(marker));
        assertEquals("{\"a\":1}", com.resumeai.Main.firstAttempt("{\"a\":1}"));
        assertEquals("前缀\n{\"a\":1}\n后缀", com.resumeai.Main.firstAttempt("前缀\n{\"a\":1}\n后缀"));
    }

    // ---------- 研究 Q2：对抗性损坏（注释注入 / 全角定界符 / 不可见字符） ----------

    @Test
    void 字符串外行内与块注释被剥离() {
        String inline = "{\"a\":1, // 备注说明\n\"b\":2}";
        assertEquals("{\"a\":1, \n\"b\":2}", LenientRepair.repair(inline));
        String block = "{\"a\":1 /* 备注 */, \"b\":2}";
        assertEquals("{\"a\":1 , \"b\":2}", LenientRepair.repair(block));
    }

    @Test
    void 字符串内部的注释样式文本保留() {
        String url = "{\"rewritten\":\"详见 https://example.com/a//b 与 /* 重点 */\"}";
        assertEquals(url, LenientRepair.repair(url));
    }

    @Test
    void 全角结构字符归一化为ASCII() {
        String in = "\uFF5B\"sections\"\uFF1A[\uFF5B\"field\"\uFF1A\"f\"\uFF5D]\uFF0C\"score_before\"\uFF1A62\uFF5D";
        String out = LenientRepair.repair(in);
        assertEquals("{\"sections\":[{\"field\":\"f\"}],\"score_before\":62}", out);
    }

    @Test
    void 全角双引号定界符映射且串内ASCII引号是内容() {
        String in = "\uFF5B\uFF02sections\uFF02:[\uFF5B\uFF02rewritten\uFF02:\uFF02他说\"参与\"了\uFF02\uFF5D]\uFF5D";
        String out = LenientRepair.repair(in);
        assertEquals("{\"sections\":[{\"rewritten\":\"他说\\\"参与\\\"了\"}]}".replace("\\\"", "\""),
                out);
    }

    @Test
    void 结构位置零宽与NBSP删除但键内零宽保留() {
        // 结构位置（闭引号与冒号间）：删除 → 可修复
        String structural = "{\"sections\"\u200B:[],\"score_before\"\u00A0:62}";
        String expected = "{\"sections\":[],\"score_before\" :62}";
        assertEquals(expected, LenientRepair.repair(structural));
        // 键名内部：是字符串内容，词法层不碰（交 L3 拒 + 重试）
        String inKey = "{\"sections\u200B\":[]}";
        assertTrue(LenientRepair.repair(inKey).contains("sections\u200B"));
    }

    @Test
    void fenceStripper全角括号输出不再截出垃圾片段() {
        // L1 跨度起点曾落到内层 ASCII { 上，截出 {field...}（可解析的静默脏数据）
        String in = "\uFF5B\"sections\":[{\"field\":\"f\"}],\"score_after\":78\uFF5D";
        String stripped = FenceStripper.strip(in);
        assertTrue(stripped.startsWith("{\"sections\""), "截取起点错误: " + stripped);
        assertTrue(stripped.endsWith("78}"), "截取终点错误: " + stripped);
    }

    @Test
    void 基线口径修正后崩溃数为7静默脏为5() throws Exception {
        // 2026-10-14 研究 Q1：旧口径把 06/07（标记式语料）误计为崩溃；
        // 修正后 7 崩（01-05,14,15）+ 5 静默脏（06,07,09,10,11）——脏数据反而更多，
        // 即"解析成功≠数据正确"比历史记录更严重。
        java.nio.file.Path corpusDir = corpus;
        int crash = 0;
        try (var ds = java.nio.file.Files.newDirectoryStream(corpusDir, "*.txt")) {
            for (var p : ds) {
                String raw = com.resumeai.Main.firstAttempt(java.nio.file.Files.readString(p));
                try {
                    new ParserChain().parseBaseline(raw);
                } catch (Exception e) {
                    crash++;
                }
            }
        }
        assertEquals(7, crash, "修正口径后语料崩溃数");
    }

    // ---------- 研究 Q5：DRIFT_SUSPECT 阈值调优（Δ>50，去掉 after≥90 分支） ----------

    @Test
    void 漂移标记只在跳变超过50时触发() {
        // 旧规则（|Δ|>25 或 after≥90）在 19 个 benign 采样上误报 47%（Q5 数据）
        assertFalse(flagged(80, 90), "Δ=10 但 after=90：高分本身无害，不应打标");
        assertFalse(flagged(70, 95), "Δ=25 未超阈值");
        assertTrue(flagged(30, 90), "Δ=60 跳变，打标");
        assertTrue(flagged(0, 100), "Δ=100 极端跳变，打标");
        assertFalse(flagged(50, 80), "Δ=30 正常润色幅度");
    }

    private boolean flagged(int before, int after) {
        OptimizeResult r = result("润色");
        r.setScoreBefore(before);
        r.setScoreAfter(after);
        var outcome = new com.resumeai.model.PipelineOutcome("test",
                com.resumeai.model.PipelineOutcome.Status.OK, null, r, 1, 1, new java.util.ArrayList<>());
        invokeMaybeFlagDrift(outcome);
        return outcome.getLayers().contains("DRIFT_SUSPECT");
    }

    private void invokeMaybeFlagDrift(com.resumeai.model.PipelineOutcome o) {
        try {
            var m = OptimizePipeline.class.getDeclaredMethod("maybeFlagDrift",
                    com.resumeai.model.PipelineOutcome.class);
            m.setAccessible(true);
            m.invoke(new OptimizePipeline(null), o);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
    // ---------- L3：结构层 ----------

    @Test
    void schema拦截缺必填字段与越界分数() throws Exception {
        SchemaGate gate = new SchemaGate();
        com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper();
        assertNull(gate.validate(m.readTree("{\"sections\":[{\"field\":\"f\",\"original\":\"o\",\"rewritten\":\"r\"}],\"score_before\":62,\"score_after\":78}")));
        assertNotNull(gate.validate(m.readTree("{\"sections\":[{\"field\":\"f\",\"original\":\"o\"}],\"score_before\":62,\"score_after\":78}")));
        assertNotNull(gate.validate(m.readTree("{\"sections\":[{\"field\":\"f\",\"original\":\"o\",\"rewritten\":\"r\"}],\"score_before\":62,\"score_after\":250}")));
    }

    // ---------- L5：事实层 ----------

    @Test
    void 新增证书被拦截() {
        OptimizeResult r = result("负责订单模块开发，已通过 CET-6");
        List<String> v = new FidelityGate().check(snapshot(), r);
        assertTrue(v.stream().anyMatch(s -> s.startsWith("NEW_CERTIFICATE")), String.valueOf(v));
    }

    @Test
    void 新增公司被拦截() {
        OptimizeResult r = result("实习于星辰人工智能科技有限公司");
        assertTrue(new FidelityGate().check(snapshot(), r).stream()
                .anyMatch(s -> s.startsWith("NEW_COMPANY")));
    }

    @Test
    void 量级跳变量化被拦截() {
        OptimizeResult r = result("日均处理 30000 单");
        assertTrue(new FidelityGate().check(snapshot(), r).stream()
                .anyMatch(s -> s.startsWith("NEW_METRIC") || s.startsWith("METRIC_INFLATED")));
    }

    @Test
    void 同源量化数字放行() {
        OptimizeResult r = result("服务 200 家商户，日均处理 3000 单");
        assertTrue(new FidelityGate().check(snapshot(), r).isEmpty());
    }

    @Test
    void field越界被识别为结构违规() {
        OptimizeResult r = result("负责订单模块开发");
        r.getSections().get(0).setField("work_exps[9].description");
        assertTrue(new FidelityGate().check(snapshot(), r).stream()
                .anyMatch(s -> s.startsWith("FIELD_TARGET_INVALID")));
    }

    @Test
    void 锚点抽取年份不计量化() {
        var dateNums = AnchorExtractor.metrics("2023.09-2024.06");
        assertFalse(dateNums.contains(2023L) && dateNums.contains(2024L),
                "年份不得计为量化锚点: " + dateNums);
        assertTrue(AnchorExtractor.metrics("日均 3000 单").contains(3000L));
        assertTrue(AnchorExtractor.metrics("日均 3 万单").contains(30000L));
    }

    // ---------- 全管线：语料表驱动 ----------

    @ParameterizedTest
    @CsvSource({
            "01-fence-wrapped, OK",
            "02-chinese-quotes, OK",
            "03-trailing-comma, OK",
            "04-single-quote-keys, OK",
            "05-leading-prose, OK",
            "06-path-index-error, OK",
            "07-field-renamed, OK",
            "08-score-drift, OK",
            "09-fabricated-certificate, REJECTED_FABRICATION",
            "10-fabricated-company, REJECTED_FABRICATION",
            "11-fabricated-metric, REJECTED_FABRICATION",
            "12-clean, OK",
            "13-clean, OK",
            "14-retry-recoverable, OK",
            "15-truncated-unrecoverable, FAILED"
    })
    void 语料场景终态(String scenario, String expected) {
        OptimizePipeline pipeline = new OptimizePipeline(new RecordedLlmClient(corpus));
        PipelineOutcome o = pipeline.run(scenario, snapshot());
        assertEquals(expected, o.getStatus().name(), o.summarize());
    }

    // ---------- 重试纪律 ----------

    @Test
    void 重试硬上限恰好1次() {
        OptimizePipeline pipeline = new OptimizePipeline(new RecordedLlmClient(corpus));
        PipelineOutcome o = pipeline.run("15-truncated-unrecoverable", snapshot());
        assertEquals(PipelineOutcome.Status.FAILED, o.getStatus());
        assertEquals(2, o.getLlmAttempts(), "初始+重试恰好 2 次调用，禁止无上限重试");
    }

    @Test
    void 捏造违规不触发重试() {
        OptimizePipeline pipeline = new OptimizePipeline(new RecordedLlmClient(corpus));
        PipelineOutcome o = pipeline.run("09-fabricated-certificate", snapshot());
        assertEquals(1, o.getLlmAttempts(), "捏造类违规立即拒绝，重试只会换个编法");
        assertFalse(o.getLayers().contains("RETRIED"));
    }

    @Test
    void 干净输出不重试() {
        OptimizePipeline pipeline = new OptimizePipeline(new RecordedLlmClient(corpus));
        PipelineOutcome o = pipeline.run("12-clean", snapshot());
        assertEquals(PipelineOutcome.Status.OK, o.getStatus());
        assertEquals(1, o.getLlmAttempts());
    }

    @Test
    void 漂移样本被打标但不拦截() {
        OptimizePipeline pipeline = new OptimizePipeline(new RecordedLlmClient(corpus));
        PipelineOutcome o = pipeline.run("08-score-drift", snapshot());
        assertEquals(PipelineOutcome.Status.OK, o.getStatus());
        assertTrue(o.getLayers().contains("DRIFT_SUSPECT"));
    }

    // ---------- 基线复现 ----------

    @Test
    void 基线直接解析在畸形语料上崩溃() throws Exception {
        ParserChain chain = new ParserChain();
        String raw = java.nio.file.Files.readString(corpus.resolve("01-fence-wrapped.txt"));
        assertThrows(Exception.class, () -> chain.parseBaseline(raw.split("===ATTEMPT")[0].trim()));
    }

    private OptimizeResult result(String rewritten) {
        OptimizeResult r = new OptimizeResult();
        Section s = new Section();
        s.setField("work_exps[0].description");
        s.setOriginal("参与订单系统开发");
        s.setRewritten(rewritten);
        r.getSections().add(s);
        r.setScoreBefore(62);
        r.setScoreAfter(78);
        return r;
    }
}
