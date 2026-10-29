#!/usr/bin/env python3
"""Собрать JSON Schema для arcanes.json.

    python3 tools/arcanes_schema.py

Пишет src/main/resources/data/gimpanum/magic/arcanes.schema.json. Мод при
каждой загрузке настроек кладёт её рядом с arcanes.json, и редактор берёт
из неё подсказки и проверку.

Таблица поведений ниже повторяет BehaviorLibrary: имена, параметры, пределы.
Добавили или поменяли поведение — поправьте таблицу и пересоберите. Игровой
тест SchemaGameTests сверяет имена и параметры схемы с библиотекой и упадёт,
если забыть.
"""
import json
import os
import sys

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'data', 'gimpanum', 'magic',
                   'arcanes.schema.json')
SEC=3600.0; REACH=32.0; AMOUNT=1000.0
def num(lo,hi,d): return {"type":"number","minimum":lo,"maximum":hi,"description":d}
def intg(lo,hi,d): return {"type":"integer","minimum":lo,"maximum":hi,"description":d}
def boolean(d): return {"type":"boolean","description":d}
def forever(d): return {"description":d+" −1 — навсегда.","oneOf":[{"type":"number","const":-1},{"type":"number","exclusiveMinimum":0,"maximum":SEC}]}
RID={"type":"string","pattern":"^([a-z0-9_.-]+:)?[a-z0-9_./-]+$"}
def rid(d,ex): return dict(RID,description=d,examples=ex)
# name: (kind, description, {param: (schema, required)})
B={}
def b(name,kind,desc,**params): B[name]=(kind,desc,params)
M,A,P='modifier','action','process'
b("extend_duration",M,"Длительные эффекты корня длятся в factor раз дольше.",factor=(num(0,100,"Во сколько раз дольше."),True))
b("periodic_if_instant",M,"Мгновенный корень повторяется раз в interval с, пока прошло меньше duration с.",interval=(num(0.05,SEC,"Период, с."),True),duration=(num(0.05,SEC,"Сколько длится, с."),True))
b("amplify",M,"Основной параметр корня больше на percent %.",percent=(num(-100,10000,"На сколько процентов."),True))
b("target_look",M,"Цель — живая сущность под прицелом не дальше range.",range=(num(0,REACH,"Дальность, блоков."),True))
b("target_radius",M,"Цели — все живые в радиусе от каждого якоря.",radius=(num(0,REACH,"Радиус, блоков."),True),includeCaster=(boolean("Включать ли кастера."),True))
b("shape_cone",M,"Форма — конус по взгляду кастера в момент каста.",angle=(num(0,180,"Полный раствор, градусы."),True),length=(num(0,REACH,"Длина, блоков."),True))
b("shape_sphere",M,"Форма — шар вокруг якоря.",radius=(num(0,REACH,"Радиус, блоков."),True))
b("target_caster",M,"Цель — сам кастер.")
b("target_nearest",M,"От каждого якоря — ближайшая живая в радиусе, кроме самого якоря.",radius=(num(0,REACH,"Радиус, блоков."),True),excludeCaster=(boolean("Исключить кастера."),True))
b("target_filter",M,"Оставить из целей только подходящие. Не откатывается: никто не подошёл — целей нет.",filter=({"type":"string","description":"players, monsters, animals, not_caster, type:<id существа>, tag:<тег типов существ>; ! в начале обращает.","pattern":"^!?(players|monsters|animals|not_caster|type:([a-z0-9_.-]+:)?[a-z0-9_./-]+|tag:([a-z0-9_.-]+:)?[a-z0-9_./-]+)$","examples":["monsters","!players","type:minecraft:pig","tag:minecraft:undead"]},True))
b("target_limit",M,"Оставить не больше count целей, ближайших к сущности-заклинанию.",count=(intg(0,1000,"Сколько целей."),True))
b("shape_line",M,"Форма — луч от якоря по взгляду кастера.",length=(num(0,REACH,"Длина, блоков."),True),width=(num(0,REACH,"Ширина, блоков."),True))
b("shape_ring",M,"Форма — кольцо на высоте ног якоря.",radius=(num(0,REACH,"Радиус, блоков."),True),thickness=(num(0,REACH,"Толщина, блоков."),True))
b("shape_box",M,"Форма — прямоугольник x × y × z с центром в якоре.",x=(num(0,REACH,"По X, блоков."),True),y=(num(0,REACH,"По Y, блоков."),True),z=(num(0,REACH,"По Z, блоков."),True))
b("shape_ground",M,"Опустить выделение блоков на землю.",maxDepth=(intg(0,64,"Насколько далеко искать землю, блоков."),True))
b("repeat",M,"Применить действия корня times раз подряд.",times=(intg(1,100,"Сколько раз."),True))
b("delay",M,"Отложить применение корня.",seconds=(num(0,SEC,"Задержка, с."),True))
b("damage",A,"Магический урон. Основной параметр — amount.",amount=(num(0,AMOUNT,"Урон (2 = сердце)."),True))
b("ignite",A,"Поджечь. Длительное.",seconds=(num(0,SEC,"Сколько гореть, с."),True))
b("heal",A,"Вылечить. Основной параметр — amount.",amount=(num(0,AMOUNT,"Сколько здоровья (2 = сердце)."),True))
b("effect",A,"Наложить эффект. Длительное.",effect=(rid("Id эффекта.",["minecraft:speed","minecraft:glowing"]),True),seconds=(num(0,SEC,"Длительность, с."),True),amplifier=(intg(0,255,"Уровень − 1 (0 = I). Необязательно, по умолчанию 0."),False))
b("cleanse",A,"Снять все эффекты и потушить, кроме наложенного этим же заклинанием.")
b("echo_root",A,"Копия на месте сущности-заклинания повторяет только корень.",interval=(num(0.05,SEC,"Период, с."),True),duration=(num(0.05,SEC,"Сколько длится, с."),True))
b("water",A,"Вода в выделенных блоках.",seconds=(forever("Сколько продержится, с."),True))
b("light",A,"Невидимый блок света в воздухе. Основной параметр — level.",level=(intg(0,15,"Яркость."),True),seconds=(forever("Сколько продержится, с."),True))
b("knockback",A,"Отбросить от сущности-заклинания. Основной параметр — strength.",strength=(num(0,10,"Сила (0.4 — удар рукой)."),True))
b("pull",A,"Притянуть к сущности-заклинанию. Основной параметр — strength.",strength=(num(0,10,"Сила."),True))
b("launch",A,"Подбросить вверх. Основной параметр — strength.",strength=(num(0,10,"Прибавка к скорости, блоков за тик (1.0 ≈ 5 блоков)."),True))
b("freeze",A,"Заморозить, как в порошковом снегу. Длительное.",seconds=(num(0,SEC,"Сколько полностью замёрзшим, с."),True))
b("feed",A,"Накормить игроков. Основной параметр — food.",food=(intg(0,20,"Единиц еды."),True),saturation=(num(0,20,"Насыщенности."),True))
b("absorb",A,"Поглощение (золотые сердца). Основной параметр — amount. Длительное.",amount=(num(0,AMOUNT,"Единиц поглощения (2 = сердце)."),True),seconds=(num(0,SEC,"Сколько держится, с."),True))
b("extinguish",A,"Потушить цели.")
b("set_fire_blocks",A,"Огонь в выделенных блоках.",seconds=(forever("Сколько продержится поставленный огонь, с."),True))
b("explode",A,"Ванильный взрыв в каждой цели. Основной параметр — power.",power=(num(0,16,"Сила (4 — динамит)."),True),breakBlocks=(boolean("Ломать ли блоки."),True))
b("lightning",A,"Ванильная молния в каждую цель.")
b("teleport_to_origin",A,"Перенести цели в точку сущности-заклинания.")
b("swap_caster",A,"Кастер и первая цель меняются местами.")
b("remove_effect",A,"Снять один эффект.",effect=(rid("Id эффекта.",["minecraft:poison"]),True))
b("steal_effects",A,"Эффекты целей переходят на кастера.")
b("drain",A,"Урон целям (кроме кастера), кастер лечится на нанесённое. Основной параметр — amount.",amount=(num(0,AMOUNT,"Урон."),True))
b("place_block",A,"Поставить блок в выделенные блоки.",block=({"type":"string","minLength":1,"description":"Блок как в /setblock.","examples":["minecraft:glass","minecraft:oak_log[axis=x]"]},True),seconds=(forever("Срок, с."),True),onlyAir=(boolean("Только в воздух (иначе ещё и в заменяемое)."),True))
b("freeze_water",A,"Источники воды → лёд.",seconds=(forever("Срок, с."),True))
b("grow",A,"Костная мука на выделенные блоки.")
b("break_blocks",A,"Сломать выделенные блоки.",maxHardness=(num(0,1000,"Наибольшая прочность (камень 1.5)."),True),drop=(boolean("Ронять ли добычу."),True))
b("summon",A,"Призвать существо в каждой цели. Призванные ничего не роняют.",entity=(rid("Id существа.",["minecraft:wolf","minecraft:iron_golem"]),True),seconds=(forever("Срок, с."),True))
b("spawn_projectile",A,"Выпустить снаряд по взгляду кастера.",entity=(rid("Id снаряда.",["minecraft:arrow","minecraft:fireball","minecraft:snowball"]),True),speed=(num(0,10,"Скорость, блоков за тик (3 — лук)."),True))
b("wait",P,"Ждать.",seconds=(num(0,SEC,"Сколько, с."),True))
b("descend",P,"Опуститься на ближайший твёрдый блок.",maxDistance=(num(0,256,"Не дальше, блоков."),True),speed=(num(0.01,16,"Блоков за тик."),True))
b("home",P,"Наводиться на ближайшую живую сущность.",seconds=(num(0,SEC,"Сколько, с."),True),speed=(num(0,16,"Блоков за тик."),True),excludeCaster=(boolean("Не наводиться на кастера."),True))
b("emit_light",P,"Светиться, пока идёт интерфикс. Интерфикс не удлиняет.",level=(intg(0,15,"Яркость."),True))
b("move_forward",P,"Лететь по взгляду кастера.",distance=(num(0,256,"Дальность, блоков."),True),speed=(num(0.01,16,"Блоков за тик."),True))
b("rise",P,"Подняться.",distance=(num(0,256,"Высота, блоков."),True),speed=(num(0.01,16,"Блоков за тик."),True))
b("teleport_forward",P,"Мгновенно переместиться по взгляду кастера.",distance=(num(0,256,"Дальность, блоков."),True))
b("follow_caster",P,"Держаться у глаз кастера.",seconds=(num(0,SEC,"Сколько, с."),True))
b("orbit",P,"Кружить вокруг кастера.",radius=(num(0,REACH,"Радиус, блоков."),True),seconds=(num(0,SEC,"Сколько, с."),True),period=(num(0.05,SEC,"Секунд на оборот."),True))
b("wait_for_target",P,"Ждать, пока кто-то подойдёт.",radius=(num(0,REACH,"Радиус, блоков."),True),maxSeconds=(num(0,SEC,"Не дольше, с."),True),excludeCaster=(boolean("Кастер не считается."),True))
b("aura",P,"Пока идёт интерфикс, применять action ко всем в радиусе. Интерфикс не удлиняет.",radius=(num(0,REACH,"Радиус, блоков."),True),every=(num(0.05,SEC,"Период, с."),True),excludeCaster=(boolean("Не задевать кастера."),True),action=({"$ref":"#/definitions/auraAction"},True))

