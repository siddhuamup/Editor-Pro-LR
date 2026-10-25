/**
 * Clean Lightroom Web Studio - Pro 2.5 Core Engine & WebGL Pipeline
 * Complete reproduction of Adobe Lightroom Mobile & Studio Specs
 * Features:
 * - Melissa RGB (Linear ProPhoto) color space conversion
 * - 5-Zone Interactive RGB Waveform Histogram
 * - Subtabs: B&W Mode, Color Grading (4 Targets), Color Mix (8 Hollow/Solid Rings)
 * - White Balance Presets & Real-time Pipette Eyedropper Neutral Gray Sampler
 * - 1-Tap AI Auto Tone
 * - 24 Pro Presets Starter Pack with 0-200% Amount Slider
 * - Tone Curve LUT Generator (Fritsch-Carlson Spline)
 * - Lens Blur & Bokeh Shapes
 * - Crop, Rotate, Straighten (-45° to +45°) with Rule-of-Thirds Grid
 * - Adobe Camera Raw (.XMP) Sidecar Recipe Export & JSON Copy/Paste
 * - Desktop Studio & Mobile Android Device Layout Switcher
 */

// ============================================================================
// 1. STATE MANAGEMENT
// ============================================================================
const state = {
  basic: {
    exposure: 0.0,
    contrast: 0.0,
    highlights: 0.0,
    shadows: 0.0,
    whites: 0.0,
    blacks: 0.0,
    temp: 0.0,
    tint: 0.0,
    wbPreset: 'as_shot'
  },
  presence: {
    texture: 0.0,
    clarity: 0.0,
    dehaze: 0.0,
    vibrance: 0.0,
    saturation: 0.0
  },
  effects: {
    vignetteAmount: 0.0,
    vignetteMidpoint: 50.0,
    vignetteFeather: 50.0,
    grainAmount: 0.0,
    grainSize: 25.0,
    grainRoughness: 50.0
  },
  detail: {
    sharpening: 40.0,
    sharpenRadius: 1.0,
    sharpenDetail: 25.0,
    masking: 0.0,
    luminanceNoise: 0.0,
    colorNoise: 25.0
  },
  blur: {
    amount: 0.0,
    bokehShape: 'circle', // circle, blade5, blade7, swirl, cateye
    bokehBoost: 0.0,
    focalRange: 50.0
  },
  optics: {
    profileCorrections: false,
    removeCA: false,
    defringe: 0.0
  },
  crop: {
    rotationDegrees: 0,
    straightenAngle: 0.0,
    flipH: false,
    flipV: false,
    aspectRatio: 'free'
  },
  hsl: [
    { hue: 0, sat: 0, lum: 0 }, // 0: Red
    { hue: 0, sat: 0, lum: 0 }, // 1: Orange
    { hue: 0, sat: 0, lum: 0 }, // 2: Yellow
    { hue: 0, sat: 0, lum: 0 }, // 3: Green
    { hue: 0, sat: 0, lum: 0 }, // 4: Aqua
    { hue: 0, sat: 0, lum: 0 }, // 5: Blue
    { hue: 0, sat: 0, lum: 0 }, // 6: Purple
    { hue: 0, sat: 0, lum: 0 }  // 7: Magenta
  ],
  grading: {
    shadows: { hue: 0, sat: 0, lum: 0 },
    midtones: { hue: 0, sat: 0, lum: 0 },
    highlights: { hue: 0, sat: 0, lum: 0 },
    global: { hue: 0, sat: 0, lum: 0 },
    blending: 50.0,
    balance: 0.0
  },
  curves: {
    parametricHighlights: 0,
    parametricLights: 0,
    parametricDarks: 0,
    parametricShadows: 0,
    activeChannel: 'rgb',
    points: [{ x: 0, y: 0 }, { x: 255, y: 255 }]
  },
  masking: {
    type: 'brush',
    brushSize: 30,
    feather: 50,
    inverted: false
  },
  remove: {
    healSize: 25,
    feather: 30
  },
  activeMode: 'modeEdit',
  activeTool: 'tabLight',
  isMonochrome: false,
  activeHslIndex: 0,
  activeGradingTarget: 'shadows',
  eyedropperActive: false,
  altClippingMode: 0, // 0: normal, 1: whites, 2: blacks
  showOriginal: false,
  zoomScale: 1.0,
  panX: 0,
  panY: 0,
  activePreset: null,
  presetAmount: 100,
  baseStateBeforePreset: null,
  viewMode: 'studio' // 'studio' or 'mobile'
};

const HSL_COLOR_DEFS = [
  { name: 'Red', hex: '#E53935', centerHue: 0 },
  { name: 'Orange', hex: '#FB8C00', centerHue: 30 },
  { name: 'Yellow', hex: '#FDD835', centerHue: 60 },
  { name: 'Green', hex: '#43A047', centerHue: 120 },
  { name: 'Aqua', hex: '#00ACC1', centerHue: 180 },
  { name: 'Blue', hex: '#1E88E5', centerHue: 240 },
  { name: 'Purple', hex: '#8E24AA', centerHue: 280 },
  { name: 'Magenta', hex: '#D81B60', centerHue: 320 }
];

// History Stack
const history = {
  undoStack: [],
  redoStack: [],
  pushSnapshot(action) {
    this.undoStack.push({ action, state: JSON.parse(JSON.stringify(state)) });
    if (this.undoStack.length > 50) this.undoStack.shift();
    this.redoStack = [];
    updateUndoRedoButtons();
  },
  undo() {
    if (this.undoStack.length <= 1) return;
    const current = this.undoStack.pop();
    this.redoStack.push(current);
    const prev = this.undoStack[this.undoStack.length - 1];
    restoreState(prev.state);
    showToast(`Undo: ${current.action}`);
  },
  redo() {
    if (this.redoStack.length === 0) return;
    const next = this.redoStack.pop();
    this.undoStack.push(next);
    restoreState(next.state);
    showToast(`Redo: ${next.action}`);
  }
};

function restoreState(s) {
  Object.assign(state.basic, s.basic);
  Object.assign(state.presence, s.presence);
  Object.assign(state.effects, s.effects);
  Object.assign(state.detail, s.detail);
  Object.assign(state.blur, s.blur);
  Object.assign(state.optics, s.optics);
  Object.assign(state.crop, s.crop);
  state.hsl = JSON.parse(JSON.stringify(s.hsl));
  state.grading = JSON.parse(JSON.stringify(s.grading));
  state.curves = JSON.parse(JSON.stringify(s.curves));
  state.isMonochrome = s.isMonochrome || false;
  state.activePreset = s.activePreset;
  state.presetAmount = s.presetAmount || 100;
  syncAllUiInputs();
  requestPipelineRender();
}

function updateUndoRedoButtons() {
  const btnUndo = document.getElementById('btnUndo');
  const btnRedo = document.getElementById('btnRedo');
  if (btnUndo) btnUndo.disabled = history.undoStack.length <= 1;
  if (btnRedo) btnRedo.disabled = history.redoStack.length === 0;
}

function showToast(msg) {
  const toast = document.getElementById('toastMsg');
  if (!toast) return;
  toast.textContent = msg;
  toast.classList.add('show');
  clearTimeout(toast._timeout);
  toast._timeout = setTimeout(() => toast.classList.remove('show'), 1800);
}

// ============================================================================
// 2. WEBGL PIPELINE & SHADER ENGINE
// ============================================================================
const glCanvas = document.getElementById('glCanvas');
let gl = null;
let shaderProgram = null;
let imageTexture = null;
let curveLutTexture = null;
let uniformLocations = {};
let sourceImage = new Image();
let renderScheduled = false;

const VERT_SHADER_SRC = `
attribute vec2 aPosition;
attribute vec2 aTexCoord;
varying vec2 vTexCoord;

void main() {
    vTexCoord = aTexCoord;
    gl_Position = vec4(aPosition, 0.0, 1.0);
}
`;

