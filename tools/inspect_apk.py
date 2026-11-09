#!/usr/bin/env python3
"""Inspect the actual preview package, permissions, resources and signing certificate."""
import argparse
import hashlib
from pathlib import Path
import re
import subprocess
import struct
import tempfile
import zipfile
from privacy_guard import check


def inspect(apk, build_tools):
    apk = Path(apk)
    tools = Path(build_tools)
    def run(name, *args):
        return subprocess.check_output([str(tools / name), *map(str, args)], text=True)
    signature = run("apksigner", "verify", "--print-certs", apk)
    badging = run("aapt", "dump", "badging", apk)
    if "package: name='com.josxtm.autocontrol.community' versionCode='2'" not in badging:
        raise ValueError("Unexpected application identity or version")
    manifest = run("aapt", "dump", "xmltree", apk, "AndroidManifest.xml")
    permissions = re.findall(r"uses-permission: name='([^']+)'", badging)
    if set(permissions) != {"com.josxtm.autocontrol.community.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION", "android.permission.INTERNET"}:
        raise ValueError("Unexpected permissions")
    if not re.search(r"allowBackup\([^\n]*=\(type 0x12\)0x0", manifest):
        raise ValueError("Automatic backup must be disabled")
    dex_strings = []
    dex_containers = []
    xml_resources = []
    metadata = ""
    with zipfile.ZipFile(apk) as archive:
        for name in archive.namelist():
            if name.endswith("/"):
                continue
            if not (re.fullmatch(r"classes\d*\.dex", name) or name in {"AndroidManifest.xml", "resources.arsc", "META-INF/com/android/build/gradle/app-metadata.properties"}
                    or re.fullmatch(r"res/[A-Za-z0-9_+/-]+(?:\.9)?\.(?:xml|png)", name)
                    or re.fullmatch(r"kotlin/[a-z/]+\.kotlin_builtins", name)
                    or re.fullmatch(r"lib/(?:arm64-v8a|armeabi-v7a|x86|x86_64)/(?:libandroidx\.graphics\.path|libjpeg|libleptonica|libpngx|libtesseract)\.so", name)
                    or name in {"META-INF/services/kotlinx.coroutines.CoroutineExceptionHandler", "META-INF/services/kotlinx.coroutines.internal.MainDispatcherFactory"}
                    or re.fullmatch(r"META-INF/[A-Za-z0-9_.-]+", name)):
                raise ValueError("Unreviewed packaged resource: " + name)
            if name.startswith("res/raw") or name.startswith("assets/"):
                raise ValueError("Bundled data is forbidden")
            if name.startswith("res/") and name.endswith(".xml"):
                xml_resources.append(run("aapt", "dump", "xmltree", apk, name))
            if name.endswith("app-metadata.properties"):
                metadata = archive.read(name).decode("utf-8")
            if re.fullmatch(r"classes\d*\.dex", name):
                data = archive.read(name)
                if not data.startswith(b"dex\n"):
                    raise ValueError("Invalid bytecode container")
                count, table = struct.unpack_from("<II", data, 56)
                for index in range(count):
                    offset, = struct.unpack_from("<I", data, table + 4 * index)
                    for _ in range(5):
                        byte = data[offset]
                        offset += 1
                        if byte < 128:
                            break
                    end = data.index(b"\0", offset)
                    dex_strings.append(data[offset:end].decode("utf-8", errors="replace"))
                dex_containers.append(dex_strings)
                dex_strings = []
    resources = run("aapt", "dump", "resources", apk)
    with tempfile.TemporaryDirectory() as folder:
        (Path(folder) / "resources.txt").write_text(resources + "\n" + "\n".join(xml_resources) + "\n" + metadata, encoding="utf-8")
        index = 0
        for constants in dex_containers:
            chunk, size = [], 0
            for constant in constants:
                length = len(constant.encode("utf-8")) + 1
                if chunk and size + length > 1000000:
                    (Path(folder) / ("bytecode" + str(index) + ".txt")).write_text("\n".join(chunk), encoding="utf-8")
                    index += 1
                    chunk, size = [], 0
                chunk.append(constant)
                size += length
            if chunk:
                (Path(folder) / ("bytecode" + str(index) + ".txt")).write_text("\n".join(chunk), encoding="utf-8")
                index += 1
        problems = check(folder)
        if problems:
            raise ValueError("Resource privacy inspection failed: " + repr(problems))
    certs = "\n".join(line for line in signature.splitlines() if "certificate SHA-256 digest:" in line)
    if not certs:
        raise ValueError("No verified certificate fingerprint")
    return ("PASS APK identity, version, signature, permission allowlist and resource inspection\n"
            + certs + "\nAPK SHA-256: " + hashlib.sha256(apk.read_bytes()).hexdigest()
            + "\nPreview only: independent public application; ephemeral debug signing.\n")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("apk")
    parser.add_argument("build_tools")
    args = parser.parse_args()
    print(inspect(args.apk, args.build_tools), end="")
