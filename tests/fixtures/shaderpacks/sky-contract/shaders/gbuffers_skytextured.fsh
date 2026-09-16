#version 120

uniform sampler2D gtexture;
varying vec2 texcoord;
varying float skyAbiOk;

const bool colortex1Clear = true;
const vec4 colortex1ClearColor = vec4(0.0, 0.0, 0.0, 1.0);

void main() {
    vec4 sampleColour = texture2D(gtexture, texcoord);
    float sampleMin = min(min(sampleColour.r, sampleColour.g), min(sampleColour.b, sampleColour.a));
    float sampleMax = max(max(sampleColour.r, sampleColour.g), max(sampleColour.b, sampleColour.a));
    float sampleOk = step(-0.01, sampleMin) * step(sampleMax, 1.01);

    /* DRAWBUFFERS:1 */
    gl_FragData[0] = skyAbiOk * sampleOk > 0.5
        ? vec4(0.0, 1.0, 0.0, 1.0)
        : vec4(1.0, 0.0, 1.0, 1.0);
}
