#version 120

uniform sampler2D gtexture;
varying vec2 texcoord;
varying float eyeLightOk;

const bool colortex1Clear = true;
const vec4 colortex1ClearColor = vec4(0.0, 0.0, 0.0, 1.0);

/* DRAWBUFFERS:1 */

void main() {
    vec4 texel = texture2D(gtexture, texcoord);
    gl_FragColor = eyeLightOk > 0.50
        ? vec4(0.0, 1.0, 0.0, texel.a)
        : vec4(1.0, 0.0, 1.0, texel.a);
}
