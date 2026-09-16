"""Trusted Blender JSON scene adapter. No eval, exec, arbitrary code or imports
from request data. Run only in a fresh background Blender process, never over
the user's open Blender document. No avatar is installed into the Android app.
"""
import json
import math
import sys
from pathlib import Path

KINDS = {'sphere','cube','cone','cylinder','torus','plane'}

def vector(value, size, low, high):
    if not isinstance(value,list) or len(value)!=size or any(type(x) not in (int,float) or not math.isfinite(x) or not low<=x<=high for x in value):
        raise ValueError('Invalid finite numeric vector')
    return value

def validate(spec):
    if not isinstance(spec,dict) or set(spec)-{'objects','camera','render'}:
        raise ValueError('Scene accepts objects, camera and render only')
    objects=spec.get('objects')
    if not isinstance(objects,list) or not 1<=len(objects)<=80:
        raise ValueError('Scene must contain 1–80 primitives')
    for o in objects:
        if not isinstance(o,dict) or set(o)-{'kind','name','position','scale','rotation','color'} or o.get('kind') not in KINDS:
            raise ValueError('Unsupported object fields or primitive')
        if not isinstance(o.get('name','Object'),str) or len(o.get('name','Object'))>80:
            raise ValueError('Object name too long')
        vector(o.get('position',[0,0,0]),3,-100,100)
        vector(o.get('scale',[1,1,1]),3,0.01,20)
        vector(o.get('rotation',[0,0,0]),3,-360,360)
        vector(o.get('color',[0.8,0.8,0.8,1]),4,0,1)
    vector(spec.get('camera',[5,-8,5]),3,-100,100)
    if sum(x*x for x in spec.get('camera',[5,-8,5]))<0.1:
        raise ValueError('Camera must be away from the origin')
    if type(spec.get('render',True)) is not bool:
        raise ValueError('render must be a boolean')
    return spec

def build(spec, output):
    import bpy
    from mathutils import Vector
    validate(spec)
    # This is a separate --factory-startup background process, not the user's
    # running Blender instance. Only this newly created scene is replaced.
    bpy.ops.object.select_all(action='SELECT')
    bpy.ops.object.delete(use_global=False)
    for item in spec['objects']:
        kind=item['kind']
        if kind=='sphere': bpy.ops.mesh.primitive_uv_sphere_add(segments=24,ring_count=16)
        elif kind=='cube': bpy.ops.mesh.primitive_cube_add()
        elif kind=='cone': bpy.ops.mesh.primitive_cone_add(vertices=24)
        elif kind=='cylinder': bpy.ops.mesh.primitive_cylinder_add(vertices=24)
        elif kind=='torus': bpy.ops.mesh.primitive_torus_add(major_segments=32,minor_segments=12)
        else: bpy.ops.mesh.primitive_plane_add()
        obj=bpy.context.object
        obj.name=item.get('name',kind)
        obj.location=item.get('position',[0,0,0]); obj.scale=item.get('scale',[1,1,1])
        obj.rotation_euler=[math.radians(x) for x in item.get('rotation',[0,0,0])]
        mat=bpy.data.materials.new(obj.name+' material')
        mat.diffuse_color=item.get('color',[0.8,0.8,0.8,1]); mat.use_nodes=True
        mat.node_tree.nodes.get('Principled BSDF').inputs['Base Color'].default_value=mat.diffuse_color
        mat.node_tree.nodes.get('Principled BSDF').inputs['Roughness'].default_value=0.38
        obj.data.materials.append(mat)
        if kind not in ('cube','plane'):
            for polygon in obj.data.polygons: polygon.use_smooth=True
    bpy.ops.object.camera_add(location=spec.get('camera',[5,-8,5]))
    camera=bpy.context.object
    camera.rotation_euler=(Vector((0,0,0))-camera.location).to_track_quat('-Z','Y').to_euler()
    bpy.context.scene.camera=camera
    for location,energy,size in [((3,-4,6),900,5),((-4,-1,3),650,4),((0,4,5),1000,3)]:
        bpy.ops.object.light_add(type='AREA',location=location)
        light=bpy.context.object; light.data.energy=energy; light.data.shape='DISK'; light.data.size=size
        light.rotation_euler=(Vector((0,0,0))-light.location).to_track_quat('-Z','Y').to_euler()
    scene=bpy.context.scene
    engines=scene.render.bl_rna.properties['engine'].enum_items.keys()
    scene.render.engine='BLENDER_EEVEE_NEXT' if 'BLENDER_EEVEE_NEXT' in engines else 'BLENDER_EEVEE'
    scene.render.resolution_x=768; scene.render.resolution_y=768; scene.render.resolution_percentage=100
    scene.render.film_transparent=True
    bpy.ops.wm.save_as_mainfile(filepath=str(output/'scene.blend'))
    bpy.ops.export_scene.gltf(filepath=str(output/'scene.glb'),export_format='GLB',export_cameras=False,export_lights=False)
    if spec.get('render',True):
        scene.render.image_settings.file_format='PNG'; scene.render.filepath=str(output/'preview.png')
        bpy.ops.render.render(write_still=True)

if __name__=='__main__':
    args=sys.argv[sys.argv.index('--')+1:]
    spec_path=Path(args[0]).resolve(); out=Path(args[1]).resolve()
    build(validate(json.loads(spec_path.read_text())),out)
