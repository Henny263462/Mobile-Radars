#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform vec2 OutSize;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

// Tunable parameters (subtle optics — readability without heavy CRT)
const float BARREL_K1 = 0.14;
const float BARREL_K2 = 0.04;
const float CHROMA_BASE = 0.0018;
const float CHROMA_RADIAL = 0.006;
const vec3 PHOSPHOR_TINT = vec3(0.88, 1.02, 0.94);
const vec3 PHOSPHOR_BIAS = vec3(0.0, 0.015, 0.01);
const float SCAN_STRENGTH = 0.045;
const float SCAN_DENSITY = 1.2;
const float VIGNETTE_RADIUS = 1.35;

void main() {
    vec2 cc = texCoord - 0.5;
    float r2 = dot(cc, cc);

    // Barrel distortion (radial polynomial)
    vec2 warp = cc * (BARREL_K1 * r2 + BARREL_K2 * r2 * r2);
    vec2 distorted = texCoord + warp;

    // Chromatic aberration: shift R and B along the radial direction, more towards corners.
    vec2 radial = (r2 > 1e-7) ? cc / sqrt(r2) : vec2(0.0);
    float chroma = CHROMA_BASE + r2 * CHROMA_RADIAL;

    vec3 color;
    color.r = texture(DiffuseSampler, distorted + radial * chroma).r;
    color.g = texture(DiffuseSampler, distorted).g;
    color.b = texture(DiffuseSampler, distorted - radial * chroma).b;

    // Bezel: hard cut outside the distorted screen rectangle
    if (distorted.x < 0.0 || distorted.x > 1.0 || distorted.y < 0.0 || distorted.y > 1.0) {
        color = vec3(0.0);
    }

    // Phosphor (greenish CRT) tint, slight lift.
    color = color * PHOSPHOR_TINT + PHOSPHOR_BIAS;

    // Scanlines based on screen-space (resolution independent for stable look)
    float scan = 1.0 - SCAN_STRENGTH * (0.5 + 0.5 * sin(texCoord.y * OutSize.y * SCAN_DENSITY));
    color *= scan;

    // Light phosphor mask
    float maskX = mod(texCoord.x * OutSize.x, 3.0);
    vec3 mask = vec3(1.0);
    if (maskX < 1.0) mask = vec3(1.02, 0.99, 0.99);
    else if (maskX < 2.0) mask = vec3(0.99, 1.02, 0.99);
    else mask = vec3(0.99, 0.99, 1.02);
    color *= mask;

    // Soft vignette
    float vig = smoothstep(VIGNETTE_RADIUS * 0.75, 0.42, length(cc) * VIGNETTE_RADIUS);
    color *= mix(0.72, 1.0, vig);

    // Very subtle highlight lift
    float luma = dot(color, vec3(0.299, 0.587, 0.114));
    color += vec3(0.0, 0.03, 0.02) * smoothstep(0.72, 1.0, luma);

    fragColor = vec4(color, 1.0);
}
