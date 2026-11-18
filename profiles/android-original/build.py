"""Build the local, PAL-only Android host with a private disc and ARM64 runtime."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import secrets
import subprocess
import sys
import tempfile
import zipfile

from native_inputs import LOCK, require_native
from pad_assets import BITMAPS, compile_inputs, read_pad, verify_packaged_pad

PROFILE = Path(__file__).resolve().parent
ROOT = PROFILE.parents[1]
sys.path.insert(0, str(ROOT / "tools"))
from build_progress import Progress, stream_sha256
from verify_inputs import verified_disc


def run(*command):
    subprocess.run([str(arg) for arg in command], check=True)


def android_tools():
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    jdk = os.environ.get("JAVA_HOME")
    if not sdk or not jdk:
        raise ValueError("Set ANDROID_HOME to an SDK with Android 36 / Build Tools 36.0.0, and JAVA_HOME to JDK 21.")
    tools = Path(sdk) / "build-tools/36.0.0"
    java = Path(jdk) / "bin"
    files = dict(
        aapt=tools / "aapt.exe", aapt2=tools / "aapt2.exe", align=tools / "zipalign.exe",
        d8=tools / "lib/d8.jar", signer=tools / "lib/apksigner.jar",
        java=java / "java.exe", javac=java / "javac.exe", keytool=java / "keytool.exe",
        framework=Path(sdk) / "platforms/android-36/android.jar")
    for file in files.values():
        if not file.is_file():
            raise ValueError(f"Missing Android build prerequisite: {file}")
    version = subprocess.check_output([files["javac"], "-version"], text=True).strip().split()[1]
    if version.split(".")[0] != "21":
        raise ValueError(f"Set JAVA_HOME to JDK 21 (found javac {version})")
    return files


def signing_identity(tool, cache):
    """Create a private, reusable debug identity in one same-volume rename."""
    active = cache / "local-signing"
    if not active.is_dir():
        cache.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=cache, prefix=".signing-") as temp:
            stage = Path(temp) / "identity"
            stage.mkdir()
            passfile = stage / "local.pass"
            passfile.write_text(secrets.token_hex(32), encoding="ascii")
            run(tool, "-genkeypair", "-keystore", stage / "local.p12", "-storetype", "PKCS12",
                "-storepass:file", passfile, "-alias", "personal-test", "-keyalg", "RSA",
                "-keysize", "2048", "-validity", "3650", "-dname", "CN=Private Android Test")
            try:
                stage.rename(active)
            except OSError:
                if not active.is_dir():
                    raise
    key, pw = active / "local.p12", active / "local.pass"
    if not key.is_file() or not pw.is_file():
        raise ValueError("Incomplete cache/local-signing. Restore both saved files before replacing the APK.")
    return ["--ks", str(key), "--ks-key-alias", "personal-test", "--ks-pass", "file:" + str(pw)]


def package(scratch, disc, libs, tools, dist, pad):
    # Recheck this copy before it is embedded. This also fixes the bytes chosen if the
    # input on a removable drive changes during packaging.
    snapshot = scratch / "disc.iso"
    with disc.open("rb") as source, snapshot.open("xb") as target:
        expected = stream_sha256(source, disc.stat().st_size, "Copy disc for Android", destination=target)
    verified_disc(snapshot, "pal", label="Verify Android disc copy")
    with Progress("Prepare Android resources", estimate_seconds=4):
        java_src, resource_args = compile_inputs(ROOT, scratch, pad, tools, run)
    classes, dex = scratch / "classes", scratch / "dex"
    classes.mkdir()
    dex.mkdir()
    compiled = scratch / "base.apk"
    with Progress("Link Android resources", estimate_seconds=3):
        run(tools["aapt2"], "link", "-I", tools["framework"], "--manifest", PROFILE / "app/AndroidManifest.xml",
            "-o", compiled, *resource_args)
    listing = scratch / "sources.txt"
    listing.write_text("\n".join('"' + file.as_posix() + '"' for file in java_src), "utf-8")
    with Progress("Compile Android Java", estimate_seconds=8):
        run(tools["javac"], "--release", "8", "-encoding", "UTF-8", "-classpath", tools["framework"],
            "-d", classes, "@" + str(listing))
    classes_jar = scratch / "classes.jar"
    with zipfile.ZipFile(classes_jar, "x") as jar:
        for file in sorted(classes.rglob("*.class")):
            jar.write(file, file.relative_to(classes).as_posix())
    with Progress("Compile Android bytecode", estimate_seconds=8):
        run(tools["java"], "-cp", tools["d8"], "com.android.tools.r8.D8", "--lib", tools["framework"],
            "--min-api", "28", "--output", dex, classes_jar)
    unsigned = scratch / "unsigned.apk"
    with zipfile.ZipFile(unsigned, "x", compression=zipfile.ZIP_STORED, allowZip64=False) as apk:
        with zipfile.ZipFile(compiled) as base:
            for info in base.infolist():
                apk.writestr(info, base.read(info))
        for file in sorted(dex.glob("*.dex")):
            apk.write(file, file.name)
        for name, file in libs.items():
            apk.write(file, "lib/arm64-v8a/" + name)
        for file in sorted((PROFILE / "third_party").rglob("*.txt")):
            apk.write(file, "assets/notices/" + file.relative_to(PROFILE).as_posix())
        for name in ("LICENSE", "THIRD_PARTY_NOTICES.txt", "NATIVE_SOURCE.lock.json"):
            apk.write(PROFILE / name, "assets/notices/" + name)
        apk.write(ROOT / "cache/src/hakux/LICENSE", "assets/notices/upstream-hakux-LICENSE.txt")
        apk.writestr("assets/disc/identity.txt", f"{expected} {snapshot.stat().st_size}\n")
        if pad:
            for name in BITMAPS:
                apk.writestr(name, pad[name])
        info = zipfile.ZipInfo.from_file(snapshot, "assets/disc/disc.iso")
        with snapshot.open("rb") as source, apk.open(info, "w") as target:
            stream_sha256(source, info.file_size, "Embed disc in APK", destination=target, expected=expected)
    aligned = scratch / "aligned.apk"
    ready = scratch / "android-original.apk"
    with Progress("Align APK", estimate_seconds=8):
        run(tools["align"], "-P", "16", "-f", "16384", unsigned, aligned)
    with Progress("Sign APK", estimate_seconds=10):
        signing = signing_identity(tools["keytool"], ROOT / "cache")
        run(tools["java"], "-jar", tools["signer"], "sign", *signing, "--v4-signing-enabled", "false",
            "--alignment-preserved", "true", "--out", ready, aligned)
    with Progress("Verify APK signature and alignment", estimate_seconds=12):
        run(tools["java"], "-jar", tools["signer"], "verify", "--verbose", ready)
        run(tools["align"], "-c", "-P", "16", "16384", ready)
    with zipfile.ZipFile(ready) as apk:
        verify_packaged_pad(apk, pad)
        for name, wanted in LOCK["libraries"].items():
            with apk.open("lib/arm64-v8a/" + name) as file:
                if hashlib.file_digest(file, "sha256").hexdigest() != wanted:
                    raise ValueError("Native library changed during packaging: " + name)
        with apk.open("assets/disc/disc.iso") as file:
            stream_sha256(file, snapshot.stat().st_size, "Verify embedded disc", expected=expected)
    dist.mkdir(parents=True, exist_ok=True)
    destination = dist / ready.name
    ready.replace(destination)
    print(f"Verified private APK: {destination}. The toolkit used the pinned historical ARM64 libraries.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="read-only local prerequisites")
    args = parser.parse_args()
    disc = Path(os.environ.get("BLINX_PAL_DISC", ROOT / "inputs/android-original/disc.iso"))
    if not disc.is_file():
        raise ValueError(f"Missing standalone PAL XISO: {disc}. Use the root wrapper for an extracted disc tree.")
    with Progress("Check Android tools and native libraries", estimate_seconds=5):
        libs = require_native(ROOT)
        tools = android_tools()
    work = ROOT / "build/android-original"
    if args.check:
        read_pad(ROOT, disc, work)
        verified_disc(disc, "pal")
        print(f"Local prerequisites match hakuX {LOCK['commit']} and FEX {LOCK['fex_commit']}. "
              "Uses hash-matched native libraries. Build, device behavior and corresponding source remain unverified.")
        return
    work.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=work, prefix="private-") as temp:
        scratch = Path(temp)
        pad = read_pad(ROOT, disc, scratch)
        package(scratch, disc, libs, tools, ROOT / "dist/android-original", pad)


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, subprocess.CalledProcessError, zipfile.BadZipFile, zipfile.LargeZipFile) as exc:
        print(f"android-original: {exc}", file=sys.stderr)
        sys.exit(2)
