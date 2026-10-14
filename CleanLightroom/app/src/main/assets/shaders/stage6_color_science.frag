#version 300 es
precision highp float;

in vec2 vTexCoord;

uniform sampler2D uImageTexture;

// 3-Way Color Grading Wheels (hue [0..1], sat [0..1], lum [-1..1])
uniform vec3 uGradeShadows;
uniform vec3 uGradeMidtones;
uniform vec3 uGradeHighlights;
uniform float uGradeBlending; // [0.0 .. 1.0]
uniform float uGradeBalance;  // [-1.0 .. 1.0]

// 8-Channel HSL Offsets (hue [-100..100], sat [-100..100], lum [-100..100])
uniform vec3 uHslRed;
uniform vec3 uHslOrange;
uniform vec3 uHslYellow;
uniform vec3 uHslGreen;
uniform vec3 uHslAqua;
uniform vec3 uHslBlue;
uniform vec3 uHslPurple;
uniform vec3 uHslMagenta;

// Smart Vibrance & Global Saturation
uniform float uVibrance;   // [-100.0 .. +100.0]
uniform float uSaturation; // [-100.0 .. +100.0]

out vec4 fragColor;

const vec3 PROPHOTO_LUMA = vec3(0.25, 0.50, 0.25);
const float PI = 3.14159265359;

// RGB to HSL conversion
vec3 rgbToHsl(vec3 c) {
    float maxC = max(max(c.r, c.g), c.b);
    float minC = min(min(c.r, c.g), c.b);
    float delta = maxC - minC;

    float l = (maxC + minC) * 0.5;
    float s = 0.0;
    float h = 0.0;

    if (delta > 0.00001) {
        s = (l > 0.5) ? (delta / (2.0 - maxC - minC)) : (delta / (maxC + minC));

        if (maxC == c.r) {
            h = (c.g - c.b) / delta + (c.g < c.b ? 6.0 : 0.0);
        } else if (maxC == c.g) {
            h = (c.b - c.r) / delta + 2.0;
        } else {
            h = (c.r - c.g) / delta + 4.0;
        }
        h /= 6.0;
    }
    return vec3(h, clamp(s, 0.0, 1.0), clamp(l, 0.0, 1.0));
}

// Helper for HSL to RGB
float hueToRgb(float p, float q, float t) {
    if (t < 0.0) t += 1.0;
    if (t > 1.0) t -= 1.0;
    if (t < 1.0 / 6.0) return p + (q - p) * 6.0 * t;
    if (t < 1.0 / 2.0) return q;
    if (t < 2.0 / 3.0) return p + (q - p) * (2.0 / 3.0 - t) * 6.0;
    return p;
}

// HSL to RGB conversion
vec3 hslToRgb(vec3 hsl) {
    float h = fract(hsl.x);
    float s = clamp(hsl.y, 0.0, 1.0);
    float l = clamp(hsl.z, 0.0, 1.0);

    if (s < 0.00001) {
        return vec3(l);
    }

    float q = (l < 0.5) ? (l * (1.0 + s)) : (l + s - l * s);
    float p = 2.0 * l - q;

    return vec3(
        hueToRgb(p, q, h + 1.0 / 3.0),
        hueToRgb(p, q, h),
        hueToRgb(p, q, h - 1.0 / 3.0)
    );
}

// Circular angular distance between hues on [0.0, 1.0] circle
float hueDistance(float h1, float h2) {
    float d = abs(h1 - h2);
    return min(d, 1.0 - d);
}

// Gaussian Bell Curve for 8-channel HSL hue weight
float gaussianHueWeight(float h, float centerH, float sigma) {
    float d = hueDistance(h, centerH);
    return exp(-(d * d) / (2.0 * sigma * sigma));
}

// Converts Hue/Sat wheel tone into RGB tint offset
vec3 wheelToRgb(float hue, float sat) {
    if (sat <= 0.0001) return vec3(0.0);
    return (hslToRgb(vec3(hue, 1.0, 0.5)) - vec3(0.5)) * sat * 2.0;
}

