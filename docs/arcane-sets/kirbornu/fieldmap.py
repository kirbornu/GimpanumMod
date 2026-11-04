"""Карта арканных полей: луковица из четырёх слоёв.

    python3 fieldmap.py

Пишет fields_generated.json — список полей для набора default в fields.json.
Векторы рун берёт из magic.json рядом.

Ядро — 12 базовых арканов на концах векторов рун (одна руна = базовый аркан).
Слои 2–4 — сферы с центрами в точках, куда действительно приходят рунописи из
2–4 рун, разнесённые как можно дальше друг от друга. Каждый аркан слоя — в
нескольких сферах разного размера, базовые — ещё и маленьким повтором во
втором слое. Зерно фиксировано: карта воспроизводится.
"""
import json, math, random, sys, os
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from rune_turtle import RUNES, walk, words  # noqa: E402

BASE = {  # руна → базовый аркан. АН и БЭ — как у автора (огонь и вода на концах этих рун).
    "an": "fire", "be": "water", "vol": "heal", "gi": "shadow", "dar": "light", "er": "wind",
    "zhin": "earth", "zar": "gravity", "im": "lightning", "kol": "poison", "lun": "frost", "mar": "impulse",
}
LAYERS = [  # (арканы, сфер на аркан, (r_min, r_max) центров, (радиус min, max))
    (["shield", "birth", "lava", "soul", "eye", "feather", "snare", "rime", "whirl", "veil", "fence", "step"],
     3, (1.45, 2.3), (0.22, 0.5)),
    (["explosion", "particle", "exchange", "hex", "fangs", "smite", "flare", "shards", "might", "calm"],
     3, (2.2, 3.1), (0.2, 0.5)),
    (["waiting", "arcane_1", "void", "storm", "echo"],
     3, (3.0, 4.8), (0.3, 0.65)),
]
BASE_RADIUS = 0.38
BASE_REPEAT_RADIUS = (0.15, 0.25)
rng = random.Random(20261104)

tips = [walk((i,)) for i in range(12)]
pts = {}
for w in words(4):
    p = walk(w)
    key = tuple(round(c, 3) for c in p)
    if key not in pts or len(w) < len(pts[key]):
        pts[key] = w
cands = list(pts)
norm = lambda p: math.dist(p, (0, 0, 0))

def fps(points, k, avoid):
    """Самые разнесённые k точек, подальше и от уже занятых центров."""
    chosen = []
    taken = list(avoid)
    first = rng.choice(points)
    chosen.append(first); taken.append(first)
    dist = [min(math.dist(p, t) for t in taken) for p in points]
    while len(chosen) < k:
        i = max(range(len(points)), key=lambda j: dist[j])
        chosen.append(points[i]); taken.append(points[i])
        dist = [min(d, math.dist(p, points[i])) for d, p in zip(dist, points)]
    return chosen

fields = []
for i, rune in enumerate(RUNES):
    fields.append({"arcane": BASE[rune], "center": [round(c, 3) for c in tips[i]], "radius": BASE_RADIUS})
occupied = [tuple(t) for t in tips]

def safe(center, radius):
    """Сфера не должна накрывать конец руны: одиночная руна обязана давать свой базовый аркан."""
    return all(math.dist(center, t) > radius + 0.05 for t in tips)

for names, per, (rmin, rmax), (smin, smax) in LAYERS:
    band = [p for p in cands if rmin <= norm(p) <= rmax and all(math.dist(p, t) > 0.5 for t in tips)]
    centers = fps(band, len(names) * per, occupied)
    occupied += centers
    order = names * per  # по кругу: сферы одного аркана — в разных местах карты
    for name, c in zip(order, centers):
        r = round(rng.uniform(smin, smax), 2)
        while not safe(c, r):
            r = round(r - 0.02, 2)
        fields.append({"arcane": name, "center": [round(x, 3) for x in c], "radius": r})

# Повторы базовых: маленькая сфера во втором слое, чтобы базу можно было найти не только одной руной.
band2 = [p for p in cands if 1.5 <= norm(p) <= 2.4 and all(math.dist(p, t) > 0.6 for t in tips)]
for rune, c in zip(RUNES, fps(band2, 12, occupied)):
    r = round(rng.uniform(*BASE_REPEAT_RADIUS), 2)
    fields.append({"arcane": BASE[rune], "center": [round(x, 3) for x in c], "radius": r})

json.dump(fields, open(os.path.join(HERE, 'fields_generated.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
print(len(fields), "полей")
