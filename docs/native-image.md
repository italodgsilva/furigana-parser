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
headless one). A few points deserve attention:

- **AWT metadata.** AWT's native library calls back into Java (`System.load`) from
  its `JNI_OnLoad` to load the next library. Without the matching registration
  Native Image aborts at startup with a misleading `Fatal error reported via JNI:
  Could not allocate library name` — which is what the build recommends with
  *"AWT: Use the tracing agent to collect metadata for AWT"*. Beyond that callback,
  AWT resolves Java classes, methods and fields from C, and each of them has to be
  registered or it fails at run time with `NoSuchMethodError` /
  `NoClassDefFoundError`. Two directories are committed and handed to the GUI build
  with `-H:ConfigurationFileDirectories`: `native-image/gui/` (collected with the
  tracing agent) and `native-image/awt/` (generated from the OpenJDK sources, see
  *Regenerating the configurations*). They are kept out of `src/main/resources` so
  they never reach the CLI image.
- **The AWT JNI libraries travel with the executable** (see the note in *Usage*
  above); the GUI is distributed as an archive for that reason.
- **Look-and-feel.** `GuiMain` instantiates Nimbus directly and falls back to
  Metal when it cannot initialize. With the metadata above Nimbus works in the
  native image too; the fallback remains as a safety net.
- **`java.home` and the AWT font manager.** A native image does not set the
  `java.home` system property, and AWT's `FontConfiguration` refuses to start
  without it. It also probes `${java.home}/lib` for a fontconfig configuration file
  and only enables its system-fontconfig fallback when that directory exists, so a
  `java.home` whose `lib` is missing ends in `RuntimeException: Fontconfig head is
  null`. `GuiMain.prepareNativeImageRuntime()`, before any AWT class is touched,
  points `java.home` at a throw-away directory with a `lib` directory and:
  - on **Linux/BSD** leaves `lib` empty, so AWT asks the system fontconfig for the
    installed fonts;
  - on **Windows/macOS** unpacks the matching bundled mapping (see the table in
    *native-image configuration*) as `lib/fontconfig.properties`, because those
    platforms have no such fallback and expect the file a regular JDK installs.

  On a regular JVM `java.home` is already set and none of this runs.
- **Fonts and `Desktop`.** On Linux text rendering uses the system fonts
  (freetype/fontconfig); on Windows/macOS it uses the fonts named by the mapping
  above. "Open folder" uses `java.awt.Desktop` when it is supported.

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
| `resource-config.json` | The Basic/Synth look-and-feel bundles, `sun.awt.resources.awt` and the platform font mappings below. |

It sits *outside* `src/main/resources`, so it is not packaged into the jar and does
not reach the CLI image; the `build-native-gui` execution passes it with
`-H:ConfigurationFileDirectories`.

The second directory is `native-image/awt/`:

| File | What for |
| --- | --- |
| `jni-config.json` | The JNI surface of the AWT/Swing native libraries on every platform: every java.desktop class the native code passes to `FindClass()`, implements a native method of, or mentions in a JNI signature, registered with all of its declared constructors, methods and fields (java.base classes and the compiler-generated render-queue callbacks are handled differently, see below). |

The tracing agent only records the lookups the native libraries of the machine it
runs on actually perform, so `native-image/gui/jni-config.json` (collected on Linux)
covers the X11 code paths and nothing else: on Windows the very first AWT call
fails with `NoSuchMethodError: java.awt.Toolkit.getDefaultToolkit()Ljava/awt/Toolkit;`
— and the missing entries are not the same on macOS. `native-image/awt/` closes that
gap by deriving the metadata from the OpenJDK sources instead of tracing, so it is
platform-independent. Registering a superset is harmless: Native Image silently skips
the classes that do not exist on the platform being built.

Two groups cannot be derived from the native sources and are handled explicitly:

- **java.base classes** are registered with their *public* members only
  (`allDeclaredConstructors`/`allPublicMethods`/`allPublicFields`) instead of "all
  declared". Registering their private internals aborts every native build:
  `java.lang.System.initPhase1` reaches `ClassLoader.initSystemClassLoader()`, which
  Native Image deletes. AWT only uses the public surface of java.base anyway
  (`Thread.currentThread`, `Runnable.run`, `System.load`, ...).
- **The compiler-generated `Runnable` classes** the render queues resolve `run()` on
  are listed by name (`CALLBACK_CLASSES` in the generator). The queue code calls
  `GetMethodID(GetObjectClass(runnable), "run", "()V")` on the *concrete* class, and
  Native Image refuses to answer that through `java.lang.Runnable` when the class
  overrides `run()` (its `JNIAccessibleMember.isDiscoverableIn` rejects it), so the
  interface alone leaves the Windows D3D path crashing with
  `NoSuchMethodError: sun.java2d.d3d.D3DGraphicsDevice$1.run()V`.

