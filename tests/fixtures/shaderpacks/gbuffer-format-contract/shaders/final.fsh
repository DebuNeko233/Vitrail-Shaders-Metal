#version 120

uniform sampler2D colortex4;
uniform sampler2D colortex5;
uniform sampler2D colortex6;
uniform sampler2D colortex7;
varying vec2 texcoord;

vec4 failColour() {
    return vec4(1.0, 0.0, 1.0, 1.0);
}

void main() {
    vec4 r8 = texture2D(colortex4, texcoord);
    vec4 rg8 = texture2D(colortex5, texcoord);
    vec4 rgba16f = texture2D(colortex6, texcoord);
    vec4 rgb10a2 = texture2D(colortex7, texcoord);

    bool r8Ok = abs(r8.r - 0.75) < 0.01
        && abs(r8.g) < 0.01 && abs(r8.b) < 0.01 && abs(r8.a - 1.0) < 0.01;
    bool rg8Ok = abs(rg8.r - 0.25) < 0.01 && abs(rg8.g - 0.75) < 0.01
        && abs(rg8.b) < 0.01 && abs(rg8.a - 1.0) < 0.01;
    bool rgba16fOk = abs(rgba16f.r - 0.5005) < 0.0005;
    bool rgb10a2Ok = abs(rgb10a2.a - 0.6666667) < 0.02;

    if (texcoord.x < 0.5) {
        gl_FragColor = texcoord.y < 0.5
            ? (r8Ok ? vec4(1.0, 0.0, 0.0, 1.0) : failColour())
            : (rgba16fOk ? vec4(0.0, 0.0, 1.0, 1.0) : failColour());
    } else {
        gl_FragColor = texcoord.y < 0.5
            ? (rg8Ok ? vec4(0.0, 1.0, 0.0, 1.0) : failColour())
            : (rgb10a2Ok ? vec4(1.0, 1.0, 1.0, 1.0) : failColour());
    }
}
