#version 120

uniform sampler2D colortex2;
varying vec2 texcoord;

const bool colortex2Clear = false;
/* DRAWBUFFERS:2 */

void main() {
    vec3 history = texture2D(colortex2, texcoord).rgb;
    bool first = history.r < 0.15 && history.g < 0.15 && history.b < 0.15;
    bool steady = history.b > 0.75 && history.r < 0.25 && history.g < 0.25;
    gl_FragData[0] = first ? vec4(1.0, 0.0, 0.0, 1.0)
        : steady ? vec4(0.0, 1.0, 1.0, 1.0)
        : vec4(1.0, 0.0, 1.0, 1.0);
}
