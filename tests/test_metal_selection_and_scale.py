#!/usr/bin/env python3
"""Which Metal generation a session runs, as a contract rather than as remembered intent.

**Which Metal generation a session runs is the player's choice, and it is read before the device exists.**
Metallum asks `metallum.execution` once, while it creates the Metal device; Vitrail persists the choice in
`vitrail/metal-execution.txt` and pushes it into that property from `Vitrail.initClient`, which both loader
entry points reach before the device. Three properties of that arrangement are easy to break and invisible
when broken, so they are read here:

  * an explicit `-Dmetallum.execution=` outranks the stored setting, because a harness that named a
    generation is running a test of that generation;
  * the property is written only where the JVM left it unset, which is the same rule seen from the other
    side and the one a later refactor is most likely to lose;
  * toggling the setting never touches the running session - the binding writes a file and nothing else.

And the words: this round renamed a row and added two, so every key the screen can ask for has to exist in
`en_us` - the one locale the game falls back to, and therefore the only one whose absence shows a raw key to
a player - and no locale may hold a key nothing reads.

Run `--self-test` to prove each rule fires; it writes synthetic trees under a temporary directory and points
the same checkers at them, so a guard that cannot fail fails the self-test instead of passing quietly.
"""

from __future__ import annotations

import json
import re
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
COMMON = ROOT / "common/src/main/java/dev/vitrail"
LANG = ROOT / "common/src/main/resources/assets/vitrail/lang"

CHOICE = COMMON / "compat/metallum/MetallumExecutionChoice.java"
VITRAIL = COMMON / "Vitrail.java"
CONFIG_ENTRY = COMMON / "sodium/ConfigEntry.java"
SCREEN_TEXT = COMMON / "ScreenText.java"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def before(first: str, second: str, text: str, why: str) -> None:
    if first not in text or second not in text:
        raise SystemExit(why + " -- one of the two steps is not in the file at all")
    if text.index(first) > text.index(second):
        raise SystemExit(why)


def check_choice(choice: Path, vitrail: Path) -> None:
    """The stored choice, its two words, and where it is applied."""
    text = read(choice)

    for needle, why in (
        ('METAL3("metal3")', "the stored choice no longer has a Metal 3 word"),
        ('METAL4("metal4")', "the stored choice no longer has a Metal 4 word"),
        ("public static final MetallumExecutionChoice DEFAULT = METAL3;",
         "the stored choice's default is not Metal 3, so a fresh install would run the experimental path"),
        ('private static final String FILE = "metal-execution.txt";',
         "the choice is no longer stored in vitrail/metal-execution.txt, so an upgrade loses the setting"),
    ):
        if needle not in text:
            raise SystemExit("metal selection contract: " + why)

    # The precedence rule, in the order the method has to have it: the JVM's word is read first and answered
    # with, and the property is written only on the road where it was absent.
    before("if (asked != null) {", "System.setProperty(PROPERTY, stored.word);", text,
           "metal selection contract: an explicit -D would be overwritten by the stored setting, so a harness "
           "that asked for a generation could be handed the other one")
    if text.count("System.setProperty(") != 1:
        raise SystemExit("metal selection contract: the property is written somewhere other than the one road "
                         "that has already established it was unset")

    # The control layer only: this class may not reach the device, claim support, or know a chip.
    for forbidden in ("MTL", "MetalDevice", "RenderSystem", "Backends", "supported = true"):
        if forbidden in text:
            raise SystemExit(f"metal selection contract: the stored choice names {forbidden}, so the control "
                             "layer is deciding something only the backend can answer")
    for chip in ("Apple M", "M1", "M2", "M3 Pro", "M4 Max"):
        if chip in text:
            raise SystemExit(f"metal selection contract: the stored choice names a chip ({chip}), which is a "
                             "table of hardware and not a question about the stored setting")

    # And it is applied at the entry point, after the platform exists and before anything else this mod does.
    entry = read(vitrail)
    before("platform = loaderPlatform;", "MetallumExecutionChoice.apply();", entry,
           "metal selection contract: the choice is applied before the platform exists, so it cannot find the "
           "game directory it is stored under")
    before("MetallumExecutionChoice.apply();", "LOGGER.info(\"Vitrail {} starting on", entry,
           "metal selection contract: the choice is applied after this mod has finished starting, which is "
           "later than the device it has to reach")
    if "MetallumExecutionChoice.apply()" not in entry:
        raise SystemExit("metal selection contract: the loader entry point no longer applies the stored choice")


