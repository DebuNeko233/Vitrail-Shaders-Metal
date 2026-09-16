#version 120

uniform sampler2D gtexture;
uniform sampler2D normals;
uniform sampler2D specular;
varying vec2 texcoord;

/* DRAWBUFFERS:0 */

void main() {
    vec3 albedo = texture2D(gtexture, texcoord).rgb;
    vec3 marker = vec3(0.0509804, 0.1137255, 0.1843137);
    bool markerStone = distance(albedo, marker) < 0.025;

    if (!markerStone) {
        gl_FragData[0] = vec4(0.02, 0.02, 0.02, 1.0);
        return;
    }

    vec3 normalMap = texture2D(normals, texcoord).rgb;
    vec3 specularMap = texture2D(specular, texcoord).rgb;
    bool normalOk = distance(normalMap, vec3(0.2, 0.4, 0.6)) < 0.025;
    bool specularOk = distance(specularMap, vec3(0.8, 0.3019608, 0.1019608)) < 0.025;

    gl_FragData[0] = normalOk && specularOk
        ? vec4(0.0, 1.0, 0.0, 1.0)
        : vec4(1.0, 0.0, 1.0, 1.0);
}
