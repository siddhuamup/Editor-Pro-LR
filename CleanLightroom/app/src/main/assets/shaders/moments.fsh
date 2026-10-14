#version 300 es
precision highp float;

out vec4 fragColor;

// moments.fsh: Outputs unit energy to be accumulated via GPU additive blending (GL_ONE, GL_ONE)
void main() {
    fragColor = vec4(1.0, 1.0, 1.0, 1.0);
}
