#version 120

uniform sampler2D gtexture;
varying vec2 texcoord;

/* DRAWBUFFERS:0 */

void main() {
    vec4 texel = texture2D(gtexture, texcoord);
    if (texel.a < 0.10) {
        discard;
    }
    gl_FragData[0] = vec4(0.0, 0.0, 0.0, 1.0);
}
