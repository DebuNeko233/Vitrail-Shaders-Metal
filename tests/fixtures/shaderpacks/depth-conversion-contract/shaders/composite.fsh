#version 120

uniform sampler2D depthtex1;
varying vec2 texcoord;

const bool colortex1Clear = true;
const vec4 colortex1ClearColor = vec4(0.0, 0.0, 0.0, 1.0);
/* DRAWBUFFERS:1 */

void main() {
    float depth = texture2D(depthtex1, texcoord).r;
    if (depth < 0.0 || depth > 1.0) {
        gl_FragData[0] = vec4(1.0, 0.0, 1.0, 1.0);
    } else if (depth <= 0.005) {
        gl_FragData[0] = vec4(0.0, 1.0, 0.0, 1.0);
    } else if (depth >= 0.995) {
        gl_FragData[0] = vec4(1.0, 1.0, 1.0, 1.0);
    } else {
        gl_FragData[0] = vec4(0.0, 0.0, 1.0, 1.0);
    }
}
