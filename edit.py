#!/usr/bin/env python3
"""Patrick Jane edit oluşturucu.

Bir zaman çizelgesi dosyasındaki (ör. jane_edit.txt) klipleri keser, efekt ve
yazı ekler, müziğin vuruşlarına göre arka arkaya dizer ve tek bir video üretir.

Kullanım:
    python3 edit.py jane_edit.txt                 # videoyu oluştur
    python3 edit.py jane_edit.txt --kontrol       # sadece kontrol et, zamanlamayı göster
    python3 edit.py jane_edit.txt --hizli         # düşük çözünürlüklü hızlı önizleme
"""

import argparse
import os
import re
import shutil
import subprocess
import sys
import tempfile
import textwrap
from dataclasses import dataclass, field

FORMATS = {
    "dikey": (1080, 1920),  # TikTok / Reels / Shorts
    "yatay": (1920, 1080),  # YouTube
    "kare": (1080, 1080),   # Instagram gönderisi
}

COLOR_EFFECTS = {
    "bw": "hue=s=0",
    "sepia": "colorchannelmixer=.393:.769:.189:0:.349:.686:.168:0:.272:.534:.131:0",
    "dark": "eq=contrast=1.15:brightness=-0.04:saturation=0.8,vignette=PI/5",
}
EFFECTS = set(COLOR_EFFECTS) | {"slow", "zoom", "flash", "fadein", "fadeout"}
SETTING_RE = re.compile(r"^[A-Za-z_]+\s*=")


class TimelineError(Exception):
    pass


@dataclass
class Clip:
    line: int
    src: str
    start: float
    length: float
    beats: float | None
    effects: list = field(default_factory=list)
    caption: str = ""
    frames: int = 0
    out_start: float = 0.0


@dataclass
class Timeline:
    music: str | None = None
    music_start: float = 0.0
    bpm: float | None = None
    fmt: str = "dikey"
    fps: int = 30
    font: str | None = None
    clips: list = field(default_factory=list)


def parse_time(text, line):
    """'1:02:03.5', '12:31', '75.2' -> saniye."""
    text = text.strip()
    try:
        parts = [float(p) for p in text.split(":")]
    except ValueError:
        raise TimelineError(f"satır {line}: zaman anlaşılamadı: '{text}'")
    if not 1 <= len(parts) <= 3:
        raise TimelineError(f"satır {line}: zaman anlaşılamadı: '{text}'")
    seconds = 0.0
    for p in parts:
        seconds = seconds * 60 + p
    return seconds


def parse_length(text, bpm, line):
    """'4b' -> 4 vuruş (bpm gerekir), '2.5' -> 2.5 saniye. Döner: (saniye, vuruş)."""
    text = text.strip().lower()
    if text.endswith("b"):
        if not bpm:
            raise TimelineError(f"satır {line}: '{text}' vuruş cinsinden ama 'bpm' ayarı yok")
        try:
            beats = float(text[:-1])
        except ValueError:
            raise TimelineError(f"satır {line}: süre anlaşılamadı: '{text}'")
        return beats * 60.0 / bpm, beats
    return parse_time(text, line), None


