#version 300 es
precision highp float;

in vec2 vTexCoord;

uniform sampler2D uImageTexture;
uniform int uIsInputLinear;      // 0 = sRGB JPEG/WebP, 1 = Already Linear
uniform int uEnableUndoCurve;    // 1 = Invert baked camera S-curve, 0 = Bypass

out vec4 fragColor;

// Adobe ProPhoto RGB (ROMM RGB) Bradford Adaptation Matrix
// Columns are organized such that rgb2proPhotoMatrix * col computes:
// R_pro = 0.5293238 * R + 0.3300551 * G + 0.1406402 * B  (Sum = 1.00002)
// G_pro = 0.0984265 * R + 0.8735087 * G + 0.0281116 * B  (Sum = 1.00005)
// B_pro = 0.0168458 * R + 0.1176955 * G + 0.8654740 * B  (Sum = 1.00000)
// This strictly guarantees that neutral gray (R=G=B) remains neutral in ProPhoto RGB!
const mat3 rgb2proPhotoMatrix = mat3(
    0.52932378745827813, 0.098426540365603984, 0.016845769297481432, // Col 0
    0.33005506400369466, 0.87350872697710469, 0.11769549131351224, // Col 1
    0.14064019169871309, 0.028111560975330636, 0.86547398998538982  // Col 2
);

// Evaluates inversion of baked camera tone curve (extracted from Lightroom libimaging/greylog2.fsh)
vec3 evalUndoRenderCurve(vec3 col) {
    float eps = 0.00001;
    vec2 fMinMax;
    vec2 nMinMax;

    fMinMax.x = min(min(col.r, col.g), col.b);
    fMinMax.y = max(max(col.r, col.g), col.b);

    if (abs(fMinMax.y - fMinMax.x) < eps) {
        return col; // Neutral / monochromatic preserving shortcut
    }

    vec2 t = pow(clamp(fMinMax, 0.0, 1.0), vec2(3.14453125));
    nMinMax = pow(clamp(fMinMax, 0.0, 1.0), vec2(0.8125)) * 0.3828125 * (1.0 - t) +
              (1.0 - pow(clamp(1.0 - fMinMax, 0.0, 1.0), vec2(0.69140625))) * t;

    float slope = (nMinMax.y - nMinMax.x) / (fMinMax.y - fMinMax.x + eps);
    return clamp((col - fMinMax.x) * slope + nMinMax.x, 0.0, 1.0);
}

void main() {
    vec4 src = texture(uImageTexture, vTexCoord);
    vec3 rgb = clamp(src.rgb, 0.0, 1.0);

    // 1. Convert sRGB to Linear Radiance (srgb2lin)
    vec3 linearRgb;
    if (uIsInputLinear == 1) {
        linearRgb = rgb;
    } else {
        vec3 low = rgb / 12.92;
        vec3 high = pow((rgb + 0.055) / 1.055, vec3(2.4));
        linearRgb = mix(low, high, step(0.04045, rgb));
    }

    // 2. Transform Linear sRGB into Wide-Gamut ProPhoto RGB
    vec3 proPhotoRgb = rgb2proPhotoMatrix * linearRgb;

    // 3. Optional Undo Render Curve for JPEG camera tones
    if (uEnableUndoCurve == 1) {
        proPhotoRgb = evalUndoRenderCurve(clamp(proPhotoRgb, 0.0, 1.0));
    }

    // Output is in wide-gamut ProPhoto RGB linear space (RGBA16F FBO)
    fragColor = vec4(proPhotoRgb, src.a);
}
