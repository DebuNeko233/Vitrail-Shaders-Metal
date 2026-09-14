#version 120

varying vec2 glintTexcoord;
varying float glintAbiOk;

void main() {
    gl_Position = ftransform();
    glintTexcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).st;

    vec2 light = gl_MultiTexCoord1.st;
    float lightOk = step(239.0, light.x) * step(light.x, 241.0)
        * step(239.0, light.y) * step(light.y, 241.0);
    float normalOk = 1.0 - step(0.01, length(gl_Normal - vec3(0.0, 0.0, 1.0)));
    glintAbiOk = lightOk * normalOk;
}
