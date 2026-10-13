#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

// Samplers
uniform sampler2D uImageTexture;
uniform sampler2D uToneCurveLut; // 256x1 1D Texture

// 1. Basic Exposure & Tone (PV2012)
uniform float uExposure;       // -5.0 to +5.0 EV
uniform float uContrast;       // -100 to +100
uniform float uHighlights;     // -100 to +100
uniform float uShadows;        // -100 to +100
uniform float uWhites;         // -100 to +100
uniform float uBlacks;         // -100 to +100

// 2. White Balance
uniform vec3 uWhiteBalanceGain; // (wR, wG, wB) from Kelvin & Tint

// 3. Presence
uniform float uClarity;        // -100 to +100
uniform float uTexture;        // -100 to +100
uniform float uDehaze;         // -100 to +100
uniform float uVibrance;       // -100 to +100
uniform float uSaturation;     // -100 to +100

// 4. Color Grading (Shadows, Midtones, Highlights)
uniform vec3 uGradeShadows;    // (HueRad, Saturation, Luminance)
uniform vec3 uGradeMidtones;
uniform vec3 uGradeHighlights;
uniform float uGradeBlending;  // 0.0 to 1.0 (default 0.5)
uniform float uGradeBalance;   // -1.0 to 1.0 (default 0.0)

// 5. 8-Channel HSL Offsets (Hue, Sat, Lum per channel)
// Order: Red, Orange, Yellow, Green, Aqua, Blue, Purple, Magenta
uniform vec3 uHslRed;
uniform vec3 uHslOrange;
uniform vec3 uHslYellow;
uniform vec3 uHslGreen;
uniform vec3 uHslAqua;
uniform vec3 uHslBlue;
uniform vec3 uHslPurple;
uniform vec3 uHslMagenta;

// 6. Previews & Modes
uniform int uAltClippingMode;  // 0 = Normal, 1 = Alt+Whites, 2 = Alt+Blacks, 3 = Mask
uniform bool uEnableCurve;

// --- Constants & Matrices ---
const mat3 M_srgb2melissa = mat3(
    0.52932379, 0.09842654, 0.01684577,
    0.33005506, 0.87350873, 0.11769549,
    0.14064019, 0.02811156, 0.86547399
);

const mat3 M_melissa2srgb = mat3(
     2.03468, -0.22883, -0.00857,
    -0.72588,  1.23307, -0.16958,
    -0.30880, -0.00424,  1.17815
);

// Convert non-linear sRGB input to Linear
vec3 srgbToLinear(vec3 c) {
    return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(0.04045, c));
}

// Convert Linear to non-linear sRGB output (IEC 61966-2-1)
vec3 linearToSrgb(vec3 cl) {
    cl = clamp(cl, 0.0, 1.0);
    return mix(12.92 * cl, 1.055 * pow(cl, vec3(0.41666)) - 0.055, step(0.0031308, cl));
}

// RGB to HSL
vec3 rgbToHsl(vec3 c) {
    float cMin = min(min(c.r, c.g), c.b);
    float cMax = max(max(c.r, c.g), c.b);
    float delta = cMax - cMin;
    float l = (cMax + cMin) * 0.5;
    float s = 0.0;
    float h = 0.0;

    if (delta > 0.00001) {
        s = l < 0.5 ? delta / (cMax + cMin) : delta / (2.0 - cMax - cMin);
        if (c.r >= cMax) {
            h = (c.g - c.b) / delta + (c.g < c.b ? 6.0 : 0.0);
        } else if (c.g >= cMax) {
            h = (c.b - c.r) / delta + 2.0;
        } else {
            h = (c.r - c.g) / delta + 4.0;
        }
        h /= 6.0; // 0.0 to 1.0
    }
    return vec3(h * 360.0, s, l);
}

// Helper: HSL to RGB
float hueToRgb(float p, float q, float t) {
    if (t < 0.0) t += 1.0;
    if (t > 1.0) t -= 1.0;
    if (t < 1.0 / 6.0) return p + (q - p) * 6.0 * t;
    if (t < 1.0 / 2.0) return q;
    if (t < 2.0 / 3.0) return p + (q - p) * (2.0 / 3.0 - t) * 6.0;
    return p;
}

