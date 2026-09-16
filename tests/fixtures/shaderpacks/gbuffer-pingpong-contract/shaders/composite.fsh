#version 120

const bool colortex4Clear = true;
const bool colortex5Clear = true;
const vec4 colortex4ClearColor = vec4(0.03, 0.03, 0.03, 1.0);
const vec4 colortex5ClearColor = vec4(0.03, 0.03, 0.03, 1.0);

/* RENDERTARGETS:4,5 */

varying vec2 texcoord;

void main() {
    gl_FragData[0] = vec4(texcoord.x, 0.20, 0.40, 1.0);
    gl_FragData[1] = vec4(0.60, texcoord.y, 0.80, 1.0);
}
