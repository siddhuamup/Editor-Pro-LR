#version 300 es
precision highp float;

uniform sampler2D uImageTexture;
uniform int uGridWidth;
uniform int uGridHeight;

// Tip 126: Conversion from Linear ProPhoto (Melissa RGB) to Display sRGB
// Makes the histogram 100% match what is visually rendered on the screen!
const mat3 M_proPhoto2sRGB = mat3(
    1.3459433, -0.2556075, -0.0511118,
   -0.5445989,  1.5081673,  0.0205351,
    0.0000000,  0.0000000,  1.2118128
);

vec3 lin2srgb(vec3 c) {
    vec3 c_clamped = max(c, vec3(0.0));
    vec3 low = c_clamped * 12.92;
    vec3 high = 1.055 * pow(c_clamped, vec3(1.0 / 2.4)) - 0.055;
    return mix(low, high, step(0.0031308, c_clamped));
}

void main() {
    int channel = gl_VertexID % 4; // 0=Red, 1=Green, 2=Blue, 3=Luma
    int sampleIdx = gl_VertexID / 4;
    int x = sampleIdx % uGridWidth;
    int y = sampleIdx / uGridWidth;

    vec2 uv = vec2(float(x) + 0.5, float(y) + 0.5) / vec2(float(uGridWidth), float(uGridHeight));
    vec3 rawLinear = texture(uImageTexture, uv).rgb;

    // Convert ProPhoto Linear to Display sRGB
    vec3 col = clamp(lin2srgb(M_proPhoto2sRGB * rawLinear), 0.0, 1.0);
    // Standard Rec.709 sRGB Luminance
    float luma = dot(col, vec3(0.2126, 0.7152, 0.0722));

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