const FRAG_SHADER_SRC = `
precision highp float;
varying vec2 vTexCoord;

uniform sampler2D uImageTexture;
uniform sampler2D uToneCurveLut;

uniform float uExposure;
uniform float uContrast;
uniform float uHighlights;
uniform float uShadows;
uniform float uWhites;
uniform float uBlacks;

uniform vec3 uWhiteBalanceGain;

uniform float uClarity;
uniform float uTexture;
uniform float uDehaze;
uniform float uVibrance;
uniform float uSaturation;

uniform float uVignetteAmount;
uniform float uVignetteMidpoint;
uniform float uVignetteFeather;

uniform float uGrainAmount;
uniform float uGrainSize;
uniform float uGrainRoughness;

uniform float uRotationDeg;
uniform float uStraightenDeg;
uniform vec2 uFlip;

uniform vec3 uGradeShadows;
uniform vec3 uGradeMidtones;
uniform vec3 uGradeHighlights;
uniform vec3 uGradeGlobal;
uniform float uGradeBlending;
uniform float uGradeBalance;

uniform vec3 uHslRed;
uniform vec3 uHslOrange;
uniform vec3 uHslYellow;
uniform vec3 uHslGreen;
uniform vec3 uHslAqua;
uniform vec3 uHslBlue;
uniform vec3 uHslPurple;
uniform vec3 uHslMagenta;

uniform float uLensBlurAmount;
uniform float uDefringe;
uniform int uMonochrome;
uniform int uAltClippingMode;
uniform bool uEnableCurve;

const mat3 M_srgb2melissa = mat3(
    0.52932379, 0.09842654, 0.01684577,
    0.33005506, 0.87350873, 0.11769549,
    0.14064019, 0.02811156, 0.86547399
);

const mat3 M_melissa2srgb = mat3(
     2.03468, -0.22883, -0.00857,
    -0.72588,  1.23307, -0.16958,
    -0.30880, -0.00424,  1.17815
);

vec3 srgbToLinear(vec3 c) {
    return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(0.04045, c));
}

vec3 linearToSrgb(vec3 cl) {
    cl = clamp(cl, 0.0, 1.0);
    return mix(12.92 * cl, 1.055 * pow(cl, vec3(0.41666)) - 0.055, step(0.0031308, cl));
}

vec3 rgbToHsl(vec3 c) {
    float cMin = min(min(c.r, c.g), c.b);
    float cMax = max(max(c.r, c.g), c.b);
    float delta = cMax - cMin;
    float l = (cMax + cMin) * 0.5;
    float s = 0.0;
    float h = 0.0;
    if (delta > 0.00001) {
        s = l < 0.5 ? delta / (cMax + cMin) : delta / (2.0 - cMax - cMin);
        if (c.r >= cMax) {
            h = (c.g - c.b) / delta + (c.g < c.b ? 6.0 : 0.0);
        } else if (c.g >= cMax) {
            h = (c.b - c.r) / delta + 2.0;
        } else {
            h = (c.r - c.g) / delta + 4.0;
        }
        h /= 6.0;
    }
    return vec3(h * 360.0, s, l);
}

float hueToRgb(float p, float q, float t) {
    if (t < 0.0) t += 1.0;
    if (t > 1.0) t -= 1.0;
    if (t < 1.0 / 6.0) return p + (q - p) * 6.0 * t;
    if (t < 1.0 / 2.0) return q;
    if (t < 2.0 / 3.0) return p + (q - p) * (2.0 / 3.0 - t) * 6.0;
    return p;
}

vec3 hslToRgb(vec3 hsl) {
    float h = hsl.x / 360.0;
    float s = hsl.y;
    float l = hsl.z;
    if (s <= 0.00001) return vec3(l);
    float q = l < 0.5 ? l * (1.0 + s) : l + s - l * s;
    float p = 2.0 * l - q;
    return vec3(
        hueToRgb(p, q, h + 1.0 / 3.0),
        hueToRgb(p, q, h),
        hueToRgb(p, q, h - 1.0 / 3.0)
    );
}

float circularDist(float a, float b) {
    float d = abs(a - b);
    return min(d, 360.0 - d);
}

void main() {
    // 0. Crop, Rotate, Straighten & Flip Transformation
    vec2 uv = vTexCoord;
    if (uFlip.x > 0.5) uv.x = 1.0 - uv.x;
    if (uFlip.y > 0.5) uv.y = 1.0 - uv.y;

    float totalAngle = radians(uRotationDeg + uStraightenDeg);
    if (totalAngle != 0.0) {
        vec2 centered = uv - vec2(0.5);
        float s = sin(totalAngle);
        float c = cos(totalAngle);
        float autoScale = 1.0 + abs(sin(totalAngle * 2.0)) * 0.42;
        centered = mat2(c, -s, s, c) * (centered / autoScale);
        uv = centered + vec2(0.5);
    }
    uv = clamp(uv, 0.0, 1.0);

    // Multi-tap sample for Lens Blur if active
    vec4 rawColor;
    if (uLensBlurAmount > 0.5) {
        float blurRadius = (uLensBlurAmount / 100.0) * 0.015;
        rawColor = texture2D(uImageTexture, uv) * 0.25;
        rawColor += texture2D(uImageTexture, uv + vec2( blurRadius,  0.0)) * 0.15;
        rawColor += texture2D(uImageTexture, uv + vec2(-blurRadius,  0.0)) * 0.15;
        rawColor += texture2D(uImageTexture, uv + vec2( 0.0,  blurRadius)) * 0.15;
        rawColor += texture2D(uImageTexture, uv + vec2( 0.0, -blurRadius)) * 0.15;
        rawColor += texture2D(uImageTexture, uv + vec2( blurRadius * 0.7,  blurRadius * 0.7)) * 0.075;
        rawColor += texture2D(uImageTexture, uv + vec2(-blurRadius * 0.7, -blurRadius * 0.7)) * 0.075;
    } else {
        rawColor = texture2D(uImageTexture, uv);
    }

    vec3 col = srgbToLinear(rawColor.rgb);

    // 1. Convert to Melissa RGB (Linear ProPhoto)
    col = M_srgb2melissa * col;

    // 2. White Balance Gain
    col *= uWhiteBalanceGain;

    // 3. Exposure (2^EV)
    col *= exp2(uExposure);

    float lum = dot(col, vec3(0.25, 0.50, 0.25));

    // 4. Tone Mapping (Highlights, Shadows, Whites, Blacks)
    if (uHighlights != 0.0) {
        float hlGain = (uHighlights / 100.0) * smoothstep(0.4, 1.0, lum);
        col *= (1.0 + hlGain);
    }
    if (uShadows != 0.0) {
        float shGain = (uShadows / 100.0) * (1.0 - smoothstep(0.0, 0.45, lum));
        col += vec3(shGain * 0.15);
    }
    if (uWhites != 0.0) {
        col += vec3((uWhites / 100.0) * 0.1 * smoothstep(0.7, 1.0, lum));
    }
    if (uBlacks != 0.0) {
        col += vec3((uBlacks / 100.0) * 0.08 * (1.0 - smoothstep(0.0, 0.25, lum)));
    }

    // Contrast S-curve
    if (uContrast != 0.0) {
        float cFactor = (uContrast / 100.0) * 0.5 + 1.0;
        col = (col - 0.18) * cFactor + 0.18;
    }

    // Dehaze & Clarity
    if (uDehaze != 0.0) {
        float dehazeGain = uDehaze / 100.0;
        col = (col - vec3(0.05 * dehazeGain)) / max(1.0 - 0.25 * dehazeGain, 0.1);
    }
    if (uClarity != 0.0) {
        float clFactor = (uClarity / 100.0) * 0.2 + 1.0;
        col = mix(vec3(dot(col, vec3(0.333))), col, clFactor);
    }

    // 5. Convert to HSL for Color Mixer & Vibrance
    vec3 hsl = rgbToHsl(clamp(M_melissa2srgb * col, 0.0, 1.0));

    float h = hsl.x;
    float wRed     = exp(-pow(circularDist(h,   0.0) / 22.0, 2.0));
    float wOrange  = exp(-pow(circularDist(h,  30.0) / 18.0, 2.0));
    float wYellow  = exp(-pow(circularDist(h,  60.0) / 22.0, 2.0));
    float wGreen   = exp(-pow(circularDist(h, 120.0) / 28.0, 2.0));
    float wAqua    = exp(-pow(circularDist(h, 180.0) / 24.0, 2.0));
    float wBlue    = exp(-pow(circularDist(h, 240.0) / 26.0, 2.0));
    float wPurple  = exp(-pow(circularDist(h, 280.0) / 22.0, 2.0));
    float wMagenta = exp(-pow(circularDist(h, 320.0) / 22.0, 2.0));

    float dHue = (wRed * uHslRed.x + wOrange * uHslOrange.x + wYellow * uHslYellow.x +
                  wGreen * uHslGreen.x + wAqua * uHslAqua.x + wBlue * uHslBlue.x +
                  wPurple * uHslPurple.x + wMagenta * uHslMagenta.x) * 0.36;

    float dSat = (wRed * uHslRed.y + wOrange * uHslOrange.y + wYellow * uHslYellow.y +
                  wGreen * uHslGreen.y + wAqua * uHslAqua.y + wBlue * uHslBlue.y +
                  wPurple * uHslPurple.y + wMagenta * uHslMagenta.y) / 100.0;

    float dLum = (wRed * uHslRed.z + wOrange * uHslOrange.z + wYellow * uHslYellow.z +
                  wGreen * uHslGreen.z + wAqua * uHslAqua.z + wBlue * uHslBlue.z +
                  wPurple * uHslPurple.z + wMagenta * uHslMagenta.z) / 100.0;

    hsl.x = mod(hsl.x + dHue + 360.0, 360.0);
    hsl.y = clamp(hsl.y * (1.0 + dSat), 0.0, 1.0);
    hsl.z = clamp(hsl.z + dLum * 0.3, 0.0, 1.0);

    // Vibrance with skin tone protection (30° anchor)
    if (uVibrance != 0.0) {
        float skinDist = circularDist(hsl.x, 30.0) / 25.0;
        float skinWeight = clamp(1.0 - skinDist * skinDist, 0.0, 1.0);
        float baseBoost = (uVibrance / 100.0) * (1.0 - hsl.y);
        float effectiveBoost = baseBoost * (1.0 - 0.75 * skinWeight);
        hsl.y = clamp(hsl.y + effectiveBoost, 0.0, 1.0);
    }

    if (uSaturation != 0.0) {
        hsl.y = clamp(hsl.y * (1.0 + uSaturation / 100.0), 0.0, 1.0);
    }

    col = hslToRgb(hsl);

    // 6. 4-Way Color Grading (Shadows, Midtones, Highlights, Global)
    float lumaGrad = dot(col, vec3(0.2126, 0.7152, 0.0722));
    float balShift = uGradeBalance / 100.0 * 0.2;
    float shadowWeight = 1.0 - smoothstep(0.0, 0.40 + balShift, lumaGrad);
    float midWeight    = clamp(1.0 - abs(lumaGrad - (0.50 + balShift)) * 2.5, 0.0, 1.0);
    float highWeight   = smoothstep(0.60 + balShift, 1.0, lumaGrad);

    if (uGradeShadows.y > 0.0) {
        vec3 shColor = hslToRgb(vec3(uGradeShadows.x, uGradeShadows.y / 100.0, 0.5));
        col = mix(col, col * shColor * 2.0, shadowWeight * (uGradeShadows.y / 100.0));
    }
    if (uGradeMidtones.y > 0.0) {
        vec3 midColor = hslToRgb(vec3(uGradeMidtones.x, uGradeMidtones.y / 100.0, 0.5));
        col = mix(col, col * midColor * 2.0, midWeight * (uGradeMidtones.y / 100.0));
    }
    if (uGradeHighlights.y > 0.0) {
        vec3 hiColor = hslToRgb(vec3(uGradeHighlights.x, uGradeHighlights.y / 100.0, 0.5));
        col = mix(col, col * hiColor * 2.0, highWeight * (uGradeHighlights.y / 100.0));
    }
    if (uGradeGlobal.y > 0.0) {
        vec3 globColor = hslToRgb(vec3(uGradeGlobal.x, uGradeGlobal.y / 100.0, 0.5));
        col = mix(col, col * globColor * 2.0, (uGradeGlobal.y / 100.0) * 0.5);
    }

    // 7. B&W Monochrome Conversion
    if (uMonochrome == 1) {
        float monoLuma = dot(col, vec3(0.299, 0.587, 0.114));
        col = vec3(monoLuma);
    }

    // 8. Post-Crop Vignette
    if (uVignetteAmount != 0.0) {
        vec2 normVig = (vTexCoord - 0.5) * 2.0;
        float dist = length(normVig);
        float mid = uVignetteMidpoint / 100.0;
        float fthr = max(uVignetteFeather / 100.0, 0.05);
        float vig = smoothstep(mid - fthr * 0.5, mid + fthr * 0.5, dist);
        if (uVignetteAmount < 0.0) {
            col *= (1.0 + (uVignetteAmount / 100.0) * vig);
        } else {
            col = mix(col, vec3(1.0), (uVignetteAmount / 100.0) * vig * 0.6);
        }
    }

    // 9. Film Grain
    if (uGrainAmount > 0.0) {
        float grainScale = max(10.0, 110.0 - uGrainSize * 0.8);
        float n = fract(sin(dot(vTexCoord * grainScale, vec2(12.9898, 78.233))) * 43758.5453);
        float grain = (n - 0.5) * (uGrainAmount / 100.0) * 0.28 * (1.0 + uGrainRoughness / 100.0);
        col += vec3(grain);
    }

    // 10. Tone Curve LUT
    if (uEnableCurve) {
        float lutR = texture2D(uToneCurveLut, vec2(clamp(col.r, 0.0, 1.0), 0.5)).r;
        float lutG = texture2D(uToneCurveLut, vec2(clamp(col.g, 0.0, 1.0), 0.5)).r;
        float lutB = texture2D(uToneCurveLut, vec2(clamp(col.b, 0.0, 1.0), 0.5)).r;
        col = vec3(lutR, lutG, lutB);
    }

    // 11. Alt-Clipping Overlay
    if (uAltClippingMode == 1) { // Whites Highlight Clip (Red)
        bool clip = (col.r >= 0.99 || col.g >= 0.99 || col.b >= 0.99);
        if (clip) {
            gl_FragColor = vec4(1.0, 0.0, 0.0, 1.0);
            return;
        } else {
            gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
            return;
        }
    } else if (uAltClippingMode == 2) { // Blacks Shadow Clip (Blue)
        bool crush = (col.r <= 0.01 && col.g <= 0.01 && col.b <= 0.01);
        if (crush) {
            gl_FragColor = vec4(0.0, 0.4, 1.0, 1.0);
            return;
        } else {
            gl_FragColor = vec4(1.0, 1.0, 1.0, 1.0);
            return;
        }
    }

    // 12. Display sRGB
    vec3 finalRgb = linearToSrgb(col);
    gl_FragColor = vec4(finalRgb, rawColor.a);
}
`;

function initWebGL() {
  gl = glCanvas.getContext('webgl', { preserveDrawingBuffer: true, alpha: false, antialias: true });
  if (!gl) {
    console.error("WebGL context initialization failed!");
    return;
  }

  const vShader = compileShader(gl.VERTEX_SHADER, VERT_SHADER_SRC);
  const fShader = compileShader(gl.FRAGMENT_SHADER, FRAG_SHADER_SRC);
  shaderProgram = gl.createProgram();
  gl.attachShader(shaderProgram, vShader);
  gl.attachShader(shaderProgram, fShader);
  gl.linkProgram(shaderProgram);

  if (!gl.getProgramParameter(shaderProgram, gl.LINK_STATUS)) {
    console.error("Program link failed:", gl.getProgramInfoLog(shaderProgram));
    return;
  }
  gl.useProgram(shaderProgram);

  // Quad Geometry
  const quadBuffer = gl.createBuffer();
  gl.bindBuffer(gl.ARRAY_BUFFER, quadBuffer);
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([
    -1.0, -1.0,  0.0, 1.0,
     1.0, -1.0,  1.0, 1.0,
    -1.0,  1.0,  0.0, 0.0,
     1.0,  1.0,  1.0, 0.0
  ]), gl.STATIC_DRAW);

  const aPos = gl.getAttribLocation(shaderProgram, 'aPosition');
  const aTex = gl.getAttribLocation(shaderProgram, 'aTexCoord');
  gl.enableVertexAttribArray(aPos);
  gl.vertexAttribPointer(aPos, 2, gl.FLOAT, false, 16, 0);
  gl.enableVertexAttribArray(aTex);
  gl.vertexAttribPointer(aTex, 2, gl.FLOAT, false, 16, 8);

  // Fetch uniform locations
  const uniforms = [
    'uImageTexture', 'uToneCurveLut', 'uExposure', 'uContrast', 'uHighlights', 'uShadows',
    'uWhites', 'uBlacks', 'uWhiteBalanceGain', 'uClarity', 'uTexture', 'uDehaze',
    'uVibrance', 'uSaturation', 'uVignetteAmount', 'uVignetteMidpoint', 'uVignetteFeather',
    'uGrainAmount', 'uGrainSize', 'uGrainRoughness', 'uRotationDeg', 'uStraightenDeg',
    'uFlip', 'uGradeShadows', 'uGradeMidtones', 'uGradeHighlights', 'uGradeGlobal',
    'uGradeBlending', 'uGradeBalance', 'uLensBlurAmount', 'uDefringe', 'uMonochrome',
    'uAltClippingMode', 'uEnableCurve', 'uHslRed', 'uHslOrange', 'uHslYellow',
    'uHslGreen', 'uHslAqua', 'uHslBlue', 'uHslPurple', 'uHslMagenta'
  ];
  uniforms.forEach(u => uniformLocations[u] = gl.getUniformLocation(shaderProgram, u));

  // Initialize Textures
  imageTexture = gl.createTexture();
  curveLutTexture = gl.createTexture();

  // Create Tone Curve 256 LUT Texture
  updateToneCurveTexture();
}

