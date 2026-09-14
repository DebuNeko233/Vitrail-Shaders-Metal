#version 120

varying float cloudAbiOk;

void main() {
    gl_Position = ftransform();

    vec4 colour = gl_Color;
    float colourMin = min(min(colour.r, colour.g), min(colour.b, colour.a));
    float colourMax = max(max(colour.r, colour.g), max(colour.b, colour.a));
    float colourOk = step(-0.01, colourMin) * step(colourMax, 1.01);

    vec2 uv0 = gl_MultiTexCoord0.st;
    float uvOk = 1.0 - step(0.01, length(uv0 - vec2(0.5, 0.5)));

    vec2 light1 = gl_MultiTexCoord1.st;
    vec2 light2 = gl_MultiTexCoord2.st;
    float light1Ok = 1.0 - step(0.1, length(light1 - vec2(240.0, 240.0)));
    float lightAlias = 1.0 - step(0.01, length(light1 - light2));

    vec3 normal = gl_Normal;
    float normalOk = 1.0 - step(0.01, abs(length(normal) - 1.0));

    cloudAbiOk = colourOk * uvOk * light1Ok * lightAlias * normalOk;
}
