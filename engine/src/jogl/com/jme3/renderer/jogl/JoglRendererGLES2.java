package com.jme3.renderer.jogl;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.EnumSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.media.opengl.GL2ES1;
import javax.media.opengl.GL2ES2;
import javax.media.opengl.GLContext;

import jme3tools.shader.ShaderDebug;

import com.jme3.light.LightList;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Matrix4f;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.math.Vector4f;
import com.jme3.renderer.Caps;
import com.jme3.renderer.IDList;
import com.jme3.renderer.RenderContext;
import com.jme3.renderer.Renderer;
import com.jme3.renderer.RendererException;
import com.jme3.renderer.Statistics;

import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.Mesh.Mode;
import com.jme3.scene.VertexBuffer.Format;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.scene.VertexBuffer.Usage;
import com.jme3.shader.Attribute;
import com.jme3.shader.Shader;
import com.jme3.shader.Uniform;
import com.jme3.shader.Shader.ShaderSource;
import com.jme3.shader.Shader.ShaderType;
import com.jme3.texture.FrameBuffer;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.FrameBuffer.RenderBuffer;
import com.jme3.texture.Texture.WrapAxis;
import com.jme3.util.BufferUtils;
import com.jme3.util.ListMap;
import com.jme3.util.NativeObjectManager;

public class JoglRendererGLES2 implements WrappedJoglRenderer {

	private static final Logger logger = Logger
			.getLogger(JoglRendererGLES2.class.getName());

	private static final boolean VALIDATE_SHADER = false;
	private final ByteBuffer nameBuf = BufferUtils.createByteBuffer(250);
	private final StringBuilder stringBuf = new StringBuilder(250);
	private final IntBuffer intBuf1 = BufferUtils.createIntBuffer(1);
	private final IntBuffer intBuf16 = BufferUtils.createIntBuffer(16);
	private final RenderContext context = new RenderContext();
	private final NativeObjectManager objManager = new NativeObjectManager();
	private final EnumSet<Caps> caps = EnumSet.noneOf(Caps.class);
	// current state
	private Shader boundShader;
	// private int initialDrawBuf, initialReadBuf;
	private int glslVer;
	private int vertexTextureUnits;
	private int fragTextureUnits;
	private int vertexUniforms;
	private int fragUniforms;
	private int vertexAttribs;
	// private int maxFBOSamples;
	// private int maxFBOAttachs;
	// private int maxMRTFBOAttachs;
	// private int maxRBSize;
	private int maxTexSize;
	private int maxCubeTexSize;
	private int maxVertCount;
	private int maxTriCount;
	private boolean tdc;
	// private FrameBuffer lastFb = null;
	private final Statistics statistics = new Statistics();
	private int vpX, vpY, vpW, vpH;
	private int clipX, clipY, clipW, clipH;
	// private final GL10 gl;
	private boolean powerVr = false;
	private boolean useVBO = false;

	private GL2ES2 getGL() {
		return GLContext.getCurrentGL().getGL2ES2();
	}

	protected void updateNameBuffer() {
		int len = stringBuf.length();

		nameBuf.position(0);
		nameBuf.limit(len);
		for (int i = 0; i < len; i++) {
			nameBuf.put((byte) stringBuf.charAt(i));
		}

		nameBuf.rewind();
	}

	private void checkGLError() {
		int error;
		while ((error = getGL().glGetError()) != GL2ES2.GL_NO_ERROR) {
			throw new RendererException("OpenGL Error " + error);
		}
	}

	public Statistics getStatistics() {
		return statistics;
	}

	public EnumSet<Caps> getCaps() {
		return caps;
	}

	private int extractVersion(String prefixStr, String versionStr) {
		if (versionStr != null) {
			int spaceIdx = versionStr.indexOf(" ", prefixStr.length());
			if (spaceIdx >= 1) {
				versionStr = versionStr.substring(prefixStr.length(), spaceIdx)
						.trim();
			} else {
				versionStr = versionStr.substring(prefixStr.length()).trim();
			}
			float version = Float.parseFloat(versionStr);
			return (int) (version * 100);
		} else {
			return -1;
		}
	}

	public void initialize() {

		GL2ES2 gl = getGL();

		logger.log(Level.INFO, "Vendor: {0}", gl.glGetString(GL2ES2.GL_VENDOR));
		logger.log(Level.INFO, "Renderer: {0}",
				gl.glGetString(GL2ES2.GL_RENDERER));
		logger.log(Level.INFO, "Version: {0}",
				gl.glGetString(GL2ES2.GL_VERSION));
		logger.log(Level.INFO, "Shading Language Version: {0}",
				gl.glGetString(GL2ES2.GL_SHADING_LANGUAGE_VERSION));
		logger.log(Level.INFO,"GL Profile: "+gl.getGLProfile());
		logger.log(Level.INFO,"GL Renderer Quirks:" + gl.getContext().getRendererQuirks().toString());

		powerVr = gl.glGetString(GL2ES2.GL_RENDERER).contains("PowerVR");

		// Fix issue in TestRenderToMemory when GL_FRONT is the main
		// buffer being used.

		// initialDrawBuf = GL2ES2.glGetIntegeri(GL2ES2.GL_DRAW_BUFFER);
		// initialReadBuf = GL2ES2.glGetIntegeri(GL2ES2.GL_READ_BUFFER);

		// Check OpenGL version
		
//		int openGlVer = extractVersion("OpenGL ES ",
//				gl.glGetString(GL2ES2.GL_VERSION));
//		if (openGlVer == -1) {
//			glslVer = -1;
//			throw new UnsupportedOperationException(
//					"OpenGL ES 2.0+ is required for OGLESShaderRenderer!");
//		}

//		// Check shader language version
//		glslVer = extractVersion("OpenGL ES GLSL ES ",
//				gl.glGetString(GL2ES2.GL_SHADING_LANGUAGE_VERSION));
//		switch (glslVer) {
//		// TODO: When new versions of OpenGL ES shader language come out,
//		// update this.
//		default:
//			caps.add(Caps.GLSL100);
//			break;
//		}
		caps.add(Caps.GLSL100);
		gl.glGetIntegerv(GL2ES2.GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS, intBuf16);
		vertexTextureUnits = intBuf16.get(0);
		logger.log(Level.INFO, "VTF Units: {0}", vertexTextureUnits);
		if (vertexTextureUnits > 0) {
			caps.add(Caps.VertexTextureFetch);
		}

		gl.glGetIntegerv(GL2ES2.GL_MAX_TEXTURE_IMAGE_UNITS, intBuf16);
		fragTextureUnits = intBuf16.get(0);
		logger.log(Level.INFO, "Texture Units: {0}", fragTextureUnits);

		// Multiply vector count by 4 to get float count.
		gl.glGetIntegerv(GL2ES2.GL_MAX_VERTEX_UNIFORM_VECTORS, intBuf16);
		vertexUniforms = intBuf16.get(0) * 4;
		logger.log(Level.FINER, "Vertex Uniforms: {0}", vertexUniforms);

		gl.glGetIntegerv(GL2ES2.GL_MAX_FRAGMENT_UNIFORM_VECTORS, intBuf16);
		fragUniforms = intBuf16.get(0) * 4;
		logger.log(Level.FINER, "Fragment Uniforms: {0}", fragUniforms);

		gl.glGetIntegerv(GL2ES2.GL_MAX_VARYING_VECTORS, intBuf16);
		int varyingFloats = intBuf16.get(0) * 4;
		logger.log(Level.FINER, "Varying Floats: {0}", varyingFloats);

		gl.glGetIntegerv(GL2ES2.GL_MAX_VERTEX_ATTRIBS, intBuf16);
		vertexAttribs = intBuf16.get(0);
		logger.log(Level.INFO, "Vertex Attributes: {0}", vertexAttribs);

		gl.glGetIntegerv(GL2ES2.GL_SUBPIXEL_BITS, intBuf16);
		int subpixelBits = intBuf16.get(0);
		logger.log(Level.INFO, "Subpixel Bits: {0}", subpixelBits);

		// GLES10.glGetIntegerv(GLES10.GL_MAX_ELEMENTS_VERTICES, intBuf16);
		// maxVertCount = intBuf16.get(0);
		// logger.log(Level.FINER, "Preferred Batch Vertex Count: {0}",
		// maxVertCount);
		//
		// GLES10.glGetIntegerv(GLES10.GL_MAX_ELEMENTS_INDICES, intBuf16);
		// maxTriCount = intBuf16.get(0);
		// logger.log(Level.FINER, "Preferred Batch Index Count: {0}",
		// maxTriCount);

		gl.glGetIntegerv(GL2ES2.GL_MAX_TEXTURE_SIZE, intBuf16);
		maxTexSize = intBuf16.get(0);
		logger.log(Level.INFO, "Maximum Texture Resolution: {0}", maxTexSize);

		gl.glGetIntegerv(GL2ES2.GL_MAX_CUBE_MAP_TEXTURE_SIZE, intBuf16);
		maxCubeTexSize = intBuf16.get(0);
		logger.log(Level.INFO, "Maximum CubeMap Resolution: {0}",
				maxCubeTexSize);

		/*
		 * if (ctxCaps.GL_ARB_color_buffer_float){ // XXX: Require both 16 and
		 * 32 bit float support for FloatColorBuffer. if
		 * (ctxCaps.GL_ARB_half_float_pixel){ caps.add(Caps.FloatColorBuffer); }
		 * }
		 * 
		 * if (ctxCaps.GL_ARB_depth_buffer_float){
		 * caps.add(Caps.FloatDepthBuffer); }
		 * 
		 * if (ctxCaps.GL_ARB_draw_instanced) caps.add(Caps.MeshInstancing);
		 * 
		 * if (ctxCaps.GL_ARB_texture_buffer_object)
		 * caps.add(Caps.TextureBuffer);
		 * 
		 * if (ctxCaps.GL_ARB_texture_float){ if
		 * (ctxCaps.GL_ARB_half_float_pixel){ caps.add(Caps.FloatTexture); } }
		 * 
		 * if (ctxCaps.GL_EXT_packed_float){
		 * caps.add(Caps.PackedFloatColorBuffer); if
		 * (ctxCaps.GL_ARB_half_float_pixel){ // because textures are usually
		 * uploaded as RGB16F // need half-float pixel
		 * caps.add(Caps.PackedFloatTexture); } }
		 * 
		 * if (ctxCaps.GL_EXT_texture_array) caps.add(Caps.TextureArray);
		 * 
		 * if (ctxCaps.GL_EXT_texture_shared_exponent)
		 * caps.add(Caps.SharedExponentTexture);
		 * 
		 * if (ctxCaps.GL_EXT_framebuffer_object){ caps.add(Caps.FrameBuffer);
		 * 
		 * glGetInteger(GL_MAX_RENDERBUFFER_SIZE_EXT, intBuf16); maxRBSize =
		 * intBuf16.get(0); logger.log(Level.FINER, "FBO RB Max Size: {0}",
		 * maxRBSize);
		 * 
		 * glGetInteger(GL_MAX_COLOR_ATTACHMENTS_EXT, intBuf16); maxFBOAttachs =
		 * intBuf16.get(0); logger.log(Level.FINER,
		 * "FBO Max renderbuffers: {0}", maxFBOAttachs);
		 * 
		 * if (ctxCaps.GL_EXT_framebuffer_multisample){
		 * caps.add(Caps.FrameBufferMultisample);
		 * 
		 * glGetInteger(GL_MAX_SAMPLES_EXT, intBuf16); maxFBOSamples =
		 * intBuf16.get(0); logger.log(Level.FINER, "FBO Max Samples: {0}",
		 * maxFBOSamples); }
		 * 
		 * if (ctxCaps.GL_ARB_draw_buffers){ caps.add(Caps.FrameBufferMRT);
		 * glGetInteger(ARBDrawBuffers.GL_MAX_DRAW_BUFFERS_ARB, intBuf16);
		 * maxMRTFBOAttachs = intBuf16.get(0); logger.log(Level.FINER,
		 * "FBO Max MRT renderbuffers: {0}", maxMRTFBOAttachs); } }
		 * 
		 * if (ctxCaps.GL_ARB_multisample){
		 * glGetInteger(ARBMultisample.GL_SAMPLE_BUFFERS_ARB, intBuf16); boolean
		 * available = intBuf16.get(0) != 0;
		 * glGetInteger(ARBMultisample.GL_SAMPLES_ARB, intBuf16); int samples =
		 * intBuf16.get(0); logger.log(Level.FINER, "Samples: {0}", samples);
		 * boolean enabled = glIsEnabled(ARBMultisample.GL_MULTISAMPLE_ARB); if
		 * (samples > 0 && available && !enabled){
		 * glEnable(ARBMultisample.GL_MULTISAMPLE_ARB); } }
		 */

		String extensions = gl.glGetString(GL2ES2.GL_EXTENSIONS);
		logger.log(Level.INFO, "GL_EXTENSIONS: {0}", extensions);

		gl.glGetIntegerv(GL2ES2.GL_COMPRESSED_TEXTURE_FORMATS, intBuf16);
		for (int i = 0; i < intBuf16.limit(); i++) {
			logger.log(Level.INFO, "Compressed Texture Formats: {0}",
					intBuf16.get(i));
		}

		TextureUtilGLES.loadTextureFeatures(extensions);

		applyRenderState(RenderState.DEFAULT);
		gl.glDisable(GL2ES2.GL_DITHER);

		useVBO = false;

		// NOTE: SDK_INT is only available since 1.6,
		// but for jME3 it doesn't matter since android versions 1.5 and below
		// are not supported.
		// if (Build.VERSION.SDK_INT >= 9){
		// logger.log(Level.INFO, "Force-enabling VBO (Android 2.3 or higher)");
		// useVBO = true;
		// } else {
		// useVBO = false;
		// }
		//useVBO = true;

		logger.log(Level.INFO, "Caps: {0}", caps);
	}

