const fs = require("node:fs");
const path = require("node:path");
const { initializeTestEnvironment, assertSucceeds, assertFails } = require("@firebase/rules-unit-testing");
const { doc, setDoc } = require("firebase/firestore");
const { ref, uploadBytes, getBytes, deleteObject } = require("firebase/storage");

const PROJECT_ID = "ppa-talk-profile-security-test";
const STORAGE_HOST = process.env.FIREBASE_STORAGE_EMULATOR_HOST || "127.0.0.1:9199";
const FIRESTORE_HOST = process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085";
const RULES_PATH = path.resolve(__dirname, "storage.rules.test");
let testEnv;

async function seedData() {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, "publicProfiles/profile-owner"), { uid: "profile-owner", profileVisibility: "private" });
    await setDoc(doc(db, "publicProfiles/public-owner"), { uid: "public-owner", profileVisibility: "public" });
    await setDoc(doc(db, "friendRequests/profile-owner_accepted-friend"), {
      fromUid: "profile-owner", toUid: "accepted-friend", status: "accepted"
    });
    await setDoc(doc(db, "friends/profile-owner/list/accepted-friend"), { uid: "accepted-friend" });
  });
}

async function run() {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: { host: FIRESTORE_HOST.split(":")[0], port: Number(FIRESTORE_HOST.split(":")[1]) },
    storage: {
      host: STORAGE_HOST.split(":")[0], port: Number(STORAGE_HOST.split(":")[1]),
      rules: fs.readFileSync(RULES_PATH, "utf8")
    }
  });
  await seedData();

  const owner = testEnv.authenticatedContext("profile-owner").storage();
  const friend = testEnv.authenticatedContext("accepted-friend").storage();
  const stranger = testEnv.authenticatedContext("stranger-user").storage();
  const publicOwner = testEnv.authenticatedContext("public-owner").storage();
  const publicViewer = testEnv.authenticatedContext("public-viewer").storage();
  const anonymous = testEnv.unauthenticatedContext().storage();
  const profileThumb = "profile_photos/profile-owner/avatar.jpg";
  const profileOriginal = "profile_photos/profile-owner/original_avatar.jpg";
  const coverThumb = "cover_photos/profile-owner/cover.jpg";
  const coverOriginal = "cover_photos/profile-owner/original_cover.jpg";
  const publicThumb = "profile_photos/public-owner/avatar.jpg";
  const tinyImage = new Uint8Array([0xff, 0xd8, 0xff, 0xd9]);
  const imageMetadata = { contentType: "image/jpeg" };

  await assertSucceeds(uploadBytes(ref(publicOwner, publicThumb), tinyImage, imageMetadata));
  const tests = [
    ["Owner can upload a post image", async () => assertSucceeds(uploadBytes(ref(owner, "post_photos/profile-owner/post.jpg"), tinyImage, imageMetadata))],
    ["Signed-in stranger cannot directly read a post image", async () => assertFails(getBytes(ref(stranger, "post_photos/profile-owner/post.jpg")))],
    ["Owner can upload a story image", async () => assertSucceeds(uploadBytes(ref(owner, "stories/profile-owner/story.jpg"), tinyImage, imageMetadata))],
    ["Signed-in stranger cannot directly read a story image", async () => assertFails(getBytes(ref(stranger, "stories/profile-owner/story.jpg")))],
    ["Owner can upload a reel video", async () => assertSucceeds(uploadBytes(ref(owner, "reels/profile-owner/reel.mp4"), tinyImage, { contentType: "video/mp4" }))],
    ["Signed-in stranger cannot directly read a reel video", async () => assertFails(getBytes(ref(stranger, "reels/profile-owner/reel.mp4")))],
    ["Non-owner cannot delete a post image", async () => assertFails(deleteObject(ref(stranger, "post_photos/profile-owner/post.jpg")))],
    ["Owner can delete a post image", async () => assertSucceeds(deleteObject(ref(owner, "post_photos/profile-owner/post.jpg")))],
    ["Owner can upload profile thumbnail", async () => assertSucceeds(uploadBytes(ref(owner, profileThumb), tinyImage, imageMetadata))],
    ["Owner can upload original profile image", async () => assertSucceeds(uploadBytes(ref(owner, profileOriginal), tinyImage, imageMetadata))],
    ["Owner can read own private profile thumbnail", async () => assertSucceeds(getBytes(ref(owner, profileThumb)))],
    ["Accepted friend can read private profile thumbnail", async () => assertSucceeds(getBytes(ref(friend, profileThumb)))],
    ["Non-friend cannot read private profile thumbnail", async () => assertFails(getBytes(ref(stranger, profileThumb)))],
    ["Anonymous user cannot read private profile thumbnail", async () => assertFails(getBytes(ref(anonymous, profileThumb)))],
    ["Signed-in user can read public profile thumbnail", async () => assertSucceeds(getBytes(ref(publicViewer, publicThumb)))],
    ["Non-owner cannot read original profile image directly", async () => assertFails(getBytes(ref(stranger, profileOriginal)))],
    ["Owner cannot read original profile image directly", async () => assertFails(getBytes(ref(owner, profileOriginal)))],
    ["Accepted friend cannot read original profile image directly", async () => assertFails(getBytes(ref(friend, profileOriginal)))],
    ["Owner can upload cover thumbnail", async () => assertSucceeds(uploadBytes(ref(owner, coverThumb), tinyImage, imageMetadata))],
    ["Owner can upload original cover image", async () => assertSucceeds(uploadBytes(ref(owner, coverOriginal), tinyImage, imageMetadata))],
    ["Owner can read own private cover thumbnail", async () => assertSucceeds(getBytes(ref(owner, coverThumb)))],
    ["Accepted friend can read private cover thumbnail", async () => assertSucceeds(getBytes(ref(friend, coverThumb)))],
    ["Non-friend cannot read private cover thumbnail", async () => assertFails(getBytes(ref(stranger, coverThumb)))],
    ["Non-owner cannot read original cover image directly", async () => assertFails(getBytes(ref(stranger, coverOriginal)))],
    ["Owner cannot read original cover image directly", async () => assertFails(getBytes(ref(owner, coverOriginal)))],
    ["Non-owner cannot upload into another user's profile folder", async () => assertFails(uploadBytes(ref(stranger, "profile_photos/profile-owner/forged.jpg"), tinyImage, imageMetadata))],
    ["Non-owner cannot upload into another user's cover folder", async () => assertFails(uploadBytes(ref(stranger, "cover_photos/profile-owner/forged.jpg"), tinyImage, imageMetadata))],
    ["Owner cannot upload non-image profile content", async () => assertFails(uploadBytes(ref(owner, "profile_photos/profile-owner/not-image.bin"), tinyImage, { contentType: "application/octet-stream" }))],
    ["Owner cannot upload non-image cover content", async () => assertFails(uploadBytes(ref(owner, "cover_photos/profile-owner/not-image.bin"), tinyImage, { contentType: "application/octet-stream" }))],
    ["Non-owner cannot delete profile thumbnail", async () => assertFails(deleteObject(ref(stranger, profileThumb)))],
    ["Owner can delete profile thumbnail", async () => assertSucceeds(deleteObject(ref(owner, profileThumb)))],
    ["Owner can delete original profile image", async () => assertSucceeds(deleteObject(ref(owner, profileOriginal)))],
    ["Non-owner cannot delete cover thumbnail", async () => assertFails(deleteObject(ref(stranger, coverThumb)))],
    ["Owner can delete cover thumbnail", async () => assertSucceeds(deleteObject(ref(owner, coverThumb)))],
    ["Owner can delete original cover image", async () => assertSucceeds(deleteObject(ref(owner, coverOriginal)))]
  ];

  let passed = 0, failed = 0;
  for (const [name, test] of tests) {
    try { await test(); passed++; process.stdout.write("PASS  " + name + "\n"); }
    catch (error) { failed++; process.stdout.write("FAIL  " + name + "\n      " + (error?.message || String(error)) + "\n"); }
  }
  try { await deleteObject(ref(publicOwner, publicThumb)); } catch (_) {}
  process.stdout.write("\nProfile Storage security results: " + passed + " passed, " + failed + " failed\n");
  await testEnv.cleanup();
  if (failed) process.exitCode = 1;
}
run().catch(async (error) => {
  process.stderr.write("Storage test setup error: " + (error?.stack || String(error)) + "\n");
  if (testEnv) await testEnv.cleanup();
  process.exitCode = 1;
});
