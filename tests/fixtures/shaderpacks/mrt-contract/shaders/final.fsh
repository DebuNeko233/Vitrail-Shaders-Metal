#version 120

uniform sampler2D colortex0;
uniform sampler2D colortex1;
uniform sampler2D colortex2;
uniform sampler2D colortex3;
varying vec2 texcoord;

void main() {
    if (texcoord.x < 0.5) {
        gl_FragColor = texcoord.y < 0.5
            ? texture2D(colortex0, texcoord)
            : texture2D(colortex2, texcoord);
    } else {
        gl_FragColor = texcoord.y < 0.5
            ? texture2D(colortex1, texcoord)
            : texture2D(colortex3, texcoord);
    }
}
