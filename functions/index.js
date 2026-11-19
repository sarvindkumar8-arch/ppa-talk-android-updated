const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
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
  // Registration is allowed only after Firebase Phone Auth has verified the SMS code.
  if (!request.auth?.uid) {
    throw new HttpsError("unauthenticated", "Verify your mobile number with SMS OTP before creating an account.");
  }
  const phone = normalizePhone(request.data?.phoneNumber);
  const password = validatePassword(request.data?.password);
  const displayName = typeof request.data?.displayName === "string" ? request.data.displayName.trim().slice(0, 80) : "";
  if (!displayName) throw new HttpsError("invalid-argument", "Enter your full name.");

  const verifiedUser = await getAuth().getUser(request.auth.uid);
  if (!verifiedUser.phoneNumber || verifiedUser.phoneNumber !== phone) {
    throw new HttpsError("permission-denied", "The verified mobile number does not match this signup.");
  }

  const docRef = db.collection("phoneAuthCredentials").doc(credentialId(phone));
  const hash = await makePasswordHash(password);
  await db.runTransaction(async (tx) => {
    const current = await tx.get(docRef);
    if (current.exists) throw new HttpsError("already-exists", "This mobile number is already registered.");
    tx.create(docRef, {
      uid: request.auth.uid,
      phoneNumber: phone,
      salt: hash.salt,
      passwordHash: hash.passwordHash,
      failedAttempts: 0,
      createdAt: FieldValue.serverTimestamp()
    });
    tx.set(db.collection("users").doc(request.auth.uid), {
      uid: request.auth.uid,
      phoneNumber: phone,
      createdAt: FieldValue.serverTimestamp()
    }, { merge: true });
    tx.set(db.collection("publicProfiles").doc(request.auth.uid), {
      uid: request.auth.uid,
      displayName,
      createdAt: FieldValue.serverTimestamp()
    }, { merge: true });
  });

  const token = await getAuth().createCustomToken(request.auth.uid);
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


// Send an FCM push whenever a new outgoing call session is created.
// The server reads recipient device tokens; clients never get access to other users' tokens.
const { onDocumentCreated, onDocumentWritten } = require("firebase-functions/v2/firestore");

exports.notifyIncomingCall = onDocumentCreated({
  document: "callSessions/{callId}",
  region: REGION,
  maxInstances: 20
}, async (event) => {
  const call = event.data?.data();
  if (!call || call.status !== "ringing" ||
      typeof call.callerUid !== "string" || typeof call.calleeUid !== "string" ||
      !["audio", "video"].includes(call.callType)) return;

  const tokensSnapshot = await db.collection("users").doc(call.calleeUid)
    .collection("fcmTokens").get();
  const tokens = tokensSnapshot.docs.map((doc) => doc.id).filter(Boolean);
  if (tokens.length === 0) {
    console.info("No registered FCM tokens for callee", call.calleeUid);
    return;
  }

  const callerProfile = await db.collection("publicProfiles").doc(call.callerUid).get();
  const callerName = callerProfile.get("displayName") || "PPA Talk user";
  const kind = call.callType === "video" ? "video" : "voice";
  const result = await getMessaging().sendEachForMulticast({
    tokens,
    // Send a data-only push. If a notification payload is included, Android
    // displays it itself in the background and the app cannot reliably cancel
    // that system-owned ringing notification when the call is accepted.
    data: {
      title: callerName,
      body: "Incoming " + kind + " call",
      callId: event.params.callId,
      callerUid: call.callerUid,
      callType: call.callType
    },
    android: {
      priority: "high",
      ttl: 60 * 1000
    }
  });

  const stale = [];
  result.responses.forEach((response, index) => {
    if (!response.success &&
        ["messaging/registration-token-not-registered", "messaging/invalid-registration-token"].includes(response.error?.code)) {
      stale.push(tokensSnapshot.docs.find((doc) => doc.id === tokens[index]).ref.delete());
    }
  });
  await Promise.all(stale);
  console.info("Incoming call push result", { successCount: result.successCount, failureCount: result.failureCount });
});


// Notify the recipient when a new friend request is created.
exports.notifyFriendRequest = onDocumentWritten({
  document: "friendRequests/{requestId}",
  region: REGION,
  maxInstances: 20
}, async (event) => {
  const before = event.data?.before?.data();
  const request = event.data?.after?.data();
  if (!request || request.status !== "pending" || (before?.status === "pending") ||
      typeof request.fromUid !== "string" || typeof request.toUid !== "string" ||
      request.fromUid === request.toUid) return;

  const tokensSnapshot = await db.collection("users").doc(request.toUid)
    .collection("fcmTokens").get();
  const tokens = tokensSnapshot.docs.map((doc) => doc.id).filter(Boolean);
  if (tokens.length === 0) {
    console.info("No registered FCM tokens for friend-request recipient", request.toUid);
    return;
  }

  const senderProfile = await db.collection("publicProfiles").doc(request.fromUid).get();
  const senderName = senderProfile.get("displayName") || request.fromName || "PPA Talk user";
  const result = await getMessaging().sendEachForMulticast({
    tokens,
    notification: {
      title: "New friend request",
      body: senderName + " sent you a friend request"
    },
    data: {
      type: "friend_request",
      requestId: event.params.requestId,
      fromUid: request.fromUid,
      title: "New friend request",
      body: senderName + " sent you a friend request"
    },
    android: {
      priority: "high",
      notification: {
        channelId: "ppa_general_notifications",
        sound: "default"
      }
    }
  });

  const staleTokens = [];
  result.responses.forEach((response, index) => {
    if (!response.success &&
        ["messaging/registration-token-not-registered", "messaging/invalid-registration-token"]
          .includes(response.error?.code)) {
      staleTokens.push(tokens[index]);
    }
  });
  if (staleTokens.length) {
    const batch = db.batch();
    staleTokens.forEach((token) => batch.delete(
      db.collection("users").doc(request.toUid).collection("fcmTokens").doc(token)
    ));
    await batch.commit();
  }
});


