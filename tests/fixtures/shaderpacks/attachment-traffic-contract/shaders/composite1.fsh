#version 120

/* DRAWBUFFERS:0 */

// The negative control beside the writer: the same target, and a fragment stage that can leave a pixel
// unwritten. `writesEveryPixelOfTheArea` has to read this as false, so colortex0 must still be loaded here.
// The pixels this discards keep the checker the previous pass wrote, and the pixels it does not discard are
// painted red - so an elision that should not have happened shows as the checker missing from the half the
// frame that discards, rather than hiding behind content that looks the same either way.
void main() {
    if (mod(floor(gl_FragCoord.x / 32.0) + floor(gl_FragCoord.y / 32.0), 2.0) < 0.5) {
        discard;
    }

    gl_FragData[0] = vec4(1.0, 0.0, 0.0, 1.0);
}
