package de.augmentia.strandsagents.quarkus;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Regression test: ensures every REST controller delegates to the
 * correct agents/services for each endpoint.
 *
 * Known bug from a code review:
 * A controller had two different agents injected via @Qualifier
 * (agenticChatAgent, sharedStateAgent), but almost all methods
 * incorrectly always called agenticChatAgent — regardless of whether
 * shared_state, tool_based_generative_ui, human_in_the_loop or
 * agentic_generative_ui was invoked. The path variable {agentId}
 * in /sse/{agentId} was also ignored.
 *
 * This test prevents this anti-pattern from being reintroduced.
 */
class AgentAssignmentTest {

    private static final String SRC_MAIN =
        "src/main/java/de/augmentia/strandsagents/quarkus";

    /**
     * Verifies: every endpoint in ChatResource delegates to AgentService.
     */
    @Test
    void chatResourceAllEndpointsUseAgentService() throws IOException {
        var source = readSource("resources/ChatResource.java");
        var methods = parseEndpointMethods(source);
            assertFalse(methods.isEmpty(), "No endpoint methods found in ChatResource");
        for (var m : methods) {
            assertTrue(m.body().contains("agentService"),
                "ChatResource." + m.name() + "() does not use 'agentService'."
                    + " Possible bug: wrong agent/service assigned?");
        }
    }

    @Test
    void toolResourceAllEndpointsUseAgentService() throws IOException {
        var source = readSource("resources/ToolResource.java");
        var methods = parseEndpointMethods(source);
        assertFalse(methods.isEmpty(), "No endpoint methods found in ToolResource");
        for (var m : methods) {
            assertTrue(m.body().contains("agentService"),
                "ToolResource." + m.name() + "() does not use 'agentService'.");
        }
    }

    @Test
    void sessionResourceAllEndpointsUseAgentService() throws IOException {
        var source = readSource("resources/SessionResource.java");
        var methods = parseEndpointMethods(source);
        assertFalse(methods.isEmpty(), "No endpoint methods found in SessionResource");
        for (var m : methods) {
            assertTrue(m.body().contains("agentService"),
                "SessionResource." + m.name() + "() does not use 'agentService'.");
        }
    }

    @Test
    void uiResourceAllEndpointsUseAgentService() throws IOException {
        var source = readSource("resources/UiResource.java");
        var methods = parseEndpointMethods(source);
        assertFalse(methods.isEmpty(), "No endpoint methods found in UiResource");
        for (var m : methods) {
            assertTrue(m.body().contains("agentService"),
                "UiResource." + m.name() + "() does not use 'agentService'.");
        }
    }

    @Test
    void aguiResourceAllEndpointsUseAgentService() throws IOException {
        var source = readSource("agui/resources/AguiResource.java");
        var methods = parseEndpointMethods(source);
        assertFalse(methods.isEmpty(), "No endpoint methods found in AguiResource");
        for (var m : methods) {
            assertTrue(m.body().contains("agentService"),
                "AguiResource." + m.name() + "() does not use 'agentService'."
                    + " Possible bug: wrong agent/service assigned?");
        }
    }

    /**
     * Verifies: endpoints with path variables actually use @PathParam
     * in the method body. Fails if ignored.
     */
    @Test
    void endpointsWithPathVariablesActuallyUseThem() throws IOException {
        for (var file : findResourceFiles()) {
            var source = readSource(file);
            var methods = parseEndpointMethods(source);
            for (var m : methods) {
                var pathVal = extractAnnotationValue(m.annotations(), "@Path");
                if (pathVal == null || pathVal.isEmpty()) continue;
                var pathVars = extractPathVariables(pathVal);
                if (pathVars.isEmpty()) continue;

                // @PathParam may be in the method signature (parameter annotation)
                var ctx = m.annotations() + "\n" + m.signature();
                var declaredParams = extractAnnotationValues(ctx, "@PathParam");
                for (var varName : pathVars) {
                    assertTrue(declaredParams.contains(varName),
                        file + ": " + m.name() + "() hat {" + varName
                            + "} in @Path, but no @PathParam(\"" + varName + "\")."
                            + " Path variable is ignored!");

                    assertTrue(m.body().contains(varName),
                        file + ": " + m.name() + "() has @PathParam(\"" + varName
                            + "\") but does not use it in the body."
                            + " Path variable is ignored!");
                }
            }
        }
    }

