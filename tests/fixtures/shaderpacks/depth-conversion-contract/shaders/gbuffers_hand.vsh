#version 120

attribute vec2 mc_midTexCoord;
attribute vec4 at_tangent;

varying vec2 texcoord;
varying float handAbiOk;

void main() {
    gl_Position = ftransform();
    gl_Position.z = -gl_Position.w;
    texcoord = gl_MultiTexCoord0.st;

    float midRange = step(0.0, mc_midTexCoord.x) * step(mc_midTexCoord.x, 1.0)
        * step(0.0, mc_midTexCoord.y) * step(mc_midTexCoord.y, 1.0);
    float midWritten = step(0.0001, abs(mc_midTexCoord.x) + abs(mc_midTexCoord.y));
    float midIsPolygonValue = step(0.0001, distance(mc_midTexCoord, texcoord));

    float tangentLength = length(at_tangent.xyz);
    float tangentDirection = step(0.70, tangentLength) * step(tangentLength, 1.30);
    float handedness = abs(at_tangent.w);
    float tangentHandedness = step(0.70, handedness) * step(handedness, 1.30);

    handAbiOk = midRange * midWritten * midIsPolygonValue
        * tangentDirection * tangentHandedness;
}
