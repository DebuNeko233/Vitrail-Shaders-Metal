#version 120

uniform sampler2D gtexture;
varying vec2 texcoord;
varying float entityAbiOk;

/* DRAWBUFFERS:0 */

void main() {
    vec4 texel = texture2D(gtexture, texcoord);
    if (texel.a < 0.10) {
        discard;
    }

    gl_FragColor = entityAbiOk > 0.50
        ? vec4(0.0, 1.0, 0.0, 1.0)
        : vec4(1.0, 0.0, 1.0, 1.0);
}
