#version 300 es
precision highp float;

in vec2 vTexCoord;

uniform sampler2D uBaseTexture;     // Original image before local adjustment
uniform sampler2D uAdjustedTexture; // Image with local adjustment applied
uniform sampler2D uMaskTexture;     // Single-channel mask M(x,y) in [0.0, 1.0]

uniform int uHasActiveMask;         // 1 = Active mask composite, 0 = Bypass
uniform float uMaskIntensity;       // Overall mask opacity / weight [0.0 .. 1.0]

out vec4 fragColor;

void main() {
    vec4 baseColor = texture(uBaseTexture, vTexCoord);

    // If no mask is active, pass through base color directly
    if (uHasActiveMask == 0) {
        fragColor = baseColor;
        return;
    }

    vec4 adjColor = texture(uAdjustedTexture, vTexCoord);
    float maskVal = texture(uMaskTexture, vTexCoord).r * uMaskIntensity;

    // Formula from deltamask.fsh & PDF Specification:
    // 1. diff = min(Adjusted - Original, 4.0)  (ToneMapLimitShadowGain prevents blown-out clipping)
    // 2. FinalPixel = Original + M(x, y) * diff
    vec3 diff = min(adjColor.rgb - baseColor.rgb, vec3(4.0));
    vec3 finalRgb = max(baseColor.rgb + maskVal * diff, vec3(0.0));

    fragColor = vec4(finalRgb, baseColor.a);
}
