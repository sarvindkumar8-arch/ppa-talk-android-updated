const test = require("node:test");
const assert = require("node:assert/strict");
const { randomUUID } = require("node:crypto");
const { initializeApp, deleteApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { makePasswordHash, finalizeSuccessfulLogin } = require("./password-hash");

test("successful legacy login transaction replaces Base64 hash with version 2 hex", async () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST,
    "Run this test through firebase emulators:exec with the Firestore emulator enabled.");

  const app = initializeApp({ projectId: "ppa-talk-password-migration-test" }, "password-migration-" + randomUUID());
  const db = getFirestore(app);
  const ref = db.collection("phoneAuthCredentials").doc(randomUUID());
  const originalRecord = {
    uid: "emulator-test-user",
    hashVersion: 1,
    salt: "AAAAAAAAAAAAAAAAAAAAAA==",
    passwordHash: "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==",
    failedAttempts: 4
  };
  await ref.set(originalRecord);

  try {
    const upgradedHash = await makePasswordHash("PpaTalk-Test-123");
    const upgraded = await finalizeSuccessfulLogin(
      db, ref, originalRecord, { matches: true, needsUpgrade: true }, upgradedHash, FieldValue
    );
    assert.equal(upgraded.credentialUnchanged, true);
    assert.equal(upgraded.upgradedLegacyHash, true);

    const saved = (await ref.get()).data();
    assert.equal(saved.hashVersion, 2);
    assert.match(saved.salt, /^[0-9a-f]{32}$/);
    assert.match(saved.passwordHash, /^[0-9a-f]{128}$/);
    assert.equal(saved.failedAttempts, 0);
    assert.equal(saved.lockUntil, undefined);
    assert.ok(saved.lastLoginAt);
  } finally {
    await ref.delete();
    await deleteApp(app);
  }
});

test("transaction does not overwrite a credential changed after verification", async () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST,
    "Run this test through firebase emulators:exec with the Firestore emulator enabled.");

  const app = initializeApp({ projectId: "ppa-talk-password-migration-test" }, "password-migration-race-" + randomUUID());
  const db = getFirestore(app);
  const ref = db.collection("phoneAuthCredentials").doc(randomUUID());
  const originalRecord = {
    uid: "emulator-test-user",
    hashVersion: 1,
    salt: "AAAAAAAAAAAAAAAAAAAAAA==",
    passwordHash: "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==",
    failedAttempts: 2,
    lockUntil: new Date("2030-01-01T00:00:00.000Z"),
    lastLoginAt: new Date("2025-01-01T00:00:00.000Z")
  };
  const changedRecord = { ...originalRecord, salt: "BBBBBBBBBBBBBBBBBBBBBB==" };
  await ref.set(changedRecord);

  try {
    const upgradedHash = await makePasswordHash("PpaTalk-Test-123");
    const upgraded = await finalizeSuccessfulLogin(
      db, ref, originalRecord, { matches: true, needsUpgrade: true }, upgradedHash, FieldValue
    );
    assert.equal(upgraded.credentialUnchanged, false);
    assert.equal(upgraded.upgradedLegacyHash, false);
    const saved = (await ref.get()).data();
    assert.equal(saved.hashVersion, 1);
    assert.equal(saved.salt, "BBBBBBBBBBBBBBBBBBBBBB==");
    assert.equal(saved.passwordHash, changedRecord.passwordHash);
    assert.equal(saved.failedAttempts, 2);
    assert.equal(saved.lockUntil.toMillis(), changedRecord.lockUntil.getTime());
    assert.equal(saved.lastLoginAt.toMillis(), changedRecord.lastLoginAt.getTime());
  } finally {
    await ref.delete();
    await deleteApp(app);
  }
});
