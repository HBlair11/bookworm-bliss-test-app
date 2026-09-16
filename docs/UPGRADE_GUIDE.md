# Upgrade Guide

Keep dependency and Android toolchain upgrades separate from reader-feature work.

Before changing versions:

1. Record the current working toolchain in `README.md`.
2. Change one version family at a time.
3. Run the validation commands in `docs/BUILD_AND_VALIDATION.md`.
4. Test EPUB import, parser validation, renderer startup, pagination, TOC navigation, settings reload, and position restoration.
5. Do not combine a dependency upgrade with an unrelated architecture change unless required to keep the project buildable.

The application version is controlled by the `BOOKWORMBLISS_VERSION_CODE` and `BOOKWORMBLISS_VERSION_NAME` entries in `gradle.properties`.
