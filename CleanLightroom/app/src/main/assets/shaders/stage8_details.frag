#version 300 es
precision highp float;

// Stage 8: Details & Effects (Sharpening, Vignette, Grain)
// Lightroom Core Rendering Pipeline Specification (media_1791959032261.pdf, Page 6)

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uInputTexture;
uniform vec2 uTexelSize;          // 1.0 / vec2(width, height)

// Sharpening parameters
uniform float uSharpenAmount;     // 0.0 to 1.5 (mapped from 0 to 150)
uniform float uSharpenRadius;     // typically 1.0 (pixels)
uniform float uSharpenMasking;    // 0.0 to 1.0 (edge masking threshold)

// Vignette parameters
uniform float uVignetteAmount;    // -1.0 to +1.0 (-100 to +100)
uniform float uVignetteMidpoint;  // 0.0 to 1.0 (default 0.5)
uniform float uVignetteFeather;   // 0.0 to 1.0 (default 0.5)

// Film Grain parameters
uniform float uGrainAmount;       // 0.0 to 1.0 (0 to 100)
uniform float uGrainSize;         // 1.0 to 4.0
uniform float uTimeSeed;          // Seed for noise variability

// ProPhoto luminance calculation (greylog2.fsh / luminance.fsh)
float getProPhotoLuma(vec3 c) {
    return 0.25 * c.r + 0.50 * c.g + 0.25 * c.b;
}

// Pseudo-random high-quality 2D hash for film grain
float hash21(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

void main() {
    vec3 centerColor = texture(uInputTexture, vTexCoord).rgb;

    // -------------------------------------------------------------------------
    // 1. Unsharp Mask Sharpening with Edge Masking
    // Sharpened = I + amount * (I - Gaussian(I, radius)) * EdgeMask
    // -------------------------------------------------------------------------
    vec3 color = centerColor;
    if (uSharpenAmount > 0.001) {
        vec2 r = uTexelSize * uSharpenRadius;
        
        // 9-tap separable-like Gaussian approximation
        vec3 tapTL = texture(uInputTexture, vTexCoord + vec2(-r.x, -r.y)).rgb;
        vec3 tapTC = texture(uInputTexture, vTexCoord + vec2( 0.0, -r.y)).rgb;
        vec3 tapTR = texture(uInputTexture, vTexCoord + vec2( r.x, -r.y)).rgb;
        vec3 tapML = texture(uInputTexture, vTexCoord + vec2(-r.x,  0.0)).rgb;
        vec3 tapMR = texture(uInputTexture, vTexCoord + vec2( r.x,  0.0)).rgb;
        vec3 tapBL = texture(uInputTexture, vTexCoord + vec2(-r.x,  r.y)).rgb;
        vec3 tapBC = texture(uInputTexture, vTexCoord + vec2( 0.0,  r.y)).rgb;
        vec3 tapBR = texture(uInputTexture, vTexCoord + vec2( r.x,  r.y)).rgb;

        vec3 blurred = (tapTL * 0.0625 + tapTC * 0.125 + tapTR * 0.0625 +
                        tapML * 0.125  + centerColor * 0.25 + tapMR * 0.125 +
                        tapBL * 0.0625 + tapBC * 0.125 + tapBR * 0.0625);

        vec3 highPass = centerColor - blurred;
        float edgeStrength = length(highPass);

        // Edge Masking: Masking slider (0-100) suppresses sharpening on smooth areas (sky, skin)
        float edgeMask = 1.0;
        if (uSharpenMasking > 0.001) {
            float threshold = uSharpenMasking * 0.05;
            edgeMask = smoothstep(threshold * 0.5, threshold * 1.5, edgeStrength);
        }

        color += highPass * (uSharpenAmount * edgeMask);
    }

    // -------------------------------------------------------------------------
    // 2. Cosmetic Vignette Falloff
    // r = |(x - x_c, y - y_c)| / R_max
    // Gain = 1.0 - amount * smoothstep(midpoint, 1.0, r)
    // -------------------------------------------------------------------------
    if (abs(uVignetteAmount) > 0.001) {
        vec2 centeredCoord = (vTexCoord - vec2(0.5, 0.5));
        // Normalized distance: corners are ~1.0
        float r = length(centeredCoord) * 1.41421356; // 1.0 / sqrt(0.5^2 + 0.5^2)

        float halfFeather = max(uVignetteFeather * 0.45, 0.05);
        float inner = clamp(uVignetteMidpoint - halfFeather, 0.0, 0.95);
        float outer = clamp(uVignetteMidpoint + halfFeather, inner + 0.05, 1.414);

        float falloff = smoothstep(inner, outer, r);
        float gain = 1.0 - uVignetteAmount * falloff;
        color *= max(gain, 0.0);
    }

    // -------------------------------------------------------------------------
    // 3. Procedural Film Grain
    // Modulated inversely by luminance (silver-halide film response: dense in shadows/midtones)
    // -------------------------------------------------------------------------
    if (uGrainAmount > 0.001) {
        float luma = getProPhotoLuma(color);
        // Inverse luminance response curve
        float grainLumaMod = 1.0 - smoothstep(0.4, 1.2, luma);
        
        vec2 grainCoord = (vTexCoord / max(uTexelSize, vec2(0.0001))) / max(uGrainSize, 0.5);
        grainCoord += vec2(uTimeSeed * 17.13, uTimeSeed * 31.41);

        float noise = hash21(floor(grainCoord)) * 2.0 - 1.0;
        vec3 grainOffset = vec3(noise * uGrainAmount * 0.15 * grainLumaMod);
        color += grainOffset;
    }

    fragColor = vec4(color, 1.0);
}
