#!/usr/bin/env bash
set -euo pipefail

ROOT="$(pwd)"
TOOLS="$ROOT/.android-build"
SDK="$TOOLS/android-sdk"
GRADLE_HOME="$TOOLS/gradle-8.9"
mkdir -p "$TOOLS" "$SDK/cmdline-tools" "$ROOT/public"

export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"

JDK_TGZ="$TOOLS/jdk17.tar.gz"
if [ ! -x "$TOOLS/jdk17/bin/java" ]; then
  curl -L --fail --retry 3 -o "$JDK_TGZ" "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
  rm -rf "$TOOLS/jdk17"
  mkdir -p "$TOOLS/jdk17"
  tar -xzf "$JDK_TGZ" -C "$TOOLS/jdk17" --strip-components=1
fi
export JAVA_HOME="$TOOLS/jdk17"
export PATH="$JAVA_HOME/bin:$PATH"
java -version

if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  CMD_ZIP="$TOOLS/cmdline-tools.zip"
  curl -L --fail --retry 3 -o "$CMD_ZIP" "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  rm -rf "$TOOLS/cmdline-tools-unpacked"
  mkdir -p "$TOOLS/cmdline-tools-unpacked"
  unzip -q "$CMD_ZIP" -d "$TOOLS/cmdline-tools-unpacked"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$TOOLS/cmdline-tools-unpacked/cmdline-tools" "$SDK/cmdline-tools/latest"
fi
export PATH="$SDK/cmdline-tools/latest/bin:$SDK/platform-tools:$PATH"

yes | sdkmanager --licenses >/dev/null || true
sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"

if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  GRADLE_ZIP="$TOOLS/gradle-8.9-bin.zip"
  curl -L --fail --retry 3 -o "$GRADLE_ZIP" "https://services.gradle.org/distributions/gradle-8.9-bin.zip"
  unzip -q "$GRADLE_ZIP" -d "$TOOLS"
fi
export PATH="$GRADLE_HOME/bin:$PATH"

gradle -p "$ROOT/vehicle-km-log" --no-daemon --stacktrace assembleDebug
cp "$ROOT/vehicle-km-log/app/build/outputs/apk/debug/app-debug.apk" "$ROOT/public/Vehicle-KM-Log.apk"

cat > "$ROOT/public/index.html" <<'HTML'
<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Vehicle KM Log</title><style>body{font-family:Arial,sans-serif;background:#f3f4f6;color:#111827;display:grid;place-items:center;min-height:100vh;margin:0}.card{max-width:520px;padding:32px;text-align:center;background:#fff;border-radius:16px}.btn{display:inline-block;background:#0f766e;color:#fff;padding:16px 24px;border-radius:10px;text-decoration:none;font-weight:700}</style></head><body><div class="card"><h1>Vehicle KM Log</h1><p>Daily opening and closing kilometres, saved history and Excel export.</p><a class="btn" href="/Vehicle-KM-Log.apk" download>Download APK</a></div></body></html>
HTML

ls -lh "$ROOT/public/Vehicle-KM-Log.apk"