void main() {
    vec4 src = texture(uImageTexture, vTexCoord);
    vec3 col = max(src.rgb, vec3(0.0));
    float lum = dot(col, PROPHOTO_LUMA);

    // 1. Convert to HSL for 8-Channel Mixer and Smart Vibrance
    vec3 hsl = rgbToHsl(col);
    float h = hsl.x;
    float s = hsl.y;
    float l = hsl.z;

    // 2. 8-Channel HSL Mixer (Gaussian Bell Curves around 8 Adobe centers)
    // Centers: Red=0.0, Orange=0.0833, Yellow=0.1667, Green=0.3333,
    //          Aqua=0.5000, Blue=0.6667, Purple=0.7778, Magenta=0.8889
    const float sigma = 0.065; // ~23 degree standard deviation for smooth overlap
    const float sigmaOrange = 0.050; // ~18 degree narrow standard deviation for skin tone protection (Part B Section 2.B)
    float wRed     = gaussianHueWeight(h, 0.0000, sigma);
    float wOrange  = gaussianHueWeight(h, 0.0833, sigmaOrange);
    float wYellow  = gaussianHueWeight(h, 0.1667, sigma);
    float wGreen   = gaussianHueWeight(h, 0.3333, sigma);
    float wAqua    = gaussianHueWeight(h, 0.5000, sigma);
    float wBlue    = gaussianHueWeight(h, 0.6667, sigma);
    float wPurple  = gaussianHueWeight(h, 0.7778, sigma);
    float wMagenta = gaussianHueWeight(h, 0.8889, sigma);

    float sumW = wRed + wOrange + wYellow + wGreen + wAqua + wBlue + wPurple + wMagenta;
    if (sumW > 0.001) {
        vec3 deltaHsl = (
            wRed     * uHslRed +
            wOrange  * uHslOrange +
            wYellow  * uHslYellow +
            wGreen   * uHslGreen +
            wAqua    * uHslAqua +
            wBlue    * uHslBlue +
            wPurple  * uHslPurple +
            wMagenta * uHslMagenta
        ) / sumW;

        // Apply HSL Channel Offsets
        h = fract(h + (deltaHsl.x / 100.0) * (30.0 / 360.0));
        s = clamp(s * (1.0 + deltaHsl.y / 100.0), 0.0, 1.0);
        l = clamp(l * (1.0 + deltaHsl.z / 100.0), 0.0, 1.0);
    }

    // 3. Smart Vibrance (Skin Protection Algorithm) & Global Saturation
    // Exact Math (Document Section 1.3):
    // BaseBoost = (uVibrance / 100.0) * (1.0 - S_orig)
    // SkinDist = |(H - 30°) / 25°|, SkinWeight = clamp(1.0 - SkinDist^2, 0.0, 1.0)
    // Boost_final = BaseBoost * (1.0 - 0.75 * SkinWeight)
    // S_new = clamp(S_orig + Boost_final, 0.0, 1.0)
    if (abs(uVibrance) > 0.001) {
        float skinDegDist = hueDistance(h, 30.0 / 360.0) * 360.0;
        float skinDist = skinDegDist / 25.0;
        float skinWeight = clamp(1.0 - skinDist * skinDist, 0.0, 1.0);

        float baseBoost = (uVibrance / 100.0) * (1.0 - s);
        float boostFinal = baseBoost * (1.0 - 0.75 * skinWeight);

        s = clamp(s + boostFinal, 0.0, 1.0);
    }

    if (abs(uSaturation) > 0.001) {
        s = clamp(s * (1.0 + uSaturation / 100.0), 0.0, 1.0);
    }

    // Convert back from HSL to RGB
    col = hslToRgb(vec3(h, s, l));

    // 4. Color Grading (Section 3.3: 3-Way Wheels: Shadows, Midtones, Highlights)
    // - Shadows Wheel: Y < 0.33
    // - Midtones Wheel: 0.15 < Y < 0.85
    // - Highlights Wheel: Y > 0.66
    // - Blending Slider (0-100, default 50): controls overlap width between the three zones
    // - Balance Slider (-100 to +100, default 0): shifts priority (positive tilts to Highlights, negative to Shadows)

    float balShift = uGradeBalance * 0.20; // [-1.0..1.0] -> [-0.20..+0.20]
    float sThreshold = clamp(0.33 - balShift, 0.10, 0.55);
    float hThreshold = clamp(0.66 - balShift, 0.45, 0.90);

    // Blending controls the transition half-width (0.05 tight -> 0.35 wide bleed)
    float blendWidth = 0.05 + clamp(uGradeBlending, 0.0, 1.0) * 0.30;

    // Shadows weight: Y < 0.33
    float wShadow = 1.0 - smoothstep(sThreshold - blendWidth, sThreshold + blendWidth, lum);

    // Highlights weight: Y > 0.66
    float wHigh = smoothstep(hThreshold - blendWidth, hThreshold + blendWidth, lum);

    // Midtones weight: 0.15 < Y < 0.85
    float midStart = clamp(0.15 - blendWidth * 0.5, 0.0, sThreshold);
    float midEnd = clamp(0.85 + blendWidth * 0.5, hThreshold, 1.0);
    float wMid = smoothstep(midStart, sThreshold, lum) * (1.0 - smoothstep(hThreshold, midEnd, lum));

    // Apply color grading tints
    vec3 tintShadow = wheelToRgb(uGradeShadows.x, uGradeShadows.y) * wShadow;
    vec3 tintMid    = wheelToRgb(uGradeMidtones.x, uGradeMidtones.y) * wMid;
    vec3 tintHigh   = wheelToRgb(uGradeHighlights.x, uGradeHighlights.y) * wHigh;

    // Apply luminance offsets from the 3 wheels
    float lumOffset = (uGradeShadows.z * wShadow + uGradeMidtones.z * wMid + uGradeHighlights.z * wHigh) * 0.25;

    vec3 gradeTint = tintShadow + tintMid + tintHigh;
    col = max(col + gradeTint + vec3(lumOffset), vec3(0.0));

    fragColor = vec4(col, src.a);
}
