package software.shonk.seminar.hermes;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;

public class RustWeatherCalculator {
    /** DataPoint record that represents a single datapoint including temperature, humidity and wind speed.
     * <pre>
     * {@code
     * #[repr(C)]
     * pub struct DataPoint {
     *     pub temperature: f32,
     *     pub humidity: f32,
     *     pub wind_speed: f32,
     * }
     * }
     * </pre>
     *
     * Contains a writeToSegment method,
     * a MemoryLayout representation and VarHandles for each field.
     * Corresponds to this Rust struct:
     * @param temperature
     * @param humidity
     * @param wind_speed
     **/
    record DataPoint(float temperature, float humidity, float wind_speed) {
        /** FFM API MemoryLayout (StructLayout) corresponding to this record. */
        static GroupLayout MEMORY_LAYOUT = MemoryLayout.structLayout(
                ValueLayout.JAVA_FLOAT.withName("temperature"),
                ValueLayout.JAVA_FLOAT.withName("humidity"),
                ValueLayout.JAVA_FLOAT.withName("wind_speed")
        );

        /** VarHandle, that enables access to the temperature field in the MEMORY_LAYOUT for this record. */
        static final VarHandle TEMPERATURE_HANDLE = MEMORY_LAYOUT.varHandle(
                MemoryLayout.PathElement.groupElement("temperature")
        );
        /** VarHandle, that enables access to the humidity field in the MEMORY_LAYOUT for this record. */
        static final VarHandle HUMIDITY_HANDLE = MEMORY_LAYOUT.varHandle(
                MemoryLayout.PathElement.groupElement("humidity")
        );
        /** VarHandle, that enables access to the wind_speed field in the MEMORY_LAYOUT for this record. */
        static final VarHandle WIND_SPEED_HANDLE = MEMORY_LAYOUT.varHandle(
                MemoryLayout.PathElement.groupElement("wind_speed")
        );

        /** Writes the given datapoint to a MemorySegment using the static MEMORY_LAYOUT at an offset.
         * @param segment MemorySegment to write to
         * @param offset offset to write at
         */
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

        static final VarHandle CITY_NAME_HANDLE = MEMORY_LAYOUT.varHandle(
                MemoryLayout.PathElement.groupElement("city_name")
        );
        static final VarHandle DATA_POINTS_HANDLE = MEMORY_LAYOUT.varHandle(
                MemoryLayout.PathElement.groupElement("data_points")
        );
        static final VarHandle DATA_POINTS_LEN_HANDLE = MEMORY_LAYOUT.varHandle(
                MemoryLayout.PathElement.groupElement("data_points_len")
        );


        /** Allocates memory inside the given arena for the data point array and writes the array to it.
         * @param arena to allocate in
         * @return MemorySegment the array has been written to (similar to a pointer to the array being returned)
         */
        public MemorySegment allocateDatapoints(Arena arena) {
            // Allocates space for a SequenceLayout with the length of dataPoints.length inside the arena
            MemorySegment segment = arena.allocate(MemoryLayout.sequenceLayout(
                    this.dataPoints.length, // dataPoints.length times
                    DataPoint.MEMORY_LAYOUT // the size of a single data point
            ));

            for (int i = 0; i < this.dataPoints.length; i++) {
                // Get slice of the allocated segment; size: 1 data point; position: data point * i
                MemorySegment datapointSegment = segment.asSlice(
                        i * DataPoint.MEMORY_LAYOUT.byteSize(),
                        DataPoint.MEMORY_LAYOUT.byteSize()
                );

                // Call previously defined writeToSegment of datapoint with the segment slice
                this.dataPoints[i].writeToSegment(datapointSegment, 0);
            }

            return segment;
        }


        /**  Allocates memory inside the given arena for the region name and writes the name to it.
         * @param arena to allocate in
         * @return MemorySegment the name has been written to (similar to a pointer to the string being returned)
         */
        public MemorySegment allocateName(Arena arena) {
            return arena.allocateFrom(this.cityName);
        }

        /** Writes the given region to a MemorySegment using the static MEMORY_LAYOUT at an offset.
         * @param segment MemorySegment to write to
         * @param cityNameSegment allocated MemorySegment that the city name is already written to (see allocateName)
         * @param dataPointsSegment allocated MemorySegment that the datapoints are already written to (see allocateDataPoints)
         * @param offset offset to write to
         */
        public void writeToSegment(
                MemorySegment segment,
                MemorySegment cityNameSegment,
                MemorySegment dataPointsSegment,
                long offset
        ) {
            CITY_NAME_HANDLE.set(segment, offset, cityNameSegment);
            DATA_POINTS_HANDLE.set(segment, offset, dataPointsSegment);
            DATA_POINTS_LEN_HANDLE.set(segment, offset, dataPoints.length);
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
                MemorySegment regionSegment = arraySegment.asSlice(
                        i * Region.MEMORY_LAYOUT.byteSize(),
                        Region.MEMORY_LAYOUT.byteSize()
                );
                Region region = this.regions[i];

                MemorySegment datapointSegment = region.allocateDatapoints(arena);
                MemorySegment nameSegment = region.allocateName(arena);

                this.regions[i].writeToSegment(regionSegment, nameSegment, datapointSegment, 0L);
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
        System.out.println(getWarmestRegion(
                createRegions()
        ));
    }

    /** Uses shared library to sort given regions according to temperature (warmest to lowest).
     * <br/><br/>
     * Looks up the function find_warmest_region in the shared library, writes the given regions to foreign
     * memory and invokes the foreign function. Returns the name of the first region in memory.
     * @param regions regions to sort
     * @return name of the warmest region
     * @throws Throwable when invoking the foreign function fails
     */
    private static String getWarmestRegion(Region... regions) throws Throwable {
        Linker linker = Linker.nativeLinker();
        SymbolLookup lib = lookupLibrary("../libffmexample/target/release/libffmexample");

        MethodHandle findWarmestRegion = linker.downcallHandle(
                lib.find("find_warmest_region").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        // create confined arena
        try (Arena arena = Arena.ofConfined()) {
            RegionSequence regionSequence = new RegionSequence(regions);

            // write all regions to foreign memory
            MemorySegment regionArray = regionSequence.writeToMemory(arena);

            // Call Rust
            MemorySegment regionSegment = (MemorySegment) findWarmestRegion.invoke(regionArray);

            // Reinterpret returned Segment as size of a single MemoryLayout (because we only need the first item)
            MemorySegment warmestRegion = regionSegment.reinterpret(Region.MEMORY_LAYOUT.byteSize());
            // Get the city name using the VarHandle
            MemorySegment cityNameSegment = (MemorySegment) Region.CITY_NAME_HANDLE.get(warmestRegion, 0L);
            // return the string
            return cityNameSegment.reinterpret(Long.MAX_VALUE).getString(0);
        }
    }


    /**
     * @return Regions with dummy data for testing purposes
     */
    private static Region[] createRegions() {
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

        return new Region[] {
                oldenburg,
                hannover,
                braunschweig,
        };
    }


    /**
     * looks up the library depending on host OS
     * @param basePath path to the library without the file ending
     * @return loaded library
     */
    private static SymbolLookup lookupLibrary(String basePath) {
        String os = System.getProperty("os.name").toLowerCase();
        String suffix = os.contains("mac") ? ".dylib" : os.contains("linux") ? ".so" : null;

        if (suffix == null) {
            throw new UnsupportedOperationException("Unsupported OS");
        }

        return SymbolLookup.libraryLookup(basePath + suffix, Arena.global());
    }
}
