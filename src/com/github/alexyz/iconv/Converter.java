package com.github.alexyz.iconv;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;

public class Converter {
	
	/** map of file extension to result */
	public final Map<String, Result> results = new TreeMap<>();
	public final List<File> failed = new ArrayList<>();
	
	public String format;
	public long maxLength;
	public int maxDimension;
	public File inputDir;
	public File outputDir;
	public boolean recurse;
	public boolean commit;
	public File dwebpExecutable;
	public int threads;
	public boolean convertFormat = true;
	public boolean convertLength = true;
	public boolean convertDim;
	
	private ExecutorService executor;
	private boolean cancel;
	private String formatName;
	
	
	public Converter () {
		//
	}
	
	private Result getResult (String ext) {
		return results.compute(ext, (k, v) -> v != null ? v : new Result());
	}
	
	public void run () throws Exception {
		if (maxDimension < 0) {
			throw new Exception("invalid maxdim");
		}
		
		if (inputDir == null) {
			throw new Exception("invalid indir");
		}
		
		if (outputDir == null || !inputDir.exists()) {
			throw new Exception("invalid outdir");
		}
		
		if (format == null) {
			throw new Exception("invalid fmt");
		}

		if (!(convertLength || convertFormat || convertDim)) {
			throw new Exception("must convert one of length, format, dimension");
		}
		
		Iterator<ImageWriter> i = ImageIO.getImageWritersBySuffix(format);
		Iterator<ImageReader> j = ImageIO.getImageReadersBySuffix(format);
		if (!i.hasNext() || !j.hasNext()) {
			throw new Exception("invalid fmt");
		}
		
		ImageReader reader = j.next();
		formatName = reader.getFormatName();
		reader.dispose();
		
		ImageWriter writer = i.next();
		ImageWriteParam p = writer.getDefaultWriteParam();
		if (p.canWriteCompressed()) {
			if (maxLength < 0) {
				throw new Exception("invalid maxlen/qual");
			}
		} else if (maxLength != 0) {
			throw new Exception("cannot specify maxlen with lossless fmt");
		}
		writer.dispose();

		if (threads > 1) {
			executor = Executors.newFixedThreadPool(threads);
		}
		
		find(inputDir, outputDir);
		
		if (executor != null) {
			System.out.println("shutdown");
			executor.shutdown();
			while (!executor.isTerminated()) {
				executor.awaitTermination(1, TimeUnit.SECONDS);
			}
		}
		
	}
	
	private void find (File indir, File outdir) throws Exception {
		System.out.println("indir " + indir.getAbsolutePath());
		System.out.println("outdir " + outdir.getAbsolutePath());
		
		File[] list = indir.listFiles();
		Arrays.sort(list);
		
		for (File f : list) {
			if (cancel) {
				break;
			} else if (f.isDirectory() && recurse) {
				find(f, new File(outdir, f.getName()));
			} else if (f.isFile()) {
				if (executor != null) {
					executor.submit(() -> applyBg(f, outdir));
				} else {
					apply(f, outdir);
				}
			}
		}
		
		System.gc();
	}
	
	private BufferedImage read (File f, String ext) throws Exception {
		Iterator<ImageReader> i = ImageIO.getImageReadersBySuffix(ext);
		if (i.hasNext()) {
			return readImageIo(f, i.next());
		}
		
		if (ext.equals("webp") && dwebpExecutable != null) {
			return readWebp(f);
		}
		
		return null;
	}
	
	private BufferedImage readImageIo (File f, ImageReader reader) throws Exception {
		try (InputStream is = new FileInputStream(f)) {
			try (ImageInputStream iis = ImageIO.createImageInputStream(is)) {
				reader.setInput(iis);
				if (reader.getNumImages(true) > 1) {
					throw new RuntimeException("multi image " + f);
				}
				Iterator<ImageTypeSpecifier> t = reader.getImageTypes(0);
				ImageTypeSpecifier ts = t.next();
				int dim = Math.max(reader.getWidth(0), reader.getHeight(0));
				boolean fmtok = reader.getFormatName().equals(formatName);
				boolean lengthok = maxLength == 0 || f.length() <= maxLength;
				boolean dimok = maxDimension == 0 || dim <= maxDimension;
				int type = ts.getBufferedImageType();
				System.out.println(String.format("%s fmt=%s fmtok=%s len=%d lenok=%s dim=%d dimok=%s type=%s", 
						f.getName(), reader.getFormatName(), fmtok, f.length(), lengthok, dim, dimok, ConverterUtil.getTypeStr(type)));
				BufferedImage im = null;
				if ((convertFormat && !fmtok) || (convertLength && !lengthok) || (convertDim && !dimok)) {
					im = reader.read(0);
				}
				return im;
			}
		}
	}
	
