#version 120

varying vec2 texcoord;
varying float weatherAbiOk;

void main() {
    gl_Position = ftransform();
    texcoord = gl_MultiTexCoord0.st;

    vec4 colour = gl_Color;
    float colourMin = min(min(colour.r, colour.g), min(colour.b, colour.a));
    float colourMax = max(max(colour.r, colour.g), max(colour.b, colour.a));
    float colourOk = step(-0.01, colourMin) * step(colourMax, 1.01);

    vec2 light1 = gl_MultiTexCoord1.st;
    vec2 light2 = gl_MultiTexCoord2.st;
    float lightRange = step(-0.1, light1.x) * step(light1.x, 240.1)
        * step(-0.1, light1.y) * step(light1.y, 240.1);
    float lightAlias = 1.0 - step(0.01, length(light1 - light2));
    float normalOk = 1.0 - step(0.01, length(gl_Normal - vec3(0.0, 0.0, 1.0)));

    weatherAbiOk = colourOk * lightRange * lightAlias * normalOk;
}
