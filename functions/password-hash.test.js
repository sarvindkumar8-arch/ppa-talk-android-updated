const test = require("node:test");
const assert = require("node:assert/strict");
const { scrypt: scryptCallback, randomBytes } = require("node:crypto");
const { promisify } = require("node:util");
const { makePasswordHash, verifyPassword } = require("./password-hash");

const scrypt = promisify(scryptCallback);
const options = { N: 16384, r: 8, p: 1, maxmem: 64 * 1024 * 1024 };

async function makeLegacyRecord(password) {
  const salt = randomBytes(16);
  const hash = await scrypt(password, salt, 64, options);
  return {
    hashVersion: 1,
    salt: salt.toString("base64"),
    passwordHash: Buffer.from(hash).toString("base64")
  };
}

test("new hashes are version 2 hex and verify", async () => {
  const record = await makePasswordHash("PpaTalk-Test-123");
  assert.equal(record.hashVersion, 2);
  assert.match(record.salt, /^[0-9a-f]{32}$/);
  assert.match(record.passwordHash, /^[0-9a-f]{128}$/);
  assert.deepEqual(await verifyPassword("PpaTalk-Test-123", record), {
    matches: true,
    needsUpgrade: false
  });
});

test("wrong password does not verify for a new hash", async () => {
  const record = await makePasswordHash("PpaTalk-Test-123");
  assert.deepEqual(await verifyPassword("WrongPass-123", record), {
    matches: false,
    needsUpgrade: false
  });
});

test("legacy version 1 Base64 hash verifies and requests upgrade", async () => {
  const record = await makeLegacyRecord("PpaTalk-Test-123");
  assert.deepEqual(await verifyPassword("PpaTalk-Test-123", record), {
    matches: true,
    needsUpgrade: true
  });
});

test("wrong password on legacy hash fails but identifies legacy format", async () => {
  const record = await makeLegacyRecord("PpaTalk-Test-123");
  assert.deepEqual(await verifyPassword("WrongPass-123", record), {
    matches: false,
    needsUpgrade: true
  });
});

test("existing versionless hex records remain verifiable", async () => {
  const current = await makePasswordHash("PpaTalk-Test-123");
  const { hashVersion, ...versionless } = current;
  assert.equal(hashVersion, 2);
  assert.deepEqual(await verifyPassword("PpaTalk-Test-123", versionless), {
    matches: true,
    needsUpgrade: false
  });
});

test("malformed and unsupported records fail closed", async () => {
  assert.deepEqual(await verifyPassword("PpaTalk-Test-123", {
    hashVersion: 1, salt: "not-base64!", passwordHash: "bad"
  }), { matches: false, needsUpgrade: false });
  assert.deepEqual(await verifyPassword("PpaTalk-Test-123", {
    hashVersion: 99, salt: "00", passwordHash: "00"
  }), { matches: false, needsUpgrade: false });
});
