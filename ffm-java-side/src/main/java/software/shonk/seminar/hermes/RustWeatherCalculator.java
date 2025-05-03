package software.shonk.seminar.hermes;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.util.concurrent.ThreadLocalRandom;

public class RustWeatherCalculator {
    static final GroupLayout DATA_POINT_LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_FLOAT.withName("temperature"),
            ValueLayout.JAVA_FLOAT.withName("humidity"),
            ValueLayout.JAVA_FLOAT.withName("wind_speed")
    );

    static final GroupLayout REGION_LAYOUT = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("city_name"),
            ValueLayout.ADDRESS.withName("data_points"),
            ValueLayout.JAVA_LONG.withName("data_points_len")
    );

    static final GroupLayout REGION_SEQUENCE_LAYOUT = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("regions"),
            ValueLayout.JAVA_LONG.withName("regions_len")
    );

    public static void main(String[] args) throws Throwable {
        Linker linker = Linker.nativeLinker();
        SymbolLookup lib;
        if (System.getProperty("os.name").toLowerCase().contains("linux")) {
            lib = SymbolLookup.libraryLookup("../libquicksort/target/release/libffmexample.so", Arena.global());
        } else if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            lib = SymbolLookup.libraryLookup("../libquicksort/target/release/libffmexample.dylib", Arena.global());
        } else {
            throw new UnsupportedOperationException("Unsupported OS");
        }

        MethodHandle findWarmestRegion = linker.downcallHandle(
                lib.find("find_warmest_region").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        try (Arena arena = Arena.ofConfined()) {
            String[] cities = {"Braunschweig", "Hannover", "Hamburg"};

            int pointsPerCity = 5;

            MemoryLayout regionArrayLayout = MemoryLayout.sequenceLayout(cities.length, REGION_LAYOUT);
            MemorySegment regionArray = arena.allocate(regionArrayLayout);
            for (int i = 0; i < cities.length; i++) {
                MemorySegment dataPoints = generateRandomDataPoints(arena, pointsPerCity);
                MemorySegment cityName = arena.allocateFrom(cities[i]);
                MemorySegment region = arena.allocate(REGION_LAYOUT);

                VarHandle cityNameHandle = REGION_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("city_name"));
                VarHandle dataPointsHandle = REGION_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("data_points"));
                VarHandle dataPointsLenHandle = REGION_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("data_points_len"));

                cityNameHandle.set(region, 0L, cityName);
                dataPointsHandle.set(region, 0L, dataPoints);
                dataPointsLenHandle.set(region, 0L, pointsPerCity);

                regionArray.asSlice(i * REGION_LAYOUT.byteSize()).copyFrom(region);
            }

            // Write the regions to a region sequence
            MemorySegment regionSequence = arena.allocate(REGION_SEQUENCE_LAYOUT);
            VarHandle regionsHandle = REGION_SEQUENCE_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("regions"));
            VarHandle regionsLenHandle = REGION_SEQUENCE_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("regions_len"));
            regionsHandle.set(regionSequence, 0L, regionArray);
            regionsLenHandle.set(regionSequence, 0L, cities.length);

            // Call the Rust function
            MemorySegment warmestRegion = (MemorySegment) findWarmestRegion.invoke(regionSequence);

            // Read the result, for that we have to reinterpret the segment as a REGION_LAYOUT
            MemorySegment warmestRegionReinterpreted = warmestRegion.reinterpret(REGION_LAYOUT.byteSize());
            VarHandle cityNameHandle = REGION_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("city_name"));

            // print the city name
            MemorySegment cityNameSegment = (MemorySegment) cityNameHandle.get(warmestRegionReinterpreted, 0L);
            String cityName = cityNameSegment.reinterpret(Long.MAX_VALUE).getString(0);
            System.out.println("Warmest region: " + cityName);
        }
    }

    static MemorySegment generateRandomDataPoints(Arena arena, int count) {
        MemorySegment dpArray = arena.allocate(DATA_POINT_LAYOUT.byteSize() * count);

        VarHandle tempHandle = DATA_POINT_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("temperature"));
        VarHandle humHandle = DATA_POINT_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("humidity"));
        VarHandle windHandle = DATA_POINT_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("wind_speed"));

        for (int i = 0; i < count; i++) {
            MemorySegment dp = dpArray.asSlice(i * DATA_POINT_LAYOUT.byteSize());
            float temp = (float) ThreadLocalRandom.current().nextDouble(-20.0, 45.0);
            float humidity = (float) ThreadLocalRandom.current().nextDouble(20.0, 90.0);
            float wind = (float) ThreadLocalRandom.current().nextDouble(0.0, 10.0);

            tempHandle.set(dp, 0L, temp);
            humHandle.set(dp, 0L, humidity);
            windHandle.set(dp, 0L, wind);
        }
        return dpArray;
    }
}
