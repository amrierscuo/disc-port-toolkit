"""Generate private menu audio from user-supplied files. Never ships music."""
from pathlib import Path
import argparse,hashlib,json,shutil,subprocess
def sha(p):
    with p.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
def main():
    ap=argparse.ArgumentParser(description=__doc__)
    for key in ['bremix','boss','output','ffmpeg','ffprobe']:ap.add_argument('--'+key,type=Path,required=True)
    a=ap.parse_args();out=a.output.resolve()
    if out.exists():raise RuntimeError('Choose a new private output directory')
    for p in [a.bremix,a.boss,a.ffmpeg,a.ffprobe]:
        if not p.is_file():raise RuntimeError('Missing explicit input')
        if out==p.resolve() or out in p.resolve().parents:raise RuntimeError('Output overlaps input')
    data=json.loads(subprocess.check_output([str(a.ffprobe),'-v','error','-select_streams','a:0','-show_entries','stream=sample_rate','-of','json',str(a.boss)]))
    rate=int(data['streams'][0]['sample_rate']);cut=5*rate
    out.mkdir(parents=True);music=out/'menu_music';music.mkdir()
    shutil.copyfile(a.bremix,music/'Bremix.mp3')
    subprocess.run([str(a.ffmpeg),'-v','error','-i',str(a.boss),'-vn','-af',f'atrim=start_sample={cut},asetpts=PTS-STARTPTS','-c:a','flac',str(music/'boss_E_from_5s.flac')],check=True)
    (out/'AUDIO_RECIPE.json').write_text(json.dumps(dict(cut_seconds=5,sample_rate=rate,cut_sample=cut,outputs={p.name:sha(p) for p in music.iterdir()},private_outputs=True,reference_MP3_bit_identity_claim=False),indent=2)+'\n')
if __name__=='__main__':main()
