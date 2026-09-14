#version 120

uniform sampler2D colortex4;
uniform sampler2D colortex5;
varying vec2 texcoord;

/* RENDERTARGETS:4,5 */

bool closeEnough(float actual, float expected) {
    return abs(actual - expected) < 0.02;
}

void main() {
    vec4 source4 = texture2D(colortex4, texcoord);
    vec4 source5 = texture2D(colortex5, texcoord);

    bool source4Ok = closeEnough(source4.r, 0.15)
        && closeEnough(source4.g, texcoord.x)
        && closeEnough(source4.b, 0.35)
        && closeEnough(source4.a, 1.0);
    bool source5Ok = closeEnough(source5.r, 0.45)
        && closeEnough(source5.g, 0.55)
        && closeEnough(source5.b, texcoord.y)
        && closeEnough(source5.a, 1.0);

    if (!source4Ok || !source5Ok) {
        gl_FragData[0] = vec4(1.0, 0.0, 1.0, 1.0);
        gl_FragData[1] = vec4(1.0, 0.0, 1.0, 1.0);
        return;
    }

    gl_FragData[0] = texcoord.y < 0.5
        ? vec4(1.0, 0.0, 0.0, 1.0)
        : vec4(0.0, 0.0, 1.0, 1.0);
    gl_FragData[1] = texcoord.y < 0.5
        ? vec4(0.0, 1.0, 0.0, 1.0)
        : vec4(1.0, 1.0, 1.0, 1.0);
}
