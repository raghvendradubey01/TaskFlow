const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validTaskPayload(userId, overrides = {}) {
  const now = new Date();
  return {
    title: "Sync project roadmap across devices",
    notes: "Review Q4 milestones and assign owners",
    completed: false,
    priority: "HIGH",
    workspaceName: "Work",
    dueDateMillis: 1792700000000,
    reminderTimeMillis: 1792690000000,
    alertMode: "CALL",
    subtasks: ["0|Draft outline", "1|Share with team"],
    tags: ["roadmap", "sync"],
    pinned: true,
    deviceName: "Pixel 9 Pro",
    userId: userId,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validWorkspacePayload(userId, overrides = {}) {
  const now = new Date();
  return {
    name: "Deep Work",
    colorHex: "#6366F1",
    iconName: "rocket",
    userId: userId,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

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

test("1. Unauthenticated user: cannot read or create tasks", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("tasks").where("userId", "==", ALICE_UID).get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").set(validTaskPayload(ALICE_UID))
  );
});

test("2. Authenticated user: can create, get, update reminder/alertMode, and delete own valid task", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const taskRef = aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1");

  await assertSucceeds(taskRef.set(validTaskPayload(ALICE_UID)));
  await assertSucceeds(taskRef.get());
  await assertSucceeds(
    taskRef.update({
      completed: true,
      alertMode: "POPUP",
      reminderTimeMillis: 1792710000000,
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(taskRef.delete());
});

test("3. Cross-user isolation: Bob cannot read, update, or delete Alice's task", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").set(validTaskPayload(ALICE_UID))
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceTaskFromBob = bobDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1");
  await assertFails(aliceTaskFromBob.get());
  await assertFails(aliceTaskFromBob.update({ completed: true, updatedAt: new Date() }));
  await assertFails(aliceTaskFromBob.delete());
});

test("4. Identity spoofing: Alice cannot create a task with Bob's userId", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_spoof").set(validTaskPayload(BOB_UID))
  );
});

test("5. Shadow update / Ghost field injection: rejects unknown fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const taskRef = aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_shadow");
  await assertFails(
    taskRef.set(validTaskPayload(ALICE_UID, { isVerified: true }))
  );
});

test("6. Enum & boundary enforcement: rejects invalid priority, invalid alertMode, or oversized title", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const taskRef = aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_invalid");
  await assertFails(
    taskRef.set(validTaskPayload(ALICE_UID, { priority: "INVALID_PRIORITY" }))
  );
  await assertFails(
    taskRef.set(validTaskPayload(ALICE_UID, { alertMode: "SMS_ALERT" }))
  );
  await assertFails(
    taskRef.set(validTaskPayload(ALICE_UID, { title: "" }))
  );
});

test("7. Client query alignment: Alice can list own tasks with userId filter", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").set(validTaskPayload(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").where("userId", "==", ALICE_UID).get()
  );
});

test("8. Workspaces: Alice can create, list, update, and delete own workspace, and Bob is blocked", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const wsRef = aliceDb.collection("users").doc(ALICE_UID).collection("workspaces").doc("ws_1");
  await assertSucceeds(wsRef.set(validWorkspacePayload(ALICE_UID)));
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("workspaces").where("userId", "==", ALICE_UID).get()
  );
  await assertSucceeds(wsRef.update({ name: "Core Focus", updatedAt: new Date() }));

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("workspaces").doc("ws_1").get());
  await assertSucceeds(wsRef.delete());
});
