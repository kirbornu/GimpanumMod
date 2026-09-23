#!/usr/bin/env python3
"""Собрать шаблоны структур Гимпанума.

    python3 tools/structures.py

Пишет .nbt в src/main/resources/data/gimpanum/structure/. Каждая структура —
функция, которая ставит блоки в Template; варианты различаются зерном. Блоки,
которые не поставлены, в шаблон не попадают: там при генерации остаётся мир
как есть. Воздух ставится явно — там, где надо вырезать полость.
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
import nbt  # noqa: E402

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'data', 'gimpanum', 'structure')
DATA_VERSION = 3955  # Minecraft 1.21.1

COLORS = ['white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime', 'pink', 'gray', 'light_gray',
          'cyan', 'purple', 'blue', 'brown', 'green', 'red', 'black']


def clot(kind):
    return ('gimpanum:phonos_clot', {'kind': kind})


class Template:
    def __init__(self, sx, sy, sz):
        self.size = (sx, sy, sz)
        self.blocks = {}

    def set(self, x, y, z, block, props=None):
        if 0 <= x < self.size[0] and 0 <= y < self.size[1] and 0 <= z < self.size[2]:
            self.blocks[(x, y, z)] = (block, tuple(sorted((props or {}).items())))

    def get(self, x, y, z):
        entry = self.blocks.get((x, y, z))
        return entry[0] if entry else None

    def put(self, x, y, z, state):
        block, props = state if isinstance(state, tuple) else (state, None)
        self.set(x, y, z, block, props)

    def fill(self, x0, y0, z0, x1, y1, z1, block, props=None):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, block, props)

    def save(self, name):
        palette = []
        index = {}
        blocks = []
        for pos in sorted(self.blocks):
            key = self.blocks[pos]
            if key not in index:
                index[key] = len(palette)
                entry = {'Name': (8, key[0])}
                if key[1]:
                    entry['Properties'] = (10, {k: (8, v) for k, v in key[1]})
                palette.append(entry)
            blocks.append({'pos': (9, (3, list(pos))), 'state': (3, index[key])})
        root = {
            'DataVersion': (3, DATA_VERSION),
            'size': (9, (3, list(self.size))),
            'palette': (9, (10, palette)),
            'blocks': (9, (10, blocks)),
            'entities': (9, (10, [])),
        }
        path = os.path.join(OUT, name + '.nbt')
        nbt.save(path, '', root)
        print(f'{name}: {len(blocks)} blocks, {len(palette)} states, size {self.size}')


# --- Застывшее воспоминание -------------------------------------------------

FLOWERS = ['poppy', 'dandelion', 'cornflower', 'oxeye_daisy', 'azure_bluet', 'allium',
           'lily_of_the_valley', 'blue_orchid']


def frozen_memory(seed, toy):
    """Лужайка из детского воспоминания Хару: трава, цветы, деревце, пруд и
    игрушка в середине. Слой y=0 ложится вместо верхнего песка."""
    rng = random.Random(seed)
    R = 8
    t = Template(2 * R + 1, 11, 2 * R + 1)
    c = R
    for x in range(t.size[0]):
        for z in range(t.size[2]):
            d = math.hypot(x - c, z - c)
            if d > R - rng.random() * 1.2:
                continue
            t.set(x, 0, z, 'minecraft:grass_block')
            for y in range(1, t.size[1]):
                t.set(x, y, z, 'minecraft:air')
            if d > 2.5 and rng.random() < 0.35:
                t.set(x, 1, z, 'minecraft:' + (rng.choice(FLOWERS) if rng.random() < 0.5 else 'short_grass'))

    # Пруд — в стороне от игрушки.
    px, pz = c + rng.choice([-5, 4]), c + rng.choice([-4, 3])
    for x in range(px, px + 3):
        for z in range(pz, pz + 2):
            t.set(x, 0, z, 'minecraft:water')
            t.set(x, 1, z, 'minecraft:air')

    # Деревце — по другую сторону.
    tx, tz = 2 * c - px - 1, 2 * c - pz
    for y in range(1, 5):
        t.set(tx, y, tz, 'minecraft:oak_log')
    for y in range(3, 7):
        r = 2 if y < 5 else 1
        for x in range(tx - r, tx + r + 1):
            for z in range(tz - r, tz + r + 1):
                if (x, y, z) != (tx, y, tz) or y > 4:
                    if abs(x - tx) + abs(z - tz) <= r + (1 if y < 5 else 0):
                        t.set(x, y, z, 'minecraft:oak_leaves', {'persistent': 'true'})

    # Игрушка в середине, сгусток — там, где ребёнок взял бы её в руки.
    for x in range(c - 2, c + 3):
        for z in range(c - 2, c + 3):
            for y in range(1, 9):
                t.set(x, y, z, 'minecraft:air')
    toy(t, c, rng)
    return t


def wooden_horse(t, c, rng):
    t.fill(c - 1, 2, c, c + 1, 2, c + 1, 'minecraft:oak_planks')
    for x, z in [(c - 1, c), (c - 1, c + 1), (c + 1, c), (c + 1, c + 1)]:
        t.set(x, 1, z, 'minecraft:oak_fence')
    t.fill(c + 2, 3, c, c + 2, 4, c + 1, 'minecraft:oak_planks')
    t.set(c + 3, 4, c, 'minecraft:oak_planks')
    t.set(c + 3, 4, c + 1, 'minecraft:oak_planks')
    t.set(c + 2, 5, c, 'minecraft:brown_wool')
    t.set(c + 1, 3, c, 'minecraft:brown_wool')
    t.set(c - 2, 2, c, 'minecraft:brown_wool')
    t.set(c, 3, c + 1, 'minecraft:red_wool')
    t.put(c, 3, c, clot('frozen_memory'))


def spinning_top(t, c, rng):
    colors = rng.sample(['red', 'yellow', 'blue', 'lime', 'orange', 'magenta'], 3)
    t.set(c, 1, c, f'minecraft:{colors[0]}_wool')
    for y, r in [(2, 1), (3, 2), (4, 1)]:
        for x in range(c - r, c + r + 1):
            for z in range(c - r, c + r + 1):
                if abs(x - c) + abs(z - c) <= r + (1 if r == 2 else 0):
                    t.set(x, y, z, f'minecraft:{colors[y % 3]}_wool')
    t.set(c, 5, c, 'minecraft:oak_fence')
    t.put(c, 6, c, clot('frozen_memory'))


def doll_house(t, c, rng):
    wall = 'minecraft:white_terracotta'
    t.fill(c - 2, 1, c - 2, c + 2, 3, c + 2, wall)
    t.fill(c - 1, 1, c - 1, c + 1, 3, c + 1, 'minecraft:air')
    t.set(c, 1, c - 2, 'minecraft:air')
    t.set(c, 2, c - 2, 'minecraft:air')
    t.set(c - 2, 2, c, 'minecraft:light_blue_stained_glass')
    t.set(c + 2, 2, c, 'minecraft:light_blue_stained_glass')
    for i, y in enumerate(range(4, 7)):
        r = 2 - i
        t.fill(c - r, y, c - 2, c + r, y, c + 2, 'minecraft:red_wool')
    t.put(c, 1, c, clot('frozen_memory'))


# --- Грёзы -------------------------------------------------------------------

def glass_dreams(seed):
    """Странные фигуры из разноцветного стекла над барханами: пустотелый шар с
    наградой в середине, кольцо вокруг, парящие кубы и спираль, и тонкая нога,
    на которой всё это стоит."""
    rng = random.Random(seed)
    S = 25
    t = Template(S, 22, S)
    c = S // 2
    heart_y = 11 + rng.randint(0, 3)

    def glass(color):
        return f'minecraft:{color}_stained_glass'

    palette = rng.sample(COLORS, 6)

    # Нога — от земли до шара.
    for y in range(0, heart_y - 3):
        t.set(c, y, c, glass(palette[0]))

    # Пустотелый шар.
    R = 4
    for x in range(c - R, c + R + 1):
        for y in range(heart_y - R, heart_y + R + 1):
            for z in range(c - R, c + R + 1):
                d = math.sqrt((x - c) ** 2 + (y - heart_y) ** 2 + (z - c) ** 2)
                if R - 1 <= d <= R:
                    t.set(x, y, z, glass(palette[1] if (x + y + z) % 3 else palette[2]))
                elif d < R - 1:
                    t.set(x, y, z, 'minecraft:air')
    t.put(c, heart_y, c, clot('glass_dreams'))

    # Наклонное кольцо вокруг шара.
    tilt = rng.uniform(0.3, 0.9)
    ring = 8
    for i in range(160):
        a = 2 * math.pi * i / 160
        x = c + ring * math.cos(a)
        z = c + ring * math.sin(a)
        y = heart_y + ring * math.sin(a) * tilt
        t.set(round(x), round(y), round(z), glass(palette[3]))

    # Спираль вверх из шара.
    for i in range(40):
        a = i * 0.45
        x = c + 2.5 * math.cos(a)
        z = c + 2.5 * math.sin(a)
        y = heart_y + R + 1 + i * 0.15
        t.set(round(x), round(y), round(z), glass(palette[4]))

    # Парящие кубы.
    for _ in range(rng.randint(4, 7)):
        x, y, z = rng.randint(1, S - 3), rng.randint(3, 19), rng.randint(1, S - 3)
        if math.dist((x, y, z), (c, heart_y, c)) < R + 3:
            continue
        color = rng.choice(palette)
        size = rng.choice([1, 2, 2, 3])
        t.fill(x, y, z, x + size - 1, y + size - 1, z + size - 1, glass(color))
    return t


# --- Галерея Радитажей -------------------------------------------------------

def mosaic(t, rng, x0, y0, z0, dx, dz, width, height):
    """Абстрактная картина на стене — пятна трёх-четырёх цветов."""
    colors = rng.sample(COLORS, 4)
    seeds = [(rng.randrange(width), rng.randrange(height), rng.choice(colors)) for _ in range(5)]
    for u in range(width):
        for v in range(height):
            color = min(seeds, key=lambda s: (s[0] - u) ** 2 + (s[1] - v) ** 2)[2]
            t.set(x0 + dx * u, y0 + v, z0 + dz * u, f'minecraft:{color}_terracotta')


def statue(t, rng, x, z):
    """Верит на постаменте. Сгусток — его голова: в ней и живёт фоносомика."""
    t.set(x, 1, z, 'minecraft:polished_andesite')
    stone = rng.choice(['minecraft:calcite', 'minecraft:smooth_quartz', 'minecraft:white_concrete'])
    t.set(x, 2, z, 'minecraft:quartz_pillar')
    t.set(x, 3, z, stone)
    t.set(x, 4, z, stone)
    pose = rng.randrange(3)
    if pose == 0:
        t.set(x - 1, 4, z, stone)
        t.set(x + 1, 4, z, stone)
    elif pose == 1:
        t.set(x + 1, 4, z, stone)
        t.set(x + 1, 5, z, stone)
    else:
        t.set(x - 1, 3, z, stone)
    t.put(x, 5, z, clot('radita_gallery'))


def radita_gallery(seed):
    """Зал в толще: пол из кварца, стены с мозаиками, вдоль зала статуи Веритов.
    Проходы с торцов — в лабиринт, если он рядом."""
    rng = random.Random(seed)
    W, H, D = 25, 9, 15
    t = Template(W, H, D)
    t.fill(0, 0, 0, W - 1, H - 1, D - 1, 'minecraft:smooth_sandstone')
    t.fill(1, 1, 1, W - 2, H - 2, D - 2, 'minecraft:air')
    t.fill(1, 0, 1, W - 2, 0, D - 2, 'minecraft:smooth_quartz')
    for x in range(2, W - 2, 4):
        t.fill(x, 0, 1, x, 0, D - 2, 'minecraft:chiseled_quartz_block')
    t.fill(1, H - 1, 1, W - 2, H - 1, D - 2, 'minecraft:smooth_quartz')
    # Проходы с торцов.
    for x in (0, W - 1):
        t.fill(x, 1, D // 2 - 1, x, 4, D // 2 + 1, 'minecraft:air')
    # Мозаики на длинных стенах.
    for start in (3, 14):
        mosaic(t, rng, start, 2, 1, 1, 0, 8, 5)
        mosaic(t, rng, start, 2, D - 2, 1, 0, 8, 5)
    # Статуи — два ряда.
    for x in range(4, W - 4, 4):
        statue(t, rng, x, 4)
        statue(t, rng, x, D - 5)
    return t


# --- Город Примо -------------------------------------------------------------

def primo_city(seed):
    """Небольшая крепость в духе Древнего города: стены и башни из глубинного
    сланца, врата из укреплённого сланца, в середине — зал со сгустком.
    Скалк вокруг троп, датчики, крикуны у зала; ковры — тихая тропа."""
    rng = random.Random(seed)
    S, H = 41, 16
    t = Template(S, H, S)
    c = S // 2

    floor = ['minecraft:deepslate_tiles', 'minecraft:deepslate_bricks', 'minecraft:polished_deepslate',
             'minecraft:cracked_deepslate_tiles', 'minecraft:cracked_deepslate_bricks']
    for x in range(S):
        for z in range(S):
            t.set(x, 0, z, rng.choices(floor, weights=[5, 5, 3, 1, 1])[0])
            for y in range(1, H):
                t.set(x, y, z, 'minecraft:air')

    # Стена по периметру с зубцами.
    for i in range(S):
        for x, z in [(i, 0), (i, S - 1), (0, i), (S - 1, i)]:
            for y in range(1, 6):
                t.set(x, y, z, rng.choice(['minecraft:deepslate_bricks', 'minecraft:deepslate_bricks',
                                           'minecraft:cracked_deepslate_bricks']))
            if i % 2 == 0:
                t.set(x, 6, z, 'minecraft:deepslate_bricks')
    # Ворота в южной стене.
    t.fill(c - 1, 1, 0, c + 1, 4, 0, 'minecraft:air')

    # Башни по углам.
    for bx, bz in [(0, 0), (S - 5, 0), (0, S - 5), (S - 5, S - 5)]:
        t.fill(bx, 1, bz, bx + 4, 10, bz + 4, 'minecraft:deepslate_tiles')
        t.fill(bx + 1, 1, bz + 1, bx + 3, 9, bz + 3, 'minecraft:air')
        t.set(bx + 2, 11, bz + 2, 'minecraft:soul_lantern')

    # Врата Примо — арка из укреплённого сланца у северной стены.
    gz = S - 8
    for y in range(1, 11):
        t.set(c - 4, y, gz, 'minecraft:reinforced_deepslate')
        t.set(c + 4, y, gz, 'minecraft:reinforced_deepslate')
    for x in range(c - 4, c + 5):
        t.set(x, 11, gz, 'minecraft:reinforced_deepslate')
    t.fill(c - 3, 1, gz, c + 3, 10, gz, 'minecraft:air')

    # Зал в середине.
    k = 5
    t.fill(c - k, 1, c - k, c + k, 7, c + k, 'minecraft:polished_deepslate')
    t.fill(c - k + 1, 1, c - k + 1, c + k - 1, 6, c + k - 1, 'minecraft:air')
    t.fill(c - k + 1, 8, c - k + 1, c + k - 1, 8, c + k - 1, 'minecraft:deepslate_tile_slab')
    t.fill(c - 1, 1, c - k, c + 1, 3, c - k, 'minecraft:air')
    t.set(c, 1, c, 'minecraft:chiseled_deepslate')
    t.put(c, 2, c, clot('primo_city'))
    for x, z in [(c - 2, c - 2), (c + 2, c + 2)]:
        t.set(x, 1, z, 'minecraft:sculk_shrieker')
    for x, z in [(c + 2, c - 2), (c - 2, c + 2)]:
        t.set(x, 1, z, 'minecraft:sculk_sensor')
    for x in range(c - k + 1, c + k):
        for z in range(c - k + 1, c + k):
            if t.get(x, 1, z) == 'minecraft:air' and rng.random() < 0.5:
                t.set(x, 0, z, 'minecraft:sculk')

    # Тихая тропа из серого ковра — от ворот к залу.
    for z in range(1, c - k):
        for x in range(c - 1, c + 2):
            t.set(x, 1, z, 'minecraft:gray_carpet')

    # Скалк, датчики и крикуны во дворе — в стороне от тропы.
    for _ in range(90):
        x, z = rng.randint(2, S - 3), rng.randint(2, S - 3)
        if abs(x - c) <= k + 1 and abs(z - c) <= k + 1 or abs(x - c) <= 2 and z < c - k:
            continue
        if t.get(x, 1, z) != 'minecraft:air':
            continue
        t.set(x, 0, z, 'minecraft:sculk')
        roll = rng.random()
        if roll < 0.12:
            t.set(x, 1, z, 'minecraft:sculk_sensor')
        elif roll < 0.18:
            t.set(x, 1, z, 'minecraft:sculk_shrieker')
        elif roll < 0.30:
            t.set(x, 1, z, 'minecraft:candle', {'candles': str(rng.randint(1, 4)), 'lit': 'false'})

    # Столбы с фонарями душ.
    for x, z in [(8, 8), (S - 9, 8), (8, S - 9), (S - 9, S - 9), (c, 8), (8, c), (S - 9, c)]:
        for y in range(1, 4):
            t.set(x, y, z, 'minecraft:cobbled_deepslate_wall')
        t.set(x, 4, z, 'minecraft:soul_lantern')

    # Обрушенные участки — городу века.
    for _ in range(12):
        x, z = rng.randint(1, S - 2), rng.randint(1, S - 2)
        if abs(x - c) <= k and abs(z - c) <= k:
            continue
        t.set(x, 1, z, 'minecraft:cobbled_deepslate')
        if rng.random() < 0.5:
            t.set(x, 2, z, 'minecraft:cobbled_deepslate')
    return t


def main():
    frozen_memory(1, wooden_horse).save('frozen_memory_1')
    frozen_memory(2, spinning_top).save('frozen_memory_2')
    frozen_memory(3, doll_house).save('frozen_memory_3')
    for i in range(1, 4):
        glass_dreams(100 + i).save(f'glass_dreams_{i}')
    for i in range(1, 3):
        radita_gallery(200 + i).save(f'radita_gallery_{i}')
    primo_city(300).save('primo_city')


if __name__ == '__main__':
    main()
