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

// Matrix: Melissa RGB (ProPhoto linear) to Display P3 Linear (M_melissa2P3, PDF Page 2)
// [R G B]_P3_linear = [ 1.345943 -0.255608 -0.051112 ]
//                     [-0.544587  1.508167  0.020536 ]
//                     [ 0.000000 -0.020584  1.211812 ] * [R G B]_Melissa
const mat3 M_proPhoto2DisplayP3 = mat3(
    vec3( 1.345943, -0.544587,  0.000000), // Col 0
    vec3(-0.255608,  1.508167, -0.020584), // Col 1
    vec3(-0.051112,  0.020536,  1.211812)  // Col 2
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
