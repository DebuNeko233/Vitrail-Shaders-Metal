#version 120

uniform sampler2D colortex1;
varying vec2 texcoord;

/* DRAWBUFFERS:1 */

// The read-then-overwrite control: this pass samples the very target it writes, so the old contents are part
// of the result and `readsWhatItWrites` has to read true - the load may not be elided here. If it were, every
// pixel would start from nothing instead of from what the pass before wrote, and the quantised step below
// would land one level away in the first frame and stay there, which is a picture difference rather than a
// nuance. Nothing depends on the sun, the world or the pack's clock.
void main() {
    float carried = texture2D(colortex1, texcoord).r;
    gl_FragData[0] = vec4(min(carried + 0.5, 1.0), 0.0, 0.0, 1.0);
}