The GUI additionally bundles the font mapping that a Windows/macOS JDK installs as
`lib/fontconfig.bfc`, as two resources under
`src/main/resources/com/shinji2324/furigana/gui/awt/`:

| Resource | Platform |
| --- | --- |
| `fontconfig-windows.properties` | Windows |
| `fontconfig-macos.properties` | macOS |

They are copied verbatim from OpenJDK
(`src/java.desktop/{windows,macosx}/data/fontconfig/fontconfig.properties`) and keep
Oracle's GPLv2-with-Classpath-exception header, so the project is MIT except for
these two data files. `resource-config.json` embeds them in the GUI image and
`GuiMain` unpacks the matching one into `${java.home}/lib/fontconfig.properties`.
Linux and the BSDs get nothing: they read the fonts from the system fontconfig
instead (see *`java.home` and the AWT font manager* above).

### Regenerating the configurations

If the model or the dependencies change (new reflective serialization, new resource
read from the classpath), regenerate with the native-image *tracing agent*:

```
GRAALVM=/path/to/graalvm
CFG=src/main/resources/META-INF/native-image/com.shinji2324/furigana-parser

# 1) JSON route (Jackson) — config-output-dir overwrites
"$GRAALVM/bin/java" -agentlib:native-image-agent=config-output-dir=$CFG \
  -jar target/furigana-parser-0.2.0-SNAPSHOT.jar input.srt output.json

# 2) ASS + subset route (JNA) — config-merge-dir accumulates
"$GRAALVM/bin/java" -agentlib:native-image-agent=config-merge-dir=$CFG \
  -jar target/furigana-parser-0.2.0-SNAPSHOT.jar \
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

The agent does not know about the bundled font mappings either, so
`native-image/gui/resource-config.json` has to keep the two
`com/shinji2324/furigana/gui/awt/fontconfig-*.properties` patterns.

The GUI JNI metadata in `native-image/gui/` is Linux-specific (it names X11/AWT
classes), which is why the platform-independent `native-image/awt/` metadata exists:

```
python3 tools/generate-awt-jni-config.py          # regenerate native-image/awt/
python3 tools/generate-awt-jni-config.py --check   # fail when it is out of date
```

It downloads the `src/java.desktop/**/native` sources of the pinned OpenJDK revision
(`DEFAULT_REF`, keep it in step with the GraalVM version), collects every class they
use over JNI and writes the result as "register all declared members". The output
only changes when the pin changes, so the script and its output are committed
together; re-run it (and rebuild) when the `native` profile is moved to another
GraalVM/JDK version. It needs network access and is therefore not part of the build.

`CALLBACK_CLASSES` is the one hand-maintained part of the generator: those classes
have no source file, so no scan can find them. Refresh the list from the JDK the
release uses with

```
jimage list "$JAVA_HOME/lib/modules" \
  | grep -E 'StrikeCache\$[0-9]+|java2d\.(d3d|opengl|pipe)\.(D3D|WGL|GLX|Buffered|OGL).*\$[0-9]+|TranslucentWindowPainter.*\$[0-9]+'
```

If a Windows/macOS binary still fails with a `NoSuchMethodError`/`NoClassDefFoundError`
that the two directories do not cover — the native event loop resolves some classes
dynamically — collect a trace on that platform and merge it in:

```
java -agentlib:native-image-agent=config-output-dir=config -jar furigana-parser-gui-0.2.0-SNAPSHOT.jar
# click through the window (move it, type, open the file and colour choosers), then
# merge config/*.json into native-image/gui/ and rebuild.
```

A `NoSuchMethodError` whose message ends in `$<number>.run()V` is the missing
`CALLBACK_CLASSES` case above, not a tracing problem: add the named class to the
generator instead.

The bundled font mappings come from OpenJDK and already cover Windows and macOS.

## Measured results

In the development environment (Linux x86-64, GraalVM CE 21.0.2, two-subtitle
input):

| | Native binary | `java -jar` |
| --- | --- | --- |
| Startup | ~0.75 s | ~1.7 s |
| Memory (RSS) | ~112 MB | ~255 MB |
| Artifact | ~85 MB (CLI) / ~132 MB plus the AWT libraries (GUI) | ~18 MB (jar) |

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
- The committed GUI metadata (`native-image/gui/`) was collected on Linux, so it
  only describes the X11 code paths; the platform-independent metadata in
  `native-image/awt/` covers the Windows and macOS ones. **Linux is the verified
  target**; the Windows/macOS GUI binaries are best effort, because Native Image's
  AWT support is itself incomplete there and the native event loop resolves some
  classes at run time, after the build (see *Regenerating the configurations*).
