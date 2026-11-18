"""JVM filesystem checks of the real DiscInstaller; not Android device tests."""
from pathlib import Path
import shutil
import uuid
import subprocess

HERE=Path(__file__).resolve().parent
STUBS={
"android/content/Context.java":'''package android.content; public class Context {
private final java.io.File root; public Context(java.io.File root){this.root=root;}
public java.io.File getFilesDir(){return new java.io.File(root,"files");}
public android.content.res.AssetManager getAssets(){return new android.content.res.AssetManager(new java.io.File(root,"assets"));}}''',
"android/content/res/AssetManager.java":'''package android.content.res; public class AssetManager {
private final java.io.File root; public AssetManager(java.io.File root){this.root=root;}
public java.io.InputStream open(String name)throws java.io.IOException{return new java.io.FileInputStream(new java.io.File(root,name));}}''',
"android/os/StatFs.java":'''package android.os; public class StatFs { public static long available=Long.MAX_VALUE;
public StatFs(String path){} public long getAvailableBytes(){return available;}}''',
"android/system/Os.java":'''package android.system; public class Os {
public static void rename(String from,String to)throws java.io.IOException {java.nio.file.Files.move(java.nio.file.Paths.get(from),java.nio.file.Paths.get(to),java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE);}}''',
}
HARNESS=r'''package it.blinx.demo.v1;
import java.io.*; import java.nio.file.*; import java.util.*; import java.security.*;
import android.content.Context;
public class DiscInstallerTest {
static int passed; static Path root;
static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
static void pass(String label){System.out.println("PASS "+label);passed++;}
static byte[] bytes(int value){byte[] b=new byte[65536];Arrays.fill(b,(byte)value);return b;}
static String hash(byte[] b)throws Exception{StringBuilder out=new StringBuilder();for(byte v:MessageDigest.getInstance("SHA-256").digest(b))out.append(String.format("%02x",v&255));return out.toString();}
static Context context(String name)throws Exception{Path p=root.resolve(name);Files.createDirectories(p.resolve("files"));Files.createDirectories(p.resolve("assets/discs"));return new Context(p.toFile());}
static Path asset(Context c,String id,String ext){return c.getFilesDir().toPath().getParent().resolve("assets/discs/"+id+ext);}
static void bundle(Context c,String id,byte[] b)throws Exception{Files.write(asset(c,id,".iso"),b);Properties p=new Properties();p.setProperty("id",id);p.setProperty("bytes",""+b.length);p.setProperty("sha256",hash(b));try(OutputStream o=Files.newOutputStream(asset(c,id,".properties"))){p.store(o,"");}}
static void fail(Context c,String id)throws Exception{boolean denied=false;try{DiscInstaller.prepare(c,id,(m,p)->{});}catch(Exception expected){denied=true;}check(denied,"Expected failure: "+id);}
public static void main(String[] args)throws Exception{
root=Paths.get(args[0]);Context c=context("main");bundle(c,"demo",bytes(1));
final int[] progress={0};DiscInstaller.Paths demo=DiscInstaller.prepare(c,"demo",(m,p)->progress[0]=p);
check(progress[0]==100&&Arrays.equals(bytes(1),Files.readAllBytes(Paths.get(demo.discPath))),"Disc copy mismatch");pass("copy_hash_and_progress");
Files.delete(asset(c,"demo",".iso"));check(DiscInstaller.isReady(c,"demo"),"Marker not ready");DiscInstaller.prepare(c,"demo",(m,p)->{});pass("matching_marker_avoids_asset_read");
Path marker=Paths.get(demo.dataRoot,".disc.sha256");Files.delete(marker);android.os.StatFs.available=1;
DiscInstaller.prepare(c,"demo",(m,p)->{});
check(DiscInstaller.isReady(c,"demo")&&Arrays.equals(bytes(1),Files.readAllBytes(Paths.get(demo.discPath))),"Missing marker recovery failed");pass("missing_marker_recovered_without_asset_or_spare_space");
Files.write(marker,"stale".getBytes("US-ASCII"));DiscInstaller.prepare(c,"demo",(m,p)->{});
check(DiscInstaller.isReady(c,"demo"),"Stale marker recovery failed");pass("stale_marker_recovered");
Files.delete(marker);Files.write(Paths.get(demo.discPath),bytes(7));fail(c,"demo");
check(!Files.exists(marker),"Incorrect disc received a readiness marker");pass("mismatched_local_disc_rejected");
Files.write(Paths.get(demo.discPath),bytes(1));DiscInstaller.prepare(c,"demo",(m,p)->{});android.os.StatFs.available=Long.MAX_VALUE;
Path saves=Paths.get(demo.hddPath,"saves");Files.createDirectories(saves);Files.write(saves.resolve("sentinel"),bytes(9));
Path hle=Paths.get(demo.hlePath);Files.createDirectories(hle);Files.write(hle.resolve("sentinel"),bytes(8));
bundle(c,"pal",bytes(2));DiscInstaller.Paths pal=DiscInstaller.prepare(c,"pal",(m,p)->{});
check(!pal.dataRoot.equals(demo.dataRoot),"Editions share paths");check(Arrays.equals(bytes(1),Files.readAllBytes(Paths.get(demo.discPath))),"PAL replaced Demo");pass("editions_have_independent_discs");
bundle(c,"demo",bytes(3));Files.write(asset(c,"demo",".iso"),bytes(4));fail(c,"demo");
check(Arrays.equals(bytes(1),Files.readAllBytes(Paths.get(demo.discPath))),"Bad update replaced old disc");check(!Files.exists(Paths.get(demo.dataRoot,"disc.iso.part")),"Partial remains");pass("corrupt_update_preserves_previous_disc");
check(Arrays.equals(bytes(9),Files.readAllBytes(saves.resolve("sentinel")))&&Arrays.equals(bytes(8),Files.readAllBytes(hle.resolve("sentinel"))),"HDD/HLE touched");pass("hdd_hle_preserved");
bundle(c,"demo",bytes(5));android.os.StatFs.available=1;fail(c,"demo");android.os.StatFs.available=Long.MAX_VALUE;
check(Arrays.equals(bytes(1),Files.readAllBytes(Paths.get(demo.discPath))),"Low-space update touched disc");pass("insufficient_space_preserves_previous_disc");
Files.write(Paths.get(demo.dataRoot,"disc.iso.part"),new byte[]{7});DiscInstaller.prepare(c,"demo",(m,p)->{});check(Arrays.equals(bytes(5),Files.readAllBytes(Paths.get(demo.discPath))),"Interrupted retry failed");pass("interrupted_copy_recovers");
for(String id:new String[]{"../pal","PAL","unknown","demo/../../outside"})fail(c,id);pass("edition_whitelist");
System.out.println("PASSED="+passed);}}
'''

