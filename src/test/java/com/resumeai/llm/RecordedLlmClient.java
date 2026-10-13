package com.resumeai.llm;

import java.io.IOException;
import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 历史故障样本回放客户端（测试源集；fixtures/llm-corpus 为程序合成的故障输出样本库，
 * 用于防御层的确定性回归——对喂入的历史故障形态，防御层行为必须稳定）。
 * 多次尝试用行 "===ATTEMPT n===" 分隔（n 从 0 开始）；attempt 超出时复用最后一块
 * （保证"截断不可恢复"场景两次都拿到坏输出，从而走到 FAILED 上限）。
 */
public class RecordedLlmClient implements LlmClient {

    private final Path corpusDir;

    public RecordedLlmClient(Path corpusDir) {
        this.corpusDir = corpusDir;
    }

    @Override
    public String complete(String scenarioId, String requirement, int attempt, String feedback) {
        try {
            Path file = corpusDir.resolve(scenarioId + ".txt");
            if (!Files.exists(file)) {
                throw new IllegalArgumentException("语料不存在: " + file);
            }
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            // 标记语义：行 "===ATTEMPT n===" 宣告**接下来**是第 n 块（预告式，非结束式）。
            // 无标记文件 = 单块语料，attempt 0/1 均返回全文（同一输出重放）。
            List<String> blocks = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            for (String line : lines) {
                if (line.startsWith("===ATTEMPT")) {
                    blocks.add(cur.toString());
                    cur = new StringBuilder();
                } else {
                    cur.append(line).append('\n');
                }
            }
            blocks.add(cur.toString());

            if (lines.stream().noneMatch(l -> l.startsWith("===ATTEMPT"))) {
                return blocks.get(0).trim();
            }
            // 有效块从 blocks[1] 开始（blocks[0] 是首标记前的空段）
            List<String> valid = blocks.subList(1, blocks.size());
            int idx = Math.min(attempt, valid.size() - 1); // 超界复用最后块
            return valid.get(idx).trim();
        } catch (IOException e) {
            throw new UncheckedIoException(e);
        }
    }


    private static class UncheckedIoException extends RuntimeException {
        UncheckedIoException(IOException cause) { super(cause); }
    }
}
