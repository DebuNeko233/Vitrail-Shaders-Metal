#version 120

uniform sampler2D gtexture;
varying vec2 texcoord;
varying float handAbiOk;

const bool colortex1Clear = true;
const vec4 colortex1ClearColor = vec4(0.0, 0.0, 0.0, 1.0);
/* DRAWBUFFERS:1 */

void main() {
    vec4 texel = texture2D(gtexture, texcoord);
    if (texel.a < 0.10 || handAbiOk < 0.50) {
        discard;
    }
    gl_FragData[0] = vec4(0.0, 0.0, 1.0, 1.0);
}
