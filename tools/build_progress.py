"""Compact, phase-local console progress for build tools. stdout stays available for results."""
import hashlib
import math
import shutil
import sys
import threading
import time


class Progress:
    def __init__(self, label, estimate_seconds=None, *, stream=None):
        self.label = label
        self.duration = estimate_seconds
        self.stream = sys.stderr if stream is None else stream
        self.interactive = self.stream.isatty()
        self.percent = 0
        self.estimated = estimate_seconds is not None
        self.detail = ""
        self.lock = threading.Lock()
        self.stop = threading.Event()
        self.last_render = None
        self.last_width = 0

    def __enter__(self):
        self.started = time.monotonic()
        self._render(force=True)
        self.worker = threading.Thread(target=self._tick, name="build-progress", daemon=True)
        self.worker.start()
        return self

    def _tick(self):
        while not self.stop.wait(1):
            with self.lock:
                self._render()

    def update(self, percent, detail="", estimated=False):
        with self.lock:
            value = min(99, max(0, int(percent)))
            if value >= self.percent:
                self.percent, self.estimated = value, estimated
            if detail:
                self.detail = detail
            self._render()

    def set_detail(self, detail):
        with self.lock:
            self.detail = detail
            self._render()

    def _render(self, *, force=False, final=""):
        now = time.monotonic()
        interval = 1 if self.interactive else 5
        if not force and self.last_render is not None and now - self.last_render < interval:
            return
        elapsed = max(0, now - self.started)
        if not final and self.estimated and self.duration:
            # Approach the end gradually when a quiet tool runs longer than expected.
            estimate = int(99 * -math.expm1(-2 * elapsed / self.duration))
            self.percent = max(self.percent, min(98, estimate))
        filled = self.percent // 5
        meter = "#" * filled + "-" * (20 - filled)
        marker = "~" if self.estimated else ""
        mins, secs = divmod(int(elapsed), 60)
        line = f"{self.label} [{meter}] {marker}{self.percent}%  {mins}:{secs:02d}"
        detail = final or self.detail
        if detail:
            line += "  " + " ".join(str(detail).split())
        if self.interactive:
            width = max(1, shutil.get_terminal_size(fallback=(100, 24)).columns - 1)
            line = line[:width]
            prefix, suffix = "\r", "\n" if final else ""
            padding = " " * max(0, min(self.last_width, width) - len(line))
        else:
            prefix, suffix, padding = "", "\n", ""
        self.stream.write(prefix + line + padding + suffix)
        self.stream.flush()
        self.last_width, self.last_render = len(line), now

    def __exit__(self, exc_type, exc, traceback):
        self.stop.set()
        self.worker.join()
        with self.lock:
            if exc_type is None:
                self.percent, self.estimated = 100, False
            final = "done" if exc_type is None else "interrupted" if exc_type is KeyboardInterrupt else "failed"
            self._render(force=True, final=final)


def stream_sha256(stream, size, label, *, destination=None, expected=None):
    """Hash, optionally copy, and verify a binary stream while counting its bytes."""
    digest = hashlib.sha256()
    count = 0
    with Progress(label) as progress:
        while block := stream.read(1024 * 1024):
            if destination is not None:
                destination.write(block)
            count += len(block)
            digest.update(block)
            progress.update(100 * count / max(1, size),
                            f"{count // 1048576}/{size // 1048576} MiB")
        if count != size:
            raise ValueError(f"{label}: input size changed while reading")
        actual = digest.hexdigest()
        if expected is not None and actual != expected:
            raise ValueError(f"{label}: SHA-256 mismatch")
    return actual
