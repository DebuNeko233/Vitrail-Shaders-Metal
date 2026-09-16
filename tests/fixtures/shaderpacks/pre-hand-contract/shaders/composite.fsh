#version 120

uniform sampler2D depthtex1;
uniform sampler2D depthtex2;
varying vec2 texcoord;

const bool colortex1Clear = true;
const vec4 colortex1ClearColor = vec4(0.0, 0.0, 0.0, 1.0);
/* DRAWBUFFERS:1 */

void main() {
    float withHand = texture2D(depthtex1, texcoord).r;
    float beforeHand = texture2D(depthtex2, texcoord).r;
    bool valid = withHand >= 0.0 && withHand <= 1.0
        && beforeHand >= 0.0 && beforeHand <= 1.0;
    float difference = abs(withHand - beforeHand);

    if (!valid) {
        gl_FragData[0] = vec4(1.0, 0.0, 1.0, 1.0);
    } else if (difference <= 0.00001) {
        gl_FragData[0] = vec4(0.0, 1.0, 1.0, 1.0);
    } else if (withHand <= 0.005 && beforeHand > 0.01) {
        gl_FragData[0] = vec4(0.0, 1.0, 0.0, 1.0);
    } else {
        gl_FragData[0] = vec4(1.0, 0.0, 1.0, 1.0);
    }
}
