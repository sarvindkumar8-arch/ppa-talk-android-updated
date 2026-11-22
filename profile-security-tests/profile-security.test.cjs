const fs = require("node:fs");
const path = require("node:path");
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails
} = require("@firebase/rules-unit-testing");
const { doc, getDoc, setDoc } = require("firebase/firestore");

const PROJECT_ID = "ppa-talk-profile-security-test";
const EMULATOR_HOST = process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085";
const RULES_PATH = path.resolve(__dirname, "../firestore.rules.example");

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

    await setDoc(doc(db, "profilePresence/private-user"), {
      uid: "private-user",
      isOnline: true,
      lastSeen: new Date(),
      updatedAt: new Date()
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
    ["Non-friend cannot read profile presence", async () => {
      const db = testEnv.authenticatedContext("stranger-user").firestore();
      await assertFails(getDoc(doc(db, "profilePresence/private-user")));
    }],
    ["Accepted friend can read profile presence", async () => {
      const db = testEnv.authenticatedContext("friend-user").firestore();
      await assertSucceeds(getDoc(doc(db, "profilePresence/private-user")));
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