vec3 hslToRgb(vec3 hsl) {
    float h = hsl.x / 360.0;
    float s = hsl.y;
    float l = hsl.z;
    if (s <= 0.00001) return vec3(l);
    float q = l < 0.5 ? l * (1.0 + s) : l + s - l * s;
    float p = 2.0 * l - q;
    return vec3(
        hueToRgb(p, q, h + 1.0 / 3.0),
        hueToRgb(p, q, h),
        hueToRgb(p, q, h - 1.0 / 3.0)
    );
}

float circularDist(float a, float b) {
    float d = abs(a - b);
    return min(d, 360.0 - d);
}

void main() {
    vec4 rawColor = texture(uImageTexture, vTexCoord);
    vec3 col = srgbToLinear(rawColor.rgb);

    // 1. Convert to Melissa RGB (Linear ProPhoto)
    col = M_srgb2melissa * col;

    // 2. White Balance (Bradford Chromatic Adaptation)
    col *= uWhiteBalanceGain;

    // 3. Exposure (2^EV)
    col *= exp2(uExposure);

    // Calculate ProPhoto Linear Luminance (weights: 0.25, 0.50, 0.25)
    float lum = dot(col, vec3(0.25, 0.50, 0.25));

    // 4. Tone Mapping (Highlights, Shadows, Whites, Blacks)
    if (uHighlights != 0.0) {
        float hlGain = (uHighlights / 100.0) * smoothstep(0.4, 1.0, lum);
        col *= (1.0 + hlGain);
    }
    if (uShadows != 0.0) {
        float shGain = (uShadows / 100.0) * (1.0 - smoothstep(0.0, 0.45, lum));
        col += vec3(shGain * 0.15);
    }
    if (uWhites != 0.0) {
        col += vec3((uWhites / 100.0) * 0.1 * smoothstep(0.7, 1.0, lum));
    }
    if (uBlacks != 0.0) {
        col += vec3((uBlacks / 100.0) * 0.08 * (1.0 - smoothstep(0.0, 0.25, lum)));
    }

    // Contrast (S-Curve about midtone 0.18 in linear)
    if (uContrast != 0.0) {
        float cFactor = (uContrast / 100.0) * 0.5 + 1.0;
        col = (col - 0.18) * cFactor + 0.18;
    }

    // 5. Dehaze
    if (uDehaze != 0.0) {
        float dehazeGain = uDehaze / 100.0;
        col = (col - vec3(0.05 * dehazeGain)) / max(1.0 - 0.25 * dehazeGain, 0.1);
    }

    // 6. Convert to HSL for Color Mixer & Vibrance
    vec3 hsl = rgbToHsl(clamp(M_melissa2srgb * col, 0.0, 1.0));

    // 8-Channel HSL Weights
    float h = hsl.x;
    float wRed     = exp(-pow(circularDist(h,   0.0) / 22.0, 2.0));
    float wOrange  = exp(-pow(circularDist(h,  30.0) / 18.0, 2.0));
    float wYellow  = exp(-pow(circularDist(h,  60.0) / 22.0, 2.0));
    float wGreen   = exp(-pow(circularDist(h, 120.0) / 28.0, 2.0));
    float wAqua    = exp(-pow(circularDist(h, 180.0) / 24.0, 2.0));
    float wBlue    = exp(-pow(circularDist(h, 240.0) / 26.0, 2.0));
    float wPurple  = exp(-pow(circularDist(h, 280.0) / 22.0, 2.0));
    float wMagenta = exp(-pow(circularDist(h, 320.0) / 22.0, 2.0));

    // Shift Hue, Sat, Lum
    float dHue = (wRed * uHslRed.x + wOrange * uHslOrange.x + wYellow * uHslYellow.x +
                  wGreen * uHslGreen.x + wAqua * uHslAqua.x + wBlue * uHslBlue.x +
                  wPurple * uHslPurple.x + wMagenta * uHslMagenta.x) * 0.36;

    float dSat = (wRed * uHslRed.y + wOrange * uHslOrange.y + wYellow * uHslYellow.y +
                  wGreen * uHslGreen.y + wAqua * uHslAqua.y + wBlue * uHslBlue.y +
                  wPurple * uHslPurple.y + wMagenta * uHslMagenta.y) / 100.0;

    float dLum = (wRed * uHslRed.z + wOrange * uHslOrange.z + wYellow * uHslYellow.z +
                  wGreen * uHslGreen.z + wAqua * uHslAqua.z + wBlue * uHslBlue.z +
                  wPurple * uHslPurple.z + wMagenta * uHslMagenta.z) / 100.0;

    hsl.x = mod(hsl.x + dHue + 360.0, 360.0);
    hsl.y = clamp(hsl.y * (1.0 + dSat), 0.0, 1.0);
    hsl.z = clamp(hsl.z + dLum * 0.3, 0.0, 1.0);

    // Vibrance with Skin Tone Protection (Center = 30°, Tolerance = 25°)
    if (uVibrance != 0.0) {
        float skinDist = circularDist(hsl.x, 30.0) / 25.0;
        float skinWeight = clamp(1.0 - skinDist * skinDist, 0.0, 1.0);
        float baseBoost = (uVibrance / 100.0) * (1.0 - hsl.y);
        float effectiveBoost = baseBoost * (1.0 - 0.75 * skinWeight);
        hsl.y = clamp(hsl.y + effectiveBoost, 0.0, 1.0);
    }

    if (uSaturation != 0.0) {
        hsl.y = clamp(hsl.y * (1.0 + uSaturation / 100.0), 0.0, 1.0);
    }

    // Convert back from HSL
    col = hslToRgb(hsl);

    // 7. 3-Way Color Grading
    float lumaGrad = dot(col, vec3(0.2126, 0.7152, 0.0722));
    float shadowWeight = 1.0 - smoothstep(0.0, 0.35 + uGradeBalance * 0.15, lumaGrad);
    float highWeight   = smoothstep(0.65 - uGradeBalance * 0.15, 1.0, lumaGrad);
    float midWeight    = 1.0 - shadowWeight - highWeight;

    if (uGradeShadows.y > 0.0) {
        vec3 shColor = hslToRgb(vec3(uGradeShadows.x, uGradeShadows.y / 100.0, 0.5));
        col = mix(col, col * shColor * 2.0, shadowWeight * (uGradeShadows.y / 100.0));
    }
    if (uGradeHighlights.y > 0.0) {
        vec3 hiColor = hslToRgb(vec3(uGradeHighlights.x, uGradeHighlights.y / 100.0, 0.5));
        col = mix(col, col * hiColor * 2.0, highWeight * (uGradeHighlights.y / 100.0));
    }

    // 8. Tone Curve LUT
    if (uEnableCurve) {
        float lutR = texture(uToneCurveLut, vec2(clamp(col.r, 0.0, 1.0), 0.5)).r;
        float lutG = texture(uToneCurveLut, vec2(clamp(col.g, 0.0, 1.0), 0.5)).r;
        float lutB = texture(uToneCurveLut, vec2(clamp(col.b, 0.0, 1.0), 0.5)).r;
        col = vec3(lutR, lutG, lutB);
    }

    // 9. Alt-Clipping Overlay Preview Mode
    if (uAltClippingMode == 1) { // Alt + Whites / Exposure
        bool clipR = col.r >= 1.0;
        bool clipG = col.g >= 1.0;
        bool clipB = col.b >= 1.0;
        if (clipR && clipG && clipB) {
            fragColor = vec4(1.0, 1.0, 1.0, 1.0); // Pure blown white
            return;
        } else if (clipR || clipG || clipB) {
            fragColor = vec4(float(clipR), float(clipG), float(clipB), 1.0);
            return;
        } else {
            fragColor = vec4(0.0, 0.0, 0.0, 1.0); // Black canvas
            return;
        }
    } else if (uAltClippingMode == 2) { // Alt + Blacks
        bool crush = (col.r <= 0.001 || col.g <= 0.001 || col.b <= 0.001);
        if (crush) {
            fragColor = vec4(0.0, 0.33, 1.0, 1.0); // Blue crushed shadows
            return;
        } else {
            fragColor = vec4(1.0, 1.0, 1.0, 1.0); // Pure white canvas
            return;
        }
    }

    // 10. Output: Convert Linear to Display sRGB
    vec3 finalRgb = linearToSrgb(col);
    fragColor = vec4(finalRgb, rawColor.a);
}