    /**
     * Resource classes with multiple @Inject fields must use each field
     * in at least one endpoint.
     */
    @Test
    void noResourceHasUnusedInjectedFields() throws IOException {
        for (var file : findResourceFiles()) {
            var source = readSource(file);
            var injectedFields = parseInjectFields(source);
            if (injectedFields.size() < 2) continue;

            var methods = parseEndpointMethods(source);
            var usedInAnyEndpoint = new HashSet<String>();
            for (var m : methods) {
                usedInAnyEndpoint.addAll(findFieldReferences(m.body()));
            }
            for (var field : injectedFields) {
                assertTrue(usedInAnyEndpoint.contains(field),
                    file + " has @Inject field '" + field
                        + "' but no endpoint uses it."
                        + " Possible bug: wrong agent or forgotten field.");
            }
        }
    }

    // ========== Source parsing ==========

    private record EndpointMethod(String name, String annotations, String signature, String body) {}

    private static String readSource(String relativePath) throws IOException {
        var path = java.nio.file.Path.of(SRC_MAIN, relativePath);
        if (!path.toFile().exists()) return "";
        return Files.readString(path);
    }

    private static List<String> findResourceFiles() throws IOException {
        var base = java.nio.file.Path.of(SRC_MAIN);
        if (!base.toFile().isDirectory()) return List.of();
        try (var files = Files.walk(base)) {
            return files
                .filter(p -> p.toString().endsWith("Resource.java"))
                .map(base::relativize)
                .map(java.nio.file.Path::toString)
                .sorted()
                .toList();
        }
    }

    /**
     * Finds all HTTP endpoint methods with correct brace matching.
     * Searches for @GET, @POST, @PUT, @DELETE, @PATCH and extracts the
     * associated method declaration + body (curly braces at any depth).
     */
    private static List<EndpointMethod> parseEndpointMethods(String source) {
        var result = new ArrayList<EndpointMethod>();
        var clean = stripComments(source);
        var lines = clean.split("\n", -1);

        // Create position array for more efficient access
        int[] annTypeAtLine = new int[lines.length];
        Arrays.fill(annTypeAtLine, -1);

        for (int i = 0; i < lines.length; i++) {
            var line = lines[i].strip();
            if (line.contains("@GET")) annTypeAtLine[i] = 1;
            else if (line.contains("@POST")) annTypeAtLine[i] = 2;
            else if (line.contains("@PUT")) annTypeAtLine[i] = 3;
            else if (line.contains("@DELETE")) annTypeAtLine[i] = 4;
            else if (line.contains("@PATCH")) annTypeAtLine[i] = 5;
        }

        for (int i = 0; i < annTypeAtLine.length; i++) {
            if (annTypeAtLine[i] < 0) continue;

            // Annotation block: go back from here to previous method
            // or to the class declaration
            var annBlock = new StringBuilder();
            int blockStart = i;
            while (blockStart > 0) {
                var prev = lines[blockStart - 1].strip();
                if (prev.startsWith("@") || prev.isEmpty()) {
                    blockStart--;
                } else {
                    // Check if it's a parameter annotation within a signature
                    // (e.g., @PathParam("id") in the method signature)
                    if (prev.contains("@") && !prev.contains("public ")
                        && !prev.contains("class ") && !prev.contains("import ")
                        && !prev.contains(";")) {
                        blockStart--;
                    } else {
                        break;
                    }
                }
            }
            for (int j = blockStart; j <= i; j++) {
                annBlock.append(lines[j]).append("\n");
            }

            // Find method signature: from current line, find first line
            // with "public " and parentheses
            int sigLine = i;
            while (sigLine < lines.length) {
                var sl = lines[sigLine].strip();
                if (sl.contains("public ") && sl.contains("(")) break;
                // Collect annotations between HTTP method and signature
                if (sigLine > i && (sl.startsWith("@") || sl.isEmpty())) {
                    annBlock.append(lines[sigLine]).append("\n");
                }
                sigLine++;
            }
            if (sigLine >= lines.length) continue;

            // Extract method name: word directly before '('
            var sigLineStr = lines[sigLine].strip();
            var parenIdx = sigLineStr.indexOf('(');
            if (parenIdx < 0) continue;
            var beforeParen = sigLineStr.substring(0, parenIdx).strip();
            var words = beforeParen.split("\\s+");
            if (words.length == 0) continue;
            var methodName = words[words.length - 1];

            // Extract body with brace depth tracking
            var body = extractMethodBody(lines, sigLine);
            if (body == null) continue;

            result.add(new EndpointMethod(methodName, annBlock.toString(), sigLineStr, body));
            // Skip past the method body
            i = skipMethodBody(lines, sigLine);
        }

        return result;
    }

