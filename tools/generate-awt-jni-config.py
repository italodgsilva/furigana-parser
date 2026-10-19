#!/usr/bin/env python3
"""Regenerate the JNI metadata used by the native GUI binary.

Why this exists
---------------
Native Image does not use reflection to resolve JNI lookups: every class,
method and field that native code hands to ``FindClass``/``GetMethodID``/
``GetFieldID`` must be registered in a ``jni-config.json``.  AWT and Swing do a
lot of that from their C code, so the native GUI needs metadata that the
tracing agent cannot provide on its own: the agent only records the lookups that
the native libraries of the machine it runs on actually perform.

``native-image/gui/jni-config.json`` was produced by the agent on Linux, so it
only covers the X11/Unix code paths.  Building the GUI for Windows or macOS with
that file alone fails at run time with

    java.lang.NoSuchMethodError: java.awt.Toolkit.getDefaultToolkit()Ljava/awt/Toolkit;
    java.lang.NoSuchMethodError: java.awt.Point.<init>(II)V
    ...

This script derives the complete JNI surface of the AWT/Swing native libraries
from the OpenJDK sources instead, and registers it for *all* platforms at once.
A class is included when the native code

  * passes its name to ``FindClass()``,
  * implements one of its native methods, or
  * writes its name as a string literal or in a JNI signature (the macOS code
    resolves classes through the JavaNativeFoundation helpers, which never put
    the name next to a ``FindClass`` call).

Every such class is registered with all of its declared constructors, methods
and fields (plus the public ones it inherits).  Over-registering is harmless --
Native Image skips classes that do not exist on the platform being built -- and
it keeps the metadata independent of the build machine.

The render loops need a second kind of metadata.  ``GraphicsPrimitiveMgr``
registers most of its primitives as ``GraphicsPrimitiveProxy`` objects, which
keep only the *name* of the real class and load it lazily with

    Class.forName(name).getDeclaredConstructor().newInstance()

(``GraphicsPrimitiveProxy.instantiate``).  A native image cannot load a class by
name unless it was registered for reflection, so ``sun.java2d.loops.SetDrawLineANY``
and the other proxies are missing and painting fails with

    java.lang.RuntimeException: java.lang.ClassNotFoundException:
        sun.java2d.loops.SetDrawLineANY

The proxy class names live in the ``GeneralRenderer``/``CustomComponent``
sources (the classes themselves are secondary types declared in those files), so
the generator reads them out of the ``sun/java2d`` sources of every platform and
writes ``native-image/awt/reflect-config.json`` next to the JNI metadata.

Usage
-----
    python3 tools/generate-awt-jni-config.py           # write both files
    python3 tools/generate-awt-jni-config.py --check   # fail if either is stale
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

# The OpenJDK revision the metadata is derived from.  Bump it together with the
# GraalVM version used by the `native` Maven profile.
DEFAULT_REF = "bb6b279495ceff8442dbd0225756b92d466d5309"
REPOSITORY = "openjdk/jdk21u"

# Every module of java.desktop that has Java sources or native code.
CLASS_ROOTS = (
    "src/java.desktop/share/classes",
    "src/java.desktop/windows/classes",
    "src/java.desktop/macosx/classes",
    "src/java.desktop/unix/classes",
)
# java.base classes are looked up too (java.lang.Thread in GDIWindowSurfaceData,
# java.lang.Runnable for the render queues), so they have to pass the existence
# check even though only java.desktop's native code is scanned.
BASE_CLASS_ROOTS = (
    "src/java.base/share/classes",
)
NATIVE_ROOTS = (
    "src/java.desktop/share/native",
    "src/java.desktop/windows/native",
    "src/java.desktop/macosx/native",
    "src/java.desktop/unix/native",
)

NATIVE_FILE = re.compile(r"\.(c|cpp|m|mm)$")
SKIP_NATIVE = re.compile(r"/freetype/")  # bundled font rasterizer, no JNI

# `Java_com_example_Foo_bar` and friends, as written in the C sources.
NATIVE_FUNCTION = re.compile(r"\b(Java_[A-Za-z0-9_]+)\s*\(")
FIND_CLASS = re.compile(r"FindClass\s*\(\s*(?:env\s*,\s*)?\"([^\"]+)\"")
# GetMethodID(env, cls, "name", "signature") and the static/field variants.
GET_ID = re.compile(
    r"Get(?:Static)?(?:Method|Field)ID\s*\(\s*[^,]+,\s*(?:[^,]+,\s*)?\"([^\"]*)\""
)
# Any class name written as a string, whatever API consumes it: the macOS code
# looks classes up with the JavaNativeFoundation helpers ("sun/lwawt/LWWindowPeer"),
# never next to a FindClass call.
CLASS_LITERAL = re.compile(r"\"((?:[a-z][A-Za-z0-9_]*/)+[A-Z][A-Za-z0-9_$]*)\"")
# ... as well as any class used in a JNI signature, e.g. "(Ljava/awt/Foo;)V".
CLASS_DESCRIPTOR = re.compile(r"L((?:[a-z][A-Za-z0-9_]*/)+[A-Z][A-Za-z0-9_$]*);")

# Every platform directory under which GraphicsPrimitiveProxy objects are built.
# The proxy targets always live in the package of their owner, so the sources
# have to be read per platform.
JAVA2D_DIRS = tuple(f"{root}/sun/java2d" for root in CLASS_ROOTS)
# `Class<?> owner = GeneralRenderer.class;` and the proxies that use it.  The
# relative name is resolved against the owner's package (GraphicsPrimitiveProxy
# does the same with `getPackageName(owner.getName())`).
OWNER_DECL = re.compile(r"\b(\w+)\s*=\s*(\w+)\.class\b")
PROXY_CALL = re.compile(
    r"new\s+GraphicsPrimitiveProxy\s*\(\s*(\w+)\s*,\s*\"([A-Za-z0-9_$]+)\""
)
# The proxies are only reached through Class.forName + getDeclaredConstructor,
# so the constructors are all the reflection metadata they need.
REFLECT_FLAGS = {
    "allDeclaredConstructors": True,
}

FLAGS = {
    "allDeclaredConstructors": True,
    "allDeclaredMethods": True,
    "allDeclaredFields": True,
    "allPublicMethods": True,
    "allPublicFields": True,
}

# java.base classes only get their constructors and public members.  Their
# private internals are substituted or deleted by Native Image -- registering
# java.lang.System.initPhase1/System.props makes the build abort with
# "Unsupported method java.lang.ClassLoader.initSystemClassLoader() is reachable".
# AWT only ever touches the public API of java.base (Thread.currentThread,
# Runnable.run, System.load, ...), so the public surface is enough.
BASE_FLAGS = {
    "allDeclaredConstructors": True,
    "allPublicMethods": True,
    "allPublicFields": True,
}

# The AWT render queues do not call back into Java through the Runnable
# interface: D3DRenderQueue.cpp ends flushBuffer with
#
#     JNU_CallMethodByName(env, &hasException, pFlush->runnable, "run", "()V")
#
# which resolves `run` on the *concrete* class of the object that was handed to
# RenderQueue.flushAndInvokeNow.  Native Image refuses to satisfy that through
# java.lang.Runnable: JNIReflectionDictionary.findMethod does recurse into
# superinterfaces, but JNIAccessibleMember.isDiscoverableIn then rejects the
# result because the concrete class overrides run() (it is one of the
# `hidingSubclasses`, computed over the whole image).  Registering the
# interface alone therefore still fails at run time with
#
#     NoSuchMethodError: sun.java2d.d3d.D3DGraphicsDevice$1.run()V
#
# Every compiler-generated class the queues can see therefore has to be listed
# by name.  Anonymous classes cannot be derived from the OpenJDK sources, so the
# list below was enumerated from the JDK images the release uses: take the
# classes that call RenderQueue.flushAndInvokeNow (found by grepping the class
# files for that name) and keep their `$<number>` nested classes.  The
# java.desktop sources that create them are unchanged between 21.0.11 and
# 21.0.12, so the numbering is stable.  StrikeCache$1 is a PrivilegedAction that
# shares the file with StrikeCache$2; it is registered for safety only.
CALLBACK_CLASSES = (
    "sun.awt.windows.TranslucentWindowPainter$VIOptWindowPainter$1",
    "sun.font.StrikeCache$1",
    "sun.font.StrikeCache$2",
    "sun.java2d.d3d.D3DGraphicsDevice$1",
    "sun.java2d.d3d.D3DGraphicsDevice$2",
    "sun.java2d.d3d.D3DGraphicsDevice$3",
    "sun.java2d.d3d.D3DGraphicsDevice$4",
    "sun.java2d.d3d.D3DGraphicsDevice$5",
    "sun.java2d.d3d.D3DGraphicsDevice$6",
    "sun.java2d.d3d.D3DGraphicsDevice$7",
    "sun.java2d.d3d.D3DGraphicsDevice$8",
    "sun.java2d.d3d.D3DRenderQueue$1",
    "sun.java2d.d3d.D3DSurfaceData$1",
    "sun.java2d.d3d.D3DSurfaceData$2",
    "sun.java2d.d3d.D3DSurfaceData$D3DDataBufferNative$1",
    "sun.java2d.d3d.D3DSurfaceData$D3DDataBufferNative$2",
    "sun.java2d.opengl.GLXGraphicsConfig$1",
    "sun.java2d.opengl.OGLSurfaceData$1",
    "sun.java2d.opengl.WGLGraphicsConfig$1",
    "sun.java2d.pipe.BufferedMaskFill$1",
    "sun.java2d.pipe.BufferedRenderPipe$1",
    "sun.java2d.pipe.BufferedTextPipe$1",
)

OUTPUT = Path(__file__).resolve().parent.parent / "native-image" / "awt" / "jni-config.json"
REFLECT_OUTPUT = Path(__file__).resolve().parent.parent / "native-image" / "awt" / "reflect-config.json"


def fetch(url: str) -> str:
    """GET a text resource, retrying a couple of times on transient errors."""
    last: Exception | None = None
    for _ in range(3):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": "furigana-parser"})
            with urllib.request.urlopen(request, timeout=60) as response:
                return response.read().decode("utf-8", "replace")
        except (urllib.error.URLError, TimeoutError) as error:  # pragma: no cover - network
            last = error
    raise SystemExit(f"could not download {url}: {last}")


def listing(ref: str, root: str) -> list[str]:
    """Return the repository-relative path of every file below `root`."""
    import json as _json

    payload = _json.loads(fetch(f"https://api.github.com/repos/{REPOSITORY}/git/trees/{ref}:{root}?recursive=1"))
    return [entry["path"] for entry in payload.get("tree", []) if entry["type"] == "blob"]


def jni_class_of_function(name: str) -> str | None:
    """Turn `Java_java_awt_Toolkit_initIDs` into `java.awt.Toolkit`.

    Package parts are joined with `_`, nested classes use `_00024` and the
    method name is the final component (whose own underscores are escaped), so
    the class starts at the first upper-case component and ends when the method
    name begins.
    """
    mangled = name[len("Java_") :]
    for escape, plain in (("_00024", "$"), ("_00026", "&"), ("_1", "_"), ("_2", ";"), ("_3", "[")):
        mangled = mangled.replace(escape, plain)
    parts = mangled.split("_")
    start = next((i for i, part in enumerate(parts) if part[:1].isupper()), None)
    if start is None:
        return None
    class_parts = []
    for i in range(start, len(parts)):
        if i > start and parts[i][:1].islower():
            break
        class_parts.append(parts[i])
    package = ".".join(parts[:start])
    return f"{package}.{'.'.join(class_parts)}" if package else ".".join(class_parts)


def classes_in(source: str) -> set[str]:
    """Collect the class names the native code of a single file refers to."""
    found: set[str] = set()
    for name in FIND_CLASS.findall(source):
        found.add(name.replace("/", "."))
    for name in NATIVE_FUNCTION.findall(source):
        class_name = jni_class_of_function(name)
        if class_name:
            found.add(class_name)
    for signature in GET_ID.findall(source):
        # Signatures hold the parameter and return types the native code passes
        # around: `(Ljava/awt/Component;IJIICI)V` mentions java.awt.Component.
        found.update(name for name in CLASS_DESCRIPTOR.findall(signature))
    for name in CLASS_LITERAL.findall(source):
        found.add(name)
    for name in CLASS_DESCRIPTOR.findall(source):
        found.add(name)
    return {name.replace("/", ".") for name in found}


def java_classes(ref: str, roots: tuple[str, ...]) -> set[str]:
    """The fully-qualified names of every top-level class below `roots`."""
    names: set[str] = set()
    for root in roots:
        for path in listing(ref, root):
            if path.endswith(".java") and not path.endswith(("module-info.java", "package-info.java")):
                names.add(path[: -len(".java")].replace("/", "."))
    return names


def declarations(ref: str) -> tuple[list[str], set[str]]:
    """The JNI classes to register, and which of them come from java.base."""
    desktop = java_classes(ref, CLASS_ROOTS)
    base = java_classes(ref, BASE_CLASS_ROOTS)
    known = desktop | base

    def declared(name: str) -> bool:
        # Nested classes have no source file of their own: the native code looks
        # up java.awt.desktop.UserSessionEvent$Reason with FindClass, but
        # Reason is declared inside UserSessionEvent.java, so only the outer
        # class can be checked against the file list.
        return name in known or name.split("$")[0] in known

    wanted: set[str] = set()
    for root in NATIVE_ROOTS:
        for path in listing(ref, root):
            if not NATIVE_FILE.search(path) or SKIP_NATIVE.search(path):
                continue
            wanted.update(classes_in(fetch(f"https://raw.githubusercontent.com/{REPOSITORY}/{ref}/{root}/{path}")))

    # java.base's own native code is deliberately *not* scanned: it would drag in
    # jdk.internal.* classes (notably jdk.internal.foreign.abi.fallback.LibFallback)
    # that abort the native build when their static initializer runs at build time.
    #
    # Only the java.base classes named by java.desktop's native code are kept, plus
    # the compiler-generated Runnable classes the queues look `run` up on
    # (CALLBACK_CLASSES -- they have no source file, so no scan can find them).
    names = sorted({name for name in wanted if declared(name)} | set(CALLBACK_CLASSES))
    base_names = {
        name
        for name in names
        if name not in desktop
        and name.split("$")[0] not in desktop
        and (name in base or name.split("$")[0] in base)
    }
    return names, base_names


def proxy_classes(ref: str) -> list[str]:
    """The classes ``GraphicsPrimitiveProxy`` loads at run time with ``Class.forName``.

    ``GraphicsPrimitiveProxy`` keeps only the *name* of the real primitive and
    resolves it lazily against the package of its owner.  The proxies are
    secondary (package-private) classes declared inside the file that builds
    them, so no file listing can find them; the names have to be read out of the
    ``sun/java2d`` sources of every platform instead.
    """

    def package_of(path: str) -> str:
        relative = path.rsplit("/", 1)[0] if "/" in path else ""
        return "sun.java2d" + (f".{relative.replace('/', '.')}" if relative else "")

    files: list[tuple[str, str]] = []  # (class root, path below its sun/java2d)
    packages: dict[str, set[str]] = {}  # simple file name -> packages that declare it
    for base in JAVA2D_DIRS:
        for path in listing(ref, base):
            if not path.endswith(".java"):
                continue
            files.append((base, path))
            simple = path.rsplit("/", 1)[-1][: -len(".java")]
            packages.setdefault(simple, set()).add(package_of(path))

    names: set[str] = set()
    for base, path in files:
        source = fetch(f"https://raw.githubusercontent.com/{REPOSITORY}/{ref}/{base}/{path}")
        owners = dict(OWNER_DECL.findall(source))
        fallback = package_of(path)
        for variable, relative in PROXY_CALL.findall(source):
            candidates = packages.get(owners.get(variable, ""), {fallback})
            if fallback in candidates or len(candidates) != 1:
                package = fallback
            else:
                package = next(iter(candidates))
            names.add(f"{package}.{relative}")
    return sorted(names)


def build_reflect(ref: str) -> str:
    entries = [{"name": name, **REFLECT_FLAGS} for name in proxy_classes(ref)]
    body = ",\n".join("  " + json.dumps(entry) for entry in entries)
    return f"[\n{body}\n]\n"


def build(ref: str) -> str:
    names, base_names = declarations(ref)
    entries = [
        {"name": name, **(BASE_FLAGS if name in base_names else FLAGS)}
        for name in names
    ]
    body = ",\n".join("  " + json.dumps(entry) for entry in entries)
    return f"[\n{body}\n]\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--ref", default=DEFAULT_REF, help=f"OpenJDK revision to derive the metadata from (default: {DEFAULT_REF})")
    parser.add_argument("--check", action="store_true", help="do not write, exit with 1 when the file is out of date")
    options = parser.parse_args()

    generated = {OUTPUT: build(options.ref), REFLECT_OUTPUT: build_reflect(options.ref)}
    if options.check:
        stale = False
        for path, content in generated.items():
            current = path.read_text() if path.exists() else ""
            if current != content:
                print(f"{path} is out of date, run tools/generate-awt-jni-config.py", file=sys.stderr)
                stale = True
        if stale:
            return 1
        print(", ".join(str(path) for path in generated) + " are up to date")
        return 0

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    for path, content in generated.items():
        path.write_text(content)
        print(f"wrote {path} ({content.count('\"name\"')} classes)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