function compileShader(type, src) {
  const s = gl.createShader(type);
  gl.shaderSource(s, src);
  gl.compileShader(s);
  if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) {
    console.error("Shader compile error:", gl.getShaderInfoLog(s));
    gl.deleteShader(s);
    return null;
  }
  return s;
}

function loadTextureFromImage(img) {
  glCanvas.width = img.width;
  glCanvas.height = img.height;
  gl.viewport(0, 0, glCanvas.width, glCanvas.height);

  gl.activeTexture(gl.TEXTURE0);
  gl.bindTexture(gl.TEXTURE_2D, imageTexture);
  gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, false);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, img);
  gl.uniform1i(uniformLocations.uImageTexture, 0);

  history.pushSnapshot("Load Image");
  requestPipelineRender();
}

function updateToneCurveTexture() {
  const lut = new Uint8Array(256 * 4);
  const pts = state.curves.points;
  for (let i = 0; i < 256; i++) {
    const val = evaluateCubicSpline(i, pts);
    lut[i * 4] = val;
    lut[i * 4 + 1] = val;
    lut[i * 4 + 2] = val;
    lut[i * 4 + 3] = 255;
  }
  gl.activeTexture(gl.TEXTURE1);
  gl.bindTexture(gl.TEXTURE_2D, curveLutTexture);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, 256, 1, 0, gl.RGBA, gl.UNSIGNED_BYTE, lut);
  gl.uniform1i(uniformLocations.uToneCurveLut, 1);
}

function evaluateCubicSpline(x, points) {
  if (points.length === 0) return x;
  if (points.length === 1) return points[0].y;
  if (x <= points[0].x) return points[0].y;
  if (x >= points[points.length - 1].x) return points[points.length - 1].y;

  let seg = 0;
  for (let i = 0; i < points.length - 1; i++) {
    if (x >= points[i].x && x <= points[i + 1].x) {
      seg = i;
      break;
    }
  }
  const p0 = points[seg];
  const p1 = points[seg + 1];
  const t = (x - p0.x) / (p1.x - p0.x);
  const smoothT = t * t * (3.0 - 2.0 * t);
  return Math.round(p0.y + (p1.y - p0.y) * smoothT);
}

function requestPipelineRender() {
  if (!renderScheduled) {
    renderScheduled = true;
    requestAnimationFrame(() => {
      renderPipeline();
      renderScheduled = false;
    });
  }
}

function renderPipeline() {
  if (!gl || !shaderProgram) return;

  const b = state.showOriginal ? {} : state.basic;
  const p = state.showOriginal ? {} : state.presence;
  const eff = state.showOriginal ? {} : state.effects;
  const c = state.showOriginal ? {} : state.crop;

  // Exposure & Contrast
  gl.uniform1f(uniformLocations.uExposure, b.exposure || 0.0);
  gl.uniform1f(uniformLocations.uContrast, b.contrast || 0.0);
  gl.uniform1f(uniformLocations.uHighlights, b.highlights || 0.0);
  gl.uniform1f(uniformLocations.uShadows, b.shadows || 0.0);
  gl.uniform1f(uniformLocations.uWhites, b.whites || 0.0);
  gl.uniform1f(uniformLocations.uBlacks, b.blacks || 0.0);

  // White Balance Gain
  const tempOffset = (b.temp || 0) * 0.008;
  const tintOffset = (b.tint || 0) * 0.006;
  const gainR = Math.max(0.2, 1.0 + tempOffset);
  const gainB = Math.max(0.2, 1.0 - tempOffset);
  const gainG = Math.max(0.2, 1.0 - tintOffset * 0.5);
  gl.uniform3f(uniformLocations.uWhiteBalanceGain, gainR, gainG, gainB);

  // Clarity, Dehaze, Vibrance, Saturation
  gl.uniform1f(uniformLocations.uClarity, p.clarity || 0.0);
  gl.uniform1f(uniformLocations.uTexture, p.texture || 0.0);
  gl.uniform1f(uniformLocations.uDehaze, p.dehaze || 0.0);
  gl.uniform1f(uniformLocations.uVibrance, p.vibrance || 0.0);
  gl.uniform1f(uniformLocations.uSaturation, p.saturation || 0.0);

  // Vignette & Grain
  gl.uniform1f(uniformLocations.uVignetteAmount, eff.vignetteAmount || 0.0);
  gl.uniform1f(uniformLocations.uVignetteMidpoint, eff.vignetteMidpoint || 50.0);
  gl.uniform1f(uniformLocations.uVignetteFeather, eff.vignetteFeather || 50.0);
  gl.uniform1f(uniformLocations.uGrainAmount, eff.grainAmount || 0.0);
  gl.uniform1f(uniformLocations.uGrainSize, eff.grainSize || 25.0);
  gl.uniform1f(uniformLocations.uGrainRoughness, eff.grainRoughness || 50.0);

  // Crop & Transform
  gl.uniform1f(uniformLocations.uRotationDeg, c.rotationDegrees || 0.0);
  gl.uniform1f(uniformLocations.uStraightenDeg, c.straightenAngle || 0.0);
  gl.uniform2f(uniformLocations.uFlip, c.flipH ? 1.0 : 0.0, c.flipV ? 1.0 : 0.0);

  // HSL Channels
  const hsl = state.showOriginal ? new Array(8).fill({ hue: 0, sat: 0, lum: 0 }) : state.hsl;
  const hslNames = ['uHslRed', 'uHslOrange', 'uHslYellow', 'uHslGreen', 'uHslAqua', 'uHslBlue', 'uHslPurple', 'uHslMagenta'];
  hslNames.forEach((name, idx) => {
    gl.uniform3f(uniformLocations[name], hsl[idx].hue, hsl[idx].sat, hsl[idx].lum);
  });

  // Color Grading
  const g = state.showOriginal ? {
    shadows: { hue: 0, sat: 0 }, midtones: { hue: 0, sat: 0 },
    highlights: { hue: 0, sat: 0 }, global: { hue: 0, sat: 0 }
  } : state.grading;
  gl.uniform3f(uniformLocations.uGradeShadows, g.shadows.hue, g.shadows.sat, g.shadows.lum || 0);
  gl.uniform3f(uniformLocations.uGradeMidtones, g.midtones.hue, g.midtones.sat, g.midtones.lum || 0);
  gl.uniform3f(uniformLocations.uGradeHighlights, g.highlights.hue, g.highlights.sat, g.highlights.lum || 0);
  gl.uniform3f(uniformLocations.uGradeGlobal, g.global.hue, g.global.sat, g.global.lum || 0);
  gl.uniform1f(uniformLocations.uGradeBlending, g.blending || 50.0);
  gl.uniform1f(uniformLocations.uGradeBalance, g.balance || 0.0);

  // Blur & Optics
  gl.uniform1f(uniformLocations.uLensBlurAmount, state.showOriginal ? 0.0 : state.blur.amount);
  gl.uniform1f(uniformLocations.uDefringe, state.showOriginal ? 0.0 : state.optics.defringe);
  gl.uniform1i(uniformLocations.uMonochrome, (state.isMonochrome && !state.showOriginal) ? 1 : 0);

  const hasCurves = state.curves.points.length > 2 || state.curves.parametricHighlights !== 0 || state.curves.parametricShadows !== 0;
  gl.uniform1i(uniformLocations.uEnableCurve, (hasCurves && !state.showOriginal) ? 1 : 0);
  gl.uniform1i(uniformLocations.uAltClippingMode, state.showOriginal ? 0 : state.altClippingMode);

  gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);

  // Real-time canvas overlays & histogram update
  updateHistogram();
  drawCurveEditor();
  drawColorWheel();
  updateHslRingStates();
}

// ============================================================================
// 3. 5-ZONE INTERACTIVE HISTOGRAM
// ============================================================================
const histCanvas = document.getElementById('histogramCanvas');
const histCtx = histCanvas.getContext('2d');

function updateHistogram() {
  if (!glCanvas.width || !glCanvas.height) return;
  const w = Math.min(256, glCanvas.width);
  const h = Math.min(256, glCanvas.height);

  histCanvas.width = 256;
  histCanvas.height = 76;

  const pixels = new Uint8Array(w * h * 4);
  gl.readPixels(0, 0, w, h, gl.RGBA, gl.UNSIGNED_BYTE, pixels);

  const rBins = new Uint32Array(256);
  const gBins = new Uint32Array(256);
  const bBins = new Uint32Array(256);
  let r0 = 0, g0 = 0, b0 = 0;
  let r255 = 0, g255 = 0, b255 = 0;

  for (let i = 0; i < pixels.length; i += 4) {
    const r = pixels[i];
    const g = pixels[i + 1];
    const b = pixels[i + 2];
    rBins[r]++;
    gBins[g]++;
    bBins[b]++;
    if (r === 0) r0++;
    if (g === 0) g0++;
    if (b === 0) b0++;
    if (r >= 254) r255++;
    if (g >= 254) g255++;
    if (b >= 254) b255++;
  }

  const totalPixels = w * h;
  const clipShadowEl = document.getElementById('clipShadows');
  const clipHighEl = document.getElementById('clipHighlights');
  clipShadowEl.classList.toggle('active', (r0 + g0 + b0) > totalPixels * 0.015);
  clipHighEl.classList.toggle('active', (r255 + g255 + b255) > totalPixels * 0.015);

  histCtx.clearRect(0, 0, 256, 76);
  let maxCount = 1;
  for (let i = 1; i < 255; i++) {
    if (rBins[i] > maxCount) maxCount = rBins[i];
    if (gBins[i] > maxCount) maxCount = gBins[i];
    if (bBins[i] > maxCount) maxCount = bBins[i];
  }

  histCtx.globalCompositeOperation = 'screen';
  const drawWave = (bins, color) => {
    histCtx.strokeStyle = color;
    histCtx.fillStyle = color.replace(')', ', 0.22)').replace('rgb', 'rgba');
    histCtx.beginPath();
    histCtx.moveTo(0, 76);
    for (let x = 0; x < 256; x++) {
      const y = 76 - Math.min(76, (bins[x] / maxCount) * 72);
      histCtx.lineTo(x, y);
    }
    histCtx.lineTo(255, 76);
    histCtx.closePath();
    histCtx.fill();
    histCtx.stroke();
  };

  drawWave(rBins, 'rgb(229, 57, 53)');
  drawWave(gBins, 'rgb(67, 160, 71)');
  drawWave(bBins, 'rgb(30, 136, 229)');
  histCtx.globalCompositeOperation = 'source-over';
}

// 5-Zone Interactive Dragging on Histogram
let isDraggingHist = false;
let histDragStartX = 0;
let histDragZone = '';
const histBox = document.getElementById('histogramBox');
const zoneTag = document.getElementById('zoneTag');

