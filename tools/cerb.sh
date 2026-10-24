#!/usr/bin/env bash
# Cerberus dev tool for Linux/macOS - same commands as tools/cerb.ps1 (the Windows version).
#
#   tools/cerb.sh status | setup | build | deploy | start | run | backup
#
# Settings (env): CERB_SERVER_DIR (default <repo>/test-server), CERB_SERVER_JAR,
#   BUILD_JAVA_HOME, RUN_JAVA_HOME, JAVA_XMS (1G), JAVA_XMX (4G), CERB_MVN (default ./mvnw).
set -euo pipefail

FOLIA_VERSION=1.21.11
FOLIA_BUILD=14
FOLIA_JAR="folia-${FOLIA_VERSION}-${FOLIA_BUILD}.jar"
FOLIA_SHA256=f52c408490a0225611e67907a3ca19f7e6da2c6bc899e715d5f46844e7103c39
FOLIA_URL="https://fill-data.papermc.io/v1/objects/${FOLIA_SHA256}/${FOLIA_JAR}"
MIN_JAVA=21
PLUGIN_JAR=Cerberus.jar
PLUGIN_DIR=CerberusPlugin
KEEP_DATA_BACKUPS=10

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVER_DIR="${CERB_SERVER_DIR:-$REPO/test-server}"
BUILT_JAR="$REPO/target/$PLUGIN_JAR"

ok()   { printf '  [ok]   %s\n' "$*"; }
warn() { printf '  [warn] %s\n' "$*"; }
fail() { printf '\n  [FAIL] %s\n' "$*"; exit 1; }
step() { printf '\n== %s\n' "$*"; }

