package com.example.meowmba.ui.companion

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.sqrt

/** One bounded offscreen pass, five texture taps only at presentation. No animation clock.
 * RGBA stays premultiplied so transparent stage silhouettes composite cleanly in TextureView.
 */
internal class Fly3DEdgePass {
    private var program = 0
    private var framebuffer = 0
    private var texture = 0
    private var depth = 0
    private var position = 0
    private var texel = 0
    private var width = 1
    private var height = 1
    private var outputWidth = 1
    private var outputHeight = 1
    private val names = IntArray(1)
    private val quad = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f,-1f,1f,-1f,-1f,1f,1f,1f)); position(0)
    }
    val renderPixels: Int get() = width * height
    var available = false
        private set

    fun contextCreated() {
        framebuffer=0; texture=0; depth=0; available=false
        fun compile(type:Int, text:String):Int {
            val shader=GLES20.glCreateShader(type); GLES20.glShaderSource(shader,text); GLES20.glCompileShader(shader)
            GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,names,0)
            check(names[0]!=0) { GLES20.glGetShaderInfoLog(shader) }
            return shader
        }
        val vs=compile(GLES20.GL_VERTEX_SHADER,"""
            attribute vec2 aPosition; varying vec2 vUv;
            void main(){vUv=aPosition*.5+.5;gl_Position=vec4(aPosition,0.,1.);}
        """)
        val fs=compile(GLES20.GL_FRAGMENT_SHADER,"""
            precision mediump float; varying vec2 vUv;
            uniform sampler2D uScene; uniform vec2 uTexel;
            void main(){
                vec4 c=texture2D(uScene,vUv);
                vec4 n=texture2D(uScene,vUv+vec2(0.,uTexel.y));
                vec4 s=texture2D(uScene,vUv-vec2(0.,uTexel.y));
                vec4 e=texture2D(uScene,vUv+vec2(uTexel.x,0.));
                vec4 w=texture2D(uScene,vUv-vec2(uTexel.x,0.));
                vec3 l=vec3(.299,.587,.114);
                float spread=max(max(abs(dot(n.rgb-s.rgb,l)),abs(dot(e.rgb-w.rgb,l))),
                    max(abs(n.a-s.a),abs(e.a-w.a)));
                // Reconstruct only high-contrast edges; interiors keep their original color.
                // Convex premultiplied RGBA weights prevent dark stage-compositing fringes.
                float weight=smoothstep(.035,.22,spread)*.48;
                gl_FragColor=mix(c,(n+s+e+w)*.25,weight);
            }
        """)
        program=GLES20.glCreateProgram(); GLES20.glAttachShader(program,vs); GLES20.glAttachShader(program,fs)
        GLES20.glLinkProgram(program); GLES20.glDeleteShader(vs); GLES20.glDeleteShader(fs)
        GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,names,0)
        check(names[0]!=0) { GLES20.glGetProgramInfoLog(program) }
        position=GLES20.glGetAttribLocation(program,"aPosition")
        texel=GLES20.glGetUniformLocation(program,"uTexel")
    }

    fun resize(w:Int,h:Int) {
        outputWidth=max(1,w); outputHeight=max(1,h)
        // <=1.2 million render pixels / ~7.2 MB color+depth, even on an unfolded high-DPI display.
        // Small viewports spend unused budget on actual silhouette coverage, not extra taps.
        // Large/high-DPI phones retain exactly the prior maximum allocation.
        val ratio=minOf(1.35f,sqrt(1_200_000f/(outputWidth.toFloat()*outputHeight)))
        width=max(1,(outputWidth*ratio).toInt()); height=max(1,(outputHeight*ratio).toInt())
        if(framebuffer!=0) { names[0]=framebuffer; GLES20.glDeleteFramebuffers(1,names,0) }
        if(texture!=0) { names[0]=texture; GLES20.glDeleteTextures(1,names,0) }
        if(depth!=0) { names[0]=depth; GLES20.glDeleteRenderbuffers(1,names,0) }
        GLES20.glGenTextures(1,names,0); texture=names[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,width,height,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,null)
        GLES20.glGenRenderbuffers(1,names,0); depth=names[0]
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER,depth)
        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER,GLES20.GL_DEPTH_COMPONENT16,width,height)
        GLES20.glGenFramebuffers(1,names,0); framebuffer=names[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,framebuffer)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,GLES20.GL_COLOR_ATTACHMENT0,GLES20.GL_TEXTURE_2D,texture,0)
        GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER,GLES20.GL_DEPTH_ATTACHMENT,GLES20.GL_RENDERBUFFER,depth)
        available=GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)==GLES20.GL_FRAMEBUFFER_COMPLETE
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,0)
    }

    fun begin() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,if(available)framebuffer else 0)
        GLES20.glViewport(0,0,if(available)width else outputWidth,if(available)height else outputHeight)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST); GLES20.glEnable(GLES20.GL_CULL_FACE)
    }

    fun present() {
        if(!available) return
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,0)
        GLES20.glViewport(0,0,outputWidth,outputHeight)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST); GLES20.glDisable(GLES20.GL_CULL_FACE); GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture)
        GLES20.glUniform2f(texel,1f/width,1f/height)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,0); quad.position(0)
        GLES20.glEnableVertexAttribArray(position); GLES20.glVertexAttribPointer(position,2,GLES20.GL_FLOAT,false,0,quad)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4)
    }
}