def check_toggle(config_entry: Path) -> None:
    """The settings row: a boolean, restart-flagged, and inert in the running session."""
    text = read(config_entry)
    start = text.index("private static OptionBuilder metal4(")
    end = text.index("\n\t}", start)
    body = text[start:end]

    for needle, why in (
        ("builder.createBooleanOption(METAL4)", "the Metal 4 setting is not a boolean option"),
        ("OptionFlag.REQUIRES_GAME_RESTART",
         "the setting is not flagged as needing a restart, so the screen would tell a player it took effect"),
        ("MetallumExecutionChoice.write(", "the setting no longer persists the choice the next launch reads"),
        ("MetallumExecutionChoice.read()", "the setting no longer shows the stored choice"),
    ):
        if needle not in body:
            raise SystemExit("metal selection contract: " + why)

    # A live switch is not something this engine can do, so the binding may not touch the device at all.
    for forbidden in ("RenderSystem", "Backends.", "MetalDevice", "engine", "device."):
        if forbidden in body:
            raise SystemExit(f"metal selection contract: toggling the setting reaches {forbidden}, so it would "
                             "change the running session rather than the next launch")

    # The row is on the page, and the page is the video settings rather than a shader-pack sub-page.
    before("addOption(metal4(builder))", "addOption(graphicsApi(builder))", text,
           "metal selection contract: the Metal 4 row is not on the engine's own page")


def check_language(lang: Path, screen_text: Path) -> None:
    """Every key the screen can ask for exists in en_us, and no locale invents one."""
    en = json.loads((lang / "en_us.json").read_text(encoding="utf-8"))
    declared = set(re.findall(r'public static final String \w+ = "([^"]+)";', read(screen_text)))
    if len(declared) < 20:
        raise SystemExit(f"language contract: only {len(declared)} string keys were read out of ScreenText, so "
                         "the reading has stopped finding them rather than the translations being complete")
    # Keys outside this mod's own namespaces belong to the files that own them - `options.graphicsApi` is
    # Sodium's option and `key.vitrail.*` is the game's keybind file - so the parity this checks is over the
    # keys this mod ships translations for.
    keys = {key for key in declared if key.startswith(("options.vitrail.", "pack.vitrail.", "overlay.vitrail."))}

    missing = sorted(key for key in keys if key not in en)
    if missing:
        raise SystemExit("language contract: en_us is missing " + ", ".join(missing)
                         + " -- the game falls back to en_us, so a missing key here is a raw key on screen")

    for path in sorted(lang.glob("*.json")):
        locale = json.loads(path.read_text(encoding="utf-8"))
        extra = sorted(set(locale) - set(en))
        if extra:
            raise SystemExit(f"language contract: {path.name} holds keys nothing reads: " + ", ".join(extra))

    for key, why in (
        ("options.vitrail.metal4", "the Metal 4 row has no caption in en_us"),
        ("options.vitrail.metal4_tooltip", "the Metal 4 row has no tooltip in en_us"),
    ):
        if key not in en:
            raise SystemExit("language contract: " + why)
        for path in sorted(lang.glob("*.json")):
            if key not in json.loads(path.read_text(encoding="utf-8")):
                raise SystemExit(f"language contract: {path.name} does not carry {key}, so that locale shows "
                                 "the key this round added as untranslated rather than falling back to en_us")