	/**
	 * <code>resetGLObjects</code> should be called when die GLView gets
	 * recreated to reset all GPU objects
	 */
	public void resetGLObjects() {
		objManager.resetObjects();
		statistics.clearMemory();
		boundShader = null;
		// lastFb = null;
		context.reset();
	}

	public void cleanup() {
		objManager.deleteAllObjects(this);
		statistics.clearMemory();
	}

	private void checkCap(Caps cap) {
		if (!caps.contains(cap)) {
			throw new UnsupportedOperationException(
					"Required capability missing: " + cap.name());
		}
	}

	/*********************************************************************
	 * \ |* Render State *| \
	 *********************************************************************/
	public void setDepthRange(float start, float end) {
		getGL().glDepthRangef(start, end);
	}

	public void clearBuffers(boolean color, boolean depth, boolean stencil) {
		int bits = 0;
		if (color) {
			bits = GL2ES2.GL_COLOR_BUFFER_BIT;
		}
		if (depth) {
			bits |= GL2ES2.GL_DEPTH_BUFFER_BIT;
		}
		if (stencil) {
			bits |= GL2ES2.GL_STENCIL_BUFFER_BIT;
		}
		if (bits != 0) {
			getGL().glClear(bits);
		}
	}

	public void setBackgroundColor(ColorRGBA color) {
		getGL().glClearColor(color.r, color.g, color.b, color.a);
	}

	public void applyRenderState(RenderState state) {

		GL2ES2 gl = getGL();
		/*
		 * if (state.isWireframe() && !context.wireframe){
		 * GL2ES2.glPolygonMode(GL2ES2.GL_FRONT_AND_BACK, GL2ES2.GL_LINE);
		 * context.wireframe = true; }else if (!state.isWireframe() &&
		 * context.wireframe){ GL2ES2.glPolygonMode(GL2ES2.GL_FRONT_AND_BACK,
		 * GL2ES2.GL_FILL); context.wireframe = false; }
		 */
		if (state.isDepthTest() && !context.depthTestEnabled) {
			gl.glEnable(GL2ES2.GL_DEPTH_TEST);
			gl.glDepthFunc(GL2ES2.GL_LEQUAL);
			context.depthTestEnabled = true;
		} else if (!state.isDepthTest() && context.depthTestEnabled) {
			gl.glDisable(GL2ES2.GL_DEPTH_TEST);
			context.depthTestEnabled = false;
		}

		if (state.isDepthWrite() && !context.depthWriteEnabled) {
			gl.glDepthMask(true);
			context.depthWriteEnabled = true;
		} else if (!state.isDepthWrite() && context.depthWriteEnabled) {
			gl.glDepthMask(false);
			context.depthWriteEnabled = false;
		}
		if (state.isColorWrite() && !context.colorWriteEnabled) {
			gl.glColorMask(true, true, true, true);
			context.colorWriteEnabled = true;
		} else if (!state.isColorWrite() && context.colorWriteEnabled) {
			gl.glColorMask(false, false, false, false);
			context.colorWriteEnabled = false;
		}
		// if (state.isPointSprite() && !context.pointSprite) {
		// // GL2ES2.glEnable(GL2ES2.GL_POINT_SPRITE);
		// // GL2ES2.glTexEnvi(GL2ES2.GL_POINT_SPRITE, GL2ES2.GL_COORD_REPLACE,
		// GL2ES2.GL_TRUE);
		// // GL2ES2.glEnable(GL2ES2.GL_VERTEX_PROGRAM_POINT_SIZE);
		// // GL2ES2.glPointParameterf(GL2ES2.GL_POINT_SIZE_MIN, 1.0f);
		// } else if (!state.isPointSprite() && context.pointSprite) {
		// // GL2ES2.glDisable(GL2ES2.GL_POINT_SPRITE);
		// }

		if (state.isPolyOffset()) {
			if (!context.polyOffsetEnabled) {
				gl.glEnable(GL2ES2.GL_POLYGON_OFFSET_FILL);
				gl.glPolygonOffset(state.getPolyOffsetFactor(),
						state.getPolyOffsetUnits());
				context.polyOffsetEnabled = true;
				context.polyOffsetFactor = state.getPolyOffsetFactor();
				context.polyOffsetUnits = state.getPolyOffsetUnits();
			} else {
				if (state.getPolyOffsetFactor() != context.polyOffsetFactor
						|| state.getPolyOffsetUnits() != context.polyOffsetUnits) {
					gl.glPolygonOffset(state.getPolyOffsetFactor(),
							state.getPolyOffsetUnits());
					context.polyOffsetFactor = state.getPolyOffsetFactor();
					context.polyOffsetUnits = state.getPolyOffsetUnits();
				}
			}
		} else {
			if (context.polyOffsetEnabled) {
				gl.glDisable(GL2ES2.GL_POLYGON_OFFSET_FILL);
				context.polyOffsetEnabled = false;
				context.polyOffsetFactor = 0;
				context.polyOffsetUnits = 0;
			}
		}
		if (state.getFaceCullMode() != context.cullMode) {
			if (state.getFaceCullMode() == RenderState.FaceCullMode.Off) {
				gl.glDisable(GL2ES2.GL_CULL_FACE);
			} else {
				gl.glEnable(GL2ES2.GL_CULL_FACE);
			}

			switch (state.getFaceCullMode()) {
			case Off:
				break;
			case Back:
				gl.glCullFace(GL2ES2.GL_BACK);
				break;
			case Front:
				gl.glCullFace(GL2ES2.GL_FRONT);
				break;
			case FrontAndBack:
				gl.glCullFace(GL2ES2.GL_FRONT_AND_BACK);
				break;
			default:
				throw new UnsupportedOperationException(
						"Unrecognized face cull mode: "
								+ state.getFaceCullMode());
			}

			context.cullMode = state.getFaceCullMode();
		}

		if (state.getBlendMode() != context.blendMode) {
			if (state.getBlendMode() == RenderState.BlendMode.Off) {
				gl.glDisable(GL2ES2.GL_BLEND);
			} else {
				gl.glEnable(GL2ES2.GL_BLEND);
				switch (state.getBlendMode()) {
				case Off:
					break;
				case Additive:
					gl.glBlendFunc(GL2ES2.GL_ONE, GL2ES2.GL_ONE);
					break;
				case AlphaAdditive:
					gl.glBlendFunc(GL2ES2.GL_SRC_ALPHA, GL2ES2.GL_ONE);
					break;
				case Color:
					gl.glBlendFunc(GL2ES2.GL_ONE, GL2ES2.GL_ONE_MINUS_SRC_COLOR);
					break;
				case Alpha:
					gl.glBlendFunc(GL2ES2.GL_SRC_ALPHA,
							GL2ES2.GL_ONE_MINUS_SRC_ALPHA);
					break;
				case PremultAlpha:
					gl.glBlendFunc(GL2ES2.GL_ONE, GL2ES2.GL_ONE_MINUS_SRC_ALPHA);
					break;
				case Modulate:
					gl.glBlendFunc(GL2ES2.GL_DST_COLOR, GL2ES2.GL_ZERO);
					break;
				case ModulateX2:
					gl.glBlendFunc(GL2ES2.GL_DST_COLOR, GL2ES2.GL_SRC_COLOR);
					break;
				default:
					throw new UnsupportedOperationException(
							"Unrecognized blend mode: " + state.getBlendMode());
				}
			}
			context.blendMode = state.getBlendMode();
		}
	}

