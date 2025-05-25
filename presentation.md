---
marp: true
---
<!--
title: "Reading and Writing (structured) Memory using Java's Foreign Function & Memory API"
author: "Nikki Tschierske, Jakob Jungherr"
footer: "Reading and Writing (structured) Memory using Java's Foreign Function & Memory API" # TODO
paginate: true
-->

TITLE PAGE

---

# FFM Technical Aspects introduction here?



---

# Memory Segments

Provide:
- A unified type-safe abstraction over on- and off-heap memory
- Spatial and temporal guarantees

<!--
Memory Segments provide a unified, type-safe abstraction over on- and off-heap memory.
They also provide Spatial and temporal guarantees, which I will explain soon.
-->

---

# Memory Segments

Combination of (base) pointer & length

Wrap:
- On-heap
- Off-heap
- Function pointers

Basically anything pointer

<!--
They're fat pointers that wrap on-heap memory, off-heap memory and function pointers.
That means that they're made up of a base pointer & length, plus some additional metadata like lifetime scope, Thread access control etc.
They provide spatial safety via the stored length, reads and writes are checked for out of bounds access like arrays.
-->

---

# Memory Segments
## Arena
- Implementor of `SegmentAllocator`
- Controls lifecycle of segments
  - Global / Automatic / Shared / Confined

<!--
MemorySegments can be allocated by a SegmentAllocator.
Arena is one of these (basically the main) implementor of the SegmentAllocator interface and controls the lifecycle of MemorySegments allocated within it.
There are different types of Arenas:
- Global (which lives as long as the entire application and is accessible from everywhere. Memory allocated within it is never deallocated.)
- Automatic (which lives as long as it and any MemorySegment allocated inside it is deemed reachable by the Garbage Collector)
- Shared (simple Arena which can be shared between Threads and closed manually by any Thread)
- Confined (an Arena that cannot be shared between threads and is usually used in combination with a try-with-resources)
-->
---

# Memory Segments
## Arena - Confined

```java
try (Arena arena = Arena.ofConfined()) {
    MemorySegment seg = arena.allocate(4);
    seg.set(ValueLayout.JAVA_INT, 0, 42);
    int value = seg.get(ValueLayout.JAVA_INT, 0);
}
```

---

# Memory Segments
## Arena - Shared

```java
Arena arena = Arena.ofShared();
MemorySegment seg = arena.allocate(4);
seg.set(ValueLayout.JAVA_INT, 0, 42);
int value = seg.get(ValueLayout.JAVA_INT, 0);
arena.close(); // explicit close; deallocates memory for all threads
// Closing a shared arena is however an expensive operation
```

<!--
Closing a shared arena is expensive as it involves synchronization 
-->

---

# Memory Segments
## Slices & Read-only
- MemorySegments can be sliced
- MemorySegments can be made read-only

---

## Memory Segments
## Slices / Views & Read-only: Code example

```java
try (Arena a = Arena.ofConfined()) {
    MemorySegment s  = a.allocate(12);            // 3 ints
    s.set(ValueLayout.JAVA_INT, 4, 20);           // Middle int, byte offset
    MemorySegment ro = s.asSlice(4, 4).asReadOnly();   // slice + RO
    System.out.println(ro.get(ValueLayout.JAVA_INT, 0)); // 20
    ro.set(ValueLayout.JAVA_INT, 0, 99);  // throws (read-only)
}
```

---

# Memory Segments
## Native interop
- On-heap segments cannot be passed to native code
- MemorySegments wrap pointers returned from native code

---
# Memory Segments
## Native interop - Function pointers
- Zero-length MemorySegment
  - Can be passed to native code accepting function pointers
  - MethodHandle to call

---

# Memory Segments
## Native interop - Zero-length MemorySegments
- Have to be reinterpreted

<!-- Example from our demo -->
```java
cityNameSegment.reinterpret(Long.MAX_VALUE).getString(0)
```

---

# Memory Segments - Summary
- Unified type-safe abstraction over anything pointer-like
- Spatial and temporal guarantees
- Ergonomic handling via Arenas
- Allows slicing and read-only access

