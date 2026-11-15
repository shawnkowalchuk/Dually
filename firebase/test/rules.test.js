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
import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  setDoc,
  writeBatch,
} from 'firebase/firestore';

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

  test('writes and reads which phone is backing up (since 2026-11-12)', async () => {
    const ref = doc(as(OWNER), `users/${OWNER}/backup/owner`);
    await assertSucceeds(setDoc(ref, { json: '{"install":"a1"}' }));
    await assertSucceeds(getDoc(ref));
  });

  test('reads the whole of its trips and its backup folder at once, for a restore', async () => {
    const db = as(OWNER);
    await assertSucceeds(getDocs(collection(db, `users/${OWNER}/trips`)));
    await assertSucceeds(getDocs(collection(db, `users/${OWNER}/backup`)));
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

  test('cannot list the owner\'s trips', async () => {
    await assertFails(getDocs(collection(as(OTHER), `users/${OWNER}/trips`)));
    await assertFails(getDocs(collection(as(null), `users/${OWNER}/trips`)));
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

describe('the purchase check\'s own records', () => {
  test('no app can read or write a purchase or a deletion', async () => {
    await seed('purchases/abc', { uid: OWNER });
    await seed(`deletions/${OWNER}`, { dueAt: 1 });
    for (const path of ['purchases/abc', `deletions/${OWNER}`]) {
      await assertFails(getDoc(doc(as(OWNER), path)));
      await assertFails(setDoc(doc(as(OWNER), path), { uid: OWNER }));
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

// ADR-017 (2026-11-14): every phone backs up into its own folder, and reads the others'.
describe("each phone's own folder", () => {
  const PHONE = '0f9b6c1e-6a2b-4c55-9d1e-3b2a8f7c6d5e';
  const IPHONE = 'a7c3e2d1-1b2c-4d3e-8f4a-5b6c7d8e9f01';
  const record = { json: '{"name":"iPhone","platform":"iphone"}' };

  test('an account with the unlock writes its phone, its trips and its backup documents', async () => {
    await unlock(OWNER);
    const db = as(OWNER);
    await assertSucceeds(setDoc(doc(db, `users/${OWNER}/phones/${PHONE}`), record));
    for (const path of ['trips/17', 'backup/sentReports', 'backup/settings']) {
      const ref = doc(db, `users/${OWNER}/phones/${PHONE}/${path}`);
      await assertSucceeds(setDoc(ref, trip));
      await assertSucceeds(getDoc(ref));
      await assertSucceeds(deleteDoc(ref));
    }
  });

  test('every phone of the account lists the phones and reads the other phones\' trips', async () => {
    await unlock(OWNER);
    await seed(`users/${OWNER}/phones/${PHONE}`, record);
    await seed(`users/${OWNER}/phones/${PHONE}/trips/17`, trip);
    const db = as(OWNER);
    await assertSucceeds(getDocs(collection(db, `users/${OWNER}/phones`)));
    await assertSucceeds(getDocs(collection(db, `users/${OWNER}/phones/${PHONE}/trips`)));
    await assertSucceeds(getDocs(collection(db, `users/${OWNER}/phones/${IPHONE}/trips`)));
  });

  test('without the unlock, a phone writes nothing, and still reads', async () => {
    await seed(`users/${OWNER}/phones/${PHONE}/trips/17`, trip);
    const db = as(OWNER);
    await assertFails(setDoc(doc(db, `users/${OWNER}/phones/${PHONE}`), record));
    await assertFails(setDoc(doc(db, `users/${OWNER}/phones/${PHONE}/trips/18`), trip));
    await assertSucceeds(getDoc(doc(db, `users/${OWNER}/phones/${PHONE}/trips/17`)));
  });

  test('no other account, and nobody signed out, reads or writes a phone\'s folder', async () => {
    await unlock(OWNER);
    await unlock(OTHER);
    await seed(`users/${OWNER}/phones/${PHONE}/trips/17`, trip);
    for (const db of [as(OTHER), as(null)]) {
      await assertFails(getDocs(collection(db, `users/${OWNER}/phones`)));
      await assertFails(getDoc(doc(db, `users/${OWNER}/phones/${PHONE}/trips/17`)));
      await assertFails(setDoc(doc(db, `users/${OWNER}/phones/${PHONE}/trips/18`), trip));
    }
  });

  test('a phone folder takes only a phone\'s name, and only the backup\'s own paths', async () => {
    await unlock(OWNER);
    const db = as(OWNER);
    await assertFails(setDoc(doc(db, `users/${OWNER}/phones/not-a-phone`), record));
    await assertFails(setDoc(doc(db, `users/${OWNER}/phones/${PHONE}/photos/1`), trip));
    await assertFails(setDoc(doc(db, `users/${OWNER}/phones/${PHONE}/trips/abc`), trip));
    await assertFails(setDoc(doc(db, `users/${OWNER}/phones/${PHONE}`), { json: 1 }));
  });
});

describe('the settings every phone shares (ADR-017, step 3)', () => {
  test('an account with the unlock writes and lists them; without it, it only reads', async () => {
    await unlock(OWNER);
    const db = as(OWNER);
    for (const name of ['report', 'service', 'odometer']) {
      await assertSucceeds(setDoc(doc(db, `users/${OWNER}/shared/${name}`), trip));
    }
    await assertSucceeds(getDocs(collection(db, `users/${OWNER}/shared`)));
    await assertFails(setDoc(doc(db, `users/${OWNER}/shared/photos`), trip));
    await assertFails(getDocs(collection(as(OTHER), `users/${OWNER}/shared`)));
  });

  test('without the unlock nothing is written', async () => {
    await seed(`users/${OWNER}/shared/report`, trip);
    await assertFails(setDoc(doc(as(OWNER), `users/${OWNER}/shared/report`), trip));
    await assertSucceeds(getDoc(doc(as(OWNER), `users/${OWNER}/shared/report`)));
  });
});