	/*********************************************************************
	 * \ |* Camera and World transforms *| \
	 *********************************************************************/
	public void setViewPort(int x, int y, int w, int h) {
		if (x != vpX || vpY != y || vpW != w || vpH != h) {
			getGL().glViewport(x, y, w, h);
			vpX = x;
			vpY = y;
			vpW = w;
			vpH = h;
		}
	}

	public void setClipRect(int x, int y, int width, int height) {

		GL2ES2 gl = getGL();

		if (!context.clipRectEnabled) {
			gl.glEnable(GL2ES2.GL_SCISSOR_TEST);
			context.clipRectEnabled = true;
		}
		if (clipX != x || clipY != y || clipW != width || clipH != height) {
			gl.glScissor(x, y, width, height);
			clipX = x;
			clipY = y;
			clipW = width;
			clipH = height;
		}
	}

	public void clearClipRect() {
		if (context.clipRectEnabled) {
			getGL().glDisable(GL2ES2.GL_SCISSOR_TEST);
			context.clipRectEnabled = false;

			clipX = 0;
			clipY = 0;
			clipW = 0;
			clipH = 0;
		}
	}

	public void onFrame() {
		int error = getGL().glGetError();
		
		if (error != GL2ES2.GL_NO_ERROR) {
			throw new RendererException("onFrame//OpenGL Error " + error
					+ ". Enable error checking for more info.");
		}
		objManager.deleteUnused(this);
	}

	public void setWorldMatrix(Matrix4f worldMatrix) {
	}

	public void setViewProjectionMatrices(Matrix4f viewMatrix,
			Matrix4f projMatrix) {
	}

	/*********************************************************************
	 * \ |* Shaders *| \
	 *********************************************************************/
	protected void updateUniformLocation(Shader shader, Uniform uniform) {
		stringBuf.setLength(0);
		stringBuf.append(uniform.getName()).append('\0');
		updateNameBuffer();
		int loc = getGL().glGetUniformLocation(shader.getId(),
				uniform.getName());
		if (loc < 0) {
			uniform.setLocation(-1);
			// uniform is not declared in shader
		} else {
			uniform.setLocation(loc);
		}
	}

	protected void bindProgram(Shader shader) {
		int shaderId = shader.getId();
		if (context.boundShaderProgram != shaderId) {
			getGL().glUseProgram(shaderId);
			statistics.onShaderUse(shader, true);
			boundShader = shader;
			context.boundShaderProgram = shaderId;
		} else {
			statistics.onShaderUse(shader, false);
		}
	}

	protected void updateUniform(Shader shader, Uniform uniform) {

		GL2ES2 gl = getGL();

		int shaderId = shader.getId();

		assert uniform.getName() != null;
		assert shader.getId() > 0;

		if (context.boundShaderProgram != shaderId) {
			gl.glUseProgram(shaderId);
			statistics.onShaderUse(shader, true);
			boundShader = shader;
			context.boundShaderProgram = shaderId;
		} else {
			statistics.onShaderUse(shader, false);
		}

		int loc = uniform.getLocation();
		if (loc == -1) {
			return;
		}

		if (loc == -2) {
			// get uniform location
			updateUniformLocation(shader, uniform);
			if (uniform.getLocation() == -1) {
				// not declared, ignore
				uniform.clearUpdateNeeded();
				return;
			}
			loc = uniform.getLocation();
		}

		if (uniform.getVarType() == null) {
			logger.warning("value is not set yet.");
			return; // value not set yet..
		}

		statistics.onUniformSet();

		uniform.clearUpdateNeeded();
		FloatBuffer fb;
		switch (uniform.getVarType()) {
		case Float:
			Float f = (Float) uniform.getValue();
			gl.glUniform1f(loc, f.floatValue());
			break;
		case Vector2:
			Vector2f v2 = (Vector2f) uniform.getValue();
			gl.glUniform2f(loc, v2.getX(), v2.getY());
			break;
		case Vector3:
			Vector3f v3 = (Vector3f) uniform.getValue();
			gl.glUniform3f(loc, v3.getX(), v3.getY(), v3.getZ());
			break;
		case Vector4:
			Object val = uniform.getValue();
			if (val instanceof ColorRGBA) {
				ColorRGBA c = (ColorRGBA) val;
				gl.glUniform4f(loc, c.r, c.g, c.b, c.a);
			} else if (val instanceof Vector4f) {
				Vector4f c = (Vector4f) val;
				gl.glUniform4f(loc, c.x, c.y, c.z, c.w);
			} else {
				Quaternion c = (Quaternion) uniform.getValue();
				gl.glUniform4f(loc, c.getX(), c.getY(), c.getZ(), c.getW());
			}
			break;
		case Boolean:
			Boolean b = (Boolean) uniform.getValue();
			gl.glUniform1i(loc, b.booleanValue() ? GL2ES2.GL_TRUE
					: GL2ES2.GL_FALSE);
			break;
		case Matrix3:
			fb = (FloatBuffer) uniform.getValue();
			assert fb.remaining() == 9;
			gl.glUniformMatrix3fv(loc, 1, false, fb);
			break;
		case Matrix4:
			fb = (FloatBuffer) uniform.getValue();
			assert fb.remaining() == 16;
			gl.glUniformMatrix4fv(loc, 1, false, fb);
			break;
		case FloatArray:
			fb = (FloatBuffer) uniform.getValue();
			gl.glUniform1fv(loc, fb.limit(), fb);
			break;
		case Vector2Array:
			fb = (FloatBuffer) uniform.getValue();
			gl.glUniform2fv(loc, fb.limit() / 2, fb);
			break;
		case Vector3Array:
			fb = (FloatBuffer) uniform.getValue();
			gl.glUniform3fv(loc, fb.limit() / 3, fb);
			break;
		case Vector4Array:
			fb = (FloatBuffer) uniform.getValue();
			gl.glUniform4fv(loc, fb.limit() / 4, fb);
			break;
		case Matrix4Array:
			fb = (FloatBuffer) uniform.getValue();
			gl.glUniformMatrix4fv(loc, fb.limit() / 16, false, fb);
			break;
		case Int:
			Integer i = (Integer) uniform.getValue();
			gl.glUniform1i(loc, i.intValue());
			break;
		default:
			throw new UnsupportedOperationException(
					"Unsupported uniform type: " + uniform.getVarType());
		}
	}

	protected void updateShaderUniforms(Shader shader) {
		ListMap<String, Uniform> uniforms = shader.getUniformMap();
		for (int i = 0; i < uniforms.size(); i++) {
			Uniform uniform = uniforms.getValue(i);
			if (uniform.isUpdateNeeded()) {
				updateUniform(shader, uniform);
			}
		}
	}

	protected void resetUniformLocations(Shader shader) {
		ListMap<String, Uniform> uniforms = shader.getUniformMap();
		for (int i = 0; i < uniforms.size(); i++) {
			Uniform uniform = uniforms.getValue(i);
			uniform.reset(); // e.g check location again
		}
	}

	/*
	 * (Non-javadoc) Only used for fixed-function. Ignored.
	 */
	public void setLighting(LightList list) {
	}

	public int convertShaderType(ShaderType type) {
		switch (type) {
		case Fragment:
			return GL2ES2.GL_FRAGMENT_SHADER;
		case Vertex:
			return GL2ES2.GL_VERTEX_SHADER;
			// case Geometry:
			// return ARBGeometryShader4.GL_GEOMETRY_SHADER_ARB;
		default:
			throw new RuntimeException("Unrecognized shader type.");
		}
	}

	public String glGetShaderInfoLog(int shader) {
		ByteBuffer buffer = ByteBuffer.allocateDirect(1024 * 10);
		buffer.order(ByteOrder.nativeOrder());
		ByteBuffer tmp = ByteBuffer.allocateDirect(4);
		tmp.order(ByteOrder.nativeOrder());
		IntBuffer intBuffer = tmp.asIntBuffer();

		getGL().glGetShaderInfoLog(shader, 1024 * 10, intBuffer, buffer);
		int numBytes = intBuffer.get(0);
		byte[] bytes = new byte[numBytes];
		buffer.get(bytes);
		return new String(bytes);
	}

	public String glGetProgramInfoLog(int program) {
		ByteBuffer buffer = ByteBuffer.allocateDirect(1024 * 10);
		buffer.order(ByteOrder.nativeOrder());
		ByteBuffer tmp = ByteBuffer.allocateDirect(4);
		tmp.order(ByteOrder.nativeOrder());
		IntBuffer intBuffer = tmp.asIntBuffer();

		getGL().glGetProgramInfoLog(program, 1024 * 10, intBuffer, buffer);
		int numBytes = intBuffer.get(0);
		byte[] bytes = new byte[numBytes];
		buffer.get(bytes);
		return new String(bytes);
	}