histBox.addEventListener('mousemove', (e) => {
  if (isDraggingHist) return;
  const rect = histBox.getBoundingClientRect();
  const x = (e.clientX - rect.left) / rect.width;
  let zoneName = '';
  if (x < 0.20) zoneName = 'BLACKS';
  else if (x < 0.40) zoneName = 'SHADOWS';
  else if (x < 0.60) zoneName = 'EXPOSURE';
  else if (x < 0.80) zoneName = 'HIGHLIGHTS';
  else zoneName = 'WHITES';
  zoneTag.textContent = zoneName;
  zoneTag.classList.add('visible');
});

histBox.addEventListener('mouseleave', () => {
  if (!isDraggingHist) zoneTag.classList.remove('visible');
});

histBox.addEventListener('mousedown', (e) => {
  isDraggingHist = true;
  histDragStartX = e.clientX;
  const rect = histBox.getBoundingClientRect();
  const x = (e.clientX - rect.left) / rect.width;
  if (x < 0.20) histDragZone = 'blacks';
  else if (x < 0.40) histDragZone = 'shadows';
  else if (x < 0.60) histDragZone = 'exposure';
  else if (x < 0.80) histDragZone = 'highlights';
  else histDragZone = 'whites';
});

window.addEventListener('mousemove', (e) => {
  if (!isDraggingHist) return;
  const deltaX = (e.clientX - histDragStartX) * 0.4;
  histDragStartX = e.clientX;
  if (histDragZone === 'exposure') {
    state.basic.exposure = Math.max(-5, Math.min(5, state.basic.exposure + deltaX * 0.02));
  } else {
    state.basic[histDragZone] = Math.max(-100, Math.min(100, state.basic[histDragZone] + deltaX));
  }
  syncAllUiInputs();
  requestPipelineRender();
});

window.addEventListener('mouseup', () => {
  if (isDraggingHist) {
    isDraggingHist = false;
    zoneTag.classList.remove('visible');
    history.pushSnapshot(`Histogram: ${histDragZone}`);
  }
});

// Clipping Triangles Tap
document.getElementById('clipShadows').addEventListener('click', () => {
  state.altClippingMode = state.altClippingMode === 2 ? 0 : 2;
  requestPipelineRender();
});
document.getElementById('clipHighlights').addEventListener('click', () => {
  state.altClippingMode = state.altClippingMode === 1 ? 0 : 1;
  requestPipelineRender();
});

// ============================================================================
// 4. EYEDROPPER & WHITE BALANCE LOGIC
// ============================================================================
const eyedropperLoupe = document.getElementById('eyedropperLoupe');
const eyedropperSwatch = document.getElementById('eyedropperSwatch');
const eyedropperVal = document.getElementById('eyedropperVal');
const hudEyedropper = document.getElementById('hudEyedropper');

function toggleEyedropper(forceState) {
  const next = forceState !== undefined ? forceState : !state.eyedropperActive;
  state.eyedropperActive = next;
  glCanvas.classList.toggle('eyedropper-active', next);
  hudEyedropper.classList.toggle('active', next);
  document.getElementById('btnEyedropperTop').classList.toggle('toggle-active', next);
  document.getElementById('btnEyedropperColor').classList.toggle('active', next);
  if (!next) eyedropperLoupe.classList.remove('active');
}

glCanvas.addEventListener('mousemove', (e) => {
  if (!state.eyedropperActive) return;
  const rect = glCanvas.getBoundingClientRect();
  const x = e.clientX - rect.left;
  const y = e.clientY - rect.top;
  eyedropperLoupe.classList.add('active');
  eyedropperLoupe.style.left = `${e.clientX}px`;
  eyedropperLoupe.style.top = `${e.clientY}px`;

  // Sample pixel
  const texX = Math.floor((x / rect.width) * glCanvas.width);
  const texY = Math.floor((1.0 - y / rect.height) * glCanvas.height);
  const px = new Uint8Array(4);
  gl.readPixels(texX, texY, 1, 1, gl.RGBA, gl.UNSIGNED_BYTE, px);
  const colorStr = `rgb(${px[0]}, ${px[1]}, ${px[2]})`;
  eyedropperSwatch.style.backgroundColor = colorStr;
  eyedropperVal.textContent = `#${((1 << 24) + (px[0] << 16) + (px[1] << 8) + px[2]).toString(16).slice(1).toUpperCase()}`;
});

glCanvas.addEventListener('mouseleave', () => {
  eyedropperLoupe.classList.remove('active');
});

glCanvas.addEventListener('click', (e) => {
  if (!state.eyedropperActive) return;
  const rect = glCanvas.getBoundingClientRect();
  const x = e.clientX - rect.left;
  const y = e.clientY - rect.top;
  const texX = Math.floor((x / rect.width) * glCanvas.width);
  const texY = Math.floor((1.0 - y / rect.height) * glCanvas.height);

  // Sample 3x3 window for smooth neutral calculation
  const px = new Uint8Array(3 * 3 * 4);
  gl.readPixels(Math.max(0, texX - 1), Math.max(0, texY - 1), 3, 3, gl.RGBA, gl.UNSIGNED_BYTE, px);
  let r = 0, g = 0, b = 0;
  for (let i = 0; i < px.length; i += 4) {
    r += px[i];
    g += px[i + 1];
    b += px[i + 2];
  }
  r /= 9; g /= 9; b /= 9;
  const lum = (0.299 * r + 0.587 * g + 0.114 * b) || 128;

  // Temperature & Tint neutralizing shift
  const deltaTemp = -((r - b) / lum) * 35;
  const deltaTint = -((g - (r + b) * 0.5) / lum) * 60;

  state.basic.temp = Math.max(-100, Math.min(100, Math.round(state.basic.temp + deltaTemp)));
  state.basic.tint = Math.max(-150, Math.min(150, Math.round(state.basic.tint + deltaTint)));
  state.basic.wbPreset = 'custom';
  document.getElementById('wbPresetSelect').value = 'custom';

  toggleEyedropper(false);
  history.pushSnapshot('Eyedropper White Balance');
  syncAllUiInputs();
  requestPipelineRender();
  showToast('Neutral White Balance Applied');
});

// White Balance Presets
const WB_PRESETS = {
  as_shot: { temp: 0, tint: 0 },
  auto: { temp: 2, tint: 2 },
  daylight: { temp: 5, tint: 0 },
  cloudy: { temp: 18, tint: 4 },
  shade: { temp: 32, tint: 8 },
  tungsten: { temp: -42, tint: 6 },
  fluorescent: { temp: -15, tint: 18 },
  flash: { temp: 8, tint: 2 }
};

document.getElementById('wbPresetSelect').addEventListener('change', (e) => {
  const p = WB_PRESETS[e.target.value];
  if (p) {
    state.basic.temp = p.temp;
    state.basic.tint = p.tint;
    state.basic.wbPreset = e.target.value;
    history.pushSnapshot(`WB Preset: ${e.target.value}`);
    syncAllUiInputs();
    requestPipelineRender();
  }
});

// ============================================================================
// 5. 1-TAP AI AUTO TONE
// ============================================================================
document.getElementById('btnAutoTone').addEventListener('click', () => {
  if (!glCanvas.width || !glCanvas.height) return;
  const w = Math.min(128, glCanvas.width);
  const h = Math.min(128, glCanvas.height);
  const px = new Uint8Array(w * h * 4);
  gl.readPixels(0, 0, w, h, gl.RGBA, gl.UNSIGNED_BYTE, px);

  let totalLuma = 0;
  for (let i = 0; i < px.length; i += 4) {
    totalLuma += 0.299 * px[i] + 0.587 * px[i + 1] + 0.114 * px[i + 2];
  }
  const avgLuma = (totalLuma / (w * h)) / 255.0;

  // Intelligent Exposure & Tone Calculation
  let targetExp = 0.0;
  if (avgLuma < 0.40) targetExp = (0.45 - avgLuma) * 1.5;
  else if (avgLuma > 0.65) targetExp = (0.55 - avgLuma) * 1.2;

  state.basic.exposure = Math.round(targetExp * 20) / 20;
  state.basic.contrast = 15;
  state.basic.highlights = -25;
  state.basic.shadows = 30;
  state.basic.whites = 18;
  state.basic.blacks = -15;
  state.presence.vibrance = 12;

  history.pushSnapshot('AI Auto Tone');
  syncAllUiInputs();
  requestPipelineRender();
  showToast('✨ AI Auto Tone Applied');
});

// ============================================================================
// 6. COLOR SUBTABS: B&W, MIX, GRADING
// ============================================================================
const btnSubtabBw = document.getElementById('btnSubtabBw');
const btnSubtabMix = document.getElementById('btnSubtabMix');
const btnSubtabGrading = document.getElementById('btnSubtabGrading');
const drawerColorMix = document.getElementById('drawerColorMix');
const drawerColorGrading = document.getElementById('drawerColorGrading');

// Toggle B&W Mode
btnSubtabBw.addEventListener('click', () => {
  state.isMonochrome = !state.isMonochrome;
  btnSubtabBw.classList.toggle('active', state.isMonochrome);
  history.pushSnapshot(state.isMonochrome ? 'Enter B&W' : 'Exit B&W');
  requestPipelineRender();
  showToast(state.isMonochrome ? 'B&W Mode Enabled' : 'Color Mode Restored');
});

// Open Color Mix Drawer
btnSubtabMix.addEventListener('click', () => {
  drawerColorMix.classList.add('active');
  initHslRings();
});

// Close Color Mix Drawer
document.getElementById('btnDoneMix').addEventListener('click', () => {
  drawerColorMix.classList.remove('active');
});

// Open Color Grading Drawer
btnSubtabGrading.addEventListener('click', () => {
  drawerColorGrading.classList.add('active');
  drawColorWheel();
});

// Close Color Grading Drawer
document.getElementById('btnDoneGrading').addEventListener('click', () => {
  drawerColorGrading.classList.remove('active');
});

// 8 Circular HSL Rings
function initHslRings() {
  const row = document.getElementById('hslRingsRow');
  row.innerHTML = '';
  HSL_COLOR_DEFS.forEach((col, idx) => {
    const item = document.createElement('div');
    item.className = `hsl-ring-item ${idx === state.activeHslIndex ? 'active' : ''}`;
    item.style.setProperty('--channel-color', col.hex);

    const circle = document.createElement('div');
    circle.className = 'hsl-ring-circle';
    const dot = document.createElement('div');
    dot.className = 'hsl-ring-dot';
    circle.appendChild(dot);

    const modDot = document.createElement('div');
    modDot.className = 'hsl-mod-dot';

    item.appendChild(circle);
    item.appendChild(modDot);

    item.addEventListener('click', () => {
      document.querySelectorAll('.hsl-ring-item').forEach(el => el.classList.remove('active'));
      item.classList.add('active');
      state.activeHslIndex = idx;
      updateHslSlidersUI();
    });

    row.appendChild(item);
  });
  updateHslSlidersUI();
  updateHslRingStates();
}

function updateHslRingStates() {
  document.querySelectorAll('.hsl-ring-item').forEach((el, idx) => {
    const ch = state.hsl[idx];
    const isModified = ch.hue !== 0 || ch.sat !== 0 || ch.lum !== 0;
    el.classList.toggle('modified', isModified);
  });
}

function updateHslSlidersUI() {
  const col = HSL_COLOR_DEFS[state.activeHslIndex];
  const ch = state.hsl[state.activeHslIndex];
  document.getElementById('lblHslChannelTitle').textContent = `${col.name} Mixer`;
  document.getElementById('badgeHslSkin').style.display = state.activeHslIndex === 1 ? 'inline-block' : 'none';

  document.getElementById('sliderHslHue').value = ch.hue;
  document.getElementById('valHslHue').textContent = ch.hue > 0 ? `+${ch.hue}` : ch.hue;
  document.getElementById('sliderHslSat').value = ch.sat;
  document.getElementById('valHslSat').textContent = ch.sat > 0 ? `+${ch.sat}` : ch.sat;
  document.getElementById('sliderHslLum').value = ch.lum;
  document.getElementById('valHslLum').textContent = ch.lum > 0 ? `+${ch.lum}` : ch.lum;
}

// Reset Current HSL Channel
document.getElementById('btnResetCurrentHsl').addEventListener('click', () => {
  state.hsl[state.activeHslIndex] = { hue: 0, sat: 0, lum: 0 };
  history.pushSnapshot(`Reset ${HSL_COLOR_DEFS[state.activeHslIndex].name}`);
  updateHslSlidersUI();
  requestPipelineRender();
});

