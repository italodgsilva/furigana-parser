# Container smoke tests

Builds and exercises the release artifacts in containers, so the checks do not
depend on the host having GraalVM, a C toolchain or a display. Two flavours:

| File | Builds | Checks |
| --- | --- | --- |
| [`Dockerfile.jar`](Dockerfile.jar) | the executable JARs (CLI + GUI) | the CLI generates correct JSON/ASS; the GUI opens under Xvfb |
| [`Dockerfile.native`](Dockerfile.native) | the native binaries (CLI + GUI) with GraalVM | the CLI runs from a directory of its own (self-contained) and the GUI opens — with its AWT libraries next to it — under Xvfb, on a clean Debian with no JVM |

Both run the same script, [`smoke-test.sh`](smoke-test.sh), against a small sample
subtitle, [`sample.ja.srt`](sample.ja.srt). The GUI needs a display, so it is
started under Xvfb and is expected to stay open until a timeout; any early exit
(including the headless message) is a failure.

## Usage

With Docker Compose:

```
docker compose build jar    && docker compose run --rm jar
docker compose build native && docker compose run --rm native
```

Or directly, from the repository root:

```
docker build -f docker/Dockerfile.native -t furigana-parser-native .
docker run --rm furigana-parser-native
```

The native build takes a few minutes (two Native Image builds); the JAR one takes
about a minute.

## Regenerating the AWT metadata

[`Dockerfile.trace`](Dockerfile.trace) is the helper that runs the GUI under Xvfb
with the native-image *tracing agent* and writes the AWT/Swing metadata to
`/config`. See the "Regenerating the configurations" section of
[`../docs/native-image.md`](../docs/native-image.md).

```
docker build -f docker/Dockerfile.trace -t furigana-parser-trace .
docker run --name furigana-trace furigana-parser-trace
docker cp furigana-trace:/config/reflect-config.json  ../native-image/gui/
docker cp furigana-trace:/config/jni-config.json      ../native-image/gui/
docker cp furigana-trace:/config/resource-config.json ../native-image/gui/
docker rm furigana-trace
```

## Notes

- The native test image copies the AWT/font JNI libraries (`*.so`) next to the GUI
  binary, mirroring the released archive. The CLI binary is placed in its own
  directory so the test proves it needs none of them.
- On Windows + WSL, where the Docker Desktop integration is not enabled for the
  distribution, the Windows client works from WSL: run `docker.exe` instead of
  `docker` (the build context path is translated automatically).
