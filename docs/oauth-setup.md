# Google sign-in setup (one-time, per developer)

The app builds and the UI runs right away, but **Google sign-in won't work until you do the setup below** — the SHA-1 + package name registered in Google Cloud Console are tied to a specific signing certificate, and `release.jks`/`keystore.properties` are gitignored on purpose (nobody else's build can reuse the original author's OAuth credentials). This has nothing to do with any secret file in this repo — there isn't one to copy — it's entirely configuration on your own Google Cloud project.

1. Create a project in [Google Cloud Console](https://console.cloud.google.com/) (or reuse one you already have).
2. Enable the **Google Drive API** and **Photos Library API** for that project (APIs & Services → Library).
3. Configure the **OAuth consent screen** (APIs & Services → OAuth consent screen): add these scopes (they're in `auth/GoogleApiScopes.kt`):
   - `https://www.googleapis.com/auth/drive.file`
   - `https://www.googleapis.com/auth/photoslibrary.appendonly`
   - `https://www.googleapis.com/auth/photoslibrary.readonly.appcreateddata`
4. While the app is unverified, add your own Google account as a **test user** on that same consent screen — otherwise sign-in will refuse to proceed.
5. Create an **OAuth 2.0 Client ID** of type **Android** (APIs & Services → Credentials → Create Credentials):
   - Package name: `com.santiagojorda.baul`
   - SHA-1 certificate fingerprint: for a debug build, get it from the auto-generated debug keystore:
     ```bash
     keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
     ```
     (copy the `SHA1:` line, it's already colon-separated — paste it as-is)
6. Save, wait a couple of minutes for it to propagate, then `make install` and try connecting an account.

No client ID or secret needs to go in code or in any local file for this — `GoogleSignInClient`/`GoogleAuthUtil` resolve everything server-side from the calling app's package name + signing certificate automatically. If sign-in fails with **error 10 (`DEVELOPER_ERROR`)**, it's almost always one of: SHA-1 doesn't match the installed build's actual signature (debug vs. release use different certificates — verify with `apksigner verify --print-certs path/to/app.apk`), package name typo, or the change hasn't propagated yet.

Building a signed **release** build is a separate step (see `make apk-release` in the main README) and needs its own SHA-1 registered the same way, from your own `release.jks` — not required just to run/test the app locally.
