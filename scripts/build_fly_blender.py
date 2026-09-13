#!/usr/bin/env python3
"""Author the original Meow cartoon fly as editable Blender geometry and export assets.

Run: blender -b --python scripts/build_fly_blender.py -- --output-dir /path/to/art --render
No downloads, subprocesses, third-party add-ons, or reference-image tracing.
"""
import argparse
import json
import gzip
import struct
import math
import os
import sys
from pathlib import Path

import bpy
import bmesh
from mathutils import Matrix, Vector


def arguments():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", default="art/avatars/cartoon-fly-3d")
    parser.add_argument("--render", action="store_true", help="Render the authored still previews")
    parser.add_argument("--resolution", type=int, default=900)
    parser.add_argument("--samples", type=int, default=48)
    return parser.parse_args(argv)


ARGS = arguments()
OUT = Path(ARGS.output_dir).resolve()
OUT.mkdir(parents=True, exist_ok=True)
PALETTE = {
    "shell": "3B5154", "shell_light": "708B85", "ink": "192D31",
    "green": "78A758", "dark_green": "416C48", "red": "EC6856",
    "cream": "FFF2D0", "tongue": "D58E86", "wing": "B7DFDF",
    "vein": "71989E", "wood": "A88662", "wood_dark": "735F49",
    "book": "477C77", "pages": "E6D6B5", "water": "72A9B1",
    "glass": "B9D7D4", "ball": "DF9965", "wall": "DADAC4",
    "floor": "B9B69A", "sky": "C0DFD8",
}
MATERIALS = {}
EXPORT_MATERIALS = {}
PARTS = []
JOINTS = {}
ACTIONS = {}
# Blender (X right, Y back, Z up) -> runtime (X right, Y up, Z front).
BASIS = Matrix(((1, 0, 0, 0), (0, 0, 1, 0), (0, -1, 0, 0), (0, 0, 0, 1)))


def linear_channel(v):
    return v / 12.92 if v <= .04045 else ((v + .055) / 1.055) ** 2.4


def rgba(key, alpha=1.0):
    text = PALETTE.get(key, key)
    return tuple(linear_channel(int(text[i:i + 2], 16) / 255) for i in (0, 2, 4)) + (alpha,)


def point(x, y, z):
    return Vector((x, -z, y))


def make_material(key, alpha=1.0):
    name = key + ("_transparent" if alpha < 1 else "")
    if name in MATERIALS:
        return MATERIALS[name]
    base = rgba(key, alpha)
    material = bpy.data.materials.new("toon_" + name)
    material.diffuse_color = base
    material.use_nodes = True
    nodes = material.node_tree.nodes
    nodes.clear()
    output = nodes.new("ShaderNodeOutputMaterial")
    if alpha < 1:
        shader = nodes.new("ShaderNodeBsdfPrincipled")
        shader.inputs["Base Color"].default_value = base
        shader.inputs["Roughness"].default_value = .65
        shader.inputs["Alpha"].default_value = alpha
        material.node_tree.links.new(shader.outputs["BSDF"], output.inputs["Surface"])
        if hasattr(material, "surface_render_method"):
            material.surface_render_method = "DITHERED"
        elif hasattr(material, "blend_method"):
            material.blend_method = "BLEND"
        material.use_backface_culling = False
    else:
        diffuse = nodes.new("ShaderNodeBsdfDiffuse")
        diffuse.inputs["Color"].default_value = (1, 1, 1, 1)
        shader_rgb = nodes.new("ShaderNodeShaderToRGB")
        ramp = nodes.new("ShaderNodeValToRGB")
        ramp.color_ramp.interpolation = "CONSTANT"
        ramp.color_ramp.elements.remove(ramp.color_ramp.elements[1])
        for index, (threshold, multiplier) in enumerate(((0, .55), (.25, .80), (.62, 1.0))):
            entry = ramp.color_ramp.elements[0] if index == 0 else ramp.color_ramp.elements.new(threshold)
            entry.position = threshold
            entry.color = tuple(min(1, c * multiplier) for c in base[:3]) + (1,)
        emission = nodes.new("ShaderNodeEmission")
        material.node_tree.links.new(diffuse.outputs[0], shader_rgb.inputs[0])
        material.node_tree.links.new(shader_rgb.outputs[0], ramp.inputs[0])
        material.node_tree.links.new(ramp.outputs[0], emission.inputs[0])
        material.node_tree.links.new(emission.outputs[0], output.inputs[0])
    export = bpy.data.materials.new("gltf_" + name)
    export.diffuse_color = base
    export.use_nodes = True
    bsdf = export.node_tree.nodes.get("Principled BSDF")
    bsdf.inputs["Base Color"].default_value = base
    bsdf.inputs["Roughness"].default_value = 1.0
    bsdf.inputs["Alpha"].default_value = alpha
    if alpha < 1:
        if hasattr(export, "surface_render_method"):
            export.surface_render_method = "DITHERED"
        elif hasattr(export, "blend_method"):
            export.blend_method = "BLEND"
    MATERIALS[name] = material
    EXPORT_MATERIALS[material.name] = export
    return material


