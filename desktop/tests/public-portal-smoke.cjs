const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const staticRoot = path.join(root, 'src/main/resources/static');
const output = path.join(root, 'output/portal');
const support = {
  faqs: [
    { question: 'How do I access my application after registration closes?', answer: 'Log in with your application number and password to view your saved application.' },
    { question: 'Where can I view my responses and score card?', answer: 'Released responses and score cards are available from your candidate dashboard.' },
    { question: 'When can I challenge the answer key?', answer: 'Challenges can be submitted only during the published challenge window after the answer key is released.' }
  ],
  helpEmail: 'helpdesk@example.test', helpPhone: '+91 44 1234 5678',
  helpHours: 'Monday to Friday, 09:00-17:00 IST', helpUrl: 'https://example.test/help'
};
let exam = {
  examCode: 'GATE2027DEMO', name: 'GATE 2027', mode: 'PHYSICAL_CENTER', durationMinutes: 180, active: true,
  registrationOpen: false, correctionsOpen: false, streamSelectionRequired: false,
  streams: [], customFields: [], enabledFields: ['FULL_NAME', 'DATE_OF_BIRTH', 'EMAIL', 'PHONE', 'PASSWORD'],
  portalHeaderTitle: 'GATE 2027', portalHeaderSubtitle: 'Graduate Aptitude Test in Engineering',
  portalTopBannerImageUrl: '/qa-banner.png',
  portalNotifications: 'Registration is now closed. Candidate login remains available.\nAnswer-key challenge dates will be published in the candidate dashboard.',
  portalSupportContent: support
};
const candidate = {
  registrationId: 'DEMO2027001384', fullName: 'Demo Candidate', examName: 'GATE 2027', dateOfBirth: '2000-01-01',
  responseSubmitted: true, originalAnswerKeyReleased: true, answerKeyChallengeEnabled: true,
  answerKeyChallengeOpensAt: '2026-09-30T03:30:00Z', answerKeyChallengeClosesAt: '2026-10-03T11:30:00Z',
  allowedCountryIso2s: ['IN', 'IN', 'US', 'GB', 'SG', 'CA'], candidateCorrectionFields: {}
};
const mime = { '.html': 'text/html', '.js': 'application/javascript', '.css': 'text/css', '.png': 'image/png', '.gif': 'image/gif' };
const server = http.createServer(async (request, response) => {
  const url = new URL(request.url, 'http://localhost');
  response.setHeader('Cache-Control', 'no-store');
  const json = data => { response.setHeader('Content-Type', 'application/json'); response.end(JSON.stringify(data)); };
  if (url.pathname === '/api/public/exams') return json([exam]);
  if (url.pathname.endsWith('security-token') || url.pathname.endsWith('csrf-token')) return json({ token: 'preview-only' });
  if (url.pathname === '/api/candidate/dashboard') return json(candidate);
  if (url.pathname === '/api/candidate/responses') return json({ answers: [] });
  if (url.pathname.endsWith('/registration-config') && request.method === 'PATCH') {
    let body = '';
    for await (const chunk of request) body += chunk;
    exam = { ...exam, ...JSON.parse(body) };
    return json(exam);
  }
  if (url.pathname === '/api/exams') {
    return json(url.searchParams.has('page')
      ? { content: [exam], page: 0, size: 10, totalElements: 1, totalPages: 1 }
      : [exam]);
  }
  if (url.pathname.startsWith('/api/')) return json(url.searchParams.has('page') ? { content: [], totalElements: 0, totalPages: 0 } : []);
  const banner = 'C:/Users/LENOVO/Downloads/gate2027_banner_final2.png';
  const candidatePage = /^\/candidate-entry\/(dashboard|details|registration-personal)$/.exec(url.pathname);
  const pathname = url.pathname === '/' ? '/index.html'
    : candidatePage ? '/candidate-' + candidatePage[1] + '.html' : url.pathname;
  const file = url.pathname === '/qa-banner.png' ? banner : path.resolve(staticRoot, '.' + decodeURIComponent(pathname));
  if ((file !== banner && !file.startsWith(staticRoot + path.sep)) || !fs.existsSync(file) || !fs.statSync(file).isFile()) {
    response.writeHead(404); response.end(); return;
  }
  response.setHeader('Content-Type', mime[path.extname(file)] || 'application/octet-stream');
  fs.createReadStream(file).pipe(response);
});

