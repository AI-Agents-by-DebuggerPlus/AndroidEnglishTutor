"""Build Desktop.XShoot/dictionary.json from AET topic word lists."""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TOPICS = ROOT / "app" / "src" / "main" / "assets" / "topics"
OUT = ROOT / "Desktop.XShoot" / "dictionary.json"


def main() -> None:
    pairs: dict[str, dict] = {}
    for path in TOPICS.rglob("*.json"):
        data = json.loads(path.read_text(encoding="utf-8"))
        for row in data.get("words", []):
            en = str(row.get("en", "")).strip()
            ru = str(row.get("ru", "")).strip()
            if not en or not ru:
                continue
            key = en.casefold()
            if key in pairs:
                continue
            pairs[key] = {
                "en": en,
                "ru": ru.split("|")[0].split(";")[0].strip(),
                "concise": True,
            }
    payload = {"words": sorted(pairs.values(), key=lambda item: item["en"].casefold())}
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"words={len(payload['words'])} -> {OUT}")


if __name__ == "__main__":
    main()
