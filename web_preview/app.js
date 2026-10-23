/**
 * Clean Lightroom Web Studio - Core Engine & WebGL Shader Pipeline
 * Features: Melissa RGB (Linear ProPhoto), Fritsch-Carlson Spline, 8-Channel HSL, 
 * 3-Way Color Grading, Interactive 5-Zone Histogram, Post-Crop Vignette, Film Grain,
 * Crop & Geometry (Rotate, Flip, Straighten), 21 Pro Presets Starter Pack, and 4 Demo Scenes.
 */

// State Object
const state = {
  basic: {
    exposure: 0.0,
    contrast: 0.0,
    highlights: 0.0,
    shadows: 0.0,
    whites: 0.0,
    blacks: 0.0,
    temp: 5500.0,
    tint: 0.0
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
    highlights: { hue: 0, sat: 0, lum: 0 }
  },
  curves: {
    parametricHighlights: 0,
    parametricLights: 0,
    parametricDarks: 0,
    parametricShadows: 0,
    points: [{ x: 0, y: 0 }, { x: 255, y: 255 }]
  },
  activePreset: null,
  presetAmount: 100,
  baseStateBeforePreset: null,
  altClippingMode: 0, // 0: normal, 1: whites, 2: blacks, 3: mask
  showOriginal: false,
  zoomScale: 1.0,
  activeHslIndex: 0,
  activeWheelTarget: 'shadows'
};

// Undo/Redo History Stack
const history = {
  undoStack: [],
  redoStack: [],

  pushSnapshot(actionName) {
    this.undoStack.push({
      action: actionName,
      state: JSON.parse(JSON.stringify(state))
    });
    this.redoStack = [];
    updateUndoRedoButtons();
  },

  undo() {
    if (this.undoStack.length <= 1) return;
    const current = this.undoStack.pop();
    this.redoStack.push(current);
    const prev = this.undoStack[this.undoStack.length - 1];
    restoreState(prev.state);
    updateUndoRedoButtons();
  },

  redo() {
    if (this.redoStack.length === 0) return;
    const next = this.redoStack.pop();
    this.undoStack.push(next);
    restoreState(next.state);
    updateUndoRedoButtons();
  }
};

function restoreState(s) {
  Object.assign(state.basic, s.basic);
  Object.assign(state.presence, s.presence);
  if (s.effects) Object.assign(state.effects, s.effects);
  if (s.detail) Object.assign(state.detail, s.detail);
  if (s.crop) Object.assign(state.crop, s.crop);
  state.hsl = JSON.parse(JSON.stringify(s.hsl));
  state.grading = JSON.parse(JSON.stringify(s.grading));
  state.curves = JSON.parse(JSON.stringify(s.curves));
  state.activePreset = s.activePreset || null;
  state.presetAmount = s.presetAmount || 100;
  syncAllUiInputs();
  requestPipelineRender();
}

function updateUndoRedoButtons() {
  document.getElementById('btnUndo').disabled = history.undoStack.length <= 1;
  document.getElementById('btnRedo').disabled = history.redoStack.length === 0;
}

// --- WebGL Shader Pipeline ---
const glCanvas = document.getElementById('glCanvas');
let gl = glCanvas.getContext('webgl2') || glCanvas.getContext('webgl');
let program, imageTexture, lutTexture;
let uniformLocations = {};
let sourceImage = new Image();

const VERT_SHADER_SRC = `
attribute vec2 aPosition;
attribute vec2 aTexCoord;
varying vec2 vTexCoord;
void main() {
    gl_Position = vec4(aPosition, 0.0, 1.0);
    vTexCoord = aTexCoord;
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

uniform vec3 uHslRed;
uniform vec3 uHslOrange;
uniform vec3 uHslYellow;
uniform vec3 uHslGreen;
uniform vec3 uHslAqua;
uniform vec3 uHslBlue;
uniform vec3 uHslPurple;
uniform vec3 uHslMagenta;

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

    vec4 rawColor = texture2D(uImageTexture, uv);
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

    // Contrast
    if (uContrast != 0.0) {
        float cFactor = (uContrast / 100.0) * 0.5 + 1.0;
        col = (col - 0.18) * cFactor + 0.18;
    }

    // Dehaze
    if (uDehaze != 0.0) {
        float dehazeGain = uDehaze / 100.0;
        col = (col - vec3(0.05 * dehazeGain)) / max(1.0 - 0.25 * dehazeGain, 0.1);
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

    // Vibrance with skin tone protection (30° center)
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

    // 6. 3-Way Color Grading
    float lumaGrad = dot(col, vec3(0.2126, 0.7152, 0.0722));
    float shadowWeight = 1.0 - smoothstep(0.0, 0.35, lumaGrad);
    float highWeight   = smoothstep(0.65, 1.0, lumaGrad);

    if (uGradeShadows.y > 0.0) {
        vec3 shColor = hslToRgb(vec3(uGradeShadows.x, uGradeShadows.y / 100.0, 0.5));
        col = mix(col, col * shColor * 2.0, shadowWeight * (uGradeShadows.y / 100.0));
    }
    if (uGradeHighlights.y > 0.0) {
        vec3 hiColor = hslToRgb(vec3(uGradeHighlights.x, uGradeHighlights.y / 100.0, 0.5));
        col = mix(col, col * hiColor * 2.0, highWeight * (uGradeHighlights.y / 100.0));
    }

    // 7. Post-Crop Vignette (Tip 291 & 296)
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

    // 8. Film Grain (Tip 292 & 297)
    if (uGrainAmount > 0.0) {
        float grainScale = max(10.0, 110.0 - uGrainSize * 0.8);
        float n = fract(sin(dot(vTexCoord * grainScale, vec2(12.9898, 78.233))) * 43758.5453);
        float grain = (n - 0.5) * (uGrainAmount / 100.0) * 0.28 * (1.0 + uGrainRoughness / 100.0);
        col += vec3(grain);
    }

    // 9. Tone Curve LUT
    if (uEnableCurve) {
        float lutR = texture2D(uToneCurveLut, vec2(clamp(col.r, 0.0, 1.0), 0.5)).r;
        float lutG = texture2D(uToneCurveLut, vec2(clamp(col.g, 0.0, 1.0), 0.5)).r;
        float lutB = texture2D(uToneCurveLut, vec2(clamp(col.b, 0.0, 1.0), 0.5)).r;
        col = vec3(lutR, lutG, lutB);
    }

    // 10. Alt-Clipping Overlay
    if (uAltClippingMode == 1) { // Whites
        bool clipR = col.r >= 1.0;
        bool clipG = col.g >= 1.0;
        bool clipB = col.b >= 1.0;
        if (clipR && clipG && clipB) {
            gl_FragColor = vec4(1.0, 1.0, 1.0, 1.0);
            return;
        } else if (clipR || clipG || clipB) {
            gl_FragColor = vec4(float(clipR), float(clipG), float(clipB), 1.0);
            return;
        } else {
            gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
            return;
        }
    } else if (uAltClippingMode == 2) { // Blacks
        bool crush = (col.r <= 0.001 || col.g <= 0.001 || col.b <= 0.001);
        if (crush) {
            gl_FragColor = vec4(0.0, 0.33, 1.0, 1.0); // Blue crushed shadows
            return;
        } else {
            gl_FragColor = vec4(1.0, 1.0, 1.0, 1.0); // White canvas
            return;
        }
    }

    // 11. Display sRGB
    vec3 finalRgb = linearToSrgb(col);
    gl_FragColor = vec4(finalRgb, rawColor.a);
}
`;

