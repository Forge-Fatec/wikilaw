import { expect, test as base } from '@playwright/test';
import { mkdir } from 'node:fs/promises';
import path from 'node:path';

const LOCAL_BASE_URL = 'http://localhost:3000';
const SCREENSHOTS_DIR = path.resolve(import.meta.dirname, 'screenshots');

function getBaseURL() {
  const baseURL = process.env.E2E_BASE_URL?.trim() || LOCAL_BASE_URL;

  try {
    const url = new URL(baseURL);

    if (url.protocol !== 'http:' && url.protocol !== 'https:') {
      throw new Error('the protocol must be http or https');
    }

    return url.toString();
  } catch (error) {
    const reason = error instanceof Error ? error.message : String(error);

    throw new Error(`Invalid E2E_BASE_URL "${baseURL}": ${reason}`, {
      cause: error,
    });
  }
}

const test = base.extend({
  baseURL: getBaseURL(),
});

test.afterEach(async ({ page }, testInfo) => {
  const testFailed = testInfo.status !== testInfo.expectedStatus;

  if (!testFailed || page.isClosed()) {
    return;
  }

  const safeTitle = testInfo.title
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-zA-Z0-9]+/g, '-')
    .replace(/^-|-$/g, '')
    .toLowerCase();
  const fileName = [
    testInfo.project.name,
    safeTitle || 'failed-test',
    `retry-${testInfo.retry}`,
  ].join('-');

  await mkdir(SCREENSHOTS_DIR, { recursive: true });
  await page.screenshot({
    fullPage: true,
    path: path.join(SCREENSHOTS_DIR, `${fileName}.png`),
  });
});

export { expect, test };
