# 🔊 Payment Soundbox - UPI Alert

An offline Android app that **speaks every received UPI payment out loud** — like the speaker boxes shopkeepers use.

> Example: *"200 rupees received from Google Pay"*

- Listens to **PhonePe, Google Pay, Paytm** (and BHIM) notifications
- Speaks only when the notification says **received / credited** (never sent / debited)
- Loud text-to-speech (media volume is pushed to MAX, then restored)
- English / Hindi switch, last 10 payments list, test button
- **Setup checklist** screen to enable every permission needed to stay alive
- Foreground service keeps it alive
- **No INTERNET permission** – everything stays on your phone

Package: `com.soundbox.app` · Min SDK 24 · Target SDK 34

---

## 1) Push to GitHub (APK builds automatically)

```bash
cd Payment-Soundbox
git init
git add .
git commit -m "Initial commit - Payment Soundbox"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/Payment-Soundbox.git
git push -u origin main
```

Create the empty repo `Payment-Soundbox` on github.com first (no README/license), and replace `YOUR_USERNAME`.

## 2) Download the APK

1. Open your repo on GitHub → **Actions** tab
2. Click the latest **Build APK** run (wait for the green tick, ~3-5 min)
3. Scroll to **Artifacts** → download **payment-soundbox-apk** (a zip containing `app-debug.apk`)

## 3) Install on your phone

1. Copy `app-debug.apk` to your phone and tap it
2. Allow *Install unknown apps* for your file manager/browser if asked
3. Open **Payment Soundbox**

## 4) Enable notification access (required)

1. Open the app and tap **Enable notification access**
2. Turn **Payment Soundbox - UPI Alert** ON and confirm
3. Come back — status should show **ENABLED ✓**
4. Tap **Test sound (200 Rs)** — you should hear "200 rupees received from Google Pay"

### ✅ Use the in-app Setup checklist
The main screen has a **Setup checklist (keep app alive)** with live status. Tap each button until everything is ✓:

| # | Item | How it is checked |
|---|---|---|
| 1 | Allow restricted settings (Android 13+ APK installs) | Tick it yourself (auto-ticks once access is ON) |
| 2 | Notification access | Automatic ✓/✗ |
| 3 | Show notifications (keeps the "running" notification) | Automatic ✓/✗ |
| 4 | Battery: Unrestricted | Automatic ✓/✗ |
| 5 | Autostart / background (Xiaomi, Oppo, Vivo, Realme, Huawei) | Tick it yourself after opening the screen |
| 6 | Don't pause app if unused (Android 11+) | Automatic ✓/✗ |
| 7 | Lock app in Recents | Tick it yourself (button shows how) |
| 8 | Payment app notifications ON (one row per installed app: PhonePe, GPay, Paytm, BHIM, business apps) | Tap Open → enable notifications → tick |

If the notification access toggle is greyed out: **Settings → Apps → Payment Soundbox → ⋮ → Allow restricted settings**, then enable access again.

### More tips
- Lock the app in **Recents** (long-press the app card → Lock) on Xiaomi/Oppo/Vivo/Realme/Samsung
- Make sure PhonePe/GPay/Paytm notifications are **not muted**
- For Hindi voice: install *Google Text-to-speech* Hindi voice data; otherwise it falls back to English

---

## ✏️ Modify this app on your own

Every important place has a searchable marker like `ANCHOR: NAME`. Search the project for it (Ctrl+F / Ctrl+Shift+F).

| I want to… | File | Search for |
|---|---|---|
| Add another UPI app (Amazon Pay, CRED…) | `PaymentNotificationService.kt` **and** `AndroidManifest.xml` | `ANCHOR: SUPPORTED_APPS`, `ANCHOR: QUERIES` |
| Change which words trigger it | `PaymentNotificationService.kt` | `ANCHOR: KEYWORDS` |
| Change the amount regex | `PaymentNotificationService.kt` | `ANCHOR: AMOUNT_REGEX` |
| Change what it says / add a language text | `TTSManager.kt` | `ANCHOR: SPOKEN_MESSAGES` |
| Change test amount | `MainActivity.kt` | `ANCHOR: TEST_SOUND_BUTTON` |
| Change history size (10) | `AppPrefs.kt` | `MAX_HISTORY` |
| Add / edit setup checklist items | `MainActivity.kt` | `ANCHOR: SETUP_CHECKLIST_ROWS`, `CHECKLIST_LOGIC` |
| Add another phone brand's Autostart screen | `MainActivity.kt` | `autostartScreens` |
| Change screen design | `res/layout/activity_main.xml` | `ANCHOR:` |
| Change app name | `res/values/strings.xml` | `app_name` |
| Change colors | `res/values/colors.xml` | `green_primary` |

**Find a UPI app's package name:** open its Play Store page — the link ends with `?id=PACKAGE_NAME`.

**Example – add Amazon Pay:**
```kotlin
"in.amazon.mShop.android.shopping" to "Amazon Pay",
```

**Example – new spoken sentence (English):**
```kotlin
"Payment of $amount rupees received on $appName"
```

**Workflow:** edit a file → `git add . && git commit -m "change" && git push` → new APK appears in the **Actions** tab.

### Using AI to modify it
Paste the file you want to change plus a request like:
> "In `TTSManager.kt`, find `ANCHOR: SPOKEN_MESSAGES` and add Marathi support."

Because of the anchors, the AI can edit the exact spot without reading the whole project.

### Build locally (optional)
```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```
Needs JDK 17 and the Android SDK. `gradlew` downloads `gradle-wrapper.jar` automatically on first run.

---

## How it works

```
UPI app notification
      ↓
PaymentNotificationService  (NotificationListenerService)
  • package in supported list?
  • text has received/credited/"paid you" and NOT sent/debited?
  • amount found by regex?
      ↓
TTSManager  → max volume → speaks "{amount} rupees received from {app}"
      ↓
AppPrefs (SharedPreferences) → last 10 payments → RecyclerView
KeepAliveService → foreground notification keeps the app alive
```

## Notes
- Debug-signed APK, for personal use. Don't rely on it as your only payment confirmation.
- Notification wording differs between app versions; if one is missed, adjust `KEYWORDS` / `AMOUNT_REGEX`.
- The app reads notifications only from the listed UPI apps and never sends data anywhere.
