# -*- coding: utf-8 -*-
"""Build topic JSON from word banks + handcrafted phrases/sentences (no template filler)."""
from __future__ import annotations

import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[1]
TOPICS_DIR = ROOT / "app/src/main/assets/topics"

TARGETS = {
    "general_conversation": (300, 150, 120),
    "food": (200, 150, 100),
    "tools": (200, 150, 100),
    "bottle_depot": (200, 150, 100),
    "ai_engineering": (200, 150, 100),
    "trading": (200, 150, 100),
}

TITLES = {
    "general_conversation": "Общий разговорный",
    "food": "Еда",
    "tools": "Инструменты",
    "bottle_depot": "Разговор на работе Bottle Depot",
    "ai_engineering": "ИИ-инженеринг",
    "trading": "Трейдинг",
}

from topic_extra_banks import (  # noqa: E402
    AI_EXTRA,
    AI_RU,
    BOTTLE_EXTRA,
    FOOD_EXTRA,
    GENERAL_EXTRA,
    TOOLS_EXTRA,
    TRADING_EXTRA,
)
from topic_ready_content import (  # noqa: E402
    AI_PHRASES,
    AI_SENTENCES,
    BOTTLE_PHRASES,
    BOTTLE_SENTENCES,
    FOOD_PHRASES,
    FOOD_SENTENCES,
    GENERAL_PHRASES,
    GENERAL_SENTENCES,
    TOOLS_PHRASES,
    TOOLS_SENTENCES,
    TRADING_PHRASES,
    TRADING_SENTENCES,
)


def normalize_en(en: str) -> str:
    return en.strip().lower()


def unique_words(
    existing: list[dict],
    extras: list[tuple[str, str]],
    used_en: set[str],
    target: int,
    ru_override: dict[str, str] | None = None,
) -> list[dict]:
    out: list[dict] = []
    for item in existing:
        en = normalize_en(item["en"])
        if en in used_en:
            continue
        used_en.add(en)
        ru = (ru_override or {}).get(en, item["ru"])
        out.append({"en": item["en"].strip(), "ru": ru})
    for en_raw, ru in extras:
        if len(out) >= target:
            break
        en = normalize_en(en_raw)
        if not en or en in used_en:
            continue
        used_en.add(en)
        out.append({"en": en_raw.strip(), "ru": (ru_override or {}).get(en, ru)})
    if len(out) < target:
        raise SystemExit(f"Need {target} words, got {len(out)}. Add more extras.")
    return out[:target]


def take_ready(
    items: list[tuple[str, str, list[str]]],
    target: int,
    word_lemmas: set[str],
    label: str,
) -> list[dict]:
    """Take handcrafted items; prefer those whose uses intersect topic words."""
    seen: set[str] = set()
    primary: list[dict] = []
    secondary: list[dict] = []
    for en_raw, ru, uses in items:
        en_key = normalize_en(en_raw)
        if not en_key or en_key in seen:
            continue
        seen.add(en_key)
        uses_norm = [normalize_en(u) for u in uses if u and normalize_en(u)]
        # Keep uses that match topic lemmas; if none match, keep original uses for linkage.
        linked = [u for u in uses_norm if u in word_lemmas] or uses_norm
        row = {"en": en_raw.strip(), "ru": ru.strip(), "uses": linked}
        if any(u in word_lemmas for u in uses_norm):
            primary.append(row)
        else:
            secondary.append(row)
    out = (primary + secondary)[:target]
    if len(out) < target:
        raise SystemExit(
            f"{label}: need {target} handcrafted items, got {len(out)}. "
            "Extend topic_ready_content.py (no template filler)."
        )
    return out


EXTRAS = {
    "general_conversation": GENERAL_EXTRA,
    "food": FOOD_EXTRA,
    "tools": TOOLS_EXTRA,
    "bottle_depot": BOTTLE_EXTRA,
    "ai_engineering": AI_EXTRA,
    "trading": TRADING_EXTRA,
}

READY = {
    "general_conversation": (GENERAL_PHRASES, GENERAL_SENTENCES),
    "food": (FOOD_PHRASES, FOOD_SENTENCES),
    "tools": (TOOLS_PHRASES, TOOLS_SENTENCES),
    "bottle_depot": (BOTTLE_PHRASES, BOTTLE_SENTENCES),
    "ai_engineering": (AI_PHRASES, AI_SENTENCES),
    "trading": (TRADING_PHRASES, TRADING_SENTENCES),
}

NOTES = {
    "ai_engineering": (
        "Готовые фразы и предложения по ИИ-инженерингу; для когнатов и аббревиатур "
        "в ru — определение/расшифровка, не транслитерация."
    ),
    "trading": (
        "Готовые фразы и предложения по трейдингу и инвестициям с полезным смыслом по теме."
    ),
}


def main() -> None:
    used: set[str] = set()
    order = [
        "general_conversation",
        "food",
        "tools",
        "bottle_depot",
        "ai_engineering",
        "trading",
    ]
    for tid in order:
        path = TOPICS_DIR / f"{tid}.json"
        old = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {"words": []}
        tw, tp, ts = TARGETS[tid]
        override = AI_RU if tid == "ai_engineering" else None
        words = unique_words(old.get("words", []), EXTRAS[tid], used, tw, override)
        lemmas = {normalize_en(w["en"]) for w in words}
        phrases_src, sentences_src = READY[tid]
        phrases = take_ready(phrases_src, tp, lemmas, f"{tid}.phrases")
        sentences = take_ready(sentences_src, ts, lemmas, f"{tid}.sentences")
        data = {
            "id": tid,
            "titleRu": TITLES[tid],
            "words": words,
            "phrases": phrases,
            "sentences": sentences,
            "notes": NOTES.get(
                tid,
                "Готовые словосочетания и предложения без шаблонной автоподстановки.",
            ),
        }
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"{tid}: words={len(words)} phrases={len(phrases)} sentences={len(sentences)}")
    print(f"unique EN lemmas across topics: {len(used)}")


if __name__ == "__main__":
    main()
