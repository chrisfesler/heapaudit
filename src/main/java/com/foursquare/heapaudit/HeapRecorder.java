package com.foursquare.heapaudit;

import java.lang.instrument.Instrumentation;
import java.util.ArrayList;

public abstract class HeapRecorder {

    abstract public void record(String type,
                                int count,
                                long size);

    protected static String friendly(String type) {

        switch (type.charAt(0)) {

        case 'Z':

            return "boolean";

        case 'B':

            return "byte";

        case 'C':

            return "char";

        case 'S':

            return "short";

        case 'I':

            return "int";

        case 'J':

            return "long";

        case 'F':

            return "float";

        case 'D':

            return "double";

        default:

            return type.replaceAll("^\\[*L", "").replaceAll(";$", "").replaceAll("/", ".");

        }

    }

    // TODO (norberthu): If possible, declare isAuditing as final such that JVM
    // can optimize out the status checks during JIT.

    public static boolean isAuditing = false;

    public static Instrumentation instrumentation = null;

    private static ArrayList<HeapRecorder> globalRecorders = new ArrayList<HeapRecorder>();

    private static ThreadLocal<ArrayList<HeapRecorder>> localRecorders = new ThreadLocal<ArrayList<HeapRecorder>>() {

        @Override protected ArrayList<HeapRecorder> initialValue() {

            return new ArrayList<HeapRecorder>();

        }

    };

    // This is a per thread status to ignore allocations performed within the
    // hasRecorders code path

    private static ThreadLocal<Integer> checkingRecorders = new ThreadLocal<Integer>() {

        @Override protected Integer initialValue() {

            return 0;

        }

    };

    public static boolean hasRecorders() {

        boolean hasLocal = false;

        // The following suppresses recording of allocations due to the
        // HeapAudit library itself to avoid being caught in an infinite loop.

        int index = checkingRecorders.get();

        checkingRecorders.set(index + 1);
        
        if (index == 0) {

            hasLocal = localRecorders.get().size() > 0;
            
        }

        checkingRecorders.set(index);
        
        return hasLocal || (globalRecorders.size() > 0);
    }

    public static ArrayList<HeapRecorder> getRecorders() {

        ArrayList<HeapRecorder> recorders = new ArrayList<HeapRecorder>(globalRecorders);

        recorders.addAll(localRecorders.get());

        return recorders;

    }

    public static synchronized void register(HeapRecorder recorder) {

        ArrayList<HeapRecorder> recorders = new ArrayList<HeapRecorder>(globalRecorders);

        recorders.add(recorder);

        globalRecorders = recorders;

    }

    public static synchronized void unregister(HeapRecorder recorder) {

        ArrayList<HeapRecorder> recorders = new ArrayList<HeapRecorder>(globalRecorders);

        recorders.remove(recorder);

        globalRecorders = recorders;

    }

    public static void register(HeapRecorder recorder,
                                boolean global) {

        if (global) {

            register(recorder);

        }
        else {

            localRecorders.get().add(recorder);

        }

    }

    public static void unregister(HeapRecorder recorder,
                                  boolean global) {

        if (global) {

            unregister(recorder);

        }
        else {

            localRecorders.get().remove(recorder);

        }

    }

}
