package com.github.alexyz.iconv;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

public class ConverterUtil {

	public static String getExtension (File f) {
		String n = f.getName().toLowerCase();
		int i = n.lastIndexOf(".");
		return i > 0 ? n.substring(i + 1) : null;
	}

	public static String getTypeStr (int t) {
		switch (t) {
			case BufferedImage.TYPE_CUSTOM: return "CUSTOM";
			case BufferedImage.TYPE_INT_RGB: return "RGB";
			case BufferedImage.TYPE_INT_ARGB: return "ARGB";
			case BufferedImage.TYPE_INT_ARGB_PRE: return "ARGB_PRE";
			case BufferedImage.TYPE_INT_BGR: return "BGR";
			case BufferedImage.TYPE_3BYTE_BGR: return "3_BGR";
			case BufferedImage.TYPE_4BYTE_ABGR: return "4_ABGR";
			case BufferedImage.TYPE_4BYTE_ABGR_PRE: return "4_ABGR_PRE";
			case BufferedImage.TYPE_USHORT_565_RGB: return "2_RGB";
			case BufferedImage.TYPE_USHORT_555_RGB: return "2_RGB";
			case BufferedImage.TYPE_BYTE_GRAY: return "GRAY";
			case BufferedImage.TYPE_USHORT_GRAY: return "2_GRAY";
			case BufferedImage.TYPE_BYTE_BINARY: return "BINARY";
			case BufferedImage.TYPE_BYTE_INDEXED: return "INDEXED";
			default: return "" + t;
		}
	}
	
	/** true if image needs type conversion */
	public static boolean convert (int t) {
		switch (t) {
			case BufferedImage.TYPE_INT_RGB:
			case BufferedImage.TYPE_INT_BGR:
			case BufferedImage.TYPE_3BYTE_BGR:
			case BufferedImage.TYPE_BYTE_GRAY:
				return false;
			default:
				return true;
		}
	}
	
	public static int convertType (int t) {
		switch (t) {
			case BufferedImage.TYPE_3BYTE_BGR:
			case BufferedImage.TYPE_INT_RGB:
			case BufferedImage.TYPE_INT_BGR:
			case BufferedImage.TYPE_USHORT_565_RGB:
			case BufferedImage.TYPE_USHORT_555_RGB:
			case BufferedImage.TYPE_BYTE_INDEXED:
			case BufferedImage.TYPE_INT_ARGB:
			case BufferedImage.TYPE_INT_ARGB_PRE:
			case BufferedImage.TYPE_4BYTE_ABGR:
			case BufferedImage.TYPE_4BYTE_ABGR_PRE:
			case BufferedImage.TYPE_CUSTOM:
				return BufferedImage.TYPE_3BYTE_BGR;
				
			case BufferedImage.TYPE_BYTE_GRAY:
			case BufferedImage.TYPE_USHORT_GRAY:
			case BufferedImage.TYPE_BYTE_BINARY:
				return BufferedImage.TYPE_BYTE_GRAY;
				
			default: 
				throw new RuntimeException();
		}
	}

	public static byte[] write (ImageWriter writer, ImageWriteParam param, BufferedImage image) {
		try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
			try (ImageOutputStream output = ImageIO.createImageOutputStream(os)) {
				writer.setOutput(output);
				writer.write(null, new IIOImage(image, null, null), param);
			}
			return os.toByteArray();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static byte[] writeLossy (ImageWriter writer, BufferedImage image, float quality, float maxquality) {
		ImageWriteParam p = writer.getDefaultWriteParam();
		p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
		p.setCompressionQuality(quality / maxquality);
		return write(writer, p, image);
	}
	
}
