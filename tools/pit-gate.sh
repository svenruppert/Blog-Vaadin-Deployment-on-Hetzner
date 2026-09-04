#!/usr/bin/env bash
# Mutation-coverage gate. Parses target/pit-reports/index.html, compares
# every package's mutation coverage against the floor in
# tools/pit-baselines.txt, and exits non-zero on any regression.
#
# Run manually after PIT:
#   ./mvnw -o org.pitest:pitest-maven:mutationCoverage
#   ./tools/pit-gate.sh
#
# Or activate it in one shot via the _mutation-gate Maven profile —
# that profile runs PIT, then this script.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPORT="${ROOT_DIR}/target/pit-reports/index.html"
BASELINES="${ROOT_DIR}/tools/pit-baselines.txt"

if [[ ! -f "${REPORT}" ]]; then
  echo "✗ PIT report missing at ${REPORT}" >&2
  echo "  Run: ./mvnw -o org.pitest:pitest-maven:mutationCoverage" >&2
  exit 2
fi
if [[ ! -f "${BASELINES}" ]]; then
  echo "✗ Baselines file missing at ${BASELINES}" >&2
  exit 2
fi

# Pair every <a href="./<pkg>/index.html"> link with its next 3
# coverage_legend values (line / mutation / strength). Print
# "<pkg> <mutation-percent>" per line.
# Written without `mapfile` so it runs on macOS's bash 3.2.
COVERAGE_FILE=$(mktemp)
trap 'rm -f "${COVERAGE_FILE}"' EXIT
awk '
  /<td><a href="\.\/com\.svenruppert/ {
    match($0, /">[^<]+<\/a/); pkg = substr($0, RSTART+2, RLENGTH-5); count=0; next
  }
  pkg != "" && /coverage_legend/ {
    match($0, /coverage_legend">[^<]+</); val = substr($0, RSTART+17, RLENGTH-18);
    arr[++count] = val;
    if (count == 3) {
      split(arr[2], parts, "/");
      pct = (parts[2] == 0) ? 100 : int(parts[1] * 100 / parts[2]);
      printf "%s %d\n", pkg, pct;
      pkg=""; delete arr
    }
  }
' "${REPORT}" > "${COVERAGE_FILE}"

# Overall mutation coverage — the first <tbody> row in the report carries
# the project-wide summary as (line / mutation / strength); we take the
# 2nd legend value.
OVERALL=$(awk '
  /coverage_legend">[0-9]+\/[0-9]+</ {
    seen++;
    if (seen == 2) {
      match($0, /coverage_legend">[0-9]+\/[0-9]+</);
      val = substr($0, RSTART+17, RLENGTH-18);
      split(val, parts, "/");
      pct = (parts[2] == 0) ? 100 : int(parts[1] * 100 / parts[2]);
      print pct; exit
    }
  }
' "${REPORT}")

# Read baselines into an associative array.
# bash 3.2 has no associative arrays — use a lookup function instead.
lookup_floor() {
  local key="$1"
  awk -v k="${key}" -F= '
    /^#/ || NF < 2 { next }
    $1 == k { print $2; exit }
  ' "${BASELINES}"
}

FAILURES=0
GOOD=0
printf "%-55s %-12s %-12s %s\n" "Package" "Coverage%" "Floor%" "Verdict"
printf "%-55s %-12s %-12s %s\n" "-------" "---------" "------" "-------"

# Per-package check.
while read -r line; do
  pkg="${line% *}"
  pct="${line##* }"
  floor="$(lookup_floor "${pkg}")"
  if [[ -z "${floor}" ]]; then
    printf "%-55s %-12s %-12s ⚠ no baseline\n" "${pkg}" "${pct}%" "(none)"
    continue
  fi
  if (( pct < floor )); then
    printf "%-55s %-12s %-12s ✗ REGRESSION\n" "${pkg}" "${pct}%" "${floor}%"
    FAILURES=$((FAILURES + 1))
  else
    printf "%-55s %-12s %-12s ✓\n" "${pkg}" "${pct}%" "${floor}%"
    GOOD=$((GOOD + 1))
  fi
done < "${COVERAGE_FILE}"

# Overall check.
overall_floor="$(lookup_floor "__overall__")"
overall_floor="${overall_floor:-0}"
if [[ -n "${OVERALL}" ]]; then
  printf "%-55s %-12s %-12s " "__overall__" "${OVERALL}%" "${overall_floor}%"
  if (( OVERALL < overall_floor )); then
    echo "✗ REGRESSION"
    FAILURES=$((FAILURES + 1))
  else
    echo "✓"
    GOOD=$((GOOD + 1))
  fi
fi

echo
if (( FAILURES > 0 )); then
  echo "✗ Mutation gate FAILED — ${FAILURES} regression(s), ${GOOD} ok"
  echo "  Either kill the surviving mutants, or update tools/pit-baselines.txt"
  echo "  with a written reason in the commit message."
  exit 1
fi
echo "✓ Mutation gate passed — ${GOOD}/${GOOD} package(s) at or above their floor"
