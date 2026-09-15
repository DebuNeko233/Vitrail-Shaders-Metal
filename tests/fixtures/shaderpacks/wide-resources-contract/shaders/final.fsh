#version 120

varying vec2 texcoord;

uniform sampler2D wide00;
uniform sampler2D wide01;
uniform sampler2D wide02;
uniform sampler2D wide03;
uniform sampler2D wide04;
uniform sampler2D wide05;
uniform sampler2D wide06;
uniform sampler2D wide07;
uniform sampler2D wide08;
uniform sampler2D wide09;
uniform sampler2D wide10;
uniform sampler2D wide11;
uniform sampler2D wide12;
uniform sampler2D wide13;
uniform sampler2D wide14;
uniform sampler2D wide15;
uniform sampler2D wide16;

bool isWhite(vec4 sampleValue) {
    return sampleValue.r > 0.90 && sampleValue.g > 0.90 && sampleValue.b > 0.90 && sampleValue.a > 0.90;
}

void main() {
    vec2 probe = vec2(0.5, 0.5);
    vec4 s00 = texture2D(wide00, probe);
    vec4 s01 = texture2D(wide01, probe);
    vec4 s02 = texture2D(wide02, probe);
    vec4 s03 = texture2D(wide03, probe);
    vec4 s04 = texture2D(wide04, probe);
    vec4 s05 = texture2D(wide05, probe);
    vec4 s06 = texture2D(wide06, probe);
    vec4 s07 = texture2D(wide07, probe);
    vec4 s08 = texture2D(wide08, probe);
    vec4 s09 = texture2D(wide09, probe);
    vec4 s10 = texture2D(wide10, probe);
    vec4 s11 = texture2D(wide11, probe);
    vec4 s12 = texture2D(wide12, probe);
    vec4 s13 = texture2D(wide13, probe);
    vec4 s14 = texture2D(wide14, probe);
    vec4 s15 = texture2D(wide15, probe);
    vec4 s16 = texture2D(wide16, probe);

    bool ok = isWhite(s00) && isWhite(s01) && isWhite(s02) && isWhite(s03)
            && isWhite(s04) && isWhite(s05) && isWhite(s06) && isWhite(s07)
            && isWhite(s08) && isWhite(s09) && isWhite(s10) && isWhite(s11)
            && isWhite(s12) && isWhite(s13) && isWhite(s14) && isWhite(s15)
            && isWhite(s16);

    gl_FragColor = ok ? vec4(0.0, 1.0, 0.0, 1.0) : vec4(1.0, 0.0, 1.0, 1.0);
}
