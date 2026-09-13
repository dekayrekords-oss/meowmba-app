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

    fun cube(): Fly3DMesh {
        val out = ArrayList<Float>(216)
        fun face(nx: Float, ny: Float, nz: Float, a: FloatArray, b: FloatArray, c: FloatArray, d: FloatArray) {
            for (p in arrayOf(a, b, c, a, c, d)) {
                out.add(p[0]); out.add(p[1]); out.add(p[2]); out.add(nx); out.add(ny); out.add(nz)
            }
        }
        face(0f, 0f, 1f, floatArrayOf(-1f,-1f,1f), floatArrayOf(1f,-1f,1f), floatArrayOf(1f,1f,1f), floatArrayOf(-1f,1f,1f))
        face(0f, 0f,-1f, floatArrayOf(1f,-1f,-1f), floatArrayOf(-1f,-1f,-1f), floatArrayOf(-1f,1f,-1f), floatArrayOf(1f,1f,-1f))
        face(1f, 0f, 0f, floatArrayOf(1f,-1f,1f), floatArrayOf(1f,-1f,-1f), floatArrayOf(1f,1f,-1f), floatArrayOf(1f,1f,1f))
        face(-1f,0f,0f, floatArrayOf(-1f,-1f,-1f), floatArrayOf(-1f,-1f,1f), floatArrayOf(-1f,1f,1f), floatArrayOf(-1f,1f,-1f))
        face(0f,1f,0f, floatArrayOf(-1f,1f,1f), floatArrayOf(1f,1f,1f), floatArrayOf(1f,1f,-1f), floatArrayOf(-1f,1f,-1f))
        face(0f,-1f,0f, floatArrayOf(-1f,-1f,-1f), floatArrayOf(1f,-1f,-1f), floatArrayOf(1f,-1f,1f), floatArrayOf(-1f,-1f,1f))
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
