#!/usr/bin/env python3
"""
Survival reachability check: which Factory Ascent items can actually be obtained?

Starting from every vanilla item (assumed obtainable) plus what the mod's ores drop, it applies
recipes until nothing new appears:
- crafting (shaped, shapeless, transmute) and smelting/blasting recipes of this mod
- machine recipes, which need a machine of that kind whose grade is at least the recipe's
  min_grade to itself be obtainable (grades come from MachineType.java)

Item tags are resolved from this mod's data, the vanilla client jar and the NeoForge jar.
Prints every mod item that stays unobtainable and the recipes that are blocked, then exits
non-zero if anything is unobtainable.

    python3 tools/check_progression.py            # report
    python3 tools/check_progression.py --why ITEM # show what blocks one item
"""
import json
import re
import sys
import zipfile
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
MOD = "factoryascent"
CLIENT_JAR = Path("/root/.gradle/caches/neoformruntime/artifacts/minecraft_26.2_client.jar")

# Vanilla items you can't get in survival (so they don't count as free inputs).
NOT_SURVIVAL = {"minecraft:bedrock", "minecraft:command_block", "minecraft:barrier", "minecraft:structure_block",
                "minecraft:jigsaw", "minecraft:debug_stick", "minecraft:light", "minecraft:spawner",
                "minecraft:reinforced_deepslate", "minecraft:end_portal_frame", "minecraft:budding_amethyst",
                "minecraft:petrified_oak_slab", "minecraft:player_head", "minecraft:knowledge_book"}


def ns(x):
    return x if ":" in x else f"minecraft:{x}"


# ---------------------------------------------------------------- tags

def load_tags():
    tags = defaultdict(set)  # "c:ingots/iron" -> {entries (items or "#tags")}

    def add(ns_, path, data):
        key = f"{ns_}:{path}"
        if data.get("replace"):
            tags[key] = set()
        for v in data.get("values", []):
            if isinstance(v, dict):
                v = v["id"]
            tags[key].add(v)

    def scan_zip(jar):
        with zipfile.ZipFile(jar) as z:
            for n in z.namelist():
                m = re.match(r"data/([^/]+)/tags/item/(.+)\.json$", n)
                if m:
                    add(m.group(1), m.group(2), json.loads(z.read(n)))

    if CLIENT_JAR.exists():
        scan_zip(CLIENT_JAR)
    for jar in Path("/root/.gradle/caches").rglob("neoforge-26.2.0.88-universal.jar"):
        scan_zip(jar)
        break
    for f in (RES / "data").glob("*/tags/item/**/*.json"):
        rel = f.relative_to(RES / "data")
        ns_ = rel.parts[0]
        path = "/".join(rel.parts[3:])[:-5]
        add(ns_, path, json.loads(f.read_text()))
    return tags


def expand(tag, tags, seen=None):
    seen = seen or set()
    out = set()
    for v in tags.get(tag, ()):
        if v.startswith("#"):
            t = v[1:]
            if t not in seen:
                seen.add(t)
                out |= expand(t, tags, seen)
        else:
            out.add(ns(v))
    return out


# ---------------------------------------------------------------- mod items and machines

def mod_items():
    lang = json.loads((RES / f"assets/{MOD}/lang/en_us.json").read_text())
    items = set()
    for k in lang:
        m = re.match(rf"(item|block)\.{MOD}\.([a-z0-9_]+)$", k)
        if m:
            items.add(f"{MOD}:{m.group(2)}")
    return items


def machine_grades():
    """kind -> list of (machine item id, grade), from MachineType.java."""
    src = (ROOT / "src/main/java/net/juli2kapo/factoryascent/machine/MachineType.java").read_text()
    out = defaultdict(list)
    for m in re.finditer(r"^\s+([A-Z_]+)\(Age\.\w+, Category\.PROCESSOR, RecipeKind\.([A-Z]+), Power\.\w+, (\d+)", src, re.M):
        out[m.group(2).lower()].append((f"{MOD}:{m.group(1).lower()}", int(m.group(3))))
    return out


# ---------------------------------------------------------------- recipes

def ingredient_options(ing, tags):
    """A recipe ingredient -> set of item ids that satisfy it."""
    if isinstance(ing, list):
        s = set()
        for i in ing:
            s |= ingredient_options(i, tags)
        return s
    if isinstance(ing, dict):
        if "ingredient" in ing:
            return ingredient_options(ing["ingredient"], tags)
        if "tag" in ing:
            return expand(ing["tag"], tags)
        if "item" in ing:
            return {ns(ing["item"])}
        if "items" in ing:
            return ingredient_options(ing["items"], tags)
        return set()
    if isinstance(ing, str):
        if ing.startswith("#"):
            return expand(ing[1:], tags)
        return {ns(ing)}
    return set()


def result_id(r):
    if isinstance(r, str):
        return ns(r)
    return ns(r.get("id") or r.get("item"))


