# How the three profiles use the game

Clone 1 builds the PAL Windows game from the blinx1-recomp project, the xboxrecomp toolkit and the PAL-specific changes listed in the Windows guide. Its C/C++ build links guest code and a runtime which handles graphics, input and the disc filesystem. The game files and generated guest source are local inputs or outputs.

Both Android profiles host Xbox code through the hakuX runtime and FEX on ARM64. They consume an entire disc filesystem at runtime. Original packages a single PAL disc, starts directly, and supplies touch controls or a physical pad. Full adds the current Original/Demo hub plus extra privately supplied resources. Its private packaging recipe describes 324 distinct targets, including two native libraries. The publicly distributed material consists of source, recipes, checksums, and notices.

Android identity checks bind the certificate title and SHA-256 of default.xbe, then the sorted list of all disc paths, sizes, and content hashes. The PAL ID is 4D530013 and the OXM12 demo ID is 494D000C. Hashing file contents independently of ISO layout permits verification of re-created images. A physical full-disc dump with a video partition needs conversion to a bare game partition first. Generated artifacts live under build/ or dist/. See the profile guides for exact host requirements.
