#version 120

uniform sampler2D colortex0;
varying vec2 texcoord;

// The reader, and the reason the fixture can be compared at all: the value is quantised to eight levels, so
// a pixel that came from a target nobody loaded is a whole level of difference rather than a shade. The
// window is fully painted by this pass, so the frame is the fixture's own output and not the world's.
void main() {
    vec3 colour = texture2D(colortex0, texcoord).rgb;
    gl_FragColor = vec4(floor(colour * 8.0 + 0.5) / 8.0, 1.0);
}
