#version 120

uniform sampler2D colortex0;
varying vec2 texcoord;
const bool colortex0MipmapEnabled = true;

/* DRAWBUFFERS:0 */

void main() {
    vec3 lod = texture2D(colortex0, texcoord, 6.0).rgb;
    bool averaged = abs(lod.r - 0.5) < 0.12 && abs(lod.g - 0.5) < 0.12 && abs(lod.b - 0.5) < 0.12;
    float checker = mod(floor(gl_FragCoord.x) + floor(gl_FragCoord.y), 2.0);
    gl_FragData[0] = averaged ? vec4(0.0, checker, 0.0, 1.0) : vec4(1.0, 0.0, 1.0, 1.0);
}
