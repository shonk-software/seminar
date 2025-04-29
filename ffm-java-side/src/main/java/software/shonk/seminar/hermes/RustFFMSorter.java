package software.shonk.seminar.hermes;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

public class RustFFMSorter {
    static {
        System.loadLibrary("your_rust_library_name"); // Without 'lib' or extension
    }

    static MethodHandle

    public static void main(String[] args) throws Throwable {
        double[] values = {3.1, 2.4, 5.6, 1.9};

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment arraySegment = arena.allocate(ValueLayout.JAVA_DOUBLE, values.length);
            for (int i = 0; i < values.length; i++) {
                arraySegment.setAtIndex(ValueLayout.JAVA_DOUBLE, i, values[i]);
            }

            Linker linker = Linker.nativeLinker();

            SymbolLookup lib = SymbolLookup.libraryLookup("/* path/to/your/FileName.dylib */", Arena.global()); // Loads the Rust library

            yourMethodName = linker.downcallHandle(
                    lib.find("/* function_name */").orElseThrow(), // Replace with the Rust function name
                    FunctionDescriptor.of(
                            ValueLayout.JAVA_INT,                     // Match Java's return type to Rust's return type
                            ValueLayout.JAVA_INT,                     // Match Java's first parameter type to Rust's first parameter type
                            ValueLayout.JAVA_INT                      // Match Java's second parameter type to Rust's second parameter type
                    )
            );



            // Define comparator function in Java
            MemorySegment comparatorFn = linker.upcallStub(
                    MethodHandles.lookup().findStatic(QuicksortFFM.class, "compareDoubles",
                            MethodType.methodType(int.class, double.class, double.class)),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE),
                    arena
            );

            quicksortHandle.invoke(arraySegment, (long) values.length, comparatorFn);

            // Read back and print sorted values
            for (int i = 0; i < values.length; i++) {
                System.out.print(arraySegment.getAtIndex(ValueLayout.JAVA_DOUBLE, i) + " ");
            }
        }
    }

    // Must match the function signature in Rust
    public static int compareDoubles(double a, double b) {
        return Double.compare(a, b);
    }
}
