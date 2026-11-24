const test = require("node:test");
const assert = require("node:assert/strict");
const { isStrongPassword, isValidLoginPassword } = require("./password-policy");

test("strong password with all required character types is accepted", () => {
  assert.equal(isStrongPassword("PpaTalk@123"), true);
});

test("strong password rejects missing uppercase, lowercase, number or special character", () => {
  assert.equal(isStrongPassword("ppatalk@123"), false);
  assert.equal(isStrongPassword("PPATALK@123"), false);
  assert.equal(isStrongPassword("PpaTalk@abc"), false);
  assert.equal(isStrongPassword("PpaTalk123"), false);
});

test("whitespace alone is not treated as a special character", () => {
  assert.equal(isStrongPassword("PpaTalk 123"), false);
  assert.equal(isStrongPassword("PpaTalk @123"), true);
});

test("strong password enforces 8 to 128 character length", () => {
  assert.equal(isStrongPassword("Aa1@"), false);
  assert.equal(isStrongPassword("A".repeat(124) + "a1@"), true);
  assert.equal(isStrongPassword("A".repeat(125) + "a1@"), true);
  assert.equal(isStrongPassword("A".repeat(126) + "a1@"), false);
});

test("login policy preserves old passwords with only length requirements", () => {
  assert.equal(isValidLoginPassword("oldpass1"), true);
  assert.equal(isValidLoginPassword("PpaTalk@123"), true);
  assert.equal(isValidLoginPassword("short"), false);
  assert.equal(isValidLoginPassword("A".repeat(129)), false);
  assert.equal(isValidLoginPassword(null), false);
});
