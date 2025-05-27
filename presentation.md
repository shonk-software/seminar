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

<!--
Explain the code...
- Confined Arena is created
- Memory Segment with 4 bytes of space is allocated
- We write an int into the segment
- We retrieve the int from the segment
- try() Block ends, memory deallocated yippie
-->

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

<!--
- Slice acts as a view or window
- Original segment is still accessible in its entirety
- Slices are new segments, same lifetime scope only different base pointer and length
- Read only - selbsterklärend, no?
- Read only - Why would you even need it? -> for example passing Segments to other code and sleeping safe knowing nothing can be modified
-->

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

<!--
- Beispiel erklären, kurz !!
-->

---

# Memory Segments
## Native interop
- On-heap segments cannot be passed to native code
- MemorySegments wrap pointers returned from native code

<!--
- On-heap segments cannot be passed to native code
- MemorySegments are used to wrap pointers returned from native code
  - You need to know what the functions return to properly work with the returned pointer / Segment
-->

---
# Memory Segments
## Native interop - Function pointers
- Zero-length MemorySegment
  - Can be passed to native code accepting function pointers
  - MethodHandle to call

<!--
- MemorySegments also wrap function pointers so that they can be passed to native code.
- They also wrap function pointers to native code, which can be turned into a callable MethodHandle.
-->

---

# Memory Segments
## Native interop - Zero-length MemorySegments
- Have to be reinterpreted

<!-- Example from our demo -->
```java
cityNameSegment.reinterpret(Long.MAX_VALUE).getString(0)
```

<!--
- As the length / size of pointers that get returned from native code is unknown (like the length of a string) they get wrapped in a MemorySegment of length 0.
- They have to be reinterpreted into a new MemorySegment with a different size to be accessed, this is an unsafe operation as you could theoretically now try to access out of bounds memory which can crash the JVM or corrupt memory.
- Reinterpret is one of the restricted methods, access to which has to be explicitely enabled.
-->

---

# Memory Segments - Summary
- Unified type-safe abstraction over anything pointer-like
- Spatial and temporal guarantees
- Ergonomic handling via Arenas
- Allows slicing and read-only access

<!--
- All in all MemorySegments provide a unified type-safe abstraction over pointers while giving certain spatial and temporal guarantees.
- They provide ergonomic handling via arenas, allowing the developers to make trade-offs between aspects like ease of use or speed
-->

---

# Memory Layouts
<!--
header: MemoryLayouts
-->

<!--
Da jetzt bekannt ist, wie mit MemorySegments Speicher allocated, gelesen und manipuliert werden kann ->  
MemoryLayouts zum Beschreiben der Struktur des Speichers.

- Speicher selten einfach nur ein Int, wie in den Folien bisher gezeigt, sondern komplizierter
- MemoryLayouts erlauben es Struktur, inklusive
    - Größe
    - Alignment
    - Anordnung von Objekten
- zu beschreiben -> einfacheren, strukturierten Zugriff auf den Speicher.
- sind nur eine Karte für MemorySegments! MemorySegments an sich immer noch nötig
  - Hitchhikers Guide to the Data
- mehrere Subtypen:
-->

Describe the memory's structure:
- size
- alignment
- data arrangement within a segment

-> Allow easy access to structured memory 

---
<!--
Zuerst - ValueLayouts:
- eine der einfachsten MemoryLayouts
- beschreibt Struktur von simplen Datentypen, wie Integern und Doubles (oder auch Adressen)

- JAVA_INT beschreibt also Größe und Alignment von einem Java int (4 bytes groß, 4 bytes aligned)

- um deutlich zu machen, dass es sich hier um ValueLayouts handelt, haben wir bisher das 'ValueLayout.' immer stehen gelassen.
(SWITCH)
-->

# ValueLayout
Describes memory of basic data types:
```java
MemoryLayout integerLayout = ValueLayout.JAVA_INT;


MemoryLayout doubleLayout = ValueLayout.JAVA_DOUBLE;
```

---

# ValueLayout

<!-- 
In den folgenden Folien, werden wir aber so tun als würden wir die ValueLayouts direkt importen, um die Folien ein bisschen lesbarer zu halten

