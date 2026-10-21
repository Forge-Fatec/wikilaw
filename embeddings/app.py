"""Internal, CPU-only embedding service. No document text is stored or logged."""
from contextlib import asynccontextmanager
import math
import os
import threading
from typing import Literal

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field, field_validator

MODEL = "intfloat/multilingual-e5-small"
REVISION = "614241f622f53c4eeff9890bdc4f31cfecc418b3"
FINGERPRINT = f"{MODEL}@{REVISION}:windows-v1"
DIMENSIONS = 384


class EmbedRequest(BaseModel):
    texts: list[str] = Field(min_length=1, max_length=16)
    kind: Literal["query", "passage"]

    @field_validator("texts")
    @classmethod
    def bounded_text(cls, texts):
        if any(not text.strip() or len(text) > 100_000 for text in texts):
            raise ValueError("Each text must contain 1 to 100000 characters")
        if sum(map(len, texts)) > 200_000:
            raise ValueError("Request exceeds 200000 characters")
        return texts


class Encoder:
    def __init__(self):
        import torch
        from sentence_transformers import SentenceTransformer
        torch.set_num_threads(max(1, min(8, int(os.getenv("EMBEDDING_THREADS", "2")))))
        self.torch = torch
        self.model = SentenceTransformer(MODEL, revision=REVISION, device="cpu", trust_remote_code=False)
        self.model.eval()
        if self.model.get_embedding_dimension() != DIMENSIONS:
            raise RuntimeError("Unexpected model dimensions")
        self.lock = threading.Lock()

    def encode(self, texts, kind):
        tokenizer = self.model.tokenizer
        result = []
        # Serialize inference to bound CPU/RAM. Uvicorn also bounds queued requests.
        with self.lock, self.torch.inference_mode():
            for text in texts:
                prefix = tokenizer.encode(f"{kind}: ", add_special_tokens=False)
                tokens = tokenizer.encode(text, add_special_tokens=False, truncation=False)
                size = min(512, self.model.max_seq_length) - len(prefix) - tokenizer.num_special_tokens_to_add(False)
                windows = []
                weights = []
                for start in range(0, len(tokens), size - 64):
                    end = min(start + size, len(tokens))
                    windows.append([tokenizer.cls_token_id] + prefix + tokens[start:end] + [tokenizer.sep_token_id])
                    weights.append(end - start if start == 0 else end - start - 64)
                    if end == len(tokens):
                        break
                if not windows or len(windows) > 512:
                    raise ValueError("Text exceeds model processing limits")
                total = self.torch.zeros(DIMENSIONS)
                for start in range(0, len(windows), 8):
                    batch = windows[start:start + 8]
                    length = max(map(len, batch))
                    features = {
                        "input_ids": self.torch.tensor([ids + [tokenizer.pad_token_id] * (length - len(ids)) for ids in batch]),
                        "attention_mask": self.torch.tensor([[1] * len(ids) + [0] * (length - len(ids)) for ids in batch]),
                    }
                    vectors = self.model(features)["sentence_embedding"]
                    vectors = self.torch.nn.functional.normalize(vectors, p=2, dim=1)
                    for vector, weight in zip(vectors, weights[start:start + 8]):
                        total += vector * weight
                result.append(self.torch.nn.functional.normalize(total, p=2, dim=0).tolist())
        return result


def create_app(encoder_factory=Encoder):
    @asynccontextmanager
    async def lifespan(application):
        application.state.encoder = encoder_factory()
        yield

    application = FastAPI(lifespan=lifespan, docs_url=None, redoc_url=None, openapi_url=None)

    @application.get("/health")
    def health():
        return {"ready": True, "model": FINGERPRINT, "dimensions": DIMENSIONS}

    @application.post("/embed")
    def embed(request: EmbedRequest):
        try:
            vectors = application.state.encoder.encode(request.texts, request.kind)
            if len(vectors) != len(request.texts) or any(
                len(vector) != DIMENSIONS or not all(math.isfinite(value) for value in vector)
                for vector in vectors
            ):
                raise RuntimeError("Invalid embedding output")
            return {"model": FINGERPRINT, "dimensions": DIMENSIONS, "vectors": vectors}
        except ValueError:
            raise HTTPException(422, "Text exceeds processing limits") from None
        except Exception:
            raise HTTPException(503, "Embedding service is temporarily unavailable") from None

    return application


app = create_app()
