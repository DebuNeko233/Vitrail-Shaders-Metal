#version 120

varying float blockEntityAbiOk;

/* DRAWBUFFERS:0 */

void main() {
    gl_FragColor = blockEntityAbiOk > 0.50
        ? vec4(0.0, 1.0, 0.0, 1.0)
        : vec4(1.0, 0.0, 1.0, 1.0);
}
