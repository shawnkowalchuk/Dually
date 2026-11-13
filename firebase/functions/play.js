// Google Play's Developer API, as the purchase check uses it (unlock.js). It runs as the Cloud
// Functions' own service account, which Play Console lets view orders (firebase/README.md).

import { androidpublisher, auth } from '@googleapis/androidpublisher';
import { PACKAGE_NAME, PRODUCT_ID } from './unlock.js';

export function googlePlay() {
  const api = androidpublisher({
    version: 'v3',
    auth: new auth.GoogleAuth({ scopes: ['https://www.googleapis.com/auth/androidpublisher'] }),
  });
  const product = { packageName: PACKAGE_NAME, productId: PRODUCT_ID };
  return {
    async getProduct(token) {
      return (await api.purchases.products.get({ ...product, token })).data;
    },
    async acknowledge(token) {
      await api.purchases.products.acknowledge({ ...product, token, requestBody: {} });
    },
    async voided(sinceMs) {
      const all = [];
      let pageToken;
      do {
        const page = await api.purchases.voidedpurchases.list({
          packageName: PACKAGE_NAME,
          startTime: String(sinceMs),
          maxResults: 1000,
          pageToken,
        });
        all.push(...(page.data.voidedPurchases ?? []));
        pageToken = page.data.tokenPagination?.nextPageToken;
      } while (pageToken);
      return all;
    },
  };
}
