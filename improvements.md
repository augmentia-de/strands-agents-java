# Improvement Plan

Analysis based on 155 production Java files across 3 modules, 34 test files, build configuration, and architecture review.

## Status Summary

| Metric | Current |
|--------|---------|
| Production classes | 155 (129 core + 11 quarkus + 15 examples) |
| Test classes | 34 (all in core/quarkus; examples has zero) |
| Test coverage ratio | ~31% (core), ~9% (quarkus), 0% (examples) |
| Untested packages | 10 fully uncovered packages in core alone |
| static analysis tools | 0 active (JaCoCo/Spotless declared but never bound) |
| null-safety annotations | 0 across all files |
| SLF4J usage in prod code | 0 (available but unused) |
| `catch (Exception)` swallows | 69 occurrences |
| `System.out`/`System.err` in prod | 15 occurrences |
| `@Deprecated` annotations | 0 |

---

## 🔴 High Impact

### H1: Activate JaCoCo Coverage Reporting & Gate

**Problem:** JaCoCo 0.8.12 is declared in `<pluginManagement>` in the root POM but never bound to any Maven lifecycle phase. No coverage reports are generated during `mvn test`, making it impossible to track coverage trends.

**Implementation:**
1. Add `<executions>` to JaCoCo in the `strands-agents` module POM:
   - `prepare-agent` bound to `initialize`
   - `report` bound to `verify`
2. Optionally add a `<check>` rule with a minimum instruction coverage ratio (start at 20%, ratchet up).
3. Verify: `mvn verify -pl strands-agents` generates `target/site/jacoco/index.html`.

**Effort:** Small (config change). **Risk:** None.

---

### H2: Add Static Analysis (Checkstyle + SpotBugs)

**Problem:** No static analysis tooling is active. Code style drifts, bug patterns go undetected, and there is no automated quality gate. `spotless-maven-plugin` is declared in `pluginManagement` but also never activated.

**Implementation:**
1. Create `checkstyle.xml` at the root (Google Java Style or custom, e.g., 4-space indent, no unused imports).
2. Add `checkstyle-suppressions.xml` for generated code or known violations.
3. Add `maven-checkstyle-plugin` to the root POM's `<build><plugins>` section (not just `pluginManagement`), or activate per-module.
4. Configure `spotless-maven-plugin` with a formatter (e.g., Palantir or Google) and bind to `validate`.
5. Run `mvn spotless:check` and `mvn checkstyle:check` to baseline current violations before enforcing.
6. Consider adding SpotBugs (`spotbugs-maven-plugin`) with a threshold (e.g., `Low`).

**Effort:** Medium (config + initial violation cleanup). **Risk:** Low (can start with warnings-only).

---

### H3: Eliminate Silent Exception Swallowing

**Problem:** 69 occurrences of `catch (Exception e)` and 16 occurrences of `catch (Exception ignored)`. Many silently return null, empty collections, or placeholders. Key examples:

| File | Line | Pattern |
|------|------|---------|
| `SkillParser.java` | 75 | `catch (Exception ignored) { return null; }` → NPE at call site |
| `AgentSkillsPlugin.java` | 160 | `catch (Exception ignored) {}` → complete silence |
| `CapabilitySearchTool.java` | 81 | `catch (Exception e) { return Stream.of(); }` → hides failures |
| `CompositeSecretProvider.java` | 26, 37 | `catch (Exception ignored) {}` → secret resolution failures invisible |
| `JdbcSessionManager.java` | multiple | SQL exceptions silently return `Optional.empty()` |

**Implementation:**
1. Add SLF4J logger to each affected class: `private static final Logger log = LoggerFactory.getLogger(...)`
2. Replace `catch (Exception ignored)` with `catch (SpecificException e) { log.warn("...", e); }`
3. Where returning null/empty on failure is intentional, document with a comment explaining why.
4. For MCP/network failures, consider distinguishing transient (retryable) from permanent failures.

**Effort:** Medium (15-20 files to touch). **Risk:** Low — mostly mechanical.

---

### H4: Add Null-Safety Annotations

