#version 120

/* DRAWBUFFERS:0 */

uniform sampler2D phase15Tex;
varying vec2 texcoord;

void main() {
    vec3 marker = texture2D(phase15Tex, texcoord).rgb;
    bool green = marker.g > 0.90 && marker.r < 0.10 && marker.b < 0.10;
    gl_FragData[0] = green
            ? vec4(0.0, 1.0, 0.0, 1.0)
            : vec4(1.0, 0.0, 1.0, 1.0);
}
