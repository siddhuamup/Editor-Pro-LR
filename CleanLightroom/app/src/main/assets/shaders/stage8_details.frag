#version 300 es
precision highp float;

// Stage 8: Details & Effects (Sharpening, Noise Reduction, Vignette, Grain)
// Lightroom Core Rendering Pipeline Specification (Tips 291–335)

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uInputTexture;
uniform vec2 uTexelSize;          // 1.0 / vec2(width, height)

// Sharpening parameters
uniform float uSharpenAmount;     // 0.0 to 1.5 (mapped from 0 to 150)
uniform float uSharpenRadius;     // typically 1.0 (pixels)
uniform float uSharpenDetail;     // 0.0 to 1.0 (Tip 324)
uniform float uSharpenMasking;    // 0.0 to 1.0 (edge masking threshold)

// Noise Reduction parameters (Tip 294)
uniform float uLuminanceNoise;    // 0.0 to 1.0
uniform float uColorNoise;        // 0.0 to 1.0

// Vignette parameters
uniform float uVignetteAmount;    // -1.0 to +1.0 (-100 to +100)
uniform float uVignetteMidpoint;  // 0.0 to 1.0 (default 0.5)
uniform float uVignetteFeather;   // 0.0 to 1.0 (default 0.5)

// Film Grain parameters
uniform float uGrainAmount;       // 0.0 to 1.0 (0 to 100)
uniform float uGrainSize;         // 1.0 to 4.0
uniform float uGrainRoughness;    // 0.0 to 1.0 (Tip 307)
uniform float uTimeSeed;          // Seed for noise variability