def empty(name, xyz, parent=None):
    obj = bpy.data.objects.new(name, None)
    bpy.context.collection.objects.link(obj)
    obj.parent = parent
    obj.location = point(*xyz)
    obj.empty_display_type = "SPHERE"
    obj.empty_display_size = .04
    JOINTS[name] = obj
    return obj


def finish(obj, name, xyz, scale, material, parent, outline=True, asset=True):
    obj.name = name
    obj.parent = parent
    obj.location = point(*xyz)
    # Mesh primitives are isotropic before scale; reorder physical up/depth axes.
    obj.scale = (scale[0], scale[2], scale[1])
    obj.data.materials.append(make_material(material, .60 if material == "wing" else 1))
    obj["mesh_part"] = name
    obj["runtime_color"] = list(rgba(material, .60 if material == "wing" else 1))
    if asset:
        PARTS.append(obj)
    if outline:
        add_hull(obj)
    return obj


def add_hull(obj):
    hull = obj.copy()
    hull.data = obj.data.copy()
    hull.name = obj.name + "__outline"
    bpy.context.collection.objects.link(hull)
    hull.parent = obj
    hull.location = (0, 0, 0)
    hull.rotation_euler = (0, 0, 0)
    hull.scale = (1.035, 1.035, 1.035)
    hull.data.materials.clear()
    hull.data.materials.append(OUTLINE)
    bm = bmesh.new()
    bm.from_mesh(hull.data)
    bmesh.ops.reverse_faces(bm, faces=list(bm.faces))
    bm.to_mesh(hull.data)
    bm.free()
    hull["outline_only"] = True
    if hasattr(hull, "visible_shadow"):
        hull.visible_shadow = False
    return hull


def ellipsoid(name, xyz, scale, color, parent=None, outline=True, asset=True):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=24, ring_count=16, radius=1)
    obj = bpy.context.object
    for face in obj.data.polygons:
        face.use_smooth = True
    return finish(obj, name, xyz, scale, color, parent, outline, asset)


def box(name, xyz, scale, color, parent=None, outline=True, asset=True):
    bpy.ops.mesh.primitive_cube_add(size=2)
    obj = finish(bpy.context.object, name, xyz, scale, color, parent, False, asset)
    # Applied bevel makes a real editable rounded object in both GLB and mesh-parts JSON.
    bpy.context.view_layer.objects.active = obj
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    bevel = obj.modifiers.new("soft_cartoon_edges", "BEVEL")
    bevel.width = .012
    bevel.segments = 2
    bpy.ops.object.modifier_apply(modifier=bevel.name)
    if outline:
        add_hull(obj)
    return obj


def segment(name, start, end, radius, color, parent, outline=True):
    a, b = point(*start), point(*end)
    middle = (a + b) * .5
    delta = b - a
    obj = ellipsoid(name, (0, 0, 0), (radius, delta.length * .5 + radius * .3, radius), color, parent, False)
    obj.location = middle
    # Blender ellipsoid long axis is Z after scale reordering.
    obj.rotation_mode = "QUATERNION"
    obj.rotation_quaternion = Vector((0, 0, 1)).rotation_difference(delta.normalized())
    if outline:
        add_hull(obj)
    return obj