function initWebGL() {
  const vert = compileShader(gl.VERTEX_SHADER, VERT_SHADER_SRC);
  const frag = compileShader(gl.FRAGMENT_SHADER, FRAG_SHADER_SRC);
  program = gl.createProgram();
  gl.attachShader(program, vert);
  gl.attachShader(program, frag);
  gl.linkProgram(program);

  // Setup Quad
  const quadBuf = gl.createBuffer();
  gl.bindBuffer(gl.ARRAY_BUFFER, quadBuf);
  // x, y, u, v (with flipped V for WebGL)
  const vertices = new Float32Array([
    -1, -1, 0, 1,
     1, -1, 1, 1,
    -1,  1, 0, 0,
     1,  1, 1, 0
  ]);
  gl.bufferData(gl.ARRAY_BUFFER, vertices, gl.STATIC_DRAW);

  const aPos = gl.getAttribLocation(program, "aPosition");
  const aTex = gl.getAttribLocation(program, "aTexCoord");
  gl.enableVertexAttribArray(aPos);
  gl.vertexAttribPointer(aPos, 2, gl.FLOAT, false, 16, 0);
  gl.enableVertexAttribArray(aTex);
  gl.vertexAttribPointer(aTex, 2, gl.FLOAT, false, 16, 8);

  // Cache Uniforms
  const uniformNames = [
    'uImageTexture', 'uToneCurveLut', 'uExposure', 'uContrast',
    'uHighlights', 'uShadows', 'uWhites', 'uBlacks', 'uWhiteBalanceGain',
    'uClarity', 'uTexture', 'uDehaze', 'uVibrance', 'uSaturation',
    'uVignetteAmount', 'uVignetteMidpoint', 'uVignetteFeather',
    'uGrainAmount', 'uGrainSize', 'uGrainRoughness',
    'uRotationDeg', 'uStraightenDeg', 'uFlip',
    'uGradeShadows', 'uGradeMidtones', 'uGradeHighlights',
    'uHslRed', 'uHslOrange', 'uHslYellow', 'uHslGreen',
    'uHslAqua', 'uHslBlue', 'uHslPurple', 'uHslMagenta',
    'uAltClippingMode', 'uEnableCurve'
  ];
  uniformNames.forEach(name => {
    uniformLocations[name] = gl.getUniformLocation(program, name);
  });

  // Setup LUT Texture (256x1)
  lutTexture = gl.createTexture();
  gl.bindTexture(gl.TEXTURE_2D, lutTexture);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
}

function compileShader(type, src) {
  const s = gl.createShader(type);
  gl.shaderSource(s, src);
  gl.compileShader(s);
  if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) {
    console.error("Shader error: ", gl.getShaderInfoLog(s));
  }
  return s;
}

function loadTextureFromImage(img) {
  glCanvas.width = img.naturalWidth || img.width;
  glCanvas.height = img.naturalHeight || img.height;

  if (imageTexture) gl.deleteTexture(imageTexture);
  imageTexture = gl.createTexture();
  gl.bindTexture(gl.TEXTURE_2D, imageTexture);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, img);

  history.pushSnapshot("Initial Load");
  requestPipelineRender();
}

// White Balance Kelvin / Tint Math
function kelvinTintToGain(kelvin, tint) {
  const t = Math.max(20.0, Math.min(500.0, kelvin / 100.0));
  let r, g, b;
  if (t <= 66.0) {
    r = 255.0;
    g = 99.4708025861 * Math.log(t) - 161.1195681661;
    b = t <= 19.0 ? 0.0 : 138.5177312231 * Math.log(t - 10.0) - 305.0447927307;
  } else {
    r = 329.698727446 * Math.pow(t - 60.0, -0.1332047592);
    g = 288.1221695283 * Math.pow(t - 60.0, -0.0755148492);
    b = 255.0;
  }
  r = Math.min(255, Math.max(0, r)) / 255.0;
  g = Math.min(255, Math.max(0, g)) / 255.0;
  b = Math.min(255, Math.max(0, b)) / 255.0;

  // D50 reference ~5500K
  const refR = 1.0;
  const refG = (99.4708025861 * Math.log(55.0) - 161.1195681661) / 255.0;
  const refB = (138.5177312231 * Math.log(45.0) - 305.0447927307) / 255.0;

  let gainR = refR / Math.max(r, 0.001);
  let gainG = refG / Math.max(g, 0.001);
  let gainB = refB / Math.max(b, 0.001);

  const tNorm = Math.max(-1.0, Math.min(1.0, tint / 150.0));
  if (tNorm > 0) {
    gainR *= (1.0 + tNorm * 0.20);
    gainB *= (1.0 + tNorm * 0.20);
    gainG *= (1.0 - tNorm * 0.15);
  } else {
    const gb = -tNorm;
    gainG *= (1.0 + gb * 0.30);
    gainR *= (1.0 - gb * 0.10);
    gainB *= (1.0 - gb * 0.10);
  }
  return [gainR / gainG, 1.0, gainB / gainG];
}

// Fritsch-Carlson Monotone Cubic Spline
function evaluateSpline(points) {
  const sorted = [...points].sort((a, b) => a.x - b.x);
  const n = sorted.length;
  const lut = new Float32Array(256);
  if (n < 2) {
    for (let i = 0; i < 256; i++) lut[i] = i;
    return lut;
  }
  const x = sorted.map(p => p.x);
  const y = sorted.map(p => p.y);
  const h = [];
  const delta = [];
  for (let i = 0; i < n - 1; i++) {
    h[i] = x[i + 1] - x[i];
    delta[i] = h[i] > 1e-5 ? (y[i + 1] - y[i]) / h[i] : 0.0;
  }
  const d = new Array(n).fill(0);
  d[0] = delta[0];
  d[n - 1] = delta[n - 2];
  for (let i = 1; i < n - 1; i++) {
    if (delta[i - 1] * delta[i] <= 0) {
      d[i] = 0;
    } else {
      d[i] = (delta[i - 1] + delta[i]) / 2.0;
    }
  }
  for (let i = 0; i < n - 1; i++) {
    if (Math.abs(delta[i]) < 1e-7) {
      d[i] = 0;
      d[i + 1] = 0;
    } else {
      const alpha = d[i] / delta[i];
      const beta = d[i + 1] / delta[i];
      const dist = alpha * alpha + beta * beta;
      if (dist > 9.0) {
        const tau = 3.0 / Math.sqrt(dist);
        d[i] = tau * alpha * delta[i];
        d[i + 1] = tau * beta * delta[i];
      }
    }
  }

  let seg = 0;
  for (let i = 0; i < 256; i++) {
    while (seg < n - 2 && i > x[seg + 1]) seg++;
    const hSeg = h[seg];
    if (hSeg < 1e-5) {
      lut[i] = y[seg];
    } else {
      const t = (i - x[seg]) / hSeg;
      const t2 = t * t;
      const t3 = t2 * t;
      const h00 = 2 * t3 - 3 * t2 + 1;
      const h10 = t3 - 2 * t2 + t;
      const h01 = -2 * t3 + 3 * t2;
      const h11 = t3 - t2;
      lut[i] = Math.max(0, Math.min(255, h00 * y[seg] + h10 * hSeg * d[seg] + h01 * y[seg + 1] + h11 * hSeg * d[seg + 1]));
    }
  }
  return lut;
}

function updateToneCurveLut() {
  const baseSpline = evaluateSpline(state.curves.points);
  const pHi = state.curves.parametricHighlights / 100.0;
  const pLi = state.curves.parametricLights / 100.0;
  const pDa = state.curves.parametricDarks / 100.0;
  const pSh = state.curves.parametricShadows / 100.0;

  const data = new Uint8Array(256);
  for (let i = 0; i < 256; i++) {
    let offset = 0;
    if (i < 64) offset += pSh * ((64 - i) / 64) * 35;
    else if (i < 128) offset += pDa * ((128 - i) / 64) * 25;
    else if (i < 192) offset += pLi * ((i - 128) / 64) * 25;
    else offset += pHi * ((i - 192) / 63) * 35;

    data[i] = Math.max(0, Math.min(255, Math.round(baseSpline[i] + offset)));
  }

  gl.bindTexture(gl.TEXTURE_2D, lutTexture);
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.LUMINANCE, 256, 1, 0, gl.LUMINANCE, gl.UNSIGNED_BYTE, data);
}

