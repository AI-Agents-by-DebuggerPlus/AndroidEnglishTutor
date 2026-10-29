"""Load XSHOOT guessed-word archive for lesson generators."""
from __future__ import annotations

import json
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def archive_paths() -> list[Path]:
    appdata = os.environ.get("APPDATA", "")
    return [
        ROOT / "Files" / "xshoot_archive.json",
        ROOT / "app" / "src" / "main" / "assets" / "xshoot_archive.json",
        Path(appdata) / "AETXShoot" / "guessed_archive.json" if appdata else Path(),
    ]


def known_english() -> set[str]:
    keys: set[str] = set()
    for path in archive_paths():
        if not path or not path.is_file():
            continue
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except Exception:
            continue
        for word in data.get("words", []):
            key = str(word).strip().casefold()
            if key:
                keys.add(key)
    return keys


def filter_words(words: list[tuple[str, str]]) -> list[tuple[str, str]]:
    known = known_english()
    if not known:
        return words
    return [(en, ru) for en, ru in words if en.casefold() not in known]
