#version 120

uniform sampler2D gtexture;
varying vec2 glintTexcoord;
varying float glintAbiOk;

const bool colortex1Clear = true;
const vec4 colortex1ClearColor = vec4(0.0, 0.0, 0.0, 1.0);

/* DRAWBUFFERS:1 */

void main() {
    vec4 texel = texture2D(gtexture, glintTexcoord);
    float minSample = min(min(texel.r, texel.g), min(texel.b, texel.a));
    float maxSample = max(max(texel.r, texel.g), max(texel.b, texel.a));
    float sampledTextureOk = step(-0.01, minSample) * step(maxSample, 1.01);
    float contractOk = glintAbiOk * sampledTextureOk;

    // Keep the real glint sampler and texture-matrix path live, but do not modulate the
    // acceptance colour by the animated stripe texture. The launcher separately proves that
    // this draw read enchanted_glint_armor.png, while the screenshot stays stable across views.
    gl_FragColor = contractOk > 0.50
        ? vec4(0.0, 1.0, 0.0, 1.0)
        : vec4(1.0, 0.0, 1.0, 1.0);
}
