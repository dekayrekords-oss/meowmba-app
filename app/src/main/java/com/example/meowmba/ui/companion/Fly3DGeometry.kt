package com.example.meowmba.ui.companion

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Immutable CPU geometry survives EGL loss. GPU names are recreated on the GL thread. */
internal class Fly3DMesh(private val vertices: FloatArray) {
    private var buffer = 0
    val vertexCount = vertices.size / 6

    fun upload() {
        val names = IntArray(1)
        GLES20.glGenBuffers(1, names, 0)
        buffer = names[0]
        val data = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        data.put(vertices).position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffer)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertices.size * 4, data, GLES20.GL_STATIC_DRAW)
    }

    fun bind(position: Int, normal: Int) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffer)
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glEnableVertexAttribArray(normal)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 24, 0)
        GLES20.glVertexAttribPointer(normal, 3, GLES20.GL_FLOAT, false, 24, 12)
    }

    fun draw() = GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
}

/** Primitive surfaces have actual thickness and outward winding; no billboard sprites. */
internal object Fly3DGeometry {
    fun sphere(slices: Int = 20, stacks: Int = 14): Fly3DMesh {
        require(slices in 6..64 && stacks in 4..48)
        val values = ArrayList<Float>(slices * stacks * 36)
        fun point(longitude: Double, latitude: Double) {
            val x = (sin(latitude) * cos(longitude)).toFloat()
            val y = cos(latitude).toFloat()
            val z = (sin(latitude) * sin(longitude)).toFloat()
            values.add(x); values.add(y); values.add(z)
            values.add(x); values.add(y); values.add(z)
        }
        for (row in 0 until stacks) for (column in 0 until slices) {
            val a = column * 2.0 * PI / slices
            val b = (column + 1) * 2.0 * PI / slices
            val p = row * PI / stacks
            val q = (row + 1) * PI / stacks
            // Longitude then latitude makes outward facing CCW triangles.
            point(a, p); point(b, p); point(b, q)
            point(a, p); point(b, q); point(a, q)
        }
        return Fly3DMesh(values.toFloatArray())
    }

