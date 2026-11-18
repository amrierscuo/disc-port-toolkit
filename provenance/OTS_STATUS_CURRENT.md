# Historical OpenTimestamps proof

OpenTimestamps proof successfully upgraded with a Bitcoin block attestation at height 976290. Some calendar branches remain pending. Independent verification against a local Bitcoin node has not been performed.

The 1,691-byte LEGACY_ANCHOR.json.ots was decoded offline. Its document digest matches the unmodified LEGACY_ANCHOR.json (SHA-256 1c1d35f426182f08c44ae28f0a4d272116446261e0a3360b2ce12a4048a989aa). Current proof SHA-256 is 3ed2daea868cb1ca1ac0dbca012e8521745d58a637783ec27054d3f1c6142f5f. Three calendar branches remain pending. LEGACY_OTS_RECEIPT.txt describes the earlier 689-byte proof.

The old anchor identifies two previous Android candidate trees and artifact digests. Its earlier Git dates are recorded metadata. This new toolkit will have a different tree and its own first commit. After that commit run python -B tools/create_anchor_after_commit.py to record the commit, tree and committed manifest locally. Only a separate, later request could timestamp that receipt.
