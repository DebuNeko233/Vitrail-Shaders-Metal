#version 120

varying float skyAbiOk;
varying float skyColourAlpha;

void main() {
    gl_Position = ftransform();

    vec4 colour = gl_Color;
    vec2 uv0 = gl_MultiTexCoord0.st;
    vec2 light1 = gl_MultiTexCoord1.st;
    vec2 light2 = gl_MultiTexCoord2.st;

    float colourMin = min(min(colour.r, colour.g), min(colour.b, colour.a));
    float colourMax = max(max(colour.r, colour.g), max(colour.b, colour.a));
    float colourOk = step(-0.01, colourMin) * step(colourMax, 1.01);
    float uvMin = min(uv0.x, uv0.y);
    float uvMax = max(uv0.x, uv0.y);
    float uvOk = step(-0.01, uvMin) * step(uvMax, 16.01);
    float light1Ok = 1.0 - step(0.01, length(light1 - vec2(240.0)));
    float light2Ok = 1.0 - step(0.01, length(light2 - vec2(240.0)));
    float normalOk = 1.0 - step(0.01, length(gl_Normal - vec3(0.0, 0.0, 1.0)));

    skyAbiOk = colourOk * uvOk * light1Ok * light2Ok * normalOk;
    skyColourAlpha = clamp(colour.a, 0.0, 1.0);
}
