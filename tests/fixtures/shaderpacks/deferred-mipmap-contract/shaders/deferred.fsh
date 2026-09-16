#version 120

varying vec2 texcoord;

/* DRAWBUFFERS:0 */

void main() {
    float checker = mod(floor(gl_FragCoord.x) + floor(gl_FragCoord.y), 2.0);
    gl_FragData[0] = vec4(checker, checker, checker, 1.0);
}
