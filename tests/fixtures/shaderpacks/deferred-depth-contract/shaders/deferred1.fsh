#version 120

uniform sampler2D colortex0;
uniform sampler2D depthtex0;
varying vec2 texcoord;

/* DRAWBUFFERS:0 */

void main() {
    const float probe = 1.0 / 128.0;
    vec2 uvX = clamp(texcoord + vec2(probe, 0.0), vec2(0.0), vec2(1.0));
    vec2 uvY = clamp(texcoord + vec2(0.0, probe), vec2(0.0), vec2(1.0));

    vec3 previous = texture2D(colortex0, texcoord).rgb;
    bool previousValid = previous.g > 0.75 && previous.r < 0.25;
    bool previousVariation = previous.b > 0.50;

    float center = texture2D(depthtex0, texcoord).r;
    float right = texture2D(depthtex0, uvX).r;
    float down = texture2D(depthtex0, uvY).r;
    bool valid = center >= 0.0 && center <= 1.0
        && right >= 0.0 && right <= 1.0
        && down >= 0.0 && down <= 1.0;
    bool variation = max(abs(center - right), abs(center - down)) > 0.0000001;

    if (!previousValid || !valid || variation != previousVariation) {
        gl_FragData[0] = vec4(1.0, 0.0, 1.0, 1.0);
    } else if (variation) {
        gl_FragData[0] = vec4(0.0, 1.0, 1.0, 1.0);
    } else {
        gl_FragData[0] = vec4(0.0, 0.0, 1.0, 1.0);
    }
}
