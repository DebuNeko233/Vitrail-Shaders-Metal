"""Lock pack-defined compiler-builtin helpers behind Vitrail's generic shadowing path."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
LEGACY = ROOT / "common/src/main/java/dev/vitrail/glsl/LegacyGlsl.java"
TRANSLATOR = ROOT / "common/src/main/java/dev/vitrail/glsl/GlslTranslator.java"


class BuiltinShadowingContractTest(unittest.TestCase):
    def test_trinary_minmax_family_is_shadowable(self):
        source = LEGACY.read_text(encoding="utf-8")
        match = re.search(
            r"SHADOWABLE_BUILTINS\s*=\s*Set\.of\((.*?)\);",
            source,
            flags=re.S,
        )
        self.assertIsNotNone(match)
        listed = match.group(1)
        for name in ("min3", "max3", "mid3"):
            self.assertIn(f'"{name}"', listed)

    def test_shadowing_requires_a_pack_function_declaration(self):
        source = TRANSLATOR.read_text(encoding="utf-8")
        collect = source.split("private void collectDeclarations()", 1)[1].split(
            "/**\n\t * The names a declaration declares", 1
        )[0]
        rewrite = source.split("private void rewriteIdentifiers()", 1)[1].split(
            "if (name.equals(\"gl_TextureMatrix\")", 1
        )[0]

        self.assertIn("LegacyGlsl.SHADOWABLE_BUILTINS.contains(token.text())", collect)
        self.assertIn("this.tokens.callOpener(index) >= 0", collect)
        self.assertIn("this.shadowedBuiltins.add(token.text())", collect)
        self.assertIn("this.shadowedBuiltins.contains(name)", rewrite)
        self.assertIn('this.tokens.replace(index, "of_" + name);', rewrite)
        self.assertNotIn("Sundial", collect)
        self.assertNotIn("Sundial", rewrite)


if __name__ == "__main__":
    unittest.main()
