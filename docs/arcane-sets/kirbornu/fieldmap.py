"""Карта арканных полей: луковица из пяти слоёв.

    python3 fieldmap.py

Пишет fields_generated.json — список полей для набора default в fields.json.
Векторы рун и угол берёт из magic.json рядом. Нужен numpy.

Ядро — 12 базовых арканов на концах векторов рун: одна руна = базовый аркан.
Слои 2–4 — сферы с центрами там, куда приходят рунописи из 2–4 рун. Слой 5 —
призыв: центры там, куда доходят только рунописи из 5 рун, и проверено, что
ни одна рунопись короче к призыву не ведёт (вне сфер побеждает ближайший
центр, поэтому внешние точки из 4 рун прикрываются «сторожевыми» сферами
арканов четвёртого слоя). Зерно фиксировано: карта воспроизводится.
"""
import json, math, os, random
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
M = json.load(open(os.path.join(HERE, 'magic.json'), encoding='utf-8'))
RUNES = ["an", "be", "vol", "gi", "dar", "er", "zhin", "zar", "im", "kol", "lun", "mar"]
V = np.array([M["runes"][r] for r in RUNES], dtype=float)
ANG = math.radians(M["turnAngle"]); STEP = M["stepMultiplier"]

BASE = {"an": "fire", "be": "water", "vol": "heal", "gi": "shadow", "dar": "light", "er": "wind",
        "zhin": "earth", "zar": "gravity", "im": "lightning", "kol": "poison", "lun": "frost", "mar": "impulse"}
LAYERS = [  # (арканы, сфер на аркан, (r_min, r_max) центров, (радиус min, max))
    (["shield", "birth", "lava", "soul", "eye", "feather", "snare", "rime", "whirl", "veil", "fence", "step"],
     3, (1.45, 2.3), (0.22, 0.5)),
    (["explosion", "particle", "exchange", "hex", "fangs", "smite", "flare", "shards", "might", "calm"],
     3, (2.2, 3.1), (0.2, 0.5)),
    (["waiting", "arcane_1", "void", "storm", "echo"],
     3, (3.0, 4.8), (0.3, 0.65)),
]
SUMMONS = ["herd", "thicket", "deep", "swarm", "guardian", "nest", "undead", "vexes", "inferno", "edge"]
BASE_RADIUS = 0.38
rng = random.Random(20261104)


def qmul(a, b):
    w1, x1, y1, z1 = a.T; w2, x2, y2, z2 = b.T
    return np.stack([w1*w2 - x1*x2 - y1*y2 - z1*z2, w1*x2 + x1*w2 + y1*z2 - z1*y2,
                     w1*y2 - x1*z2 + y1*w2 + z1*x2, w1*z2 + x1*y2 - y1*x2 + z1*w2], 1)


