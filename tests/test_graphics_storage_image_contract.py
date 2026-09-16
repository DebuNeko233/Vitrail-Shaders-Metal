from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SAMPLER_TYPES = ROOT / "common/src/main/java/dev/vitrail/pack/target/SamplerTypes.java"
SAMPLER_PLAN = ROOT / "common/src/main/java/dev/vitrail/pack/target/SamplerPlan.java"
TARGET_PLAN = ROOT / "common/src/main/java/dev/vitrail/pack/target/TargetPlan.java"
COLOR_TARGETS = ROOT / "common/src/main/java/dev/vitrail/render/ColorTargets.java"
GRAPHICS_IMAGES = ROOT / "common/src/main/java/dev/vitrail/render/PackStorageImages.java"
PACK_PASS = ROOT / "common/src/main/java/dev/vitrail/render/PackPass.java"
GEOMETRY = ROOT / "common/src/main/java/dev/vitrail/render/GeometryProgram.java"


def text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


class GraphicsStorageImageContract(unittest.TestCase):
    def test_target_plan_keeps_image_writes_distinct_for_preallocation(self):
        plan = text(TARGET_PLAN)
        self.assertIn("private final Set<Integer> imageWritten;", plan)
        self.assertIn("draft.imageWritten.addAll(imageWrites);", plan)
        self.assertIn("draft.imageWritten.addAll(images);", plan)
        self.assertIn("public Set<Integer> imageWritten()", plan)

    def test_color_targets_are_shader_writable_before_first_allocation(self):
        targets = text(COLOR_TARGETS)
        constructor = targets.index("this.plan = plan;")
        allocation = targets.index("boolean storage = this.storageTargets.contains(index)")
        self.assertIn("this.storageTargets = Set.copyOf(plan.imageWritten());", targets)
        self.assertLess(constructor, allocation)
        self.assertIn("Set<Integer> merged = new TreeSet<>(this.storageTargets);", targets)
        self.assertIn("merged.addAll(targets);", targets)

    def test_colorimg_is_typed_as_storage_image_and_keeps_schedule_side(self):
        types = text(SAMPLER_TYPES)
        samplers = text(SAMPLER_PLAN)
        self.assertIn("public static boolean image(String type)", types)
        self.assertIn("DISTANT_DEPTH, COLOR_IMAGE, CUSTOM_IMAGE", samplers)
        self.assertIn("SamplerTypes.image(type) && TargetName.imageIndex(name).isPresent()", samplers)
        self.assertIn("new Binding(name, Kind.COLOR_IMAGE, index, side(step, index), false)", samplers)

    def test_fullscreen_graphics_binds_storage_view_not_black_fallback(self):
        helper = text(GRAPHICS_IMAGES)
        pack_pass = text(PACK_PASS)
        self.assertIn("surface.storage() ? surface.storageView() : null", helper)
        self.assertIn("this.storageImages = PackStorageImages.names(loaded);", pack_pass)
        self.assertIn("PackStorageImages.view(sampler, binding, targets)", pack_pass)
        self.assertIn("no storage-capable image is available", pack_pass)

    def test_geometry_uses_same_binding_and_refuses_attachment_alias(self):
        helper = text(GRAPHICS_IMAGES)
        geometry = text(GEOMETRY)
        self.assertIn("attachmentConflicts", helper)
        self.assertIn("loaded.samplers(), this.extra", geometry)
        self.assertIn("keeps the game's shader", geometry)
        self.assertIn("return PackStorageImages.view(sampler, one.binding, this.targets);", geometry)
        self.assertIn("if (!resolve())", geometry)


if __name__ == "__main__":
    unittest.main()