	private BufferedImage readWebp (File f) throws Exception {
		ProcessBuilder pb = new ProcessBuilder(dwebpExecutable.getAbsolutePath(), f.getAbsolutePath(), "-bmp", "-o", "-");
		Process p = pb.start();
		
		Iterator<ImageReader> i = ImageIO.getImageReadersBySuffix("bmp");
		ImageReader reader = i.next();
		BufferedImage image = null;
		
		try (InputStream is = new BufferedInputStream(p.getInputStream())) {
			try (ImageInputStream iis = ImageIO.createImageInputStream(is)) {
				reader.setInput(iis);
				image = reader.read(0, null);
				Iterator<ImageTypeSpecifier> tsi = reader.getImageTypes(0);
				ImageTypeSpecifier ts = tsi.next();
				int dim = Math.max(reader.getWidth(0), reader.getHeight(0));
				int type = ts.getBufferedImageType();
				System.out.println(String.format("%s fmt=%s len=%d dim=%d type=%s",
						f.getName(), reader.getFormatName(), f.length(), dim, ConverterUtil.getTypeStr(type)));
			}
		}
		
		reader.dispose();
		int ex = p.waitFor();
		if (ex != 0) {
			throw new RuntimeException("dwebp exited " + ex);
		}
		
		return image;
	}
	
	private void applyBg (File infile, File outdir) {
		try {
			apply(infile,outdir);
		} catch (Exception e) {
			cancel = true;
			e.printStackTrace();
		}
	}
	
	private void apply (File infile, File outdir) throws Exception {
		String ext = ConverterUtil.getExtension(infile);
		Result result = getResult(ext);
		result.inbytes += infile.length();
		
		BufferedImage image = null;
		try {
			image = read(infile, ext);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			e.printStackTrace(System.out);
			failed.add(infile);
			image = null;
		}
		
		if (image != null) {
			int dim = Math.max(image.getWidth(), image.getHeight());
			boolean dimok = maxDimension == 0 || dim <= maxDimension;
			if (!dimok || ConverterUtil.convert(image.getType())) {
				image = redraw(image);
			}
			int size = compress(infile, outdir, image);
			result.converted++;
			result.outbytes += size;
			
		} else {
			copyTo(infile, outdir);
			result.copied++;
			result.outbytes += infile.length();
		}
		
	}
	
	private BufferedImage redraw (BufferedImage image) {
		int dim = Math.max(image.getWidth(), image.getHeight());
		boolean dimok = maxDimension == 0 || dim <= maxDimension;
		double ratio = dimok ? 1 : ((double) maxDimension) / dim;
		int w2 = (int) (image.getWidth() * ratio);
		int h2 = (int) (image.getHeight() * ratio);
		int t2 = ConverterUtil.convertType(image.getType());
		System.out.println(String.format("\tredraw to %d, %dm %s", w2, h2, ConverterUtil.getTypeStr(t2)));
		
		BufferedImage image2 = new BufferedImage(w2, h2, t2);
		Graphics2D g2 = image2.createGraphics();
		g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g2.drawImage(image, 0, 0, w2, h2, null);
		
		return image2;
	}
	
	private int compress (File infile, File outdir, BufferedImage image) throws Exception {
		Iterator<ImageWriter> writeri = ImageIO.getImageWritersBySuffix(format);
		ImageWriter writer = writeri.next();
		ImageWriteParam p = writer.getDefaultWriteParam();
		byte[] a = null;
		
		if (p.canWriteCompressed()) {
			p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			if (maxLength > 0) {
				int maxq = 256;
				CompressionList l = new CompressionList(i -> ConverterUtil.writeLossy(writer, image, i.intValue(), maxq), maxq + 1);
				int maxqlen = l.get(maxq).intValue();
				
				if (maxqlen > maxLength) {
					// binary search for quality
					int i = Collections.binarySearch(l, Long.valueOf(maxLength));
					int j = i >= 0 ? i : i == -1 ? 0 : -i - 2;
					a = l.getData(j);
					//System.out.println("final i = " + i + " j=" + j + " q=" + ((j * 1f) / maxq) + " len=" + a.length);
					System.out.println(String.format("\tcompress at qual %d => %d", j, a.length));
				} else {
					System.out.println("\tcompress at max qual => " + maxqlen);
					a=l.getData(maxq);
				}
				
			} else {
				p.setCompressionQuality(1);
				a = ConverterUtil.write(writer, p, image);
				System.out.println("  compress at max qual => " + a.length);
			}
		} else {
			a = ConverterUtil.write(writer, p, image);
			System.out.println("  compress lossless => " + a.length);
		}
		
		File outfile = new File(outdir, infile.getName() + "." + format);
		if (outfile.exists()) {
			throw new Exception(outfile + " exists");
		}
		
		// System.out.println(" write " + outfile.getName());
		if (commit) {
			Files.write(outfile.toPath(), a);
		}
		
		writer.dispose();
		return a.length;
	}
	
	private void copyTo (File infile, File outdir) throws Exception {
		File outfile = new File(outdir, infile.getName());
		if (outfile.exists()) {
			throw new Exception(outfile + " exists");
		}
		
		if (commit) {
			if (!outdir.isDirectory()) {
				outdir.mkdirs();
			}
			System.out.println("\tcopy");
			Files.copy(infile.toPath(), outfile.toPath());
		}
	}
	
	@Override
	public String toString () {
		return String.format("Converter [commit=%s maxdim=%s maxlen=%s recurse=%s fmt=%s indir=%s outdir=%s cfmt=%s clen=%s cdim=%s]",
				commit, maxDimension, maxLength, recurse, format, inputDir, outputDir, convertFormat, convertLength, convertDim);
	}
}
