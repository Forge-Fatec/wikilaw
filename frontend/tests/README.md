# Testes end-to-end

Execute os comandos abaixo a partir da pasta `frontend`.

```bash
# Roda todos os testes em modo headless contra o frontend local.
npm run test:e2e

# Roda os testes mostrando a janela do navegador.
npm run test:e2e -- --headed

# Roda somente no Chromium e mantém apenas um worker.
npm run test:e2e -- --project=chromium --workers=1

# Roda somente os testes cujo título contém o texto informado.
npm run test:e2e -- --grep "filters documents"

# Abre a interface interativa do Playwright.
npm run test:e2e -- --ui

# Abre o inspector e pausa a execução para depuração.
PWDEBUG=1 npm run test:e2e -- --project=chromium --workers=1

# Executa os mesmos testes contra um frontend hospedado.
E2E_BASE_URL=https://frontend.exemplo.com npm run test:e2e

# Remove as screenshots geradas, preservando o arquivo que mantém a pasta no Git.
find tests/screenshots -type f ! -name '.gitignore' -delete
```

Quando um teste falha, o helper salva automaticamente uma captura de página inteira em
`tests/screenshots`. As imagens geradas são ignoradas pelo Git.
