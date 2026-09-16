#version 120

attribute vec2 mc_midTexCoord;
attribute vec4 at_tangent;

varying vec2 texcoord;
varying float shadowAbiOk;

invariant gl_Position;

void main() {
    gl_Position = ftransform();
    texcoord = gl_MultiTexCoord0.st;

    float midRange = step(0.0, mc_midTexCoord.x) * step(mc_midTexCoord.x, 1.0)
        * step(0.0, mc_midTexCoord.y) * step(mc_midTexCoord.y, 1.0);
    float tangentLength = length(at_tangent.xyz);
    float tangentDirection = step(0.70, tangentLength) * step(tangentLength, 1.30);
    float handedness = abs(at_tangent.w);
    float tangentHandedness = step(0.70, handedness) * step(handedness, 1.30);
    float normalLength = length(gl_Normal);
    float normalDirection = step(0.70, normalLength) * step(normalLength, 1.30);

    shadowAbiOk = midRange * tangentDirection * tangentHandedness * normalDirection;
}