// Mint short-lived LiveKit room tokens only for authenticated participants in an active PPA Talk call.
const { defineSecret } = require("firebase-functions/params");
const { AccessToken } = require("livekit-server-sdk");
const LIVEKIT_API_KEY = defineSecret("LIVEKIT_API_KEY");
const LIVEKIT_API_SECRET = defineSecret("LIVEKIT_API_SECRET");
const LIVEKIT_URL = defineSecret("LIVEKIT_URL");

exports.createLiveKitToken = onCall({
  region: REGION,
  enforceAppCheck: false,
  maxInstances: 20,
  secrets: [LIVEKIT_API_KEY, LIVEKIT_API_SECRET, LIVEKIT_URL]
}, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in before joining a call.");

  const callId = typeof request.data?.callId === "string" ? request.data.callId.trim() : "";
  if (!/^[A-Za-z0-9_-]{10,128}$/.test(callId)) {
    throw new HttpsError("invalid-argument", "A valid call ID is required.");
  }

  const callRef = db.collection("callSessions").doc(callId);
  const callSnapshot = await callRef.get();
  if (!callSnapshot.exists) throw new HttpsError("not-found", "Call session was not found.");

  const call = callSnapshot.data();
  const isParticipant = call.callerUid === uid || call.calleeUid === uid;
  if (!isParticipant) throw new HttpsError("permission-denied", "You are not a participant in this call.");
  if (!["accepting", "accepted"].includes(call.status)) {
    throw new HttpsError("failed-precondition", "This call is not active.");
  }
  if (!["audio", "video"].includes(call.callType)) {
    throw new HttpsError("failed-precondition", "This call type is not supported.");
  }

  const apiKey = LIVEKIT_API_KEY.value();
  const apiSecret = LIVEKIT_API_SECRET.value();
  const serverUrl = LIVEKIT_URL.value();
  const urlHost = (() => {
    try { return new URL(serverUrl).host; } catch (_) { return "invalid-url"; }
  })();
  if (!apiKey || !apiSecret || !serverUrl || !/^wss:\/\//.test(serverUrl)) {
    console.error("LiveKit token config invalid", {
      hasApiKey: Boolean(apiKey),
      hasApiSecret: Boolean(apiSecret),
      hasServerUrl: Boolean(serverUrl),
      urlProtocolValid: /^wss:\/\//.test(serverUrl || "")
    });
    throw new HttpsError("failed-precondition", "LiveKit server configuration is incomplete.");
  }

  const now = Math.floor(Date.now() / 1000);
  const token = new AccessToken(apiKey, apiSecret, {
    identity: uid,
    ttl: "10m"
  });
  token.addGrant({
    roomJoin: true,
    room: callId,
    canPublish: true,
    canSubscribe: true,
    canPublishData: true
  });

  const jwt = await token.toJwt();
  // Decode only the JWT payload for diagnostics; never log the token or API secret.
  let claims;
  try {
    const payloadPart = jwt.split(".")[1];
    claims = JSON.parse(Buffer.from(payloadPart, "base64url").toString("utf8"));
  } catch (error) {
    console.error("LiveKit JWT payload decode failed", { errorName: error?.name || "Error" });
    throw new HttpsError("internal", "Could not validate the generated LiveKit token.");
  }

  const diagnostic = {
    urlHost,
    identityMatches: claims.sub === uid,
    issuerMatchesApiKey: claims.iss === apiKey,
    hasRoomJoinGrant: claims.video?.roomJoin === true,
    roomMatchesCall: claims.video?.room === callId,
    canPublish: claims.video?.canPublish === true,
    canSubscribe: claims.video?.canSubscribe === true,
    issuedAtPresent: Number.isFinite(claims.iat),
    notBeforePresent: claims.nbf == null || Number.isFinite(claims.nbf),
    expiresAtPresent: Number.isFinite(claims.exp),
    expiresInSeconds: Number.isFinite(claims.exp) ? claims.exp - now : null
  };
  console.info("LiveKit token diagnostics", diagnostic);

  if (!diagnostic.identityMatches || !diagnostic.issuerMatchesApiKey ||
      !diagnostic.hasRoomJoinGrant || !diagnostic.roomMatchesCall ||
      !diagnostic.canPublish || !diagnostic.canSubscribe ||
      !diagnostic.issuedAtPresent || !diagnostic.expiresAtPresent ||
      diagnostic.expiresInSeconds <= 0) {
    console.error("LiveKit generated token failed local validation", diagnostic);
    throw new HttpsError("internal", "Generated LiveKit token did not pass validation.");
  }

  return {
    serverUrl,
    token: jwt,
    roomName: callId,
    identity: uid
  };
});