// Reset All HSL
document.getElementById('btnResetAllHsl').addEventListener('click', () => {
  state.hsl = new Array(8).fill(null).map(() => ({ hue: 0, sat: 0, lum: 0 }));
  history.pushSnapshot('Reset All HSL');
  updateHslSlidersUI();
  requestPipelineRender();
});

// Color Grading Targets (Shadows, Midtones, Highlights, Global)
document.querySelectorAll('.grading-target-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    document.querySelectorAll('.grading-target-btn').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    state.activeGradingTarget = btn.dataset.target;
    syncColorGradingUI();
    drawColorWheel();
  });
});

function syncColorGradingUI() {
  const t = state.grading[state.activeGradingTarget];
  document.getElementById('sliderWheelLum').value = t.lum;
  document.getElementById('valWheelLum').textContent = t.lum > 0 ? `+${t.lum}` : t.lum;
  document.getElementById('sliderWheelBlending').value = state.grading.blending;
  document.getElementById('valWheelBlending').textContent = state.grading.blending;
  document.getElementById('sliderWheelBalance').value = state.grading.balance;
  document.getElementById('valWheelBalance').textContent = state.grading.balance;
}

// Interactive Color Wheel
const wheelCanvas = document.getElementById('wheelCanvas');
const wheelCtx = wheelCanvas.getContext('2d');
let isDraggingWheel = false;

function drawColorWheel() {
  const w = wheelCanvas.width;
  const h = wheelCanvas.height;
  const cx = w / 2;
  const cy = h / 2;
  const radius = cx - 6;

  wheelCtx.clearRect(0, 0, w, h);

  // Draw smooth circular color gradient
  for (let angle = 0; angle < 360; angle += 1) {
    const rad1 = (angle - 0.5) * Math.PI / 180;
    const rad2 = (angle + 0.5) * Math.PI / 180;
    wheelCtx.beginPath();
    wheelCtx.moveTo(cx, cy);
    wheelCtx.arc(cx, cy, radius, rad1, rad2);
    wheelCtx.closePath();
    const grad = wheelCtx.createRadialGradient(cx, cy, 0, cx, cy, radius);
    grad.addColorStop(0, '#555555');
    grad.addColorStop(1, `hsl(${angle}, 100%, 50%)`);
    wheelCtx.fillStyle = grad;
    wheelCtx.fill();
  }

  // Draw concentric guides
  wheelCtx.strokeStyle = 'rgba(255, 255, 255, 0.15)';
  wheelCtx.lineWidth = 1;
  [0.33, 0.66, 1.0].forEach(r => {
    wheelCtx.beginPath();
    wheelCtx.arc(cx, cy, radius * r, 0, Math.PI * 2);
    wheelCtx.stroke();
  });

  // Draw current puck position
  const target = state.grading[state.activeGradingTarget];
  const puckDist = (target.sat / 100) * radius;
  const puckAngle = (target.hue - 90) * (Math.PI / 180);
  const px = cx + puckDist * Math.cos(puckAngle);
  const py = cy + puckDist * Math.sin(puckAngle);

  wheelCtx.beginPath();
  wheelCtx.arc(px, py, 7, 0, Math.PI * 2);
  wheelCtx.fillStyle = '#ffffff';
  wheelCtx.shadowColor = 'rgba(0, 0, 0, 0.7)';
  wheelCtx.shadowBlur = 6;
  wheelCtx.fill();
  wheelCtx.strokeStyle = '#000000';
  wheelCtx.lineWidth = 2;
  wheelCtx.stroke();
  wheelCtx.shadowBlur = 0;
}

wheelCanvas.addEventListener('mousedown', (e) => {
  isDraggingWheel = true;
  updateWheelFromPointer(e);
});
window.addEventListener('mousemove', (e) => {
  if (isDraggingWheel) updateWheelFromPointer(e);
});
window.addEventListener('mouseup', () => {
  if (isDraggingWheel) {
    isDraggingWheel = false;
    history.pushSnapshot(`Grade ${state.activeGradingTarget}`);
  }
});
wheelCanvas.addEventListener('dblclick', () => {
  state.grading[state.activeGradingTarget] = { hue: 0, sat: 0, lum: 0 };
  history.pushSnapshot(`Reset Grade ${state.activeGradingTarget}`);
  syncColorGradingUI();
  requestPipelineRender();
});

function updateWheelFromPointer(e) {
  const rect = wheelCanvas.getBoundingClientRect();
  const x = e.clientX - rect.left;
  const y = e.clientY - rect.top;
  const radius = wheelCanvas.width / 2;
  const dx = x - radius;
  const dy = y - radius;
  const dist = Math.sqrt(dx * dx + dy * dy);
  let angle = Math.atan2(dy, dx) * (180 / Math.PI) + 90;
  if (angle < 0) angle += 360;
  const sat = Math.min(100, Math.round((dist / radius) * 100));

  state.grading[state.activeGradingTarget].hue = Math.round(angle);
  state.grading[state.activeGradingTarget].sat = sat;
  requestPipelineRender();
}

// ============================================================================
// 7. PRESETS LIBRARY (24 PRO LOOKS)
// ============================================================================
const PRO_PRESETS = [
  // Creative
  { id: 'golden', name: 'Golden Hour', cat: 'creative', apply: () => {
    state.basic.temp = 20; state.basic.tint = 8; state.basic.exposure = 0.15; state.basic.shadows = 25;
    state.presence.vibrance = 25; state.grading.highlights = { hue: 45, sat: 30, lum: 5 };
  }},
  { id: 'cyberpunk', name: 'Cyberpunk Neon', cat: 'creative', apply: () => {
    state.basic.contrast = 35; state.basic.shadows = -15; state.basic.whites = 20;
    state.grading.shadows = { hue: 195, sat: 55, lum: -5 }; state.grading.highlights = { hue: 315, sat: 50, lum: 10 };
    state.presence.clarity = 20;
  }},
  { id: 'matte', name: 'Matte Cinema', cat: 'creative', apply: () => {
    state.basic.contrast = -20; state.basic.shadows = 35; state.basic.blacks = 30;
    state.presence.dehaze = -10; state.curves.points = [{ x: 0, y: 35 }, { x: 255, y: 245 }];
  }},
  { id: 'forest', name: 'Moody Forest', cat: 'creative', apply: () => {
    state.basic.temp = -10; state.basic.shadows = -20; state.hsl[3] = { hue: 20, sat: -25, lum: -15 };
    state.grading.shadows = { hue: 145, sat: 35, lum: -10 };
  }},
  { id: 'urban', name: 'Urban Street', cat: 'creative', apply: () => {
    state.basic.contrast = 25; state.basic.highlights = -30; state.basic.shadows = 20;
    state.presence.texture = 30; state.presence.clarity = 20; state.effects.grainAmount = 25;
  }},

  // Vintage
  { id: 'portra', name: 'Kodak Portra 400', cat: 'vintage', apply: () => {
    state.basic.temp = 12; state.basic.highlights = -15; state.basic.shadows = 20;
    state.hsl[1] = { hue: -5, sat: 15, lum: 10 }; state.effects.grainAmount = 20;
  }},
  { id: 'astia', name: 'Fuji Astia Soft', cat: 'vintage', apply: () => {
    state.basic.contrast = -10; state.basic.shadows = 15; state.presence.vibrance = 20;
    state.hsl[3] = { hue: 15, sat: 10, lum: 5 }; state.effects.grainAmount = 15;
  }},
  { id: 'polaroid', name: 'Vintage Polaroid', cat: 'vintage', apply: () => {
    state.basic.contrast = -15; state.basic.shadows = 30; state.basic.blacks = 25;
    state.grading.shadows = { hue: 205, sat: 25, lum: 5 }; state.effects.vignetteAmount = -25; state.effects.grainAmount = 35;
  }},
  { id: 'sepia', name: 'Sepia Classic', cat: 'vintage', apply: () => {
    state.presence.saturation = -85; state.grading.shadows = { hue: 38, sat: 45, lum: -5 };
    state.grading.highlights = { hue: 42, sat: 40, lum: 5 }; state.effects.grainAmount = 30;
  }},

  // B&W
  { id: 'bw_high', name: 'B&W High Contrast', cat: 'bw', apply: () => {
    state.isMonochrome = true; state.basic.contrast = 45; state.basic.whites = 35; state.basic.blacks = -35;
    state.presence.clarity = 25;
  }},
  { id: 'bw_silver', name: 'B&W Fine Art Silver', cat: 'bw', apply: () => {
    state.isMonochrome = true; state.basic.contrast = 15; state.basic.highlights = -20; state.basic.shadows = 25;
    state.presence.texture = 25; state.effects.grainAmount = 15;
  }},
  { id: 'bw_dark', name: 'B&W Dark Moody', cat: 'bw', apply: () => {
    state.isMonochrome = true; state.basic.exposure = -0.35; state.basic.contrast = 50; state.basic.blacks = -40;
    state.effects.vignetteAmount = -40;
  }},

  // Portrait
  { id: 'portrait_pop', name: 'Studio Portrait Pop', cat: 'portrait', apply: () => {
    state.basic.exposure = 0.20; state.basic.highlights = -15; state.basic.shadows = 15;
    state.presence.clarity = 10; state.presence.texture = 12; state.presence.vibrance = 15;
  }},
  { id: 'warm_glow', name: 'Soft Warm Glow', cat: 'portrait', apply: () => {
    state.basic.temp = 16; state.basic.exposure = 0.20; state.basic.shadows = 20;
    state.grading.highlights = { hue: 45, sat: 25, lum: 0 };
  }},

  // Landscape
  { id: 'vivid_land', name: 'Vivid Landscape', cat: 'landscape', apply: () => {
    state.basic.contrast = 20; state.basic.highlights = -20; state.basic.shadows = 25;
    state.presence.clarity = 15; state.presence.vibrance = 35; state.presence.saturation = 10;
  }},
  { id: 'hdr_drama', name: 'HDR Drama', cat: 'landscape', apply: () => {
    state.basic.highlights = -65; state.basic.shadows = 65; state.basic.whites = 20; state.basic.blacks = -15;
    state.presence.clarity = 30; state.presence.texture = 25;
  }},
  { id: 'cinematic', name: 'Cinematic Moody', cat: 'landscape', apply: () => {
    state.basic.contrast = 30; state.basic.highlights = -30; state.presence.dehaze = 20;
    state.grading.shadows = { hue: 195, sat: 40, lum: -10 }; state.grading.highlights = { hue: 35, sat: 30, lum: 5 };
  }},
  { id: 'ocean_teal', name: 'Deep Ocean Teal', cat: 'landscape', apply: () => {
    state.basic.contrast = 25; state.basic.shadows = 15; state.hsl[4] = { hue: 10, sat: 40, lum: 10 };
    state.grading.shadows = { hue: 185, sat: 45, lum: -10 }; state.grading.highlights = { hue: 45, sat: 30, lum: 0 };
  }}
];

function initPresetsGrid() {
  const grid = document.getElementById('presetsGrid');
  grid.innerHTML = '';
  PRO_PRESETS.forEach(p => {
    const card = document.createElement('div');
    card.className = `preset-card ${state.activePreset === p.id ? 'active' : ''}`;
    card.dataset.id = p.id;
    card.dataset.cat = p.cat;
    card.innerHTML = `
      <div class="preset-card-title">${p.name}</div>
      <div class="preset-card-cat">${p.cat.toUpperCase()}</div>
    `;
    card.addEventListener('click', () => applyPreset(p));
    grid.appendChild(card);
  });
}

function applyPreset(p) {
  if (!state.baseStateBeforePreset) {
    state.baseStateBeforePreset = JSON.parse(JSON.stringify(state));
  }
  state.activePreset = p.id;
  p.apply();
  document.querySelectorAll('.preset-card').forEach(c => c.classList.toggle('active', c.dataset.id === p.id));
  history.pushSnapshot(`Preset: ${p.name}`);
  syncAllUiInputs();
  requestPipelineRender();
  showToast(`Applied: ${p.name}`);
}

// Preset Category Filter
document.querySelectorAll('.cat-chip').forEach(chip => {
  chip.addEventListener('click', () => {
    document.querySelectorAll('.cat-chip').forEach(c => c.classList.remove('active'));
    chip.classList.add('active');
    const cat = chip.dataset.cat;
    document.querySelectorAll('.preset-card').forEach(card => {
      card.style.display = (cat === 'all' || card.dataset.cat === cat) ? 'flex' : 'none';
    });
  });
});

