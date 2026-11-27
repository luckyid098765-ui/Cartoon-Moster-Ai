const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_001";
const BOB_UID = "bob_uid_002";
const ADMIN_EMAIL = "sudarshanmanna06@gmail.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function buildProjectPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    title: "Brave Fox Adventure",
    storyPrompt: "A clever red fox builds a flying wooden ship in the forest.",
    characterPrompt: "Amber-furred fox wearing blue goggles",
    characterBible: "Pip: small amber fox, oversized cyan aviator goggles, red scarf.",
    style: "THREE_D",
    language: "ENGLISH",
    durationMinutes: 5,
    quality: "FHD_1080P",
    includeVoiceover: true,
    includeBgm: true,
    includeSfx: true,
    status: "GENERATING_STORY",
    progressPercent: 15,
    statusMessage: "Writing storyboard...",
    sceneCount: 6,
    completedScenes: 1,
    fileSizeBytes: 0,
    verifiedMp4: false,
    retryCount: 0,
    providerUsed: "FIREBASE_AI_NATIVE",
    downloadCount: 0,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("projects").get());
  await assertFails(unauthDb.collection("generationJobs").get());
  await assertFails(unauthDb.collection("adminConfig").doc("providers").get());
});

test("User profile creation, update, and cross-user PII isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      preferredLanguage: "ENGLISH",
      preferredQuality: "HD_720P",
      totalVideosCreated: 0,
      totalDownloads: 0,
      storageUsedBytes: 0,
      accountStatus: "ACTIVE",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob cannot read Alice's PII profile or list all users
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").get());

  // Alice cannot escalate her accountStatus or inject ghost fields
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      accountStatus: "SUSPENDED",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Cartoon project & scene lifecycle, terminal locking, and cross-user denial", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const projectRef = aliceDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_1");

  await assertSucceeds(projectRef.set(buildProjectPayload(ALICE_UID)));

  // Reject @DocumentId 'id' in payload
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_bad").set(
      buildProjectPayload(ALICE_UID, { id: "proj_bad" })
    )
  );

  // Reject invalid duration (e.g. 45 minutes > max 30)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_45").set(
      buildProjectPayload(ALICE_UID, { durationMinutes: 45 })
    )
  );

  // Scene subcollection creation & update
  const now = new Date(Date.now() - 1000);
  const sceneRef = projectRef.collection("scenes").doc("scene_0");
  await assertSucceeds(
    sceneRef.set({
      userId: ALICE_UID,
      projectId: "proj_1",
      sceneIndex: 0,
      title: "Opening Meadow",
      visualPrompt: "Pip standing by a sunlit oak tree",
      narrationText: "One bright morning, Pip set off on an adventure.",
      sfxCue: "birds_chirp",
      cameraMotion: "ZOOM_IN",
      durationSeconds: 12,
      status: "READY",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Pause & resume transition allowed while not COMPLETED
  await assertSucceeds(
    projectRef.update({
      status: "PAUSED",
      progressPercent: 45,
      statusMessage: "Paused at checkpoint",
      quality: "SD_480P",
      style: "CINEMATIC",
      updatedAt: new Date(Date.now() - 800),
    })
  );

  // Complete project
  await assertSucceeds(
    projectRef.update({
      status: "COMPLETED",
      progressPercent: 100,
      statusMessage: "MP4 verified and ready",
      verifiedMp4: true,
      fileSizeBytes: 2450000,
      localVideoPath: "/data/user/0/app/files/videos/proj_1.mp4",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Terminal lock: cannot revert status back to DRAFT or mutate storyPrompt after COMPLETED
  await assertFails(
    projectRef.update({
      status: "DRAFT",
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Allowed post-completion action: increment downloadCount
  await assertSucceeds(
    projectRef.update({
      downloadCount: 1,
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Bob cannot read or delete Alice's project
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_1").delete());
});

test("GenerationJobs query alignment and Admin access controls", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const adminDb = testEnv.authenticatedContext("admin_uid", {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();
  const spoofedAdminDb = testEnv.authenticatedContext("spoof_uid", {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();

  const now = new Date(Date.now() - 2000);
  await assertSucceeds(
    aliceDb.collection("generationJobs").doc("job_1").set({
      userId: ALICE_UID,
      projectId: "proj_1",
      projectTitle: "Brave Fox Adventure",
      style: "THREE_D",
      language: "HINDI",
      durationMinutes: 10,
      quality: "HD_720P",
      providerName: "FIREBASE_AI_NATIVE",
      status: "RUNNING",
      progressPercent: 40,
      retryCount: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Scoped user query succeeds; unfiltered query fails for non-admin
  await assertSucceeds(aliceDb.collection("generationJobs").where("userId", "==", ALICE_UID).get());
  await assertFails(bobDb.collection("generationJobs").get());

  // Verified admin can list all jobs and configure adminConfig
  await assertSucceeds(adminDb.collection("generationJobs").get());
  await assertSucceeds(
    adminDb.collection("adminConfig").doc("providers").set({
      activeProvider: "FIREBASE_AI_NATIVE",
      providerLabel: "Firebase AI Logic + MediaCodec Studio",
      remoteEndpointUrl: "",
      timeoutSeconds: 60,
      maxRetries: 3,
      storageQuotaMb: 2048,
      adMobEnabled: false,
      adMobBannerUnitId: "",
      maintenanceMode: false,
      updatedBy: ADMIN_EMAIL,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Unverified email spoof cannot write adminConfig; regular user cannot write adminConfig
  await assertFails(
    spoofedAdminDb.collection("adminConfig").doc("providers").set({
      activeProvider: "FIREBASE_AI_NATIVE",
      providerLabel: "Spoof",
      remoteEndpointUrl: "",
      timeoutSeconds: 60,
      maxRetries: 3,
      storageQuotaMb: 2048,
      adMobEnabled: false,
      adMobBannerUnitId: "",
      maintenanceMode: false,
      updatedBy: ADMIN_EMAIL,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    aliceDb.collection("adminConfig").doc("providers").set({
      activeProvider: "CUSTOM_REST_BACKEND",
      providerLabel: "Hijack",
      remoteEndpointUrl: "https://evil.example.com",
      timeoutSeconds: 60,
      maxRetries: 3,
      storageQuotaMb: 2048,
      adMobEnabled: false,
      adMobBannerUnitId: "",
      maintenanceMode: false,
      updatedBy: "alice",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});
