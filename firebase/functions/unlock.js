// The purchase check of the cloud backup's one-time unlock (ADR-013), apart from Firebase and
// Google Play so that it is tested on the emulator with a stand-in for Google Play
// (firebase/test/unlock.test.js). index.js connects it to both.
//
// What it keeps, in Firestore, where no app can read or write (firebase/firestore.rules):
//   unlocks/{uid}          the account holds the unlock. The rules let it upload while this exists.
//   purchases/{tokenHash}  which account a purchase unlocked, so one purchase unlocks one
//                          account, and whether it was refunded.
//   deletions/{uid}        when the cloud copy of a refunded account is due to be deleted, 30 days
//                          after the refund (the deletion itself is slice 1e).

import { createHash } from 'node:crypto';

/** The app on Google Play. */
export const PACKAGE_NAME = 'top.dually.triptracker';

/** The one-time product in Play Console: the cloud backup, US$19.99. */
export const PRODUCT_ID = 'cloud_backup';

/** How long the cloud copy of a refunded account is kept (ADR-013). */
export const KEPT_AFTER_REFUND_MS = 30 * 24 * 60 * 60 * 1000;

/**
 * What the app sends Google Play with a purchase as its "obfuscated account id", and what the
 * check expects back: the SHA-256 of the Firebase account id, in hex. It ties a purchase to the
 * account that made it without Google Play seeing the account. The app computes the same
 * (data/cloud/CloudAccount.kt, cloudAccountTag).
 */
export function accountTag(uid) {
  return sha256(uid);
}

function sha256(text) {
  return createHash('sha256').update(text, 'utf8').digest('hex');
}

/** A refusal the app is told about, by its code and a reason it understands. */
export class UnlockError extends Error {
  constructor(code, reason) {
    super(reason);
    this.code = code;
    this.reason = reason;
  }
}

/**
 * Gives [uid] the unlock if [token] is a purchase of the backup that Google Play says is paid
 * for, made by this account, and not used by another one. Acknowledges it to Google Play, which
 * otherwise refunds a purchase after three days. Doing it twice is the same as once: that is the
 * "Restore purchase" button.
 *
 * @param play the Google Play Developer API, or a stand-in (play.js).
 * @param db Firestore, through the Admin SDK.
 * @param now the time, in milliseconds.
 */
export async function unlock({ uid, token, play, db, now }) {
  if (typeof token !== 'string' || token.length < 10 || token.length > 4096) {
    throw new UnlockError('invalid-argument', 'no-token');
  }
  const purchase = await play.getProduct(token);
  if (purchase.purchaseState === 2) throw new UnlockError('failed-precondition', 'pending');
  if (purchase.purchaseState !== 0) throw new UnlockError('failed-precondition', 'not-purchased');
  const boughtHere = purchase.obfuscatedExternalAccountId === accountTag(uid);
  const tokenHash = sha256(token);
  await db.runTransaction(async (transaction) => {
    const kept = db.doc(`purchases/${tokenHash}`);
    const known = await transaction.get(kept);
    // A purchase whose account was deleted is released (deleteAccount): whoever holds its
    // token, which only the Google Play account that paid for it has, may take it to a new
    // account. Otherwise it unlocks the account it was bought for, and only that one.
    const released = known.exists && known.get('released') === true;
    if (!boughtHere && !released) {
      throw new UnlockError('permission-denied', 'other-account');
    }
    if (known.exists && !released && known.get('uid') !== uid) {
      throw new UnlockError('permission-denied', 'other-account');
    }
    if (known.exists && known.get('refunded') === true) {
      throw new UnlockError('failed-precondition', 'refunded');
    }
    transaction.set(kept, {
      uid,
      orderId: purchase.orderId ?? null,
      refunded: false,
      released: false,
      at: now,
    });
    transaction.set(db.doc(`unlocks/${uid}`), {
      productId: PRODUCT_ID,
      orderId: purchase.orderId ?? null,
      purchaseTimeMs: Number(purchase.purchaseTimeMillis ?? 0),
      at: now,
    });
    transaction.delete(db.doc(`deletions/${uid}`));
  });
  if (purchase.acknowledgementState === 0) await play.acknowledge(token);
  return { unlocked: true };
}

/**
 * Takes the unlock away from every account whose purchase Google Play lists as refunded,
 * charged back or cancelled since [sinceMs], and marks its cloud copy for deletion 30 days after
 * the refund (ADR-013). Uploads stop at once: the rules no longer find the unlock. Run daily.
 *
 * @returns how many accounts lost their unlock this time.
 */
export async function revokeRefunded({ play, db, sinceMs }) {
  let revoked = 0;
  for (const voided of await play.voided(sinceMs)) {
    const tokenHash = sha256(voided.purchaseToken);
    const kept = db.doc(`purchases/${tokenHash}`);
    const changed = await db.runTransaction(async (transaction) => {
      const known = await transaction.get(kept);
      if (!known.exists || known.get('refunded') === true) return false;
      const uid = known.get('uid');
      const voidedAt = Number(voided.voidedTimeMillis ?? 0);
      transaction.update(kept, { refunded: true, refundedAt: voidedAt });
      transaction.delete(db.doc(`unlocks/${uid}`));
      transaction.set(db.doc(`deletions/${uid}`), { dueAt: voidedAt + KEPT_AFTER_REFUND_MS });
      return true;
    });
    if (changed) revoked++;
  }
  return revoked;
}

/**
 * Deletes an account at its own request (since 2026-11-12, ADR-012: both stores ask for a
 * deletion in the app): every document of its backup, its unlock and any deletion waiting for
 * it, and then the sign-in account itself. Its purchase is released, not forgotten, so that the
 * Google Play account that paid for it can restore it to a new account later (`unlock`).
 *
 * @param auth the Firebase Authentication admin, or null to leave the sign-in alone (tests).
 * @returns how many documents of the backup were deleted.
 */
export async function deleteAccount({ uid, db, auth, now }) {
  const backup = await countDocuments(db, `users/${uid}`);
  await db.recursiveDelete(db.doc(`users/${uid}`));
  const bought = await db.collection('purchases').where('uid', '==', uid).get();
  const batch = db.batch();
  for (const purchase of bought.docs) {
    batch.update(purchase.ref, { uid: null, released: true, releasedAt: now });
  }
  batch.delete(db.doc(`unlocks/${uid}`));
  batch.delete(db.doc(`deletions/${uid}`));
  await batch.commit();
  if (auth) {
    try {
      await auth.deleteUser(uid);
    } catch (error) {
      if (error?.code !== 'auth/user-not-found') throw error;
    }
  }
  return backup;
}

/**
 * Deletes the backups whose time has come: those of refunded purchases, 30 days after the
 * refund (`revokeRefunded` writes `deletions/{uid}`). Run once a day, after the refunds. An
 * account that bought the backup again in the meantime has no deletion left (`unlock`).
 *
 * @returns how many backups were deleted.
 */
export async function runDeletions({ db, now }) {
  const due = await db.collection('deletions').where('dueAt', '<=', now).get();
  for (const deletion of due.docs) {
    await db.recursiveDelete(db.doc(`users/${deletion.id}`));
    await deletion.ref.delete();
  }
  return due.size;
}

/** How many documents an account's backup holds, all folders together. */
async function countDocuments(db, path) {
  let count = 0;
  for (const folder of await db.doc(path).listCollections()) {
    count += (await folder.count().get()).data().count;
  }
  return count;
}
