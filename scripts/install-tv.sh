#!/usr/bin/env bash
# Compila Liviano e instálalo en la tele por ADB en red.
# Uso: ./scripts/install-tv.sh 192.168.1.50
set -euo pipefail
TV="${1:?Uso: $0 IP-DE-LA-TELE}"
cd "$(dirname "$0")/.."
./gradlew assembleRelease
adb connect "$TV:5555"
adb -s "$TV:5555" install -r app/build/outputs/apk/release/app-release.apk
adb -s "$TV:5555" shell am start -n dev.liviano.tv/.MainActivity