// ProPhoto luminance calculation
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
    vec3 color = centerColor;

    // -------------------------------------------------------------------------
    // 0. Edge-Preserving Bilateral Filter for Noise Reduction (Tip 294 & 314)
    // Runs before sharpening: First chroma noise reduction, then luma noise reduction.
    // -------------------------------------------------------------------------
    if (uLuminanceNoise > 0.001 || uColorNoise > 0.001) {
        float centerLuma = getProPhotoLuma(centerColor);
        float lumaSigma = max(uLuminanceNoise * 0.25, 0.001);
        float colorSigma = max(uColorNoise * 0.35, 0.001);

        vec3 accumColor = centerColor;
        float totalWeight = 1.0;

        vec2 offsets[8] = vec2[8](
            vec2(-1.0, -1.0), vec2(0.0, -1.0), vec2(1.0, -1.0),
            vec2(-1.0,  0.0),                  vec2(1.0,  0.0),
            vec2(-1.0,  1.0), vec2(0.0,  1.0), vec2(1.0,  1.0)
        );

        for (int i = 0; i < 8; i++) {
            vec2 sampleCoord = vTexCoord + offsets[i] * uTexelSize * 1.5;
            vec3 neighbor = texture(uInputTexture, sampleCoord).rgb;
            float neighborLuma = getProPhotoLuma(neighbor);

            // Photometric range distance
            float dLuma = abs(neighborLuma - centerLuma);
            float dColor = distance(neighbor, centerColor);

            float wLuma = exp(-(dLuma * dLuma) / (2.0 * lumaSigma * lumaSigma));
            float wColor = exp(-(dColor * dColor) / (2.0 * colorSigma * colorSigma));
            float w = mix(1.0, wLuma, step(0.001, uLuminanceNoise)) * mix(1.0, wColor, step(0.001, uColorNoise));

            accumColor += neighbor * w;
            totalWeight += w;
        }

        color = accumColor / totalWeight;
    }

    // -------------------------------------------------------------------------
    // 1. Unsharp Mask Sharpening with Edge Masking & Detail Control (Tips 44, 324)
    // -------------------------------------------------------------------------
    if (uSharpenAmount > 0.001) {
        vec2 r = uTexelSize * uSharpenRadius;
        
        // 9-tap Gaussian blur approximation
        vec3 tapTL = texture(uInputTexture, vTexCoord + vec2(-r.x, -r.y)).rgb;
        vec3 tapTC = texture(uInputTexture, vTexCoord + vec2( 0.0, -r.y)).rgb;
        vec3 tapTR = texture(uInputTexture, vTexCoord + vec2( r.x, -r.y)).rgb;
        vec3 tapML = texture(uInputTexture, vTexCoord + vec2(-r.x,  0.0)).rgb;
        vec3 tapMR = texture(uInputTexture, vTexCoord + vec2( r.x,  0.0)).rgb;
        vec3 tapBL = texture(uInputTexture, vTexCoord + vec2(-r.x,  r.y)).rgb;
        vec3 tapBC = texture(uInputTexture, vTexCoord + vec2( 0.0,  r.y)).rgb;
        vec3 tapBR = texture(uInputTexture, vTexCoord + vec2( r.x,  r.y)).rgb;

        vec3 blurred = (tapTL * 0.0625 + tapTC * 0.125 + tapTR * 0.0625 +
                        tapML * 0.125  + color * 0.25     + tapMR * 0.125 +
                        tapBL * 0.0625 + tapBC * 0.125 + tapBR * 0.0625);

        vec3 highPass = color - blurred;
        float edgeStrength = length(highPass);

        // Edge Masking: Masking slider suppresses sharpening on smooth regions
        float edgeMask = 1.0;
        if (uSharpenMasking > 0.001) {
            float threshold = uSharpenMasking * 0.05;
            edgeMask = smoothstep(threshold * 0.5, threshold * 1.5, edgeStrength);
        }

        // Tip 324: Detail slider enhances high-frequency micro textures
        float detailGain = 1.0 + uSharpenDetail * 0.50;
        color += highPass * (uSharpenAmount * edgeMask * detailGain);
    }

    // -------------------------------------------------------------------------
    // 2. Cosmetic Vignette Falloff with Highlight Priority (Tip 306)
    // -------------------------------------------------------------------------
    if (abs(uVignetteAmount) > 0.001) {
        vec2 centeredCoord = (vTexCoord - vec2(0.5, 0.5));
        float r = length(centeredCoord) * 1.41421356;

        float halfFeather = max(uVignetteFeather * 0.45, 0.05);
        float inner = clamp(uVignetteMidpoint - halfFeather, 0.0, 0.95);
        float outer = clamp(uVignetteMidpoint + halfFeather, inner + 0.05, 1.414);

        float falloff = smoothstep(inner, outer, r);
        float gain = 1.0 - uVignetteAmount * falloff;

        // Tip 306: Highlight priority preservation so specular lights don't crush to gray
        float luma = getProPhotoLuma(color);
        float highlightPreservation = 1.0 - smoothstep(0.75, 1.0, luma) * 0.5;
        color *= max(mix(1.0, gain, highlightPreservation), 0.0);
    }

    // -------------------------------------------------------------------------
    // 3. Procedural Multi-Octave Film Grain (Tip 307)
    // -------------------------------------------------------------------------
    if (uGrainAmount > 0.001) {
        float luma = getProPhotoLuma(color);
        float grainLumaMod = 1.0 - smoothstep(0.4, 1.2, luma);
        
        vec2 grainCoord = (vTexCoord / max(uTexelSize, vec2(0.0001))) / max(uGrainSize, 0.5);
        grainCoord += vec2(uTimeSeed * 17.13, uTimeSeed * 31.41);

        // Tip 307: Two octaves mixed by uGrainRoughness
        float noiseOctave1 = hash21(floor(grainCoord)) * 2.0 - 1.0;
        float noiseOctave2 = hash21(floor(grainCoord * 2.13)) * 2.0 - 1.0;
        float noise = mix(noiseOctave1, noiseOctave2, uGrainRoughness);

        vec3 grainOffset = vec3(noise * uGrainAmount * 0.15 * grainLumaMod);
        color += grainOffset;
    }

    fragColor = vec4(color, 1.0);
}
