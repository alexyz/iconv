package com.github.alexyz.iconv;

import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

public class ConverterMain {
	
	public static void main (String[] args) throws Exception {
		
		if (args.length == 0) {
			System.out.println("usage: java -jar iconv.jar {opt=?}");
			System.out.println("  maxdim = maximum image dimension");
			System.out.println("  maxlen = maximum file size");
			System.out.println("  format = output format " + Arrays.toString(ImageIO.getReaderFileSuffixes()));
			System.out.println("  commit = commit changes");
			System.out.println("  recurse = recurse input directory");
			System.out.println("  in = input directory");
			System.out.println("  out = output directory");
			return;
		}
		
		Converter converter = new Converter();
		
		for (String a : args) {
			int i = a.indexOf("=");
			if (i <= 0 || i + 1 == a.length()) {
				throw new Exception("invalid arg " + a);
			}
			String k = a.substring(0, i);
			String v = a.substring(i+1);
			switch (k) {
				case "maxdim": converter.maxDimension = Integer.parseInt(v); break;
				case "maxlen": converter.maxLength = parseLen(v); break;
				case "format": converter.format = v; break;
				case "commit": converter.commit = Boolean.parseBoolean(v); break;
				case "recurse": converter.recurse = Boolean.parseBoolean(v); break;
				case "in": converter.inputDir = new File(v); break;
				case "out": converter.outputDir = new File(v); break;
				case "dwebp": converter.dwebpExecutable = new File(v); break;
				case "threads": converter.threads = Integer.parseInt(v); break;
				default: throw new Exception("unrecognised key " + k);
			}
		}
		
		System.out.println(converter);
		
		long st = System.nanoTime();
		converter.run();
		
		long et = System.nanoTime();
		System.out.println(String.format("time: %.1f", (et-st)/1000000000.0));
		
		Result t = new Result();
		for (Map.Entry<String,Result> e : converter.results.entrySet()) {
			Result r = e.getValue();
			System.out.println(e.getKey() + " => " + r);
			t.converted += r.converted;
			t.copied += r.copied;
			t.inbytes += r.inbytes;
			t.outbytes += r.outbytes;
		}
		
		System.out.println("total: " + t);
	}
	
	private static long parseLen (String s) throws Exception {
		Pattern p = Pattern.compile("(\\d+(\\.\\d+)?)([KMG])?");
		Matcher m = p.matcher(s.toUpperCase());
		if (m.matches()) {
			double i = Double.parseDouble(m.group(1));
			switch (String.valueOf(m.group(3))) {
				case "K":
					i = i * Math.pow(2, 10);
					break;
				case "M":
					i = i * Math.pow(2, 20);
					break;
				case "G":
					i = i * Math.pow(2, 30);
					break;
				default:
					break;
			}
			return (long) i;
		} else {
			throw new Exception("cannot parse " + s);
		}
	}
}
