package de.augmentia.strandsagents.quarkus;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Regression-Test: Stellt sicher, dass in jedem REST-Controller die
 * korrekten Agenten/Services an die jeweiligen Endpunkte delegiert werden.
 *
 * Bekannter Bug aus einem Code-Review:
 * Ein Controller hatte zwei verschiedene Agenten per @Qualifier injiziert
 * (agenticChatAgent, sharedStateAgent), aber fast alle Methoden riefen
 * fälschlich immer den agenticChatAgent auf – egal ob shared_state,
 * tool_based_generative_ui, human_in_the_loop oder agentic_generative_ui
 * aufgerufen wurde. Auch die Pfadvariable {agentId} in /sse/{agentId}
 * wurde ignoriert.
 *
 * Dieser Test verhindert ein erneutes Einschleichen dieses Anti-Patterns.
 */
class AgentAssignmentTest {

    private static final String SRC_MAIN =
        "src/main/java/de/augmentia/strandsagents/quarkus";

    /**
     * Prüft: Jeder Endpoint in ChatResource delegiert an AgentService.
     */
    @Test
    void chatResourceAllEndpointsUseAgentService() throws IOException {
        var source = readSource("resources/ChatResource.java");
        var methods = parseEndpointMethods(source);
        assertFalse(methods.isEmpty(), "Keine Endpoint-Methoden in ChatResource gefunden");
        for (var m : methods) {
            assertTrue(m.body().contains("agentService"),
                "ChatResource." + m.name() + "() verwendet nicht 'agentService'."
                    + " Möglicher Bug: falscher Agent/Service zugewiesen?");
        }
    }

    @Test
    void toolResourceAllEndpointsUseAgentService() throws IOException {
        var source = readSource("resources/ToolResource.java");
        var methods = parseEndpointMethods(source);
        assertFalse(methods.isEmpty(), "Keine Endpoint-Methoden in ToolResource gefunden");
        for (var m : methods) {
            assertTrue(m.body().contains("agentService"),
                "ToolResource." + m.name() + "() verwendet nicht 'agentService'.");
        }
    }

    @Test
    void sessionResourceAllEndpointsUseAgentService() throws IOException {
        var source = readSource("resources/SessionResource.java");
        var methods = parseEndpointMethods(source);
        assertFalse(methods.isEmpty(), "Keine Endpoint-Methoden in SessionResource gefunden");
        for (var m : methods) {
            assertTrue(m.body().contains("agentService"),
                "SessionResource." + m.name() + "() verwendet nicht 'agentService'.");
        }
    }

    @Test
    void uiResourceAllEndpointsUseAgentService() throws IOException {
        var source = readSource("resources/UiResource.java");
        var methods = parseEndpointMethods(source);
        assertFalse(methods.isEmpty(), "Keine Endpoint-Methoden in UiResource gefunden");
        for (var m : methods) {
            assertTrue(m.body().contains("agentService"),
                "UiResource." + m.name() + "() verwendet nicht 'agentService'.");
        }
    }

    @Test
    void aguiResourceAllEndpointsUseAgentService() throws IOException {
        var source = readSource("agui/resources/AguiResource.java");
        var methods = parseEndpointMethods(source);
        assertFalse(methods.isEmpty(), "Keine Endpoint-Methoden in AguiResource gefunden");
        for (var m : methods) {
            assertTrue(m.body().contains("agentService"),
                "AguiResource." + m.name() + "() verwendet nicht 'agentService'."
                    + " Möglicher Bug: falscher Agent/Service zugewiesen?");
        }
    }

