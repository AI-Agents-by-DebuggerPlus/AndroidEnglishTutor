import json
import pathlib

root = pathlib.Path(__file__).resolve().parents[1] / "app/src/main/assets/topics"
for p in sorted(root.glob("*.json")):
    d = json.loads(p.read_text(encoding="utf-8"))
    print(
        f"{p.name}: words={len(d['words'])} "
        f"phrases={len(d['phrases'])} sentences={len(d['sentences'])}"
    )
