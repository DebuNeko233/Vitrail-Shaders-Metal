#version 120

varying float skyAbiOk;
varying float skyColourAlpha;

const bool colortex1Clear = true;
const vec4 colortex1ClearColor = vec4(0.0, 0.0, 0.0, 1.0);

void main() {
    /* DRAWBUFFERS:1 */
    if (skyAbiOk < 0.5) {
        gl_FragData[0] = vec4(1.0, 0.0, 1.0, 1.0);
    } else if (skyColourAlpha < 0.95) {
        gl_FragData[0] = vec4(1.0, 1.0, 0.0, 1.0);
    } else {
        gl_FragData[0] = vec4(0.0, 1.0, 1.0, 1.0);
    }
}
