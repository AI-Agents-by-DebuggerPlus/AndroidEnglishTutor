"""Build the everyday-life topic: 1000 English/Russian word cards."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = Path(__file__).with_name("everyday_1000_words.tsv")
OUT = ROOT / "app" / "src" / "main" / "assets" / "topics" / "Everyday" / "everyday_thousand.json"


def main() -> None:
    seen: set[str] = set()
    words = []
    for line in SRC.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        en, ru = line.split("\t", 1)
        en, ru = en.strip(), ru.strip()
        key = en.casefold()
        if key in seen:
            continue
        if not en or not ru:
            raise SystemExit(f"empty pair: {line!r}")
        seen.add(key)
        words.append({"en": en, "ru": ru})
    if len(words) < 1000:
        raise SystemExit(f"expected at least 1000 words, got {len(words)}")
    words = words[:1000]
    payload = {
        "id": "everyday_thousand",
        "titleRu": "Повседневная лексика",
        "words": words,
        "phrases": [],
        "sentences": [],
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {OUT} ({len(words)} words)")


if __name__ == "__main__":
    main()
