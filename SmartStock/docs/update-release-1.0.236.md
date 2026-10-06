# SmartStock 1.0.236 / SmartStudio 0.1.2 Windows release

Built from a frozen copy at `target/release-source-1.0.236` of all completed
changes present at the start of release preparation. The active shared checkout
continues to contain work in progress. Later edits are outside this snapshot.

The custom-order spoils service, migration, appended base SQL, schema readiness
and permissions, reference-sync tables, phone routes/resource mapping and phone
activation permission branch were removed only from the release copy. The live
working tree was not reverted. The exact release JAR contains no spoils classes,
resources or SQL/HTML/JS references.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| SmartStock update 1.0.236 | 1113960087 | `1e8d89c87f148755510b3c49d26aa764907540cea9ca5cedb9756f44da183843` |
| SmartStock setup 1.0.236 | 1520392468 | `ed8d3f27dc435cfaca3fc41d1365239832a83777b198e537a6552e67fec401c0` |
| SmartStudio setup 0.1.2 | 137625877 | `f4f31594b5a7dac2bc96c83742486a7a4f17fd62522a54daa40692bd2ae1985c` |

SmartStock setup includes Java, both pinned photo models and the verified
PostgreSQL installer. SmartStudio setup includes Java and the staff client.
Update the server first, then manually install SmartStudio 0.1.2 to obtain its
in-app updater. Existing device approval, pairing and staff login still apply.

## Checks

- Frozen reactor: 788 tests, zero failures/errors, 38 skips. Separate disposable
  PostgreSQL enrollment checks ran all five tests with zero skips.
- Exact update ZIP ran both offline models, retaining dimensions/transparency.
  The previous local schema upgraded/validated in a disposable database; that
  database was stopped afterward.
- SmartStock setup was actually installed to a workspace fixture. The native
  launcher opened the Welcome screen with an isolated client profile. The
  installed runtime ran Best quality inference. Its JAR matched the update JAR.
- SmartStudio setup was actually installed and its native image preview opened.
  The installed updater verified this installer and handed off a reinstall.
  Fixture data and exact release JAR were preserved; the updated launcher opened.
- Both fixtures were uninstalled with their official uninstallers. Production
  applications, database, services, router and hardware were not changed.
- Security and whitespace checks passed. Multipart publisher/portal tests passed
  (18), including denial of update/Studio uploads as the public SmartStock setup.
  The portal was deployed with existing store authentication/configuration.

## Publication

SmartStudio 0.1.2 was uploaded and downloaded in full; size/SHA-256 matched before
its release row was published. Independent feed readback matched.
SmartStock setup 1.0.236 was uploaded and downloaded in full with matching size
and SHA-256 before the public Windows installer selection was changed. The
in-app update ZIP was also downloaded in full and verified before publishing
SmartStock 1.0.236 build 100236. Independent readback confirmed both separate
Windows release feeds and the public SmartStock installer manifest against the
exact expected sizes and SHA-256 values. Publication is complete.

Multipart upload handles the larger model-containing artifacts. Studio releases
use `smartstudio/windows/` and are separated from SmartStock update selection.
The full stored download is verified before release metadata or the installer
selection changes.

Live paired staff login/removal, remote network-share access, production update
application and hardware behavior remain separate checks. No macOS artifacts
were built or published on this Windows machine.
