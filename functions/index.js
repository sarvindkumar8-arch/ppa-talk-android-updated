const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, FieldValue, FieldPath } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const { getStorage } = require("firebase-admin/storage");
const { createHash } = require("node:crypto");
const {
  makePasswordHash,
  verifyPassword,
  finalizeSuccessfulLogin,
  recordFailedLoginAttempt,
  resetPasswordCredentials,
  isAccountLocked
} = require("./password-hash");
const { isStrongPassword, isValidLoginPassword } = require("./password-policy");

initializeApp();
const db = getFirestore();
const REGION = "asia-south1";
const MIN_PASSWORD_LENGTH = 8;
const MAX_FAILED_ATTEMPTS = 5;
const LOCK_MINUTES = 15;

// Server-side abuse throttling. Documents are private to Admin SDK because no
// client rule grants access to serverRateLimits.
async function consumeRateLimit(scope, identity, limit, windowMs) {
  const bucket = Math.floor(Date.now() / windowMs);
  const id = createHash("sha256").update(scope + ":" + identity).digest("hex");
  const ref = db.collection("serverRateLimits").doc(id);
  const now = Date.now();
  await db.runTransaction(async (tx) => {
    const snapshot = await tx.get(ref);
    const previous = snapshot.exists ? snapshot.data() : {};
    const previousStart = Number(previous.windowStart || 0);
    const count = previousStart === bucket ? Number(previous.count || 0) + 1 : 1;
    if (count > limit) {
      throw new HttpsError("resource-exhausted", "Too many requests. Please wait and try again.");
    }
    tx.set(ref, {
      scope,
      windowStart: bucket,
      count,
      updatedAt: FieldValue.serverTimestamp(),
      expiresAt: new Date(now + Math.max(windowMs * 2, 60 * 60 * 1000))
    });
  });
}

function requestNetworkKey(request) {
  const ip = request.rawRequest?.ip || "unknown";
  return createHash("sha256").update(String(ip)).digest("hex");
}

function normalizePhone(value) {
  if (typeof value !== "string") throw new HttpsError("invalid-argument", "Enter a valid mobile number.");
  const phone = value.replace(/[^\d+]/g, "");
  if (!/^\+[1-9]\d{7,14}$/.test(phone)) {
    throw new HttpsError("invalid-argument", "Use a valid international mobile number.");
  }
  return phone;
}

function validatePassword(value) {
  if (!isStrongPassword(value)) {
    throw new HttpsError(
      "invalid-argument",
      "Password must be 8 to 128 characters and include uppercase, lowercase, number and special character."
    );
  }
  return value;
}

function validateLoginPassword(value) {
  if (!isValidLoginPassword(value)) {
    throw new HttpsError("invalid-argument", "Password must be 8 to 128 characters long.");
  }
  return value;
}

function credentialId(phone) {
  return createHash("sha256").update(phone).digest("hex");
}



exports.registerWithPhonePassword = onCall({ region: REGION, enforceAppCheck: true, maxInstances: 10 }, async (request) => {
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
      hashVersion: hash.hashVersion,
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

  // Keep Firebase Auth identity in sync with the verified signup name.
  await getAuth().updateUser(request.auth.uid, { displayName });
  const token = await getAuth().createCustomToken(request.auth.uid);
  return { token };
});

