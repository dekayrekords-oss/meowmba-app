#!/usr/bin/env python3
"""Build an offline POI pack for Meowmba's navigator from an OpenStreetMap extract.

This is the "Geoapify, but local" half of the navigation feature. Geoapify's Places
API is OSM-derived, so the same category ids answer the same questions from a file on
the phone — no API key, no quota, no network.

Usage:
    pip install osmium
    # grab a region from https://download.geofabrik.de/ , e.g.
    #   curl -LO https://download.geofabrik.de/north-america/us/arizona-latest.osm.pbf
    python3 scripts/build_poi_pack.py arizona-latest.osm.pbf arizona.sqlite

Then install it on the phone:
    adb push arizona.sqlite /sdcard/Download/
    # and move it into the app's files dir, or use the in-app importer

Schema must stay in sync with app/src/main/java/com/example/memba2/nav/OfflinePoiSource.kt
"""
from __future__ import annotations

import os
import sqlite3
import sys

try:
    import osmium
except ImportError:
    sys.exit(
        "pyosmium is required:  pip install osmium\n"
        "(it is the standard reader for .osm.pbf files)"
    )

# OSM tags -> Geoapify-style category ids. Order matters: first match wins, so the
# more specific cuisine-aware rules are applied inside the handler, not here.
TAG_CATEGORIES = {
    ("amenity", "fast_food"): "catering.fast_food",
    ("amenity", "restaurant"): "catering.restaurant",
    ("amenity", "cafe"): "catering.cafe",
    ("amenity", "bar"): "catering.bar",
    ("amenity", "pub"): "catering.pub",
    ("amenity", "fuel"): "service.vehicle.fuel",
    ("amenity", "charging_station"): "service.vehicle.charging_station",
    ("amenity", "car_wash"): "service.vehicle.car_wash",
    ("amenity", "pharmacy"): "healthcare.pharmacy",
    ("amenity", "hospital"): "healthcare.hospital",
    ("amenity", "clinic"): "healthcare.clinic_or_praxis",
    ("amenity", "doctors"): "healthcare.clinic_or_praxis",
    ("amenity", "atm"): "service.financial.atm",
    ("amenity", "bank"): "service.financial.bank",
    ("amenity", "toilets"): "service.toilet",
    ("amenity", "parking"): "parking",
    ("shop", "convenience"): "commercial.convenience",
    ("shop", "supermarket"): "commercial.supermarket",
    ("shop", "department_store"): "commercial.department_store",
    ("shop", "car_repair"): "service.vehicle.repair",
    ("tourism", "hotel"): "accommodation.hotel",
    ("tourism", "motel"): "accommodation.motel",
}

# cuisine=* -> category suffix, so "burger" requests can hit the specific subtree the
# way they do on Geoapify.
CUISINE_SUFFIX = {
    "burger": "burger",
    "pizza": "pizza",
    "mexican": "mexican",
    "chinese": "chinese",
    "japanese": "japanese",
    "sushi": "sushi",
    "sandwich": "sandwich",
    "chicken": "chicken",
    "coffee_shop": "coffee_shop",
    "breakfast": "breakfast",
    "american": "american",
    "italian": "italian",
    "thai": "thai",
    "indian": "indian",
    "barbecue": "barbecue",
    "seafood": "seafood",
    "steak_house": "steak_house",
    "ice_cream": "ice_cream",
    "donut": "donut",
}


def categorize(tags) -> str | None:
    """Return a category id for these tags, or None if this is not a POI we index."""
    for (k, v), base in TAG_CATEGORIES.items():
        if tags.get(k) == v:
            cuisine = (tags.get("cuisine") or "").split(";")[0].strip().lower()
            suffix = CUISINE_SUFFIX.get(cuisine)
            # Only catering categories take a cuisine suffix.
            if suffix and base.startswith("catering."):
                return f"{base}.{suffix}"
            return base
    return None


def address_of(tags) -> str | None:
    house = tags.get("addr:housenumber")
    street = tags.get("addr:street")
    city = tags.get("addr:city")
    parts = []
    if house and street:
        parts.append(f"{house} {street}")
    elif street:
        parts.append(street)
    if city:
        parts.append(city)
    return ", ".join(parts) if parts else None


