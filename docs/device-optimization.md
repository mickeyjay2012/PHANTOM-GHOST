# 4 GB Android optimization profile

A 4 GB device has less than 4 GB available to the model: Android, the UI, audio buffers, KV cache and other apps share physical RAM.

- Default 4 GB profile: 1B–1.5B Q4 instruct model, 2048-token context, short output budget, Whisper tiny.
- Optional 3B Q4 profile: only after memory preflight on devices with additional available RAM (6 GB+ recommended).
- 8B models are excluded from the 4 GB profile.
- Reserve several GB of free storage for model downloads and temporary files.

A 3B Q4 model's weight file alone can consume a large fraction of a 4 GB device's memory. Quantized file size is not total runtime memory: weights, KV cache, compute buffers and mapped pages all contribute. Do not promise that 3B "fits comfortably" on every 4 GB phone.

## Audio

Whisper expects mono 16 kHz audio. Do not downsample to 8 kHz. Use Whisper tiny or base and convert signed 16-bit PCM samples to float [-1, 1] before calling whisper.cpp. The upstream Android sample recommends tiny/base models for Android.

## TTS

Keep voice assets optional and downloadable. Integrate a maintained Android Piper wrapper or sherpa-onnx Android API only after selecting a real model/config pair and checking its license. The current Kotlin Piper class is an adapter, not a working inference runtime.

## Memory

MemoryGuard uses hysteresis: offload one oldest half below 20% free heap, then wait until availability recovers above 40% before rearming. Persist first; only remove from RAM after SQLite succeeds. Run checks at conversation boundaries, not per generated token.

## Broad device tiers

- 4 GB RAM: 1B–1.5B quantized LLM, 2048 context, Whisper tiny, optional TTS.
- 6 GB RAM: optional 3B quantized LLM after memory preflight.
- 8 GB+ RAM: larger models can be offered as optional downloads.

Inspect available memory and storage and let users choose a supported tier rather than assuming every Android phone can run the same model.