    /**
     * Prüft: Endpoints mit Pfadvariable verwenden die @PathParam auch
     * tatsächlich im Methodenrumpf. Schlägt fehl, wenn ignoriert.
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

                // @PathParam kann in der Methodensignatur stehen (Parameter-Annotation)
                var ctx = m.annotations() + "\n" + m.signature();
                var declaredParams = extractAnnotationValues(ctx, "@PathParam");
                for (var varName : pathVars) {
                    assertTrue(declaredParams.contains(varName),
                        file + ": " + m.name() + "() hat {" + varName
                            + "} in @Path, aber kein @PathParam(\"" + varName + "\")."
                            + " Pfadvariable wird ignoriert!");

                    assertTrue(m.body().contains(varName),
                        file + ": " + m.name() + "() hat @PathParam(\"" + varName
                            + "\"), verwendet es aber nicht im Rumpf."
                            + " Pfadvariable wird ignoriert!");
                }
            }
        }
    }

    /**
     * Resource-Klassen mit mehreren @Inject-Feldern müssen jedes Feld
     * in mindestens einem Endpoint verwenden.
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
                    file + " hat @Inject-Feld '" + field
                        + "', aber kein Endpoint verwendet es."
                        + " Möglicher Bug: falscher Agent oder vergessenes Feld.");
            }
        }
    }

    // ========== Source-Parsing ==========

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
     * Findet alle HTTP-Endpoint-Methoden mit korrektem Brace-Matching.
     * Sucht nach @GET, @POST, @PUT, @DELETE, @PATCH und extrahiert die
     * zugehörige Methodendeklaration + Rumpf (geschweifte Klammern
     * in beliebiger Tiefe).
     */
    private static List<EndpointMethod> parseEndpointMethods(String source) {
        var result = new ArrayList<EndpointMethod>();
        var clean = stripComments(source);
        var lines = clean.split("\n", -1);

        // Erstelle Positions-Array für effizienteren Zugriff
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

            // Annotation-Block: von hier zurück bis zur vorherigen Methode
            // oder bis zur Klassendeklaration
            var annBlock = new StringBuilder();
            int blockStart = i;
            while (blockStart > 0) {
                var prev = lines[blockStart - 1].strip();
                if (prev.startsWith("@") || prev.isEmpty()) {
                    blockStart--;
                } else {
                    // Prüfe ob es eine Parameter-Annotation innerhalb einer Signatur ist
                    // (z.B. @PathParam("id") in der Methodensignatur)
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

            // Methodensignatur suchen: suche ab aktueller Zeile die erste Zeile
            // mit "public " und runden Klammern
            int sigLine = i;
            while (sigLine < lines.length) {
                var sl = lines[sigLine].strip();
                if (sl.contains("public ") && sl.contains("(")) break;
                // Annotationen zwischen HTTP-Methode und Signatur sammeln
                if (sigLine > i && (sl.startsWith("@") || sl.isEmpty())) {
                    annBlock.append(lines[sigLine]).append("\n");
                }
                sigLine++;
            }
            if (sigLine >= lines.length) continue;

            // Methodennamen extrahieren: Wort direkt vor '('
            var sigLineStr = lines[sigLine].strip();
            var parenIdx = sigLineStr.indexOf('(');
            if (parenIdx < 0) continue;
            var beforeParen = sigLineStr.substring(0, parenIdx).strip();
            var words = beforeParen.split("\\s+");
            if (words.length == 0) continue;
            var methodName = words[words.length - 1];

            // Rumpf extrahieren mit Brace-Depth-Tracking
            var body = extractMethodBody(lines, sigLine);
            if (body == null) continue;

            result.add(new EndpointMethod(methodName, annBlock.toString(), sigLineStr, body));
            // Springe hinter den Methodenrumpf
            i = skipMethodBody(lines, sigLine);
        }

        return result;
    }

    /**
     * Extrahiert den Methodenrumpf ab der Zeile mit der Signatur.
     * Handelt beliebig tiefe geschachtelte Klammern.
     */
    private static String extractMethodBody(String[] lines, int sigLine) {
        // Finde erste { ab sigLine
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
                        // Ab erstem Zeichen nach { beginnen
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

    /** Gibt Zeilenindex nach dem schliessenden } der Methode zurück. */
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
