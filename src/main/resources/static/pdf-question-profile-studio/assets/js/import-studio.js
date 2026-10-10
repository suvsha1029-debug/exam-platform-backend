(function () {
  const LEGACY_FRAME = {
    question: { canvasWidth: 640, baseHeight: 90, viewerWidth: 700, padX: 8, padY: 6, verticalAlign: 'top' },
    option: { canvasWidth: 500, baseHeight: 46, viewerWidth: 600, padX: 10, padY: 6, verticalAlign: 'middle' }
  };

  const TEMPLATE_PROFILES = {
    gate_bordered_v1: {
      id: 'gate_bordered_v1',
      label: 'GATE Bordered Table v1',
      description: 'Classic bordered table paper with a left question legend strip and option rows under the question body.',
      questionPrefix: 'Q.',
      optionStyle: 'paren-alpha',
      layoutType: 'bordered-table',
      questionLegendStripWidth: 72,
      optionLegendStripWidth: 34,
      questionBodyInset: { left: 10, top: 10, right: 14, bottom: 12 },
      optionBodyInset: { left: 8, top: 8, right: 12, bottom: 10 },
      pageIgnore: { top: 18, bottom: 12 },
      borderTrim: { x: 12, y: 8 },
      facts: ['Question number outside image', 'A/B/C/D outside image', 'Border lines trimmed']
    },
    gate_two_col_legend_v1: {
      id: 'gate_two_col_legend_v1',
      label: 'Two-Column Legend Sheet v1',
      description: 'Question body starts after a wider legend strip and options usually follow in clearer separated rows.',
      questionPrefix: 'Q.',
      optionStyle: 'alpha-dot',
      layoutType: 'two-col-legend',
      questionLegendStripWidth: 86,
      optionLegendStripWidth: 38,
      questionBodyInset: { left: 12, top: 10, right: 14, bottom: 12 },
      optionBodyInset: { left: 8, top: 8, right: 12, bottom: 10 },
      pageIgnore: { top: 20, bottom: 12 },
      borderTrim: { x: 14, y: 10 },
      facts: ['Legend strip wider', 'Best for A. B. C. D. rows', 'Content-only crop']
    },
    simple_vertical_v1: {
      id: 'simple_vertical_v1',
      label: 'Simple Vertical Option Block v1',
      description: 'Fallback for cleaner vertical layouts with fewer visible table borders and slightly looser crop padding.',
      questionPrefix: 'Q.',
      optionStyle: 'alpha-bracket',
      layoutType: 'simple-vertical',
      questionLegendStripWidth: 62,
      optionLegendStripWidth: 28,
      questionBodyInset: { left: 8, top: 8, right: 12, bottom: 10 },
      optionBodyInset: { left: 8, top: 8, right: 12, bottom: 10 },
      pageIgnore: { top: 16, bottom: 10 },
      borderTrim: { x: 8, y: 6 },
      facts: ['Loose border snapping', 'Vertical option flow', 'Best fallback profile']
    }
  };

  const state = {
    questions: [],
    selectedIndex: -1,
    selectedPdfFile: null,
    extractedPdf: null,
    activeProfileId: 'gate_bordered_v1'
  };

  const els = {
    pdfFile: document.getElementById('pdfFile'),
    choosePdfBtn: document.getElementById('choosePdfBtn'),
    uploadZone: document.getElementById('uploadZone'),
    sourceStatus: document.getElementById('sourceStatus'),
    templateName: document.getElementById('templateName'),
    templateProfile: document.getElementById('templateProfile'),
    profileSummary: document.getElementById('profileSummary'),
    questionPrefix: document.getElementById('questionPrefix'),
    questionIdPrefix: document.getElementById('questionIdPrefix'),
    optionIdPrefix: document.getElementById('optionIdPrefix'),
    optionStyle: document.getElementById('optionStyle'),
    exportTarget: document.getElementById('exportTarget'),
    parseBtn: document.getElementById('parseBtn'),
    resetBtn: document.getElementById('resetBtn'),
    questionList: document.getElementById('questionList'),
    questionCountPill: document.getElementById('questionCountPill'),
    warningCountPill: document.getElementById('warningCountPill'),
    editorTitle: document.getElementById('editorTitle'),
    editorMeta: document.getElementById('editorMeta'),
    sourceQuestionNo: document.getElementById('sourceQuestionNo'),
    generatedQuestionId: document.getElementById('generatedQuestionId'),
    questionTypeSingle: document.getElementById('questionTypeSingle'),
    questionMarksSingle: document.getElementById('questionMarksSingle'),
    questionNegMarksSingle: document.getElementById('questionNegMarksSingle'),
    questionSnippetPreview: document.getElementById('questionSnippetPreview'),
    optionFields: document.getElementById('optionFields'),
    questionWarnings: document.getElementById('questionWarnings'),
    prevQuestionBtn: document.getElementById('prevQuestionBtn'),
    nextQuestionBtn: document.getElementById('nextQuestionBtn'),
    examCode: document.getElementById('examCode'),
    sectionCode: document.getElementById('sectionCode'),
    questionTypeDefault: document.getElementById('questionTypeDefault'),
    questionMarksDefault: document.getElementById('questionMarksDefault'),
    questionNegMarksDefault: document.getElementById('questionNegMarksDefault'),
    exportJsonBtn: document.getElementById('exportJsonBtn'),
    copyJsonBtn: document.getElementById('copyJsonBtn'),
    exportPreview: document.getElementById('exportPreview')
  };

  function activeProfile() {
    return TEMPLATE_PROFILES[state.activeProfileId] || TEMPLATE_PROFILES.gate_bordered_v1;
  }

  function optionLegendLetters() {
    return ['A', 'B', 'C', 'D'];
  }

  function optionRegexForStyle(style) {
    if (style === 'alpha-dot') return /^([A-D])\.\s*/i;
    if (style === 'alpha-bracket') return /^\[([A-D])\]\s*/i;
    return /^\(([A-D])\)\s*/i;
  }

  function renderProfileOptions() {
    els.templateProfile.innerHTML = Object.values(TEMPLATE_PROFILES).map((profile) => (
      '<option value="' + escapeHtml(profile.id) + '">' + escapeHtml(profile.label) + '</option>'
    )).join('');
    els.templateProfile.value = state.activeProfileId;
  }

  function renderProfileSummary() {
    const profile = activeProfile();
    els.profileSummary.innerHTML =
      '<strong>' + escapeHtml(profile.label) + '</strong>' +
      '<p>' + escapeHtml(profile.description) + '</p>' +
      '<div class="profile-facts">' +
      profile.facts.map((fact) => '<span class="profile-fact">' + escapeHtml(fact) + '</span>').join('') +
      '</div>';
  }

  function applyProfile(profileId) {
    state.activeProfileId = TEMPLATE_PROFILES[profileId] ? profileId : 'gate_bordered_v1';
    const profile = activeProfile();
    els.questionPrefix.value = profile.questionPrefix;
    els.optionStyle.value = profile.optionStyle;
    if (!els.templateName.value.trim()) els.templateName.value = profile.label;
    renderProfileSummary();
    state.extractedPdf = null;
    if (state.selectedPdfFile) {
      setSourceStatus('Profile changed. Re-detect snippets for ' + state.selectedPdfFile.name + '.', 'warn');
    }
    refreshExportPreview();
  }

  function setSourceStatus(message, mode) {
    els.sourceStatus.textContent = message;
    els.sourceStatus.style.color =
      mode === 'error' ? '#9d2232' :
      mode === 'ok' ? '#15623d' :
      mode === 'warn' ? '#8a5b00' :
      '#4f6277';
  }

  function generatedOptionId(questionIndex, optionIndex) {
    const prefix = (els.optionIdPrefix.value || 'OPT').trim();
    return prefix + '-' + String(questionIndex + 1).padStart(3, '0') + '-' + optionLegendLetters()[optionIndex];
  }

  function buildQuestion(questionNo, questionIndex) {
    return {
      sourceQuestionNo: questionNo,
      generatedQuestionId: ((els.questionIdPrefix.value || 'QIMP').trim() || 'QIMP') + '-' + String(questionIndex + 1).padStart(4, '0'),
      type: els.questionTypeDefault.value || 'MCQ',
      marks: Number(els.questionMarksDefault.value || 1),
      negMarks: Number(els.questionNegMarksDefault.value || 0),
      questionImage: '',
      questionBaseImage: '',
      questionViewerImage: '',
      options: optionLegendLetters().map((letter, index) => ({
        legend: letter,
        optionId: generatedOptionId(questionIndex, index),
        image: '',
        baseImage: '',
        viewerImage: '',
        correct: false
      })),
      warnings: []
    };
  }

  async function extractStructuredPdf(file, profile) {
    if (!window.pdfjsLib) throw new Error('PDF reader library did not load.');
    if (pdfjsLib.GlobalWorkerOptions) {
      pdfjsLib.GlobalWorkerOptions.workerSrc = 'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js';
    }
    const buffer = await file.arrayBuffer();
    const pdf = await pdfjsLib.getDocument({ data: buffer }).promise;
    const structuredQuestions = [];
    const pageStats = [];
    for (let pageNumber = 1; pageNumber <= pdf.numPages; pageNumber += 1) {
      const page = await pdf.getPage(pageNumber);
      const viewport = page.getViewport({ scale: 2 });
      const canvas = document.createElement('canvas');
      canvas.width = Math.ceil(viewport.width);
      canvas.height = Math.ceil(viewport.height);
      const ctx = canvas.getContext('2d', { willReadFrequently: true });
      await page.render({ canvasContext: ctx, viewport }).promise;
      const textContent = await page.getTextContent();
      const normalizedItems = normalizePdfItems(textContent.items || [], viewport, profile);
      const cleanItems = normalizedItems.filter((item) => !item.isWatermark);
      const pageQuestions = detectStructuredQuestionsFromPage(cleanItems, canvas, pageNumber, profile);
      structuredQuestions.push(...pageQuestions);
      pageStats.push({ pageNumber, questions: pageQuestions.length });
    }
    return { questions: structuredQuestions, pages: pageStats, profileId: profile.id };
  }

  function normalizePdfItems(items, viewport, profile) {
    const ignoreTop = profile.pageIgnore?.top || 0;
    const ignoreBottom = profile.pageIgnore?.bottom || 0;
    return items.map((item) => {
      const text = String(item.str || '').replace(/\s+/g, ' ').trim();
      const transform = Array.isArray(item.transform) ? item.transform : [1, 0, 0, 1, 0, 0];
      const rendered = pdfjsLib.Util.transform(viewport.transform, transform);
      const x = Number(rendered[4] || 0);
      const baseline = Number(rendered[5] || 0);
      const height = Math.max(10, Math.hypot(rendered[2], rendered[3]) || (Number(item.height || 0) * viewport.scale) || 10);
      const width = Math.max(2, Math.abs(Number(item.width || 0)) * viewport.scale);
      const top = baseline - height;
      const rotationish = Math.abs(Number(transform[1] || 0)) > 0.25 || Math.abs(Number(transform[2] || 0)) > 0.25;
      const watermarkish = rotationish || (text.length >= 6 && width > 110 && height >= 12 && /^[A-Z0-9 .:_-]+$/i.test(text));
      return { text, x, top, bottom: baseline, width, height, isWatermark: watermarkish };
    }).filter((item) => (
      item.text &&
      item.top >= ignoreTop &&
      item.bottom <= (viewport.height - ignoreBottom)
    ));
  }

  function detectStructuredQuestionsFromPage(items, pageCanvas, pageNumber, profile) {
    const qPrefix = (profile.questionPrefix || 'Q.').trim();
    const qRegex = new RegExp('^' + qPrefix.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '\\s*(\\d+)\\b', 'i');
    const optionRegex = optionRegexForStyle(profile.optionStyle);
    const questionLabels = items.filter((item) => qRegex.test(item.text)).sort((a, b) => a.top - b.top);
    const questions = [];

    questionLabels.forEach((label, index) => {
      const nextLabel = questionLabels[index + 1];
      const regionTop = Math.max(0, label.top - (profile.questionBodyInset?.top || 10));
      const regionBottom = nextLabel ? nextLabel.top - 10 : pageCanvas.height - (profile.pageIgnore?.bottom || 8);
      const regionItems = items.filter((item) => item.top >= regionTop && item.top < regionBottom + 2);
      const optionLabels = regionItems.filter((item) => optionRegex.test(item.text)).sort((a, b) => a.top - b.top);
      const qMatch = label.text.match(qRegex);
      const questionNo = qMatch ? qMatch[1] : String(index + 1);
      const bodyCutoff = optionLabels.length ? optionLabels[0].top - 6 : regionBottom;
      const bodyLeft = label.x + (profile.questionLegendStripWidth || 56);
      const bodyItems = regionItems.filter((item) => item !== label && item.top >= label.top - 2 && item.top < bodyCutoff && item.x >= bodyLeft);

      const built = buildQuestion(questionNo, questions.length + state.questions.length);
      const questionRect = buildContentCropRect(
        bodyItems,
        {
          top: label.top,
          bottom: bodyCutoff,
          minLeft: bodyLeft,
          pageWidth: pageCanvas.width,
          inset: profile.questionBodyInset,
          trim: profile.borderTrim
        }
      );
      const questionImage = cropBlockImage(pageCanvas, questionRect, LEGACY_FRAME.question);
      built.questionImage = questionImage;
      built.questionBaseImage = questionImage;
      built.questionViewerImage = makeViewerSizedImage(questionImage, LEGACY_FRAME.question);

      built.options = built.options.map((option, optionIndex) => {
        const optionLabel = optionLabels.find((item) => extractOptionLetter(item.text, optionRegex) === option.legend);
        if (!optionLabel) {
          built.warnings.push('Option ' + option.legend + ' was not detected on page ' + pageNumber + '.');
          return option;
        }
        const nextOption = optionLabels.find((candidate) => candidate.top > optionLabel.top + 4);
        const optionTop = Math.max(optionLabel.top - 6, regionTop);
        const optionBottom = nextOption ? nextOption.top - 8 : regionBottom;
        const optionBodyLeft = optionLabel.x + (profile.optionLegendStripWidth || 24);
        const optionItems = regionItems.filter((item) => item.top >= optionTop && item.top < optionBottom && item.x >= optionBodyLeft);
        const optionRect = buildContentCropRect(
          optionItems,
          {
            top: optionTop,
            bottom: optionBottom,
            minLeft: optionBodyLeft,
            pageWidth: pageCanvas.width,
            inset: profile.optionBodyInset,
            trim: profile.borderTrim
          }
        );
        const optionImage = cropBlockImage(pageCanvas, optionRect, LEGACY_FRAME.option);
        return {
          ...option,
          image: optionImage,
          baseImage: optionImage,
          viewerImage: makeViewerSizedImage(optionImage, LEGACY_FRAME.option)
        };
      });

      built.pageNumber = pageNumber;
      built.profileId = profile.id;
      if (!questionImage) built.warnings.push('Question image crop could not be created.');
      if (built.options.filter((option) => option.image).length === 0) built.warnings.push('Option crops were not detected for this question.');
      questions.push(built);
    });

    return questions;
  }

  function extractOptionLetter(text, optionRegex) {
    const match = String(text || '').match(optionRegex);
    return match ? String(match[1] || '').toUpperCase() : '';
  }

  function buildContentCropRect(items, config) {
    const textItems = (items || []).filter(Boolean);
    const inset = config.inset || { left: 10, top: 8, right: 14, bottom: 10 };
    const trim = config.trim || { x: 0, y: 0 };
    const top = textItems.length ? Math.max(0, Math.min(...textItems.map((item) => item.top)) - inset.top + trim.y) : Math.max(0, config.top + trim.y);
    const bottom = textItems.length ? Math.max(top + 24, Math.max(...textItems.map((item) => item.bottom)) + inset.bottom - trim.y) : Math.max(top + 24, config.bottom - trim.y);
    const left = textItems.length ? Math.max(config.minLeft, Math.min(...textItems.map((item) => item.x)) - inset.left + trim.x) : Math.max(config.minLeft, config.minLeft + trim.x);
    const right = textItems.length
      ? Math.min(config.pageWidth, Math.max(...textItems.map((item) => item.x + item.width)) + inset.right - trim.x)
      : Math.min(config.pageWidth, left + 120);
    return { x: left, y: top, w: Math.max(40, right - left), h: Math.max(28, bottom - top) };
  }

  function cropBlockImage(pageCanvas, rect, frameSpec) {
    if (!rect || !rect.w || !rect.h) return '';
    const sx = Math.max(0, Math.floor(rect.x));
    const sy = Math.max(0, Math.floor(rect.y));
    const sw = Math.min(pageCanvas.width - sx, Math.ceil(rect.w));
    const sh = Math.min(pageCanvas.height - sy, Math.ceil(rect.h));
    if (sw < 8 || sh < 8) return '';
    const rawCanvas = document.createElement('canvas');
    rawCanvas.width = sw;
    rawCanvas.height = sh;
    const rawCtx = rawCanvas.getContext('2d', { willReadFrequently: true });
    rawCtx.drawImage(pageCanvas, sx, sy, sw, sh, 0, 0, sw, sh);
    const trimmedCanvas = trimCanvasWhitespace(rawCanvas);
    return normalizeToLegacyFrame(trimmedCanvas, frameSpec || LEGACY_FRAME.question);
  }

  function trimCanvasWhitespace(sourceCanvas) {
    const ctx = sourceCanvas.getContext('2d', { willReadFrequently: true });
    const image = ctx.getImageData(0, 0, sourceCanvas.width, sourceCanvas.height);
    const { data, width, height } = image;
    let minX = width;
    let minY = height;
    let maxX = -1;
    let maxY = -1;
    for (let y = 0; y < height; y += 1) {
      for (let x = 0; x < width; x += 1) {
        const i = (y * width + x) * 4;
        const r = data[i];
        const g = data[i + 1];
        const b = data[i + 2];
        const a = data[i + 3];
        const isInk = a > 8 && (r < 245 || g < 245 || b < 245);
        if (isInk) {
          if (x < minX) minX = x;
          if (y < minY) minY = y;
          if (x > maxX) maxX = x;
          if (y > maxY) maxY = y;
        }
      }
    }
    if (maxX < minX || maxY < minY) return sourceCanvas;
    const bleed = 4;
    minX = Math.max(0, minX - bleed);
    minY = Math.max(0, minY - bleed);
    maxX = Math.min(width - 1, maxX + bleed);
    maxY = Math.min(height - 1, maxY + bleed);
    const trimmed = document.createElement('canvas');
    trimmed.width = Math.max(1, maxX - minX + 1);
    trimmed.height = Math.max(1, maxY - minY + 1);
    const trimmedCtx = trimmed.getContext('2d', { willReadFrequently: true });
    trimmedCtx.drawImage(sourceCanvas, minX, minY, trimmed.width, trimmed.height, 0, 0, trimmed.width, trimmed.height);
    return trimmed;
  }

  function normalizeToLegacyFrame(sourceCanvas, frameSpec) {
    const targetWidth = frameSpec.canvasWidth;
    const usableWidth = Math.max(32, targetWidth - ((frameSpec.padX || 0) * 2));
    const scale = usableWidth / Math.max(1, sourceCanvas.width);
    const drawWidth = Math.round(sourceCanvas.width * scale);
    const drawHeight = Math.round(sourceCanvas.height * scale);
    const targetHeight = Math.max(frameSpec.baseHeight, drawHeight + ((frameSpec.padY || 0) * 2));
    const canvas = document.createElement('canvas');
    canvas.width = targetWidth;
    canvas.height = targetHeight;
    const ctx = canvas.getContext('2d', { willReadFrequently: true });
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.imageSmoothingEnabled = true;
    ctx.imageSmoothingQuality = 'high';
    const drawX = Math.max(0, Math.round((targetWidth - drawWidth) / 2));
    const drawY = frameSpec.verticalAlign === 'middle'
      ? Math.max(0, Math.round((targetHeight - drawHeight) / 2))
      : Math.max(0, frameSpec.padY || 0);
    ctx.drawImage(sourceCanvas, 0, 0, sourceCanvas.width, sourceCanvas.height, drawX, drawY, drawWidth, drawHeight);
    return canvas.toDataURL('image/png');
  }

  function makeViewerSizedImage(dataUrl) {
    return dataUrl || '';
  }

  function renderQuestionList() {
    els.questionList.innerHTML = '';
    if (!state.questions.length) {
      els.questionList.innerHTML = '<div class="question-row"><strong>No parsed questions yet.</strong><small>Choose a PDF, then detect snippets with a matching template profile.</small></div>';
      return;
    }
    state.questions.forEach((question, index) => {
      const row = document.createElement('button');
      row.type = 'button';
      row.className = 'question-row' + (index === state.selectedIndex ? ' active' : '');
      row.innerHTML =
        '<strong>' + escapeHtml(question.generatedQuestionId) + '</strong>' +
        '<small>Source ' + escapeHtml(question.sourceQuestionNo) + '</small>' +
        '<small>' + escapeHtml(question.type) + ' | page ' + escapeHtml(String(question.pageNumber || '-')) + ' | ' + escapeHtml(question.profileId || activeProfile().id) + '</small>';
      row.addEventListener('click', () => selectQuestion(index));
      els.questionList.appendChild(row);
    });
  }

  function renderQuestionEditor() {
    const question = state.questions[state.selectedIndex];
    if (!question) {
      els.editorTitle.textContent = 'No question selected';
      els.editorMeta.textContent = 'Upload or parse a paper to begin.';
      els.sourceQuestionNo.value = '';
      els.generatedQuestionId.value = '';
      els.questionSnippetPreview.removeAttribute('src');
      els.optionFields.innerHTML = '';
      els.questionWarnings.textContent = 'No warnings.';
      return;
    }
    els.editorTitle.textContent = question.generatedQuestionId;
    els.editorMeta.textContent = question.generatedQuestionId + ' | ' + question.type + ' | page ' + (question.pageNumber || '-') + ' | ' + (question.profileId || activeProfile().id);
    els.sourceQuestionNo.value = question.sourceQuestionNo;
    els.generatedQuestionId.value = question.generatedQuestionId;
    els.questionTypeSingle.value = question.type || 'MCQ';
    els.questionMarksSingle.value = Number(question.marks ?? 1);
    els.questionNegMarksSingle.value = Number(question.negMarks ?? 0);
    if (question.questionViewerImage || question.questionImage) {
      els.questionSnippetPreview.src = question.questionViewerImage || question.questionImage;
    } else {
      els.questionSnippetPreview.removeAttribute('src');
    }
    els.optionFields.innerHTML = '';
    question.options.forEach((option, optionIndex) => {
      const card = document.createElement('div');
      card.className = 'option-card';
      card.innerHTML =
        '<h3>' + escapeHtml(option.optionId) + '</h3>' +
        '<label class="field"><span>Generated Option ID</span><input data-role="option-id" data-index="' + optionIndex + '" value="' + escapeHtml(option.optionId) + '"></label>' +
        '<div class="option-snippet-box"><span class="field"><span>Option Snippet Preview</span></span><img class="option-snippet-preview" alt="Option snippet preview" src="' + escapeHtml(option.viewerImage || option.image || '') + '"></div>';
      els.optionFields.appendChild(card);
    });
    els.questionWarnings.innerHTML = question.warnings.length ? question.warnings.map((warning) => '<div>' + escapeHtml(warning) + '</div>').join('') : 'No warnings.';
  }

  function renderCounts() {
    const warningCount = state.questions.reduce((sum, question) => sum + question.warnings.length, 0);
    els.questionCountPill.textContent = state.questions.length + ' Questions';
    els.warningCountPill.textContent = warningCount + ' Warnings';
  }

  function buildPlainReviewExport() {
    return {
      templateName: els.templateName.value.trim(),
      paperProfileId: activeProfile().id,
      exportTarget: els.exportTarget.value,
      examCode: els.examCode.value.trim(),
      sectionCode: els.sectionCode.value.trim(),
      questions: state.questions.map((question) => ({
        questionId: question.generatedQuestionId,
        type: question.type,
        marks: question.marks,
        negMarks: question.negMarks,
        questionImage: question.questionImage,
        options: question.options.map((option) => ({
          optionId: option.optionId,
          image: option.image
        })),
        warnings: question.warnings
      }))
    };
  }

  function buildLegacyImageExport() {
    const examCode = els.examCode.value.trim() || 'STRUCTURED_IMPORT';
    const sectionCode = els.sectionCode.value.trim() || 'GEN';
    return {
      _version: 2,
      _app: 'pdf-question-profile-studio',
      _savedAt: new Date().toISOString(),
      paperProfileId: activeProfile().id,
      examName: examCode,
      subjects: [sectionCode],
      qs: state.questions.map((question, index) => ({
        qid: question.generatedQuestionId,
        type: question.type || 'MCQ',
        subject: sectionCode,
        questionText: '',
        questionComposerHTML: '',
        marks: Number(question.marks ?? 1),
        negMarks: Number(question.negMarks ?? 0),
        questionImage: question.questionImage || '',
        questionBaseImage: question.questionBaseImage || question.questionImage || '',
        questionFigures: [],
        questionLegends: [],
        options: (question.type === 'NAT' ? [] : question.options).map((option) => ({
          oid: option.optionId,
          image: option.image || '',
          baseImage: option.baseImage || option.image || '',
          viewerImage: option.viewerImage || option.image || '',
          text: '',
          composerHTML: '',
          figures: [],
          legends: [],
          correct: !!option.correct
        })),
        natAnswer: question.type === 'NAT' ? '' : null,
        correctOptionIds: (question.type === 'NAT' ? [] : question.options.filter((option) => option.correct).map((option) => option.optionId)),
        questionViewerImage: question.questionViewerImage || question.questionImage || '',
        parseMeta: {
          page: question.pageNumber || null,
          profileId: question.profileId || activeProfile().id,
          warnings: question.warnings
        }
      }))
    };
  }

  function refreshExportPreview() {
    const payload = els.exportTarget.value === 'legacy-image' ? buildLegacyImageExport() : buildPlainReviewExport();
    els.exportPreview.value = JSON.stringify(payload, null, 2);
  }

  function selectQuestion(index) {
    state.selectedIndex = index;
    renderQuestionList();
    renderQuestionEditor();
  }

  function resetStudio() {
    state.questions = [];
    state.selectedIndex = -1;
    state.selectedPdfFile = null;
    state.extractedPdf = null;
    els.pdfFile.value = '';
    els.exportPreview.value = '';
    setSourceStatus('No PDF loaded yet.');
    renderQuestionList();
    renderQuestionEditor();
    renderCounts();
  }

  async function parseNow() {
    const profile = activeProfile();
    try {
      if (!state.extractedPdf && state.selectedPdfFile) {
        setSourceStatus('Reading structured snippets from ' + state.selectedPdfFile.name + ' using ' + profile.label + ' ...');
        state.extractedPdf = await extractStructuredPdf(state.selectedPdfFile, profile);
      }
    } catch (error) {
      setSourceStatus('PDF read failed: ' + (error.message || error), 'error');
      alert('Could not read this PDF yet. Try a closer template profile or a cleaner structured PDF.');
      return;
    }
    if (!state.selectedPdfFile) {
      alert('Choose a PDF first.');
      return;
    }
    state.questions = state.extractedPdf && state.extractedPdf.questions ? state.extractedPdf.questions.map((question) => ({
      ...question,
      options: (question.options || []).map((option) => ({ ...option })),
      warnings: [...(question.warnings || [])]
    })) : [];
    if (!state.questions.length) {
      setSourceStatus('No content-only question snippets were detected. This PDF likely needs a different template profile.', 'error');
    } else {
      setSourceStatus('Built ' + state.questions.length + ' content-only question snippets with ' + profile.label + '.', 'ok');
    }
    state.selectedIndex = state.questions.length ? 0 : -1;
    renderQuestionList();
    renderQuestionEditor();
    renderCounts();
    refreshExportPreview();
  }

  function syncCurrentQuestion() {
    const question = state.questions[state.selectedIndex];
    if (!question) return;
    question.sourceQuestionNo = els.sourceQuestionNo.value.trim();
    question.generatedQuestionId = els.generatedQuestionId.value.trim();
    question.type = els.questionTypeSingle.value;
    question.marks = Number(els.questionMarksSingle.value || 0);
    question.negMarks = Number(els.questionNegMarksSingle.value || 0);
    els.optionFields.querySelectorAll('[data-role="option-id"]').forEach((input) => {
      const index = Number(input.dataset.index);
      if (question.options[index]) question.options[index].optionId = input.value.trim();
    });
    renderQuestionList();
    refreshExportPreview();
  }

  function exportJson() {
    refreshExportPreview();
    const blob = new Blob([els.exportPreview.value], { type: 'application/json' });
    const link = document.createElement('a');
    const examCode = (els.examCode.value || 'structured-import').trim();
    link.href = URL.createObjectURL(blob);
    link.download = examCode + '-question-import.json';
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(link.href);
  }

  function copyJson() {
    refreshExportPreview();
    navigator.clipboard.writeText(els.exportPreview.value).catch(() => {
      alert('Clipboard copy failed. You can still copy from the export preview box.');
    });
  }

  function escapeHtml(value) {
    return String(value ?? '')
      .replaceAll('&', '&amp;')
      .replaceAll('"', '&quot;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;');
  }

  els.choosePdfBtn.addEventListener('click', () => els.pdfFile.click());
  els.pdfFile.addEventListener('change', async () => {
    const file = els.pdfFile.files && els.pdfFile.files[0];
    if (!file) return;
    state.selectedPdfFile = file;
    state.extractedPdf = null;
    els.uploadZone.querySelector('p').textContent = 'Selected PDF: ' + file.name;
    setSourceStatus('Selected PDF: ' + file.name + '. Ready for ' + activeProfile().label + ' detection.');
  });
  els.templateProfile.addEventListener('change', () => applyProfile(els.templateProfile.value));
  els.parseBtn.addEventListener('click', parseNow);
  els.resetBtn.addEventListener('click', resetStudio);
  els.prevQuestionBtn.addEventListener('click', () => {
    if (state.selectedIndex > 0) selectQuestion(state.selectedIndex - 1);
  });
  els.nextQuestionBtn.addEventListener('click', () => {
    if (state.selectedIndex < state.questions.length - 1) selectQuestion(state.selectedIndex + 1);
  });
  els.exportJsonBtn.addEventListener('click', exportJson);
  els.copyJsonBtn.addEventListener('click', copyJson);

  [
    els.sourceQuestionNo,
    els.generatedQuestionId,
    els.questionTypeSingle,
    els.questionMarksSingle,
    els.questionNegMarksSingle,
    els.examCode,
    els.sectionCode,
    els.questionTypeDefault,
    els.questionMarksDefault,
    els.questionNegMarksDefault,
    els.templateName,
    els.exportTarget
  ].forEach((el) => el.addEventListener('input', () => {
    syncCurrentQuestion();
    refreshExportPreview();
  }));

  renderProfileOptions();
  applyProfile(state.activeProfileId);
  renderQuestionList();
  renderQuestionEditor();
  renderCounts();
  refreshExportPreview();
})();