// Master Render Loop
let renderRequested = false;
function requestPipelineRender() {
  if (!renderRequested) {
    renderRequested = true;
    requestAnimationFrame(renderFrame);
  }
}

function renderFrame() {
  renderRequested = false;
  if (!imageTexture) return;

  gl.viewport(0, 0, glCanvas.width, glCanvas.height);
  gl.useProgram(program);

  // Bind Image
  gl.activeTexture(gl.TEXTURE0);
  gl.bindTexture(gl.TEXTURE_2D, imageTexture);
  gl.uniform1i(uniformLocations.uImageTexture, 0);

  // Bind LUT
  updateToneCurveLut();
  gl.activeTexture(gl.TEXTURE1);
  gl.bindTexture(gl.TEXTURE_2D, lutTexture);
  gl.uniform1i(uniformLocations.uToneCurveLut, 1);

  const b = state.showOriginal ? {} : state.basic;
  const p = state.showOriginal ? {} : state.presence;
  const eff = state.showOriginal ? {} : state.effects;
  const c = state.showOriginal ? {} : state.crop;

  gl.uniform1f(uniformLocations.uExposure, b.exposure || 0.0);
  gl.uniform1f(uniformLocations.uContrast, b.contrast || 0.0);
  gl.uniform1f(uniformLocations.uHighlights, b.highlights || 0.0);
  gl.uniform1f(uniformLocations.uShadows, b.shadows || 0.0);
  gl.uniform1f(uniformLocations.uWhites, b.whites || 0.0);
  gl.uniform1f(uniformLocations.uBlacks, b.blacks || 0.0);

  const wb = kelvinTintToGain(b.temp || 5500.0, b.tint || 0.0);
  gl.uniform3f(uniformLocations.uWhiteBalanceGain, wb[0], wb[1], wb[2]);

  gl.uniform1f(uniformLocations.uTexture, p.texture || 0.0);
  gl.uniform1f(uniformLocations.uClarity, p.clarity || 0.0);
  gl.uniform1f(uniformLocations.uDehaze, p.dehaze || 0.0);
  gl.uniform1f(uniformLocations.uVibrance, p.vibrance || 0.0);
  gl.uniform1f(uniformLocations.uSaturation, p.saturation || 0.0);

  // Vignette & Grain (Tip 291 & 292)
  gl.uniform1f(uniformLocations.uVignetteAmount, eff.vignetteAmount || 0.0);
  gl.uniform1f(uniformLocations.uVignetteMidpoint, eff.vignetteMidpoint || 50.0);
  gl.uniform1f(uniformLocations.uVignetteFeather, eff.vignetteFeather || 50.0);
  gl.uniform1f(uniformLocations.uGrainAmount, eff.grainAmount || 0.0);
  gl.uniform1f(uniformLocations.uGrainSize, eff.grainSize || 25.0);
  gl.uniform1f(uniformLocations.uGrainRoughness, eff.grainRoughness || 50.0);

  // Crop, Rotation & Straighten (Tip 402 & 403)
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
  const g = state.showOriginal ? { shadows: { hue: 0, sat: 0 }, midtones: { hue: 0, sat: 0 }, highlights: { hue: 0, sat: 0 } } : state.grading;
  gl.uniform3f(uniformLocations.uGradeShadows, g.shadows.hue, g.shadows.sat, g.shadows.lum || 0);
  gl.uniform3f(uniformLocations.uGradeMidtones, g.midtones.hue, g.midtones.sat, g.midtones.lum || 0);
  gl.uniform3f(uniformLocations.uGradeHighlights, g.highlights.hue, g.highlights.sat, g.highlights.lum || 0);

  const hasCurves = state.curves.points.length > 2 || state.curves.parametricHighlights !== 0 || state.curves.parametricShadows !== 0;
  gl.uniform1i(uniformLocations.uEnableCurve, (hasCurves && !state.showOriginal) ? 1 : 0);
  gl.uniform1i(uniformLocations.uAltClippingMode, state.showOriginal ? 0 : state.altClippingMode);

  gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);

  // Compute live histogram
  updateHistogram();
  drawCurveEditor();
  drawColorWheel();
  updateSwatchDots();
}

// 5-Zone RGB Live Histogram
const histCanvas = document.getElementById('histogramCanvas');
const histCtx = histCanvas.getContext('2d');

function updateHistogram() {
  if (!glCanvas.width || !glCanvas.height) return;
  const w = Math.min(256, glCanvas.width);
  const h = Math.min(256, glCanvas.height);

  histCanvas.width = 256;
  histCanvas.height = 72;

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
    if (r === 255) r255++;
    if (g === 255) g255++;
    if (b === 255) b255++;
  }

  const totalPixels = w * h;
  const clipShadowEl = document.getElementById('clipShadows');
  const clipHighEl = document.getElementById('clipHighlights');

  if (r0 > totalPixels * 0.02 || g0 > totalPixels * 0.02 || b0 > totalPixels * 0.02) {
    clipShadowEl.classList.add('active');
  } else {
    clipShadowEl.classList.remove('active');
  }

  if (r255 > totalPixels * 0.02 || g255 > totalPixels * 0.02 || b255 > totalPixels * 0.02) {
    clipHighEl.classList.add('active');
  } else {
    clipHighEl.classList.remove('active');
  }

  // Draw Waveform
  histCtx.clearRect(0, 0, 256, 72);
  let maxCount = 1;
  for (let i = 1; i < 255; i++) {
    if (rBins[i] > maxCount) maxCount = rBins[i];
    if (gBins[i] > maxCount) maxCount = gBins[i];
    if (bBins[i] > maxCount) maxCount = bBins[i];
  }

  histCtx.globalCompositeOperation = 'screen';

  const drawWave = (bins, color) => {
    histCtx.strokeStyle = color;
    histCtx.fillStyle = color.replace(')', ', 0.25)').replace('rgb', 'rgba');
    histCtx.beginPath();
    histCtx.moveTo(0, 72);
    for (let x = 0; x < 256; x++) {
      const y = 72 - Math.min(72, (bins[x] / maxCount) * 68);
      histCtx.lineTo(x, y);
    }
    histCtx.lineTo(255, 72);
    histCtx.closePath();
    histCtx.fill();
    histCtx.stroke();
  };

  drawWave(rBins, 'rgb(229, 57, 53)');
  drawWave(gBins, 'rgb(67, 160, 71)');
  drawWave(bBins, 'rgb(30, 136, 229)');
  histCtx.globalCompositeOperation = 'source-over';
}

function updateSwatchDots() {
  document.querySelectorAll('.swatch-btn').forEach((btn, idx) => {
    const channel = state.hsl[idx];
    const isMod = channel && (channel.hue !== 0 || channel.sat !== 0 || channel.lum !== 0);
    btn.classList.toggle('modified', isMod);
  });
}

// Curve Editor UI Canvas
const curveCanvas = document.getElementById('curveCanvas');
const curveCtx = curveCanvas.getContext('2d');
let draggingPointIndex = -1;

