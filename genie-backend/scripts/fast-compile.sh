#!/usr/bin/env bash
# Incremental compile for the local Docker loop.
# Few changed .java files → javac + Lombok. Otherwise → mvn compile.
set -euo pipefail
cd "$(dirname "$0")/.."

stamp=target/classes/.compile-ok
trigger=target/classes/.reloadtrigger
lib=target/genie-backend/lib
mkdir -p target/classes

copy_changed_resources() {
  local f rel dest
  while IFS= read -r f; do
    [ -z "$f" ] && continue
    rel="${f#src/main/resources/}"
    dest="target/classes/$rel"
    mkdir -p "$(dirname "$dest")"
    cp "$f" "$dest"
    echo "fast-compile: resource $rel"
  done < <(find src/main/resources -type f -newer "$stamp" -print 2>/dev/null || true)
}

if [ ! -d "$lib" ]; then
  echo "fast-compile: runtime lib missing; run a full package first" >&2
  exit 1
fi

if [ ! -f "$stamp" ] || [ pom.xml -nt "$stamp" ]; then
  echo "fast-compile: mvn compile (cold or pom.xml changed)"
  mvn -o -Dmaven.test.skip=true -Djacoco.skip=true -s aliyun-settings.xml compile
  touch "$stamp" "$trigger"
  exit 0
fi

mapfile -t changed < <(find src/main/java -name '*.java' -newer "$stamp" -print | sort)
count=${#changed[@]}

if [ "$count" -eq 0 ]; then
  copy_changed_resources
  touch "$stamp" "$trigger"
  echo "fast-compile: no java changes"
  exit 0
fi

if [ "$count" -gt 20 ]; then
  echo "fast-compile: mvn compile ($count files)"
  mvn -o -Dmaven.test.skip=true -Djacoco.skip=true -s aliyun-settings.xml compile
  touch "$stamp" "$trigger"
  exit 0
fi

lombok=$(ls -1 "$lib"/lombok-*.jar 2>/dev/null | head -n 1)
if [ -z "$lombok" ]; then
  echo "fast-compile: lombok jar not found in $lib" >&2
  exit 1
fi

echo "fast-compile: javac $count file(s)"
printf '  %s\n' "${changed[@]}"
javac --release 17 -parameters -encoding UTF-8 \
  -cp "$lombok:target/classes:$lib/*" \
  -processorpath "$lombok" \
  -d target/classes \
  "${changed[@]}"
copy_changed_resources
touch "$stamp" "$trigger"
echo "fast-compile: done (touch .reloadtrigger)"
