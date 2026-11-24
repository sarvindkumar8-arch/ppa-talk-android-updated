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
  deleteDoc,
  serverTimestamp,
  deleteField
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
    // Story sharing is governed by profile visibility, not the source Reel audience.
    await setDoc(doc(db, "posts/public-owner-private-reel"), {
      uid: "public-user", authorName: "Public User", text: "Private source Reel",
      mediaUrl: "", mediaType: "video", postType: "reel", audience: "private"
    });
    await setDoc(doc(db, "posts/private-owner-public-reel"), {
      uid: "private-user", authorName: "Private User", text: "Public source Reel",
      mediaUrl: "", mediaType: "video", postType: "reel", audience: "public"
    });
    await setDoc(doc(db, "posts/private-post/comments/private-comment"), {
      uid: "private-user", text: "Private comment", parentId: "private-post"
    });
    await setDoc(doc(db, "posts/friends-post/comments/friends-comment"), {
      uid: "private-user", text: "Friends comment", parentId: "friends-post"
    });
    await setDoc(doc(db, "posts/private-post/likes/stranger-user"), {
      uid: "stranger-user", reaction: "❤️"
    });
    await setDoc(doc(db, "stories/private-story"), {
      uid: "private-user", authorName: "Private User",
      mediaUrl: "https://example.test/story.jpg", mediaType: "image",
      storagePath: "stories/private-user/private-story.jpg",
      createdAt: new Date(), expiresAt: new Date(Date.now() + 3600000)
    });
    await setDoc(doc(db, "stories/public-story"), {
      uid: "public-user", authorName: "Public User",
      mediaUrl: "https://example.test/story.jpg", mediaType: "image",
      storagePath: "stories/public-user/public-story.jpg",
      createdAt: new Date(), expiresAt: new Date(Date.now() + 3600000)
    });
    await setDoc(doc(db, "stories/public-story-from-private-reel"), {
      uid: "public-user", authorName: "Public User",
      mediaUrl: "https://example.test/shared-reel-story.mp4", mediaType: "video",
      storagePath: "reels/public-user/shared-reel.mp4",
      createdAt: new Date(), expiresAt: new Date(Date.now() + 3600000)
    });
    await setDoc(doc(db, "stories/private-story-from-public-reel"), {
      uid: "private-user", authorName: "Private User",
      mediaUrl: "https://example.test/private-profile-story.mp4", mediaType: "video",
      storagePath: "reels/private-user/shared-reel.mp4",
      createdAt: new Date(), expiresAt: new Date(Date.now() + 3600000)
    });

    await setDoc(doc(db, "stories/expired-story"), {
      uid: "public-user", authorName: "Public User",
      mediaUrl: "https://example.test/expired.jpg", mediaType: "image",
      storagePath: "stories/public-user/expired.jpg",
      createdAt: new Date(Date.now() - 48 * 3600000), expiresAt: new Date(Date.now() - 3600000)
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
    ["Client cannot read server-side rate-limit records", async () => {
      const db = testEnv.authenticatedContext("stranger-user").firestore();
      await assertFails(getDoc(doc(db, "serverRateLimits/any-key")));
    }],
    ["Client cannot write server-side rate-limit records", async () => {
      const db = testEnv.authenticatedContext("stranger-user").firestore();
      await assertFails(setDoc(doc(db, "serverRateLimits/forged-key"), { count: 999 }));
    }],
    ["Client cannot read phone password credentials", async () => {
      const db = testEnv.authenticatedContext("stranger-user").firestore();
      await assertFails(getDoc(doc(db, "phoneAuthCredentials/credential-hash")));
    }],
    ["Client cannot create phone password credentials", async () => {
      const db = testEnv.authenticatedContext("stranger-user").firestore();
      await assertFails(setDoc(doc(db, "phoneAuthCredentials/forged-hash"), { passwordHash: "nope" }));
    }],
    ["Owner can create a path-only story without a download token URL", async () => {
      const db = testEnv.authenticatedContext("stranger-user").firestore();
      await assertSucceeds(setDoc(doc(db, "stories/path-only-story"), {
        uid: "stranger-user", authorName: "Stranger", mediaUrl: "",
        storagePath: "stories/stranger-user/story.jpg", mediaType: "image",
        createdAt: serverTimestamp(), expiresAt: new Date(Date.now() + 3600000)
      }));
    }],
    ["User cannot create a story using another user's storage path", async () => {
      const db = testEnv.authenticatedContext("stranger-user").firestore();
      await assertFails(setDoc(doc(db, "stories/foreign-path-story"), {
        uid: "stranger-user", authorName: "Stranger", mediaUrl: "",
        storagePath: "stories/private-user/story.jpg", mediaType: "image",
        createdAt: serverTimestamp(), expiresAt: new Date(Date.now() + 3600000)
      }));
    }],
    ["Owner can create a path-only post without a download token URL", async () => {
      const db = testEnv.authenticatedContext("stranger-user").firestore();
      await assertSucceeds(setDoc(doc(db, "posts/path-only-post"), {
        uid: "stranger-user", authorName: "Stranger", text: "", mediaUrl: "",
        storagePath: "post_photos/stranger-user/post.jpg", audience: "public"
      }));
    }],
    ["User cannot create a post using another user's storage path", async () => {
      const db = testEnv.authenticatedContext("stranger-user").firestore();
      await assertFails(setDoc(doc(db, "posts/foreign-path-post"), {
        uid: "stranger-user", authorName: "Stranger", text: "", mediaUrl: "",
        storagePath: "post_photos/private-user/post.jpg", audience: "public"
      }));
    }],
    ["Owner can create a Reel post with a UID containing regex characters", async () => {
      const uid = "phone+91user";
      const db = testEnv.authenticatedContext(uid).firestore();
      await assertSucceeds(setDoc(doc(db, "posts/phone-reel"), {
        uid, authorName: "Phone User", text: "", mediaUrl: "",
        storagePath: "reels/phone+91user/1795524047.mp4",
        mediaType: "video", postType: "reel", audience: "public",
        createdAt: serverTimestamp()
      }));
    }],
    ["User cannot create a Reel post with another user's storage path", async () => {
      const uid = "phone+91user";
      const db = testEnv.authenticatedContext(uid).firestore();
      await assertFails(setDoc(doc(db, "posts/foreign-reel"), {
        uid, authorName: "Phone User", text: "", mediaUrl: "",
        storagePath: "reels/another-user/clip.mp4",
        mediaType: "video", postType: "reel", audience: "public",
        createdAt: serverTimestamp()
      }));
    }],
    ["Expired story cannot be read even from a public profile", async () => {
      const db = testEnv.authenticatedContext("stranger-user").firestore();
      await assertFails(getDoc(doc(db, "stories/expired-story")));
    }],
    ["Non-friend cannot create a direct chat", async () => {
      const db = testEnv.authenticatedContext("stranger-user").firestore();
      await assertFails(setDoc(doc(db, "chats/stranger-chat"), {
        participantIds: ["stranger-user", "private-user"]
      }));
    }],
    ["Non-friend can start a chat with a public profile", async () => {
      const db = testEnv.authenticatedContext("stranger-user").firestore();
      await assertSucceeds(setDoc(doc(db, "chats/public-profile-chat"), {
        participantIds: ["stranger-user", "public-user"]
      }));
    }],
    ["Accepted friends can create a direct chat", async () => {
      const db = testEnv.authenticatedContext("private-user").firestore();
      await assertSucceeds(setDoc(doc(db, "chats/friends-chat"), {
        participantIds: ["private-user", "friend-user"]
      }));
    }],
    ["Non-friend cannot initiate a call", async () => {
      const db = testEnv.authenticatedContext("stranger-user").firestore();
      await assertFails(setDoc(doc(db, "callSessions/stranger-call"), {
        callerUid: "stranger-user", calleeUid: "private-user",
        status: "ringing", callType: "audio"
      }));
    }],
    ["Accepted friend can initiate a call", async () => {
      const db = testEnv.authenticatedContext("private-user").firestore();
      await assertSucceeds(setDoc(doc(db, "callSessions/friends-call"), {
        callerUid: "private-user", calleeUid: "friend-user",
        status: "ringing", callType: "audio"
      }));
    }],
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
    ["Post owner cannot transfer ownership by editing UID", async () => {
      const db = testEnv.authenticatedContext("public-user").firestore();
      await assertFails(updateDoc(doc(db, "posts/public-post"), { uid: "stranger-user" }));
    }],
    ["Post owner cannot change audience to an unsupported value", async () => {
      const db = testEnv.authenticatedContext("public-user").firestore();
      await assertFails(updateDoc(doc(db, "posts/public-post"), { audience: "everyone" }));
    }],
    ["Non-friend cannot read comments on a private post", async () => {
      const db = testEnv.authenticatedContext("stranger-user").firestore();
      await assertFails(getDoc(doc(db, "posts/private-post/comments/private-comment")));
    }],
    ["Private post owner can read their comments", async () => {
      const db = testEnv.authenticatedContext("private-user").firestore();
      await assertSucceeds(getDoc(doc(db, "posts/private-post/comments/private-comment")));
    }],
    ["Accepted friend can read comments on a friends-only post", async () => {
      const db = testEnv.authenticatedContext("friend-user").firestore();
      await assertSucceeds(getDoc(doc(db, "posts/friends-post/comments/friends-comment")));
    }],
    ["Non-friend cannot delete a reaction under a private post", async () => {
      const db = testEnv.authenticatedContext("stranger-user").firestore();
      await assertFails(deleteDoc(doc(db, "posts/private-post/likes/stranger-user")));
    }],
    ["Comment cannot claim a different parent post", async () => {
      const db = testEnv.authenticatedContext("stranger-user").firestore();
      await assertFails(setDoc(doc(db, "posts/public-post/comments/forged-comment"), {
        uid: "stranger-user", text: "forged parent", parentId: "another-post"
      }));
    }],
    ["User cannot create a post for another UID", async () => {
      const db = testEnv.authenticatedContext("stranger-user").firestore();
      await assertFails(setDoc(doc(db, "posts/forged-post"), {
        uid: "private-user", authorName: "Forged", text: "forged", audience: "public"
      }));
    }],
    ["User cannot create a post with an unsupported audience", async () => {
      const db = testEnv.authenticatedContext("stranger-user").firestore();
      await assertFails(setDoc(doc(db, "posts/bad-audience"), {
        uid: "stranger-user", authorName: "Stranger", text: "bad audience", audience: "everyone"
      }));
    }],
    ["User cannot create a post with oversized text", async () => {
      const db = testEnv.authenticatedContext("stranger-user").firestore();
      await assertFails(setDoc(doc(db, "posts/oversized-post"), {
        uid: "stranger-user", authorName: "Stranger", text: "x".repeat(5001), audience: "public"
      }));
    }],
    ["User cannot create a story that expires after more than 24 hours", async () => {
      const db = testEnv.authenticatedContext("stranger-user").firestore();
      await assertFails(setDoc(doc(db, "stories/long-story"), {
        uid: "stranger-user", authorName: "Stranger",
        mediaUrl: "https://example.test/story.jpg", mediaType: "image",
        createdAt: new Date(), expiresAt: new Date(Date.now() + 25 * 60 * 60 * 1000)
      }));
    }],
    ["Non-friend cannot read a friends-only post", async () => {
      const db = testEnv.authenticatedContext("stranger-user").firestore();
      await assertFails(getDoc(doc(db, "posts/friends-post")));
    }],
    ["Accepted friend can read a friends-only post", async () => {
      const db = testEnv.authenticatedContext("friend-user").firestore();
      await assertSucceeds(getDoc(doc(db, "posts/friends-post")));
    }],
    ["Accepted friend cannot read an explicitly private post", async () => {
      const db = testEnv.authenticatedContext("friend-user").firestore();
      await assertFails(getDoc(doc(db, "posts/private-post")));
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
    ["Public-profile Story shared from a private Reel is visible to a signed-in non-friend", async () => {
      const db = testEnv.authenticatedContext("stranger-user").firestore();
      await assertSucceeds(getDoc(doc(db, "stories/public-story-from-private-reel")));
    }],
    ["The source private Reel remains hidden from a signed-in non-friend", async () => {
      const db = testEnv.authenticatedContext("stranger-user").firestore();
      await assertFails(getDoc(doc(db, "posts/public-owner-private-reel")));
    }],
    ["Private-profile Story shared from a public Reel remains hidden from a non-friend", async () => {
      const db = testEnv.authenticatedContext("stranger-user").firestore();
      await assertFails(getDoc(doc(db, "stories/private-story-from-public-reel")));
    }],
    ["Accepted friend can read a private-profile Story shared from a public Reel", async () => {
      const db = testEnv.authenticatedContext("friend-user").firestore();
      await assertSucceeds(getDoc(doc(db, "stories/private-story-from-public-reel")));
    }],
    ["Public Story does not inherit the source Reel audience field", async () => {
      const db = testEnv.authenticatedContext("stranger-user").firestore();
      const snapshot = await assertSucceeds(getDoc(doc(db, "stories/public-story-from-private-reel")));
      if (Object.prototype.hasOwnProperty.call(snapshot.data(), "audience")) {
        throw new Error("Story must use profile visibility, not a copied Reel audience");
      }
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
    ["Owner can remove legacy private profile media URLs from directory", async () => {
      const db = testEnv.authenticatedContext("private-user").firestore();
      await assertSucceeds(updateDoc(doc(db, "profileDirectory/private-user"), {
        displayName: "Updated Private User",
        photoUrl: deleteField(),
        coverPhotoUrl: deleteField()
      }));
    }],
    ["Owner cannot re-add private profile media URLs to directory", async () => {
      const db = testEnv.authenticatedContext("private-user").firestore();
      await assertFails(updateDoc(doc(db, "profileDirectory/private-user"), {
        photoUrl: "https://example.test/private-avatar.jpg"
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