	public void updateShaderSourceData(ShaderSource source) {

		GL2ES2 gl = getGL();

		int id = source.getId();
		if (id == -1) {
			// Create id
			id = gl.glCreateShader(convertShaderType(source.getType()));
			if (id <= 0) {
				throw new RendererException(
						"Invalid ID received when trying to create shader.");
			}
			source.setId(id);
		}

		if (!source.getLanguage().equals("GLSL100")) {
			throw new RendererException("This shader cannot run in OpenGL ES. "
					+ "Only GLSL 1.0 shaders are supported.");
		}

		// upload shader source
		// merge the defines and source code
		byte[] definesCodeData = source.getDefines().getBytes();
		byte[] sourceCodeData = source.getSource().getBytes();
		ByteBuffer codeBuf = BufferUtils
				.createByteBuffer(definesCodeData.length
						+ sourceCodeData.length);
		codeBuf.put(definesCodeData);
		codeBuf.put(sourceCodeData);
		codeBuf.flip();

		String[] vlines = new String[] { source.getSource() };
		int[] vlengths = new int[] { vlines[0].length() };
		gl.glShaderSource(id, vlines.length, vlines, vlengths, 0);

		gl.glCompileShader(id);
		gl.glGetShaderiv(id, GL2ES2.GL_COMPILE_STATUS, intBuf1);

		boolean compiledOK = intBuf1.get(0) == GL2ES2.GL_TRUE;
		String infoLog = null;

		if (VALIDATE_SHADER || !compiledOK) {
			// even if compile succeeded, check
			// log for warnings
			gl.glGetShaderiv(id, GL2ES2.GL_INFO_LOG_LENGTH, intBuf1);
			checkGLError();

			infoLog = glGetShaderInfoLog(id);
		}

		if (compiledOK) {
			if (infoLog != null) {
				logger.log(Level.INFO, "compile success: {0}, {1}",
						new Object[] { source.getName(), infoLog });
			} else {
				logger.log(Level.FINE, "compile success: {0}", source.getName());
			}
			source.clearUpdateNeeded();
		} else {
			logger.log(
					Level.WARNING,
					"Bad compile of:\n{0}",
					new Object[] { ShaderDebug.formatShaderSource(
							source.getDefines(), source.getSource(),
							stringBuf.toString()) });
			if (infoLog != null) {
				throw new RendererException("compile error in:" + source
						+ " error:" + infoLog);
			} else {
				throw new RendererException("compile error in:" + source
						+ " error: <not provided>");
			}
		}
	}

	public void updateShaderData(Shader shader) {

		GL2ES2 gl = getGL();

		int id = shader.getId();
		boolean needRegister = false;
		if (id == -1) {
			// create program
			id = gl.glCreateProgram();

			if (id <= 0) {
				throw new RendererException(
						"Invalid ID received when trying to create shader program.");
			}

			shader.setId(id);
			needRegister = true;
		}

		for (ShaderSource source : shader.getSources()) {
			if (source.isUpdateNeeded()) {
				updateShaderSourceData(source);
			}
			gl.glAttachShader(id, source.getId());
		}

		// link shaders to program
		gl.glLinkProgram(id);
		gl.glGetProgramiv(id, GL2ES2.GL_LINK_STATUS, intBuf1);

		boolean linkOK = intBuf1.get(0) == GL2ES2.GL_TRUE;
		String infoLog = null;

		if (VALIDATE_SHADER || !linkOK) {
			gl.glGetProgramiv(id, GL2ES2.GL_INFO_LOG_LENGTH, intBuf1);

			int length = intBuf1.get(0);
			if (length > 3) {
				// get infos

				infoLog = glGetProgramInfoLog(id);
			}
		}

		if (linkOK) {
			if (infoLog != null) {
				logger.log(Level.INFO, "shader link success. \n{0}", infoLog);
			} else {
				logger.fine("shader link success");
			}
			shader.clearUpdateNeeded();
			if (needRegister) {
				// Register shader for clean up if it was created in this
				// method.
				objManager.registerForCleanup(shader);
				statistics.onNewShader();
			} else {
				// OpenGL spec: uniform locations may change after re-link
				resetUniformLocations(shader);
			}
		} else {
			if (infoLog != null) {
				throw new RendererException("Shader link failure, shader:"
						+ shader + " info:" + infoLog);
			} else {
				throw new RendererException("Shader link failure, shader:"
						+ shader + " info: <not provided>");
			}
		}
	}

	public void setShader(Shader shader) {
		if (shader == null) {
			throw new IllegalArgumentException("Shader cannot be null");
		} else {
			if (shader.isUpdateNeeded()) {
				updateShaderData(shader);
			}

			// NOTE: might want to check if any of the
			// sources need an update?

			assert shader.getId() > 0;

			updateShaderUniforms(shader);
			bindProgram(shader);
		}
	}

	public void deleteShaderSource(ShaderSource source) {
		if (source.getId() < 0) {
			logger.warning("Shader source is not uploaded to GPU, cannot delete.");
			return;
		}

		source.clearUpdateNeeded();
		getGL().glDeleteShader(source.getId());
		source.resetObject();
	}

	public void deleteShader(Shader shader) {

		GL2ES2 gl = getGL();

		if (shader.getId() == -1) {
			logger.warning("Shader is not uploaded to GPU, cannot delete.");
			return;
		}

		for (ShaderSource source : shader.getSources()) {
			if (source.getId() != -1) {
				gl.glDetachShader(shader.getId(), source.getId());
				deleteShaderSource(source);
			}
		}

		gl.glDeleteProgram(shader.getId());
		statistics.onDeleteShader();
		shader.resetObject();
	}

	/*********************************************************************
	 * \ |* Framebuffers *| \
	 *********************************************************************/
	public void copyFrameBuffer(FrameBuffer src, FrameBuffer dst) {
		logger.warning("copyFrameBuffer is not supported.");
	}

	public void copyFrameBuffer(FrameBuffer src, FrameBuffer dst,
			boolean copyDepth) {
		logger.warning("copyFrameBuffer is not supported.");
	}

	/*
	 * public void copyFrameBuffer(FrameBuffer src, FrameBuffer dst){ if
	 * (GLContext.getCapabilities().GL_EXT_framebuffer_blit){ int srcW = 0; int
	 * srcH = 0; int dstW = 0; int dstH = 0; int prevFBO = context.boundFBO;
	 * 
	 * if (src != null && src.isUpdateNeeded()) updateFrameBuffer(src);
	 * 
	 * if (dst != null && dst.isUpdateNeeded()) updateFrameBuffer(dst);
	 * 
	 * if (src == null){ glBindFramebufferEXT(GL_READ_FRAMEBUFFER_EXT, 0); //
	 * srcW = viewWidth; // srcH = viewHeight; }else{
	 * glBindFramebufferEXT(GL_READ_FRAMEBUFFER_EXT, src.getId()); srcW =
	 * src.getWidth(); srcH = src.getHeight(); } if (dst == null){
	 * glBindFramebufferEXT(GL_DRAW_FRAMEBUFFER_EXT, 0); // dstW = viewWidth; //
	 * dstH = viewHeight; }else{ glBindFramebufferEXT(GL_DRAW_FRAMEBUFFER_EXT,
	 * dst.getId()); dstW = dst.getWidth(); dstH = dst.getHeight(); }
	 * glBlitFramebufferEXT(0, 0, srcW, srcH, 0, 0, dstW, dstH,
	 * GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT, GL_NEAREST);
	 * 
	 * glBindFramebufferEXT(GL_FRAMEBUFFER_EXT, prevFBO); try {
	 * checkFrameBufferError(); } catch (IllegalStateException ex){
	 * logger.log(Level.SEVERE, "Source FBO:\n{0}", src);
	 * logger.log(Level.SEVERE, "Dest FBO:\n{0}", dst); throw ex; } }else{ throw
	 * new UnsupportedOperationException("EXT_framebuffer_blit required."); //
	 * TODO: support non-blit copies? } }
	 */

	private void checkFrameBufferError() {
		logger.warning("checkFrameBufferError is not supported.");
	}

	/*
	 * private void checkFrameBufferError() { int status =
	 * glCheckFramebufferStatusEXT(GL_FRAMEBUFFER_EXT); switch (status) { case
	 * GL_FRAMEBUFFER_COMPLETE_EXT: break; case GL_FRAMEBUFFER_UNSUPPORTED_EXT:
	 * //Choose different formats throw new
	 * IllegalStateException("Framebuffer object format is " +
	 * "unsupported by the video hardware."); case
	 * GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT_EXT: throw new
	 * IllegalStateException("Framebuffer has erronous attachment."); case
	 * GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT_EXT: throw new
	 * IllegalStateException("Framebuffer is missing required attachment.");
	 * case GL_FRAMEBUFFER_INCOMPLETE_DIMENSIONS_EXT: throw new
	 * IllegalStateException
	 * ("Framebuffer attachments must have same dimensions."); case
	 * GL_FRAMEBUFFER_INCOMPLETE_FORMATS_EXT: throw new
	 * IllegalStateException("Framebuffer attachments must have same formats.");
	 * case GL_FRAMEBUFFER_INCOMPLETE_DRAW_BUFFER_EXT: throw new
	 * IllegalStateException("Incomplete draw buffer."); case
	 * GL_FRAMEBUFFER_INCOMPLETE_READ_BUFFER_EXT: throw new
	 * IllegalStateException("Incomplete read buffer."); case
	 * GL_FRAMEBUFFER_INCOMPLETE_MULTISAMPLE_EXT: throw new
	 * IllegalStateException("Incomplete multisample buffer."); default:
	 * //Programming error; will fail on all hardware throw new
	 * IllegalStateException("Some video driver error " +
	 * "or programming error occured. " +
	 * "Framebuffer object status is invalid. "); } }
	 */

	private void updateRenderBuffer(FrameBuffer fb, RenderBuffer rb) {
		logger.warning("updateRenderBuffer is not supported.");
	}

	/*
	 * private void updateRenderBuffer(FrameBuffer fb, RenderBuffer rb){ int id
	 * = rb.getId(); if (id == -1){ glGenRenderbuffersEXT(intBuf1); id =
	 * intBuf1.get(0); rb.setId(id); }
	 * 
	 * if (context.boundRB != id){ glBindRenderbufferEXT(GL_RENDERBUFFER_EXT,
	 * id); context.boundRB = id; }
	 * 
	 * if (fb.getWidth() > maxRBSize || fb.getHeight() > maxRBSize) throw new
	 * UnsupportedOperationException("Resolution "+fb.getWidth()+
	 * ":"+fb.getHeight()+" is not supported.");
	 * 
	 * if (fb.getSamples() > 0 &&
	 * GLContext.getCapabilities().GL_EXT_framebuffer_multisample){ int samples
	 * = fb.getSamples(); if (maxFBOSamples < samples){ samples = maxFBOSamples;
	 * } glRenderbufferStorageMultisampleEXT(GL_RENDERBUFFER_EXT, samples,
	 * TextureUtil.convertTextureFormat(rb.getFormat()), fb.getWidth(),
	 * fb.getHeight()); }else{ glRenderbufferStorageEXT(GL_RENDERBUFFER_EXT,
	 * TextureUtil.convertTextureFormat(rb.getFormat()), fb.getWidth(),
	 * fb.getHeight()); } }
	 */

