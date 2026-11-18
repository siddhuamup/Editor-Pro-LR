#version 300 es
precision highp float;

// Stage 9: Geometry Transform, Crop, Expand Checkerboard & Color Management (lin2srgb.fsh)

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uInputTexture;
uniform int uTargetGamut;          // 0 = sRGB (default), 1 = Display P3
uniform int uAltClippingMode;      // 0 = Normal, 1 = Whites/Exposure, 2 = Blacks, 3 = Sharpen Masking
uniform float uSharpenMasking;     // 0.0 to 1.0

// Geometry & Crop uniforms (Top-down normalized coordinate space)
uniform vec2 uViewportSize;        // (viewportWidth, viewportHeight) in pixels
uniform vec4 uDisplayRect;         // (left, top, width, height) in pixels
uniform vec4 uCropRect;            // (left, top, right, bottom) normalized
uniform int uIsCropActive;         // 1 = live Crop/Geometry edit view, 0 = committed cropped view
uniform int uIsExpandEnabled;      // 1 = render checkerboard outside image bounds
uniform vec2 uFlip;                // (+1/-1, +1/-1)
uniform int uRotationSteps;        // 0, 1 (90° CW), 2 (180°), 3 (270° CW)
uniform float uTotalAngleRad;      // radians(straightenAngle + geometryRotate)
uniform vec2 uPerspective;         // (verticalPerspective/100, horizontalPerspective/100)
uniform float uDistortion;         // distortion/100
uniform float uGeoAspect;          // geometryAspect/100
uniform float uGeoScale;           // (geometryScale/100) * constrainedScale
uniform vec2 uGeoOffset;           // (geometryXOffset/100, geometryYOffset/100)
uniform float uImageAspect;        // effectiveWidth / effectiveHeight after 90°/270° rotation
uniform float uBlurAmount;         // 0.0..1.0 Lens Blur / Adaptive Blur Background
uniform float uBlurBoost;          // 0.0..1.0 Bokeh highlights boost

// Matrix: ProPhoto RGB Linear to sRGB Linear
const mat3 M_proPhoto2sRGB = mat3(
    vec3( 2.03420482, -0.22895313, -0.00846069),
    vec3(-0.72726743,  1.23175691, -0.15334315),
    vec3(-0.30693739, -0.00280378,  1.16180384)
);

// Matrix: Melissa RGB (ProPhoto linear) to Display P3 Linear
const mat3 M_proPhoto2DisplayP3 = mat3(
    vec3( 1.345943, -0.544587,  0.000000),
    vec3(-0.255608,  1.508167, -0.020584),
    vec3(-0.051112,  0.020536,  1.211812)
);

vec3 lin2srgb(vec3 cl) {
    cl = clamp(cl, 0.0, 1.0);
    return mix(
        12.92 * cl,
        1.055 * pow(cl, vec3(0.41666667)) - 0.055,
        step(0.0031308, cl)
    );
}

vec3 checkerboardColor(vec2 screenPx) {
    vec2 cell = floor(screenPx / 16.0);
    float odd = mod(cell.x + cell.y, 2.0);
    return mix(vec3(0.11, 0.11, 0.12), vec3(0.165, 0.165, 0.18), odd);
}

