#version 120

uniform sampler2D colortex6;
uniform sampler2D colortex7;
varying vec2 texcoord;

void main() {
    vec4 left = texture2D(colortex6, texcoord);
    vec4 right = texture2D(colortex7, texcoord);
    gl_FragColor = texcoord.x < 0.5 ? left : right;
}
