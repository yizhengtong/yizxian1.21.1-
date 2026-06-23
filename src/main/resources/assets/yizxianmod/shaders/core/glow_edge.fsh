#version 150

uniform sampler2D Sampler0;
uniform sampler2D ScreenTexture;
uniform sampler2D TargetTexture;

uniform vec4 ColorModulator;
uniform vec4 uColor;
uniform int uType;
uniform vec2 screenSize;
uniform float time;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

vec2 hash(float n)
{
    float x = fract(sin(n * 12.9898) * 43758.5453);
    float y = fract(sin(n * 78.233) * 43758.5453);
    return vec2(x, y);
}

vec3 hash3(vec3 p)
{
    p = fract(p * 0.3183099 + vec3(0.1, 0.2, 0.3));
    p *= 17.0;
    return fract(p * (p.x + p.y + p.z)) * 2.0 - 1.0;
}

float perlinNoise(vec3 p)
{
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);

    float n000 = dot(hash3(i + vec3(0.0, 0.0, 0.0)), f - vec3(0.0, 0.0, 0.0));
    float n100 = dot(hash3(i + vec3(1.0, 0.0, 0.0)), f - vec3(1.0, 0.0, 0.0));
    float n010 = dot(hash3(i + vec3(0.0, 1.0, 0.0)), f - vec3(0.0, 1.0, 0.0));
    float n110 = dot(hash3(i + vec3(1.0, 1.0, 0.0)), f - vec3(1.0, 1.0, 0.0));
    float n001 = dot(hash3(i + vec3(0.0, 0.0, 1.0)), f - vec3(0.0, 0.0, 1.0));
    float n101 = dot(hash3(i + vec3(1.0, 0.0, 1.0)), f - vec3(1.0, 0.0, 1.0));
    float n011 = dot(hash3(i + vec3(0.0, 1.0, 1.0)), f - vec3(0.0, 1.0, 1.0));
    float n111 = dot(hash3(i + vec3(1.0, 1.0, 1.0)), f - vec3(1.0, 1.0, 1.0));

    return mix(mix(mix(n000, n100, u.x), mix(n010, n110, u.x), u.y), mix(mix(n001, n101, u.x), mix(n011, n111, u.x), u.y), u.z);
}

float perlinNoiseOctaves(vec3 p, int octaves, float persistence, float contrast)
{
    float total = 0.0;
    float amplitude = 1.0;
    float frequency = 1.0;
    float maxValue = 0.0;

    for (int i = 0; i < 8; i++)
    {
        if (i >= octaves) break;
        total += perlinNoise(p * frequency) * amplitude;
        maxValue += amplitude;
        amplitude *= persistence;
        frequency *= 2.0;
    }

    float n = total / maxValue * 0.5 + 0.5;
    n = (n - 0.5) * contrast + 0.5;
    return clamp(n, 0.0, 1.0);
}

vec3 blend3BW(vec3 dark, vec3 mid, vec3 midAlt, vec3 bright, float n, vec2 uv, float time)
{
    vec3 blobPos = vec3(uv * 0.4, time * 0.02);
    float blobNoise = perlinNoise(blobPos) * 0.5 + 0.5;
    blobNoise = smoothstep(0.35, 0.65, blobNoise);

    vec3 midMix = mix(mid, midAlt, blobNoise);
    float t1 = smoothstep(0.2, 0.5, n);
    float t2 = smoothstep(0.2, 1.0, n);

    vec3 col1 = mix(dark, midMix, t1);
    vec3 col2 = mix(midMix, bright, t2);
    vec3 tint = mix(col1, col2, n);

    return mix(vec3(n), tint, 1.0);
}

vec3 galaxy(vec2 fragCoord, float mult, float speed, vec2 res, float time)
{
    vec2 uv = fragCoord * mult;
    float tSpeed = speed < 3.0 ? speed * 3.0 : speed;
    vec3 p = vec3(uv + time / speed, time / tSpeed);
    float n = 1.0 - abs(perlinNoiseOctaves(p * 0.15, 8, 0.6, 4.0));
    n = pow(n, 2.0);

    return blend3BW(vec3(0.05, 0.0, 0.10), vec3(0.45, 0.10, 0.75), vec3(0.70, 0.25, 0.95), vec3(1.35, 0.95, 1.75), n, uv, time);
}

vec3 galaxyRed(vec2 fragCoord, float mult, float speed, vec2 res, float time)
{
    vec2 uv = fragCoord * mult;
    float tSpeed = speed < 3.0 ? speed * 3.0 : speed;
    vec3 p = vec3(uv + time / speed, time / tSpeed);
    float n = 1.0 - abs(perlinNoiseOctaves(p * 0.15, 8, 0.6, 4.0));
    n = pow(n, 2.0);
    // 这里的 blend3BW 参数被我替换成了：深红暗物质、纯红星云、血橙星云、以及炽热的亮黄白星光
    return blend3BW(vec3(0.1, 0.0, 0.0), vec3(0.6, 0.0, 0.0), vec3(0.5, 0.1, 0.0), vec3(1.6, 1.2, 1.0), n, uv, time);
}

