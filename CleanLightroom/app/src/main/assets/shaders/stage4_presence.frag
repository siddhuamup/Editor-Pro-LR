#version 300 es
precision highp float;

in vec2 vTexCoord;

uniform sampler2D uImageTexture;
uniform vec2 uTexelSize;     // 1.0 / vec2(width, height)
uniform float uClarity;      // [-100.0 .. +100.0] Local Laplacian mid-frequency
uniform float uTexture;      // [-100.0 .. +100.0] High-frequency detail
uniform float uDehaze;       // [-100.0 .. +100.0] Dark Channel Prior haze removal

out vec4 fragColor;

// ProPhoto RGB Luminance weights from Lightroom luminance.fsh
const vec3 PROPHOTO_LUMA = vec3(0.25, 0.50, 0.25);

// Atmospheric airlight estimate for Dehaze (DCP)
const vec3 AIRLIGHT = vec3(0.92, 0.95, 1.0);

void main() {
    vec4 centerSample = texture(uImageTexture, vTexCoord);
    vec3 col = centerSample.rgb;

    // 1. Multi-scale Sampling for High-Frequency & Mid-Frequency Bands
    // 4-point immediate cross taps for high-frequency (Texture)
    vec2 step1 = uTexelSize * 1.5;
    vec3 t1 = texture(uImageTexture, vTexCoord + vec2(-step1.x, 0.0)).rgb;
    vec3 t2 = texture(uImageTexture, vTexCoord + vec2(step1.x, 0.0)).rgb;
    vec3 t3 = texture(uImageTexture, vTexCoord + vec2(0.0, -step1.y)).rgb;
    vec3 t4 = texture(uImageTexture, vTexCoord + vec2(0.0, step1.y)).rgb;
    vec3 blurSmall = (t1 + t2 + t3 + t4) * 0.25;

    // High-frequency detail band: L_high = col - blurSmall
    vec3 highDetail = col - blurSmall;

    // 8-point wider taps for mid-frequency (Clarity / Local Laplacian)
    vec2 step2 = uTexelSize * 4.5;
    vec3 m1 = texture(uImageTexture, vTexCoord + vec2(-step2.x, 0.0)).rgb;
    vec3 m2 = texture(uImageTexture, vTexCoord + vec2(step2.x, 0.0)).rgb;
    vec3 m3 = texture(uImageTexture, vTexCoord + vec2(0.0, -step2.y)).rgb;
    vec3 m4 = texture(uImageTexture, vTexCoord + vec2(0.0, step2.y)).rgb;
    vec3 m5 = texture(uImageTexture, vTexCoord + vec2(-step2.x, -step2.y) * 0.707).rgb;
    vec3 m6 = texture(uImageTexture, vTexCoord + vec2(step2.x, -step2.y) * 0.707).rgb;
    vec3 m7 = texture(uImageTexture, vTexCoord + vec2(-step2.x, step2.y) * 0.707).rgb;
    vec3 m8 = texture(uImageTexture, vTexCoord + vec2(step2.x, step2.y) * 0.707).rgb;
    vec3 blurMid = (m1 + m2 + m3 + m4 + m5 + m6 + m7 + m8) * 0.125;

    // Mid-frequency band: L_mid = blurSmall - blurMid
    vec3 midDetail = blurSmall - blurMid;

    // 2. Texture Enhancement (High Frequency)
    // Works on fine skin pores and micro-details with threshold clamping
    if (abs(uTexture) > 0.01) {
        float textureAmount = uTexture / 100.0;
        // Soft limit to avoid boosting high-frequency noise
        vec3 limitedHigh = clamp(highDetail, vec3(-0.15), vec3(0.15));
        col += limitedHigh * textureAmount * 1.5;
    }

    // 3. Local Laplacian Clarity (Mid Frequency)
    // Formula from locallaplacian.fsh:
    // alpha = max(min(uInv_ds * (g - uRef) + 1.0, uInv_ds * (uRef - g) + 1.0), 0.0)
    if (abs(uClarity) > 0.01) {
        float clarityAmount = uClarity / 100.0;

        float g = dot(blurSmall, PROPHOTO_LUMA);
        float uRef = dot(blurMid, PROPHOTO_LUMA);
        float uInv_ds = 4.0; // stops per sample scaling

        float line0 = uInv_ds * (g - uRef) + 1.0;
        float line1 = uInv_ds * (uRef - g) + 1.0;
        float alpha = max(min(line0, line1), 0.0);

        // Adobe Local Laplacian halo-free local contrast scaling
        vec3 clarityDelta = midDetail * alpha * clarityAmount * 1.8;
        col += clarityDelta;
    }

    // 4. Dark Channel Prior Dehaze (Atmospheric Scattering Inversion)
    if (abs(uDehaze) > 0.01) {
        float dehazeAmount = uDehaze / 100.0;

        // Dark channel = minimum of RGB normalized by airlight
        vec3 normCol = col / AIRLIGHT;
        float darkChannel = min(min(normCol.r, normCol.g), normCol.b);

        if (dehazeAmount > 0.0) {
            // Remove haze: recover atmospheric transmission t(x)
            float t = clamp(1.0 - 0.75 * darkChannel * dehazeAmount, 0.15, 1.0);
            col = ((col - AIRLIGHT) / t) + AIRLIGHT;
        } else {
            // Add atmospheric mist
            float fog = -dehazeAmount * 0.4;
            col = mix(col, AIRLIGHT, fog);
        }
    }

    fragColor = vec4(max(col, vec3(0.0)), centerSample.a);
}