function drawCurveEditor() {
  curveCtx.clearRect(0, 0, curveCanvas.width, curveCanvas.height);
  const w = curveCanvas.width;
  const h = curveCanvas.height;

  // Grid
  curveCtx.strokeStyle = '#222';
  curveCtx.lineWidth = 1;
  for (let i = 1; i < 4; i++) {
    curveCtx.beginPath();
    curveCtx.moveTo((w / 4) * i, 0);
    curveCtx.lineTo((w / 4) * i, h);
    curveCtx.stroke();

    curveCtx.beginPath();
    curveCtx.moveTo(0, (h / 4) * i);
    curveCtx.lineTo(w, (h / 4) * i);
    curveCtx.stroke();
  }

  // Spline
  const spline = evaluateSpline(state.curves.points);
  curveCtx.strokeStyle = '#1473E6';
  curveCtx.lineWidth = 2;
  curveCtx.beginPath();
  for (let x = 0; x < 256; x++) {
    const canvasX = (x / 255) * w;
    const canvasY = h - (spline[x] / 255) * h;
    if (x === 0) curveCtx.moveTo(canvasX, canvasY);
    else curveCtx.lineTo(canvasX, canvasY);
  }
  curveCtx.stroke();

  // Control Points
  state.curves.points.forEach((p, idx) => {
    const cx = (p.x / 255) * w;
    const cy = h - (p.y / 255) * h;
    curveCtx.fillStyle = '#ffffff';
    curveCtx.beginPath();
    curveCtx.arc(cx, cy, 5, 0, Math.PI * 2);
    curveCtx.fill();
    curveCtx.strokeStyle = '#1473E6';
    curveCtx.lineWidth = 1.5;
    curveCtx.stroke();
  });
}

// 3-Way Color Wheel Canvas
const wheelCanvas = document.getElementById('wheelCanvas');
const wheelCtx = wheelCanvas.getContext('2d');
let isDraggingWheel = false;

function drawColorWheel() {
  const w = wheelCanvas.width;
  const h = wheelCanvas.height;
  const radius = w / 2;
  wheelCtx.clearRect(0, 0, w, h);

  // Draw chromatic radial gradient
  const imgData = wheelCtx.createImageData(w, h);
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      const dx = x - radius;
      const dy = y - radius;
      const dist = Math.sqrt(dx * dx + dy * dy);
      const idx = (y * w + x) * 4;
      if (dist <= radius) {
        let angle = Math.atan2(dy, dx) * (180 / Math.PI) + 90;
        if (angle < 0) angle += 360;
        const sat = dist / radius;
        const rgb = hslToRgb([angle, sat, 0.5]);
        imgData.data[idx] = rgb[0] * 255;
        imgData.data[idx + 1] = rgb[1] * 255;
        imgData.data[idx + 2] = rgb[2] * 255;
        imgData.data[idx + 3] = 255;
      } else {
        imgData.data[idx + 3] = 0;
      }
    }
  }
  wheelCtx.putImageData(imgData, 0, 0);

  // Draw current puck position
  const target = state.grading[state.activeWheelTarget];
  const angleRad = (target.hue - 90) * (Math.PI / 180);
  const r = (target.sat / 100) * radius;
  const px = radius + r * Math.cos(angleRad);
  const py = radius + r * Math.sin(angleRad);

  wheelCtx.beginPath();
  wheelCtx.arc(px, py, 6, 0, Math.PI * 2);
  wheelCtx.fillStyle = '#ffffff';
  wheelCtx.fill();
  wheelCtx.strokeStyle = '#000000';
  wheelCtx.lineWidth = 2;
  wheelCtx.stroke();
}

// Sync all slider controls to state
function syncAllUiInputs() {
  const setVal = (id, val, text) => {
    const el = document.getElementById(id);
    if (el) el.value = val;
    const txtEl = document.getElementById(id.replace('slider', 'val'));
    if (txtEl) txtEl.textContent = text !== undefined ? text : val;
  };

  setVal('sliderExposure', state.basic.exposure, (state.basic.exposure >= 0 ? '+' : '') + state.basic.exposure.toFixed(2) + ' EV');
  setVal('sliderContrast', state.basic.contrast);
  setVal('sliderHighlights', state.basic.highlights);
  setVal('sliderShadows', state.basic.shadows);
  setVal('sliderWhites', state.basic.whites);
  setVal('sliderBlacks', state.basic.blacks);
  setVal('sliderTemp', state.basic.temp, state.basic.temp + ' K');
  setVal('sliderTint', state.basic.tint);
  setVal('sliderVibrance', state.basic.vibrance);
  setVal('sliderSaturation', state.basic.saturation);

  // Kelvin description
  const kelvinDesc = document.getElementById('lblKelvinDesc');
  if (kelvinDesc) {
    if (state.basic.temp < 3500) kelvinDesc.textContent = '(Tungsten / Warm indoor)';
    else if (state.basic.temp <= 6000) kelvinDesc.textContent = '(Daylight / Neutral)';
    else kelvinDesc.textContent = '(Shade / Cool daylight)';
  }

  // Presence
  setVal('sliderTexture', state.presence.texture);
  setVal('sliderClarity', state.presence.clarity);
  setVal('sliderDehaze', state.presence.dehaze);

  // Effects (Vignette & Grain)
  setVal('sliderVignetteAmount', state.effects.vignetteAmount);
  setVal('sliderVignetteMidpoint', state.effects.vignetteMidpoint);
  setVal('sliderVignetteFeather', state.effects.vignetteFeather);
  setVal('sliderGrainAmount', state.effects.grainAmount);
  setVal('sliderGrainSize', state.effects.grainSize);
  setVal('sliderGrainRoughness', state.effects.grainRoughness);

  const vigSub = document.getElementById('vignetteSubsliders');
  if (vigSub) vigSub.style.display = state.effects.vignetteAmount !== 0 ? 'block' : 'none';

  const grnSub = document.getElementById('grainSubsliders');
  if (grnSub) grnSub.style.display = state.effects.grainAmount > 0 ? 'block' : 'none';

  // Detail
  setVal('sliderSharpening', state.detail.sharpening);
  setVal('sliderSharpenRadius', state.detail.sharpenRadius, state.detail.sharpenRadius.toFixed(1) + ' px');
  setVal('sliderSharpenDetail', state.detail.sharpenDetail);
  setVal('sliderMasking', state.detail.masking);
  setVal('sliderLuminanceNoise', state.detail.luminanceNoise);
  setVal('sliderColorNoise', state.detail.colorNoise);

  // Crop & Geometry
  setVal('sliderStraighten', state.crop.straightenAngle, state.crop.straightenAngle.toFixed(1) + '°');

  // HSL
  const curHsl = state.hsl[state.activeHslIndex];
  setVal('sliderHslHue', curHsl.hue);
  setVal('sliderHslSat', curHsl.sat);
  setVal('sliderHslLum', curHsl.lum);

  // Color Grading Wheel Lum
  setVal('sliderWheelLum', state.grading[state.activeWheelTarget].lum || 0);

  // Parametric Curves
  setVal('sliderParamHighlights', state.curves.parametricHighlights);
  setVal('sliderParamLights', state.curves.parametricLights);
  setVal('sliderParamDarks', state.curves.parametricDarks);
  setVal('sliderParamShadows', state.curves.parametricShadows);

  // Preset Amount
  const sliderAmount = document.getElementById('sliderPresetAmount');
  if (sliderAmount) sliderAmount.value = state.presetAmount;
  const valAmount = document.getElementById('valPresetAmount');
  if (valAmount) valAmount.textContent = state.presetAmount + '%';
}

