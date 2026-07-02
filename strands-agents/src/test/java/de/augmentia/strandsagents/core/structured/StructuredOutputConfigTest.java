package de.augmentia.strandsagents.core.structured;

import static org.assertj.core.api.Assertions.assertThat;

import de.augmentia.strandsagents.model.structured.StructuredOutputConfig;
import de.augmentia.strandsagents.model.structured.StructuredOutputMode;
import org.junit.jupiter.api.Test;

class StructuredOutputConfigTest {

    // --- Mode ---

    @Test
    void modeValues() {
        assertThat(StructuredOutputMode.values()).containsExactly(
            StructuredOutputMode.STATIC, StructuredOutputMode.DYNAMIC);
    }

    // --- staticModel ---

    @Test
    void staticModelWithClass() {
        var config = StructuredOutputConfig.staticModel(TestRecord.class);
        assertThat(config.mode()).isEqualTo(StructuredOutputMode.STATIC);
        assertThat(config.outputClass()).isEqualTo(TestRecord.class);
        assertThat(config.jsonSchema()).isNull();
        assertThat(config.forcePrompt()).isEqualTo(
            "You must format the previous response as structured output.");
    }

    @Test
    void staticModelWithCustomForcePrompt() {
        var config = StructuredOutputConfig.staticModel(TestRecord.class, "Force JSON!");
        assertThat(config.forcePrompt()).isEqualTo("Force JSON!");
    }

    @Test
    void staticModelIsEnabledWhenClassPresent() {
        var config = StructuredOutputConfig.staticModel(TestRecord.class);
        assertThat(config.isEnabled()).isTrue();
    }

    @Test
    void staticModelIsDisabledWhenClassNull() {
        var config = new StructuredOutputConfig(StructuredOutputMode.STATIC, null, null, "prompt");
        assertThat(config.isEnabled()).isFalse();
    }

    // --- dynamicSchema ---

    @Test
    void dynamicSchema() {
        var schema = "{\"type\": \"object\", \"properties\": {}}";
        var config = StructuredOutputConfig.dynamicSchema(schema);
        assertThat(config.mode()).isEqualTo(StructuredOutputMode.DYNAMIC);
        assertThat(config.outputClass()).isNull();
        assertThat(config.jsonSchema()).isEqualTo(schema);
    }

    @Test
    void dynamicSchemaWithCustomForcePrompt() {
        var config = StructuredOutputConfig.dynamicSchema("{}", "Custom!");
        assertThat(config.forcePrompt()).isEqualTo("Custom!");
    }

    @Test
    void dynamicSchemaIsEnabledWhenSchemaPresent() {
        var config = StructuredOutputConfig.dynamicSchema("{\"type\": \"object\"}");
        assertThat(config.isEnabled()).isTrue();
    }

    @Test
    void dynamicSchemaIsDisabledWhenBlank() {
        var config = StructuredOutputConfig.dynamicSchema("");
        assertThat(config.isEnabled()).isFalse();
    }

    @Test
    void dynamicSchemaIsDisabledWhenNullSchema() {
        var config = new StructuredOutputConfig(StructuredOutputMode.DYNAMIC, null, null, "prompt");
        assertThat(config.isEnabled()).isFalse();
    }

    // --- effectiveSchema ---

    @Test
    void effectiveSchemaForDynamicReturnsRawSchema() {
        var schema = "{\"type\": \"object\"}";
        var config = StructuredOutputConfig.dynamicSchema(schema);
        assertThat(config.effectiveSchema()).isEqualTo(schema);
    }

    @Test
    void effectiveSchemaForStaticGeneratesJsonSchema() {
        var config = StructuredOutputConfig.staticModel(SimpleRecord.class);
        var result = config.effectiveSchema();

        assertThat(result).contains("\"title\": \"SimpleRecord\"");
        assertThat(result).contains("\"type\": \"object\"");
        assertThat(result).contains("\"name\"");
        assertThat(result).contains("\"value\"");
        assertThat(result).contains("\"required\"");
    }

    @Test
    void effectiveSchemaForStaticWithNullClassReturnsNull() {
        var config = new StructuredOutputConfig(StructuredOutputMode.STATIC, null, null, "prompt");
        assertThat(config.effectiveSchema()).isNull();
    }

    @Test
    void effectiveSchemaForNestedRecords() {
        var config = StructuredOutputConfig.staticModel(NestedRecord.class);
        var result = config.effectiveSchema();
        assertThat(result).contains("\"inner\"");
        assertThat(result).contains("\"label\"");
        assertThat(result).contains("\"NestedRecord\"");
    }

    @Test
    void effectiveSchemaIncludesStringType() {
        var config = StructuredOutputConfig.staticModel(SimpleRecord.class);
        var result = config.effectiveSchema();
        assertThat(result).contains("\"type\": \"string\"");
    }

    @Test
    void effectiveSchemaIncludesIntegerType() {
        var config = StructuredOutputConfig.staticModel(SimpleRecord.class);
        var result = config.effectiveSchema();
        assertThat(result).contains("\"type\": \"integer\"");
    }

    @Test
    void effectiveSchemaIncludesBooleanType() {
        var config = StructuredOutputConfig.staticModel(BooleanRecord.class);
        var result = config.effectiveSchema();
        assertThat(result).contains("\"type\": \"boolean\"");
    }

    @Test
    void effectiveSchemaIsValidJson() {
        var config = StructuredOutputConfig.staticModel(SimpleRecord.class);
        var result = config.effectiveSchema();
        assertThat(result).startsWith("{").endsWith("}");
    }

    // --- types ---

    @Test
    void mapJavaTypeInteger() {
        assertThat(StructuredOutputConfig.staticModel(IntegerRecord.class).effectiveSchema())
            .contains("\"type\": \"integer\"");
    }

    @Test
    void mapJavaTypeDouble() {
        assertThat(StructuredOutputConfig.staticModel(DoubleRecord.class).effectiveSchema())
            .contains("\"type\": \"number\"");
    }

    @Test
    void mapJavaTypeLong() {
        assertThat(StructuredOutputConfig.staticModel(LongRecord.class).effectiveSchema())
            .contains("\"type\": \"integer\"");
    }

    @Test
    void mapJavaTypeBoolean() {
        assertThat(StructuredOutputConfig.staticModel(BooleanRecord.class).effectiveSchema())
            .contains("\"type\": \"boolean\"");
    }

    // --- test records for schema generation ---

    private record SimpleRecord(String name, int value) {}
    private record InnerRecord(String label) {}
    private record NestedRecord(String id, InnerRecord inner) {}
    private record BooleanRecord(boolean active) {}
    private record IntegerRecord(int count) {}
    private record DoubleRecord(double price) {}
    private record LongRecord(long id) {}
    private record TestRecord(String field) {}
}
