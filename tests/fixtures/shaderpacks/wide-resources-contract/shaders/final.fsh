#version 120

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
uniform sampler2D wide17;
uniform sampler2D wide18;
uniform sampler2D wide19;
uniform sampler2D wide20;
uniform sampler2D wide21;
uniform sampler2D wide22;
uniform sampler2D wide23;
uniform sampler2D wide24;
uniform sampler2D wide25;
uniform sampler2D wide26;
uniform sampler2D wide27;
uniform sampler2D wide28;
uniform sampler2D wide29;
uniform sampler2D wide30;
uniform sampler2D wide31;
uniform sampler2D wide32;

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
    vec4 s17 = texture2D(wide17, probe);
    vec4 s18 = texture2D(wide18, probe);
    vec4 s19 = texture2D(wide19, probe);
    vec4 s20 = texture2D(wide20, probe);
    vec4 s21 = texture2D(wide21, probe);
    vec4 s22 = texture2D(wide22, probe);
    vec4 s23 = texture2D(wide23, probe);
    vec4 s24 = texture2D(wide24, probe);
    vec4 s25 = texture2D(wide25, probe);
    vec4 s26 = texture2D(wide26, probe);
    vec4 s27 = texture2D(wide27, probe);
    vec4 s28 = texture2D(wide28, probe);
    vec4 s29 = texture2D(wide29, probe);
    vec4 s30 = texture2D(wide30, probe);
    vec4 s31 = texture2D(wide31, probe);
    vec4 s32 = texture2D(wide32, probe);

    bool ok = isWhite(s00)
            && isWhite(s01) && isWhite(s02) && isWhite(s03) && isWhite(s04)
            && isWhite(s05) && isWhite(s06) && isWhite(s07) && isWhite(s08)
            && isWhite(s09) && isWhite(s10) && isWhite(s11) && isWhite(s12)
            && isWhite(s13) && isWhite(s14) && isWhite(s15) && isWhite(s16)
            && isWhite(s17) && isWhite(s18) && isWhite(s19) && isWhite(s20)
            && isWhite(s21) && isWhite(s22) && isWhite(s23) && isWhite(s24)
            && isWhite(s25) && isWhite(s26) && isWhite(s27) && isWhite(s28)
            && isWhite(s29) && isWhite(s30) && isWhite(s31) && isWhite(s32);

    gl_FragColor = ok ? vec4(0.0, 1.0, 0.0, 1.0) : vec4(1.0, 0.0, 1.0, 1.0);
}
