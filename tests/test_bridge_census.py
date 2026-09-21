"""The bridge census is on every road across the seam, and it counts a lookup apart from a call.

Track D of the long-term plan asks what the Vitrail-to-Metallum seam costs a frame: calls, CPU and the
reflection behind them. Every call across it is a reflective `Method.invoke`, and the resolution of those
methods is cached per bridge - so the two readings have to be counted apart, or "reflection only at startup"
stays an assertion. This file pins both halves and the arithmetic the report rests on.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
BRIDGES = ROOT / "common/src/main/java/dev/vitrail/compat/metallum"
CENSUS = BRIDGES / "BridgeCensus.java"

# Every bridge, and how many roads into Metallum each one carries: one per reflective invoke helper except the
# scale and attachment bridges, which invoke their methods where they use them.
ROADS = {
    "MetallumFrameBridge.java": 1,
    "MetallumComputeBridge.java": 1,
    "MetallumDepthMipmapBridge.java": 1,
    "MetallumSamplerBridge.java": 1,
    "MetallumTextureBridge.java": 1,
    "MetallumScaleBridge.java": 2,
    "MetallumAttachmentBridge.java": 2,
}


def text(path):
    return path.read_text(encoding="utf-8")


class BridgeCensusContract(unittest.TestCase):

    def test_census_counts_calls_cpu_and_varargs_arrays(self):
        census = text(CENSUS)
        for needle in (
            "static final int FRAME = 0;",
            "static final int COMPUTE = 1;",
            "static final int DEPTH = 2;",
            "static final int SAMPLER = 3;",
            "static final int TEXTURE = 4;",
            "static final int SCALE = 5;",
            "static final int ATTACHMENT = 6;",
            "static void invoked(final int bridge, final int argumentCount, final long elapsedNanos) {",
            "static void lookedUp() {",
            '"Vitrail bridge: %d calls over %d ms (%.1f a second, %.0f ns a call), %s - "',
            "long arrayBytes = arrays * 16L + slots * 4L;",
            "private static Consumer<String> sink;",
            "static void reporter(final Consumer<String> installed) {",
        ):
            self.assertIn(needle, census, needle)

    def test_every_bridge_road_is_counted(self):
        total = 0
        for name, expected in ROADS.items():
            source = text(BRIDGES / name)
            found = source.count("BridgeCensus.invoked(")
            self.assertEqual(expected, found, f"{name} counts {found} call roads, not {expected}")
            total += found
        self.assertEqual(9, total, "a road across the seam is not counted")

    def test_every_bridge_resolution_is_counted_apart(self):
        for name in ROADS:
            source = text(BRIDGES / name)
            self.assertEqual(
                1, source.count("BridgeCensus.lookedUp()"),
                f"{name} does not count its reflective resolution",
            )

    def test_the_call_is_counted_where_it_is_made(self):
        # The timer has to be taken before the invoke and reported after it, in the same method, or the number is
        # the cost of something else.
        for name in ROADS:
            source = text(BRIDGES / name)
            self.assertIn("long began = System.nanoTime();", source, name)
            self.assertIn("System.nanoTime() - began", source, name)


if __name__ == "__main__":
    unittest.main()
