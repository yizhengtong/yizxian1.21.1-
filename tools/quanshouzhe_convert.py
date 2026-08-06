#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
全首者（Quanshouzhe）bbmodel → 原版 ModelPart 转换脚本（M1a）

将 Blockbench bbmodel（box_uv、3 纹理、32 骨骼、20 元素）转换为：
1. 合并单图集 textures/entity/quanshouzhe/quanshouzhe.png（1024x1024）
   —— 把 bbmodel 每面 UV（CEM box_uv 置换布局）重排到原版 ModelPart.Cube 槽位
2. 参考纹理 PNG ×3（warden/wither_skull/dragon）
3. QuanshouzheModel.createBodyLayer() 的 PartDefinition 树 Java 代码

原版 ModelPart.Cube 槽位（uv 起点 (u,v)，size w×h×d）：
  DOWN:  (u+d, v)      .. (u+d+w, v+d)
  UP:    (u+d+w, v+d)  .. (u+d+2w, v+d)
  WEST:  (u, v+d)      .. (u+d, v+d+h)
  NORTH: (u+d, v+d)    .. (u+d+w, v+d+h)
  EAST:  (u+d+w, v+d)  .. (u+d+w+d, v+d+h)
  SOUTH: (u+d+w+d, v+d).. (u+d+w+d+w, v+d+h)
