// The purchase check of the cloud backup's one-time unlock (ADR-013), apart from Firebase and
// Google Play so that it is tested on the emulator with a stand-in for Google Play
// (firebase/test/unlock.test.js). index.js connects it to both.
//
// What it keeps, in Firestore, where no app can read or write (firebase/firestore.rules):
//   unlocks/{uid}          the account holds the unlock. The rules let it upload while this exists.
//   purchases/{tokenHash}  which account a purchase unlocked, so one purchase unlocks one
//                          account, and whether it was refunded. A Google Play purchase is kept
//                          under the SHA-256 of its token, an Apple one under that of "apple:"
//                          and its original transaction id (ADR-016).
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
  await grant({
    uid,
    db,
    now,
    tokenHash: sha256(token),
    boughtHere: purchase.obfuscatedExternalAccountId === accountTag(uid),
    record: { orderId: purchase.orderId ?? null },
    purchaseTimeMs: Number(purchase.purchaseTimeMillis ?? 0),
  });
  if (purchase.acknowledgementState === 0) await play.acknowledge(token);
  return { unlocked: true };
}

/**
 * What the iPhone sends Apple with a purchase as its `appAccountToken` (ADR-016), which must be
 * a UUID: the first 32 hex digits of the account's tag, written as one. The app computes the
 * same (`cloudAppleAccountToken`, shared/.../data/cloud/CloudAccountTag.kt; both sides test one value: CloudAccountTagTest).
 */
export function appleAccountToken(uid) {
  const hex = accountTag(uid);
  return [hex.slice(0, 8), hex.slice(8, 12), hex.slice(12, 16), hex.slice(16, 20), hex.slice(20, 32)]
    .join('-');
}

/** The key a purchase made on the iPhone is recorded under: one per Apple purchase, ever. */
export function applePurchaseKey(originalTransactionId) {
  return sha256(`apple:${originalTransactionId}`);
}

/**
 * The iPhone's [unlock] (ADR-016): gives [uid] the unlock if [signed], a transaction StoreKit 2
 * handed the app, is Apple's, of the backup, not refunded, made by this account, and not used by
 * another. Apple needs no acknowledgement; the app finishes the transaction itself.
 *
 * @param appStore Apple's App Store, or a stand-in (apple.js).
 */
export async function unlockApple({ uid, signed, appStore, db, now }) {
  if (typeof signed !== 'string' || signed.length < 10 || signed.length > 20000) {
    throw new UnlockError('invalid-argument', 'no-token');
  }
  let purchase;
  try {
    purchase = await appStore.transaction(signed);
  } catch (error) {
    // Apple's certificate check could not be made (its revocation list was out of reach): try
    // again later. Anything else is not a purchase Apple signed.
    if (error?.status === 2) throw error;
    throw new UnlockError('permission-denied', 'not-apple');
  }
  if (purchase.bundleId !== PACKAGE_NAME || purchase.productId !== PRODUCT_ID) {
    throw new UnlockError('failed-precondition', 'not-purchased');
  }
  const tokenHash = applePurchaseKey(purchase.originalTransactionId);
  if (purchase.revocationDate) {
    // Apple refunded it; a notification may not have come. Taken as a refund here and now.
    await revokePurchase({ db, tokenHash, voidedAt: Number(purchase.revocationDate) });
    throw new UnlockError('failed-precondition', 'refunded');
  }
  await grant({
    uid,
    db,
    now,
    tokenHash,
    boughtHere: purchase.appAccountToken === appleAccountToken(uid),
    record: {
      store: 'apple',
      environment: purchase.environment ?? null,
      orderId: purchase.originalTransactionId ?? null,
    },
    purchaseTimeMs: Number(purchase.originalPurchaseDate ?? purchase.purchaseDate ?? 0),
  });
  return { unlocked: true };
}

/**
 * Apple's notification (ADR-016): a refund or a revocation of a purchase of the backup takes the
 * unlock away, as Google Play's voided purchases do in [revokeRefunded]. Anything else changes
 * nothing.
 *
 * @returns whether an unlock was taken away.
 */
export async function appleNotified({ signed, appStore, db }) {
  const { type, transaction } = await appStore.notification(signed);
  if (type !== 'REFUND' && type !== 'REVOKE') return false;
  if (!transaction || transaction.productId !== PRODUCT_ID) return false;
  return revokePurchase({
    db,
    tokenHash: applePurchaseKey(transaction.originalTransactionId),
    voidedAt: Number(transaction.revocationDate ?? Date.now()),
  });
}

/**
 * Records the purchase under [tokenHash] for [uid] and gives the account the unlock, in one
 * transaction, unless the purchase is another account's or refunded.
 *
 * @param boughtHere whether the purchase carries this account's tag.
 * @param record what is kept about the purchase beside the account it unlocked.
 */
async function grant({ uid, db, now, tokenHash, boughtHere, record, purchaseTimeMs }) {
  await db.runTransaction(async (transaction) => {
    const kept = db.doc(`purchases/${tokenHash}`);
    const known = await transaction.get(kept);
    // A purchase whose account was deleted is released (deleteAccount): whoever holds its
    // token, which only the store account that paid for it has, may take it to a new account.
    // Otherwise it unlocks the account it was bought for, and only that one.
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
    transaction.set(kept, { ...record, uid, refunded: false, released: false, at: now });
    transaction.set(db.doc(`unlocks/${uid}`), {
      productId: PRODUCT_ID,
      orderId: record.orderId,
      purchaseTimeMs,
      at: now,
    });
    transaction.delete(db.doc(`deletions/${uid}`));
  });
}

/**
 * Marks the purchase under [tokenHash] refunded, takes its account's unlock away and writes the
 * deletion of its cloud copy, 30 days after [voidedAt]. A purchase not known, or refunded
 * already, changes nothing.
 *
 * @returns whether anything changed.
 */
async function revokePurchase({ db, tokenHash, voidedAt }) {
  const kept = db.doc(`purchases/${tokenHash}`);
  return db.runTransaction(async (transaction) => {
    const known = await transaction.get(kept);
    if (!known.exists || known.get('refunded') === true) return false;
    const uid = known.get('uid');
    transaction.update(kept, { refunded: true, refundedAt: voidedAt });
    if (uid) {
      transaction.delete(db.doc(`unlocks/${uid}`));
      transaction.set(db.doc(`deletions/${uid}`), { dueAt: voidedAt + KEPT_AFTER_REFUND_MS });
    }
    return true;
  });
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
    const changed = await revokePurchase({
      db,
      tokenHash: sha256(voided.purchaseToken),
      voidedAt: Number(voided.voidedTimeMillis ?? 0),
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
