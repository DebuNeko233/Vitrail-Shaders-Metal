#version 120

uniform sampler2D colortex0;
uniform sampler2D colortex1;
varying vec2 texcoord;

/* DRAWBUFFERS:01 */

void main() {
    vec3 a = texture2D(colortex0, texcoord).rgb;
    vec3 b = texture2D(colortex1, texcoord).rgb;
    bool ok = a.r > 0.75 && a.g < 0.25 && a.b < 0.25
        && b.r < 0.25 && b.g > 0.75 && b.b > 0.75;
    gl_FragData[0] = ok ? vec4(0.0, 1.0, 0.0, 1.0) : vec4(1.0, 0.0, 1.0, 1.0);
    gl_FragData[1] = ok ? vec4(1.0, 1.0, 0.0, 1.0) : vec4(1.0, 0.0, 1.0, 1.0);
}
