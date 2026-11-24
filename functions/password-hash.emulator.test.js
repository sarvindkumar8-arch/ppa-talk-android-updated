const test = require("node:test");
const assert = require("node:assert/strict");
const { randomUUID } = require("node:crypto");
const { initializeApp, deleteApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const {
  makePasswordHash,
  finalizeSuccessfulLogin,
  recordFailedLoginAttempt,
  resetPasswordCredentials,
  isAccountLocked
} = require("./password-hash");

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


test("failed login does not increment attempts after credential changes", async () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST);
  const app = initializeApp({ projectId: "ppa-talk-password-migration-test" }, "failed-race-" + randomUUID());
  const db = getFirestore(app);
  const ref = db.collection("phoneAuthCredentials").doc(randomUUID());
  const original = { hashVersion: 1, salt: "AAAAAAAAAAAAAAAAAAAAAA==", passwordHash: "A".repeat(128), failedAttempts: 2 };
  const changed = { ...original, salt: "BBBBBBBBBBBBBBBBBBBBBB==" };
  await ref.set(changed);
  try {
    const result = await recordFailedLoginAttempt(db, ref, original, 5, 900000);
    assert.equal(result, false);
    const saved = (await ref.get()).data();
    assert.equal(saved.failedAttempts, 2);
    assert.equal(saved.lockUntil, undefined);
    assert.equal(saved.salt, changed.salt);
  } finally {
    await ref.delete();
    await deleteApp(app);
  }
});

test("failed login increments attempts when credential is unchanged", async () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST);
  const app = initializeApp({ projectId: "ppa-talk-password-migration-test" }, "failed-count-" + randomUUID());
  const db = getFirestore(app);
  const ref = db.collection("phoneAuthCredentials").doc(randomUUID());
  const original = { hashVersion: 1, salt: "AAAAAAAAAAAAAAAAAAAAAA==", passwordHash: "A".repeat(128), failedAttempts: 2 };
  await ref.set(original);
  try {
    const result = await recordFailedLoginAttempt(db, ref, original, 5, 900000);
    assert.equal(result, true);
    const saved = (await ref.get()).data();
    assert.equal(saved.failedAttempts, 3);
    assert.equal(saved.lockUntil, undefined);
  } finally {
    await ref.delete();
    await deleteApp(app);
  }
});


test("fifth failed attempt locks account for 15 minutes", async () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST);
  const app = initializeApp({ projectId: "ppa-talk-password-migration-test" }, "lock-test-" + randomUUID());
  const db = getFirestore(app);
  const ref = db.collection("phoneAuthCredentials").doc(randomUUID());
  const original = {
    hashVersion: 1,
    salt: "AAAAAAAAAAAAAAAAAAAAAA==",
    passwordHash: "A".repeat(128),
    failedAttempts: 4
  };
  await ref.set(original);
  try {
    const before = Date.now();
    const result = await recordFailedLoginAttempt(db, ref, original, 5, 900000);
    assert.equal(result, true);
    const saved = (await ref.get()).data();
    assert.equal(saved.failedAttempts, 0);
    assert.ok(saved.lockUntil.toMillis() >= before + 900000);
    assert.ok(saved.lockUntil.toMillis() <= Date.now() + 902000);
  } finally {
    await ref.delete();
    await deleteApp(app);
  }
});

test("active and expired account locks are checked by the real helper", async () => {
  assert.equal(isAccountLocked({ lockUntil: new Date(Date.now() + 900000) }), true);
  assert.equal(isAccountLocked({ lockUntil: new Date(Date.now() - 1000) }), false);
  assert.equal(isAccountLocked({}), false);
});

test("verified password reset updates credentials and clears lock state", async () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST);
  const app = initializeApp({ projectId: "ppa-talk-password-migration-test" }, "reset-valid-" + randomUUID());
  const db = getFirestore(app);
  const ref = db.collection("phoneAuthCredentials").doc(randomUUID());
  await ref.set({
    uid: "reset-test-user",
    phoneNumber: "+919876543210",
    hashVersion: 1,
    salt: "AAAAAAAAAAAAAAAAAAAAAA==",
    passwordHash: "A".repeat(88),
    failedAttempts: 4,
    lockUntil: new Date(Date.now() + 900000)
  });

  try {
    const newHash = await makePasswordHash("PpaTalk-New-123");
    const updated = await resetPasswordCredentials(
      db, ref, "reset-test-user", "+919876543210", newHash, FieldValue
    );

    assert.equal(updated, true);
    const saved = (await ref.get()).data();
    assert.equal(saved.hashVersion, 2);
    assert.equal(saved.salt, newHash.salt);
    assert.equal(saved.passwordHash, newHash.passwordHash);
    assert.equal(saved.failedAttempts, 0);
    assert.equal(saved.lockUntil, undefined);
    assert.ok(saved.updatedAt);
  } finally {
    await ref.delete();
    await deleteApp(app);
  }
});

