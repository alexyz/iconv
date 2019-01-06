package com.github.alexyz.iconv;

public class Result {
	public int copied;
	public int converted;
	public int inbytes;
	public int outbytes;
	@Override
	public String toString () {
		return String.format("copied=%d converted=%d inbytes=%.1fM outbytes=%.1fM out/in=%.1fpc", 
				copied, converted, inbytes / 1000000f, outbytes / 1000000f, inbytes > 0 ? (outbytes *100f) / inbytes : -1);
	}
}
