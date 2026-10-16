/**
 * Clean Lightroom Web Studio - Core Engine & WebGL Shader Pipeline
 * Features: Melissa RGB (Linear ProPhoto), Fritsch-Carlson Spline, 8-Channel HSL, 
 * 3-Way Color Grading, Interactive 5-Zone Histogram, Before/After & Alt-Clipping.
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
  detail: {
    sharpening: 40.0,
    masking: 0.0,
    noiseReduction: 0.0
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
  snapshot: null,

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
  Object.assign(state.detail, s.detail);
  state.hsl = JSON.parse(JSON.stringify(s.hsl));
  state.grading = JSON.parse(JSON.stringify(s.grading));
  state.curves = JSON.parse(JSON.stringify(s.curves));
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
    vec4 rawColor = texture2D(uImageTexture, vTexCoord);
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

    // 7. Tone Curve LUT
    if (uEnableCurve) {
        float lutR = texture2D(uToneCurveLut, vec2(clamp(col.r, 0.0, 1.0), 0.5)).r;
        float lutG = texture2D(uToneCurveLut, vec2(clamp(col.g, 0.0, 1.0), 0.5)).r;
        float lutB = texture2D(uToneCurveLut, vec2(clamp(col.b, 0.0, 1.0), 0.5)).r;
        col = vec3(lutR, lutG, lutB);
    }

    // 8. Alt-Clipping Overlay
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

    // 9. Display sRGB
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
}

// 5-Zone RGB Live Histogram
const histCanvas = document.getElementById('histogramCanvas');
const histCtx = histCanvas.getContext('2d');
const zoneTag = document.getElementById('zoneTag');
let activeHistogramZone = null;

function updateHistogram() {
  const w = 180;
  const h = 120;
  const pixels = new Uint8Array(w * h * 4);
  gl.readPixels(0, 0, w, h, gl.RGBA, gl.UNSIGNED_BYTE, pixels);

  const rBins = new Int32Array(256);
  const gBins = new Int32Array(256);
  const bBins = new Int32Array(256);
  const lBins = new Int32Array(256);

  let maxCount = 1;
  const total = w * h;
  for (let i = 0; i < total * 4; i += 4) {
    const r = pixels[i];
    const g = pixels[i + 1];
    const b = pixels[i + 2];
    const l = Math.min(255, (r * 77 + g * 150 + b * 29) >> 8);

    rBins[r]++;
    gBins[g]++;
    bBins[b]++;
    lBins[l]++;
    if (rBins[r] > maxCount) maxCount = rBins[r];
    if (gBins[g] > maxCount) maxCount = gBins[g];
    if (bBins[b] > maxCount) maxCount = bBins[b];
  }

  // Check shadow and highlight clipping
  const isShadowCrushed = lBins[0] > 0 || state.altClippingMode === 2;
  const isHighlightBlown = lBins[255] > 0 || state.altClippingMode === 1;
  document.getElementById('clipShadows').classList.toggle('active', isShadowCrushed);
  document.getElementById('clipHighlights').classList.toggle('active', isHighlightBlown);

  // Draw Waveform Curves
  histCanvas.width = histCanvas.parentElement.clientWidth;
  histCanvas.height = histCanvas.parentElement.clientHeight;
  const cw = histCanvas.width;
  const ch = histCanvas.height;

  histCtx.clearRect(0, 0, cw, ch);

  // Draw 5 Zone Dividing Lines
  const x1 = cw * 0.15;
  const x2 = cw * 0.40;
  const x3 = cw * 0.60;
  const x4 = cw * 0.85;

  histCtx.strokeStyle = 'rgba(255, 255, 255, 0.08)';
  histCtx.lineWidth = 1;
  [x1, x2, x3, x4].forEach(x => {
    histCtx.beginPath();
    histCtx.moveTo(x, 0);
    histCtx.lineTo(x, ch);
    histCtx.stroke();
  });

  // Active zone highlight
  if (activeHistogramZone) {
    histCtx.fillStyle = 'rgba(20, 115, 230, 0.2)';
    let zLeft = 0, zWidth = 0;
    if (activeHistogramZone === 'Blacks') { zLeft = 0; zWidth = x1; }
    else if (activeHistogramZone === 'Shadows') { zLeft = x1; zWidth = x2 - x1; }
    else if (activeHistogramZone === 'Exposure') { zLeft = x2; zWidth = x3 - x2; }
    else if (activeHistogramZone === 'Highlights') { zLeft = x3; zWidth = x4 - x3; }
    else if (activeHistogramZone === 'Whites') { zLeft = x4; zWidth = cw - x4; }
    histCtx.fillRect(zLeft, 0, zWidth, ch);
  }

  function drawChannel(bins, color) {
    histCtx.beginPath();
    histCtx.strokeStyle = color;
    histCtx.lineWidth = 1.2;
    histCtx.moveTo(0, ch);
    for (let i = 0; i < 256; i++) {
      const px = (i / 255) * cw;
      const py = ch - (bins[i] / maxCount) * (ch * 0.95);
      histCtx.lineTo(px, py);
    }
    histCtx.stroke();
  }

  drawChannel(rBins, 'rgba(229, 57, 53, 0.85)');
  drawChannel(gBins, 'rgba(67, 160, 71, 0.85)');
  drawChannel(bBins, 'rgba(30, 136, 229, 0.85)');
  drawChannel(lBins, 'rgba(220, 220, 220, 0.4)');
}

// Histogram Dragging Interactions
const histBox = document.getElementById('histogramBox');
let isDraggingHist = false;
let lastHistX = 0;

histBox.addEventListener('mousedown', (e) => {
  isDraggingHist = true;
  lastHistX = e.clientX;
  const rect = histBox.getBoundingClientRect();
  const ratio = (e.clientX - rect.left) / rect.width;
  if (ratio < 0.15) activeHistogramZone = 'Blacks';
  else if (ratio < 0.40) activeHistogramZone = 'Shadows';
  else if (ratio < 0.60) activeHistogramZone = 'Exposure';
  else if (ratio < 0.85) activeHistogramZone = 'Highlights';
  else activeHistogramZone = 'Whites';

  zoneTag.textContent = activeHistogramZone.toUpperCase();
  zoneTag.classList.add('visible');
  history.pushSnapshot(`Adjust ${activeHistogramZone}`);
});

window.addEventListener('mousemove', (e) => {
  if (!isDraggingHist) return;
  const dx = e.clientX - lastHistX;
  lastHistX = e.clientX;

  if (activeHistogramZone === 'Exposure') {
    state.basic.exposure = Math.max(-5, Math.min(5, state.basic.exposure + dx * 0.02));
  } else if (activeHistogramZone === 'Highlights') {
    state.basic.highlights = Math.max(-100, Math.min(100, state.basic.highlights + dx * 0.6));
  } else if (activeHistogramZone === 'Shadows') {
    state.basic.shadows = Math.max(-100, Math.min(100, state.basic.shadows + dx * 0.6));
  } else if (activeHistogramZone === 'Whites') {
    state.basic.whites = Math.max(-100, Math.min(100, state.basic.whites + dx * 0.6));
  } else if (activeHistogramZone === 'Blacks') {
    state.basic.blacks = Math.max(-100, Math.min(100, state.basic.blacks + dx * 0.6));
  }
  syncAllUiInputs();
  requestPipelineRender();
});

window.addEventListener('mouseup', () => {
  if (isDraggingHist) {
    isDraggingHist = false;
    activeHistogramZone = null;
    zoneTag.classList.remove('visible');
    requestPipelineRender();
  }
});

// Interactive Tone Curve Canvas Editor
const curveCanvas = document.getElementById('curveCanvas');
const curveCtx = curveCanvas.getContext('2d');
let draggingPointIdx = -1;

function drawCurveEditor() {
  const w = curveCanvas.width;
  const h = curveCanvas.height;
  curveCtx.clearRect(0, 0, w, h);

  // Draw 4x4 Grid
  curveCtx.strokeStyle = '#222222';
  curveCtx.lineWidth = 1;
  for (let i = 1; i < 4; i++) {
    const x = (i / 4) * w;
    const y = (i / 4) * h;
    curveCtx.beginPath(); curveCtx.moveTo(x, 0); curveCtx.lineTo(x, h); curveCtx.stroke();
    curveCtx.beginPath(); curveCtx.moveTo(0, y); curveCtx.lineTo(w, y); curveCtx.stroke();
  }

  // Diagonal reference
  curveCtx.strokeStyle = '#333333';
  curveCtx.beginPath(); curveCtx.moveTo(0, h); curveCtx.lineTo(w, 0); curveCtx.stroke();

  // Draw Spline
  const spline = evaluateSpline(state.curves.points);
  curveCtx.strokeStyle = '#ffffff';
  curveCtx.lineWidth = 2;
  curveCtx.beginPath();
  curveCtx.moveTo(0, h - (spline[0] / 255) * h);
  for (let i = 1; i < 256; i++) {
    const px = (i / 255) * w;
    const py = h - (spline[i] / 255) * h;
    curveCtx.lineTo(px, py);
  }
  curveCtx.stroke();

  // Draw Control Points
  state.curves.points.forEach((pt, idx) => {
    const px = (pt.x / 255) * w;
    const py = h - (pt.y / 255) * h;
    curveCtx.fillStyle = idx === draggingPointIdx ? '#1473E6' : '#ffffff';
    curveCtx.beginPath();
    curveCtx.arc(px, py, 5, 0, Math.PI * 2);
    curveCtx.fill();
    curveCtx.strokeStyle = '#000000';
    curveCtx.lineWidth = 1.5;
    curveCtx.stroke();
  });
}

curveCanvas.addEventListener('mousedown', (e) => {
  const rect = curveCanvas.getBoundingClientRect();
  const clickX = (e.clientX - rect.left) / rect.width * 255;
  const clickY = (1.0 - (e.clientY - rect.top) / rect.height) * 255;

  const found = state.curves.points.findIndex(p => Math.hypot(p.x - clickX, p.y - clickY) < 18);
  if (found !== -1) {
    draggingPointIdx = found;
  } else {
    // Add point
    state.curves.points.push({ x: clickX, y: clickY });
    state.curves.points.sort((a, b) => a.x - b.x);
    draggingPointIdx = state.curves.points.findIndex(p => Math.abs(p.x - clickX) < 1);
  }
  history.pushSnapshot("Edit Tone Curve");
  drawCurveEditor();
  requestPipelineRender();
});

curveCanvas.addEventListener('dblclick', (e) => {
  const rect = curveCanvas.getBoundingClientRect();
  const clickX = (e.clientX - rect.left) / rect.width * 255;
  const clickY = (1.0 - (e.clientY - rect.top) / rect.height) * 255;

  const found = state.curves.points.findIndex(p => Math.hypot(p.x - clickX, p.y - clickY) < 18);
  if (found > 0 && found < state.curves.points.length - 1) {
    state.curves.points.splice(found, 1);
    draggingPointIdx = -1;
    drawCurveEditor();
    requestPipelineRender();
  }
});

window.addEventListener('mousemove', (e) => {
  if (draggingPointIdx === -1) return;
  const rect = curveCanvas.getBoundingClientRect();
  const newX = Math.max(0, Math.min(255, (e.clientX - rect.left) / rect.width * 255));
  const newY = Math.max(0, Math.min(255, (1.0 - (e.clientY - rect.top) / rect.height) * 255));

  const isFirst = draggingPointIdx === 0;
  const isLast = draggingPointIdx === state.curves.points.length - 1;

  state.curves.points[draggingPointIdx] = {
    x: isFirst ? 0 : isLast ? 255 : newX,
    y: newY
  };
  state.curves.points.sort((a, b) => a.x - b.x);
  drawCurveEditor();
  requestPipelineRender();
});

window.addEventListener('mouseup', () => {
  draggingPointIdx = -1;
});

// 3-Way Color Grading Wheel Canvas
const wheelCanvas = document.getElementById('wheelCanvas');
const wheelCtx = wheelCanvas.getContext('2d');
let isDraggingWheel = false;

function drawColorWheel() {
  const w = wheelCanvas.width;
  const h = wheelCanvas.height;
  const cx = w / 2;
  const cy = h / 2;
  const r = cx - 4;

  wheelCtx.clearRect(0, 0, w, h);

  // Draw Circular Color Spectrum
  for (let angle = 0; angle < 360; angle += 2) {
    const startRad = (angle - 1) * Math.PI / 180;
    const endRad = (angle + 1) * Math.PI / 180;
    wheelCtx.beginPath();
    wheelCtx.moveTo(cx, cy);
    wheelCtx.arc(cx, cy, r, startRad, endRad);
    wheelCtx.closePath();
    wheelCtx.fillStyle = `hsl(${angle}, 100%, 50%)`;
    wheelCtx.fill();
  }

  // Radial White Desaturation Overlay
  const grad = wheelCtx.createRadialGradient(cx, cy, 0, cx, cy, r);
  grad.addColorStop(0, 'rgba(255, 255, 255, 1.0)');
  grad.addColorStop(0.8, 'rgba(255, 255, 255, 0.2)');
  grad.addColorStop(1, 'rgba(255, 255, 255, 0.0)');
  wheelCtx.fillStyle = grad;
  wheelCtx.beginPath();
  wheelCtx.arc(cx, cy, r, 0, Math.PI * 2);
  wheelCtx.fill();

  // Draw Puck Indicator
  const target = state.grading[state.activeWheelTarget];
  const puckDist = (target.sat / 100) * (r - 6);
  const puckRad = target.hue * Math.PI / 180;
  const px = cx + puckDist * Math.cos(puckRad);
  const py = cy + puckDist * Math.sin(puckRad);

  wheelCtx.fillStyle = '#ffffff';
  wheelCtx.beginPath();
  wheelCtx.arc(px, py, 6, 0, Math.PI * 2);
  wheelCtx.fill();
  wheelCtx.strokeStyle = '#000000';
  wheelCtx.lineWidth = 1.5;
  wheelCtx.stroke();
}

wheelCanvas.addEventListener('mousedown', (e) => {
  isDraggingWheel = true;
  updateWheelTargetFromMouse(e);
});

wheelCanvas.addEventListener('dblclick', () => {
  state.grading[state.activeWheelTarget].hue = 0;
  state.grading[state.activeWheelTarget].sat = 0;
  drawColorWheel();
  requestPipelineRender();
});

window.addEventListener('mousemove', (e) => {
  if (isDraggingWheel) updateWheelTargetFromMouse(e);
});

window.addEventListener('mouseup', () => {
  isDraggingWheel = false;
});

function updateWheelTargetFromMouse(e) {
  const rect = wheelCanvas.getBoundingClientRect();
  const cx = rect.width / 2;
  const cy = rect.height / 2;
  const dx = e.clientX - rect.left - cx;
  const dy = e.clientY - rect.top - cy;
  const dist = Math.hypot(dx, dy);
  const maxR = cx - 4;

  const sat = Math.max(0, Math.min(100, (dist / maxR) * 100));
  let deg = Math.atan2(dy, dx) * 180 / Math.PI;
  if (deg < 0) deg += 360;

  state.grading[state.activeWheelTarget].hue = deg;
  state.grading[state.activeWheelTarget].sat = sat;
  drawColorWheel();
  requestPipelineRender();
}

// Wire UI Slider Controls
function bindSlider(id, targetObj, prop, displayId, formatter) {
  const el = document.getElementById(id);
  const valEl = document.getElementById(displayId);

  el.addEventListener('input', () => {
    targetObj[prop] = parseFloat(el.value);
    valEl.textContent = formatter(targetObj[prop]);
    valEl.classList.toggle('modified', targetObj[prop] !== 0);
    requestPipelineRender();
  });

  el.addEventListener('mousedown', () => {
    history.pushSnapshot(`Adjust ${prop}`);
  });
}

function syncAllUiInputs() {
  document.getElementById('sliderExposure').value = state.basic.exposure;
  document.getElementById('valExposure').textContent = `${state.basic.exposure >= 0 ? '+' : ''}${state.basic.exposure.toFixed(2)} EV`;

  document.getElementById('sliderContrast').value = state.basic.contrast;
  document.getElementById('valContrast').textContent = state.basic.contrast;

  document.getElementById('sliderHighlights').value = state.basic.highlights;
  document.getElementById('valHighlights').textContent = state.basic.highlights;

  document.getElementById('sliderShadows').value = state.basic.shadows;
  document.getElementById('valShadows').textContent = state.basic.shadows;

  document.getElementById('sliderWhites').value = state.basic.whites;
  document.getElementById('valWhites').textContent = state.basic.whites;

  document.getElementById('sliderBlacks').value = state.basic.blacks;
  document.getElementById('valBlacks').textContent = state.basic.blacks;

  document.getElementById('sliderTemp').value = state.basic.temp;
  document.getElementById('valTemp').textContent = `${Math.round(state.basic.temp)} K`;

  document.getElementById('sliderTint').value = state.basic.tint;
  document.getElementById('valTint').textContent = state.basic.tint;

  document.getElementById('sliderVibrance').value = state.presence.vibrance;
  document.getElementById('valVibrance').textContent = state.presence.vibrance;

  document.getElementById('sliderSaturation').value = state.presence.saturation;
  document.getElementById('valSaturation').textContent = state.presence.saturation;

  document.getElementById('sliderTexture').value = state.presence.texture;
  document.getElementById('valTexture').textContent = state.presence.texture;

  document.getElementById('sliderClarity').value = state.presence.clarity;
  document.getElementById('valClarity').textContent = state.presence.clarity;

  document.getElementById('sliderDehaze').value = state.presence.dehaze;
  document.getElementById('valDehaze').textContent = state.presence.dehaze;

  const currentHsl = state.hsl[state.activeHslIndex];
  document.getElementById('sliderHslHue').value = currentHsl.hue;
  document.getElementById('valHslHue').textContent = currentHsl.hue;
  document.getElementById('sliderHslSat').value = currentHsl.sat;
  document.getElementById('valHslSat').textContent = currentHsl.sat;
  document.getElementById('sliderHslLum').value = currentHsl.lum;
  document.getElementById('valHslLum').textContent = currentHsl.lum;
}

function initUiBindings() {
  bindSlider('sliderExposure', state.basic, 'exposure', 'valExposure', v => `${v >= 0 ? '+' : ''}${v.toFixed(2)} EV`);
  bindSlider('sliderContrast', state.basic, 'contrast', 'valContrast', v => `${v}`);
  bindSlider('sliderHighlights', state.basic, 'highlights', 'valHighlights', v => `${v}`);
  bindSlider('sliderShadows', state.basic, 'shadows', 'valShadows', v => `${v}`);
  bindSlider('sliderWhites', state.basic, 'whites', 'valWhites', v => `${v}`);
  bindSlider('sliderBlacks', state.basic, 'blacks', 'valBlacks', v => `${v}`);

  // Alt clipping while dragging whites or blacks
  document.getElementById('sliderWhites').addEventListener('mousedown', () => { state.altClippingMode = 1; requestPipelineRender(); });
  document.getElementById('sliderWhites').addEventListener('mouseup', () => { state.altClippingMode = 0; requestPipelineRender(); });
  document.getElementById('sliderBlacks').addEventListener('mousedown', () => { state.altClippingMode = 2; requestPipelineRender(); });
  document.getElementById('sliderBlacks').addEventListener('mouseup', () => { state.altClippingMode = 0; requestPipelineRender(); });

  bindSlider('sliderTemp', state.basic, 'temp', 'valTemp', v => `${Math.round(v)} K`);
  bindSlider('sliderTint', state.basic, 'tint', 'valTint', v => `${v}`);
  bindSlider('sliderVibrance', state.presence, 'vibrance', 'valVibrance', v => `${v}`);
  bindSlider('sliderSaturation', state.presence, 'saturation', 'valSaturation', v => `${v}`);

  bindSlider('sliderTexture', state.presence, 'texture', 'valTexture', v => `${v}`);
  bindSlider('sliderClarity', state.presence, 'clarity', 'valClarity', v => `${v}`);
  bindSlider('sliderDehaze', state.presence, 'dehaze', 'valDehaze', v => `${v}`);

  bindSlider('sliderSharpening', state.detail, 'sharpening', 'valSharpening', v => `${v}`);
  bindSlider('sliderMasking', state.detail, 'masking', 'valMasking', v => `${v}`);
  bindSlider('sliderNoiseReduction', state.detail, 'noiseReduction', 'valNoiseReduction', v => `${v}`);

  bindSlider('sliderParamHighlights', state.curves, 'parametricHighlights', 'valParamHighlights', v => `${v}`);
  bindSlider('sliderParamLights', state.curves, 'parametricLights', 'valParamLights', v => `${v}`);
  bindSlider('sliderParamDarks', state.curves, 'parametricDarks', 'valParamDarks', v => `${v}`);
  bindSlider('sliderParamShadows', state.curves, 'parametricShadows', 'valParamShadows', v => `${v}`);

  // HSL Channels & Swatches
  const hslLabels = ['Red', 'Orange', 'Yellow', 'Green', 'Aqua', 'Blue', 'Purple', 'Magenta'];
  const swatches = document.querySelectorAll('.swatch-btn');
  swatches.forEach(btn => {
    btn.addEventListener('click', () => {
      swatches.forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      state.activeHslIndex = parseInt(btn.dataset.color, 10);
      document.getElementById('lblHslChannel').textContent = `${hslLabels[state.activeHslIndex]} Hue`;
      syncAllUiInputs();
    });
  });

  const hslHue = document.getElementById('sliderHslHue');
  const hslSat = document.getElementById('sliderHslSat');
  const hslLum = document.getElementById('sliderHslLum');
  [hslHue, hslSat, hslLum].forEach((sl, idx) => {
    sl.addEventListener('input', () => {
      const prop = ['hue', 'sat', 'lum'][idx];
      state.hsl[state.activeHslIndex][prop] = parseFloat(sl.value);
      syncAllUiInputs();
      requestPipelineRender();
    });
  });

  // Wheel Tabs
  const wheelTabs = document.querySelectorAll('.wheel-tab-btn');
  wheelTabs.forEach(btn => {
    btn.addEventListener('click', () => {
      wheelTabs.forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      state.activeWheelTarget = btn.dataset.wheel;
      drawColorWheel();
    });
  });

  // Category Tabs
  const tabs = document.querySelectorAll('.tab-btn');
  tabs.forEach(btn => {
    btn.addEventListener('click', () => {
      tabs.forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      document.querySelectorAll('.tool-section').forEach(sec => sec.classList.remove('active'));
      document.getElementById(btn.dataset.tab).classList.add('active');
    });
  });

  // Double Click Labels to Reset
  document.querySelectorAll('.slider-label[data-reset]').forEach(lbl => {
    lbl.addEventListener('dblclick', () => {
      const prop = lbl.dataset.reset;
      if (prop in state.basic) state.basic[prop] = (prop === 'temp' ? 5500 : 0);
      if (prop in state.presence) state.presence[prop] = 0;
      history.pushSnapshot(`Reset ${prop}`);
      syncAllUiInputs();
      requestPipelineRender();
    });
  });

  // Header Buttons
  document.getElementById('btnUndo').addEventListener('click', () => history.undo());
  document.getElementById('btnRedo').addEventListener('click', () => history.redo());

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

  // Reset All
  document.getElementById('btnResetAll').addEventListener('click', () => {
    state.basic = { exposure: 0, contrast: 0, highlights: 0, shadows: 0, whites: 0, blacks: 0, temp: 5500, tint: 0 };
    state.presence = { texture: 0, clarity: 0, dehaze: 0, vibrance: 0, saturation: 0 };
    state.detail = { sharpening: 40, masking: 0, noiseReduction: 0 };
    state.hsl = new Array(8).fill(null).map(() => ({ hue: 0, sat: 0, lum: 0 }));
    state.grading = { shadows: { hue: 0, sat: 0, lum: 0 }, midtones: { hue: 0, sat: 0, lum: 0 }, highlights: { hue: 0, sat: 0, lum: 0 } };
    state.curves = { parametricHighlights: 0, parametricLights: 0, parametricDarks: 0, parametricShadows: 0, points: [{ x: 0, y: 0 }, { x: 255, y: 255 }] };
    history.pushSnapshot("Reset All");
    syncAllUiInputs();
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

  // Presets
  document.querySelectorAll('.preset-chip').forEach(chip => {
    chip.addEventListener('click', () => {
      const preset = chip.dataset.preset;
      applyPreset(preset);
    });
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

function applyPreset(name) {
  if (name === 'golden') {
    state.basic.temp = 6800;
    state.basic.tint = 12;
    state.basic.exposure = 0.35;
    state.basic.highlights = -25;
    state.basic.shadows = 30;
    state.presence.vibrance = 25;
    state.presence.dehaze = 15;
    state.grading.shadows = { hue: 215, sat: 20, lum: 0 };
    state.grading.highlights = { hue: 42, sat: 35, lum: 0 };
  } else if (name === 'vivid') {
    state.basic.contrast = 20;
    state.basic.highlights = -15;
    state.basic.shadows = 20;
    state.presence.clarity = 15;
    state.presence.vibrance = 35;
    state.presence.saturation = 10;
  } else if (name === 'cinematic') {
    state.basic.contrast = 30;
    state.basic.shadows = -10;
    state.basic.highlights = -30;
    state.presence.dehaze = 20;
    state.presence.vibrance = -10;
    state.grading.shadows = { hue: 195, sat: 40, lum: -10 };
    state.grading.highlights = { hue: 35, sat: 30, lum: 5 };
  } else if (name === 'hdr') {
    state.basic.highlights = -65;
    state.basic.shadows = 65;
    state.basic.whites = 20;
    state.basic.blacks = -15;
    state.presence.clarity = 30;
    state.presence.texture = 25;
    state.presence.vibrance = 20;
  } else if (name === 'bw') {
    state.presence.saturation = -100;
    state.basic.contrast = 45;
    state.basic.whites = 35;
    state.basic.blacks = -35;
    state.presence.clarity = 25;
  } else if (name === 'natural') {
    document.getElementById('btnResetAll').click();
    return;
  }

  history.pushSnapshot(`Preset: ${name}`);
  syncAllUiInputs();
  requestPipelineRender();
}

// Bootstrap
window.addEventListener('DOMContentLoaded', () => {
  initWebGL();
  initUiBindings();
  sourceImage.onload = () => loadTextureFromImage(sourceImage);
  sourceImage.src = 'sample.jpg';
});
