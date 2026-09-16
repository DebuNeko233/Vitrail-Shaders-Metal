#version 120

/* DRAWBUFFERS:0 */

uniform sampler2D colortex0;
varying vec2 texcoord;

void main() {
    vec3 previous = texture2D(colortex0, texcoord).rgb;
    bool sawDeferred1 = previous.g > 0.75 && previous.r < 0.25 && previous.b < 0.25;
    gl_FragData[0] = sawDeferred1
        ? vec4(0.0, 0.0, 1.0, 1.0)
        : vec4(1.0, 0.0, 1.0, 1.0);
}