def load_recipes(tags):
    """-> list of (name, output ids, [input option sets], required (kind, grade) or None)."""
    recipes = []
    for f in (RES / "data").glob("*/recipe/**/*.json"):
        name = str(f.relative_to(RES / "data"))
        r = json.loads(f.read_text())
        t = r.get("type", "")
        outs, ins, need = [], [], None
        if t == "minecraft:crafting_shaped":
            used = set("".join(r["pattern"])) - {" "}
            ins = [ingredient_options(v, tags) for k, v in r["key"].items() if k in used]
            outs = [result_id(r["result"])]
        elif t == "minecraft:crafting_shapeless":
            ins = [ingredient_options(i, tags) for i in r["ingredients"]]
            outs = [result_id(r["result"])]
        elif t in ("minecraft:crafting_transmute", f"{MOD}:jet_suit"):  # jet_suit: a transmute that keeps both items' data
            ins = [ingredient_options(r["input"], tags), ingredient_options(r["material"], tags)]
            outs = [result_id(r["result"])]
        elif t in ("minecraft:smelting", "minecraft:blasting", "minecraft:smoking", "minecraft:campfire_cooking"):
            ins = [ingredient_options(r["ingredient"], tags)]
            outs = [result_id(r["result"])]
        elif t.startswith(f"{MOD}:") and "result" in r:  # special recipes (no fixed result) are skipped
            kind = t.split(":", 1)[1]
            ins = [ingredient_options(i, tags) for i in r.get("ingredients", [])]
            if "mold" in r:
                ins.append(ingredient_options(r["mold"], tags))
            outs = [result_id(r["result"])]
            if r.get("byproduct"):
                outs.append(result_id(r["byproduct"]["item"]))
            for extra in r.get("extras", []):
                outs.append(result_id(extra["item"]))
            need = (kind, r.get("min_grade", 1))
        else:
            continue  # special recipes (custom types) are reported separately
        recipes.append((name, outs, ins, need))
    return recipes


def ore_drops():
    """Items the mod's blocks drop (ores give raw materials, everything drops something)."""
    drops = defaultdict(set)  # block -> items
    for f in (RES / f"data/{MOD}/loot_table/blocks").glob("*.json"):
        block = f"{MOD}:{f.stem}"
        text = f.read_text()
        for m in re.finditer(r'"name":\s*"([^"]+)"', text):
            drops[block].add(ns(m.group(1)))
    return drops


def worldgen_blocks():
    """Mod blocks placed by world generation: ores (configured features) and the terrain of the mod's own
    dimensions (the planets' noise settings: default block, fluid and surface rules)."""
    found = set()
    for sub in ("configured_feature", "noise_settings"):
        for f in (RES / f"data/{MOD}/worldgen/{sub}").glob("*.json"):
            for m in re.finditer(r'"Name":\s*"(factoryascent:[^"]+)"', f.read_text()):
                found.add(m.group(1))
    return found


def feature_progression():
    """Conversions that aren't recipes (a reactor burning fuel rods into spent ones...), declared by feature
    modules as PROGRESSION = [(name, [outputs], [inputs], [blocks that must be obtainable])]."""
    import importlib.util
    out = []
    for path in sorted((ROOT / "tools" / "features").glob("*.py")):
        if "PROGRESSION = " not in path.read_text():
            continue
        spec = importlib.util.spec_from_file_location(f"progression_{path.stem}", path)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        for name, outs, ins, blocks in getattr(module, "PROGRESSION", []):
            out.append((f"{path.stem}: {name}", [ns(o) if ":" in o else f"{MOD}:{o}" for o in outs],
                        [{ns(i) if ":" in i else f"{MOD}:{i}"} for i in list(ins) + list(blocks)], None))
    return out


def main():
    why = sys.argv[sys.argv.index("--why") + 1] if "--why" in sys.argv else None
    tags = load_tags()
    items = mod_items()
    grades = machine_grades()
    recipes = load_recipes(tags) + feature_progression()
    drops = ore_drops()

    have = set()
    # vanilla: everything but creative-only items
    if CLIENT_JAR.exists():
        with zipfile.ZipFile(CLIENT_JAR) as z:
            for n in z.namelist():
                m = re.match(r"assets/minecraft/items/([a-z0-9_]+)\.json$", n)
                if m:
                    have.add(f"minecraft:{m.group(1)}")
    have -= NOT_SURVIVAL
    for block in worldgen_blocks():
        have.add(block)  # silk touch
        have |= drops.get(block, set())

    changed = True
    while changed:
        changed = False
        for name, outs, ins, need in recipes:
            if all(o in have for o in outs):
                continue
            if need:
                kind, g = need
                if not any(m in have and mg >= g for m, mg in grades.get(kind, [])):
                    continue
            if all(opts & have for opts in ins):
                for o in outs:
                    if o not in have:
                        have.add(o)
                        changed = True
        # placed blocks drop themselves (and loot)
        for block, items_ in drops.items():
            if block in have and not items_ <= have:
                have |= items_
                changed = True

    missing = sorted(i for i in items if i not in have)
    if why:
        target = ns(why) if ":" in why else f"{MOD}:{why}"
        print(f"{target}: {'obtainable' if target in have else 'NOT obtainable'}")
        for name, outs, ins, need in recipes:
            if target in outs:
                gaps = [sorted(opts)[:4] for opts in ins if not opts & have]
                machine = ""
                if need:
                    kind, g = need
                    ok = [m for m, mg in grades.get(kind, []) if mg >= g]
                    machine = f"  needs {kind} grade {g}: " + (", ".join(ok) if ok else "NO SUCH MACHINE")
                print(f"  {name}{machine}")
                for gap in gaps:
                    print(f"      missing one of {gap}")
        return
    print(f"{len(items) - len(missing)}/{len(items)} mod items obtainable in survival")
    if missing:
        print("UNOBTAINABLE:")
        for i in missing:
            print("  ", i)
        # machine grades that recipes ask for but no machine provides
        asked = defaultdict(set)
        for name, outs, ins, need in recipes:
            if need:
                asked[need[0]].add(need[1])
        for kind, gs in sorted(asked.items()):
            top = max((g for _, g in grades.get(kind, [])), default=0)
            too_high = sorted(g for g in gs if g > top)
            if too_high:
                print(f"  recipes ask for {kind} grade {too_high}, best machine is grade {top}")
        sys.exit(1)


if __name__ == "__main__":
    main()
