const fs = require("node:fs");
const assert = require("node:assert/strict");
const path = require("node:path");
const { before, after, beforeEach, test } = require("node:test");
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
  deleteDoc,
  collection,
  query,
  where,
  getDocs,
  writeBatch,
} = require("firebase/firestore");

const projectId = "demo-ppa-talk-friend-rules";
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
  return testEnv.authenticatedContext(uid).firestore();
}

async function seedAcceptedFriendship(fromUid = "alice", toUid = "bob") {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    await setDoc(doc(adminDb, "friendRequests", `${fromUid}_${toUid}`), {
      fromUid,
      toUid,
      fromName: "Alice",
      toName: "Bob",
      status: "accepted",
    });
    await setDoc(doc(adminDb, "friends", fromUid, "list", toUid), {
      uid: toUid,
      displayName: "Bob",
      photoUrl: "",
    });
    await setDoc(doc(adminDb, "friends", toUid, "list", fromUid), {
      uid: fromUid,
      displayName: "Alice",
      photoUrl: "",
    });
  });
}

test("sender can create a pending friend request; spoofed sender and self-request are denied", async () => {
  const aliceDb = db("alice");
  await assertSucceeds(setDoc(doc(aliceDb, "friendRequests/alice_bob"), {
    fromUid: "alice",
    toUid: "bob",
    fromName: "Alice",
    toName: "Bob",
    status: "pending",
  }));

  await assertFails(setDoc(doc(aliceDb, "friendRequests/mallory_bob"), {
    fromUid: "mallory",
    toUid: "bob",
    status: "pending",
  }));

  await assertFails(setDoc(doc(aliceDb, "friendRequests/alice_alice"), {
    fromUid: "alice",
    toUid: "alice",
    status: "pending",
  }));
});

test("only request participants can read the request; incoming and outgoing queries are allowed", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "friendRequests/alice_bob"), {
      fromUid: "alice", toUid: "bob", status: "pending",
    });
  });

  await assertSucceeds(getDoc(doc(db("alice"), "friendRequests/alice_bob")));
  await assertSucceeds(getDoc(doc(db("bob"), "friendRequests/alice_bob")));
  await assertFails(getDoc(doc(db("mallory"), "friendRequests/alice_bob")));

  await assertSucceeds(getDocs(query(
    collection(db("bob"), "friendRequests"),
    where("toUid", "==", "bob")
  )));
  await assertSucceeds(getDocs(query(
    collection(db("alice"), "friendRequests"),
    where("fromUid", "==", "alice")
  )));
});

test("recipient can accept request and atomically create both friend-list entries", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "friendRequests/alice_bob"), {
      fromUid: "alice", toUid: "bob", status: "pending",
    });
  });

  const bobDb = db("bob");
  const batch = writeBatch(bobDb);
  batch.update(doc(bobDb, "friendRequests/alice_bob"), { status: "accepted" });
  batch.set(doc(bobDb, "friends/bob/list/alice"), {
    uid: "alice", displayName: "Alice", photoUrl: "",
  });
  batch.set(doc(bobDb, "friends/alice/list/bob"), {
    uid: "bob", displayName: "Bob", photoUrl: "",
  });
  await assertSucceeds(batch.commit());

  await assertSucceeds(getDoc(doc(db("bob"), "friends/bob/list/alice")));
  await assertSucceeds(getDoc(doc(db("alice"), "friends/alice/list/bob")));
  await assertFails(getDoc(doc(db("mallory"), "friends/bob/list/alice")));
});

test("stranger cannot accept a request or create friend-list entries", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "friendRequests/alice_bob"), {
      fromUid: "alice", toUid: "bob", status: "pending",
    });
  });

  const malloryDb = db("mallory");
  const batch = writeBatch(malloryDb);
  batch.update(doc(malloryDb, "friendRequests/alice_bob"), { status: "accepted" });
  batch.set(doc(malloryDb, "friends/mallory/list/alice"), { uid: "alice" });
  await assertFails(batch.commit());
});

test("recipient can reject a pending request; sender cannot accept it", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "friendRequests/alice_bob"), {
      fromUid: "alice", toUid: "bob", status: "pending",
    });
  });

  await assertSucceeds(updateDoc(doc(db("bob"), "friendRequests/alice_bob"), {
    fromUid: "alice",
    toUid: "bob",
    status: "rejected",
  }));
  await assertFails(updateDoc(doc(db("alice"), "friendRequests/alice_bob"), {
    fromUid: "alice",
    toUid: "bob",
    status: "accepted",
  }));
});

test("either friend can unfriend: remove both list entries and mark accepted request rejected", async () => {
  await seedAcceptedFriendship();

  const aliceDb = db("alice");
  const batch = writeBatch(aliceDb);
  batch.delete(doc(aliceDb, "friends/alice/list/bob"));
  batch.delete(doc(aliceDb, "friends/bob/list/alice"));
  batch.update(doc(aliceDb, "friendRequests/alice_bob"), { status: "rejected" });
  await assertSucceeds(batch.commit());

  const aliceFriendEntry = await getDoc(doc(db("alice"), "friends/alice/list/bob"));
  const bobFriendEntry = await getDoc(doc(db("bob"), "friends/bob/list/alice"));
  assert.equal(aliceFriendEntry.exists(), false);
  assert.equal(bobFriendEntry.exists(), false);
  await assertSucceeds(getDoc(doc(db("alice"), "friendRequests/alice_bob")));
});

test("non-participant cannot delete a request or either friend-list entry", async () => {
  await seedAcceptedFriendship();

  await assertFails(deleteDoc(doc(db("mallory"), "friendRequests/alice_bob")));
  await assertFails(deleteDoc(doc(db("mallory"), "friends/alice/list/bob")));
  await assertFails(deleteDoc(doc(db("mallory"), "friends/bob/list/alice")));
});


test("request ID pattern alone does not grant a non-participant read access", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "friendRequests/alice_bob"), {
      fromUid: "alice",
      toUid: "charlie",
      status: "pending",
    });
  });
  await assertSucceeds(getDoc(doc(db("alice"), "friendRequests/alice_bob")));
  await assertSucceeds(getDoc(doc(db("charlie"), "friendRequests/alice_bob")));
  await assertFails(getDoc(doc(db("bob"), "friendRequests/alice_bob")));
});

test("recipient cannot accept a request after it has already been rejected", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "friendRequests/alice_bob"), {
      fromUid: "alice",
      toUid: "bob",
      status: "rejected",
    });
  });
  await assertFails(updateDoc(doc(db("bob"), "friendRequests/alice_bob"), {
    fromUid: "alice",
    toUid: "bob",
    status: "accepted",
  }));
});