kurzer Einschub, bevor wir über andere Typen von MemoryLayouts reden:
Wie kann man MemoryLayouts benutzen?
-->
Describes memory of basic data types:
```java
MemoryLayout integerLayout = JAVA_INT;


MemoryLayout doubleLayout = JAVA_DOUBLE;
```

---
# Memory Allocation with MemoryLayouts
<!--
Hier ein Beispiel, was mit einem MemoryLayout ein MemorySegment allocated.
-> sehr ähnlich zu schon gezeigtem Code.

Allocated automatisch Speicher für einen Java Integer im Segment
-->
```java
try (Arena arena = Arena.ofConfined()) {

    ...

    MemorySegment segment = arena.allocate(
        JAVA_INT, 0
    );

    ...
}
```

---

# PaddingLayout
<!--
Der nächste, relativ simple, subtyp von MemoryLayouts:
PaddingLayout

- Beschreibt simples padding, also ungenutzten Speicher
- Vor allem als Sublayout in den folgenden, komplizierteren MemoryLayouts gebraucht
-->
Describes padding of `n` bytes:
```java
MemoryLayout padding = MemoryLayout.paddingLayout(3);
```

---

# StructLayout
<!--
Nun zu komplizierteren MemoryLayouts:
StructLayouts

Name sagt es schon: beschreiben Speicher, wie er für ein C struct genutzt wird.

Hier beispiel C struct: ein Item mit id und value

Darunter Java Beispiel Code um Speicher dafür zu allocaten, ohne StructLayouts zu benutzen:
- benötigte Größe bestimmen
- entsprechendes Alignment bestimmen
- manuelles allocaten
-->

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
<!--
Hier jetzt das gleiche C struct und Beispiel Java Code zum allocaten des Speichers hierfür mit dem StructLayout

- bessere Lesbarkeit, starke Ähnlichkeit zum C Code -> Maintainability
- withName später noch genauer, aktuell auch einfach Lesbarkeit
- allocate nimmt dann nur noch das structLayout, alignment ist automatisch bekannt

Wichtig zu erwähnen: StructLayouts achten nicht automatisch auf alignment und wenn Padding benötigt wird, muss das auf der FFM Seite mit PaddingLayout manuell realisiert werden.
-->

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
<!--
Ein sehr ähnlich aussehendes MemoryLayout sind UnionLayouts:
genau so wie beim StructLayout entspricht das UnionLayout seinem C äquivalent: der Union (hier oben)

darunter sieht man, wie gerade eben, wie das UnionLayout in Java funktioniert.
-->
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
<!--
Da jetzt bekannt ist wie C Structs und Unions mithilfe der FFM API in Speicher modelliert werden können, fehlen jetzt nur noch:
Arrays

Um Objekte zu modellieren, die sequentiell im Speicher liegen, bietet die FFM API SequenceLayouts

In dem gezeigten Beispiel, wird der Speicher so modelliert, dass vier longs gespeichert werden können.
Das lässt sich einerseits mit StructLayouts realisieren
Andererseits mit dem SequenceLayout wesentlich simpler (100x longs)
-->
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
# Referencing Memory with VarHandles
<!--
Da jetzt alle signifikanten MemoryLayouts bekannt sind, kann angefangen werden damit ordentlich zu arbeiten.
Das Allocaten von Speicher mithilfe der Layouts haben wir schon gezeigt, aber wie greift man dann darauf zu?

Hier ist ein Beispiel-Layout gegeben, das auch in den nachfolgenden Folien gleich bleiben wird:
Ein StructLayout mit einem x und einem y Wert -> Koordinate
davon 4 in einer Sequence

Unten:
- Referenz auf Variable x des ersten Elements der Sequenz
- VarHandle auf das Layout x

-->
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
VarHandle xHandle = points.varHandle(
    PathElement.sequenceElement(0),
    PathElement.groupElement("x")
);
```

---
<!--
- Was kann man mit einem VarHandle machen?
- oben jetzt die Definition des VarHandles, die gerade unten stand
- `.get` und `.set` zum Lesen und Schreiben von Speicher mit dem VarHandle
  - Argument: MemorySegment, das als Inhalt Daten im Layout unseres originalen SequenceLayouts mit 4 Koordinaten hat 
-->

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