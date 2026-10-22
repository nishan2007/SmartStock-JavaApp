# Windows release 1.1.40

Prepared and published at the user's request on 2026-10-17, together with SmartStudio 0.1.36. Adds server validation for badge underline/strike/outline fields and permits approved SmartStudio device credentials to access badge-status and first-time PIN setup. Existing device/store/active-badge/password enforcement remains in the handlers.

Validation: full Maven package gate passed (1056 tests, 61 skipped, no failures/errors). Security and whitespace checks passed. Update archive is model-free and hosted model catalogue is ready. Exact update and extracted installer native launchers passed in an isolated user profile; installer/update application JAR identities match. No production database or service was changed and the running SmartStock instance was not restarted. Physical reader, printer/duplex, service-upgrade and live database-upgrade checks remain outstanding. No macOS release was prepared.

Artifacts:
- Update ZIP: 167017263 bytes; SHA-256 26ad187266744ca9046db1de449de8c6266bb873dfd277740eceba2bc263da19.
- All-in-one installer: 580089171 bytes; SHA-256 0363cf3e4d85632b449c2259d3f60081fb844b04ca72d5d514ee0d143501af4e.
- Model migration bridge SHA-256: 01f690e5e48de4f91e4bcf94e488737a49ad62220b856da032fbc1ed019390b2.

Update ZIP was uploaded, fully downloaded and verified before release metadata publication. Metadata version/build/object path/size/SHA were read back and matched. All-in-one first-install hosted bytes were fully downloaded and verified before switching the public Windows selection to 1.1.40. Update the store server first, then client computers. Existing OneDrive template backups still need provider configuration repair.
