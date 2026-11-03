# Terminarz rodzinny

Android app (Java 17) for a shared family calendar.

## Features
- E-mail and password registration, login, and password reset (Firebase Authentication).
- Create a family and share its invitation code.
- Join by invitation code; the family owner approves join requests.
- Add, edit, and delete shared events with a family member name, description, date, optional time, and optional reminder.
- Polish weekday names and locally scheduled Android notifications.
- Cloud sync through Cloud Firestore.
- GitHub Actions builds a debug APK and publishes it as an Actions artifact.

## Firebase setup (required before the APK can build)
1. Open the Firebase project `rodzinne-wizyty`.
2. In Project settings, add a new **Android app** with package name `pl.rodzina.terminarz`.
3. Download that app's `google-services.json` and put it at `app/google-services.json` in this repository. Do not reuse the config for the old `pl.rodzina.wizyty` package.
4. In Firebase Authentication, enable **Email/Password** sign-in.
5. Create/enable Cloud Firestore.
6. Publish the rules from `firestore.rules` in Firestore > Rules.

After `google-services.json` is committed, GitHub Actions will build the APK on the next push. Download it from the workflow run's Artifacts section.

## Data model
- `families/{familyCode}`: family metadata and owner UID.
- `families/{familyCode}/members/{uid}`: approved members.
- `families/{familyCode}/requests/{uid}`: pending join requests.
- `families/{familyCode}/data/events`: shared event list.

The invitation code is a randomly generated 24-character hexadecimal value. Treat it as private and share it only with family members.
