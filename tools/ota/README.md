# Future OTA candidate format, not a release

SPDELTA v1 is a transport artifact, never installable directly.

Header (92 bytes): eight magic bytes `SPDELTA` followed by byte 1; big-endian unsigned 64-bit base length and target length; 32 raw base SHA256 bytes; 32 raw target SHA256 bytes; big-endian unsigned 32-bit operation count. Lengths accepted by Android are positive and at most 512 MiB. Operation count is 1 through 100000.

The remaining file is exactly one zlib stream. Each operation has a one-byte opcode and big-endian signed-positive 64-bit fields:

- COPY, opcode 1: base offset, length. Read exactly that range of the app's own canonical installed base.
- LITERAL, opcode 2: length followed by exactly that many literal bytes.

Each operation must produce positive bytes within the remaining exact target size. COPY offset/length must fit wholly inside the exact base size without overflow. No paths, extraction, executable code, or arbitrary source files are accepted. Unknown opcodes, incomplete stream, extra operations, trailing compressed/uncompressed data, length/hash mismatch and cancellation are failures. Output goes only to an attempt-private temporary file and is removed on failure. A complete target hash and Android archive/package/code/SDK/ABI/current-signer checks are required before Ready. Installation uses a separate explicit Android confirmation path.

Memory uses fixed 64 KiB buffers. The app checkpoints cancellation and bounds preparation time. The current local host harness passes exact published42-to47 reconstruction with a 64 MiB Java heap limit, not an Android memory/latency claim. Android and hostile-input acceptance is still pending. This custom parser is a small codebase, not a security-reviewed standard.

Generate only after final target signing:

    python3 tools/ota/make_delta.py BASE.apk TARGET.apk OUTPUT.spdelta

Required release workflow after generation: apply through the exact Android decoder or the identical Java decoder; compare every byte/hash to TARGET; run Android SDK apksigner verification; validate package/version/signers/SDK/ABI; execute lifecycle/resource/fallback tests. Do not repack or re-sign TARGET after generation. No historical patch backfill.

Future catalog is an official release asset named spool-ota-v1.json. Schema 1 has a patches array with at most 16 records, including: format=spdelta-1, layout=standalone, package, target_version, target_code, target_size, target_sha256, base_code, base_size, base_sha256, min_sdk, abi, patch_asset_id, patch_size, patch_sha256. Asset IDs come from the actual new release, not invented names. The app cross-checks each patch against that release's actual uploaded asset URL, size and GitHub SHA256 digest and its chosen full target. Catalog bytes are also bounded and pinned by the release's digest. This is HTTPS/GitHub integrity metadata, not an independently signed catalog. Invalid/absent catalog never authorizes delta use. Final Android installer verification remains mandatory.

Full APK bootstrap is required for clients without this decoder. Unsupported layouts, split installations, mismatched canonical base hashes, unhelpful patch sizes or decoder failures use the ordinary full APK only with honest size/data disclosure. A changed base after a smaller offer requires separate full-download consent. No silent140MB fallback after offering9MB.

Current publication state: NONE. This document describes candidate code under test. Existing releases, signing identities, published assets and website links are unchanged.

Host hostile-input supplement (not Android or production security certification):

    javac -d /tmp/spool-delta-classes app/src/main/java/com/music/bitchord/data/SpoolDelta.java tools/ota/DeltaMutationHarness.java
    java -Xmx64m -cp /tmp/spool-delta-classes DeltaMutationHarness

The deterministic fixture tries 1000 seeded mutations and six cancellations after preparation starts. A successful decode must match the target bytes exactly; rejected and cancelled outputs must not remain. Keep the seed stable for reproducibility and add targeted regression cases for any newly discovered failure.
