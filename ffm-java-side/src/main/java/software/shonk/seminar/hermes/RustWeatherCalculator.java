package software.shonk.seminar.hermes;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;

public class RustWeatherCalculator {
    record DataPoint(float temperature, float humidity, float wind_speed) {
        static GroupLayout MEMORY_LAYOUT = MemoryLayout.structLayout(
                ValueLayout.JAVA_FLOAT.withName("temperature"),
                ValueLayout.JAVA_FLOAT.withName("humidity"),
                ValueLayout.JAVA_FLOAT.withName("wind_speed")
        );

        static final VarHandle TEMPERATURE_HANDLE = MEMORY_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("temperature"));
        static final VarHandle HUMIDITY_HANDLE = MEMORY_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("humidity"));
        static final VarHandle WIND_SPEED_HANDLE = MEMORY_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("wind_speed"));

        public void writeToSegment(MemorySegment segment, long offset) {
            TEMPERATURE_HANDLE.set(segment, offset, this.temperature);
            HUMIDITY_HANDLE.set(segment, offset, this.humidity);
            WIND_SPEED_HANDLE.set(segment, offset, this.wind_speed);
        }
    }

    record Region(String cityName, DataPoint[] dataPoints) {
        static GroupLayout MEMORY_LAYOUT = MemoryLayout.structLayout(
                ValueLayout.ADDRESS.withName("city_name"),
                ValueLayout.ADDRESS.withName("data_points"),
                ValueLayout.JAVA_LONG.withName("data_points_len")
        );

        static final VarHandle CITY_NAME_HANDLE = MEMORY_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("city_name"));
        static final VarHandle DATA_POINTS_HANDLE = MEMORY_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("data_points"));
        static final VarHandle DATA_POINTS_LEN_HANDLE = MEMORY_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("data_points_len"));

        private MemorySegment allocateDatapoints(Arena arena) {
            MemorySegment segment = arena.allocate(MemoryLayout.sequenceLayout(
                    this.dataPoints.length,
                    DataPoint.MEMORY_LAYOUT
            ));

            for (int i = 0; i < this.dataPoints.length; i++) {
                MemorySegment datapointSegment = segment.asSlice(
                        i * DataPoint.MEMORY_LAYOUT.byteSize(),
                        DataPoint.MEMORY_LAYOUT.byteSize()
                );

                this.dataPoints[i].writeToSegment(datapointSegment, 0);
            }

            return segment;
        }

        public void writeToSegment(MemorySegment segment, MemorySegment dataPointsSegment, MemorySegment cityNameSegment, long offset) {
            CITY_NAME_HANDLE.set(segment, offset, cityNameSegment);
            DATA_POINTS_HANDLE.set(segment, offset, dataPointsSegment);
            DATA_POINTS_LEN_HANDLE.set(segment, offset, dataPoints.length);
        }

        public MemorySegment allocateAndWrite(Arena arena) {
            MemorySegment nameSegment = arena.allocateFrom(this.cityName);
            MemorySegment datapointSegment = allocateDatapoints(arena);
            MemorySegment segment = arena.allocate(MEMORY_LAYOUT);

            this.writeToSegment(segment, datapointSegment, nameSegment, 0L);
            return segment;
        }
    }

    record RegionSequence(Region[] regions) {
        static GroupLayout MEMORY_LAYOUT = MemoryLayout.structLayout(
                ValueLayout.ADDRESS.withName("regions"),
                ValueLayout.JAVA_LONG.withName("regions_len")
        );

        static final VarHandle REGIONS_HANDLE = MEMORY_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("regions"));
        static final VarHandle REGIONS_LEN_HANDLE = MEMORY_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("regions_len"));

        private MemorySegment allocateRegions(Arena arena) {
            MemoryLayout regionsSequenceLayout = MemoryLayout.sequenceLayout(
                    this.regions.length,
                    Region.MEMORY_LAYOUT
            );

            MemorySegment arraySegment = arena.allocate(regionsSequenceLayout);

            for (int i = 0; i < this.regions.length; i++) {
                MemorySegment regionSegment = this.regions[i].allocateAndWrite(arena);
                arraySegment.asSlice(
                        i * Region.MEMORY_LAYOUT.byteSize(),
                        Region.MEMORY_LAYOUT.byteSize()
                ).copyFrom(regionSegment);
            }

            return arraySegment;
        }

        public void writeToSegment(MemorySegment segment, MemorySegment regionsSegment, long offset) {
            REGIONS_HANDLE.set(segment, offset, regionsSegment);
            REGIONS_LEN_HANDLE.set(segment, offset, this.regions.length);
        }

        public MemorySegment writeToMemory(Arena arena) {
            MemorySegment regionsSegment = allocateRegions(arena);
            MemorySegment segment = arena.allocate(MEMORY_LAYOUT);
            this.writeToSegment(segment, regionsSegment, 0L);
            return segment;
        }
    }

    public static void main(String[] args) throws Throwable {
        Linker linker = Linker.nativeLinker();
        SymbolLookup lib = lookupLibrary("../libquicksort/target/release/libffmexample");

        MethodHandle findWarmestRegion = linker.downcallHandle(
                lib.find("find_warmest_region").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment regionArray = createRegions(arena);

            // Call Rust
            MemorySegment warmestRegion = (MemorySegment) findWarmestRegion.invoke(regionArray);

            // Read and print result
            printWarmestRegion(warmestRegion);
        }
    }

    private static MemorySegment createRegions(Arena arena) {
        Region oldenburg = new Region("Oldenburg", new DataPoint[]{
                new DataPoint(50, 10, 10),
                new DataPoint(10, 10, 10),
                new DataPoint(10, 10, 10),
        });

        Region hannover = new Region("Hannover", new DataPoint[]{
                new DataPoint(20, 10, 10),
                new DataPoint(10, 10, 10),
                new DataPoint(10, 10, 10),
        });

        Region braunschweig = new Region("Braunschweig", new DataPoint[]{
                new DataPoint(30, 10, 10),
                new DataPoint(10, 10, 10),
                new DataPoint(10, 10, 10),
        });

        RegionSequence regions = new RegionSequence(
                new Region[] {
                        oldenburg,
                        hannover,
                        braunschweig,
                }
        );

        return regions.writeToMemory(arena);
    }

    private static void printWarmestRegion(MemorySegment regionSegment) {
        MemorySegment warmestRegion = regionSegment.reinterpret(Region.MEMORY_LAYOUT.byteSize());
        MemorySegment cityNameSegment = (MemorySegment) Region.CITY_NAME_HANDLE.get(warmestRegion, 0L);
        String cityName = cityNameSegment.reinterpret(Long.MAX_VALUE).getString(0);
        System.out.println("Warmest region: " + cityName);
    }

    private static SymbolLookup lookupLibrary(String basePath) {
        String os = System.getProperty("os.name").toLowerCase();
        String suffix = os.contains("mac") ? ".dylib" : os.contains("linux") ? ".so" : null;

        if (suffix == null) {
            throw new UnsupportedOperationException("Unsupported OS");
        }

        return SymbolLookup.libraryLookup(basePath + suffix, Arena.global());
    }
}
