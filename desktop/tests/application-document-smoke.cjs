const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const { execFileSync } = require('node:child_process');
const root = path.resolve(__dirname, '../..');
const staticRoot = path.join(root, 'src/main/resources/static');
const output = path.join(root, 'output/pdf');
const poppler = path.join(process.env.USERPROFILE, '.cache/codex-runtimes/codex-primary-runtime/dependencies/native/poppler/Library/bin');
const python = path.join(process.env.USERPROFILE, '.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe');
const pdfText = file => execFileSync(python, ['-c', "import sys; from pypdf import PdfReader; sys.stdout.reconfigure(encoding='utf-8'); print('\\n'.join(p.extract_text() for p in PdfReader(sys.argv[1]).pages))", file], { encoding: 'utf8' });
const candidate = {
  registrationId: 'DEMO2027001384', examCode: 'DEMO27', examName: 'Graduate Engineering Examination 2027',
  fullName: 'Aarav S. Menon', dateOfBirth: '2001-08-14', createdAt: '2026-09-15T10:00:00Z', streamCode: 'EC',
  gender: 'Male', nationality: 'Indian', email: 'aarav.menon@example.test', alternateEmail: 'aarav.study@example.test',
  phone: '+91 90000 12345', address: 'Apartment 24, Research Park Road\nAdyar, Chennai', state: 'Tamil Nadu', pincode: '600020',
  parentName: 'S. Menon', parentPhone: '+91 90000 23456', guardianName: 'R. Menon', guardianPhone: '+91 90000 34567', guardianRelation: 'Parent',
  ugDetails: 'B.Tech, Electronics and Communication Engineering\nExample Institute of Technology, 2023 | CGPA: 8.70 / 10',
  pgDetails: 'Not applicable', photoUploaded: true, signatureUploaded: true, photoPath: 'photo.png', signaturePath: 'signature.png',
  idCardUploaded: true, class10MarksheetUploaded: true, class12MarksheetUploaded: true, ugDocumentUploaded: true, pgDocumentUploaded: false,
  customFieldValues: { accessibility: 'No accommodation requested.', project: 'Low-power sensor networks.' },
  customFilePaths: { category_certificate: 'private/path/certificate.pdf' },
  responsesReleased: true, resultReleased: true, scoreCardPdfReleased: true, responseSubmitted: true,
  originalAnswerKeyReleased: true, originalQuestionPaperReleased: true, answerKeyChallengeEnabled: true,
  answerKeyChallengeOpensAt: '2026-09-30T03:30:00Z', answerKeyChallengeClosesAt: '2027-10-03T11:30:00Z',
  allowedCountryIso2s: ['IN'], candidateCorrectionFields: {}
};
let current = structuredClone(candidate);
let photo = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aWQAAAABJRU5ErkJggg==';
let signature = photo;
const samplePhoto = path.join(output, 'candidate-photo-fixture.png');
const sampleSignature = path.join(output, 'candidate-signature-fixture.png');
if (fs.existsSync(samplePhoto)) photo = 'data:image/png;base64,' + fs.readFileSync(samplePhoto).toString('base64');
if (fs.existsSync(sampleSignature)) signature = 'data:image/png;base64,' + fs.readFileSync(sampleSignature).toString('base64');
let failImages = false;
const preview = process.argv.includes('--serve');
const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost');
  const json = data => { res.setHeader('Content-Type', 'application/json'); res.end(JSON.stringify(data)); };
  res.setHeader('Cache-Control', 'no-store');
  if (url.pathname === '/api/public/exams' || url.pathname === '/api/public/exams/open') return json([{
    examCode: 'DEMO27', name: current.examName, registrationOpen: true, streams: [], enabledFields: ['FULL_NAME','EMAIL','PHONE','DATE_OF_BIRTH','PASSWORD'],
    portalNotices: [
      { text: 'Candidate score cards are now available.', isNew: true, publishedOn: '2026-10-07' },
      { text: 'Answer-key challenge window is open.', isNew: true, publishedOn: '2026-10-06' },
      { text: 'Examination registration has closed.', isNew: false, publishedOn: '2026-09-30' }
    ],
    customFields: [{ key: 'accessibility', label: 'Accessibility Requirements', type: 'TEXT' }, { key: 'project', label: 'Research Project', type: 'TEXT' }, { key: 'category_certificate', label: 'Category Certificate', type: 'PDF_UPLOAD' }]
  }]);
  if (url.pathname === '/api/candidate/file') {
    if (failImages) { res.statusCode = 503; return json({ message: 'Image service unavailable' }); }
    let body = ''; for await (const chunk of req) body += chunk;
    const payload = JSON.parse(body);
    return json({ dataUrl: payload.path === 'photo.png' ? photo : signature });
  }
  if (url.pathname.endsWith('security-token')) return json({ token: 'qa' });
  if (url.pathname === '/api/candidate/dashboard') return json(current);
  if (url.pathname === '/api/candidate/responses') return json({ answers: [] });
  if (url.pathname.startsWith('/api/')) return json([]);
  const pathname = url.pathname === '/' ? '/candidate-details.html' : url.pathname;
  const file = path.resolve(staticRoot, '.' + pathname);
  if (!file.startsWith(staticRoot + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) { res.writeHead(404); res.end(); return; }
  const mime = { '.html': 'text/html', '.js': 'application/javascript', '.css': 'text/css', '.gif': 'image/gif' };
  res.setHeader('Content-Type', mime[path.extname(file)] || 'application/octet-stream');
  if (preview && pathname === '/candidate-details.html') {
    const init = '<script>sessionStorage.setItem("candidateDashboardData",' + JSON.stringify(JSON.stringify(current)) + ');</script>';
    res.end(fs.readFileSync(file, 'utf8').replace('<head>', '<head>' + init)); return;
  }
  fs.createReadStream(file).pipe(res);
});