	private int convertAttachmentSlot(int attachmentSlot) {
		logger.warning("convertAttachmentSlot is not supported.");
		return -1;
	}

	/*
	 * private int convertAttachmentSlot(int attachmentSlot){ // can also add
	 * support for stencil here if (attachmentSlot == -100){ return
	 * GL_DEPTH_ATTACHMENT_EXT; }else if (attachmentSlot < 0 || attachmentSlot
	 * >= 16){ throw new
	 * UnsupportedOperationException("Invalid FBO attachment slot: "
	 * +attachmentSlot); }
	 * 
	 * return GL_COLOR_ATTACHMENT0_EXT + attachmentSlot; }
	 */

	public void updateRenderTexture(FrameBuffer fb, RenderBuffer rb) {
		logger.warning("updateRenderTexture is not supported.");
	}

	/*
	 * public void updateRenderTexture(FrameBuffer fb, RenderBuffer rb){ Texture
	 * tex = rb.getTexture(); Image image = tex.getImage(); if
	 * (image.isUpdateNeeded()) updateTexImageData(image, tex.getType(),
	 * tex.getMinFilter().usesMipMapLevels());
	 * 
	 * glFramebufferTexture2DEXT(GL_FRAMEBUFFER_EXT,
	 * convertAttachmentSlot(rb.getSlot()), convertTextureType(tex.getType()),
	 * image.getId(), 0); }
	 */

	public void updateFrameBufferAttachment(FrameBuffer fb, RenderBuffer rb) {
		logger.warning("updateFrameBufferAttachment is not supported.");
	}

	/*
	 * public void updateFrameBufferAttachment(FrameBuffer fb, RenderBuffer rb){
	 * boolean needAttach; if (rb.getTexture() == null){ // if it hasn't been
	 * created yet, then attach is required. needAttach = rb.getId() == -1;
	 * updateRenderBuffer(fb, rb); }else{ needAttach = false;
	 * updateRenderTexture(fb, rb); } if (needAttach){
	 * glFramebufferRenderbufferEXT(GL_FRAMEBUFFER_EXT,
	 * convertAttachmentSlot(rb.getSlot()), GL_RENDERBUFFER_EXT, rb.getId()); }
	 * }
	 */

	public void updateFrameBuffer(FrameBuffer fb) {
		logger.warning("updateFrameBuffer is not supported.");
	}

	/*
	 * public void updateFrameBuffer(FrameBuffer fb) { int id = fb.getId(); if
	 * (id == -1){ // create FBO glGenFramebuffersEXT(intBuf1); id =
	 * intBuf1.get(0); fb.setId(id); objManager.registerForCleanup(fb);
	 * 
	 * statistics.onNewFrameBuffer(); }
	 * 
	 * if (context.boundFBO != id){ glBindFramebufferEXT(GL_FRAMEBUFFER_EXT,
	 * id); // binding an FBO automatically sets draw buf to
	 * GL_COLOR_ATTACHMENT0 context.boundDrawBuf = 0; context.boundFBO = id; }
	 * 
	 * FrameBuffer.RenderBuffer depthBuf = fb.getDepthBuffer(); if (depthBuf !=
	 * null){ updateFrameBufferAttachment(fb, depthBuf); }
	 * 
	 * for (int i = 0; i < fb.getNumColorBuffers(); i++){
	 * FrameBuffer.RenderBuffer colorBuf = fb.getColorBuffer(i);
	 * updateFrameBufferAttachment(fb, colorBuf); }
	 * 
	 * fb.clearUpdateNeeded(); }
	 */

	public void setMainFrameBufferOverride(FrameBuffer fb) {
	}

	public void setFrameBuffer(FrameBuffer fb) {
	}

	/*
	 * public void setFrameBuffer(FrameBuffer fb) { if (lastFb == fb) return;
	 * 
	 * // generate mipmaps for last FB if needed if (lastFb != null){ for (int i
	 * = 0; i < lastFb.getNumColorBuffers(); i++){ RenderBuffer rb =
	 * lastFb.getColorBuffer(i); Texture tex = rb.getTexture(); if (tex != null
	 * && tex.getMinFilter().usesMipMapLevels()){ setTexture(0,
	 * rb.getTexture()); glGenerateMipmapEXT(convertTextureType(tex.getType()));
	 * } } }
	 * 
	 * 
	 * if (fb == null){ // unbind any fbos if (context.boundFBO != 0){
	 * glBindFramebufferEXT(GL_FRAMEBUFFER_EXT, 0);
	 * statistics.onFrameBufferUse(null, true);
	 * 
	 * context.boundFBO = 0; } // select back buffer if (context.boundDrawBuf !=
	 * -1){ glDrawBuffer(initialDrawBuf); context.boundDrawBuf = -1; } if
	 * (context.boundReadBuf != -1){ glReadBuffer(initialReadBuf);
	 * context.boundReadBuf = -1; }
	 * 
	 * lastFb = null; }else{ if (fb.isUpdateNeeded()) updateFrameBuffer(fb);
	 * 
	 * if (context.boundFBO != fb.getId()){
	 * glBindFramebufferEXT(GL_FRAMEBUFFER_EXT, fb.getId());
	 * statistics.onFrameBufferUse(fb, true);
	 * 
	 * // update viewport to reflect framebuffer's resolution setViewPort(0, 0,
	 * fb.getWidth(), fb.getHeight());
	 * 
	 * context.boundFBO = fb.getId(); }else{ statistics.onFrameBufferUse(fb,
	 * false); } if (fb.getNumColorBuffers() == 0){ // make sure to select NONE
	 * as draw buf // no color buffer attached. select NONE if
	 * (context.boundDrawBuf != -2){ glDrawBuffer(GL_NONE); context.boundDrawBuf
	 * = -2; } if (context.boundReadBuf != -2){ glReadBuffer(GL_NONE);
	 * context.boundReadBuf = -2; } }else{ if (fb.isMultiTarget()){ if
	 * (fb.getNumColorBuffers() > maxMRTFBOAttachs) throw new
	 * UnsupportedOperationException("Framebuffer has more" +
	 * " targets than are supported" + " on the system!");
	 * 
	 * if (context.boundDrawBuf != 100 + fb.getNumColorBuffers()){
	 * intBuf16.clear(); for (int i = 0; i < fb.getNumColorBuffers(); i++)
	 * intBuf16.put( GL_COLOR_ATTACHMENT0_EXT + i );
	 * 
	 * intBuf16.flip(); glDrawBuffers(intBuf16); context.boundDrawBuf = 100 +
	 * fb.getNumColorBuffers(); } }else{ RenderBuffer rb =
	 * fb.getColorBuffer(fb.getTargetIndex()); // select this draw buffer if
	 * (context.boundDrawBuf != rb.getSlot()){
	 * glDrawBuffer(GL_COLOR_ATTACHMENT0_EXT + rb.getSlot());
	 * context.boundDrawBuf = rb.getSlot(); } } }
	 * 
	 * assert fb.getId() >= 0; assert context.boundFBO == fb.getId(); lastFb =
	 * fb; }
	 * 
	 * try { checkFrameBufferError(); } catch (IllegalStateException ex){
	 * logger.log(Level.SEVERE, "Problem FBO:\n{0}", fb); throw ex; } }
	 */

	/**
	 * Reads the Color Buffer from OpenGL and stores into the ByteBuffer. Since
	 * jME for Android does not support Frame Buffers yet, make sure the
	 * FrameBuffer passed in is NULL (default) or an exception will be thrown.
	 * Also, make sure to call setViewPort with the appropriate viewport size
	 * before calling readFrameBuffer.
	 * 
	 * @param fb
	 *            FrameBuffer (must be NULL)
	 * @param byteBuf
	 *            ByteBuffer to store the Color Buffer from OpenGL
	 */
	public void readFrameBuffer(FrameBuffer fb, ByteBuffer byteBuf) {
		if (fb != null) {
			throw new IllegalArgumentException(
					"FrameBuffer is not supported yet.");
		}

		getGL().glReadPixels(vpX, vpY, vpW, vpH, GL2ES2.GL_RGBA,
				GL2ES2.GL_UNSIGNED_BYTE, byteBuf);
	}

	/*
	 * public void readFrameBuffer(FrameBuffer fb, ByteBuffer byteBuf){ if (fb
	 * != null){ RenderBuffer rb = fb.getColorBuffer(); if (rb == null) throw
	 * new IllegalArgumentException("Specified framebuffer" +
	 * " does not have a colorbuffer");
	 * 
	 * setFrameBuffer(fb); if (context.boundReadBuf != rb.getSlot()){
	 * glReadBuffer(GL_COLOR_ATTACHMENT0_EXT + rb.getSlot());
	 * context.boundReadBuf = rb.getSlot(); } }else{ setFrameBuffer(null); }
	 * 
	 * glReadPixels(vpX, vpY, vpW, vpH, GL_RGBA GL_BGRA, GL_UNSIGNED_BYTE,
	 * byteBuf); }
	 */

	private void deleteRenderBuffer(FrameBuffer fb, RenderBuffer rb) {
		logger.warning("deleteRenderBuffer is not supported.");
	}

	/*
	 * private void deleteRenderBuffer(FrameBuffer fb, RenderBuffer rb){
	 * intBuf1.put(0, rb.getId()); glDeleteRenderbuffersEXT(intBuf1); }
	 */

	public void deleteFrameBuffer(FrameBuffer fb) {
		logger.warning("deleteFrameBuffer is not supported.");
	}

	/*
	 * public void deleteFrameBuffer(FrameBuffer fb) { if (fb.getId() != -1){ if
	 * (context.boundFBO == fb.getId()){
	 * glBindFramebufferEXT(GL_FRAMEBUFFER_EXT, 0); context.boundFBO = 0; }
	 * 
	 * if (fb.getDepthBuffer() != null){ deleteRenderBuffer(fb,
	 * fb.getDepthBuffer()); } if (fb.getColorBuffer() != null){
	 * deleteRenderBuffer(fb, fb.getColorBuffer()); }
	 * 
	 * intBuf1.put(0, fb.getId()); glDeleteFramebuffersEXT(intBuf1);
	 * fb.resetObject();
	 * 
	 * statistics.onDeleteFrameBuffer(); } }
	 */

