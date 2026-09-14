#version 120

varying vec2 texcoord;
varying float eyeLightOk;

void main() {
    gl_Position = ftransform();
    texcoord = gl_MultiTexCoord0.st;

    vec2 light = gl_MultiTexCoord1.st;
    eyeLightOk = step(239.0, light.x) * step(light.x, 241.0)
        * step(239.0, light.y) * step(light.y, 241.0);
}
