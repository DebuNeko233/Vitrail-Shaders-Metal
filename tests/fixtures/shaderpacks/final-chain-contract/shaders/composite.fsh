#version 120

varying vec2 texcoord;

/* DRAWBUFFERS:0 */

void main() {
    gl_FragData[0] = texcoord.x < 0.5
        ? vec4(1.0, 0.0, 0.0, 1.0)
        : vec4(0.0, 1.0, 0.0, 1.0);
}
