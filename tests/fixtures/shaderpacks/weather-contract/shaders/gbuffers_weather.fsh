#version 120

uniform sampler2D gtexture;
varying vec2 texcoord;
varying float weatherAbiOk;

const bool colortex1Clear = true;
const vec4 colortex1ClearColor = vec4(0.0, 0.0, 0.0, 1.0);

/* DRAWBUFFERS:1 */

void main() {
    vec4 texel = texture2D(gtexture, texcoord);
    if (texel.a < 0.10) {
        discard;
    }

    float minSample = min(min(texel.r, texel.g), min(texel.b, texel.a));
    float maxSample = max(max(texel.r, texel.g), max(texel.b, texel.a));
    float sampledTextureOk = step(-0.01, minSample) * step(maxSample, 1.01);
    float contractOk = weatherAbiOk * sampledTextureOk;

    gl_FragColor = contractOk > 0.50
        ? vec4(0.0, 1.0, 0.0, 1.0)
        : vec4(1.0, 0.0, 1.0, 1.0);
}
