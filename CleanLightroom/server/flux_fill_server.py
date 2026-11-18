"""
CleanLR Next-Gen Desktop / LAN Bridge GPU Server (Zero API Cost)
Supports:
  1. FLUX.2 Klein 4B (Apache 2.0, Q4_K_M GGUF, ~2.8GB) via local `stable-diffusion.cpp` CLI
  2. FLUX.1 Fill [dev] via Hugging Face `diffusers`
  3. Automatic mDNS/Zeroconf Wi-Fi Broadcast (`_cleanlr-ai._tcp.local.`) so CleanLR Mobile
     auto-discovers your PC on the LAN with 1 tap!

Quick Start:
    pip install fastapi uvicorn pillow zeroconf
    # Option A (GGUF via stable-diffusion.cpp):
    export CLEANLR_SD_CPP_BIN=/path/to/sd
    export CLEANLR_GGUF_MODEL=/path/to/flux2-klein-4b-Q4_K_M.gguf
    # Option B (Diffusers FLUX.1 Fill [dev]):
    pip install torch diffusers transformers accelerate
    export HF_TOKEN=hf_your_token

    uvicorn flux_fill_server:app --host 0.0.0.0 --port 8000
"""

import base64
import io
import os
import socket
import subprocess
import tempfile
from contextlib import asynccontextmanager
from typing import Optional

from fastapi import FastAPI, HTTPException
from PIL import Image
from pydantic import BaseModel

_pipeline = None
_zeroconf = None
_service_info = None


def _get_local_ip() -> str:
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"


@asynccontextmanager
async def lifespan(_app: FastAPI):
    global _zeroconf, _service_info
    port = int(os.environ.get("CLEANLR_PORT", "8000"))
    try:
        from zeroconf import ServiceInfo, Zeroconf

        ip = _get_local_ip()
        _zeroconf = Zeroconf()
        _service_info = ServiceInfo(
            type_="_cleanlr-ai._tcp.local.",
            name=f"CleanLR-Desktop-{socket.gethostname()}._cleanlr-ai._tcp.local.",
            addresses=[socket.inet_aton(ip)],
            port=port,
            properties={
                b"engine": b"FLUX.2-Klein-4B-GGUF",
                b"version": b"2.0",
            },
        )
        _zeroconf.register_service(_service_info)
        print(f"[CleanLR LAN Bridge] mDNS active at http://{ip}:{port}/v1/flux-fill")
    except Exception as exc:
        print(f"[CleanLR LAN Bridge] mDNS optional broadcast skipped: {exc}")
    yield
    if _zeroconf and _service_info:
        try:
            _zeroconf.unregister_service(_service_info)
            _zeroconf.close()
        except Exception:
            pass


app = FastAPI(title="CleanLR Desktop FLUX.2 Klein / FLUX.1 Fill LAN Server", version="2.0.0", lifespan=lifespan)


class FillRequest(BaseModel):
    image_base64: str
    mask_base64: str
    prompt: str = (
        "Clean natural background seamlessly continuing surrounding lighting, "
        "shadows, depth of field, and fine photographic texture"
    )
    mode: str = "remove"  # "remove" or "expand"
    model: str = "flux2-klein-4b-Q4_K_M.gguf"
    steps: int = 20
    guidance_scale: float = 30.0
    seed: Optional[int] = None


def decode_b64_image(b64_str: str) -> Image.Image:
    if "base64," in b64_str:
        b64_str = b64_str.split("base64,", 1)[1]
    return Image.open(io.BytesIO(base64.b64decode(b64_str)))


def run_sd_cpp_gguf(image: Image.Image, mask: Image.Image, req: FillRequest) -> Image.Image:
    sd_bin = os.environ["CLEANLR_SD_CPP_BIN"]
    gguf_path = os.environ["CLEANLR_GGUF_MODEL"]
    with tempfile.TemporaryDirectory() as tmp:
        in_path = os.path.join(tmp, "in.png")
        mask_path = os.path.join(tmp, "mask.png")
        out_path = os.path.join(tmp, "out.png")
        image.save(in_path, "PNG")
        mask.save(mask_path, "PNG")
        cmd = [
            sd_bin,
            "--diffusion-model", gguf_path,
            "-i", in_path,
            "--mask", mask_path,
            "-p", req.prompt,
            "-W", str(image.width),
            "-H", str(image.height),
            "--steps", str(req.steps),
            "-o", out_path,
        ]
        if req.seed is not None:
            cmd.extend(["-s", str(req.seed)])
        subprocess.run(cmd, check=True)
        return Image.open(out_path).convert("RGB")


def get_diffusers_pipeline(model_id: str):
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


@app.get("/health")
def health():
    backend = "stable-diffusion.cpp-GGUF" if os.environ.get("CLEANLR_SD_CPP_BIN") else "diffusers-FLUX.1-Fill"
    return {"status": "ok", "backend": backend}


@app.post("/v1/flux-fill")
def flux_fill(req: FillRequest):
    try:
        image = decode_b64_image(req.image_base64).convert("RGB")
        mask = decode_b64_image(req.mask_base64).convert("L")
        w = max(64, (image.width // 16) * 16)
        h = max(64, (image.height // 16) * 16)
        if (w, h) != image.size:
            image = image.resize((w, h), Image.Resampling.LANCZOS)
            mask = mask.resize((w, h), Image.Resampling.NEAREST)

        if os.environ.get("CLEANLR_SD_CPP_BIN") and os.environ.get("CLEANLR_GGUF_MODEL"):
            result = run_sd_cpp_gguf(image, mask, req)
        else:
            import torch

            pipe = get_diffusers_pipeline(
                req.model if not req.model.endswith(".gguf") else "black-forest-labs/FLUX.1-Fill-dev"
            )
            generator = torch.Generator("cpu").manual_seed(req.seed) if req.seed is not None else None
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
        return {
            "image_base64": base64.b64encode(buf.getvalue()).decode("ascii"),
            "width": result.width,
            "height": result.height,
        }
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc))
