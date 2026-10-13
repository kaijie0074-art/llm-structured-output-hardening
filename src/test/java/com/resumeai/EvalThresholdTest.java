package com.resumeai;

import com.resumeai.eval.EvalRunner;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** 黄金集评估阈值：这是"改 prompt 前后各跑一遍"的回归闸门（L6 层）。 */
class EvalThresholdTest {

    @Test
    void 黄金集三指标达标() throws Exception {
        // 确定性回归：对历史故障样本库（fixtures/llm-corpus，程序合成的故障输出形态），
        // 防御层行为必须稳定；真实 LLM 的在线评估用 Main.eval（需凭证）
        EvalRunner.EvalReport r = new EvalRunner().run(Path.of("fixtures/golden-resumes"),
                new com.resumeai.llm.RecordedLlmClient(Path.of("fixtures/llm-corpus")));

        assertEquals(30, r.total(), "黄金集固定 30 份");
        assertEquals(0, r.failed(), "结构失败必须为 0，实际: " + r.summaries());
        assertEquals(r.fabricationTotal(), r.fabricationCaught(), "捏造语料必须 100% 拦截");
        assertTrue(r.cleanScoreStd() < 5.0, "clean 分数漂移标准差 < 5，实际 " + r.cleanScoreStd());
        assertTrue(r.byStratum().containsKey("college") && r.byStratum().containsKey("associate"),
                "分层维度必须同时覆盖 college/associate（评审意见 #5）");
    }
}
