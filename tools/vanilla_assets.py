#!/usr/bin/env python3
"""Достать ванильные ассеты из файлов игры.

Текстуры, модели и переводы лежат в джарке клиента. Звуков там нет: игра
хранит их отдельно, в хранилище с именами по хэшу, а сопоставление
«человеческое имя → хэш» держит файл индекса. Поэтому два источника.

    python3 tools/vanilla_assets.py find mangrove
    python3 tools/vanilla_assets.py find warden
    python3 tools/vanilla_assets.py get 'textures/block/mangrove_*' out/
    python3 tools/vanilla_assets.py get 'sounds/mob/warden/roar*' out/
"""
import fnmatch
import json
import os
import shutil
import sys
import zipfile

LAUNCHER = os.path.expanduser('~/.local/share/Runes And Rails Launcher')
JAR = os.path.join(LAUNCHER, 'versions/1.21.1/1.21.1.jar')
INDEX = os.path.join(LAUNCHER, 'assets/indexes/17.json')
OBJECTS = os.path.join(LAUNCHER, 'assets/objects')

PREFIX = 'assets/minecraft/'


def from_jar():
    """Пути внутри джарки, уже без общей приставки assets/minecraft/."""
    with zipfile.ZipFile(JAR) as z:
        return {n[len(PREFIX):]: ('jar', n) for n in z.namelist()
                if n.startswith(PREFIX) and not n.endswith('/')}


def from_index():
    """Пути из индекса ассетов — там живут звуки и часть шрифтов."""
    with open(INDEX, encoding='utf-8') as f:
        objects = json.load(f)['objects']
    out = {}
    for name, entry in objects.items():
        if name.startswith('minecraft/'):
            out[name[len('minecraft/'):]] = ('index', entry['hash'])
    return out


def catalogue():
    everything = from_jar()
    everything.update(from_index())
    return everything


def matches(pattern):
    if not any(ch in pattern for ch in '*?['):
        pattern = '*%s*' % pattern
    return sorted((k, v) for k, v in catalogue().items() if fnmatch.fnmatch(k, pattern))


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return 1
    action, pattern = sys.argv[1], sys.argv[2]
    found = matches(pattern)

    if action == 'find':
        for name, (source, _) in found[:200]:
            print('%-6s %s' % (source, name))
        print('— всего %d' % len(found))
        return 0

    if action == 'get':
        if len(sys.argv) < 4:
            print('куда класть? третьим доводом укажи папку')
            return 1
        target = sys.argv[3]
        with zipfile.ZipFile(JAR) as z:
            for name, (source, ref) in found:
                out = os.path.join(target, name)
                os.makedirs(os.path.dirname(out), exist_ok=True)
                if source == 'jar':
                    with z.open(ref) as src, open(out, 'wb') as dst:
                        shutil.copyfileobj(src, dst)
                else:
                    shutil.copyfile(os.path.join(OBJECTS, ref[:2], ref), out)
                print(out)
        print('— достано %d' % len(found))
        return 0

    print('действия: find, get')
    return 1


if __name__ == '__main__':
    sys.exit(main())
