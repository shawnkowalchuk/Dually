// The Cloud Functions of Dually's cloud backup (ADR-013), in Montréal like its Firestore.
// The work is in unlock.js; this connects it to Firebase and to Google Play.

import { initializeApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getFirestore } from 'firebase-admin/firestore';
import { logger, setGlobalOptions } from 'firebase-functions/v2';
import { HttpsError, onCall } from 'firebase-functions/v2/https';
import { onSchedule } from 'firebase-functions/v2/scheduler';
import { googlePlay } from './play.js';
import { deleteAccount, revokeRefunded, runDeletions, unlock, UnlockError } from './unlock.js';

// The functions run as their own service account, which may read and write Firestore (Cloud
// Datastore User) and, in Play Console, view Dually's orders: nothing else (firebase/README.md).
setGlobalOptions({
  region: 'northamerica-northeast1',
  maxInstances: 3,
  serviceAccount: 'purchase-check@dually-trip-tracker.iam.gserviceaccount.com',
});
initializeApp();
const db = getFirestore();
const play = googlePlay();

/** The longest Google Play takes to list a refund, and a day more. */
const REFUND_LOOK_BACK_MS = 31 * 24 * 60 * 60 * 1000;

/**
 * Called by the app after a purchase, and by "Restore purchase", with the purchase's token.
 * Answers { unlocked: true }, or an error whose message is the reason (unlock.js).
 *
 * `invoker: 'public'` lets the call reach the function at all; who may use it is checked
 * inside, by the Firebase sign-in on the call (`request.auth`). Without it a function that was
 * not created by a clean first deploy turns every caller away with a 403 (2026-11-12).
 */
export const unlockBackup = onCall({ invoker: 'public' }, async (request) => {
  if (!request.auth) throw new HttpsError('unauthenticated', 'not-signed-in');
  try {
    return await unlock({
      uid: request.auth.uid,
      token: request.data?.purchaseToken,
      play,
      db,
      now: Date.now(),
    });
  } catch (error) {
    if (error instanceof UnlockError) throw new HttpsError(error.code, error.reason);
    logger.error('The purchase check failed', error);
    throw new HttpsError('unavailable', 'play-unreachable');
  }
});

/** Once a day: refunds take the unlock away (ADR-013). */
export const checkRefunds = onSchedule(
  { schedule: 'every day 04:00', timeZone: 'America/Edmonton' },
  async () => {
    const revoked = await revokeRefunded({
      play,
      db,
      sinceMs: Date.now() - REFUND_LOOK_BACK_MS,
    });
    logger.info(`Refunds: ${revoked} unlock(s) taken away.`);
    // Then the backups of refunds 30 days old (ADR-013), in the same daily run.
    const deleted = await runDeletions({ db, now: Date.now() });
    logger.info(`Deletions: ${deleted} backup(s) deleted.`);
  },
);

/**
 * "Delete my online backup and account", from the app (since 2026-11-12): deletes the backup,
 * the unlock and the sign-in account of whoever calls it, and releases their purchase.
 */
export const deleteMyAccount = onCall({ invoker: 'public' }, async (request) => {
  if (!request.auth) throw new HttpsError('unauthenticated', 'not-signed-in');
  try {
    const deleted = await deleteAccount({
      uid: request.auth.uid,
      db,
      auth: getAuth(),
      now: Date.now(),
    });
    return { deleted };
  } catch (error) {
    logger.error('Deleting an account failed', error);
    throw new HttpsError('unavailable', 'delete-failed');
  }
});
