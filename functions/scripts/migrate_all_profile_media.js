#!/usr/bin/env node
"use strict";

/**
 * One-time, idempotent migration for every existing PPA Talk profile.
 *
 * Safe by default: performs a dry run. Pass --apply to change Firestore and
 * revoke Firebase Storage download tokens. Uses Application Default Credentials;
 * never place a service-account key in the repository.
 *
 * Run from the functions directory:
 *   node scripts/migrate_all_profile_media.js
 *   node scripts/migrate_all_profile_media.js --apply
 * Optional:
 *   --limit=100
 */

const { initializeApp, applicationDefault, getApps } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getStorage } = require("firebase-admin/storage");

const PROJECT_ID = process.env.GCLOUD_PROJECT || process.env.GOOGLE_CLOUD_PROJECT || "ppa-talk";
const APPLY = process.argv.includes("--apply");
const limitArg = process.argv.find((arg) => arg.startsWith("--limit="));
const LIMIT = limitArg ? Number(limitArg.split("=")[1]) : Infinity;

if (!Number.isInteger(LIMIT) && LIMIT !== Infinity || LIMIT <= 0) {
  throw new Error("--limit must be a positive integer.");
}

if (getApps().length === 0) {
  initializeApp({ credential: applicationDefault(), projectId: PROJECT_ID });
}

const db = getFirestore();
const bucket = getStorage().bucket();

function storagePathFromUrl(value) {
  if (typeof value !== "string" || !value) return null;
  try {
    const parsed = new URL(value);
    const marker = parsed.pathname.indexOf("/o/");
    if (marker < 0) return null;
    return decodeURIComponent(parsed.pathname.slice(marker + 3));
  } catch (_) {
    return null;
  }
}

function validOriginalPath(path, folder, uid) {
  return typeof path === "string" &&
    path.startsWith(folder + "/" + uid + "/original_") &&
    !path.includes("..");
}

async function migrateProfile(uid, profileSnapshot, stats) {
  const profile = profileSnapshot.data() || {};
  const profileUpdates = {};

  for (const [urlField, pathField, folder] of [
    ["photoOriginalUrl", "photoOriginalPath", "profile_photos"],
    ["coverPhotoOriginalUrl", "coverPhotoOriginalPath", "cover_photos"]
  ]) {
    let objectPath = profile[pathField];
    if (!objectPath) objectPath = storagePathFromUrl(profile[urlField]);
    if (validOriginalPath(objectPath, folder, uid)) {
      if (profile[pathField] !== objectPath) profileUpdates[pathField] = objectPath;
      if (profile[urlField]) profileUpdates[urlField] = FieldValue.delete();
    }
  }

  // Revoke legacy download tokens only from the original, private-resolution
  // objects. Display thumbnails are intentionally left unchanged.
  for (const folder of ["profile_photos", "cover_photos"]) {
    const [files] = await bucket.getFiles({ prefix: folder + "/" + uid + "/original_" });
    for (const file of files) {
      const [metadata] = await file.getMetadata();
      if (metadata.metadata?.firebaseStorageDownloadTokens) {
        stats.tokensFound += 1;
        if (APPLY) {
          await file.setMetadata({ metadata: { firebaseStorageDownloadTokens: null } });
          stats.tokensRevoked += 1;
        }
      }
    }
  }

  if (Object.keys(profileUpdates).length) {
    stats.profileDocsToUpdate += 1;
    if (APPLY) await profileSnapshot.ref.update(profileUpdates);
  }

  // Preserve feed rendering: only replace a mediaUrl when a real thumbnailUrl
  // exists. Keep a validated originalPath for secure full-screen viewing.
  const posts = await db.collection("posts").where("uid", "==", uid).get();
  for (const postDoc of posts.docs) {
    const post = postDoc.data();
    if (!["profile", "cover"].includes(post.photoKind) ||
        typeof post.thumbnailUrl !== "string" || !post.thumbnailUrl) continue;

    const folder = post.photoKind === "cover" ? "cover_photos" : "profile_photos";
    let originalPath = post.originalPath;
    if (!originalPath) originalPath = storagePathFromUrl(post.mediaUrl);
    const update = {};
    if (post.mediaUrl !== post.thumbnailUrl) update.mediaUrl = post.thumbnailUrl;
    if (validOriginalPath(originalPath, folder, uid) && post.originalPath !== originalPath) {
      update.originalPath = originalPath;
    }
    if (Object.keys(update).length) {
      stats.postsToUpdate += 1;
      if (APPLY) await postDoc.ref.update(update);
    }
  }

  if (APPLY) {
    await db.collection("users").doc(uid).set({
      profileMediaTokensRevokedAt: FieldValue.serverTimestamp(),
      profileMediaGlobalMigrationVersion: 1
    }, { merge: true });
  }
  stats.profilesProcessed += 1;
}

async function main() {
  console.log("PPA Talk global profile-media migration");
  console.log("Project:", PROJECT_ID);
  console.log("Mode:", APPLY ? "APPLY (writes enabled)" : "DRY RUN (no changes)");
  console.log("Limit:", LIMIT === Infinity ? "all profiles" : LIMIT);

  const stats = {
    profilesProcessed: 0,
    profileDocsToUpdate: 0,
    postsToUpdate: 0,
    tokensFound: 0,
    tokensRevoked: 0,
    errors: 0
  };

  let lastDoc = null;
  while (stats.profilesProcessed < LIMIT) {
    let query = db.collection("publicProfiles").orderBy("__name__").limit(100);
    if (lastDoc) query = query.startAfter(lastDoc);
    const page = await query.get();
    if (page.empty) break;

    for (const profileSnapshot of page.docs) {
      if (stats.profilesProcessed >= LIMIT) break;
      const uid = profileSnapshot.id;
      try {
        await migrateProfile(uid, profileSnapshot, stats);
      } catch (error) {
        stats.errors += 1;
        console.error("Failed profile migration:", uid, error?.message || error);
      }
      if (stats.profilesProcessed > 0 && stats.profilesProcessed % 25 === 0) {
        console.log("Progress:", stats.profilesProcessed, "profiles", JSON.stringify(stats));
      }
    }
    lastDoc = page.docs[page.docs.length - 1];
    if (page.size < 100) break;
  }

  console.log("Migration summary:", JSON.stringify(stats, null, 2));
  if (stats.errors > 0) process.exitCode = 1;
  if (!APPLY) {
    console.log("Dry run only. Review the summary, then rerun with --apply to make changes.");
  }
}

main().catch((error) => {
  console.error("Global profile-media migration failed:", error);
  process.exitCode = 1;
});
