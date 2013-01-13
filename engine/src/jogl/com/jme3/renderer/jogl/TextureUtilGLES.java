package com.jme3.renderer.jogl;

import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.media.opengl.GL2ES2;
import javax.media.opengl.GLContext;

import com.jme3.asset.AndroidImageInfo;
import com.jme3.math.FastMath;
import com.jme3.renderer.RendererException;
import com.jme3.renderer.android.TextureUtil;
import com.jme3.texture.Image;
import com.jme3.texture.Image.Format;
import com.jme3.util.BufferUtils;

public class TextureUtilGLES {
	
    private static final Logger logger = Logger.getLogger(TextureUtilGLES.class.getName());
    
    //TODO Make this configurable through appSettings
    public static boolean ENABLE_COMPRESSION = true;
    private static boolean NPOT = false;
    private static boolean ETC1support = false;
    private static boolean DXT1 = false;
    private static boolean DEPTH24 = false;
    
    public static void loadTextureFeatures(String extensionString) {
        ETC1support = extensionString.contains("GL_OES_compressed_ETC1_RGB8_texture");
        DEPTH24 = extensionString.contains("GL_OES_depth24");
        NPOT = extensionString.contains("GL_OES_texture_npot") || extensionString.contains("GL_NV_texture_npot_2D_mipmap");
        DXT1 = extensionString.contains("GL_EXT_texture_compression_dxt1");
        logger.log(Level.FINE, "Supports ETC1? {0}", ETC1support);
        logger.log(Level.FINE, "Supports DEPTH24? {0}", DEPTH24);
        logger.log(Level.FINE, "Supports NPOT? {0}", NPOT);
        logger.log(Level.FINE, "Supports DXT1? {0}", DXT1);
    }
    
	private static GL2ES2 getGL() {
		return GLContext.getCurrentGL().getGL2ES2();
	}
	

    
    public static void uploadTextureAny(Image img, int target, int index, boolean needMips) {
 
            logger.log(Level.FINEST, " === Uploading image {0}. Using BUFFER PATH === ", img);
            boolean wantGeneratedMips = needMips && !img.hasMipmaps();
            
            if (wantGeneratedMips && img.getFormat().isCompressed()) {
                logger.log(Level.WARNING, "Generating mipmaps is only"
                        + " supported for Bitmap based or non-compressed images!");
            }
            
            // Upload using slower path
            logger.log(Level.FINEST, " - Uploading bitmap directly.{0}", 
                        (wantGeneratedMips ? 
                            " Mipmaps will be generated in HARDWARE" : 
                            " Mipmaps are not generated."));
            uploadTexture(img, target, index);
            
            // Image was uploaded using slower path, since it is not compressed,
            // then compress it
            if (wantGeneratedMips) {
                // No pregenerated mips available,
                // generate from base level if required
                getGL().glGenerateMipmap(target);
            }
        
    }
    
    private static void unsupportedFormat(Format fmt) {
        throw new UnsupportedOperationException("The image format '" + fmt + "' is unsupported by the video hardware.");
    }

