#version 150

// 紫昭明光 — 雾状光球（模仿自然雾气：朦胧、形态流动、浓淡层次）
// 多频 3D 噪声随时间扰动密度 → 雾成团成块、翻滚流动；低饱和紫灰，不刺眼。

uniform vec4 ColorModulator;
uniform float time;

in vec3 localPos;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

// 简易 3D 值噪声
float hash(vec3 p) {
    return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453);
}
float noise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n = mix(
        mix(mix(hash(i), hash(i + vec3(1, 0, 0)), f.x),
            mix(hash(i + vec3(0, 1, 0)), hash(i + vec3(1, 1, 0)), f.x), f.y),
        mix(mix(hash(i + vec3(0, 0, 1)), hash(i + vec3(1, 0, 1)), f.x),
            mix(hash(i + vec3(0, 1, 1)), hash(i + vec3(1, 1, 1)), f.x), f.y),
        f.z);
    return n;
}

void main()
{
    vec3 p = localPos;
    // 缓慢自旋
    float spin = time * 0.35;
    float ca = cos(spin), sa = sin(spin);
    p = vec3(p.x * ca - p.z * sa, p.y, p.x * sa + p.z * ca);

    // 雾的形态：多频噪声随时间流动（成团成块、翻滚、聚散）
    vec3 n1 = p * 3.0 + vec3(time * 0.40, time * 0.30, 0.0);
    vec3 n2 = p * 6.0 + vec3(0.0, time * 0.20, time * 0.30);
    vec3 n3 = p * 1.5 + vec3(time * 0.25, 0.0, time * 0.35);
    float d1 = noise(n1);
    float d2 = noise(n2);
    float d3 = noise(n3);
    float density = 0.40 + 0.60 * (0.5 * d1 + 0.35 * d2 + 0.15 * d3);

    // 雾色：低饱和紫灰（朦胧、不刺眼），微弱冷暖变化
    vec3 fogColor = vec3(0.48, 0.30, 0.62);
    fogColor += 0.05 * vec3(sin(time * 0.5), sin(time * 0.5 + 2.0), cos(time * 0.5));

    // 浓淡层次：中心浓、边缘柔和雾化
    float r = length(localPos);
    float radial = clamp(1.3 - r * 0.9, 0.0, 1.0);
    // 密度扰动 → 形态流动（雾不是均匀的）
    float alpha = radial * (0.30 + 0.70 * density);

    fragColor = vec4(fogColor * (0.45 + 0.55 * density), alpha) * ColorModulator;
}
