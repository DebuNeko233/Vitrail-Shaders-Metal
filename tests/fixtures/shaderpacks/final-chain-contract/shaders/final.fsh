#version 120

uniform sampler2D colortex0;
varying vec2 texcoord;

void main() {
    vec3 marker = texture2D(colortex0, texcoord).rgb;
    bool red = marker.r > 0.75 && marker.g < 0.25 && marker.b < 0.25;
    bool green = marker.g > 0.75 && marker.r < 0.25 && marker.b < 0.25;
    gl_FragColor = red ? vec4(0.0, 0.0, 1.0, 1.0)
        : green ? vec4(1.0, 1.0, 0.0, 1.0)
        : vec4(1.0, 0.0, 1.0, 1.0);
}
