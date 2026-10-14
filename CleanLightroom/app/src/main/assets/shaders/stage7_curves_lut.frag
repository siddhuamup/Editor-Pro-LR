#version 300 es
precision highp float;
precision highp sampler3D;

// Stage 7: Curves & Creative LUTs
// Chained 4-Zone Parametric Curve -> Point Curve -> Creative 3D LUT
// Special Rule: Parametric curve and point curve are kept as separate LUTs and chained in shader!

in vec2 vTexCoord;

uniform sampler2D uImageTexture;

// 1. Parametric Tone Curve (4-Zone Spline 256x1 1D texture)
uniform sampler2D uParametricCurveLut;
uniform int uEnableParametricCurve;

// 2. Point Curve (User Control Points 256x1 1D texture) - Chained after parametric curve!
uniform sampler2D uPointCurveLut;
uniform int uEnablePointCurve;

// 3. Creative 3D LUT (33x33x33 or 64x64x64) with hardware trilinear filtering
uniform sampler3D uLut3D;
uniform int uHasLut3D;
uniform float uLut3DIntensity;

out vec4 fragColor;

void main() {
    vec4 src = texture(uImageTexture, vTexCoord);
    vec3 col = clamp(src.rgb, 0.0, 1.0);

    // 1. Parametric Tone Curve Evaluation (4-Zone Spline LUT)
    if (uEnableParametricCurve == 1) {
        col.r = texture(uParametricCurveLut, vec2(col.r, 0.5)).r;
        col.g = texture(uParametricCurveLut, vec2(col.g, 0.5)).r;
        col.b = texture(uParametricCurveLut, vec2(col.b, 0.5)).r;
    }

    // 2. Point Curve Evaluation (User Control Points Spline LUT) - CHAINED!
    if (uEnablePointCurve == 1) {
        col.r = texture(uPointCurveLut, vec2(col.r, 0.5)).r;
        col.g = texture(uPointCurveLut, vec2(col.g, 0.5)).r;
        col.b = texture(uPointCurveLut, vec2(col.b, 0.5)).r;
    }

    // 3. Creative 3D LUT Evaluation (Hardware Trilinear Interpolation)
    if (uHasLut3D == 1 && uLut3DIntensity > 0.001) {
        vec3 lutColor = texture(uLut3D, col).rgb;
        col = mix(col, lutColor, clamp(uLut3DIntensity, 0.0, 1.0));
    }

    fragColor = vec4(col, src.a);
}
