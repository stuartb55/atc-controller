# Releasing Manchester Approach

Only CI should produce a distributable Android App Bundle. The release build reads its signing
material from environment variables through Gradle providers; no keystore, password or signing
properties file belongs in this repository.

## Required CI environment

- `CI=true`
- `ATC_RELEASE_VERSION_CODE`: a positive, monotonically increasing CI run number in the range
  `2..2100000000`. Configure the release workflow to source this from its protected, repository-wide
  monotonic counter and never reuse a value accepted by Play.
- `ATC_RELEASE_STORE_FILE`: absolute path to a temporary JKS keystore created by the CI secret store.
- `ATC_RELEASE_STORE_PASSWORD`: keystore password from the CI secret store.
- `ATC_RELEASE_KEY_ALIAS`: release-key alias from the CI secret store.
- `ATC_RELEASE_KEY_PASSWORD`: release-key password from the CI secret store.

After the CI runner has materialized the temporary keystore and exported those values, run exactly:

```sh
./gradlew --no-configuration-cache :app:bundleRelease
```

The configuration cache is deliberately disabled for this task so credentials cannot be serialized
under `.gradle/`. The build fails before release-specific work if any variable is absent, the version
code is invalid, the keystore path is unreadable, or an abbreviated task name is used. The signed
bundle is written to `app/build/outputs/bundle/release/app-release.aab`. Delete the temporary
keystore in an always-run CI cleanup step, and retain the mapping file from
`app/build/outputs/mapping/release/` with the release artifacts.

For local shrinking and packaging verification, leave all signing variables unset and run:

```sh
./gradlew :app:assembleRelease
```

That command intentionally produces an unsigned APK and does not require a release version code.
