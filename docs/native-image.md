# Native Executable (GraalVM)

## Goal

Generate a standalone binary from the project, without depending on the JVM at
runtime. The binary does not require a Java installed on the target machine, starts
faster and uses less memory than running via `java -jar`.

## Requirements

- **GraalVM** (Community or Oracle) with `native-image`, as `JAVA_HOME` (or on the
  `PATH`). The project uses Java 21, so a GraalVM for JDK 21 is the natural match.
- **C toolchain**: a `gcc` compiler and the zlib headers/library.
  - Debian/Ubuntu: `sudo apt install build-essential zlib1g-dev`
  - Fedora: `sudo dnf install gcc zlib-devel`
  - macOS: `xcode-select --install`

## Usage

```
mvn -Pnative package
```

The output is `target/furigana-parser`, an ordinary executable. The command line is
the same as the JAR version:

```
./target/furigana-parser input.srt output.json
./target/furigana-parser input.srt output.ass
./target/furigana-parser --embed-font --subset-font input.srt output.ass
./target/furigana-parser --help
```

The `native` profile does two things:

1. **Disables the `maven-shade-plugin`.** The uber jar would be handed to
   `native-image` *in addition* to the dependency jars, duplicating classes and,
   what really weighs, the resources — the Kuromoji dictionary (~33 MB) would be
   included twice, inflating the binary by about 35 MB. In the native build,
   `native-image` reads `target/classes` + the dependencies. As a consequence, this
   profile generates only the "thin" jar (not the uber jar).
2. **Runs the `native-maven-plugin`** (`native:compile-no-fork`) in the `package`
   phase.

## native-image configuration

The configurations live in
`src/main/resources/META-INF/native-image/com.shinji2324/furigana-parser/` and are
discovered automatically (both by Maven and by a manual `native-image -jar`, since
they are also inside the jar):

| File | What for |
| --- | --- |
| `reflect-config.json` | Reflection: the `FuriganaDocument` / `FuriganaEntry` / `FuriganaSegment` model (serialized by Jackson), JNA classes and Jisho's TLS/HTTP provider. |
| `resource-config.json` | Resources read at runtime: the Kuromoji dictionary `.bin` files, `ass/default-style.ass` and JNA's `libjnidispatch`. |
| `jni-config.json` | JNI access used by JNA. |
| `proxy-config.json` | Dynamic proxy of the `HarfBuzzSubsetLibrary$HarfBuzz` JNA interface. |
| `native-image.properties` | Fixed arguments, today `--no-fallback`. |

Why each one is needed:

- **Kuromoji** loads the IPADIC dictionary as a classpath resource;
- **Jackson** serializes the records by reflection (`JsonWriter`);
- **JNA** loads `libharfbuzz-subset` dynamically, extracts `libjnidispatch` from the
  jar itself at runtime and uses JNI.

The `jnidispatch` resource pattern in `resource-config.json` covers all platforms
(`com/sun/jna/.*jnidispatch\.(so|dylib|jnilib|dll)`), so that the same file works on
Linux, macOS and Windows.

### Regenerating the configurations

If the model or the dependencies change (new reflective serialization, new resource
read from the classpath), regenerate with the native-image *tracing agent*:

```
GRAALVM=/path/to/graalvm
CFG=src/main/resources/META-INF/native-image/com.shinji2324/furigana-parser

# 1) JSON route (Jackson) — config-output-dir overwrites
"$GRAALVM/bin/java" -agentlib:native-image-agent=config-output-dir=$CFG \
  -jar target/furigana-parser-0.1.0-SNAPSHOT.jar input.srt output.json

# 2) ASS + subset route (JNA) — config-merge-dir accumulates
"$GRAALVM/bin/java" -agentlib:native-image-agent=config-merge-dir=$CFG \
  -jar target/furigana-parser-0.1.0-SNAPSHOT.jar \
  --embed-font --subset-font input.srt output.ass
```

Use `config-output-dir` on the first run and `config-merge-dir` on the following
ones (or separate directories, merging afterwards). When you finish, check that the
multiplatform `jnidispatch` pattern is still in `resource-config.json`, because the
agent writes the specific path of the platform on which it ran.

## Measured results

In the development environment (Linux x86-64, GraalVM CE 21.0.2, two-subtitle
input):

| | Native binary | `java -jar` |
| --- | --- | --- |
| Startup | ~0.75 s | ~1.7 s |
| Memory (RSS) | ~112 MB | ~255 MB |
| Artifact | ~85 MB | ~18 MB (jar) |

The outputs — JSON, ASS and ASS with `--embed-font --subset-font` — are
**identical, byte for byte**, to those of the JVM.

## Limits

- The binary depends, at runtime, on common system libraries (`libz`, `libc`,
  `libpthread`).
- Subsetting (`--subset-font`) still requires `libharfbuzz-subset` installed: the
  native binary does not embed it. Its absence is handled as in the JVM version
  (warning and embedding the full font). On Windows the core and subset DLLs are
  loaded separately and looked up in the PATH and the usual MSYS2/vcpkg
  directories. See
  [`font-embedding.md`](font-embedding.md).
- The native build is not part of the CI on every push (it would require GraalVM
  and a C toolchain on every run). It is generated only in the release workflow, on
  dedicated runners that already bring these components.
