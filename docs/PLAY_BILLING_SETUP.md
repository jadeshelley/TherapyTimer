# Step-by-step: Google Play Billing for Therapy Timer

Your app uses `applicationId = "com.example.therapytimer"`. These steps assume you use that (or your final package name) in Play Console.

---

## Part 1: Google Play Console (web)

### Step 1: Create / open your app in Play Console

1. Go to [Google Play Console](https://play.google.com/console) and sign in.
2. If you haven’t created an app yet:
   - Click **Create app**.
   - Fill in **App name** (e.g. "Therapy Timer"), **Default language**, and accept the declarations.
   - Click **Create app**.
3. If asked, complete **App access**, **Ads** (if no ads, say “No”), and **Content rating** so you can use testing tracks. You can leave **Store listing** and **Pricing** as draft for now.

### Step 2: Create the in-app product

1. In the left menu: **Monetize** → **Products** → **In-app products**.
2. Click **Create product**.
3. Fill in:
   - **Product ID**: `full_version_unlock` (use this exact ID in code later).
   - **Name**: e.g. "Unlock full version".
   - **Description**: e.g. "Unlock custom routines, add/edit/import/export."
   - **Price**: set a price (e.g. one-time purchase).
4. Click **Save** and then **Activate** the product.

### Step 3: Set up Internal testing

1. Left menu: **Testing** → **Internal testing**.
2. Click **Create new release** (or use the first release).
3. You’ll upload an AAB here in Part 2. For now you can leave the release empty and come back after building.

### Step 4: Add license testers (for free test purchases)

1. Left menu: **Setup** → **License testing** (or **Settings** → **License testing**).
2. Add the **Gmail addresses** that will test purchases (e.g. your own). These accounts can “buy” the product without being charged.
3. Save.

---

## Part 2: Android Studio – add billing to the app

### Step 5: Add the Billing Library dependency

1. In Android Studio, open **Gradle** (e.g. **View → Tool Windows → Gradle**), or open `app/build.gradle.kts` in the Project view.
2. In `app/build.gradle.kts`, in the `dependencies { ... }` block, add:

```kotlin
implementation("com.android.billingclient:billing-ktx:7.1.1")
```

3. Sync the project (**File → Sync Project with Gradle Files** or the elephant icon).

### Step 6: Create a BillingManager (or use a single helper)

Create a class that:

- Builds and connects a `BillingClient` (e.g. in `Application` or when Settings opens).
- Defines the product ID: `const val FULL_VERSION_PRODUCT_ID = "full_version_unlock"`.
- Queries product details with `queryProductDetailsAsync()` for this product.
- Launches the purchase flow with `launchBillingFlow()` when the user taps **Unlock** in the paywall.
- In `onPurchasesUpdated`, when a purchase is `PURCHASED`, call `preferencesManager.setFullVersionUnlocked(true)` and dismiss the paywall.
- Optionally: query existing purchases (e.g. `queryPurchasesAsync()`) for “Restore purchases” and set the entitlement if the product is owned.

You can implement this in a single class (e.g. `BillingManager`) that takes `Context` and `PreferencesManager`, and expose a simple API: `queryProductDetails(callback)`, `purchase(activity, callback)`, `restorePurchases(callback)`.

### Step 7: Replace the test “Unlock” button in the paywall

1. Open **SettingsScreen.kt** and find the paywall `AlertDialog` where the **“Unlock (for testing)”** button only calls `setFullVersionUnlocked(true)`.
2. Replace that with a call to your BillingManager’s purchase method (e.g. pass the current `Activity` from `LocalContext.current as? Activity`).
3. On success in the callback, call `preferencesManager.setFullVersionUnlocked(true)`, set `fullVersionUnlocked = true`, and dismiss the dialog (`showPaywall = false`).
4. Optionally show the product **price** from `ProductDetails` in the dialog and handle loading/error states.

### Step 8: (Optional) Restore purchases

- Add a “Restore purchases” button in the paywall or in Settings.
- When tapped, call BillingManager’s `queryPurchasesAsync()` (or equivalent). If the full-version product is in the list, call `setFullVersionUnlocked(true)` and update UI.

---

## Part 3: Build and upload for testing

### Step 9: Sign the app (if not already)

- For Play uploads you need a **release** keystore. If you don’t have one:
  - **Build → Generate Signed Bundle / APK** → **Android App Bundle** → **Create new...** to create a keystore and key. Remember passwords and store the keystore file safely.
- Configure `signingConfigs` in `app/build.gradle.kts` for release (or use the same dialog to build the AAB).

### Step 10: Build the release AAB

1. **Build → Generate Signed Bundle / APK**.
2. Choose **Android App Bundle**, select your keystore, and build the release AAB.
3. The AAB file will be in `app/release/` (or the path shown in the wizard).

### Step 11: Upload to Internal testing

1. In Play Console, go to **Testing** → **Internal testing**.
2. Open the release you created in Step 3 (or create one).
3. Upload the AAB you built. Add release name/notes if required.
4. **Save** and **Review release** → **Start rollout to Internal testing**.

### Step 12: Install and test on a device

1. In **Internal testing**, open the **Testers** tab and add the same Gmail you added as a license tester.
2. Copy the **opt-in link** and open it on your phone (signed in with that Gmail). Accept the opt-in.
3. Install the app from the Internal testing link (or from the Play Store internal testing card).
4. In the app, open Settings → tap **Unlock full version** → complete the purchase flow. It should complete without charging (license tester).
5. Confirm that after “purchase” the app shows full version (e.g. you can add routines). Optionally uninstall, reinstall, and use **Restore purchases** to verify restore.

---

## Checklist

- [ ] App created in Play Console (can stay unpublished).
- [ ] In-app product created with ID `full_version_unlock` and activated.
- [ ] License testers added in Play Console.
- [ ] Billing Library added in `app/build.gradle.kts`.
- [ ] BillingManager (or helper) implemented: connect, query product, launch purchase, handle result and restore.
- [ ] Paywall “Unlock” button uses real purchase flow; on success, set `fullVersionUnlocked` and dismiss.
- [ ] Release AAB built and uploaded to Internal testing; rollout started.
- [ ] Installed from Internal testing and purchase tested as license tester.

---

## Notes

- **Package name**: Your app uses `com.example.therapytimer`. For a real published app you may want to change this to your own domain (e.g. `com.yourname.therapytimer`) and create the Play Console app with that package name. Changing `applicationId` later is possible but more involved.
- **Debug vs release**: Billing works on builds signed with the same keystore you use for the track. Internal testing uses the uploaded AAB (release or a build type you configure). For quick iteration you can use **Internal app sharing** with a signed AAB if you prefer.

If you want, the next step can be concrete code for `BillingManager` and the exact changes in `SettingsScreen.kt` for your project.
