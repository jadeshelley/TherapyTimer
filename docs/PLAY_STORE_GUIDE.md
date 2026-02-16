# Google Play: Account, Test Upload, and Pro Paid Version

This guide covers: (1) setting up a Google Play developer account, (2) uploading Therapy Timer as a **test app** (Internal testing), and (3) how the app is set up for the **Pro paid version** (in-app purchase).

---

## Part 1: Google Play developer account

### What you need

- A **Google account** (Gmail).
- A **one-time registration fee** (as of 2025, typically **$25**). Payment is via the Play Console; it’s a one-time fee, not per app.
- **Identity verification** (name, address, etc.) as required by Google.

### Steps

1. Go to **[Google Play Console](https://play.google.com/console)** and sign in with your Google account.
2. Accept the **Developer Distribution Agreement**.
3. Pay the **registration fee** ($25 one-time) when prompted.
4. Complete **account details**: developer name (shown on the store), email, and any requested verification.

After this, you can create apps and upload builds. You do **not** need to publish to the public store to use Internal testing.

---

## Part 2: Upload the app as a test app (Internal testing)

Internal testing lets you (and testers you add) install the app from Play without making it public. Billing can be tested here using license testers.

### Step 1: Create the app in Play Console

1. In [Play Console](https://play.google.com/console), click **Create app**.
2. Fill in:
   - **App name**: e.g. *Therapy Timer*
   - **Default language**: e.g. English (United States)
   - **App or game**: App
   - **Free or paid**: Free (the app is free; Pro is an in-app purchase)
3. Accept declarations and click **Create app**.

### Step 2: Complete required setup (so you can use Internal testing)

Play requires some sections to be filled before you can publish to any track (including Internal testing). Do the minimum:

- **App access**: Indicate whether the app has restricted access (e.g. login). If everyone can use the free part, choose the option that says no special access.
- **Ads**: Does your app contain ads? Select **No** (or Yes if you add ads later).
- **Content rating**: Complete the questionnaire (e.g. category, content) and submit. You’ll get a rating (e.g. Everyone).
- **Target audience**: Set age groups (e.g. 18+ or 13+ depending on your app).
- **News app**: Usually **No**.

You can leave **Store listing** (description, screenshots) as draft for now; you’ll need it before going to Production.

### Step 3: Sign the app and build an AAB

Your app is already set up to use a **release keystore** when `keystore.properties` exists.

1. **Create a keystore** (one-time):
   - In Android Studio: **Build → Generate Signed Bundle / APK**.
   - Choose **Android App Bundle** → **Next**.
   - Click **Create new...** and create a new keystore:
     - Save the `.jks` file somewhere safe (e.g. `therapy-timer-keystore.jks`).
     - Set **Key store password**, **Key alias**, **Key password**.
     - **Back up the .jks file and passwords**; you need them for every future update.
2. **Configure the project** to use it:
   - Copy `keystore.properties.example` to **`keystore.properties`** in the project root.
   - Edit `keystore.properties`: set `storePassword`, `keyPassword`, `keyAlias`, and `storeFile` (full path to the `.jks` file).
3. **Build the release AAB**:
   - **Build → Generate Signed Bundle / APK** → **Android App Bundle** → select your keystore → **release** → **Create**.
   - The `.aab` file is in `app/release/` (or the path shown in the dialog).

### Step 4: Upload to Internal testing

1. In Play Console, open your app.
2. Go to **Testing → Internal testing**.
3. Click **Create new release** (or use the existing one).
4. Upload the `.aab` you built. Add a **release name** (e.g. “1.0 (1)”) and optional **release notes**.
5. Click **Save** → **Review release** → **Start rollout to Internal testing**.

### Step 5: Add testers and install

1. In **Internal testing**, open the **Testers** tab.
2. Create a **mailing list** (e.g. “Internal testers”) and add your Gmail (and any other testers).
3. Copy the **opt-in URL** and open it on your phone (signed in with that Google account). Accept the opt-in.
4. Install the app from the Internal testing link (or from the Play Store internal testing card).

You’ve now uploaded the app as a **test app** on Internal testing.

---

## Part 3: Pro paid version (in-app product)

Therapy Timer is already set up for a **Pro** paid tier via a **one-time in-app product** (not a separate paid app).

### How it works in the app

- **Free**: Basic mode and one demo routine.
- **Pro (paid)**: Unlock custom routines, add/edit/reorder exercises, backup and import/export. Unlock is a **one-time purchase**.

### App configuration (already done)

| Item | Value | Where |
|------|--------|--------|
| Product ID | `full_version_unlock` | `BillingManager.kt` + must match Play Console |
| Billing | Google Play Billing (billing-ktx) | `app/build.gradle.kts` |
| Unlock state | Stored in SharedPreferences | `PreferencesManager` |
| Paywall | Settings → “Unlock full version” / “Unlock Pro” | `SettingsScreen.kt` |

### What you must do in Play Console for Pro

1. **Create the in-app product**
   - In Play Console: **Monetize → Products → In-app products** → **Create product**.
   - **Product ID**: `full_version_unlock` (must match the app exactly).
   - **Name**: e.g. *Therapy Timer Pro* or *Unlock Pro*.
   - **Description**: e.g. “Unlock custom routines, add and edit exercises, backup and import.”
   - **Price**: Set a one-time price (e.g. $2.99).
   - **Save** → **Activate**.

2. **License testing** (so testers aren’t charged)
   - **Setup → License testing** (or **Settings → License testing**).
   - Add the Gmail addresses that will test purchases. They can “buy” Pro without being charged.

### Testing the Pro purchase

1. Install the app from **Internal testing** (same build you uploaded).
2. Open **Settings** → tap the Pro / “Unlock full version” card.
3. Tap **Unlock** (or the price). Complete the purchase flow; as a license tester you won’t be charged.
4. After purchase, the app should unlock (e.g. add routine, edit, export). Use **Restore purchases** after reinstall to verify restore.

---

## Checklist: Is the app ready for Play?

- [ ] **Keystore**: Created and `keystore.properties` filled (path, passwords, alias). Never commit `keystore.properties`.
- [ ] **applicationId**: `com.example.therapytimer` (or your final package name). Must match the app you create in Play Console.
- [ ] **versionCode** / **versionName**: Set in `app/build.gradle.kts` (e.g. `versionCode = 1`, `versionName = "1.0"`). Increment `versionCode` for each upload.
- [ ] **Release signing**: Release build uses `signingConfigs.release` when `keystore.properties` exists.
- [ ] **Billing**: Dependency `billing-ktx` and `BillingManager` with product ID `full_version_unlock`.
- [ ] **Pro product in Play Console**: In-app product `full_version_unlock` created and **Activate**d.
- [ ] **License testers**: Added in Play Console so test purchases don’t charge.

---

## Changing to “Pro” branding (paid version)

The code uses the product ID `full_version_unlock` (required to match Play Console). The **user-facing text** can say “Pro”:

- In **Play Console**, set the in-app product **name** to e.g. *Therapy Timer Pro*.
- In the app, the Settings paywall and dialog already use (or can use) “Pro” in the title and button (e.g. “Unlock Pro”, “Therapy Timer Pro”). See `SettingsScreen.kt` and any strings you add.

So: **product ID stays `full_version_unlock`**; **label it “Pro”** in the console and in the app UI.

---

## Summary

1. **Account**: Play Console → pay $25 one-time → complete profile.
2. **Test app**: Create app → complete App access, Ads, Content rating, Target audience → build signed AAB with `keystore.properties` → **Testing → Internal testing** → upload AAB → add testers → install via opt-in link.
3. **Pro paid version**: Create in-app product `full_version_unlock` in **Monetize → In-app products**, activate it, add license testers, then test purchase and restore from an Internal testing install.

For more detail on billing in code, see `PLAY_BILLING_SETUP.md` and `BEST_PATH_TO_STORE.md`.
