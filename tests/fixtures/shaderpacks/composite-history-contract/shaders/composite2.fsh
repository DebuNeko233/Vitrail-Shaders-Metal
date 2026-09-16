#version 120

uniform sampler2D colortex2;
varying vec2 texcoord;

/* DRAWBUFFERS:02 */

void main() {
    vec3 before = texture2D(colortex2, texcoord).rgb;
    bool first = before.g > 0.75 && before.r < 0.25 && before.b < 0.25;
    bool steady = before.r > 0.75 && before.g > 0.75 && before.b < 0.25;
    vec4 temporal = (first || steady) ? vec4(0.0, 0.0, 1.0, 1.0) : vec4(1.0, 0.0, 1.0, 1.0);
    vec4 diagnostic = first ? vec4(1.0, 0.0, 0.0, 1.0)
        : steady ? vec4(0.0, 1.0, 1.0, 1.0)
        : vec4(1.0, 0.0, 1.0, 1.0);
    gl_FragData[0] = diagnostic;
    gl_FragData[1] = temporal;
}
