# Fishbone Tuner — Android v0.1

Native Android guitar/bass tuner built with **Kotlin + Jetpack Compose**. Fishbone Dark UI.

## Requirements
- Android Studio with Android SDK 35, JDK 17 and internet access for first Gradle sync.
- Android phone running Android 8.0+ (API 26+) with a microphone.

## Open and run
1. Unzip `FishboneTuner.zip`.
2. In Android Studio choose **Open** and select the `FishboneTuner` folder.
3. Allow the Gradle sync to install dependencies. The ZIP does **not** contain the `gradle-wrapper.jar` bootstrap binary or a prebuilt APK. If Android Studio cannot sync without it, create a new empty Android Studio project using Gradle 8.11.1 and copy its `gradle/wrapper/gradle-wrapper.jar` into this project's `gradle/wrapper/`, or run `gradle wrapper --gradle-version 8.11.1` if Gradle is installed.
4. Connect a real Android phone (USB debugging) and select **Run ▶**. An emulator microphone can be unreliable for tuning.
5. Tap **START TUNING**, grant microphone access, then pluck one string at a time.
6. To create an APK choose **Build → Build App Bundle(s) / APK(s) → Build APK(s)**. Debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## What works
- Live PCM16 mono microphone capture at 44.1 kHz using `AudioRecord`.
- YIN-style pitch detection with median stabilization, ~28–1000 Hz detection window, and noise gate.
- Chromatic mode or manual string tuning; note, frequency, cents and analogue gauge.
- Guitar Standard/Drop D/Drop C/Drop B/B Standard; Bass Standard/Drop D/5-string.
- Local-only microphone analysis; no storage or network permission.

## v0.1 limitations
- Low bass notes may be less stable on phone microphones; harmonics, background noise and noisy rooms affect pitch.
- No calibration A4 adjustment, custom tunings, landscape layout or audio playback yet.
- Not built or tested on a connected Android device in this environment.
- Android Studio/Gradle version requirements may need updating for your local installation.

## Project structure
- `app/src/main/java/com/fishbone/tuner/MainActivity.kt` — Compose interface and microphone permission.
- `.../audio/TunerEngine.kt` — realtime recording and smoothing.
- `.../audio/PitchDetector.kt` — YIN estimator.
- `.../audio/Tuning.kt` — presets/note conversions.

## Δημιουργία APK χωρίς Android Studio (GitHub Actions)

1. Στο github.com δημιούργησε ένα νέο **private** repository (χωρίς αρχικά αρχεία).
2. Ανέβασε **τα περιεχόμενα** του φακέλου `FishboneTuner` στη ρίζα του repository, περιλαμβάνοντας τον κρυφό φάκελο `.github/workflows`.
3. Άνοιξε την καρτέλα **Actions**, επίλεξε **Build Fishbone Tuner APK** και πάτησε **Run workflow**.
4. Μετά από επιτυχημένο build, άνοιξε το run και κατέβασε το artifact **Fishbone-Tuner-v0.1-APK**. Αποσυμπίεσέ το για να βρεις το `app-debug.apk`.
5. Μετέφερε το `app-debug.apk` στο Android και άνοιξέ το για εγκατάσταση. Ίσως χρειαστεί προσωρινή άδεια **Install unknown apps** στον διαχειριστή αρχείων.

Το APK είναι **debug signed**, κατάλληλο για προσωπικές δοκιμές (όχι για Play Store release). Το workflow απαιτεί σύνδεση στο internet από το GitHub και διαθέσιμα GitHub Actions. Η μεταγλώττιση δεν έχει επαληθευτεί σε αυτή τη συνεδρία.
