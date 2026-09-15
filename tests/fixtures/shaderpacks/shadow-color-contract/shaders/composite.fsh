#version 120

uniform sampler2D shadowcolor0;
uniform sampler2D shadowcolor1;
varying vec2 texcoord;

const bool colortex1Clear = true;
const vec4 colortex1ClearColor = vec4(0.0, 0.0, 0.0, 1.0);
/* DRAWBUFFERS:1 */

void main() {
    vec3 first = texture2D(shadowcolor0, texcoord).rgb;
    vec3 second = texture2D(shadowcolor1, texcoord).rgb;
    bool clearPair = dot(abs(first), vec3(1.0)) < 0.15
        && dot(abs(second), vec3(1.0)) < 0.15;
    bool writtenPair = first.r > 0.75 && first.g < 0.25 && first.b < 0.25
        && second.g > 0.75 && second.r < 0.25 && second.b < 0.25;

    if (writtenPair) {
        gl_FragData[0] = vec4(1.0, 1.0, 0.0, 1.0);
    } else if (clearPair) {
        gl_FragData[0] = vec4(1.0, 0.0, 0.0, 1.0);
    } else {
        gl_FragData[0] = vec4(1.0, 0.0, 1.0, 1.0);
    }
}