vec3 galaxyDynamic(vec2 fragCoord, float mult, float speed, vec2 res, float time, vec4 baseColor)
{
    vec2 uv = fragCoord * mult;
    float tSpeed = speed < 3.0 ? speed * 3.0 : speed;
    vec3 p = vec3(uv + time / speed, time / tSpeed);

    // 这里的 8 次倍频会让细节很多，如果觉得乱，可以把 8 改小（如 4）
    float n = 1.0 - abs(perlinNoiseOctaves(p * 0.15, 8, 0.6, 4.0));

    // ★ 关键改动：将 pow(n, 2.0) 改为 pow(n, 1.5) 或更小，降低黑斑的对比度
    n = pow(n, 1.5);

    vec3 dark = baseColor.rgb * 0.2; // 提高暗部亮度，防止变黑
    vec3 mid = baseColor.rgb * 0.8;
    vec3 midAlt = baseColor.rgb * vec3(1.0, 0.9, 0.8) * 0.7;
    vec3 bright = baseColor.rgb * 1.5 + 0.3;

    return blend3BW(dark, mid, midAlt, bright, n, uv, time);
}

// 类型2：折射效果
vec4 refractionEffect(vec2 fragCoord, vec2 uv, float time)
{
    // 计算屏幕UV坐标（0-1范围）
    vec2 screenUV = fragCoord / screenSize;

    // 创建动态偏移量，基于时间和噪声
    float timeFactor = time * 0.5; // 控制折射动画速度

    // 添加基于噪声的动态偏移
    vec2 noiseOffset = vec2(perlinNoise(vec3(fragCoord * 0.01, timeFactor)) * 0.01, perlinNoise(vec3(fragCoord * 0.01 + 100.0, timeFactor)) * 0.01);
    vec2 refractedUV = screenUV + noiseOffset;

    // 确保UV在有效范围内
    refractedUV = clamp(refractedUV, 0.0, 1.0);

    // 采样屏幕纹理
    vec4 screenColor = texture(ScreenTexture, refractedUV);

    // 添加轻微的色散效果（RGB通道轻微不同的偏移）
    vec2 dispersionOffset = vec2(0.005, -0.005);

    vec4 screenColorR = texture(ScreenTexture, refractedUV + dispersionOffset * 0.3);
    vec4 screenColorB = texture(ScreenTexture, refractedUV - dispersionOffset * 0.3);

    // 组合色散效果
    screenColor.r = screenColorR.r;
    screenColor.b = screenColorB.b;

    return screenColor;
}

void main()
{
    // 1. 获取基础贴图颜色
    vec4 color = texture(Sampler0, texCoord0);

    // 2. 透明度裁剪（性能优化）
    if (color.a < 0.01) discard;

    // ★ 关键修复：将变量定义放在 main 函数的最开头，确保全局可用
    vec2 fragCoord = gl_FragCoord.xy;

    if (uType == 1)
    {
        // 【类型 1】原版紫蓝星域
        vec3 gCol = galaxy(fragCoord, 0.03, 0.5, screenSize, time) / 4.0 + galaxy(fragCoord, 0.01, 3.0, screenSize, time);
        fragColor = vec4(gCol, 1.0) * ColorModulator;
    }
    else if (uType == 2)
    {
        // 【类型 2】原版折射效果
        fragColor = refractionEffect(fragCoord, texCoord0, time) * ColorModulator;
    }
    else if (uType == 3)
    {
        // 【类型 3】彩虹流光呼吸
        float t = time * 0.05 + (fragCoord.x + fragCoord.y) * 0.002;
        vec3 rainbow = vec3(0.5 + 0.5 * sin(t), 0.5 + 0.5 * sin(t + 2.094), 0.5 + 0.5 * sin(t + 4.188));
        fragColor = vec4(uColor.rgb * rainbow, uColor.a) * ColorModulator;
    }
    else if (uType == 4)
    {
        // 【类型 4】固定猩红星域
        vec3 gCol = galaxyRed(fragCoord, 0.03, 0.5, screenSize, time) / 4.0 + galaxyRed(fragCoord, 0.01, 3.0, screenSize, time);
        fragColor = vec4(gCol, 1.0) * ColorModulator;
    }
    else if (uType == 5)
    {
        // 【类型 5】万能动态星域（颜色由 Java 端传来的 uColor 控制）
        vec3 gCol = galaxyDynamic(fragCoord, 0.03, 0.5, screenSize, time, uColor) / 4.0
                  + galaxyDynamic(fragCoord, 0.01, 3.0, screenSize, time, uColor);
        fragColor = vec4(gCol, 1.0) * ColorModulator;
    }
else if (uType == 6)
    {
        // =======================================================
        // ★ 【类型 6】彩虹呼吸 + 动态星域
        // =======================================================

        // 1. 彩虹底色变色的速度
        // （0.05 是当前速度。如果你觉得彩虹变色也太快了，可以把它改成 0.02 或更小）
        float t = time * 0.05 + (fragCoord.x + fragCoord.y) * 0.002;
        vec3 rainbow = vec3(0.5 + 0.5 * sin(t), 0.5 + 0.5 * sin(t + 2.094), 0.5 + 0.5 * sin(t + 4.188));
        vec4 rainbowBaseColor = vec4(rainbow * 0.8 + 0.2, 1.0);

        // 2. ★ 星空流动和闪烁的速度！
        // 这里乘以 0.2，意味着星空的动画速度会放慢整整 5 倍！
        // 你可以随时根据感觉微调这个数字（比如 0.1 极慢，0.5 适中）
        float starTime = time * 0.2;

        // 3. 把变慢的 starTime 喂给星域生成器
        vec3 gCol = galaxyDynamic(fragCoord, 0.03, 0.5, screenSize, starTime, rainbowBaseColor) / 4.0
                  + galaxyDynamic(fragCoord, 0.01, 3.0, screenSize, starTime, rainbowBaseColor);

        fragColor = vec4(gCol, 1.0) * ColorModulator;
    }
    else
    {
        // 【类型 0/保底】静止纯色
        fragColor = uColor * ColorModulator;
    }
}