def walk_all(n):
    """Точки всех рунописей длины 1..n: словарь длина → массив (12^k, 3), порядок — как у чисел по основанию 12."""
    axes = np.array([np.concatenate([[math.cos(ANG / 2)], v / np.linalg.norm(v) * math.sin(ANG / 2)]) for v in V])
    p = np.zeros((1, 3)); q = np.array([[1.0, 0, 0, 0]]); out = {}
    for k in range(1, n + 1):
        p = np.repeat(p, 12, 0); q = np.repeat(q, 12, 0); r = np.tile(np.arange(12), len(p) // 12)
        qv = np.concatenate([np.zeros((len(r), 1)), V[r] * STEP], 1)
        p = p + qmul(qmul(q, qv), q * np.array([1, -1, -1, -1]))[:, 1:]
        q = qmul(q, axes[r]); out[k] = p.copy()
    return out


def resolve(points, fields):
    """Индекс поля-победителя для каждой точки — то же правило, что в моде."""
    c = np.array([f["center"] for f in fields]); r = np.array([f["radius"] for f in fields])
    out = np.empty(len(points), dtype=int)
    for s in range(0, len(points), 20000):
        d = np.linalg.norm(points[s:s + 20000, None, :] - c[None, :, :], axis=2)
        inside = d <= r[None, :]
        key = np.where(inside, r[None, :] * 1e6 + d, np.inf)
        out[s:s + 20000] = np.where(inside.any(1), np.argmin(key, 1), np.argmin(d, 1))
    return out


def fps(points, k, avoid):
    """Самые разнесённые k точек, подальше и от уже занятых центров."""
    d = np.full(len(points), np.inf)
    for a in avoid:
        d = np.minimum(d, np.linalg.norm(points - a, axis=1))
    chosen = []
    first = rng.randrange(len(points)) if not avoid else int(np.argmax(d))
    for _ in range(k):
        i = first if not chosen else int(np.argmax(d))
        chosen.append(points[i]); d = np.minimum(d, np.linalg.norm(points - points[i], axis=1))
    return chosen


P = walk_all(5)
short = np.concatenate([P[1], P[2], P[3], P[4]])
tips = P[1]
fields = [{"arcane": BASE[r], "center": [round(float(x), 3) for x in tips[i]], "radius": BASE_RADIUS}
          for i, r in enumerate(RUNES)]
occupied = [t for t in tips]


def safe(center, radius):
    return all(np.linalg.norm(center - t) > radius + 0.05 for t in tips)


def add_layer(names, per, band_points, sizes):
    centers = fps(band_points, len(names) * per, occupied)
    occupied.extend(centers)
    for name, c in zip(names * per, centers):
        r = round(rng.uniform(*sizes), 2)
        while not safe(c, r):
            r = round(r - 0.02, 2)
        fields.append({"arcane": name, "center": [round(float(x), 3) for x in c], "radius": r})


uniq = np.unique(np.round(short, 3), axis=0)
norm = np.linalg.norm(uniq, axis=1)
far_from_tips = np.min(np.linalg.norm(uniq[:, None, :] - tips[None, :, :], axis=2), axis=1)
for names, per, (rmin, rmax), sizes in LAYERS:
    add_layer(names, per, uniq[(norm >= rmin) & (norm <= rmax) & (far_from_tips > 0.5)], sizes)
# Повторы базовых — маленькие сферы во втором слое.
band2 = uniq[(norm >= 1.5) & (norm <= 2.4) & (far_from_tips > 0.6)]
for rune, c in zip(RUNES, fps(band2, 12, occupied)):
    occupied.append(c)
    fields.append({"arcane": BASE[rune], "center": [round(float(x), 3) for x in c],
                   "radius": round(rng.uniform(0.15, 0.25), 2)})

# Слой 5 — призыв: центры, до которых рунописи из ≤4 рун не дотягиваются.
five = np.unique(np.round(P[5], 3), axis=0)
r5 = np.linalg.norm(five, axis=1)
band5 = five[(r5 >= 4.9) & (r5 <= 6.0)][::25]
gap = np.array([np.min(np.linalg.norm(short - c, axis=1)) for c in band5])
add_layer(SUMMONS, 2, band5[gap > 0.9], (0.35, 0.6))

# Сторожевые сферы: внешние точки из ≤4 рун не должны уходить к призыву.
outer = LAYERS[2][0]
for round_ in range(20):
    names = np.array([f["arcane"] for f in fields])
    won = names[resolve(short, fields)]
    bad = short[np.isin(won, SUMMONS)]
    if len(bad) == 0:
        break
    for i, c in enumerate(fps(np.unique(np.round(bad, 3), axis=0), min(len(bad), 6), [])):
        fields.append({"arcane": outer[(round_ * 6 + i) % len(outer)], "center": [round(float(x), 3) for x in c],
                       "radius": round(rng.uniform(0.25, 0.45), 2)})
else:
    raise SystemExit("сторожевые сферы не закрыли все короткие рунописи")

json.dump(fields, open(os.path.join(HERE, 'fields_generated.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
print(len(fields), "полей; сторожевых раундов:", round_)
