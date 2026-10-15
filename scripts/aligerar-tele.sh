#!/usr/bin/env bash
# Aligera una tele Android con poca RAM: quita animaciones, limpia caché y
# desactiva (para el usuario 0, reversible) las apps que pases como argumentos.
# Uso: ./scripts/aligerar-tele.sh IP-DE-LA-TELE [paquete1 paquete2 ...]
# Revertir una app: adb shell cmd package install-existing <paquete>
set -euo pipefail
TV="${1:?Uso: $0 IP-DE-LA-TELE [paquetes...]}"; shift
A="adb -s $TV:5555"
adb connect "$TV:5555"
for k in window_animation_scale transition_animation_scale animator_duration_scale; do
  $A shell settings put global "$k" 0
done
$A shell pm trim-caches 4G
for p in "$@"; do
  printf '%-45s ' "$p"; $A shell pm uninstall --user 0 "$p"
done
$A shell df -h /data | tail -1