    /** Beveled cuboid: 44 triangles instead of 12; no subdivisions, textures or extra draws.
     * Broad planes stay flat while twelve chamfers catch light and soften toy-box corners.
     */
    fun cube(): Fly3DMesh {
        val out = ArrayList<Float>(44 * 18)
        val inset = .92f
        fun triangle(a:FloatArray,b:FloatArray,c:FloatArray,n:FloatArray) {
            val crossX=(b[1]-a[1])*(c[2]-a[2])-(b[2]-a[2])*(c[1]-a[1])
            val crossY=(b[2]-a[2])*(c[0]-a[0])-(b[0]-a[0])*(c[2]-a[2])
            val crossZ=(b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0])
            val forward=crossX*n[0]+crossY*n[1]+crossZ*n[2]>0f
            for(v in if(forward)arrayOf(a,b,c) else arrayOf(a,c,b)) {
                out.add(v[0]);out.add(v[1]);out.add(v[2])
                out.add(n[0]);out.add(n[1]);out.add(n[2])
            }
        }
        fun quad(a:FloatArray,b:FloatArray,c:FloatArray,d:FloatArray,n:FloatArray) {
            triangle(a,b,c,n);triangle(a,c,d,n)
        }
        for(axis in 0..2)for(sign in listOf(-1f,1f)) {
            val u=(axis+1)%3;val v=(axis+2)%3
            fun point(x:Float,y:Float)=FloatArray(3).apply { this[axis]=sign;this[u]=x;this[v]=y }
            val normal=FloatArray(3).apply { this[axis]=sign }
            quad(point(-inset,-inset),point(inset,-inset),point(inset,inset),point(-inset,inset),normal)
        }
        for(axis in 0..2)for(a in listOf(-1f,1f))for(b in listOf(-1f,1f)) {
            val u=(axis+1)%3;val v=(axis+2)%3
            fun point(t:Float,outerU:Boolean)=FloatArray(3).apply {
                this[axis]=t;this[u]=a*if(outerU)1f else inset;this[v]=b*if(outerU)inset else 1f
            }
            val normal=FloatArray(3).apply { this[u]=a*.7071068f;this[v]=b*.7071068f }
            quad(point(-inset,true),point(inset,true),point(inset,false),point(-inset,false),normal)
        }
        for(x in listOf(-1f,1f))for(y in listOf(-1f,1f))for(z in listOf(-1f,1f)) {
            triangle(floatArrayOf(x,y*inset,z*inset),floatArrayOf(x*inset,y,z*inset),
                floatArrayOf(x*inset,y*inset,z),floatArrayOf(x*.5773503f,y*.5773503f,z*.5773503f))
        }
        return Fly3DMesh(out.toFloatArray())
    }

    /** Open tapered shade, real thickness supplied by the lip/rim in scene composition. */
    fun shade():Fly3DMesh {
        val out=ArrayList<Float>(24*36)
        fun point(a:Double,y:Float,r:Float) {
            out.add(cos(a).toFloat()*r);out.add(y);out.add(sin(a).toFloat()*r)
            out.add(cos(a).toFloat()*.97f);out.add(.24f);out.add(sin(a).toFloat()*.97f)
        }
        for(i in 0 until 24){
            val a=i*PI/12;val b=(i+1)*PI/12
            point(a,-1f,1f);point(a,1f,.5f);point(b,1f,.5f)
            point(a,-1f,1f);point(b,1f,.5f);point(b,-1f,1f)
        }
        return Fly3DMesh(out.toFloatArray())
    }

    /** Unit torus in XZ, major radius1 and small tube .055. Reused for rims and ripples. */
    fun ring():Fly3DMesh {
        val out=ArrayList<Float>(24*6*36)
        fun vertex(a:Double,b:Double) {
            val radius=1f+.055f*cos(b).toFloat()
            out.add(radius*cos(a).toFloat());out.add(.055f*sin(b).toFloat());out.add(radius*sin(a).toFloat())
            out.add(cos(b).toFloat()*cos(a).toFloat());out.add(sin(b).toFloat());out.add(cos(b).toFloat()*sin(a).toFloat())
        }
        for(i in 0 until 24)for(j in 0 until 6){
            val a=i*PI/12;val b=j*PI/3;val c=(i+1)*PI/12;val d=(j+1)*PI/3
            vertex(a,b);vertex(a,d);vertex(c,d);vertex(a,b);vertex(c,d);vertex(c,b)
        }
        return Fly3DMesh(out.toFloatArray())
    }

    /** A closed tapered curved wing: span along Z, small camber in Y, elliptical tip. */
    fun wing(): Fly3DMesh {
        val out = ArrayList<Float>()
        val segments = 18
        fun vertex(t: Double, a: Double) {
            val width = sin(PI * t).toFloat() * (.23f + .18f * t.toFloat())
            val x = width * cos(a).toFloat()
            val y = .045f * sin(PI * t).toFloat() * sin(a).toFloat() + .10f * sin(PI * t).toFloat()
            val z = t.toFloat() * 1.48f
            val nx = cos(a).toFloat() * .15f
            val ny = sin(a).toFloat()
            out.add(x); out.add(y); out.add(z)
            out.add(nx); out.add(ny); out.add(.08f * cos(PI * t).toFloat())
        }
        for (i in 0 until segments) for (j in 0 until 8) {
            val t = i.toDouble() / segments; val u = (i + 1.0) / segments
            val a = j * PI / 4; val b = (j + 1) * PI / 4
            vertex(t,a); vertex(u,b); vertex(u,a)
            vertex(t,a); vertex(t,b); vertex(u,b)
        }
        return Fly3DMesh(out.toFloatArray())
    }
}