    private static void uploadTexture(Image img,
                                     int target,
                                     int index){

        if (img.getEfficentData() instanceof AndroidImageInfo){
            throw new RendererException("This image uses efficient data. "
                    + "Use uploadTextureBitmap instead.");
        }

        // Otherwise upload image directly. 
        // Prefer to only use power of 2 textures here to avoid errors.
        Image.Format fmt = img.getFormat();
        ByteBuffer data;
        if (index >= 0 || img.getData() != null && img.getData().size() > 0){
            data = img.getData(index);
        }else{
            data = null;
        }

        int width = img.getWidth();
        int height = img.getHeight();
        int depth = img.getDepth();
        
        if (!NPOT) {
            // Check if texture is POT
            if (!FastMath.isPowerOfTwo(width) || !FastMath.isPowerOfTwo(height)) {
                throw new RendererException("Non-power-of-2 textures "
                        + "are not supported by the video hardware "
                        + "and no scaling path available for image: " + img);
            }
        }
        
        boolean compress = false;
        int format = -1;
        int dataType = -1;

        switch (fmt){
            case RGBA16:
            case RGB16:
            case RGB10:
            case Luminance16:
            case Luminance16Alpha16:
            case Alpha16:
            case Depth32:
            case Depth32F:
                throw new UnsupportedOperationException("The image format '" 
                        + fmt + "' is not supported by OpenGL ES 2.0 specification.");
            case Alpha8:
                format = GL2ES2.GL_ALPHA;
                dataType = GL2ES2.GL_UNSIGNED_BYTE;                
                break;
            case Luminance8:
                format = GL2ES2.GL_LUMINANCE;
                dataType = GL2ES2.GL_UNSIGNED_BYTE;
                break;
            case Luminance8Alpha8:
                format = GL2ES2.GL_LUMINANCE_ALPHA;
                dataType = GL2ES2.GL_UNSIGNED_BYTE;
                break;
            case RGB565:
                format = GL2ES2.GL_RGB;
                dataType = GL2ES2.GL_UNSIGNED_SHORT_5_6_5;
                break;
            case ARGB4444:
                format = GL2ES2.GL_RGBA4;
                dataType = GL2ES2.GL_UNSIGNED_SHORT_4_4_4_4;
                break;
            case RGB5A1:
                format = GL2ES2.GL_RGBA;
                dataType = GL2ES2.GL_UNSIGNED_SHORT_5_5_5_1;
                break;
            case RGB8:
                format = GL2ES2.GL_RGB;
                dataType = GL2ES2.GL_UNSIGNED_BYTE;
                break;
            case BGR8:
                format = GL2ES2.GL_RGB;
                dataType = GL2ES2.GL_UNSIGNED_BYTE;
                break;
            case RGBA8:
                format = GL2ES2.GL_RGBA;                
                dataType = GL2ES2.GL_UNSIGNED_BYTE;
                break;
            case Depth:
            case Depth16:
            case Depth24:
                format = GL2ES2.GL_DEPTH_COMPONENT;
                dataType = GL2ES2.GL_UNSIGNED_BYTE;
                break;
            case DXT1:
                if (!DXT1) {
                    unsupportedFormat(fmt);
                }
                format = 0x83F0;
                dataType = GL2ES2.GL_UNSIGNED_BYTE;
                compress = true;
                break;
            case DXT1A:
                if (!DXT1) {
                    unsupportedFormat(fmt);
                }
                format = 0x83F1;
                dataType = GL2ES2.GL_UNSIGNED_BYTE;
                compress = true;
                break;
            default:
                throw new UnsupportedOperationException("Unrecognized format: " + fmt);
        }

        if (data != null) {
            getGL().glPixelStorei(GL2ES2.GL_UNPACK_ALIGNMENT, 1);
        }

        int[] mipSizes = img.getMipMapSizes();
        int pos = 0;
        if (mipSizes == null){
            if (data != null)
                mipSizes = new int[]{ data.capacity() };
            else
                mipSizes = new int[]{ width * height * fmt.getBitsPerPixel() / 8 };
        }

        // XXX: might want to change that when support
        // of more than paletted compressions is added..
        /// NOTE: Doesn't support mipmaps
//        if (compress){
//            data.clear();
//            GLES20.glCompressedTexImage2D(target,
//                                      1 - mipSizes.length,
//                                      format,
//                                      width,
//                                      height,
//                                      0,
//                                      data.capacity(),
//                                      data);
//            return;
//        }

        for (int i = 0; i < mipSizes.length; i++){
            int mipWidth =  Math.max(1, width  >> i);
            int mipHeight = Math.max(1, height >> i);
//            int mipDepth =  Math.max(1, depth  >> i);

            if (data != null){
                data.position(pos);
                data.limit(pos + mipSizes[i]);
            }

            if (compress && data != null){
                getGL().glCompressedTexImage2D(target,
                                          i,
                                          format,
                                          mipWidth,
                                          mipHeight,
                                          0,
                                          data.remaining(),
                                          data);
            }else{
                getGL().glTexImage2D(target,
                                i,
                                format,
                                mipWidth,
                                mipHeight,
                                0,
                                format,
                                dataType,
                                data);
            }

            pos += mipSizes[i];
        }
    }
}
