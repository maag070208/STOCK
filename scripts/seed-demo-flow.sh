#!/usr/bin/env bash
# Siembra el flujo del negocio: facturas de compra con sus materiales (recibidas, contadas y colocadas, que es lo que
# mete existencias) y órdenes de venta en tres estados. Va contra el API, así que todo pasa por las mismas reglas que
# usa una persona. Antes tiene que estar el catálogo: scripts/seed-demo.sh.
set -euo pipefail
exec python3 "$(dirname "${BASH_SOURCE[0]}")/seed-demo-flow.py"
