"""Build a private combined Demo/PAL Android APK from staged frontend + hakuX.

All outputs remain under this packaging directory. Inputs (the existing signing
local key, verified native libraries, sources and disc images) are read only.
Use --check-inputs to validate the supplied inputs without compiling/signing.
"""
from __future__ import annotations
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import re
import shutil
import struct
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

HERE=Path(__file__).resolve().parent
NS="{http://schemas.android.com/apk/res/android}"

def sha(path):
    with path.open("rb") as source:return hashlib.file_digest(source,"sha256").hexdigest()

def safe_name(name):
    return bool(name) and not name.startswith("/") and ":" not in name and "\\" not in name and "\0" not in name and all(part not in (".","..") for part in name.split("/"))

def owned(path):
    path=path.resolve()
    if not path.is_relative_to(HERE):raise RuntimeError("Output must remain under "+str(HERE))
    return path

def app_root(path):
    path=path.resolve()
    return path if (path/"AndroidManifest.xml").is_file() else path/"app"

def elf_check(path):
    with path.open("rb") as source:
        header=source.read(64)
        if len(header)!=64 or header[:6]!=b"\x7fELF\x02\x01" or struct.unpack_from("<H",header,18)[0]!=183:
            raise RuntimeError("Not an ARM64 shared library: "+str(path))
        if struct.unpack_from("<H",header,16)[0]!=3:raise RuntimeError("ELF is not a shared object")
        phoff=struct.unpack_from("<Q",header,32)[0]
        phentsize,phnum=struct.unpack_from("<HH",header,54)
        alignments=[]
        for index in range(phnum):
            source.seek(phoff+phentsize*index);segment=source.read(phentsize)
            if len(segment)<56:raise RuntimeError("Truncated program header")
            if struct.unpack_from("<I",segment)[0]==1:
                offset,address=struct.unpack_from("<QQ",segment,8)
                alignment=struct.unpack_from("<Q",segment,48)[0]
                if alignment<16384 or offset%16384!=address%16384:raise RuntimeError("Native library is not 16 KiB compatible: "+str(path))
                alignments.append(alignment)
    if not alignments:raise RuntimeError("No loadable ELF segments")
    return {"path":str(path),"sha256":sha(path),"bytes":path.stat().st_size,"load_alignments":alignments}

def zip_data_offset(archive,path,entry):
    with path.open("rb") as raw:
        raw.seek(entry.header_offset)
        header=raw.read(30)
        if header[:4]!=b"PK\x03\x04":raise RuntimeError("Bad ZIP local header")
        name_size,extra_size=struct.unpack_from("<HH",header,26)
    return entry.header_offset+30+name_size+extra_size

def get_args():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--app",type=Path,required=True,help="Base staged app folder (or parent containing app/)")
    parser.add_argument("--overlay",type=Path,help="Optional app tree overlay, merged into private build staging")
    parser.add_argument("--extra-assets",type=Path,action="append",default=[],help="Extra assets root; can be repeated")
    parser.add_argument("--runtime",type=Path,default=HERE.parent/"runtime")
    parser.add_argument("--keystore",type=Path,required=True)
    parser.add_argument("--alias",required=True)
    parser.add_argument("--payloads",type=Path,default=HERE/"PAYLOADS.json")
    parser.add_argument("--sdk",type=Path,required=True)
    parser.add_argument("--jdk",type=Path,required=True)
    parser.add_argument("--output",type=Path,default=HERE/"artifacts/BLINX_ANDROID_COMBINED.apk")
    parser.add_argument("--check-inputs",action="store_true")
    return parser.parse_args()

