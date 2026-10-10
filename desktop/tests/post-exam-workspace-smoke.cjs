const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const root = path.resolve(__dirname, '../..');
const staticRoot = path.join(root, 'src/main/resources/static');
const output = path.join(root, 'output/post-exam');
const exam = {
  examCode: 'EX2027DEMO', name: 'Engineering Aptitude Examination 2027', mode: 'PHYSICAL_CENTER', durationMinutes: 180, active: true,
  registrationOpen: false, correctionsOpen: false, streamSelectionRequired: false, streams: [], customFields: [],
  enabledFields: ['FULL_NAME', 'DATE_OF_BIRTH', 'EMAIL', 'PHONE', 'PASSWORD'],
  originalQuestionPaperPdfUploaded: true, originalQuestionPaperPdfName: 'Question-paper.pdf',
  originalAnswerKeyPdfUploaded: true, originalAnswerKeyPdfName: 'Answer-key.pdf',
  responsesReleased: true, resultReleased: false, scoreCardPdfReleased: false,
  originalQuestionPaperReleased: true, originalAnswerKeyReleased: true,
  answerKeyChallengeEnabled: true, answerKeyChallengeOpensAt: '2026-10-05T03:30:00Z', answerKeyChallengeClosesAt: '2026-10-10T11:30:00Z'
};
const candidates = [
  { registrationId: 'DEMO2027001384', name: 'Aarav S. Menon', examCode: exam.examCode, withheld: false },
  { registrationId: 'DEMO2027001385', name: 'Ananya Rao', examCode: exam.examCode, withheld: true },
  { registrationId: 'DEMO2027001386', name: 'Vikram Singh', examCode: exam.examCode, withheld: false }
];
let publicationPayload = null;
const history = [];
const jsonBody = async request => { let body = ''; for await (const chunk of request) body += chunk; return JSON.parse(body); };
const server = http.createServer(async (request, response) => {
  const url = new URL(request.url, 'http://localhost');
  response.setHeader('Cache-Control', 'no-store');
  const json = data => { response.setHeader('Content-Type', 'application/json'); response.end(JSON.stringify(data)); };
  if (url.pathname.endsWith('/csrf-token')) return json({ token: 'test-only' });
  if (url.pathname.endsWith('/post-exam-publication') && request.method === 'PATCH') { publicationPayload = await jsonBody(request); Object.assign(exam, publicationPayload); return json(exam); }
  if (url.pathname.endsWith('/registration-config') && request.method === 'PATCH') { Object.assign(exam, await jsonBody(request)); return json(exam); }
  if (url.pathname === '/api/exams') return json(url.searchParams.has('page') ? { content: [exam], totalElements: 1, totalPages: 1, page: 0 } : [exam]);
  if (url.pathname === '/api/master-admin/result-controls') {
    let rows = candidates.filter(row => (!url.searchParams.get('exam') || row.examCode === url.searchParams.get('exam')) && (url.searchParams.get('heldOnly') !== 'true' || row.withheld));
    const search = (url.searchParams.get('search') || '').toLowerCase();
    rows = rows.filter(row => (row.registrationId + row.name).toLowerCase().includes(search));
    return json({ content: rows, totalElements: rows.length, totalPages: rows.length ? 1 : 0, page: 0 });
  }
  if (url.pathname.startsWith('/api/master-admin/result-controls/')) {
    if (url.pathname.endsWith('/history')) return json(history);
    const row = candidates.find(row => row.registrationId === url.pathname.split('/').pop());
    const body = await jsonBody(request); assert.equal(body.expectedWithheld, row.withheld);
    row.withheld = body.withheld; history.unshift({ ...body, actor: 'master@example.test', decidedAt: new Date().toISOString() }); return json(row);
  }
  if (url.pathname.endsWith('/answer-key')) return json({ uploaded: true, originalName: 'Evaluation-key.json', maxMarks: 100 });
  if (url.pathname.endsWith('/results')) return json([]);
  if (url.pathname.startsWith('/api/')) return json(url.searchParams.has('page') ? { content: [], totalElements: 0, totalPages: 0, page: 0 } : []);
  const mapping = { '/console/c7e42a9d51f8603b7a24/master': '/master-admin.html', '/console/8f3a91d6c42e7b5a0d19/admin': '/admin-7xk92q-portal.html' };
  const file = path.resolve(staticRoot, '.' + (mapping[url.pathname] || url.pathname));
  if (!file.startsWith(staticRoot + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) { response.writeHead(404); response.end(); return; }
  response.setHeader('Content-Type', ({ '.html': 'text/html', '.js': 'application/javascript', '.css': 'text/css', '.png': 'image/png' })[path.extname(file)] || 'application/octet-stream');
  fs.createReadStream(file).pipe(response);
});
async function smoke(origin) {
  const { chromium } = require('playwright');
  const browser = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true });
  fs.mkdirSync(output, { recursive: true });
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
    const errors = []; page.on('pageerror', error => errors.push(error.message));
    page.on('dialog', dialog => dialog.accept());
    await page.goto(origin + '/admin-7xk92q-portal.html#post-exam');
    await page.locator('#responseCenterCard.active').waitFor();
    for (const tab of ['responsesTab', 'evaluationTab', 'postDocumentsTab', 'postChallengesTab', 'postPublicationTab']) {
      await page.locator('[data-response-tab="' + tab + '"]').click();
      assert.ok(await page.locator('#' + tab).isVisible());
      assert.equal(await page.locator('.response-tab-panel.active').count(), 1);
      await page.screenshot({ path: path.join(output, tab + '-desktop.png'), fullPage: true });
    }
    assert.equal(await page.locator('#resultReleased').evaluate(el => el.form.id), 'examConfigForm');
    await page.locator('[data-response-tab="postDocumentsTab"]').click();
    for (const [input, button, endpoint] of [['originalQuestionPaperPdfFile', 'uploadOriginalQuestionPaperPdf', 'original-question-paper-pdf'], ['originalAnswerKeyPdfFile', 'uploadOriginalAnswerKeyPdf', 'original-answer-key-pdf']]) {
      await page.locator('#' + input).setInputFiles({ name: 'sample.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4 test fixture') });
      const sent = page.waitForRequest(request => request.url().endsWith('/' + endpoint) && request.method() === 'POST');
      await page.locator('#' + button).click(); await sent;
      await page.waitForFunction(id => document.getElementById(id).files.length === 0, input);
    }
    await page.locator('[data-response-tab="postPublicationTab"]').click();
    await page.locator('#resultReleased').check(); await page.locator('#scoreCardPdfReleased').check();
    await page.locator('#savePostPublication').click();
    await page.locator('#postPublicationStatus').filter({ hasText: 'Publication settings saved' }).waitFor();
    assert.equal(publicationPayload.resultReleased, true);
    assert.equal(publicationPayload.scoreCardPdfReleased, true);
    assert.equal(Object.keys(publicationPayload).length, 8);
    assert.equal(publicationPayload.registrationOpen, undefined);
    await page.locator('#themeToggle').click();
    await page.screenshot({ path: path.join(output, 'publication-dark.png'), fullPage: true });
    await page.locator('#themeToggle').click();
    await page.reload(); await page.locator('#responseCenterCard.active').waitFor();
    await page.locator('[data-response-tab="postPublicationTab"]').click();
    assert.ok(await page.locator('#resultReleased').isChecked());
    for (const width of [390, 320]) {
      await page.setViewportSize({ width, height: 844 });
      assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), 'Admin mobile overflow at ' + width);
      await page.screenshot({ path: path.join(output, 'publication-' + width + '.png'), fullPage: true });
    }
    await page.setViewportSize({ width: 1440, height: 1000 });
    await page.goto(origin + '/master-admin.html#results');
    await page.locator('#holdRows tr').filter({ hasText: candidates[0].name }).waitFor();
    await page.screenshot({ path: path.join(output, 'master-result-controls.png'), fullPage: true });
    await page.locator('[data-decision="0"]').click();
    await page.locator('#holdReason').fill('Identity verification pending');
    await page.screenshot({ path: path.join(output, 'withhold-confirmation.png'), fullPage: true });
    await page.locator('#holdConfirm').click();
    await page.locator('#holdDialog').waitFor({ state: 'hidden' });
    assert.equal(candidates[0].withheld, true);
    await page.locator('[data-history="0"]').click();
    await page.locator('#holdHistoryBody').filter({ hasText: 'Identity verification pending' }).waitFor();
    await page.locator('#holdHistoryClose').click();
    await page.locator('[data-decision="0"]').click();
    await page.locator('#holdReason').fill('Identity review completed'); await page.locator('#holdConfirm').click();
    await page.locator('#holdDialog').waitFor({ state: 'hidden' }); assert.equal(candidates[0].withheld, false);
    await page.locator('#holdOnly').check(); await page.locator('#resultControlSearch button').click();
    await page.locator('#holdStatus').filter({ hasText: '1 candidate(s)' }).waitFor();
    assert.equal(await page.locator('#holdRows [data-decision]').count(), 1);
    await page.locator('#holdSearch').fill('MISSING'); await page.locator('#resultControlSearch button').click();
    await page.locator('#holdRows').filter({ hasText: 'No matching candidates' }).waitFor();
    await page.locator('#holdSearch').fill(''); await page.locator('#holdOnly').uncheck(); await page.locator('#resultControlSearch button').click();
    await page.locator('#holdStatus').filter({ hasText: '3 candidate(s)' }).waitFor();
    for (const width of [390, 320]) {
      await page.setViewportSize({ width, height: 844 });
      assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), 'Master mobile overflow at ' + width);
      await page.screenshot({ path: path.join(output, 'master-results-' + width + '.png'), fullPage: true });
    }
    await page.locator('[data-decision="0"]').click();
    assert.ok(await page.locator('#holdDialog').evaluate(el => el.scrollWidth <= el.clientWidth + 1));
    await page.screenshot({ path: path.join(output, 'withhold-mobile.png'), fullPage: true });
    await page.locator('#holdCancel').click();
    await page.route('**/api/master-admin/result-controls?*', route => route.fulfill({ status: 503, json: { detail: 'Service temporarily unavailable' } }));
    await page.locator('#resultControlSearch button').click();
    await page.locator('#holdStatus').filter({ hasText: 'Service temporarily unavailable' }).waitFor();
    assert.deepEqual(errors, []);
    console.log('Post-exam tabs, narrow publication payload, reload, withholding/restoration, audit, filters, empty state and desktop/mobile layouts passed.');
  } finally { await browser.close(); }
}
server.listen(process.argv.includes('--serve') ? 8093 : 0, '127.0.0.1', async () => {
  const origin = 'http://127.0.0.1:' + server.address().port;
  if (process.argv.includes('--serve')) { console.log('Demo post-exam workspace: ' + origin + '/admin-7xk92q-portal.html#post-exam'); return; }
  try { await smoke(origin); } catch (error) { console.error(error); process.exitCode = 1; } finally { server.close(); }
});
