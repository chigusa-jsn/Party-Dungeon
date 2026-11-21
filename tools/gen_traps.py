"""Party Dungeon の罠ブロックの絵・モデル・名前"""
import json, os, random
from PIL import Image, ImageDraw

R = os.path.join(os.path.dirname(__file__), "..", "src/main/resources")
A = f"{R}/assets/partydungeon"
def hx(c): return ((c >> 16) & 255, (c >> 8) & 255, c & 255, 255)
def mix(a, b, t): return tuple(int(a[i] * (1 - t) + b[i] * t) for i in range(3)) + (255,)
def wj(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f: json.dump(obj, f, ensure_ascii=False, indent=2); f.write("\n")
def save(im, name):
    os.makedirs(f"{A}/textures/block", exist_ok=True); im.save(f"{A}/textures/block/{name}.png")

def tiles(base, seed):
    """石畳のベース（4枚のタイル）"""
    im = Image.new("RGBA", (16, 16)); d = ImageDraw.Draw(im); rnd = random.Random(seed)
    for y in range(16):
        for x in range(16):
            v = rnd.randint(-10, 10); im.putpixel((x, y), tuple(max(0, min(255, c + v)) for c in base[:3]) + (255,))
    dark = mix(base, (0, 0, 0), 0.45)
    d.line([0, 0, 15, 0], fill=dark); d.line([0, 0, 0, 15], fill=dark)
    d.line([0, 8, 15, 8], fill=dark); d.line([8, 0, 8, 15], fill=dark)
    return im, d

# 状態異常の床（id, 色, 模様）
FLOORS = [
    ("spike_floor", "トゲの床", "Spike Floor", 0xB8B8C0, "spike"),
    ("fire_floor", "炎の床", "Fire Floor", 0xFF6020, "flame"),
    ("poison_floor", "毒の床", "Poison Floor", 0x60D040, "drop"),
    ("slow_floor", "鈍足の床", "Slowness Floor", 0x6080A0, "wave"),
    ("weak_floor", "弱体化の床", "Weakness Floor", 0x806080, "cross"),
    ("blind_floor", "暗闇の床", "Blindness Floor", 0x202030, "eye"),
    ("wither_floor", "ウィザーの床", "Wither Floor", 0x303030, "skull"),
    ("nausea_floor", "混乱の床", "Nausea Floor", 0xA040C0, "spiral"),
    ("hunger_floor", "空腹の床", "Hunger Floor", 0x8A6A30, "drop"),
    ("levitate_floor", "浮遊の床", "Levitation Floor", 0xE0E0FF, "up"),
    ("speed_floor", "加速の床", "Speed Floor", 0x40C0FF, "arrow"),
    ("jump_floor", "ジャンプ強化の床", "Jump Boost Floor", 0x80FF60, "up"),
    ("heal_floor", "回復の床", "Healing Floor", 0xFF80B0, "plus"),
]
STONE = (70, 70, 78)
for i, (fid, ja, en, color, mark) in enumerate(FLOORS):
    col = hx(color)
    base = mix(STONE, col, 0.35)
    im, d = tiles(base, i)
    glow = mix(col, (255, 255, 255), 0.35); deep = mix(col, (0, 0, 0), 0.3)
    if mark == "flame":
        for x0 in (2, 10):
            for y0 in (2, 10):
                d.polygon([(x0, y0 + 5), (x0 + 2, y0), (x0 + 4, y0 + 5)], fill=col); d.point((x0 + 2, y0 + 3), fill=(255, 230, 120, 255))
    elif mark == "drop":
        for x0, y0 in ((3, 2), (11, 2), (3, 10), (11, 10)):
            d.polygon([(x0, y0), (x0 - 2, y0 + 3), (x0 + 2, y0 + 3)], fill=col); d.ellipse([x0 - 2, y0 + 2, x0 + 2, y0 + 5], fill=col)
            d.point((x0 - 1, y0 + 3), fill=glow)
    elif mark == "wave":
        for y0 in (3, 7, 11):
            d.line([(1, y0), (4, y0 - 1), (7, y0), (10, y0 - 1), (14, y0)], fill=col)
    elif mark == "cross":
        d.line([3, 3, 12, 12], fill=col, width=2); d.line([12, 3, 3, 12], fill=col, width=2)
    elif mark == "eye":
        d.ellipse([2, 5, 13, 10], outline=(150, 150, 170, 255)); d.rectangle([6, 6, 9, 9], fill=(10, 10, 15, 255))
        d.line([2, 12, 13, 3], fill=(150, 150, 170, 255))
    elif mark == "skull":
        d.ellipse([4, 3, 11, 10], fill=(200, 200, 200, 255)); d.rectangle([6, 10, 9, 12], fill=(200, 200, 200, 255))
        d.point([(6, 6), (9, 6)], fill=(20, 20, 20, 255)); d.rectangle([5, 6, 6, 7], fill=(20, 20, 20, 255)); d.rectangle([9, 6, 10, 7], fill=(20, 20, 20, 255))
    elif mark == "spiral":
        pts = [(8, 8), (9, 8), (9, 9), (7, 9), (6, 7), (8, 5), (11, 6), (12, 9), (10, 12), (6, 12), (3, 9), (4, 4), (8, 2), (13, 3)]
        d.line(pts, fill=col)
    elif mark == "up":
        for x0 in (4, 11):
            d.polygon([(x0, 2), (x0 - 3, 6), (x0 + 3, 6)], fill=col); d.rectangle([x0 - 1, 6, x0 + 1, 13], fill=col)
    elif mark == "arrow":
        for y0 in (4, 11):
            d.polygon([(13, y0), (9, y0 - 3), (9, y0 + 3)], fill=col); d.rectangle([2, y0 - 1, 9, y0 + 1], fill=col)
    elif mark == "spike":
        for x0 in (3, 11):
            for y0 in (3, 11):
                d.ellipse([x0 - 2, y0 - 2, x0 + 2, y0 + 2], fill=mix(base, (0, 0, 0), 0.5))
                d.point((x0, y0), fill=(235, 235, 245, 255)); d.point((x0 - 1, y0 - 1), fill=(170, 170, 180, 255))
                d.point((x0 + 1, y0 + 1), fill=(110, 110, 120, 255))
        d.point([(7, 7), (8, 8)], fill=(200, 200, 210, 255))
    elif mark == "plus":
        d.rectangle([6, 2, 9, 13], fill=col); d.rectangle([2, 6, 13, 9], fill=col); d.rectangle([7, 3, 8, 12], fill=glow)
    save(im, fid)
    side, _ = tiles(mix(STONE, col, 0.15), i + 50)
    ImageDraw.Draw(side).rectangle([0, 0, 15, 1], fill=col)
    save(side, fid + "_side")
    wj(f"{A}/models/block/{fid}.json", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": f"partydungeon:block/{fid}", "side": f"partydungeon:block/{fid}_side", "bottom": f"partydungeon:block/{fid}_side"}})
    wj(f"{A}/models/item/{fid}.json", {"parent": f"partydungeon:block/{fid}"})
    wj(f"{A}/blockstates/{fid}.json", {"variants": {"": {"model": f"partydungeon:block/{fid}"}}})

# 矢の発射台（石レンガにまぎれる見た目）
def bricks(seed):
    im = Image.new("RGBA", (16, 16)); d = ImageDraw.Draw(im); rnd = random.Random(seed)
    for y in range(16):
        for x in range(16):
            v = rnd.randint(-8, 8); im.putpixel((x, y), (122 + v, 122 + v, 122 + v, 255))
    m = (84, 84, 84, 255)
    for y in (3, 7, 11, 15): d.line([0, y, 15, y], fill=m)
    for row, y0 in enumerate((0, 4, 8, 12)):
        for x in ((7, 15) if row % 2 == 0 else (3, 11)): d.line([x, y0, x, y0 + 2], fill=m)
    return im, d
front, d = bricks(1)
d.rectangle([4, 5, 11, 10], fill=(40, 36, 32, 255)); d.rectangle([5, 6, 10, 9], fill=(16, 14, 12, 255))
d.line([5, 7, 10, 7], fill=(150, 120, 70, 255)); d.polygon([(11, 7), (9, 6), (9, 8)], fill=(200, 200, 210, 255))
d.rectangle([3, 4, 12, 4], fill=(150, 40, 30, 255)); d.rectangle([3, 11, 12, 11], fill=(150, 40, 30, 255))
save(front, "arrow_trap_front")
side, _ = bricks(2); save(side, "arrow_trap_side")
wj(f"{A}/models/block/arrow_trap.json", {"parent": "minecraft:block/orientable", "textures": {
    "front": "partydungeon:block/arrow_trap_front", "side": "partydungeon:block/arrow_trap_side", "top": "partydungeon:block/arrow_trap_side"}})
wj(f"{A}/models/block/arrow_trap_vertical.json", {"parent": "minecraft:block/orientable_vertical", "textures": {
    "front": "partydungeon:block/arrow_trap_front", "side": "partydungeon:block/arrow_trap_side"}})
wj(f"{A}/models/item/arrow_trap.json", {"parent": "partydungeon:block/arrow_trap"})
v = {"facing=north": {"model": "partydungeon:block/arrow_trap"}, "facing=east": {"model": "partydungeon:block/arrow_trap", "y": 90},
     "facing=south": {"model": "partydungeon:block/arrow_trap", "y": 180}, "facing=west": {"model": "partydungeon:block/arrow_trap", "y": 270},
     "facing=up": {"model": "partydungeon:block/arrow_trap_vertical"}, "facing=down": {"model": "partydungeon:block/arrow_trap_vertical", "x": 180}}
wj(f"{A}/blockstates/arrow_trap.json", {"variants": v})

# 崩れる床（0: ふつう 1: ひび 2: 消えている）
for st in (0, 1):
    im = Image.new("RGBA", (16, 16)); d = ImageDraw.Draw(im); rnd = random.Random(9)
    for y in range(16):
        for x in range(16):
            v = rnd.randint(-8, 8); im.putpixel((x, y), (200 + v, 176 + v, 120 + v, 255))
    d.rectangle([0, 0, 15, 15], outline=(150, 124, 76, 255)); d.rectangle([3, 3, 12, 12], outline=(176, 150, 98, 255))
    if st == 1:
        for c in ([(2, 1), (5, 5), (4, 9), (7, 14)], [(13, 2), (10, 6), (12, 11)], [(1, 12), (6, 9), (11, 8), (15, 6)]):
            d.line(c, fill=(90, 64, 30, 255))
    save(im, f"crumble_floor_{st}")
    wj(f"{A}/models/block/crumble_floor_{st}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"partydungeon:block/crumble_floor_{st}"}})
wj(f"{A}/models/block/empty.json", {"textures": {"particle": "partydungeon:block/crumble_floor_0"}, "elements": []})
wj(f"{A}/blockstates/crumble_floor.json", {"variants": {"stage=0": {"model": "partydungeon:block/crumble_floor_0"},
    "stage=1": {"model": "partydungeon:block/crumble_floor_1"}, "stage=2": {"model": "partydungeon:block/empty"}}})
wj(f"{A}/models/item/crumble_floor.json", {"parent": "partydungeon:block/crumble_floor_0"})

# 落下リターンブロック（うずまき）
im = Image.new("RGBA", (16, 16)); rnd = random.Random(3)
import math
for y in range(16):
    for x in range(16):
        dx, dy = x - 7.5, y - 7.5; r = math.hypot(dx, dy); a = math.atan2(dy, dx)
        t = (math.sin(a * 2 + r * 0.9) + 1) / 2
        im.putpixel((x, y), (int(40 + 90 * t), int(20 + 40 * t), int(90 + 150 * t), 255))
ImageDraw.Draw(im).rectangle([0, 0, 15, 15], outline=(30, 10, 60, 255))
save(im, "fall_return")
wj(f"{A}/models/block/fall_return.json", {"parent": "minecraft:block/cube_all", "textures": {"all": "partydungeon:block/fall_return"}})
wj(f"{A}/models/item/fall_return.json", {"parent": "partydungeon:block/fall_return"})
wj(f"{A}/blockstates/fall_return.json", {"variants": {"": {"model": "partydungeon:block/fall_return"}}})

# 間取りブロック（通路）
wj(f"{A}/models/block/plan_corridor.json", {"parent": "minecraft:block/cube_all", "textures": {"all": "minecraft:block/light_gray_concrete"}})
wj(f"{A}/models/item/plan_corridor.json", {"parent": "partydungeon:block/plan_corridor"})
wj(f"{A}/blockstates/plan_corridor.json", {"variants": {"": {"model": "partydungeon:block/plan_corridor"}}})

# 名前
for lang, idx in (("ja_jp", 1), ("en_us", 2)):
    p = f"{A}/lang/{lang}.json"; j = json.load(open(p, encoding="utf-8"))
    j["block.partydungeon.arrow_trap"] = "矢の発射台" if idx == 1 else "Arrow Trap"
    for f in FLOORS: j[f"block.partydungeon.{f[0]}"] = f[idx]
    j["block.partydungeon.crumble_floor"] = "崩れる床" if idx == 1 else "Crumbling Floor"
    j["block.partydungeon.fall_return"] = "落下リターンブロック" if idx == 1 else "Fall Return Block"
    j["block.partydungeon.plan_corridor"] = "間取りブロック（通路）" if idx == 1 else "Plan Block (Corridor)"
    j["block.partydungeon.plan_room"] = "間取りブロック（部屋）" if idx == 1 else "Plan Block (Room)"
    wj(p, j)
