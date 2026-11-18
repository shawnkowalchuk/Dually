// Apple's App Store, as the purchase check sees it (ADR-016): what a purchase made on the iPhone
// is, read from the signed transaction StoreKit 2 hands the app, and what Apple's notifications
// say. Nothing Apple sends is believed until its signature and its certificate chain, up to
// Apple Root CA - G3, check out; Apple's own library does the checking. No key is needed for it.
// unlock.js decides what a purchase unlocks; this only reads it.

import { readFileSync } from 'node:fs';
import {
  Environment,
  SignedDataVerifier,
  VerificationException,
  VerificationStatus,
} from '@apple/app-store-server-library';
import { PACKAGE_NAME } from './unlock.js';

/** The app's number in App Store Connect, which Apple's production signatures name. */
export const APP_APPLE_ID = 6834196352;

/**
 * Apple Root CA - G3, from https://www.apple.com/certificateauthority/ (2026-11-14). Its SHA-256
 * is 63:34:3A:BF:B8:9A:6A:03:EB:B5:7E:9B:3F:5F:A7:BE:7C:4F:5C:75:6F:30:17:B3:A8:C4:88:C3:65:3E:91:79.
 */
const ROOT = readFileSync(new URL('./apple/AppleRootCA-G3.cer', import.meta.url));

/**
 * The App Store, for the purchase check: the production store first, then the sandbox
 * (TestFlight and Apple's test accounts), each with its own verifier, since Apple signs each
 * one's data for that environment only.
 */
export function appStore() {
  const verifiers = [
    new SignedDataVerifier([ROOT], true, Environment.PRODUCTION, PACKAGE_NAME, APP_APPLE_ID),
    new SignedDataVerifier([ROOT], true, Environment.SANDBOX, PACKAGE_NAME),
  ];
  return {
    /** The purchase a signed transaction describes, or an error if it is not Apple's. */
    transaction: (signed) => firstThatVerifies(verifiers, (v) => v.verifyAndDecodeTransaction(signed)),
    /** What one of Apple's notifications says, with its transaction read too. */
    async notification(signed) {
      const payload = await firstThatVerifies(verifiers, (v) => v.verifyAndDecodeNotification(signed));
      const inner = payload.data?.signedTransactionInfo;
      const transaction = inner
        ? await firstThatVerifies(verifiers, (v) => v.verifyAndDecodeTransaction(inner))
        : null;
      return { type: payload.notificationType, transaction };
    },
  };
}

/** The first verifier's answer whose environment it is; any other failure stops at once. */
async function firstThatVerifies(verifiers, read) {
  let last;
  for (const verifier of verifiers) {
    try {
      return await read(verifier);
    } catch (error) {
      last = error;
      const otherEnvironment =
        error instanceof VerificationException &&
        error.status === VerificationStatus.INVALID_ENVIRONMENT;
      if (!otherEnvironment) break;
    }
  }
  throw last;
}