// Reset a phone password only after Firebase Phone Auth has verified the same number.
exports.changePhonePassword = onCall({ region: REGION, enforceAppCheck: true, maxInstances: 10 }, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in before changing your password.");
  const phone = normalizePhone(request.data?.phoneNumber);
  const currentPassword = validateLoginPassword(request.data?.currentPassword);
  const newPassword = validatePassword(request.data?.newPassword);
  if (currentPassword === newPassword) {
    throw new HttpsError("invalid-argument", "Choose a password different from your current password.");
  }
  await consumeRateLimit("phone-password-change", uid + ":" + credentialId(phone), 5, 15 * 60 * 1000);

  const verifiedUser = await getAuth().getUser(uid);
  if (!verifiedUser.phoneNumber || verifiedUser.phoneNumber !== phone) {
    throw new HttpsError("permission-denied", "The signed-in mobile number does not match this account.");
  }

  const docRef = db.collection("phoneAuthCredentials").doc(credentialId(phone));
  const snapshot = await docRef.get();
  if (!snapshot.exists || snapshot.get("uid") !== uid || snapshot.get("phoneNumber") !== phone) {
    throw new HttpsError("not-found", "Phone account was not found.");
  }
  const record = snapshot.data();
  if (isAccountLocked(record)) {
    throw new HttpsError("resource-exhausted", "Too many attempts. Try again after 15 minutes.");
  }
  const verification = await verifyPassword(currentPassword, record);
  if (!verification.matches) {
    await recordFailedLoginAttempt(
      db, docRef, record, MAX_FAILED_ATTEMPTS, LOCK_MINUTES * 60 * 1000
    );
    throw new HttpsError("unauthenticated", "Current password is incorrect.");
  }

  const replacement = await makePasswordHash(newPassword);
  const updated = await resetPasswordCredentials(
    db, docRef, uid, phone, replacement, FieldValue, record
  );
  if (!updated) {
    throw new HttpsError("aborted", "Password changed during this request. Please try again.");
  }
  return { success: true };
});

exports.resetPhonePassword = onCall({ region: REGION, enforceAppCheck: true, maxInstances: 10 }, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Verify your mobile number with SMS OTP first.");
  const phone = normalizePhone(request.data?.phoneNumber);
  const newPassword = validatePassword(request.data?.newPassword);
  await consumeRateLimit("phone-password-reset", uid + ":" + credentialId(phone), 5, 60 * 60 * 1000);

  const verifiedUser = await getAuth().getUser(uid);
  if (!verifiedUser.phoneNumber || verifiedUser.phoneNumber !== phone) {
    throw new HttpsError("permission-denied", "The verified mobile number does not match this account.");
  }

  const docRef = db.collection("phoneAuthCredentials").doc(credentialId(phone));
  const hash = await makePasswordHash(newPassword);
  const updated = await resetPasswordCredentials(
    db, docRef, uid, phone, hash, FieldValue
  );
  if (!updated) {
    throw new HttpsError("not-found", "Phone account was not found.");
  }

  return { success: true };
});

exports.loginWithPhonePassword = onCall({ region: REGION, enforceAppCheck: true, maxInstances: 10 }, async (request) => {
  let diagnosticStage = "validate-input";
  console.info("Phone login diagnostic started");
  try {
  const phone = normalizePhone(request.data?.phoneNumber);
  const password = validateLoginPassword(request.data?.password);
  diagnosticStage = "rate-limit";
  await consumeRateLimit("phone-login", credentialId(phone) + ":" + requestNetworkKey(request), 20, 15 * 60 * 1000);
  const docRef = db.collection("phoneAuthCredentials").doc(credentialId(phone));
  diagnosticStage = "credential-lookup";
  const snapshot = await docRef.get();
  if (!snapshot.exists) throw new HttpsError("unauthenticated", "Mobile number or password is incorrect.");

  const record = snapshot.data();
  if (isAccountLocked(record)) {
    throw new HttpsError("resource-exhausted", "Too many attempts. Try again after 15 minutes.");
  }

  diagnosticStage = "password-verification";
  const verification = await verifyPassword(password, record);
  if (!verification.matches) {
    await recordFailedLoginAttempt(
      db, docRef, record, MAX_FAILED_ATTEMPTS, LOCK_MINUTES * 60 * 1000
    );
    throw new HttpsError("unauthenticated", "Mobile number or password is incorrect.");
  }

  diagnosticStage = "credential-finalization";
  const upgradedHash = verification.needsUpgrade ? await makePasswordHash(password) : null;
  const loginFinalization = await finalizeSuccessfulLogin(
    db, docRef, record, verification, upgradedHash, FieldValue
  );
  if (!loginFinalization.credentialUnchanged) {
    throw new HttpsError("unauthenticated", "Mobile number or password is incorrect.");
  }
  if (loginFinalization.upgradedLegacyHash) {
    console.info("Legacy phone password hash upgraded to version 2");
  }
  if (typeof record.uid !== "string" || !record.uid.trim()) {
    throw new HttpsError("failed-precondition", "This mobile account is missing its authentication identifier. Please contact support.");
  }

  // Repair legacy phone credentials whose Firebase Auth user was removed,
  // while preserving the UID already stored in the server-only credential.
  diagnosticStage = "firebase-auth-user-lookup-or-repair";
  let authUser;
  try {
    authUser = await getAuth().getUser(record.uid);
  } catch (error) {
    if (error?.code !== "auth/user-not-found") throw error;
    try {
      authUser = await getAuth().createUser({
        uid: record.uid,
        phoneNumber: phone
      });
      console.warn("Recreated missing Firebase Auth user for an existing phone credential", {
        uid: record.uid
      });
    } catch (createError) {
      if (createError?.code === "auth/phone-number-already-exists") {
        throw new HttpsError(
          "failed-precondition",
          "This mobile number is linked to a different authentication account. Support is required to safely recover it."
        );
      }
      throw createError;
    }
  }

  diagnosticStage = "profile-sync";
  const profileRef = db.collection("publicProfiles").doc(record.uid);
  const profileSnapshot = await profileRef.get();
  const savedName = profileSnapshot.get("displayName");
  const authName = authUser.displayName;
  const displayName =
    (typeof savedName === "string" && savedName.trim()
      ? savedName.trim()
      : (typeof authName === "string" && authName.trim()
        ? authName.trim()
        : "PPA Talk user")).slice(0, 80);

  // Preserve the existing profile name and synchronize legacy Auth accounts.
  if (authName !== displayName) {
    await getAuth().updateUser(record.uid, { displayName });
  }
  await profileRef.set({
    uid: record.uid,
    displayName,
    updatedAt: FieldValue.serverTimestamp()
  }, { merge: true });
  diagnosticStage = "custom-token-creation";
  const token = await getAuth().createCustomToken(record.uid);
  console.info("Phone login diagnostic completed", { stage: diagnosticStage });
  return { token };
  } catch (error) {
    console.error("Phone login diagnostic failed", {
      stage: diagnosticStage,
      code: error?.code || "unknown",
      name: error?.name || "Error"
    });
    throw error;
  }
});


