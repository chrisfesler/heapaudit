package com.foursquare.heapaudit;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodAdapter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public abstract class HeapUtil {

    public static void log(String text) {

        HeapSettings.output.println(text);

    }

    public static void instrumentation(boolean debug,
                                       String text) {

        if (debug) {

            log("\t" + text);

        }

    }

    public static void execution(boolean trace,
                                 MethodAdapter mv,
                                 String text) {

        if (trace) {

            // STACK [...]
            mv.visitLdcInsn(text);
            // STACK [...|text]
            mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                               "com/foursquare/heapaudit/HeapUtil",
                               "log",
                               "(Ljava/lang/String;)V");
            // STACK [...]

        }

    }

    protected static void visitCheck(MethodVisitor mv,
                                     Label cleanup) {

        // STACK: [...]
        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                           "com/foursquare/heapaudit/HeapRecorder",
                           "hasRecorders",
                           "()Z");
        // STACK: [...|status]
        mv.visitJumpInsn(Opcodes.IFEQ,
                         cleanup);
        // STACK: [...]

    }

    protected static void visitCleanup(MethodVisitor mv,
                                       Label cleanup,
                                       Label finish) {

        // STACK: [...]
        mv.visitJumpInsn(Opcodes.GOTO,
                         finish);
        // STACK: [...]
        mv.visitLabel(cleanup);
        // STACK: [...]

    }

    protected static void visitFinish(MethodVisitor mv,
                                      Label finish) {

        // STACK: [...]
        mv.visitLabel(finish);
        // STACK: [...]

    }

    // The following holds a cache of type to size mappings.

    private static ConcurrentHashMap<String, Long> sizes = new ConcurrentHashMap<String, Long>();

    // This is a per thread status to ignore allocations performed within the
    // sizeOf code path. Unfortunately, we can't rely on disableRecording because
    // the various sizeOf calls happen previous to the call to the primary record
    // method, which is where notification of the recorders is prevented if
    // recording is non-zero.

    public static void clearSizeCache() {

        sizes.clear();

    }

    private static ThreadLocal<Integer> cachingSize = new ThreadLocal<Integer>() {

        @Override protected Integer initialValue() {

            return 0;

        }

    };

    private static long sizeOf(Object obj,
                               String type) {

        Long size = sizes.get(type);

        if (size == null) {

            size = HeapRecorder.instrumentation.getObjectSize(obj);

            // The following suppresses recording of allocations due to the
            // HeapAudit library itself to avoid being caught in an infinite loop.

            int index = cachingSize.get();

            cachingSize.set(index + 1);

            if (index == 0) {

                sizes.put(type,
                      size);

            }

            cachingSize.set(index);

        }

        return size;

    }

    // This is a per thread status to ignore allocations performed within the
    // record code path.

    private static ThreadLocal<Integer> recording = new ThreadLocal<Integer>() {

        @Override protected Integer initialValue() {

            return 0;

        }

    };

    public static void record(Object obj,
                              int count,
                              String type,
                              long size) {

        // Avoid recording allocations due to our caching of sizes.

        if (cachingSize.get() != 0) {
            return;
        }

        // The following suppresses recording of allocations due to the
        // HeapAudit library itself to avoid being caught in an infinite loop.

        int index = recording.get();

        recording.set(index + 1);

        if (index == 0) {

            record(count,
                   type,
                   size < 0 ? sizeOf(obj, type) : size);

        }

        recording.set(index);

    }

    public static void record(Object obj,
                              String type) {

        if (type.charAt(0) != '[') {

            record(obj,
                   -1,
                   type,
                   -1);

        }
        else {

            long overhead = 0;

            Object[] o = (Object[])obj;

            int length = o.length;

            int count = length;

            for (int i = 1; i < type.length(); ++i) {

                if (type.charAt(i) == '[') {

                    // The following assumes the size of array of array,
                    // including the overhead of the array bookkeeping itself
                    // is only affected by the number of elements, not the
                    // actual element type.

                    int index = disableRecording();

                    String typeArg = "" + length + "[[L";

                    enableRecording(index);

                    overhead += sizeOf(o,
                                       typeArg);

                    switch (type.charAt(i + 1)) {

                    case 'Z':

                        length = ((boolean[])o[0]).length;

                        break;

                    case 'B':

                        length = ((byte[])o[0]).length;

                        break;

                    case 'C':

                        length = ((char[])o[0]).length;

                        break;

                    case 'S':

                        length = ((short[])o[0]).length;

                        break;

                    case 'I':

                        length = ((int[])o[0]).length;

                        break;

                    case 'J':

                        length = ((long[])o[0]).length;

                        break;

                    case 'F':

                        length = ((float[])o[0]).length;

                        break;

                    case 'D':

                        length = ((double[])o[0]).length;

                        break;

                    case 'L':

                        length = ((Object[])o[0]).length;

                        break;

                    default:

                        o = (Object[])(o[0]);

                        length = o.length;

                        count *= length;

                    }

                }
                else {

                    int index = disableRecording();

                    String typeArg = "" + length + type.substring(i - 1);

                    enableRecording(index);

                    record(obj,
                           count * length,
                           type.substring(i),
                           overhead + count * sizeOf(o[0],
                                   typeArg));

                    break;
                }

            }

        }

    }

    public static void record(Object obj, int count, String type) {

        int index = disableRecording();

        String typeArg = "" + count + "[" + type;

        enableRecording(index);

        record(obj,
               count,
               type,
               sizeOf(obj,
                      typeArg));

    }

    public static void record(Object obj,
                              int[] dimensions,
                              String type) {

        long overhead = 0;

        Object o[] = (Object[])obj;

        int count = 1;

        for (int i = 0; i < dimensions.length - 1 && count > 0; ++i) {

            int length = dimensions[i];

            if (length >= 0) {

                // The following assumes the size of array of array, including
                // the overhead of the array bookkeeping itself is only affected
                // by the number of elements, not the actual element type.

                int index = disableRecording();

                String typeArg = "" + length + "[[L";

                enableRecording(index);

                overhead += sizeOf(o,
                                   typeArg);

                o = (Object[])(o[0]);

            }

            count *= length;

        }

        if (count > 0) {

            int length = dimensions[dimensions.length - 1];

            int index = disableRecording();

            String typeArg = "" + length + "[" + type;

            enableRecording(index);

            record(obj,
                   count * length,
                   type,
                   overhead + count * sizeOf(o,
                                             typeArg));

        }

    }

    public static void record(int count,
                              String type,
                              long size) {

        try {

            for (HeapRecorder recorder: HeapRecorder.getRecorders()) {

                recorder.record(type,
                                count,
                                size);

            }

        } catch (Exception e) {

            System.err.println(e);

        }

    }

    private final static HashMap<String, HeapQuantile> recorders = new HashMap<String, HeapQuantile>();

    public static boolean inject(String id) {

        if (recorders.containsKey(id)) {

            log("Recorder already exists for " + id);

            return false;

        }

        log(id);

        recorders.put(id,
                      new HeapQuantile());

        return true;

    }

    public static boolean remove(String id) {

        HeapQuantile recorder = recorders.remove(id);

        if (recorder == null) {

            log("Recorder does not exist for " + id);

            return false;

        }

        HeapSettings.output.println(recorder.summarize(true,
                                                       id));

        return true;

    }

    public static void register(String id) {

        HeapQuantile recorder = recorders.get(id);

        if (recorder != null) {

            HeapRecorder.register(recorder);

        }

    }

    public static void unregister(String id) {

        HeapQuantile recorder = recorders.get(id);

        if (recorder != null) {

            HeapRecorder.unregister(recorder);

        }

    }

    /**
     * Stop all recording. Invoke prior to doing anything in HeapRecorder that will allocate
     * in order to prevent HeapRecorder's own allocations from being counted.
     *
     * Be sure to reenable recording after your allocations by calling enableRecording, passing
     * the value returned by this method.
     *
     * @return index, pass back to enableRecording.
     */
    private static int disableRecording() {
        
        int index = recording.get();
        
        recording.set(index + 1);
        
        return index;

    }

    /**
     * Use in tandem with disableRecording, to exclude some allocations from
     * being counted.
     *
     * @param index the value returned by the corresponding call to disableRecording
     */
    private static void enableRecording(int index) {
        
        recording.set(index);

    }
}