**Problem:** Zero `@Nullable` / `@NonNull` / `@NonNullApi` annotations in 155 files. Parameter and return types carry no contract. Nullable values flow without documentation (e.g., `Optional.orElse(null)`, `Map.of()` as null fallback).

**Implementation:**
1. Add a null-safety dependency (e.g., `org.jspecify:jspecify:1.0.0` or `jakarta.annotation:jakarta.annotation-api`).
2. Annotate public API surface first:
   - All `Agent` method parameters and return types.
   - All hook/plugin interfaces.
   - All tool method signatures.
   - `SessionManager` and `ConversationManager` interfaces.
3. Use `@NullMarked` or `@NonNullApi` at package level via `package-info.java`.
4. Run a Nullness checker (e.g., Checker Framework or IntelliJ inspections) to find violations incrementally.

**Effort:** Large (systematic across ~50 interface + public API files). **Risk:** Medium — may reveal latent NPEs.

---

### H5: Move Test Doubles out of src/main

**Problem:** `MockChatModel` and `MockStreamingChatModel` live in `src/main/java/de/augmentia/strandsagents/core/agent/`. They ship as part of the production JAR and bloat the artifact with test infrastructure.

**Implementation:**
1. Move both classes to `src/test/java/de/augmentia/strandsagents/core/agent/`.
2. Update imports in test files (they should resolve naturally since tests already depend on the core module).
3. Check for any production code referencing these (grep) — likely none.
4. Alternatively, extract them into a separate test-jar via `maven-jar-plugin` `<execution>` with `test-jar` goal.

**Effort:** Small. **Risk:** Low.

---

### H6: Replace `System.out`/`System.err` with SLF4J in Production Code

