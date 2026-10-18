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

Usage
-----
    python3 tools/generate-awt-jni-config.py           # write the file
    python3 tools/generate-awt-jni-config.py --check   # fail if it is stale
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

FLAGS = {
    "allDeclaredConstructors": True,
    "allDeclaredMethods": True,
    "allDeclaredFields": True,
    "allPublicMethods": True,
    "allPublicFields": True,
}

OUTPUT = Path(__file__).resolve().parent.parent / "native-image" / "awt" / "jni-config.json"


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


def awt_classes(ref: str) -> list[str]:
    """Every java.desktop class whose native code is reached through JNI."""
    known = set()
    for root in CLASS_ROOTS:
        for path in listing(ref, root):
            if path.endswith(".java") and not path.endswith(("module-info.java", "package-info.java")):
                known.add(path[: -len(".java")].replace("/", "."))

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

    # Only classes that really exist in java.desktop: the heuristics above
    # cannot always tell a class name from a method name with escaped
    # underscores, and bogus entries would only produce build warnings.
    return sorted(name for name in wanted if declared(name))


def build(ref: str) -> str:
    entries = [
        {"name": name, **FLAGS}
        for name in awt_classes(ref)
    ]
    body = ",\n".join("  " + json.dumps(entry) for entry in entries)
    return f"[\n{body}\n]\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--ref", default=DEFAULT_REF, help=f"OpenJDK revision to derive the metadata from (default: {DEFAULT_REF})")
    parser.add_argument("--check", action="store_true", help="do not write, exit with 1 when the file is out of date")
    options = parser.parse_args()

    generated = build(options.ref)
    if options.check:
        current = OUTPUT.read_text() if OUTPUT.exists() else ""
        if current != generated:
            print(f"{OUTPUT} is out of date, run tools/generate-awt-jni-config.py", file=sys.stderr)
            return 1
        print(f"{OUTPUT} is up to date")
        return 0

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(generated)
    print(f"wrote {OUTPUT} ({generated.count(chr(10)) - 1} classes)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
