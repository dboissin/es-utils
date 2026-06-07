# es-utils

## Description

A collection of tools for working with Elasticsearch.

## Run the Rust Diff Index Tool
Run the Rust tool to diff the two indices:
```bash
cargo run --bin es-utils -- mon_index_a mon_index_b
```
**Output:** You should see output describing the 150 differences (50 additions, 50 deletions, and 50 modifications).

## Generate Java Record Models from Mappings
The workspace includes a command-line tool `es_to_java` to automatically generate Spring Data Elasticsearch compatible Java 21 `record` structures from any existing index mapping.

It supports:
- Recursive generation of nested static record structures for inner objects.
- Explicit list/array mapping for primitives or object fields.
- Mapping of modern ES types like `flattened` (`Map<String, String>`) and `dense_vector` (`List<Float>`).
- Dynamic mapping fallbacks for untyped objects (`Map<String, Object>`).
- Automatic Java reserved keyword escaping (e.g. `_class` -> `clazz`).

Run the tool using the following command:
```bash
# Basic usage (prints to stdout)
cargo run --bin es_to_java -- mon_index_a

# Advanced usage (with list mappings, custom package, and direct output writing)
cargo run --bin es_to_java -- mon_index_a \
  --package dev.ceven.testbench.model \
  --lists tags,categories \
  --output test-bench/spring-app/src/main/java/dev/ceven/testbench/model/GeneratedRecord.java
```

**Options**:
- `<index_name>`: Name of the Elasticsearch index to fetch the mapping from (required).
- `--package <pkg_name>`: Package name for the generated Java file (optional, defaults to `com.example.model`).
- `--lists` or `--arrays` `<fields>`: Comma-separated list of fields that represent arrays, forcing the tool to wrap them in `java.util.List<...>` (optional).
- `--output <file_path>`: Destination path to write the Java class file (optional, prints to console if omitted).


## Cross-Compiling for Windows 10

Since the Rust client is configured to use pure-Rust TLS (`rustls`), it has no C/C++ system library dependencies (like OpenSSL). This makes cross-compiling the Rust binaries from Linux to Windows 10 extremely straightforward.

You can use two different approaches depending on whether you target the GNU toolchain (MinGW) or the MSVC toolchain (Microsoft):

### Approach A: Using MinGW (GNU target - Recommended for simplicity)
This compiles the application with the MinGW-w64 toolchain.

1. **Install the Windows GNU Target**:
   ```bash
   rustup target add x86_64-pc-windows-gnu
   ```
2. **Install the MinGW toolchain** (Ubuntu/Debian):
   ```bash
   sudo apt-get update
   sudo apt-get install -y mingw-w64
   ```
3. **Build the Binaries**:
   ```bash
   # Build all binaries (the main diff tool and the es_to_java generator)
   cargo build --release --target x86_64-pc-windows-gnu
   ```
   The compiled `.exe` files will be located in `target/x86_64-pc-windows-gnu/release/`.

---

### Approach B: Using MSVC (Native Windows target)
To link against the official MSVC runtime directly from Linux, you can use the `cargo-xwin` helper.

1. **Install LLVM & Rust Target**:
   ```bash
   sudo apt-get update
   sudo apt-get install -y clang lld
   rustup target add x86_64-pc-windows-msvc
   ```
2. **Install cargo-xwin**:
   ```bash
   cargo install cargo-xwin --locked
   ```
3. **Build the Binaries**:
   ```bash
   # Build all binaries
   cargo xwin build --release --target x86_64-pc-windows-msvc
   ```
   The compiled `.exe` files will be located in `target/x86_64-pc-windows-msvc/release/`.

