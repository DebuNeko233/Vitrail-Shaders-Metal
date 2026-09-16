#version 120

uniform sampler2D shadowtex0;
uniform sampler2D shadowtex1;
varying vec2 texcoord;

const bool colortex1Clear = true;
const vec4 colortex1ClearColor = vec4(0.0, 0.0, 0.0, 1.0);
const bool shadowtex0Nearest = true;
const bool shadowtex1Nearest = true;
/* DRAWBUFFERS:1 */

void main() {
    float complete = texture2D(shadowtex0, texcoord).r;
    float opaque = texture2D(shadowtex1, texcoord).r;
    bool valid = complete >= 0.0 && complete <= 1.0 && opaque >= 0.0 && opaque <= 1.0;
    float delta = opaque - complete;

    if (!valid) {
        gl_FragData[0] = vec4(1.0, 0.0, 1.0, 1.0);
    } else if (abs(delta) <= 0.00001) {
        gl_FragData[0] = vec4(0.0, 1.0, 1.0, 1.0);
    } else if (delta > 0.00001) {
        gl_FragData[0] = vec4(1.0, 1.0, 1.0, 1.0);
    } else {
        gl_FragData[0] = vec4(1.0, 0.0, 1.0, 1.0);
    }
}
