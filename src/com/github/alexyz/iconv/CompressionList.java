package com.github.alexyz.iconv;

import java.util.AbstractList;
import java.util.function.Function;

/** a list that calls a function to generate a byte array for each element */
public class CompressionList extends AbstractList<Long> {
	
	private final Function<Integer, byte[]> f;
	private final byte[][] a;

	public CompressionList (Function<Integer,byte[]> f, int size) {
		this.f = f;
		this.a = new byte[size][];
	}
	
	@Override
	public int size () {
		return a.length;
	}

	@Override
	public Long get (int i) {
		return Long.valueOf((a[i] != null ? a[i] : (a[i] = f.apply(Integer.valueOf(i)))).length);
	}

	public byte[] getData (int i) {
		return a[i];
	}
}
