#version 120

uniform sampler2D depthtex2;
varying vec2 texcoord;

const bool colortex1Clear = true;
const vec4 colortex1ClearColor = vec4(0.0, 0.0, 0.0, 1.0);

/* DRAWBUFFERS:1 */

void main() {
    const float probe = 1.0 / 128.0;
    vec2 uvX = clamp(texcoord + vec2(probe, 0.0), vec2(0.0), vec2(1.0));
    vec2 uvY = clamp(texcoord + vec2(0.0, probe), vec2(0.0), vec2(1.0));

    float center = texture2D(depthtex2, texcoord).r;
    float right = texture2D(depthtex2, uvX).r;
    float down = texture2D(depthtex2, uvY).r;

    bool valid = center >= 0.0 && center <= 1.0
        && right >= 0.0 && right <= 1.0
        && down >= 0.0 && down <= 1.0;
    float variation = max(abs(center - right), abs(center - down));

    if (!valid) {
        gl_FragData[0] = vec4(1.0, 0.0, 1.0, 1.0);
    } else if (variation > 0.0000001) {
        gl_FragData[0] = vec4(0.0, 1.0, 1.0, 1.0);
    } else {
        gl_FragData[0] = vec4(1.0, 0.0, 0.0, 1.0);
    }
}
