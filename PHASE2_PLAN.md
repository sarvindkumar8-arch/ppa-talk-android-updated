# PPA Talk — Phase 2: Social Feed, Posts & Reels

Status: Approved scope; implementation not yet complete.
Working branch: `feature/phase-2-social-feed-reels`
Base: `main`

## Phase 2 goals

Build the core social experience while preserving the PPA Talk Royal White theme:
- Royal Blue: `#2457D6`
- Orange: `#F27A21`
- Light surfaces: white / soft grey
- Dark mode: charcoal surfaces
- Tagline: **Create. Connect. Share.**
- Top navigation icons; do not reintroduce bottom navigation without approval.

## Work sequence

Each module must be implemented, built, reviewed, and tested before the next module begins. Do not mark a module complete solely because code was committed or CI passed.

### 2.1 Social home and feed
- Scrollable feed with author avatar/name, timestamp, text and media.
- Pull-to-refresh and paginated loading.
- Empty, loading, and error states.
- Feed item actions and navigation to creator profile.

### 2.2 Create and manage posts
- Create text posts and photo posts; support selecting multiple photos.
- Caption/text entry and upload progress.
- Edit own post and delete own post with confirmation.
- Validate file type/size and handle upload failures.

### 2.3 Post engagement
- Like/unlike with accurate counts and duplicate-action protection.
- Read and add comments; show comment counts.
- Share through Android share sheet / supported in-app share flow.
- Enforce authorisation so users can edit/delete only their own content.

### 2.4 Reels experience
- Full-screen vertical, swipe/scroll Reels feed.
- Load video posts with creator identity and caption.
- Gallery video selection and Reel upload with progress and retry/error states.
- Like/unlike, comments, and share actions.
- Open creator profile from a Reel.
- Delete own Reel with confirmation.
- Record and display view counts and engagement counts; avoid counting repeated playback as multiple unique views where practical.
- Handle playback lifecycle, pause when leaving the screen, and release media resources.

### 2.5 Social connections
- Friend request send, accept, reject, and cancel.
- Friends list and profile navigation.
- Prevent duplicate/self friend requests and enforce access rules.

## Backend and data safeguards
- Use Firebase Authentication identity as the source of user identity.
- Store posts, comments, reactions, friend requests, and Reel metadata in Firestore.
- Store photos and videos in Firebase Storage; store only metadata and storage paths in Firestore.
- Add and review Firestore and Storage security rules before enabling writes.
- Enforce ownership checks for editing/deleting posts and Reels.
- Add pagination and sensible media limits; never expose private credentials or password hashes in public documents.
- Confirm required Firebase services, indexes, and deployment steps before claiming backend functionality.

## Acceptance gates
For every module:
1. Code review for loading, empty, error, and permission states.
2. GitHub Actions build must pass.
3. Test authenticated and unauthenticated access, owner and non-owner actions, and media upload failures where applicable.
4. Device testing is still required before production readiness; CI is not proof of Firebase deployment or real-device behavior.
5. User approval is required before moving to the next module.

## Existing Phase 1 work
Phase 1 remains a separate track. Its previously reported mobile signup `Not found` issue, backend deployment verification, OTP/authentication device tests, profile/media tests, and security-rule audit remain open until independently verified. Phase 2 work must not mark those items complete.

## Explicit non-goals for this phase
Live streaming, video editing tools, monetisation/payouts, advanced recommendation algorithms, and public launch hardening are not included unless separately approved.
