"""Compatibility entry point for the previous Android source review command."""
import runpy
from pathlib import Path

if __name__ == "__main__":
    runpy.run_path(str(Path(__file__).with_name("verify_repository.py")), run_name="__main__")