def parse_timeline(path):
    tl = Timeline()
    base = os.path.dirname(os.path.abspath(path))
    raw_clips = []

    with open(path, encoding="utf-8") as f:
        for n, raw in enumerate(f, 1):
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            if not SETTING_RE.match(line):
                if "|" not in line:
                    raise TimelineError(f"satır {n}: ne ayar ne klip: '{line}'")
                raw_clips.append((n, line))
                continue
            key, value = (s.strip() for s in line.split("=", 1))
            value = value.split(" #", 1)[0].strip()
            key = key.lower()
            if key == "muzik":
                tl.music = os.path.join(base, value) if value else None
            elif key == "muzik_baslangic":
                tl.music_start = parse_time(value, n)
            elif key == "bpm":
                tl.bpm = float(value) if value else None
            elif key == "format":
                if value not in FORMATS:
                    raise TimelineError(f"satır {n}: format {', '.join(FORMATS)} olmalı")
                tl.fmt = value
            elif key == "fps":
                tl.fps = int(value)
            elif key == "font":
                tl.font = value or None
            else:
                raise TimelineError(f"satır {n}: bilinmeyen ayar '{key}'")

    # Klipler ayarlardan sonra çözülür ki 'bpm' dosyanın neresinde olursa olsun geçerli olsun.
    for n, line in raw_clips:
        cols = [c.strip() for c in line.split("|")]
        if len(cols) < 3:
            raise TimelineError(f"satır {n}: en az 'dosya | başlangıç | süre' gerekli")
        cols += [""] * (5 - len(cols))
        src, start, length, effects, caption = cols[:4] + ["|".join(cols[4:]).strip()]
        seconds, beats = parse_length(length, tl.bpm, n)
        if seconds <= 0:
            raise TimelineError(f"satır {n}: süre sıfırdan büyük olmalı")
        fx = [e.strip().lower() for e in effects.split("+") if e.strip() and e.strip() != "-"]
        unknown = [e for e in fx if e not in EFFECTS]
        if unknown:
            raise TimelineError(
                f"satır {n}: bilinmeyen efekt {unknown}; kullanılabilir: {', '.join(sorted(EFFECTS))}"
            )
        tl.clips.append(Clip(n, os.path.join(base, src), parse_time(start, n), seconds, beats, fx, caption))

    if not tl.clips:
        raise TimelineError("zaman çizelgesinde hiç klip yok")

    # Kesim noktalarını kümülatif olarak kareye yuvarla: kayma birikmez, müzikle senkron kalır.
    elapsed, frames_done = 0.0, 0
    for c in tl.clips:
        c.out_start = frames_done / tl.fps
        elapsed += c.length
        end = round(elapsed * tl.fps)
        c.frames = end - frames_done
        frames_done = end
        if c.frames <= 0:
            raise TimelineError(f"satır {c.line}: süre bir kareden kısa")
    return tl


def probe_duration(path):
    try:
        out = subprocess.run(
            ["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", path],
            capture_output=True, text=True, check=True,
        ).stdout.strip()
        return float(out)
    except (subprocess.CalledProcessError, ValueError):
        return None


def find_font(tl):
    if tl.font:
        return tl.font
    if shutil.which("fc-match"):
        for name in ("DejaVu Sans:bold", "Arial:bold", "sans:bold"):
            out = subprocess.run(["fc-match", "-f", "%{file}", name], capture_output=True, text=True).stdout
            if out and os.path.exists(out):
                return out
    return None


def fmt_time(seconds):
    m, s = divmod(seconds, 60)
    return f"{int(m)}:{s:05.2f}"


def check(tl):
    """Dosyaları doğrular, uyarıları döndürür. Eksik dosya varsa TimelineError."""
    missing = sorted({c.src for c in tl.clips if not os.path.exists(c.src)})
    if tl.music and not os.path.exists(tl.music):
        missing.append(tl.music)
    if missing:
        raise TimelineError("bulunamayan dosyalar:\n  " + "\n  ".join(missing))

    warnings = []
    durations = {}
    for c in tl.clips:
        if c.src not in durations:
            durations[c.src] = probe_duration(c.src)
        total = durations[c.src]
        need = c.frames / tl.fps / (2 if "slow" in c.effects else 1)
        if total is not None and c.start + need > total + 0.05:
            warnings.append(
                f"satır {c.line}: kaynak {fmt_time(total)} uzunluğunda, klip sonuna kadar yetmiyor "
                "(son kare dondurulacak)"
            )
    if tl.music:
        music_len = probe_duration(tl.music)
        video_len = sum(c.frames for c in tl.clips) / tl.fps
        if music_len is not None and tl.music_start + video_len > music_len:
            warnings.append("müzik videodan önce bitiyor")
    return warnings


