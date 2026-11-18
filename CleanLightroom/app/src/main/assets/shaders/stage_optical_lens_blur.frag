#version 300 es
precision highp float;

// =============================================================================
// CleanLR 5-Stage Optical Lens Blur & Compositing Shader (OpenGL ES 3.0 / 3.2)
// Tuned for Fine Hair Strands (BiRefNet 1024x1024 + Foreground Decontamination)
// & Ground-Plane Continuity (Depth Anything V2 16-bit RG Bilinear + Focal Zone).
//
// Stage 1: Clean-Plate Background Reconstruction (LaMa) + Decontaminated Foreground Hair
// Stage 2: Physical Depth-Guided Circle of Confusion CoC(Z) = clamp(K*max(|Z-S|-dof,0)/Z, 0, Rmax)
// Stage 3: Linear Light Space + Inverse Tone-Mapped HDR Specular Bokeh Boost (> 0.90)
// Stage 4: 44-Tap Golden-Angle Spiral Gather + 7 Aperture Blades + Cats-Eye + Hair-Safe Partial Occlusion
// Stage 5: Calibrated Subject Sensor Grain Re-Injection + ACES Filmic Roll-Off
// =============================================================================

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uOriginalTex;        // Linear ProPhoto current pipeline buffer (sharp subject & floor)
uniform sampler2D uCleanPlateTex;      // Stage 1: Subject-free LaMa Clean Background Plate (sRGB)
uniform sampler2D uAlphaMatteTex;      // Stage 1: Continuous 1024px BiRefNet Hair AlphaMatte (0..1)
uniform sampler2D uDepthMapTex;        // Stage 2: 16-bit DepthAnythingV2 DepthMap packed in RG (GL_NEAREST)
uniform sampler2D uDecontaminatedFgTex;// Stage 1: Foreground-color-decontaminated hair/edge plate (sRGB)

uniform int uHasOpticalMaps;           // 1 = full BiRefNet+Depth+LaMa maps loaded, 0 = ground-plane fallback
uniform float uBlurAmount;             // 0.0..1.0 (aperture strength K)
uniform float uFocalDepthS;            // 0.05..0.95 (focal plane depth S sampled at subject/feet)
uniform float uBokehBoost;             // 0.0..1.0 (HDR specular highlight bokeh disc boost)
uniform int uBokehShape;               // 0=Circle, 1=Bubble, 2=5-blade, 3=7-blade, 4=9-blade, 5=Oval, 6=Cat Eye
uniform float uNoiseSigma;             // Measured high-frequency subject sensor grain sigma
uniform float uImageAspect;            // width / height
uniform vec2 uDepthTexSize;            // Pixel dimensions of uDepthMapTex for artifact-free RG16 bilinear decode

const float PI = 3.14159265359;
const float GOLDEN_ANGLE = 2.39996323;

// Exact sRGB -> Linear EOTF (Stage 3)
vec3 srgb2lin(vec3 c) {
    c = clamp(c, 0.0, 1.0);
    return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(0.04045, c));
}

// sRGB -> ProPhoto Linear matrix so Clean Plate & Decontaminated FG match CleanLR pipeline space
const mat3 M_sRGB2ProPhoto = mat3(
    vec3(0.529317, 0.098368, 0.016879),
    vec3(0.330092, 0.873465, 0.117663),
    vec3(0.140588, 0.028169, 0.865457)
);

// Decode single RG-packed 16-bit texel without byte wrap-around error
float decodeRG16Texel(vec2 uv) {
    vec2 rg = texture(uDepthMapTex, uv).rg;
    return clamp((rg.r * 255.0 * 256.0 + rg.g * 255.0) / 65535.0, 0.05, 1.0);
}

// Manual bilinear decode of 16-bit RG DepthMap (eliminates GL_LINEAR 8-bit carry contour bands!)
// Expects top-down Android Bitmap UV (`mapUv = vec2(uv.x, 1.0 - uv.y)`).
float sampleDepth16(vec2 mapUv) {
    if (uHasOpticalMaps == 1) {
        vec2 sz = max(uDepthTexSize, vec2(64.0));
        vec2 fCoord = mapUv * sz - 0.5;
        vec2 f = fract(fCoord);
        vec2 baseUv = (floor(fCoord) + 0.5) / sz;
        vec2 dx = vec2(1.0 / sz.x, 0.0);
        vec2 dy = vec2(0.0, 1.0 / sz.y);

        float d00 = decodeRG16Texel(clamp(baseUv, vec2(0.001), vec2(0.999)));
        float d10 = decodeRG16Texel(clamp(baseUv + dx, vec2(0.001), vec2(0.999)));
        float d01 = decodeRG16Texel(clamp(baseUv + dy, vec2(0.001), vec2(0.999)));
        float d11 = decodeRG16Texel(clamp(baseUv + dx + dy, vec2(0.001), vec2(0.999)));
        return mix(mix(d00, d10, f.x), mix(d01, d11, f.x), f.y);
    }
    // Ground-plane aware fallback depth (mapUv.y=0 top, mapUv.y=1 bottom floor)
    float centerDist = length(vec2((mapUv.x - 0.5) * uImageAspect, mapUv.y - 0.48));
    float subjMask = 1.0 - smoothstep(0.20, 0.44, centerDist);
    float groundZ = mix(0.92, uFocalDepthS, smoothstep(0.10, 0.86, mapUv.y));
    return mix(groundZ, uFocalDepthS, subjMask);
}

