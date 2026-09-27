#!/usr/bin/env bash
# Runs Gradle and, if it fails, surfaces the important lines (compiler errors, failed tests,
# "What went wrong") as workflow annotations so they're visible without opening the raw log.
set -uo pipefail
log="$(mktemp)"
./gradlew --console=plain "$@" 2>&1 | tee "$log"
status=${PIPESTATUS[0]}
if [ "$status" -ne 0 ]; then
  {
    grep -E '^e: ' "$log"
    grep -E -B1 -A14 'FAILED$' "$log"
    grep -E -A20 'What went wrong' "$log"
    grep -E -A3 '^[^ ]+\.(kt|xml|kts):[0-9]+: (Error|Warning)' "$log"
  } | head -400 > "$log.err"
  split -l 45 "$log.err" "$log.chunk."
  n=0
  for f in "$log".chunk.*; do
    [ -e "$f" ] || continue
    n=$((n + 1))
    [ "$n" -gt 9 ] && break
    msg=$(sed -e 's/%/%25/g' -e 's/\r//g' "$f" | awk '{printf "%s%%0A", $0}')
    echo "::error title=Gradle failure (part $n)::$msg"
  done
fi
exit "$status"
