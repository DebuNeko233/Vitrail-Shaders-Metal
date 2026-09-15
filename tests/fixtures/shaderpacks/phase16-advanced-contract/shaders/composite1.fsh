#version 120

uniform sampler2D colortex1;
uniform sampler2D colortex2;
uniform sampler2D colortex3;
uniform sampler2DShadow shadowtex0;
varying vec2 texcoord;

/* DRAWBUFFERS:0 */

void main() {
    vec4 flags = texture2D(colortex2, texcoord);
    bool volumeOk = flags.r > 0.90;
    bool noiseOk = flags.g > 0.90;

    vec3 blended = texture2D(colortex3, texcoord).rgb;
    vec3 zeroed = texture2D(colortex1, texcoord).rgb;
    bool untouched = length(blended) < 0.04
        && distance(zeroed, vec3(1.0)) < 0.04;
    bool blendOk = distance(blended, vec3(0.75, 0.25, 0.5)) < 0.04
        && length(zeroed) < 0.04;

    float compareLow = texture(shadowtex0, vec3(0.5, 0.5, -1.0));
    float compareHigh = texture(shadowtex0, vec3(0.5, 0.5, 2.0));
    bool compareOk = compareLow > 0.95 && compareHigh < 0.05;

    vec4 passColour = vec4(0.0, 1.0, 0.0, 1.0);
    vec4 failColour = vec4(1.0, 0.0, 1.0, 1.0);
    vec4 diagnostic;
    if (texcoord.x < 0.25) {
        diagnostic = volumeOk ? passColour : failColour;
    } else if (texcoord.x < 0.50) {
        diagnostic = noiseOk ? passColour : failColour;
    } else if (texcoord.x < 0.75) {
        diagnostic = untouched ? vec4(0.0, 0.0, 0.0, 1.0)
            : (blendOk ? passColour : failColour);
    } else {
        diagnostic = compareOk ? passColour : failColour;
    }

    gl_FragData[0] = diagnostic;
}