---

# Memory Layouts
<!--
header: MemoryLayouts
-->

Describe the memory's structure:
- size
- alignment
- data arrangement within a segment

-> Allow safe memory access 

---

# ValueLayout
```java
MemoryLayout integerLayout = ValueLayout.JAVA_INT;

MemoryLayout doubleLayout = ValueLayout.JAVA_DOUBLE;
```

---

# ValueLayout

```java
MemoryLayout integerLayout = JAVA_INT;

MemoryLayout doubleLayout = JAVA_DOUBLE;
```

---

# PaddingLayout

```java
MemoryLayout padding = MemoryLayout.paddingLayout(3);
```


---
# Memory Allocation with MemoryLayouts

```java
try (Arena arena = Arena.ofConfined()) {

    ...

    MemorySegment segment = arena.allocate(
        JAVA_INT, 3
    );

    ...
}
```

---

# StructLayout

C:
```c
struct Item {
    int32_t id;
    float value;
};
```

Java:
```java
var size = JAVA_INT.byteSize() + JAVA_FLOAT.byteSize();
var alignment = JAVA_INT.byteAlignment();
MemorySegment segment = Arena.ofAuto().allocate(size, alignment);
```

---

# StructLayout cont'd

C:
```c
struct Item {
    int32_t id;
    float value;
};
```

Java:
```java
MemoryLayout structLayout = MemoryLayout.structLayout(
    JAVA_INT.withName("id"),
    JAVA_FLOAT.withName("value")
);
Arena.ofAuto().allocate(structLayout);
```

---

# UnionLayout
C:
```c
union number {
  int32_t integer;
  double not_integer;
};
```
Java:
```java
MemoryLayout unionLayout = MemoryLayout.unionLayout(
    JAVA_INT.withName("integer"),
    JAVA_FLOAT.withName("not_integer")
);
```

---
# SequenceLayout
As a `StructLayout`:
```java
MemoryLayout sequence = MemoryLayout.structLayout(
    JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG
);
```
As a `SequenceLayout`:
```java
MemoryLayout sequence = MemoryLayout.sequenceLayout(4, JAVA_LONG);
```

---
# Accessing Memory with MemoryLayouts
Given a `MemoryLayout`:
```java
MemoryLayout points = MemoryLayout.sequenceLayout(
    4,
    MemoryLayout.structLayout(
        ValueLayout.JAVA_INT.withName("x"),
        ValueLayout.JAVA_INT.withName("y")
    )
);
```

Referencing the first `StructLayout` in the sequence:
```java
MemoryLayout point0 = points.select(
    PathElement.sequenceElement(0)
);
```

---
# Accessing Memory with MemoryLayouts
Given a `MemoryLayout`:
```java
MemoryLayout points = MemoryLayout.sequenceLayout( 
    4,
    MemoryLayout.structLayout(
        ValueLayout.JAVA_INT.withName("x"),
        ValueLayout.JAVA_INT.withName("y")
    )
);
```

Referencing the first `StructLayout`'s `x`:
```java
MemoryLayout xValue = points.select(
    PathElement.sequenceElement(0), PathElement.groupElement("x")
);
```

---
# Referencing Memory with VarHandles
Given a `MemoryLayout`:
```java
MemoryLayout points = MemoryLayout.sequenceLayout( 
    4,
    MemoryLayout.structLayout(
        ValueLayout.JAVA_INT.withName("x"),
        ValueLayout.JAVA_INT.withName("y")
    )
);
```

Accessing the first `StructLayout`'s `x`:
```java
VarHandle xHandle = points.varHandle(
    PathElement.sequenceElement(0),
    PathElement.groupElement("x")
);
```

---

# Accessing Memory with VarHandles
Given the `VarHandle` from before:
```java
VarHandle xHandle = points.varHandle(
    PathElement.sequenceElement(0), 
    PathElement.groupElement("x")
);
```
Assuming a given `MemorySegment`:
```java
int oldValue = (int) xHandle.get(memorySegment, 0);

int newValue = 3;
xHandle.set(memorySegment, 0, newValue)
```