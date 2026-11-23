const fs = require("node:fs");
const path = require("node:path");
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails
} = require("@firebase/rules-unit-testing");
const {
  doc,
  getDoc,
  setDoc,
  updateDoc,
  deleteDoc
} = require("firebase/firestore");

const PROJECT_ID = "ppa-talk-profile-security-test";
const EMULATOR_HOST = process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085";
const RULES_PATH = path.resolve(__dirname, "firestore.rules.test");

let testEnv;

async function seedData() {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();

    await setDoc(doc(db, "publicProfiles/public-user"), {
      uid: "public-user",
      displayName: "Public User",
      profileVisibility: "public"
    });

    await setDoc(doc(db, "publicProfiles/private-user"), {
      uid: "private-user",
      displayName: "Private User",
      profileVisibility: "private"
    });

    await setDoc(doc(db, "friendRequests/private-user_friend-user"), {
      fromUid: "private-user",
      toUid: "friend-user",
      status: "accepted"
    });

    await setDoc(doc(db, "friends/private-user/list/friend-user"), {
      uid: "friend-user"
    });

    await setDoc(doc(db, "profileDirectory/private-user"), {
      uid: "private-user",
      displayName: "Private User",
      username: "privateuser",
      photoUrl: "https://example.test/avatar.jpg",
      coverPhotoUrl: "https://example.test/cover.jpg",
      profileVisibility: "private",
      updatedAt: new Date()
    });

    await setDoc(doc(db, "profilePresence/private-user"), {
      uid: "private-user",
      isOnline: true,
      lastSeen: new Date(),
      updatedAt: new Date()
    });

    await setDoc(doc(db, "posts/public-post"), {
      uid: "public-user", authorName: "Public User", text: "Public post",
      mediaUrl: "", mediaType: "", postType: "post", audience: "public"
    });
    await setDoc(doc(db, "posts/friends-post"), {
      uid: "private-user", authorName: "Private User", text: "Friends post",
      mediaUrl: "", mediaType: "", postType: "post", audience: "friends"
    });
    await setDoc(doc(db, "posts/private-post"), {
      uid: "private-user", authorName: "Private User", text: "Private post",
      mediaUrl: "", mediaType: "", postType: "post", audience: "private"
    });
    await setDoc(doc(db, "stories/private-story"), {
      uid: "private-user", authorName: "Private User",
      mediaUrl: "https://example.test/story.jpg", mediaType: "image",
      createdAt: new Date(), expiresAt: new Date(Date.now() + 3600000)
    });
    await setDoc(doc(db, "stories/public-story"), {
      uid: "public-user", authorName: "Public User",
      mediaUrl: "https://example.test/story.jpg", mediaType: "image",
      createdAt: new Date(), expiresAt: new Date(Date.now() + 3600000)
    });
  });
}

