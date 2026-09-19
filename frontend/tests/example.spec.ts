import { expect, test } from './test-helper';

test('shows the legal research page', async ({ page }) => {
  await page.goto('/');

  await expect(page).toHaveTitle(/Lumen Juris/);
  await expect(
    page.getByRole('heading', { name: /Descreva o seu caso/ }),
  ).toBeVisible();
  await expect(page.getByText('4 documentos encontrados')).toBeVisible();
});

test('filters documents by type', async ({ page }) => {
  await page.goto('/');

  await page.getByRole('checkbox', { name: 'Doutrina' }).uncheck();

  await expect(page.getByText('3 documentos encontrados')).toBeVisible();
  await expect(
    page.getByRole('heading', {
      name: 'Elementos da responsabilidade civil objetiva',
    }),
  ).not.toBeVisible();
});
