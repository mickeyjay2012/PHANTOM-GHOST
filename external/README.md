# External native dependencies

CMake FetchContent downloads pinned upstream source revisions into this directory's build cache:
- ggml-org/llama.cpp at 9c003a97ab34a69cc49243f9b02014038205ffae
- ggml-org/whisper.cpp at 868e55578ea5f69550c046da22aefe11dc6e04c0

Do not vendor model weights here. Piper/sherpa-onnx is a separate future integration.
