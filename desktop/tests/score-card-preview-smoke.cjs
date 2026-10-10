const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const { chromium } = require('playwright');

const root = path.resolve(__dirname, '../..');
const staticRoot = path.join(root, 'src/main/resources/static');
const output = path.join(root, 'output/pdf');
const fixture = JSON.parse(fs.readFileSync(path.join(output, 'official-score-card-sample.json'), 'utf8'));
const mime = { '.html': 'text/html', '.css': 'text/css', '.js': 'application/javascript', '.png': 'image/png' };
let documentRequests = 0;
const server = http.createServer((request, response) => {
  const url = new URL(request.url, 'http://localhost');
  response.setHeader('Cache-Control', 'no-store');
  if (url.pathname === '/api/candidate/score-card-document') {
    documentRequests++; response.setHeader('Content-Type', 'application/json'); response.end(JSON.stringify(fixture)); return;
  }
  if (url.pathname.includes('/api/evaluation/') && url.pathname.includes('/score-card-document/')) {
    response.setHeader('Content-Type', 'application/json'); response.end(JSON.stringify(fixture)); return;
  }
  if (url.pathname.startsWith('/api/')) {
    response.setHeader('Content-Type', 'application/json');
    response.end(url.pathname.includes('security-token') ? '{"token":"qa"}' : '[]'); return;
  }
  const file = path.resolve(staticRoot, '.' + decodeURIComponent(url.pathname));
  if (!file.startsWith(staticRoot + path.sep) || !fs.existsSync(file)) { response.writeHead(404); response.end(); return; }
  response.setHeader('Content-Type', mime[path.extname(file)] || 'application/octet-stream');
  fs.createReadStream(file).pipe(response);
});

(async () => {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const origin = 'http://127.0.0.1:' + server.address().port;
  const browser = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true });
  try {
    for (const [name, viewport] of [['desktop', { width: 1440, height: 1100 }], ['mobile', { width: 390, height: 844 }]]) {
      const context = await browser.newContext({ viewport, acceptDownloads: true });
      await context.addInitScript(() => sessionStorage.setItem('candidateDashboardData', JSON.stringify({ registrationId: 'REG-2027-100384', scoreCardPdfReleased: true })));
      const page = await context.newPage();
      const errors = [];
      page.on('pageerror', error => errors.push(error.message));
      await page.goto(origin + '/candidate-result.html');
      await page.locator('#printScoreCard:enabled').waitFor();
      assert.equal(await page.locator('.score-preview-page').count(), fixture.pages.length);
      assert.equal(await page.locator('.score-preview-page').first().getAttribute('src'), fixture.pages[0]);
      assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1));
      await page.screenshot({ path: path.join(output, 'score-card-web-' + name + '.png'), fullPage: true });
      const requestsBeforeDownload = documentRequests;
      const [download] = await Promise.all([page.waitForEvent('download'), page.locator('#downloadScoreCardPdf').click()]);
      const downloaded = fs.readFileSync(await download.path());
      assert.ok(downloaded.equals(Buffer.from(fixture.pdfDataUrl.split(',')[1], 'base64')), 'Download must be the exact preview PDF');
      assert.equal(documentRequests, requestsBeforeDownload, 'Download must not regenerate a different document');
      await page.locator('#scorePreviewZoom').evaluate(input => { input.value = '200'; input.dispatchEvent(new Event('input', { bubbles: true })); });
      assert.ok(await page.locator('#sheet').evaluate(el => el.scrollWidth > el.clientWidth));
      assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1));
      if (name === 'desktop') await page.pdf({ path: path.join(output, 'score-card-browser-print.pdf'), preferCSSPageSize: true, printBackground: true });
      assert.deepEqual(errors, []);
      console.log(name + ': preview, exact PDF download, responsive width and zoom passed');
      await context.close();
    }
    const admin = await browser.newPage({ viewport: { width: 1280, height: 1000 } });
    await admin.route(/\/assets\/admin\/(admin-portal|admin-registration|admin-security)\.js/, route => route.fulfill({ contentType: 'application/javascript', body: '' }));
    await admin.goto(origin + '/admin-7xk92q-portal.html');
    await admin.evaluate(() => {
      document.body.replaceChildren(document.getElementById('examConfigForm'));
      const select = document.getElementById('configExamCode');
      select.add(new Option('Demo', 'EX2027DEMO', true, true));
      ScoreCardSettings.populate({ reportCardAppearance: { instituteName: 'Demo Institute', backgroundColor: '#f5f8f9', accentColor: '#153e50', stripColor: '#967344', stripsEnabled: false, watermarkOpacity: 9, notesTitle: 'Score notes', notesText: 'Saved notes.' } });
    });
    await admin.locator('#scoreNotesImageFile').setInputFiles({ name: 'score-note.svg', mimeType: 'image/svg+xml', buffer: Buffer.from('<svg xmlns="http://www.w3.org/2000/svg" width="120" height="80"><rect width="120" height="80" fill="#153e50"/></svg>') });
    const settings = await admin.evaluate(() => ScoreCardSettings.serialize());
    assert.equal(settings.instituteName, 'Demo Institute');
    assert.equal(settings.stripsEnabled, false);
    assert.equal(settings.watermarkOpacity, 9);
    assert.ok(settings.notesImageDataUrl.startsWith('data:image/png;base64,'));
    await admin.evaluate(settings => ScoreCardSettings.populate({ reportCardAppearance: settings }), settings);
    assert.equal(await admin.locator('#scoreNotesText').inputValue(), 'Saved notes.');
    await admin.locator('[data-clear-score-image="scoreNotesImage"]').click();
    assert.equal((await admin.evaluate(() => ScoreCardSettings.serialize())).notesImageDataUrl, '');
    await admin.locator('#scorePreviewApplication').fill('REG-2027-100384');
    await admin.locator('#scorePreviewButton').click();
    await admin.locator('#scorePreviewDialog[open]').waitFor();
    assert.equal(await admin.locator('#scorePreviewDownload').getAttribute('href'), fixture.pdfDataUrl);
    await admin.screenshot({ path: path.join(output, 'score-card-admin-preview.png') });
    await admin.close();
    console.log('admin: settings round-trip, SVG normalization, image removal and PDF preview passed');
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
})().catch(error => { console.error(error); server.close(); process.exitCode = 1; });
