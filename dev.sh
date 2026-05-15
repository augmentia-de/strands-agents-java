#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Automatisch set_keys.sh laden, falls vorhanden
if [ -f "$SCRIPT_DIR/set_keys.sh" ]; then
    source "$SCRIPT_DIR/set_keys.sh"
fi

PROJECT="Strands Agents SDK (Java 21)"
JAVA_VERSION="21"

usage() {
    cat <<EOF
Usage: ./dev.sh <command>

Commands:
  build         Vollständigen Build ausführen (compile + test)
  test          Unit-Tests ausführen (ohne Integrationstests)
  test-all      Alle Tests inkl. Integrationstests (benötigt OPENAI_API_KEY)
  run           Beispiel mit OpenAI starten (benötigt OPENAI_API_KEY)
  run-mock      Beispiel mit SimpleMockModel starten (kein API-Key nötig)
  clean         Build-Artefakte entfernen
  help          Diese Hilfe anzeigen

EOF
}

check_java() {
    if ! java --version 2>/dev/null | grep -q "21"; then
        echo "Warnung: Java $JAVA_VERSION wird benötigt. Gefunden:"
        java --version 2>/dev/null || echo "  (kein Java gefunden)"
    fi
}

cmd_build() {
    echo ">>> $PROJECT: Build"
    check_java
    mvn clean compile
    echo ">>> Build erfolgreich"
}

cmd_test() {
    echo ">>> $PROJECT: Unit-Tests"
    check_java
    mvn test
    echo ">>> Tests erfolgreich"
}

cmd_test_all() {
    echo ">>> $PROJECT: Alle Tests (inkl. Integration)"
    check_java
    mvn verify
    echo ">>> Alle Tests erfolgreich"
}

cmd_run() {
    echo ">>> $PROJECT: Starte mit OpenAI"
    check_java
    if [ -z "${OPENAI_API_KEY:-}" ]; then
        echo "Fehler: OPENAI_API_KEY ist nicht gesetzt."
        echo "  export OPENAI_API_KEY=sk-..."
        exit 1
    fi
    mvn -q install -DskipTests
    (cd strands-agents-examples && \
     mvn -q exec:java \
        -Dexec.mainClass="com.strands.agents.examples.Main")
}

cmd_run_mock() {
    echo ">>> $PROJECT: Starte mit MockChatModel (Demo – kein API-Key nötig)"
    check_java
    mvn -q install -DskipTests
    (cd strands-agents-examples && \
     mvn -q exec:java \
        -Dexec.mainClass="com.strands.agents.examples.MainMock")
}

cmd_clean() {
    echo ">>> $PROJECT: Clean"
    mvn clean
    echo ">>> Clean abgeschlossen"
}

case "${1:-help}" in
    build)      cmd_build ;;
    test)       cmd_test ;;
    test-all)   cmd_test_all ;;
    run)        cmd_run ;;
    run-mock)   cmd_run_mock ;;
    clean)      cmd_clean ;;
    help|--help|-h) usage ;;
    *)
        echo "Unbekannter Befehl: $1"
        usage
        exit 1
        ;;
esac
