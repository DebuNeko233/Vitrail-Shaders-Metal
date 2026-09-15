#version 120

uniform sampler2D colortex0;
varying vec2 texcoord;

/* DRAWBUFFERS:0 */

void main() {
    vec3 before = texture2D(colortex0, texcoord).rgb;
    bool ok = before.g > 0.75 && before.r < 0.25 && before.b < 0.25;
    gl_FragData[0] = ok ? vec4(0.0, 0.0, 1.0, 1.0) : vec4(1.0, 0.0, 1.0, 1.0);
}