def minus(a, b):
    return tuple(x - y for x, y in zip(a, b))


def limb(name, shoulder, elbow, tip, radius, root, is_leg=False):
    upper = empty(name + "_upper", shoulder, root)
    lower = empty(name + "_forearm", minus(elbow, shoulder), upper)
    hand = empty(name + "_hand", minus(tip, elbow), lower)
    segment(name + "_upper_mesh", (0, 0, 0), minus(elbow, shoulder), radius, "shell", upper)
    segment(name + "_forearm_mesh", (0, 0, 0), minus(tip, elbow), radius * .90, "shell", lower)
    if is_leg:
        ellipsoid(name + "_shoe", (0, -.015, .085), (.14, .075, .22), "shell", hand)
    else:
        ellipsoid(name + "_palm", (0, 0, 0), (.105, .095, .086), "shell", hand)
        ellipsoid(name + "_thumb", (.055, .025, .018), (.042, .042, .044), "shell", hand)
        ellipsoid(name + "_knuckle_light", (-.024, .033, .067), (.026, .019, .013), "shell_light", hand, False)
    return upper, lower, hand


def curved_wing(name, root):
    vertices, faces = [], []
    # Closed curved airfoil surface matching the native procedural wing's actual volume.
    spans, rings = 18, 12
    for i in range(spans + 1):
        t = i / spans
        width = math.sin(math.pi * t) * (.23 + .18 * t)
        for j in range(rings):
            a = 2 * math.pi * j / rings
            vertices.append(tuple(point(width * math.cos(a),
                .045 * math.sin(math.pi * t) * math.sin(a) + .10 * math.sin(math.pi * t), t * 1.48)))
    for i in range(spans):
        for j in range(rings):
            a = i * rings + j
            b = i * rings + (j + 1) % rings
            c = (i + 1) * rings + (j + 1) % rings
            d = (i + 1) * rings + j
            faces.append((a, b, c, d))
    mesh = bpy.data.meshes.new(name + "_geometry")
    mesh.from_pydata(vertices, [], faces)
    mesh.update()
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(obj)
    obj.parent = root
    obj.data.materials.append(make_material("wing", .60))
    obj["mesh_part"] = name
    obj["runtime_color"] = list(rgba("wing", .60))
    PARTS.append(obj)
    for face in mesh.polygons:
        face.use_smooth = True
    segment(name + "_vein", (0, .02, .05), (0, .095, 1.25), .009, "vein", root, False)
    for i in range(1, 4):
        segment(name + "_vein_" + str(i), (0, .07, .27 * i), (.17, .09, .27 * i + .17), .006, "vein", root, False)
    return obj


def key_action(obj, name, path, values):
    """Same-named NLA tracks group per-node channels into named GLB clips."""
    if obj.animation_data is None:
        obj.animation_data_create()
    action = bpy.data.actions.new(name + "__" + obj.name)
    obj.animation_data.action = action
    original = tuple(getattr(obj, path))
    for frame, value in values:
        setattr(obj, path, value)
        obj.keyframe_insert(data_path=path, frame=frame)
    setattr(obj, path, original)
    obj.animation_data.action = None
    track = obj.animation_data.nla_tracks.new()
    track.name = name
    strip = track.strips.new(name, 1, action)
    strip.name = name
    strip.extrapolation = "NOTHING"
    track.mute = True
    ACTIONS.setdefault(name, []).append((obj, track))


def activate(name=None):
    for tracks in ACTIONS.values():
        for obj, track in tracks:
            track.mute = track.name != name
    bpy.context.scene.frame_set(1)
    bpy.context.view_layer.update()


