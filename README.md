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


## Phone OTP + password authentication

The mobile-number flow is:
- Sign up: verify the phone with Firebase SMS OTP, then create a password.
- Sign in: mobile number + password; no SMS is sent.
- Forgot password: verify the phone with SMS OTP, then set a new password.
- Email/password and Google sign-in remain available.

The phone/password flow uses Firebase callable Cloud Functions in `asia-south1`. The Functions use Firebase Admin Auth custom tokens for password login and store only salted scrypt password hashes in the server-only `phoneAuthCredentials` collection. The Firestore client rules deny all client access to that collection. Never store passwords or password hashes in app preferences, client-readable Firestore documents, or public profiles.

### Required deployment

1. Confirm Firebase project `ppa-talk` is linked to this repository's `.firebaserc`.
2. Enable Firebase Authentication > Phone provider and allow the countries you intend to support in SMS region policy.
3. Deploy the callable backend: `firebase deploy --only functions`.
4. Publish Firestore rules: `firebase deploy --only firestore:rules`.
5. Build and install the new Android APK from the `security/phone-otp-auth` branch.
6. Test new signup, password login after app restart, wrong-password rejection, forgot-password OTP, and login with the reset password.

The backend must be deployed before phone/password login or password creation can work. The Android code changes and a successful CI build do not themselves deploy Cloud Functions or verify real SMS delivery.

Before public launch, enable and enforce Firebase App Check for callable functions after configuring the Android app, add stronger per-account/IP abuse throttling and monitoring, and review billing alerts. Firebase Phone Auth SMS charges apply to OTP sends during signup and password recovery; ordinary password login does not send an SMS.
