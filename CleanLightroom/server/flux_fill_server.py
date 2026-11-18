"""
CleanLightroom Self-Hosted SOTA GPU Server — FLUX.1 Fill [dev] (Zero APK Bloat)

Run on RunPod / Lambda Labs / Local NVIDIA GPU (16GB+ VRAM, bfloat16):
    pip install fastapi uvicorn torch diffusers transformers accelerate pillow
    export HF_TOKEN=hf_your_huggingface_token
    uvicorn flux_fill_server:app --host 0.0.0.0 --port 8000

Then in CleanLightroom -> Remove or Expand -> AI Server Settings:
    Backend: Self-Hosted GPU Server
    Endpoint: http://<YOUR_GPU_IP>:8000/v1/flux-fill
"""

import base64
import io
import os
from typing import Optional

from fastapi import FastAPI, HTTPException
from PIL import Image
from pydantic import BaseModel

app = FastAPI(title="CleanLightroom FLUX.1 Fill Server", version="1.0.0")
_pipeline = None


class FillRequest(BaseModel):
    image_base64: str
    mask_base64: str
    prompt: str = (
        "Clean natural background seamlessly continuing surrounding lighting, "
        "shadows, depth of field, and fine photographic texture"
    )
    mode: str = "remove"  # "remove" or "expand"
    model: str = "black-forest-labs/FLUX.1-Fill-dev"
    steps: int = 28
    guidance_scale: float = 30.0
    seed: Optional[int] = None


def get_pipeline(model_id: str):
    global _pipeline
    if _pipeline is None:
        import torch
        from diffusers import FluxFillPipeline

        _pipeline = FluxFillPipeline.from_pretrained(
            model_id,
            torch_dtype=torch.bfloat16,
            token=os.environ.get("HF_TOKEN"),
        )
        _pipeline.enable_model_cpu_offload()
    return _pipeline


def decode_b64_image(b64_str: str) -> Image.Image:
    if "base64," in b64_str:
        b64_str = b64_str.split("base64,", 1)[1]
    raw = base64.b64decode(b64_str)
    return Image.open(io.BytesIO(raw))


@app.get("/health")
def health():
    return {"status": "ok", "model": "black-forest-labs/FLUX.1-Fill-dev"}


@app.post("/v1/flux-fill")
def flux_fill(req: FillRequest):
    try:
        import torch

        image = decode_b64_image(req.image_base64).convert("RGB")
        mask = decode_b64_image(req.mask_base64).convert("L")

        # Snap to FLUX VAE 16px multiples
        w = max(64, (image.width // 16) * 16)
        h = max(64, (image.height // 16) * 16)
        if (w, h) != image.size:
            image = image.resize((w, h), Image.Resampling.LANCZOS)
            mask = mask.resize((w, h), Image.Resampling.NEAREST)

        pipe = get_pipeline(req.model)
        generator = (
            torch.Generator("cpu").manual_seed(req.seed)
            if req.seed is not None
            else None
        )

        result = pipe(
            prompt=req.prompt,
            image=image,
            mask_image=mask,
            height=h,
            width=w,
            guidance_scale=req.guidance_scale,
            num_inference_steps=req.steps,
            max_sequence_length=512,
            generator=generator,
        ).images[0]

        buf = io.BytesIO()
        result.save(buf, format="PNG")
        out_b64 = base64.b64encode(buf.getvalue()).decode("ascii")
        return {"image_base64": out_b64, "width": result.width, "height": result.height}
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc))
