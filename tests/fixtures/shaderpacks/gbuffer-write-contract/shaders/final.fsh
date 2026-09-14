#version 120

uniform sampler2D colortex4;
uniform sampler2D colortex5;
uniform sampler2D colortex6;
uniform sampler2D colortex7;
varying vec2 texcoord;

vec4 failColour() {
    return vec4(1.0, 0.0, 1.0, 1.0);
}

bool closeEnough(float actual, float expected) {
    return abs(actual - expected) < 0.02;
}

void main() {
    vec4 w4 = texture2D(colortex4, texcoord);
    vec4 w5 = texture2D(colortex5, texcoord);
    vec4 w6 = texture2D(colortex6, texcoord);
    vec4 w7 = texture2D(colortex7, texcoord);

    bool w4Ok = closeEnough(w4.r, 0.80) && closeEnough(w4.g, 0.20)
        && closeEnough(w4.b, texcoord.x) && closeEnough(w4.a, 0.40);
    bool w5Ok = closeEnough(w5.r, 0.20) && closeEnough(w5.g, 0.80)
        && closeEnough(w5.b, texcoord.y) && closeEnough(w5.a, 0.60);
    bool w6Ok = closeEnough(w6.r, texcoord.x) && closeEnough(w6.g, 0.20)
        && closeEnough(w6.b, 0.80) && closeEnough(w6.a, 0.70);
    bool w7Ok = closeEnough(w7.r, 0.90) && closeEnough(w7.g, texcoord.y)
        && closeEnough(w7.b, 0.10) && closeEnough(w7.a, 0.80);

    if (texcoord.x < 0.5) {
        gl_FragColor = texcoord.y < 0.5
            ? (w4Ok ? vec4(1.0, 0.0, 0.0, 1.0) : failColour())
            : (w6Ok ? vec4(0.0, 0.0, 1.0, 1.0) : failColour());
    } else {
        gl_FragColor = texcoord.y < 0.5
            ? (w5Ok ? vec4(0.0, 1.0, 0.0, 1.0) : failColour())
            : (w7Ok ? vec4(1.0, 1.0, 1.0, 1.0) : failColour());
    }
}