defs={}
for name,(kind,desc,params) in B.items():
    props={"type":{"const":name}}
    req=["type"]
    for pn,(sch,r) in params.items():
        props[pn]=sch
        if r: req.append(pn)
    defs["params_"+name]={"description":desc,"properties":props,"required":req,"additionalProperties":False}
def behavior(names,desc):
    return {"type":"object","description":desc,"required":["type"],
            "properties":{"type":{"type":"string","enum":names,"description":"Поведение из библиотеки."}},
            "allOf":[{"if":{"properties":{"type":{"const":n}},"required":["type"]},"then":{"$ref":"#/definitions/params_"+n}} for n in names]}
mods=[n for n,v in B.items() if v[0]==M]; acts=[n for n,v in B.items() if v[0]==A]; procs=[n for n,v in B.items() if v[0]==P]
defs["roleBehavior"]=behavior(mods+acts,"Изменение контекста или действие — для корня, префикса и суффикса.")
defs["interfixBehavior"]=behavior(procs,"Процесс — только для интерфикса.")
defs["auraAction"]=behavior([n for n in acts if n!="echo_root"],"Действие (кроме echo_root), которое aura применяет к окружающим.")
defs["particleLayer"]={"type":"object","description":"Слой частиц роли.","required":["particle"],"additionalProperties":False,
  "properties":{"particle":{"type":"object","required":["type"],"description":"Частица в ванильном JSON-формате, как у /particle.","properties":{"type":dict(RID,description="Id типа частицы.",examples=["minecraft:flame","minecraft:dust","minecraft:dust_color_transition"])},
     "examples":[{"type":"minecraft:flame"},{"type":"minecraft:dust","color":[1.0,0.4,0.0],"scale":1.0}]},
   "count":intg(1,1000,"Частиц в точке узора. По умолчанию 1."),"spread":num(0,16,"Разброс, блоков. По умолчанию 0."),"speed":num(0,10,"Скорость. По умолчанию 0.")}}
