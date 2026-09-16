from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
PACK_COMPUTE = ROOT / "common/src/main/java/dev/vitrail/render/PackCompute.java"
PACK_CHAIN = ROOT / "common/src/main/java/dev/vitrail/render/PackChain.java"


def text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


class SetupComputeContract(unittest.TestCase):
    def test_setup_is_loaded_instead_of_dropped(self):
        compute = text(PACK_COMPUTE)
        self.assertIn("private final List<Pass> setup;", compute)
        self.assertIn('boolean setupCompute = family.equals("setup")', compute)
        self.assertIn('Loaded setup compute {} ({})', compute)
        self.assertNotIn("Setup has no moment in this frame yet", compute)
        self.assertIn("setup.add(pass);", compute)
        self.assertIn("storageTargets.addAll(colourImagesOf(compute.get()));", compute)

    def test_setup_uses_initial_target_side_and_iris_dispatch_size(self):
        compute = text(PACK_COMPUTE)
        self.assertIn('new TargetSchedule.Bound("setup", List.of(), true, Set.of(), Set.of())', compute)
        self.assertIn('dispatchChain(this.setup, "setup"', compute)
        self.assertIn("null, null, 1, 1);", compute)
        self.assertIn("this.setup.forEach(Pass::close);", compute)

    def test_setup_runs_after_clear_only_when_allocation_size_changes(self):
        chain = text(PACK_CHAIN)
        clear = chain.index("this.targets.clear(encoder, this.fogClear);")
        flush = chain.index("this.targets.flushPending(encoder);", clear)
        setup = chain.index("this.compute.dispatchSetup(encoder, device, this.values, this.targets);", flush)
        opened = chain.index("this.opened = true;", setup)
        self.assertLess(clear, flush)
        self.assertLess(flush, setup)
        self.assertLess(setup, opened)
        self.assertIn("main.width != this.setupWidth || main.height != this.setupHeight", chain)
        self.assertLess(setup, chain.index("this.setupWidth = main.width;", setup))


if __name__ == "__main__":
    unittest.main()