	/*********************************************************************
	 * \ |* Textures *| \
	 *********************************************************************/
	private int convertTextureType(Texture.Type type) {
		switch (type) {
		case TwoDimensional:
			return GL2ES2.GL_TEXTURE_2D;
			// case TwoDimensionalArray:
			// return EXTTextureArray.GL_TEXTURE_2D_ARRAY_EXT;
			// case ThreeDimensional:
			// return GL2ES2.GL_TEXTURE_3D;
		case CubeMap:
			return GL2ES2.GL_TEXTURE_CUBE_MAP;
		default:
			throw new UnsupportedOperationException("Unknown texture type: "
					+ type);
		}
	}

	private int convertMagFilter(Texture.MagFilter filter) {
		switch (filter) {
		case Bilinear:
			return GL2ES2.GL_LINEAR;
		case Nearest:
			return GL2ES2.GL_NEAREST;
		default:
			throw new UnsupportedOperationException("Unknown mag filter: "
					+ filter);
		}
	}

	private int convertMinFilter(Texture.MinFilter filter) {
		switch (filter) {
		case Trilinear:
			return GL2ES2.GL_LINEAR_MIPMAP_LINEAR;
		case BilinearNearestMipMap:
			return GL2ES2.GL_LINEAR_MIPMAP_NEAREST;
		case NearestLinearMipMap:
			return GL2ES2.GL_NEAREST_MIPMAP_LINEAR;
		case NearestNearestMipMap:
			return GL2ES2.GL_NEAREST_MIPMAP_NEAREST;
		case BilinearNoMipMaps:
			return GL2ES2.GL_LINEAR;
		case NearestNoMipMaps:
			return GL2ES2.GL_NEAREST;
		default:
			throw new UnsupportedOperationException("Unknown min filter: "
					+ filter);
		}
	}

	private int convertWrapMode(Texture.WrapMode mode) {
		switch (mode) {
		case BorderClamp:
		case Clamp:
		case EdgeClamp:
			return GL2ES2.GL_CLAMP_TO_EDGE;
		case Repeat:
			return GL2ES2.GL_REPEAT;
		case MirroredRepeat:
			return GL2ES2.GL_MIRRORED_REPEAT;
		default:
			throw new UnsupportedOperationException("Unknown wrap mode: "
					+ mode);
		}
	}

	/**
	 * <code>setupTextureParams</code> sets the OpenGL context texture
	 * parameters
	 * 
	 * @param tex
	 *            the Texture to set the texture parameters from
	 */
	private void setupTextureParams(Texture tex) {
		int target = convertTextureType(tex.getType());

		GL2ES2 gl = getGL();
		// filter things
		int minFilter = convertMinFilter(tex.getMinFilter());
		int magFilter = convertMagFilter(tex.getMagFilter());

		gl.glTexParameteri(target, GL2ES2.GL_TEXTURE_MIN_FILTER, minFilter);
		gl.glTexParameteri(target, GL2ES2.GL_TEXTURE_MAG_FILTER, magFilter);

		/*
		 * if (tex.getAnisotropicFilter() > 1){
		 * 
		 * if (GLContext.getCapabilities().GL_EXT_texture_filter_anisotropic){
		 * glTexParameterf(target,
		 * EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT,
		 * tex.getAnisotropicFilter()); }
		 * 
		 * }
		 */
		// repeat modes

		switch (tex.getType()) {
		case ThreeDimensional:
		case CubeMap: // cubemaps use 3D coords
			// GL_TEXTURE_WRAP_R is not available in api 8
			// GL2ES2.glTexParameteri(target, GL2ES2.GL_TEXTURE_WRAP_R,
			// convertWrapMode(tex.getWrap(WrapAxis.R)));
		case TwoDimensional:
		case TwoDimensionalArray:
			gl.glTexParameteri(target, GL2ES2.GL_TEXTURE_WRAP_T,
					convertWrapMode(tex.getWrap(WrapAxis.T)));

			// fall down here is intentional..
			// case OneDimensional:
			gl.glTexParameteri(target, GL2ES2.GL_TEXTURE_WRAP_S,
					convertWrapMode(tex.getWrap(WrapAxis.S)));
			break;
		default:
			throw new UnsupportedOperationException("Unknown texture type: "
					+ tex.getType());
		}

		// R to Texture compare mode
		/*
		 * if (tex.getShadowCompareMode() != Texture.ShadowCompareMode.Off){
		 * GL2ES2.glTexParameteri(target, GL2ES2.GL_TEXTURE_COMPARE_MODE,
		 * GL2ES2.GL_COMPARE_R_TO_TEXTURE); GL2ES2.glTexParameteri(target,
		 * GL2ES2.GL_DEPTH_TEXTURE_MODE, GL2ES2.GL_INTENSITY); if
		 * (tex.getShadowCompareMode() ==
		 * Texture.ShadowCompareMode.GreaterOrEqual){
		 * GL2ES2.glTexParameteri(target, GL2ES2.GL_TEXTURE_COMPARE_FUNC,
		 * GL2ES2.GL_GEQUAL); }else{ GL2ES2.glTexParameteri(target,
		 * GL2ES2.GL_TEXTURE_COMPARE_FUNC, GL2ES2.GL_LEQUAL); } }
		 */
	}

	/**
	 * activates and binds the texture
	 * 
	 * @param img
	 * @param type
	 */
	public void updateTexImageData(Image img, Texture.Type type) {

		GL2ES2 gl = getGL();

		int texId = img.getId();

		if (texId == -1) {
			// create texture
			gl.glGenTextures(1, intBuf1);
			texId = intBuf1.get(0);
			img.setId(texId);
			objManager.registerForCleanup(img);

			statistics.onNewTexture();
		}

		// bind texture
		int target = convertTextureType(type);
		if (context.boundTextures[0] != img) {
			if (context.boundTextureUnit != 0) {
				gl.glActiveTexture(GL2ES2.GL_TEXTURE0);
				context.boundTextureUnit = 0;
			}

			gl.glBindTexture(target, texId);
			context.boundTextures[0] = img;
		}

		boolean needMips = false;
		if (img.isGeneratedMipmapsRequired()) {
			needMips = true;
			img.setMipmapsGenerated(true);
		}

		if (target == GL2ES2.GL_TEXTURE_CUBE_MAP) {
			// Check max texture size before upload
			if (img.getWidth() > maxCubeTexSize
					|| img.getHeight() > maxCubeTexSize) {
				throw new RendererException("Cannot upload cubemap " + img
						+ ". The maximum supported cubemap resolution is "
						+ maxCubeTexSize);
			}
		} else {
			if (img.getWidth() > maxTexSize || img.getHeight() > maxTexSize) {
				throw new RendererException("Cannot upload texture " + img
						+ ". The maximum supported texture resolution is "
						+ maxTexSize);
			}
		}

		if (target == GL2ES2.GL_TEXTURE_CUBE_MAP) {
			
				// Standard jme3 image data
				List<ByteBuffer> data = img.getData();
				if (data.size() != 6) {
					throw new UnsupportedOperationException("Invalid texture: "
							+ img
							+ "Cubemap textures must contain 6 data units.");
				}
				for (int i = 0; i < 6; i++) {
					TextureUtilGLES.uploadTextureAny(img,
							GL2ES2.GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, i,
							needMips);
				}
			
		} else {
			TextureUtilGLES.uploadTextureAny(img, target, 0, needMips);
			
		}

		img.clearUpdateNeeded();
	}

	public void setTexture(int unit, Texture tex) {
		
		GL2ES2 gl = getGL();
		
		Image image = tex.getImage();
		
		if (image.isUpdateNeeded()
				|| (image.isGeneratedMipmapsRequired() && !image
						.isMipmapsGenerated())) {
			updateTexImageData(image, tex.getType());
		}

		int texId = image.getId();
		assert texId != -1;

		if (texId == -1) {
			logger.warning("error: texture image has -1 id");
		}

		Image[] textures = context.boundTextures;

		int type = convertTextureType(tex.getType());
		if (!context.textureIndexList.moveToNew(unit)) {
			// if (context.boundTextureUnit != unit){
			// glActiveTexture(GL_TEXTURE0 + unit);
			// context.boundTextureUnit = unit;
			// }
			// glEnable(type);
		}

		if (textures[unit] != image) {
			if (context.boundTextureUnit != unit) {
				gl.glActiveTexture(GL2ES2.GL_TEXTURE0 + unit);
				context.boundTextureUnit = unit;
			}

			gl.glBindTexture(type, texId);
			textures[unit] = image;

			statistics.onTextureUse(tex.getImage(), true);
		} else {
			statistics.onTextureUse(tex.getImage(), false);
		}

		setupTextureParams(tex);
	}

	public void clearTextureUnits() {
		IDList textureList = context.textureIndexList;
		Image[] textures = context.boundTextures;
		for (int i = 0; i < textureList.oldLen; i++) {
			int idx = textureList.oldList[i];
			// if (context.boundTextureUnit != idx){
			// glActiveTexture(GL_TEXTURE0 + idx);
			// context.boundTextureUnit = idx;
			// }
			// glDisable(convertTextureType(textures[idx].getType()));
			textures[idx] = null;
		}
		context.textureIndexList.copyNewToOld();
	}

	public void deleteImage(Image image) {
		int texId = image.getId();
		if (texId != -1) {
			intBuf1.put(0, texId);
			intBuf1.position(0).limit(1);

			getGL().glDeleteTextures(1, intBuf1);
			image.resetObject();

			statistics.onDeleteTexture();
		}
	}

	/*********************************************************************
	 * \ |* Vertex Buffers and Attributes *| \
	 *********************************************************************/
	private int convertUsage(Usage usage) {
		switch (usage) {
		case Static:
			return GL2ES2.GL_STATIC_DRAW;
		case Dynamic:
			return GL2ES2.GL_DYNAMIC_DRAW;
		case Stream:
			return GL2ES2.GL_STREAM_DRAW;
		default:
			throw new RuntimeException("Unknown usage type.");
		}
	}

