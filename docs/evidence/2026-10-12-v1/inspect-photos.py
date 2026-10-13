"""Decode the original device JPEGs and report pixel/EXIF evidence (requires Pillow)."""
import hashlib
import json
from pathlib import Path

from PIL import Image

results = []
photos = sorted(Path(__file__).parent.glob("photo-default-*.jpg"))
if not photos:
    raise SystemExit("No original photo-default-*.jpg evidence found; nothing was decoded.")
for path in photos:
    with Image.open(path) as image:
        exif = image.getexif()
        results.append({
            "file": path.name,
            "bytes": path.stat().st_size,
            "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            "size": list(image.size),
            "exif_orientation": exif.get(274),
            "has_gps": 34853 in exif,
            "rgb_extrema": image.convert("RGB").getextrema(),
        })
print(json.dumps(results, indent=2, ensure_ascii=False))
