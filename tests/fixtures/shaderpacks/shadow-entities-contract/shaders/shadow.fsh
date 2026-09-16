#version 120

uniform sampler2D gtexture;
varying vec2 texcoord;

const bool shadowcolor0Clear = true;
const vec4 shadowcolor0ClearColor = vec4(0.0, 0.0, 0.0, 1.0);
/* DRAWBUFFERS:0 */

void main() {
    vec4 texel = texture2D(gtexture, texcoord);
    if (texel.a < 0.10) {
        discard;
    }
    gl_FragColor = vec4(0.0, 0.0, 1.0, 1.0);
}