	private int convertFormat(Format format) {
		switch (format) {
		case Byte:
			return GL2ES2.GL_BYTE;
		case UnsignedByte:
			return GL2ES2.GL_UNSIGNED_BYTE;
		case Short:
			return GL2ES2.GL_SHORT;
		case UnsignedShort:
			return GL2ES2.GL_UNSIGNED_SHORT;
		case Int:
			return GL2ES2.GL_INT;
		case UnsignedInt:
			return GL2ES2.GL_UNSIGNED_INT;
			/*
			 * case Half: return NVHalfFloat.GL_HALF_FLOAT_NV; // return
			 * ARBHalfFloatVertex.GL_HALF_FLOAT;
			 */
		case Float:
			return GL2ES2.GL_FLOAT;
			// case Double:
			// return GL2ES2.GL_DOUBLE;
		default:
			throw new RuntimeException("Unknown buffer format.");

		}
	}

	public void updateBufferData(VertexBuffer vb) {
		
		GL2ES2 gl = getGL();
		
		int bufId = vb.getId();
		boolean created = false;
		
		if (bufId == -1) {
			// create buffer
			gl.glGenBuffers(1, intBuf1);
			bufId = intBuf1.get(0);
			vb.setId(bufId);
			objManager.registerForCleanup(vb);

			created = true;
		}

		// bind buffer
		int target;
		if (vb.getBufferType() == VertexBuffer.Type.Index) {
			target = GL2ES2.GL_ELEMENT_ARRAY_BUFFER;
			if (context.boundElementArrayVBO != bufId) {
				gl.glBindBuffer(target, bufId);
				context.boundElementArrayVBO = bufId;
			}
		} else {
			target = GL2ES2.GL_ARRAY_BUFFER;
			if (context.boundArrayVBO != bufId) {
				gl.glBindBuffer(target, bufId);
				context.boundArrayVBO = bufId;
			}
		}

		int usage = convertUsage(vb.getUsage());
		vb.getData().rewind();

		if (created || vb.hasDataSizeChanged()) {
			// upload data based on format
			int size = vb.getData().limit() * vb.getFormat().getComponentSize();

			switch (vb.getFormat()) {
			case Byte:
			case UnsignedByte:
				gl.glBufferData(target, size, (ByteBuffer) vb.getData(),
						usage);
				break;
			// case Half:
			case Short:
			case UnsignedShort:
				gl.glBufferData(target, size, (ShortBuffer) vb.getData(),
						usage);
				break;
			case Int:
			case UnsignedInt:
				gl.glBufferData(target, size, (IntBuffer) vb.getData(),
						usage);
				break;
			case Float:
				gl.glBufferData(target, size, (FloatBuffer) vb.getData(),
						usage);
				break;
			case Double:
				gl.glBufferData(target, size, (DoubleBuffer) vb.getData(),
						usage);
				break;
			default:
				throw new RuntimeException("Unknown buffer format.");
			}
		} else {
			int size = vb.getData().limit() * vb.getFormat().getComponentSize();

			switch (vb.getFormat()) {
			case Byte:
			case UnsignedByte:
				gl.glBufferSubData(target, 0, size,
						(ByteBuffer) vb.getData());
				break;
			case Short:
			case UnsignedShort:
				gl.glBufferSubData(target, 0, size,
						(ShortBuffer) vb.getData());
				break;
			case Int:
			case UnsignedInt:
				gl.glBufferSubData(target, 0, size,
						(IntBuffer) vb.getData());
				break;
			case Float:
				gl.glBufferSubData(target, 0, size,
						(FloatBuffer) vb.getData());
				break;
			case Double:
				gl.glBufferSubData(target, 0, size,
						(DoubleBuffer) vb.getData());
				break;
			default:
				throw new RuntimeException("Unknown buffer format.");
			}
		}
		vb.clearUpdateNeeded();
	}

	public void deleteBuffer(VertexBuffer vb) {
		int bufId = vb.getId();
		if (bufId != -1) {
			// delete buffer
			intBuf1.put(0, bufId);
			intBuf1.position(0).limit(1);

			getGL().glDeleteBuffers(1, intBuf1);
			vb.resetObject();
		}
	}

	public void clearVertexAttribs() {
		IDList attribList = context.attribIndexList;
		for (int i = 0; i < attribList.oldLen; i++) {
			int idx = attribList.oldList[i];

			getGL().glDisableVertexAttribArray(idx);
			context.boundAttribs[idx] = null;
		}
		context.attribIndexList.copyNewToOld();
	}

	public void setVertexAttrib(VertexBuffer vb, VertexBuffer idb) {
		
		GL2ES2 gl = getGL();
		
		if (vb.getBufferType() == VertexBuffer.Type.Index) {
			throw new IllegalArgumentException(
					"Index buffers not allowed to be set to vertex attrib");
		}

		if (vb.isUpdateNeeded() && idb == null) {
			updateBufferData(vb);
		}

		int programId = context.boundShaderProgram;
		
		if (programId > 0) {
			Attribute attrib = boundShader.getAttribute(vb.getBufferType());
			int loc = attrib.getLocation();
			if (loc == -1) {
				return; // not defined
			}

			if (loc == -2) {
				// stringBuf.setLength(0);
				// stringBuf.append("in").append(vb.getBufferType().name()).append('\0');
				// updateNameBuffer();

				String attributeName = "in" + vb.getBufferType().name();
				loc = gl.glGetAttribLocation(programId, attributeName);

				// not really the name of it in the shader (inPosition\0) but
				// the internal name of the enum (Position).
				if (loc < 0) {
					attrib.setLocation(-1);
					return; // not available in shader.
				} else {
					attrib.setLocation(loc);
				}
			}

			VertexBuffer[] attribs = context.boundAttribs;
			
			if (!context.attribIndexList.moveToNew(loc)) {
				gl.glEnableVertexAttribArray(loc);
				// System.out.println("Enabled ATTRIB IDX: "+loc);
			}
			
			if (attribs[loc] != vb) {
				// NOTE: Use id from interleaved buffer if specified
				int bufId = idb != null ? idb.getId() : vb.getId();
				assert bufId != -1;

				if (bufId == -1) {
					logger.warning("invalid buffer id");
				}

				if (context.boundArrayVBO != bufId) {
					gl.glBindBuffer(GL2ES2.GL_ARRAY_BUFFER, bufId);
					context.boundArrayVBO = bufId;
				}

				vb.getData().rewind();

				gl.glVertexAttribPointer(loc,
						vb.getNumComponents(), convertFormat(vb.getFormat()),
						vb.isNormalized(), vb.getStride(), 0);

				attribs[loc] = vb;
			}
		} else {
			throw new IllegalStateException(
					"Cannot render mesh without shader bound");
		}
	}

	public void setVertexAttrib(VertexBuffer vb) {
		setVertexAttrib(vb, null);
	}

	public void drawTriangleArray(Mesh.Mode mode, int count, int vertCount) {
		/*
		 * if (count > 1){
		 * ARBDrawInstanced.glDrawArraysInstancedARB(convertElementMode(mode),
		 * 0, vertCount, count); }else{
		 */
		getGL().glDrawArrays(convertElementMode(mode), 0, vertCount);
		/*
		 * }
		 */
	}

	public void drawTriangleList(VertexBuffer indexBuf, Mesh mesh, int count) {
		
		GL2ES2 gl = getGL();
		
		if (indexBuf.getBufferType() != VertexBuffer.Type.Index) {
			throw new IllegalArgumentException(
					"Only index buffers are allowed as triangle lists.");
		}

		if (indexBuf.isUpdateNeeded()) {
			updateBufferData(indexBuf);
		}

		int bufId = indexBuf.getId();
		assert bufId != -1;

		if (bufId == -1) {
			logger.info("invalid buffer id!");
		}

		if (context.boundElementArrayVBO != bufId) {
			gl.glBindBuffer(GL2ES2.GL_ELEMENT_ARRAY_BUFFER, bufId);
			context.boundElementArrayVBO = bufId;
		}

		int vertCount = mesh.getVertexCount();
		boolean useInstancing = count > 1 && caps.contains(Caps.MeshInstancing);

		Buffer indexData = indexBuf.getData();
		if (mesh.getMode() == Mode.Hybrid) {
			int[] modeStart = mesh.getModeStart();
			int[] elementLengths = mesh.getElementLengths();

			int elMode = convertElementMode(Mode.Triangles);
			int fmt = convertFormat(indexBuf.getFormat());
			int elSize = indexBuf.getFormat().getComponentSize();
			int listStart = modeStart[0];
			int stripStart = modeStart[1];
			int fanStart = modeStart[2];
			int curOffset = 0;
			for (int i = 0; i < elementLengths.length; i++) {
				if (i == stripStart) {
					elMode = convertElementMode(Mode.TriangleStrip);
				} else if (i == fanStart) {
					elMode = convertElementMode(Mode.TriangleStrip);
				}
				int elementLength = elementLengths[i];

				if (useInstancing) {
					// ARBDrawInstanced.
					throw new IllegalArgumentException(
							"instancing is not supported.");
					/*
					 * GL2ES2.glDrawElementsInstancedARB(elMode, elementLength,
					 * fmt, curOffset, count);
					 */
				} else {
					indexBuf.getData().position(curOffset);
					gl.glDrawElements(elMode, elementLength, fmt,
							indexBuf.getData());
					/*
					 * glDrawRangeElements(elMode, 0, vertCount, elementLength,
					 * fmt, curOffset);
					 */
				}

				curOffset += elementLength * elSize;
			}
		} else {
			if (useInstancing) {
				throw new IllegalArgumentException(
						"instancing is not supported.");
				// ARBDrawInstanced.
				/*
				 * GL2ES2.glDrawElementsInstancedARB(convertElementMode(mesh.getMode
				 * ()), indexBuf.getData().limit(),
				 * convertFormat(indexBuf.getFormat()), 0, count);
				 */
			} else {
				indexData.rewind();
				gl.glDrawElements(convertElementMode(mesh.getMode()),
						indexBuf.getData().limit(),
						convertFormat(indexBuf.getFormat()), 0);
			}
		}
	}

