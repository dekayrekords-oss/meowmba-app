#!/bin/bash
# Update the MASTER_PLAN.md checklist by scanning the codebase.
# Run from the repo root: ./update-master-plan.sh
set -e
cd "$(dirname "$0")"
echo "🔍 Scanning codebase..."
python3 scripts/update_master_plan.py --skip-tests
echo ""
echo "Done. Open MASTER_PLAN.md to see the updated checklist."
