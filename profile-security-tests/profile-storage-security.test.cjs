const fs = require("node:fs");
const path = require("node:path");
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails
} = require("@firebase/rules-unit-testing");
const { ref, uploadBytes, getBytes, deleteObject } = require("firebase/storage");

const PROJECT_ID = "ppa-talk-profile-security-test";
const EMULATOR_HOST = process.env.FIREBASE_STORAGE_EMULATOR_HOST || "127.0.0.1:9199";
const RULES_PATH = path.resolve(__dirname, "storage.rules.test");

let testEnv;

async function run() {
  const rules = fs.readFileSync(RULES_PATH, "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    storage: {
      host: EMULATOR_HOST.split(":")[0],
      port: Number(EMULATOR_HOST.split(":")[1]),
      rules
    }
  });

  const owner = testEnv.authenticatedContext("profile-owner").storage();
  const friend = testEnv.authenticatedContext("accepted-friend").storage();
  const stranger = testEnv.authenticatedContext("stranger-user").storage();
  const anonymous = testEnv.unauthenticatedContext().storage();

  const profileThumb = "profile_photos/profile-owner/avatar.jpg";
  const profileOriginal = "profile_photos/profile-owner/original_avatar.jpg";
  const coverThumb = "cover_photos/profile-owner/cover.jpg";
  const coverOriginal = "cover_photos/profile-owner/original_cover.jpg";
  const tinyImage = new Uint8Array([0xff, 0xd8, 0xff, 0xd9]);
  const imageMetadata = { contentType: "image/jpeg" };

  const tests = [
    ["Owner can upload profile thumbnail", async () => {
      await assertSucceeds(uploadBytes(ref(owner, profileThumb), tinyImage, imageMetadata));
    }],
    ["Owner can upload original profile image for secure callable access", async () => {
      await assertSucceeds(uploadBytes(ref(owner, profileOriginal), tinyImage, imageMetadata));
    }],
    ["Signed-in user can read profile thumbnail", async () => {
      await assertSucceeds(getBytes(ref(stranger, profileThumb)));
    }],
    ["Anonymous user cannot read profile thumbnail", async () => {
      await assertFails(getBytes(ref(anonymous, profileThumb)));
    }],
    ["Non-owner cannot read original profile image directly", async () => {
      await assertFails(getBytes(ref(stranger, profileOriginal)));
    }],
    ["Owner cannot read original profile image directly", async () => {
      await assertFails(getBytes(ref(owner, profileOriginal)));
    }],
    ["Accepted friend cannot read original profile image directly", async () => {
      await assertFails(getBytes(ref(friend, profileOriginal)));
    }],
    ["Owner can upload cover thumbnail", async () => {
      await assertSucceeds(uploadBytes(ref(owner, coverThumb), tinyImage, imageMetadata));
    }],
    ["Owner can upload original cover image for secure callable access", async () => {
      await assertSucceeds(uploadBytes(ref(owner, coverOriginal), tinyImage, imageMetadata));
    }],
    ["Signed-in user can read cover thumbnail", async () => {
      await assertSucceeds(getBytes(ref(stranger, coverThumb)));
    }],
    ["Non-owner cannot read original cover image directly", async () => {
      await assertFails(getBytes(ref(stranger, coverOriginal)));
    }],
    ["Owner cannot read original cover image directly", async () => {
      await assertFails(getBytes(ref(owner, coverOriginal)));
    }],
    ["Non-owner cannot upload into another user's profile folder", async () => {
      await assertFails(uploadBytes(ref(stranger, "profile_photos/profile-owner/forged.jpg"), tinyImage, imageMetadata));
    }],
    ["Non-owner cannot upload into another user's cover folder", async () => {
      await assertFails(uploadBytes(ref(stranger, "cover_photos/profile-owner/forged.jpg"), tinyImage, imageMetadata));
    }],
    ["Owner cannot upload non-image content as profile photo", async () => {
      await assertFails(uploadBytes(ref(owner, "profile_photos/profile-owner/not-image.bin"), tinyImage, { contentType: "application/octet-stream" }));
    }],
    ["Owner cannot upload non-image content as cover photo", async () => {
      await assertFails(uploadBytes(ref(owner, "cover_photos/profile-owner/not-image.bin"), tinyImage, { contentType: "application/octet-stream" }));
    }]
  ];

  let passed = 0;
  let failed = 0;
  const cleanupPaths = [
    [owner, profileThumb], [owner, profileOriginal],
    [owner, coverThumb], [owner, coverOriginal]
  ];

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

  for (const [storage, objectPath] of cleanupPaths) {
    try { await deleteObject(ref(storage, objectPath)); } catch (_) {}
  }

  process.stdout.write("\nProfile Storage security results: " + passed + " passed, " + failed + " failed\n");
  await testEnv.cleanup();
  if (failed > 0) process.exitCode = 1;
}

run().catch(async (error) => {
  process.stderr.write("Storage test setup error: " + (error && error.stack ? error.stack : String(error)) + "\n");
  if (testEnv) await testEnv.cleanup();
  process.exitCode = 1;
});
