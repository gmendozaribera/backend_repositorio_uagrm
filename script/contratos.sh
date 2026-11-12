#!/usr/bin/env bash
# Control de calidad y de cambios de los contratos OpenAPI (source/spec/src/main/resources/openapi).
#   1. Lint (Redocly): estructura valida + reglas del equipo (redocly.yaml).
#   2. Cambios incompatibles (oasdiff): compara cada contrato con su version en la rama base.
#      Si hay un breaking change, el build falla salvo que se haya subido la version MAYOR del contrato
#      (info.version), que es la senal explicita de que el frontend debe adaptarse.
# Uso: script/contratos.sh [rama-base]      (por defecto origin/main)
# Requiere: Node (npx) y Docker.
set -euo pipefail
export MSYS_NO_PATHCONV=1   # Git Bash en Windows: no convertir las rutas de los contenedores

BASE="${1:-origin/main}"
DIR="source/spec/src/main/resources/openapi"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
RAIZ="$(pwd)"
[ -n "${WINDIR:-}" ] && RAIZ="$(pwd -W)"

echo "== Lint de contratos"
npx -y @redocly/cli@2 lint

mayor() { sed -n 's/^  version: *\([0-9]*\)\..*/\1/p' "$1" | head -1; }

echo "== Cambios incompatibles contra ${BASE}"
fallo=0
for contrato in "$DIR"/*.yaml; do
  nombre="$(basename "$contrato")"
  if ! git show "${BASE}:${contrato}" > "$TMP/$nombre" 2>/dev/null; then
    echo "   ${nombre}: contrato nuevo, sin version base"
    continue
  fi
  if docker run --rm -v "$RAIZ:/repo" -v "$TMP:/base" tufin/oasdiff:latest \
      breaking "/base/$nombre" "/repo/$contrato" --fail-on ERR; then
    echo "   ${nombre}: sin cambios incompatibles"
  elif [ "$(mayor "$contrato")" -gt "$(mayor "$TMP/$nombre")" ]; then
    echo "   ${nombre}: cambios incompatibles ACEPTADOS (version mayor $(mayor "$TMP/$nombre") -> $(mayor "$contrato"))"
  else
    echo "   ${nombre}: cambios incompatibles sin subir la version mayor de info.version"
    fallo=1
  fi
done
exit "$fallo"
