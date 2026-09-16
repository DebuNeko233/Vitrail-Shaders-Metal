#version 120

const bool colortex4Clear = true;
const bool colortex5Clear = true;
const bool colortex6Clear = true;
const bool colortex7Clear = true;

const vec4 colortex4ClearColor = vec4(1.0, 0.0, 0.0, 1.0);
const vec4 colortex5ClearColor = vec4(0.0, 1.0, 0.0, 1.0);
const vec4 colortex6ClearColor = vec4(0.0, 0.0, 1.0, 1.0);
const vec4 colortex7ClearColor = vec4(1.0, 1.0, 1.0, 1.0);

/* RENDERTARGETS:4,5 */

void main() {
    discard;
}
