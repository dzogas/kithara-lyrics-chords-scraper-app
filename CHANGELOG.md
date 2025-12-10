# 📜 Changelog

Όλες οι σημαντικές αλλαγές σε αυτό το project θα καταγράφονται εδώ.


## [0.1.1] - 2025-12-10
### Added
- Ενσωμάτωση **AndroidX SplashScreen API** με νέο `Theme.KitharaScraper.Splash`.
- Υποστήριξη **custom PNG splash logo** μέσω `windowSplashScreenAnimatedIcon`.
- Προσθήκη **windowSplashScreenIconBackground** για κυκλικό background πίσω από το splash icon.
- Localization strings για μηνύματα (π.χ. `Unknown Artist`, `Unknown Title`, `page_loaded`, `copied_clipboard`, `saved_downloads`, `share_file`).

### Changed
- Αναβάθμιση `MainActivity` ώστε να καλεί `installSplashScreen()` στην εκκίνηση.
- Ενημέρωση `themes.xml` με δύο themes:
  - `Theme.KitharaScraper` (κανονικό UI).
  - `Theme.KitharaScraper.Splash` (splash screen).
- Ενημέρωση `AndroidManifest.xml` ώστε το `application` να χρησιμοποιεί το splash theme.
- Καθαρισμός Gradle dependencies και προσθήκη `androidx.core:core-splashscreen`.

### Fixed
- Διορθώθηκε το σφάλμα **Unresolved reference 'SplashScreen'** με σωστό import:
  ```kotlin
  import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
- Διορθώθηκε το σφάλμα Too many arguments for installSplashScreen() (χρήση χωρίς παραμέτρους).
- Διορθώθηκε το σφάλμα Cannot resolve symbol '@drawable/splash_logo' με σωστή τοποθέτηση PNG σε res/drawable/.
- Διορθώθηκε το σφάλμα Duplicate attribute στο themes.xml (μόνο μία δήλωση για windowSplashScreenAnimatedIcon).
- Διορθώθηκε το warning Namespace declaration is never used με αφαίρεση του xmlns:tools από <resources>.
- Διορθώθηκε το warning Version reference 'splashscreen' is not used με καθαρισμό του libs.versions.toml.


## [0.1.0] - 2025-12-10
### Fixed
- Διάφορες διορθώσεις προειδοποιήσων στο Android Studio .

### Improved
- Ενίσχυση Ασφάλειας Webview.

## [0.0.9] - 2025-12-10
### Added
- Νέο app icon με σωστό padding.
- Προσθήκη LICENSE αρχείου (MIT).

### Changed
- Αναβάθμιση `versionCode` σε 9.
- Βελτίωση parsing για ChordPro export.

---

## [0.0.8] - 2025-12-05
### Added
- Flip animations στο UI.
- Φόρτωση εικόνων ψηφίων από assets για split-flap look.

---

## [0.0.7] - 2025-11-28
### Fixed
- Διορθώθηκε bug στο clipboard automation.
- Σταθεροποιήθηκε το Gradle build.

---