def make_character():
    root = empty("rig_root", (0, 1.35, 0))
    root.scale = (.5, .5, .5)
    root["design"] = "Original Meow fly; authored cartoon anatomy, not a biological neural simulation"
    root["axes"] = "Runtime X right / Y up / Z front; Blender X right / Z up / -Y front"
    ellipsoid("body", (0, -.27, -.04), (.43, .57, .39), "green", root)
    ellipsoid("abdomen_band_upper", (0, -.38, .01), (.435, .12, .388), "dark_green", root)
    ellipsoid("abdomen_band_lower", (0, -.63, -.015), (.31, .10, .285), "dark_green", root)
    ellipsoid("thorax", (0, .12, -.02), (.42, .36, .35), "shell", root)
    ellipsoid("head", (0, .49, .13), (.62, .52, .47), "shell", root)
    ellipsoid("head_highlight", (-.22, .71, -.14), (.24, .17, .12), "shell_light", root, False)
    for side, label in ((-1, "L"), (1, "R")):
        limb("leg_" + label, (side * .20, -.57, 0), (side * .29, -.76, 0), (side * .28, -1, 0), .056, root, True)
        limb("arm_rear_" + label, (side * .34, -.08, -.15), (side * .56, -.25, -.18), (side * .69, -.35, -.09), .050, root)
        limb("arm_front_" + label, (side * .43, .21, .07), (side * .67, .08, .16), (side * .82, .04, .19), .063, root)
        eye = empty("eye_" + label + "_joint", (side * .30, .53, .48), root)
        ellipsoid("eye_" + label, (0, 0, 0), (.30, .335, .23), "red", eye)
        gaze = empty("pupil_" + label + "_joint", (0, 0, 0), eye)
        ellipsoid("pupil_" + label, (0, -.01, .209), (.102, .157, .045), "ink", gaze, False)
        ellipsoid("eye_glint_" + label, (-.026, .03, .249), (.031, .039, .012), "cream", gaze, False)
        ellipsoid("eye_glint_small_" + label, (.025, -.04, .247), (.012, .015, .009), "cream", gaze, False)
        for i in range(4):
            a = -.8 + i * .49
            ellipsoid("eye_facet_%s_%s" % (label, i), (side * .20, math.sin(a) * .335 * .68, .12 + math.cos(a) * .04),
                (.016, .020, .010), "A83F4A", eye, False)
        antenna = empty("antenna_" + label + "_joint", (side * .19, .89, .12), root)
        segment("antenna_" + label, (0, 0, 0), (side * .04, .21, -.03), .026, "shell", antenna)
        ellipsoid("antenna_tip_" + label, (side * .04, .21, -.03), (.057, .080, .055), "shell_light", antenna)
        for i in range(3):
            segment("arista_%s_%s" % (label, i), (side * .05, .21 + i * .025, -.03),
                (side * (.15 + i * .018), .24 + i * .046, -.02), .009, "shell", antenna, False)
        hinge = empty("wing_" + label + "_joint", (side * .24, .32, -.25), root)
        # Compose runtime yaw and pitch, then convert the local matrix to Blender axes.
        runtime_rot = Matrix.Rotation(math.radians(side * 116), 4, "Y") @ Matrix.Rotation(math.radians(-42), 4, "X")
        hinge.rotation_euler = (BASIS.inverted() @ runtime_rot @ BASIS).to_euler()
        curved_wing("wing_" + label, hinge)
        original = tuple(hinge.rotation_euler)
        key_action(hinge, "fly", "rotation_euler", [(f, (original[0], original[1] + side * math.radians(a), original[2]))
            for f, a in ((1, -23), (4, 23), (7, -23), (10, 23), (13, -23), (16, 23), (19, -23), (22, 23), (25, -23))])
        key_action(eye, "blink", "scale", [(1, (1, 1, 1)), (10, (1, 1, 1)), (13, (1, 1, .08)), (16, (1, 1, 1)), (30, (1, 1, 1))])
        ant_rot = tuple(antenna.rotation_euler)
        key_action(antenna, "talk", "rotation_euler", [(f, (ant_rot[0], side * a, ant_rot[2])) for f, a in ((1, 0), (8, .09), (16, -.04), (24, .06), (30, 0))])
    mouth = empty("mouth_joint", (0, .185, .566), root)
    ellipsoid("mouth", (0, 0, 0), (.12, .035, .021), "ink", mouth, False)
    ellipsoid("tongue", (0, -.016, .023), (.063, .014, .007), "tongue", mouth, False)
    # A single soft smile remains visible around a small opening, without a snout/tube.
    segment("smile_L", (-.13, .205, .554), (-.065, .159, .573), .013, "ink", root, False)
    segment("smile_R", (.065, .159, .573), (.13, .205, .554), .013, "ink", root, False)
    key_action(mouth, "talk", "scale", [(1, (1, 1, 1)), (5, (1, 1, 2.8)), (9, (1, 1, 1.2)), (14, (1, 1, 2.2)), (20, (1, 1, .8)), (25, (1, 1, 2.5)), (30, (1, 1, 1))])
    for label, sign in (("L", -1), ("R", 1)):
        upper = JOINTS["leg_" + label + "_upper"]
        lower = JOINTS["leg_" + label + "_forearm"]
        key_action(upper, "walk", "rotation_euler", [(f, (sign * angle, 0, 0)) for f, angle in ((1, -.35), (9, .35), (17, -.35), (25, .35), (33, -.35))])
        key_action(lower, "walk", "rotation_euler", [(f, (angle, 0, 0)) for f, angle in ((1, .1), (9, .42), (17, .1), (25, .42), (33, .1))])
        key_action(upper, "fly", "rotation_euler", [(1, (.48, sign * -.20, 0)), (13, (.60, sign * -.22, 0)), (25, (.48, sign * -.20, 0))])
    wave = JOINTS["arm_front_R_upper"]
    key_action(wave, "wave", "rotation_euler", [(f, (0, -angle, 0)) for f, angle in ((1, 0), (8, 1.12), (15, .88), (22, 1.17), (29, .90), (36, 0))])
    key_action(JOINTS["arm_front_R_forearm"], "wave", "rotation_euler", [(f, (0, angle, 0)) for f, angle in ((1, 0), (8, -.3), (15, .1), (22, -.3), (29, .1), (36, 0))])
    key_action(root, "fly", "location", [(f, tuple(point(0, y, z))) for f, y, z in ((1, 1.55, 0), (13, 1.67, -.08), (25, 1.55, 0))])
    activate()
    return root


