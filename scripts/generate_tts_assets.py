#!/usr/bin/env python3
"""Generate pinned offline TTS assets from upstream source files.

The generated JSON is placed in app/src/main/assets and bundled into the APK.
No runtime network access is required for lexicon or phoneme-map lookup.
"""
from __future__ import annotations

import json
import re
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app" / "src" / "main" / "assets"
CMUDICT_REV = "0f8072f814306c5ee4fbf992ed853601b12c01f9"
FREQUENCY_REV = "072bbed282316a23651aa7068c7173aa7898cf80"
PIPER_VOICES_REV = "bae641dcb5a608cff81ccaf7ff018baaca1a33e1"
CMUDICT_URL = f"https://raw.githubusercontent.com/cmusphinx/cmudict/{CMUDICT_REV}/cmudict.dict"
FREQUENCY_URL = f"https://raw.githubusercontent.com/hermitdave/FrequencyWords/{FREQUENCY_REV}/content/2018/en/en_50k.txt"
LJSPEECH_MEDIUM_CONFIG_URL = (
    f"https://huggingface.co/rhasspy/piper-voices/resolve/{PIPER_VOICES_REV}/"
    "en/en_US/ljspeech/medium/en_US-ljspeech-medium.onnx.json"
)
LJSPEECH_HIGH_CONFIG_URL = (
    f"https://huggingface.co/rhasspy/piper-voices/resolve/{PIPER_VOICES_REV}/"
    "en/en_US/ljspeech/high/en_US-ljspeech-high.onnx.json"
)
SUPPORTED_ARPABET = {
    "AA", "AE", "AH", "AO", "AW", "AY", "B", "CH", "D", "DH", "EH", "ER",
    "EY", "F", "G", "HH", "IH", "IY", "JH", "K", "L", "M", "N", "NG",
    "OW", "OY", "P", "R", "S", "SH", "T", "TH", "UH", "UW", "V", "W",
    "Y", "Z", "ZH", "DX", "EL", "EM", "EN", "NX", "Q", "AX", "IX", "UX", "WH"
}
WORD_RE = re.compile(r"^[a-z]+(?:'[a-z]+)?$")


def fetch(url: str) -> str:
    request = urllib.request.Request(
        url, headers={"User-Agent": "PHANTOM-GHOST-TTS-asset-builder/1.0"}
    )
    with urllib.request.urlopen(request, timeout=90) as response:
        return response.read().decode("utf-8")


def parse_cmudict(raw: str) -> dict[str, list[str]]:
    entries: dict[str, list[str]] = {}
    for line in raw.splitlines():
        if not line or line.startswith(";;;"):
            continue
        parts = line.split()
        if len(parts) < 2:
            continue
        word = re.sub(r"\(\d+\)$", "", parts[0]).lower().replace("’", "'")
        if WORD_RE.fullmatch(word) and word not in entries:
            entries[word] = parts[1:]
    return entries


def main() -> None:
    print("Fetching pinned CMUdict and English frequency list…")
    dictionary = parse_cmudict(fetch(CMUDICT_URL))
    frequency_lines = fetch(FREQUENCY_URL).splitlines()
    selected: dict[str, list[str]] = {}
    for line in frequency_lines:
        parts = line.split()
        if not parts:
            continue
        word = parts[0].lower().replace("’", "'")
        if WORD_RE.fullmatch(word) and word in dictionary and word not in selected:
            selected[word] = dictionary[word]
            if len(selected) == 10_000:
                break
    if len(selected) != 10_000:
        raise RuntimeError(f"Expected 10,000 CMUdict matches; generated {len(selected)}.")

    required_words = {"algorithm", "phantom", "decentralized"}
    missing_words = required_words - dictionary.keys()
    if missing_words:
        raise RuntimeError(f"Pronunciation QA words missing from CMUdict: {sorted(missing_words)}")
    qa_overrides = {word: dictionary[word] for word in sorted(required_words)}

    for word, phones in {**selected, **qa_overrides}.items():
        unsupported = sorted({re.sub(r"\d$", "", phone) for phone in phones} - SUPPORTED_ARPABET)
        if unsupported:
            raise RuntimeError(f"Unsupported ARPAbet symbols for {word}: {unsupported}")

    medium = json.loads(fetch(LJSPEECH_MEDIUM_CONFIG_URL))
    high = json.loads(fetch(LJSPEECH_HIGH_CONFIG_URL))
    if medium.get("phoneme_type") != "espeak" or high.get("phoneme_type") != "espeak":
        raise RuntimeError("Pinned LJSpeech models must both use eSpeak phonemes.")
    phoneme_map = medium.get("phoneme_id_map")
    if not isinstance(phoneme_map, dict) or not {"_", "^", "$", " ", "ˈ", "ˌ"}.issubset(phoneme_map):
        raise RuntimeError("Pinned LJSpeech config has an incomplete phoneme_id_map.")
    if high.get("phoneme_id_map") != phoneme_map:
        raise RuntimeError("LJSpeech medium/high phoneme maps differ; a single bundled map is unsafe.")

    ASSETS.mkdir(parents=True, exist_ok=True)
    lexicon_payload = {
        "metadata": {
            "source": "Carnegie Mellon University Pronouncing Dictionary",
            "source_revision": CMUDICT_REV,
            "frequency_source": "Hermit Dave FrequencyWords (English 2018)",
            "frequency_revision": FREQUENCY_REV,
            "entry_count": len(selected),
            "phoneme_alphabet": "ARPAbet with CMU stress digits"
        },
        "entries": selected,
        "qa_overrides": qa_overrides
    }
    (ASSETS / "cmu_lexicon.json").write_text(
        json.dumps(lexicon_payload, ensure_ascii=False, separators=(",", ":")) + "\n",
        encoding="utf-8"
    )
    map_payload = {
        "source": "rhasspy/piper-voices",
        "revision": PIPER_VOICES_REV,
        "voice": "en_US-ljspeech-medium",
        "phoneme_type": medium["phoneme_type"],
        "phoneme_id_map": phoneme_map
    }
    (ASSETS / "ljspeech_id_map.json").write_text(
        json.dumps(map_payload, ensure_ascii=False, separators=(",", ":")) + "\n",
        encoding="utf-8"
    )
    print(f"Generated {len(selected)} CMUdict entries and {len(phoneme_map)} eSpeak symbols.")


if __name__ == "__main__":
    main()
