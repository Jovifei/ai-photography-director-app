"""Legacy test entry point delegates to the single real typed-input suite."""
from pathlib import Path
import subprocess
import sys
if __name__ == "__main__":
    raise SystemExit(subprocess.call([sys.executable, str(Path(__file__).with_name("test_t14_bundle_preflight_contract.py"))]))