def print_table(tl):
    print(f"{'#':>3}  {'videoda':>8}  {'şarkıda':>8}  {'süre':>6}  {'vuruş':>5}  {'efekt':<14}  kaynak @ başlangıç / yazı")
    for i, c in enumerate(tl.clips, 1):
        beats = f"{c.beats:g}" if c.beats is not None else "-"
        fx = "+".join(c.effects) or "-"
        print(
            f"{i:>3}  {fmt_time(c.out_start):>8}  {fmt_time(tl.music_start + c.out_start):>8}  "
            f"{c.frames / tl.fps:>5.2f}s  {beats:>5}  {fx:<14}  "
            f"{os.path.basename(c.src)} @ {fmt_time(c.start)}" + (f"  “{c.caption}”" if c.caption else "")
        )
    total = sum(c.frames for c in tl.clips) / tl.fps
    print(f"\nToplam: {fmt_time(total)} ({len(tl.clips)} klip, {tl.fmt}, {tl.fps} fps)")


def caption_filters(clip, w, h, font, workdir, index):
    size = round(min(w, h) / 16)
    width = 22 if w < h else 40
    lines = textwrap.wrap(clip.caption, width) or []
    filters = []
    y0 = h * 0.70
    for j, text in enumerate(lines):
        # Metin dosyadan okunur; böylece ':' , "'" ve Türkçe karakterler kaçış gerektirmez.
        path = os.path.join(workdir, f"cap_{index}_{j}.txt")
        with open(path, "w", encoding="utf-8") as f:
            f.write(text)
        filters.append(
            f"drawtext=fontfile='{font}':textfile='{path}':fontcolor=white:fontsize={size}"
            f":borderw={max(2, size // 14)}:bordercolor=black@0.85"
            f":x=(w-text_w)/2:y={y0 + j * size * 1.25:.0f}:alpha='min(1,t/0.25)'"
        )
    return filters


def build_filter(clip, w, h, fps, font, workdir, index):
    dur = clip.frames / fps
    f = []
    if "slow" in clip.effects:
        f.append("setpts=2.0*PTS")
    f.append(f"fps={fps}")
    f.append(f"scale={w}:{h}:force_original_aspect_ratio=increase,crop={w}:{h},setsar=1")
    # Kaynak kısa kalırsa son kareyi dondur; segment her zaman tam uzunlukta olsun.
    f.append(f"tpad=stop_mode=clone:stop_duration={dur:.3f}")
    for name in clip.effects:
        if name in COLOR_EFFECTS:
            f.append(COLOR_EFFECTS[name])
    if "zoom" in clip.effects:
        # Önce büyütmek zoompan'ın titremesini azaltır.
        f.append(
            f"scale={w * 2}:{h * 2},zoompan=z='min(1+0.0015*on,1.25)':d=1"
            f":x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':s={w}x{h}:fps={fps}"
        )
    fade = min(0.5, dur / 2)
    if "flash" in clip.effects:
        f.append(f"fade=t=in:st=0:d={min(0.2, dur / 2):.3f}:color=white")
    if "fadein" in clip.effects:
        f.append(f"fade=t=in:st=0:d={fade:.3f}")
    if "fadeout" in clip.effects:
        f.append(f"fade=t=out:st={dur - fade:.3f}:d={fade:.3f}")
    if clip.caption and font:
        f += caption_filters(clip, w, h, font, workdir, index)
    f.append("format=yuv420p")
    return ",".join(f)


def run(cmd, verbose):
    if verbose:
        print("  $ " + " ".join(cmd))
    result = subprocess.run(cmd, capture_output=not verbose, text=True)
    if result.returncode != 0:
        sys.stderr.write((result.stderr or "")[-3000:])
        raise SystemExit(f"\nffmpeg hata verdi (komut: {' '.join(cmd[:6])} ...)")


