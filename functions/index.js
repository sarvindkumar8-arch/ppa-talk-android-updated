const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { scrypt: scryptCallback, randomBytes, timingSafeEqual, createHash } = require("node:crypto");
const { promisify } = require("node:util");

initializeApp();
const db = getFirestore();
const scrypt = promisify(scryptCallback);
const REGION = "asia-south1";
const MIN_PASSWORD_LENGTH = 8;
const MAX_FAILED_ATTEMPTS = 5;
const LOCK_MINUTES = 15;

function normalizePhone(value) {
  if (typeof value !== "string") throw new HttpsError("invalid-argument", "Enter a valid mobile number.");
  const phone = value.replace(/[^\d+]/g, "");
  if (!/^\+[1-9]\d{7,14}$/.test(phone)) {
    throw new HttpsError("invalid-argument", "Use a valid international mobile number.");
  }
  return phone;
}

function validatePassword(value) {
  if (typeof value !== "string" || value.length < MIN_PASSWORD_LENGTH || value.length > 128) {
    throw new HttpsError("invalid-argument", "Password must be 8 to 128 characters long.");
  }
  return value;
}

function credentialId(phone) {
  return createHash("sha256").update(phone).digest("hex");
}

async function makePasswordHash(password, saltHex) {
  const salt = saltHex ? Buffer.from(saltHex, "hex") : randomBytes(16);
  const derived = await scrypt(password, salt, 64, { N: 16384, r: 8, p: 1, maxmem: 64 * 1024 * 1024 });
  return { salt: salt.toString("hex"), passwordHash: Buffer.from(derived).toString("hex") };
}

async function verifyPassword(password, saltHex, expectedHex) {
  const { passwordHash } = await makePasswordHash(password, saltHex);
  const actual = Buffer.from(passwordHash, "hex");
  const expected = Buffer.from(expectedHex, "hex");
  return actual.length === expected.length && timingSafeEqual(actual, expected);
}

exports.registerWithPhonePassword = onCall({ region: REGION, enforceAppCheck: false, maxInstances: 10 }, async (request) => {
  const phone = normalizePhone(request.data?.phoneNumber);
  const password = validatePassword(request.data?.password);
  const displayName = typeof request.data?.displayName === "string" ? request.data.displayName.trim().slice(0, 80) : "";
  if (!displayName) throw new HttpsError("invalid-argument", "Enter your full name.");

  const docRef = db.collection("phoneAuthCredentials").doc(credentialId(phone));
  const existing = await docRef.get();
  if (existing.exists) throw new HttpsError("already-exists", "This mobile number is already registered.");

  let userRecord;
  try {
    userRecord = await getAuth().createUser({ phoneNumber: phone, displayName, disabled: false });
  } catch (error) {
    if (error.code === "auth/phone-number-already-exists") {
      throw new HttpsError("already-exists", "This mobile number is already registered.");
    }
    throw new HttpsError("internal", "Could not create your account. Please try again.");
  }

  try {
    const hash = await makePasswordHash(password);
    await db.runTransaction(async (tx) => {
      const current = await tx.get(docRef);
      if (current.exists) throw new HttpsError("already-exists", "This mobile number is already registered.");
      tx.create(docRef, {
        uid: userRecord.uid,
        phoneNumber: phone,
        salt: hash.salt,
        passwordHash: hash.passwordHash,
        failedAttempts: 0,
        createdAt: FieldValue.serverTimestamp()
      });
      tx.set(db.collection("users").doc(userRecord.uid), {
        uid: userRecord.uid,
        phoneNumber: phone,
        createdAt: FieldValue.serverTimestamp()
      });
      tx.set(db.collection("publicProfiles").doc(userRecord.uid), {
        uid: userRecord.uid,
        displayName,
        createdAt: FieldValue.serverTimestamp()
      });
    });
  } catch (error) {
    await getAuth().deleteUser(userRecord.uid).catch(() => {});
    if (error instanceof HttpsError) throw error;
    throw new HttpsError("internal", "Could not finish account setup. Please try again.");
  }

  const token = await getAuth().createCustomToken(userRecord.uid);
  return { token };
});

exports.loginWithPhonePassword = onCall({ region: REGION, enforceAppCheck: false, maxInstances: 10 }, async (request) => {
  const phone = normalizePhone(request.data?.phoneNumber);
  const password = validatePassword(request.data?.password);
  const docRef = db.collection("phoneAuthCredentials").doc(credentialId(phone));
  const snapshot = await docRef.get();
  if (!snapshot.exists) throw new HttpsError("unauthenticated", "Mobile number or password is incorrect.");

  const record = snapshot.data();
  const now = Date.now();
  const lockUntil = record.lockUntil?.toMillis ? record.lockUntil.toMillis() : 0;
  if (lockUntil > now) throw new HttpsError("resource-exhausted", "Too many attempts. Try again after 15 minutes.");

  const matches = await verifyPassword(password, record.salt, record.passwordHash);
  if (!matches) {
    await db.runTransaction(async (tx) => {
      const latest = await tx.get(docRef);
      if (!latest.exists) return;
      const attempts = (latest.get("failedAttempts") || 0) + 1;
      const update = { failedAttempts: attempts };
      if (attempts >= MAX_FAILED_ATTEMPTS) {
        update.failedAttempts = 0;
        update.lockUntil = new Date(Date.now() + LOCK_MINUTES * 60 * 1000);
      }
      tx.update(docRef, update);
    });
    throw new HttpsError("unauthenticated", "Mobile number or password is incorrect.");
  }

  await docRef.update({ failedAttempts: 0, lockUntil: FieldValue.delete(), lastLoginAt: FieldValue.serverTimestamp() });
  const authUser = await getAuth().getUser(record.uid);
  await db.collection("publicProfiles").doc(record.uid).set({
    uid: record.uid,
    displayName: authUser.displayName || "PPA Talk user",
    updatedAt: FieldValue.serverTimestamp()
  }, { merge: true });
  const token = await getAuth().createCustomToken(record.uid);
  return { token };
});