// Expects top-down Android Bitmap UV (`mapUv = vec2(uv.x, 1.0 - uv.y)`).
float sampleAlpha(vec2 mapUv) {
    if (uHasOpticalMaps == 1) {
        return clamp(texture(uAlphaMatteTex, mapUv).r, 0.0, 1.0);
    }
    float centerDist = length(vec2((mapUv.x - 0.5) * uImageAspect, mapUv.y - 0.48));
    return 1.0 - smoothstep(0.20, 0.42, centerDist);
}

// Stage 2: Physical Circle of Confusion with Optical Depth-of-Field Focal Tolerance
// Keeps the floor/ground directly beneath the subject's feet 100% razor sharp!
float computeCoC(float z) {
    float K = uBlurAmount * 0.034;
    float maxR = 0.038;
    float dofDeadZone = 0.032; // Physical focal plane thickness around subject & feet contact patch
    float dz = max(abs(z - uFocalDepthS) - dofDeadZone, 0.0);
    return clamp(K * dz / max(z, 0.05), 0.0, maxR);
}

// Stage 4: Custom Aperture Blade & Cat's-Eye Shaping Weight
float apertureShapeWeight(vec2 diskPt, float rNorm, vec2 screenCentered) {
    if (uBokehShape == 0) {
        return 1.0;
    } else if (uBokehShape == 1) {
        return mix(0.35, 1.85, smoothstep(0.55, 0.98, rNorm));
    } else if (uBokehShape == 2 || uBokehShape == 3 || uBokehShape == 4) {
        float blades = (uBokehShape == 2) ? 5.0 : ((uBokehShape == 3) ? 7.0 : 9.0);
        float angle = atan(diskPt.y, diskPt.x);
        float sector = PI / blades;
        float polyR = cos(sector) / max(cos(mod(angle + sector, 2.0 * sector) - sector), 0.25);
        return 1.0 - smoothstep(polyR - 0.08, polyR + 0.04, rNorm);
    } else if (uBokehShape == 6) {
        vec2 edgeDir = screenCentered * 1.15;
        float vignetteClip = length(diskPt - edgeDir * 0.42);
        return 1.0 - smoothstep(0.72, 0.95, vignetteClip);
    }
    return 1.0;
}

// Stage 5: Procedural zero-mean Gaussian-like sensor grain hash
float sensorNoise(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    float u1 = fract((p3.x + p3.y) * p3.z);
    float u2 = fract((p3.y + p3.z) * p3.x);
    return (u1 + u2 - 1.0);
}

// Stage 5: ACES Filmic highlight roll-off (preserves creamy HDR bokeh discs)
vec3 acesHighlightRollOff(vec3 x) {
    vec3 soft = (x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14);
    float peak = max(x.r, max(x.g, x.b));
    return mix(x, soft, smoothstep(0.92, 1.8, peak));
}

