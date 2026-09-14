#version 120

uniform sampler2D gtexture;
varying vec2 texcoord;
varying float shadowAbiOk;

const bool shadowcolor0Clear = true;
const vec4 shadowcolor0ClearColor = vec4(0.0, 0.0, 0.0, 1.0);
/* DRAWBUFFERS:0 */

void main() {
    vec4 texel = texture2D(gtexture, texcoord);
    if (shadowAbiOk <= 0.50 || texel.a <= 0.0) {
        gl_FragColor = vec4(1.0, 0.0, 1.0, 1.0);
        return;
    }
    gl_FragColor = vec4(0.0, 1.0, 0.0, 1.0);
}
