package software.shonk.seminar.hermes;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class RustFFMSorter {
    static MethodHandle quicksort;

    public static void main(String[] args) throws Throwable {
        double[] values = {5.0, 4.0, 4.1, 4.01, 4.001, 4.0001, 4.00001, 3.0, 2.0, 1.0};

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment arraySegment = arena.allocate(ValueLayout.JAVA_DOUBLE, values.length);
            for (int i = 0; i < values.length; i++) {
                arraySegment.setAtIndex(ValueLayout.JAVA_DOUBLE, i, values[i]);
            }

            Linker linker = Linker.nativeLinker();

            SymbolLookup lib;
            if (System.getProperty("os.name").toLowerCase().contains("linux")) {
                lib = SymbolLookup.libraryLookup("../libquicksort/target/release/libquicksort.so", Arena.global());
            } else if (System.getProperty("os.name").toLowerCase().contains("mac")) {
                lib = SymbolLookup.libraryLookup("../libquicksort/target/release/libquicksort.dylib", Arena.global());
            } else {
                throw new UnsupportedOperationException("Unsupported OS");
            }

            quicksort = linker.downcallHandle(
                    lib.find("quicksort").orElseThrow(),
                    FunctionDescriptor.ofVoid(
                            ValueLayout.ADDRESS,
                            ValueLayout.JAVA_LONG,
                            ValueLayout.ADDRESS
                    )
            );

            // Define comparator function in Java
            MemorySegment comparatorFn = linker.upcallStub(
                    MethodHandles.lookup().findStatic(RustFFMSorter.class, "compareDoubles",
                            MethodType.methodType(int.class, double.class, double.class)),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE),
                    arena
            );


            quicksort.invoke(arraySegment, (long) values.length, comparatorFn);

            // Read back and print sorted values
            for (int i = 0; i < values.length; i++) {
                System.out.print(arraySegment.getAtIndex(ValueLayout.JAVA_DOUBLE, i) + " ");
            }
            System.out.println();
        }
    }

    // Must match the function signature in Rust
    public static int compareDoubles(double a, double b) {
        return Double.compare(a, b);
    }

    public void test() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(4);           // allocate 4 bytes off-heap
            seg.set(ValueLayout.JAVA_INT, 0, 42);            // write an int value
            int value = seg.get(ValueLayout.JAVA_INT, 0);    // read the int back
        }
    }
}
