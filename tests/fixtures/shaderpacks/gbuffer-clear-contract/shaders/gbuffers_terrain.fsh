#version 120

/* DRAWBUFFERS:0 */

void main() {
    gl_FragData[0] = vec4(0.75, 0.15, 0.15, 1.0);
    gl_FragData[1] = vec4(0.15, 0.75, 0.15, 1.0);
    gl_FragData[2] = vec4(0.15, 0.15, 0.75, 1.0);
}
