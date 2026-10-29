"""
Canonical AET topic for Hermes Q&A practice is authored at:

  app/src/main/assets/topics/Hermes/hermes_english_practice.json

Source guide: Docs/Hermes-English-Practice-QA.md

This script only validates that the JSON loads and prints counts
(it does not overwrite the topic).
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app" / "src" / "main" / "assets" / "topics" / "Hermes" / "hermes_english_practice.json"


def main() -> int:
    data = json.loads(OUT.read_text(encoding="utf-8"))
    w, p, s = len(data["words"]), len(data["phrases"]), len(data["sentences"])
    print(f"{OUT.relative_to(ROOT)}: {w}w / {p}p / {s}s  id={data.get('id')!r}")
    if data.get("id") != "hermes_english_practice":
        print("ERROR: unexpected id", file=sys.stderr)
        return 1
    if w < 1 or p < 1 or s < 1:
        print("ERROR: empty section", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
