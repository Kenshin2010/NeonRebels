#!/usr/bin/env bash
# Compiles the libGDX-free model and progress code with Kotlin and runs ModelChecks and
# ProgressChecks, without Gradle.
# Uses `kotlinc` from PATH, or a kotlin-compiler-embeddable already present in the Gradle cache.
set -euo pipefail
cd "$(dirname "$0")/.."
work_dir=$(mktemp -d "${TMPDIR:-/tmp}/nova-model.XXXXXX")
trap 'rm -rf "$work_dir"' EXIT

src=core/src/main/java/com/manroid/neonrebels
mapfile -t sources < <(find "$src/model" "$src/progress" -name '*.kt' -print)
sources+=(
    "$src/input/ButtonLayout.kt"
    "$src/input/PointerButtons.kt"
    core/src/test/java/com/manroid/neonrebels/model/ModelChecks.kt
    core/src/test/java/com/manroid/neonrebels/progress/ProgressChecks.kt
)

cache="${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1"
# Newest cached jar of a module, optionally limited to one version.
cached_jar() {
    local module=$1 version=${2:-}
    { find "$cache/$module"/${version:-*} -name '*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' \
        -print 2>/dev/null || true; } | sort -V | tail -n 1
}

if command -v kotlinc >/dev/null 2>&1; then
    kotlinc -jvm-target 11 -d "$work_dir" "${sources[@]}"
    stdlib=$(dirname "$(readlink -f "$(command -v kotlinc)")")/../lib/kotlin-stdlib.jar
else
    compiler=$(cached_jar org.jetbrains.kotlin/kotlin-compiler-embeddable)
    if [[ -z "$compiler" ]]; then
        echo "Need kotlinc on PATH or kotlin-compiler-embeddable in the Gradle cache." >&2
        exit 1
    fi
    version=$(basename "$(dirname "$(dirname "$compiler")")")
    stdlib=$(cached_jar org.jetbrains.kotlin/kotlin-stdlib "$version")
    compiler_cp="$compiler:$stdlib"
    for module in org.jetbrains.kotlin/kotlin-script-runtime org.jetbrains.kotlin/kotlin-daemon-embeddable; do
        compiler_cp+=":$(cached_jar "$module" "$version")"
    done
    compiler_cp+=":$(cached_jar org.jetbrains.intellij.deps/trove4j)"
    compiler_cp+=":$(cached_jar org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm)"
    compiler_cp+=":$(cached_jar org.jetbrains/annotations 13.0)"
    echo "Using cached Kotlin compiler $version"
    java -cp "$compiler_cp" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
        -no-stdlib -no-reflect -classpath "$stdlib" -jvm-target 11 -d "$work_dir" "${sources[@]}"
fi

java -ea -cp "$work_dir:$stdlib" com.manroid.neonrebels.model.ModelChecks
