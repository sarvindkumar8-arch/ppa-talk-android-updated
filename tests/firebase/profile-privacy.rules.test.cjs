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
  getDoc,
  setDoc,
  updateDoc,
} = require("firebase/firestore");

const projectId = "demo-ppa-talk-profile-rules";
let testEnv;
const rules = fs.readFileSync(
  path.join(__dirname, "../../firestore.rules.example"),
  "utf8"
);

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId,
    firestore: { rules },
  });
});

after(async () => {
  await testEnv.cleanup();
});

beforeEach(async () => {
  await testEnv.clearFirestore();
});

function db(uid) {
  return uid
    ? testEnv.authenticatedContext(uid).firestore()
    : testEnv.unauthenticatedContext().firestore();
}

async function seedProfile(uid, profileVisibility = "private") {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "publicProfiles", uid), {
      uid,
      displayName: uid === "alice" ? "Alice" : "Bob",
      username: uid,
      photoUrl: "https://example.test/avatar.jpg",
      bio: "Private profile bio",
      profileVisibility,
    });
    await setDoc(doc(context.firestore(), "profileDirectory", uid), {
      uid,
      displayName: uid === "alice" ? "Alice" : "Bob",
      username: uid,
      photoUrl: "https://example.test/avatar.jpg",
      profileVisibility,
    });
  });
}

async function seedAcceptedFriendship(ownerUid = "alice", friendUid = "bob") {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    await setDoc(doc(adminDb, "friendRequests", `${ownerUid}_${friendUid}`), {
      fromUid: ownerUid,
      toUid: friendUid,
      status: "accepted",
    });
    await setDoc(doc(adminDb, "friends", ownerUid, "list", friendUid), {
      uid: friendUid,
    });
    await setDoc(doc(adminDb, "friends", friendUid, "list", ownerUid), {
      uid: ownerUid,
    });
  });
}

test("public profile is readable by any signed-in user; unauthenticated read is denied", async () => {
  await seedProfile("alice", "public");
  await assertSucceeds(getDoc(doc(db("bob"), "publicProfiles/alice")));
  await assertFails(getDoc(doc(testEnv.unauthenticatedContext().firestore(), "publicProfiles", "alice")));
});

test("private profile is readable by its owner but denied to a non-friend", async () => {
  await seedProfile("alice", "private");
  await assertSucceeds(getDoc(doc(db("alice"), "publicProfiles/alice")));
  await assertFails(getDoc(doc(db("mallory"), "publicProfiles/alice")));
});

test("accepted friend can read private profile only when friendship records are valid", async () => {
  await seedProfile("alice", "private");
  await seedAcceptedFriendship("alice", "bob");
  await assertSucceeds(getDoc(doc(db("bob"), "publicProfiles/alice")));
  await assertFails(getDoc(doc(db("mallory"), "publicProfiles/alice")));
});

test("friend-list entry without an accepted friend request does not unlock a private profile", async () => {
  await seedProfile("alice", "private");
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "friends/alice/list/bob"), {
      uid: "bob",
    });
  });
  await assertFails(getDoc(doc(db("bob"), "publicProfiles/alice")));
});

test("profile owner can create and update only allowlisted public-profile fields", async () => {
  const aliceDb = db("alice");
  const profile = {
    uid: "alice",
    displayName: "Alice",
    username: "alice",
    photoUrl: "https://example.test/avatar.jpg",
    profileVisibility: "private",
    bio: "Hello",
  };
  await assertSucceeds(setDoc(doc(aliceDb, "publicProfiles/alice"), profile));
  await assertSucceeds(updateDoc(doc(aliceDb, "publicProfiles/alice"), {
    bio: "Updated bio",
    profileVisibility: "public",
  }));
  await assertFails(updateDoc(doc(aliceDb, "publicProfiles/alice"), {
    email: "alice@example.test",
  }));
});

test("another user cannot create, edit, or delete someone else's public profile", async () => {
  await seedProfile("alice", "public");
  const malloryDb = db("mallory");
  await assertFails(updateDoc(doc(malloryDb, "publicProfiles/alice"), {
    displayName: "Hijacked",
  }));
  await assertFails(setDoc(doc(malloryDb, "publicProfiles/mallory"), {
    uid: "alice",
    displayName: "Spoofed",
  }));
  const { deleteDoc } = require("firebase/firestore");
  await assertFails(deleteDoc(doc(malloryDb, "publicProfiles/alice")));
});

test("private profile directory card is readable to signed-in users and rejects extra private fields", async () => {
  await seedProfile("alice", "private");
  const directoryCard = await getDoc(doc(db("mallory"), "profileDirectory/alice"));
  await assertSucceeds(Promise.resolve(directoryCard));
  assert.deepEqual(
    Object.keys(directoryCard.data()).sort(),
    ["displayName", "photoUrl", "profileVisibility", "uid", "username"].sort()
  );

  await assertFails(setDoc(doc(db("alice"), "profileDirectory/alice"), {
    uid: "alice",
    displayName: "Alice",
    username: "alice",
    photoUrl: "https://example.test/avatar.jpg",
    profileVisibility: "private",
    email: "alice@example.test",
    phone: "9999999999",
  }));
});

test("profile directory can be written only by its owner", async () => {
  await assertSucceeds(setDoc(doc(db("alice"), "profileDirectory/alice"), {
    uid: "alice",
    displayName: "Alice",
    username: "alice",
    photoUrl: "https://example.test/avatar.jpg",
    coverPhotoUrl: "",
    profileVisibility: "private",
    isOnline: false,
    lastSeen: null,
    updatedAt: null,
  }));
  await assertFails(updateDoc(doc(db("mallory"), "profileDirectory/alice"), {
    displayName: "Changed by stranger",
  }));
});

test("accepted friend can read private profile without reciprocal friend-list mirror", async () => {
  await seedProfile("alice", "private");
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "friendRequests/alice_bob"), {
      fromUid: "alice",
      toUid: "bob",
      status: "accepted",
    });
  });

  await assertSucceeds(getDoc(doc(db("bob"), "publicProfiles/alice")));
  await assertFails(getDoc(doc(db("mallory"), "publicProfiles/alice")));
});

test("accepted friend can read private profile when accepted request is in reverse direction", async () => {
  await seedProfile("alice", "private");
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "friendRequests/bob_alice"), {
      fromUid: "bob",
      toUid: "alice",
      status: "accepted",
    });
  });

  await assertSucceeds(getDoc(doc(db("bob"), "publicProfiles/alice")));
  await assertFails(getDoc(doc(db("mallory"), "publicProfiles/alice")));
});


test("profile without an explicit public visibility setting defaults to private", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "publicProfiles/alice"), {
      uid: "alice",
      displayName: "Alice",
      username: "alice",
      bio: "Legacy profile without visibility",
    });
  });
  await assertSucceeds(getDoc(doc(db("alice"), "publicProfiles/alice")));
  await assertFails(getDoc(doc(db("bob"), "publicProfiles/alice")));
  await assertFails(getDoc(doc(testEnv.unauthenticatedContext().firestore(), "publicProfiles", "alice")));
});
