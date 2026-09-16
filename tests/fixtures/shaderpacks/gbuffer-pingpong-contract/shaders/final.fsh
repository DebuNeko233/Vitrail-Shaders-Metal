#version 120

uniform sampler2D colortex4;
uniform sampler2D colortex5;
varying vec2 texcoord;

void main() {
    vec4 left = texture2D(colortex4, texcoord);
    vec4 right = texture2D(colortex5, texcoord);
    gl_FragColor = texcoord.x < 0.5 ? left : right;
}
