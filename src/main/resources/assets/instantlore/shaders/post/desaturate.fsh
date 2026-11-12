#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform DesaturateConfig {
    float Strength;
};

out vec4 fragColor;

void main() {
    vec4 c = texture(InSampler, texCoord);

    // Luminance grey (Rec.601) — desaturate, not lighten/darken.
    float luma = dot(c.rgb, vec3(0.299, 0.587, 0.114));

    // Warm ashen tint of the grey, rather than a dead-neutral one.
    vec3 ash = vec3(luma) * vec3(1.04, 1.0, 0.93);

    // Applies to the whole frame uniformly, no saturation gating.
    vec3 outc = mix(c.rgb, ash, Strength);
    fragColor = vec4(outc, 1.0);
}
