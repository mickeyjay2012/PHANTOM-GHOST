# Third-party native source checkouts

This directory is reserved for reviewed, pinned upstream source trees. Do not copy unpinned snapshots into release builds.

Expected layout:
- llama.cpp/
- whisper.cpp/
- piper/

The CMake project currently supports optional llama.cpp and whisper.cpp subdirectories. Piper requires a separate ONNX Runtime Android integration and is not yet built by this project. See docs/native-build.md.
