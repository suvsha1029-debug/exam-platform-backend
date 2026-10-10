(function (global) {
  let questionOrderMeta = [];
  let sectionPriorityOrder = [];
  let markPriorityBySection = {};
  let registrationModuleBound = false;
  let allowedCountrySelection = new Set();

  const candidateFields = [
    ['FULL_NAME', 'Full name'],
    ['DATE_OF_BIRTH', 'Date of birth'],
    ['ADDRESS', 'Address'],
    ['STATE', 'State / province'],
    ['PINCODE', 'Pincode'],
    ['EMAIL', 'Email ID'],
    ['ALTERNATE_EMAIL', 'Alternate email'],
    ['PHONE', 'Candidate phone'],
    ['NATIONALITY', 'Nationality'],
    ['PASSWORD', 'Candidate password'],
    ['GENDER', 'Gender'],
    ['PARENT_NAME', 'Parent name'],
    ['PARENT_PHONE', 'Parent phone'],
    ['GUARDIAN_NAME', 'Guardian name'],
    ['GUARDIAN_PHONE', 'Guardian phone'],
    ['GUARDIAN_RELATION', 'Guardian relation'],
    ['CLASS_10_MARKSHEET', 'Class 10 marksheet PDF'],
    ['CLASS_12_MARKSHEET', 'Class 12 marksheet PDF'],
    ['UG_DETAILS', 'UG details'],
    ['PG_DETAILS', 'PG details'],
    ['PHOTO', 'Latest photo'],
    ['SIGNATURE', 'Signature'],
    ['ID_CARD', 'ID card']
  ];
  const mandatoryFields = new Set(['FULL_NAME', 'DATE_OF_BIRTH', 'EMAIL', 'PHONE', 'PASSWORD']);
  const customFieldTypes = [
    ['TEXT', 'Text box'],
    ['TEXTAREA', 'Long text'],
    ['IMAGE_UPLOAD', 'Image upload'],
    ['PDF_UPLOAD', 'PDF upload']
  ];
  const countryRegistry = ((global.CANDIDATE_LOCATION_REGISTRY && Array.isArray(global.CANDIDATE_LOCATION_REGISTRY))
    ? global.CANDIDATE_LOCATION_REGISTRY
    : []).map((entry) => ({
      iso2: String(entry.iso2 || '').toUpperCase(),
      country: String(entry.country || '').trim(),
      code: String(entry.code || '').trim()
    })).filter((entry) => entry.iso2 && entry.country);

  function renderCandidateFields() {
    const wrap = document.getElementById('candidateFields');
    wrap.innerHTML = candidateFields.map(([value, label]) => {
      const locked = mandatoryFields.has(value) ? ' checked disabled' : '';
      return '<label class="candidate-field-card">'
        + '<input type="checkbox" name="candidateField" value="' + value + '"' + locked + '>'
        + '<span>' + escapeHtml(label) + '</span>'
        + '<label class="mini-toggle required-toggle"><input type="checkbox" name="candidateRequiredField" value="' + value + '"' + (mandatoryFields.has(value) ? ' checked disabled' : '') + '> Compulsory</label>'
        + '<label class="mini-toggle correction-toggle"><input type="checkbox" name="candidateCorrectionField" value="' + value + '"> Allow correction</label>'
        + '<select name="candidateFieldStep" data-field-step="' + value + '">'
        + '<option value="personal">Personal information</option>'
        + '<option value="education">Education details</option>'
        + '<option value="uploads">Uploads / documents</option>'
        + '</select>'
        + '</label>';
    }).join('');
    syncFieldWorkflowMode();
  }

  function syncFieldWorkflowMode() {
    const grid = document.getElementById('candidateFields');
    if (!grid) return;
    const registrationOpen = document.getElementById('registrationOpen')?.checked;
    const correctionsOpen = document.getElementById('correctionsOpen')?.checked;
    grid.classList.toggle('field-workflow-registration', Boolean(registrationOpen));
    grid.classList.toggle('field-workflow-correction', !registrationOpen && Boolean(correctionsOpen));
    const note = document.getElementById('fieldWorkflowNote');
    if (registrationOpen) {
      note.textContent = 'Registration fields';
    } else if (correctionsOpen) {
      note.textContent = 'Correction fields';
    } else {
      note.textContent = 'Both registration and correction windows are closed. Field layout stays saved, while compulsory/correction controls are folded.';
    }
  }

  function renderCustomCandidateFields(fields = []) {
    const wrap = document.getElementById('customCandidateFields');
    wrap.innerHTML = '';
    if (!fields.length) {
      wrap.innerHTML = '<div class="hint">No extra candidate boxes added yet.</div>';
      return;
    }
    fields.forEach(addCustomCandidateFieldRow);
  }

  function addCustomCandidateFieldRow(field = {}) {
    const wrap = document.getElementById('customCandidateFields');
    if (wrap.querySelector('.hint')) {
      wrap.innerHTML = '';
    }
    const row = document.createElement('div');
    row.className = 'custom-field-row';
    row.innerHTML = ''
      + '<input name="customFieldLabel" placeholder="Field label" value="' + escapeHtml(field.label || '') + '">'
      + '<input name="customFieldKey" placeholder="field_key" value="' + escapeHtml(field.key || '') + '">'
      + '<select name="customFieldStep">'
      + '<option value="personal">Personal</option>'
      + '<option value="education">Education</option>'
      + '<option value="uploads">Uploads</option>'
      + '</select>'
      + '<select name="customFieldType">'
      + customFieldTypes.map(([value, label]) => '<option value="' + value + '">' + escapeHtml(label) + '</option>').join('')
      + '</select>'
      + '<input name="customFieldMaxSizeKb" type="number" min="1" placeholder="KB max" value="' + escapeHtml(field.maxSizeKb || '') + '">'
      + '<label><input name="customFieldRequired" type="checkbox"' + (field.required ? ' checked' : '') + '> Required</label>'
      + '<label><input name="customFieldCorrection" type="checkbox"' + (field.correctionAllowed ? ' checked' : '') + '> Allow correction</label>'
      + '<button type="button" class="danger" data-remove-custom-field>Remove</button>';
    row.querySelector('[name="customFieldStep"]').value = field.step || 'personal';
    row.querySelector('[name="customFieldType"]').value = field.type || 'TEXT';
    toggleCustomFieldSizeInput(row);
    row.querySelector('[name="customFieldType"]').addEventListener('change', () => toggleCustomFieldSizeInput(row));
    row.querySelector('[name="customFieldLabel"]').addEventListener('input', () => {
      const keyInput = row.querySelector('[name="customFieldKey"]');
      if (!keyInput.dataset.touched) {
        keyInput.value = slugifyCustomFieldKey(row.querySelector('[name="customFieldLabel"]').value);
      }
    });
    row.querySelector('[name="customFieldKey"]').addEventListener('input', () => {
      row.querySelector('[name="customFieldKey"]').dataset.touched = 'true';
    });
    row.querySelector('[data-remove-custom-field]').addEventListener('click', () => {
      row.remove();
      if (!wrap.querySelector('.custom-field-row')) {
        wrap.innerHTML = '<div class="hint">No extra candidate boxes added yet.</div>';
      }
    });
    wrap.appendChild(row);
  }

  function toggleCustomFieldSizeInput(row) {
    const type = row.querySelector('[name="customFieldType"]').value;
    const maxInput = row.querySelector('[name="customFieldMaxSizeKb"]');
    const needsSize = type === 'IMAGE_UPLOAD' || type === 'PDF_UPLOAD';
    maxInput.disabled = !needsSize;
    maxInput.placeholder = needsSize ? (type === 'IMAGE_UPLOAD' ? '100' : '1024') : 'Not used';
    if (!needsSize) {
      maxInput.value = '';
    }
  }

  function selectedCustomCandidateFields() {
    return [...document.querySelectorAll('.custom-field-row')].map((row) => {
      const type = row.querySelector('[name="customFieldType"]').value;
      const maxInput = row.querySelector('[name="customFieldMaxSizeKb"]').value.trim();
      return {
        label: row.querySelector('[name="customFieldLabel"]').value.trim(),
        key: slugifyCustomFieldKey(row.querySelector('[name="customFieldKey"]').value),
        step: row.querySelector('[name="customFieldStep"]').value,
        type,
        required: row.querySelector('[name="customFieldRequired"]').checked,
        correctionAllowed: row.querySelector('[name="customFieldCorrection"]').checked,
        maxSizeKb: (type === 'IMAGE_UPLOAD' || type === 'PDF_UPLOAD') && maxInput ? Number(maxInput) : null
      };
    }).filter((field) => field.label && field.key);
  }

  function defaultCandidateFieldStep(fieldName) {
    if (fieldName === 'UG_DETAILS' || fieldName === 'PG_DETAILS') return 'education';
    if (['PHOTO', 'SIGNATURE', 'ID_CARD', 'CLASS_10_MARKSHEET', 'CLASS_12_MARKSHEET'].includes(fieldName)) return 'uploads';
    return 'personal';
  }

  function selectedCandidateFieldSteps() {
    const out = {};
    document.querySelectorAll('[data-field-step]').forEach((select) => {
      out[select.dataset.fieldStep] = select.value || defaultCandidateFieldStep(select.dataset.fieldStep);
    });
    return out;
  }

  function selectedCandidateRequiredFields() {
    const out = {};
    document.querySelectorAll('input[name="candidateRequiredField"]').forEach((input) => {
      out[input.value] = input.checked;
    });
    return out;
  }

  function selectedCandidateCorrectionFields() {
    const out = {};
    document.querySelectorAll('input[name="candidateCorrectionField"]').forEach((input) => {
      out[input.value] = input.checked;
    });
    return out;
  }

  function slugifyCustomFieldKey(value) {
    return String(value || '')
      .trim()
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '_')
      .replace(/^_+|_+$/g, '');
  }

  function addStreamRow(stream = {}) {
    const row = document.createElement('div');
    row.className = 'stream-row';
    row.innerHTML =
      '<input name="streamShortCode" placeholder="EC" maxlength="24" value="' + escapeHtml(stream.shortCode || '') + '">'
      + '<input name="streamFullName" placeholder="Electronics and Communication" maxlength="140" value="' + escapeHtml(stream.fullName || '') + '">'
      + '<label><input name="streamEnabled" type="checkbox"' + (stream.enabled === false ? '' : ' checked') + '> Enabled</label>'
      + '<button class="danger" type="button">Remove</button>';
    row.querySelector('button').addEventListener('click', () => row.remove());
    document.getElementById('streams').appendChild(row);
  }

  function renderStreams(streams = []) {
    const wrap = document.getElementById('streams');
    wrap.innerHTML = '';
    if (!streams.length) {
      return;
    }
    streams.forEach(addStreamRow);
  }

  function selectedStreams() {
    return [...document.querySelectorAll('.stream-row')].map((row) => ({
      shortCode: row.querySelector('[name="streamShortCode"]').value.trim(),
      fullName: row.querySelector('[name="streamFullName"]').value.trim(),
      enabled: row.querySelector('[name="streamEnabled"]').checked
    })).filter((stream) => stream.shortCode && stream.fullName);
  }

  function renderAllowedCountryOptions(selectedIso2s = []) {
    allowedCountrySelection = new Set((selectedIso2s || [...allowedCountrySelection]).map((value) => String(value || '').toUpperCase()));
    const wrap = document.getElementById('allowedCountryIso2s');
    if (!wrap) return;
    const search = String(document.getElementById('countryRestrictionSearch')?.value || '').trim().toLowerCase();
    const selectedSet = allowedCountrySelection;
    const filtered = countryRegistry.filter((entry) => !search
      || entry.country.toLowerCase().includes(search)
      || entry.iso2.toLowerCase().includes(search));
    wrap.innerHTML = filtered.map((entry) =>
      '<label class="country-check-card">'
      + '<input type="checkbox" name="allowedCountryIso2" value="' + escapeHtml(entry.iso2) + '"' + (selectedSet.has(entry.iso2) ? ' checked' : '') + '>'
      + '<span>' + escapeHtml(entry.country + ' (' + entry.iso2 + ', ' + entry.code + ')') + '</span>'
      + '</label>'
    ).join('');
    updateAllowedCountrySummary();
  }

  function syncAllowedCountrySelectionFromDom() {
    const visibleChecked = [...document.querySelectorAll('input[name="allowedCountryIso2"]:checked')]
      .map((input) => String(input.value || '').toUpperCase())
      .filter(Boolean);
    const visibleValues = [...document.querySelectorAll('input[name="allowedCountryIso2"]')]
      .map((input) => String(input.value || '').toUpperCase())
      .filter(Boolean);
    visibleValues.forEach((value) => allowedCountrySelection.delete(value));
    visibleChecked.forEach((value) => allowedCountrySelection.add(value));
  }

  function selectedAllowedCountryIso2s() {
    syncAllowedCountrySelectionFromDom();
    return [...allowedCountrySelection];
  }

  function updateAllowedCountrySummary() {
    const summary = document.getElementById('countryRestrictionSummary');
    if (!summary) return;
    const selected = selectedAllowedCountryIso2s();
    if (!selected.length) {
      summary.textContent = 'No restriction selected. Candidates can choose from all countries.';
      return;
    }
    if (selected.length === 1) {
      const only = countryRegistry.find((entry) => entry.iso2 === selected[0]);
      summary.textContent = 'Locked to one country: ' + (only ? only.country + ' (' + only.iso2 + ')' : selected[0]) + '.';
      return;
    }
    summary.textContent = selected.length + ' countries selected for this exam.';
  }

  function fillConfigFromSelectedExam() {
    const examCode = document.getElementById('configExamCode').value;
    const exam = exams.find((item) => item.examCode === examCode);
    if (!exam) {
      syncReportDesignerStudioLink();
      updateWorkspaceState();
      return;
    }
    document.getElementById('registrationOpen').checked = Boolean(exam.registrationOpen);
    document.getElementById('correctionsOpen').checked = Boolean(exam.correctionsOpen);
    document.getElementById('streamSelectionRequired').checked = Boolean(exam.streamSelectionRequired);
    document.getElementById('examLobbyEnabled').checked = Boolean(exam.examLobbyEnabled);
    document.getElementById('examLaunchEnabled').checked = Boolean(exam.examLaunchEnabled);
    document.getElementById('candidateSubmitEnabled').checked = Boolean(exam.candidateSubmitEnabled);
    document.getElementById('responsesReleased').checked = Boolean(exam.responsesReleased);
    document.getElementById('resultReleased').checked = Boolean(exam.resultReleased);
    document.getElementById('scoreCardPdfReleased').checked = Boolean(exam.scoreCardPdfReleased);
    document.getElementById('originalQuestionPaperReleased').checked = Boolean(exam.originalQuestionPaperReleased);
    document.getElementById('originalAnswerKeyReleased').checked = Boolean(exam.originalAnswerKeyReleased);
    document.getElementById('answerKeyChallengeEnabled').checked = Boolean(exam.answerKeyChallengeEnabled);
    document.getElementById('optionRandomizationEnabled').checked = Boolean(exam.optionRandomizationEnabled);
    document.getElementById('desktopEntryTimerEnabled').checked = exam.desktopEntryTimerEnabled !== false;
    document.getElementById('desktopEntryDelaySeconds').value = exam.desktopEntryDelaySeconds ?? 120;
    document.getElementById('desktopCaptureProtectionEnabled').checked = exam.desktopCaptureProtectionEnabled !== false;
    document.getElementById('examReadinessTimerEnabled').checked = exam.examReadinessTimerEnabled !== false;
    document.getElementById('examReadinessDurationSeconds').value = exam.examReadinessDurationSeconds ?? 120;
    document.getElementById('reportCardAuthorityName').value = exam.reportCardAuthorityName || '';
    document.getElementById('reportCardAuthorityDesignation').value = exam.reportCardAuthorityDesignation || '';
    document.getElementById('reportCardValidityText').value = exam.reportCardValidityText || '';
    global.ScoreCardSettings.populate(exam);
    global.PortalSupportSettings.populate(exam);
    document.getElementById('reportCardInstituteLogoDataUrl').value = exam.reportCardInstituteLogoDataUrl || '';
    document.getElementById('reportCardWatermarkLogoDataUrl').value = exam.reportCardWatermarkLogoDataUrl || '';
    document.getElementById('reportCardAuthoritySignatureDataUrl').value = exam.reportCardAuthoritySignatureDataUrl || '';
    document.getElementById('reportCardInstituteLogoFile').value = '';
    document.getElementById('reportCardWatermarkLogoFile').value = '';
    document.getElementById('reportCardAuthoritySignatureFile').value = '';
    document.getElementById('examStartAt').value = toDateTimeLocal(exam.examStartAt);
    document.getElementById('answerKeyChallengeOpensAt').value = toDateTimeLocal(exam.answerKeyChallengeOpensAt);
    document.getElementById('answerKeyChallengeClosesAt').value = toDateTimeLocal(exam.answerKeyChallengeClosesAt);
    document.getElementById('loginOpensMinutesBeforeStart').value = exam.loginOpensMinutesBeforeStart ?? 30;
    document.getElementById('loginClosesMinutesBeforeStart').value = exam.loginClosesMinutesBeforeStart ?? 15;
    document.getElementById('questionPaperFile').value = '';
    document.getElementById('answerKeyFile').value = '';
    document.getElementById('originalQuestionPaperPdfFile').value = '';
    document.getElementById('originalAnswerKeyPdfFile').value = '';
    document.getElementById('questionPaperStatus').textContent = exam.questionPaperUploaded
      ? 'Uploaded: ' + (exam.questionPaperOriginalName || 'question paper') + ' | Questions: ' + (exam.questionPaperQuestionCount || 0)
      : 'No question paper uploaded yet.';
    document.getElementById('originalQuestionPaperPdfStatus').textContent = exam.originalQuestionPaperPdfUploaded
      ? 'Uploaded: ' + (exam.originalQuestionPaperPdfName || 'question-paper.pdf')
      : 'No original question paper PDF uploaded yet.';
    const evaluationStatus = document.getElementById('evaluationStatus');
    if (evaluationStatus) {
      evaluationStatus.innerHTML = exam.answerKeyUploaded
        ? '<span class="ok">Answer key ready</span>\n' + escapeHtml(exam.answerKeyOriginalName || 'answer key') + ' | Max marks: ' + escapeHtml(exam.answerKeyTotalMarks || 0)
        : 'No answer key uploaded yet. Open Evaluation / Answer Key and upload the JSON key.';
    }
    document.getElementById('originalAnswerKeyPdfStatus').textContent = exam.originalAnswerKeyPdfUploaded
      ? 'Uploaded: ' + (exam.originalAnswerKeyPdfName || 'answer-key.pdf')
      : 'No original answer key PDF uploaded yet.';
    refreshReportTemplateSelector(true);
    document.getElementById('questionSectionOrderJson').value = exam.questionSectionOrderJson || '[]';
    document.getElementById('questionMarkPriorityBySectionJson').value = exam.questionMarkPriorityBySectionJson || '{}';
    sectionPriorityOrder = parseJsonArray(exam.questionSectionOrderJson);
    markPriorityBySection = parseJsonObject(exam.questionMarkPriorityBySectionJson);
    loadQuestionOrderMetadata(exam);
    document.getElementById('portalNotification').value = exam.portalNotification || '';
    document.getElementById('portalNotifications').value = exam.portalNotifications || exam.portalNotification || '';
    global.PortalNoticeEditor.populate(exam);
    document.getElementById('portalNotificationBadgeStyle').value = exam.portalNotificationBadgeStyle === 'OLD' ? 'OLD' : 'NEW';
    document.getElementById('portalNoticeTabEnabled').checked = Boolean(exam.portalNoticeTabEnabled);
    document.getElementById('portalNoticeDocumentLabel').value = exam.portalNoticeDocumentLabel || '';
    document.getElementById('portalNoticePdfFile').value = '';
    document.getElementById('portalNoticePdfStatus').textContent = exam.portalNoticePdfUploaded
      ? 'Uploaded: ' + (exam.portalNoticePdfName || 'notice.pdf')
      : 'No notice PDF uploaded yet.';
    document.getElementById('portalNotificationTextColor').value = exam.portalNotificationTextColor || '#3d5549';
    document.getElementById('portalNotificationBackgroundColor').value = exam.portalNotificationBackgroundColor || '#f0f5f2';
    document.getElementById('portalNotificationFontSize').value = exam.portalNotificationFontSize || '15px';
    document.getElementById('portalWelcomeText').value = exam.portalWelcomeText || '';
    document.getElementById('portalHeaderTitle').value = exam.portalHeaderTitle || '';
    document.getElementById('portalHeaderSubtitle').value = exam.portalHeaderSubtitle || '';
    document.getElementById('portalHeaderTextColor').value = exam.portalHeaderTextColor || '#202124';
    document.getElementById('portalHeaderBackgroundColor').value = exam.portalHeaderBackgroundColor || '#ffffff';
    document.getElementById('portalHeroImageUrl').value = exam.portalHeroImageUrl || '';
    document.getElementById('portalTopBannerImageUrl').value = exam.portalTopBannerImageUrl || '';
    document.getElementById('portalPageBackgroundImageUrl').value = exam.portalPageBackgroundImageUrl || '';
    document.getElementById('portalWelcomeTextColor').value = exam.portalWelcomeTextColor || '#60676d';
    document.getElementById('examUiHeaderTitle').value = exam.examUiHeaderTitle || '';
    document.getElementById('examUiHeaderSubtitle').value = exam.examUiHeaderSubtitle || '';
    document.getElementById('examUiBannerImageUrl').value = exam.examUiBannerImageUrl || '';
    document.getElementById('portalHeroImageFile').value = '';
    document.getElementById('portalTopBannerImageFile').value = '';
    document.getElementById('portalPageBackgroundImageFile').value = '';
    document.getElementById('examUiBannerImageFile').value = '';
    renderImagePreview('portalHeroPreview', exam.portalHeroImageUrl, 'No welcome card image uploaded yet.');
    renderImagePreview('portalTopBannerPreview', exam.portalTopBannerImageUrl, 'No top strip banner uploaded yet.');
    renderImagePreview('portalPageBackgroundPreview', exam.portalPageBackgroundImageUrl, 'No page background image uploaded yet.');
    renderImagePreview('examUiBannerPreview', exam.examUiBannerImageUrl, 'No exam UI banner uploaded. Exam header will stay plain dark gray.');
    document.getElementById('notificationSenderEmail').value = exam.notificationSenderEmail || '';
    const senderPasswordInput = document.getElementById('notificationSenderAppPassword');
    const senderPasswordStatus = document.getElementById('notificationSenderPasswordStatus');
    if (senderPasswordInput) {
      senderPasswordInput.value = '';
      senderPasswordInput.placeholder = exam.notificationSenderAppPasswordSaved
        ? 'Saved - leave blank to keep it'
        : 'Paste Google app password';
    }
    if (senderPasswordStatus) {
      senderPasswordStatus.textContent = exam.notificationSenderAppPasswordSaved
        ? 'Sender app password is saved. Enter a new one only when rotating credentials.'
        : 'No sender app password saved. Gmail sending will use server MAIL_PASSWORD fallback if configured.';
    }
    document.getElementById('registrationEmailBody').value = exam.registrationEmailBody || '';
    renderStreams(exam.streams || []);
    renderCustomCandidateFields(exam.customFields || []);
    const enabled = new Set([...(exam.enabledFields || []), ...mandatoryFields]);
    document.querySelectorAll('input[name="candidateField"]').forEach((input) => {
      input.checked = enabled.has(input.value);
    });
    document.querySelectorAll('input[name="candidateRequiredField"]').forEach((input) => {
      const configured = exam.candidateRequiredFields || {};
      input.checked = mandatoryFields.has(input.value) || configured[input.value] === 'true';
    });
    document.querySelectorAll('input[name="candidateCorrectionField"]').forEach((input) => {
      const configured = exam.candidateCorrectionFields || {};
      input.checked = configured[input.value] === 'true';
    });
    document.querySelectorAll('[data-field-step]').forEach((select) => {
      const configured = exam.candidateFieldSteps || {};
      select.value = configured[select.dataset.fieldStep] || defaultCandidateFieldStep(select.dataset.fieldStep);
    });
    renderAllowedCountryOptions(exam.allowedCountryIso2s || []);
    syncFieldWorkflowMode();
    updateExamOpsStatus(exam);
    syncReportDesignerStudioLink();
    syncReportDesignerStudioEmbed();
    updateWorkspaceState();
  }

  function updateExamOpsStatus(examOverride) {
    const exam = examOverride || activeExam();
    const paperReady = Boolean(exam && exam.questionPaperUploaded);
    const lobbyReady = Boolean(document.getElementById('examLobbyEnabled').checked);
    const launchReady = Boolean(document.getElementById('examLaunchEnabled').checked);
    const submitReady = Boolean(document.getElementById('candidateSubmitEnabled').checked);
    const responsesReleased = document.getElementById('responsesReleased').checked;
    const resultReleased = document.getElementById('resultReleased').checked;
    const scoreCardPdfReleased = document.getElementById('scoreCardPdfReleased').checked;
    const originalQuestionPaperReleased = document.getElementById('originalQuestionPaperReleased').checked;
    const originalAnswerKeyReleased = document.getElementById('originalAnswerKeyReleased').checked;
    document.getElementById('examOpsStatus').innerHTML = [
      statusChip('Paper ' + (paperReady ? 'ready' : 'missing'), paperReady),
      statusChip('Lobby ' + (lobbyReady ? 'enabled' : 'off'), lobbyReady),
      statusChip('Launch ' + (launchReady ? 'enabled' : 'blocked'), launchReady),
      statusChip('Submit ' + (submitReady ? 'manual' : 'locked'), submitReady, !submitReady),
      statusChip('Responses ' + (responsesReleased ? 'released' : 'hidden'), responsesReleased, !responsesReleased),
      statusChip('Result ' + (resultReleased ? 'released' : 'hidden'), resultReleased, !resultReleased),
      statusChip('Score PDF ' + (scoreCardPdfReleased ? 'released' : 'hidden'), scoreCardPdfReleased, !scoreCardPdfReleased),
      statusChip('QP PDF ' + (originalQuestionPaperReleased ? 'released' : 'hidden'), originalQuestionPaperReleased, !originalQuestionPaperReleased),
      statusChip('AK PDF ' + (originalAnswerKeyReleased ? 'released' : 'hidden'), originalAnswerKeyReleased, !originalAnswerKeyReleased)
    ].join('');
  }

  function statusChip(text, ok, warn = false) {
    return '<div class="status-chip ' + (ok ? 'on' : (warn ? 'warn' : '')) + '">' + escapeHtml(text) + '</div>';
  }

  function clearPortalNotifications() {
    global.PortalNoticeEditor.clear();
    document.getElementById('portalNotifications').value = '';
    document.getElementById('portalNotification').value = '';
    document.getElementById('configResult').innerHTML = 'Notifications cleared. Click <b>Save Registration Config</b> to publish the change.';
  }

  function fileToDataUrl(file) {
    if (!file) return Promise.resolve('');
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result || ''));
      reader.onerror = () => reject(new Error('Could not read selected image.'));
      reader.readAsDataURL(file);
    });
  }

  async function persistedDataUrl(fileInput, hiddenInput) {
    return global.ScoreCardSettings.readAsset(fileInput, hiddenInput);
  }

  function parseJsonArray(value) {
    try {
      const parsed = JSON.parse(value || '[]');
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }

  function parseJsonObject(value) {
    try {
      const parsed = JSON.parse(value || '{}');
      return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {};
    } catch {
      return {};
    }
  }

  async function handleQuestionPaperPicked(event) {
    updateExamOpsStatus();
    const file = event.currentTarget.files && event.currentTarget.files[0];
    const status = document.getElementById('questionPaperStatus');
    if (!file) {
      const examCode = document.getElementById('configExamCode').value;
      const exam = exams.find((item) => item.examCode === examCode);
      if (exam && exam.questionPaperUploaded) {
        status.textContent = 'Uploaded: ' + (exam.questionPaperOriginalName || 'question paper') + ' | Questions: ' + (exam.questionPaperQuestionCount || 0);
        await loadQuestionOrderMetadata(exam);
      } else {
        questionOrderMeta = [];
        sectionPriorityOrder = [];
        markPriorityBySection = {};
        syncQuestionOrderInputs();
        document.getElementById('questionOrderControls').textContent = 'Upload a question paper JSON to detect sections and marks sorting choices.';
        status.textContent = 'No question paper selected yet.';
      }
      return;
    }
      status.textContent = 'Checking question paper...';
    try {
      const text = await file.text();
      const parsed = JSON.parse(text);
      const localMeta = metadataFromQuestionPaperJson(parsed);
      questionOrderMeta = localMeta.sections || [];
      const activeOrder = parseJsonArray(document.getElementById('questionSectionOrderJson').value);
      sectionPriorityOrder = activeOrder.filter((name) => questionOrderMeta.some((section) => section.name === name));
      markPriorityBySection = pruneMarkPriorityMap(parseJsonObject(document.getElementById('questionMarkPriorityBySectionJson').value));
      renderQuestionOrderControls();
      const marksDetected = (localMeta.sections || []).some((section) => Array.isArray(section.marks) && section.marks.length);
      status.innerHTML = '<span class="ok">Question paper ready</span>: '
        + escapeHtml(file.name)
        + ' | Questions: ' + escapeHtml(localMeta.questionCount || 0)
        + (marksDetected ? '' : ' | Marks not available in this JSON; question ordering will stay in JSON order inside each section')
        + '. Click <b>Upload Paper</b> to save it to this exam.';
    } catch (error) {
      questionOrderMeta = [];
      sectionPriorityOrder = [];
      markPriorityBySection = {};
      syncQuestionOrderInputs();
      document.getElementById('questionOrderControls').innerHTML = '<span class="error">Could not read local question paper</span><br>' + escapeHtml(error.message || 'Invalid JSON file.');
      status.innerHTML = '<span class="error">Question paper JSON is invalid</span>';
    }
  }

  function metadataFromQuestionPaperJson(data) {
    const questions = Array.isArray(data && data.questions) ? data.questions : [];
    const sectionsMap = new Map();
    questions.forEach((question) => {
      const sectionName = firstNonBlank(
        question && question.section,
        question && question.section_name,
        question && question.sectionName,
        question && question.subject,
        question && question.subject_name,
        question && question.subjectName,
        deepFieldText(question, ['sectionCode', 'section_code', 'sectionTitle', 'section_title', 'subjectCode', 'subject_code', 'subjectTitle', 'subject_title'])
      ) || 'Section 1';
      const section = sectionsMap.get(sectionName) || { name: sectionName, questionCount: 0, marks: [] };
      section.questionCount += 1;
      const mark = cleanMarkValue(firstNonBlank(
        question && question.marks,
        question && question.max_marks,
        question && question.maxMarks,
        question && question.positive_marks,
        question && question.positiveMarks,
        question && question.mark,
        question && question.positive_mark,
        question && question.positiveMark,
        question && question.question_marks,
        question && question.questionMarks,
        question && question.maximum_marks,
        question && question.maximumMarks,
        question && question.weight,
        question && question.weightage,
        question && question.score,
        deepFieldText(question, ['marks', 'max_marks', 'maxMarks', 'positive_marks', 'positiveMarks', 'positive_mark', 'positiveMark', 'question_marks', 'questionMarks', 'maximum_marks', 'maximumMarks', 'weight', 'weightage', 'score'])
      ));
      if (mark && !section.marks.includes(mark)) {
        section.marks.push(mark);
      }
      sectionsMap.set(sectionName, section);
    });
    const sections = [...sectionsMap.values()].map((section) => ({
      ...section,
      marks: section.marks.slice().sort(compareMarks)
    }));
    return { questionCount: questions.length, sections };
  }

  function deepFieldText(node, keys, depth = 0) {
    if (!node || depth > 4) return '';
    if (Array.isArray(node)) {
      for (const item of node) {
        const found = deepFieldText(item, keys, depth + 1);
        if (found) return found;
      }
      return '';
    }
    if (typeof node !== 'object') return '';
    for (const key of keys) {
      const direct = firstNonBlank(node[key]);
      if (direct) return direct;
    }
    for (const value of Object.values(node)) {
      if (value && typeof value === 'object') {
        const found = deepFieldText(value, keys, depth + 1);
        if (found) return found;
      }
    }
    return '';
  }

  function firstNonBlank(...values) {
    for (const value of values) {
      const text = String(value ?? '').trim();
      if (text) return text;
    }
    return '';
  }

  function cleanMarkValue(value) {
    let text = String(value ?? '').trim();
    if (!text) return '';
    text = text
      .replace(/Marks:/gi, '')
      .replace(/Mark:/gi, '')
      .replace(/\+/g, '')
      .replace(/[mM]\b/g, '')
      .trim();
    if (/^(none|nil|na|n\/a)$/i.test(text)) {
      return '0';
    }
    const number = Number(text);
    if (!Number.isNaN(number)) {
      return Number.isInteger(number) ? String(number) : String(number).replace(/\.?0+$/, '');
    }
    return text;
  }

  function compareMarks(left, right) {
    const leftNumber = Number(left);
    const rightNumber = Number(right);
    const leftIsNumber = !Number.isNaN(leftNumber);
    const rightIsNumber = !Number.isNaN(rightNumber);
    if (leftIsNumber && rightIsNumber) {
      return leftNumber - rightNumber;
    }
    if (leftIsNumber) return -1;
    if (rightIsNumber) return 1;
    return String(left).localeCompare(String(right), undefined, { sensitivity: 'base' });
  }

  async function loadQuestionOrderMetadata(exam) {
    const box = document.getElementById('questionOrderControls');
    if (!exam || !exam.examCode || !exam.questionPaperUploaded) {
      questionOrderMeta = [];
      sectionPriorityOrder = [];
      markPriorityBySection = {};
      document.getElementById('questionSectionOrderJson').value = '[]';
      document.getElementById('questionMarkPriorityBySectionJson').value = '{}';
      box.textContent = 'Upload a question paper JSON to detect sections and marks sorting choices.';
      return;
    }
    box.textContent = 'Loading question order controls...';
    try {
      const meta = await jsonRequest('/api/exams/' + encodeURIComponent(exam.examCode) + '/question-paper-metadata', 'GET');
      questionOrderMeta = meta.sections || [];
      sectionPriorityOrder = parseJsonArray(exam.questionSectionOrderJson).filter((name) => questionOrderMeta.some((section) => section.name === name));
      markPriorityBySection = pruneMarkPriorityMap(parseJsonObject(
        exam.questionMarkPriorityBySectionJson || document.getElementById('questionMarkPriorityBySectionJson').value
      ));
      renderQuestionOrderControls(exam);
    } catch (error) {
      questionOrderMeta = [];
      markPriorityBySection = {};
      box.innerHTML = '<span class="error">Could not load question order metadata</span>\n' + escapeHtml(error.message);
    }
  }

  function renderQuestionOrderControls() {
    const box = document.getElementById('questionOrderControls');
    if (!questionOrderMeta.length) {
      box.textContent = 'No sections found in uploaded paper.';
      return;
    }
    box.innerHTML = '<div class="hint">Section priority: tick in preferred order; untick to clear and renumber. Marks priority controls question ordering inside each section.</div>'
      + '<div class="hint">Marks priority per section follows the Python viewer logic: JSON order, forward order, or reverse order.</div>'
      + questionOrderMeta.map((section) => {
        const rank = sectionPriorityOrder.indexOf(section.name);
        const marks = (section.marks || []).filter(Boolean);
        const selectedMarks = markPriorityBySection[section.name] || '';
        const options = markPriorityOptions(marks);
        const questionCount = Number(section.questionCount || 0);
        const questionLabel = questionCount === 1 ? '1 question' : (questionCount + ' questions');
        const marksLabel = marks.length ? ('Marks found: ' + marks.join(', ')) : 'Marks found: not detected';
        return '<div class="order-row">'
          + '<span class="priority-badge">' + (rank >= 0 ? rank + 1 : '-') + '</span>'
          + '<div class="order-meta">'
          + '<label><input type="checkbox" data-section-priority="' + escapeHtml(section.name) + '"' + (rank >= 0 ? ' checked' : '') + '> ' + escapeHtml(section.name) + '</label>'
          + '<div class="order-stats">' + escapeHtml(questionLabel + ' · ' + marksLabel) + '</div>'
          + '</div>'
          + '<select data-mark-priority="' + escapeHtml(section.name) + '">'
          + options.map((option) => '<option value="' + escapeHtml(option.value) + '"' + (String(selectedMarks) === String(option.value) ? ' selected' : '') + '>' + escapeHtml(option.label) + '</option>').join('')
          + '</select>'
          + '</div>';
      }).join('');
    box.querySelectorAll('[data-section-priority]').forEach((input) => {
      input.addEventListener('change', () => {
        const section = input.dataset.sectionPriority;
        sectionPriorityOrder = sectionPriorityOrder.filter((name) => name !== section);
        if (input.checked) sectionPriorityOrder.push(section);
        syncQuestionOrderInputs();
        renderQuestionOrderControls();
      });
    });
    box.querySelectorAll('[data-mark-priority]').forEach((select) => {
      select.addEventListener('change', () => {
        if (select.value) {
          markPriorityBySection[select.dataset.markPriority] = select.value;
        } else {
          delete markPriorityBySection[select.dataset.markPriority];
        }
        syncQuestionOrderInputs();
      });
    });
    syncQuestionOrderInputs();
  }

  function syncQuestionOrderInputs() {
    document.getElementById('questionSectionOrderJson').value = JSON.stringify(sectionPriorityOrder);
    document.getElementById('questionMarkPriorityBySectionJson').value = JSON.stringify(pruneMarkPriorityMap(markPriorityBySection));
  }

  function pruneMarkPriorityMap(priorityMap) {
    const out = {};
    questionOrderMeta.forEach((section) => {
      const value = String((priorityMap || {})[section.name] || '').trim();
      if (value && value !== 'JSON order') {
        out[section.name] = value;
      }
    });
    return out;
  }

  function markPriorityOptions(marks) {
    const cleaned = [...new Set((marks || []).map((mark) => String(mark || '').trim()).filter(Boolean))];
    const options = [{ value: '', label: 'JSON order' }];
    if (!cleaned.length) return options;
    if (cleaned.length === 1) {
      options.push({ value: cleaned[0], label: cleaned[0] + ' marks' });
      return options;
    }
    const forward = cleaned.join(',');
    const reverse = [...cleaned].reverse().join(',');
    options.push({ value: forward, label: cleaned.join(' then ') + ' marks' });
    if (reverse !== forward) {
      options.push({ value: reverse, label: [...cleaned].reverse().join(' then ') + ' marks' });
    }
    return options;
  }

  function normalizeCssSize(value) {
    const trimmed = String(value || '').trim();
    if (/^\d{1,2}(\.\d+)?(px|rem|em)$/.test(trimmed)) return trimmed;
    if (/^\d{1,2}$/.test(trimmed)) return trimmed + 'px';
    return '15px';
  }

  function bindPortalImageControl(name, fileId, urlId, previewId, emptyText) {
    document.querySelector('[data-clear-image="' + name + '"]').addEventListener('click', () => {
      document.getElementById(fileId).value = '';
      renderImagePreview(previewId, document.getElementById(urlId).value, emptyText);
    });
    document.getElementById(fileId).addEventListener('change', (event) => {
      const file = event.currentTarget.files[0];
      renderImagePreview(previewId, file ? URL.createObjectURL(file) : document.getElementById(urlId).value, emptyText);
    });
  }

  async function uploadPortalImageIfPicked(examCode, placement, fileInput, urlInput) {
    const file = fileInput.files[0];
    if (!file) return null;
    const formData = new FormData();
    formData.append('file', file);
    const uploadResponse = await fetch('/api/exams/' + encodeURIComponent(examCode) + '/portal-image/' + placement, {
      method: 'POST',
      body: formData
    });
    const uploadText = await uploadResponse.text();
    const uploadData = uploadText ? JSON.parse(uploadText) : {};
    if (!uploadResponse.ok) throw new Error(uploadData.message || uploadData.detail || uploadText || uploadResponse.statusText);
    const fieldName = placement === 'top-banner'
      ? 'portalTopBannerImageUrl'
      : (placement === 'page-background'
        ? 'portalPageBackgroundImageUrl'
        : (placement === 'exam-ui-banner' ? 'examUiBannerImageUrl' : 'portalHeroImageUrl'));
    urlInput.value = uploadData[fieldName] || '';
    return uploadData;
  }

  function renderImagePreview(previewId, url, emptyText) {
    const preview = document.getElementById(previewId);
    if (!url) {
      preview.textContent = emptyText;
      return;
    }
    preview.innerHTML = '<img alt="Portal banner preview" src="' + escapeHtml(url) + '">';
  }

  async function uploadQuestionPaper() {
    const examCode = document.getElementById('configExamCode').value;
    const fileInput = document.getElementById('questionPaperFile');
    const status = document.getElementById('questionPaperStatus');
    if (!examCode) {
      status.textContent = 'Select an exam first.';
      return;
    }
    if (!fileInput.files[0]) {
      status.textContent = 'Choose a question paper JSON file first.';
      return;
    }
    status.textContent = 'Uploading question paper...';
    try {
      const formData = new FormData();
      formData.append('file', fileInput.files[0]);
      const response = await fetch('/api/exams/' + encodeURIComponent(examCode) + '/question-paper', {
        method: 'POST',
        body: formData
      });
      const text = await response.text();
      const data = text ? JSON.parse(text) : {};
      if (!response.ok) throw new Error(data.detail || data.message || text || response.statusText);
      status.innerHTML = '<span class="ok">Question paper uploaded</span>: '
        + escapeHtml(data.questionPaperOriginalName || fileInput.files[0].name)
        + ' | Questions: ' + escapeHtml(data.questionPaperQuestionCount || 0);
      await loadExams();
      const refreshedExam = exams.find((item) => item.examCode === examCode) || data;
      updateExamOpsStatus(refreshedExam);
      await loadQuestionOrderMetadata(refreshedExam);
      if (typeof validateActivePaperKey === 'function') await validateActivePaperKey();
      await refreshDatabaseView();
    } catch (error) {
      status.innerHTML = '<span class="error">Question paper upload failed</span>\n' + escapeHtml(error.message);
    }
  }

  async function clearQuestionPaper() {
    const examCode = document.getElementById('configExamCode').value;
    const status = document.getElementById('questionPaperStatus');
    if (!examCode) {
      status.textContent = 'Select an exam first.';
      return;
    }
    if (!confirm('Clear the uploaded server question paper for this exam? Candidate response records will not be deleted.')) {
      return;
    }
    status.textContent = 'Clearing server question paper...';
    try {
      const data = await jsonRequest('/api/exams/' + encodeURIComponent(examCode) + '/question-paper', 'DELETE');
      document.getElementById('questionPaperFile').value = '';
      status.innerHTML = '<span class="ok">Server question paper cleared</span>\\nUpload the correct assembled paper before launching/evaluating.';
      await loadExams();
      updateExamOpsStatus(data);
      resetQBankAssemblyUi('Server question paper cleared. Choose or assemble a fresh paper.');
      if (typeof validateActivePaperKey === 'function') await validateActivePaperKey();
      await refreshDatabaseView();
    } catch (error) {
      status.innerHTML = '<span class="error">Could not clear question paper</span>\\n' + escapeHtml(error.message);
    }
  }

  function resetQBankAssemblyUi(message) {
    const status = document.getElementById('qbankStatus');
    const inventory = document.getElementById('qbankInventory');
    const assemblyStatus = document.getElementById('qbankAssemblyStatus');
    if (status) status.textContent = message || 'No qbank analyzed yet.';
    if (inventory) inventory.textContent = 'Upload a qbank JSON to see subject, topic, marks, and difficulty availability.';
    ['assembleQBankPaper', 'downloadQBankPaper', 'downloadQBankKey', 'downloadQBankPaperPdf', 'downloadQBankKeyPdf', 'publishQBankAssembly'].forEach((id) => {
      const button = document.getElementById(id);
      if (button) button.disabled = true;
    });
    if (assemblyStatus) assemblyStatus.textContent = 'No published assembly.';
  }

  async function uploadOriginalQuestionPaperPdf() {
    const exam = activeExam();
    const status = document.getElementById('originalQuestionPaperPdfStatus');
    const fileInput = document.getElementById('originalQuestionPaperPdfFile');
    if (!exam) {
      status.textContent = 'Select an exam project first.';
      return;
    }
    if (!fileInput.files[0]) {
      status.textContent = 'Choose the original question paper PDF first.';
      return;
    }
    status.textContent = 'Uploading original question paper PDF...';
    try {
      const formData = new FormData();
      formData.append('file', fileInput.files[0]);
      const response = await fetch('/api/exams/' + encodeURIComponent(exam.examCode) + '/original-question-paper-pdf', {
        method: 'POST',
        body: formData
      });
      const text = await response.text();
      const data = text ? JSON.parse(text) : {};
      if (!response.ok) throw new Error(data.detail || data.message || text || response.statusText);
      status.innerHTML = '<span class="ok">Original question paper PDF uploaded</span>\n' + escapeHtml(data.originalQuestionPaperPdfName || fileInput.files[0].name);
      fileInput.value = '';
      await loadExams();
    } catch (error) {
      status.innerHTML = '<span class="error">Original question paper PDF upload failed</span>\n' + escapeHtml(error.message);
    }
  }

  async function uploadPortalNoticePdf() {
    const exam = activeExam();
    const status = document.getElementById('portalNoticePdfStatus');
    const fileInput = document.getElementById('portalNoticePdfFile');
    if (!exam) {
      status.textContent = 'Select an exam project first.';
      return;
    }
    if (!fileInput.files[0]) {
      status.textContent = 'Choose a notice PDF first.';
      return;
    }
    status.textContent = 'Uploading notice PDF...';
    try {
      const formData = new FormData();
      formData.append('file', fileInput.files[0]);
      const response = await fetch('/api/exams/' + encodeURIComponent(exam.examCode) + '/portal-notice-pdf', {
        method: 'POST',
        body: formData
      });
      const text = await response.text();
      const data = text ? JSON.parse(text) : {};
      if (!response.ok) throw new Error(data.detail || data.message || text || response.statusText);
      status.innerHTML = '<span class="ok">Notice PDF uploaded</span>\\n' + escapeHtml(data.portalNoticePdfName || fileInput.files[0].name);
      fileInput.value = '';
      await loadExams();
    } catch (error) {
      status.innerHTML = '<span class="error">Notice PDF upload failed</span>\\n' + escapeHtml(error.message);
    }
  }

  function toDateTimeLocal(value) {
    if (!value) return '';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '';
    const offsetMs = date.getTimezoneOffset() * 60000;
    return new Date(date.getTime() - offsetMs).toISOString().slice(0, 16);
  }

  function fromDateTimeLocal(value) {
    if (!value) return null;
    return new Date(value).toISOString();
  }

  function firstNotification(value) {
    return String(value || '').split(/\r?\n/).map((line) => line.trim()).find(Boolean) || '';
  }

  function bindRegistrationEvents() {
    if (registrationModuleBound) return;
    registrationModuleBound = true;
    document.getElementById('configExamCode').addEventListener('change', fillConfigFromSelectedExam);
    document.getElementById('registrationOpen').addEventListener('change', syncFieldWorkflowMode);
    document.getElementById('correctionsOpen').addEventListener('change', syncFieldWorkflowMode);
    document.getElementById('addStream').addEventListener('click', () => addStreamRow());
    document.getElementById('addCustomCandidateField').addEventListener('click', () => addCustomCandidateFieldRow());
    document.getElementById('countryRestrictionSearch').addEventListener('input', () => renderAllowedCountryOptions(selectedAllowedCountryIso2s()));
    document.getElementById('allowedCountryIso2s').addEventListener('change', () => {
      syncAllowedCountrySelectionFromDom();
      updateAllowedCountrySummary();
    });
    document.getElementById('uploadQuestionPaper').addEventListener('click', uploadQuestionPaper);
    document.getElementById('clearQuestionPaper').addEventListener('click', clearQuestionPaper);
    document.getElementById('questionPaperFile').addEventListener('change', handleQuestionPaperPicked);
    document.getElementById('clearPortalNotifications').addEventListener('click', clearPortalNotifications);
    ['examLobbyEnabled', 'examLaunchEnabled', 'candidateSubmitEnabled', 'responsesReleased', 'resultReleased', 'scoreCardPdfReleased', 'originalQuestionPaperReleased', 'originalAnswerKeyReleased', 'questionPaperFile'].forEach((id) => {
      document.getElementById(id).addEventListener('change', updateExamOpsStatus);
    });
    bindPortalImageControl('topBanner', 'portalTopBannerImageFile', 'portalTopBannerImageUrl', 'portalTopBannerPreview', 'No top strip banner uploaded yet.');
    bindPortalImageControl('hero', 'portalHeroImageFile', 'portalHeroImageUrl', 'portalHeroPreview', 'No welcome card image uploaded yet.');
    bindPortalImageControl('pageBackground', 'portalPageBackgroundImageFile', 'portalPageBackgroundImageUrl', 'portalPageBackgroundPreview', 'No page background image uploaded yet.');
    bindPortalImageControl('examUiBanner', 'examUiBannerImageFile', 'examUiBannerImageUrl', 'examUiBannerPreview', 'No exam UI banner uploaded. Exam header will stay plain dark gray.');
    document.getElementById('uploadOriginalQuestionPaperPdf').addEventListener('click', uploadOriginalQuestionPaperPdf);
    document.getElementById('uploadPortalNoticePdf').addEventListener('click', uploadPortalNoticePdf);
    document.getElementById('examConfigForm').addEventListener('submit', async (event) => {
      event.preventDefault();
      const form = event.currentTarget;
      const result = document.getElementById('configResult');
      const examCode = form.configExamCode.value;
      if (!examCode) {
        result.innerHTML = '<span class="error">No exam selected</span>';
        return;
      }
      if (!exams.some((exam) => exam.examCode === examCode)) {
        result.innerHTML = '<span class="error">Selected exam is stale</span>\nRefreshing exam list. Pick the exam again and save once more.';
        await loadExams();
        return;
      }
      const streams = selectedStreams();
      if (form.streamSelectionRequired.checked && !streams.some((stream) => stream.enabled)) {
        result.innerHTML = '<span class="error">Add at least one enabled stream before saving stream-based registration.</span>';
        return;
      }
      if (form.answerKeyChallengeEnabled.checked) {
        const opensAt = form.answerKeyChallengeOpensAt.value;
        const closesAt = form.answerKeyChallengeClosesAt.value;
        if (!opensAt || !closesAt) {
          result.innerHTML = '<span class="error">Set both opening and closing times for the answer-key challenge window.</span>';
          return;
        }
        if (new Date(closesAt).getTime() <= new Date(opensAt).getTime()) {
          result.innerHTML = '<span class="error">Challenge closing time must be later than its opening time.</span>';
          return;
        }
      }
      result.textContent = 'Saving registration config...';
      try {
        const enabledFields = [...document.querySelectorAll('input[name="candidateField"]:checked')].map((input) => input.value);
        const reportCardInstituteLogoDataUrl = await persistedDataUrl(form.reportCardInstituteLogoFile, form.reportCardInstituteLogoDataUrl);
        const reportCardWatermarkLogoDataUrl = await persistedDataUrl(form.reportCardWatermarkLogoFile, form.reportCardWatermarkLogoDataUrl);
        const reportCardAuthoritySignatureDataUrl = await persistedDataUrl(form.reportCardAuthoritySignatureFile, form.reportCardAuthoritySignatureDataUrl);
        const portalNotices = global.PortalNoticeEditor.serialize();
        const data = await jsonRequest('/api/exams/' + encodeURIComponent(examCode) + '/registration-config', 'PATCH', {
          portalNotices,
          registrationOpen: form.registrationOpen.checked,
          correctionsOpen: form.correctionsOpen.checked,
          streamSelectionRequired: form.streamSelectionRequired.checked,
          streams,
          enabledFields,
          portalNotification: firstNotification(form.portalNotifications.value),
          portalNotifications: form.portalNotifications.value.trim(),
          portalNotificationBadgeStyle: form.portalNotificationBadgeStyle.value,
          portalNoticeTabEnabled: form.portalNoticeTabEnabled.checked,
          portalNoticeDocumentLabel: form.portalNoticeDocumentLabel.value.trim(),
          portalWelcomeText: form.portalWelcomeText.value.trim(),
          portalSupportContent: global.PortalSupportSettings.serialize(),
          portalHeaderTitle: form.portalHeaderTitle.value.trim(),
          portalHeaderSubtitle: form.portalHeaderSubtitle.value.trim(),
          portalHeaderTextColor: form.portalHeaderTextColor.value,
          portalHeaderBackgroundColor: form.portalHeaderBackgroundColor.value,
          portalNotificationTextColor: form.portalNotificationTextColor.value,
          portalNotificationBackgroundColor: form.portalNotificationBackgroundColor.value,
          portalNotificationFontSize: normalizeCssSize(form.portalNotificationFontSize.value),
          portalHeroImageUrl: form.portalHeroImageUrl.value.trim(),
          portalTopBannerImageUrl: form.portalTopBannerImageUrl.value.trim(),
          portalPageBackgroundImageUrl: form.portalPageBackgroundImageUrl.value.trim(),
          portalWelcomeTextColor: form.portalWelcomeTextColor.value,
          examUiHeaderTitle: form.examUiHeaderTitle.value.trim(),
          examUiHeaderSubtitle: form.examUiHeaderSubtitle.value.trim(),
          examUiBannerImageUrl: form.examUiBannerImageUrl.value.trim(),
          questionSectionOrderJson: form.questionSectionOrderJson.value || '[]',
          questionMarkPriorityBySectionJson: form.questionMarkPriorityBySectionJson.value || '{}',
          customFields: selectedCustomCandidateFields(),
          candidateFieldSteps: selectedCandidateFieldSteps(),
          candidateRequiredFields: selectedCandidateRequiredFields(),
          candidateCorrectionFields: selectedCandidateCorrectionFields(),
          allowedCountryIso2s: selectedAllowedCountryIso2s(),
          registrationEmailBody: form.registrationEmailBody.value.trim(),
          notificationSenderEmail: form.notificationSenderEmail.value.trim(),
          notificationSenderAppPassword: form.notificationSenderAppPassword ? form.notificationSenderAppPassword.value.trim() : '',
          reportCardInstituteLogoDataUrl,
          reportCardWatermarkLogoDataUrl,
          reportCardAuthoritySignatureDataUrl,
          reportCardAuthorityName: form.reportCardAuthorityName.value.trim(),
          reportCardAuthorityDesignation: form.reportCardAuthorityDesignation.value.trim(),
          reportCardValidityText: form.reportCardValidityText.value.trim(),
          reportCardAppearance: await global.ScoreCardSettings.serialize(),
          examStartAt: fromDateTimeLocal(form.examStartAt.value),
          examLobbyEnabled: form.examLobbyEnabled.checked,
          examLaunchEnabled: form.examLaunchEnabled.checked,
          candidateSubmitEnabled: form.candidateSubmitEnabled.checked,
          answerKeyChallengeEnabled: form.answerKeyChallengeEnabled.checked,
          answerKeyChallengeOpensAt: fromDateTimeLocal(form.answerKeyChallengeOpensAt.value),
          answerKeyChallengeClosesAt: fromDateTimeLocal(form.answerKeyChallengeClosesAt.value),
          optionRandomizationEnabled: form.optionRandomizationEnabled.checked,
          responsesReleased: form.responsesReleased.checked,
          resultReleased: form.resultReleased.checked,
          scoreCardPdfReleased: form.scoreCardPdfReleased.checked,
          originalQuestionPaperReleased: form.originalQuestionPaperReleased.checked,
          originalAnswerKeyReleased: form.originalAnswerKeyReleased.checked,
          loginOpensMinutesBeforeStart: Number(form.loginOpensMinutesBeforeStart.value || 30),
          loginClosesMinutesBeforeStart: Number(form.loginClosesMinutesBeforeStart.value || 15),
          desktopEntryTimerEnabled: form.desktopEntryTimerEnabled.checked,
          desktopEntryDelaySeconds: Number(form.desktopEntryDelaySeconds.value || 0),
          desktopCaptureProtectionEnabled: form.desktopCaptureProtectionEnabled.checked,
          examReadinessTimerEnabled: form.examReadinessTimerEnabled.checked,
          examReadinessDurationSeconds: Number(form.examReadinessDurationSeconds.value || 0)
        });
        await uploadPortalImageIfPicked(examCode, 'top-banner', form.portalTopBannerImageFile, form.portalTopBannerImageUrl);
        await uploadPortalImageIfPicked(examCode, 'welcome', form.portalHeroImageFile, form.portalHeroImageUrl);
        await uploadPortalImageIfPicked(examCode, 'page-background', form.portalPageBackgroundImageFile, form.portalPageBackgroundImageUrl);
        await uploadPortalImageIfPicked(examCode, 'exam-ui-banner', form.examUiBannerImageFile, form.examUiBannerImageUrl);
        result.innerHTML = '<span class="ok">Registration config saved</span>\n'
          + 'Exam: ' + data.examCode + ' - ' + data.name + '\n'
          + 'Registration: ' + (data.registrationOpen ? 'OPEN' : 'CLOSED') + '\n'
          + 'Corrections: ' + (data.correctionsOpen ? 'OPEN' : 'CLOSED') + '\n'
          + 'Streams: ' + ((data.streams || []).map((stream) => stream.shortCode + '-' + stream.fullName + (stream.enabled ? '' : ' [disabled]')).join(', ') || '-') + '\n'
          + 'Fields: ' + (data.enabledFields || []).join(', ');
        await loadExams();
        await refreshDatabaseView();
      } catch (error) {
        result.innerHTML = '<span class="error">Config save failed</span>\n' + error.message;
      }
    });
  }

  global.initAdminRegistrationModule = function initAdminRegistrationModule() {
    global.renderCandidateFields = renderCandidateFields;
    global.renderCustomCandidateFields = renderCustomCandidateFields;
    global.selectedCustomCandidateFields = selectedCustomCandidateFields;
    global.defaultCandidateFieldStep = defaultCandidateFieldStep;
    global.selectedCandidateFieldSteps = selectedCandidateFieldSteps;
    global.selectedCandidateRequiredFields = selectedCandidateRequiredFields;
    global.selectedCandidateCorrectionFields = selectedCandidateCorrectionFields;
    global.addStreamRow = addStreamRow;
    global.renderStreams = renderStreams;
    global.selectedStreams = selectedStreams;
    global.renderAllowedCountryOptions = renderAllowedCountryOptions;
    global.selectedAllowedCountryIso2s = selectedAllowedCountryIso2s;
    global.updateAllowedCountrySummary = updateAllowedCountrySummary;
    global.fillConfigFromSelectedExam = fillConfigFromSelectedExam;
    global.updateExamOpsStatus = updateExamOpsStatus;
    global.statusChip = statusChip;
    global.clearPortalNotifications = clearPortalNotifications;
    global.parseJsonArray = parseJsonArray;
    global.parseJsonObject = parseJsonObject;
    global.handleQuestionPaperPicked = handleQuestionPaperPicked;
    global.metadataFromQuestionPaperJson = metadataFromQuestionPaperJson;
    global.deepFieldText = deepFieldText;
    global.firstNonBlank = firstNonBlank;
    global.cleanMarkValue = cleanMarkValue;
    global.compareMarks = compareMarks;
    global.loadQuestionOrderMetadata = loadQuestionOrderMetadata;
    global.renderQuestionOrderControls = renderQuestionOrderControls;
    global.syncQuestionOrderInputs = syncQuestionOrderInputs;
    global.pruneMarkPriorityMap = pruneMarkPriorityMap;
    global.markPriorityOptions = markPriorityOptions;
    global.normalizeCssSize = normalizeCssSize;
    global.bindPortalImageControl = bindPortalImageControl;
    global.uploadPortalImageIfPicked = uploadPortalImageIfPicked;
    global.renderImagePreview = renderImagePreview;
    global.uploadQuestionPaper = uploadQuestionPaper;
    global.uploadOriginalQuestionPaperPdf = uploadOriginalQuestionPaperPdf;
    global.uploadPortalNoticePdf = uploadPortalNoticePdf;
    global.toDateTimeLocal = toDateTimeLocal;
    global.fromDateTimeLocal = fromDateTimeLocal;
    bindRegistrationEvents();
    renderCandidateFields();
    renderAllowedCountryOptions();
  };
})(window);