def render(tl, output, fast, verbose, keep):
    w, h = FORMATS[tl.fmt]
    if fast:
        w, h = w // 2, h // 2
    preset = "ultrafast" if fast else "veryfast"
    font = find_font(tl)
    if not font and any(c.caption for c in tl.clips):
        print("uyarı: font bulunamadı, yazılar eklenmeyecek ('font = /yol/font.ttf' ayarını kullan)")

    workdir = tempfile.mkdtemp(prefix="jane_edit_")
    try:
        segments = []
        for i, c in enumerate(tl.clips, 1):
            print(f"[{i}/{len(tl.clips)}] {os.path.basename(c.src)} @ {fmt_time(c.start)}"
                  + (f" ({'+'.join(c.effects)})" if c.effects else ""))
            seg = os.path.join(workdir, f"seg_{i:03d}.mp4")
            run([
                "ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
                "-ss", f"{c.start:.3f}", "-i", c.src,
                "-vf", build_filter(c, w, h, tl.fps, font, workdir, i),
                "-frames:v", str(c.frames), "-an",
                "-c:v", "libx264", "-preset", preset, "-crf", "18",
                "-r", str(tl.fps), "-video_track_timescale", str(tl.fps * 512),
                seg,
            ], verbose)
            segments.append(seg)

        concat_list = os.path.join(workdir, "list.txt")
        with open(concat_list, "w", encoding="utf-8") as f:
            f.writelines(f"file '{s}'\n" for s in segments)
        video = os.path.join(workdir, "video.mp4")
        print("Klipler birleştiriliyor...")
        run(["ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
             "-f", "concat", "-safe", "0", "-i", concat_list, "-c", "copy", video], verbose)

        total = sum(c.frames for c in tl.clips) / tl.fps
        cmd = ["ffmpeg", "-y", "-hide_banner", "-loglevel", "error", "-i", video]
        if tl.music:
            print("Müzik ekleniyor...")
            fade_out = min(1.5, total / 4)
            cmd += [
                "-ss", f"{tl.music_start:.3f}", "-i", tl.music,
                "-map", "0:v", "-map", "1:a", "-c:v", "copy",
                "-c:a", "aac", "-b:a", "192k",
                "-af", f"afade=t=in:st=0:d=0.05,afade=t=out:st={total - fade_out:.3f}:d={fade_out:.3f}",
                "-t", f"{total:.3f}",
            ]
        else:
            cmd += ["-c", "copy"]
        cmd += ["-movflags", "+faststart", output]
        run(cmd, verbose)
    finally:
        if keep:
            print(f"Ara dosyalar: {workdir}")
        else:
            shutil.rmtree(workdir, ignore_errors=True)

    print(f"\nHazır: {output} ({fmt_time(total)}, {w}x{h})")


def main():
    p = argparse.ArgumentParser(description="Zaman çizelgesinden müzikli fan edit videosu üretir.")
    p.add_argument("timeline", help="zaman çizelgesi dosyası (ör. jane_edit.txt)")
    p.add_argument("-o", "--cikti", default="jane_edit.mp4", help="çıktı dosyası (varsayılan: jane_edit.mp4)")
    p.add_argument("--kontrol", action="store_true", help="video üretme; dosyaları ve zamanlamayı kontrol et")
    p.add_argument("--hizli", action="store_true", help="yarı çözünürlükte hızlı önizleme")
    p.add_argument("--ayrinti", action="store_true", help="ffmpeg komutlarını ve çıktısını göster")
    p.add_argument("--ara-dosyalari-tut", action="store_true", help="geçici klip dosyalarını silme")
    args = p.parse_args()

    for tool in ("ffmpeg", "ffprobe"):
        if not shutil.which(tool):
            sys.exit(f"'{tool}' bulunamadı. Önce FFmpeg'i kur: https://ffmpeg.org/download.html")

    try:
        tl = parse_timeline(args.timeline)
        print_table(tl)
        warnings = check(tl)
    except (TimelineError, OSError) as e:
        sys.exit(f"hata: {e}")
    for w in warnings:
        print(f"uyarı: {w}")
    if args.kontrol:
        print("\nKontrol tamam." if not warnings else "\nKontrol tamam (uyarılara bak).")
        return
    print()
    render(tl, args.cikti, args.hizli, args.ayrinti, args.ara_dosyalari_tut)


if __name__ == "__main__":
    main()