**Problem:** SLF4J 2.0.16 is a dependency but unused. Production code uses `System.out`/`System.err` for operational logging (not just interactive CLI prompts where it's acceptable):

- `CalculatorTool.java:15` — debug output on every calculation
- `ToolRegistry.java:321` — load error printed to stderr
- `FileLlmLogger.java:70,135,153` — log rotation errors to stderr
- `AgentService.java:200,553,596,623,651` — status/error messages

**Implementation:**
1. Add `private static final Logger log = LoggerFactory.getLogger(...)` to each affected class.
2. Replace `System.out.println(...)` with `log.info(...)` or `log.debug(...)`.
3. Replace `System.err.println(...)` with `log.warn(...)` or `log.error(...)`.
4. Keep `System.out` only in interactive CLI contexts (`HumanInTheLoopTool`, `HITLHook.consoleProvider()`).

**Effort:** Small (5-7 files). **Risk:** Low.

---

## 🟡 Medium Impact

### M1: Cover Zero-Coverage Packages with Tests

**Problem:** Entire packages have zero test coverage:

| Package | Classes | Risk |
|---------|---------|------|
| `core.agent.a2a` | 4 (A2AResult, SubAgentExecutor, SubAgentResult, SubAgentTool) | Sub-agent delegation untested |
| `core.agent.routing` | 2 (LlmRouter, RoutingResult) | LLM routing decisions untested |
| `core.model.message` | 5 (AssistantMessage, SystemMessage, ToolMessage, UserMessage, Message) | Core domain model |
| `core.model.tool` | 2 (ToolCall, ToolExecutionResult) | Tool execution model |
| `core.model.session` | 1 (Session) | Session data model |
| `core.logging` | 2 (FileLlmLogger, LoggingChatModel) | LLM audit trail |
| `core.plugin` | 2 (Plugin, PluginRegistry) | Plugin infrastructure |
| `core.tools` | 19 of 20 tools | Tool implementations |
| `skills` | 3 (CapabilitySearchTool, McpIngestTool, McpListTool) | MCP tool discovery |
| `telemetry` | 7 (covered by 1 test) | Tracing, metrics, hooks |

**Implementation approach (by priority):**
1. **Tool tests** — each tool has a simple `@Test` verifying it produces correct output/error. Use `ToolResult` assertions.
2. **Message model** — verify each Message subtype constructs, serializes, and deserializes correctly (including null/edge cases).
3. **Plugin infrastructure** — test `PluginRegistry` initialization, event delegation, tool registration.
4. **A2A + Routing** — unit test `SubAgentExecutor` with mock tools, `LlmRouter` with mock LLM responses.
5. **Logging** — `FileLlmLogger` with `@TempDir`, verify log file content and rotation.
6. **Telemetry** — extend `TelemetryTest` to cover each hook/metric individually.

**Effort:** Large (40-60 new test methods). Can be parallelized.

---

### M2: Simplify Agent Constructor Chain via AgentConfig Builder

**Problem:** `Agent.java` has 8 constructor overloads (lines 95–170) with telescoping parameters. The `AgentConfig` builder already exists and can create an agent via `AgentConfig.builder().createAgent(model)`. The overload chain is fragile and hard to maintain.

**Implementation:**
1. Deprecate all constructors except `Agent(ChatModel model)` and `Agent(AgentConfig config)`.
2. Move HookRegistry, plugins, resilience, etc. into `AgentConfig.Builder`:
   - `.hookRegistry(HookRegistry)`
   - `.plugins(List<Plugin>)`
3. Update `AgentConfig.createAgent()` internal delegation to use the new minimal constructor.
4. Update all internal callers and tests to use `AgentConfig.builder().build().createAgent(model)`.
5. Add `@Deprecated` (forRemoval=true) to old constructors with a `since` javadoc tag.

**Effort:** Medium. **Risk:** Medium — affects many callers.

---

### M3: Enable Spotless for Automated Code Formatting

**Problem:** `spotless-maven-plugin` is declared in `pluginManagement` but never activated. Formatting conventions are not enforced.

**Implementation:**
1. Add `spotless-maven-plugin` to the root `<build><plugins>` section (outside `pluginManagement`).
2. Choose a formatter and license header template:
   - Palantir Java format or Google Java Format.
   - Apache License 2.0 header from `LICENSE`.
3. Run `mvn spotless:check` to baseline.
4. Run `mvn spotless:apply` to fix current formatting.
5. Bind to `validate` phase so builds fail on formatting violations.
6. Add a `.editorconfig` file at the root.

**Effort:** Small (config + one-time formatting pass). **Risk:** Low (mechanical).

---

### M4: Add @Deprecated Annotations for API Evolution

**Problem:** Zero `@Deprecated` annotations. When the API evolves (e.g., constructors replaced by builder), consumers get no compile-time warning.

**Implementation:**
1. Audit the public API for outdated methods.
2. Add `@Deprecated(forRemoval=true, since="...")` with javadoc `@deprecated` tags pointing to the replacement.
3. Establish a convention: `@Deprecated` in 1 version, `@forRemoval` in the next, removal in the following.

**Effort:** Small (ongoing, but initial pass is quick).

---

### M5: Add Integration Tests for Quarkus Module

**Problem:** `strands-agents-quarkus` has 1 test (`ChatResourceTest`) for 11 production classes. The REST endpoints, A2A producers, and `AgentService` are untested.

**Implementation:**
1. Use `@QuarkusTest` with `@TestProfile` for mocked agent components.
2. Test `ChatResource` (chat, stream, init, release) via `given()` / `when()` / `then()`.
3. Test `SessionResource` (CRUD sessions).
4. Test `ToolResource` (list tools).
5. Test `AgentService` via CDI unit test with mocked `Agent`.

**Effort:** Medium. **Dependency:** Requires Quarkus test infrastructure to be set up (already has `quarkus-junit5` in POM).

---

### M6: Add Session Cleanup / TTL Mechanism

**Problem:** Session-Dateien (`FileSessionManager`) und -Rows (`JdbcSessionManager`) sammeln sich unbegrenzt an. Es gibt kein TTL, keinen Scheduled Cleanup, keine maximale Anzahl. Einziger Löschpfad ist das REST-`DELETE /api/sessions/{id}`. Im `.sessions/`-Verzeichnis liegen Dateien ab Mai 2026, was zeigt, dass indefinite Akkumulation real ist.

**Betroffene Komponenten:**
- `FileSessionManager` — `deleteSession()` existiert, wird aber nie automatisch aufgerufen
- `JdbcSessionManager` — `DELETE FROM sessions` via REST nur manuell
- `AgentService.@PreDestroy` — löscht nur den In-Memory-Cache, nicht die Dateien
- Kein Config-Property, keine Env-Var, kein `@Scheduled` für Retention

**Implementation:**
1. **TTL on Load**: `SessionManager.loadSession()` prüft `createdAt` / `updatedAt` gegen ein konfigurierbares Maximum (z. B. `strands.session.ttl=30d`). Bei Ablauf wird die Session gelöscht und `Optional.empty()` zurückgegeben.
2. **Scheduled Cleanup**: In der Quarkus-Module einen `@Scheduled` Job ergänzen, der regelmäßig (z. B. täglich) Sessions mit `updatedAt < now() - ttl` löscht.
3. **Konfiguration**: Neue Properties `strands.session.ttl` (Dauer), `strands.session.cleanup.interval` (Cron), jeweils mit sinnvollen Defaults (z. B. 30 Tage TTL, täglicher Cleanup).
4. **FileChatMemoryStore**: Auch die LangChain4j-ChatMemory-Dateien parallel löschen.

**Effort:** Medium. **Risk:** Low (keine bestehenden Sessions ohne TTL-Vorgabe betroffen).

---

## 🟢 Lower Impact

### L1: Isolate `--enable-preview` to ScopedValue Files

**Problem:** The root POM sets `--enable-preview` globally. Most Java 21 features used (records, sealed, pattern matching) are final and don't need it. Only `ScopedValue` (used in 5 files: `AgentContext.java`, `Agent.java`, `StreamingAgent.java`, `SubAgentExecutor.java`, `RecursiveThinkingDemo.java`) requires preview.

**Implementation:**
1. Remove `--enable-preview` from the root POM compiler config.
2. Add `--enable-preview` only in the POM configuration for the Maven Compiler Plugin's `<test>`, or move `ScopedValue` usage behind a sealed helper interface.
3. Alternatively, replace `ScopedValue` with `InheritableThreadLocal` or manual context propagation (higher effort).

**Effort:** Small. **Risk:** Low (but `ScopedValue` ergonomics are nicer with structured concurrency).

---

### L2: Create Examples Module Smoke Tests

**Problem:** `strands-agents-examples` has no `src/test/java` directory. Demos like `AgentDemo`, `ChatCLI`, `HITLDemo` have no automated verification.

**Implementation:**
1. Add `src/test/java` to `strands-agents-examples`.
2. Add a test dependency on the core module (already present) and `mockito-core` if needed.
3. Write basic smoke tests that construct agents with `MockChatModel` and verify they respond without throwing.
4. Example: `AgentDemoTest` — construct agent via `AgentConfig`, execute a simple prompt, assert `result.finalAnswer()` is non-null.

**Effort:** Small (5-10 test methods). **Risk:** None.

---

### L3: Extract Named Constants for Magic Numbers

**Problem:** ~10 hardcoded literals in production code:

| Location | Literal | Context |
|----------|---------|---------|
| `Agent.java:365` | `hookRetry < 3` | afterModelCall retry limit |
| `Agent.java:128` | `20` | fallback conversation window |
| `CircuitBreaker.java:96` | `recentCalls.size() > 100` | sliding window cap |
| `AgentService.java:398` | `10` | conversation window |

**Implementation:**
1. Extract each as a `private static final int` field with a descriptive name.
2. Where configurable, expose as constructor parameter with the constant as default.

**Effort:** Small. **Risk:** None.

---

### L4: Add module-info.java (JPMS)

**Problem:** Targeting Java 21 but no module descriptors exist. Without JPMS, there's no reliable class encapsulation or `requires transitive` for the public API.

**Implementation:**
1. Create `module-info.java` in `strands-agents/src/main/java`:
   - Export `de.augmentia.strandsagents.core`, `.core.agent`, `.core.config`, `.core.hook`, `.core.plugin`, `.core.conversation`, `.core.resilience`, `.core.structured`, `.core.tools`, `.sessions`, `.skills`, `.telemetry`, `.vault`.
   - Require `dev.langchain4j.core`, `dev.langchain4j.model.openai`, `com.fasterxml.jackson.databind`, `com.fasterxml.jackson.dataformat.yaml`, `org.slf4j`, etc.
2. Add to `strands-agents-quarkus` (requires `quarkus.core`, `jakarta.inject`, etc.).
3. Add `Automatic-Module-Name` to each POM as a transitional step before full `module-info.java`.

**Effort:** Medium. **Risk:** Medium — can break classpath scanning (e.g., Quarkus bean discovery, Jackson module discovery).

---

## Dependencies Between Improvements

```
H1 (JaCoCo) ──> M3 (Spotless) ──> H2 (Checkstyle)
                  
H3 (Exceptions) ─── H6 (SLF4J) ──> H4 (Null-safety)
                  
M1 (Tests) ──> L2 (Examples)
                  
M2 (Builder) ──> M4 (@Deprecated)
                  
L4 (JPMS) ──> M3 (formatting), H4 (null-safety)
```

- **H1, H2, M3** are independent and can start immediately.
- **H3** and **H6** should be done together since SLF4J is needed for proper logging in exception handlers.
- **M1** is independent and large — best parallelized.
- **M2** depends on knowing which API surface is stable (ideally after H4 annotations).

---

## Recommended Ordering

### Phase 1 — Quick wins (days 1-2)
- H1: Activate JaCoCo
- H5: Move test doubles
- H6: Replace System.out with SLF4J
- L2: Example smoke tests
- L3: Extract magic number constants
- M3: Enable Spotless formatting

### Phase 2 — Test coverage (days 3-7)
- M1: Write tests for zero-coverage packages (tools, messages, logging, A2A, routing, plugins)

### Phase 3 — Error handling & contract (days 8-12)
- H3: Fix silent exception swallowing (requires H6 first)
- H4: Add null-safety annotations (ongoing)

### Phase 4 — API hardening (days 13-16)
- M2: Simplify Agent constructor chain
- M4: Add @Deprecated annotations
- L1: Isolate --enable-preview

### Phase 5 — Infrastructure (days 17-20)
- H2: Add static analysis (Checkstyle, SpotBugs)
- M5: Quarkus integration tests
- L4: JPMS module-info.java

---

## Implementation State (Phase 1)

Updated: 2026-05-21

| Item | Status | Notes |
|------|--------|-------|
| H1: Activate JaCoCo | ✅ Done | Added `<executions>` (prepare-agent + report) to `strands-agents/pom.xml`. Run `mvn verify -pl strands-agents` to generate `target/site/jacoco/index.html`. |
| H5: Move test doubles | ⚠️ Skipped | `MockChatModel`/`MockStreamingChatModel` are referenced by `AgentService.java` in the Quarkus module (production code). Cannot move to `src/test` without breaking the Quarkus module. Alternative: extract to a separate test-jar via `maven-jar-plugin` with `<goal>test-jar</goal>`, or move to a `core.agent.mock` sub-package in `src/main`. |
| H6: SLF4J in prod code | ✅ Done | Replaced `System.out`/`System.err` in: `CalculatorTool.java` (debug), `ToolRegistry.java` (warn), `FileLlmLogger.java` (4× warn), `AgentService.java` (5× warn/info). Interactive CLI output preserved in `HumanInTheLoopTool` and `HITLHook.consoleProvider()`. |
| M3: Enable Spotless | ✅ Done | Added Palantir Java Format 2.50.0 to root POM `<build><plugins>`. Run `mvn spotless:apply` to format, `mvn spotless:check` to verify. Not yet bound to a lifecycle phase. |
| L2: Example smoke tests | ✅ Done | Created `strands-agents-examples/src/test/java/` with `AgentDemoSmokeTest.java` (3 tests): agent with MockChatModel, agent with tools, agent with hooks. Added JUnit 5 + AssertJ dependencies to examples POM. |
| L3: Named constants | ✅ Done | Extracted: `Agent.MAX_HOOK_RETRIES = 3`, `Agent.DEFAULT_MAX_MESSAGES = 20`, `CircuitBreaker.MAX_RECENT_CALLS = 100`. |

### Verification

All tests pass:

```
strands-agents: 319 tests, 0 failures
strands-agents-examples: 3 tests, 0 failures
```
