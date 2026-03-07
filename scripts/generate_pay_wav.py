#!/usr/bin/env python3
"""
SonicVault merchant terminal script — generate Solana Pay WAV for acoustic playback.

Usage:
  python generate_pay_wav.py --recipient <base58> [--amount 0.5] [--label "Booth 42"]
  python generate_pay_wav.py --recipient <base58> --no-play   # Save WAV only, no playback

Output: pay_request.wav

Protocol: AUDIBLE_FAST (protocolId=1) for reliability (~5–6s for ~88B URI).
URIs include memo=ts:{unix} for replay protection; valid 60s. Regenerate every 45s in loop.
"""

import argparse
import struct
import sys
import time
import wave
from urllib.parse import urlencode

try:
    import ggwave
except ImportError:
    print("Install ggwave: pip install ggwave", file=sys.stderr)
    sys.exit(1)

SAMPLE_RATE = 48000
EXPIRY_SECONDS = 60
REGENERATE_INTERVAL_SECS = 45
PLAYS_PER_CYCLE = 10


def build_solana_pay_uri(recipient: str, amount: float | None = None, label: str | None = None) -> str:
    """Build Solana Pay URI: solana:<recipient>?amount=&label=&memo=ts:{unix}"""
    params = {}
    if amount is not None:
        params["amount"] = str(amount)
    if label:
        params["label"] = label
    params["memo"] = f"ts:{int(time.time())}"
    query = urlencode(params)
    return f"solana:{recipient}?{query}" if query else f"solana:{recipient}"


def generate_wav(uri: str, protocol_id: int = 1, volume: int = 50) -> bytes:
    """Encode URI as ggwave WAV. Returns raw WAV bytes if ggwave provides them, else None."""
    waveform = ggwave.encode(uri.encode("utf-8"), protocolId=protocol_id, volume=volume)
    return waveform


def save_wav_from_ggwave(waveform: bytes, output_path: str) -> None:
    """Convert ggwave float32 output to WAV and save."""
    samples = struct.unpack(f"{len(waveform) // 4}f", waveform)
    int16_samples = [int(max(-1, min(1, s)) * 32767) for s in samples]
    with wave.open(output_path, "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(SAMPLE_RATE)
        wav.writeframes(struct.pack(f"{len(int16_samples)}h", *int16_samples))


def play_wav_aplay(path: str) -> bool:
    """Play on Linux via aplay (RPi / Ubuntu)."""
    import subprocess
    result = subprocess.run(["aplay", path], capture_output=True)
    return result.returncode == 0


def play_wav_afplay(path: str) -> bool:
    """Play on macOS via afplay."""
    import subprocess
    result = subprocess.run(["afplay", path], capture_output=True)
    return result.returncode == 0


def detect_player():
    """Detect appropriate WAV player for this OS."""
    if sys.platform.startswith("linux"):
        return play_wav_aplay
    elif sys.platform == "darwin":
        return play_wav_afplay
    return play_wav_aplay


def main():
    parser = argparse.ArgumentParser(description="SonicVault merchant terminal — generate Solana Pay WAV")
    parser.add_argument("--recipient", required=True, help="Solana recipient (base58)")
    parser.add_argument("--amount", type=float, default=0.5, help="SOL amount")
    parser.add_argument("--label", default="Conference Booth 42", help="Merchant label")
    parser.add_argument("--output", default="pay_request.wav", help="Output WAV path")
    parser.add_argument("--volume", type=int, default=50, help="Volume 0-100")
    parser.add_argument("--no-play", action="store_true", help="Save WAV only, do not play")
    args = parser.parse_args()

    play_fn = detect_player() if not args.no_play else None

    print(f"[Terminal] SonicVault Merchant Terminal")
    print(f"[Terminal] Recipient: {args.recipient[:8]}…{args.recipient[-4:]}")
    print(f"[Terminal] Amount: {args.amount} SOL")
    print(f"[Terminal] Label: {args.label}")
    print(f"[Terminal] Regenerate: every {REGENERATE_INTERVAL_SECS}s")
    print(f"[Terminal] Playing: {'disabled' if args.no_play else 'enabled'}")
    print()

    cycle = 0
    while True:
        uri = build_solana_pay_uri(args.recipient, args.amount, args.label)
        ts = int(time.time())
        print(f"[Terminal] Encoding {len(uri)} bytes, ts={ts}, valid {EXPIRY_SECONDS}s")

        waveform = generate_wav(uri, protocol_id=1, volume=args.volume)
        save_wav_from_ggwave(waveform, args.output)
        print(f"[Terminal] Wrote {args.output}")

        if args.no_play:
            print("[Terminal] --no-play set. Exiting after first generation.")
            break

        cycle_start = time.time()
        plays = 0
        while time.time() - cycle_start < REGENERATE_INTERVAL_SECS and plays < PLAYS_PER_CYCLE:
            print(f"[Terminal] ▶ Play {plays + 1} (cycle {cycle + 1})")
            play_fn(args.output)
            plays += 1

        cycle += 1
        print(f"[Terminal] Cycle {cycle} complete ({plays} plays). Regenerating…\n")


if __name__ == "__main__":
    main()
