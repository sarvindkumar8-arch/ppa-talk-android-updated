# PPA Talk — Email Login / Public Profile Android Starter

Package: `com.ppatalk.app`

## Included in this phase
- Email + password sign-up and login using Firebase Authentication
- Public user profile document creation in Cloud Firestore
- Public community feed shell and bottom navigation
- Sign-out action
- Example Firestore rules

## Firebase setup required before running
1. Create a Firebase project in the Firebase Console.
2. Add an Android app with package name `com.ppatalk.app`.
3. Download `google-services.json` and place it in the `app/` directory.
4. In Firebase Authentication, enable the Email/Password sign-in provider.
5. Create a Cloud Firestore database.
6. Review and deploy `firestore.rules.example` as Firestore rules.
7. Open this folder in Android Studio, sync Gradle, and run on an emulator/device.

## Important
- This is a development starter, not a production-ready social network or signed APK.
- Firebase credentials are intentionally not included.
- The feed, post creation, search, likes, comments, follows, chat, media upload,
  notifications and admin dashboard are not implemented yet.
- Public means profile documents are readable by anyone under the sample rules.
  Do not store private data in public profile documents.
- Before public launch, add email verification, password reset, rate limiting,
  moderation/reporting, robust security rules, privacy policy and testing.