def exercise(work):
    source = work / "src"
    classes = work / "classes"
    classes.mkdir()
    for name, text in STUBS.items():
        path = source / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
    harness = source / "it/blinx/demo/v1/DiscInstallerTest.java"
    harness.parent.mkdir(parents=True)
    harness.write_text(HARNESS, encoding="utf-8")
    actual = HERE / "java/it/blinx/demo/v1/DiscInstaller.java"
    subprocess.run([shutil.which("javac") or "javac", "--release", "8", "-encoding", "UTF-8",
                    "-d", str(classes), *map(str, source.rglob("*.java")), str(actual)], check=True)
    run = subprocess.run([shutil.which("java") or "java", "-cp", str(classes),
                          "it.blinx.demo.v1.DiscInstallerTest", str(work / "cases")],
                         capture_output=True, text=True, check=True)
    print(run.stdout)
    if "PASSED=11" not in run.stdout.splitlines():
        raise RuntimeError("The JVM harness did not execute all 11 filesystem checks.")

if __name__ == "__main__":
    root = Path(__file__).resolve().parents[5]
    parent = root / "build/android-full/tests"
    parent.mkdir(parents=True, exist_ok=True)
    scratch = parent / uuid.uuid4().hex
    scratch.mkdir()
    exercise(scratch)