    /**
     * Extracts the method body starting from the signature line.
     * Handles arbitrarily deeply nested braces.
     */
    private static String extractMethodBody(String[] lines, int sigLine) {
        // Find first { from sigLine
        int braceLine = sigLine;
        while (braceLine < lines.length && !lines[braceLine].contains("{")) {
            braceLine++;
        }
        if (braceLine >= lines.length) return null;

        int depth = 0;
        var body = new StringBuilder();
        boolean started = false;

        for (int j = braceLine; j < lines.length; j++) {
            var l = lines[j];
            for (int c = 0; c < l.length(); c++) {
                char ch = l.charAt(c);
                if (ch == '{') {
                    depth++;
                    if (!started) {
                        started = true;
                        // Start from first char after {
                        body.append(l.substring(c + 1));
                        if (depth == 0) break;
                        body.append("\n");
                        continue;
                    }
                } else if (ch == '}') {
                    depth--;
                    if (depth == 0) {
                        return body.toString().strip();
                    }
                }
                if (started) {
                    body.append(ch);
                }
            }
            if (started && depth > 0) {
                body.append("\n");
            }
        }
        return body.toString().strip();
    }

    /** Returns the line index after the closing } of the method. */
    private static int skipMethodBody(String[] lines, int sigLine) {
        int braceLine = sigLine;
        while (braceLine < lines.length && !lines[braceLine].contains("{")) {
            braceLine++;
        }
        if (braceLine >= lines.length) return lines.length;

        int depth = 0;
        boolean inBody = false;
        for (int j = braceLine; j < lines.length; j++) {
            var l = lines[j];
            for (int c = 0; c < l.length(); c++) {
                if (l.charAt(c) == '{') { depth++; inBody = true; }
                else if (l.charAt(c) == '}') {
                    depth--;
                    if (inBody && depth == 0) return j + 1;
                }
            }
        }
        return lines.length;
    }

    private static String extractAnnotationValue(String annotations, String annName) {
        var m = Pattern.compile(annName + "\\(\"([^\"]+)\"\\)").matcher(annotations);
        return m.find() ? m.group(1) : null;
    }

    private static List<String> extractAnnotationValues(String annotations, String annName) {
        var result = new ArrayList<String>();
        var m = Pattern.compile(annName + "\\(\"([^\"]+)\"\\)").matcher(annotations);
        while (m.find()) {
            result.add(m.group(1));
        }
        return result;
    }

    private static List<String> parseInjectFields(String source) {
        var fields = new ArrayList<String>();
        var m = Pattern.compile("@Inject\\s+(?:\\w+\\s+)?(\\w+)\\s*;")
            .matcher(stripComments(source));
        while (m.find()) {
            var parts = m.group(1).split("\\s+");
            fields.add(parts[parts.length - 1]);
        }
        return fields;
    }

    private static List<String> extractPathVariables(String path) {
        var vars = new ArrayList<String>();
        var m = Pattern.compile("\\{(\\w+)}").matcher(path);
        while (m.find()) {
            vars.add(m.group(1));
        }
        return vars;
    }

    private static Set<String> findFieldReferences(String body) {
        var refs = new HashSet<String>();
        var m = Pattern.compile("(?:this\\.)?(\\w+)").matcher(body);
        while (m.find()) {
            var name = m.group(1);
            if (!isJavaKeyword(name) && name.length() > 1) {
                refs.add(name);
            }
        }
        return refs;
    }

    private static boolean isJavaKeyword(String word) {
        return Set.of("abstract", "assert", "boolean", "break", "byte", "case",
            "catch", "char", "class", "const", "continue", "default", "do",
            "double", "else", "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private",
            "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile", "while", "true", "false",
            "null", "var").contains(word);
    }

    private static String stripComments(String source) {
        var result = source.replaceAll("/\\*.*?\\*/", " ");
        result = result.replaceAll("//[^\n]*", " ");
        return result;
    }
}
