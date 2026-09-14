# PHASE 7 sky acceptance contract

This fixture isolates Vitrail's two sky-pack programs while keeping every Minecraft 26.2 sky vertex shape live.

The Overworld gate must exercise `gbuffers_skybasic` on the sky disc (`POSITION`, `SKY`) and sunrise/sunset fan (`POSITION_COLOR`, `SUNSET`), plus `gbuffers_skytextured` on a celestial quad (`POSITION_TEX`, `SUN` or `MOON`). The End gate must independently exercise the End cube (`POSITION_TEX_COLOR`, `CUSTOM_SKY`).

`gbuffers_skybasic` writes cyan for the ordinary pass-colour path, yellow when a fading colour attribute is active, and magenta on an ABI failure. `gbuffers_skytextured` actively samples the game's real texture and writes green on ABI success or magenta on failure. Both write only `colortex1`; `final` reads only `colortex1`.

The disc and End cube are coverage-writing pieces. The fixture deliberately keeps that behavior live so the scene seed cannot paint the game's original sky back over the pack target. No sky result substitutes for clouds, weather, or any later dimension-routing checkpoint.
