package com.resumeai.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.io.InputStream;
import java.util.Set;

/**
 * L3 结构层防御：JSON Schema 硬校验（draft 2020-12）。
 *
 * 为什么解析成功还要 schema：Jackson 反序列化只保证"读到 Java 对象"，
 * 必填字段缺失（如模型把 rewritten 改名成 re_write 后值为 null）、
 * 数组退化成对象、数值越界（score=250）这类问题只有 schema 能稳定拦住。
 *
 * 使用 com.networknt:json-schema-validator（见 docs/open-source-references.md）。
 */
public final class SchemaGate {

    private final JsonSchema schema;

    public SchemaGate() {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        try (InputStream in = SchemaGate.class.getResourceAsStream("/schema/optimize-result.schema.json")) {
            ObjectMapper m = new ObjectMapper();
            JsonNode schemaNode = m.readTree(in);
            this.schema = factory.getSchema(schemaNode);
        } catch (Exception e) {
            throw new IllegalStateException("无法加载 optimize-result.schema.json", e);
        }
    }

    /** @return null=通过；否则返回人可读的首条违规信息（日志带全量）。 */
    public String validate(JsonNode node) {
        Set<ValidationMessage> errors = schema.validate(node);
        if (errors.isEmpty()) {
            return null;
        }
        return errors.iterator().next().getMessage();
    }
}
