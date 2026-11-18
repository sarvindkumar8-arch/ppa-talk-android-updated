const { onCall, HttpsError } = require("firebase-functions/v2/https");

const REGION = "asia-south1";

// Phone-number/password authentication is intentionally disabled.
// Phone sign-in and registration must go through Firebase Authentication SMS OTP
// from the Android client. Keep these legacy callable names temporarily so older
// app versions receive a clear failure instead of creating unverified accounts.
const disabledPhoneAuth = onCall(
  { region: REGION, enforceAppCheck: false, maxInstances: 5 },
  async () => {
    throw new HttpsError(
      "failed-precondition",
      "Phone password authentication is disabled. Please update PPA Talk and use SMS OTP."
    );
  }
);

exports.registerWithPhonePassword = disabledPhoneAuth;
exports.loginWithPhonePassword = disabledPhoneAuth;
