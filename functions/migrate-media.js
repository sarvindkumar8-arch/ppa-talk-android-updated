const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getStorage } = require("firebase-admin/storage");

initializeApp();
const db = getFirestore();
const bucket = getStorage().bucket();
const APPLY = process.argv.includes("--apply");

function toStoragePath(value) {
  if (typeof value !== "string" || !value) return "";
  if (/^(profile_photos|cover_photos|post_photos|reels)\/[A-Za-z0-9_-]+\/[A-Za-z0-9._-]+$/.test(value)) return value;
  try {
    const url = new URL(value);
    if (url.hostname !== "firebasestorage.googleapis.com") return "";
    const match = url.pathname.match(/^\/v0\/b\/[^/]+\/o\/(.+)$/);
    if (!match) return "";
    const path = decodeURIComponent(match[1]);
    return /^(profile_photos|cover_photos|post_photos|reels)\/[A-Za-z0-9_-]+\/[A-Za-z0-9._-]+$/.test(path) ? path : "";
  } catch (_) {
    return "";
  }
}

async function migrateCollection(collectionName, fields) {
  const snapshot = await db.collection(collectionName).get();
  for (const doc of snapshot.docs) {
    const data = doc.data();
    const updates = {};
    for (const [legacyField, pathField] of fields) {
      const path = toStoragePath(data[legacyField]) || toStoragePath(data[pathField]);
      if (!path) continue;
      const file = bucket.file(path);
      const [exists] = await file.exists();
      if (!exists) {
        console.warn(`SKIP missing object: ${path} (${collectionName}/${doc.id})`);
        continue;
      }
      updates[pathField] = path;
      updates[legacyField] = FieldValue.delete();
      if (APPLY) {
        // Clearing the Firebase download token invalidates previously issued token URLs.
        await file.setMetadata({ metadata: { firebaseStorageDownloadTokens: null } });
      }
      console.log(`${APPLY ? "MIGRATE" : "DRY-RUN"} ${collectionName}/${doc.id}: ${legacyField} -> ${pathField}`);
    }
    if (Object.keys(updates).length && APPLY) await doc.ref.update(updates);
  }
}

(async () => {
  console.log(APPLY ? "APPLY mode: documents and token metadata will change." : "DRY RUN: no data will be changed. Re-run with --apply to migrate.");
  await migrateCollection("publicProfiles", [["photoUrl", "photoPath"], ["coverPhotoUrl", "coverPhotoPath"]]);
  await migrateCollection("posts", [["mediaUrl", "mediaPath"]]);
  console.log("Migration scan complete.");
})().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
