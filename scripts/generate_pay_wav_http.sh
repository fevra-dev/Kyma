#!/bin/bash
# Fallback: generate Solana Pay WAV via ggwave-to-file HTTP service.
# Use when: pip install ggwave fails (e.g. Python 3.12).
#
# Usage: ./generate_pay_wav_http.sh <recipient> [amount] [label]
# Example: ./generate_pay_wav_http.sh HN7cABqLq46Es1jh92dQQisAq662SmxELLLsHHe4YWrH 0.1 "Test Booth"

set -e
RECIPIENT="${1:?Usage: $0 <recipient_base58> [amount] [label]}"
AMOUNT="${2:-0.5}"
LABEL="${3:-Test Booth}"
OUTPUT="${4:-pay_request.wav}"

RESULT=$(RECIPIENT="$RECIPIENT" AMOUNT="$AMOUNT" LABEL="$LABEL" python3 << 'PYEOF'
import os, urllib.parse, time
r, a, l = os.environ["RECIPIENT"], os.environ["AMOUNT"], os.environ["LABEL"]
ts = int(time.time())
uri = f"solana:{r}?amount={a}&label={urllib.parse.quote(l)}&memo=ts:{ts}"
enc = urllib.parse.quote(uri)
print(f"https://ggwave-to-file.ggerganov.com/?m={enc}&p=1&s=48000&v=50")
PYEOF
)

echo "[HTTP] SonicVault Pay by Ear — generate WAV"
echo "[HTTP] Recipient: ${RECIPIENT:0:8}…${RECIPIENT: -4}"
echo "[HTTP] Amount: ${AMOUNT} SOL"
echo "[HTTP] Label: ${LABEL}"
echo ""

curl -sS "$RESULT" -o "$OUTPUT"
echo "[HTTP] Wrote $OUTPUT"
echo "[HTTP] Play: afplay $OUTPUT"
