# Seminar

## Running the example
To run the example you will need the following:
- A working Rust installation 
- A working Java 24 JDK

You can simply run `build-and-run.sh` in the project root

Alternatively, you can do the following steps manually:

### Compile Rust
in the `libquicksort` directory run:
```bash
cargo build --release
```

### Compile & Run Java
in the `ffm-java-side` directory run:
```bash
mvn clean exec:java
```
