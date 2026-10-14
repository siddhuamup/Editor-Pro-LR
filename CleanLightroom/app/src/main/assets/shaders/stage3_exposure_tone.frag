#version 300 es
precision highp float;

in vec2 vTexCoord;

uniform sampler2D uImageTexture;
uniform vec2 uTexelSize;     // 1.0 / vec2(width, height)
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

    // =========================================================================
    // 1. Linear Exposure Gain: col * 2^EV (col * exp2(uExposure))
    // =========================================================================
    if (abs(uExposure) > 0.0001) {
        col *= exp2(uExposure);
    }

    float lum = dot(col, PROPHOTO_LUMA);
    if (lum <= 0.000001) {
        fragColor = vec4(col, src.a);
        return;
    }

    // =========================================================================
    // 2. Highlight Reconstruction & Chromaticity Inpainting (Section 1.5)
    // When one or more sensor channels clip (maxChannel > 0.85):
    // Propagate unclipped neighbor chromaticity (R/G and B/G ratios) so burnt
    // sky/highlights never turn gray or chalky.
    // =========================================================================
    float maxChannel = max(max(col.r, col.g), col.b);
    if (maxChannel > 0.85 && (uTexelSize.x > 0.0 || uTexelSize.y > 0.0)) {
        vec2 stepOffset = uTexelSize * 3.0;
        vec3 n1 = texture(uImageTexture, vTexCoord + vec2(-stepOffset.x, 0.0)).rgb;
        vec3 n2 = texture(uImageTexture, vTexCoord + vec2( stepOffset.x, 0.0)).rgb;
        vec3 n3 = texture(uImageTexture, vTexCoord + vec2(0.0, -stepOffset.y)).rgb;
        vec3 n4 = texture(uImageTexture, vTexCoord + vec2(0.0,  stepOffset.y)).rgb;

        vec2 chromSum = vec2(0.0);
        float weightSum = 0.0;

        if (max(max(n1.r, n1.g), n1.b) < 0.85 && n1.g > 0.001) {
            chromSum += vec2(n1.r / n1.g, n1.b / n1.g);
            weightSum += 1.0;
        }
        if (max(max(n2.r, n2.g), n2.b) < 0.85 && n2.g > 0.001) {
            chromSum += vec2(n2.r / n2.g, n2.b / n2.g);
            weightSum += 1.0;
        }
        if (max(max(n3.r, n3.g), n3.b) < 0.85 && n3.g > 0.001) {
            chromSum += vec2(n3.r / n3.g, n3.b / n3.g);
            weightSum += 1.0;
        }
        if (max(max(n4.r, n4.g), n4.b) < 0.85 && n4.g > 0.001) {
            chromSum += vec2(n4.r / n4.g, n4.b / n4.g);
            weightSum += 1.0;
        }

        if (weightSum > 0.0) {
            vec2 avgChrom = chromSum / weightSum; // (R/G, B/G)
            vec3 inpainted = vec3(avgChrom.x * col.g, col.g, avgChrom.y * col.g);
            float inpaintLum = dot(inpainted, PROPHOTO_LUMA);
            if (inpaintLum > 0.0001) {
                inpainted *= (lum / inpaintLum);
            }
            float clipFactor = clamp((maxChannel - 0.85) / 0.15, 0.0, 1.0);
            col = mix(col, inpainted, clipFactor);
            lum = dot(col, PROPHOTO_LUMA);
        }
    }

    // =========================================================================
    // 3. Luminance Knee Compression (Section 1.5):
    // L_recovered = 0.85 + 0.15 * (1.0 - exp(-(L - 0.85) / 0.15))
    // =========================================================================
    if (lum > 0.85) {
        float lKnee = 0.85 + 0.15 * (1.0 - exp(-(lum - 0.85) / 0.15));
        float recFactor = clamp(-uHighlights / 100.0, 0.0, 1.0);
        if (lum > 1.0) {
            recFactor = max(recFactor, clamp((lum - 1.0) * 2.0, 0.0, 1.0));
        }
        float targetLum = mix(lum, lKnee, recFactor);
        col *= (targetLum / lum);
        lum = targetLum;
    }

    // =========================================================================
    // 4. Log2 dynamic range transformation (greylog2.fsh)
    // =========================================================================
    float logLum = log2(lum + kFlareOffset);
    float normLum = clamp((logLum + 12.0) / 12.0, 0.0, 1.5); // maps [-12..0] to [0..1]

    // 5. Positive Highlights Boost (if uHighlights > 0)
    float highMod = (uHighlights / 100.0);
    float adjLogLum = logLum;
    if (highMod > 0.0) {
        float highFactor = smoothstep(0.45, 1.0, normLum);
        adjLogLum += highMod * highFactor * 1.2;
    }

    // 6. Shadows Lift / Darkening
    float shadowFactor = (1.0 - smoothstep(0.0, 0.55, normLum)) * smoothstep(0.0, 0.15, normLum);
    float shadowMod = (uShadows / 100.0);
    adjLogLum += shadowMod * shadowFactor * 1.6;

    // 7. Whites & Blacks Clipping Bounds
    float whitesFactor = smoothstep(0.70, 1.2, normLum);
    adjLogLum += (uWhites / 100.0) * whitesFactor * 0.8;

    float blacksFactor = 1.0 - smoothstep(0.0, 0.35, normLum);
    adjLogLum += (uBlacks / 100.0) * blacksFactor * 0.8;

    // 8. Contrast (S-Curve in Log space around mid-gray)
    if (abs(uContrast) > 0.01) {
        float contrastRatio = 1.0 + (uContrast / 100.0) * 0.5;
        float midGrayLog = -2.47; // log2(0.18)
        adjLogLum = midGrayLog + (adjLogLum - midGrayLog) * contrastRatio;
    }

    // 9. Invert back from log2 to linear radiance: exp2(adjLogLum) - 2^-12 (log2lin.fsh)
    float newLum = max(exp2(adjLogLum) - kFlareOffset, 0.0);

    // 10. Apply luminance adjustment ratio to RGB channels
    float lumScale = newLum / max(lum, 0.00001);
    vec3 outRgb = col * lumScale;

    // Output is in wide-gamut ProPhoto linear space
    fragColor = vec4(outRgb, src.a);
}