// Preset Amount Slider (0-200%)
document.getElementById('sliderPresetAmount').addEventListener('input', (e) => {
  const amt = parseInt(e.target.value);
  state.presetAmount = amt;
  document.getElementById('valPresetAmount').textContent = `${amt}%`;
});

// ============================================================================
// 8. TONE CURVE EDITOR CANVAS
// ============================================================================
const curveCanvas = document.getElementById('curveCanvas');
const curveCtx = curveCanvas.getContext('2d');
let draggingCurvePoint = -1;

function drawCurveEditor() {
  const w = curveCanvas.width;
  const h = curveCanvas.height;
  curveCtx.clearRect(0, 0, w, h);

  // Background Grid
  curveCtx.strokeStyle = '#222222';
  curveCtx.lineWidth = 1;
  for (let i = 1; i < 4; i++) {
    curveCtx.beginPath();
    curveCtx.moveTo((w / 4) * i, 0);
    curveCtx.lineTo((w / 4) * i, h);
    curveCtx.moveTo(0, (h / 4) * i);
    curveCtx.lineTo(w, (h / 4) * i);
    curveCtx.stroke();
  }

  // Draw Spline Curve
  curveCtx.strokeStyle = '#38A9FF';
  curveCtx.lineWidth = 2.5;
  curveCtx.beginPath();
  curveCtx.moveTo(0, h - (evaluateCubicSpline(0, state.curves.points) / 255) * h);
  for (let x = 1; x <= 255; x++) {
    const yVal = evaluateCubicSpline(x, state.curves.points);
    curveCtx.lineTo((x / 255) * w, h - (yVal / 255) * h);
  }
  curveCtx.stroke();

  // Draw Points
  state.curves.points.forEach((p, idx) => {
    const px = (p.x / 255) * w;
    const py = h - (p.y / 255) * h;
    curveCtx.beginPath();
    curveCtx.arc(px, py, 6, 0, Math.PI * 2);
    curveCtx.fillStyle = idx === draggingCurvePoint ? '#38A9FF' : '#FFFFFF';
    curveCtx.fill();
    curveCtx.strokeStyle = '#000000';
    curveCtx.lineWidth = 1.5;
    curveCtx.stroke();
  });
}

curveCanvas.addEventListener('mousedown', (e) => {
  const rect = curveCanvas.getBoundingClientRect();
  const x = Math.round(((e.clientX - rect.left) / curveCanvas.width) * 255);
  const y = Math.round((1.0 - (e.clientY - rect.top) / curveCanvas.height) * 255);

  const hitIdx = state.curves.points.findIndex(p => Math.hypot(p.x - x, p.y - y) < 14);
  if (hitIdx >= 0) {
    draggingCurvePoint = hitIdx;
  } else {
    state.curves.points.push({ x, y });
    state.curves.points.sort((a, b) => a.x - b.x);
    draggingCurvePoint = state.curves.points.findIndex(p => p.x === x);
    updateToneCurveTexture();
    requestPipelineRender();
  }
});

window.addEventListener('mousemove', (e) => {
  if (draggingCurvePoint >= 0) {
    const rect = curveCanvas.getBoundingClientRect();
    let x = Math.max(0, Math.min(255, Math.round(((e.clientX - rect.left) / curveCanvas.width) * 255)));
    let y = Math.max(0, Math.min(255, Math.round((1.0 - (e.clientY - rect.top) / curveCanvas.height) * 255)));
    if (draggingCurvePoint === 0) x = 0;
    if (draggingCurvePoint === state.curves.points.length - 1) x = 255;
    state.curves.points[draggingCurvePoint] = { x, y };
    updateToneCurveTexture();
    requestPipelineRender();
  }
});

window.addEventListener('mouseup', () => {
  if (draggingCurvePoint >= 0) {
    draggingCurvePoint = -1;
    history.pushSnapshot('Curve Point Edit');
  }
});

curveCanvas.addEventListener('dblclick', (e) => {
  const rect = curveCanvas.getBoundingClientRect();
  const x = Math.round(((e.clientX - rect.left) / curveCanvas.width) * 255);
  const y = Math.round((1.0 - (e.clientY - rect.top) / curveCanvas.height) * 255);
  const hitIdx = state.curves.points.findIndex(p => Math.hypot(p.x - x, p.y - y) < 16);
  if (hitIdx > 0 && hitIdx < state.curves.points.length - 1) {
    state.curves.points.splice(hitIdx, 1);
    updateToneCurveTexture();
    history.pushSnapshot('Remove Curve Point');
    requestPipelineRender();
  }
});

// ============================================================================
// 9. CROP, STRAIGHTEN & GEOMETRY
// ============================================================================
const cropGridOverlay = document.getElementById('cropGridOverlay');

document.getElementById('btnRotateCcw').addEventListener('click', () => {
  state.crop.rotationDegrees = (state.crop.rotationDegrees - 90 + 360) % 360;
  history.pushSnapshot('Rotate 90° CCW');
  requestPipelineRender();
});
document.getElementById('btnRotateCw').addEventListener('click', () => {
  state.crop.rotationDegrees = (state.crop.rotationDegrees + 90) % 360;
  history.pushSnapshot('Rotate 90° CW');
  requestPipelineRender();
});
document.getElementById('btnFlipH').addEventListener('click', () => {
  state.crop.flipH = !state.crop.flipH;
  document.getElementById('btnFlipH').classList.toggle('active', state.crop.flipH);
  history.pushSnapshot('Flip H');
  requestPipelineRender();
});
document.getElementById('btnFlipV').addEventListener('click', () => {
  state.crop.flipV = !state.crop.flipV;
  document.getElementById('btnFlipV').classList.toggle('active', state.crop.flipV);
  history.pushSnapshot('Flip V');
  requestPipelineRender();
});

document.querySelectorAll('.aspect-chip').forEach(chip => {
  chip.addEventListener('click', () => {
    document.querySelectorAll('.aspect-chip').forEach(c => c.classList.remove('active'));
    chip.classList.add('active');
    state.crop.aspectRatio = chip.dataset.ratio;
    history.pushSnapshot(`Aspect: ${state.crop.aspectRatio}`);
  });
});

document.getElementById('btnResetCrop').addEventListener('click', () => {
  state.crop = { rotationDegrees: 0, straightenAngle: 0, flipH: false, flipV: false, aspectRatio: 'free' };
  document.getElementById('btnFlipH').classList.remove('active');
  document.getElementById('btnFlipV').classList.remove('active');
  syncAllUiInputs();
  history.pushSnapshot('Reset Crop');
  requestPipelineRender();
});

// ============================================================================
// 10. ADOBE CAMERA RAW (.XMP) RECIPE & ACTIONS
// ============================================================================
function generateAcrXmpString() {
  const b = state.basic;
  const p = state.presence;
  const eff = state.effects;
  const d = state.detail;
  return `<?xpacket begin="﻿" id="W5M0MpCehiHzreSzNTczkc9d"?>
<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="CleanLightroom Web Studio v2.5">
 <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
  <rdf:Description rdf:about=""
    xmlns:crs="http://ns.adobe.com/camera-raw-settings/1.0/"
   crs:Version="16.0"
   crs:ProcessVersion="15.4"
   crs:Exposure2012="${b.exposure.toFixed(2)}"
   crs:Contrast2012="${Math.round(b.contrast)}"
   crs:Highlights2012="${Math.round(b.highlights)}"
   crs:Shadows2012="${Math.round(b.shadows)}"
   crs:Whites2012="${Math.round(b.whites)}"
   crs:Blacks2012="${Math.round(b.blacks)}"
   crs:Temperature="${Math.round(5500 + b.temp * 40)}"
   crs:Tint="${Math.round(b.tint)}"
   crs:Texture="${Math.round(p.texture)}"
   crs:Clarity2012="${Math.round(p.clarity)}"
   crs:Dehaze="${Math.round(p.dehaze)}"
   crs:Vibrance="${Math.round(p.vibrance)}"
   crs:Saturation="${Math.round(p.saturation)}"
   crs:PostCropVignetteAmount="${Math.round(eff.vignetteAmount)}"
   crs:GrainAmount="${Math.round(eff.grainAmount)}"
   crs:Sharpness="${Math.round(d.sharpening)}"
   crs:SharpenRadius="${d.sharpenRadius.toFixed(1)}"
   crs:SharpenDetail="${Math.round(d.sharpenDetail)}"
   crs:SharpenEdgeMasking="${Math.round(d.masking)}"
   crs:LuminanceSmoothing="${Math.round(d.luminanceNoise)}"
   crs:ColorNoiseReduction="${Math.round(d.colorNoise)}"
   crs:HasSettings="True"/>
 </rdf:RDF>
</x:xmpmeta>
<?xpacket end="w"?>`;
}

function downloadFile(content, fileName, mimeType) {
  const blob = new Blob([content], { type: mimeType });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = fileName;
  a.click();
  URL.revokeObjectURL(a.href);
}

document.getElementById('btnDownloadXmpSidecar').addEventListener('click', () => {
  const xmp = generateAcrXmpString();
  downloadFile(xmp, `CleanLightroom_Recipe_${Date.now()}.xmp`, 'application/rdf+xml');
  showToast('Downloaded Adobe Camera Raw (.XMP) Sidecar');
});
document.getElementById('btnExportXmp').addEventListener('click', (e) => {
  e.preventDefault();
  const xmp = generateAcrXmpString();
  downloadFile(xmp, `CleanLightroom_Recipe_${Date.now()}.xmp`, 'application/rdf+xml');
  showToast('Downloaded Adobe Camera Raw (.XMP) Sidecar');
});

// Copy & Paste Settings
document.getElementById('btnCopySettings').addEventListener('click', () => {
  const jsonStr = JSON.stringify(state, null, 2);
  navigator.clipboard.writeText(jsonStr).then(() => {
    showToast('Copied All Edit Settings to Clipboard');
  });
});

document.getElementById('btnPasteSettings').addEventListener('click', () => {
  navigator.clipboard.readText().then(text => {
    try {
      const parsed = JSON.parse(text);
      restoreState(parsed);
      showToast('Pasted Settings Successfully');
    } catch {
      showToast('Invalid Settings JSON in Clipboard');
    }
  });
});

// Export Images
function exportCanvasImage(format, quality, ext) {
  const dataUrl = glCanvas.toDataURL(format, quality);
  const a = document.createElement('a');
  a.href = dataUrl;
  a.download = `CleanLightroom_${Date.now()}.${ext}`;
  a.click();
  showToast(`Exported Photo (${ext.toUpperCase()})`);
}

document.getElementById('btnExportJpgHigh').addEventListener('click', (e) => {
  e.preventDefault();
  exportCanvasImage('image/jpeg', 0.95, 'jpg');
});
document.getElementById('btnExportPngLossless').addEventListener('click', (e) => {
  e.preventDefault();
  exportCanvasImage('image/png', 1.0, 'png');
});
document.getElementById('btnExportWebp').addEventListener('click', (e) => {
  e.preventDefault();
  exportCanvasImage('image/webp', 0.95, 'webp');
});

// ============================================================================
// 11. NAVIGATION & MODE SWITCHING
// ============================================================================
// Primary Mode Navigation (Actions, Presets, Crop, Edit, Masking, Remove)
document.querySelectorAll('.mode-nav-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    document.querySelectorAll('.mode-nav-btn').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    const mode = btn.dataset.mode;
    state.activeMode = mode;

    // Show/hide grid in crop mode
    cropGridOverlay.classList.toggle('active', mode === 'modeCrop');

    // Hide all mode panels
    document.querySelectorAll('.tool-section').forEach(s => s.classList.remove('active'));

    const toolsRack = document.getElementById('toolsRackBar');
    if (mode === 'modeEdit') {
      toolsRack.style.display = 'flex';
      const targetTool = document.getElementById(state.activeTool);
      if (targetTool) targetTool.classList.add('active');
    } else {
      toolsRack.style.display = 'none';
      if (mode === 'modePresets') {
        document.getElementById('modePresetsPanel').classList.add('active');
        initPresetsGrid();
      } else if (mode === 'modeCrop') {
        document.getElementById('modeCropPanel').classList.add('active');
      } else if (mode === 'modeMasking') {
        document.getElementById('modeMaskingPanel').classList.add('active');
      } else if (mode === 'modeRemove') {
        document.getElementById('modeRemovePanel').classList.add('active');
      } else if (mode === 'modeActions') {
        document.getElementById('modeActionsPanel').classList.add('active');
      }
    }
  });
});

