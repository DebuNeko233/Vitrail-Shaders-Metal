#version 120

/*
const int colortex4Format = R8;
const int colortex5Format = RG8;
const int colortex6Format = RGBA16F;
const int colortex7Format = RGB10_A2;
*/

/* RENDERTARGETS:4,5,6,7 */

void main() {
    gl_FragData[0] = vec4(0.75, 0.25, 0.50, 0.25);
    gl_FragData[1] = vec4(0.25, 0.75, 0.50, 0.25);
    gl_FragData[2] = vec4(0.5005, 0.25, 0.75, 0.25);
    gl_FragData[3] = vec4(0.20, 0.40, 0.60, 0.51);
}
