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

test("Unauthenticated user: cannot read favorites or cloud_items", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("favorites").get());
  await assertFails(unauthDb.collection("cloud_items").where("visibility", "==", "public").get());
});

test("Authenticated user: cannot read another user's favorite", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(BOB_UID).collection("favorites").doc("match_1").set({
      userId: BOB_UID,
      itemId: "1",
      itemType: "match",
      title: "ARS vs CHE",
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("favorites").doc("match_1").get());
});

test("Authenticated user: can create and query own favorites with matching userId filter", async () => {
  const now = new Date();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("favorites").doc("match_1").set({
      userId: ALICE_UID,
      itemId: "1",
      itemType: "match",
      title: "ARS vs CHE",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("favorites").where("userId", "==", ALICE_UID).get()
  );
});

test("Shadow update test: rejects ghost field on favorites", async () => {
  const now = new Date();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("favorites").doc("match_1").set({
      userId: ALICE_UID,
      itemId: "1",
      itemType: "match",
      title: "ARS vs CHE",
      isVerified: true,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Authenticated user: can create and list public cloud_items with visibility filter", async () => {
  const now = new Date();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("cloud_items").doc("match_101").set({
      ownerId: ALICE_UID,
      itemType: "match",
      visibility: "public",
      title: "RMA vs BAR",
      payloadJson: "{\"id\":\"101\"}",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("cloud_items").where("visibility", "==", "public").get()
  );
});