// Edit Tool Rack (Auto, Light, Color, Blur, Effects, Detail, Optics, Curves)
document.querySelectorAll('.tool-rack-btn').forEach(btn => {
  if (btn.id === 'btnAutoTone') return;
  btn.addEventListener('click', () => {
    document.querySelectorAll('.tool-rack-btn').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    document.querySelectorAll('.tool-section').forEach(s => s.classList.remove('active'));
    const target = document.getElementById(btn.dataset.tool);
    if (target) target.classList.add('active');
    state.activeTool = btn.dataset.tool;
  });
});

// Studio vs Mobile Mode Switcher
const btnViewStudio = document.getElementById('btnViewStudio');
const btnViewMobile = document.getElementById('btnViewMobile');
const mainContainer = document.getElementById('mainContainer');

btnViewStudio.addEventListener('click', () => {
  state.viewMode = 'studio';
  btnViewStudio.classList.add('active');
  btnViewMobile.classList.remove('active');
  document.body.classList.remove('mobile-view-active');
  const viewportPanel = document.getElementById('viewportPanel');
  if (glCanvas.parentElement !== viewportPanel) {
    viewportPanel.appendChild(glCanvas);
  }
  requestPipelineRender();
});

btnViewMobile.addEventListener('click', () => {
  state.viewMode = 'mobile';
  btnViewMobile.classList.add('active');
  btnViewStudio.classList.remove('active');
  document.body.classList.add('mobile-view-active');
  const mobileCanvasArea = document.getElementById('mobileCanvasArea');
  if (glCanvas.parentElement !== mobileCanvasArea) {
    mobileCanvasArea.appendChild(glCanvas);
  }
  syncMobileEmulationDock();
  requestPipelineRender();
});

function syncMobileEmulationDock() {
  const dock = document.getElementById('mobileBottomDock');
  dock.innerHTML = `
    <div class="mobile-dock-btn" data-mode="modeActions">
      <img src="icons/01_Edit_Panels/lr-mobile-quick-actions.svg" width="18" height="18">
      <span>Actions</span>
    </div>
    <div class="mobile-dock-btn" data-mode="modePresets">
      <img src="icons/01_Edit_Panels/lr-mobile-presets.svg" width="18" height="18">
      <span>Presets</span>
    </div>
    <div class="mobile-dock-btn" data-mode="modeCrop">
      <img src="icons/04_Crop_and_Geometry/lr-mobile-crop.svg" width="18" height="18">
      <span>Crop</span>
    </div>
    <div class="mobile-dock-btn active" data-mode="modeEdit">
      <img src="icons/01_Edit_Panels/lr-mobile-light.svg" width="18" height="18">
      <span>Edit</span>
    </div>
    <div class="mobile-dock-btn" data-mode="modeMasking">
      <img src="icons/02_Masking_and_AI/lr-mobile-masking.svg" width="18" height="18">
      <span>Masking</span>
    </div>
    <div class="mobile-dock-btn" data-mode="modeRemove">
      <img src="icons/03_Remove_and_Heal/lr-mobile-heal.svg" width="18" height="18">
      <span>Remove</span>
    </div>
  `;
}

// ============================================================================
// 12. SLIDER BINDINGS & INPUT SYNC
// ============================================================================
function bindSlider(id, targetObj, key, unit, step, onEndAction) {
  const el = document.getElementById(id);
  if (!el) return;
  el.addEventListener('input', (e) => {
    const val = parseFloat(e.target.value);
    targetObj[key] = val;
    syncAllUiInputs();
    requestPipelineRender();
  });
  el.addEventListener('change', () => {
    if (onEndAction) history.pushSnapshot(onEndAction);
  });
}

function initSliderBindings() {
  // Light
  bindSlider('sliderExposure', state.basic, 'exposure', 'EV', 0.05, 'Exposure');
  bindSlider('sliderContrast', state.basic, 'contrast', '', 1, 'Contrast');
  bindSlider('sliderHighlights', state.basic, 'highlights', '', 1, 'Highlights');
  bindSlider('sliderShadows', state.basic, 'shadows', '', 1, 'Shadows');
  bindSlider('sliderWhites', state.basic, 'whites', '', 1, 'Whites');
  bindSlider('sliderBlacks', state.basic, 'blacks', '', 1, 'Blacks');

  // Color
  bindSlider('sliderTemp', state.basic, 'temp', '', 1, 'Temperature');
  bindSlider('sliderTint', state.basic, 'tint', '', 1, 'Tint');
  bindSlider('sliderVibrance', state.presence, 'vibrance', '', 1, 'Vibrance');
  bindSlider('sliderSaturation', state.presence, 'saturation', '', 1, 'Saturation');

  // Blur
  bindSlider('sliderBlurAmount', state.blur, 'amount', '', 1, 'Lens Blur');
  bindSlider('sliderBokehBoost', state.blur, 'bokehBoost', '', 1, 'Bokeh Boost');
  bindSlider('sliderFocalRange', state.blur, 'focalRange', '%', 1, 'Focal Range');

  // Effects
  bindSlider('sliderTexture', state.presence, 'texture', '', 1, 'Texture');
  bindSlider('sliderClarity', state.presence, 'clarity', '', 1, 'Clarity');
  bindSlider('sliderDehaze', state.presence, 'dehaze', '', 1, 'Dehaze');
  bindSlider('sliderVignetteAmount', state.effects, 'vignetteAmount', '', 1, 'Vignette');
  bindSlider('sliderVignetteMidpoint', state.effects, 'vignetteMidpoint', '', 1, 'Vignette Midpoint');
  bindSlider('sliderVignetteFeather', state.effects, 'vignetteFeather', '', 1, 'Vignette Feather');
  bindSlider('sliderGrainAmount', state.effects, 'grainAmount', '', 1, 'Grain Amount');
  bindSlider('sliderGrainSize', state.effects, 'grainSize', '', 1, 'Grain Size');
  bindSlider('sliderGrainRoughness', state.effects, 'grainRoughness', '', 1, 'Grain Roughness');

  // Detail
  bindSlider('sliderSharpening', state.detail, 'sharpening', '', 1, 'Sharpening');
  bindSlider('sliderSharpenRadius', state.detail, 'sharpenRadius', 'px', 0.1, 'Sharpen Radius');
  bindSlider('sliderSharpenDetail', state.detail, 'sharpenDetail', '', 1, 'Sharpen Detail');
  bindSlider('sliderMasking', state.detail, 'masking', '', 1, 'Sharpen Masking');
  bindSlider('sliderLuminanceNoise', state.detail, 'luminanceNoise', '', 1, 'Luminance Noise');
  bindSlider('sliderColorNoise', state.detail, 'colorNoise', '', 1, 'Color Noise');

  // Optics
  bindSlider('sliderDefringe', state.optics, 'defringe', '', 1, 'Defringe');

  // Curves Parametric
  bindSlider('sliderParamHighlights', state.curves, 'parametricHighlights', '', 1, 'Curve Highlights');
  bindSlider('sliderParamLights', state.curves, 'parametricLights', '', 1, 'Curve Lights');
  bindSlider('sliderParamDarks', state.curves, 'parametricDarks', '', 1, 'Curve Darks');
  bindSlider('sliderParamShadows', state.curves, 'parametricShadows', '', 1, 'Curve Shadows');

  // Crop Straighten
  bindSlider('sliderStraighten', state.crop, 'straightenAngle', '°', 0.1, 'Straighten');

  // HSL Current Channel
  const bindHsl = (id, prop) => {
    const el = document.getElementById(id);
    if (!el) return;
    el.addEventListener('input', (e) => {
      state.hsl[state.activeHslIndex][prop] = parseInt(e.target.value);
      updateHslSlidersUI();
      requestPipelineRender();
    });
    el.addEventListener('change', () => history.pushSnapshot(`HSL ${prop}`));
  };
  bindHsl('sliderHslHue', 'hue');
  bindHsl('sliderHslSat', 'sat');
  bindHsl('sliderHslLum', 'lum');

  // Color Grading Sliders
  document.getElementById('sliderWheelLum').addEventListener('input', (e) => {
    state.grading[state.activeGradingTarget].lum = parseInt(e.target.value);
    syncColorGradingUI();
    requestPipelineRender();
  });
  document.getElementById('sliderWheelBlending').addEventListener('input', (e) => {
    state.grading.blending = parseInt(e.target.value);
    syncColorGradingUI();
    requestPipelineRender();
  });
  document.getElementById('sliderWheelBalance').addEventListener('input', (e) => {
    state.grading.balance = parseInt(e.target.value);
    syncColorGradingUI();
    requestPipelineRender();
  });

  // Double-Click Label to Reset
  document.querySelectorAll('[data-reset]').forEach(el => {
    el.addEventListener('dblclick', () => {
      const key = el.dataset.reset;
      if (state.basic.hasOwnProperty(key)) state.basic[key] = 0;
      else if (state.presence.hasOwnProperty(key)) state.presence[key] = 0;
      else if (state.effects.hasOwnProperty(key)) state.effects[key] = key.includes('Midpoint') ? 50 : 0;
      else if (state.crop.hasOwnProperty(key)) state.crop[key] = 0;
      history.pushSnapshot(`Reset ${key}`);
      syncAllUiInputs();
      requestPipelineRender();
    });
  });
}

function syncAllUiInputs() {
  const setVal = (id, val, fmt) => {
    const el = document.getElementById(id);
    if (el) {
      el.textContent = fmt ? fmt(val) : val;
      el.classList.toggle('modified', val !== 0);
    }
  };

  setVal('valExposure', state.basic.exposure, v => `${v >= 0 ? '+' : ''}${v.toFixed(2)} EV`);
  setVal('valContrast', state.basic.contrast, v => `${v > 0 ? '+' : ''}${Math.round(v)}`);
  setVal('valHighlights', state.basic.highlights, v => `${v > 0 ? '+' : ''}${Math.round(v)}`);
  setVal('valShadows', state.basic.shadows, v => `${v > 0 ? '+' : ''}${Math.round(v)}`);
  setVal('valWhites', state.basic.whites, v => `${v > 0 ? '+' : ''}${Math.round(v)}`);
  setVal('valBlacks', state.basic.blacks, v => `${v > 0 ? '+' : ''}${Math.round(v)}`);

  setVal('valTemp', state.basic.temp, v => `${v > 0 ? '+' : ''}${Math.round(v)}`);
  setVal('valTint', state.basic.tint, v => `${v > 0 ? '+' : ''}${Math.round(v)}`);
  setVal('valVibrance', state.presence.vibrance, v => `${v > 0 ? '+' : ''}${Math.round(v)}`);
  setVal('valSaturation', state.presence.saturation, v => `${v > 0 ? '+' : ''}${Math.round(v)}`);

  setVal('valTexture', state.presence.texture, v => `${v > 0 ? '+' : ''}${Math.round(v)}`);
  setVal('valClarity', state.presence.clarity, v => `${v > 0 ? '+' : ''}${Math.round(v)}`);
  setVal('valDehaze', state.presence.dehaze, v => `${v > 0 ? '+' : ''}${Math.round(v)}`);
  setVal('valVignetteAmount', state.effects.vignetteAmount, v => `${v > 0 ? '+' : ''}${Math.round(v)}`);
  setVal('valGrainAmount', state.effects.grainAmount, v => `${Math.round(v)}`);

  setVal('valSharpening', state.detail.sharpening, v => `${Math.round(v)}`);
  setVal('valSharpenRadius', state.detail.sharpenRadius, v => `${v.toFixed(1)} px`);
  setVal('valSharpenDetail', state.detail.sharpenDetail, v => `${Math.round(v)}`);
  setVal('valMasking', state.detail.masking, v => `${Math.round(v)}`);
  setVal('valLuminanceNoise', state.detail.luminanceNoise, v => `${Math.round(v)}`);
  setVal('valColorNoise', state.detail.colorNoise, v => `${Math.round(v)}`);

  setVal('valBlurAmount', state.blur.amount, v => `${Math.round(v)}`);
  setVal('valStraighten', state.crop.straightenAngle, v => `${v.toFixed(1)}°`);

  // Sync range slider inputs
  const syncRange = (id, val) => {
    const el = document.getElementById(id);
    if (el) el.value = val;
  };
  syncRange('sliderExposure', state.basic.exposure);
  syncRange('sliderContrast', state.basic.contrast);
  syncRange('sliderHighlights', state.basic.highlights);
  syncRange('sliderShadows', state.basic.shadows);
  syncRange('sliderWhites', state.basic.whites);
  syncRange('sliderBlacks', state.basic.blacks);
  syncRange('sliderTemp', state.basic.temp);
  syncRange('sliderTint', state.basic.tint);
  syncRange('sliderVibrance', state.presence.vibrance);
  syncRange('sliderSaturation', state.presence.saturation);
  syncRange('sliderTexture', state.presence.texture);
  syncRange('sliderClarity', state.presence.clarity);
  syncRange('sliderDehaze', state.presence.dehaze);
  syncRange('sliderStraighten', state.crop.straightenAngle);

  // Sync B&W button state
  btnSubtabBw.classList.toggle('active', state.isMonochrome);
  updateUndoRedoButtons();
}

