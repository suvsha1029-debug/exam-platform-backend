const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const html = fs.readFileSync(path.join(__dirname, '../../src/main/resources/static/candidate-exam.html'), 'utf8');
const script = [...html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g)]
  .map((match) => match[1]).find((body) => body.includes('function goTo('));

function page() {
  const elements = new Map();
  const pendingAnswers = [];
  const questionRequests = [];
  const element = (id) => {
    if (!elements.has(id)) {
      const classes = new Set();
      elements.set(id, {
        classList: {
          add: (...names) => names.forEach((name) => classes.add(name)),
          remove: (...names) => names.forEach((name) => classes.delete(name)),
          toggle: (name, enabled) => enabled ? classes.add(name) : classes.delete(name),
          contains: (name) => classes.has(name)
        },
        addEventListener() {},
        removeAttribute(name) { delete this[name]; },
        scrollTo() {},
        style: {},
        textContent: '',
        innerHTML: '',
        value: ''
      });
    }
    return elements.get(id);
  };
  const context = vm.createContext({
    console: { warn() {} },
    Date,
    Map,
    URLSearchParams,
    window: {
      location: { search: '', pathname: '/candidate-exam.html', hash: '' },
      addEventListener() {},
      __candidateSecurityTokenCache: 'test-token'
    },
    document: {
      getElementById: element,
      querySelector: () => element('questionScroller'),
      addEventListener() {}
    },
    history: { replaceState() {} },
    sessionStorage: { getItem: () => null, setItem() {} },
    localStorage: { removeItem() {} },
    setInterval() {},
    setTimeout,
    clearTimeout,
    requestAnimationFrame: (callback) => callback(),
    fetch: (url, options) => {
      if (url === '/api/candidate/server-exam/answer') {
        return new Promise((resolve) => pendingAnswers.push(resolve));
      }
      if (url === '/api/candidate/server-exam/question') {
        const { displayIndex } = JSON.parse(options.body);
        questionRequests.push(displayIndex);
        const row = {
          displayIndex, displayQuestionId: `Q.${displayIndex}`,
          question: { body: `Question ${displayIndex}`, options: { A: 'One' } },
          state: { status: 'not_visited', markedOptionIds: [] }
        };
        return Promise.resolve({ ok: true, text: async () => JSON.stringify(row) });
      }
      throw new Error(`Unexpected request: ${url}`);
    }
  });
  vm.runInContext(script, context);
  return { context, element, pendingAnswers, questionRequests };
}

test('desktop exam shows uploaded identity images and falls back to institute logo', () => {
  const { context, element } = page();
  vm.runInContext(`candidate = {
    registrationId: 'APP-1', fullName: 'Candidate', examName: 'Exam',
    examUiBannerImageUrl: '/missing-banner.png', instituteLogoDataUrl: 'data:image/png;base64,AAAA',
    photoDataUrl: 'data:image/png;base64,BBBB'
  }; renderExamIdentity();`, context);
  assert.equal(element('candidatePhotoImg').src, 'data:image/png;base64,BBBB');
  element('candidatePhotoImg').onload();
  assert.equal(element('candidateAvatar').classList.contains('has-photo'), true);
  assert.equal(element('examLogoImg').src, '/missing-banner.png');
  element('examLogoImg').onerror();
  assert.equal(element('examLogoImg').src, 'data:image/png;base64,AAAA');
  element('examLogoImg').onload();
  assert.equal(element('examLogoBox').classList.contains('has-logo'), true);
});

test('next question paints before the server save finishes and flush waits for it', async () => {
  const { context, element, pendingAnswers } = page();
  vm.runInContext(`
    examAuth = { examSessionToken: 'token' };
    examData = { sections: [{ name: 'Section', questions: [
      { displayIndex: 1, id: 'Q.1', type: 'MCQ', marks: 2, neg: 0, body: 'First', options: { A: 'One' } },
      { displayIndex: 2, id: 'Q.2', type: 'MCQ', marks: 2, neg: 0, body: 'Second', options: { A: 'Two' } }
    ] }] };
    state['0:0'] = { answer: 'A', status: 'answered' };
    state['0:1'] = { answer: null, status: 'not_visited' };
    serverExamMode = true;
    serverExamFullPaperMode = true;
    goTo(0, 0);
    saveNext();
  `, context);
  assert.equal(element('qNumber').textContent, 'Question No. Q.2');
  assert.match(element('qBody').innerHTML, /Second/);
  await Promise.resolve();
  await Promise.resolve();
  assert.equal(pendingAnswers.length, 1);
  let flushed = false;
  const flush = vm.runInContext('flushServerExamSaves()', context).then(() => { flushed = true; });
  await Promise.resolve();
  assert.equal(flushed, false);
  pendingAnswers.shift()({ ok: true, text: async () => JSON.stringify({ state: { markedOptionIds: ['A'], status: 'answered' } }) });
  await flush;
  assert.equal(flushed, true);
});

test('fallback question fetch is reused on revisit', async () => {
  const { context, element, questionRequests } = page();
  vm.runInContext(`
    examAuth = { examSessionToken: 'token' };
    examData = { sections: [{ name: 'Section', questions: [
      { displayIndex: 1, id: 'Q.1', type: 'MCQ', marks: '-', neg: 0, body: '', options: {} },
      { displayIndex: 2, id: 'Q.2', type: 'MCQ', marks: '-', neg: 0, body: '', options: {} }
    ] }] };
    state['0:0'] = { answer: null, status: 'not_visited' };
    state['0:1'] = { answer: null, status: 'not_visited' };
    serverExamMode = true;
    serverExamFullPaperMode = false;
  `, context);
  await vm.runInContext('goTo(0, 0)', context);
  await new Promise((resolve) => setTimeout(resolve, 5));
  assert.equal(element('qNumber').textContent, 'Question No. Q.1');
  assert.deepEqual(questionRequests, [1, 2]);
  await vm.runInContext('goTo(0, 1)', context);
  await vm.runInContext('goTo(0, 0)', context);
  assert.deepEqual(questionRequests, [1, 2]);
});

test('failed answer save is retried before final flush succeeds', async () => {
  const { context, pendingAnswers } = page();
  vm.runInContext(`
    examAuth = { examSessionToken: 'token' };
    examData = { sections: [{ name: 'Section', questions: [
      { displayIndex: 1, id: 'Q.1', type: 'MCQ', marks: 2, neg: 0, body: 'First', options: { A: 'One' } }
    ] }] };
    state['0:0'] = { answer: 'A', status: 'answered' };
    serverExamMode = true;
    serverExamFullPaperMode = true;
    goTo(0, 0);
  `, context);
  const firstSave = vm.runInContext('saveServerCurrentState()', context);
  await Promise.resolve();
  await Promise.resolve();
  pendingAnswers.shift()({ ok: false, text: async () => '{"detail":"temporary failure"}' });
  await firstSave;
  const flush = vm.runInContext('flushServerExamSaves()', context);
  await Promise.resolve();
  await Promise.resolve();
  assert.equal(pendingAnswers.length, 1);
  pendingAnswers.shift()({ ok: true, text: async () => JSON.stringify({ state: { markedOptionIds: ['A'], status: 'answered' } }) });
  await flush;
});
