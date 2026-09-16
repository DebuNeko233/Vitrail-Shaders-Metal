#version 120

/* DRAWBUFFERS:31 */

void main() {
    vec4 source = vec4(0.75, 0.25, 0.5, 1.0);
    gl_FragData[0] = source;
    gl_FragData[1] = source;
}
