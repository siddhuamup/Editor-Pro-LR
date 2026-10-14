#version 300 es
precision highp float;

in vec2 vTexCoord;

uniform sampler2D uImageTexture;
uniform vec2 uTexelSize;     // 1.0 / vec2(width, height)
uniform float uClarity;      // [-100.0 .. +100.0] Local Laplacian mid-frequency
uniform float uTexture;      // [-100.0 .. +100.0] High-frequency micro-contrast
uniform float uDehaze;       // [-100.0 .. +100.0] Dark Channel Prior haze removal

out vec4 fragColor;

// ProPhoto RGB Luminance weights from Lightroom luminance.fsh
const vec3 PROPHOTO_LUMA = vec3(0.25, 0.50, 0.25);

// Atmospheric airlight estimate for Dehaze (DCP) in Melissa/ProPhoto space
const vec3 AIRLIGHT = vec3(0.92, 0.95, 1.0);
const float AIRLIGHT_SCALAR = 0.955; // dot(AIRLIGHT, PROPHOTO_LUMA)

void main() {
    vec4 centerSample = texture(uImageTexture, vTexCoord);
    vec3 col = centerSample.rgb;

    // =========================================================================
    // 1. Multi-scale Laplacian Pyramid Sampling (Levels 0..3)
    // =========================================================================

    // Level 0: Center pixel (I0)
    vec3 I0 = col;
    float darkMin = min(min(col.r, col.g), col.b);

    // Level 1: 1px - 4px radius (High-frequency Texture micro-details)
    // 4 diagonal bilinear samples at offset ~2.0 texels
    vec2 offL1 = uTexelSize * 2.0;
    vec3 sL1_1 = texture(uImageTexture, vTexCoord + vec2(-offL1.x, -offL1.y)).rgb;
    vec3 sL1_2 = texture(uImageTexture, vTexCoord + vec2( offL1.x, -offL1.y)).rgb;
    vec3 sL1_3 = texture(uImageTexture, vTexCoord + vec2(-offL1.x,  offL1.y)).rgb;
    vec3 sL1_4 = texture(uImageTexture, vTexCoord + vec2( offL1.x,  offL1.y)).rgb;
    vec3 G1 = (sL1_1 + sL1_2 + sL1_3 + sL1_4) * 0.25;

    // Track Dark Channel Prior across local neighborhood Omega(x)
    darkMin = min(darkMin, min(min(sL1_1.r, sL1_1.g), sL1_1.b));
    darkMin = min(darkMin, min(min(sL1_2.r, sL1_2.g), sL1_2.b));
    darkMin = min(darkMin, min(min(sL1_3.r, sL1_3.g), sL1_3.b));
    darkMin = min(darkMin, min(min(sL1_4.r, sL1_4.g), sL1_4.b));

    // Level 2: 8px - 12px radius (Mid-frequency Local Laplacian band)
    vec2 offL2_c = uTexelSize * 9.0;
    vec2 offL2_d = uTexelSize * 6.36; // 9.0 * 0.707
    vec3 sL2_1 = texture(uImageTexture, vTexCoord + vec2(-offL2_c.x, 0.0)).rgb;
    vec3 sL2_2 = texture(uImageTexture, vTexCoord + vec2( offL2_c.x, 0.0)).rgb;
    vec3 sL2_3 = texture(uImageTexture, vTexCoord + vec2(0.0, -offL2_c.y)).rgb;
    vec3 sL2_4 = texture(uImageTexture, vTexCoord + vec2(0.0,  offL2_c.y)).rgb;
    vec3 sL2_5 = texture(uImageTexture, vTexCoord + vec2(-offL2_d.x, -offL2_d.y)).rgb;
    vec3 sL2_6 = texture(uImageTexture, vTexCoord + vec2( offL2_d.x, -offL2_d.y)).rgb;
    vec3 sL2_7 = texture(uImageTexture, vTexCoord + vec2(-offL2_d.x,  offL2_d.y)).rgb;
    vec3 sL2_8 = texture(uImageTexture, vTexCoord + vec2( offL2_d.x,  offL2_d.y)).rgb;
    vec3 G2 = (sL2_1 + sL2_2 + sL2_3 + sL2_4 + sL2_5 + sL2_6 + sL2_7 + sL2_8) * 0.125;

    // Track Dark Channel Prior across wider neighborhood
    darkMin = min(darkMin, min(min(sL2_1.r, sL2_1.g), sL2_1.b));
    darkMin = min(darkMin, min(min(sL2_2.r, sL2_2.g), sL2_2.b));
    darkMin = min(darkMin, min(min(sL2_3.r, sL2_3.g), sL2_3.b));
    darkMin = min(darkMin, min(min(sL2_4.r, sL2_4.g), sL2_4.b));

    // Level 3: 24px - 32px radius (Local reference band uRef)
    vec2 offL3_c = uTexelSize * 26.0;
    vec2 offL3_d = uTexelSize * 18.38; // 26.0 * 0.707
    vec3 sL3_1 = texture(uImageTexture, vTexCoord + vec2(-offL3_c.x, 0.0)).rgb;
    vec3 sL3_2 = texture(uImageTexture, vTexCoord + vec2( offL3_c.x, 0.0)).rgb;
    vec3 sL3_3 = texture(uImageTexture, vTexCoord + vec2(0.0, -offL3_c.y)).rgb;
    vec3 sL3_4 = texture(uImageTexture, vTexCoord + vec2(0.0,  offL3_c.y)).rgb;
    vec3 sL3_5 = texture(uImageTexture, vTexCoord + vec2(-offL3_d.x, -offL3_d.y)).rgb;
    vec3 sL3_6 = texture(uImageTexture, vTexCoord + vec2( offL3_d.x, -offL3_d.y)).rgb;
    vec3 sL3_7 = texture(uImageTexture, vTexCoord + vec2(-offL3_d.x,  offL3_d.y)).rgb;
    vec3 sL3_8 = texture(uImageTexture, vTexCoord + vec2( offL3_d.x,  offL3_d.y)).rgb;
    vec3 G3 = (sL3_1 + sL3_2 + sL3_3 + sL3_4 + sL3_5 + sL3_6 + sL3_7 + sL3_8) * 0.125;

    // =========================================================================
    // 2. Texture: Level 0..1 Micro-Contrast without touching Object Contours
    // =========================================================================
    if (abs(uTexture) > 0.01) {
        vec3 L0_detail = I0 - G1;
        float g_mag = max(max(abs(L0_detail.r), abs(L0_detail.g)), abs(L0_detail.b));

        // Edge contour gate: large gradients (contours > 0.12) are attenuated
        // so micro-contrast sharpens only fine hair, skin pores, and fabric threads
        float contourFactor = clamp(1.0 - (g_mag / 0.12) * (g_mag / 0.12), 0.0, 1.0);
        float textureAmount = uTexture / 100.0;
        col += L0_detail * contourFactor * textureAmount * 1.5;
    }

    // =========================================================================
    // 3. Clarity: Mid-Frequency Local Laplacian with Triangular Transfer Function
    // =========================================================================
    // Level 2 and Level 3 bands (8px - 32px radius)
    // Formula from Lightroom locallaplacian.fsh:
    // line0 = uInv_ds * (g - uRef) + 1.0
    // line1 = uInv_ds * (uRef - g) + 1.0
    // alpha = max(min(line0, line1), 0.0)
    if (abs(uClarity) > 0.01) {
        vec3 L_mid = G2 - G3;
        float g = dot(G2, PROPHOTO_LUMA);
        float uRef = dot(G3, PROPHOTO_LUMA);
        float uInv_ds = 4.0; // scale factor (stops per sample)

        float line0 = uInv_ds * (g - uRef) + 1.0;
        float line1 = uInv_ds * (uRef - g) + 1.0;
        float alpha = max(min(line0, line1), 0.0);

        float clarityAmount = uClarity / 100.0;
        vec3 clarityDelta = L_mid * alpha * clarityAmount * 1.8;
        col += clarityDelta;
    }

    // =========================================================================
    // 4. Dehaze: Dark Channel Prior (DCP) Atmospheric Scattering Inversion
    // =========================================================================
    // Normative Formula (Section 1.4):
    // J_dark(x) = min_{c in {r,g,b}} (min_{y in Omega(x)} I^c(y))
    // t(x) = 1.0 - omega * (J_dark(x) / A)
    // J(x) = (I(x) - A) / max(t(x), 0.1) + A
    if (abs(uDehaze) > 0.01) {
        float dehazeAmount = uDehaze / 100.0;

        if (dehazeAmount > 0.0) {
            float omega = 0.95 * dehazeAmount;
            float t = clamp(1.0 - omega * (darkMin / AIRLIGHT_SCALAR), 0.1, 1.0);
            col = ((col - AIRLIGHT) / max(t, 0.1)) + AIRLIGHT;
        } else {
            // Negative Dehaze adds atmospheric fog/haze
            float fog = -dehazeAmount * 0.45;
            col = mix(col, AIRLIGHT, fog);
        }
    }

    fragColor = vec4(max(col, vec3(0.0)), centerSample.a);
}
