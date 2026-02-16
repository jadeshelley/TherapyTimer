# Best path: from here to app on the store

Do things in this order. No need to implement billing before your first upload.

---

## 1. One-time: Create your keystore and keystore.properties

1. In Android Studio: **Build → Generate Signed Bundle / APK** → **Android App Bundle** → **Next**.
2. Click **Create new...** and create a keystore:
   - Save it somewhere safe (e.g. project folder or a backup drive). Example: `therapy-timer-keystore.jks`.
   - Set passwords and alias (e.g. `therapy-timer-key`). **Write down both passwords and back up the .jks file.**
3. Copy `keystore.properties.example` to **`keystore.properties`** (in the project root, same folder as `app/`).
4. Edit `keystore.properties`: set `storePassword`, `keyPassword`, `keyAlias`, and `storeFile` to the **full path** to your `.jks` file (use `/` or `\\`).
5. Sync Gradle. From now on, release builds will be signed automatically when you use **Generate Signed Bundle / APK** or build release.

---

## 2. Upload to Internal testing first (no billing required)

1. **Build → Generate Signed Bundle / APK** → **Android App Bundle** → choose your keystore (or it uses `keystore.properties`) → **release** → **Create**. You get an `.aab` file.
2. Go to [Play Console](https://play.google.com/console) → your app (create the app first if needed; use package name `com.example.therapytimer`).
3. **Release → Testing → Internal testing** → **Create new release** → **Upload** the `.aab` → **Save** → **Start rollout to Internal testing**.
4. Add yourself as a tester, open the opt-in link on your phone, install the app. Confirm it installs and runs.

**Why first:** So you know the “create app → build AAB → upload → install” flow works. Billing is easier to test once the app is already on a Play track.

---

## 3. Add in-app product and billing (when you’re ready)

1. In Play Console: **Monetize → Products → In-app products** → create product ID `full_version_unlock`, set price, **Activate**.
2. In **Setup → License testing**, add your Gmail so test purchases don’t charge.
3. In the app: implement BillingManager, wire the paywall “Unlock” button to the real purchase flow, and add “Restore purchases.” (See `docs/PLAY_BILLING_SETUP.md`.)
4. Build a new signed AAB, upload to Internal testing again, and test the purchase flow as a license tester.

---

## 4. When you want the app on the public store

1. In Play Console, finish **Store listing** (description, screenshots, etc.), **Content rating**, **App access**, **Ads** (if any), and **Target audience**.
2. **Release → Production** → create a release and upload your latest `.aab` → **Review and rollout**. Once you start the rollout, the app goes live after review.

---

**Summary:** Create keystore + `keystore.properties` → upload a signed AAB to Internal testing → (later) add product + billing → (when ready) complete store setup and roll out to Production.
