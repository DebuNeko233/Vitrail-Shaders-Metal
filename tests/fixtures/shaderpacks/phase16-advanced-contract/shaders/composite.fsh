#version 120

uniform sampler3D phase16Nearest;
uniform sampler3D phase16Linear;
uniform sampler2D noisetex;
varying vec2 texcoord;

/* DRAWBUFFERS:2 */

void main() {
    vec4 nearestDepth = texture(phase16Nearest, vec3(0.5, 0.5, 0.5));
    vec4 linearDepth = texture(phase16Linear, vec3(0.5, 0.5, 0.5));

    bool nearestOk = nearestDepth.b > 0.90 && nearestDepth.r < 0.10;
    bool linearOk = abs(linearDepth.r - 0.5) < 0.12
        && linearDepth.g < 0.10
        && abs(linearDepth.b - 0.5) < 0.12;

    vec4 noiseA = texture2D(noisetex, vec2(0.25, 0.25));
    vec4 noiseB = texture2D(noisetex, vec2(1.25, 0.25));
    bool noiseOk = distance(noiseA, noiseB) < 0.02;

    gl_FragData[0] = vec4(nearestOk && linearOk ? 1.0 : 0.0,
        noiseOk ? 1.0 : 0.0, 0.0, 1.0);
}
