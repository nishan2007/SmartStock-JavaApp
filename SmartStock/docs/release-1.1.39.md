# SmartStock 1.1.39: Studio template credential route

Live diagnosis after employee sign-in reproduced STUDIO_OPERATION_DENIED / This credential is only for SmartStudio for the template LIST call. StudioDeviceEnrollmentService.allowedPath omitted /v1/studio/templates, so valid Studio-only credentials could never reach the existing authenticated template handler. This prevented automatic migration and cross-computer library visibility.

The exact template endpoint is now allowed. The handler retains Studio device approval, authenticated employee session, store access and mutation permission enforcement. Neighbor paths and register/admin routes remain denied. Regression coverage includes exact-route acceptance and adjacent-route rejection alongside store, archive and access tests.

Validation: full server Maven packaging, 1,046 tests with 61 skipped and no failures/errors; security and whitespace checks; model-free update verifier; exact update archive isolated native launcher acceptance. Production server was not restarted or patched in place. Live template migration must be rechecked after updating/restarting the server.

Prepared Windows updater: 167008948 bytes; SHA-256 0fa795dd56f9ef683d8210b113cf54142b20e0d5fff21541d71f54d2f57da1d1.

Published at the user's request on 2026-10-16. Complete hosted archive download verified before release metadata publication. Subsequent metadata read-back matched exact version, build 101039, size, object path and SHA-256. Live activation and two-computer migration remain pending; SmartStock was not restarted.
