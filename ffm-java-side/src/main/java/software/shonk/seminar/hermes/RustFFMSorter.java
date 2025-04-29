package software.shonk.seminar.hermes;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class RustFFMSorter {
    static {
        Linker linker = Linker.nativeLinker();

        SymbolLookup lib = SymbolLookup.libraryLookup("../libquicksort/target/release/libquicksort.so", Arena.global()); // Loads the Rust library

        quicksort = linker.downcallHandle(
                lib.find("sum").orElseThrow(),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_DOUBLE,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_LONG
                )
        );
    }

    static MethodHandle quicksort;

    public static void main(String[] args) throws Throwable {
        double[] values = {1.0, 2.0, 3.0, 4.0, 59.0};

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment arraySegment = arena.allocate(ValueLayout.JAVA_DOUBLE, values.length);
            for (int i = 0; i < values.length; i++) {
                arraySegment.setAtIndex(ValueLayout.JAVA_DOUBLE, i, values[i]);
            }




            // Define comparator function in Java
/*            MemorySegment comparatorFn = linker.upcallStub(
                    MethodHandles.lookup().findStatic(RustFFMSorter.class, "compareDoubles",
                            MethodType.methodType(int.class, double.class, double.class)),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE),
                    arena
            );*/

            //quicksort.invoke(arraySegment, (long) values.length, comparatorFn);
            // Karsten
            double sum = (double) quicksort.invokeExact(arraySegment, (long) values.length);
            System.out.println("Sum: " + sum);

            // Read back and print sorted values
            /*for (int i = 0; i < values.length; i++) {
                System.out.print(arraySegment.getAtIndex(ValueLayout.JAVA_DOUBLE, i) + " ");
            }*/
        }
    }

    // Must match the function signature in Rust
    public static int compareDoubles(double a, double b) {
        return Double.compare(a, b);
    }
}