java_major() { # $1 = java home; prints major version or 0
  local rel="$1/release" v
  if [[ -f "$rel" ]]; then
    v="$(sed -n 's/^JAVA_VERSION="\([^"]*\)".*/\1/p' "$rel")"
    [[ "$v" == 1.* ]] && { echo "${v#1.}" | cut -d. -f1; return; }
    echo "${v%%[.+-]*}"; return
  fi
  echo 0
}

find_java() { # $1 = explicit var name, $2 = need javac (1/0); prints home
  local var="$1" need_jdk="$2" explicit="${!1:-}" best="" best_major=0 h m
  if [[ -n "$explicit" ]]; then
    [[ -x "$explicit/bin/java" ]] || fail "$var is set to '$explicit' but there is no Java there."
    m="$(java_major "$explicit")"
    [[ "$m" -eq 0 || "$m" -ge $MIN_JAVA ]] || fail "$var points to Java $m; need $MIN_JAVA+."
    [[ "$need_jdk" == 0 || -x "$explicit/bin/javac" ]] || fail "$var points to a JRE; building needs a JDK."
    echo "$explicit"; return
  fi
  local candidates=("${JAVA_HOME:-}")
  for h in /usr/lib/jvm/* /opt/*/ /opt/*/*/ /Library/Java/JavaVirtualMachines/*/Contents/Home; do candidates+=("${h%/}"); done
  if command -v java >/dev/null 2>&1; then candidates+=("$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"); fi
  for h in "${candidates[@]}"; do
    [[ -n "$h" && -x "$h/bin/java" ]] || continue
    [[ "$need_jdk" == 0 || -x "$h/bin/javac" ]] || continue
    m="$(java_major "$h")"
    if [[ "$m" -ge $MIN_JAVA && "$m" -gt "$best_major" ]]; then best="$h"; best_major="$m"; fi
  done
  echo "$best"
}

require_java() { # $1 var, $2 need jdk, $3 purpose
  local h; h="$(find_java "$1" "$2")"
  [[ -n "$h" ]] || fail "No Java $MIN_JAVA+ found for $3. Install a JDK 21 (https://adoptium.net) or set $1."
  echo "$h"
}

server_jar() {
  if [[ -n "${CERB_SERVER_JAR:-}" ]]; then echo "$SERVER_DIR/$CERB_SERVER_JAR"; return; fi
  if [[ -f "$SERVER_DIR/$FOLIA_JAR" ]]; then echo "$SERVER_DIR/$FOLIA_JAR"; return; fi
  local other; other="$(ls -t "$SERVER_DIR"/folia-*.jar 2>/dev/null | head -1 || true)"
  echo "${other:-$SERVER_DIR/$FOLIA_JAR}"
}

prop() { # $1 key, $2 default
  local f="$SERVER_DIR/server.properties" v=""
  [[ -f "$f" ]] && v="$(sed -n "s/^$1=//p" "$f" | tail -1)"
  echo "${v:-$2}"
}

eula_ok() { grep -qiE '^\s*eula\s*=\s*true\s*$' "$SERVER_DIR/eula.txt" 2>/dev/null; }

running() {
  local port host; port="$(prop server-port 25565)"; host="$(prop server-ip 127.0.0.1)"
  [[ "$host" == "0.0.0.0" ]] && host=127.0.0.1
  timeout 1 bash -c "exec 3<>/dev/tcp/$host/$port" 2>/dev/null
}

assert_stopped() {
  running && fail "The server is running on port $(prop server-port 25565). Type 'stop' in its console, then $1 again."
  return 0
}

backup_plugin_data() {
  local data="$SERVER_DIR/plugins/$PLUGIN_DIR" root="$SERVER_DIR/backups/plugin-data" dest
  [[ -d "$data" ]] || return 0
  dest="$root/$(date +%Y%m%d-%H%M%S)"
  mkdir -p "$dest"
  find "$data" -maxdepth 1 -type f \( -name '*.db' -o -name '*.yml' -o -name '*.yaml' \) -exec cp {} "$dest/" \;
  ok "plugin data backed up to backups/plugin-data/$(basename "$dest")"
  ls -1d "$root"/*/ 2>/dev/null | sort -r | tail -n +$((KEEP_DATA_BACKUPS + 1)) | xargs -r rm -rf
}

cmd_status() {
  step "Cerberus dev status"
  echo "  repo: $REPO ($(git -C "$REPO" rev-parse --abbrev-ref HEAD 2>/dev/null || echo '?'))"
  local b r; b="$(find_java BUILD_JAVA_HOME 1)"; r="$(find_java RUN_JAVA_HOME 0)"
  [[ -n "$b" ]] && ok "build JDK: Java $(java_major "$b") $b" || warn "no JDK $MIN_JAVA+ for building"
  [[ -n "$r" ]] && ok "server Java: Java $(java_major "$r") $r" || warn "no Java $MIN_JAVA+ for the server"
  step "Server"
  [[ -d "$SERVER_DIR" ]] || { warn "no server folder at $SERVER_DIR (run setup)"; return 0; }
  ok "folder: $SERVER_DIR"
  [[ -f "$(server_jar)" ]] && ok "server jar: $(basename "$(server_jar)")" || warn "server jar missing (run setup)"
  eula_ok && ok "eula.txt accepted" || warn "eula.txt not accepted yet (you accept it yourself)"
  ok "port $(prop server-port 25565), bind '$(prop server-ip '')', online-mode $(prop online-mode true), level $(prop level-name world)"
  [[ -f "$SERVER_DIR/plugins/$PLUGIN_JAR" ]] && ok "plugin deployed: plugins/$PLUGIN_JAR" || warn "plugin not deployed yet"
  running && ok "server is RUNNING" || echo "  not running"
  echo "  log: $SERVER_DIR/logs/latest.log"
}

cmd_setup() {
  step "First-time setup"
  require_java BUILD_JAVA_HOME 1 building >/dev/null
  mkdir -p "$SERVER_DIR/plugins"
  if [[ -f "$SERVER_DIR/$FOLIA_JAR" ]]; then
    ok "$FOLIA_JAR already present - not downloading"
  else
    echo "  downloading $FOLIA_JAR ..."
    curl -fsSL -A cerberus-dev-setup -o "$SERVER_DIR/$FOLIA_JAR.part" "$FOLIA_URL" || fail "download failed"
    echo "$FOLIA_SHA256  $SERVER_DIR/$FOLIA_JAR.part" | sha256sum -c --quiet - \
      || { rm -f "$SERVER_DIR/$FOLIA_JAR.part"; fail "SHA-256 check failed; nothing installed"; }
    mv "$SERVER_DIR/$FOLIA_JAR.part" "$SERVER_DIR/$FOLIA_JAR"
    ok "downloaded and verified $FOLIA_JAR"
  fi
  eula_ok && ok "eula.txt accepted" || warn "EULA not accepted: read https://aka.ms/MinecraftEULA, then set eula=true in $SERVER_DIR/eula.txt yourself (created on first start)."
}

cmd_build() {
  step "Build"
  local j; j="$(require_java BUILD_JAVA_HOME 1 building)"
  echo "  using Java $(java_major "$j") at $j"
  local wj="$REPO/.mvn/wrapper/maven-wrapper.jar"
  if [[ -f "$wj" && ! -s "$wj" ]]; then rm -f "$wj"; warn "removed an empty maven-wrapper.jar from a failed download"; fi
  (cd "$REPO" && JAVA_HOME="$j" PATH="$j/bin:$PATH" ${CERB_MVN:-./mvnw} -B -q clean package) \
    || fail "Build failed. Scroll up for the compiler or test error."
  [[ -f "$BUILT_JAR" ]] || fail "Build finished but $BUILT_JAR is missing."
  ok "built target/$PLUGIN_JAR"
}

cmd_deploy() {
  assert_stopped deploy
  cmd_build
  step "Deploy"
  [[ -d "$SERVER_DIR/plugins" ]] || fail "No $SERVER_DIR/plugins. Run setup first (or set CERB_SERVER_DIR)."
  backup_plugin_data
  cp "$BUILT_JAR" "$SERVER_DIR/plugins/$PLUGIN_JAR"
  ok "copied to plugins/$PLUGIN_JAR"
}

cmd_start() { # $1 = skip backup (1/0)
  step "Start"
  running && fail "A server is already running on port $(prop server-port 25565)."
  local jar; jar="$(server_jar)"
  [[ -f "$jar" ]] || fail "Server jar not found: $jar. Run setup first."
  eula_ok || warn "eula.txt not accepted: the server will create it and stop. Accept it yourself, then start again."
  local j; j="$(require_java RUN_JAVA_HOME 0 'running the server')"
  [[ "${1:-0}" == 1 ]] || backup_plugin_data
  echo "  Ready when you see: [CerberusPlugin] Cerberus Plugin has been enabled!   Stop it by typing: stop"
  cd "$SERVER_DIR"
  "$j/bin/java" -Xms"${JAVA_XMS:-1G}" -Xmx"${JAVA_XMX:-4G}" \
    -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 \
    -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC \
    -XX:G1HeapRegionSize=8M -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 \
    -XX:G1MixedGCLiveThresholdPercent=90 -XX:+HeapDumpOnOutOfMemoryError \
    -jar "$(basename "$jar")" nogui
}

cmd_backup() {
  assert_stopped "back up"
  step "Backup (worlds + plugin data)"
  local level dest; level="$(prop level-name world)"; dest="$SERVER_DIR/backups/full-$(date +%Y%m%d-%H%M%S)"
  mkdir -p "$dest"
  for w in "$level" "${level}_nether" "${level}_the_end"; do
    [[ -d "$SERVER_DIR/$w" ]] && cp -r "$SERVER_DIR/$w" "$dest/" && ok "copied $w"
  done
  [[ -d "$SERVER_DIR/plugins/$PLUGIN_DIR" ]] && cp -r "$SERVER_DIR/plugins/$PLUGIN_DIR" "$dest/" && ok "copied plugins/$PLUGIN_DIR"
  for f in server.properties bukkit.yml spigot.yml ops.json whitelist.json; do [[ -f "$SERVER_DIR/$f" ]] && cp "$SERVER_DIR/$f" "$dest/"; done
  [[ -d "$SERVER_DIR/config" ]] && cp -r "$SERVER_DIR/config" "$dest/"
  ok "backup complete: $dest ($(du -sh "$dest" | cut -f1))"
}

case "${1:-status}" in
  status) cmd_status ;;
  setup)  cmd_setup ;;
  build)  cmd_build ;;
  deploy) cmd_deploy; echo; echo "Deployed. Start the server to load it." ;;
  start)  cmd_start 0 ;;
  run)    assert_stopped run; cmd_deploy; cmd_start 1 ;;
  backup) cmd_backup ;;
  *) fail "Unknown command '$1'. Use: status, setup, build, deploy, start, run, backup." ;;
esac
