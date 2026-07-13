#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

NODE_MAJOR="$(node -p "process.versions.node.split('.')[0]" 2>/dev/null || echo "0")"
if [ "$NODE_MAJOR" -lt 22 ]; then
  echo "Node.js 22+ is required (detected: $(node -v 2>/dev/null || echo 'none'))."
  exit 1
fi

echo "Installing dependencies from package-lock with npm ci..."
npm run bootstrap:ci
