# VLM Candidate Qualification Comparison

**Evidence class:** official-source desk qualification only. No model, runtime, image, provider, API, GPU, Docker, WSL2, or Android integration was used. “Candidate” is not authorization to download or infer.

| Route / exact candidate | Officially observed facts | 12 GB decision | P0 disposition |
| --- | --- | --- | --- |
| Qwen `Qwen/Qwen3-VL-2B-Instruct` at `89644892e4d85e24eaac8bacfd4f463576704203` | 4.27 GB repository (4.26 GB model files); Qwen3-VL code and card declare Apache-2.0. | Suitable only as a *future* single-image, short-output baseline; actual VRAM/runtime remains unmeasured. | `NEEDS_OWNER_CHOICE` for P1 artifact authorization. |
| Qwen `Qwen/Qwen3-VL-4B-Instruct-FP8` at `fefbb44cbcce8d1bb7e20b920b94f77432b3446d` | 6.04 GB; card declares Apache-2.0 and block-128 FP8. | Conditional: target GPU/runtime must prove FP8 support, VRAM headroom, and no OOM. | `NEEDS_OWNER_CHOICE`. |
| Qwen `Qwen/Qwen3-VL-4B-Instruct` at `ebb281ec70b05090aa6165b016eac8ec08e71b17` | 8.89 GB; Apache-2.0 card. | High risk: only about 3.1 GB nominal room remains for runtime/image/KV cache. | `BLOCKED_BY_EVIDENCE` for a 12 GB baseline. |
| Qwen `Qwen/Qwen3-VL-8B-Instruct-FP8` at `9cdc6310a8cb770ce18efaf4e9935334512aee45` | 10.6 GB; Apache-2.0 card. | Weight footprint alone leaves insufficient operational headroom. | `RECOMMEND_REJECT` for 12 GB evaluation. |
| InternVL `OpenGVLab/InternVL3-2B-Instruct` | Official InternVL docs list InternVL3-2B as 2.1B / 4.2 GB; the official model card describes a MIT project and Apache-2.0 Qwen2.5 component. | Artifact size is plausibly in range, but actual VRAM, Chinese photography quality, Windows/WSL2, and weight-license/revision evidence are unverified. | `BLOCKED_BY_EVIDENCE` pending a P1 immutable model-page, weight-license, and hash review. |
| Cloud vision API benchmark | No service/model selected or called. | Not local; privacy, account, backend, cost, retention, and outage design are absent. | `NEEDS_OWNER_CHOICE`; future quality comparator only. |

## License and supply-chain decisions

Code license and weight license are evaluated separately. The Qwen3 model cards and repository are the primary Apache-2.0 evidence; P1 must still re-check the exact revision’s root license, publisher identity, file inventory, and published/local SHA-256 before download. InternVL’s code/project license statement is not automatically a weight license, so it remains blocked until that primary evidence is captured.

Two Qwen2.5 AWQ routes were deliberately excluded: `Qwen2.5-VL-3B-Instruct-AWQ` carries a non-commercial Qwen Research License, while `Qwen2.5-VL-7B-Instruct-AWQ` has metadata/root-license conflict. Neither is a commercial or redistribution candidate until the conflict is resolved by authoritative terms.

## Capability, deployment, and quality boundaries

- Qwen3-VL official usage documents multi-image input and pixel controls. These can constrain a later evaluation but do not prove 12 GB fit. Official deployment material covers Transformers, vLLM, SGLang, and Docker; native Windows/WSL2 support is not explicitly proven.
- Qwen3-VL’s official material does not prove JSON-Schema-constrained output or Chinese photography-director quality. A future provider may only use generate → strict parse → v1 JSON Schema validation → fail/limited retry. It must not trust bare generated JSON.
- InternVL documentation shows local/deployed VLM routes and the 2B/8B series, but no P0 measurement establishes its Chinese guidance quality, multiple-image behavior under this rubric, or Windows/WSL2/runtime memory fit.
- Neither family is suitable for direct Android packaging under the present contract. The Cloud route is not a fallback and is never called in P0.

## Official sources checked on 2026-07-26

- [Qwen3-VL code license](https://github.com/QwenLM/Qwen3-VL/blob/main/LICENSE), [Qwen3-VL usage and deployment](https://github.com/QwenLM/Qwen3-VL#using--transformers-to-chat), [Qwen 2B tree](https://huggingface.co/Qwen/Qwen3-VL-2B-Instruct/tree/main), [Qwen 4B FP8 tree](https://huggingface.co/Qwen/Qwen3-VL-4B-Instruct-FP8/tree/main).
- [Qwen2.5 3B AWQ license](https://huggingface.co/Qwen/Qwen2.5-VL-3B-Instruct-AWQ/blob/main/LICENSE), [Qwen2.5 7B AWQ license](https://huggingface.co/Qwen/Qwen2.5-VL-7B-Instruct-AWQ/blob/main/LICENSE).
- [InternVL official repository](https://github.com/OpenGVLab/InternVL), [InternVL3 size table](https://internvl.readthedocs.io/en/latest/internvl3.0/quick_start.html), [InternVL3-2B-Instruct card](https://huggingface.co/OpenGVLab/InternVL3-2B-Instruct), [InternVL deployment guide](https://internvl.readthedocs.io/en/latest/internvl3.0/deployment.html).

The cited model cards are primary publisher pages but may change. P1 must refresh each immutable revision and license before any artifact is allowed.
