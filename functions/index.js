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


function storagePathFromInput(value) {
  if (typeof value !== "string" || value.length > 2048) {
    throw new HttpsError("invalid-argument", "Invalid media reference.");
  }
  if (/^(profile_photos|cover_photos|post_photos|reels)\\/[A-Za-z0-9_-]+\\/[A-Za-z0-9._-]+$/.test(value)) return value;
  try {
    const url = new URL(value);
    if (url.hostname !== "firebasestorage.googleapis.com") throw new Error("host");
    const match = url.pathname.match(/^\\/v0\\/b\\/[^/]+\\/o\\/(.+)$/);
    if (!match) throw new Error("path");
    const path = decodeURIComponent(match[1]);
    if (!/^(profile_photos|cover_photos|post_photos|reels)\\/[A-Za-z0-9_-]+\\/[A-Za-z0-9._-]+$/.test(path)) throw new Error("path");
    return path;
  } catch (_) {
    throw new HttpsError("invalid-argument", "Unsupported media reference.");
  }
}

exports.getSecureMediaUrl = onCall(
  { region: REGION, enforceAppCheck: false, maxInstances: 20 },
  async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Sign in to view media.");
    const path = storagePathFromInput(request.data?.mediaRef);
    const [kind, ownerUid] = path.split("/");
    const viewerUid = request.auth.uid;

    if (kind === "profile_photos" || kind === "cover_photos") {
      if (ownerUid !== viewerUid) {
        const profile = await db.collection("publicProfiles").doc(ownerUid).get();
        if (profile.exists && profile.get("isPrivate") === true) {
          throw new HttpsError("permission-denied", "This profile is private.");
        }
      }
    } else {
      const matches = await db.collection("posts")
        .where("uid", "==", ownerUid)
        .where("mediaPath", "==", path)
        .limit(1).get();
      if (matches.empty) {
        throw new HttpsError("not-found", "Media is not attached to an available post.");
      }
      if (ownerUid !== viewerUid) {
        const profile = await db.collection("publicProfiles").doc(ownerUid).get();
        const isPrivate = profile.exists && profile.get("isPrivate") === true;
        if (isPrivate) {
          const friendship = await db.doc(`friends/${ownerUid}/list/${viewerUid}`).get();
          if (!friendship.exists) throw new HttpsError("permission-denied", "This post is private.");
        }
      }
    }

    const bucket = require("firebase-admin/storage").getStorage().bucket();
    const file = bucket.file(path);
    const [exists] = await file.exists();
    if (!exists) throw new HttpsError("not-found", "Media file does not exist.");
    const expiresAt = Date.now() + 5 * 60 * 1000;
    const [url] = await file.getSignedUrl({ action: "read", expires: expiresAt });
    return { url, expiresAt };
  }
);