class PoiHandler(osmium.SimpleHandler):
    def __init__(self, conn: sqlite3.Connection):
        super().__init__()
        self.conn = conn
        self.rows: list[tuple] = []
        self.next_id = 1
        self.count = 0
        self.skipped_unnamed = 0

    def _add(self, tags, lat, lon):
        category = categorize(tags)
        if category is None:
            return
        name = tags.get("name")
        brand = tags.get("brand")
        if not name and not brand:
            # An unnamed POI cannot be asked for by name and is noise in a list.
            self.skipped_unnamed += 1
            return
        self.rows.append(
            (self.next_id, name, brand, category, lat, lon, address_of(tags))
        )
        self.next_id += 1
        self.count += 1
        if len(self.rows) >= 20_000:
            self.flush()

    def flush(self):
        if not self.rows:
            return
        self.conn.executemany(
            "INSERT INTO poi(id,name,brand,category,lat,lon,addr) VALUES (?,?,?,?,?,?,?)",
            self.rows,
        )
        self.rows.clear()

    def node(self, n):
        if not n.tags:
            return
        try:
            self._add(n.tags, n.location.lat, n.location.lon)
        except osmium.InvalidLocationError:
            pass

    def area(self, a):
        # Buildings/plots tagged as POIs (most supermarkets, many restaurants).
        if not a.tags:
            return
        try:
            centroid = a.inner_rings, None  # touch to keep osmium happy on some builds
            lat, lon = _area_centroid(a)
        except Exception:
            return
        if lat is None:
            return
        self._add(a.tags, lat, lon)


def _area_centroid(area):
    """Cheap centroid: mean of the outer ring's nodes. Good enough for 'nearest X'."""
    for ring in area.outer_rings():
        lats, lons, n = 0.0, 0.0, 0
        for node in ring:
            try:
                lats += node.location.lat
                lons += node.location.lon
                n += 1
            except osmium.InvalidLocationError:
                continue
        if n:
            return lats / n, lons / n
    return None, None


def build(pbf_path: str, out_path: str):
    if os.path.exists(out_path):
        os.remove(out_path)

    conn = sqlite3.connect(out_path)
    conn.executescript(
        """
        PRAGMA journal_mode = OFF;
        PRAGMA synchronous = OFF;
        CREATE TABLE poi(
            id INTEGER PRIMARY KEY,
            name TEXT, brand TEXT, category TEXT,
            lat REAL, lon REAL, addr TEXT
        );
        """
    )

    handler = PoiHandler(conn)
    print(f"reading {pbf_path} ...")
    # locations=True gives ways their node coordinates so areas get a centroid.
    handler.apply_file(pbf_path, locations=True, idx="flex_mem")
    handler.flush()
    conn.commit()

    print(f"indexed {handler.count} POIs "
          f"({handler.skipped_unnamed} unnamed skipped)")

    print("building spatial + text indexes ...")
    conn.executescript(
        """
        CREATE VIRTUAL TABLE poi_rtree USING rtree(id, minLat, maxLat, minLon, maxLon);
        INSERT INTO poi_rtree SELECT id, lat, lat, lon, lon FROM poi;

        CREATE VIRTUAL TABLE poi_fts USING fts5(
            name, brand, content='poi', content_rowid='id', tokenize='unicode61'
        );
        INSERT INTO poi_fts(rowid, name, brand) SELECT id, name, brand FROM poi;

        CREATE INDEX idx_poi_category ON poi(category);
        """
    )

    # Coverage bbox, so the app can hold many state packs at once and only open the
    # ones that actually cover where the user is standing.
    conn.execute(
        "CREATE TABLE pack_meta(region TEXT, minLat REAL, maxLat REAL, "
        "minLon REAL, maxLon REAL, poi_count INTEGER)"
    )
    region = os.path.splitext(os.path.basename(out_path))[0]
    conn.execute(
        "INSERT INTO pack_meta SELECT ?, MIN(lat), MAX(lat), MIN(lon), MAX(lon), "
        "COUNT(*) FROM poi",
        (region,),
    )
    conn.commit()
    conn.execute("VACUUM")
    conn.close()

    size_mb = os.path.getsize(out_path) / 1e6
    print(f"wrote {out_path}  ({size_mb:.1f} MB)")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(f"usage: {sys.argv[0]} <region-latest.osm.pbf> <out.sqlite>")
    build(sys.argv[1], sys.argv[2])
