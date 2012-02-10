package com.foursquare.heapaudit.test;

import com.foursquare.heapaudit.HeapRecorder;
import com.foursquare.heapaudit.HeapUtil;

import java.util.concurrent.ConcurrentLinkedQueue;

public class TestUtil {

    public TestUtil() {

	this(false);

    }

    public TestUtil(boolean global) {

	HeapRecorder.register(recorder,
			      global);

    }

    public void clear() {

    HeapUtil.clearSizeCache();

	recorder.clear();

    }

    public boolean expect(String name,
			  int count,
			  long size) {

	return recorder.expect(name,
			       count,
			       size);

    }

    private final Recorder recorder = new Recorder();

    private class Recorder extends HeapRecorder {

	@Override public void record(String name,
				     int count,
				     long size) {

	    entries.add(new Entry(friendly(name),
				  count,
				  size));

	}

	public void clear() {

	    entries.clear();

	}

	public boolean expect(String name,
			      int count,
			      long size) {

	    return entries.remove(new Entry(friendly(name),
					    count,
					    size));

	}

	private class Entry {

	    public Entry(String name,
			 int count,
			 long size) {

		this.name = name;

		this.count = count;

		this.size = size;

	    }

	    @Override public boolean equals(Object obj) {

		Entry e = (Entry)obj;

		return name.equals(e.name) && (count == e.count) && (size == e.size);

	    }

	    public final String name;

	    public final int count;

	    public final long size;

	}

	private final ConcurrentLinkedQueue<Entry> entries = new ConcurrentLinkedQueue<Entry>();

    }

}
