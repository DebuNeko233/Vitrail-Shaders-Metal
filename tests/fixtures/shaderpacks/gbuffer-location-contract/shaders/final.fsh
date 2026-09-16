#version 120

uniform sampler2D colortex4;
uniform sampler2D colortex1;
uniform sampler2D colortex7;
uniform sampler2D colortex2;
varying vec2 texcoord;

void main() {
    if (texcoord.x < 0.5) {
        gl_FragColor = texcoord.y < 0.5
            ? texture2D(colortex4, texcoord)
            : texture2D(colortex7, texcoord);
    } else {
        gl_FragColor = texcoord.y < 0.5
            ? texture2D(colortex1, texcoord)
            : texture2D(colortex2, texcoord);
    }
}
