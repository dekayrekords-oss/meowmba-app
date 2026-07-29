#!/usr/bin/env bash
# Build offline POI packs for the states Meowmba travels in.
#
# Downloads each Geofabrik extract, builds the pack, then DELETES the extract before
# moving on — the ten .osm.pbf files together are several GB and the Mac does not
# have room for them all at once.
#
# Usage:  scripts/build_all_poi_packs.sh [outdir]      (default ~/memba-native/osm)
set -uo pipefail

OUT="${1:-$HOME/memba-native/osm}"
BUILDER="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/build_poi_pack.py"
BASE="https://download.geofabrik.de/north-america/us"

# Smallest first, so a usable pack exists early and any systemic failure shows up
# before hours have been spent on the big ones.
STATES=(
  nevada new-mexico utah oregon arizona
  colorado washington florida texas california
)

mkdir -p "$OUT"
cd "$OUT" || exit 1

python3 -c "import osmium" 2>/dev/null || {
  echo "pyosmium missing:  pip install osmium"; exit 1
}

for s in "${STATES[@]}"; do
  pack="$OUT/$s.sqlite"
  if [ -f "$pack" ]; then
    echo "== $s: pack exists, skipping"
    continue
  fi

  free_gb=$(df -g "$OUT" | awk 'NR==2 {print $4}')
  if [ "${free_gb:-0}" -lt 4 ]; then
    echo "!! only ${free_gb}GB free, stopping before $s"
    exit 1
  fi

  pbf="$OUT/$s-latest.osm.pbf"
  echo "== $s: downloading"
  if ! curl -fsSL -o "$pbf" "$BASE/$s-latest.osm.pbf"; then
    echo "!! $s: download failed, skipping"
    rm -f "$pbf"
    continue
  fi
  echo "   $(du -h "$pbf" | cut -f1) downloaded"

  echo "== $s: building pack"
  if python3 "$BUILDER" "$pbf" "$pack"; then
    echo "   -> $(du -h "$pack" | cut -f1)"
  else
    echo "!! $s: build failed"
    rm -f "$pack"
  fi

  rm -f "$pbf"        # reclaim immediately, this is the whole point
done

echo
echo "== packs built:"
ls -lh "$OUT"/*.sqlite 2>/dev/null | awk '{print "   "$9"  "$5}'
echo "== total: $(du -ch "$OUT"/*.sqlite 2>/dev/null | tail -1 | cut -f1)"
