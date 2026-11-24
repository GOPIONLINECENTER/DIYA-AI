const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_diya_01";
const BOB_UID = "bob_diya_02";

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

test("Unauthenticated user cannot read or write devices, pairingCodes, or commandRequests", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/devices`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/pairingCodes`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/commandRequests`).get());

  const now = new Date(Date.now() - 1000);
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/devices`).doc("mob_1").set({
      userId: ALICE_UID,
      deviceType: "ANDROID_MOBILE",
      deviceName: "Pixel 9",
      connectionStatus: "ONLINE",
      pairingStatus: "UNPAIRED",
      allowedActions: ["WORKSPACE_STATUS_CHECK"],
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Owner can register mobile device and update status, while cross-user access is denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/devices`).doc("mob_alice_1").set({
      userId: ALICE_UID,
      deviceType: "ANDROID_MOBILE",
      deviceName: "Android Handheld",
      connectionStatus: "ONLINE",
      pairingStatus: "WAITING_FOR_PC",
      allowedActions: ["OPEN_BROWSER_URL", "WORKSPACE_STATUS_CHECK"],
      lastConnectedAt: created,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update allowed fields
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/devices`).doc("mob_alice_1").update({
      connectionStatus: "STANDBY",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject immutable deviceType change & ghost field injection
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/devices`).doc("mob_alice_1").update({
      deviceType: "WINDOWS_DESKTOP",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/devices`).doc("mob_alice_2").set({
      id: "mob_alice_2",
      userId: ALICE_UID,
      deviceType: "ANDROID_MOBILE",
      deviceName: "Ghost Field Device",
      connectionStatus: "ONLINE",
      pairingStatus: "UNPAIRED",
      allowedActions: [],
      createdAt: created,
      updatedAt: created,
    })
  );

  // Cross-user isolation
  await assertFails(bobDb.collection(`users/${ALICE_UID}/devices`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/devices`).doc("mob_alice_1").get());
  await assertFails(
    bobDb.collection(`users/${ALICE_UID}/devices`).doc("mob_alice_1").update({
      deviceName: "Hijacked",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Pairing code creation enforces active status and TTL <= 15m, and revoking locks further updates", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 2000);
  const expiresValid = new Date(Date.now() + 5 * 60 * 1000);
  const expiresTooFar = new Date(Date.now() + 60 * 60 * 1000);

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/pairingCodes`).doc("pair_1").set({
      userId: ALICE_UID,
      code: "DY8K-4M2P",
      mobileDeviceId: "mob_alice_1",
      mobileDeviceName: "Android Handheld",
      status: "ACTIVE",
      expiresAt: expiresValid,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject excessive TTL (> 15m)
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/pairingCodes`).doc("pair_bad_ttl").set({
      userId: ALICE_UID,
      code: "DY8K-9999",
      mobileDeviceId: "mob_alice_1",
      mobileDeviceName: "Android Handheld",
      status: "ACTIVE",
      expiresAt: expiresTooFar,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Revoke code
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/pairingCodes`).doc("pair_1").update({
      status: "REVOKED",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Cannot re-activate revoked code
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/pairingCodes`).doc("pair_1").update({
      status: "ACTIVE",
      updatedAt: new Date(Date.now() - 200),
    })
  );
});

test("Command requests block arbitrary shell actions and enforce explicit confirmation before queuing", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 3000);

  // Reject non-allowlisted arbitrary shell command
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/commandRequests`).doc("cmd_bad").set({
      userId: ALICE_UID,
      sourceDeviceId: "mob_alice_1",
      actionType: "RUN_POWERSHELL_EXEC",
      summaryText: "Run arbitrary shell",
      targetScope: "rm -rf /",
      confirmationRequired: true,
      confirmed: true,
      status: "APPROVED_QUEUED",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject sensitive command created directly as APPROVED_QUEUED without confirmation flow
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/commandRequests`).doc("cmd_bypass").set({
      userId: ALICE_UID,
      sourceDeviceId: "mob_alice_1",
      actionType: "DRAFT_MESSAGE_PREVIEW",
      summaryText: "Send WhatsApp message",
      targetScope: "contact:family",
      confirmationRequired: true,
      confirmed: false,
      status: "APPROVED_QUEUED",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Valid sensitive command starts as PENDING_CONFIRMATION
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/commandRequests`).doc("cmd_ok").set({
      userId: ALICE_UID,
      sourceDeviceId: "mob_alice_1",
      actionType: "DRAFT_MESSAGE_PREVIEW",
      summaryText: "Draft Bengali greeting message",
      targetScope: "recipient:preview_only",
      confirmationRequired: true,
      confirmed: false,
      status: "PENDING_CONFIRMATION",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Cannot queue while confirmed remains false
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/commandRequests`).doc("cmd_ok").update({
      status: "APPROVED_QUEUED",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Explicit confirmation succeeds
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/commandRequests`).doc("cmd_ok").update({
      confirmed: true,
      status: "PC_OFFLINE_NOT_SENT",
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Can cancel or mark executed -> terminal lock prevents further edits
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/commandRequests`).doc("cmd_ok").update({
      status: "CANCELLED",
      updatedAt: new Date(Date.now() - 200),
    })
  );

  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/commandRequests`).doc("cmd_ok").update({
      status: "APPROVED_QUEUED",
      updatedAt: new Date(Date.now() - 100),
    })
  );
});