async function run(origin) {
  const { chromium } = require('playwright');
  const browser = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true });
  fs.mkdirSync(output, { recursive: true });
  for (const file of fs.readdirSync(output)) {
    if (/^application-(?:record-print-(?:desktop|mobile)|long-print)-\d+\.png$/.test(file)) fs.unlinkSync(path.join(output, file));
  }
  try {
    const maker = await browser.newPage();
    [photo, signature] = await maker.evaluate(() => {
      const portrait = document.createElement('canvas'); portrait.width = 184; portrait.height = 224;
      const ctx = portrait.getContext('2d');
      ctx.fillStyle = '#e8eded'; ctx.fillRect(0, 0, 184, 224);
      ctx.fillStyle = '#859497'; ctx.beginPath(); ctx.arc(92, 76, 35, 0, Math.PI * 2); ctx.fill();
      ctx.beginPath(); ctx.ellipse(92, 202, 68, 77, 0, 0, Math.PI * 2); ctx.fill();
      ctx.fillStyle = '#fff'; ctx.font = '12px Arial'; ctx.textAlign = 'center'; ctx.fillText('SAMPLE PHOTOGRAPH', 92, 208);
      const sign = document.createElement('canvas'); sign.width = 320; sign.height = 80;
      const ink = sign.getContext('2d'); ink.fillStyle = '#333'; ink.font = 'italic 32px Georgia'; ink.fillText('A. S. Menon', 14, 52);
      return [portrait.toDataURL('image/png'), sign.toDataURL('image/png')];
    });
    await maker.close();
    fs.writeFileSync(samplePhoto, Buffer.from(photo.split(',')[1], 'base64'));
    fs.writeFileSync(sampleSignature, Buffer.from(signature.split(',')[1], 'base64'));
    for (const [name, viewport] of [['desktop', { width: 1440, height: 1000 }], ['mobile', { width: 390, height: 844 }]]) {
      const context = await browser.newContext({ viewport });
      await context.addInitScript(data => sessionStorage.setItem('candidateDashboardData', JSON.stringify(data)), current);
      const page = await context.newPage();
      const errors = []; page.on('pageerror', error => errors.push(error.message));
      await page.goto(origin + '/candidate-details.html');
      await page.locator('#printout[data-ready="true"]').waitFor();
      assert.equal(await page.locator('#printApplication').isEnabled(), true);
      assert.equal(await page.locator('.document-photo').evaluate(el => el.complete && el.naturalWidth > 0), true);
      assert.equal(await page.locator('.document-signature').evaluate(el => el.complete && el.naturalWidth > 0), true);
      assert.ok((await page.locator('#printout').textContent()).includes('Accessibility Requirements'));
      assert.ok((await page.locator('#printout').textContent()).includes('Category Certificate'));
      assert.ok(!(await page.locator('#printout').innerText()).includes('private/path'));
      assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1));
      assert.equal(await page.locator('.printout').evaluate(el => getComputedStyle(el).borderRadius), '0px');
      await page.screenshot({ path: path.join(output, 'candidate-dashboard-' + name + '.png'), fullPage: true });
      await page.locator('#candidateBulletin:not([hidden])').waitFor();
      assert.equal(await page.locator('#candidateNoticeList li').count(), 3);
      assert.equal(await page.locator('#summaryResponse').textContent(), 'Submitted');
      await page.screenshot({ path: path.join(output, 'candidate-dashboard-overview-' + name + '.png') });
      await page.locator('#printout').screenshot({ path: path.join(output, 'application-record-' + name + '.png') });
      await page.locator('#applicationShell').evaluate(el => el.open = false);
      const pdf = path.join(output, name === 'desktop' ? 'application-record-sample.pdf' : 'application-record-mobile-print.pdf');
      await page.pdf({ path: pdf, preferCSSPageSize: true, printBackground: true, displayHeaderFooter: false });
      const printed = pdfText(pdf);
      for (const value of ['Aarav S. Menon', 'DEMO2027001384', 'Accessibility Requirements', 'Category Certificate', 'Low-power sensor networks.', '600020']) assert.ok(printed.toLowerCase().includes(value.toLowerCase()), value + ' missing from PDF');
      assert.ok(!printed.includes('Application & Examination'));
      assert.ok(!printed.includes('Challenge Window'));
      assert.equal(await page.locator('#applicationShell').evaluate(el => el.open), false);
      execFileSync(path.join(poppler, 'pdftoppm.exe'), ['-scale-to', '1400', '-png', pdf, path.join(output, 'application-record-print-' + name)]);
      assert.deepEqual(errors, []);
      await context.close();
      console.log(name + ': responsive document, uploaded media, custom fields and print content passed');
    }
    current = { ...candidate, ugDetails: Array.from({ length: 45 }, (_, i) => 'ACADEMIC_ENTRY_' + String(i).padStart(2,'0') + ': Research coursework and laboratory assessment completed successfully.').join('\n') + '\nEND_OF_ACADEMIC_RECORD', customFieldValues: { ...candidate.customFieldValues, project: 'École supérieure: communications research.' } };
    const long = await browser.newPage({ viewport: { width: 1280, height: 1000 } });
    await long.addInitScript(data => sessionStorage.setItem('candidateDashboardData', JSON.stringify(data)), current);
    await long.goto(origin);
    await long.locator('#printout[data-ready="true"]').waitFor();
    const longPdf = path.join(output, 'application-record-long-test.pdf');
    await long.pdf({ path: longPdf, preferCSSPageSize: true, printBackground: true, displayHeaderFooter: false });
    const longText = pdfText(longPdf);
    for (let i = 0; i < 45; i++) assert.ok(longText.includes('ACADEMIC_ENTRY_' + String(i).padStart(2,'0')));
    assert.ok(longText.includes('END_OF_ACADEMIC_RECORD'));
    assert.ok(longText.includes('École supérieure'));
    assert.ok(longText.toLowerCase().includes('candidate declaration'));
    execFileSync(path.join(poppler, 'pdftoppm.exe'), ['-scale-to', '1200', '-png', longPdf, path.join(output, 'application-long-print')]);
    await long.close();
    current = structuredClone(candidate); failImages = true;
    const unavailable = await browser.newPage();
    await unavailable.addInitScript(data => sessionStorage.setItem('candidateDashboardData', JSON.stringify(data)), current);
    await unavailable.goto(origin);
    await unavailable.locator('#printout[data-ready="true"]').waitFor();
    assert.equal(await unavailable.locator('.media-unavailable').count(), 2);
    assert.equal(await unavailable.locator('#printApplication').isEnabled(), true);
    await unavailable.close();
    console.log('Long multi-page records, non-ASCII text, complete final fields and unavailable-image state passed');
    const login = await browser.newPage({ viewport: { width: 390, height: 844 } });
    await login.goto(origin + '/candidate-dashboard.html');
    await login.locator('body.candidate-forms').waitFor();
    assert.equal(await login.locator('input').first().evaluate(el => getComputedStyle(el).borderRadius), '0px');
    await login.screenshot({ path: path.join(output, 'candidate-login-mobile.png'), fullPage: true });
    await login.close();
    const registration = await browser.newPage({ viewport: { width: 1280, height: 1000 } });
    const registrationErrors = [];
    registration.on('pageerror', error => registrationErrors.push(error.message));
    await registration.goto(origin + '/candidate-registration.html?step=personal');
    await registration.locator('#candidateForm:not([hidden])').waitFor();
    await registration.locator('#fullName').fill('Demo Applicant');
    assert.equal(await registration.locator('#fullName').inputValue(), 'Demo Applicant');
    assert.equal(await registration.locator('.registration-sidebar').evaluate(el => getComputedStyle(el).borderRadius), '0px');
    await registration.screenshot({ path: path.join(output, 'candidate-registration-desktop.png'), fullPage: true });
    assert.deepEqual(registrationErrors, []);
    await registration.close();
    const refreshed = await browser.newPage();
    await refreshed.addInitScript(data => sessionStorage.setItem('candidateDashboardData', JSON.stringify({ ...data, fullName: 'Stale Cached Name' })), candidate);
    await refreshed.goto(origin);
    await refreshed.waitForFunction(() => document.querySelector('.document-name').textContent === 'Aarav S. Menon' && document.querySelector('#printout').dataset.ready === 'true');
    await refreshed.close();
    console.log('Application form theme, field entry and refreshed server details passed');
  } finally { await browser.close(); }
}
(async () => {
  await new Promise(resolve => server.listen(preview ? Number(process.env.PREVIEW_PORT || 8092) : 0, '127.0.0.1', resolve));
  const origin = 'http://127.0.0.1:' + server.address().port;
  if (preview) { console.log('Application demo preview: ' + origin); return; }
  try { await run(origin); } finally { await new Promise(resolve => server.close(resolve)); }
})().catch(error => { console.error(error); server.close(); process.exitCode = 1; });