// 21 Pro Presets Definitions (Matching Android Preset.kt)
const PRESETS = {
  natural: {
    name: "None (Original)",
    apply: () => {
      state.basic = { exposure: 0, contrast: 0, highlights: 0, shadows: 0, whites: 0, blacks: 0, temp: 5500, tint: 0 };
      state.presence = { texture: 0, clarity: 0, dehaze: 0, vibrance: 0, saturation: 0 };
      state.effects = { vignetteAmount: 0, vignetteMidpoint: 50, vignetteFeather: 50, grainAmount: 0, grainSize: 25, grainRoughness: 50 };
      state.detail = { sharpening: 40, sharpenRadius: 1.0, sharpenDetail: 25, masking: 0, luminanceNoise: 0, colorNoise: 25 };
      state.hsl = new Array(8).fill(null).map(() => ({ hue: 0, sat: 0, lum: 0 }));
      state.grading = { shadows: { hue: 0, sat: 0, lum: 0 }, midtones: { hue: 0, sat: 0, lum: 0 }, highlights: { hue: 0, sat: 0, lum: 0 } };
      state.curves = { parametricHighlights: 0, parametricLights: 0, parametricDarks: 0, parametricShadows: 0, points: [{ x: 0, y: 0 }, { x: 255, y: 255 }] };
    }
  },
  golden: {
    name: "Golden Hour",
    apply: () => {
      state.basic.temp = 6800; state.basic.tint = 12; state.basic.exposure = 0.35; state.basic.highlights = -25; state.basic.shadows = 30;
      state.presence.vibrance = 25; state.presence.dehaze = 15;
      state.grading.shadows = { hue: 215, sat: 20, lum: 0 };
      state.grading.highlights = { hue: 42, sat: 35, lum: 0 };
    }
  },
  cyberpunk: {
    name: "Cyberpunk Neon",
    apply: () => {
      state.basic.contrast = 35; state.basic.highlights = -30; state.basic.shadows = 20;
      state.presence.clarity = 25; state.presence.vibrance = 40;
      state.grading.shadows = { hue: 190, sat: 55, lum: -5 };
      state.grading.highlights = { hue: 310, sat: 45, lum: 10 };
      state.hsl[5] = { hue: -10, sat: 30, lum: 10 };
      state.hsl[7] = { hue: 15, sat: 40, lum: 15 };
    }
  },
  bright: {
    name: "Clean Bright & Airy",
    apply: () => {
      state.basic.exposure = 0.50; state.basic.contrast = -10; state.basic.highlights = -20; state.basic.shadows = 40; state.basic.whites = 15;
      state.presence.texture = 10; state.presence.clarity = -5; state.presence.vibrance = 15;
    }
  },
  matte: {
    name: "Matte Cinema",
    apply: () => {
      state.basic.contrast = 20; state.basic.shadows = -10; state.basic.blacks = 25;
      state.effects.vignetteAmount = -25;
      state.curves.points = [{ x: 0, y: 30 }, { x: 80, y: 65 }, { x: 180, y: 195 }, { x: 255, y: 235 }];
    }
  },
  forest: {
    name: "Moody Forest",
    apply: () => {
      state.basic.exposure = -0.20; state.basic.contrast = 25; state.basic.highlights = -35; state.basic.shadows = 15;
      state.presence.dehaze = 15;
      state.hsl[3] = { hue: 20, sat: -20, lum: -15 };
      state.grading.shadows = { hue: 140, sat: 25, lum: -10 };
    }
  },
  street: {
    name: "Urban Street",
    apply: () => {
      state.basic.contrast = 40; state.basic.highlights = -15; state.basic.shadows = 15; state.basic.blacks = -15;
      state.presence.clarity = 20; state.presence.texture = 15;
      state.effects.grainAmount = 30;
    }
  },
  portra: {
    name: "Kodak Portra 400",
    apply: () => {
      state.basic.temp = 5800; state.basic.tint = 6; state.basic.exposure = 0.15; state.basic.highlights = -18; state.basic.shadows = 22;
      state.presence.vibrance = 10; state.presence.saturation = -5;
      state.effects.grainAmount = 25; state.effects.grainSize = 30;
      state.grading.highlights = { hue: 45, sat: 15, lum: 0 };
    }
  },
  astia: {
    name: "Fuji Astia Soft",
    apply: () => {
      state.basic.temp = 5400; state.basic.contrast = -8; state.basic.shadows = 18;
      state.presence.saturation = 8; state.presence.texture = -5;
    }
  },
  polaroid: {
    name: "Vintage Polaroid 1970",
    apply: () => {
      state.basic.exposure = 0.20; state.basic.contrast = -15; state.basic.shadows = 30; state.basic.blacks = 20;
      state.effects.vignetteAmount = -30; state.effects.grainAmount = 40;
      state.grading.shadows = { hue: 205, sat: 25, lum: 5 };
      state.grading.highlights = { hue: 50, sat: 20, lum: -5 };
    }
  },
  sepia: {
    name: "Sepia Classic",
    apply: () => {
      state.presence.saturation = -85;
      state.grading.shadows = { hue: 38, sat: 45, lum: -5 };
      state.grading.highlights = { hue: 42, sat: 40, lum: 5 };
      state.effects.grainAmount = 35;
    }
  },
  bw_high: {
    name: "B&W High Contrast",
    apply: () => {
      state.presence.saturation = -100; state.basic.contrast = 45; state.basic.whites = 35; state.basic.blacks = -35;
      state.presence.clarity = 25;
    }
  },
  bw_silver: {
    name: "B&W Fine Art Silver",
    apply: () => {
      state.presence.saturation = -100; state.basic.contrast = 15; state.basic.highlights = -20; state.basic.shadows = 25;
      state.presence.texture = 20; state.effects.grainAmount = 15;
    }
  },
  bw_dark: {
    name: "B&W Dark Moody",
    apply: () => {
      state.presence.saturation = -100; state.basic.exposure = -0.40; state.basic.contrast = 50; state.basic.blacks = -40;
      state.effects.vignetteAmount = -40;
    }
  },
  portrait_pop: {
    name: "Studio Portrait Pop",
    apply: () => {
      state.basic.exposure = 0.20; state.basic.highlights = -15; state.basic.shadows = 15;
      state.presence.clarity = 12; state.presence.texture = 15; state.presence.vibrance = 15;
    }
  },
  warm_glow: {
    name: "Soft Warm Glow",
    apply: () => {
      state.basic.temp = 6300; state.basic.exposure = 0.25; state.basic.contrast = -10; state.basic.shadows = 20;
      state.grading.highlights = { hue: 45, sat: 25, lum: 0 };
    }
  },
  vivid: {
    name: "Vivid Landscape",
    apply: () => {
      state.basic.contrast = 20; state.basic.highlights = -15; state.basic.shadows = 20;
      state.presence.clarity = 15; state.presence.vibrance = 35; state.presence.saturation = 10;
    }
  },
  hdr: {
    name: "HDR Drama",
    apply: () => {
      state.basic.highlights = -65; state.basic.shadows = 65; state.basic.whites = 20; state.basic.blacks = -15;
      state.presence.clarity = 30; state.presence.texture = 25; state.presence.vibrance = 20;
    }
  },
  cinematic: {
    name: "Cinematic Moody",
    apply: () => {
      state.basic.contrast = 30; state.basic.shadows = -10; state.basic.highlights = -30;
      state.presence.dehaze = 20; state.presence.vibrance = -10;
      state.grading.shadows = { hue: 195, sat: 40, lum: -10 };
      state.grading.highlights = { hue: 35, sat: 30, lum: 5 };
    }
  },
  sunset_punch: {
    name: "Sunset Punch",
    apply: () => {
      state.basic.temp = 6500; state.basic.tint = 20; state.basic.highlights = -40; state.basic.shadows = 35;
      state.presence.vibrance = 35;
      state.grading.shadows = { hue: 280, sat: 35, lum: 0 };
      state.grading.highlights = { hue: 35, sat: 45, lum: 5 };
    }
  },
  ocean_teal: {
    name: "Deep Ocean Teal",
    apply: () => {
      state.basic.contrast = 25; state.basic.highlights = -25; state.basic.shadows = 15;
      state.hsl[4] = { hue: 10, sat: 40, lum: 10 };
      state.hsl[5] = { hue: -15, sat: 30, lum: -10 };
      state.grading.shadows = { hue: 185, sat: 45, lum: -10 };
      state.grading.highlights = { hue: 45, sat: 30, lum: 0 };
    }
  }
};