void main() {
    vec2 screenPx = vec2(vTexCoord.x * uViewportSize.x, (1.0 - vTexCoord.y) * uViewportSize.y);

    // Map screen pixel into DisplayRect top-down UV [0..1]
    vec2 localP = vec2(
        (screenPx.x - uDisplayRect.x) / max(uDisplayRect.z, 1.0),
        (screenPx.y - uDisplayRect.y) / max(uDisplayRect.w, 1.0)
    );

    // When NOT in Crop editing mode, pixels outside the committed cropped DisplayRect are matte background
    if (uIsCropActive == 0) {
        if (localP.x < 0.0 || localP.x > 1.0 || localP.y < 0.0 || localP.y > 1.0) {
            fragColor = vec4(0.07, 0.07, 0.07, 1.0);
            return;
        }
        // Remap [0..1] into committed crop window
        localP = vec2(
            mix(uCropRect.x, uCropRect.z, localP.x),
            mix(uCropRect.y, uCropRect.w, localP.y)
        );
    }

    // Center around (0, 0)
    vec2 c = localP - vec2(0.5, 0.5);

    // 1. Offset X / Y
    c -= uGeoOffset * 0.5;

    // 2. Uniform Scale (manual Scale * Constrain Crop auto-scale)
    float safeScale = max(uGeoScale, 0.1);
    c /= safeScale;

    // 3. Aspect stretch (-100..+100)
    float stretchX = pow(2.0, uGeoAspect * 0.5);
    c.x *= stretchX;
    c.y /= stretchX;

    // 4. Vertical & Horizontal Perspective Keystone (Projective Homography)
    float vPersp = uPerspective.x * 0.45;
    float hPersp = uPerspective.y * 0.45;
    float denom = 1.0 + vPersp * c.y + hPersp * c.x;
    if (denom <= 0.08) {
        vec3 bg = (uIsExpandEnabled == 1) ? checkerboardColor(screenPx) : vec3(0.07, 0.07, 0.07);
        fragColor = vec4(bg, 1.0);
        return;
    }
    c /= denom;

    // 5. Continuous Rotation (Straighten + Geometry Rotate) in aspect-corrected Euclidean space
    if (abs(uTotalAngleRad) > 0.00001) {
        float cosA = cos(uTotalAngleRad);
        float sinA = sin(uTotalAngleRad);
        vec2 aspectC = vec2(c.x * uImageAspect, c.y);
        vec2 rotC = vec2(
            aspectC.x * cosA + aspectC.y * sinA,
            -aspectC.x * sinA + aspectC.y * cosA
        );
        c = vec2(rotC.x / max(uImageAspect, 0.05), rotC.y);
    }

    // 6. Radial Lens Distortion (Barrel / Pincushion)
    if (abs(uDistortion) > 0.0001) {
        vec2 d = vec2(c.x * uImageAspect, c.y);
        float r2 = dot(d, d);
        float factor = 1.0 + uDistortion * 0.35 * r2;
        c *= factor;
    }

    // 7. Flip Horizontal & Vertical
    c *= uFlip;

    // 8. Discrete 90-degree Clockwise Rotation Steps (0, 90, 180, 270)
    if (uRotationSteps == 1) {
        c = vec2(c.y, -c.x);
    } else if (uRotationSteps == 2) {
        c = vec2(-c.x, -c.y);
    } else if (uRotationSteps == 3) {
        c = vec2(-c.y, c.x);
    }

    vec2 srcTopDown = c + vec2(0.5, 0.5);

    // Out-of-bounds check (rotated corners, perspective wedges, or Expand canvas area)
    if (srcTopDown.x < 0.0 || srcTopDown.x > 1.0 || srcTopDown.y < 0.0 || srcTopDown.y > 1.0) {
        vec3 bg = (uIsExpandEnabled == 1) ? checkerboardColor(screenPx) : vec3(0.07, 0.07, 0.07);
        fragColor = vec4(bg, 1.0);
        return;
    }

    // Convert top-down UV back to OpenGL FBO texture UV (y=1 top, y=0 bottom)
    vec2 sampleUV = vec2(srcTopDown.x, 1.0 - srcTopDown.y);
    vec3 proPhotoLinear = texture(uInputTexture, sampleUV).rgb;

    // Real-Time Subject-Preserving Radial Bokeh Blur (Adaptive: Blur Background & Lens Blur)
    if (uBlurAmount > 0.005) {
        vec2 depthVec = vec2((srcTopDown.x - 0.5) * uImageAspect, srcTopDown.y - 0.45);
        float dist = length(depthVec);
        float blurMask = smoothstep(0.20, 0.58, dist);
        float radius = uBlurAmount * blurMask * 0.018;
        if (radius > 0.0004) {
            vec3 acc = proPhotoLinear;
            float wSum = 1.0;
            const vec2 taps[12] = vec2[12](
                vec2( 1.0,  0.0), vec2(-1.0,  0.0), vec2( 0.0,  1.0), vec2( 0.0, -1.0),
                vec2( 0.707,  0.707), vec2(-0.707,  0.707), vec2( 0.707, -0.707), vec2(-0.707, -0.707),
                vec2( 0.5,  0.288), vec2(-0.5,  0.288), vec2( 0.5, -0.288), vec2(-0.5, -0.288)
            );
            vec2 texScale = vec2(radius / max(uImageAspect, 0.25), radius);
            for (int i = 0; i < 12; i++) {
                vec2 uvTap = clamp(sampleUV + taps[i] * texScale, vec2(0.001), vec2(0.999));
                vec3 s = texture(uInputTexture, uvTap).rgb;
                // Bokeh highlight boost emphasizes bright specular circles
                float lumaS = max(s.r, max(s.g, s.b));
                float w = 1.0 + uBlurBoost * smoothstep(0.65, 1.2, lumaS) * 1.5;
                acc += s * w;
                wSum += w;
            }
            proPhotoLinear = acc / wSum;
        }
    }

    // Color Management: ProPhoto Linear -> Target Display Gamut -> sRGB EOTF
    vec3 displayLinear = (uTargetGamut == 1)
        ? (M_proPhoto2DisplayP3 * proPhotoLinear)
        : (M_proPhoto2sRGB * proPhotoLinear);
    vec3 finalDisplayColor = lin2srgb(displayLinear);

    // Alt-Clipping Overlay Preview Mode
    if (uAltClippingMode == 1) {
        bool clipR = displayLinear.r >= 1.0 || finalDisplayColor.r >= 1.0;
        bool clipG = displayLinear.g >= 1.0 || finalDisplayColor.g >= 1.0;
        bool clipB = displayLinear.b >= 1.0 || finalDisplayColor.b >= 1.0;
        if (clipR && clipG && clipB) {
            fragColor = vec4(1.0, 1.0, 1.0, 1.0);
        } else if (clipR || clipG || clipB) {
            fragColor = vec4(float(clipR), float(clipG), float(clipB), 1.0);
        } else {
            fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        }
        return;
    } else if (uAltClippingMode == 2) {
        bool crush = (displayLinear.r <= 0.001 || displayLinear.g <= 0.001 || displayLinear.b <= 0.001);
        fragColor = crush ? vec4(0.0, 0.33, 1.0, 1.0) : vec4(1.0, 1.0, 1.0, 1.0);
        return;
    } else if (uAltClippingMode == 3) {
        float luma = 0.2126 * finalDisplayColor.r + 0.7152 * finalDisplayColor.g + 0.0722 * finalDisplayColor.b;
        float edge = length(vec2(dFdx(luma), dFdy(luma))) * 15.0;
        float maskVal = step(max(uSharpenMasking * 0.25, 0.005), edge);
        fragColor = vec4(vec3(maskVal), 1.0);
        return;
    }

    fragColor = vec4(finalDisplayColor, 1.0);
}
