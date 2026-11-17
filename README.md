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


## Phone number + password authentication

The Android sign-in screen supports Email or Mobile mode. Email accounts continue to use Firebase Email/Password Authentication. Mobile-number accounts use callable Cloud Functions and Firebase custom tokens; no OTP screen is shown.

### Backend deployment (required for mobile/password)

1. Install Node.js 20 and Firebase CLI: `npm install -g firebase-tools`.
2. In the repository root, sign in and select the same Firebase project used by `app/google-services.json`: `firebase login` then `firebase use YOUR_FIREBASE_PROJECT_ID`.
3. Install backend dependencies: `cd functions && npm install && cd ..`.
4. Deploy the callable authentication backend: `firebase deploy --only functions`.
5. Publish the updated Firestore rules: `firebase deploy --only firestore:rules`.
6. In Firebase Console, enable the Phone provider in Authentication. Phone/password login itself does not send an OTP; the backend creates phone-number Auth users and verifies a salted scrypt password hash before issuing a Firebase custom token.

The backend stores password hashes in the server-only `phoneAuthCredentials` collection. Firestore client rules deny all client access to this collection. Never add password or password-hash fields to public user profile documents.

Before production release, configure Firebase App Check enforcement for callable functions, abuse monitoring, backups, and account recovery for mobile-only accounts. If the backend has not been deployed, mobile sign-up/login will return a backend setup error; email/password login remains available.