def make_room():
    room = empty("room_root", (0, 0, 0))
    box("floor", (0, -.10, 0), (4.5, .10, 3.7), "floor", room, False, False)
    box("back_wall", (0, 3, -2.6), (4.5, 3, .10), "wall", room, False, False)
    box("table_top", (0, .72, 0), (2.10, .08, 1), "wood", room, True, False)
    for side in (-1, 1):
        for z in (-.80, .80):
            box("table_leg_%s_%s" % (side, z), (side * 1.9, .35, z), (.075, .35, .075), "wood_dark", room, True, False)
    box("window_frame", (-1.65, 3.1, -2.42), (1, .95, .06), "657D77", room, True, False)
    box("window_sky", (-1.65, 3.1, -2.33), (.88, .83, .035), "sky", room, False, False)
    box("window_mullion", (-1.65, 3.1, -2.18), (.027, .86, .035), "708781", room, False, False)
    box("window_crossbar", (-1.65, 3.1, -2.17), (.9, .025, .035), "708781", room, False, False)
    book = empty("prop_book", (-1, .8, .5), room)
    box("book_cover", (0, .055, 0), (.28, .055, .20), "book", book, True, False)
    box("book_pages", (.015, .058, 0), (.248, .030, .181), "pages", book, False, False)
    box("book_top", (0, .111, 0), (.28, .012, .20), "book", book, False, False)
    glass = empty("prop_glass", (1, .8, .5), room)
    ellipsoid("glass_body", (0, .20, 0), (.125, .20, .125), "glass", glass, True, False)
    ellipsoid("water_surface", (0, .365, 0), (.113, .022, .113), "water", glass, False, False)
    ellipsoid("ball", (1.6, .98, 0), (.18, .18, .18), "ball", room, True, False)
    for i in range(3):
        segment("pan_handle_" + str(i), (1.1 + i * .67, 3.3, -2.17), (1.1 + i * .67, 2.89, -2.17), .027, "657D77", room)
        ellipsoid("pan_" + str(i), (1.1 + i * .67, 2.67, -2.13), (.23, .25, .06), "B89169", room, True, False)
    return room


