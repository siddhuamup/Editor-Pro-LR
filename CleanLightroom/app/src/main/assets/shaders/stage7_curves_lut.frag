#version 300 es
precision highp float;
precision highp sampler3D;

// Stage 7: Curves & Creative LUTs
// Chained 4-Zone Parametric Curve -> 4-Channel Point Curve -> Creative 3D LUT
// Tips 162 & 190: Perceptual sRGB evaluation & RGBA 4-channel texture for Composite + R/G/B

in vec2 vTexCoord;

uniform sampler2D uImageTexture;

// 1. Parametric Tone Curve (4-Zone Spline 256x1 1D texture)
uniform sampler2D uParametricCurveLut;
uniform int uEnableParametricCurve;

// 2. Point Curve (RGBA 4-Channel Spline 256x1 1D texture) - Tip 162: R/G/B/A independent curves!
uniform sampler2D uPointCurveLut;
uniform int uEnablePointCurve;

// 3. Creative 3D LUT (33x33x33 or 64x64x64) with hardware trilinear filtering
uniform sampler3D uLut3D;
uniform int uHasLut3D;
uniform float uLut3DIntensity;

out vec4 fragColor;

// Tip 190: Color space conversion matrices (Melissa / Linear ProPhoto <-> Perceptual sRGB)
const mat3 M_proPhoto2sRGB = mat3(
    1.3459433, -0.2556075, -0.0511118,
   -0.5445989,  1.5081673,  0.0205351,
    0.0000000,  0.0000000,  1.2118128
);

const mat3 M_sRGB2proPhoto = mat3(
    0.7976749, 0.1351917, 0.0313534,
    0.2880402, 0.7118741, 0.0000857,
    0.0000000, 0.0000000, 0.8252100
);

vec3 lin2srgb(vec3 c) {
    vec3 c_clamped = max(c, vec3(0.0));
    vec3 low = c_clamped * 12.92;
    vec3 high = 1.055 * pow(c_clamped, vec3(1.0 / 2.4)) - 0.055;
    return mix(low, high, step(0.0031308, c_clamped));
}

vec3 srgb2lin(vec3 c) {
    vec3 c_clamped = clamp(c, 0.0, 1.0);
    vec3 low = c_clamped / 12.92;
    vec3 high = pow((c_clamped + 0.055) / 1.055, vec3(2.4));
    return mix(low, high, step(0.04045, c_clamped));
}

void main() {
    vec4 src = texture(uImageTexture, vTexCoord);
    vec3 col = clamp(src.rgb, 0.0, 1.0);

    // Tip 190: Evaluate tone curves in perceptual sRGB gamma space (matches Adobe Lightroom behavior)
    vec3 srgbCol = lin2srgb(clamp(M_proPhoto2sRGB * col, 0.0, 1.0));

    // 1. Parametric Tone Curve Evaluation (4-Zone Spline LUT)
    if (uEnableParametricCurve == 1) {
        srgbCol.r = texture(uParametricCurveLut, vec2(srgbCol.r, 0.5)).r;
        srgbCol.g = texture(uParametricCurveLut, vec2(srgbCol.g, 0.5)).r;
        srgbCol.b = texture(uParametricCurveLut, vec2(srgbCol.b, 0.5)).r;
    }

    // 2. Point Curve Evaluation (Tip 162: RGBA 4-Channel Point Curve LUT: .r=Red, .g=Green, .b=Blue)
    if (uEnablePointCurve == 1) {
        srgbCol.r = texture(uPointCurveLut, vec2(srgbCol.r, 0.5)).r;
        srgbCol.g = texture(uPointCurveLut, vec2(srgbCol.g, 0.5)).g;
        srgbCol.b = texture(uPointCurveLut, vec2(srgbCol.b, 0.5)).b;
    }

    // Convert back to Linear ProPhoto
    col = clamp(M_sRGB2proPhoto * srgb2lin(srgbCol), 0.0, 1.0);

    // 3. Creative 3D LUT Evaluation (Hardware Trilinear Interpolation)
    if (uHasLut3D == 1 && uLut3DIntensity > 0.001) {
        vec3 lutColor = texture(uLut3D, col).rgb;
        col = mix(col, lutColor, clamp(uLut3DIntensity, 0.0, 1.0));
    }

    fragColor = vec4(col, src.a);
}
