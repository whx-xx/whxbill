# WHX Bill Android User App

This native Android app mirrors the WHX Bill web user-side workflows and adds automatic bookkeeping. OCR is not part of the Android app target; the mobile goal is web user feature parity plus accessibility-based WeChat and Alipay bill recognition with user confirmation.

## Build Output

Debug APK:

```text
android-app\app\build\outputs\apk\debug\app-debug.apk
```

Build command:

```powershell
$env:ANDROID_HOME="C:\Users\Administrator\AppData\Local\Android\Sdk"
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
android-app\gradlew.bat -p android-app --no-daemon assembleDebug
```

Unit test command:

```powershell
$env:ANDROID_HOME="C:\Users\Administrator\AppData\Local\Android\Sdk"
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
android-app\gradlew.bat -p android-app --no-daemon testDebugUnitTest
```

## Preflight And Install

Run the preflight script:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\android-auto-bill-preflight.ps1
```

Install after connecting an authorized Android phone:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\android-auto-bill-preflight.ps1 -Install
```

Collect diagnostics after reproducing an auto-bookkeeping problem:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\android-auto-bill-preflight.ps1 -CollectLogs
```

Open Android permission pages after installing on a phone:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\android-auto-bill-preflight.ps1 -OpenAccessibilitySettings
powershell -ExecutionPolicy Bypass -File scripts\android-auto-bill-preflight.ps1 -OpenOverlaySettings
```

Diagnostics are saved under:

```text
android-app\build\diagnostics
```

If no device is detected:

```text
No authorized Android device detected.
```

Enable Developer options and USB debugging on the phone, reconnect USB, and allow this computer on the phone prompt.

## Required App Setup

1. Open the Android app.
2. Log in with the same user account as the web user system.
3. Set backend base URL in the Mine page if the default cannot reach the backend.
4. Select or create a book.
5. Ensure the current book has income and expense categories.
6. Go to Auto tab and keep auto bookkeeping enabled.

## Web User Feature Parity

The first Android version should cover the user-side workflows that matter for daily bookkeeping:

| Web user workflow | Android entry | Current coverage |
| --- | --- | --- |
| Login and session | Login page, Mine | Token and backend base URL are stored in DataStore. |
| Home summary | Home | Monthly income, expense, balance, and recent auto bookkeeping bills. |
| Bill list | Bills | List, source filter, keyword/date filters, load more, detail, create/edit/delete through API. |
| Statistics | Statistics | Book-aware month, year, and custom date filters with category/trend data. |
| Calendar | Calendar | Date-based bill list and summary for the current book. |
| Book management | Management | Book list, create/update, delete, and current book selection. |
| Account management | Management | Account list, create/update, delete. |
| Category management | Management | Income/expense category list, create/update, delete, and parent category support. |
| Budget management | Management | Budget list, create/update, delete. |
| Messages and notices | Mine | User messages, notice list, and read actions. |
| Notes | Mine | Local notes stored in Room. |
| OCR import | Not included | Intentionally removed from Android target. |

## Android Permissions

Accessibility:

- Required for reading visible text from WeChat and Alipay payment or bill pages.
- Enable WHX Bill auto bookkeeping service in Android Accessibility settings.
- The preflight script can open this page with `-OpenAccessibilitySettings`.

Overlay:

- Optional but recommended.
- Enabled: a confirm card appears on top of WeChat/Alipay pages.
- Disabled: recognized drafts fall back to the app Pending page.
- The preflight script can open this app permission page with `-OpenOverlaySettings`.

The app does not read WeChat/Alipay databases and does not click or operate third-party apps.

Permission privacy boundary:

- The accessibility service is limited to `com.tencent.mm` and `com.eg.android.AlipayGphone`.
- It reads visible page text only when WeChat or Alipay windows change.
- Recognized content becomes a local pending draft first.
- A bill is saved to the backend only after the user taps Add Bill or confirms a draft.
- Debug logs intentionally record status, source app, character counts, confidence, and draft ids, not the full bill text.

## Manual Test Checklist

User-side parity:

1. Log in and verify Home loads the current month summary.
2. Create or select a book in Management.
3. Create account, category, and budget records in Management.
4. Add, filter, open, edit, and delete bills in Bills.
5. Switch Statistics between month, year, and custom date filters.
6. Open Calendar and verify the current book's selected date bills.
7. Open Mine and verify profile, messages, notices, local notes, settings, and logout.

Automatic bookkeeping:

1. Open Auto tab and verify readiness lines.
2. Open WeChat payment success page.
3. Open WeChat bill detail page.
4. Open WeChat transaction list item.
5. Open Alipay payment result page.
6. Open Alipay bill detail page.
7. Verify amount, merchant, time, account, income/expense direction, and category.
8. Verify the confirm card can edit book, amount, type, time, account, category, merchant, and remark.
9. Tap Add Bill and verify the bill appears in Bills with source type AUTO / auto bookkeeping.
10. Reopen the same bill page and verify no duplicate draft is generated.
11. Tap Later and verify the draft stays in Pending.
12. Disable overlay permission and verify fallback to Pending still works.
13. Close the app and reopen it; unconfirmed drafts should still exist.
14. Confirm a draft; it should be removed from Pending only after save succeeds.

## Diagnostic Flow

Use Auto tab -> Recent Capture:

- Status tells whether the service captured, ignored, saved a low confidence draft, or fell back to Pending.
- Parsed fields show amount, merchant, account, category, time, and confidence.
- Copy Diagnostics can be used to preserve a failed recognition sample.

Common statuses:

```text
No bill recognized
Low confidence draft saved
Draft generated and ready to confirm
Overlay permission missing, switched to in-app confirmation
Duplicate page ignored
Similar bill ignored
```

If a real device does not show the confirmation card:

1. Reproduce the problem on the phone.
2. Return to the app Auto tab and copy Recent Capture diagnostics.
3. Run `scripts\android-auto-bill-preflight.ps1 -CollectLogs`.
4. Check whether the diagnostics show the accessibility service enabled and overlay permission allowed.
5. If overlay is not allowed, the draft should still appear in Pending.

## Current Blocking Item

Real-device verification is not complete until an authorized Android device is connected and the checklist above is executed on WeChat and Alipay.
