#!/usr/bin/env bash
#
# Smoke tests shared by the JAR image and the native image.
#
# Usage: smoke-test.sh [native|jar]
#
#   native  tests the binary in /app/cli and the bundle in /app/gui
#   jar     tests the executable JARs in /app (CLI and GUI)
#
# The GUI needs a display, so it is started under Xvfb and expected to stay open
# until the timeout. Anything else (early exit, headless message) is a failure.

set -euo pipefail

MODE="${1:-native}"

if [ "$MODE" = "jar" ]; then
    CLI="java -jar $(ls /app/furigana-parser-*.jar | grep -v -- '-gui\.jar$')"
    GUI="java -jar $(ls /app/furigana-parser-*-gui.jar)"
else
    CLI="/app/cli/furigana-parser"
    GUI="/app/gui/furigana-parser-gui"
fi

SAMPLE="/app/sample.ja.srt"
WORK="$(mktemp -d)"
GUI_TIMEOUT="${GUI_TIMEOUT:-8}"

echo "== Smoke tests ($MODE) =="
echo "CLI: $CLI"
echo "GUI: $GUI"

echo "-- CLI --help"
$CLI --help | head -n 1

echo "-- CLI generate JSON"
$CLI "$SAMPLE" "$WORK/out.json"
grep -q '"reading" : "きょう"' "$WORK/out.json"
grep -q '"reading" : "にほんご"' "$WORK/out.json"

echo "-- CLI generate ASS (strip parentheses + color)"
$CLI --strip-parentheses --color=#1122FF "$SAMPLE" "$WORK/out.ass"
grep -q '&H00FF2211' "$WORK/out.ass"
if grep -q '笑' "$WORK/out.ass"; then
    echo "FAIL: the parentheses were not stripped"
    exit 1
fi
grep -q '猫が好きです' "$WORK/out.ass"

echo "-- GUI starts under Xvfb"
set +e
timeout "$GUI_TIMEOUT" xvfb-run -a $GUI > "$WORK/gui.log" 2>&1
code=$?
set -e
if [ "$code" -ne 124 ]; then
    echo "FAIL: the GUI exited with code $code instead of staying open"
    cat "$WORK/gui.log"
    exit 1
fi
# A process whose event dispatch thread died stays alive and would pass the check
# above, so reject anything that printed a stack trace.
if grep -qE '^[[:space:]]+at |Exception in thread|Fatal error reported' "$WORK/gui.log"; then
    echo "FAIL: the GUI logged an exception"
    cat "$WORK/gui.log"
    exit 1
fi
echo "OK: the window stayed open until the timeout"

echo "== All smoke tests passed ($MODE) =="