async function smoke(origin) {
  const { chromium } = require('playwright');
  const browser = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true });
  fs.mkdirSync(output, { recursive: true });
  try {
    for (const [name, viewport] of [['desktop', { width: 1440, height: 1000 }], ['mobile', { width: 390, height: 844 }], ['narrow', { width: 320, height: 740 }]]) {
      const page = await browser.newPage({ viewport });
      const errors = [];
      page.on('pageerror', error => errors.push(error.message));
      await page.goto(origin);
      await page.locator('#mainStatus').filter({ hasText: 'Registration Closed' }).waitFor();
      assert.equal(await page.locator('#primaryActionLabel').textContent(), 'Candidate Login');
      assert.equal(await page.locator('#primaryAction').getAttribute('href'), '/candidate-entry/dashboard');
      assert.equal(await page.locator('#registeredLogin').isVisible(), false);
      assert.equal(await page.locator('#registrationNav').isVisible(), false);
      assert.equal(await page.locator('.nav-links a:visible svg').count(), 5);
      assert.equal(await page.locator('.faq-item').count(), 3);
      assert.equal(await page.locator('a[href="mailto:helpdesk@example.test"]').count(), 1);
      assert.equal(await page.locator('a[href="tel:+914412345678"]').count(), 1);
      assert.ok(await page.locator('#portalBanner').evaluate(el => el.complete && el.naturalWidth > 0));
      await page.locator('.faq-item summary').first().click();
      assert.ok(await page.locator('.faq-item').first().getAttribute('open') !== null);
      assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1));
      assert.equal(await page.locator('#primaryAction').evaluate(el => getComputedStyle(el).borderRadius), '0px');
      await page.screenshot({ path: path.join(output, 'portal-closed-' + name + '.png'), fullPage: true });
      await page.emulateMedia({ reducedMotion: 'reduce' });
      assert.equal(await page.locator('#primaryAction').evaluate(el => getComputedStyle(el).animationName), 'none');
      await page.emulateMedia({ reducedMotion: 'no-preference' });
      const state = { ...exam, registrationOpen: true, portalNoticeTabEnabled: true, portalNoticePdfUploaded: true, portalNoticeDocumentLabel: 'Admission Brochure' };
      await page.route('**/api/public/exams', route => route.fulfill({ json: [state] }));
      await page.reload();
      await page.locator('#registeredLogin:visible').waitFor();
      assert.equal(await page.locator('#primaryActionLabel').textContent(), 'Register Now');
      assert.equal(await page.locator('#registrationNav').isVisible(), true);
      assert.equal(await page.locator('#publicNoticeLabel').textContent(), 'Admission Brochure');
      assert.equal(await page.locator('#publicNoticeTab svg').count(), 1);
      assert.equal(await page.locator('#primaryAction').evaluate(el => getComputedStyle(el).animationName), 'none');
      state.registrationOpen = false; state.correctionsOpen = true;
      await page.reload();
      await page.locator('#mainStatus').filter({ hasText: 'Correction Window Open' }).waitFor();
      assert.equal(await page.locator('#registeredLogin').isVisible(), false);
      assert.equal(await page.locator('#primaryActionLabel').textContent(), 'Candidate Login');
      assert.deepEqual(errors, []);
      await page.close();
      console.log(name + ': closed/open/correction states, navigation icons, FAQ, contacts, banner, responsive layout and reduced motion passed');
    }
    const page = await browser.newPage();
    let payload = [{ ...exam, registrationOpen: true, examCode: 'OPEN' }, { ...exam, examCode: 'CLOSED', portalSupportContent: {} }];
    await page.route('**/api/public/exams', route => route.fulfill({ json: payload }));
    await page.goto(origin);
    await page.locator('#examSelectorWrap:visible').waitFor();
    await page.locator('#examSelector').selectOption('CLOSED');
    assert.equal(await page.locator('#mainStatus').textContent(), 'Registration Closed');
    assert.equal(await page.locator('.faq-item').count(), 0);
    assert.equal(await page.locator('#helpContacts a').count(), 0);
    payload = [{ ...exam, portalSupportContent: { faqs: [{ question: '<img id="pwn" src=x>', answer: '<script>alert(1)</script>' }], helpUrl: 'javascript:alert(1)' } }];
    await page.reload();
    await page.locator('.faq-item').waitFor();
    assert.equal(await page.locator('#pwn').count(), 0);
    assert.equal(await page.locator('#helpContacts a').count(), 0);
    payload = [];
    await page.reload();
    await page.locator('#examRows').filter({ hasText: 'No examinations' }).waitFor();
    assert.equal(await page.locator('#primaryActionLabel').textContent(), 'Candidate Login');
    await page.unroute('**/api/public/exams');
    await page.route('**/api/public/exams', route => route.fulfill({ status: 503, body: '{}' }));
    await page.reload();
    await page.locator('#mainStatus').filter({ hasText: 'Temporarily Unavailable' }).waitFor();
    await page.close();
    console.log('Multi-exam switching, empty state, unavailable state and untrusted content passed');

    const admin = await browser.newPage({ viewport: { width: 1360, height: 1000 } });
    const adminErrors = [];
    admin.on('pageerror', error => adminErrors.push(error.message));
    await admin.goto(origin + '/admin-7xk92q-portal.html');
    await admin.waitForFunction(() => document.getElementById('configExamCode').options.length === 1);
    await admin.evaluate(() => openWorkspaceCard('registrationConfigCard'));
    await admin.locator('[data-admin-tab="portalDisplayTab"]').click();
    await admin.locator('#addPortalFaq').click();
    await admin.locator('[data-faq-question]').last().fill('New administrator FAQ');
    await admin.locator('[data-faq-answer]').last().fill('An updated published answer.');
    await admin.locator('#portalHelpEmail').fill('updated@example.test');
    assert.equal(await admin.locator('#portalHelpPhone').evaluate(el => { el.value = 'letters'; return el.checkValidity(); }), false);
    await admin.locator('#portalHelpPhone').fill('+91 (44) 1234-5678');
    assert.equal(await admin.locator('#portalHelpPhone').evaluate(el => el.checkValidity()), true);
    await admin.locator('[data-faq-action="up"]').last().click();
    const settings = await admin.evaluate(() => PortalSupportSettings.serialize());
    assert.equal(settings.faqs[2].question, 'New administrator FAQ');
    const [saved] = await Promise.all([
      admin.waitForRequest(request => request.method() === 'PATCH' && request.url().endsWith('/registration-config')),
      admin.locator('#examConfigForm button[type="submit"]').click()
    ]);
    assert.deepEqual(saved.postDataJSON().portalSupportContent, settings);
    await admin.locator('#configResult').filter({ hasText: 'Registration config saved' }).waitFor();
    await admin.reload();
    await admin.waitForFunction(() => document.getElementById('portalHelpEmail').value === 'updated@example.test');
    assert.equal(await admin.locator('[data-faq-question]').count(), 4);
    await admin.evaluate(() => openWorkspaceCard('registrationConfigCard'));
    await admin.locator('[data-admin-tab="portalDisplayTab"]').click();
    await admin.locator('#portalSupportSettings').screenshot({ path: path.join(output, 'portal-admin-faq.png') });
    await admin.locator('[data-faq-action="remove"]').last().click();
    assert.equal(await admin.locator('[data-faq-question]').count(), 3);
    await admin.locator('#portalHelpUrl').fill('javascript:alert(1)');
    assert.ok(await admin.evaluate(() => { try { PortalSupportSettings.serialize(); return false; } catch (_) { return true; } }));
    await admin.locator('#portalHelpUrl').fill('');
    assert.equal(await admin.locator('.notice-editor-row').count(), 2, 'Existing multiline notices migrate into editable rows');
    await admin.locator('#clearPortalNotifications').click();
    assert.equal(await admin.locator('.notice-editor-row').count(), 0);
    async function addNotice(copy, date, isNew) {
      await admin.locator('#addPortalNotice').click();
      const id = await admin.locator('[data-notice-text]:focus').getAttribute('id');
      const row = admin.locator('fieldset').filter({ has: admin.locator('#' + id) });
      await row.locator('[data-notice-text]').fill(copy);
      await row.locator('[data-notice-date]').fill(date);
      await row.locator('[data-notice-new]').setChecked(isNew);
      return row;
    }
    await addNotice('Archived registration announcement', '2026-10-07', false);
    await addNotice('Answer-key challenge window is open', '2026-10-06', true);
    await addNotice('Candidate score cards are now available', '2026-10-07', true);
    const sameDay = await addNotice('Same-day draft', '2026-10-07', true);
    await sameDay.locator('[data-notice-action="down"]').click();
    assert.equal((await admin.evaluate(() => PortalNoticeEditor.serialize()))[1].text, 'Same-day draft');
    await sameDay.locator('[data-notice-action="up"]').click();
    assert.equal((await admin.evaluate(() => PortalNoticeEditor.serialize()))[0].text, 'Same-day draft');
    await sameDay.locator('[data-notice-action="remove"]').click();
    const noticeSettings = await admin.evaluate(() => PortalNoticeEditor.serialize());
    assert.deepEqual(noticeSettings.map(n => n.text), ['Candidate score cards are now available', 'Answer-key challenge window is open', 'Archived registration announcement']);
    assert.deepEqual(noticeSettings.map(n => n.isNew), [true, true, false]);
    await admin.locator('#portalNoticeEditor').screenshot({ path: path.join(output, 'notification-editor.png') });
    await admin.evaluate(() => renderCustomCandidateFields([{key:'eligibility',label:'Eligibility certificate',type:'PDF_UPLOAD',step:'uploads',maxSizeKb:1024,correctionAllowed:true}]));
    assert.equal(await admin.locator('[name="customFieldCorrection"]').isChecked(), true);
    await admin.locator('[name="customFieldCorrection"]').uncheck();
    assert.equal((await admin.evaluate(() => selectedCustomCandidateFields()))[0].correctionAllowed, false);
    await admin.locator('[name="customFieldCorrection"]').check();
    await admin.locator('#customCandidateFields').screenshot({ path: path.join(output, 'admin-correction-permissions.png') });
    await admin.locator('#examConfigForm button[type="submit"]').click();
    await admin.locator('#configResult').filter({ hasText: 'Registration config saved' }).waitFor();
    await admin.reload();
    await admin.waitForFunction(() => document.querySelectorAll('.notice-editor-row').length === 3);
    assert.equal(await admin.locator('[name="customFieldCorrection"]').isChecked(), true);
    assert.deepEqual(await admin.evaluate(() => PortalNoticeEditor.serialize()), noticeSettings);
    const noticePage = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
    await noticePage.goto(origin);
    await noticePage.locator('.notice-copy').first().filter({ hasText: 'Candidate score cards' }).waitFor();
    assert.deepEqual(await noticePage.locator('.board-badge').allTextContents(), ['NEW', 'NEW', 'ARCHIVE']);
    assert.equal(await noticePage.locator('.notice-metadata time').first().textContent(), '07 Oct 2026');
    await noticePage.screenshot({ path: path.join(output, 'official-portal-desktop.png'), fullPage: true });
    await noticePage.setViewportSize({ width: 320, height: 740 });
    assert.ok(await noticePage.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1));
    await noticePage.screenshot({ path: path.join(output, 'official-portal-mobile.png'), fullPage: true });
    await noticePage.close();
    assert.deepEqual(adminErrors, []);
    await admin.close();
    const published = await browser.newPage();
    await published.goto(origin);
    await published.locator('a[href="mailto:updated@example.test"]').waitFor();
    assert.equal(await published.locator('.faq-item').count(), 3);
    await published.close();
    console.log('Actual admin form: FAQ add/reorder/remove, save payload, reload, published contact details and URL validation passed');

    for (const [name, viewport] of [['desktop', { width: 1440, height: 1000 }], ['mobile', { width: 390, height: 844 }]]) {
      const context = await browser.newContext({ viewport });
      await context.addInitScript(data => { sessionStorage.setItem('candidateDashboardData', JSON.stringify(data)); Date.now = () => 1790848800000; }, candidate);
      const dashboard = await context.newPage();
      const errors = [];
      dashboard.on('pageerror', error => errors.push(error.message));
      await dashboard.goto(origin + '/candidate-details.html');
      await dashboard.locator('#challengeWorkflow.visible .challenge-schedule').waitFor();
      assert.equal(await dashboard.locator('#challengeWorkflow [data-timezone="Asia/Kolkata"]').count(), 1);
      assert.equal(await dashboard.locator('#challengeWorkflow .challenge-schedule tbody tr').count(), 5);
      assert.equal(await dashboard.locator('#challengeWorkflow time').first().getAttribute('datetime'), candidate.answerKeyChallengeOpensAt.replace('Z', '.000Z'));
      const oldRow = await dashboard.locator('#challengeWorkflow .challenge-schedule').evaluate(el => { el.dataset.qaIdentity = 'retained'; return el.dataset.qaIdentity; });
      await dashboard.evaluate(() => updateChallengeAvailability());
      assert.equal(await dashboard.locator('#challengeWorkflow .challenge-schedule').getAttribute('data-qa-identity'), oldRow);
      await dashboard.locator('#applicationShell').evaluate(el => el.open = false);
      await dashboard.locator('#challengeWorkflow').screenshot({ path: path.join(output, 'challenge-schedule-' + name + '.png') });
      await dashboard.locator('#openAnswerKeyChallenge').click();
      await dashboard.locator('#challengeModal.open .challenge-schedule').waitFor();
      assert.equal(await dashboard.locator('#challengeTimelineWrap .challenge-schedule tbody tr').count(), 5);
      assert.ok(await dashboard.locator('#challengeTimelineWrap').evaluate(el => el.scrollWidth <= el.clientWidth + 1));
      assert.ok(await dashboard.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1));
      await dashboard.screenshot({ path: path.join(output, 'challenge-modal-' + name + '.png') });
      await dashboard.locator('#closeChallengeModal').click();
      await dashboard.evaluate(() => { candidate.answerKeyChallengeEnabled = false; updateChallengeAvailability(); candidate.answerKeyChallengeEnabled = true; updateChallengeAvailability(); });
      assert.equal(await dashboard.locator('#challengeWorkflow .challenge-schedule').count(), 1);
      await dashboard.evaluate(() => {
        candidate.answerKeyChallengeOpensAt = '2027-10-01T03:30:00Z';
        candidate.answerKeyChallengeClosesAt = '2027-10-03T03:30:00Z';
        updateChallengeAvailability();
        document.querySelector('#challengeWorkflow .challenge-schedule').dataset.qaIdentity = 'scheduled-retained';
        const previous = Date.now();
        Date.now = () => previous + 1000;
        updateChallengeAvailability();
      });
      assert.equal(await dashboard.locator('#challengeWorkflow .challenge-schedule').getAttribute('data-qa-identity'), 'scheduled-retained');
      assert.deepEqual(errors, []);
      await context.close();
      console.log(name + ': compact timezone schedule, no duplicate India, modal width, stable timer updates and reopening passed');
    }
  } finally { await browser.close(); }
}

(async () => {
  const preview = process.argv.includes('--serve');
  if (preview) exam.portalNotices = [
    { text: 'Candidate score cards are now available.', isNew: true, publishedOn: '2026-10-07' },
    { text: 'Answer-key challenge window is open.', isNew: true, publishedOn: '2026-10-06' },
    { text: 'Examination registration has closed.', isNew: false, publishedOn: '2026-09-30' }
  ];
  await new Promise(resolve => server.listen(preview ? Number(process.env.PREVIEW_PORT || 8091) : 0, '127.0.0.1', resolve));
  const origin = 'http://127.0.0.1:' + server.address().port;
  if (preview) { console.log('Demo-data portal preview: ' + origin); return; }
  try { await smoke(origin); } finally { await new Promise(resolve => server.close(resolve)); }
})().catch(error => { console.error(error); server.close(); process.exitCode = 1; });