test("password reset rejects a mismatched UID or phone without changing credentials", async () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST);
  const app = initializeApp({ projectId: "ppa-talk-password-migration-test" }, "reset-reject-" + randomUUID());
  const db = getFirestore(app);
  const ref = db.collection("phoneAuthCredentials").doc(randomUUID());
  const original = {
    uid: "correct-user",
    phoneNumber: "+919876543210",
    hashVersion: 2,
    salt: "a".repeat(32),
    passwordHash: "b".repeat(128),
    failedAttempts: 3
  };
  await ref.set(original);

  try {
    const newHash = await makePasswordHash("PpaTalk-New-123");
    const wrongUid = await resetPasswordCredentials(
      db, ref, "wrong-user", original.phoneNumber, newHash, FieldValue
    );
    const wrongPhone = await resetPasswordCredentials(
      db, ref, original.uid, "+919999999999", newHash, FieldValue
    );

    assert.equal(wrongUid, false);
    assert.equal(wrongPhone, false);
    const saved = (await ref.get()).data();
    assert.equal(saved.uid, original.uid);
    assert.equal(saved.phoneNumber, original.phoneNumber);
    assert.equal(saved.salt, original.salt);
    assert.equal(saved.passwordHash, original.passwordHash);
    assert.equal(saved.failedAttempts, original.failedAttempts);
  } finally {
    await ref.delete();
    await deleteApp(app);
  }
});

test("login verified with an old password cannot finalize after a password reset", async () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST);
  const app = initializeApp({ projectId: "ppa-talk-password-migration-test" }, "reset-login-race-" + randomUUID());
  const db = getFirestore(app);
  const ref = db.collection("phoneAuthCredentials").doc(randomUUID());
  const original = {
    uid: "race-test-user",
    phoneNumber: "+919876543210",
    hashVersion: 2,
    salt: "a".repeat(32),
    passwordHash: "b".repeat(128),
    failedAttempts: 2
  };
  await ref.set(original);

  try {
    const newHash = await makePasswordHash("PpaTalk-New-123");
    const reset = await resetPasswordCredentials(
      db, ref, original.uid, original.phoneNumber, newHash, FieldValue
    );
    assert.equal(reset, true);

    const login = await finalizeSuccessfulLogin(
      db, ref, original, { matches: true, needsUpgrade: false }, null, FieldValue
    );
    assert.equal(login.credentialUnchanged, false);

    const saved = (await ref.get()).data();
    assert.equal(saved.passwordHash, newHash.passwordHash);
    assert.equal(saved.salt, newHash.salt);
  } finally {
    await ref.delete();
    await deleteApp(app);
  }
});

test("successful login after expired lock clears lock and attempts", async () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST);
  const app = initializeApp({ projectId: "ppa-talk-password-migration-test" }, "expired-lock-" + randomUUID());
  const db = getFirestore(app);
  const ref = db.collection("phoneAuthCredentials").doc(randomUUID());
  const original = {
    uid: "emulator-test-user",
    hashVersion: 2,
    salt: "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
    passwordHash: "a".repeat(128),
    failedAttempts: 0,
    lockUntil: new Date(Date.now() - 60000)
  };
  await ref.set(original);
  try {
    const result = await finalizeSuccessfulLogin(
      db, ref, original, { matches: true, needsUpgrade: false }, null, FieldValue
    );
    assert.equal(result.credentialUnchanged, true);
    const saved = (await ref.get()).data();
    assert.equal(saved.failedAttempts, 0);
    assert.equal(saved.lockUntil, undefined);
    assert.ok(saved.lastLoginAt);
  } finally {
    await ref.delete();
    await deleteApp(app);
  }
});
