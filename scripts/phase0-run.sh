#!/usr/bin/env bash
# Phase 0: run one fixture through baseline -> recipes -> verify, by hand.
#
#   scripts/phase0-run.sh <fixture> [--local]
#
# Default mode runs every Maven step in a throwaway Docker container (maven + Temurin 17),
# which is closest to the future sandbox. --local uses the `mvn` on PATH (or $MVN) instead.
# Only use --local on fixtures we wrote ourselves: a build runs arbitrary code.
#
# Output: work/<fixture>/        git working copy with one commit per stage
#         evals/results/phase-0/<fixture>/  logs, surefire summaries, diffs
set -euo pipefail

REWRITE_PLUGIN_VERSION="6.46.1"
REWRITE_SPRING_VERSION="6.37.1"
MAVEN_IMAGE="maven:3.9.16-eclipse-temurin-17"

fixture="${1:?usage: phase0-run.sh <fixture> [--local]}"
mode="${2:-docker}"

root="$(cd "$(dirname "$0")/.." && pwd)"
src="$root/fixtures/$fixture"
work="$root/work/$fixture"
out="$root/evals/results/phase-0/$fixture"
[[ -d "$src" ]] || { echo "no such fixture: $src" >&2; exit 2; }

rm -rf "$work" "$out"
mkdir -p "$work" "$out"
cp -r "$src/." "$work/"
rm -f "$work/expected.yaml"

mvn_run() {
  local log="$1"; shift
  local rc=0
  if [[ "$mode" == "--local" ]]; then
    (cd "$work" && "${MVN:-mvn}" -B -ntp "$@") >"$log" 2>&1 || rc=$?
  else
    docker run --rm \
      -v "$work":/workspace -w /workspace \
      -v bootshift-phase0-m2:/root/.m2 \
      "$MAVEN_IMAGE" mvn -B -ntp "$@" >"$log" 2>&1 || rc=$?
  fi
  echo "$rc"
}

summarize() {
  # Prints "tests=N failures=N errors=N skipped=N" from surefire XML reports.
  local dir="$work/target/surefire-reports"
  if ! ls "$dir"/TEST-*.xml >/dev/null 2>&1; then echo "tests=0 failures=0 errors=0 skipped=0 (no reports)"; return; fi
  awk 'match($0, /<testsuite [^>]*>/) {
         s = substr($0, RSTART, RLENGTH)
         for (k in want) { if (match(s, k "=\"[0-9]+\"")) { v = substr(s, RSTART + length(k) + 2, RLENGTH - length(k) - 3); sum[k] += v } }
       }
       BEGIN { want["tests"]; want["failures"]; want["errors"]; want["skipped"] }
       END { printf "tests=%d failures=%d errors=%d skipped=%d\n", sum["tests"], sum["failures"], sum["errors"], sum["skipped"] }' \
      "$dir"/TEST-*.xml
}

stage() { echo; echo "== $*"; }

cd "$work"
git init -q -b main
git config core.autocrlf false
git -c user.name=bootshift -c user.email=bootshift@localhost add -A
git -c user.name=bootshift -c user.email=bootshift@localhost commit -q -m "fixture: $fixture (baseline)"

stage "baseline: mvn verify"
rc=$(mvn_run "$out/01-baseline.log" verify)
echo "exit=$rc $(summarize)" | tee "$out/01-baseline.summary"
mvn_run "$out/01-clean.log" clean >/dev/null

stage "recipe: UpgradeSpringBoot_4_0 (rewrite-spring $REWRITE_SPRING_VERSION)"
rc=$(mvn_run "$out/02-recipe-4_0.log" -U \
  "org.openrewrite.maven:rewrite-maven-plugin:$REWRITE_PLUGIN_VERSION:runNoFork" \
  "-Drewrite.recipeArtifactCoordinates=org.openrewrite.recipe:rewrite-spring:$REWRITE_SPRING_VERSION" \
  "-Drewrite.activeRecipes=org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0" \
  "-Drewrite.exportDatatables=true")
echo "exit=$rc" | tee "$out/02-recipe-4_0.summary"
[[ "$rc" == "0" ]] || { echo "recipe run failed, see $out/02-recipe-4_0.log" >&2; exit 1; }
cp -r "$work/target/rewrite" "$out/02-datatables" 2>/dev/null || true
mvn_run "$out/02-clean.log" clean >/dev/null
git add -A && git -c user.name=bootshift -c user.email=bootshift@localhost commit -q -m "recipe: UpgradeSpringBoot_4_0" || echo "(no changes)"

stage "recipe: dev.bootshift.UpgradeSpringBoot_4_1"
cp "$root/recipe/bootshift-boot-4-1.yml" "$work/rewrite.yml"
rc=$(mvn_run "$out/03-recipe-4_1.log" \
  "org.openrewrite.maven:rewrite-maven-plugin:$REWRITE_PLUGIN_VERSION:runNoFork" \
  "-Drewrite.recipeArtifactCoordinates=org.openrewrite.recipe:rewrite-spring:$REWRITE_SPRING_VERSION" \
  "-Drewrite.activeRecipes=dev.bootshift.UpgradeSpringBoot_4_1")
rm -f "$work/rewrite.yml"
echo "exit=$rc" | tee "$out/03-recipe-4_1.summary"
[[ "$rc" == "0" ]] || { echo "recipe run failed, see $out/03-recipe-4_1.log" >&2; exit 1; }
mvn_run "$out/03-clean.log" clean >/dev/null
git add -A && git -c user.name=bootshift -c user.email=bootshift@localhost commit -q -m "recipe: UpgradeSpringBoot_4_1" || echo "(no changes)"

git diff --stat HEAD~2 HEAD >"$out/recipe-diff.stat" 2>/dev/null || true
git diff HEAD~2 HEAD >"$out/recipe.diff" 2>/dev/null || true

stage "verify: mvn verify after recipes"
rc=$(mvn_run "$out/04-verify.log" verify)
echo "exit=$rc $(summarize)" | tee "$out/04-verify.summary"

stage "remaining compiler errors"
grep -E '^\[ERROR\] .*\.java:\[' "$out/04-verify.log" | sed -E 's#.*/src/#src/#' | sort -u | tee "$out/04-errors.txt" || echo "(none)"

echo
echo "results in $out"
