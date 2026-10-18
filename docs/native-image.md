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

A second binary, `target/furigana-parser-gui`, is the graphical interface. It takes
no arguments: running it opens the window (see [`gui.md`](gui.md)).

```
./target/furigana-parser-gui
```

Unlike the CLI, the GUI image also pulls in AWT, and Native Image emits the
AWT/font JNI libraries next to the executable (`libawt*.so`, `libfontmanager.so`,
the `libjava.so`/`libjvm.so` shims, ... on Linux; `.dylib` on macOS, `.dll` on
Windows). They **must be shipped and run together with the binary**, so the release
publishes the GUI as an archive
(`furigana-parser-gui-<version>-<platform>.tar.gz`, `.zip` on Windows) rather than
as a single file: extract it and run the executable from inside the extracted
directory.

The `native` profile does two things:

1. **Disables the `maven-shade-plugin`** (both the CLI and the GUI executions).
   The uber jar would be handed to `native-image` *in addition* to the dependency
   jars, duplicating classes and, what really weighs, the resources — the Kuromoji
   dictionary (~33 MB) would be included twice, inflating the binary by about 35 MB.
   In the native build, `native-image` reads `target/classes` + the dependencies.
   As a consequence, this profile generates only the "thin" jar (not the uber jar).
2. **Runs the `native-maven-plugin`** (`native:compile-no-fork`) twice, once per
   entry point, in the `package` phase: the CLI (`Application`) and the GUI
   (`GuiMain`). The CLI binary does not reference the GUI, so AWT/Swing stays out
   of it.

### GUI binary (AWT/Swing)

The GUI image is built from the same classes, with `-H:+AddAllCharsets` (non-ASCII
file paths) and `-Djava.awt.headless=false` (links the X11 AWT instead of the
headless one). Three points deserve attention:

- **AWT metadata.** AWT's native library calls back into Java (`System.load`) from
  its `JNI_OnLoad` to load the next library. Without the matching registration
  Native Image aborts at startup with a misleading `Fatal error reported via JNI:
  Could not allocate library name` — which is what the build recommends with
  *"AWT: Use the tracing agent to collect metadata for AWT"*. The metadata is
  collected with the tracing agent and committed under `native-image/gui/`, then
  handed to the GUI build with `-H:ConfigurationFileDirectories`. It is kept out of
  `src/main/resources` so it never reaches the CLI image.
- **The AWT JNI libraries travel with the executable** (see the note in *Usage*
  above); the GUI is distributed as an archive for that reason.
- **Look-and-feel.** `GuiMain` instantiates Nimbus directly and falls back to
  Metal when it cannot initialize. With the metadata above Nimbus works in the
  native image too; the fallback remains as a safety net.
- **`java.home` and the AWT font manager.** A native image does not set the
  `java.home` system property, and AWT's `FontConfiguration` refuses to start
  without it. It also probes `${java.home}/lib` for a `fontconfig.properties` file
  and only enables its system-fontconfig fallback when that directory exists, so a
  `java.home` whose `lib` is missing ends in `RuntimeException: Fontconfig head is
  null`. `GuiMain.prepareNativeImageRuntime()` therefore points `java.home` at a
  throw-away directory holding an empty `lib` directory, before any AWT class is
  touched. On a regular JVM `java.home` is already set and the method does nothing.
- **Fonts and `Desktop`.** Text rendering uses the system fonts (freetype/fontconfig
  on Linux). "Open folder" uses `java.awt.Desktop` when it is supported.

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

The GUI has its own metadata, in `native-image/gui/`:

| File | What for |
| --- | --- |
| `jni-config.json` | The AWT/X11/font JNI callbacks: `java.lang.System.load(String)` (the AWT native library loads the next one through it) plus the font-manager callbacks `GraphicsEnvironment.getLocalGraphicsEnvironment()`, `SunGraphicsEnvironment` and its `isDisplayLocal()`. The tracing agent does not capture the last three, so they are added by hand — keep them when regenerating (see below). |
| `reflect-config.json` | Swing/Nimbus painter classes, key-event fields, the colour chooser. |
| `resource-config.json` | The Basic/Synth look-and-feel bundles and `sun.awt.resources.awt`. |

It sits *outside* `src/main/resources`, so it is not packaged into the jar and does
not reach the CLI image; the `build-native-gui` execution passes it with
`-H:ConfigurationFileDirectories`.

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

The GUI route needs a display and a running window, so it is easier through the
helper image in [`../docker/Dockerfile.trace`](../docker/Dockerfile.trace): it
builds the GUI jar, opens the window under Xvfb with the agent attached and leaves
the result in `/config`.

```
docker build -f docker/Dockerfile.trace -t furigana-parser-trace .
docker run --name furigana-trace furigana-parser-trace
docker cp furigana-trace:/config/reflect-config.json native-image/gui/
docker cp furigana-trace:/config/jni-config.json     native-image/gui/
docker cp furigana-trace:/config/resource-config.json native-image/gui/
docker rm furigana-trace
```

Only those three files are kept; the agent also writes empty
`proxy-config`/`serialization-config`/`predefined-classes-config` files that add
nothing. Rebuild the native images afterwards to pick the metadata up.

Use `config-output-dir` on the first run and `config-merge-dir` on the following
ones (or separate directories, merging afterwards). When you finish, check that the
multiplatform `jnidispatch` pattern is still in `resource-config.json`, because the
agent writes the specific path of the platform on which it ran.

Two entries that the agent does **not** produce must survive a regeneration of
`native-image/gui/jni-config.json`, otherwise the native GUI fails at startup with
`NoSuchMethodError`/`NoClassDefFoundError` while the font manager is loaded:

```json
{ "name": "java.awt.GraphicsEnvironment",
  "methods": [ { "name": "getLocalGraphicsEnvironment", "parameterTypes": [] } ] },
{ "name": "sun.java2d.SunGraphicsEnvironment",
  "methods": [ { "name": "isDisplayLocal", "parameterTypes": [] } ] }
```

The GUI metadata is Linux-specific (it names X11/AWT classes). The other platforms
need their own set, collected with the tracing agent on that platform.

## Measured results

In the development environment (Linux x86-64, GraalVM CE 21.0.2, two-subtitle
input):

| | Native binary | `java -jar` |
| --- | --- | --- |
| Startup | ~0.75 s | ~1.7 s |
| Memory (RSS) | ~112 MB | ~255 MB |
| Artifact | ~85 MB (CLI) / ~120 MB plus the AWT libraries (GUI) | ~18 MB (jar) |

The outputs — JSON, ASS and ASS with `--embed-font --subset-font` — are
**identical, byte for byte**, to those of the JVM.

## Limits

- The binary depends, at runtime, on common system libraries (`libz`, `libc`,
  `libpthread`).
- The **GUI** binary additionally needs the AWT/font libraries emitted next to it
  at build time, so it is shipped as an archive (see *Usage*).
- Subsetting (`--subset-font`) still requires `libharfbuzz-subset` installed: the
  native binary does not embed it. Its absence is handled as in the JVM version
  (warning and embedding the full font). On Windows the core and subset DLLs are
  loaded separately and looked up in the PATH and the usual MSYS2/vcpkg
  directories. See
  [`font-embedding.md`](font-embedding.md).
- The native build is not part of the CI on every push (it would require GraalVM
  and a C toolchain on every run). It is generated only in the release workflow, on
  dedicated runners that already bring these components.
- The committed GUI metadata (`native-image/gui/`) was collected on Linux and is
  used for every platform; the Windows and macOS GUI builds still have to be
  validated (and their metadata regenerated) on those systems.