def export_meshparts(root):
    """Float positions/normals are in runtime axes, local to their hierarchy node."""
    activate()
    bpy.context.view_layer.update()
    nodes, meshes = [], []
    hierarchy = [root] + list(root.children_recursive)
    hierarchy = [o for o in hierarchy if not o.get("outline_only")]
    allowed = set(hierarchy)
    for obj in hierarchy:
        local = BASIS @ obj.matrix_local @ BASIS.inverted()
        nodes.append({"name": obj.name, "parent": obj.parent.name if obj.parent in allowed else None,
            "localMatrix": [round(local[row][col], 7) for col in range(4) for row in range(4)]})
        if obj.type != "MESH":
            continue
        mesh = obj.data
        mesh.calc_loop_triangles()
        positions, normals, indices = [], [], []
        # Expanded triangle vertices preserve smooth corner normals without normal-index ambiguity.
        for triangle in mesh.loop_triangles:
            for loop_index in triangle.loops:
                loop = mesh.loops[loop_index]
                vertex = mesh.vertices[loop.vertex_index]
                position = BASIS.to_3x3() @ vertex.co
                normal = BASIS.to_3x3() @ vertex.normal
                indices.append(len(indices))
                positions.extend(round(float(v), 7) for v in position)
                normals.extend(round(float(v), 7) for v in normal.normalized())
        meshes.append({"node": obj.name, "positions": positions, "normals": normals, "indices": indices,
            "color": list(obj.get("runtime_color", (1, 1, 1, 1))),
            "transparent": obj.name.startswith("wing_") and "vein" not in obj.name})
    document = {"version": 1, "coordinateSystem": "X-right Y-up Z-front", "units": "world",
        "root": root.name, "rootRestY": 1.35, "outline": "runtime inverted hull; outline meshes omitted",
        "nodes": nodes, "meshes": meshes, "animationClips": sorted(ACTIONS),
        "provenance": "Original procedurally authored geometry; no image tracing or video likeness claim"}
    payload = json.dumps(document, separators=(",", ":")).encode("utf-8")
    (OUT / "fly_meshparts.json").write_bytes(payload)
    with gzip.GzipFile(filename=str(OUT / "fly_meshparts.json.gz"), mode="wb", mtime=0) as stream:
        stream.write(payload)
    return len(nodes), len(meshes), sum(len(m["indices"]) // 3 for m in meshes)


def look_at(obj, target):
    obj.rotation_euler = (Vector(target) - obj.location).to_track_quat("-Z", "Y").to_euler()


def setup_scene():
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete(use_global=False)
    scene = bpy.context.scene
    scene.render.engine = "BLENDER_EEVEE_NEXT" if bpy.app.version >= (4, 2, 0) else "BLENDER_EEVEE"
    if hasattr(scene, "eevee"):
        if hasattr(scene.eevee, "taa_render_samples"):
            scene.eevee.taa_render_samples = max(8, min(256, ARGS.samples))
        if hasattr(scene.eevee, "use_gtao"):
            scene.eevee.use_gtao = True
            scene.eevee.gtao_distance = 3
            scene.eevee.gtao_factor = 1.15
    scene.render.resolution_x = max(256, min(2400, ARGS.resolution))
    scene.render.resolution_y = round(scene.render.resolution_x * 4 / 3)
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    scene.render.film_transparent = False
    scene.render.fps = 30
    scene.frame_start = 1
    scene.frame_end = 36
    scene.view_settings.view_transform = "Standard"
    try:
        scene.view_settings.look = "Medium High Contrast"
    except TypeError:
        pass
    scene.world.color = (.25, .25, .25)
    scene.world.use_nodes = True
    scene.world.node_tree.nodes["Background"].inputs[0].default_value = (.65, .72, .68, 1)
    scene.world.node_tree.nodes["Background"].inputs[1].default_value = .35
    bpy.ops.object.light_add(type="AREA", location=(-3.5, -4, 6))
    key = bpy.context.object
    key.name = "key_softbox"
    key.data.energy = 650
    key.data.shape = "DISK"
    key.data.size = 4
    look_at(key, (0, 0, 1.35))
    bpy.ops.object.light_add(type="AREA", location=(3, 1, 4))
    fill = bpy.context.object
    fill.name = "rim_softbox"
    fill.data.energy = 400
    fill.data.size = 3
    look_at(fill, (0, 0, 1.5))
    bpy.ops.object.camera_add(location=(3.4, -6, 3.3))
    camera = bpy.context.object
    camera.name = "portrait_camera"
    camera.data.type = "PERSP"
    camera.data.lens = 60
    look_at(camera, (0, 0, 1.35))
    scene.camera = camera
    return scene, camera


SCENE, CAMERA = setup_scene()
OUTLINE = bpy.data.materials.new("inverted_hull_ink")
OUTLINE.diffuse_color = rgba("ink")
OUTLINE.use_nodes = True
OUTLINE.use_backface_culling = True
outline_nodes = OUTLINE.node_tree.nodes
outline_nodes.clear()
ink = outline_nodes.new("ShaderNodeEmission")
ink.inputs[0].default_value = rgba("ink")
ink_output = outline_nodes.new("ShaderNodeOutputMaterial")
OUTLINE.node_tree.links.new(ink.outputs[0], ink_output.inputs[0])
ROOT = make_character()
ROOM = make_room()
COUNTS = export_meshparts(ROOT)
REST_TRANSFORMS = {}
for node in [ROOT] + list(ROOT.children_recursive):
    if not node.get("outline_only"):
        runtime_local = BASIS @ node.matrix_local @ BASIS.inverted()
        loc, rot, scale = runtime_local.decompose()
        REST_TRANSFORMS[node.name] = {"translation": list(loc),
            "rotation": [rot.x, rot.y, rot.z, rot.w], "scale": list(scale)}
# Save editable authored scene with all animation tracks and native toon materials.
activate()
bpy.ops.wm.save_as_mainfile(filepath=str(OUT / "cartoon-fly.blend"))

# Portable GLB uses honest base-color PBR materials; native/Blender supply cel shading.
# Export character only; the .blend also contains the full editable room and camera.
saved_slots = []
for obj in [ROOT] + list(ROOT.children_recursive):
    if obj.type == "MESH" and not obj.get("outline_only"):
        for slot in obj.material_slots:
            if slot.material and slot.material.name in EXPORT_MATERIALS:
                saved_slots.append((slot, slot.material))
                slot.material = EXPORT_MATERIALS[slot.material.name]
bpy.ops.object.select_all(action="DESELECT")
ROOT.select_set(True)
for child in ROOT.children_recursive:
    if not child.get("outline_only"):
        child.select_set(True)
# NLA strip names group animations consistently; muted tracks stay excluded from rest evaluation.
for tracks in ACTIONS.values():
    for obj, track in tracks:
        track.mute = False
SCENE.frame_set(0)
export_options = dict(filepath=str(OUT / "cartoon-fly.glb"), export_format="GLB", use_selection=True,
    export_animations=True, export_nla_strips=True, export_animation_mode="NLA_TRACKS",
    export_extras=True, export_yup=True)
valid_properties = {prop.identifier for prop in bpy.ops.export_scene.gltf.get_rna_type().properties}
export_options = {key: value for key, value in export_options.items() if key in valid_properties}
bpy.ops.export_scene.gltf(**export_options)
activate()
glb = (OUT / "cartoon-fly.glb").read_bytes()
json_length, chunk_type = struct.unpack_from("<II", glb, 12)
if chunk_type != 0x4E4F534A:
    raise RuntimeError("Exported GLB did not begin with a JSON chunk")
glb_document = json.loads(glb[20:20 + json_length].decode("utf-8"))
# Blender4.2 NLA export resets unkeyed channel defaults (including the root and wing rest
# rotations). Restore authored rest TRS in the portable file independently of animation clips.
for node in glb_document.get("nodes", []):
    rest = REST_TRANSFORMS.get(node.get("name"))
    if rest:
        node.pop("matrix", None)
        node.update(rest)
json_chunk = json.dumps(glb_document, separators=(",", ":")).encode("utf-8")
json_chunk += b" " * ((-len(json_chunk)) % 4)
remaining_chunks = glb[20 + json_length:]
fixed_glb = struct.pack("<III", 0x46546C67, 2, 20 + len(json_chunk) + len(remaining_chunks))
fixed_glb += struct.pack("<II", len(json_chunk), 0x4E4F534A) + json_chunk + remaining_chunks
(OUT / "cartoon-fly.glb").write_bytes(fixed_glb)
exported_clips = [clip.get("name", "") for clip in glb_document.get("animations", [])]
missing_clips = [name for name in ACTIONS if not any(name == clip or clip.startswith(name + "_") for clip in exported_clips)]
if missing_clips:
    raise RuntimeError("GLB animation export missing clips: " + ", ".join(missing_clips) + "; got " + repr(exported_clips))
for slot, material in saved_slots:
    slot.material = material

# The4.2 glTF exporter also mutates live parent transforms while restoring NLA state.
# Reopen the saved authoring scene rather than rendering that contaminated exporter state.
bpy.ops.wm.open_mainfile(filepath=str(OUT / "cartoon-fly.blend"))
SCENE = bpy.context.scene
CAMERA = bpy.data.objects["portrait_camera"]
ROOT = bpy.data.objects["rig_root"]
ACTIONS.clear()
for obj in bpy.data.objects:
    if obj.animation_data:
        for track in obj.animation_data.nla_tracks:
            if track.name in ("fly", "walk", "wave", "talk", "blink"):
                ACTIONS.setdefault(track.name, []).append((obj, track))
bpy.context.view_layer.update()
for name, expected in REST_TRANSFORMS.items():
    obj = bpy.data.objects[name]
    restored = BASIS @ obj.matrix_local @ BASIS.inverted()
    location, rotation, scale = restored.decompose()
    if max(abs(location[i] - expected["translation"][i]) for i in range(3)) > .0001:
        raise RuntimeError("Authoring rest location was not restored: " + name)
    if max(abs(scale[i] - expected["scale"][i]) for i in range(3)) > .0001:
        raise RuntimeError("Authoring rest scale was not restored: " + name)
previews = []
if ARGS.render:
    # The camera stays in the scene and views a real rotated object, not reprojected flat artwork.
    for label, yaw in (("front", 0), ("quarter", -32), ("side", -90), ("back", 180)):
        activate()
        ROOT.rotation_euler = (0, 0, math.radians(yaw))
        CAMERA.location = (0, -4.0, 2.5)
        CAMERA.data.lens = 68
        look_at(CAMERA, (0, 0, 1.4))
        SCENE.render.filepath = str(OUT / ("fly-" + label + ".png"))
        bpy.ops.render.render(write_still=True)
        previews.append("fly-" + label + ".png")
    ROOT.rotation_euler = (0, 0, 0)
    activate("fly")
    SCENE.frame_set(13)
    CAMERA.location = (1.6, -4.0, 2.6)
    look_at(CAMERA, (0, 0, 1.5))
    SCENE.render.filepath = str(OUT / "fly-flight-quarter.png")
    bpy.ops.render.render(write_still=True)
    previews.append("fly-flight-quarter.png")
    activate()

manifest = {
    "asset": "Meow original cartoon fruit fly", "blenderVersion": bpy.app.version_string,
    "files": ["cartoon-fly.blend", "cartoon-fly.glb", "fly_meshparts.json", "fly_meshparts.json.gz"] + previews,
    "nodes": COUNTS[0], "meshParts": COUNTS[1], "triangles": COUNTS[2],
    "authoredAnimationClips": sorted(ACTIONS), "verifiedGlbAnimationClips": exported_clips, "previewsRendered": bool(ARGS.render),
    "previewStyle": "Native EEVEE cel-shader and inverted hull, genuine volumetric geometry",
    "glbStyle": "Portable base-color material; host renderer supplies cel shading and outline",
    "referencePolicy": "Original design inspired by supplied fly references; no exact video likeness asserted",
}
(OUT / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
print("MEOW_FLY_ASSET_MANIFEST=" + str(OUT / "manifest.json"))
print(json.dumps(manifest, indent=2))
