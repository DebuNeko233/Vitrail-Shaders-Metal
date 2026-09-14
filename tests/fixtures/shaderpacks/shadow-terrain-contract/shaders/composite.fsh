#version 120

uniform sampler2D shadowcolor0;
varying vec2 texcoord;

const bool colortex1Clear = true;
const vec4 colortex1ClearColor = vec4(0.0, 0.0, 0.0, 1.0);
/* DRAWBUFFERS:1 */

void main() {
    gl_FragColor = texture2D(shadowcolor0, texcoord);
}
