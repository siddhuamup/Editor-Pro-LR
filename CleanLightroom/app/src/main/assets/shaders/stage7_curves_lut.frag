#version 300 es
precision highp float;
precision highp sampler3D;

in vec2 vTexCoord;

uniform sampler2D uImageTexture;

// 1D/2D Tone Curve LUT (256x1 texture)
uniform sampler2D uToneCurveLut;
uniform int uEnableCurve;

// Native 3D LUT (33x33x33 or 64x64x64) with hardware trilinear filtering
uniform sampler3D uLut3D;
uniform int uHasLut3D;
uniform float uLut3DIntensity;

out vec4 fragColor;

void main() {
    vec4 src = texture(uImageTexture, vTexCoord);
    vec3 col = clamp(src.rgb, 0.0, 1.0);

    // 1. Tone Curves Evaluation (Cubic Spline Monotone LUT)
    if (uEnableCurve == 1) {
        col.r = texture(uToneCurveLut, vec2(col.r, 0.5)).r;
        col.g = texture(uToneCurveLut, vec2(col.g, 0.5)).r;
        col.b = texture(uToneCurveLut, vec2(col.b, 0.5)).r;
    }

    // 2. Creative 3D LUT Evaluation (Hardware Trilinear Interpolation)
    if (uHasLut3D == 1 && uLut3DIntensity > 0.001) {
        vec3 lutColor = texture(uLut3D, col).rgb;
        col = mix(col, lutColor, clamp(uLut3DIntensity, 0.0, 1.0));
    }

    fragColor = vec4(col, src.a);
}
