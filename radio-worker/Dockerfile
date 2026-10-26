FROM eclipse-temurin:25-jre-resolute
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg && rm -rf /var/lib/apt/lists/* && mkdir /recordings /opt/zipformer && chown 1000:1000 /recordings
ADD --chmod=644 --checksum=sha256:4249db3c9a9a66dc368b169cccb39088fe2f4d3f62d19907f110daea8a2d387f https://huggingface.co/TalTechNLP/streaming-zipformer-large.et-en/resolve/2812262bd4fd5d4327dc09ca0e6c19e8a32a03d6/encoder.int8.onnx /opt/zipformer/encoder.int8.onnx
ADD --chmod=644 --checksum=sha256:75293b68a6a391f926e9c8a62680c447ba9a97e885aef7dcba0cbfa5433a6198 https://huggingface.co/TalTechNLP/streaming-zipformer-large.et-en/resolve/2812262bd4fd5d4327dc09ca0e6c19e8a32a03d6/decoder.int8.onnx /opt/zipformer/decoder.int8.onnx
ADD --chmod=644 --checksum=sha256:cfa4a072813c4ae140d3604eb16f875fd13f5bbcedb32a673815dc12fa17521b https://huggingface.co/TalTechNLP/streaming-zipformer-large.et-en/resolve/2812262bd4fd5d4327dc09ca0e6c19e8a32a03d6/joiner.int8.onnx /opt/zipformer/joiner.int8.onnx
ADD --chmod=644 --checksum=sha256:cbe23ea2ef76303d874ab45ef8471b570e565b9048ab0aeda1c7adf2c663ee97 https://huggingface.co/TalTechNLP/streaming-zipformer-large.et-en/resolve/2812262bd4fd5d4327dc09ca0e6c19e8a32a03d6/tokens.txt /opt/zipformer/tokens.txt
COPY ced-tiny /opt/ced-tiny
COPY build/install/radio-worker /opt/radio-worker
USER ubuntu
RUN ffmpeg -v error -f lavfi -i sine=frequency=440:duration=3 -ac 2 -ar 48000 -c:a libmp3lame -b:a 128k /tmp/smoke.mp3 && /opt/radio-worker/bin/radio-worker run --source /tmp/smoke.mp3 --out /tmp/smoke && test -s /tmp/smoke/transcript.json && test -s /tmp/smoke/sounds.json && rm -rf /tmp/smoke /tmp/smoke.mp3
ENTRYPOINT ["/opt/radio-worker/bin/radio-worker"]