用法: python quanshouzhe_convert.py <input.bbmodel> <output_dir>
"""
import base64
import io
import json
import os
import sys

from PIL import Image


# ── 原版 ModelPart 槽位相对偏移（u 轴、v 轴）──────────────
# 每面：目标矩形在 box 块内的 (u_off, v_off, 宽, 高, 水平翻转?, 垂直翻转?)
# 方向约定按原版 ModelPart 顶点 uv 顺序；先按未翻转，M1 目检后用 --flip 调整
def vanilla_slots(w, h, d):
    """返回 {face: (u_off, v_off, tw, th, flip_u, flip_v)}，相对 box 块 (u,v) 起点"""
    return {
        # 水平排（v 0..d）
        'down': (d, 0, w, d, False, False),
        'up': (d + w, 0, w, d, False, False),
        # 垂直排（v d..d+h）
        'west': (0, d, d, h, False, False),
        'north': (d, d, w, h, False, False),
        'east': (d + w, d, d, h, False, False),
        'south': (d + w + d, d, w, h, False, False),
    }


def slot_rect(slots, face):
    """返回 (u_off, v_off, tw, th)。"""
    return slots[face][:4]


def main():
    src = sys.argv[1] if len(sys.argv) > 1 else '全首者 - Converted.bbmodel'
    out = sys.argv[2] if len(sys.argv) > 2 else 'out'
    flip = set()
    if '--flip' in sys.argv:
        i = sys.argv.index('--flip')
        flip = set(sys.argv[i + 1].split(','))  # 如 "up,down"

    os.makedirs(out, exist_ok=True)
    with open(src, encoding='utf-8') as f:
        d = json.load(f)

    # ── 1. 提取纹理 base64 → PNG ─────────────────────────
    # 注意：textures 的 id 可能全是 0（CEM 约定），faces.texture 引用的是【数组索引】，
    # 因此 tex_images 用数组下标作 key。
    tex_images = {}
    for idx, t in enumerate(d.get('textures', [])):
        name = t.get('name', str(idx))
        path = t.get('source') or t.get('path') or ''
        if path.startswith('data:image'):
            b64 = path.split('base64,')[1]
            img = Image.open(io.BytesIO(base64.b64decode(b64))).convert('RGBA')
            tex_images[idx] = img
            img.save(os.path.join(out, f'{name}.png'))
            print(f'纹理[{idx}] {name}: {img.size[0]}x{img.size[1]}')
        else:
            # 外部路径引用
            if path and os.path.exists(path):
                img = Image.open(path).convert('RGBA')
                tex_images[idx] = img

    # ── 2. 解析 groups / elements / outliner ──────────────
    groups = {}
    for g in d.get('groups', []):
        groups[g.get('uuid')] = g
    elements = {}
    for e in d.get('elements', []):
        elements[e.get('uuid')] = e

    # 骨骼树：递归 outliner
    def parse_outliner(nodes, parent):
        """返回 bone dict 列表。bone: {name, origin, rotation, mirror, world_origin, children, cubes}"""
        result = []
        for node in nodes:
            if isinstance(node, str):
                # 裸 element uuid → 挂到 parent.cubes
                parent['cubes'].append(node)
            elif isinstance(node, dict):
                uuid = node.get('uuid')
                g = groups.get(uuid)
                children_nodes = node.get('children', [])
                if g is None:
                    # 未知 group：仅当作容器递归
                    tmp = {'name': 'x' + uuid[:4], 'origin': [0, 0, 0],
                           'rotation': [0, 0, 0], 'mirror': False,
                           'world_origin': list(parent['world_origin']),
                           'children': [], 'cubes': []}
                    result.append(tmp)
                    result.extend(parse_outliner(children_nodes, tmp))
                    parent['children'].extend([tmp])
                    continue
                bone = {'name': g.get('name', 'bone'),
                        'origin': g.get('origin', [0, 0, 0]),
                        'rotation': g.get('rotation', [0, 0, 0]),
                        'mirror': g.get('mirror_uv', False),
                        'world_origin': None,
                        'parent_world_origin': list(parent['world_origin']),
                        'children': [], 'cubes': []}
                # 关键：bbmodel 的 group origin 是【世界坐标】（非相对父）。
                # 因此 world_origin = origin 本身；相对父的 PartPose offset = origin - 父.world_origin。
                bone['world_origin'] = bone['origin']
                parent['children'].append(bone)
                result.append(bone)
                # 子节点
                result.extend(parse_outliner(children_nodes, bone))
        return result

    root = {'name': 'root', 'origin': [0, 0, 0], 'rotation': [0, 0, 0],
            'mirror': False, 'world_origin': [0, 0, 0], 'children': [], 'cubes': []}
    parse_outliner(d.get('outliner', []), root)

    # 收集可见 cube（排除 visibility=false）
    visible_cubes = []
    def collect_cubes(bone, out):
        for uid in bone['cubes']:
            e = elements.get(uid)
            if e is None:
                continue
            if e.get('visibility', True) is False:
                continue
            out.append((bone, e))
        for c in bone['children']:
            collect_cubes(c, out)
    all_cubes = []
    collect_cubes(root, all_cubes)
    print(f'可见 cube 数: {len(all_cubes)}')

    # ── 3. 装箱：为每个 cube 分配图集块 ──────────────────
    atlas_size = 1024
    placed = []  # (bone, element, block_u, block_v)
    cursor_x, cursor_y, row_h = 0, 0, 0
    for bone, e in all_cubes:
        f, t = e['from'], e['to']
        w, h, dd = t[0] - f[0], t[1] - f[1], t[2] - f[2]
        bw, bh = 2 * w + 2 * dd, h + dd  # 原版 box 块尺寸
        bw, bh = int(bw), int(bh)
        if cursor_x + bw > atlas_size:
            cursor_x = 0
            cursor_y += row_h
            row_h = 0
        placed.append((bone, e, cursor_x, cursor_y, bw, bh))
        cursor_x += bw
        row_h = max(row_h, bh)
    max_y = cursor_y + row_h
    if max_y > atlas_size:
        print(f'警告: 图集超出 {atlas_size}（需要 {max_y} 高），改用 2048')
        atlas_size = 2048
        # 重新装箱（简单重算）
        placed = []
        cursor_x, cursor_y, row_h = 0, 0, 0
        for bone, e in all_cubes:
            f, t = e['from'], e['to']
            w, h, dd = t[0] - f[0], t[1] - f[1], t[2] - f[2]
            bw, bh = int(2 * w + 2 * dd), int(h + dd)
            if cursor_x + bw > atlas_size:
                cursor_x = 0
                cursor_y += row_h
                row_h = 0
            placed.append((bone, e, cursor_x, cursor_y, bw, bh))
            cursor_x += bw
            row_h = max(row_h, bh)

    atlas = Image.new('RGBA', (atlas_size, atlas_size), (0, 0, 0, 0))
    cube_defs = []  # 每 cube 的 (bone, element, block_u, block_v, 原版槽位表)

    for bone, e, bu, bv, bw, bh in placed:
        f, t = e['from'], e['to']
        w, h, dd = int(t[0] - f[0]), int(t[1] - f[1]), int(t[2] - f[2])
        slots = vanilla_slots(w, h, dd)
        cube_defs.append((bone, e, bu, bv, slots))
        # 每面：源区域 → 原版槽位
        faces = e.get('faces') or {}
        for face_name, slot in slots.items():
            fd = faces.get(face_name)
            if fd is None:
                continue
            uv = fd.get('uv')
            if uv is None:
                continue
            x1, y1, x2, y2 = [float(v) for v in uv]
            tid = fd.get('texture', 0)
            src_img = tex_images.get(tid)
            if src_img is None:
                continue
            # 源区域归一化
            sx0, sx1 = int(min(x1, x2)), int(max(x1, x2))
            sy0, sy1 = int(min(y1, y2)), int(max(y1, y2))
            src_region = src_img.crop((sx0, sy0, sx1, sy1))
            # 源反向 → 翻转
            hflip = x1 > x2
            vflip = y1 > y2
            # 原版 up/down 槽位 V 方向与 bbmodel 相反（翼膜等水平大面会上下颠倒），强制垂直翻转
            if face_name in ('up', 'down'):
                vflip = not vflip
            if face_name in flip:
                hflip = not hflip
            if hflip:
                src_region = src_region.transpose(Image.FLIP_LEFT_RIGHT)
            if vflip:
                src_region = src_region.transpose(Image.FLIP_TOP_BOTTOM)
            # 目标槽位
            u_off, v_off, tw, th = slot[:4]
            target_x = bu + u_off
            target_y = bv + v_off
            # 尺寸可能不一致（源区域像素 vs 槽位）——按槽位缩放
            src_region = src_region.resize((int(tw), int(th)), Image.NEAREST)
            atlas.paste(src_region, (int(target_x), int(target_y)))

    atlas.save(os.path.join(out, 'quanshouzhe.png'))
    print(f'图集: {atlas_size}x{atlas_size} -> {out}/quanshouzhe.png')

    # ── 4. 生成 Java ModelPart 代码 ──────────────────────
    lines = []
    lines.append('    public static LayerDefinition createBodyLayer() {')
    lines.append('        MeshDefinition mesh = new MeshDefinition();')
    lines.append('        PartDefinition root = mesh.getRoot();')
    indent = '        '

    def math_rad(deg):
        return round(deg * 3.141592653589793 / 180.0, 4)

    def fmt(v):
        # 统一加 f 后缀（float 参数），避免 double 字面量 → float 编译损失
        return '%gf' % v

    # bone 名 → Java 变量名（去非法字符 + 前缀）
    used_vars = set()
    def var_name(name):
        v = 'p_' + ''.join(c for c in name if c.isalnum() or c == '_')
        if not v:
            v = 'p_bone'
        n = 2
        base = v
        while v in used_vars:
            v = base + str(n)
            n += 1
        used_vars.add(v)
        return v

    def emit_bone(bone, parent_var, ind):
        """递归生成 PartDefinition 代码，子骨挂到父骨变量。"""
        children_lines = []
        for uid in bone['cubes']:
            e = elements.get(uid)
            if e is None or e.get('visibility', True) is False:
                continue
            f, t = e['from'], e['to']
            wo = bone['world_origin']
            # 原版 LivingEntityRenderer 渲染时 poseStack.scale(-1,-1,1)：模型 X/Y 与世界相反。
            # 因此 X/Y 取反（z 不变），用「原最大角」取反作为 addBox 起点，尺寸保持正。
            lx = -(t[0] - wo[0])
            ly = -(t[1] - wo[1])
            lz = f[2] - wo[2]
            w = t[0] - f[0]
            h = t[1] - f[1]
            dd = t[2] - f[2]
            # 找该 cube 的图集块 uv 起点
            bu = bv = 0
            for b, el, _bu, _bv, slots in cube_defs:
                if el is e:
                    bu, bv = _bu, _bv
                    break
            children_lines.append(
                f'    .texOffs({bu}, {bv}).addBox({fmt(lx)}, {fmt(ly)}, {fmt(lz)}, {fmt(w)}, {fmt(h)}, {fmt(dd)})')
        name = bone['name']
        var = var_name(name)
        builder = 'CubeListBuilder.create()'
        if children_lines:
            builder = 'CubeListBuilder.create()\n' + ('\n'.join(f'{ind}{cl}' for cl in children_lines))
        rot = bone['rotation']
        # origin 是世界坐标，PartPose offset = origin - 父.world_origin（相对父）；
        # X/Y 取反匹配原版坐标翻转；绕 X/Y 旋转取反（绕 Z 不变）
        pw = bone.get('parent_world_origin', [0, 0, 0])
        px = -(bone['origin'][0] - pw[0])
        py = -(bone['origin'][1] - pw[1])
        pz = bone['origin'][2] - pw[2]
        rx = math_rad(-rot[0]); ry = math_rad(-rot[1]); rz = math_rad(rot[2])
        lines.append(
            f'{ind}PartDefinition {var} = {parent_var}.addOrReplaceChild("{name}", {builder}, '
            f'PartPose.offsetAndRotation({fmt(px)}, {fmt(py)}, {fmt(pz)}, {fmt(rx)}, {fmt(ry)}, {fmt(rz)}));')
        for c in bone['children']:
            emit_bone(c, var, ind)

    for c in root['children']:
        emit_bone(c, 'root', indent)

    lines.append('        return LayerDefinition.create(mesh, %d, %d);' % (atlas_size, atlas_size))
    lines.append('    }')
    java = '\n'.join(lines)
    with open(os.path.join(out, 'createBodyLayer.txt'), 'w', encoding='utf-8') as f:
        f.write(java)
    print('Java 代码已生成: %s/createBodyLayer.txt' % out)


if __name__ == '__main__':
    main()