	/*********************************************************************
	 * \ |* Render Calls *| \
	 *********************************************************************/
	public int convertElementMode(Mesh.Mode mode) {
		switch (mode) {
		case Points:
			return GL2ES2.GL_POINTS;
		case Lines:
			return GL2ES2.GL_LINES;
		case LineLoop:
			return GL2ES2.GL_LINE_LOOP;
		case LineStrip:
			return GL2ES2.GL_LINE_STRIP;
		case Triangles:
			return GL2ES2.GL_TRIANGLES;
		case TriangleFan:
			return GL2ES2.GL_TRIANGLE_FAN;
		case TriangleStrip:
			return GL2ES2.GL_TRIANGLE_STRIP;
		default:
			throw new UnsupportedOperationException("Unrecognized mesh mode: "
					+ mode);
		}
	}

	public void updateVertexArray(Mesh mesh) {
		logger.log(Level.INFO, "updateVertexArray({0})", mesh);
		int id = mesh.getId();
		/*
		 * if (id == -1){ IntBuffer temp = intBuf1; //
		 * ARBVertexArrayObject.glGenVertexArrays(temp);
		 * GL2ES2.glGenVertexArrays(temp); id = temp.get(0); mesh.setId(id); }
		 * 
		 * if (context.boundVertexArray != id){ //
		 * ARBVertexArrayObject.glBindVertexArray(id);
		 * GL2ES2.glBindVertexArray(id); context.boundVertexArray = id; }
		 */
		VertexBuffer interleavedData = mesh.getBuffer(Type.InterleavedData);
		if (interleavedData != null && interleavedData.isUpdateNeeded()) {
			updateBufferData(interleavedData);
		}

		for (VertexBuffer vb : mesh.getBufferList().getArray()) {

			if (vb.getBufferType() == Type.InterleavedData
					|| vb.getUsage() == Usage.CpuOnly // ignore cpu-only buffers
					|| vb.getBufferType() == Type.Index) {
				continue;
			}

			if (vb.getStride() == 0) {
				// not interleaved
				setVertexAttrib(vb);
			} else {
				// interleaved
				setVertexAttrib(vb, interleavedData);
			}
		}
	}

	/**
	 * renderMeshVertexArray renders a mesh using vertex arrays
	 * 
	 * @param mesh
	 * @param lod
	 * @param count
	 */
	private void renderMeshVertexArray(Mesh mesh, int lod, int count) {
		// IntMap<VertexBuffer> buffers = mesh.getBuffers();
		for (VertexBuffer vb : mesh.getBufferList().getArray()) {

			if (vb.getBufferType() == Type.InterleavedData
					|| vb.getUsage() == Usage.CpuOnly // ignore cpu-only buffers
					|| vb.getBufferType() == Type.Index) {
				continue;
			}

			if (vb.getStride() == 0) {
				// not interleaved
				setVertexAttrib_Array(vb);
			} else {
				// interleaved
				VertexBuffer interleavedData = mesh
						.getBuffer(Type.InterleavedData);
				setVertexAttrib_Array(vb, interleavedData);
			}
		}

		VertexBuffer indices = null;
		if (mesh.getNumLodLevels() > 0) {
			indices = mesh.getLodLevel(lod);
		} else {
			indices = mesh.getBuffer(Type.Index);// buffers.get(Type.Index.ordinal());
		}
		if (indices != null) {
			drawTriangleList_Array(indices, mesh, count);
		} else {
			getGL().glDrawArrays(convertElementMode(mesh.getMode()), 0,
					mesh.getVertexCount());
		}
		clearVertexAttribs();
		clearTextureUnits();
	}

	private void renderMeshDefault(Mesh mesh, int lod, int count) {
		VertexBuffer indices = null;
		VertexBuffer interleavedData = mesh.getBuffer(Type.InterleavedData);
		if (interleavedData != null && interleavedData.isUpdateNeeded()) {
			updateBufferData(interleavedData);
		}

		// IntMap<VertexBuffer> buffers = mesh.getBuffers(); ;
		if (mesh.getNumLodLevels() > 0) {
			indices = mesh.getLodLevel(lod);
		} else {
			indices = mesh.getBuffer(Type.Index);// buffers.get(Type.Index.ordinal());
		}
		for (VertexBuffer vb : mesh.getBufferList().getArray()) {

			if (vb.getBufferType() == Type.InterleavedData
					|| vb.getUsage() == Usage.CpuOnly // ignore cpu-only buffers
					|| vb.getBufferType() == Type.Index) {
				continue;
			}

			if (vb.getStride() == 0) {
				// not interleaved
				setVertexAttrib(vb);
			} else {
				// interleaved
				setVertexAttrib(vb, interleavedData);
			}
		}
		if (indices != null) {
			drawTriangleList(indices, mesh, count);
		} else {
			// throw new
			// UnsupportedOperationException("Cannot render without index buffer");
			getGL().glDrawArrays(convertElementMode(mesh.getMode()), 0,
					mesh.getVertexCount());
		}
		clearVertexAttribs();
		clearTextureUnits();
	}

	public void renderMesh(Mesh mesh, int lod, int count) {
		
		GL2ES1 gles1 = GLContext.getCurrentGL().getGL2ES1();
		GL2ES2 gles2 = getGL();
		
		if (context.pointSize != mesh.getPointSize()) {
			System.out.println("------render mesh GLES1");
			gles1.glPointSize(mesh.getPointSize());
			context.pointSize = mesh.getPointSize();
		}
		if (context.lineWidth != mesh.getLineWidth()) {
			gles2.glLineWidth(mesh.getLineWidth());
			context.lineWidth = mesh.getLineWidth();
		}

		statistics.onMeshDrawn(mesh, lod);
		// if (GLContext.getCapabilities().GL_ARB_vertex_array_object){
		// renderMeshVertexArray(mesh, lod, count);
		// }else{

		if (useVBO) {
			renderMeshDefault(mesh, lod, count);
		} else {
			renderMeshVertexArray(mesh, lod, count);
		}
	}

	/**
	 * drawTriangleList_Array uses Vertex Array
	 * 
	 * @param indexBuf
	 * @param mesh
	 * @param count
	 */
	public void drawTriangleList_Array(VertexBuffer indexBuf, Mesh mesh,
			int count) {
		if (indexBuf.getBufferType() != VertexBuffer.Type.Index) {
			throw new IllegalArgumentException(
					"Only index buffers are allowed as triangle lists.");
		}

		boolean useInstancing = count > 1 && caps.contains(Caps.MeshInstancing);
		if (useInstancing) {
			throw new IllegalArgumentException(
					"Caps.MeshInstancing is not supported.");
		}

		int vertCount = mesh.getVertexCount();
		Buffer indexData = indexBuf.getData();
		indexData.rewind();

		if (mesh.getMode() == Mode.Hybrid) {
			int[] modeStart = mesh.getModeStart();
			int[] elementLengths = mesh.getElementLengths();

			int elMode = convertElementMode(Mode.Triangles);
			int fmt = convertFormat(indexBuf.getFormat());
			int elSize = indexBuf.getFormat().getComponentSize();
			int listStart = modeStart[0];
			int stripStart = modeStart[1];
			int fanStart = modeStart[2];
			int curOffset = 0;
			for (int i = 0; i < elementLengths.length; i++) {
				if (i == stripStart) {
					elMode = convertElementMode(Mode.TriangleStrip);
				} else if (i == fanStart) {
					elMode = convertElementMode(Mode.TriangleStrip);
				}
				int elementLength = elementLengths[i];

				indexBuf.getData().position(curOffset);
				getGL().glDrawElements(elMode, elementLength, fmt,
						indexBuf.getData());

				curOffset += elementLength * elSize;
			}
		} else {
			getGL().glDrawElements(convertElementMode(mesh.getMode()), indexBuf
					.getData().limit(), convertFormat(indexBuf.getFormat()),
					indexBuf.getData());
		}
	}

	/**
	 * setVertexAttrib_Array uses Vertex Array
	 * 
	 * @param vb
	 * @param idb
	 */
	public void setVertexAttrib_Array(VertexBuffer vb, VertexBuffer idb) {
		
		GL2ES2 gl = getGL();
		
		if (vb.getBufferType() == VertexBuffer.Type.Index) {
			throw new IllegalArgumentException(
					"Index buffers not allowed to be set to vertex attrib");
		}

		// Get shader
		int programId = context.boundShaderProgram;
		
		if (programId > 0) {
			VertexBuffer[] attribs = context.boundAttribs;

			Attribute attrib = boundShader.getAttribute(vb.getBufferType());
			int loc = attrib.getLocation();
			if (loc == -1) {
				// throw new
				// IllegalArgumentException("Location is invalid for attrib: ["
				// + vb.getBufferType().name() + "]");
				return;
			} else if (loc == -2) {
				String attributeName = "in" + vb.getBufferType().name();

				loc = gl.glGetAttribLocation(programId, attributeName);
				if (loc < 0) {
					attrib.setLocation(-1);
					return; // not available in shader.
				} else {
					attrib.setLocation(loc);
				}

			} // if (loc == -2)

			if ((attribs[loc] != vb) || vb.isUpdateNeeded()) {
				// NOTE: Use data from interleaved buffer if specified
				VertexBuffer avb = idb != null ? idb : vb;
				avb.getData().rewind();
				avb.getData().position(vb.getOffset());

				// Upload attribute data
				gl.glVertexAttribPointer(loc, vb.getNumComponents(),
						convertFormat(vb.getFormat()), vb.isNormalized(),
						vb.getStride(), avb.getData());

				gl.glEnableVertexAttribArray(loc);

				attribs[loc] = vb;
			} // if (attribs[loc] != vb)
		} else {
			throw new IllegalStateException(
					"Cannot render mesh without shader bound");
		}
	}

	/**
	 * setVertexAttrib_Array uses Vertex Array
	 * 
	 * @param vb
	 */
	public void setVertexAttrib_Array(VertexBuffer vb) {
		setVertexAttrib_Array(vb, null);
	}

	public void setAlphaToCoverage(boolean value) {
		if (value) {
			getGL().glEnable(GL2ES2.GL_SAMPLE_ALPHA_TO_COVERAGE);
		} else {
			getGL().glDisable(GL2ES2.GL_SAMPLE_ALPHA_TO_COVERAGE);
		}
	}

	@Override
	public void invalidateState() {
		context.reset();
		boundShader = null;
		// lastFb = null;
	}

}
