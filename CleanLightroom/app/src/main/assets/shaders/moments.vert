#version 300 es
precision highp float;

uniform sampler2D uImageTexture;
uniform int uGridWidth;
uniform int uGridHeight;

// Lightroom ProPhoto linear luminance weights from luminance.fsh
const vec3 PROPHOTO_LUMA = vec3(0.25, 0.50, 0.25);

void main() {
    int channel = gl_VertexID % 4; // 0=Red, 1=Green, 2=Blue, 3=Luma
    int sampleIdx = gl_VertexID / 4;
    int x = sampleIdx % uGridWidth;
    int y = sampleIdx / uGridWidth;

    vec2 uv = vec2(float(x) + 0.5, float(y) + 0.5) / vec2(float(uGridWidth), float(uGridHeight));
    vec3 col = texture(uImageTexture, uv).rgb;
    float luma = dot(col, PROPHOTO_LUMA);

    float val = (channel == 0) ? col.r : ((channel == 1) ? col.g : ((channel == 2) ? col.b : luma));
    val = clamp(val, 0.0, 1.0);

    // Bin index [0..255]
    float bin = floor(val * 255.0 + 0.5);

    // Map (bin, channel) into NDC coordinates of 256x4 render target
    float x_ndc = (bin + 0.5) / 256.0 * 2.0 - 1.0;
    float y_ndc = (float(channel) + 0.5) / 4.0 * 2.0 - 1.0;

    gl_Position = vec4(x_ndc, y_ndc, 0.0, 1.0);
    gl_PointSize = 1.0;
}
