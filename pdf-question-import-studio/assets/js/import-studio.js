(function () {
  const sampleBlock = [
    'Q.16 The response of a discrete time system y[n] obeys the following relation:',
    'y[n] = 5/6 y[n-1] - 1/6 y[n-2] + x[n].',
    'The input to the system is x[n] = delta[n] - 1/3 delta[n-1].',
    'Which of the following options is TRUE for y[n]?',
    '(A) Stable and causal response',
    '(B) Stable and non-causal response',
    '(C) Unstable and causal response',
    '(D) Unstable and non-causal response',
    '',
    'Q.17 Choose the correct statement for a right-sided sequence.',
    '(A) It is always finite length',
    '(B) It can be non-zero only for n >= n0',
    '(C) It is always unstable',
    '(D) It is periodic by default'
  ].join('\n');

  const LEGACY_FRAME = {
    question: { canvasWidth: 640, baseHeight: 90, viewerWidth: 700 },
    option: { canvasWidth: 500, baseHeight: 46, viewerWidth: 600 }
  };

  const state = {
    questions: [],
    selectedIndex: -1,
    selectedPdfFile: null,
    extractedPdf: null
  };

  const els = {
    pdfFile: document.getElementById('pdfFile'),
    choosePdfBtn: document.getElementById('choosePdfBtn'),
    uploadZone: document.getElementById('uploadZone'),
    sourceStatus: document.getElementById('sourceStatus'),
    templateName: document.getElementById('templateName'),
    questionPrefix: document.getElementById('questionPrefix'),
    optionStyle: document.getElementById('optionStyle'),
    questionIdPrefix: document.getElementById('questionIdPrefix'),
    optionIdPrefix: document.getElementById('optionIdPrefix'),
    exportTarget: document.getElementById('exportTarget'),
    rawText: document.getElementById('rawText'),
    loadSampleBtn: document.getElementById('loadSampleBtn'),
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
    questionBody: document.getElementById('questionBody'),
    questionImageData: document.getElementById('questionImageData'),
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

  function optionLegendLetters() {
    return ['A', 'B', 'C', 'D'];
  }

  function generatedOptionId(questionIndex, optionIndex) {
    const prefix = (els.optionIdPrefix.value || 'OPT').trim();
    return prefix + '-' + String(questionIndex + 1).padStart(3, '0') + '-' + optionLegendLetters()[optionIndex];
  }

  function buildQuestion(questionNo, body, options, questionIndex) {
    const warnings = [];
    if (options.length !== 4) warnings.push('Expected exactly 4 options, found ' + options.length + '.');
    if (!body.trim()) warnings.push('Question body is empty.');
    return {
      sourceQuestionNo: questionNo,
      generatedQuestionId: ((els.questionIdPrefix.value || 'QIMP').trim() || 'QIMP') + '-' + String(questionIndex + 1).padStart(4, '0'),
      type: els.questionTypeDefault.value || 'MCQ',
      marks: Number(els.questionMarksDefault.value || 1),
      negMarks: Number(els.questionNegMarksDefault.value || 0),
      body: body.trim(),
      questionImage: '',
      questionBaseImage: '',
      questionViewerImage: '',
      options: optionLegendLetters().map((letter, index) => ({
        legend: letter,
        optionId: generatedOptionId(questionIndex, index),
        text: options[index] || '',
        image: '',
        baseImage: '',
        viewerImage: '',
        correct: false
      })),
      warnings
    };
  }

  function parseStrictText(raw) {
    const prefix = (els.questionPrefix.value || 'Q.').trim().replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const lines = String(raw || '').replace(/\r/g, '').split('\n');
    const questionStart = new RegExp('^' + prefix + '\\s*(\\d+)\\s*(.*)$');
    const optionStart = /^\(([A-D])\)\s*(.*)$/;

    const blocks = [];
    let current = null;

    lines.forEach((line) => {
      const text = line.trim();
      if (!text) return;
      const qMatch = text.match(questionStart);
      if (qMatch) {
        if (current) blocks.push(current);
        current = {
          number: qMatch[1],
          bodyLines: qMatch[2] ? [qMatch[2]] : [],
          optionMap: {}
        };
        return;
      }
      if (!current) return;
      const optMatch = text.match(optionStart);
      if (optMatch) {
        current.optionMap[optMatch[1]] = (current.optionMap[optMatch[1]] ? current.optionMap[optMatch[1]] + ' ' : '') + optMatch[2];
        return;
      }
      const openOption = ['A', 'B', 'C', 'D'].find((letter) => current.optionMap[letter] && !current.optionMap[String.fromCharCode(letter.charCodeAt(0) + 1)]);
      if (openOption && Object.keys(current.optionMap).length > 0) {
        current.optionMap[openOption] += ' ' + text;
      } else {
        current.bodyLines.push(text);
      }
    });

    if (current) blocks.push(current);

    return blocks.map((block, index) => buildQuestion(
      block.number,
      block.bodyLines.join(' '),
      optionLegendLetters().map((letter) => (block.optionMap[letter] || '').trim()),
      index
    ));
  }

  async function extractStructuredPdf(file) {
    if (!window.pdfjsLib) {
      throw new Error('PDF reader library did not load.');
    }
    if (pdfjsLib.GlobalWorkerOptions) {
      pdfjsLib.GlobalWorkerOptions.workerSrc = 'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js';
    }
    const buffer = await file.arrayBuffer();
    const loadingTask = pdfjsLib.getDocument({ data: buffer });
    const pdf = await loadingTask.promise;
    const pages = [];
    const structuredQuestions = [];
    for (let pageNumber = 1; pageNumber <= pdf.numPages; pageNumber += 1) {
      const page = await pdf.getPage(pageNumber);
      const viewport = page.getViewport({ scale: 2 });
      const canvas = document.createElement('canvas');
      canvas.width = Math.ceil(viewport.width);
      canvas.height = Math.ceil(viewport.height);
      const ctx = canvas.getContext('2d', { willReadFrequently: true });
      await page.render({ canvasContext: ctx, viewport }).promise;
      const textContent = await page.getTextContent();
      const normalizedItems = normalizePdfItems(textContent.items || [], viewport);
      const cleanItems = normalizedItems.filter((item) => !item.isWatermark);
      const pageText = collapsePdfItemsToLines(cleanItems).join('\n');
      const pageQuestions = detectStructuredQuestionsFromPage(cleanItems, canvas, pageNumber);
      pages.push({ pageNumber, text: pageText, questions: pageQuestions.length });
      structuredQuestions.push(...pageQuestions);
    }
    return {
      pages,
      rawText: pages.map((page) => page.text).join('\n\n'),
      questions: structuredQuestions
    };
  }

  async function extractTextFromPdf(file) {
    const structured = await extractStructuredPdf(file);
    state.extractedPdf = structured;
    return structured.rawText;
  }

  function normalizePdfItems(items, viewport) {
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
      const watermarkish = rotationish || (
        text.length >= 6 &&
        width > 110 &&
        height >= 12 &&
        /^[A-Z0-9 .:_-]+$/i.test(text)
      );
      return {
        text,
        x,
        y: baseline,
        top,
        bottom: baseline,
        width,
        height,
        isWatermark: watermarkish
      };
    }).filter((item) => item.text);
  }

  function collapsePdfItemsToLines(items) {
    const buckets = [];
    items.forEach((item) => {
      const text = String(item.text || '').trim();
      if (!text) return;
      const x = Number(item.x || 0);
      const y = Number(item.top || 0);
      let bucket = buckets.find((row) => Math.abs(row.y - y) <= 4);
      if (!bucket) {
        bucket = { y, parts: [] };
        buckets.push(bucket);
      }
      bucket.parts.push({ x, text });
    });
    buckets.sort((a, b) => a.y - b.y);
    return buckets.map((row) => row.parts
      .sort((a, b) => a.x - b.x)
      .map((part) => part.text)
      .join(' ')
      .replace(/\s+/g, ' ')
      .trim()
    ).filter(Boolean);
  }

  function detectStructuredQuestionsFromPage(items, pageCanvas, pageNumber) {
    const qPrefix = (els.questionPrefix.value || 'Q.').trim();
    const qRegex = new RegExp('^' + qPrefix.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '\\s*(\\d+)\\b', 'i');
    const optionRegex = /^\(([A-D])\)\s*/i;
    const questionLabels = items
      .filter((item) => qRegex.test(item.text))
      .sort((a, b) => a.top - b.top);
    const questions = [];
    questionLabels.forEach((label, index) => {
      const nextLabel = questionLabels[index + 1];
      const regionTop = Math.max(0, label.top - 8);
      const regionBottom = nextLabel ? nextLabel.top - 10 : pageCanvas.height - 8;
      const regionItems = items.filter((item) => item.top >= regionTop && item.top < regionBottom + 2);
      const optionLabels = regionItems
        .filter((item) => optionRegex.test(item.text))
        .sort((a, b) => a.top - b.top);
      const qMatch = label.text.match(qRegex);
      const questionNo = qMatch ? qMatch[1] : String(index + 1);
      const bodyCutoff = optionLabels.length ? optionLabels[0].top - 6 : regionBottom;
      const bodyItems = regionItems.filter((item) => item !== label && item.top >= label.top - 2 && item.top < bodyCutoff && item.x > label.x + 28);
      const optionTexts = [];
      const optionImages = [];
      optionLegendLetters().forEach((letter, optionIndex) => {
        const optLabel = optionLabels.find((item) => item.text.toUpperCase().startsWith('(' + letter + ')'));
        if (!optLabel) {
          optionTexts.push('');
          optionImages.push('');
          return;
        }
        const nextOpt = optionLabels.find((candidate) => candidate.top > optLabel.top + 4);
        const optTop = Math.max(optLabel.top - 8, regionTop);
        const optBottom = nextOpt ? nextOpt.top - 8 : regionBottom;
        const optItems = regionItems.filter((item) => item.top >= optTop && item.top < optBottom && item.x > optLabel.x + 20);
        optionTexts.push(stripLeadingOptionLegend(itemsToParagraph(optItems), letter));
        optionImages.push(cropBlockImage(
          pageCanvas,
          buildCropRect(optItems, optTop, optBottom, pageCanvas.width, optLabel.x + 34),
          LEGACY_FRAME.option
        ));
      });
      const questionText = itemsToParagraph(bodyItems);
      const questionImage = cropBlockImage(
        pageCanvas,
        buildCropRect(bodyItems, label.top - 2, bodyCutoff, pageCanvas.width, label.x + 42),
        LEGACY_FRAME.question
      );
      const built = buildQuestion(questionNo, questionText, optionTexts, questions.length);
      built.questionImage = questionImage;
      built.questionBaseImage = questionImage;
      built.questionViewerImage = makeViewerSizedImage(questionImage, LEGACY_FRAME.question);
      built.options = built.options.map((option, optionIndex) => ({
        ...option,
        image: optionImages[optionIndex] || '',
        baseImage: optionImages[optionIndex] || '',
        viewerImage: makeViewerSizedImage(optionImages[optionIndex] || '', LEGACY_FRAME.option)
      }));
      built.pageNumber = pageNumber;
      if (!questionImage) built.warnings.push('Question image crop could not be created.');
      if (built.options.filter((option) => option.image).length === 0) {
        built.warnings.push('Option image crops were not detected for this question.');
      }
      questions.push(built);
    });
    return questions;
  }

  function buildCropRect(items, suggestedTop, suggestedBottom, pageWidth, minLeft) {
    const textItems = (items || []).filter(Boolean);
    const top = textItems.length ? Math.max(0, Math.min(...textItems.map((item) => item.top)) - 10) : Math.max(0, suggestedTop);
    const bottom = textItems.length ? Math.max(top + 24, Math.max(...textItems.map((item) => item.bottom)) + 10) : Math.max(top + 24, suggestedBottom);
    const left = textItems.length
      ? Math.max(0, Math.min(...textItems.map((item) => item.x)) - 12)
      : Math.max(0, minLeft || 0);
    const right = textItems.length
      ? Math.min(pageWidth, Math.max(...textItems.map((item) => item.x + item.width)) + 18)
      : Math.min(pageWidth, Math.max(pageWidth - 18, left + 20));
    return {
      x: Math.max(left, minLeft || 0),
      y: Math.max(0, top),
      w: Math.max(40, right - Math.max(left, minLeft || 0)),
      h: Math.max(28, bottom - top)
    };
  }

  function itemsToParagraph(items) {
    return collapsePdfItemsToLines(items).join('\n').trim();
  }

  function stripLeadingOptionLegend(text, letter) {
    return String(text || '').replace(new RegExp('^\\(' + letter + '\\)\\s*', 'i'), '').trim();
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
    return normalizeToLegacyFrame(rawCanvas, frameSpec || LEGACY_FRAME.question);
  }

  function softenWatermark(canvas) {
    const ctx = canvas.getContext('2d', { willReadFrequently: true });
    const image = ctx.getImageData(0, 0, canvas.width, canvas.height);
    const data = image.data;
    for (let i = 0; i < data.length; i += 4) {
      const r = data[i];
      const g = data[i + 1];
      const b = data[i + 2];
      const avg = (r + g + b) / 3;
      const spread = Math.max(r, g, b) - Math.min(r, g, b);
      if (avg > 150 && spread < 42) {
        data[i] = Math.min(255, Math.round(r + (255 - r) * 0.78));
        data[i + 1] = Math.min(255, Math.round(g + (255 - g) * 0.78));
        data[i + 2] = Math.min(255, Math.round(b + (255 - b) * 0.78));
      }
    }
    ctx.putImageData(image, 0, 0);
  }

  function normalizeToLegacyFrame(sourceCanvas, frameSpec) {
    const targetWidth = frameSpec.canvasWidth;
    const targetHeight = Math.max(
      frameSpec.baseHeight,
      Math.round(sourceCanvas.height * (targetWidth / Math.max(1, sourceCanvas.width)))
    );
    const canvas = document.createElement('canvas');
    canvas.width = targetWidth;
    canvas.height = targetHeight;
    const ctx = canvas.getContext('2d', { willReadFrequently: true });
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.imageSmoothingEnabled = true;
    ctx.imageSmoothingQuality = 'high';
    const drawHeight = Math.round(sourceCanvas.height * (targetWidth / Math.max(1, sourceCanvas.width)));
    const drawY = frameSpec === LEGACY_FRAME.option ? Math.max(0, Math.round((targetHeight - drawHeight) / 2)) : 0;
    ctx.drawImage(sourceCanvas, 0, 0, sourceCanvas.width, sourceCanvas.height, 0, drawY, targetWidth, drawHeight);
    return canvas.toDataURL('image/png');
  }

  function makeViewerSizedImage(dataUrl, frameSpec) {
    if (!dataUrl) return '';
    const img = new Image();
    img.src = dataUrl;
    const sourceWidth = frameSpec.canvasWidth;
    const sourceHeight = frameSpec.baseHeight;
    const viewerWidth = frameSpec.viewerWidth;
    const canvas = document.createElement('canvas');
    const targetHeight = Math.max(
      Math.round(sourceHeight * (viewerWidth / sourceWidth)),
      Math.round(sourceHeight * (viewerWidth / sourceWidth))
    );
    canvas.width = viewerWidth;
    canvas.height = targetHeight;
    const ctx = canvas.getContext('2d', { willReadFrequently: true });
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    const drawHeight = Math.round(targetHeight);
    const drawY = frameSpec === LEGACY_FRAME.option ? Math.max(0, Math.round((targetHeight - drawHeight) / 2)) : 0;
    try {
      ctx.drawImage(img, 0, 0, img.width || sourceWidth, img.height || sourceHeight, 0, drawY, viewerWidth, drawHeight);
      return canvas.toDataURL('image/png');
    } catch (_) {
      return dataUrl;
    }
  }

  function setSourceStatus(message, mode) {
    els.sourceStatus.textContent = message;
    els.sourceStatus.style.color = mode === 'error' ? '#9d2232' : mode === 'ok' ? '#15623d' : '#4f6277';
  }

  function renderQuestionList() {
    els.questionList.innerHTML = '';
    if (!state.questions.length) {
      els.questionList.innerHTML = '<div class="question-row"><strong>No parsed questions yet.</strong><small>Upload or paste strict-format text, then parse.</small></div>';
      return;
    }
    state.questions.forEach((question, index) => {
      const row = document.createElement('button');
      row.type = 'button';
      row.className = 'question-row' + (index === state.selectedIndex ? ' active' : '');
      row.innerHTML =
        '<strong>Q.' + escapeHtml(question.sourceQuestionNo) + '</strong>' +
        '<small>' + escapeHtml(question.generatedQuestionId) + '</small>' +
        '<small>' + escapeHtml(trimPreview(question.body, 92)) + '</small>';
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
      els.questionBody.value = '';
      els.optionFields.innerHTML = '';
      els.questionWarnings.textContent = 'No warnings.';
      return;
    }

    els.editorTitle.textContent = 'Question ' + question.sourceQuestionNo;
    els.editorMeta.textContent = question.generatedQuestionId + ' | ' + question.type + ' | ' + question.options.length + ' options';
    els.sourceQuestionNo.value = question.sourceQuestionNo;
    els.generatedQuestionId.value = question.generatedQuestionId;
    els.questionTypeSingle.value = question.type || 'MCQ';
    els.questionMarksSingle.value = Number(question.marks ?? 1);
    els.questionNegMarksSingle.value = Number(question.negMarks ?? 0);
    els.questionBody.value = question.body;
    els.questionImageData.value = question.questionImage || '';
    els.optionFields.innerHTML = '';

    question.options.forEach((option, optionIndex) => {
      const card = document.createElement('div');
      card.className = 'option-card';
      card.innerHTML =
        '<h3>Option ' + escapeHtml(option.legend) + '</h3>' +
        '<label class="field"><span>Generated Option ID</span><input data-role="option-id" data-index="' + optionIndex + '" value="' + escapeHtml(option.optionId) + '"></label>' +
        '<label class="field"><span>Option Text</span><textarea data-role="option-text" data-index="' + optionIndex + '">' + escapeHtml(option.text) + '</textarea></label>' +
        '<label class="field"><span>Option Frame Image (Base64 PNG)</span><textarea data-role="option-image" data-index="' + optionIndex + '" placeholder="data:image/png;base64,...">' + escapeHtml(option.image || '') + '</textarea></label>';
      els.optionFields.appendChild(card);
    });

    els.questionWarnings.innerHTML = question.warnings.length
      ? question.warnings.map((warning) => '<div>' + escapeHtml(warning) + '</div>').join('')
      : 'No warnings.';
  }

  function renderCounts() {
    const warningCount = state.questions.reduce((sum, question) => sum + question.warnings.length, 0);
    els.questionCountPill.textContent = state.questions.length + ' Questions';
    els.warningCountPill.textContent = warningCount + ' Warnings';
  }

  function refreshExportPreview() {
    const payload = els.exportTarget.value === 'legacy-image'
      ? buildLegacyImageExport()
      : buildPlainReviewExport();
    els.exportPreview.value = JSON.stringify(payload, null, 2);
  }

  function buildPlainReviewExport() {
    return {
      templateName: els.templateName.value.trim(),
      exportTarget: els.exportTarget.value,
      examCode: els.examCode.value.trim(),
      sectionCode: els.sectionCode.value.trim(),
      defaultQuestionType: els.questionTypeDefault.value,
      marksPerQuestion: Number(els.questionMarksDefault.value || 0),
      questions: state.questions.map((question) => ({
        sourceQuestionNo: question.sourceQuestionNo,
        questionId: question.generatedQuestionId,
        type: question.type,
        marks: question.marks,
        negMarks: question.negMarks,
        questionImage: question.questionImage,
        body: question.body,
        options: question.options.map((option) => ({
          legend: option.legend,
          optionId: option.optionId,
          text: option.text,
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
      _version: 1,
      _app: 'pdf-question-import-studio',
      _savedAt: new Date().toISOString(),
      examName: examCode,
      subjects: [sectionCode],
      qs: state.questions.map((question, index) => ({
        id: 'imp-' + String(index + 1).padStart(5, '0'),
        qid: question.generatedQuestionId,
        type: question.type || 'MCQ',
        subject: sectionCode,
        label: '',
        questionText: question.body || '',
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
          text: option.text || '',
          composerHTML: '',
          figures: [],
          legends: [],
          correct: !!option.correct
        })),
        natAnswer: question.type === 'NAT' ? '' : null,
        correctOptionIds: [],
        questionViewerImage: question.questionViewerImage || question.questionImage || ''
      }))
    };
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
    els.rawText.value = '';
    els.exportPreview.value = '';
    setSourceStatus('No PDF loaded yet.');
    renderQuestionList();
    renderQuestionEditor();
    renderCounts();
  }

  async function ensureRawTextReady() {
    const raw = els.rawText.value.trim();
    if (raw) return raw;
    if (!state.selectedPdfFile) return '';
    setSourceStatus('Reading PDF text from ' + state.selectedPdfFile.name + ' ...');
    const extracted = await extractTextFromPdf(state.selectedPdfFile);
    els.rawText.value = extracted;
    setSourceStatus('PDF text extracted from ' + state.selectedPdfFile.name + '.', 'ok');
    return extracted.trim();
  }

  async function parseNow() {
    let raw = '';
    try {
      raw = await ensureRawTextReady();
    } catch (error) {
      setSourceStatus('PDF read failed: ' + (error.message || error), 'error');
      alert('Could not read this PDF yet. Use a text-based PDF or paste extracted text.');
      return;
    }
    if (!raw) {
      alert('Choose a PDF or paste extracted text first.');
      return;
    }
    state.questions = state.extractedPdf && state.extractedPdf.questions && state.extractedPdf.questions.length
      ? state.extractedPdf.questions.map((question) => ({
          ...question,
          options: (question.options || []).map((option) => ({ ...option })),
          warnings: [...(question.warnings || [])]
        }))
      : parseStrictText(raw);
    if (!state.questions.length) {
      setSourceStatus('PDF/text loaded, but no strict question blocks were detected. Check legends like Q.16 and (A).', 'error');
    } else {
      setSourceStatus('Parsed ' + state.questions.length + ' questions successfully.', 'ok');
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
    question.body = els.questionBody.value;
    question.questionImage = els.questionImageData.value.trim();
    question.questionBaseImage = question.questionImage;
    question.questionViewerImage = question.questionImage;
    els.optionFields.querySelectorAll('[data-role="option-id"]').forEach((input) => {
      const index = Number(input.dataset.index);
      if (question.options[index]) question.options[index].optionId = input.value.trim();
    });
    els.optionFields.querySelectorAll('[data-role="option-text"]').forEach((input) => {
      const index = Number(input.dataset.index);
      if (question.options[index]) question.options[index].text = input.value;
    });
    els.optionFields.querySelectorAll('[data-role="option-image"]').forEach((input) => {
      const index = Number(input.dataset.index);
      if (question.options[index]) {
        question.options[index].image = input.value.trim();
        question.options[index].baseImage = question.options[index].image;
        question.options[index].viewerImage = question.options[index].image;
      }
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

  function trimPreview(text, max) {
    const value = String(text || '').trim();
    return value.length > max ? value.slice(0, max - 1) + '…' : value;
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
    els.uploadZone.querySelector('p').textContent = 'Selected PDF: ' + file.name;
    setSourceStatus('Selected PDF: ' + file.name + '. Extracting text ...');
    try {
      const extracted = await extractTextFromPdf(file);
      els.rawText.value = extracted;
      const found = state.extractedPdf && state.extractedPdf.questions ? state.extractedPdf.questions.length : 0;
      setSourceStatus('PDF text extracted. Detected ' + found + ' question blocks. Click "Parse Strict Layout" to build the review list.', found ? 'ok' : 'error');
    } catch (error) {
      setSourceStatus('PDF read failed: ' + (error.message || error), 'error');
    }
  });
  els.loadSampleBtn.addEventListener('click', () => {
    els.rawText.value = sampleBlock;
    setSourceStatus('Sample block loaded.', 'ok');
  });
  els.parseBtn.addEventListener('click', () => { parseNow(); });
  els.resetBtn.addEventListener('click', resetStudio);
  els.prevQuestionBtn.addEventListener('click', () => {
    if (state.selectedIndex > 0) selectQuestion(state.selectedIndex - 1);
  });
  els.nextQuestionBtn.addEventListener('click', () => {
    if (state.selectedIndex < state.questions.length - 1) selectQuestion(state.selectedIndex + 1);
  });
  els.exportJsonBtn.addEventListener('click', exportJson);
  els.copyJsonBtn.addEventListener('click', copyJson);

  [els.sourceQuestionNo, els.generatedQuestionId, els.questionTypeSingle, els.questionMarksSingle, els.questionNegMarksSingle, els.questionBody, els.questionImageData, els.examCode, els.sectionCode, els.questionTypeDefault, els.questionMarksDefault, els.questionNegMarksDefault, els.templateName, els.exportTarget]
    .forEach((el) => el.addEventListener('input', () => {
      syncCurrentQuestion();
      refreshExportPreview();
    }));

  els.optionFields.addEventListener('input', () => {
    syncCurrentQuestion();
  });

  renderQuestionList();
  renderQuestionEditor();
  renderCounts();
})();