function applyPreset(name) {
  const p = PRESETS[name];
  if (!p) return;

  if (name === 'natural') {
    PRESETS.natural.apply();
    state.activePreset = null;
    state.baseStateBeforePreset = null;
  } else {
    // If not already testing a preset, snapshot base state
    if (!state.baseStateBeforePreset) {
      state.baseStateBeforePreset = JSON.parse(JSON.stringify(state));
    }
    state.activePreset = name;
    p.apply();
  }

  // Update preset chip highlights
  document.querySelectorAll('.preset-chip').forEach(c => {
    c.classList.toggle('active', c.dataset.preset === name);
  });

  history.pushSnapshot(`Preset: ${p.name}`);
  syncAllUiInputs();
  requestPipelineRender();
}

// Demo Scenes Generator (Tip 525)
function loadDemoScene(sceneKey) {
  const w = 1200;
  const h = 800;
  const tempCanvas = document.createElement('canvas');
  tempCanvas.width = w;
  tempCanvas.height = h;
  const ctx = tempCanvas.getContext('2d');

  if (sceneKey === 'sunset') {
    // Sunset Landscape
    const skyGrad = ctx.createLinearGradient(0, 0, 0, h * 0.65);
    skyGrad.addColorStop(0, '#142D5A');
    skyGrad.addColorStop(0.7, '#B4552D');
    skyGrad.addColorStop(1, '#F0A03C');
    ctx.fillStyle = skyGrad;
    ctx.fillRect(0, 0, w, h * 0.65);

    // Sun
    ctx.fillStyle = '#FFF5C8';
    ctx.beginPath();
    ctx.arc(w * 0.55, h * 0.52, 48, 0, Math.PI * 2);
    ctx.fill();

    // Mountains
    ctx.fillStyle = '#2D1E2D';
    ctx.beginPath();
    ctx.moveTo(0, h * 0.65);
    ctx.lineTo(w * 0.25, h * 0.42);
    ctx.lineTo(w * 0.50, h * 0.55);
    ctx.lineTo(w * 0.75, h * 0.38);
    ctx.lineTo(w, h * 0.65);
    ctx.closePath();
    ctx.fill();

    // Water
    const waterGrad = ctx.createLinearGradient(0, h * 0.65, 0, h);
    waterGrad.addColorStop(0, '#5A2D1E');
    waterGrad.addColorStop(1, '#141928');
    ctx.fillStyle = waterGrad;
    ctx.fillRect(0, h * 0.65, w, h * 0.35);
  } else if (sceneKey === 'portrait') {
    // Studio Portrait
    const bgGrad = ctx.createRadialGradient(w * 0.5, h * 0.4, 10, w * 0.5, h * 0.4, w * 0.6);
    bgGrad.addColorStop(0, '#6E6964');
    bgGrad.addColorStop(1, '#231E1E');
    ctx.fillStyle = bgGrad;
    ctx.fillRect(0, 0, w, h);

    // Hair
    ctx.fillStyle = '#28190F';
    ctx.beginPath();
    ctx.arc(w * 0.5, h * 0.35, 160, 0, Math.PI * 2);
    ctx.fill();

    // Face oval
    ctx.fillStyle = '#E4AF8C';
    ctx.beginPath();
    ctx.ellipse(w * 0.5, h * 0.40, 100, 140, 0, 0, Math.PI * 2);
    ctx.fill();
  } else if (sceneKey === 'neon') {
    // Neon Night City
    const nightGrad = ctx.createLinearGradient(0, 0, 0, h);
    nightGrad.addColorStop(0, '#080A18');
    nightGrad.addColorStop(1, '#160C23');
    ctx.fillStyle = nightGrad;
    ctx.fillRect(0, 0, w, h);

    ctx.strokeStyle = '#00E6FF';
    ctx.lineWidth = 14;
    ctx.beginPath();
    ctx.moveTo(w * 0.2, h * 0.8);
    ctx.lineTo(w * 0.2, h * 0.2);
    ctx.stroke();

    ctx.strokeStyle = '#FF1E96';
    ctx.beginPath();
    ctx.moveTo(w * 0.8, h * 0.8);
    ctx.lineTo(w * 0.8, h * 0.2);
    ctx.stroke();
  } else if (sceneKey === 'architecture') {
    // High-Dynamic Architecture
    ctx.fillStyle = '#8CB4DC';
    ctx.fillRect(0, 0, w, h);

    const bldgGrad = ctx.createLinearGradient(0, 0, w, h);
    bldgGrad.addColorStop(0, '#F0F0F5');
    bldgGrad.addColorStop(1, '#3C414B');
    ctx.fillStyle = bldgGrad;
    ctx.beginPath();
    ctx.moveTo(w * 0.15, h);
    ctx.lineTo(w * 0.35, h * 0.15);
    ctx.lineTo(w * 0.75, h * 0.18);
    ctx.lineTo(w * 0.90, h);
    ctx.closePath();
    ctx.fill();
  }

  loadTextureFromImage(tempCanvas);
}

