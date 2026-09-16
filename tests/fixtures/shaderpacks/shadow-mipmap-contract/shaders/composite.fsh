#version 120
#extension GL_ARB_shader_texture_lod : enable

uniform sampler2D shadowtex0;
uniform sampler2D shadowtex1;
varying vec2 texcoord;

const bool colortex1Clear = true;
const vec4 colortex1ClearColor = vec4(0.0, 0.0, 1.0, 1.0);
const bool shadowtex0Nearest = true;
const bool shadowtex1Nearest = true;
const bool shadowtex0Mipmap = true;
const bool shadowtex1Mipmap = true;
/* DRAWBUFFERS:1 */

void main() {
    float base0 = texture2D(shadowtex0, texcoord).r;
    float mip0 = texture2DLod(shadowtex0, texcoord, 4.0).r;
    float base1 = texture2D(shadowtex1, texcoord).r;
    float mip1 = texture2DLod(shadowtex1, texcoord, 4.0).r;

    bool valid = base0 >= 0.0 && base0 <= 1.0
        && mip0 >= 0.0 && mip0 <= 1.0
        && base1 >= 0.0 && base1 <= 1.0
        && mip1 >= 0.0 && mip1 <= 1.0;
    bool reduced0 = abs(base0 - mip0) > 0.05;
    bool reduced1 = abs(base1 - mip1) > 0.05;

    if (!valid || reduced0 != reduced1) {
        gl_FragData[0] = vec4(1.0, 0.0, 1.0, 1.0);
    } else if (reduced0 && reduced1) {
        gl_FragData[0] = vec4(0.0, 1.0, 0.0, 1.0);
    } else {
        gl_FragData[0] = vec4(0.0, 0.0, 1.0, 1.0);
    }
}
