#version 120

const bool colortex4Clear = true;
const bool colortex5Clear = true;
const bool colortex6Clear = true;
const bool colortex7Clear = true;
const vec4 colortex4ClearColor = vec4(0.05, 0.05, 0.05, 1.0);
const vec4 colortex5ClearColor = vec4(0.05, 0.05, 0.05, 1.0);
const vec4 colortex6ClearColor = vec4(0.05, 0.05, 0.05, 1.0);
const vec4 colortex7ClearColor = vec4(0.05, 0.05, 0.05, 1.0);

/* RENDERTARGETS:4,5,6,7 */

varying vec2 texcoord;

void main() {
    gl_FragData[0] = vec4(0.80, 0.20, texcoord.x, 0.40);
    gl_FragData[1] = vec4(0.20, 0.80, texcoord.y, 0.60);
    gl_FragData[2] = vec4(texcoord.x, 0.20, 0.80, 0.70);
    gl_FragData[3] = vec4(0.90, texcoord.y, 0.10, 0.80);
}
