const { scrypt: scryptCallback, randomBytes, timingSafeEqual } = require("node:crypto");
const { promisify } = require("node:util");

const scrypt = promisify(scryptCallback);
const SCRYPT_OPTIONS = { N: 16384, r: 8, p: 1, maxmem: 64 * 1024 * 1024 };
const KEY_LENGTH = 64;

async function makePasswordHash(password) {
  const salt = randomBytes(16);
  const derived = await scrypt(password, salt, KEY_LENGTH, SCRYPT_OPTIONS);
  return {
    hashVersion: 2,
    salt: salt.toString("hex"),
    passwordHash: Buffer.from(derived).toString("hex")
  };
}

function decodeCanonicalBase64(value) {
  if (typeof value !== "string" || value.length === 0) return null;
  try {
    const decoded = Buffer.from(value, "base64");
    return decoded.toString("base64") === value ? decoded : null;
  } catch (_) {
    return null;
  }
}

function decodeHex(value, expectedBytes) {
  if (typeof value !== "string" || !/^(?:[0-9a-fA-F]{2})+$/.test(value)) return null;
  const decoded = Buffer.from(value, "hex");
  return decoded.length === expectedBytes ? decoded : null;
}

async function verifyPassword(password, record) {
  if (!record || typeof record !== "object") return { matches: false, needsUpgrade: false };

  let salt;
  let expected;
  let needsUpgrade = false;

  if (record.hashVersion === 1) {
    salt = decodeCanonicalBase64(record.salt);
    expected = decodeCanonicalBase64(record.passwordHash);
    needsUpgrade = true;
  } else if (record.hashVersion == null || record.hashVersion === 2) {
    salt = decodeHex(record.salt, 16);
    expected = decodeHex(record.passwordHash, KEY_LENGTH);
  } else {
    return { matches: false, needsUpgrade: false };
  }

  if (!salt || salt.length !== 16 || !expected || expected.length !== KEY_LENGTH) {
    return { matches: false, needsUpgrade: false };
  }

  const derived = Buffer.from(await scrypt(password, salt, KEY_LENGTH, SCRYPT_OPTIONS));
  return {
    matches: derived.length === expected.length && timingSafeEqual(derived, expected),
    needsUpgrade
  };
}

async function finalizeSuccessfulLogin(db, docRef, originalRecord, verification, upgradedHash, FieldValue) {
  let upgradedLegacyHash = false;
  await db.runTransaction(async (tx) => {
    const latest = await tx.get(docRef);
    if (!latest.exists) throw new Error("Phone credential disappeared during login.");

    const latestRecord = latest.data();
    const stillSameCredential = latestRecord.salt === originalRecord.salt &&
      latestRecord.passwordHash === originalRecord.passwordHash &&
      latestRecord.hashVersion === originalRecord.hashVersion;
    const update = {
      failedAttempts: 0,
      lockUntil: FieldValue.delete(),
      lastLoginAt: FieldValue.serverTimestamp()
    };
    if (verification.needsUpgrade && stillSameCredential && upgradedHash) {
      update.hashVersion = upgradedHash.hashVersion;
      update.salt = upgradedHash.salt;
      update.passwordHash = upgradedHash.passwordHash;
      upgradedLegacyHash = true;
    }
    tx.update(docRef, update);
  });
  return upgradedLegacyHash;
}

module.exports = { makePasswordHash, verifyPassword, finalizeSuccessfulLogin };
