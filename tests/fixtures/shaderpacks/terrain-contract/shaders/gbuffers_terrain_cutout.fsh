#version 120

uniform sampler2D texture;
varying vec2 texcoord;

void main() {
    float alpha = texture2D(texture, texcoord).a;
    gl_FragColor = vec4(0.0, 1.0, 0.0, alpha);
}
