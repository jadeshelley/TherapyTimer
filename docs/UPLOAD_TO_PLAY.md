# How to upload your app to Google Play

You **don’t need to change any code** just to upload. You only need to **build a signed release** and then **upload that file** in Play Console.

---

## Step 1: Create a keystore (one-time)

Android needs a **keystore** to sign the release build. You only create it once; keep the file and passwords safe.

1. In Android Studio: **Build → Generate Signed Bundle / APK**.
2. Choose **Android App Bundle** (recommended) → **Next**.
3. Under **Key store path**, click **Create new...**.
4. Fill in:
   - **Key store path**: e.g. `C:\Users\spyxa\AndroidStudioProjects\TherapyTimer\therapy-timer-keystore.jks` (or pick a folder and name).
   - **Password**: choose a password (and confirm). **Remember it.**
   - **Alias**: e.g. `therapy-timer-key`.
   - **Key password**: can be same as keystore password.
   - **Validity**: 25 years is fine.
   - **First and Last name**, etc.: can be your name or app name.
5. Click **OK**, then **Next**.
6. Select **release** build variant, click **Create**. Android Studio will build the AAB.
7. When it’s done, it will say where the file is (e.g. `app/release/app-release.aab`). **Don’t upload this one yet** if you just created the keystore — you need to **add the keystore to your project** so future builds use it (see Step 2). Or you can use this AAB to upload.

**Important:** Back up the `.jks` file and write down the two passwords. If you lose them, you can’t update the app on Play Store with the same key.

---

## Step 2: (Optional) Save signing config so you don’t re-enter it

So you don’t have to pick the keystore every time:

1. In the project, create a file `keystore.properties` in the **project root** (same folder as `build.gradle.kts`), with something like (use your real path and passwords):

```
storePassword=YourKeystorePassword
keyPassword=YourKeyPassword
keyAlias=therapy-timer-key
storeFile=C:\\Users\\spyxa\\AndroidStudioProjects\\TherapyTimer\\therapy-timer-keystore.jks
```

2. Add `keystore.properties` to `.gitignore` so you don’t commit passwords:
   - Open `.gitignore` and add a line: `keystore.properties`

3. In `app/build.gradle.kts`, inside `android { ... }`, add a `signingConfigs` block and use it in `buildTypes.release`. Example:

```kotlin
android {
    // ... existing namespace, defaultConfig, etc. ...

    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            if (keystorePropertiesFile.exists()) {
                val keystoreProperties = java.util.Properties()
                keystoreProperties.load(keystorePropertiesFile.inputStream())
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            // ... rest unchanged
        }
    }
}
```

Then **Build → Generate Signed Bundle / APK** again: you can leave keystore fields as-is (or they’ll be read from `keystore.properties`). Build the AAB.

---

## Step 3: Upload the AAB in Play Console

1. Go to [Google Play Console](https://play.google.com/console) and open your app (or create one with the same **applicationId** as your app: `com.example.therapytimer`).
2. In the left menu: **Release** → **Testing** → **Internal testing** (or **Production** when you’re ready for the public).
3. Click **Create new release** (or the existing release).
4. Under **App bundles**, click **Upload** and select your **`.aab`** file (e.g. `app/release/app-release.aab`).
5. Add a **Release name** (e.g. "1" or "1.0 (1)") and **Release notes** if asked.
6. Click **Save**, then **Review release**, then **Start rollout to Internal testing** (or **Save** and do that later).

That’s it — the app is “uploaded” to that track. It’s **not** on the public store until you roll out to **Production** and complete store listing, policy, etc.

---

## Summary

| Question | Answer |
|----------|--------|
| Do I need to change the app before uploading? | **No.** Build a signed AAB and upload it. |
| How do I build the file to upload? | **Build → Generate Signed Bundle / APK** → choose or create keystore → build **Android App Bundle**. |
| Where do I upload it? | **Play Console** → your app → **Release** → **Testing** → **Internal testing** → **Create new release** → **Upload** the `.aab` file. |
| Is it on the store after upload? | Only on the **Internal testing** track (or whichever you chose). The public store is **Production**; you do that later when you’re ready. |

If you want, we can add the exact `signingConfigs` block to your `app/build.gradle.kts` so release builds are signed automatically (after you create the keystore and `keystore.properties`).
