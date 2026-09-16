#version 120

uniform sampler2D colortex0;
varying vec2 texcoord;

void main() {
    vec3 marker = texture2D(colortex0, texcoord).rgb;
    bool green = marker.g > 0.90 && marker.r < 0.10 && marker.b < 0.10;
    gl_FragColor = green
            ? vec4(0.0, 1.0, 0.0, 1.0)
            : vec4(1.0, 0.0, 1.0, 1.0);
}
