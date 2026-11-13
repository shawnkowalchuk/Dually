// The purchase check (firebase/functions/unlock.js) on the Firestore emulator, with a stand-in
// for Google Play: which purchases unlock which account, the acknowledgement, and refunds.
// Run by `npm test` in firebase/, with the rules' tests.

import { after, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import {
  accountTag,
  deleteAccount,
  KEPT_AFTER_REFUND_MS,
  revokeRefunded,
  runDeletions,
  unlock,
} from '../functions/unlock.js';

const app = initializeApp({ projectId: 'demo-dually' }, 'unlock-tests');
const db = getFirestore(app);
const OWNER = 'owner-uid';
const OTHER = 'other-uid';
const TOKEN = 'purchase-token-0001';

/** Google Play, as the test says it is. */
function fakePlay(purchases = {}, voided = []) {
  const acknowledged = [];
  return {
    acknowledged,
    async getProduct(token) {
      if (!(token in purchases)) throw new Error('Play: no such purchase');
      return purchases[token];
    },
    async acknowledge(token) {
      acknowledged.push(token);
    },
    async voided() {
      return voided;
    },
  };
}

/** A purchase of the backup by [uid], paid for and not yet acknowledged. */
function paidBy(uid, extra = {}) {
  return {
    purchaseState: 0,
    acknowledgementState: 0,
    obfuscatedExternalAccountId: accountTag(uid),
    orderId: 'GPA.1234-5678',
    purchaseTimeMillis: '1762900000000',
    ...extra,
  };
}

async function exists(path) {
  return (await db.doc(path).get()).exists;
}

async function rejection(promise) {
  try {
    await promise;
  } catch (error) {
    return `${error.code}/${error.reason}`;
  }
  assert.fail('expected a refusal');
}

beforeEach(async () => {
  await Promise.all(
    ['unlocks', 'purchases', 'deletions'].map(async (name) => {
      const all = await db.collection(name).get();
      await Promise.all(all.docs.map((d) => d.ref.delete()));
    }),
  );
  await Promise.all([OWNER, OTHER].map((uid) => db.recursiveDelete(db.doc(`users/${uid}`))));
});

after(async () => {
  await deleteApp(app);
});

describe('the account tag', () => {
  test('is the SHA-256 of the account id in hex, as the app computes it', () => {
    // The app's CloudAccountFileTest checks the same value for the same id.
    assert.equal(
      accountTag('owner-uid'),
      '27dfbde6ec9d28c36258ee6edad10e40cdaeca1adf3f7ea7cdb23b494a1765ac',
    );
  });
});

describe('a purchase', () => {
  test('paid for by this account unlocks it, and is acknowledged once', async () => {
    const play = fakePlay({ [TOKEN]: paidBy(OWNER) });

    assert.deepEqual(await unlock({ uid: OWNER, token: TOKEN, play, db, now: 5 }), {
      unlocked: true,
    });

    assert.ok(await exists(`unlocks/${OWNER}`));
    assert.deepEqual(play.acknowledged, [TOKEN]);
  });

  test('restored a second time is the same, and is not acknowledged again', async () => {
    const play = fakePlay({ [TOKEN]: paidBy(OWNER, { acknowledgementState: 1 }) });

    await unlock({ uid: OWNER, token: TOKEN, play, db, now: 5 });
    await unlock({ uid: OWNER, token: TOKEN, play, db, now: 6 });

    assert.ok(await exists(`unlocks/${OWNER}`));
    assert.deepEqual(play.acknowledged, []);
  });

  test('made by another account unlocks nothing', async () => {
    const play = fakePlay({ [TOKEN]: paidBy(OWNER) });

    assert.equal(
      await rejection(unlock({ uid: OTHER, token: TOKEN, play, db, now: 5 })),
      'permission-denied/other-account',
    );
    assert.ok(!(await exists(`unlocks/${OTHER}`)));
  });

  test('pending, cancelled or unknown unlocks nothing', async () => {
    const play = fakePlay({
      pendingToken01: paidBy(OWNER, { purchaseState: 2 }),
      cancelledToken: paidBy(OWNER, { purchaseState: 1 }),
    });

    assert.equal(
      await rejection(unlock({ uid: OWNER, token: 'pendingToken01', play, db, now: 5 })),
      'failed-precondition/pending',
    );
    assert.equal(
      await rejection(unlock({ uid: OWNER, token: 'cancelledToken', play, db, now: 5 })),
      'failed-precondition/not-purchased',
    );
    assert.equal(
      await rejection(unlock({ uid: OWNER, token: '', play, db, now: 5 })),
      'invalid-argument/no-token',
    );
    await assert.rejects(unlock({ uid: OWNER, token: 'unknownToken01', play, db, now: 5 }));
    assert.ok(!(await exists(`unlocks/${OWNER}`)));
  });
});

describe('a refund', () => {
  test('takes the unlock away and marks the cloud copy for deletion 30 days on', async () => {
    const play = fakePlay({ [TOKEN]: paidBy(OWNER) }, [
      { purchaseToken: TOKEN, voidedTimeMillis: '1000' },
    ]);
    await unlock({ uid: OWNER, token: TOKEN, play, db, now: 5 });

    assert.equal(await revokeRefunded({ play, db, sinceMs: 0 }), 1);
    assert.equal(await revokeRefunded({ play, db, sinceMs: 0 }), 0);

    assert.ok(!(await exists(`unlocks/${OWNER}`)));
    const deletion = await db.doc(`deletions/${OWNER}`).get();
    assert.equal(deletion.get('dueAt'), 1000 + KEPT_AFTER_REFUND_MS);
  });

  test('of a purchase no account used changes nothing', async () => {
    const play = fakePlay({}, [{ purchaseToken: 'neverUsedToken', voidedTimeMillis: '1000' }]);

    assert.equal(await revokeRefunded({ play, db, sinceMs: 0 }), 0);
  });

  test('cannot be undone by restoring the refunded purchase', async () => {
    const play = fakePlay({ [TOKEN]: paidBy(OWNER) }, [
      { purchaseToken: TOKEN, voidedTimeMillis: '1000' },
    ]);
    await unlock({ uid: OWNER, token: TOKEN, play, db, now: 5 });
    await revokeRefunded({ play, db, sinceMs: 0 });

    assert.equal(
      await rejection(unlock({ uid: OWNER, token: TOKEN, play, db, now: 6 })),
      'failed-precondition/refunded',
    );
  });
});

/** A backup of three trips and the two backup documents, as the app writes them. */
async function backupOf(uid) {
  for (const path of ['trips/1', 'trips/2', 'trips/3', 'backup/sentReports', 'backup/settings']) {
    await db.doc(`users/${uid}/${path}`).set({ json: '{}' });
  }
}

describe('deleting an account', () => {
  test('deletes its backup, its unlock and any deletion waiting, and releases its purchase', async () => {
    const play = fakePlay({ [TOKEN]: paidBy(OWNER) });
    await unlock({ uid: OWNER, token: TOKEN, play, db, now: 5 });
    await backupOf(OWNER);
    await backupOf(OTHER);
    await db.doc(`deletions/${OWNER}`).set({ dueAt: 1 });

    assert.equal(await deleteAccount({ uid: OWNER, db, auth: null, now: 9 }), 5);

    assert.equal((await db.collection(`users/${OWNER}/trips`).get()).size, 0);
    assert.ok(!(await exists(`users/${OWNER}/backup/settings`)));
    assert.ok(!(await exists(`unlocks/${OWNER}`)));
    assert.ok(!(await exists(`deletions/${OWNER}`)));
    // Another account's backup is not touched.
    assert.equal((await db.collection(`users/${OTHER}/trips`).get()).size, 3);
    const purchases = await db.collection('purchases').get();
    assert.equal(purchases.docs[0].get('released'), true);
    assert.equal(purchases.docs[0].get('uid'), null);
  });

  test('lets the Google Play account that paid restore the purchase to a new account', async () => {
    const play = fakePlay({ [TOKEN]: paidBy(OWNER, { acknowledgementState: 1 }) });
    await unlock({ uid: OWNER, token: TOKEN, play, db, now: 5 });
    await deleteAccount({ uid: OWNER, db, auth: null, now: 9 });

    assert.deepEqual(await unlock({ uid: OTHER, token: TOKEN, play, db, now: 10 }), {
      unlocked: true,
    });
    assert.ok(await exists(`unlocks/${OTHER}`));
    // And it is that account's from then on: the first one cannot take it back.
    assert.equal(
      await rejection(unlock({ uid: OWNER, token: TOKEN, play, db, now: 11 })),
      'permission-denied/other-account',
    );
  });

  test('of an account that never bought anything deletes its backup and nothing else', async () => {
    await backupOf(OWNER);

    assert.equal(await deleteAccount({ uid: OWNER, db, auth: null, now: 9 }), 5);
    assert.equal((await db.collection('purchases').get()).size, 0);
  });
});

describe('the daily deletions', () => {
  test('delete the backups whose time has come, and only those', async () => {
    await backupOf(OWNER);
    await backupOf(OTHER);
    await db.doc(`deletions/${OWNER}`).set({ dueAt: 1000 });
    await db.doc(`deletions/${OTHER}`).set({ dueAt: 5000 });

    assert.equal(await runDeletions({ db, now: 2000 }), 1);

    assert.equal((await db.collection(`users/${OWNER}/trips`).get()).size, 0);
    assert.ok(!(await exists(`deletions/${OWNER}`)));
    assert.equal((await db.collection(`users/${OTHER}/trips`).get()).size, 3);
    assert.ok(await exists(`deletions/${OTHER}`));
  });
});