def self_test() -> None:
    """Prove every rule fires, on trees written for the purpose."""
    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        choice = root / "MetallumExecutionChoice.java"
        vitrail = root / "Vitrail.java"

        choice.write_text(
            'METAL3("metal3")\nMETAL4("metal4")\n'
            "public static final MetallumExecutionChoice DEFAULT = METAL3;\n"
            'private static final String FILE = "metal-execution.txt";\n'
            "public static MetallumExecutionChoice apply() {\n"
            "    String asked = System.getProperty(PROPERTY);\n"
            "    if (asked != null) {\n        return known(asked);\n    }\n"
            "    System.setProperty(PROPERTY, stored.word);\n    return stored;\n}\n",
            encoding="utf-8")
        vitrail.write_text(
            "platform = loaderPlatform;\nMetallumExecutionChoice.apply();\n"
            'LOGGER.info("Vitrail {} starting on", 1);\n', encoding="utf-8")
        check_choice(choice, vitrail)

        # A stored setting applied after the platform is gone: the same tree, one line moved.
        vitrail.write_text(
            "MetallumExecutionChoice.apply();\nplatform = loaderPlatform;\n"
            'LOGGER.info("Vitrail {} starting on", 1);\n', encoding="utf-8")
        if not fires(lambda: check_choice(choice, vitrail)):
            raise SystemExit("metal selection self-test: a choice applied before the platform exists passed")
        vitrail.write_text(
            "platform = loaderPlatform;\n"
            'LOGGER.info("Vitrail {} starting on", 1);\nMetallumExecutionChoice.apply();\n', encoding="utf-8")
        if not fires(lambda: check_choice(choice, vitrail)):
            raise SystemExit("metal selection self-test: a choice applied after the mod started passed")

        # An unconditional write, which is how a stored setting would silently beat the JVM's own word.
        choice.write_text(
            'METAL3("metal3")\nMETAL4("metal4")\n'
            "public static final MetallumExecutionChoice DEFAULT = METAL3;\n"
            'private static final String FILE = "metal-execution.txt";\n'
            "public static MetallumExecutionChoice apply() {\n"
            "    String asked = System.getProperty(PROPERTY);\n"
            "    if (asked != null) {\n        return known(asked);\n    }\n"
            "    System.setProperty(PROPERTY, stored.word);\n    return stored;\n}\n"
            "    static { System.setProperty(PROPERTY, \"metal3\"); }\n", encoding="utf-8")
        if not fires(lambda: check_choice(choice, vitrail)):
            raise SystemExit("metal selection self-test: a second, unconditional property write passed")

        # The toggle, on a tree that reaches the device.
        config = root / "ConfigEntry.java"
        config.write_text(
            "private static OptionBuilder metal4(ConfigBuilder builder) {\n"
            "    builder.createBooleanOption(METAL4)\n"
            "        .setBinding(chosen -> MetallumExecutionChoice.write(dir, chosen),\n"
            "                () -> MetallumExecutionChoice.read() == METAL4)\n"
            "        .setFlags(OptionFlag.REQUIRES_GAME_RESTART);\n"
            "    RenderSystem.getDevice();\n"
            "\t}\naddOption(metal4(builder))\naddOption(graphicsApi(builder))\n", encoding="utf-8")
        if not fires(lambda: check_toggle(config)):
            raise SystemExit("metal selection self-test: a toggle that reaches the running device passed")

        config.write_text(
            "private static OptionBuilder metal4(ConfigBuilder builder) {\n"
            "    builder.createBooleanOption(METAL4)\n"
            "        .setBinding(chosen -> MetallumExecutionChoice.write(dir, chosen),\n"
            "                () -> MetallumExecutionChoice.read() == METAL4)\n"
            "        .setFlags(OptionFlag.REQUIRES_GAME_RESTART);\n"
            "\t}\naddOption(metal4(builder))\naddOption(graphicsApi(builder))\n", encoding="utf-8")
        check_toggle(config)

        # The words, on a locale tree missing one key.
        lang = root / "lang"
        lang.mkdir()
        screen_text = root / "ScreenText.java"
        screen_text.write_text(
            "\n".join(f'public static final String K{i} = "options.vitrail.k{i}";' for i in range(20)),
            encoding="utf-8")
        keys = {f"options.vitrail.k{i}": f"k{i}" for i in range(20)}
        keys.update({
            "options.vitrail.metal4": "Metal 4 (Experimental)",
            "options.vitrail.metal4_tooltip": "Uses the experimental Metal 4 renderer. Restart to apply.",
            "options.vitrail.render_scale_native": "100% (Off)",
            "options.vitrail.render_scale": "MetalFX Render Scale",
            "options.vitrail.render_scale_tooltip": "MetalFX. 100% is native.",
        })
        keys["options.vitrail.k0"] = "k0"
        (lang / "en_us.json").write_text(json.dumps(keys), encoding="utf-8")
        (lang / "xx_xx.json").write_text(json.dumps(keys), encoding="utf-8")
        check_language(lang, screen_text)

        (lang / "xx_xx.json").write_text(json.dumps({**keys, "options.vitrail.new": "x"}), encoding="utf-8")
        if not fires(lambda: check_language(lang, screen_text)):
            raise SystemExit("language self-test: a locale holding a key en_us does not have passed")
        (lang / "xx_xx.json").write_text(json.dumps(keys), encoding="utf-8")

        (lang / "en_us.json").write_text(json.dumps({k: v for k, v in keys.items()
                                                     if k != "options.vitrail.k7"}), encoding="utf-8")
        if not fires(lambda: check_language(lang, screen_text)):
            raise SystemExit("language self-test: a key missing from en_us passed")
        (lang / "en_us.json").write_text(json.dumps(keys), encoding="utf-8")
        (lang / "zh_cn.json").write_text(json.dumps(keys), encoding="utf-8")

    print("metal selection contract: self-test PASS")


def fires(check) -> bool:
    """Whether a check raises, which is what the self-test asks of every synthetic tree."""
    try:
        check()
    except SystemExit:
        return True

    return False


def main() -> int:
    if "--self-test" in sys.argv:
        self_test()
        return 0

    check_choice(CHOICE, VITRAIL)
    check_toggle(CONFIG_ENTRY)
    check_language(LANG, SCREEN_TEXT)

    locales = len(list(LANG.glob("*.json")))
    print(f"metal selection contract: PASS ({locales} locales, the stored choice applied at the entry point, "
          f"and an explicit -D still outranking it)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
