#version 300 es
precision highp float;

in vec2 vTexCoord;

uniform sampler2D uMomentsTexture; // 256x4 texture containing raw accumulated bin counts
uniform float uMaxCount;

out vec4 fragColor;

// reductionsum.fsh: Normalizes the 256x4 histogram texture into [0..1] range
// Row 0 = Red bins, Row 1 = Green bins, Row 2 = Blue bins, Row 3 = Luma bins
void main() {
    vec4 rawCount = texture(uMomentsTexture, vTexCoord);
    float norm = max(uMaxCount, 1.0);
    fragColor = rawCount / norm;
}
