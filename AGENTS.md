# Agent instructions

## WSL builds (opencode runs in Ubuntu WSL, Android Studio runs on Windows)

- `local.properties` is gitignored and rewritten by Windows Studio on every open
  with `sdk.dir=C\:\\Users\\Xenae\\AppData\\Local\\Android\\Sdk`. Never commit it.
- On Linux that Windows path does not exist, so AGP prints
  `WARNING: ... sdk.dir property in local.properties file. Problem: Directory does not exist`
  and falls back to `$ANDROID_HOME`. That warning is harmless — do not "fix" the file permanently.

### 1. Check the environment before any Gradle call

```sh
echo "JAVA_HOME=$JAVA_HOME ANDROID_HOME=$ANDROID_HOME"
which java; java -version 2>&1 | head -n 2
```

Expect: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`,
`java 21.0.x Ubuntu`, `ANDROID_HOME=$HOME/Android/Sdk`.
(`java` must be Linux; Windows JBR is only `winjava` via `$WIN_JBR_HOME`,
Windows SDK is only `$WIN_ANDROID_HOME`.)

### 2. Run Gradle normally (no flag exists to override `sdk.dir`)

`-Psdk.dir=...` and `-Pandroid.sdk.dir=...` do NOT override `local.properties`
(verified). Just run, e.g.:

```sh
./gradlew :app:assembleDebug
```

### 3. If the build fails with an SDK error, temp-swap the file for one command only

Only needed if `local.properties` contains a *valid* Linux path pointing at the
wrong SDK, or `ANDROID_HOME` is unset (symptom: `missing AAPT`,
`Build Tools ... is corrupted`). Procedure:

```sh
cp local.properties /tmp/local.properties.bak
sed -i "s|^sdk.dir=.*|sdk.dir=$HOME/Android/Sdk|" local.properties
./gradlew :app:assembleDebug
mv /tmp/local.properties.bak local.properties
```

Always restore the backup immediately; never leave the swap in place and never
commit `local.properties`.

## Testing and Emulator Policy

- **No running Android emulator**: Do NOT launch or run the Android emulator for automated or interactive testing (neither directly via `emulator` / `emulator.exe` nor via `android emulator` CLI). There are known difficulties with it for now.
- **Explicit manual testing notice**: Every time manual testing or on-device verification is needed (such as instrumented tests like `connectedDebugAndroidTest`, UI walk-throughs, or hardware/device-specific checks), do NOT attempt to run the emulator. Instead, mention this explicitly in the results and deliverables so the developer can perform manual testing.
- **Automated verification standard**: Rely on JVM unit tests (`./gradlew testDebugUnitTest` or `.\gradlew.bat testDebugUnitTest`), compile checks (`compileDebugSources`), and build assembly (`:app:assembleDebug`, `:app:assembleDebugAndroidTest`).