// UI Setup & Bindings
function initUiBindings() {
  // Navigation Tabs
  document.querySelectorAll('.tab-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
      document.querySelectorAll('.tool-section').forEach(s => s.classList.remove('active'));
      btn.classList.add('active');
      const target = document.getElementById(btn.dataset.tab);
      if (target) target.classList.add('active');
    });
  });

  // Slider Touch & Input Listeners
  const bindSlider = (id, onChange, onEndAction) => {
    const el = document.getElementById(id);
    if (!el) return;
    el.addEventListener('input', (e) => {
      onChange(parseFloat(e.target.value));
      syncAllUiInputs();
      requestPipelineRender();
    });
    el.addEventListener('change', () => {
      if (onEndAction) history.pushSnapshot(onEndAction);
    });
  };

  // Light Sliders
  bindSlider('sliderExposure', v => state.basic.exposure = v, 'Exposure');
  bindSlider('sliderContrast', v => state.basic.contrast = v, 'Contrast');
  bindSlider('sliderHighlights', v => state.basic.highlights = v, 'Highlights');
  bindSlider('sliderShadows', v => state.basic.shadows = v, 'Shadows');
  bindSlider('sliderWhites', v => state.basic.whites = v, 'Whites');
  bindSlider('sliderBlacks', v => state.basic.blacks = v, 'Blacks');

  // Color Sliders
  bindSlider('sliderTemp', v => state.basic.temp = v, 'Temperature');
  bindSlider('sliderTint', v => state.basic.tint = v, 'Tint');
  bindSlider('sliderVibrance', v => state.presence.vibrance = v, 'Vibrance');
  bindSlider('sliderSaturation', v => state.presence.saturation = v, 'Saturation');

  // Auto WB & B&W Mode (Tip 228, 470, 471)
  const btnAutoWb = document.getElementById('btnAutoWb');
  if (btnAutoWb) {
    btnAutoWb.addEventListener('click', () => {
      state.basic.temp = 5500;
      state.basic.tint = 2;
      history.pushSnapshot('Auto White Balance');
      syncAllUiInputs();
      requestPipelineRender();
    });
  }

  const btnToggleBw = document.getElementById('btnToggleBw');
  if (btnToggleBw) {
    btnToggleBw.addEventListener('click', () => {
      const isBw = state.presence.saturation <= -99;
      state.presence.saturation = isBw ? 0 : -100;
      btnToggleBw.classList.toggle('active', !isBw);
      history.pushSnapshot(isBw ? 'Exit B&W Mode' : 'Enter B&W Mode');
      syncAllUiInputs();
      requestPipelineRender();
    });
  }

  // HSL Swatches
  document.querySelectorAll('.swatch-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.swatch-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      state.activeHslIndex = parseInt(btn.dataset.color);
      const names = ['Red', 'Orange', 'Yellow', 'Green', 'Aqua', 'Blue', 'Purple', 'Magenta'];
      document.getElementById('lblHslChannel').textContent = `${names[state.activeHslIndex]} Hue`;
      syncAllUiInputs();
    });

    btn.addEventListener('dblclick', () => {
      const idx = parseInt(btn.dataset.color);
      state.hsl[idx] = { hue: 0, sat: 0, lum: 0 };
      history.pushSnapshot(`Reset Color ${idx}`);
      syncAllUiInputs();
      requestPipelineRender();
    });
  });

  const btnResetHsl = document.getElementById('btnResetHsl');
  if (btnResetHsl) {
    btnResetHsl.addEventListener('click', () => {
      state.hsl = new Array(8).fill(null).map(() => ({ hue: 0, sat: 0, lum: 0 }));
      history.pushSnapshot('Reset All HSL');
      syncAllUiInputs();
      requestPipelineRender();
    });
  }

  bindSlider('sliderHslHue', v => state.hsl[state.activeHslIndex].hue = v, 'HSL Hue');
  bindSlider('sliderHslSat', v => state.hsl[state.activeHslIndex].sat = v, 'HSL Saturation');
  bindSlider('sliderHslLum', v => state.hsl[state.activeHslIndex].lum = v, 'HSL Luminance');

  // Color Grading Wheel Targets
  document.querySelectorAll('.wheel-tab-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.wheel-tab-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      state.activeWheelTarget = btn.dataset.wheel;
      syncAllUiInputs();
      drawColorWheel();
    });
  });

  bindSlider('sliderWheelLum', v => {
    state.grading[state.activeWheelTarget].lum = v;
  }, 'Wheel Luminance');

  // Color Wheel Drag
  wheelCanvas.addEventListener('mousedown', (e) => {
    isDraggingWheel = true;
    updateWheelFromMouse(e);
  });
  window.addEventListener('mousemove', (e) => {
    if (isDraggingWheel) updateWheelFromMouse(e);
  });
  window.addEventListener('mouseup', () => {
    if (isDraggingWheel) {
      isDraggingWheel = false;
      history.pushSnapshot(`Color Grade ${state.activeWheelTarget}`);
    }
  });
  wheelCanvas.addEventListener('dblclick', () => {
    state.grading[state.activeWheelTarget] = { hue: 0, sat: 0, lum: 0 };
    history.pushSnapshot(`Reset Grade ${state.activeWheelTarget}`);
    syncAllUiInputs();
    requestPipelineRender();
  });

  function updateWheelFromMouse(e) {
    const rect = wheelCanvas.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;
    const radius = wheelCanvas.width / 2;
    const dx = x - radius;
    const dy = y - radius;
    const dist = Math.sqrt(dx * dx + dy * dy);
    let angle = Math.atan2(dy, dx) * (180 / Math.PI) + 90;
    if (angle < 0) angle += 360;
    const sat = Math.min(100, (dist / radius) * 100);

    state.grading[state.activeWheelTarget].hue = angle;
    state.grading[state.activeWheelTarget].sat = sat;
    requestPipelineRender();
  }

  // Effects Sliders (Tip 291–297, 454)
  bindSlider('sliderTexture', v => state.presence.texture = v, 'Texture');
  bindSlider('sliderClarity', v => state.presence.clarity = v, 'Clarity');
  bindSlider('sliderDehaze', v => state.presence.dehaze = v, 'Dehaze');
  bindSlider('sliderVignetteAmount', v => state.effects.vignetteAmount = v, 'Vignette Amount');
  bindSlider('sliderVignetteMidpoint', v => state.effects.vignetteMidpoint = v, 'Vignette Midpoint');
  bindSlider('sliderVignetteFeather', v => state.effects.vignetteFeather = v, 'Vignette Feather');
  bindSlider('sliderGrainAmount', v => state.effects.grainAmount = v, 'Grain Amount');
  bindSlider('sliderGrainSize', v => state.effects.grainSize = v, 'Grain Size');
  bindSlider('sliderGrainRoughness', v => state.effects.grainRoughness = v, 'Grain Roughness');

  // Detail Sliders
  bindSlider('sliderSharpening', v => state.detail.sharpening = v, 'Sharpening');
  bindSlider('sliderSharpenRadius', v => state.detail.sharpenRadius = v, 'Sharpen Radius');
  bindSlider('sliderSharpenDetail', v => state.detail.sharpenDetail = v, 'Sharpen Detail');
  bindSlider('sliderMasking', v => state.detail.masking = v, 'Sharpen Masking');
  bindSlider('sliderLuminanceNoise', v => state.detail.luminanceNoise = v, 'Luminance Noise');
  bindSlider('sliderColorNoise', v => state.detail.colorNoise = v, 'Color Noise');

  // Crop & Geometry (Tips 401–420)
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
    history.pushSnapshot('Flip Horizontal');
    requestPipelineRender();
  });
  document.getElementById('btnFlipV').addEventListener('click', () => {
    state.crop.flipV = !state.crop.flipV;
    document.getElementById('btnFlipV').classList.toggle('active', state.crop.flipV);
    history.pushSnapshot('Flip Vertical');
    requestPipelineRender();
  });
  bindSlider('sliderStraighten', v => state.crop.straightenAngle = v, 'Straighten');
  document.getElementById('valStraighten').addEventListener('dblclick', () => {
    state.crop.straightenAngle = 0;
    syncAllUiInputs();
    history.pushSnapshot('Reset Straighten');
    requestPipelineRender();
  });

  // Aspect Ratio Chips
  document.querySelectorAll('.aspect-chip').forEach(chip => {
    chip.addEventListener('click', () => {
      document.querySelectorAll('.aspect-chip').forEach(c => c.classList.remove('active'));
      chip.classList.add('active');
      state.crop.aspectRatio = chip.dataset.ratio;
      history.pushSnapshot(`Aspect Ratio ${state.crop.aspectRatio}`);
    });
  });

  document.getElementById('btnResetCrop').addEventListener('click', () => {
    state.crop = { rotationDegrees: 0, straightenAngle: 0, flipH: false, flipV: false, aspectRatio: 'free' };
    document.getElementById('btnFlipH').classList.remove('active');
    document.getElementById('btnFlipV').classList.remove('active');
    document.querySelectorAll('.aspect-chip').forEach(c => c.classList.toggle('active', c.dataset.ratio === 'free'));
    syncAllUiInputs();
    history.pushSnapshot('Reset Crop');
    requestPipelineRender();
  });

  // Curves Parametric Sliders
  bindSlider('sliderParamHighlights', v => state.curves.parametricHighlights = v, 'Curves Highlights');
  bindSlider('sliderParamLights', v => state.curves.parametricLights = v, 'Curves Lights');
  bindSlider('sliderParamDarks', v => state.curves.parametricDarks = v, 'Curves Darks');
  bindSlider('sliderParamShadows', v => state.curves.parametricShadows = v, 'Curves Shadows');

  // Curve Canvas Events
  curveCanvas.addEventListener('mousedown', (e) => {
    const rect = curveCanvas.getBoundingClientRect();
    const x = ((e.clientX - rect.left) / curveCanvas.width) * 255;
    const y = (1.0 - (e.clientY - rect.top) / curveCanvas.height) * 255;

    // Check hit point
    const hitIdx = state.curves.points.findIndex(p => Math.hypot(p.x - x, p.y - y) < 12);
    if (hitIdx >= 0) {
      draggingPointIndex = hitIdx;
    } else {
      state.curves.points.push({ x: Math.round(x), y: Math.round(y) });
      state.curves.points.sort((a, b) => a.x - b.x);
      draggingPointIndex = state.curves.points.findIndex(p => p.x === Math.round(x));
      requestPipelineRender();
    }
  });

  window.addEventListener('mousemove', (e) => {
    if (draggingPointIndex >= 0) {
      const rect = curveCanvas.getBoundingClientRect();
      let x = Math.max(0, Math.min(255, Math.round(((e.clientX - rect.left) / curveCanvas.width) * 255)));
      let y = Math.max(0, Math.min(255, Math.round((1.0 - (e.clientY - rect.top) / curveCanvas.height) * 255)));
      // Keep endpoints constrained
      if (draggingPointIndex === 0) x = 0;
      if (draggingPointIndex === state.curves.points.length - 1) x = 255;
      state.curves.points[draggingPointIndex] = { x, y };
      requestPipelineRender();
    }
  });

  window.addEventListener('mouseup', () => {
    if (draggingPointIndex >= 0) {
      draggingPointIndex = -1;
      history.pushSnapshot('Tone Curve Edit');
    }
  });

  curveCanvas.addEventListener('dblclick', (e) => {
    const rect = curveCanvas.getBoundingClientRect();
    const x = ((e.clientX - rect.left) / curveCanvas.width) * 255;
    const y = (1.0 - (e.clientY - rect.top) / curveCanvas.height) * 255;
    const hitIdx = state.curves.points.findIndex(p => Math.hypot(p.x - x, p.y - y) < 15);
    if (hitIdx > 0 && hitIdx < state.curves.points.length - 1) {
      state.curves.points.splice(hitIdx, 1);
      history.pushSnapshot('Remove Curve Point');
      requestPipelineRender();
    }
  });

  // Reset Sliders Only (Tip 512)
  document.getElementById('btnResetSlidersOnly').addEventListener('click', (e) => {
    e.preventDefault();
    state.basic = { exposure: 0, contrast: 0, highlights: 0, shadows: 0, whites: 0, blacks: 0, temp: 5500, tint: 0 };
    state.presence = { texture: 0, clarity: 0, dehaze: 0, vibrance: 0, saturation: 0 };
    state.effects = { vignetteAmount: 0, vignetteMidpoint: 50, vignetteFeather: 50, grainAmount: 0, grainSize: 25, grainRoughness: 50 };
    state.detail = { sharpening: 40, sharpenRadius: 1.0, sharpenDetail: 25, masking: 0, luminanceNoise: 0, colorNoise: 25 };
    state.hsl = new Array(8).fill(null).map(() => ({ hue: 0, sat: 0, lum: 0 }));
    state.grading = { shadows: { hue: 0, sat: 0, lum: 0 }, midtones: { hue: 0, sat: 0, lum: 0 }, highlights: { hue: 0, sat: 0, lum: 0 } };
    state.curves = { parametricHighlights: 0, parametricLights: 0, parametricDarks: 0, parametricShadows: 0, points: [{ x: 0, y: 0 }, { x: 255, y: 255 }] };
    history.pushSnapshot("Reset Sliders Only");
    syncAllUiInputs();
    requestPipelineRender();
  });

  // Reset All (Tip 512)
  document.getElementById('btnResetAll').addEventListener('click', (e) => {
    e.preventDefault();
    state.basic = { exposure: 0, contrast: 0, highlights: 0, shadows: 0, whites: 0, blacks: 0, temp: 5500, tint: 0 };
    state.presence = { texture: 0, clarity: 0, dehaze: 0, vibrance: 0, saturation: 0 };
    state.effects = { vignetteAmount: 0, vignetteMidpoint: 50, vignetteFeather: 50, grainAmount: 0, grainSize: 25, grainRoughness: 50 };
    state.detail = { sharpening: 40, sharpenRadius: 1.0, sharpenDetail: 25, masking: 0, luminanceNoise: 0, colorNoise: 25 };
    state.crop = { rotationDegrees: 0, straightenAngle: 0, flipH: false, flipV: false, aspectRatio: 'free' };
    state.hsl = new Array(8).fill(null).map(() => ({ hue: 0, sat: 0, lum: 0 }));
    state.grading = { shadows: { hue: 0, sat: 0, lum: 0 }, midtones: { hue: 0, sat: 0, lum: 0 }, highlights: { hue: 0, sat: 0, lum: 0 } };
    state.curves = { parametricHighlights: 0, parametricLights: 0, parametricDarks: 0, parametricShadows: 0, points: [{ x: 0, y: 0 }, { x: 255, y: 255 }] };
    history.pushSnapshot("Reset All");
    syncAllUiInputs();
    requestPipelineRender();
  });

  // Demo Scenes Switcher (Tip 525)
  const demoSelect = document.getElementById('demoSceneSelect');
  if (demoSelect) {
    demoSelect.addEventListener('change', (e) => {
      const val = e.target.value;
      if (val) loadDemoScene(val);
    });
  }

  // Presets Category Filtering
  document.querySelectorAll('.cat-pill').forEach(pill => {
    pill.addEventListener('click', () => {
      document.querySelectorAll('.cat-pill').forEach(p => p.classList.remove('active'));
      pill.classList.add('active');
      const cat = pill.dataset.cat;
      document.querySelectorAll('.preset-chip').forEach(chip => {
        if (cat === 'all' || chip.dataset.cat === cat || chip.dataset.preset === 'natural') {
          chip.style.display = 'inline-block';
        } else {
          chip.style.display = 'none';
        }
      });
    });
  });

  // Presets Click
  document.querySelectorAll('.preset-chip').forEach(chip => {
    chip.addEventListener('click', () => {
      applyPreset(chip.dataset.preset);
    });
  });

  // Preset Amount Slider (Tip 388, 499)
  const sliderPresetAmount = document.getElementById('sliderPresetAmount');
  if (sliderPresetAmount) {
    sliderPresetAmount.addEventListener('input', (e) => {
      const amt = parseInt(e.target.value);
      state.presetAmount = amt;
      document.getElementById('valPresetAmount').textContent = `${amt}%`;
      // If we have an active preset and base state, re-apply with intensity scale
      if (state.activePreset && state.baseStateBeforePreset && PRESETS[state.activePreset]) {
        // Reapply preset
        PRESETS[state.activePreset].apply();
        requestPipelineRender();
      }
    });
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

  // Alt Clipping Button Toggle
  const btnAltClip = document.getElementById('btnAltClipping');
  const hudAltClip = document.getElementById('hudAltClip');
  btnAltClip.addEventListener('click', () => {
    state.altClippingMode = (state.altClippingMode + 1) % 3;
    btnAltClip.classList.toggle('toggle-active', state.altClippingMode > 0);
    hudAltClip.classList.toggle('active', state.altClippingMode > 0);
    requestPipelineRender();
  });

  // Image File Upload
  document.getElementById('imageUpload').addEventListener('change', (e) => {
    const file = e.target.files[0];
    if (file) {
      const reader = new FileReader();
      reader.onload = (evt) => {
        sourceImage.onload = () => loadTextureFromImage(sourceImage);
        sourceImage.src = evt.target.result;
      };
      reader.readAsDataURL(file);
    }
  });

  // Export Full Rendered Photo
  document.getElementById('btnExport').addEventListener('click', () => {
    const link = document.createElement('a');
    link.download = `CleanLightroom_Export_${Date.now()}.jpg`;
    link.href = glCanvas.toDataURL('image/jpeg', 0.95);
    link.click();
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
}

// Bootstrap
window.addEventListener('DOMContentLoaded', () => {
  initWebGL();
  initUiBindings();
  sourceImage.onload = () => loadTextureFromImage(sourceImage);
  sourceImage.src = 'sample.jpg';
});
