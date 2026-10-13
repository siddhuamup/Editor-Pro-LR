#version 300 es
precision highp float;

in vec2 vTexCoord;

uniform sampler2D uImageTexture;
uniform float uExposure;    // EV gain [-5.0 .. +5.0]
uniform float uContrast;    // [-100.0 .. +100.0]
uniform float uHighlights;  // [-100.0 .. +100.0]
uniform float uShadows;     // [-100.0 .. +100.0]
uniform float uWhites;      // [-100.0 .. +100.0]
uniform float uBlacks;      // [-100.0 .. +100.0]

out vec4 fragColor;

// ProPhoto RGB Luminance weights from Lightroom luminance.fsh
const vec3 PROPHOTO_LUMA = vec3(0.25, 0.50, 0.25);

// Flare offset in log2 space (2^-12) from Lightroom greylog2.fsh
const float kFlareOffset = 0.000244140625;

void main() {
    vec4 src = texture(uImageTexture, vTexCoord);
    vec3 col = max(src.rgb, vec3(0.0));

    // 1. Linear Exposure Gain: C * 2^EV (col * exp2(uExposure))
    if (abs(uExposure) > 0.0001) {
        col *= exp2(uExposure);
    }

    // 2. Measure Luminance in ProPhoto RGB linear space
    float lum = dot(col, PROPHOTO_LUMA);
    if (lum <= 0.000001) {
        fragColor = vec4(col, src.a);
        return;
    }

    // 3. Log2 dynamic range transformation (greylog2.fsh)
    // Normalized perceptual range: log2(lum + 2^-12)
    float logLum = log2(lum + kFlareOffset);
    // Reference mid-gray 0.18 in log2 space: log2(0.18 + kFlareOffset) ~ -2.47
    float normLum = clamp((logLum + 12.0) / 12.0, 0.0, 1.5); // maps [-12..0] to [0..1]

    // 4. Highlights Recovery / Compression
    // Highlights affect high luminance zones (normLum > 0.5)
    float highFactor = smoothstep(0.45, 1.0, normLum);
    float highMod = (uHighlights / 100.0);
    // Shoulder compression knee curve
    float adjLogLum = logLum;
    if (highMod < 0.0) {
        // Recovery: pull back overexposed highlights
        adjLogLum += highMod * highFactor * 1.8;
    } else {
        // Boost highlights
        adjLogLum += highMod * highFactor * 1.2;
    }

    // 5. Shadows Lift / Darkening
    // Shadows affect dark areas without lifting the absolute zero noise floor
    float shadowFactor = (1.0 - smoothstep(0.0, 0.55, normLum)) * smoothstep(0.0, 0.15, normLum);
    float shadowMod = (uShadows / 100.0);
    adjLogLum += shadowMod * shadowFactor * 1.6;

    // 6. Whites & Blacks Clipping Bounds
    // Whites shifts specular endpoint
    float whitesFactor = smoothstep(0.70, 1.2, normLum);
    adjLogLum += (uWhites / 100.0) * whitesFactor * 0.8;

    // Blacks shifts deep baseline floor
    float blacksFactor = 1.0 - smoothstep(0.0, 0.35, normLum);
    adjLogLum += (uBlacks / 100.0) * blacksFactor * 0.8;

    // 7. Contrast (S-Curve in Log space around mid-gray)
    if (abs(uContrast) > 0.01) {
        float contrastRatio = 1.0 + (uContrast / 100.0) * 0.5;
        float midGrayLog = -2.47; // log2(0.18)
        adjLogLum = midGrayLog + (adjLogLum - midGrayLog) * contrastRatio;
    }

    // 8. Invert back from log2 to linear radiance: exp2(adjLogLum) - 2^-12 (log2lin.fsh)
    float newLum = max(exp2(adjLogLum) - kFlareOffset, 0.0);

    // 9. Apply luminance adjustment ratio to RGB channels to preserve chromaticity & hue
    float lumScale = newLum / max(lum, 0.00001);
    vec3 outRgb = col * lumScale;

    // Output is in wide-gamut ProPhoto linear space (stored in RGBA16F FBO, preventing banding)
    fragColor = vec4(outRgb, src.a);
}
