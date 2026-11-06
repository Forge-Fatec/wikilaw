import { expect, test } from '@playwright/test'
import { abrirFrontend } from './helpers/test-helper'

test('exibe a opção de anexar documento ao caso', async ({ page }) => {
  await abrirFrontend(page)

  const formulario = page.locator('.hero')
  await expect(formulario.getByRole('button', { name: 'Anexar documento' })).toBeVisible()
  await expect(formulario.getByLabel('Selecionar documento do caso')).toBeAttached()
})

for (const { nome, formato, mimeType } of [
  { nome: 'caso.pdf', formato: 'PDF', mimeType: 'application/pdf' },
  { nome: 'caso.docx', formato: 'DOCX', mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' },
]) {
  test(`permite anexar um ${formato} simulado`, async ({ page }) => {
    const conteudo = 'arquivo simulado'
    const textoExtraido = 'Texto do caso teste'
    const requisicoes: string[] = []
    await page.route('**/api/pesquisas/documentos', route => {
      requisicoes.push(route.request().postData() ?? '')
      return route.fulfill({ json: {
        nome, formato, tamanhoBytes: Buffer.byteLength(conteudo), caracteres: textoExtraido.length,
        paginas: formato === 'PDF' ? 1 : null, texto: textoExtraido,
      } })
    })

    await abrirFrontend(page)
    await page.locator('.hero').getByLabel('Selecionar documento do caso').setInputFiles({
      name: nome, mimeType, buffer: Buffer.from(conteudo),
    })

    await expect(page.locator('.hero .case-document-card')).toContainText(nome)
    expect(requisicoes).toHaveLength(1)
    expect(requisicoes[0]).toContain(`filename="${nome}"`)
    expect(requisicoes[0]).toContain(conteudo)
  })
}

test('recusa um formato não suportado sem enviar o arquivo', async ({ page }) => {
  let requisicoes = 0
  await page.route('**/api/pesquisas/documentos', route => {
    requisicoes += 1
    return route.fulfill({ status: 500 })
  })

  await abrirFrontend(page)
  await page.locator('.hero').getByLabel('Selecionar documento do caso').setInputFiles({
    name: 'caso.txt', mimeType: 'text/plain', buffer: Buffer.from('arquivo simulado'),
  })

  await expect(page.locator('.hero .case-document-error')).toHaveText(
    'Formato não suportado. Envie um arquivo PDF ou DOCX.',
  )
  expect(requisicoes).toBe(0)
})