void main() {
    vec2 uv = vTexCoord;
    vec3 origLinear = texture(uOriginalTex, uv).rgb;

    if (uBlurAmount <= 0.002) {
        fragColor = vec4(origLinear, 1.0);
        return;
    }

    // Align Android top-down Bitmaps (mapUv.y=0 top) with GL FBO (uv.y=1 top)
    vec2 mapUv = vec2(uv.x, 1.0 - uv.y);
    float alpha = sampleAlpha(mapUv);
    float depthZ = sampleDepth16(mapUv);
    float centerCoC = computeCoC(depthZ);

    // Hair-strand / silhouette boundary indicator (peaks at A=0.5, 0 inside solid core & open background)
    float edgeBand = alpha * (1.0 - alpha) * 4.0;

    // Fast path: 100% solid in-focus subject core OR 100% in-focus ground beneath feet -> zero unnecessary blur!
    if (alpha > 0.985 && centerCoC < 0.0005) {
        fragColor = vec4(origLinear, 1.0);
        return;
    }

    // ─── STAGE 1 & 4: Golden-Angle Spiral Gather over Clean Background Plate ───
    vec3 bgAccum = vec3(0.0);
    float weightSum = 0.0;
    float edgeBgOcclusion = 0.0;

    vec2 screenCentered = (uv - vec2(0.5)) * vec2(uImageAspect, 1.0);
    vec2 anamorphicScale = (uBokehShape == 5) ? vec2(0.62, 1.32) : vec2(1.0, 1.0);

    // Only enforce a soft minimum gather radius inside the hair/silhouette fringe so Clean Plate bokeh
    // stays continuous behind semi-transparent hair, while keeping in-focus ground (centerCoC==0) 100% sharp!
    float fringeMinGather = uBlurAmount * 0.014 * smoothstep(0.04, 0.50, edgeBand);
    float gatherRadius = max(centerCoC, fringeMinGather);

    const int TAPS = 44;
    if (gatherRadius > 0.0004) {
        for (int i = 0; i < TAPS; i++) {
            float fi = float(i) + 0.5;
            float rNorm = sqrt(fi / float(TAPS));
            float theta = fi * GOLDEN_ANGLE;
            vec2 diskPt = vec2(cos(theta), sin(theta)) * rNorm;

            float shapeW = apertureShapeWeight(diskPt, rNorm, screenCentered);
            if (shapeW <= 0.01) continue;

            vec2 offset = diskPt * anamorphicScale * gatherRadius;
            offset.x /= max(uImageAspect, 0.25);

            vec2 tapUv = clamp(uv + offset, vec2(0.002), vec2(0.998));
            vec2 tapMapUv = vec2(tapUv.x, 1.0 - tapUv.y);

            float tapZ = sampleDepth16(tapMapUv);
            float tapCoC = computeCoC(tapZ);

            // Scatter-as-gather depth weighting: prevents sharp ground pixels from bleeding into far bokeh
            float depthWeight = smoothstep(0.0, max(gatherRadius, 0.001), tapCoC + 0.0025);
            float w = shapeW * mix(0.18, 1.0, depthWeight);

            // Stage 1: Sample Clean Background Plate (100% subject-free, zero foreground color bleed!)
            vec3 sampleLin;
            if (uHasOpticalMaps == 1) {
                vec3 srgbClean = texture(uCleanPlateTex, tapMapUv).rgb;
                sampleLin = M_sRGB2ProPhoto * srgb2lin(srgbClean);
            } else {
                sampleLin = texture(uOriginalTex, tapUv).rgb;
            }

            // Stage 3: Linear Light Space HDR Specular Bokeh Boost (> 0.90 luma -> creamy bokeh balls)
            float lum = dot(sampleLin, vec3(0.2126, 0.7152, 0.0722));
            float hdrSpecularBoost = 1.0 + uBokehBoost * pow(max(lum - 0.88, 0.0) / 0.12, 1.35) * 4.2;
            vec3 boosted = sampleLin * hdrSpecularBoost;

            bgAccum += boosted * w;
            weightSum += w;

            // Track far out-of-focus background bokeh adjacent to high depth discontinuity for Partial Occlusion
            float tapAlpha = sampleAlpha(tapMapUv);
            float depthJump = abs(tapZ - depthZ);
            if (tapAlpha < 0.15 && tapCoC > 0.009 && depthJump > 0.12) {
                edgeBgOcclusion += shapeW * (1.0 - rNorm * 0.45);
            }
        }
    }

    vec3 blurredBg = (weightSum > 0.001) ? (bgAccum / weightSum) : origLinear;

    // ─── STAGE 5: Procedural Sensor Grain Re-Injection in Linear Space ───
    float blurRatio = clamp(gatherRadius / 0.022, 0.0, 1.0);
    float grain = sensorNoise(gl_FragCoord.xy) * uNoiseSigma * blurRatio * 1.15;
    blurredBg = max(blurredBg + vec3(grain), vec3(0.0));

    // Ground-plane smooth transition: floor right beneath feet (centerCoC==0) stays 100% sharp origLinear!
    float bgBlurMix = smoothstep(0.0005, 0.0055, gatherRadius);
    vec3 finalBg = mix(origLinear, blurredBg, bgBlurMix);

    // ─── STAGE 1 (Hair Decontamination) + STAGE 4 (Hair-Safe Partial Occlusion Edge Wrap) ───
    // Blend Foreground-Decontaminated RGB strictly inside the semi-transparent hair strand zone
    // so fine strands never carry dark or cyan/sky spill halos!
    vec3 fgLinear = origLinear;
    if (uHasOpticalMaps == 1 && edgeBand > 0.02) {
        vec3 decontamLin = M_sRGB2ProPhoto * srgb2lin(texture(uDecontaminatedFgTex, mapUv).rgb);
        // Preserve exposure/grading delta applied upstream in CleanLR pipeline
        fgLinear = mix(origLinear, decontamLin, clamp(edgeBand * 0.85, 0.0, 0.85));
    }

    // 5–9% optical partial-occlusion diffraction wrap ONLY at high depth-discontinuity silhouettes
    // (Attenuated on fine semi-transparent wisps so individual hair strands stay crisp & luminous)
    float solidEdgeFactor = smoothstep(0.35, 0.85, alpha) * (1.0 - smoothstep(0.88, 0.99, alpha));
    float occlusionWrap = clamp((edgeBgOcclusion / float(TAPS)) * 0.16, 0.0, 0.09) * solidEdgeFactor;
    float effectiveFgAlpha = clamp(alpha - occlusionWrap, 0.0, 1.0);

    vec3 composited = mix(finalBg, fgLinear, effectiveFgAlpha);

    // Stage 5: Smooth ACES Filmic highlight roll-off for boosted HDR bokeh discs
    composited = acesHighlightRollOff(composited);

    fragColor = vec4(composited, 1.0);
}
