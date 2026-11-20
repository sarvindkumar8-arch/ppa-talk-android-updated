const fs = require("node:fs");
const path = require("node:path");
const { before, after, beforeEach, test } = require("node:test");
const assert = require("node:assert/strict");
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require("@firebase/rules-unit-testing");
const {
  doc,
  setDoc,
} = require("firebase/firestore");
const {
  ref: storageRef,
  uploadBytes,
  getBytes,
  deleteObject,
} = require("firebase/storage");

const projectId = "demo-ppa-talk";
let testEnv;
const rules = fs.readFileSync(
  path.join(__dirname, "../../storage.rules.example"),
  "utf8"
);
const imageBytes = new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId,
    storage: { rules },
    firestore: { rules: "rules_version = '2'; service cloud.firestore { match /databases/{database}/documents/{document=**} { allow read, write: if true; } }" },
  });
});

after(async () => {
  await testEnv.cleanup();
});

beforeEach(async () => {
  await testEnv.clearStorage();
  await testEnv.clearFirestore();
});

function storage(uid) {
  return uid
    ? testEnv.authenticatedContext(uid).storage()
    : testEnv.unauthenticatedContext().storage();
}

async function seedProfile(uid, visibility) {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "publicProfiles", uid), {
      uid,
      profileVisibility: visibility,
    });
  });
}

async function seedAcceptedFriendship(ownerUid = "alice", friendUid = "bob") {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, "friendRequests", ownerUid + "_" + friendUid), {
      fromUid: ownerUid,
      toUid: friendUid,
      status: "accepted",
    });
    await setDoc(doc(db, "friends", ownerUid, "list", friendUid), { uid: friendUid });
    await setDoc(doc(db, "friends", friendUid, "list", ownerUid), { uid: ownerUid });
  });
}

async function uploadProfilePhoto(uid = "alice", folder = "profile_photos") {
  return assertSucceeds(uploadBytes(
    storageRef(storage(uid), folder + "/" + uid + "/avatar.png"),
    imageBytes,
    { contentType: "image/png" }
  ));
}

test("owner can upload and read their own profile photo", async () => {
  await seedProfile("alice", "private");
  await uploadProfilePhoto();
  await assertSucceeds(getBytes(storageRef(storage("alice"), "profile_photos/alice/avatar.png"), 1024));
});

test("private profile photo and cover photo are not readable by a stranger", async () => {
  await seedProfile("alice", "private");
  await uploadProfilePhoto("alice", "profile_photos");
  await assertSucceeds(uploadBytes(
    storageRef(storage("alice"), "cover_photos/alice/cover.png"),
    imageBytes,
    { contentType: "image/png" }
  ));
  await assertFails(getBytes(storageRef(storage("mallory"), "profile_photos/alice/avatar.png"), 1024));
  await assertFails(getBytes(storageRef(storage("mallory"), "cover_photos/alice/cover.png"), 1024));
});

test("signed-in users can read public profile photo and cover photo", async () => {
  await seedProfile("alice", "public");
  await uploadProfilePhoto("alice", "profile_photos");
  await assertSucceeds(uploadBytes(
    storageRef(storage("alice"), "cover_photos/alice/cover.png"),
    imageBytes,
    { contentType: "image/png" }
  ));
  await assertSucceeds(getBytes(storageRef(storage("bob"), "profile_photos/alice/avatar.png"), 1024));
  await assertSucceeds(getBytes(storageRef(storage("bob"), "cover_photos/alice/cover.png"), 1024));
});

test("accepted friend can read private profile photo", async () => {
  await seedProfile("alice", "private");
  await seedAcceptedFriendship("alice", "bob");
  await uploadProfilePhoto();
  await assertSucceeds(getBytes(storageRef(storage("bob"), "profile_photos/alice/avatar.png"), 1024));
  await assertFails(getBytes(storageRef(storage("mallory"), "profile_photos/alice/avatar.png"), 1024));
});

test("unauthenticated users cannot read profile media", async () => {
  await seedProfile("alice", "public");
  await uploadProfilePhoto("alice", "profile_photos");
  await assertFails(getBytes(storageRef(storage(null), "profile_photos/alice/avatar.png"), 1024));
});

test("only the owner can upload into their profile folder", async () => {
  await assertSucceeds(uploadBytes(
    storageRef(storage("alice"), "profile_photos/alice/new.png"),
    imageBytes,
    { contentType: "image/png" }
  ));
  await assertFails(uploadBytes(
    storageRef(storage("mallory"), "profile_photos/alice/spoof.png"),
    imageBytes,
    { contentType: "image/png" }
  ));
});

test("profile photo uploads reject non-image content types", async () => {
  await assertFails(uploadBytes(
    storageRef(storage("alice"), "profile_photos/alice/not-image.png"),
    imageBytes,
    { contentType: "text/plain" }
  ));
});

test("owner can delete their profile photo; another user cannot delete it", async () => {
  await uploadProfilePhoto();
  await assertFails(deleteObject(storageRef(storage("mallory"), "profile_photos/alice/avatar.png")));
  await assertSucceeds(deleteObject(storageRef(storage("alice"), "profile_photos/alice/avatar.png")));
});
