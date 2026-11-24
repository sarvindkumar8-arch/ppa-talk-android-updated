function isValidLoginPassword(value) {
  return typeof value === "string" && value.length >= 8 && value.length <= 128;
}

function isStrongPassword(value) {
  return isValidLoginPassword(value) &&
    /[A-Z]/.test(value) &&
    /[a-z]/.test(value) &&
    /[0-9]/.test(value) &&
    /[^A-Za-z0-9\s]/.test(value);
}

module.exports = { isStrongPassword, isValidLoginPassword };