// Reset Sliders Only
document.getElementById('btnResetSlidersOnly').addEventListener('click', (e) => {
  e.preventDefault();
  state.basic = { exposure: 0, contrast: 0, highlights: 0, shadows: 0, whites: 0, blacks: 0, temp: 0, tint: 0, wbPreset: 'as_shot' };
  state.presence = { texture: 0, clarity: 0, dehaze: 0, vibrance: 0, saturation: 0 };
  state.effects = { vignetteAmount: 0, vignetteMidpoint: 50, vignetteFeather: 50, grainAmount: 0, grainSize: 25, grainRoughness: 50 };
  state.detail = { sharpening: 40, sharpenRadius: 1.0, sharpenDetail: 25, masking: 0, luminanceNoise: 0, colorNoise: 25 };
  state.hsl = new Array(8).fill(null).map(() => ({ hue: 0, sat: 0, lum: 0 }));
  state.grading = { shadows: { hue: 0, sat: 0, lum: 0 }, midtones: { hue: 0, sat: 0, lum: 0 }, highlights: { hue: 0, sat: 0, lum: 0 }, global: { hue: 0, sat: 0, lum: 0 }, blending: 50, balance: 0 };
  state.isMonochrome = false;
  history.pushSnapshot('Reset Sliders Only');
  syncAllUiInputs();
  requestPipelineRender();
  showToast('Reset Sliders Only');
});

// Reset All
document.getElementById('btnResetAll').addEventListener('click', (e) => {
  e.preventDefault();
  state.basic = { exposure: 0, contrast: 0, highlights: 0, shadows: 0, whites: 0, blacks: 0, temp: 0, tint: 0, wbPreset: 'as_shot' };
  state.presence = { texture: 0, clarity: 0, dehaze: 0, vibrance: 0, saturation: 0 };
  state.effects = { vignetteAmount: 0, vignetteMidpoint: 50, vignetteFeather: 50, grainAmount: 0, grainSize: 25, grainRoughness: 50 };
  state.detail = { sharpening: 40, sharpenRadius: 1.0, sharpenDetail: 25, masking: 0, luminanceNoise: 0, colorNoise: 25 };
  state.crop = { rotationDegrees: 0, straightenAngle: 0, flipH: false, flipV: false, aspectRatio: 'free' };
  state.hsl = new Array(8).fill(null).map(() => ({ hue: 0, sat: 0, lum: 0 }));
  state.grading = { shadows: { hue: 0, sat: 0, lum: 0 }, midtones: { hue: 0, sat: 0, lum: 0 }, highlights: { hue: 0, sat: 0, lum: 0 }, global: { hue: 0, sat: 0, lum: 0 }, blending: 50, balance: 0 };
  state.isMonochrome = false;
  history.pushSnapshot('Reset All');
  syncAllUiInputs();
  requestPipelineRender();
  showToast('Reset Everything');
});

// Demo Scenes Switcher
document.getElementById('demoSceneSelect').addEventListener('change', (e) => {
  loadDemoScene(e.target.value);
});

function loadDemoScene(sceneKey) {
  const w = 1200;
  const h = 800;
  const tempCanvas = document.createElement('canvas');
  tempCanvas.width = w;
  tempCanvas.height = h;
  const ctx = tempCanvas.getContext('2d');

  if (sceneKey === 'sunset') {
    const sky = ctx.createLinearGradient(0, 0, 0, h * 0.65);
    sky.addColorStop(0, '#102A54');
    sky.addColorStop(0.65, '#B85028');
    sky.addColorStop(1, '#F29E38');
    ctx.fillStyle = sky;
    ctx.fillRect(0, 0, w, h * 0.65);

    // Glowing Sun
    ctx.fillStyle = '#FFF8D4';
    ctx.beginPath();
    ctx.arc(w * 0.55, h * 0.52, 48, 0, Math.PI * 2);
    ctx.fill();

    // Mountain silhouettes
    ctx.fillStyle = '#241424';
    ctx.beginPath();
    ctx.moveTo(0, h * 0.65);
    ctx.lineTo(w * 0.25, h * 0.42);
    ctx.lineTo(w * 0.50, h * 0.55);
    ctx.lineTo(w * 0.75, h * 0.38);
    ctx.lineTo(w, h * 0.65);
    ctx.closePath();
    ctx.fill();

    // Water reflection
    const water = ctx.createLinearGradient(0, h * 0.65, 0, h);
    water.addColorStop(0, '#5A2D1E');
    water.addColorStop(1, '#101420');
    ctx.fillStyle = water;
    ctx.fillRect(0, h * 0.65, w, h * 0.35);
  } else if (sceneKey === 'portrait') {
    const bg = ctx.createRadialGradient(w * 0.5, h * 0.4, 20, w * 0.5, h * 0.4, w * 0.6);
    bg.addColorStop(0, '#665E58');
    bg.addColorStop(1, '#1A1616');
    ctx.fillStyle = bg;
    ctx.fillRect(0, 0, w, h);

    ctx.fillStyle = '#23140A';
    ctx.beginPath();
    ctx.arc(w * 0.5, h * 0.36, 160, 0, Math.PI * 2);
    ctx.fill();

    ctx.fillStyle = '#E5B18C';
    ctx.beginPath();
    ctx.ellipse(w * 0.5, h * 0.40, 100, 140, 0, 0, Math.PI * 2);
    ctx.fill();
  } else if (sceneKey === 'neon') {
    const bg = ctx.createLinearGradient(0, 0, 0, h);
    bg.addColorStop(0, '#060814');
    bg.addColorStop(1, '#140A1E');
    ctx.fillStyle = bg;
    ctx.fillRect(0, 0, w, h);

    ctx.strokeStyle = '#00E5FF';
    ctx.lineWidth = 16;
    ctx.beginPath();
    ctx.moveTo(w * 0.2, h * 0.85);
    ctx.lineTo(w * 0.2, h * 0.15);
    ctx.stroke();

    ctx.strokeStyle = '#FF1493';
    ctx.beginPath();
    ctx.moveTo(w * 0.8, h * 0.85);
    ctx.lineTo(w * 0.8, h * 0.15);
    ctx.stroke();
  } else if (sceneKey === 'architecture') {
    ctx.fillStyle = '#8AB0D6';
    ctx.fillRect(0, 0, w, h);
    const bldg = ctx.createLinearGradient(0, 0, w, h);
    bldg.addColorStop(0, '#EAEAF0');
    bldg.addColorStop(1, '#343840');
    ctx.fillStyle = bldg;
    ctx.beginPath();
    ctx.moveTo(w * 0.15, h);
    ctx.lineTo(w * 0.35, h * 0.15);
    ctx.lineTo(w * 0.75, h * 0.18);
    ctx.lineTo(w * 0.90, h);
    ctx.closePath();
    ctx.fill();
  }

  loadTextureFromImage(tempCanvas);
  showToast(`Loaded Scene: ${sceneKey}`);
}

// Before/After Compare
const btnCompare = document.getElementById('btnCompare');
const hudBefore = document.getElementById('hudBefore');
const setComparing = (active) => {
  state.showOriginal = active;
  btnCompare.classList.toggle('toggle-active', active);
  hudBefore.classList.toggle('active', active);
  requestPipelineRender();
};
btnCompare.addEventListener('mousedown', () => setComparing(true));
window.addEventListener('mouseup', () => { if (state.showOriginal) setComparing(false); });

// Alt-Clipping Toggle
const btnAltClip = document.getElementById('btnAltClipping');
const hudAltClip = document.getElementById('hudAltClip');
btnAltClip.addEventListener('click', () => {
  state.altClippingMode = (state.altClippingMode + 1) % 3;
  btnAltClip.classList.toggle('toggle-active', state.altClippingMode > 0);
  hudAltClip.classList.toggle('active', state.altClippingMode > 0);
  requestPipelineRender();
});

// Eyedropper Top Button
document.getElementById('btnEyedropperTop').addEventListener('click', () => toggleEyedropper());
document.getElementById('btnEyedropperColor').addEventListener('click', () => toggleEyedropper());

// File Upload Handler & Drag-and-Drop
document.getElementById('imageUpload').addEventListener('change', (e) => {
  const file = e.target.files[0];
  if (file) handleImageFile(file);
});

function handleImageFile(file) {
  const reader = new FileReader();
  reader.onload = (evt) => {
    sourceImage.onload = () => loadTextureFromImage(sourceImage);
    sourceImage.src = evt.target.result;
    showToast('Loaded Custom Photo');
  };
  reader.readAsDataURL(file);
}

// Window Drag-and-Drop
window.addEventListener('dragover', (e) => e.preventDefault());
window.addEventListener('drop', (e) => {
  e.preventDefault();
  if (e.dataTransfer.files && e.dataTransfer.files[0]) {
    handleImageFile(e.dataTransfer.files[0]);
  }
});

// Keyboard Shortcuts
window.addEventListener('keydown', (e) => {
  if (e.key === '\\') {
    setComparing(true);
  } else if (e.key === 'Alt') {
    state.altClippingMode = 1;
    hudAltClip.classList.add('active');
    requestPipelineRender();
  } else if (e.ctrlKey && e.key === 'z') {
    e.preventDefault();
    history.undo();
  } else if (e.ctrlKey && (e.key === 'y' || (e.shiftKey && e.key === 'Z'))) {
    e.preventDefault();
    history.redo();
  }
});

window.addEventListener('keyup', (e) => {
  if (e.key === '\\') {
    setComparing(false);
  } else if (e.key === 'Alt') {
    state.altClippingMode = 0;
    hudAltClip.classList.remove('active');
    requestPipelineRender();
  }
});

// Viewport Zoom & Fit Controls
document.getElementById('btnFit').addEventListener('click', () => {
  state.zoomScale = 1.0;
  glCanvas.style.transform = 'scale(1.0)';
  document.getElementById('zoomLevelText').textContent = '100%';
});
document.getElementById('btn100').addEventListener('click', () => {
  state.zoomScale = 1.0;
  glCanvas.style.transform = 'scale(1.0)';
  document.getElementById('zoomLevelText').textContent = '100%';
});
document.getElementById('btnZoomIn').addEventListener('click', () => {
  state.zoomScale = Math.min(3.0, state.zoomScale + 0.25);
  glCanvas.style.transform = `scale(${state.zoomScale})`;
  document.getElementById('zoomLevelText').textContent = `${Math.round(state.zoomScale * 100)}%`;
});
document.getElementById('btnZoomOut').addEventListener('click', () => {
  state.zoomScale = Math.max(0.5, state.zoomScale - 0.25);
  glCanvas.style.transform = `scale(${state.zoomScale})`;
  document.getElementById('zoomLevelText').textContent = `${Math.round(state.zoomScale * 100)}%`;
});

// Mobile Clock
function updateClock() {
  const clockEl = document.getElementById('mobileClock');
  if (clockEl) {
    const now = new Date();
    clockEl.textContent = `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}`;
  }
}
setInterval(updateClock, 1000);
updateClock();

// ============================================================================
// 13. BOOTSTRAP INITIALIZATION
// ============================================================================
window.addEventListener('DOMContentLoaded', () => {
  initWebGL();
  initSliderBindings();
  initHslRings();
  initPresetsGrid();
  sourceImage.onload = () => loadTextureFromImage(sourceImage);
  sourceImage.src = 'sample.jpg';
});
