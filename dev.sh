#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ -f "$SCRIPT_DIR/set_keys.sh" ]; then
    source "$SCRIPT_DIR/set_keys.sh"
fi

PROJECT="Strands Agents SDK (Java 21)"
JAVA_VERSION="21"

usage() {
    cat <<EOF
Usage: ./dev.sh <command> [options]

Commands:
  build               Vollständigen Build ausführen (compile + test)
  test                Unit-Tests ausführen
  run                 Beispiel mit OpenAI starten (benötigt OPENAI_API_KEY)
  run-mock            Beispiel mit MockChatModel starten (kein API-Key nötig)
  chat [--mock]       Interaktive Chat-CLI starten (mit --mock ohne API-Key)
  clean               Build-Artefakte entfernen
  help                Diese Hilfe anzeigen

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

cmd_run() {
    echo ">>> $PROJECT: Starte mit OpenAI"
    check_java
    if [ -z "${OPENAI_API_KEY:-}" ]; then
        echo "Fehler: OPENAI_API_KEY ist nicht gesetzt."
        echo "  export OPENAI_API_KEY=sk-..."
        exit 1
    fi
    mvn -q install -DskipTests
    mvn -q -pl strands-agents-examples \
        exec:exec \
        -Dexec.executable="java" \
        -Dexec.args="--enable-preview -cp %classpath com.strands.agents.examples.Main"
}

cmd_run_mock() {
    echo ">>> $PROJECT: Starte mit MockChatModel (Demo – kein API-Key nötig)"
    check_java
    mvn -q install -DskipTests
    mvn -q -pl strands-agents-examples \
        exec:exec \
        -Dexec.executable="java" \
        -Dexec.args="--enable-preview -cp %classpath com.strands.agents.examples.MainMock"
}

cmd_chat() {
    echo ">>> $PROJECT: Starte interaktive Chat-CLI"
    check_java
    local mock_flag=""
    if [[ "$*" == *"--mock"* ]]; then
        mock_flag="--mock"
        echo "    (Mock-Modus – kein API-Key nötig)"
    fi
    mvn -q install -DskipTests 2>/dev/null
    mvn -q -pl strands-agents-examples \
        exec:exec \
        -Dexec.executable="java" \
        -Dexec.args="--enable-preview -cp %classpath com.strands.agents.examples.ChatCLI $mock_flag"
}

cmd_clean() {
    echo ">>> $PROJECT: Clean"
    mvn clean
    echo ">>> Clean abgeschlossen"
}

case "${1:-help}" in
    build)      cmd_build ;;
    test)       cmd_test ;;
    run)        cmd_run ;;
    run-mock)   cmd_run_mock ;;
    chat)       shift; cmd_chat "$@" ;;
    clean)      cmd_clean ;;
    help|--help|-h) usage ;;
    *)
        echo "Unbekannter Befehl: $1"
        usage
        exit 1
        ;;
esac
