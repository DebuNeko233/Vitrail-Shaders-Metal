#version 120

uniform sampler2D colortex0;
uniform sampler2D colortex1;
varying vec2 texcoord;

void main() {
    vec4 left = texture2D(colortex0, texcoord);
    vec4 right = texture2D(colortex1, texcoord);
    gl_FragColor = texcoord.x < 0.5 ? left : right;
}
