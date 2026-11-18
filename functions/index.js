const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const crypto = require("node:crypto");
const { promisify } = require("node:util");

initializeApp();

const REGION = "asia-south1";
const scrypt = promisify(crypto.scrypt);
const db = getFirestore();
const credentials = db.collection("phoneAuthCredentials");

function normalizePhone(value) {
  if (typeof value !== "string" || !/^\+[1-9]\d{7,14}$/.test(value)) {
    throw new HttpsError("invalid-argument", "Enter a valid phone number in international format.");
  }
  return value;
}

function validatePassword(value) {
  if (typeof value !== "string" || value.length < 8 || value.length > 128 ||
      !/[A-Z]/.test(value) || !/[a-z]/.test(value) || !/[0-9]/.test(value) ||
      !/[^A-Za-z0-9]/.test(value)) {
    throw new HttpsError(
      "invalid-argument",
      "Password must be 8–128 characters and include uppercase, lowercase, a number and a special character."
    );
  }
}

function credentialDocId(phone) {
  return crypto.createHash("sha256").update(phone).digest("hex");
}

async function makePasswordRecord(password) {
  const salt = crypto.randomBytes(16);
  const derived = await scrypt(password, salt, 64, { N: 16384, r: 8, p: 1, maxmem: 64 * 1024 * 1024 });
  return {
    salt: salt.toString("base64"),
    passwordHash: derived.toString("base64"),
    hashVersion: 1,
    updatedAt: FieldValue.serverTimestamp()
  };
}

async function verifyPassword(password, record) {
  if (!record || record.hashVersion !== 1 || !record.salt || !record.passwordHash) return false;
  const salt = Buffer.from(record.salt, "base64");
  const expected = Buffer.from(record.passwordHash, "base64");
  const actual = await scrypt(password, salt, expected.length, { N: 16384, r: 8, p: 1, maxmem: 64 * 1024 * 1024 });
  return expected.length === actual.length && crypto.timingSafeEqual(expected, actual);
}

function requireVerifiedPhone(request, expectedPhone) {
  if (!request.auth) throw new HttpsError("unauthenticated", "Verify your phone number first.");
  const claim = request.auth.token.phone_number;
  if (!claim || claim !== expectedPhone) {
    throw new HttpsError("permission-denied", "Verified phone number does not match this request.");
  }
}

exports.registerWithPhonePassword = onCall(
  { region: REGION, enforceAppCheck: false, maxInstances: 5 },
  async (request) => {
    const phone = normalizePhone(request.data?.phoneNumber);
    const password = request.data?.password;
    validatePassword(password);
    requireVerifiedPhone(request, phone);

    const uid = request.auth.uid;
    const user = await getAuth().getUser(uid);
    if (user.phoneNumber !== phone) {
      throw new HttpsError("permission-denied", "Phone number verification failed.");
    }

    const ref = credentials.doc(credentialDocId(phone));
    const existing = await ref.get();
    if (existing.exists) {
      throw new HttpsError("already-exists", "An account already exists for this mobile number.");
    }
    await ref.create({ uid, phoneNumber: phone, ...(await makePasswordRecord(password)) });
    return { success: true };
  }
);

exports.loginWithPhonePassword = onCall(
  { region: REGION, enforceAppCheck: false, maxInstances: 5 },
  async (request) => {
    const phone = normalizePhone(request.data?.phoneNumber);
    const password = request.data?.password;
    if (typeof password !== "string" || password.length < 1 || password.length > 128) {
      throw new HttpsError("invalid-argument", "Enter your password.");
    }

    const snapshot = await credentials.doc(credentialDocId(phone)).get();
    if (!snapshot.exists || !(await verifyPassword(password, snapshot.data()))) {
      throw new HttpsError("unauthenticated", "Mobile number or password is incorrect.");
    }

    const record = snapshot.data();
    const user = await getAuth().getUser(record.uid);
    if (user.disabled || user.phoneNumber !== phone) {
      throw new HttpsError("permission-denied", "This account is unavailable. Contact support.");
    }
    const customToken = await getAuth().createCustomToken(record.uid);
    return { token: customToken };
  }
);

exports.resetPhonePassword = onCall(
  { region: REGION, enforceAppCheck: false, maxInstances: 5 },
  async (request) => {
    const phone = normalizePhone(request.data?.phoneNumber);
    const password = request.data?.password;
    validatePassword(password);
    requireVerifiedPhone(request, phone);

    const uid = request.auth.uid;
    const user = await getAuth().getUser(uid);
    if (user.phoneNumber !== phone) {
      throw new HttpsError("permission-denied", "Verified phone number does not match this account.");
    }

    const ref = credentials.doc(credentialDocId(phone));
    const snapshot = await ref.get();
    if (!snapshot.exists || snapshot.data().uid !== uid) {
      throw new HttpsError("not-found", "No password account exists for this mobile number.");
    }
    await ref.set({ ...(await makePasswordRecord(password)) }, { merge: true });
    return { success: true };
  }
);
