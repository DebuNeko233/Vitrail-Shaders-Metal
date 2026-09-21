# The render scale, and what it covers

**MetalFX Render Scale**, under Video Settings on Vitrail's own page, draws the world at a fraction of
the window and brings the finished picture back to full size before the interface. It is the
setting to reach for when a pack runs but runs slowly, and it is worth knowing exactly how far it
reaches, because a lot of a frame is not measured in pixels at all.

The row was called **Render Scale** until the name learned to say what does the upscaling. Only the
name changed: the identifier, the key in `pack.txt` and the stored number are the same, so an
existing scale survives the update.

Two things about it before anything else. At 100 percent it does not run, and that is now what the
row says - it reads `100% (Off)` rather than a bare number, because the value a player is looking for
there is "this is off". The world is drawn at the window's own size, no scaled target is created, the
MetalFX scaler is never asked for and the fallback upscale never runs. And it only engages while a
pack is drawing, so with shaders off it changes nothing either.

## What follows it

Nearly everything with a screen-sized life, and it follows without being told. The whole mechanism
is the size of the game's main render target, and the rest of the frame reads its size off that
one object each frame rather than off the window:

- the pack's colour targets, and the depth images the engine converts for it;
- the translucency targets the game's own frame graph describes;
- `viewWidth` and `viewHeight`, which is what a pack computes its own texel sizes from, so a pass
  that samples its neighbours keeps sampling the right ones;
- the entity outline target, which is resized alongside by hand because it is the one screen-sized
  target allocated outside the frame's pool.

So a composite chain of thirty passes runs on thirty smaller images, and none of those passes had
to be told about it.

**The interface never follows.** The scaled picture is drawn up onto the window-sized colour
texture before any widget lands on it, so menus, chat, the F3 screen and the hotbar stay at the
window's own resolution whatever the slider says.

## What does not follow it

**The shadow map is the one screen-sized-feeling thing that keeps its own number**, and it is not
an oversight. A pack asks for a square map by its own directive, 1024 unless it says otherwise,
and everything it then does with shadows is computed from that number: its filter radius, its
depth bias, and the coordinates it reads the map at. A map allocated at any other size than the
one the pack was told is not a coarser shadow, it is a picture computed against an image that
does not exist.

That is why it has a slider of its own, **Shadow Map Scale**, beside the render scale and
defaulting to the whole map, and why moving it reloads the pack. What the slider changes is the
number the pack is TOLD, by rewriting the declaration the pack makes of it before a line is
translated, so the pack recomputes everything against the map it really gets. That is what makes
a smaller map a wider penumbra: the filter radius is a fraction of the map, so it covers more of
the world as the map shrinks.

**That holds while the pack smooths its shadows, and not otherwise.** A pack taking a single
sample has no radius to widen, so a smaller map simply gives it a coarser edge, and packs ship
quality profiles that turn shadows on without their filter. Either way the pack is computing
against the map it has, which is the part that matters; what a player sees at the low end is not
the same in both cases.

It is a real trade and a player has to make it deliberately; dragging it along behind a slider
about the window would hand it to somebody who only wanted the world smaller. The engine says at
load that the setting is in force, and the line where the map is allocated says the size it came
out at.

It composes with the pack's own setting rather than replacing it. Four packs of the test corpus
already offer this resolution as a slider in their own screen, and what this scales is whatever
the pack and the player between them settled on there.

The panorama capture does not follow it either, and never will: it renders the world at 4096
square down a path that is not the ordinary frame at all, so the scale never sees it.

## Bringing it back: MetalFX

The picture is brought back up in one step, by MetalFX's spatial scaler, which the Metal backend owns
and this engine asks for through a narrow capability: the scaled colour texture in, the game's own
window-sized colour texture out, one encode. It used to be two passes of this engine's own - AMD's
FidelityFX Super Resolution 1.0, an edge-adaptive upsample into a window-sized image and then a
contrast-adaptive sharpen - and those are gone, along with the intermediate image they needed and the
temporal fold that ran between them. The reasoning is in the performance plan; what a reader of this
page needs is what changed underneath the slider.

**The upscale still runs at the window's size and is still a fixed cost.** Whatever writes the window's
pixels, it writes all of them however small the world was drawn, so lowering the scale makes the world
cheaper and leaves the upscale where it was. What that cost is has been measured from both sides: the
FSR 1.0 pair took 2.31 ms of a frame at 1800x1019 and MetalFX's encode takes 0.38, so the same slider
setting now returns more of what it saves. The numbers and the fits behind them are in the performance
plan.

**A device without it gets a blit.** Where MetalFX is not there to be used - an older system, a GPU that
refuses the scaler, a backend that is not this one - the picture is brought back with a plain bilinear
pass instead. The slider keeps working and the picture keeps arriving; what it loses is the sharpness,
and the log says which of the two roads a session is on.

**And the log says which of the three states, not two.** A session that scales announces itself twice -
`The world renders at 1056x660 for a 1920x1200 window, render scale 55%` and `The 55% render scale
brings the picture back with MetalFX` - and the off position used to announce nothing at all, so a run
at 100 percent was a run with no scale line, which is an absence a reader has to argue about rather than
read. It now says `The render scale is 100%, so the world is drawn at the window's own size and MetalFX
is off`, once per setting rather than once per frame, and the latch is lifted when the number moves - so
a live 55 to 100 says it, and so does 100 to 55 and back. Those three lines are the whole of what a log
tells you about this slider, and each of them is pinned by `tests/test_metal_selection_and_scale.py`
along with the ordering that makes it true: the 100 percent gate stands before the scaled set is
allocated, and `endWorld` returns before it asks the device for MetalFX or reaches the bilinear fallback.

**Temporal Fold is gone with the path it belonged to.** It took the thin detail a small picture loses
from the frames before, by reprojecting the depth through the two cameras. But it consumed the
*upscaled* frame and ran between the upsample and the sharpen, and a fused encode has no such slot -
which is what the decision to put the fold on the FSR side had already implied. Its quality at a low
scale is what a temporal MetalFX scaler would bring back, in one effect rather than two, and that is its
own decision: it needs the frame jittered, and jitter is something a pack can see.

## Per-pass costs do not shrink either

The scale buys fragment work and nothing else. A frame also pays for things counted per pass, per
draw or per object, and a smaller picture makes none of them cheaper:

- the number of passes the pack runs, and the pipeline binds and barriers between them;
- the uniforms written for each program, one block filled per program however few pixels it covers;
- the geometry submitted. The world is walked, culled and drawn from the same sections at the same
  render distance, into fewer pixels.

A pack that is slow because it runs forty composites will still run forty of them at 50 percent. A
pack that is slow because each of them reads six full-screen textures is the kind the scale helps
most.

## Deciding what to lower

In the order of how much they change what is drawn rather than how it is drawn:

- **Shadow Distance** changes what is drawn at all: terrain and entities past it are not
  submitted to the light. That is geometry not walked, not culled and not rasterised.
- **Shadow Map Scale** keeps the same geometry and rasterises it into a smaller map, and tells
  the pack that is what it got. It costs less defined shadow edges, softer or coarser depending
  on whether the pack smooths them, and a pack reload each time it moves.
- **Render Scale** keeps the whole frame and rasterises it into fewer pixels, then buys the
  sharpness back with a fixed pass at full size.
- **The pack's own settings** are the last and often the largest: a pack's own shadow, volumetric
  and reflection quality settings are what decide how many passes there are to pay for.

None of these has a number attached here on purpose. What each costs depends on the pack, the
machine and where you are standing, and the honest way to know is to try one at a time and watch
the frame rate the game itself reports.