def main():
    args=get_args()
    if not (HERE / "PAYLOADS.json").is_file() or HERE.parent.parent.name != "android-full" or HERE.parent.parent.parent.name != "build":
        raise RuntimeError("Run the Full profile wrapper; packaging requires a private build staging directory.")
    from build_progress import Progress, stream_sha256
    runtime=args.runtime.resolve();base=app_root(args.app)
    overlay=app_root(args.overlay) if args.overlay else None
    manifest_path=(overlay/"AndroidManifest.xml") if overlay and (overlay/"AndroidManifest.xml").is_file() else base/"AndroidManifest.xml"
    manifest=ET.parse(manifest_path).getroot()
    if manifest.attrib.get("package")!="it.blinx.demo.v1":raise RuntimeError("Unexpected package; preserve installed app identity")
    if any(child.tag.startswith("uses-permission") for child in manifest):raise RuntimeError("Offline combined package must declare zero permissions")
    sdk_tag=manifest.find("uses-sdk")
    if sdk_tag is None or int(sdk_tag.attrib.get(NS+"minSdkVersion","0"))<28:raise RuntimeError("Minimum API must be at least 28")
    activities=manifest.findall("application/activity")
    accelerated=[a for a in activities if a.attrib.get(NS+"name","").endswith("AcceleratedGameActivity")]
    if len(accelerated)!=1 or accelerated[0].attrib.get(NS+"exported")!="false" or accelerated[0].attrib.get(NS+"process")!=":game":
        raise RuntimeError("Require one non-exported AcceleratedGameActivity in :game")
    restarts=[a for a in activities if a.attrib.get(NS+"name","").endswith("CoreRestartActivity")]
    if len(restarts)!=1 or restarts[0].attrib.get(NS+"exported")!="false" or restarts[0].attrib.get(NS+"process"):
        raise RuntimeError("Require CoreRestartActivity non-exported in the frontend process")
    metadata=manifest.findall("application/meta-data")+manifest.findall("application/activity/meta-data")
    if not any(m.attrib.get(NS+"name")=="SDL_MAIN_LIBRARY" and m.attrib.get(NS+"value")=="hakuxcore" for m in metadata):
        raise RuntimeError("Missing SDL_MAIN_LIBRARY=hakuxcore metadata")
    required_sources=[runtime/"sdl_java/org/libsdl/app/SDLActivity.java",runtime/"java/com/rfandango/haku_x/NativeCoreActivity.java",
                      runtime/"java/it/blinx/demo/v1/AcceleratedGameActivity.java",HERE/"java/it/blinx/demo/v1/DiscInstaller.java"]
    for required in required_sources:
        if not required.is_file():raise RuntimeError("Missing runtime Java: "+str(required))
    profiles=json.loads(args.payloads.read_text(encoding="utf-8-sig"))["profiles"]
    if sorted(p["id"] for p in profiles)!=["demo","pal"]:raise RuntimeError("Exactly demo/pal payloads are required")
    for profile in profiles:
        path=Path(profile["path"])
        if profile["asset"]!="discs/"+profile["id"]+".iso":raise RuntimeError("Wrong profile asset path")
        with path.open("rb") as stream:
            stream_sha256(stream, profile["bytes"], "Verify staged " + profile["id"] + " disc", expected=profile["sha256"])
    libraries={name:runtime/"jniLibs/arm64-v8a"/name for name in ("libSDL3.so","libhakuxcore.so")}
    native={name:elf_check(path) for name,path in libraries.items()}
    expected_libraries = {
        "libSDL3.so": "303899002f1891317c74e7f3a71cc78cbdaae88b46055cba241c73d5b24c65ff",
        "libhakuxcore.so": "45e4968858b5fcd07fd15b5b052fdacb3a9f046ffd5279265fae204138763f7b",
    }
    for name, info in native.items():
        if info["sha256"] != expected_libraries[name]:
            raise RuntimeError("Unexpected hakuX v1.3.0 library: " + name)
    notice_plan=json.loads((runtime/"NOTICE_FILES.json").read_text(encoding="utf-8-sig"))
    notices=notice_plan if isinstance(notice_plan,list) else notice_plan["files"]
    if not notices:raise RuntimeError("Runtime notices are required")
    for notice in notices:
        if not notice["apk_entry"].startswith("assets/licenses/") or not safe_name(notice["apk_entry"]):raise RuntimeError("Unsafe notice destination")
        if sha(Path(notice["source"]))!=notice["sha256"].lower():raise RuntimeError("Notice hash changed")
    tools=args.sdk/"build-tools/36.0.0";android_jar=args.sdk/"platforms/android-36/android.jar";java_bin=args.jdk/"bin"
    keystore=args.keystore
    for path in [tools/"aapt2.exe",tools/"aapt.exe",tools/"zipalign.exe",tools/"lib/d8.jar",tools/"lib/apksigner.jar",android_jar,
                 java_bin/"java.exe",java_bin/"javac.exe",keystore]:
        if not path.is_file():raise RuntimeError("Missing build input: "+str(path))
    if sum(p["bytes"] for p in profiles)+sum(x["bytes"] for x in native.values())>2*1024**3-32*1024**2:
        raise RuntimeError("Disc payloads exceed conservative single-APK size limit")
    input_receipt={"schema":"blinx-combined-inputs/1","app":str(base),"overlay":str(overlay) if overlay else None,
                   "manifest":str(manifest_path),"version":manifest.attrib.get(NS+"versionName"),"profiles":profiles,
                   "native":native,"native_source_revision":"a90f01ee4629977b36a76175cbd781d383056293",
                   "notices":len(notices),"permissions":[],"check_only":args.check_inputs}
    if args.check_inputs:
        print(json.dumps(input_receipt,indent=2));return 0
    output=owned(args.output)
    if output.exists():raise RuntimeError("Refusing to overwrite an existing APK: "+str(output))
    stamp=dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    work=owned(HERE/"work");work.mkdir()
    for name in ["app","classes","generated","dex"]:(work/name).mkdir()
    log_path=work/"build.log"
    def run(label, budget, exe, *arguments):
        with Progress(label, estimate_seconds=budget):
            result=subprocess.run([str(exe),*map(str,arguments)],capture_output=True,encoding="utf-8",errors="replace")
            text=result.stdout+result.stderr
            with log_path.open("a",encoding="utf-8") as log:log.write("\nTOOL "+exe.name+"\n"+text)
            if text.strip():print(text.rstrip(),flush=True)
            if result.returncode:raise RuntimeError(exe.name+" failed: "+str(result.returncode)+"; see "+str(log_path))
            return text
    def copy_app(source):
        for name in ["src","res","assets"]:
            if (source/name).is_dir():shutil.copytree(source/name,work/"app"/name,dirs_exist_ok=True)
        if (source/"AndroidManifest.xml").is_file():shutil.copyfile(source/"AndroidManifest.xml",work/"app/AndroidManifest.xml")
    with Progress("Copy Full app files", estimate_seconds=4):
        copy_app(base)
        if overlay:copy_app(overlay)
    overrides=[]
    def merge_java(source):
        for path in source.rglob("*.java"):
            destination=work/"app/src"/path.relative_to(source)
            if destination.exists() and sha(destination)!=sha(path):
                overrides.append({"path":str(path.relative_to(source)),"frontend_sha256":sha(destination),"runtime_sha256":sha(path)})
            destination.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(path,destination)
    merge_java(runtime/"sdl_java");merge_java(runtime/"java");merge_java(HERE/"java")
    legacy=work/"app/src/it/blinx/demo/v1/GameActivity.java"
    # The old CPU host calls the old TouchControls constructor. It is not part
    # of the accelerated manifest and must not enter this separate build.
    if legacy.exists():
        if any(a.attrib.get(NS+"name","").split(".")[-1]=="GameActivity" for a in activities):
            raise RuntimeError("Combined manifest still launches the legacy CPU GameActivity")
        legacy.unlink()
    (work/"JAVA_OVERRIDES.json").write_text(json.dumps(overrides,indent=2)+"\n",encoding="utf-8")
    run("Compile Full resources", 4, tools/"aapt2.exe","compile","--dir",work/"app/res","-o",work/"resources.zip")
    run("Link Full resources", 4, tools/"aapt2.exe","link","-I",android_jar,"--manifest",work/"app/AndroidManifest.xml",work/"resources.zip","--java",work/"generated","-o",work/"base.apk")
    sources=sorted((work/"app/src").rglob("*.java"))+sorted((work/"generated").rglob("*.java"))
    source_list=work/"javac-sources.txt";source_list.write_text("\n".join('"'+p.as_posix()+'"' for p in sources),encoding="utf-8")
    run("Compile Full Java", 10, java_bin/"javac.exe","--release","8","-encoding","UTF-8","-classpath",android_jar,"-d",work/"classes","@"+str(source_list))
    with zipfile.ZipFile(work/"classes.jar","x",zipfile.ZIP_STORED) as archive:
        for path in sorted((work/"classes").rglob("*.class")):archive.write(path,path.relative_to(work/"classes").as_posix())
    run("Compile Full bytecode", 10, java_bin/"java.exe","-cp",tools/"lib/d8.jar","com.android.tools.r8.D8","--lib",android_jar,"--min-api","28","--output",work/"dex",work/"classes.jar")
    unsigned=work/"unsigned.apk";written=set()
    with zipfile.ZipFile(unsigned,"x") as apk:
        def add_bytes(name,data,compression=zipfile.ZIP_STORED):
            if not safe_name(name) or name in written:raise RuntimeError("Duplicate/unsafe APK path: "+name)
            written.add(name);apk.writestr(name,data,compress_type=compression)
        def add_file(name,path):
            if not safe_name(name) or name in written:raise RuntimeError("Duplicate/unsafe APK path: "+name)
            written.add(name);apk.write(path,name,compress_type=zipfile.ZIP_STORED)
        with zipfile.ZipFile(work/"base.apk") as base_apk:
            for entry in base_apk.infolist():add_bytes(entry.filename.replace("\\","/"),base_apk.read(entry),entry.compress_type)
        for dex in sorted((work/"dex").glob("classes*.dex")):add_file(dex.name,dex)
        for name,path in libraries.items():add_file("lib/arm64-v8a/"+name,path)
        for profile in profiles:
            name = "assets/" + profile["asset"]
            if not safe_name(name) or name in written:
                raise RuntimeError("Duplicate/unsafe APK path: " + name)
            written.add(name)
            path = Path(profile["path"])
            info = zipfile.ZipInfo.from_file(path, name)
            with path.open("rb") as stream, apk.open(info, "w") as target:
                stream_sha256(stream, profile["bytes"], "Embed " + profile["id"] + " disc",
                              destination=target, expected=profile["sha256"])
            props={"id":profile["id"],"bytes":profile["bytes"],"sha256":profile["sha256"],"xbeSha256":profile["xbe_sha256"]}
            add_bytes("assets/discs/"+profile["id"]+".properties",("\n".join(str(k)+"="+str(v) for k,v in props.items())+"\n").encode("ascii"))
        for notice in notices:add_file(notice["apk_entry"],Path(notice["source"]))
        add_bytes("assets/licenses/PRIVATE_COMBINED_NOTICE.txt",b"Private offline Blinx frontend. Native hakuX 1.3.0 libraries match the pinned ARM64 reference hashes. Demo and PAL game data remain proprietary user-supplied material. No redistribution rights are asserted. Library notices and source references accompany this package.\n")
        with Progress("Embed Full assets") as progress:
            assets = []
            for folder in [work / "app/assets", *args.extra_assets]:
                if folder.is_dir():
                    for path in sorted(folder.rglob("*")):
                        if path.is_file() and path.name not in ("game_files.zip", "game_files.properties"):
                            assets.append((folder, path))
            for i, (folder, path) in enumerate(assets, 1):
                add_file("assets/" + path.relative_to(folder).as_posix(), path)
                progress.update(100 * i / len(assets), f"{i}/{len(assets)} files")
    aligned=work/"aligned.apk";signed=work/"signed.apk"
    # Align every uncompressed entry to 16 KiB, including disc data offsets.
    run("Align Full APK", 12, tools/"zipalign.exe","-P","16","-f","16384",unsigned,aligned)
    run("Sign Full APK", 18, java_bin/"java.exe","-jar",tools/"lib/apksigner.jar","sign","--ks",keystore,"--ks-key-alias",args.alias,"--ks-pass","env:BLINX_FULL_STOREPASS",
        "--v1-signing-enabled","true","--v2-signing-enabled","true","--v3-signing-enabled","true","--v4-signing-enabled","false",
        "--alignment-preserved","true","--out",signed,aligned)
    signature=run("Verify Full APK signature", 15, java_bin/"java.exe","-jar",tools/"lib/apksigner.jar","verify","--verbose","--print-certs",signed)
    run("Check Full alignment", 6, tools/"zipalign.exe","-c","-P","16","16384",signed)
    badging=run("Read Full APK identity", 3, tools/"aapt.exe","dump","badging",signed);permissions=run("Check Full permissions", 3, tools/"aapt.exe","dump","permissions",signed)
    if "native-code: 'arm64-v8a'" not in badging or any(x.startswith("uses-permission") for x in permissions.splitlines()):raise RuntimeError("Final ABI/permission check failed")
    offsets=[]
    with zipfile.ZipFile(signed) as apk:
        for profile in profiles:
            entry=apk.getinfo("assets/"+profile["asset"])
            offset=zip_data_offset(apk,signed,entry)
            if entry.compress_type!=zipfile.ZIP_STORED or offset%16384:raise RuntimeError("Disc storage/alignment check failed")
            with apk.open(entry) as stream:
                actual = stream_sha256(stream, profile["bytes"], "Verify embedded " + profile["id"] + " disc", expected=profile["sha256"])
            offsets.append({"id":profile["id"],"asset":entry.filename,"offset":offset,"length":entry.file_size,"stored":True,"aligned_16k":True,"sha256":actual})
        for name,info in native.items():
            entry=apk.getinfo("lib/arm64-v8a/"+name)
            if entry.compress_type!=zipfile.ZIP_STORED or zip_data_offset(apk,signed,entry)%16384:raise RuntimeError("Native entry alignment mismatch")
            with apk.open(entry) as stream:actual=hashlib.file_digest(stream,"sha256").hexdigest()
            if actual!=info["sha256"]:raise RuntimeError("Packaged native library changed")
        for notice in notices:
            if hashlib.sha256(apk.read(notice["apk_entry"])).hexdigest()!=notice["sha256"].lower():raise RuntimeError("Packaged notice changed")
    output.parent.mkdir(parents=True,exist_ok=True)
    size = signed.stat().st_size
    with signed.open("rb") as stream, output.open("xb") as target:
        final_hash = stream_sha256(stream, size, "Copy signed Full APK", destination=target)
    with output.open("rb") as stream:
        stream_sha256(stream, size, "Verify signed Full APK copy", expected=final_hash)
    receipt={**input_receipt,"schema":"blinx-combined-apk/1","utc":stamp,"apk":str(output),"bytes":output.stat().st_size,"sha256":final_hash,
             "disc_apk_entries":offsets,"native_unchanged":True,"signature_verified":True,"all_stored_entries_alignment":16384,
             "build_directory":str(work),"android_device_tested":False,"disc_access":"Private extraction; fd offsets are recorded but not passed to the core"}
    for name,content in [("SIGNATURE.txt",signature),("BADGING.txt",badging),("PERMISSIONS.txt",permissions)]: (work/name).write_text(content,encoding="utf-8")
    (work/"PACKAGE_RECEIPT.json").write_text(json.dumps(receipt,indent=2)+"\n",encoding="utf-8")
    output.with_suffix(".sha256").write_text(final_hash+"  "+output.name+"\n",encoding="ascii")
    print(json.dumps(receipt,indent=2));return 0

if __name__=="__main__":
    try:raise SystemExit(main())
    except (OSError,RuntimeError,ValueError,zipfile.BadZipFile) as error:
        print("ERROR: "+str(error),file=sys.stderr);raise SystemExit(1)
