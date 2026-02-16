# Step-by-step: Paywall & Google Play Billing

Use this guide to turn your in-app paywall into real purchases. You use **Android Studio** for code and **Google Play Console** for the product and testing.

---

## Part 1 — Google Play Console

### 1.1 Create or open your app in Play Console

1. Go to [Google Play Console](https://play.google.com/console) and sign in.
2. If you don’t have an app yet:
   - Click **Create app**.
   - Fill in **App name** (e.g. “Therapy Timer”), **Default language**, and accept the declarations.
   - Create the app (you can leave the rest of the store listing for later).
3. Open your app from the dashboard.  
   The app does **not** need to be published. It can stay in draft.

### 1.2 Confirm your app’s package name

1. In Play Console, go to **Setup** → **App integrity** (or **App signing**).
2. Confirm the **Application ID** matches your app: `com.example.therapytimer` (or whatever is in your `app/build.gradle.kts` under `applicationId`).  
   If you haven’t uploaded a build yet, the package is taken from the first upload.

### 1.3 Create the in-app product

1. In the left menu, go to **Monetize** → **Products** → **In-app products**.
2. Click **Create product**.
3. Fill in:
   - **Product ID**: e.g. `full_version_unlock`  
     (Use only lowercase letters, numbers, underscores. You’ll use this exact ID in code.)
   - **Name**: e.g. “Unlock full version”
   - **Description**: Short description for the Play Console (e.g. “Unlock custom routines, add/edit, backup and import”).
4. Under **Pricing**, set the price (e.g. one-time purchase).
5. Save. Set status to **Active** when you’re ready to test.  
   Leave the product **Inactive** until you’ve uploaded a build and added license testers if you prefer.

### 1.4 Set up Internal testing (so billing works)

1. Go to **Testing** → **Internal testing** (or **Release** → **Testing** → **Internal testing**).
2. Create a new **Release** (e.g. “First internal build”).
3. You will **upload an AAB** here later (from Android Studio). For now you can leave the release empty.
4. Add **testers**:
   - Open **Testers** tab and create a list (e.g. “Internal testers”).
   - Add the **email addresses** (Google accounts) that will test purchases (including yours).
5. Save. Copy the **opt-in link** for internal testers; they (and you) will use it to install the app for testing.  
   Billing only works for builds installed from Play (internal/closed/open), not from “Run” in Android Studio until you install the same build via the internal link.

### 1.5 Add license testers (sandbox purchases)

1. Go to **Setup** → **License testing** (or **Settings** → **License testing**).
2. Add the same **Gmail addresses** you use for internal testers.
3. Optionally set **License response** to **RESPOND_NORMALLY** for testing.  
   These accounts will see sandbox purchases (no real charge).

---

## Part 2 — Android Studio: add Billing and build

### 2.1 Add the Play Billing dependency

1. Open your project in Android Studio.
2. In the **Project** view, open **Gradle** → **libs.versions.toml** (under `gradle`).
3. Under `[versions]`, add a line, e.g.:
   ```toml
   billing = "7.0.0"
   ```
4. Under `[libraries]`, add:
   ```toml
   billing-ktx = { group = "com.android.billingclient", name = "billing-ktx", version.ref = "billing" }
   ```
5. Open **app/build.gradle.kts**.
6. In the `dependencies { }` block, add:
   ```kotlin
   implementation(libs.billing.ktx)
   ```
7. Click **Sync Now** (or **File** → **Sync Project with Gradle Files**).

### 2.2 Implement BillingManager and wire the paywall

The project already includes a `BillingManager` and paywall wiring:

- **BillingManager** (`app/src/main/java/.../billing/BillingManager.kt`): connects to Play Billing, queries the product, starts the purchase flow, and calls `PreferencesManager.setFullVersionUnlocked(true)` on success. It also supports **Restore purchases**.
- **SettingsScreen** uses this: the paywall dialog shows **Unlock** (real purchase) and **Restore purchases** instead of “Unlock (for testing)”.

Use the **product ID** you created in Play Console (e.g. `full_version_unlock`) in `BillingManager` (constant at the top of the file). If your product ID is different, change that constant and sync.

### 2.3 Sign the app for Play

1. In Android Studio: **Build** → **Generate Signed Bundle / APK**.
2. Choose **Android App Bundle** (recommended for Play).
3. Create or select a **keystore** (remember path and passwords).
4. Pick **release** (or a build type you use for upload), then **Create**.
5. The AAB is in `app/release/app-release.aab` (or the path shown).

You can use the same keystore for all future updates. If you use **Play App Signing**, you can upload your AAB and let Google manage the signing key.

### 2.4 Upload the build to Internal testing

1. In [Play Console](https://play.google.com/console), open your app.
2. Go to **Testing** → **Internal testing** → the release you created.
3. Click **Create new release** (or **Edit release**).
4. **Upload** the AAB you built (`app-release.aab`).
5. Add **Release name** (e.g. “1.0 (1)”) and save.
6. **Review release** → **Start rollout to Internal testing**.
7. Wait a few minutes (sometimes up to a few hours) for Play to process the build.

### 2.5 Install from Play and test purchases

1. On your phone, open the **internal testing opt-in link** from step 1.4 (or from the Internal testing page: **Copy link**).
2. Accept the tester invite and install the app from the Play Store (the link opens the store page).
3. Open the app, go to **Settings**, tap **Unlock full version**.
4. In the paywall dialog:
   - Tap **Unlock** → the Play purchase sheet should appear. Complete the purchase (sandbox = no real charge).
   - After success, the app should unlock (add/edit routines, etc.).
5. Test **Restore purchases**: uninstall, reinstall from the same internal link, open Settings → Unlock full version → **Restore purchases**. The app should unlock again without paying.

---

## Part 3 — Checklist

- [ ] App created in Play Console (package = `com.example.therapytimer` or your `applicationId`).
- [ ] In-app product created (e.g. `full_version_unlock`), status Active.
- [ ] Internal testing release created; testers list and opt-in link set.
- [ ] License testers added (same Gmail accounts).
- [ ] Billing dependency added in project; BillingManager uses the same product ID.
- [ ] Signed AAB built and uploaded to Internal testing; rollout started.
- [ ] App installed from internal testing link; Unlock and Restore tested.

---

## Troubleshooting

- **“Item not found” or product not loading**  
  - Product ID in code must match exactly (e.g. `full_version_unlock`).  
  - App must be installed from Play (internal link), not from Android Studio Run.  
  - Wait a few hours after first upload and after activating the product.

- **Purchase doesn’t unlock**  
  - Check that on purchase success you call `preferencesManager.setFullVersionUnlocked(true)` (BillingManager does this).  
  - Check Logcat for BillingClient / BillingManager logs.

- **Restore doesn’t work**  
  - Ensure you query **purchases** (e.g. `queryPurchasesAsync`) and for each owned item call `setFullVersionUnlocked(true)`.

- **Testing on emulator**  
  - Use an emulator with **Google Play** (not “Google APIs” only).  
  - Log in with a license tester Gmail.  
  - Install the app from the internal testing link (sideload or download from Play on the emulator).

Once this works, you can add a **Closed** or **Open** testing track, or go to production, without changing the billing code.
