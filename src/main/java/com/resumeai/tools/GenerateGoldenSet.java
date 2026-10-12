package com.resumeai.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

/**
 * 黄金集生成器：确定性生成 30 份合成简历（固定种子），无任何真实 PII。
 *
 * 语料分配（scenario 字段）：
 * - 18 份 clean（12-clean / 13-clean 轮换）
 * - 01~05 各 1 份（词法/语法故障）
 * - 06/07/08/09/10/11 各 1 份（结构/漂移/捏造）
 * - 14 一份（截断重试）
 *
 * 事实池约束（与 FidelityGate 联动的关键约定）：
 * - 证书池不含 CET-6/CPA/PMP（那是 09 场景的捏造词，绝不能出现在原文）
 * - 公司池不含"星辰人工智能科技有限公司"（10 场景捏造词）
 * - 量化数字池最大 3000（11 场景的 30000 必然是新增/跳变）
 */
public final class GenerateGoldenSet {

    private static final List<String> COMPANIES = List.of(
            "某某咨询有限公司", "某某贸易有限公司", "某某传媒有限公司", "某某信息技术服务有限公司");
    private static final List<String> SCHOOLS = List.of(
            "某某大学", "某某学院", "某某职业技术学院");
    private static final List<String> TITLES = List.of(
            "后端开发实习生", "运营实习生", "数据分析实习生", "产品助理");
    private static final List<String> DESCRIPTIONS = List.of(
            "参与订单系统开发，日均处理 3000 单",
            "参与社群运营，维护 200 个用户群，群活跃率 65%",
            "协助数据报表搭建，覆盖 150 项业务指标",
            "参与小程序迭代开发，服务 800 名种子用户");
    private static final List<String> CERTS = List.of("计算机二级", "普通话二甲", "驾驶证");

    private static final String[] SCENARIOS = {
            // 18 clean
            "12-clean", "13-clean", "12-clean", "13-clean", "12-clean", "13-clean",
            "12-clean", "13-clean", "12-clean", "13-clean", "12-clean", "13-clean",
            "12-clean", "13-clean", "12-clean", "13-clean", "12-clean", "13-clean",
            // 词法语法 5
            "01-fence-wrapped", "02-chinese-quotes", "03-trailing-comma",
            "04-single-quote-keys", "05-leading-prose",
            // 结构/漂移/捏造 6
            "06-path-index-error", "07-field-renamed", "08-score-drift",
            "09-fabricated-certificate", "10-fabricated-company", "11-fabricated-metric",
            // 截断重试 1
            "14-retry-recoverable"
    };

    public static void main(String[] args) throws Exception {
        Path outDir = Path.of(args.length > 0 ? args[0] : "fixtures/golden-resumes");
        Files.createDirectories(outDir);
        ObjectMapper mapper = new ObjectMapper();
        Random random = new Random(20261013L);

        for (int i = 0; i < SCENARIOS.length; i++) {
            ObjectNode root = mapper.createObjectNode();
            root.put("id", String.format("resume-%03d", i + 1));
            // 评审意见 #5：分层 stratum（本科/大专），eval 报告分组呈现
            root.put("stratum", i % 3 == 0 ? "college" : (i % 3 == 1 ? "associate" : "college"));
            root.put("scenario", SCENARIOS[i]);

            ArrayNode workExps = root.putArray("work_exps");
            ObjectNode work = workExps.addObject();
            work.put("company", COMPANIES.get(random.nextInt(COMPANIES.size())));
            work.put("title", TITLES.get(random.nextInt(TITLES.size())));
            // 固定第 0 段 description 与语料 original/rewritten 数字同源（3000/200），
            // 满足 FidelityGate 的"量化口径同源"约定；多样化由第 1 段承担
            work.put("description", "参与订单系统开发，服务 200 家商户，日均处理 3000 单");
            ObjectNode work2 = workExps.addObject();
            work2.put("company", COMPANIES.get(random.nextInt(COMPANIES.size())));
            work2.put("title", TITLES.get(random.nextInt(TITLES.size())));
            work2.put("description", DESCRIPTIONS.get(random.nextInt(DESCRIPTIONS.size())));
            work2.put("date_range", "2024.07-2025.01");

            ArrayNode educations = root.putArray("educations");
            ObjectNode edu = educations.addObject();
            edu.put("school", SCHOOLS.get(random.nextInt(SCHOOLS.size())));
            edu.put("degree", "电子商务专业本科在读");
            edu.put("date_range", "2022.09-2026.06");

            ArrayNode certs = root.putArray("certificates");
            certs.add(CERTS.get(random.nextInt(CERTS.size())));
            if (random.nextBoolean()) certs.add(CERTS.get(random.nextInt(CERTS.size())));

            mapper.writerWithDefaultPrettyPrinter().writeValue(
                    outDir.resolve(String.format("resume-%03d.json", i + 1)).toFile(), root);
        }
        System.out.println("生成 " + SCENARIOS.length + " 份 → " + outDir.toAbsolutePath());
    }
}
