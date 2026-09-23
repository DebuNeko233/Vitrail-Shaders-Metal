<p align="center">
  <img src="common/src/main/resources/vitrail.png" width="128" alt="">
</p>

<h1 align="center">Vitrail Shaders</h1>

<p align="center">
  OptiFine-format shader packs, on Apple Metal.
</p>

<p align="center">
  <a href="https://www.curseforge.com/minecraft/mc-mods/vitrail-shaders"><img src="https://img.shields.io/curseforge/dt/1649385?style=flat-square&logo=curseforge&logoColor=white&label=CurseForge&color=F16436" alt="Vitrail on CurseForge, with its download count"></a>
  <a href="https://modrinth.com/mod/vitrail-shaders"><img src="https://img.shields.io/modrinth/dt/oSIKhgz3?style=flat-square&logo=modrinth&logoColor=white&label=Modrinth&color=00AF5C" alt="Vitrail on Modrinth, with its download count"></a>
  <a href="https://ko-fi.com/B1H225VJC4"><img src="https://img.shields.io/badge/support-Ko--fi-FF5E5B?style=flat-square&logo=kofi&logoColor=white" alt="Support Vitrail on Ko-fi"></a>
</p>

---

<p align="center">
  <img src="docs/images/screenshot-mountains.jpg" alt="Snow-capped mountains over a cherry grove and a savanna, a mushroom island out at sea, under volumetric clouds, rendered on Metal" width="830">
</p>
<p align="center">
  <sub>An OptiFine-format pack, running unmodified on Metal.</sub>
</p>

<details>
<summary>More screenshots</summary>
<br>
<p align="center">
  <img src="docs/images/screenshot-savanna-sunset.jpg" alt="The sun setting over a savanna and a lake, fog lying on the water, rendered on Metal" width="830">
</p>
<p align="center">
  <img src="docs/images/screenshot-ocean-ruins.jpg" alt="Sunken ruins on the sea floor, drowned walking through the light shafts, a school of tropical fish beside them, rendered on Metal" width="830">
</p>
<p align="center">
  <img src="docs/images/screenshot-lush-cave.jpg" alt="A lush cave under a cliff, a shaft of sunlight falling through the opening onto glow berries and dripstone, rendered on Metal" width="830">
</p>
</details>

Minecraft 26.2 does not render through OpenGL. Every shader pack that exists was
written for OpenGL, and none of them run unmodified on what the game ships with
instead.

**Vitrail runs them anyway, unmodified.** It reads an OptiFine-format pack out
of `shaderpacks/`, translates its GLSL once when the pack loads, compiles it to
SPIR-V, and hands that across to [Metallum](https://github.com/DebuNeko233/metallum),
which turns it into Metal shaders. Nothing translates while a frame is drawn.

It started as a question, whether packs written for OpenGL over more than a
decade could run untouched on the renderer the game has now. It is one person's
side project, worked on every day since July, and an early one: the backend it
runs on is a separate mod with no release yet.

It is built the way a lot of software gets built now: AI tools do a real share
of the typing and the debugging, and a human decides, tests against real packs
and against Iris, reviews every line and carries the blame for every bug. What
was taken from Iris and from Kroppeb's stareval is credited file by file in
[NOTICE](NOTICE), under the same licence. If any of that matters to you, now you
know. If the mod is useful, there is a coffee link below, and the issues here
are where I answer.

## Requirements

| Component | Version |
| --- | --- |
| macOS on Apple Silicon | the only supported target |
| Minecraft | 26.2, below 26.3 |
| Fabric Loader | 0.19.3 or later, with Fabric API |
| Sodium | 0.9.x, required |
| [Metallum](https://github.com/DebuNeko233/metallum) | required: the Metal backend, API v1 |
| Java | 25 |

**Sodium is not optional.** It owns the command submission this engine draws through, so the game
refuses to start without it rather than showing a picture that is missing quietly. **Metallum is not
optional either**: it owns Metal device creation, pipeline compilation, resource binding, encoder
lifecycle, synchronization and presentation, so a session without it, with an incompatible API
version, or whose Metal device does not come up draws nothing of a pack and says so. On Fabric, two
modules of Fabric API are declared as required and they are the whole of what this mod takes from
it; nothing of the world's rendering goes through Fabric API.

There is no NeoForge product. Vitrail keeps its NeoForge source tree, but Metallum has no NeoForge
distribution and no Metal provider exists for that loader, so the NeoForge artifact is built and not
published. The install steps describe the Fabric jar, which is the one that runs;
[INSTALL.md](INSTALL.md) has the same requirement set with those steps.

## Quick start

- One jar for Fabric, on Minecraft 26.2 and macOS on Apple Silicon. On
  [CurseForge](https://www.curseforge.com/minecraft/mc-mods/vitrail-shaders), on
  [Modrinth](https://modrinth.com/mod/vitrail-shaders) and on every
  [release](https://github.com/avpbynf/Vitrail-Shaders/releases) here.
- Put it in `mods/` next to Sodium and Metallum. Client only.
- In Options then Video Settings, set Graphics API to "Prefer Metal", and
  restart the game. The change only takes effect on the next start, which the
  game says itself when you pick it.
- Packs go in `shaderpacks/` as they always have, and are picked from Vitrail's
  own settings screen.

[INSTALL.md](INSTALL.md) has the versions this needs, the Chloride settings that
decide what reaches your pack, and what a session that did not come up on Metal
looks like.

## What goes through your pack

Terrain, water, shadows, sky, clouds, weather, particles, mobs, block entities,
the held hand, and the far terrain of Distant Horizons given a build of that mod
that draws on this backend, which [Other mods](INSTALL.md#other-mods) names. The
settings screen reads the pack's own menu layout, and a resource pack's normal
and specular maps are served beside the blocks they belong to.

The rest still comes from the game, and that set moves from one release to the
next: the engine logs which families do when a place first draws, and
[pack compatibility](docs/compatibility.md) starts from what you are seeing and
names the cause. If what you want today is a finished picture, use
[Iris](https://github.com/IrisShaders/Iris) on the OpenGL backend instead, which
is the reference this engine is checked against.

## Read more

| If you want to | Read |
| --- | --- |
| Know why the format is OptiFine's, and how this sits next to Iris, Sulkan and Aperture | [Why this exists](docs/why.md) |
| Work out why your pack looks wrong, starting from what you see | [Pack compatibility](docs/compatibility.md) |
| See what changed from one version to the next | [CHANGELOG.md](CHANGELOG.md) |
| Install it, and know what it refuses to run beside | [INSTALL.md](INSTALL.md) |
| Understand how any of this works | [The documentation](docs/README.md) |

## Support

<a href="https://ko-fi.com/B1H225VJC4"><img src="https://storage.ko-fi.com/cdn/kofi3.png?v=6" width="220" alt="Buy Me a Coffee at ko-fi.com"></a>

## Contributing

Open an issue before writing anything substantial. I order the work by risk, and
code that lands ahead of what can be verified is hard to accept however good it
is.

[CONTRIBUTING.md](CONTRIBUTING.md) opens on the short version, what refuses a first
push, and carries the rest.

## Licence

LGPL-3.0-only, in [LICENSE](LICENSE), with [GPL-3.0.txt](GPL-3.0.txt) beside it
because version 3 of the Lesser GPL is written as permissions on top of the
ordinary GPL rather than as a standalone document.

Parts of the pack loader and of the value catalogue are adapted from Iris, which
is LGPL-3.0 as well. What was taken and what was changed on the way is recorded
in [NOTICE](NOTICE).
