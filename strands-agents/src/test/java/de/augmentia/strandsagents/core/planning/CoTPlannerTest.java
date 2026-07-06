package de.augmentia.strandsagents.core.planning;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import de.augmentia.strandsagents.tools.builtin.BaseToolNames;
import org.junit.jupiter.api.Test;

/**
 * Tests pure (static) methods of CoTPlanner: parseSteps, formatToolNames, resolveTemplate.
 * The LLM-dependent methods (createPlan, revise, isComplete, executeStep) are tested via integration.
 */
class CoTPlannerTest {

    @Test
    void parseSteps_emptyJson_returnsEmpty() {
        var steps = callParseSteps("[]");
        assertThat(steps).isEmpty();
    }

    @Test
    void parseSteps_invalidJson_returnsEmpty() {
        var steps = callParseSteps("not json");
        assertThat(steps).isEmpty();
    }

    @Test
    void parseSteps_singleStep_minimal() {
        var json = """
            [{"id":"s1","description":"calc","toolName":"calc","argumentsTemplate":"{}"}]
            """;
        var steps = callParseSteps(json);
        assertThat(steps).hasSize(1);
        assertThat(steps.get(0).id()).isEqualTo("s1");
        assertThat(steps.get(0).description()).isEqualTo("calc");
        assertThat(steps.get(0).toolName()).isEqualTo("calc");
        assertThat(steps.get(0).argumentsTemplate()).isEqualTo("{}");
        assertThat(steps.get(0).dependsOn()).isEmpty();
        assertThat(steps.get(0).optional()).isFalse();
    }

    @Test
    void parseSteps_singleStep_withDependsOnAndOptional() {
        var json = """
            [{"id":"s1","description":"calc","toolName":"calc","argumentsTemplate":"{}",
              "dependsOn":["s0"],"optional":true}]
            """;
        var steps = callParseSteps(json);
        assertThat(steps).hasSize(1);
        assertThat(steps.get(0).dependsOn()).containsExactly("s0");
        assertThat(steps.get(0).optional()).isTrue();
    }

    @Test
    void parseSteps_multipleSteps() {
        var json = """
            [{"id":"s1","description":"Search","toolName":"web_search","argumentsTemplate":"{\\"q\\":\\"test\\""},
             {"id":"s2","description":"Calc","toolName":"calc","argumentsTemplate":"{}","dependsOn":["s1"]}]
            """;
        var steps = callParseSteps(json);
        assertThat(steps).hasSize(2);
        assertThat(steps.get(0).toolName()).isEqualTo(BaseToolNames.WEB_SEARCH);
        assertThat(steps.get(1).toolName()).isEqualTo("calc");
        assertThat(steps.get(1).dependsOn()).containsExactly("s1");
    }

    @Test
    void parseSteps_fencedJsonBlock() {
        var json = """
            ```json
            [{"id":"s1","description":"step1","toolName":"none","argumentsTemplate":"do it"}]
            ```
            """;
        var steps = callParseSteps(json);
        assertThat(steps).hasSize(1);
        assertThat(steps.get(0).description()).isEqualTo("step1");
    }

    @Test
    void parseSteps_fencedNoLang() {
        var json = """
            ```
            [{"id":"s1","description":"a","toolName":"none","argumentsTemplate":"b"}]
            ```
            """;
        var steps = callParseSteps(json);
        assertThat(steps).hasSize(1);
        assertThat(steps.get(0).id()).isEqualTo("s1");
    }

    @Test
    void parseSteps_missingFields_getDefaults() {
        var json = """
            [{}]
            """;
        var steps = callParseSteps(json);
        assertThat(steps).hasSize(1);
        assertThat(steps.get(0).id()).isEqualTo("step-1");
        assertThat(steps.get(0).description()).isEmpty();
        assertThat(steps.get(0).toolName()).isEqualTo("none");
        assertThat(steps.get(0).argumentsTemplate()).isEmpty();
    }

    @Test
    void parseSteps_nullItem_skipped() {
        var json = "[null, {\"id\":\"s1\"}]";
        var steps = callParseSteps(json);
        assertThat(steps).hasSize(1);
    }

    @Test
    void parseSteps_nonMapItem_skipped() {
        var json = "[\"string\", {\"id\":\"s1\"}]";
        var steps = callParseSteps(json);
        assertThat(steps).hasSize(1);
    }

    @Test
    void formatToolNames_returnsJoined() {
        var names = List.of("calc", BaseToolNames.WEB_SEARCH, "file_read");
        assertThat(callFormatToolNames(names)).isEqualTo("calc, web_search, file_read");
    }

    @Test
    void formatToolNames_null_returnsNone() {
        assertThat(callFormatToolNames(null)).isEqualTo("none (only 'none' allowed)");
    }

    @Test
    void formatToolNames_empty_returnsNone() {
        assertThat(callFormatToolNames(List.of())).isEqualTo("none (only 'none' allowed)");
    }

    @Test
    void resolveTemplate_noPlaceholders_returnsOriginal() {
        assertThat(callResolveTemplate("hello", Map.<String, Object>of())).isEqualTo("hello");
    }

    @Test
    void resolveTemplate_replacesPlaceholders() {
        var ctx = Map.<String, Object>of("name", "Torsten", "value", "42");
        assertThat(callResolveTemplate("${name}: ${value}", ctx)).isEqualTo("Torsten: 42");
    }

    @Test
    void resolveTemplate_unknownPlaceholder_keepsOriginal() {
        assertThat(callResolveTemplate("${missing}", Map.<String, Object>of("other", "val"))).isEqualTo("${missing}");
    }

    @Test
    void resolveTemplate_null_returnsEmpty() {
        assertThat(callResolveTemplate(null, Map.of())).isEmpty();
    }

    // --- helpers to access private static methods via reflection ---

    private static List<Step> callParseSteps(String json) {
        try {
            var m = CoTPlanner.class.getDeclaredMethod("parseSteps", String.class);
            m.setAccessible(true);
            // create instance with null model since parseSteps is an instance method
            var planner = new CoTPlanner(null);
            @SuppressWarnings("unchecked")
            var result = (List<Step>) m.invoke(planner, json);
            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String callFormatToolNames(List<String> names) {
        try {
            var m = CoTPlanner.class.getDeclaredMethod("formatToolNames", List.class);
            m.setAccessible(true);
            return (String) m.invoke(new CoTPlanner(null), names);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String callResolveTemplate(String template, Map<String, Object> context) {
        try {
            var m = CoTPlanner.class.getDeclaredMethod("resolveTemplate", String.class, Map.class);
            m.setAccessible(true);
            return (String) m.invoke(null, template, context);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