role=lambda d,ref:{"type":"array","description":d,"items":{"$ref":"#/definitions/"+ref}}
arcane={"type":"object","required":["id","name","order"],"additionalProperties":False,
 "properties":{
  "id":{"type":"string","pattern":"^[a-z0-9_]{1,64}$","description":"Латиница в нижнем регистре, цифры, _; уникален."},
  "name":{"type":"string","minLength":1,"pattern":"\\S","description":"Название для людей."},
  "order":{"type":"integer","minimum":1,"maximum":2147483647,"description":"Порядковый номер, больше нуля. Решает роли в заклинании."},
  "root":role("Поведения в роли корня.","roleBehavior"),
  "prefix":role("Поведения в роли префикса.","roleBehavior"),
  "suffix":role("Поведения в роли суффикса.","roleBehavior"),
  "interfix":role("Поведения в роли интерфикса — только процессы.","interfixBehavior"),
  "particles":{"type":"object","additionalProperties":False,"description":"Частицы по ролям.",
     "properties":{k:{"$ref":"#/definitions/particleLayer"} for k in ["root","prefix","suffix","interfix"]}}}}
schema={"$schema":"http://json-schema.org/draft-07/schema#","$id":"https://github.com/kirbornu/GimpanumMod/arcanes.schema.json",
 "title":"Арканы рунной магии Гимпанума (arcanes.json)",
 "description":"Схема для подсказок и проверки в редакторе. Окончательную проверку делает мод при /gimpanum magic reload: он ещё сверяет существование эффектов, блоков, существ и частиц. Описание — docs/magic-config.md.",
 "type":"object","required":["arcanes"],"additionalProperties":False,
 "properties":{"$schema":{"type":"string","description":"Путь к этой схеме — для редактора; мод это поле пропускает."},
  "arcanes":{"type":"array","minItems":1,"items":{"$ref":"#/definitions/arcane"}}},
 "definitions":dict({"arcane":arcane},**defs)}
out = sys.argv[1] if len(sys.argv) > 1 else OUT
with open(out, 'w', encoding='utf-8') as f:
    json.dump(schema, f, ensure_ascii=False, indent=2)
    f.write('\n')
print(len(B), 'поведений →', os.path.normpath(out))
