#version 120

uniform sampler2D gtexture;
varying vec2 glintTexcoord;
varying float glintAbiOk;

const bool colortex1Clear = true;
const vec4 colortex1ClearColor = vec4(0.0, 0.0, 0.0, 1.0);

/* DRAWBUFFERS:1 */

void main() {
    vec4 texel = texture2D(gtexture, glintTexcoord);
    float textureSignal = max(texel.r, max(texel.g, texel.b));
    gl_FragColor = glintAbiOk > 0.50
        ? vec4(0.0, step(0.02, textureSignal), 0.0, texel.a)
        : vec4(1.0, 0.0, 1.0, texel.a);
}