async function run() {
  const rules = fs.readFileSync(RULES_PATH, "utf8");

  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      host: EMULATOR_HOST.split(":")[0],
      port: Number(EMULATOR_HOST.split(":")[1]),
      rules
    }
  });

  await testEnv.clearFirestore();
  await seedData();

  const tests = [
    ["Anonymous user cannot read a public profile", async () => {
      const db = testEnv.unauthenticatedContext().firestore();
      await assertFails(getDoc(doc(db, "publicProfiles/public-user")));
    }],
    ["Signed-in non-friend can read a public profile", async () => {
      const db = testEnv.authenticatedContext("stranger-user").firestore();
      await assertSucceeds(getDoc(doc(db, "publicProfiles/public-user")));
    }],
    ["Signed-in non-friend cannot read a private profile", async () => {
      const db = testEnv.authenticatedContext("stranger-user").firestore();
      await assertFails(getDoc(doc(db, "publicProfiles/private-user")));
    }],
    ["Accepted friend can read a private profile", async () => {
      const db = testEnv.authenticatedContext("friend-user").firestore();
      await assertSucceeds(getDoc(doc(db, "publicProfiles/private-user")));
    }],
    ["Owner can read their own private profile", async () => {
      const db = testEnv.authenticatedContext("private-user").firestore();
      await assertSucceeds(getDoc(doc(db, "publicProfiles/private-user")));
    }],
    ["Signed-in user can read a public post", async () => {
      const db = testEnv.authenticatedContext("stranger-user").firestore();
      await assertSucceeds(getDoc(doc(db, "posts/public-post")));
    }],
    ["Non-friend cannot read a friends-only post", async () => {
      const db = testEnv.authenticatedContext("stranger-user").firestore();
      await assertFails(getDoc(doc(db, "posts/friends-post")));
    }],
    ["Accepted friend can read a friends-only post", async () => {
      const db = testEnv.authenticatedContext("friend-user").firestore();
      await assertSucceeds(getDoc(doc(db, "posts/friends-post")));
    }],
    ["Non-friend cannot read a private post", async () => {
      const db = testEnv.authenticatedContext("stranger-user").firestore();
      await assertFails(getDoc(doc(db, "posts/private-post")));
    }],
    ["Post owner can read their private post", async () => {
      const db = testEnv.authenticatedContext("private-user").firestore();
      await assertSucceeds(getDoc(doc(db, "posts/private-post")));
    }],
    ["Non-friend cannot read a private-profile story", async () => {
      const db = testEnv.authenticatedContext("stranger-user").firestore();
      await assertFails(getDoc(doc(db, "stories/private-story")));
    }],
    ["Accepted friend can read a private-profile story", async () => {
      const db = testEnv.authenticatedContext("friend-user").firestore();
      await assertSucceeds(getDoc(doc(db, "stories/private-story")));
    }],
    ["Signed-in user can read a public-profile story", async () => {
      const db = testEnv.authenticatedContext("stranger-user").firestore();
      await assertSucceeds(getDoc(doc(db, "stories/public-story")));
    }],
    ["Non-friend cannot read profile presence", async () => {
      const db = testEnv.authenticatedContext("stranger-user").firestore();
      await assertFails(getDoc(doc(db, "profilePresence/private-user")));
    }],
    ["Accepted friend can read profile presence", async () => {
      const db = testEnv.authenticatedContext("friend-user").firestore();
      await assertSucceeds(getDoc(doc(db, "profilePresence/private-user")));
    }],
    ["Non-owner cannot create a profile directory entry", async () => {
      const db = testEnv.authenticatedContext("stranger-user").firestore();
      await assertFails(setDoc(doc(db, "profileDirectory/another-user"), {
        uid: "another-user",
        displayName: "Forged User",
        profileVisibility: "public"
      }));
    }],
    ["Non-owner cannot update another user's profile directory", async () => {
      const db = testEnv.authenticatedContext("stranger-user").firestore();
      await assertFails(updateDoc(doc(db, "profileDirectory/private-user"), {
        displayName: "Hacked Name"
      }));
    }],
    ["Non-owner cannot delete another user's profile directory", async () => {
      const db = testEnv.authenticatedContext("stranger-user").firestore();
      await assertFails(deleteDoc(doc(db, "profileDirectory/private-user")));
    }],
    ["Owner can update allowed profile directory fields", async () => {
      const db = testEnv.authenticatedContext("private-user").firestore();
      await assertSucceeds(updateDoc(doc(db, "profileDirectory/private-user"), {
        displayName: "Updated Private User"
      }));
    }],
    ["Owner cannot add online status to profile directory", async () => {
      const db = testEnv.authenticatedContext("private-user").firestore();
      await assertFails(updateDoc(doc(db, "profileDirectory/private-user"), {
        isOnline: true
      }));
    }],
    ["Owner cannot add private fields to profile directory", async () => {
      const db = testEnv.authenticatedContext("private-user").firestore();
      await assertFails(updateDoc(doc(db, "profileDirectory/private-user"), {
        bio: "This must not be in the directory"
      }));
    }],
    ["Non-owner cannot create another user's profile presence", async () => {
      const db = testEnv.authenticatedContext("stranger-user").firestore();
      await assertFails(setDoc(doc(db, "profilePresence/other-user"), {
        uid: "other-user",
        isOnline: true,
        lastSeen: new Date(),
        updatedAt: new Date()
      }));
    }],
    ["Non-owner cannot update another user's profile presence", async () => {
      const db = testEnv.authenticatedContext("stranger-user").firestore();
      await assertFails(updateDoc(doc(db, "profilePresence/private-user"), {
        isOnline: false
      }));
    }],
    ["Accepted friend cannot update another user's profile presence", async () => {
      const db = testEnv.authenticatedContext("friend-user").firestore();
      await assertFails(updateDoc(doc(db, "profilePresence/private-user"), {
        isOnline: false
      }));
    }],
    ["Owner can update their own profile presence", async () => {
      const db = testEnv.authenticatedContext("private-user").firestore();
      await assertSucceeds(updateDoc(doc(db, "profilePresence/private-user"), {
        isOnline: false,
        lastSeen: new Date()
      }));
    }],
    ["Owner cannot add unapproved fields to profile presence", async () => {
      const db = testEnv.authenticatedContext("private-user").firestore();
      await assertFails(updateDoc(doc(db, "profilePresence/private-user"), {
        privateNote: "not allowed"
      }));
    }],
    ["Owner cannot create profile presence for another UID", async () => {
      const db = testEnv.authenticatedContext("private-user").firestore();
      await assertFails(setDoc(doc(db, "profilePresence/fake-user"), {
        uid: "fake-user",
        isOnline: true,
        lastSeen: new Date(),
        updatedAt: new Date()
      }));
    }]
  ];

  let passed = 0;
  let failed = 0;

  for (const [name, test] of tests) {
    try {
      await test();
      passed += 1;
      process.stdout.write("PASS  " + name + "\n");
    } catch (error) {
      failed += 1;
      process.stdout.write("FAIL  " + name + "\n");
      process.stdout.write("      " + (error && error.message ? error.message : String(error)) + "\n");
    }
  }

  process.stdout.write("\nProfile security results: " + passed + " passed, " + failed + " failed\n");
  await testEnv.cleanup();

  if (failed > 0) process.exitCode = 1;
}

run().catch(async (error) => {
  process.stderr.write("Test setup error: " + (error && error.stack ? error.stack : String(error)) + "\n");
  if (testEnv) await testEnv.cleanup();
  process.exitCode = 1;
});
