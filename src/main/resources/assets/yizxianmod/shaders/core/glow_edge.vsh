#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec2 screenSize;        // 屏幕宽高（像素），与 fsh 共用
uniform vec2 uScreenOffset;     // 屏幕空间描边偏移（像素），默认 (0,0)=不偏移

out vec4 vertexColor;
out vec2 texCoord0;

void main()
{
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    // 屏幕空间描边：像素偏移 → NDC，再乘 w，使边宽与顶点深度（距离）无关
    vec2 ndcOffset = uScreenOffset / screenSize * 2.0;
    gl_Position.xy += ndcOffset * gl_Position.w;
    vertexColor = Color;
    texCoord0 = UV0;
}
