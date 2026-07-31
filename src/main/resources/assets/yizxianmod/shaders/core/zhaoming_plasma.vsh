#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 localPos;
out vec4 vertexColor;
out vec2 texCoord0;

void main()
{
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    // 顶点 Color 存球面法线方向（单位球坐标）→ fragment 做真正的 3D 等离子计算（非贴图）
    localPos = Color.rgb;
    vertexColor = Color;
    texCoord0 = UV0;
}
