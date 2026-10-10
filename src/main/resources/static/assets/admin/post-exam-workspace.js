(function () {
  'use strict';
  const byId = id => document.getElementById(id);
  // Move existing controls intact so uploads, validation and review handlers are retained.
  function move(node, target) {
    node.querySelectorAll('input, select, textarea, button').forEach(control => {
      if (control.form?.id === 'examConfigForm') control.setAttribute('form', 'examConfigForm');
    });
    target.append(node);
  }
  function moveUpload(inputId, statusId, targetId) {
    const input = byId(inputId);
    const label = document.querySelector('label[for="' + inputId + '"]');
    if (label) move(label, byId(targetId));
    move(input.parentElement, byId(targetId));
    move(byId(statusId), byId(targetId));
  }
  moveUpload('originalQuestionPaperPdfFile', 'originalQuestionPaperPdfStatus', 'postQuestionPaper');
  moveUpload('originalAnswerKeyPdfFile', 'originalAnswerKeyPdfStatus', 'postAnswerKey');
  const evaluation = byId('evaluationTab');
  const headings = [...evaluation.querySelectorAll('h4')];
  let node = headings[0];
  const end = byId('evaluationRows').closest('.table-wrap');
  while (node && node !== end) {
    const next = node.nextElementSibling;
    byId('postChallengesTab').append(node);
    node = next;
  }
  const releaseIds = ['responsesReleased', 'originalQuestionPaperReleased', 'originalAnswerKeyReleased', 'resultReleased', 'scoreCardPdfReleased'];
  releaseIds.forEach(id => move(byId(id).closest('label'), byId('postReleaseFields')));
  move(byId('answerKeyChallengeEnabled').closest('label'), byId('postChallengeWindow'));
  move(byId('answerKeyChallengeOpensAt').closest('.color-grid'), byId('postChallengeWindow'));

  ['openResponseCenter', 'openResponseCenterDashboard'].forEach(id => byId(id).textContent = 'Open post-exam workspace');
  byId('openResponseCenterDashboard').previousElementSibling.textContent = 'Post-exam operations';
  byId('responseCenterHref').hidden = true;
  const nav = document.createElement('nav');
  nav.className = 'operations-nav';
  nav.setAttribute('aria-label', 'Exam workspace');
  const items = [['workspaceDashboard', 'layout-dashboard', 'Overview'], ['registrationConfigCard', 'settings-2', 'Exam settings'], ['databaseCard', 'users', 'Candidates & records'], ['responseCenterCard', 'clipboard-check', 'Post-exam']];
  items.forEach(([target, icon, label]) => {
    const button = document.createElement('button');
    button.type = 'button'; button.dataset.workspaceTarget = target;
    button.innerHTML = '<i data-lucide="' + icon + '"></i><span>' + label + '</span>';
    button.addEventListener('click', () => openWorkspaceCard(target));
    nav.append(button);
  });
  byId('workspaceShell').prepend(nav);
  const labels = { responsesTab: 'inbox', evaluationTab: 'calculator', postDocumentsTab: 'files', postChallengesTab: 'messages-square', postPublicationTab: 'send', manualEvaluationTab: 'file-check-2', examIncidentsTab: 'triangle-alert', approvedExamActionsTab: 'shield-check' };
  document.querySelectorAll('[data-response-tab]').forEach(button => {
    const icon = document.createElement('i');
    icon.dataset.lucide = labels[button.dataset.responseTab];
    button.prepend(icon);
    button.setAttribute('role', 'tab');
    button.setAttribute('aria-controls', button.dataset.responseTab);
    button.setAttribute('aria-selected', String(button.classList.contains('active')));
    button.tabIndex = button.classList.contains('active') ? 0 : -1;
  });
  byId('responseCenterCard').querySelector('[role="tablist"]').addEventListener('keydown', event => {
    if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
    const tabs = [...document.querySelectorAll('[data-response-tab]')].filter(button => button.getClientRects().length);
    const index = tabs.indexOf(document.activeElement);
    if (index < 0) return;
    event.preventDefault();
    const next = event.key === 'Home' ? 0 : event.key === 'End' ? tabs.length - 1 : (index + (event.key === 'ArrowRight' ? 1 : -1) + tabs.length) % tabs.length;
    tabs[next].click(); tabs[next].focus();
  });
  window.refreshPostPublicationState = function () {
    const exam = activeExam();
    byId('postPublicationBadge').textContent = exam?.resultReleased ? 'Results published' : 'Results not published';
    byId('savePostPublication').disabled = !exam;
  };
  byId('savePostPublication').addEventListener('click', async () => {
    const exam = activeExam();
    const status = byId('postPublicationStatus');
    if (!exam) { status.textContent = 'Select an exam first.'; return; }
    const payload = Object.fromEntries([...releaseIds, 'answerKeyChallengeEnabled'].map(id => [id, byId(id).checked]));
    payload.answerKeyChallengeOpensAt = fromDateTimeLocal(byId('answerKeyChallengeOpensAt').value);
    payload.answerKeyChallengeClosesAt = fromDateTimeLocal(byId('answerKeyChallengeClosesAt').value);
    if (payload.answerKeyChallengeEnabled && (!payload.answerKeyChallengeOpensAt || !payload.answerKeyChallengeClosesAt || payload.answerKeyChallengeClosesAt <= payload.answerKeyChallengeOpensAt)) {
      status.textContent = 'Set a closing time after the challenge opening time.'; return;
    }
    if (!confirm('Save candidate publication settings for ' + exam.examCode + '?')) return;
    const button = byId('savePostPublication'); button.disabled = true;
    status.textContent = 'Saving publication settings...';
    try {
      const response = await fetch('/api/exams/' + encodeURIComponent(exam.examCode) + '/post-exam-publication', {
        method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
      });
      const data = await response.json();
      if (!response.ok) throw new Error(data.detail || data.message || 'Could not save publication settings.');
      Object.assign(exam, data);
      refreshPostPublicationState();
      status.textContent = 'Publication settings saved for ' + exam.examCode + '.';
    } catch (error) { status.textContent = error.message; }
    finally { button.disabled = false; }
  });
  lucide.createIcons({ attrs: { 'aria-hidden': 'true' } });
})();
