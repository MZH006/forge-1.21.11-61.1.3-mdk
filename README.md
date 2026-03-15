# GenCraft

A Minecraft Forge mod that generates 3D structures in-game from a text prompt using AI.

Type `/generate generate 32 castle` and watch a castle appear in front of you.

---

## How It Works

```
Text prompt → Tripo AI (text-to-3D) → GLB file → Voxelizer → Minecraft blocks
```

1. **Tripo AI** converts your text prompt directly into a 3D GLB model
2. **GlbParser** extracts geometry and samples colors from the embedded PBR texture
3. **Voxelizer** converts the mesh into a 3D grid of colored voxels
4. **BlockMapper** maps each voxel's RGB color to the nearest Minecraft block using CIE-LAB color space
5. Blocks are placed in the world in front of the player

Results are **cached locally** — repeated or similar prompts reuse the saved GLB instantly without calling the API again.

---

## Requirements

- Minecraft **1.21.11**
- Forge **61.1.3**
- Java **21**
- A [Tripo AI](https://platform.tripo3d.ai) account (free tier: 300 credits/month)

---

## Setup

### 1. Install the mod

Build with Gradle and drop the jar into your `mods/` folder, or run directly via:

```bash
./gradlew runClient
```

### 2. Get a Tripo AI API key

1. Sign up at [platform.tripo3d.ai](https://platform.tripo3d.ai)
2. Go to **API Keys** and create a new key (starts with `tsk_`)

### 3. Set your API key in-game

```
/generate settripokey tsk_your_key_here
```

---

## Commands

| Command | Description |
|---------|-------------|
| `/generate generate <size> <prompt>` | Generate and place a structure |
| `/generate settripokey <key>` | Save your Tripo AI API key |
| `/generate setkey <key>` | Save your HuggingFace API key (optional fallback) |
| `/generate setreplicatekey <key>` | Save your Replicate API key (optional fallback) |
| `/generate status` | Show which keys are configured and which pipeline is active |

### Size parameter

The `size` argument controls the voxel grid resolution (8–256). Larger = more detail, more blocks, slower placement.

| Size | Blocks (approx) | Use for |
|------|----------------|---------|
| 16 | ~1,000 | Quick test |
| 32 | ~8,000 | Small objects |
| 64 | ~30,000 | Buildings |
| 128 | ~70,000 | Large structures |

To change the size limits yourself, edit `ModCommands.java`:
```java
// Line ~92 — change the two numbers (min, max)
.then(argument("size", IntegerArgumentType.integer(8, 256))
```

---

## Pipeline Architecture

The mod uses a provider pattern so APIs can be swapped without changing any other code.

```
provider/
  TextToImageProvider.java       ← interface
  ImageTo3dProvider.java         ← interface
  ModelDownloader.java           ← shared HTTP downloader
  huggingface/
    HuggingFaceTextToImageProvider.java
  replicate/
    ReplicateImageTo3dProvider.java
  fal/
    FalTextToImageProvider.java
    FalImageTo3dProvider.java
pipeline/
  GenerationPipeline.java        ← text→image→3D pipeline
  TripoGenerationPipeline.java   ← direct text→3D pipeline (preferred)
cache/
  PromptCache.java               ← fuzzy prompt matching + GLB cache
fal/
  GlbParser.java                 ← parses GLB + samples PBR textures
  Voxelizer.java                 ← mesh → voxel grid
  BlockMapper.java               ← RGB → nearest Minecraft block (CIE-LAB)
```

### To add a new provider

Implement `TextToImageProvider` or `ImageTo3dProvider`, then swap it in `ModCommands.buildPipeline()`:

```java
private static GenerationPipeline buildPipeline(String hfKey, String replicateKey) {
    return new GenerationPipeline(
        new YourTextToImageProvider(hfKey),   // ← swap here
        new YourImageTo3dProvider(replicateKey) // ← swap here
    );
}
```

---

## Caching

Generated GLB files are saved to `run/config/aibuilder/cache/`.

- **Exact match**: "castle" reuses `castle.glb` instantly
- **Fuzzy match**: "a big red castle" matches "castle" if word overlap ≥ 60%
- To force a fresh generation, delete the relevant `.glb` file from the cache folder

To change the similarity threshold, edit `PromptCache.java`:
```java
private static final double SIMILARITY_THRESHOLD = 0.6; // 0.0 = exact only, 1.0 = always reuse
```

---

## API Key Storage

Keys are stored as plain text files in `run/config/aibuilder/`:

| File | Key |
|------|-----|
| `tripo-api-key.txt` | Tripo AI |
| `hf-api-key.txt` | HuggingFace |
| `replicate-api-key.txt` | Replicate |

---

## Credits

- [Tripo AI](https://www.tripo3d.ai) — text-to-3D generation
- [Black Forest Labs Flux](https://blackforestlabs.ai) — text-to-image (HuggingFace endpoint)
- [Replicate](https://replicate.com) — Hunyuan3D-2.1 image-to-3D
- Minecraft Forge MDK
