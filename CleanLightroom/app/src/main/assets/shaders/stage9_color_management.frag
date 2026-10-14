#version 300 es
precision highp float;

// Stage 9: Color Management & Output Display (lin2srgb.fsh)
// Lightroom Core Rendering Pipeline Specification (media_1791959032261.pdf, Page 7)

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uInputTexture;
uniform int uTargetGamut; // 0 = sRGB (default), 1 = Display P3

// Matrix: ProPhoto RGB Linear to sRGB Linear (row sum = 1.0, preserving neutral gray)
const mat3 M_proPhoto2sRGB = mat3(
    vec3( 2.03420482, -0.22895313, -0.00846069), // Col 0
    vec3(-0.72726743,  1.23175691, -0.15334315), // Col 1
    vec3(-0.30693739, -0.00280378,  1.16180384)  // Col 2
);

// Matrix: ProPhoto RGB Linear to Display P3 Linear (row sum = 1.0, preserving neutral gray)
const mat3 M_proPhoto2DisplayP3 = mat3(
    vec3( 1.63219660, -0.15371161,  0.01039147), // Col 0
    vec3(-0.37947633,  1.16671469, -0.06281092), // Col 1
    vec3(-0.25272028, -0.01300308,  1.05241945)  // Col 2
);

// Electro-Optical Transfer Function (lin2srgb.fsh)
// Applies standard sRGB / Display P3 transfer curve (gamma ~2.4)
vec3 lin2srgb(vec3 cl) {
    cl = clamp(cl, 0.0, 1.0);
    return mix(
        12.92 * cl,
        1.055 * pow(cl, vec3(0.41666667)) - 0.055,
        step(0.0031308, cl)
    );
}

void main() {
    vec3 proPhotoLinear = texture(uInputTexture, vTexCoord).rgb;

    // 1. Transform from wide-gamut ProPhoto RGB linear to target display gamut
    vec3 displayLinear;
    if (uTargetGamut == 1) {
        displayLinear = M_proPhoto2DisplayP3 * proPhotoLinear;
    } else {
        displayLinear = M_proPhoto2sRGB * proPhotoLinear;
    }

    // 2. Apply EOTF / Gamma curve (lin2srgb.fsh)
    vec3 finalDisplayColor = lin2srgb(displayLinear);

    fragColor = vec4(finalDisplayColor, 1.0);
}
