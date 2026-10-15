#!/usr/bin/env bash
# Instala el servidor de streaming de Stremio en una PC Linux como servicio de usuario (systemd).
# La tele reproduce los torrents a través de esta PC: http://IP-DE-TU-PC:11470
# Requisitos: Node.js >= 18, curl, systemd. No necesita sudo.
set -euo pipefail

VERSION="${STREMIO_SERVER_VERSION:-4.20.12}"
DIR="$HOME/stremio-server"
NODE="$(command -v node || true)"
[ -n "$NODE" ] || { echo "Falta Node.js (https://nodejs.org o nvm)"; exit 1; }
NODE="$(readlink -f "$NODE")"

mkdir -p "$DIR" "$HOME/.config/systemd/user"
echo "Descargando server.js v$VERSION desde dl.strem.io…"
curl -fsSLo "$DIR/server.js" "https://dl.strem.io/server/v$VERSION/desktop/server.js"

sed "s|@NODE@|$NODE|" "$(dirname "$0")/stremio-server.service" > "$HOME/.config/systemd/user/stremio-server.service"
systemctl --user daemon-reload
systemctl --user enable --now stremio-server

for _ in $(seq 1 30); do curl -fs localhost:11470/settings >/dev/null && break; sleep 1; done
IP="$(hostname -I | awk '{print $1}')"
echo
echo "Servidor activo. En la app Liviano → Ajustes → Servidor de torrents pon:"
echo "    http://$IP:11470"
echo "Para que arranque aunque no inicies sesión en la PC: sudo loginctl enable-linger $USER"
