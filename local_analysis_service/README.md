# PhotoAI local analysis service

This service is the private-Wi-Fi Windows adapter for the Android app. It never downloads model weights. The runtime is frozen to the existing offline 2B artifact:

<https://huggingface.co/Qwen/Qwen3-VL-2B-Instruct>

The verified quarantine snapshot is staged at `E:\AI_Tools\Other\LocalLLM\Qwen3-VL-2B-Instruct\89644892e4d85e24eaac8bacfd4f463576704203`. Do not download or replace it in this gate. Add a completed `photoai-model-manifest.json` beside `config.json`; do not commit the runtime directory. The manifest must contain the exact revision, artifact SHA-256 and runtime identifier.

After copying, verify without downloading:

```powershell
python -m local_analysis_service.verify_model `
  --model-dir 'E:\AI_Tools\Other\LocalLLM\Qwen3-VL-2B-Instruct\89644892e4d85e24eaac8bacfd4f463576704203' `
  --model-id 'Qwen/Qwen3-VL-2B-Instruct' `
  --model-revision '89644892e4d85e24eaac8bacfd4f463576704203' `
  --write-manifest
```

The service binds an HTTPS endpoint, accepts one already-sanitized JPEG in memory, and returns a schema-shaped analysis Envelope. Pairing is one-time and access tokens stay in process memory. Logs deliberately omit request bodies, image names, paths, URIs and device identifiers. If the local model or Python runtime is absent, health is unavailable and analysis returns `PROVIDER_NOT_CONFIGURED`.

Set `PHOTOAI_TLS_CERTFILE` and `PHOTOAI_TLS_KEYFILE` before starting; the service refuses to start without TLS. Bind the firewall rule to the Windows Private network profile only. The Android side pins the certificate supplied during pairing.

On startup the local console prints one `PAIRING_CODE`; enter it in the Android pairing dialog before the process exits. Set `PHOTOAI_PAIRING_CODE` yourself if you need a predetermined code. The code is single-use and is never written to a file.

The recommended runtime is native Windows Transformers (`transformers>=4.57.0`). Install dependencies only after the Owner has chosen the local runtime and into the approved local tool environment; this repository does not run the install or download weights.

This machine's prepared environment is `E:\AI_Tools\Other\LocalLLM\photoai-service-venv`; its dependencies are installed, but the model is intentionally absent until the Owner downloads it.