// Delete chat messages whose per-message expiry time has passed.
// The client only writes expireAt; Admin SDK performs the actual cleanup.
const { onSchedule } = require("firebase-functions/v2/scheduler");

exports.deleteExpiredChatMessages = onSchedule({
  schedule: "every 15 minutes",
  region: REGION,
  maxInstances: 1,
  timeZone: "UTC"
}, async () => {
  let deleted = 0;
  // Bound each invocation so a large backlog cannot run indefinitely.
  for (let page = 0; page < 10; page++) {
    const expired = await db.collectionGroup("messages")
      .where("expireAt", "<=", new Date())
      .orderBy("expireAt", "asc")
      .limit(400)
      .get();
    if (expired.empty) break;
    const batch = db.batch();
    expired.docs.forEach((doc) => batch.delete(doc.ref));
    await batch.commit();
    deleted += expired.size;
    if (expired.size < 400) break;
  }
  console.info("Expired chat message cleanup completed", { deleted });
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



// Cancel an incoming-call alert on the callee's devices as soon as the call
// is accepted, rejected, ended, or missed. This is data-only so Android does
// not create an uncancellable system notification on its own.
exports.cancelIncomingCallAlert = onDocumentWritten({
  document: "callSessions/{callId}",
  region: REGION,
  maxInstances: 20
}, async (event) => {
  const before = event.data?.before?.data();
  const after = event.data?.after?.data();
  if (!after || !["accepted", "accepting", "rejected", "ended", "missed"].includes(after.status)) return;
  if (before?.status === after.status) return;
  // Clear the call notification on every device belonging to both participants.
  // The caller may have an older notification from a previous app state/device.
  const participantUids = [...new Set([after.calleeUid, after.callerUid]
    .filter((uid) => typeof uid === "string" && uid.length > 0))];
  if (!participantUids.length) return;

  const tokenSnapshots = await Promise.all(participantUids.map((uid) =>
    db.collection("users").doc(uid).collection("fcmTokens").get()
  ));
  const tokenOwners = new Map();
  tokenSnapshots.forEach((snapshot) => {
    snapshot.docs.forEach((doc) => tokenOwners.set(doc.id, doc.ref));
  });
  const tokens = [...tokenOwners.keys()];
  if (!tokens.length) return;

  const result = await getMessaging().sendEachForMulticast({
    tokens,
    data: {
      type: "call_cancel",
      callId: event.params.callId
    },
    android: { priority: "high", ttl: 60 * 1000 }
  });

  const stale = [];
  result.responses.forEach((response, index) => {
    if (!response.success &&
        ["messaging/registration-token-not-registered", "messaging/invalid-registration-token"].includes(response.error?.code)) {
      const tokenRef = tokenOwners.get(tokens[index]);
      if (tokenRef) stale.push(tokenRef.delete());
    }
  });
  await Promise.all(stale);
  console.info("Incoming call cancellation push result", {
    callId: event.params.callId,
    status: after.status,
    successCount: result.successCount,
    failureCount: result.failureCount
  });
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


// Notify the other participant whenever a new chat message is created.
exports.notifyNewChatMessage = onDocumentCreated({
  document: "chats/{chatId}/messages/{messageId}",
  region: REGION,
  maxInstances: 30
}, async (event) => {
  const message = event.data?.data();
  if (!message || typeof message.uid !== "string" ||
      typeof message.text !== "string" || !message.text.trim()) return;

  const chatRef = db.collection("chats").doc(event.params.chatId);
  const chatSnapshot = await chatRef.get();
  if (!chatSnapshot.exists) return;
  const participants = chatSnapshot.get("participantIds");
  if (!Array.isArray(participants) || participants.length !== 2 ||
      !participants.includes(message.uid)) return;

  const recipientUid = participants.find((uid) => uid !== message.uid);
  if (typeof recipientUid !== "string") return;

  // Count only messages that were not already read in an open conversation.
  const messageCreatedAt = message.createdAt?.toDate ? message.createdAt.toDate() : null;
  let shouldNotify = true;
  await db.runTransaction(async (transaction) => {
    const latestChat = await transaction.get(chatRef);
    if (!latestChat.exists) {
      shouldNotify = false;
      return;
    }
    const lastReadAt = latestChat.get("lastReadAt_" + recipientUid);
    if (messageCreatedAt && lastReadAt?.toDate &&
        lastReadAt.toDate().getTime() >= messageCreatedAt.getTime()) {
      shouldNotify = false;
      return;
    }
    transaction.set(chatRef, {
      ["unreadCount_" + recipientUid]: FieldValue.increment(1)
    }, { merge: true });
  });
  if (!shouldNotify) return;

  const tokensSnapshot = await db.collection("users").doc(recipientUid)
    .collection("fcmTokens").get();
  const tokens = tokensSnapshot.docs.map((doc) => doc.id).filter(Boolean);
  if (!tokens.length) {
    console.info("No registered FCM tokens for chat recipient", recipientUid);
    return;
  }

  const senderProfile = await db.collection("publicProfiles").doc(message.uid).get();
  const senderName = senderProfile.get("displayName") || "PPA Talk user";
  const preview = message.text.trim().replace(/\s+/g, " ").slice(0, 140);
  const result = await getMessaging().sendEachForMulticast({
    tokens,
    // Data-only: PpaMessagingService renders a consistent Android notification
    // in foreground and background, using the app's chat notification channel.
    data: {
      type: "chat_message",
      title: senderName,
      body: preview,
      chatId: event.params.chatId,
      senderUid: message.uid
    },
    android: {
      priority: "high",
      ttl: 24 * 60 * 60 * 1000
    }
  });

  const stale = [];
  result.responses.forEach((response, index) => {
    if (!response.success &&
        ["messaging/registration-token-not-registered", "messaging/invalid-registration-token"]
          .includes(response.error?.code)) {
      stale.push(tokensSnapshot.docs.find((doc) => doc.id === tokens[index]).ref.delete());
    }
  });
  await Promise.all(stale);
  console.info("Chat message push result", {
    chatId: event.params.chatId,
    successCount: result.successCount,
    failureCount: result.failureCount
  });
});

// Mint short-lived LiveKit room tokens only for authenticated participants in an active PPA Talk call.
const { defineSecret } = require("firebase-functions/params");
const { AccessToken } = require("livekit-server-sdk");
const LIVEKIT_API_KEY = defineSecret("LIVEKIT_API_KEY");
const LIVEKIT_API_SECRET = defineSecret("LIVEKIT_API_SECRET");
const LIVEKIT_URL = defineSecret("LIVEKIT_URL");

exports.createLiveKitToken = onCall({
  region: REGION,
  enforceAppCheck: true,
  maxInstances: 20,
  secrets: [LIVEKIT_API_KEY, LIVEKIT_API_SECRET, LIVEKIT_URL]
}, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in before joining a call.");
  await consumeRateLimit("livekit-token", uid, 12, 60 * 1000);

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


// Authorize and mint a short-lived URL for media attached to a Firestore post or story.
// This endpoint intentionally accepts a Firestore document ID and verifies the stored
// object path; clients cannot request arbitrary objects from another user's folder.
exports.getSecureMediaUrl = onCall({
  region: REGION,
  enforceAppCheck: true,
  maxInstances: 30
}, async (request) => {
  const viewerUid = request.auth?.uid;
  if (!viewerUid) throw new HttpsError("unauthenticated", "Sign in to view this media.");
  await consumeRateLimit("secure-media-url", viewerUid, 120, 60 * 1000);

  const mediaType = request.data?.mediaType;
  const collection = mediaType === "story" ? "stories" :
    (mediaType === "post" || mediaType === "reel") ? "posts" : "";
  const documentId = typeof request.data?.documentId === "string" ? request.data.documentId.trim() : "";
  const objectPath = typeof request.data?.objectPath === "string" ? request.data.objectPath.trim() : "";
  if (!collection || !/^[A-Za-z0-9_-]{1,150}$/.test(documentId) ||
      !/^(post_photos|stories|reels)\/[A-Za-z0-9_-]{1,128}\/[A-Za-z0-9_.-]{1,180}$/.test(objectPath)) {
    throw new HttpsError("invalid-argument", "Invalid media reference.");
  }

  const docSnapshot = await db.collection(collection).doc(documentId).get();
  if (!docSnapshot.exists) throw new HttpsError("not-found", "Media record was not found.");
  const record = docSnapshot.data();
  const ownerUid = record.uid;
  const objectFolder = objectPath.split("/")[0];
  const folderMatchesType = mediaType === "story"
    ? ["stories", "reels"].includes(objectFolder)
    : mediaType === "reel"
      ? objectFolder === "reels" && record.postType === "reel"
      : objectFolder === "post_photos" || (objectFolder === "reels" && record.postType === "reel");
  if (typeof ownerUid !== "string" || objectPath.split("/")[1] !== ownerUid ||
      record.storagePath !== objectPath || !folderMatchesType) {
    throw new HttpsError("permission-denied", "Media reference does not match its owner, type, or record.");
  }

  if (mediaType === "story") {
    const expiresAt = record.expiresAt?.toMillis ? record.expiresAt.toMillis() : 0;
    if (!expiresAt || expiresAt <= Date.now()) {
      throw new HttpsError("not-found", "This story has expired or has no valid expiry.");
    }
  }

  let canView = viewerUid === ownerUid;
  const profileSnapshot = await db.collection("publicProfiles").doc(ownerUid).get();
  const visibility = profileSnapshot.get("profileVisibility") || "private";
  if (collection === "posts") {
    const audience = typeof record.audience === "string" ? record.audience.toLowerCase() : "public";
    if (!["public", "friends", "private"].includes(audience)) {
      throw new HttpsError("permission-denied", "This post has an invalid audience setting.");
    }
    canView = canView || (audience === "public" && visibility === "public");
    if (!canView && audience !== "private") {
      const pairIds = [ownerUid + "_" + viewerUid, viewerUid + "_" + ownerUid];
      const [friendEntry, requestSnapshots] = await Promise.all([
        db.collection("friends").doc(ownerUid).collection("list").doc(viewerUid).get(),
        Promise.all(pairIds.map((id) => db.collection("friendRequests").doc(id).get()))
      ]);
      canView = friendEntry.exists && requestSnapshots.some((snapshot) =>
        snapshot.exists && snapshot.get("status") === "accepted"
      );
    }
  } else {
    canView = canView || visibility === "public";
    if (!canView) {
      const pairIds = [ownerUid + "_" + viewerUid, viewerUid + "_" + ownerUid];
      const [friendEntry, requestSnapshots] = await Promise.all([
        db.collection("friends").doc(ownerUid).collection("list").doc(viewerUid).get(),
        Promise.all(pairIds.map((id) => db.collection("friendRequests").doc(id).get()))
      ]);
      canView = friendEntry.exists && requestSnapshots.some((snapshot) =>
        snapshot.exists && snapshot.get("status") === "accepted"
      );
    }
  }
  if (!canView) throw new HttpsError("permission-denied", "You cannot view this media.");

  const file = getStorage().bucket().file(objectPath);
  const [exists] = await file.exists();
  if (!exists) throw new HttpsError("not-found", "Media file was not found.");
  const [metadata] = await file.getMetadata();
  if (metadata.metadata?.firebaseStorageDownloadTokens) {
    await file.setMetadata({ metadata: { firebaseStorageDownloadTokens: null } });
  }
  const [signedUrl] = await file.getSignedUrl({
    action: "read",
    expires: Date.now() + 5 * 60 * 1000
  });
  return { url: signedUrl, expiresInSeconds: 300 };
});

exports.getSecureProfileMediaUrl = onCall({
  region: REGION,
  enforceAppCheck: true,
  maxInstances: 20
}, async (request) => {
  const viewerUid = request.auth?.uid;
  if (!viewerUid) throw new HttpsError("unauthenticated", "Sign in to view this photo.");
  await consumeRateLimit("secure-profile-media-url", viewerUid, 60, 60 * 1000);

  const profileUid = typeof request.data?.profileUid === "string" ? request.data.profileUid.trim() : "";
  const objectPath = typeof request.data?.objectPath === "string" ? request.data.objectPath.trim() : "";
  if (!/^[A-Za-z0-9_-]{1,128}$/.test(profileUid) ||
      !new RegExp("^(profile_photos|cover_photos)/" + profileUid + "/original_[A-Za-z0-9_.-]+$").test(objectPath)) {
    throw new HttpsError("invalid-argument", "Invalid profile media reference.");
  }

  const profileSnapshot = await db.collection("publicProfiles").doc(profileUid).get();
  if (!profileSnapshot.exists) throw new HttpsError("not-found", "Profile was not found.");
  const expectedPathField = objectPath.startsWith("profile_photos/") ? "photoOriginalPath" : "coverPhotoOriginalPath";
  const storedPath = profileSnapshot.get(expectedPathField);
  if (storedPath !== objectPath) {
    throw new HttpsError("permission-denied", "The requested original photo is not the profile's current stored media.");
  }
  const visibility = profileSnapshot.get("profileVisibility");
  let canView = viewerUid === profileUid || visibility === "public";
  if (!canView) {
    const pairIds = [profileUid + "_" + viewerUid, viewerUid + "_" + profileUid];
    const [friendEntry, requestSnapshots] = await Promise.all([
      db.collection("friends").doc(profileUid).collection("list").doc(viewerUid).get(),
      Promise.all(pairIds.map((id) => db.collection("friendRequests").doc(id).get()))
    ]);
    canView = friendEntry.exists && requestSnapshots.some((snapshot) =>
      snapshot.exists && snapshot.get("status") === "accepted"
    );
  }
  if (!canView) throw new HttpsError("permission-denied", "Only the owner, public viewers, or accepted friends can view this original photo.");

  const file = getStorage().bucket().file(objectPath);
  const [exists] = await file.exists();
  if (!exists) throw new HttpsError("not-found", "Original photo was not found.");
  const [metadata] = await file.getMetadata();
  if (metadata.metadata?.firebaseStorageDownloadTokens) {
    await file.setMetadata({ metadata: { firebaseStorageDownloadTokens: null } });
  }
  const [signedUrl] = await file.getSignedUrl({ action: "read", expires: Date.now() + 5 * 60 * 1000 });
  return { url: signedUrl, expiresInSeconds: 300 };
});

// One-time owner-triggered migration for legacy post, story and reel media.
// Backfills trusted object paths and revokes bearer download tokens.
exports.revokeLegacyContentMediaTokens = onCall({
  region: REGION,
  enforceAppCheck: true,
  maxInstances: 5
}, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in to secure your media.");
  const requestedUid = typeof request.data?.ownerUid === "string" ? request.data.ownerUid.trim() : "";
  if (requestedUid !== uid) throw new HttpsError("permission-denied", "Only the media owner can migrate their media.");
  const markerRef = db.collection("users").doc(uid);
  const marker = await markerRef.get();
  if (marker.get("contentMediaTokensRevokedAt")) return { alreadySecured: true, migratedCount: 0, revokedCount: 0 };
  await consumeRateLimit("content-media-migration", uid, 1, 60 * 60 * 1000);

  const bucket = getStorage().bucket();
  let migratedCount = 0;
  let revokedCount = 0;
  for (const collection of ["posts", "stories"]) {
    let query = db.collection(collection).where("uid", "==", uid).orderBy(FieldPath.documentId()).limit(200);
    while (true) {
      const page = await query.get();
      if (page.empty) break;
      for (const doc of page.docs) {
        const record = doc.data();
        let objectPath = typeof record.storagePath === "string" ? record.storagePath : "";
        if (!objectPath && typeof record.mediaUrl === "string") {
          try {
            const parsed = new URL(record.mediaUrl);
            const objectSegment = parsed.pathname.split("/o/")[1];
            if (objectSegment) objectPath = decodeURIComponent(objectSegment);
          } catch (_) {}
        }
        const isFirebaseStorageUrl = typeof record.mediaUrl === "string" &&
          /https:\/\/firebasestorage\.googleapis\.com\//i.test(record.mediaUrl);
        const validObjectPath = /^(post_photos|stories|reels)\/[A-Za-z0-9_-]{1,128}\/[A-Za-z0-9_.-]{1,180}$/.test(objectPath);
        if (!validObjectPath) {
          // External media URLs are not managed by this Firebase migration.
          // A malformed stored path or legacy Firebase URL must fail closed.
          if (objectPath || isFirebaseStorageUrl) {
            throw new HttpsError("failed-precondition",
              "A legacy Firebase media reference could not be mapped safely. Migration stopped without marking this account secured.");
          }
          continue;
        }
        if (objectPath.split("/")[1] !== uid) {
          throw new HttpsError("failed-precondition",
            "A media object path does not belong to this account. Migration stopped without marking this account secured.");
        }
        const folder = objectPath.split("/")[0];
        const validFolder = collection === "stories"
          ? ["stories", "reels"].includes(folder)
          : ["post_photos", "reels"].includes(folder);
        if (!validFolder) {
          throw new HttpsError("failed-precondition",
            "A media object is stored in an unexpected folder. Migration stopped without marking this account secured.");
        }

        const file = bucket.file(objectPath);
        const [exists] = await file.exists();
        if (!exists) {
          throw new HttpsError("failed-precondition",
            "A referenced media object is missing from Storage. Migration stopped without marking this account secured.");
        }
        const [metadata] = await file.getMetadata();
        if (metadata.metadata?.firebaseStorageDownloadTokens) {
          await file.setMetadata({ metadata: { firebaseStorageDownloadTokens: null } });
          revokedCount += 1;
        }
        if (record.storagePath !== objectPath) {
          await doc.ref.update({ storagePath: objectPath });
          migratedCount += 1;
        }
      }
      if (page.size < 200) break;
      query = db.collection(collection).where("uid", "==", uid).orderBy(FieldPath.documentId()).startAfter(page.docs[page.docs.length - 1]).limit(200);
    }
  }
  await markerRef.set({ contentMediaTokensRevokedAt: FieldValue.serverTimestamp() }, { merge: true });
  console.info("Legacy content media migration completed", { uid, migratedCount, revokedCount });
  return { alreadySecured: false, migratedCount, revokedCount };
});

exports.revokeLegacyProfileMediaTokens = onCall({
  region: REGION,
  enforceAppCheck: true,
  maxInstances: 10
}, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in to secure your profile photos.");
  const profileUid = typeof request.data?.profileUid === "string" ? request.data.profileUid.trim() : "";
  if (profileUid !== uid) throw new HttpsError("permission-denied", "Only the profile owner can secure legacy photo links.");

  const markerRef = db.collection("users").doc(uid);
  const marker = await markerRef.get();
  const legacyMediaAlreadySecured = !!marker.get("profileMediaTokensRevokedAt");
  const directoryPrivacyAlreadySecured = !!marker.get("profileDirectoryPrivacyV2At");
  if (legacyMediaAlreadySecured && directoryPrivacyAlreadySecured) {
    return { alreadySecured: true, directoryPrivacySecured: true };
  }

  const bucket = getStorage().bucket();
  let revokedCount = 0;
  for (const folder of ["profile_photos", "cover_photos"]) {
    const [files] = await bucket.getFiles({ prefix: folder + "/" + uid + "/original_" });
    for (const file of files) {
      const [metadata] = await file.getMetadata();
      if (metadata.metadata?.firebaseStorageDownloadTokens) {
        await file.setMetadata({ metadata: { firebaseStorageDownloadTokens: null } });
        revokedCount += 1;
      }
    }
  }
  // Replace legacy token URLs in the profile document with non-secret object paths.
  const profileRef = db.collection("publicProfiles").doc(uid);
  const profileDoc = await profileRef.get();
  if (profileDoc.exists) {
    const profileUpdates = {};
    for (const [urlField, pathField, folder] of [
      ["photoOriginalUrl", "photoOriginalPath", "profile_photos"],
      ["coverPhotoOriginalUrl", "coverPhotoOriginalPath", "cover_photos"]
    ]) {
      const legacyUrl = profileDoc.get(urlField);
      let objectPath = profileDoc.get(pathField);
      if (!objectPath && typeof legacyUrl === "string") {
        try {
          const parsed = new URL(legacyUrl);
          const objectSegment = parsed.pathname.split("/o/")[1];
          if (objectSegment) objectPath = decodeURIComponent(objectSegment);
        } catch (_) {}
      }
      if (typeof objectPath === "string" && objectPath.startsWith(folder + "/" + uid + "/original_")) {
        profileUpdates[pathField] = objectPath;
        profileUpdates[urlField] = FieldValue.delete();
      }
    }
    if (Object.keys(profileUpdates).length) await profileRef.update(profileUpdates);
  }

  // Keep existing profile-photo feed posts rendering with their safe thumbnail URLs.
  const profilePosts = await db.collection("posts").where("uid", "==", uid).get();
  let migratedPostCount = 0;
  for (const postDoc of profilePosts.docs) {
    const post = postDoc.data();
    if (!["profile", "cover"].includes(post.photoKind) || typeof post.thumbnailUrl !== "string" || !post.thumbnailUrl) continue;
    let originalPath = post.originalPath;
    if (!originalPath && typeof post.mediaUrl === "string") {
      try {
        const parsed = new URL(post.mediaUrl);
        const objectSegment = parsed.pathname.split("/o/")[1];
        if (objectSegment) originalPath = decodeURIComponent(objectSegment);
      } catch (_) {}
    }
    const update = { mediaUrl: post.thumbnailUrl };
    if (typeof originalPath === "string" &&
        (originalPath.startsWith("profile_photos/" + uid + "/original_") ||
         originalPath.startsWith("cover_photos/" + uid + "/original_"))) {
      update.originalPath = originalPath;
    }
    await postDoc.ref.update(update);
    migratedPostCount += 1;
  }

  // Remove bearer photo URLs from the signed-in-readable directory for private
  // profiles. Name and username remain discoverable; media URLs do not.
  const currentProfile = await db.collection("publicProfiles").doc(uid).get();
  const visibility = currentProfile.get("profileVisibility") || "private";
  if (visibility !== "public") {
    await db.collection("profileDirectory").doc(uid).set({
      uid,
      displayName: currentProfile.get("displayName") || "PPA Talk user",
      username: currentProfile.get("username") || "",
      photoUrl: FieldValue.delete(),
      coverPhotoUrl: FieldValue.delete(),
      profileVisibility: visibility,
      updatedAt: FieldValue.serverTimestamp()
    }, { merge: true });
  }

  const markerUpdates = {
    profileDirectoryPrivacyV2At: FieldValue.serverTimestamp()
  };
  if (!legacyMediaAlreadySecured) {
    markerUpdates.profileMediaTokensRevokedAt = FieldValue.serverTimestamp();
  }
  await markerRef.set(markerUpdates, { merge: true });
  return {
    alreadySecured: false,
    directoryPrivacySecured: true,
    revokedCount,
    migratedPostCount
  };
});
