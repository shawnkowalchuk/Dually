// The security rules of the cloud backup (firebase/firestore.rules), on Firebase's Firestore
// emulator: `npm test` in firebase/ starts the emulator and runs this file. Nothing here
// reaches the real project; "demo-dually" is a project id the emulator keeps to itself.

import { after, before, beforeEach, describe, test } from 'node:test';
import { readFileSync } from 'node:fs';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, setDoc, writeBatch } from 'firebase/firestore';

const OWNER = 'owner-uid';
const OTHER = 'other-uid';
const trip = { json: '{"formatVersion":3,"trip":{"id":17}}' };

let environment;

before(async () => {
  environment = await initializeTestEnvironment({
    projectId: 'demo-dually',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
});

after(async () => {
  await environment.cleanup();
});

beforeEach(async () => {
  await environment.clearFirestore();
});

/** Firestore as the account [uid] sees it, or as nobody signed in when [uid] is null. */
function as(uid) {
  return uid === null
    ? environment.unauthenticatedContext().firestore()
    : environment.authenticatedContext(uid).firestore();
}

/** Writes past the rules, as the purchase check on the server will. */
async function seed(path, data) {
  await environment.withSecurityRulesDisabled((context) =>
    setDoc(doc(context.firestore(), path), data),
  );
}

const unlock = (uid) => seed(`unlocks/${uid}`, { at: 1 });

describe('an account that holds the unlock', () => {
  beforeEach(() => unlock(OWNER));

  test('writes, reads and removes its trips and its two backup documents', async () => {
    const db = as(OWNER);
    for (const path of ['trips/17', 'backup/sentReports', 'backup/settings']) {
      const ref = doc(db, `users/${OWNER}/${path}`);
      await assertSucceeds(setDoc(ref, trip));
      await assertSucceeds(getDoc(ref));
      await assertSucceeds(deleteDoc(ref));
    }
  });

  test('sends a batch of 400 writes and removals at once', async () => {
    const db = as(OWNER);
    const batch = writeBatch(db);
    for (let id = 1; id <= 300; id++) batch.set(doc(db, `users/${OWNER}/trips/${id}`), trip);
    for (let id = 301; id <= 400; id++) batch.delete(doc(db, `users/${OWNER}/trips/${id}`));
    await assertSucceeds(batch.commit());
  });

  test('cannot write a document of another shape', async () => {
    const db = as(OWNER);
    const ref = doc(db, `users/${OWNER}/trips/17`);
    await assertFails(setDoc(ref, { json: '{}', extra: 1 }));
    await assertFails(setDoc(ref, { json: 17 }));
    await assertFails(setDoc(ref, { other: '{}' }));
    await assertFails(setDoc(ref, { json: 'x'.repeat(500001) }));
    await assertSucceeds(setDoc(ref, { json: 'x'.repeat(500000) }));
  });

  test('cannot write anywhere but the backup\'s own paths', async () => {
    const db = as(OWNER);
    for (const path of [
      'trips/abc',
      'trips/0',
      'trips/017',
      'trips/-1',
      'backup/points',
      'points/17',
      'trips/17/points/1',
    ]) {
      await assertFails(setDoc(doc(db, `users/${OWNER}/${path}`), trip));
    }
    await assertFails(setDoc(doc(db, `users/${OWNER}`), trip));
    await assertFails(setDoc(doc(db, 'trips/17'), trip));
  });
});

describe('an account without the unlock', () => {
  test('cannot write, and still reads and removes what it has', async () => {
    await seed(`users/${OWNER}/trips/17`, trip);
    const db = as(OWNER);
    const ref = doc(db, `users/${OWNER}/trips/17`);
    await assertFails(setDoc(ref, trip));
    await assertSucceeds(getDoc(ref));
    await assertSucceeds(deleteDoc(ref));
  });
});

describe('another account, and nobody signed in', () => {
  beforeEach(async () => {
    await unlock(OWNER);
    await unlock(OTHER);
    await seed(`users/${OWNER}/trips/17`, trip);
  });

  test('can neither read, write nor remove the owner\'s documents', async () => {
    for (const db of [as(OTHER), as(null)]) {
      const ref = doc(db, `users/${OWNER}/trips/17`);
      await assertFails(getDoc(ref));
      await assertFails(setDoc(ref, trip));
      await assertFails(deleteDoc(ref));
    }
  });
});

describe('the unlocks', () => {
  beforeEach(() => unlock(OWNER));

  test('an account reads its own, and no one else\'s', async () => {
    await assertSucceeds(getDoc(doc(as(OWNER), `unlocks/${OWNER}`)));
    await assertFails(getDoc(doc(as(OTHER), `unlocks/${OWNER}`)));
    await assertFails(getDoc(doc(as(null), `unlocks/${OWNER}`)));
  });

  test('no app can give itself one, or take one away', async () => {
    await assertFails(setDoc(doc(as(OTHER), `unlocks/${OTHER}`), { at: 1 }));
    await assertFails(setDoc(doc(as(OWNER), `unlocks/${OWNER}`), { at: 2 }));
    await assertFails(deleteDoc(doc(as(OWNER), `unlocks/${OWNER}`)));
  });
});
