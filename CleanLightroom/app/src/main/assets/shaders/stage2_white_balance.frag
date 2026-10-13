#version 300 es
precision highp float;

in vec2 vTexCoord;

uniform sampler2D uImageTexture;
uniform vec3 uWhiteBalanceGains; // (W_R, W_G, W_B) Bradford CAT scaling

out vec4 fragColor;

void main() {
    vec4 src = texture(uImageTexture, vTexCoord);
    // Linear ProPhoto RGB White Balance channel scaling
    // Scales channels so neutral gray aligns to (R'=G'=B')
    vec3 balanced = src.rgb * uWhiteBalanceGains;
    fragColor = vec4(balanced, src.a);
}
