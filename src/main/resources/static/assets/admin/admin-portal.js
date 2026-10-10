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
      let exams = [];
      let students = [];
      let notifications = [];
      let responseRows = [];
      let examIncidentRows = [];
      let examAccessRequestRows = [];
      let evaluationRows = [];
      let manualEvaluationRows = [];
      let evaluationOverrideRows = [];
      let challengeRequestRows = [];
      let challengeQuestionSummaryRows = [];
      let selectedChallengeReviewId = null;
      let questionOrderMeta = [];
      let sectionPriorityOrder = [];
      let markPriorityBySection = {};
      let qbankAssemblyState = { qbankId: '', assemblyId: '', inventory: null, history: [] };
      let projectCreateModalAction = null;
      let currentWorkspaceScreen = location.hash === '#post-exam' ? 'responseCenterCard' : 'workspaceDashboard';
      let selectedResponseId = null;
      let reportElements = [];
      let reportCardTemplateLibrary = [];
      let reportWatermark = { type: 'none', text: '', src: '', size: 72, opacity: 8, angle: -28, color: '#0d5ca6', x: 0, y: 0, w: 794, h: 1123 };
      let selectedReportElementId = null;
      let reportPointer = null;
      const adminPageState = {
        exams: { page: 0, size: 10, totalElements: 0, totalPages: 0, hasNext: false, hasPrevious: false },
        students: { page: 0, size: 10, totalElements: 0, totalPages: 0, hasNext: false, hasPrevious: false },
        notifications: { page: 0, size: 10, totalElements: 0, totalPages: 0, hasNext: false, hasPrevious: false },
        responses: { page: 0, size: 10, totalElements: 0, totalPages: 0, hasNext: false, hasPrevious: false }
      };
      let workspaceSnapshot = {
        candidateCount: 0,
        recentCandidates: [],
        notificationCount: 0,
        responseCount: 0
      };

    async function jsonRequest(url, method, payload) {
      const response = await fetch(url, {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: payload ? JSON.stringify(payload) : undefined
      });
      const text = await response.text();
      const data = text ? JSON.parse(text) : {};
      if (!response.ok) throw new Error(data.message || text || response.statusText);
      return data;
    }

    function setAdminPageMeta(key, pageData = {}) {
      const state = adminPageState[key];
      if (!state) return;
      state.page = Number(pageData.page ?? state.page ?? 0);
      state.size = Number(pageData.size ?? state.size ?? 10);
      state.totalElements = Number(pageData.totalElements ?? 0);
      state.totalPages = Number(pageData.totalPages ?? 0);
      state.hasNext = Boolean(pageData.hasNext);
      state.hasPrevious = Boolean(pageData.hasPrevious);
    }

    function pagedUrl(baseUrl, key, extraParams = {}) {
      const state = adminPageState[key];
      const params = new URLSearchParams({
        page: String(state.page),
        size: String(state.size)
      });
      Object.entries(extraParams).forEach(([name, value]) => {
        if (value !== undefined && value !== null && String(value).trim() !== '') {
          params.set(name, String(value).trim());
        }
      });
      return baseUrl + '?' + params.toString();
    }

    function renderPager(containerId, key, pageData, noun) {
      const host = document.getElementById(containerId);
      if (!host) return;
      const total = Number(pageData?.totalElements ?? 0);
      if (!total) {
        host.innerHTML = '';
        return;
      }
      const state = adminPageState[key];
      const currentPage = Number(pageData.page ?? 0);
      const totalPages = Math.max(Number(pageData.totalPages ?? 0), 1);
      host.innerHTML = '<div class="pager-summary">'
        + 'Showing page ' + (currentPage + 1) + ' of ' + totalPages + ' | ' + total + ' ' + noun
        + '</div>'
        + '<div class="pager-actions">'
        + '<button class="secondary" type="button" data-page-jump="' + key + ':-1"' + (pageData.hasPrevious ? '' : ' disabled') + '>Prev</button>'
        + '<button class="secondary" type="button" data-page-jump="' + key + ':1"' + (pageData.hasNext ? '' : ' disabled') + '>Next</button>'
        + '</div>';
      host.querySelectorAll('[data-page-jump]').forEach((button) => {
        button.addEventListener('click', async () => {
          const [, deltaText] = String(button.dataset.pageJump || '').split(':');
          const delta = Number(deltaText || 0);
          state.page = Math.max(0, state.page + delta);
          if (key === 'responses') {
            await refreshResponseCenter();
            return;
          }
          await refreshDatabaseView();
        });
      });
    }

    function setProjectModal({ title, text, busy = true, okLabel = 'OK', onOk = null }) {
      document.getElementById('projectCreateModalTitle').textContent = title;
      document.getElementById('projectCreateModalText').textContent = text;
      document.getElementById('projectCreateSpinner').classList.toggle('hidden', !busy);
      document.getElementById('projectCreateProgress').classList.toggle('hidden', !busy);
      const okButton = document.getElementById('projectCreateOk');
      okButton.textContent = okLabel;
      okButton.classList.toggle('hidden', busy);
      projectCreateModalAction = onOk;
    }

    function openProjectModal(options) {
      setProjectModal(options);
      const modal = document.getElementById('projectCreateModal');
      modal.classList.add('show');
      modal.setAttribute('aria-hidden', 'false');
    }

    function closeProjectModal() {
      const modal = document.getElementById('projectCreateModal');
      modal.classList.remove('show');
      modal.setAttribute('aria-hidden', 'true');
    }

      function activeExam() {
        const code = document.getElementById('configExamCode').value;
        return exams.find((exam) => exam.examCode === code) || null;
      }

      function reportDesignerStudioUrl() {
        const exam = activeExam();
        const url = new URL('/report-card-designer.html', window.location.origin);
        if (exam && exam.examCode) {
          url.searchParams.set('examCode', exam.examCode);
          url.searchParams.set('examName', exam.examName || exam.examCode);
        }
        return url.toString();
      }
      function selectedReportTemplateId() {
        const select = document.getElementById('reportCardTemplateSelect');
        return select ? select.value : '';
      }

      function reportDesignerStudioUrlForTemplate(templateId = '') {
        const url = new URL(reportDesignerStudioUrl());
        if (templateId) {
          url.searchParams.set('templateId', templateId);
        }
        return url.toString();
      }

      function updateReportTemplateAdminStatus(message, mode = '') {
        const status = document.getElementById('reportCardTemplateAdminStatus');
        if (!status) return;
        status.innerHTML = mode === 'ok'
          ? '<span class="ok">' + escapeHtml(message) + '</span>'
          : mode === 'warn'
            ? '<span class="warn">' + escapeHtml(message) + '</span>'
            : escapeHtml(message);
      }

      function renderReportTemplateSelector(activeTemplateId = '', activeTemplateName = '') {
        const select = document.getElementById('reportCardTemplateSelect');
        if (!select) return;
        if (!reportCardTemplateLibrary.length) {
          select.innerHTML = '<option value="">No saved template</option>';
          updateReportTemplateAdminStatus('Candidate PDF downloads use the official layout. Saved designs remain available in the studio.');
          return;
        }
        select.innerHTML = reportCardTemplateLibrary.map((entry, index) => {
          const label = (entry.templateName || entry.templateId || ('Template ' + (index + 1))) + (entry.templateId === activeTemplateId ? ' [studio default]' : '');
          return '<option value="' + escapeHtml(entry.templateId || '') + '">' + escapeHtml(label) + '</option>';
        }).join('');
        if (activeTemplateId && reportCardTemplateLibrary.some((entry) => entry.templateId === activeTemplateId)) {
          select.value = activeTemplateId;
        }
        updateReportTemplateAdminStatus('Studio default: ' + (activeTemplateName || activeTemplateId || 'none') + '. Candidate PDFs use the official layout.', activeTemplateId ? 'ok' : '');
      }

      async function refreshReportTemplateSelector(silent = false) {
        const exam = activeExam();
        if (!exam || !exam.examCode) {
          reportCardTemplateLibrary = [];
          renderReportTemplateSelector();
          return;
        }
        try {
          const response = await fetch('/api/exams/' + encodeURIComponent(exam.examCode) + '/report-card-template', { credentials: 'same-origin' });
          const data = await response.json().catch(() => ({}));
          if (!response.ok) throw new Error(data.message || data.detail || response.statusText || 'Could not load report-card templates');
          try {
            reportCardTemplateLibrary = JSON.parse(data.templatesJson || '[]');
            if (!Array.isArray(reportCardTemplateLibrary)) reportCardTemplateLibrary = [];
          } catch {
            reportCardTemplateLibrary = [];
          }
          renderReportTemplateSelector(data.activeTemplateId || exam.activeReportCardTemplateId || '', data.activeTemplateName || exam.activeReportCardTemplateName || '');
        } catch (error) {
          reportCardTemplateLibrary = [];
          renderReportTemplateSelector();
          if (!silent) {
            updateReportTemplateAdminStatus('Could not load score card templates: ' + error.message, 'warn');
          }
        }
      }

      function previewSelectedReportTemplate() {
        const exam = activeExam();
        if (!exam || !exam.examCode) {
          updateReportTemplateAdminStatus('Select an exam first.', 'warn');
          return;
        }
        const templateId = selectedReportTemplateId();
        if (!templateId) {
          updateReportTemplateAdminStatus('Select a saved score card template first.', 'warn');
          return;
        }
        const entry = reportCardTemplateLibrary.find((item) => item.templateId === templateId);
        const modal = document.getElementById('templatePreviewModal');
        const frame = document.getElementById('templatePreviewFrame');
        const meta = document.getElementById('templatePreviewMeta');
        frame.src = reportDesignerStudioUrlForTemplate(templateId);
        meta.textContent = 'Exam: ' + exam.examCode + ' | Template: ' + ((entry && (entry.templateName || entry.templateId)) || templateId);
        modal.classList.add('open');
        modal.setAttribute('aria-hidden', 'false');
      }

      function closeTemplatePreviewModal() {
        const modal = document.getElementById('templatePreviewModal');
        const frame = document.getElementById('templatePreviewFrame');
        modal.classList.remove('open');
        modal.setAttribute('aria-hidden', 'true');
        frame.removeAttribute('src');
      }

      async function activateSelectedReportTemplate() {
        const exam = activeExam();
        if (!exam || !exam.examCode) {
          updateReportTemplateAdminStatus('Select an exam first.', 'warn');
          return;
        }
        const templateId = selectedReportTemplateId();
        if (!templateId) {
          updateReportTemplateAdminStatus('Select a saved score card template first.', 'warn');
          return;
        }
        try {
          const response = await fetch('/api/exams/' + encodeURIComponent(exam.examCode) + '/report-card-template/' + encodeURIComponent(templateId) + '/activate', {
            method: 'POST',
            credentials: 'same-origin'
          });
          const data = await response.json().catch(() => ({}));
          if (!response.ok) throw new Error(data.message || data.detail || response.statusText || 'Could not activate report-card template');
          await loadExams(exam.examCode);
          updateReportTemplateAdminStatus('Studio default updated. Candidate PDFs use the official layout.', 'ok');
        } catch (error) {
          updateReportTemplateAdminStatus('Could not activate score card template: ' + error.message, 'warn');
        }
      }

      function syncReportDesignerStudioLink() {
        const link = document.getElementById('reportDesignerStudioLink');
        if (!link) return;
        link.href = reportDesignerStudioUrl();
      }

      function syncReportDesignerStudioEmbed(forceReload = false) {
        const frame = document.getElementById('reportDesignerStudioFrame');
        const status = document.getElementById('reportDesignerStatus');
        const exam = activeExam();
        if (!frame || !status) return;
        if (!exam) {
          frame.removeAttribute('src');
          status.textContent = 'Select an exam project to load the unified report-card studio.';
          return;
        }
        const targetUrl = reportDesignerStudioUrl();
        if (forceReload || frame.dataset.loadedUrl !== targetUrl) {
          frame.src = targetUrl;
          frame.dataset.loadedUrl = targetUrl;
        }
        status.innerHTML = '<span class="ok">Unified studio ready</span><br>'
          + 'Exam: ' + escapeHtml(exam.examCode) + ' | Use placeholders and preview tools inside the embedded studio.';
      }

      function openReportDesignerStudio() {
        const exam = activeExam();
        if (exam && exam.examCode) {
          try {
            localStorage.setItem('reportDesignerActiveExam', JSON.stringify({
              examCode: exam.examCode,
              examName: exam.examName || exam.examCode,
              openedAt: new Date().toISOString()
            }));
          } catch (error) {
            console.warn('Could not persist report designer exam context', error);
          }
        }
        window.open(reportDesignerStudioUrl(), '_blank', 'noopener');
      }

      function openWorkspaceCard(id) {
        const current = activeExam();
        if (!current) return;
        currentWorkspaceScreen = id;
        document.querySelectorAll('[data-workspace-target]').forEach(button => {
          button.classList.toggle('active', button.dataset.workspaceTarget === id);
          button.setAttribute('aria-current', button.dataset.workspaceTarget === id ? 'page' : 'false');
        });
        document.getElementById('projectCreateCard').classList.add('hidden');
        document.getElementById('workspaceShell').classList.remove('hidden');
        document.querySelectorAll('.workspace-screen').forEach((panel) => {
          panel.classList.toggle('active', panel.id === id);
        });
        if (id === 'reportDesignerCard') {
          syncReportDesignerStudioEmbed();
        }
      }

      function showProjectCreator() {
        document.getElementById('projectCreateCard').classList.remove('hidden');
        document.getElementById('workspaceShell').classList.add('hidden');
      }

      function openWorkspaceHome() {
        openWorkspaceCard('workspaceDashboard');
      }
  
      function updateWorkspaceState() {
        const projectCreateCard = document.getElementById('projectCreateCard');
        const workspace = document.getElementById('workspaceShell');
        const projectHub = document.getElementById('projectHub');
        const gateNote = document.getElementById('projectGateNote');
        const current = activeExam();
        const hasProject = Boolean(current);
        projectCreateCard.classList.toggle('hidden', hasProject);
        workspace.classList.toggle('hidden', !hasProject);
        projectHub.classList.add('hidden');
        gateNote.classList.toggle('hidden', hasProject);
        document.getElementById('activeProjectLabel').textContent = current
          ? (current.examCode + ' - ' + current.name)
          : 'None';
        document.getElementById('workspaceProjectLabel').textContent = current
          ? (current.examCode + ' - ' + current.name)
          : 'None';
        document.getElementById('workspaceProjectCode').textContent = current ? current.examCode : '-';
        document.getElementById('workspaceProjectMode').textContent = current ? current.mode : '-';
        document.getElementById('workspaceProjectDuration').textContent = current ? ((current.durationMinutes || '-') + ' min') : '-';
        updateWorkspaceDashboard(current);
        if (hasProject) {
          openWorkspaceCard(currentWorkspaceScreen || 'workspaceDashboard');
        } else {
          currentWorkspaceScreen = 'workspaceDashboard';
          document.querySelectorAll('.workspace-screen').forEach((panel) => {
            panel.classList.toggle('active', panel.id === 'workspaceDashboard');
          });
        }
      }

    function updateWorkspaceDashboard(exam) {
      const candidateRows = exam ? workspaceSnapshot.recentCandidates : [];
      const candidateCount = exam ? Number(workspaceSnapshot.candidateCount || 0) : 0;
      const notificationCount = Number(workspaceSnapshot.notificationCount || 0);
      document.getElementById('workspaceRegistrationWindow').textContent = exam
        ? (exam.registrationOpen ? 'OPEN' : 'CLOSED')
        : '-';
      document.getElementById('workspaceRegistrationMeta').textContent = exam
        ? ('Corrections are ' + (exam.correctionsOpen ? 'OPEN' : 'CLOSED') + '. Lobby is ' + (exam.examLobbyEnabled ? 'ON' : 'OFF') + '.')
        : 'Open or close registration from Exam Settings.';
      document.getElementById('workspaceCandidateCount').textContent = String(candidateCount);
      document.getElementById('workspaceCandidateMeta').textContent = exam
        ? (candidateRows.length
          ? ('Latest registration: ' + (candidateRows[0].registrationId || '-') + ' by ' + (candidateRows[0].fullName || 'candidate') + '.')
          : 'No candidates registered.')
        : 'Candidates linked to the selected project.';
      document.getElementById('workspacePaperState').textContent = exam
        ? (exam.questionPaperUploaded ? 'READY' : 'MISSING')
        : '-';
      document.getElementById('workspacePaperMeta').textContent = exam
        ? (exam.questionPaperUploaded
          ? ((exam.questionPaperOriginalName || 'Question paper') + ' | ' + (exam.questionPaperQuestionCount || 0) + ' questions | Launch ' + (exam.examLaunchEnabled ? 'enabled' : 'blocked'))
          : 'Upload a JSON question paper to activate exam ordering controls.')
        : 'Upload a JSON question paper to activate exam ordering controls.';
      document.getElementById('workspaceNotificationCount').textContent = String(notificationCount);
      document.getElementById('workspaceNotificationMeta').textContent = exam
        ? (notificationCount
          ? ('SMTP sender: ' + (exam.notificationSenderEmail || 'not configured') + '. Latest outbox rows are paged in Database View.')
          : 'No notification outbox rows are visible right now.')
        : 'Email/SMS outbox rows for this project.';
      renderWorkspaceRecentCandidates(candidateRows);
    }

    function renderWorkspaceRecentCandidates(rows) {
      const host = document.getElementById('workspaceRecentCandidates');
      if (!rows.length) {
        host.innerHTML = '<div class="workspace-feed-item"><strong>No candidate data.</strong></div>';
        return;
      }
      host.innerHTML = rows.slice(0, 4).map((student) =>
        '<div class="workspace-feed-item">'
        + '<strong>' + escapeHtml(student.fullName || 'Candidate') + '</strong>'
        + '<span>'
        + escapeHtml(student.registrationId || '-')
        + ' | ' + escapeHtml(student.email || student.phone || 'No contact saved')
        + ' | Created ' + escapeHtml(student.createdAt || '-')
        + '</span>'
        + '</div>'
      ).join('');
    }

    async function refreshWorkspaceSnapshot(examCode = '') {
      if (!examCode) {
        workspaceSnapshot = {
          candidateCount: 0,
          recentCandidates: [],
          notificationCount: adminPageState.notifications.totalElements,
          responseCount: adminPageState.responses.totalElements
        };
        return;
      }
      try {
        const candidatePage = await jsonRequest(
          pagedUrl('/api/students', 'students', { page: 0, size: 4, examCode }),
          'GET'
        );
        workspaceSnapshot = {
          candidateCount: Number(candidatePage.totalElements || 0),
          recentCandidates: Array.isArray(candidatePage.content) ? candidatePage.content : [],
          notificationCount: adminPageState.notifications.totalElements,
          responseCount: adminPageState.responses.totalElements
        };
      } catch (error) {
        workspaceSnapshot = {
          candidateCount: 0,
          recentCandidates: [],
          notificationCount: adminPageState.notifications.totalElements,
          responseCount: adminPageState.responses.totalElements
        };
      }
    }

    function reportTemplateKey() {
      const exam = activeExam();
      return 'reportCardTemplate:' + (exam ? exam.examCode : 'unselected');
    }

    function defaultReportTemplate() {
      const exam = activeExam();
      return [
        reportText('title', 72, 42, 650, 48, (exam ? exam.name : 'EXAM') + ' SCORE CARD', {
          fontSize: 24,
          fontWeight: '700',
          textAlign: 'center',
          color: '#4b2f91'
        }),
        reportText('candidate', 80, 142, 310, 34, 'Candidate Name: {{candidateName}}', { fontSize: 18, fontWeight: '700' }),
        reportText('app', 80, 184, 310, 32, 'Application No: {{applicationNumber}}', { fontSize: 16 }),
        reportText('score', 430, 142, 260, 40, 'Score: {{score}} / {{maxMarks}}', { fontSize: 20, fontWeight: '700' }),
        reportText('rank', 430, 188, 260, 34, 'Rank: {{rank}}', { fontSize: 18 }),
        reportText('footer', 72, 1032, 650, 34, 'This score card is computer generated and valid only after official result declaration.', {
          fontSize: 12,
          textAlign: 'center',
          color: '#5f6f7f'
        })
      ];
    }

    function reportText(idPrefix, x, y, w, h, text, overrides = {}) {
      return {
        id: idPrefix + '-' + Date.now() + '-' + Math.floor(Math.random() * 10000),
        type: 'text',
        x, y, w, h,
        text,
        fontFamily: 'Arial, Helvetica, sans-serif',
        fontSize: overrides.fontSize || 16,
        fontWeight: overrides.fontWeight || '400',
        fontStyle: overrides.fontStyle || 'normal',
        textDecoration: overrides.textDecoration || 'none',
        textAlign: overrides.textAlign || 'left',
        color: overrides.color || '#10243f',
        borderColor: overrides.borderColor || '#10243f',
        borderWidth: overrides.borderWidth || 0,
        opacity: overrides.opacity || 100,
        z: reportElements.length + 2
      };
    }

    function reportImage(src, x = 90, y = 90, w = 160, h = 120) {
      return {
        id: 'image-' + Date.now() + '-' + Math.floor(Math.random() * 10000),
        type: 'image',
        x, y, w, h,
        src,
        borderColor: '#10243f',
        borderWidth: 0,
        opacity: 100,
        z: reportElements.length + 2
      };
    }

    function loadReportTemplate() {
      const status = document.getElementById('reportDesignerStatus');
      const exam = activeExam();
      if (!exam) {
        reportElements = [];
        reportWatermark = defaultReportWatermark();
        selectedReportElementId = null;
        renderReportCanvas();
        status.textContent = 'Select an exam project before designing a report card.';
        return;
      }
      try {
        const raw = localStorage.getItem(reportTemplateKey());
        const parsed = raw ? JSON.parse(raw) : null;
        reportElements = parsed ? parsed.elements || [] : defaultReportTemplate();
        reportWatermark = normalizeReportWatermark(parsed && parsed.watermark ? parsed.watermark : defaultReportWatermark());
      } catch (error) {
        reportElements = defaultReportTemplate();
        reportWatermark = defaultReportWatermark();
      }
      selectedReportElementId = null;
      renderReportCanvas();
      status.textContent = 'Designer ready for ' + exam.examCode + '. Drag objects on the A4 page, or paste/import images.';
    }

    function renderReportCanvas() {
      const canvas = document.getElementById('reportCanvas');
      if (!canvas) return;
      canvas.innerHTML = reportWatermarkHtml() + reportElements
        .slice()
        .sort((a, b) => (a.z || 0) - (b.z || 0))
        .map(reportElementHtml)
        .join('');
      bindReportElementEvents();
      syncReportControls();
    }

    function defaultReportWatermark() {
      return { type: 'none', text: '', src: '', size: 72, opacity: 8, angle: -28, color: '#0d5ca6', x: 0, y: 0, w: 794, h: 1123 };
    }

    function normalizeReportWatermark(value) {
      return Object.assign(defaultReportWatermark(), value || {});
    }

    function reportWatermarkHtml() {
      if (!reportWatermark || reportWatermark.type === 'none') {
        return '<span class="report-guide v" id="reportGuideV"></span><span class="report-guide h" id="reportGuideH"></span>';
      }
      const opacity = Math.max(1, Math.min(60, Number(reportWatermark.opacity) || 8)) / 100;
      const angle = Number(reportWatermark.angle) || 0;
      const size = Math.max(12, Math.min(180, Number(reportWatermark.size) || 72));
      const selected = selectedReportElementId === '__watermark' ? ' selected' : '';
      const box = 'left:' + (reportWatermark.x || 0) + 'px;top:' + (reportWatermark.y || 0) + 'px;width:' + (reportWatermark.w || 794) + 'px;height:' + (reportWatermark.h || 1123) + 'px;opacity:' + opacity + ';transform:rotate(' + angle + 'deg);';
      let content = '';
      if (reportWatermark.type === 'image' && reportWatermark.src) {
        content = '<img class="report-watermark-image" src="' + escapeHtml(reportWatermark.src) + '" alt="Watermark">';
      } else if (reportWatermark.type === 'text' && reportWatermark.text) {
        content = '<div class="report-watermark-text" style="font-size:' + size + 'px;color:' + escapeHtml(reportWatermark.color || '#0d5ca6') + '">'
          + escapeHtml(reportWatermark.text)
          + '</div>';
      }
      return '<div class="report-watermark' + selected + '" data-report-id="__watermark" style="' + box + '">' + content + '</div>'
        + '<span class="report-guide v" id="reportGuideV"></span><span class="report-guide h" id="reportGuideH"></span>';
    }

    function reportElementHtml(el) {
      const selected = el.id === selectedReportElementId ? ' selected' : '';
      const style = 'left:' + el.x + 'px;top:' + el.y + 'px;width:' + el.w + 'px;height:' + el.h + 'px;'
        + 'z-index:' + (el.z || 2) + ';'
        + 'opacity:' + ((el.opacity || 100) / 100) + ';'
        + 'border-width:' + (el.borderWidth || 0) + 'px;'
        + 'border-style:solid;'
        + 'border-color:' + escapeHtml(el.borderColor || 'transparent') + ';';
      if (el.type === 'image') {
        return '<div class="report-item' + selected + '" data-report-id="' + escapeHtml(el.id) + '" style="' + style + '">'
          + '<img class="report-image" src="' + escapeHtml(el.src || '') + '" alt="Report image">'
          + reportResizeHandles() + '</div>';
      }
      const textStyle = 'font-family:' + escapeHtml(el.fontFamily || 'Arial, Helvetica, sans-serif') + ';'
        + 'font-size:' + (el.fontSize || 16) + 'px;'
        + 'font-weight:' + escapeHtml(el.fontWeight || '400') + ';'
        + 'font-style:' + escapeHtml(el.fontStyle || 'normal') + ';'
        + 'text-decoration:' + escapeHtml(el.textDecoration || 'none') + ';'
        + 'text-align:' + escapeHtml(el.textAlign || 'left') + ';'
        + 'color:' + escapeHtml(el.color || '#10243f') + ';';
      return '<div class="report-item' + selected + '" data-report-id="' + escapeHtml(el.id) + '" style="' + style + '">'
        + '<div class="report-text" contenteditable="true" style="' + textStyle + '">' + escapeHtml(el.text || '') + '</div>'
        + reportResizeHandles() + '</div>';
    }

    function reportResizeHandles() {
      return ['nw', 'n', 'ne', 'e', 'se', 's', 'sw', 'w']
        .map((handle) => '<span class="report-resize ' + handle + '" data-resize-handle="' + handle + '"></span>')
        .join('');
    }

    function bindReportElementEvents() {
      const watermark = document.querySelector('.report-watermark');
      if (watermark) {
        watermark.addEventListener('pointerdown', startReportPointer);
      }
      document.querySelectorAll('.report-item').forEach((node) => {
        node.addEventListener('pointerdown', startReportPointer);
        const text = node.querySelector('.report-text');
        if (text) {
          text.addEventListener('input', () => {
            const el = selectedReportElement();
            if (el) {
              el.text = text.textContent;
              document.getElementById('reportTextContent').value = el.text;
            }
          });
        }
      });
    }

    function startReportPointer(event) {
      const item = event.currentTarget;
      const id = item.dataset.reportId;
      selectReportElement(id);
      const resizeHandle = event.target.dataset.resizeHandle || '';
      const resize = Boolean(resizeHandle);
      const el = selectedReportElement();
      if (!el) return;
      reportPointer = {
        id,
        resize,
        resizeHandle,
        startX: event.clientX,
        startY: event.clientY,
        x: el.x,
        y: el.y,
        w: el.w,
        h: el.h
      };
      item.setPointerCapture(event.pointerId);
      event.preventDefault();
    }

    function updateReportPointer(event) {
      if (!reportPointer) return;
      const el = reportElements.find((row) => row.id === reportPointer.id);
      if (!el) return;
      const dx = event.clientX - reportPointer.startX;
      const dy = event.clientY - reportPointer.startY;
      if (reportPointer.resize) {
        resizeReportElement(el, reportPointer, dx, dy);
      } else {
        const snapped = snapReportPosition(
          Math.max(0, Math.min(794 - el.w, Math.round(reportPointer.x + dx))),
          Math.max(0, Math.min(1123 - el.h, Math.round(reportPointer.y + dy))),
          el.w,
          el.h
        );
        el.x = snapped.x;
        el.y = snapped.y;
      }
      renderReportCanvas();
    }

    function resizeReportElement(el, start, dx, dy) {
      const handle = start.resizeHandle;
      let x = start.x;
      let y = start.y;
      let w = start.w;
      let h = start.h;
      if (handle.includes('e')) w = start.w + dx;
      if (handle.includes('s')) h = start.h + dy;
      if (handle.includes('w')) {
        x = start.x + dx;
        w = start.w - dx;
      }
      if (handle.includes('n')) {
        y = start.y + dy;
        h = start.h - dy;
      }
      if (w < 28) {
        if (handle.includes('w')) x = start.x + start.w - 28;
        w = 28;
      }
      if (h < 24) {
        if (handle.includes('n')) y = start.y + start.h - 24;
        h = 24;
      }
      el.x = Math.max(0, Math.min(794 - w, Math.round(x)));
      el.y = Math.max(0, Math.min(1123 - h, Math.round(y)));
      el.w = Math.min(794 - el.x, Math.round(w));
      el.h = Math.min(1123 - el.y, Math.round(h));
    }

    function stopReportPointer() {
      reportPointer = null;
      hideReportGuides();
    }

    function selectedReportElement() {
      if (selectedReportElementId === '__watermark') {
        return reportWatermark && reportWatermark.type !== 'none' ? reportWatermark : null;
      }
      return reportElements.find((el) => el.id === selectedReportElementId) || null;
    }

    function selectReportElement(id) {
      selectedReportElementId = id;
      renderReportCanvas();
    }

    function syncReportControls() {
      const el = selectedReportElement();
      const textDisabled = !el || el.type !== 'text';
      document.getElementById('reportTextContent').disabled = textDisabled;
      document.getElementById('reportTextContent').value = el && el.type === 'text' ? (el.text || '') : '';
      if (!el) return;
      document.getElementById('reportFontFamily').value = el.fontFamily || 'Arial, Helvetica, sans-serif';
      document.getElementById('reportFontSize').value = el.fontSize || 16;
      document.getElementById('reportTextColor').value = el.color || '#10243f';
      document.getElementById('reportBoxColor').value = el.borderColor || '#10243f';
      document.getElementById('reportBorderWidth').value = el.borderWidth || 0;
      document.getElementById('reportOpacity').value = el.opacity || 100;
      document.getElementById('reportWatermarkText').value = reportWatermark.text || '';
      document.getElementById('reportWatermarkSize').value = reportWatermark.size || 72;
      document.getElementById('reportWatermarkOpacity').value = reportWatermark.opacity || 8;
      document.getElementById('reportWatermarkAngle').value = reportWatermark.angle || -28;
      document.getElementById('reportWatermarkColor').value = reportWatermark.color || '#0d5ca6';
    }

    function patchSelectedReport(updates) {
      const el = selectedReportElement();
      if (!el) return;
      Object.assign(el, updates);
      renderReportCanvas();
    }

    function addReportTextBox() {
      reportElements.push(reportText('text', 120, 260, 260, 42, 'New text box'));
      selectedReportElementId = reportElements[reportElements.length - 1].id;
      renderReportCanvas();
    }

    function addReportImageFromDataUrl(src) {
      reportElements.push(reportImage(src));
      selectedReportElementId = reportElements[reportElements.length - 1].id;
      renderReportCanvas();
    }

    function saveReportTemplate() {
      const exam = activeExam();
      if (!exam) return;
      localStorage.setItem(reportTemplateKey(), JSON.stringify({
        version: 1,
        examCode: exam.examCode,
        page: { size: 'A4', width: 794, height: 1123 },
        watermark: reportWatermark,
        elements: reportElements
      }));
      document.getElementById('reportDesignerStatus').innerHTML = '<span class="ok">Template saved locally</span>\n' + exam.examCode + ' report card template has ' + reportElements.length + ' object(s).';
    }

    function exportReportTemplate() {
      const exam = activeExam();
      const payload = {
        version: 1,
        examCode: exam ? exam.examCode : '',
        page: { size: 'A4', width: 794, height: 1123 },
        watermark: reportWatermark,
        elements: reportElements
      };
      document.getElementById('reportDesignerStatus').textContent = JSON.stringify(payload, null, 2);
    }

    function reportFileToImage(file) {
      if (!file || !file.type.startsWith('image/')) return;
      const reader = new FileReader();
      reader.onload = () => addReportImageFromDataUrl(reader.result);
      reader.readAsDataURL(file);
    }

    function applyReportWatermark() {
      const text = document.getElementById('reportWatermarkText').value.trim();
      reportWatermark = {
        type: text ? 'text' : reportWatermark.type === 'image' ? 'image' : 'none',
        text,
        src: reportWatermark.type === 'image' && !text ? reportWatermark.src : '',
        size: Number(document.getElementById('reportWatermarkSize').value) || 72,
        opacity: Number(document.getElementById('reportWatermarkOpacity').value) || 8,
        angle: Number(document.getElementById('reportWatermarkAngle').value) || -28,
        color: document.getElementById('reportWatermarkColor').value || '#0d5ca6',
        x: reportWatermark.x || 0,
        y: reportWatermark.y || 0,
        w: reportWatermark.w || 794,
        h: reportWatermark.h || 1123
      };
      renderReportCanvas();
    }

    function clearReportWatermark() {
      reportWatermark = defaultReportWatermark();
      if (selectedReportElementId === '__watermark') selectedReportElementId = null;
      document.getElementById('reportWatermarkText').value = '';
      renderReportCanvas();
    }

    function reportWatermarkImage(file) {
      if (!file || !file.type.startsWith('image/')) return;
      const reader = new FileReader();
      reader.onload = () => {
        reportWatermark = {
          type: 'image',
          text: '',
          src: reader.result,
          size: Number(document.getElementById('reportWatermarkSize').value) || 72,
          opacity: Number(document.getElementById('reportWatermarkOpacity').value) || 8,
          angle: Number(document.getElementById('reportWatermarkAngle').value) || -28,
          color: document.getElementById('reportWatermarkColor').value || '#0d5ca6',
          x: reportWatermark.x || 0,
          y: reportWatermark.y || 0,
          w: reportWatermark.w || 794,
          h: reportWatermark.h || 1123
        };
        selectedReportElementId = '__watermark';
        renderReportCanvas();
      };
      reader.readAsDataURL(file);
    }

    function deleteSelectedReportElement() {
      if (!selectedReportElementId) return;
      if (selectedReportElementId === '__watermark') {
        clearReportWatermark();
        return;
      }
      reportElements = reportElements.filter((el) => el.id !== selectedReportElementId);
      selectedReportElementId = null;
      renderReportCanvas();
    }

    function nudgeSelectedReportElement(dx, dy) {
      const el = selectedReportElement();
      if (!el) return;
      el.x = Math.max(0, Math.min(794 - el.w, el.x + dx));
      el.y = Math.max(0, Math.min(1123 - el.h, el.y + dy));
      renderReportCanvas();
    }

    function snapReportPosition(x, y, w, h) {
      const guides = [
        { axis: 'v', pos: 397, value: x + w / 2, snap: 397 - w / 2 },
        { axis: 'v', pos: 40, value: x, snap: 40 },
        { axis: 'v', pos: 754, value: x + w, snap: 754 - w },
        { axis: 'h', pos: 562, value: y + h / 2, snap: 562 - h / 2 },
        { axis: 'h', pos: 40, value: y, snap: 40 },
        { axis: 'h', pos: 1083, value: y + h, snap: 1083 - h }
      ];
      hideReportGuides();
      let outX = x;
      let outY = y;
      guides.forEach((guide) => {
        if (Math.abs(guide.value - guide.pos) <= 6) {
          if (guide.axis === 'v') {
            outX = Math.round(guide.snap);
            showReportGuide('v', guide.pos);
          } else {
            outY = Math.round(guide.snap);
            showReportGuide('h', guide.pos);
          }
        }
      });
      return { x: outX, y: outY };
    }

    function showReportGuide(axis, pos) {
      const guide = document.getElementById(axis === 'v' ? 'reportGuideV' : 'reportGuideH');
      if (!guide) return;
      guide.style.display = 'block';
      if (axis === 'v') guide.style.left = pos + 'px';
      else guide.style.top = pos + 'px';
    }

    function hideReportGuides() {
      ['reportGuideV', 'reportGuideH'].forEach((id) => {
        const guide = document.getElementById(id);
        if (guide) guide.style.display = 'none';
      });
    }

    async function loadExams(preferredExamCode = '') {
      const result = document.getElementById('examResult');
      try {
        exams = await jsonRequest('/api/exams', 'GET');
        renderExamSelect(preferredExamCode);
        const examPage = adminPageState.exams;
        const maxPage = Math.max(Math.ceil(exams.length / examPage.size) - 1, 0);
        examPage.page = Math.min(examPage.page, maxPage);
        const start = examPage.page * examPage.size;
        const examRows = exams.slice(start, start + examPage.size);
        renderExamDbRows(examRows);
        renderPager('examDbPager', 'exams', {
          page: examPage.page,
          size: examPage.size,
          totalElements: exams.length,
          totalPages: Math.max(Math.ceil(exams.length / examPage.size), exams.length ? 1 : 0),
          hasNext: start + examPage.size < exams.length,
          hasPrevious: examPage.page > 0
        }, 'exam(s)');
        if (!exams.length) {
          result.textContent = 'No exams created yet.';
          updateWorkspaceState();
          return;
        }
        result.textContent = exams.map((exam) =>
          exam.examCode + ' | ' + exam.name + ' | ' + exam.mode + ' | ' + exam.durationMinutes + ' min'
          + ' | registration ' + (exam.registrationOpen ? 'OPEN' : 'CLOSED')
        ).join('\\n');
        updateWorkspaceState();
      } catch (error) {
        result.innerHTML = '<span class="error">Could not load exams</span>\\n' + error.message;
        updateWorkspaceState();
      }
    }

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

    function applyAdminTheme(theme) {
      const dark = theme === 'dark';
      document.body.classList.toggle('dark-theme', dark);
      document.getElementById('themeToggle').textContent = dark ? 'Light Theme' : 'Black Theme';
      try {
        localStorage.setItem('examAdminTheme', dark ? 'dark' : 'light');
      } catch (error) {
        // Ignore storage failures in restricted browsers.
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

    function renderExamDbRows(rows) {
      const tbody = document.getElementById('examDbRows');
      if (!rows.length) {
        tbody.innerHTML = '<tr><td colspan="8">No exams saved yet.</td></tr>';
        return;
      }
      tbody.innerHTML = rows.map((exam) => {
        const streams = (exam.streams || [])
          .map((stream) => escapeHtml(stream.shortCode + ' - ' + stream.fullName + (stream.enabled ? '' : ' [off]')))
          .join('<br>');
        const fields = (exam.enabledFields || []).join(', ');
        const requiredFields = Object.entries(exam.candidateRequiredFields || {})
          .filter((entry) => entry[1] === 'true' || entry[1] === true)
          .map((entry) => entry[0])
          .join(', ');
        const correctionFields = Object.entries(exam.candidateCorrectionFields || {})
          .filter((entry) => entry[1] === 'true' || entry[1] === true)
          .map((entry) => entry[0])
          .join(', ');
        return '<tr>'
          + '<td>' + escapeHtml(exam.examCode) + '</td>'
          + '<td>' + escapeHtml(exam.name) + '</td>'
          + '<td>' + escapeHtml(exam.mode) + '<br>' + exam.durationMinutes + ' min</td>'
          + '<td>' + (exam.registrationOpen ? 'Open' : 'Closed') + '</td>'
          + '<td>' + (exam.correctionsOpen ? 'Open' : 'Closed') + '</td>'
          + '<td>' + (streams || '-') + '</td>'
            + '<td>' + escapeHtml(fields || '-') + '<br>Required: ' + escapeHtml(requiredFields || '-') + '<br>Correction allowed: ' + escapeHtml(correctionFields || '-') + '<br>Extra boxes: ' + escapeHtml((exam.customFields || []).map((field) => field.label + ' [' + field.step + ']').join(', ') || '-') + '<br>Paper: ' + escapeHtml(exam.questionPaperOriginalName || '-') + ' (' + escapeHtml(exam.questionPaperQuestionCount || 0) + ' questions)' + '<br>Original QP PDF: ' + escapeHtml(exam.originalQuestionPaperPdfName || '-') + '<br>Original AK PDF: ' + escapeHtml(exam.originalAnswerKeyPdfName || '-') + '<br>Start: ' + escapeHtml(exam.examStartAt || '-') + '<br>Lobby: ' + (exam.examLobbyEnabled ? 'on' : 'off') + ', Launch: ' + (exam.examLaunchEnabled ? 'on' : 'off') + ', Responses: ' + (exam.responsesReleased ? 'released' : 'hidden') + ', Result: ' + (exam.resultReleased ? 'released' : 'hidden') + ', Score PDF: ' + (exam.scoreCardPdfReleased ? 'released' : 'hidden') + '<br>Notices: ' + escapeHtml(exam.portalNotifications || exam.portalNotification || '-') + '<br>Header: ' + escapeHtml(exam.portalHeaderTitle || '-') + '<br>Welcome: ' + escapeHtml(exam.portalWelcomeText || '-') + '<br>Hero: ' + escapeHtml(exam.portalHeroImageUrl || '-') + '<br>Top: ' + escapeHtml(exam.portalTopBannerImageUrl || '-') + '<br>Background: ' + escapeHtml(exam.portalPageBackgroundImageUrl || '-') + '<br>SMTP sender: ' + escapeHtml(exam.notificationSenderEmail || '-') + '<br>Sender password: ' + (exam.notificationSenderAppPasswordSaved ? 'saved' : 'not saved') + '</td>'
          + '<td><button class="danger" type="button" data-delete-exam="' + escapeHtml(exam.examCode) + '">Delete</button></td>'
          + '</tr>';
      }).join('');
      tbody.querySelectorAll('[data-delete-exam]').forEach((button) => {
        button.addEventListener('click', () => deleteExam(button.dataset.deleteExam));
      });
    }

    function studentFilePreviewHtml(student) {
      const registrationId = escapeHtml(student.registrationId || '');
      const chips = [
        student.photoPath ? '<button class="secondary student-file-btn" type="button" data-student-file-kind="photo" data-student-id="' + registrationId + '">Photo</button>' : '',
        student.signaturePath ? '<button class="secondary student-file-btn" type="button" data-student-file-kind="signature" data-student-id="' + registrationId + '">Sign</button>' : '',
        student.idCardPath ? '<button class="secondary student-file-btn" type="button" data-student-file-kind="id-card" data-student-id="' + registrationId + '">ID</button>' : '',
        student.class10MarksheetPath ? '<button class="secondary student-file-btn" type="button" data-student-file-kind="class-10" data-student-id="' + registrationId + '">10th</button>' : '',
        student.class12MarksheetPath ? '<button class="secondary student-file-btn" type="button" data-student-file-kind="class-12" data-student-id="' + registrationId + '">12th</button>' : '',
        student.ugDocumentPath ? '<button class="secondary student-file-btn" type="button" data-student-file-kind="ug" data-student-id="' + registrationId + '">UG</button>' : '',
        student.pgDocumentPath ? '<button class="secondary student-file-btn" type="button" data-student-file-kind="pg" data-student-id="' + registrationId + '">PG</button>' : ''
      ].filter(Boolean).join('');
      const photo = student.photoPath
        ? '<img class="student-photo-thumb" alt="Candidate photo" data-student-file-preview="photo" data-student-id="' + registrationId + '">'
        : '<span class="student-photo-empty">No photo</span>';
      return '<div class="student-file-panel">' + photo + '<div class="student-file-actions">' + (chips || '-') + '</div></div>';
    }

    async function fetchStudentFileDataUrl(registrationId, kind) {
      const data = await jsonRequest('/api/students/' + encodeURIComponent(registrationId) + '/file?kind=' + encodeURIComponent(kind), 'GET');
      return data.dataUrl || '';
    }

    async function loadStudentFilePreview(image) {
      try {
        const dataUrl = await fetchStudentFileDataUrl(image.dataset.studentId, image.dataset.studentFilePreview);
        if (dataUrl && dataUrl.startsWith('data:image/')) {
          image.src = dataUrl;
        } else {
          image.replaceWith(Object.assign(document.createElement('span'), { className: 'student-photo-empty', textContent: 'Photo unavailable' }));
        }
      } catch (ignored) {
        image.replaceWith(Object.assign(document.createElement('span'), { className: 'student-photo-empty', textContent: 'Photo unavailable' }));
      }
    }

    async function openStudentFile(registrationId, kind) {
      const status = document.getElementById('dbStatus');
      try {
        const dataUrl = await fetchStudentFileDataUrl(registrationId, kind);
        if (!dataUrl) throw new Error('File data is empty.');
        const win = window.open();
        if (win) {
          win.document.write('<iframe title="Candidate file" src="' + dataUrl + '" style="border:0;width:100%;height:100vh"></iframe>');
          win.document.close();
        }
      } catch (error) {
        if (status) status.innerHTML = '<span class="error">Could not open candidate file</span>\\n' + escapeHtml(error.message);
      }
    }

    function renderStudentDbRows(rows) {
      const tbody = document.getElementById('studentDbRows');
      if (!rows.length) {
        tbody.innerHTML = '<tr><td colspan="9">No students saved yet.</td></tr>';
        return;
      }
      tbody.innerHTML = rows.map((student) => {
        const files = [
          student.photoPath && 'photo',
          student.signaturePath && 'signature',
          student.idCardPath && 'id',
          student.class10MarksheetPath && '10th',
          student.class12MarksheetPath && '12th',
          student.ugDocumentPath && 'ug',
          student.pgDocumentPath && 'pg'
        ].filter(Boolean).join(', ');
        return '<tr>'
        + '<td>' + escapeHtml(student.registrationId) + '<br>' + escapeHtml(student.examCode || '-') + '/' + escapeHtml(student.streamCode || '-') + '</td>'
        + '<td>' + escapeHtml(student.fullName) + '</td>'
        + '<td>' + escapeHtml(student.dateOfBirth) + '</td>'
        + '<td>' + escapeHtml(student.email || '-') + '</td>'
        + '<td>' + escapeHtml(student.phone || '-') + '</td>'
        + '<td>' + escapeHtml([student.guardianName, student.guardianRelation, student.guardianPhone].filter(Boolean).join(' | ') || '-') + '</td>'
        + '<td>' + studentFilePreviewHtml(student) + '<div class="student-file-list">' + escapeHtml(files || '-') + '</div></td>'
        + '<td>' + escapeHtml(student.createdAt || '-') + '</td>'
        + '<td><button class="danger" type="button" data-delete-student="' + escapeHtml(student.registrationId) + '">Delete</button></td>'
        + '</tr>';
      }).join('');
      tbody.querySelectorAll('[data-delete-student]').forEach((button) => {
        button.addEventListener('click', () => deleteStudent(button.dataset.deleteStudent));
      });
      tbody.querySelectorAll('[data-student-file-preview]').forEach((image) => {
        loadStudentFilePreview(image);
      });
      tbody.querySelectorAll('[data-student-file-kind]').forEach((button) => {
        button.addEventListener('click', () => openStudentFile(button.dataset.studentId, button.dataset.studentFileKind));
      });
    }

    function renderNotificationDbRows(rows) {
      const tbody = document.getElementById('notificationDbRows');
      if (!rows.length) {
        tbody.innerHTML = '<tr><td colspan="10">No notification rows yet.</td></tr>';
        return;
      }
      tbody.innerHTML = rows.map((notification) =>
        '<tr>'
        + '<td>' + escapeHtml(notification.id) + '</td>'
        + '<td>' + escapeHtml(notification.channel) + '</td>'
        + '<td>' + escapeHtml(notification.recipient) + '</td>'
        + '<td>' + escapeHtml(notification.senderEmail || '-') + '</td>'
        + '<td>' + escapeHtml(notification.subject) + '</td>'
        + '<td>' + escapeHtml(notification.body) + '</td>'
        + '<td>' + escapeHtml(notification.status) + '</td>'
        + '<td>' + escapeHtml(notification.createdAt || '-') + '</td>'
        + '<td>' + escapeHtml(notification.sentAt || '-') + '</td>'
        + '<td>' + escapeHtml(notification.errorMessage || '-') + '</td>'
        + '</tr>'
      ).join('');
    }

    async function refreshResponseCenter() {
      const status = document.getElementById('responseStatus');
      status.textContent = 'Refreshing response database view...';
      try {
        const responsePage = await jsonRequest(pagedUrl('/api/responses', 'responses'), 'GET');
        responseRows = Array.isArray(responsePage.content) ? responsePage.content : [];
        setAdminPageMeta('responses', responsePage);
        renderResponseRows(responseRows);
        renderPager('responsePager', 'responses', responsePage, 'response sheet(s)');
        workspaceSnapshot.responseCount = Number(responsePage.totalElements || 0);
        status.innerHTML = '<span class="ok">Response database refreshed</span>\\n'
          + responsePage.totalElements + ' saved response sheet(s) across ' + Math.max(responsePage.totalPages || 1, 1) + ' page(s).';
        updateWorkspaceState();
      } catch (error) {
        status.innerHTML = '<span class="error">Response refresh failed</span>\\n' + error.message;
      }
    }

    async function uploadAnswerKey() {
      const exam = activeExam();
      const status = document.getElementById('evaluationStatus');
      const fileInput = document.getElementById('answerKeyFile');
      if (!exam) {
        status.textContent = 'Select an exam project first.';
        return;
      }
      if (!fileInput.files[0]) {
        status.textContent = 'Choose an answer key JSON file first.';
        return;
      }
      status.textContent = 'Uploading answer key...';
      try {
        const formData = new FormData();
        formData.append('file', fileInput.files[0]);
        const response = await fetch('/api/exams/' + encodeURIComponent(exam.examCode) + '/answer-key', {
          method: 'POST',
          body: formData
        });
        const text = await response.text();
        const data = text ? JSON.parse(text) : {};
        if (!response.ok) throw new Error(data.detail || data.message || text || response.statusText);
        status.innerHTML = '<span class="ok">Answer key uploaded</span>\\n'
          + escapeHtml(data.answerKeyOriginalName || fileInput.files[0].name)
          + ' | Max marks: ' + escapeHtml(data.answerKeyTotalMarks || 0);
        fileInput.value = '';
        await loadExams();
        await validateActivePaperKey();
        await refreshEvaluation();
      } catch (error) {
        status.innerHTML = '<span class="error">Answer key upload failed</span>\\n' + escapeHtml(error.message);
      }
    }

    async function clearAnswerKey() {
      const exam = activeExam();
      const status = document.getElementById('evaluationStatus');
      if (!exam) {
        status.textContent = 'Select an exam project first.';
        return;
      }
      if (!confirm('Clear the uploaded server answer key for this exam? Candidate responses stay untouched.')) {
        return;
      }
      status.textContent = 'Clearing server answer key...';
      try {
        const data = await jsonRequest('/api/exams/' + encodeURIComponent(exam.examCode) + '/answer-key', 'DELETE');
        document.getElementById('answerKeyFile').value = '';
        status.innerHTML = '<span class="ok">Server answer key cleared</span>\\nUpload the key matching the active assembled paper before evaluating.';
        await loadExams(data.examCode);
        await validateActivePaperKey();
        await refreshEvaluation();
      } catch (error) {
        status.innerHTML = '<span class="error">Could not clear answer key</span>\\n' + escapeHtml(error.message);
      }
    }

    function renderPaperKeyStatus(data) {
      const status = document.getElementById('activePaperKeyStatus');
      if (!status) return;
      const missing = Array.isArray(data.missingInKey) ? data.missingInKey : [];
      const extra = Array.isArray(data.extraInKey) ? data.extraInKey : [];
      status.innerHTML = '<span class="' + (data.matched ? 'ok' : 'error') + '">'
        + (data.matched ? 'Active pair matched' : 'Active pair not matched')
        + '</span>\\n'
        + escapeHtml(data.message || '-') + '\\n'
        + 'Paper: ' + escapeHtml(data.questionPaperName || '-') + ' | Q IDs: ' + escapeHtml(data.paperQuestionCount || 0) + '\\n'
        + 'Key: ' + escapeHtml(data.answerKeyName || '-') + ' | Answer IDs: ' + escapeHtml(data.keyAnswerCount || 0)
        + (data.keyTotalMarks == null ? '' : ' | Marks: ' + escapeHtml(data.keyTotalMarks)) + '\\n'
        + 'License: paper=' + escapeHtml(data.paperLicenseKey || '-') + ' | key=' + escapeHtml(data.keyLicenseKey || '-')
        + (missing.length ? '\\nMissing in key: ' + escapeHtml(missing.slice(0, 12).join(', ')) + (missing.length > 12 ? ' +' + (missing.length - 12) + ' more' : '') : '')
        + (extra.length ? '\\nExtra in key: ' + escapeHtml(extra.slice(0, 12).join(', ')) + (extra.length > 12 ? ' +' + (extra.length - 12) + ' more' : '') : '');
    }

    async function validateActivePaperKey() {
      const exam = activeExam();
      const status = document.getElementById('activePaperKeyStatus');
      if (!status) return;
      if (!exam) {
        status.textContent = 'Select an exam project first.';
        return;
      }
      status.textContent = 'Checking active server paper/key pair...';
      try {
        const data = await jsonRequest('/api/exams/' + encodeURIComponent(exam.examCode) + '/paper-key-status', 'GET');
        renderPaperKeyStatus(data);
      } catch (error) {
        status.innerHTML = '<span class="error">Pair check failed</span>\\n' + escapeHtml(error.message);
      }
    }

    async function downloadActiveExamJson(kind) {
      const exam = activeExam();
      const status = document.getElementById('activePaperKeyStatus');
      if (!exam) {
        if (status) status.textContent = 'Select an exam project first.';
        return;
      }
      const paper = kind === 'paper';
      const url = '/api/exams/' + encodeURIComponent(exam.examCode) + (paper ? '/active-question-paper' : '/active-answer-key');
      try {
        const response = await fetch(url);
        const text = await response.text();
        let body = text;
        if (!response.ok) {
          try {
            const data = text ? JSON.parse(text) : {};
            throw new Error(data.detail || data.message || text || response.statusText);
          } catch (parseError) {
            if (parseError instanceof Error && parseError.message) throw parseError;
            throw new Error(text || response.statusText);
          }
        }
        try {
          body = JSON.stringify(JSON.parse(text), null, 2);
        } catch (ignored) {
          body = text;
        }
        const blob = new Blob([body], { type: 'application/json' });
        const link = document.createElement('a');
        link.href = URL.createObjectURL(blob);
        link.download = (exam.examCode || 'exam') + (paper ? '_active_question_paper.json' : '_active_answer_key.json');
        document.body.appendChild(link);
        link.click();
        link.remove();
        URL.revokeObjectURL(link.href);
      } catch (error) {
        if (status) status.innerHTML = '<span class="error">Download failed</span>\\n' + escapeHtml(error.message);
      }
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
        status.innerHTML = '<span class="ok">Original question paper PDF uploaded</span>\\n' + escapeHtml(data.originalQuestionPaperPdfName || fileInput.files[0].name);
        fileInput.value = '';
        await loadExams();
      } catch (error) {
        status.innerHTML = '<span class="error">Original question paper PDF upload failed</span>\\n' + escapeHtml(error.message);
      }
    }

    async function uploadOriginalAnswerKeyPdf() {
      const exam = activeExam();
      const status = document.getElementById('originalAnswerKeyPdfStatus');
      const fileInput = document.getElementById('originalAnswerKeyPdfFile');
      if (!exam) {
        status.textContent = 'Select an exam project first.';
        return;
      }
      if (!fileInput.files[0]) {
        status.textContent = 'Choose the original answer key PDF first.';
        return;
      }
      status.textContent = 'Uploading original answer key PDF...';
      try {
        const formData = new FormData();
        formData.append('file', fileInput.files[0]);
        const response = await fetch('/api/exams/' + encodeURIComponent(exam.examCode) + '/original-answer-key-pdf', {
          method: 'POST',
          body: formData
        });
        const text = await response.text();
        const data = text ? JSON.parse(text) : {};
        if (!response.ok) throw new Error(data.detail || data.message || text || response.statusText);
        status.innerHTML = '<span class="ok">Original answer key PDF uploaded</span>\\n' + escapeHtml(data.originalAnswerKeyPdfName || fileInput.files[0].name);
        fileInput.value = '';
        await loadExams();
      } catch (error) {
        status.innerHTML = '<span class="error">Original answer key PDF upload failed</span>\\n' + escapeHtml(error.message);
      }
    }

    async function refreshEvaluation() {
      const exam = activeExam();
      const status = document.getElementById('evaluationStatus');
      if (!exam) {
        status.textContent = 'Select an exam project first.';
        return;
      }
      status.textContent = 'Evaluating saved responses...';
      try {
        const key = await jsonRequest('/api/evaluation/' + encodeURIComponent(exam.examCode) + '/answer-key', 'GET');
        if (!key.uploaded) {
          evaluationRows = [];
          renderEvaluationRows(evaluationRows);
          status.innerHTML = '<span class="error">No answer key uploaded</span>\\nUpload answer key JSON before evaluation.';
          return;
        }
        evaluationRows = await jsonRequest('/api/evaluation/' + encodeURIComponent(exam.examCode) + '/results', 'GET');
        renderEvaluationRows(evaluationRows);
        status.innerHTML = '<span class="ok">Evaluation complete</span>\\n'
          + evaluationRows.length + ' response sheet(s). Key: ' + escapeHtml(key.originalName || '-')
          + ' | Max marks: ' + escapeHtml(key.maxMarks || 0);
      } catch (error) {
        status.innerHTML = '<span class="error">Evaluation failed</span>\\n' + escapeHtml(error.message);
      }
    }

    async function refreshManualEvaluations() {
      const exam = activeExam();
      const status = document.getElementById('manualEvaluationStatus');
      const tbody = document.getElementById('manualEvaluationRows');
      if (!status || !tbody) return;
      if (!exam) {
        manualEvaluationRows = [];
        tbody.innerHTML = '<tr><td colspan="6">Select an exam project first.</td></tr>';
        status.innerHTML = '<span class="error">No exam project selected.</span>';
        return;
      }
      status.textContent = 'Loading the manual-evaluation approval ledger...';
      try {
        manualEvaluationRows = await jsonRequest('/api/evaluation/' + encodeURIComponent(exam.examCode) + '/manual-evaluator/requests', 'GET');
        renderManualEvaluationRows();
        status.innerHTML = '<span class="ok">Manual-evaluation ledger loaded</span>\\n' + manualEvaluationRows.length + ' request(s) for ' + escapeHtml(exam.examCode) + '.';
      } catch (error) {
        manualEvaluationRows = [];
        tbody.innerHTML = '<tr><td colspan="6">The approval ledger could not be loaded.</td></tr>';
        status.innerHTML = '<span class="error">Ledger load failed</span>\\n' + escapeHtml(error.message);
      }
    }

    async function requestManualEvaluation() {
      const exam = activeExam();
      const reasonInput = document.getElementById('manualEvaluationReason');
      const status = document.getElementById('manualEvaluationStatus');
      const reason = String(reasonInput?.value || '').trim();
      if (!exam) {
        status.innerHTML = '<span class="error">Select an exam project first.</span>';
        return;
      }
      if (reason.length < 12) {
        status.innerHTML = '<span class="error">Enter a clear reason of at least 12 characters.</span>';
        return;
      }
      status.textContent = 'Sending the controlled recovery request for master review...';
      try {
        await jsonRequest('/api/evaluation/' + encodeURIComponent(exam.examCode) + '/manual-evaluator/requests', 'POST', {reason});
        reasonInput.value = '';
        await refreshManualEvaluations();
      } catch (error) {
        status.innerHTML = '<span class="error">Request failed</span>\\n' + escapeHtml(error.message);
      }
    }

    function renderManualEvaluationRows() {
      const tbody = document.getElementById('manualEvaluationRows');
      if (!tbody) return;
      if (!manualEvaluationRows.length) {
        tbody.innerHTML = '<tr><td colspan="6">No manual-evaluation requests exist for this exam.</td></tr>';
        return;
      }
      tbody.innerHTML = manualEvaluationRows.map((row) => {
        const approvedAction = row.status === 'APPROVED' && row.usable
          ? '<div class="db-actions"><input id="manualEvaluationKey-' + Number(row.id) + '" type="file" accept="application/json,.json"><button class="secondary" type="button" data-run-manual-evaluation="' + Number(row.id) + '">Run Once</button></div>'
          : '<span class="note">' + (row.status === 'APPROVED' ? 'Approval expired or unavailable.' : 'No executable grant.') + '</span>';
        const audit = row.keySha256
          ? 'File: ' + escapeHtml(row.keyOriginalName || '-') + '<br>SHA-256: <code>' + escapeHtml(row.keySha256) + '</code><br>Responses: ' + escapeHtml(row.evaluatedResponseCount ?? 0)
          : 'Requested: ' + escapeHtml(row.requestedAt || '-') + '<br>Reviewed: ' + escapeHtml(row.reviewedAt || '-');
        return '<tr>'
          + '<td>#' + Number(row.id) + '<br>' + escapeHtml(row.requestedBy || '-') + '</td>'
          + '<td>' + escapeHtml(row.reason || '-') + '</td>'
          + '<td><strong>' + escapeHtml(row.status || '-') + '</strong><br>' + escapeHtml(row.reviewNote || '') + '</td>'
          + '<td>' + escapeHtml(row.expiresAt || '-') + '</td>'
          + '<td>' + audit + '</td>'
          + '<td>' + approvedAction + '</td>'
          + '</tr>';
      }).join('');
      tbody.querySelectorAll('[data-run-manual-evaluation]').forEach((button) => {
        button.addEventListener('click', () => runManualEvaluation(Number(button.dataset.runManualEvaluation)));
      });
    }

    async function runManualEvaluation(requestId) {
      const exam = activeExam();
      const status = document.getElementById('manualEvaluationStatus');
      const fileInput = document.getElementById('manualEvaluationKey-' + requestId);
      if (!exam || !fileInput?.files?.[0]) {
        status.innerHTML = '<span class="error">Choose the answer-key JSON for the approved request.</span>';
        return;
      }
      const formData = new FormData();
      formData.append('file', fileInput.files[0]);
      status.textContent = 'Strictly validating IDs and scoring the saved responses...';
      try {
        const response = await fetch('/api/evaluation/' + encodeURIComponent(exam.examCode) + '/manual-evaluator/requests/' + requestId + '/run', {method:'POST', body:formData});
        const text = await response.text();
        const data = text ? JSON.parse(text) : {};
        if (!response.ok) throw new Error(data.detail || data.message || text || response.statusText);
        evaluationRows = Array.isArray(data.results) ? data.results : [];
        renderEvaluationRows(evaluationRows);
        await refreshManualEvaluations();
        status.innerHTML = '<span class="ok">Manual evaluation completed and grant consumed</span>\\n' + escapeHtml(data.evaluatedResponses || 0) + ' response sheet(s) evaluated.';
      } catch (error) {
        await refreshManualEvaluations();
        status.innerHTML = '<span class="error">Manual evaluation rejected</span>\\n' + escapeHtml(error.message) + '\\nThe grant remains available when strict validation fails.';
      }
    }

    function renderEvaluationRows(rows) {
      const tbody = document.getElementById('evaluationRows');
      if (!tbody) return;
      if (!rows.length) {
        tbody.innerHTML = '<tr><td colspan="9">No evaluated responses yet.</td></tr>';
        return;
      }
      tbody.innerHTML = rows.map((row) =>
        '<tr>'
        + '<td>' + escapeHtml(row.applicationNumber || row.registrationId) + '</td>'
        + '<td>' + escapeHtml(row.candidateName || '-') + '</td>'
        + '<td><strong>' + escapeHtml(row.score) + '</strong></td>'
        + '<td>' + escapeHtml(row.maxMarks) + '</td>'
        + '<td>' + escapeHtml(row.percentage) + '%</td>'
        + '<td>' + escapeHtml(row.correctCount) + '</td>'
        + '<td>' + escapeHtml(row.wrongCount) + '</td>'
        + '<td>' + escapeHtml(row.blankCount) + '</td>'
        + '<td><button class="secondary" type="button" data-view-evaluation="' + escapeHtml(row.responseId) + '">View</button></td>'
        + '</tr>'
      ).join('');
      tbody.querySelectorAll('[data-view-evaluation]').forEach((button) => {
        button.addEventListener('click', () => {
          const selected = evaluationRows.find((row) => String(row.responseId) === String(button.dataset.viewEvaluation));
          renderEvaluationDetail(selected);
        });
      });
    }

    function renderEvaluationDetail(row) {
      const modal = document.getElementById('responseModal');
      const meta = document.getElementById('responseModalMeta');
      const body = document.getElementById('responseModalBody');
      if (!row) return;
      selectedResponseId = null;
      document.getElementById('responseModalTitle').textContent = 'Evaluation - ' + (row.candidateName || row.applicationNumber || 'Candidate');
      meta.textContent = 'Application: ' + (row.applicationNumber || row.registrationId)
        + ' | Score: ' + row.score + ' / ' + row.maxMarks
        + ' | Percentage: ' + row.percentage + '%';
      const questionRows = (row.questions || []).map((question) =>
        '<tr>'
        + '<td>' + escapeHtml(question.displayQuestionId || question.questionId) + '</td>'
        + '<td>' + escapeHtml(question.type || '-') + '</td>'
        + '<td>' + escapeHtml((question.marked || []).join(', ') || question.natAnswer || '-') + '</td>'
        + '<td>' + escapeHtml((question.correct || []).join(', ') || question.correctNatAnswer || '-') + '</td>'
        + '<td>' + escapeHtml(question.resolution || (question.correctAnswer ? 'Correct' : (question.attempted ? 'Wrong' : 'Blank'))) + '</td>'
        + '<td>' + escapeHtml(question.awardedMarks) + '</td>'
        + '</tr>'
      ).join('');
      body.innerHTML = '<div class="table-wrap"><table><thead><tr><th>Q</th><th>Type</th><th>Marked</th><th>Correct</th><th>Status</th><th>Marks</th></tr></thead><tbody>'
        + (questionRows || '<tr><td colspan="6">No question-level evaluation available.</td></tr>')
        + '</tbody></table></div>';
      modal.classList.add('open');
      modal.setAttribute('aria-hidden', 'false');
    }

    async function refreshEvaluationOverrides() {
      const exam = activeExam();
      const tbody = document.getElementById('evaluationOverrideRows');
      if (!tbody) return;
      if (!exam) {
        tbody.innerHTML = '<tr><td colspan="6">Select an exam project first.</td></tr>';
        return;
      }
      evaluationOverrideRows = await jsonRequest('/api/evaluation/' + encodeURIComponent(exam.examCode) + '/overrides', 'GET');
      renderEvaluationOverrideRows();
    }

    function renderEvaluationOverrideRows() {
      const tbody = document.getElementById('evaluationOverrideRows');
      if (!tbody) return;
      if (!evaluationOverrideRows.length) {
        tbody.innerHTML = '<tr><td colspan="6">No overrides yet.</td></tr>';
        return;
      }
      tbody.innerHTML = evaluationOverrideRows.map((row) =>
        '<tr><td>' + escapeHtml(row.questionId || '-') + '</td>'
        + '<td>' + escapeHtml(row.displayQuestionId || '-') + '</td>'
        + '<td>' + escapeHtml(row.type || '-') + '</td>'
        + '<td>' + escapeHtml(row.active ? 'ACTIVE' : 'DISABLED') + '</td>'
        + '<td>' + escapeHtml(row.reason || '-') + '</td>'
        + '<td>' + (row.active ? '<button class="secondary" type="button" data-disable-override="' + escapeHtml(row.id) + '">Disable</button>' : '-') + '</td></tr>'
      ).join('');
      tbody.querySelectorAll('[data-disable-override]').forEach((button) => {
        button.addEventListener('click', async () => {
          await jsonRequest('/api/evaluation/overrides/' + encodeURIComponent(button.dataset.disableOverride) + '/disable', 'PATCH');
          await refreshEvaluationOverrides();
          await refreshEvaluation();
        });
      });
    }

    async function refreshChallengeRequests() {
      const exam = activeExam();
      const tbody = document.getElementById('challengeRequestRows');
      if (!tbody) return;
      if (!exam) {
        challengeRequestRows = [];
        challengeQuestionSummaryRows = [];
        tbody.innerHTML = '<tr><td colspan="7">Select an exam project first.</td></tr>';
        renderChallengeSummaryRows();
        return;
      }
      [challengeRequestRows, challengeQuestionSummaryRows] = await Promise.all([
        jsonRequest('/api/evaluation/' + encodeURIComponent(exam.examCode) + '/challenges', 'GET'),
        jsonRequest('/api/evaluation/' + encodeURIComponent(exam.examCode) + '/challenge-summary', 'GET')
      ]);
      renderChallengeSummaryRows();
      renderChallengeRequestRows();
    }

    function renderChallengeSummaryRows() {
      const tbody = document.getElementById('challengeSummaryRows');
      const stats = document.getElementById('challengeQueueStats');
      if (!tbody || !stats) return;
      const totalChallenges = challengeQuestionSummaryRows.reduce((sum, row) => sum + Number(row.challengeCount || 0), 0);
      const totalCandidates = new Set(challengeRequestRows.map((row) => row.registrationId).filter(Boolean)).size;
      const pending = challengeRequestRows.filter((row) => row.status === 'PENDING').length;
      stats.innerHTML = '<strong>' + escapeHtml(challengeQuestionSummaryRows.length) + '</strong> challenged question(s)'
        + '<strong>' + escapeHtml(totalChallenges) + '</strong> total challenge(s)'
        + '<strong>' + escapeHtml(totalCandidates) + '</strong> candidate(s)'
        + '<strong>' + escapeHtml(pending) + '</strong> pending review';
      if (!challengeQuestionSummaryRows.length) {
        tbody.innerHTML = '<tr><td colspan="8">No challenged questions yet.</td></tr>';
        return;
      }
      tbody.innerHTML = challengeQuestionSummaryRows.map((row) =>
        '<tr><td><span class="challenge-rank">#' + escapeHtml(row.rank) + '</span></td>'
        + '<td>' + escapeHtml(row.displayQuestionId || '-') + '<br><small>' + escapeHtml(row.questionId || '-') + '</small></td>'
        + '<td><strong>' + escapeHtml(row.challengeCount || 0) + '</strong></td>'
        + '<td>' + escapeHtml(row.candidateCount || 0) + '</td>'
        + '<td>' + escapeHtml(row.pendingCount || 0) + '</td>'
        + '<td>' + escapeHtml((row.acceptedCount || 0) + (row.rejectedCount || 0)) + '</td>'
        + '<td>' + escapeHtml(row.latestChallengeAt || '-') + '</td>'
        + '<td><button class="secondary" type="button" data-open-ranked-challenge="' + escapeHtml(row.latestChallengeId) + '">Open</button></td></tr>'
      ).join('');
      tbody.querySelectorAll('[data-open-ranked-challenge]').forEach((button) => button.addEventListener('click', () => openChallengeReviewModal(button.dataset.openRankedChallenge)));
    }

    function renderChallengeRequestRows() {
      const tbody = document.getElementById('challengeRequestRows');
      if (!tbody) return;
      const filter = (document.getElementById('challengeStatusFilter') || {}).value || 'ALL';
      const rows = challengeRequestRows.filter((row) => filter === 'ALL' || String(row.status || '') === filter);
      if (!rows.length) {
        tbody.innerHTML = '<tr><td colspan="7">No challenges match this view.</td></tr>';
        return;
      }
      tbody.innerHTML = rows.map((row) =>
        '<tr><td>' + escapeHtml(row.candidateName || '-') + '<br>' + escapeHtml(row.registrationId || '-') + '</td>'
        + '<td>' + escapeHtml(row.displayQuestionId || row.questionId || '-') + '<br>' + escapeHtml(row.questionId || '-') + '</td>'
        + '<td>' + escapeHtml(row.status || '-') + '</td>'
        + '<td>' + escapeHtml(challengeResolutionLabel(row.resolution)) + '</td>'
        + '<td>' + escapeHtml(row.createdAt || '-') + '</td>'
        + '<td>PDF: ' + escapeHtml(row.proofPdfName || '-') + '<br>Images: ' + escapeHtml(row.proofImageCount || 0) + '</td>'
        + '<td><button class="secondary" type="button" data-open-challenge="' + escapeHtml(row.id) + '">' + (row.status === 'PENDING' ? 'Review' : 'View decision') + '</button></td></tr>'
      ).join('');
      tbody.querySelectorAll('[data-open-challenge]').forEach((button) => button.addEventListener('click', () => openChallengeReviewModal(button.dataset.openChallenge)));
    }

    function challengeResolutionLabel(value) {
      return ({
        KEEP_CURRENT_KEY: 'Current key retained',
        MARK_TO_ALL: 'Marks awarded to all',
        DROP_QUESTION: 'Question revoked'
      })[String(value || '')] || '-';
    }

    function challengeNodeValue(node, names) {
      if (!node || typeof node !== 'object') return '';
      for (const name of names) {
        if (node[name] !== undefined && node[name] !== null && node[name] !== '') return node[name];
      }
      return '';
    }

    function challengeMediaHtml(value, cssClass, fallback) {
      const media = value && typeof value === 'object'
        ? challengeNodeValue(value, [
            'viewerUrl', 'viewer_url', 'question_viewer_url', 'questionViewerUrl',
            'viewerImage', 'viewer_image', 'displayImage', 'display_image',
            'baseImage', 'base_image', 'image', 'selectable_text',
            'selectableText', 'question_selectable_text', 'questionSelectableText',
            'text', 'label', 'value'
          ])
        : value;
      if (typeof media === 'string' && (media.startsWith('data:image/') || media.startsWith('/api/evaluation/challenges/'))) {
        return '<img class="' + cssClass + '" alt="Question content" loading="lazy" src="' + escapeHtml(media) + '" onerror="this.hidden=true;this.nextElementSibling.hidden=false">'
          + '<div class="challenge-media-error" hidden>' + escapeHtml(fallback || 'Image could not be rendered.') + '</div>';
      }
      return '<div class="challenge-review-text">' + escapeHtml(media || fallback || '-') + '</div>';
    }

    function challengeQuestionHtml(question, key) {
      if (!question) return '<div class="challenge-review-status"><span class="error">Served question unavailable</span>\nThe published paper could not be matched to this challenge ID.</div>';
      const type = String(challengeNodeValue(question, ['question_type', 'questionType', 'type']) || (key && key.questionType) || '').toUpperCase();
      const bodyValue = challengeNodeValue(question, [
        'question_viewer_url', 'questionViewerUrl', 'question_viewer_image', 'questionViewerImage', 'viewer_image', 'viewerImage',
        'question_display_image', 'questionDisplayImage', 'display_image', 'displayImage',
        'question_base_image', 'questionBaseImage', 'base_image', 'baseImage',
        'question_image', 'questionImage', 'image', 'body',
        'question_selectable_text', 'questionSelectableText',
        'question_text', 'questionText', 'text'
      ]);
      let optionEntries = [];
      const options = question.options;
      if (Array.isArray(options)) {
        optionEntries = options.map((value, index) => [String.fromCharCode(65 + index), value]);
      } else if (options && typeof options === 'object') {
        optionEntries = Object.entries(options);
      } else {
        optionEntries = ['A', 'B', 'C', 'D', 'E', 'F']
          .map((letter) => [letter, challengeNodeValue(question, ['option_' + letter.toLowerCase(), 'option' + letter, letter])])
          .filter((entry) => entry[1] !== '');
      }
      const correctLetters = new Set((key && key.correctOptions) || []);
      const optionIds = challengeNodeValue(question, ['option_ids', 'optionIds']) || {};
      const correctIds = new Set((key && key.correctOptionIds) || []);
      Object.entries(optionIds).forEach(([letter, id]) => {
        if (correctIds.has(id)) correctLetters.add(String(letter).toUpperCase());
      });
      const optionsHtml = type === 'NAT' || type.includes('NUM')
        ? '<div class="challenge-current-key"><strong>Current numerical key / range</strong><span>' + escapeHtml((key && key.correctAnswer) || 'Not available') + '</span></div>'
        : '<div class="challenge-review-options">' + optionEntries.map(([letter, value]) => {
            const normalized = String(letter).toUpperCase();
            return '<div class="challenge-review-option' + (correctLetters.has(normalized) ? ' keyed' : '') + '"><b>' + escapeHtml(normalized) + '</b><div>'
              + challengeMediaHtml(value, 'challenge-review-option-image', 'Option content unavailable')
              + '</div>' + (correctLetters.has(normalized) ? '<span class="key-badge">Current key</span>' : '') + '</div>';
          }).join('') + '</div>';
      return '<article class="challenge-question-panel">'
        + '<div class="challenge-question-meta"><span>' + escapeHtml(type || '-') + '</span><span>' + escapeHtml((key && key.marks) || question.marks || 0) + ' marks</span><span>' + escapeHtml(challengeNodeValue(question, ['section', 'section_name', 'subject', 'subject_name', 'subjectName']) || 'Section') + '</span></div>'
        + '<div class="challenge-review-question-body">' + challengeMediaHtml(bodyValue, 'challenge-review-question-image', 'Question content unavailable') + '</div>'
        + optionsHtml + '</article>';
    }

    async function openChallengeReviewModal(id) {
      const modal = document.getElementById('challengeReviewModal');
      const body = document.getElementById('challengeReviewBody');
      const meta = document.getElementById('challengeReviewMeta');
      selectedChallengeReviewId = String(id || '');
      body.innerHTML = '<div class="challenge-review-status">Loading challenge proof bundle...</div>';
      meta.textContent = 'Opening challenge review card...';
      modal.classList.add('open');
      modal.setAttribute('aria-hidden', 'false');
      try {
        const data = await jsonRequest('/api/evaluation/challenges/' + encodeURIComponent(id), 'GET');
        const challenge = data.challenge || {};
        const pending = String(challenge.status || '') === 'PENDING';
        document.getElementById('challengeReviewTitle').textContent = 'Challenge Review - ' + (challenge.registrationId || '');
        meta.textContent = 'Question: ' + (challenge.displayQuestionId || challenge.questionId || '-') + ' | Status: ' + (challenge.status || '-');
        const resolution = challenge.resolution || '';
        const proofPdfUrl = data.proofPdfDownloadUrl || ('/api/evaluation/challenges/' + encodeURIComponent(id) + '/proof-pdf');
        const proofImages = Array.isArray(data.proofImageDownloads) ? data.proofImageDownloads : [];
        const proofImageHtml = proofImages.length
          ? '<div class="challenge-proof-gallery">' + proofImages.map((item, index) =>
              '<figure><img class="challenge-proof-image" src="' + escapeHtml(item.downloadUrl || '#') + '" alt="Candidate proof image ' + (index + 1) + '" loading="lazy" onerror="this.hidden=true;this.nextElementSibling.hidden=false">'
              + '<div class="challenge-media-error" hidden>Proof image could not be rendered.</div>'
              + '<figcaption>Image proof ' + (index + 1) + '</figcaption></figure>'
            ).join('') + '</div>'
          : '<div class="challenge-review-status">No optional image proofs were uploaded.</div>';
        body.innerHTML = '<div class="challenge-review-form">'
          + '<div class="challenge-review-grid">'
          + '<div class="challenge-review-chip"><strong>Candidate</strong>' + escapeHtml(challenge.candidateName || '-') + '<br>' + escapeHtml(challenge.registrationId || '-') + '</div>'
          + '<div class="challenge-review-chip"><strong>Question</strong>' + escapeHtml(challenge.displayQuestionId || challenge.questionId || '-') + '<br>' + escapeHtml(challenge.questionId || '-') + '</div>'
          + '<div class="challenge-review-chip"><strong>Proof Bundle</strong>PDF: ' + escapeHtml(challenge.proofPdfName || '-') + '<br>Images: ' + escapeHtml(challenge.proofImageCount || 0) + '</div>'
          + '</div>'
          + '<div class="challenge-adjudication-layout">'
          + '<section><h4>Served Question And Current Key</h4>' + challengeQuestionHtml(data.servedQuestion, data.currentKey) + '</section>'
          + '<section class="challenge-proof-panel"><h4>Candidate Evidence</h4>'
          + '<div class="response-answer-card"><strong>Candidate objection</strong><div style="margin-top:8px">' + escapeHtml(challenge.noteText || '-') + '</div></div>'
          + '<iframe class="challenge-proof-pdf" src="' + escapeHtml(proofPdfUrl) + '" title="Candidate PDF proof"></iframe>'
          + proofImageHtml
          + '<div class="challenge-proof-downloads"><a class="challenge-download-button" href="' + escapeHtml(proofPdfUrl) + '" download>Download PDF Proof</a>'
          + (proofImages.map((item, index) => '<a class="challenge-download-button" href="' + escapeHtml(item.downloadUrl || '#') + '" download>Download Image ' + (index + 1) + '</a>').join(''))
          + '</div>'
          + '</section></div>'
          + '<section class="challenge-decision-panel"><h4>Final Scoring Decision</h4>'
          + (pending ? '<div class="challenge-resolution-grid">'
            + '<label><input type="radio" name="challengeResolution" value="KEEP_CURRENT_KEY"><strong>Keep current key</strong><span>Reject this objection. Scoring remains unchanged.</span></label>'
            + '<label><input type="radio" name="challengeResolution" value="MARK_TO_ALL"><strong>Award marks to all</strong><span>Give full marks for this question to every candidate.</span></label>'
            + '<label><input type="radio" name="challengeResolution" value="DROP_QUESTION"><strong>Revoke question</strong><span>Remove this question and its marks from the paper maximum.</span></label>'
            + '</div>' : '<div class="challenge-final-resolution"><strong>' + escapeHtml(challengeResolutionLabel(resolution)) + '</strong><span>' + escapeHtml(challenge.reviewNote || '-') + '</span></div>')
          + '<div><strong>Decision reason</strong><textarea id="challengeReviewNote" class="challenge-review-note" placeholder="Record the academic reason for this decision."' + (pending ? '' : ' disabled') + '>' + escapeHtml(challenge.reviewNote || '') + '</textarea></div>'
          + (pending ? '<label class="challenge-confirm"><input id="challengeReviewConfirm" type="checkbox"> I confirm this resolution and understand that scoring changes apply to every candidate.</label>' : '')
          + '<div id="challengeReviewStatus" class="challenge-review-status">' + (pending ? 'Select one resolution, add a reason, and confirm the global scoring effect.' : 'Decision finalized by ' + escapeHtml(challenge.reviewedBy || '-') + ' on ' + escapeHtml(challenge.reviewedAt || '-') + '.') + '</div>'
          + '<div class="response-modal-actions">'
          + '<button class="secondary" type="button" id="resolveChallengeReviewBtn"' + (pending ? '' : ' disabled') + '>Apply Final Decision</button>'
          + '</div>'
          + '</section></div>';
        const resolveBtn = document.getElementById('resolveChallengeReviewBtn');
        if (resolveBtn && pending) resolveBtn.addEventListener('click', reviewChallengeDecision);
      } catch (error) {
        body.innerHTML = '<div class="challenge-review-status"><span class="error">Challenge card could not be opened</span>\n' + escapeHtml(error.message) + '</div>';
        meta.textContent = 'Challenge review failed';
      }
    }

    async function reviewChallengeDecision() {
      const status = document.getElementById('challengeReviewStatus');
      const noteInput = document.getElementById('challengeReviewNote');
      const note = noteInput ? noteInput.value.trim() : '';
      const selected = document.querySelector('input[name="challengeResolution"]:checked');
      const confirmed = document.getElementById('challengeReviewConfirm');
      if (!selectedChallengeReviewId) {
        status.innerHTML = '<span class="error">No challenge selected</span>\nOpen a challenge row first.';
        return;
      }
      if (!selected) {
        status.innerHTML = '<span class="error">Resolution required</span>\nChoose keep key, award marks to all, or revoke question.';
        return;
      }
      if (!note) {
        status.innerHTML = '<span class="error">Decision reason required</span>\nRecord the academic reason for the audit ledger.';
        return;
      }
      if (!confirmed || !confirmed.checked) {
        status.innerHTML = '<span class="error">Confirmation required</span>\nConfirm the global scoring effect before applying.';
        return;
      }
      status.textContent = 'Applying final challenge resolution...';
      try {
        await jsonRequest('/api/evaluation/challenges/' + encodeURIComponent(selectedChallengeReviewId) + '/resolve', 'PATCH', {
          resolution: selected.value,
          reviewNote: note
        });
        status.innerHTML = '<span class="ok">Challenge resolution applied</span>\nThe scoring ledger and matching pending challenges were updated.';
        await refreshChallengeRequests();
        await refreshEvaluationOverrides();
        await refreshEvaluation();
        await openChallengeReviewModal(selectedChallengeReviewId);
      } catch (error) {
        status.innerHTML = '<span class="error">Challenge decision failed</span>\n' + escapeHtml(error.message);
      }
    }

    function renderResponseRows(rows) {
      const tbody = document.getElementById('responseRows');
      if (!tbody) return;
      if (!rows.length) {
        tbody.innerHTML = '<tr><td colspan="7">No response sheets saved yet.</td></tr>';
        document.getElementById('responseDetail').textContent = 'No saved responses yet.';
        return;
      }
      tbody.innerHTML = rows.map((row) =>
        '<tr>'
        + '<td>' + escapeHtml(row.id) + '</td>'
        + '<td>' + escapeHtml(row.examCode) + '</td>'
        + '<td>' + escapeHtml(row.candidateName) + '<br>' + escapeHtml(row.applicationNumber || row.registrationId) + '</td>'
        + '<td>' + escapeHtml(row.dateOfBirth) + '</td>'
        + '<td>' + escapeHtml(row.questionCount) + '</td>'
        + '<td>' + escapeHtml(row.submittedAt || '-') + '</td>'
        + '<td><button class="secondary" type="button" data-view-response="' + escapeHtml(row.id) + '">View</button> <button class="danger" type="button" data-request-reexam="' + escapeHtml(row.id) + '">Request Re-exam</button></td>'
        + '</tr>'
      ).join('');
      tbody.querySelectorAll('[data-view-response]').forEach((button) => {
        button.addEventListener('click', () => {
          const selected = responseRows.find((row) => String(row.id) === String(button.dataset.viewResponse));
          renderResponseDetail(selected);
        });
      });
      tbody.querySelectorAll('[data-request-reexam]').forEach((button) => {
        button.addEventListener('click', () => requestReexam(button.dataset.requestReexam));
      });
      document.getElementById('responseDetail').textContent = rows.length + ' response sheet(s) loaded.';
    }

    function renderResponseDetail(row) {
      const modal = document.getElementById('responseModal');
      const meta = document.getElementById('responseModalMeta');
      const body = document.getElementById('responseModalBody');
      if (!row) {
        selectedResponseId = null;
        meta.textContent = 'Select a response row to view the saved sheet.';
        body.innerHTML = '';
        return;
      }
      selectedResponseId = row.id;
      const answers = Array.isArray(row.answers) ? row.answers : [];
      const summary = answers.map((answer) =>
        '<tr><td>' + escapeHtml(answer.questionId || answer.displayQuestionId) + '</td><td>' + escapeHtml(adminMarkedAnswer(answer)) + '</td><td>' + escapeHtml(answer.status || '-') + '</td></tr>'
      ).join('');
      const detail = answers.slice(0, 80).map((answer, index) => {
        const marked = new Set(answer.markedOptionIds || []);
        const options = Object.entries(answer.options || {}).map(([letter, value]) => {
          const rendered = adminOptionHtml(value);
          return '<div style="padding:8px;margin:6px 0;border:1px solid ' + (marked.has(letter) ? '#1ca566' : '#d8e6f4') + ';border-radius:10px;background:' + (marked.has(letter) ? '#effaf4' : '#fff') + '"><strong>' + escapeHtml(letter) + '.</strong> ' + rendered + '</div>';
        }).join('');
        return '<div class="response-answer-card">'
          + '<strong>' + escapeHtml(answer.displayQuestionId || ('Question ' + (index + 1))) + '</strong>'
          + '<div style="color:#607089;font-size:12px;margin:4px 0">QID: ' + escapeHtml(answer.questionId) + ' | Type: ' + escapeHtml(answer.type) + ' | Marked: ' + escapeHtml(adminMarkedAnswer(answer)) + '</div>'
          + (answer.questionImage ? '<img alt="Question image" src="' + escapeHtml(answer.questionImage) + '" style="max-width:min(700px,100%);width:auto;height:auto;max-height:190px;object-fit:contain;border-radius:10px;display:block;margin:8px 0">' : '<div>' + escapeHtml(answer.body || 'No question text saved.') + '</div>')
          + (answer.type === 'NAT' ? '<div style="margin-top:8px;color:#11633a;font-weight:900">NAT answer: ' + escapeHtml(answer.natAnswer || '-') + '</div>' : options)
          + '</div>';
      }).join('');
      document.getElementById('responseModalTitle').textContent = row.candidateName || 'Response Sheet';
      meta.textContent = 'Application: ' + (row.applicationNumber || row.registrationId) + ' | DOB: ' + row.dateOfBirth + ' | Submitted: ' + (row.submittedAt || '-');
      body.innerHTML = '<div class="table-wrap" style="margin-top:10px"><table><thead><tr><th>Question ID</th><th>Marked</th><th>Status</th></tr></thead><tbody>' + (summary || '<tr><td colspan="3">No saved answers.</td></tr>') + '</tbody></table></div>'
        + detail;
      modal.classList.add('open');
      modal.setAttribute('aria-hidden', 'false');
    }

    function closeResponseModal() {
      document.getElementById('responseModal').classList.remove('open');
      document.getElementById('responseModal').setAttribute('aria-hidden', 'true');
    }

    async function deleteResponse(id) {
      await requestReexam(id);
    }

    async function requestReexam(id) {
      const row = responseRows.find((item) => String(item.id) === String(id));
      if (!row) return;
      const reason = prompt('Reason for requesting a fresh re-exam attempt for ' + (row.applicationNumber || row.registrationId) + ':', 'Response sheet reset requested after admin review.');
      if (!reason) return;
      await jsonRequest('/api/exam-access/requests', 'POST', {
        examCode: row.examCode,
        registrationId: row.registrationId,
        type: 'REEXAM',
        reason
      });
      alert('Re-exam request sent to master admin. The response sheet remains intact until approval and final apply.');
      await refreshExamAccessRequests();
    }

    async function refreshExamIncidents() {
      const status = document.getElementById('examIncidentStatus');
      status.textContent = 'Checking active attempts and heartbeat flags...';
      examIncidentRows = await jsonRequest('/api/exam-access/incidents', 'GET');
      const tbody = document.getElementById('examIncidentRows');
      tbody.innerHTML = examIncidentRows.map((row) =>
        '<tr><td>' + escapeHtml(row.examCode) + '</td>'
        + '<td>' + escapeHtml(row.candidateName) + '<br>' + escapeHtml(row.registrationId) + '</td>'
        + '<td>' + escapeHtml(row.interruptedAt || '-') + '</td>'
        + '<td>' + escapeHtml(formatRemaining(row.remainingSeconds)) + '</td>'
        + '<td>' + escapeHtml(row.reason || '-') + '</td>'
        + '<td><button class="secondary" type="button" data-request-rejoin="' + escapeHtml(row.attemptSessionId) + '">Request Rejoin</button></td></tr>'
      ).join('') || '<tr><td colspan="6">No interrupted candidate sessions detected.</td></tr>';
      tbody.querySelectorAll('[data-request-rejoin]').forEach((button) => {
        button.addEventListener('click', () => requestRejoin(button.dataset.requestRejoin));
      });
      status.textContent = examIncidentRows.length + ' interrupted candidate session(s) detected.';
    }

    async function requestRejoin(attemptSessionId) {
      const row = examIncidentRows.find((item) => String(item.attemptSessionId) === String(attemptSessionId));
      if (!row) return;
      const reason = prompt('Reason for requesting rejoin access for ' + row.registrationId + ':', row.reason || 'Candidate connection closed during exam.');
      if (!reason) return;
      await jsonRequest('/api/exam-access/requests', 'POST', {
        examCode: row.examCode,
        registrationId: row.registrationId,
        type: 'REJOIN',
        reason
      });
      alert('Rejoin request sent to master admin. The captured remaining time is ' + formatRemaining(row.remainingSeconds) + '.');
      await refreshExamAccessRequests();
    }

    async function refreshExamAccessRequests() {
      const status = document.getElementById('examAccessRequestStatus');
      status.textContent = 'Loading master-approval ledger...';
      examAccessRequestRows = await jsonRequest('/api/exam-access/requests', 'GET');
      const approvedTab = document.getElementById('approvedExamActionsTabButton');
      if (approvedTab) approvedTab.classList.toggle('hidden', !examAccessRequestRows.some((row) => row.status === 'APPROVED' || row.status === 'APPLIED'));
      const tbody = document.getElementById('examAccessRequestRows');
      tbody.innerHTML = examAccessRequestRows.map((row) => {
        const canApply = row.status === 'APPROVED';
        return '<tr><td>' + escapeHtml(row.examCode) + '</td>'
          + '<td>' + escapeHtml(row.candidateName) + '<br>' + escapeHtml(row.registrationId) + '</td>'
          + '<td>' + escapeHtml(row.type) + '<br>' + escapeHtml(row.reason || '-') + '</td>'
          + '<td>' + escapeHtml(row.status) + '</td>'
          + '<td>' + escapeHtml(formatRemaining(row.remainingSeconds)) + '</td>'
          + '<td>' + escapeHtml(row.requestedBy || '-') + '<br>' + escapeHtml(row.requestedAt || '-') + '</td>'
          + '<td>' + (canApply ? '<label style="display:block;margin-bottom:6px"><input type="checkbox" data-confirm-exam-access="' + escapeHtml(row.id) + '"> Confirm action</label><button class="secondary" type="button" data-apply-exam-access="' + escapeHtml(row.id) + '" disabled>Apply Approved Action</button>' : '-') + '</td></tr>';
      }).join('') || '<tr><td colspan="7">No access requests have been sent.</td></tr>';
      tbody.querySelectorAll('[data-confirm-exam-access]').forEach((checkbox) => {
        checkbox.addEventListener('change', () => {
          const button = tbody.querySelector('[data-apply-exam-access="' + CSS.escape(checkbox.dataset.confirmExamAccess) + '"]');
          if (button) button.disabled = !checkbox.checked;
        });
      });
      tbody.querySelectorAll('[data-apply-exam-access]').forEach((button) => {
        button.addEventListener('click', () => applyExamAccessRequest(button.dataset.applyExamAccess));
      });
      status.textContent = examAccessRequestRows.length + ' exam access request(s) loaded. Approved actions are enabled only after master-admin review.';
    }

    async function applyExamAccessRequest(id) {
      const row = examAccessRequestRows.find((item) => String(item.id) === String(id));
      if (!row || row.status !== 'APPROVED') return;
      const effect = row.type === 'REEXAM'
        ? 'This will delete the old saved response and draft, then allow a fresh attempt.'
        : 'This will allow one resumed launch with the captured remaining time and latest autosaved draft.';
      if (!confirm('Apply approved ' + row.type + ' action for ' + row.registrationId + '?\\n\\n' + effect)) return;
      await jsonRequest('/api/exam-access/requests/' + encodeURIComponent(id) + '/apply', 'PATCH');
      alert('Approved action applied.');
      await refreshExamAccessRequests();
      await refreshResponseCenter();
    }

    function formatRemaining(seconds) {
      if (seconds === null || seconds === undefined) return '-';
      const safe = Math.max(0, Number(seconds) || 0);
      const h = Math.floor(safe / 3600);
      const m = Math.floor((safe % 3600) / 60);
      const s = Math.floor(safe % 60);
      return [h, m, s].map((value) => String(value).padStart(2, '0')).join(':');
    }

    function adminMarkedAnswer(answer) {
      if (answer.type === 'NAT') return answer.natAnswer || '-';
      const optionIds = answer && answer.optionIds && typeof answer.optionIds === 'object' ? answer.optionIds : {};
      const reverse = new Map(Object.entries(optionIds).map(([letter, id]) => [String(id), String(letter)]));
      return (answer.markedOptionIds || [])
        .map((value) => {
          const clean = String(value || '').trim();
          return reverse.get(clean) || (/^[A-Z]$/i.test(clean) ? clean.toUpperCase() : '');
        })
        .filter(Boolean)
        .join(', ') || '-';
    }

    function adminOptionHtml(value) {
      const text = typeof value === 'object' && value !== null
        ? (
          value.viewerImage || value.viewer_image
          || value.optionViewerImage || value.option_viewer_image
          || value.displayImage || value.display_image
          || value.optionDisplayImage || value.option_display_image
          || value.baseImage || value.base_image
          || value.optionBaseImage || value.option_base_image
          || value.image || value.optionImage || value.option_image
          || value.text || value.label || JSON.stringify(value)
        )
        : value;
      return typeof text === 'string' && text.startsWith('data:image/')
        ? '<img alt="Option image" src="' + escapeHtml(text) + '" style="max-width:min(600px,100%);width:auto;height:auto;max-height:52px;object-fit:contain;border-radius:8px;display:block">'
        : escapeHtml(text || '-');
    }

    async function deleteStudent(registrationId) {
      if (!confirm('Delete candidate ' + registrationId + '?')) return;
      await jsonRequest('/api/students/' + encodeURIComponent(registrationId), 'DELETE');
      await refreshDatabaseView();
    }

    async function deleteExam(examCode) {
      if (!confirm('Delete exam ' + examCode + '? Candidate records should be cleared separately if needed.')) return;
      await jsonRequest('/api/exams/' + encodeURIComponent(examCode), 'DELETE');
      await refreshDatabaseView();
    }

    async function deleteAllStudents() {
      if (!confirm('Delete ALL candidate records from the current database?')) return;
      await jsonRequest('/api/students', 'DELETE');
      await refreshDatabaseView();
    }

    async function deleteAllExams() {
      if (!confirm('Delete ALL exams from the current database? This also removes exam stream/config rows.')) return;
      await jsonRequest('/api/exams', 'DELETE');
      await refreshDatabaseView();
    }

    async function sendQueuedEmails() {
      const status = document.getElementById('dbStatus');
      status.textContent = 'Sending queued emails...';
      try {
        const summary = await jsonRequest('/api/notifications/send-queued-email', 'POST');
        status.innerHTML = '<span class="ok">Queued email send attempted</span>\\n'
          + 'Attempted: ' + summary.attempted + '\\n'
          + 'Sent: ' + summary.sent + '\\n'
          + 'Failed: ' + summary.failed + '\\n'
          + 'Skipped: ' + summary.skipped;
        await refreshDatabaseView();
      } catch (error) {
        status.innerHTML = '<span class="error">Queued email send failed</span>\\n' + error.message;
      }
    }

    async function checkMailHealth() {
      const status = document.getElementById('dbStatus');
      status.textContent = 'Checking Gmail SMTP credentials...';
      try {
        const exam = activeExam();
        const url = exam && exam.examCode
          ? '/api/notifications/mail-health?examCode=' + encodeURIComponent(exam.examCode)
          : '/api/notifications/mail-health';
        const health = await jsonRequest(url, 'GET');
        const cssClass = health.authenticated ? 'ok' : 'error';
        status.innerHTML = '<span class="' + cssClass + '">' + escapeHtml(health.message || 'Mail check completed.') + '</span>\n'
          + 'Authenticated sender: ' + escapeHtml(health.sender || 'not configured');
      } catch (error) {
        status.innerHTML = '<span class="error">Mail setup check failed</span>\n' + escapeHtml(error.message || 'Unable to check mail setup.');
      }
    }

      async function refreshDatabaseView() {
        const status = document.getElementById('dbStatus');
        status.textContent = 'Refreshing database view...';
        try {
          const currentExamCode = document.getElementById('configExamCode').value;
          const [examPage, studentPage, notificationPage, responsePage] = await Promise.all([
            jsonRequest(pagedUrl('/api/exams', 'exams'), 'GET'),
            jsonRequest(pagedUrl('/api/students', 'students'), 'GET'),
            jsonRequest(pagedUrl('/api/notifications', 'notifications'), 'GET'),
            jsonRequest(pagedUrl('/api/responses', 'responses'), 'GET')
          ]);
          students = Array.isArray(studentPage.content) ? studentPage.content : [];
          notifications = Array.isArray(notificationPage.content) ? notificationPage.content : [];
          responseRows = Array.isArray(responsePage.content) ? responsePage.content : [];
          setAdminPageMeta('exams', examPage);
          setAdminPageMeta('students', studentPage);
          setAdminPageMeta('notifications', notificationPage);
          setAdminPageMeta('responses', responsePage);
          renderExamSelect(currentExamCode);
          renderExamDbRows(Array.isArray(examPage.content) ? examPage.content : []);
          renderStudentDbRows(students);
          renderNotificationDbRows(notifications);
          renderResponseRows(responseRows);
          renderPager('examDbPager', 'exams', examPage, 'exam(s)');
          renderPager('studentDbPager', 'students', studentPage, 'candidate(s)');
          renderPager('notificationDbPager', 'notifications', notificationPage, 'notification row(s)');
          renderPager('responsePager', 'responses', responsePage, 'response sheet(s)');
          await refreshWorkspaceSnapshot(currentExamCode);
          status.innerHTML = '<span class="ok">Database view refreshed</span>\\n'
            + examPage.totalElements + ' exam(s), ' + studentPage.totalElements + ' student(s), '
            + notificationPage.totalElements + ' notification(s), ' + responsePage.totalElements + ' response sheet(s).';
          updateWorkspaceState();
        } catch (error) {
          status.innerHTML = '<span class="error">Database refresh failed</span>\\n' + error.message;
          updateWorkspaceState();
        }
      }

    function escapeHtml(value) {
      return String(value)
        .replaceAll('&', '&amp;')
        .replaceAll('"', '&quot;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;');
    }

    function addStreamRow(stream = {}) {
      const row = document.createElement('div');
      row.className = 'stream-row';
      row.innerHTML =
        '<input name="streamShortCode" placeholder="EC" maxlength="24" value="' + escapeHtml(stream.shortCode || '') + '">' +
        '<input name="streamFullName" placeholder="Electronics and Communication" maxlength="140" value="' + escapeHtml(stream.fullName || '') + '">' +
        '<label><input name="streamEnabled" type="checkbox"' + (stream.enabled === false ? '' : ' checked') + '> Enabled</label>' +
        '<button class="danger" type="button">Remove</button>';
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

    function renderExamSelect(preferredExamCode = '') {
      const select = document.getElementById('configExamCode');
      const previous = select.value;
      if (!exams.length) {
        select.innerHTML = '';
        updateWorkspaceState();
        return;
      }
      select.innerHTML = exams.map((exam) =>
        '<option value="' + exam.examCode + '">' + exam.examCode + ' - ' + escapeHtml(exam.name) + '</option>'
      ).join('');
      if (preferredExamCode && exams.some((exam) => exam.examCode === preferredExamCode)) {
        select.value = preferredExamCode;
      } else if (previous && exams.some((exam) => exam.examCode === previous)) {
        select.value = previous;
      } else {
        select.value = exams[0].examCode;
      }
      fillConfigFromSelectedExam();
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
      window.ScoreCardSettings.populate(exam);
      window.PortalSupportSettings.populate(exam);
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
      const qbankFile = document.getElementById('qbankFile');
      const qbankAnswerKeyFile = document.getElementById('qbankAnswerKeyFile');
      if (qbankFile) qbankFile.value = '';
      if (qbankAnswerKeyFile) qbankAnswerKeyFile.value = '';
      resetQBankAssemblyUi('No qbank analyzed yet for this exam.');
      loadQBankAssemblyHistory(exam);
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
          ? '<span class="ok">Answer key ready</span>\\n' + escapeHtml(exam.answerKeyOriginalName || 'answer key') + ' | Max marks: ' + escapeHtml(exam.answerKeyTotalMarks || 0)
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
      window.PortalNoticeEditor.populate(exam);
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
      syncFieldWorkflowMode();
      updateExamOpsStatus(exam);
      syncReportDesignerStudioLink();
      syncReportDesignerStudioEmbed();
      updateWorkspaceState();
    }

    document.getElementById('examForm').addEventListener('submit', async (event) => {
      event.preventDefault();
      const form = event.currentTarget;
      const result = document.getElementById('examResult');
      const submitButton = form.querySelector('button[type="submit"]');
      submitButton.disabled = true;
      openProjectModal({
        title: 'Saving Exam Project',
        text: 'Saving exam project...',
        busy: true
      });
      result.textContent = 'Creating exam...';
      try {
        const examStartAt = form.projectExamStartAt.value ? new Date(form.projectExamStartAt.value).toISOString() : null;
        const data = await jsonRequest('/api/exams', 'POST', {
          name: form.examName.value.trim(),
          mode: form.examMode.value,
          durationMinutes: Number(form.durationMinutes.value),
          examStartAt
        });
        result.innerHTML = '<span class="ok">Exam created</span>\\n'
          + 'Exam Code: ' + data.examCode + '\\n'
          + 'Name: ' + data.name + '\\n'
          + 'Mode: ' + data.mode + '\\n'
          + 'Duration: ' + data.durationMinutes + ' minutes\\n'
          + 'Start: ' + (data.examStartAt || 'Not set');
        await loadExams(data.examCode);
        await refreshDatabaseView();
        form.reset();
        setProjectModal({
          title: 'Project Ready',
          text: 'The exam project is saved. Press OK to open the main exam settings pane.',
          busy: false,
          okLabel: 'Open Settings',
          onOk: () => openWorkspaceCard('registrationConfigCard')
        });
      } catch (error) {
        result.innerHTML = '<span class="error">Exam creation failed</span>\\n' + error.message;
        setProjectModal({
          title: 'Could Not Create Project',
          text: error.message || 'The exam project could not be saved.',
          busy: false,
          okLabel: 'Close',
          onOk: null
        });
      } finally {
        submitButton.disabled = false;
      }
    });

    document.getElementById('projectCreateOk').addEventListener('click', () => {
      closeProjectModal();
      if (typeof projectCreateModalAction === 'function') {
        const action = projectCreateModalAction;
        projectCreateModalAction = null;
        action();
      }
    });
    document.getElementById('openProjectSettings').addEventListener('click', () => openWorkspaceCard('registrationConfigCard'));
    document.getElementById('openDatabaseView').addEventListener('click', () => openWorkspaceCard('databaseCard'));
    document.getElementById('openResponseCenter').addEventListener('click', () => openWorkspaceCard('responseCenterCard'));
    document.getElementById('openProjectSettingsDashboard').addEventListener('click', () => openWorkspaceCard('registrationConfigCard'));
    document.getElementById('openDatabaseViewDashboard').addEventListener('click', () => openWorkspaceCard('databaseCard'));
    document.getElementById('openResponseCenterDashboard').addEventListener('click', () => openWorkspaceCard('responseCenterCard'));
    document.getElementById('openReportDesignerDashboard').addEventListener('click', () => openWorkspaceCard('reportDesignerCard'));
    const openReportDesignerStudioDashboardButton=document.getElementById('openReportDesignerStudioDashboard');
    if(openReportDesignerStudioDashboardButton)openReportDesignerStudioDashboardButton.addEventListener('click', openReportDesignerStudio);
    const openReportDesignerStudioCardButton=document.getElementById('openReportDesignerStudioCard');
    if(openReportDesignerStudioCardButton)openReportDesignerStudioCardButton.addEventListener('click', openReportDesignerStudio);
    document.getElementById('openWorkspaceHome').addEventListener('click', openWorkspaceHome);
    document.getElementById('showProjectCreator').addEventListener('click', showProjectCreator);
    document.getElementById('themeToggle').addEventListener('click', () => {
      applyAdminTheme(document.body.classList.contains('dark-theme') ? 'light' : 'dark');
    });
    document.querySelectorAll('[data-admin-tab]').forEach((button) => {
      button.addEventListener('click', () => selectAdminTab(button.dataset.adminTab));
    });
    document.getElementById('refreshDb').addEventListener('click', refreshDatabaseView);
    document.getElementById('refreshResponses').addEventListener('click', refreshResponseCenter);
    document.getElementById('refreshExamIncidents').addEventListener('click', refreshExamIncidents);
    document.getElementById('refreshExamAccessRequests').addEventListener('click', refreshExamAccessRequests);
    document.getElementById('uploadAnswerKey').addEventListener('click', uploadAnswerKey);
    document.getElementById('clearAnswerKey').addEventListener('click', clearAnswerKey);
    document.getElementById('validateActivePaperKey').addEventListener('click', validateActivePaperKey);
    document.getElementById('downloadActiveQuestionPaper').addEventListener('click', () => downloadActiveExamJson('paper'));
    document.getElementById('downloadActiveAnswerKey').addEventListener('click', () => downloadActiveExamJson('key'));
    document.getElementById('analyzeQBank').addEventListener('click', analyzeQBank);
    document.getElementById('uploadQBankAnswerKey').addEventListener('click', uploadQBankDefaultAnswerKey);
    document.getElementById('assembleQBankPaper').addEventListener('click', assembleQBankPaper);
    document.getElementById('downloadQBankPaper').addEventListener('click', () => downloadQBankAssembly('paper'));
    document.getElementById('downloadQBankKey').addEventListener('click', () => downloadQBankAssembly('key'));
    document.getElementById('downloadQBankPaperPdf').addEventListener('click', () => downloadQBankAssembly('paperPdf'));
    document.getElementById('downloadQBankKeyPdf').addEventListener('click', () => downloadQBankAssembly('keyPdf'));
    document.getElementById('publishQBankAssembly').addEventListener('click', publishQBankAssembly);
    document.getElementById('aggregatePublishedQBankKey').addEventListener('click', aggregatePublishedQBankKey);
    document.getElementById('refreshQBankAssemblies').addEventListener('click', () => loadQBankAssemblyHistory());
    document.getElementById('uploadOriginalAnswerKeyPdf').addEventListener('click', uploadOriginalAnswerKeyPdf);
    document.getElementById('refreshEvaluation').addEventListener('click', refreshEvaluation);
    document.getElementById('requestManualEvaluation').addEventListener('click', requestManualEvaluation);
    document.getElementById('refreshManualEvaluations').addEventListener('click', refreshManualEvaluations);
    document.getElementById('refreshEvaluationOverrides').addEventListener('click', refreshEvaluationOverrides);
    document.getElementById('refreshChallengeRequests').addEventListener('click', refreshChallengeRequests);
    document.getElementById('challengeStatusFilter').addEventListener('change', renderChallengeRequestRows);
    document.getElementById('openEvaluationTab').addEventListener('click', () => selectResponseTab('evaluationTab'));
    document.addEventListener('pointermove', updateReportPointer);
    document.addEventListener('pointerup', stopReportPointer);
    const legacyReportCanvas = document.getElementById('reportCanvas');
    if (legacyReportCanvas) {
      legacyReportCanvas.addEventListener('pointerdown', (event) => {
        if (event.target.id === 'reportCanvas') {
          selectedReportElementId = null;
          renderReportCanvas();
        }
      });
      document.getElementById('reportAddText').addEventListener('click', addReportTextBox);
      document.getElementById('reportImageInput').addEventListener('change', (event) => reportFileToImage(event.target.files[0]));
      document.getElementById('reportWatermarkImageInput').addEventListener('change', (event) => reportWatermarkImage(event.target.files[0]));
      document.getElementById('reportApplyWatermark').addEventListener('click', applyReportWatermark);
      document.getElementById('reportClearWatermark').addEventListener('click', clearReportWatermark);
      ['reportWatermarkText', 'reportWatermarkSize', 'reportWatermarkOpacity', 'reportWatermarkAngle', 'reportWatermarkColor'].forEach((id) => {
        document.getElementById(id).addEventListener('input', applyReportWatermark);
      });
      document.getElementById('reportFontFamily').addEventListener('change', (event) => patchSelectedReport({ fontFamily: event.target.value }));
      document.getElementById('reportFontSize').addEventListener('input', (event) => patchSelectedReport({ fontSize: Number(event.target.value) || 16 }));
      document.getElementById('reportTextColor').addEventListener('input', (event) => patchSelectedReport({ color: event.target.value }));
      document.getElementById('reportBoxColor').addEventListener('input', (event) => patchSelectedReport({ borderColor: event.target.value }));
      document.getElementById('reportBorderWidth').addEventListener('input', (event) => patchSelectedReport({ borderWidth: Math.max(0, Number(event.target.value) || 0) }));
      document.getElementById('reportOpacity').addEventListener('input', (event) => patchSelectedReport({ opacity: Math.max(5, Math.min(100, Number(event.target.value) || 100)) }));
      document.getElementById('reportTextContent').addEventListener('input', (event) => {
        const el = selectedReportElement();
        if (!el || el.type !== 'text') return;
        el.text = event.target.value;
        const selectedNode = document.querySelector('.report-item.selected .report-text');
        if (selectedNode) selectedNode.textContent = el.text;
      });
      document.getElementById('reportBold').addEventListener('click', () => {
        const el = selectedReportElement();
        patchSelectedReport({ fontWeight: el && el.fontWeight === '700' ? '400' : '700' });
      });
      document.getElementById('reportItalic').addEventListener('click', () => {
        const el = selectedReportElement();
        patchSelectedReport({ fontStyle: el && el.fontStyle === 'italic' ? 'normal' : 'italic' });
      });
      document.getElementById('reportUnderline').addEventListener('click', () => {
        const el = selectedReportElement();
        patchSelectedReport({ textDecoration: el && el.textDecoration === 'underline' ? 'none' : 'underline' });
      });
      document.getElementById('reportAlignLeft').addEventListener('click', () => patchSelectedReport({ textAlign: 'left' }));
      document.getElementById('reportAlignCenter').addEventListener('click', () => patchSelectedReport({ textAlign: 'center' }));
      document.getElementById('reportAlignRight').addEventListener('click', () => patchSelectedReport({ textAlign: 'right' }));
      document.getElementById('reportBringFront').addEventListener('click', () => patchSelectedReport({ z: Math.max(2, ...reportElements.map((el) => el.z || 2)) + 1 }));
      document.getElementById('reportSendBack').addEventListener('click', () => patchSelectedReport({ z: 2 }));
      document.getElementById('reportDelete').addEventListener('click', () => {
        deleteSelectedReportElement();
      });
      document.getElementById('reportClear').addEventListener('click', () => {
        if (!confirm('Clear the current report card canvas?')) return;
        reportElements = [];
        selectedReportElementId = null;
        renderReportCanvas();
      });
      document.getElementById('reportSave').addEventListener('click', saveReportTemplate);
      document.getElementById('reportExport').addEventListener('click', exportReportTemplate);
      document.getElementById('previewReportTemplateBtn').addEventListener('click', previewSelectedReportTemplate);
      document.getElementById('activateReportTemplateBtn').addEventListener('click', activateSelectedReportTemplate);
      document.getElementById('templatePreviewReleaseBtn').addEventListener('click', activateSelectedReportTemplate);
      document.getElementById('closeTemplatePreview').addEventListener('click', closeTemplatePreviewModal);
    }
    document.addEventListener('paste', (event) => {
      if (!legacyReportCanvas || !document.getElementById('reportDesignerCard').classList.contains('active')) return;
      const item = [...event.clipboardData.items].find((entry) => entry.type.startsWith('image/'));
      if (!item) return;
      reportFileToImage(item.getAsFile());
      event.preventDefault();
    });
    document.addEventListener('keydown', (event) => {
      if (!legacyReportCanvas || !document.getElementById('reportDesignerCard').classList.contains('active')) return;
      const tag = event.target.tagName;
      const editingText = event.target.classList && event.target.classList.contains('report-text');
      if ((tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || editingText) && event.key !== 'Escape') return;
      if (event.key === 'Delete' || event.key === 'Backspace') {
        deleteSelectedReportElement();
        event.preventDefault();
      } else if (event.key === 'ArrowLeft') {
        nudgeSelectedReportElement(event.shiftKey ? -10 : -1, 0);
        event.preventDefault();
      } else if (event.key === 'ArrowRight') {
        nudgeSelectedReportElement(event.shiftKey ? 10 : 1, 0);
        event.preventDefault();
      } else if (event.key === 'ArrowUp') {
        nudgeSelectedReportElement(0, event.shiftKey ? -10 : -1);
        event.preventDefault();
      } else if (event.key === 'ArrowDown') {
        nudgeSelectedReportElement(0, event.shiftKey ? 10 : 1);
        event.preventDefault();
      } else if (event.key === 'Escape') {
        selectedReportElementId = null;
        renderReportCanvas();
      }
    });
    document.getElementById('closeResponseModal').addEventListener('click', closeResponseModal);
    document.getElementById('responseModal').addEventListener('click', (event) => {
      if (event.target.id === 'responseModal') closeResponseModal();
    });
    document.getElementById('closeChallengeReviewModal').addEventListener('click', () => {
      document.getElementById('challengeReviewModal').classList.remove('open');
      document.getElementById('challengeReviewModal').setAttribute('aria-hidden', 'true');
    });
    document.getElementById('challengeReviewModal').addEventListener('click', (event) => {
      if (event.target.id === 'challengeReviewModal') {
        document.getElementById('challengeReviewModal').classList.remove('open');
        document.getElementById('challengeReviewModal').setAttribute('aria-hidden', 'true');
      }
    });
    document.getElementById('templatePreviewModal').addEventListener('click', (event) => {
      if (event.target.id === 'templatePreviewModal') closeTemplatePreviewModal();
    });
    document.getElementById('deleteResponseModal').addEventListener('click', () => {
      if (selectedResponseId) deleteResponse(selectedResponseId);
    });
    document.getElementById('sendQueuedEmails').addEventListener('click', sendQueuedEmails);
    document.getElementById('checkMailHealth').addEventListener('click', checkMailHealth);
    document.getElementById('clearNotificationOutbox').addEventListener('click', clearNotificationOutbox);
    document.getElementById('resetStudents').addEventListener('click', deleteAllStudents);
    document.getElementById('resetExams').addEventListener('click', deleteAllExams);

    applyAdminTheme((() => {
      try {
        return localStorage.getItem('examAdminTheme') || 'light';
      } catch (error) {
        return 'light';
      }
    })());
    if (typeof initAdminRegistrationModule === 'function') {
      initAdminRegistrationModule();
    }
    document.querySelectorAll('[data-response-tab]').forEach((button) => {
      button.addEventListener('click', () => selectResponseTab(button.dataset.responseTab));
    });
    syncReportDesignerStudioLink();
    loadExams();
    refreshDatabaseView();
    refreshExamAccessRequests();

    function firstNotification(value) {
      return String(value || '').split(/\r?\n/).map((line) => line.trim()).find(Boolean) || '';
    }

    function selectAdminTab(tabId) {
    document.querySelectorAll('[data-admin-tab]').forEach((button) => {
        button.classList.toggle('active', button.dataset.adminTab === tabId);
    });
      document.querySelectorAll('.admin-tab-panel').forEach((panel) => {
        panel.classList.toggle('active', panel.id === tabId);
      });
    }

    function selectResponseTab(tabId) {
      document.querySelectorAll('[data-response-tab]').forEach((button) => {
        button.classList.toggle('active', button.dataset.responseTab === tabId);
        button.setAttribute('aria-selected', String(button.dataset.responseTab === tabId));
        button.tabIndex = button.dataset.responseTab === tabId ? 0 : -1;
      });
      document.querySelectorAll('.response-tab-panel').forEach((panel) => {
        panel.classList.toggle('active', panel.id === tabId);
      });
      if (tabId === 'evaluationTab') {
        const status = document.getElementById('evaluationStatus');
        const exam = activeExam();
        status.innerHTML = exam
          ? 'Selected exam: <strong>' + escapeHtml(exam.examCode + ' - ' + exam.name) + '</strong>\\nChoose answer key JSON here, then click Upload Answer Key.'
          : '<span class="error">Select/create an exam project first.</span>';
      } else if (tabId === 'manualEvaluationTab') {
        refreshManualEvaluations();
      } else if (tabId === 'postChallengesTab') {
        refreshChallengeRequests();
        refreshEvaluationOverrides();
      } else if (tabId === 'postPublicationTab') {
        window.refreshPostPublicationState();
      } else if (tabId === 'examIncidentsTab') {
        refreshExamIncidents();
      } else if (tabId === 'approvedExamActionsTab') {
        refreshExamAccessRequests();
      }
    }

    function updateExamOpsStatus(examOverride) {
      const examCode = document.getElementById('configExamCode').value;
      const exam = examOverride || exams.find((item) => item.examCode === examCode) || {};
      const paperPicked = Boolean(document.getElementById('questionPaperFile').files[0]);
      const paperReady = Boolean(exam.questionPaperUploaded || paperPicked);
      const lobbyReady = document.getElementById('examLobbyEnabled').checked;
      const launchReady = document.getElementById('examLaunchEnabled').checked;
      const submitReady = document.getElementById('candidateSubmitEnabled').checked;
      const responsesReleased = document.getElementById('responsesReleased').checked;
      const resultReleased = document.getElementById('resultReleased').checked;
      const scoreCardPdfReleased = document.getElementById('scoreCardPdfReleased').checked;
      const originalQuestionPaperReleased = document.getElementById('originalQuestionPaperReleased').checked;
      const originalAnswerKeyReleased = document.getElementById('originalAnswerKeyReleased').checked;
      const challengeEnabled = document.getElementById('answerKeyChallengeEnabled').checked;
      const challengeOpens = document.getElementById('answerKeyChallengeOpensAt').value;
      const challengeCloses = document.getElementById('answerKeyChallengeClosesAt').value;
      const challengeTimesValid = Boolean(challengeOpens && challengeCloses && new Date(challengeCloses) > new Date(challengeOpens));
      const challengeReady = challengeEnabled && challengeTimesValid && originalAnswerKeyReleased;
      document.getElementById('examOpsStatus').innerHTML = [
        statusChip('Paper ' + (paperReady ? 'ready' : 'missing'), paperReady),
        statusChip('Lobby ' + (lobbyReady ? 'enabled' : 'off'), lobbyReady),
        statusChip('Launch ' + (launchReady ? 'enabled' : 'blocked'), launchReady),
        statusChip('Submit ' + (submitReady ? 'manual' : 'locked'), submitReady, !submitReady),
        statusChip('Responses ' + (responsesReleased ? 'released' : 'hidden'), responsesReleased, !responsesReleased),
        statusChip('Result ' + (resultReleased ? 'released' : 'hidden'), resultReleased, !resultReleased),
        statusChip('Score PDF ' + (scoreCardPdfReleased ? 'released' : 'hidden'), scoreCardPdfReleased, !scoreCardPdfReleased),
        statusChip('QP PDF ' + (originalQuestionPaperReleased ? 'released' : 'hidden'), originalQuestionPaperReleased, !originalQuestionPaperReleased),
        statusChip('AK PDF ' + (originalAnswerKeyReleased ? 'released' : 'hidden'), originalAnswerKeyReleased, !originalAnswerKeyReleased),
        statusChip('Challenge ' + (!challengeEnabled ? 'off' : challengeReady ? 'scheduled' : !challengeTimesValid ? 'times missing' : 'waiting for AK release'), challengeReady, challengeEnabled && !challengeReady)
      ].join('');
    }

    function statusChip(text, ok, warn = false) {
      return '<div class="status-chip ' + (ok ? 'on' : (warn ? 'warn' : '')) + '">' + escapeHtml(text) + '</div>';
    }

    function clearPortalNotifications() {
      window.PortalNoticeEditor.clear();
      document.getElementById('portalNotifications').value = '';
      document.getElementById('portalNotification').value = '';
      document.getElementById('configResult').innerHTML = 'Notifications cleared. Click <b>Save Registration Config</b> to publish the change.';
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
        box.innerHTML = '<span class="error">Could not load question order metadata</span>\\n' + escapeHtml(error.message);
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
            + '<div class="order-stats">' + escapeHtml(questionLabel + ' Â· ' + marksLabel) + '</div>'
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
      if (!cleaned.length) {
        return options;
      }
      if (cleaned.length === 1) {
        options.push({
          value: cleaned[0],
          label: cleaned[0] + ' marks'
        });
        return options;
      }
      const forward = cleaned.join(',');
      const reverse = [...cleaned].reverse().join(',');
      options.push({
        value: forward,
        label: cleaned.join(' then ') + ' marks'
      });
      if (reverse !== forward) {
        options.push({
          value: reverse,
          label: [...cleaned].reverse().join(' then ') + ' marks'
        });
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
        await refreshDatabaseView();
      } catch (error) {
        status.innerHTML = '<span class="error">Question paper upload failed</span>\\n' + escapeHtml(error.message);
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
        await loadExams(data.examCode);
        updateExamOpsStatus(data);
        resetQBankAssemblyUi('Server question paper cleared. Choose or assemble a fresh paper.');
        await refreshDatabaseView();
      } catch (error) {
        status.innerHTML = '<span class="error">Could not clear question paper</span>\\n' + escapeHtml(error.message);
      }
    }

    function resetQBankAssemblyUi(message = 'No qbank analyzed yet.') {
      qbankAssemblyState = { qbankId: '', assemblyId: '', inventory: null, history: qbankAssemblyState.history || [] };
      const status = document.getElementById('qbankStatus');
      const inventory = document.getElementById('qbankInventory');
      const assemblyStatus = document.getElementById('qbankAssemblyStatus');
      if (status) status.textContent = message;
      if (inventory) inventory.textContent = 'Upload a qbank JSON to see subject, topic, marks, and difficulty availability.';
      ['assembleQBankPaper', 'downloadQBankPaper', 'downloadQBankKey', 'downloadQBankPaperPdf', 'downloadQBankKeyPdf', 'publishQBankAssembly'].forEach((id) => {
        const button = document.getElementById(id);
        if (button) button.disabled = true;
      });
      if (assemblyStatus) assemblyStatus.textContent = 'No published assembly.';
    }

    function qbankAssemblyDownloadUrl(kind, assemblyId) {
      const exam = activeExam();
      if (!exam || !assemblyId) return '';
      return '/api/exams/' + encodeURIComponent(exam.examCode)
        + '/qbank/assemblies/' + encodeURIComponent(assemblyId)
        + (kind === 'key' ? '/answer-key'
          : kind === 'keyPdf' ? '/answer-key.pdf'
            : kind === 'paperPdf' ? '/question-paper.pdf'
              : '/question-paper');
    }

    function setQBankAssemblyDownloads(assemblyId, answerKeyAvailable = true) {
      qbankAssemblyState.assemblyId = assemblyId || '';
      const paperButton = document.getElementById('downloadQBankPaper');
      const keyButton = document.getElementById('downloadQBankKey');
      const publishButton = document.getElementById('publishQBankAssembly');
      if (paperButton) {
        paperButton.disabled = !assemblyId;
        paperButton.dataset.downloadUrl = qbankAssemblyDownloadUrl('paper', assemblyId);
      }
      if (keyButton) {
        keyButton.disabled = !assemblyId || !answerKeyAvailable;
        keyButton.dataset.downloadUrl = answerKeyAvailable ? qbankAssemblyDownloadUrl('key', assemblyId) : '';
      }
      const paperPdfButton = document.getElementById('downloadQBankPaperPdf');
      const keyPdfButton = document.getElementById('downloadQBankKeyPdf');
      if (paperPdfButton) {
        paperPdfButton.disabled = !assemblyId;
        paperPdfButton.dataset.downloadUrl = qbankAssemblyDownloadUrl('paperPdf', assemblyId);
      }
      if (keyPdfButton) {
        keyPdfButton.disabled = !assemblyId || !answerKeyAvailable;
        keyPdfButton.dataset.downloadUrl = answerKeyAvailable ? qbankAssemblyDownloadUrl('keyPdf', assemblyId) : '';
      }
      if (publishButton) publishButton.disabled = !assemblyId;
    }

    function qbankSelectedQuestionLine(question) {
      return [
        question.questionNumber || '',
        question.questionId || '',
        question.topic || '',
        question.difficulty || '',
        question.marks ? question.marks + 'M' : ''
      ].filter(Boolean).join(' | ');
    }

    function qbankSelectedQuestionList(selected, limit = 12) {
      if (!Array.isArray(selected) || !selected.length) {
        return '<div class="qbank-selected-list"><span>No selected-question summary was saved for this assembly.</span></div>';
      }
      const shown = selected.slice(0, limit);
      return '<div class="qbank-selected-list">'
        + shown.map((question) => '<span>' + escapeHtml(qbankSelectedQuestionLine(question)) + '</span>').join('')
        + (selected.length > limit ? '<span>+' + (selected.length - limit) + ' more selected questions...</span>' : '')
        + '</div>';
    }

    async function loadQBankAssemblyHistory(examArg) {
      const exam = examArg || activeExam();
      const box = document.getElementById('qbankAssemblyHistory');
      if (!box) return;
      if (!exam) {
        qbankAssemblyState.history = [];
        box.textContent = 'Select an exam to view published papers.';
        return;
      }
      box.textContent = 'Loading published paper records...';
      try {
        const rows = await jsonRequest('/api/exams/' + encodeURIComponent(exam.examCode) + '/qbank/assemblies', 'GET');
        qbankAssemblyState.history = Array.isArray(rows) ? rows : [];
        renderQBankAssemblyHistory();
      } catch (error) {
        qbankAssemblyState.history = [];
        box.innerHTML = '<span class="error">Could not load qbank assembly ledger</span><br>' + escapeHtml(error.message);
      }
    }

    function renderQBankAssemblyHistory() {
      const box = document.getElementById('qbankAssemblyHistory');
      if (!box) return;
      const rows = Array.isArray(qbankAssemblyState.history) ? qbankAssemblyState.history : [];
      if (!rows.length) {
        box.innerHTML = 'No published papers are available for this exam yet.'
          + '<div class="qbank-flow-list">'
          + 'Workflow: analyze the full qbank, store its original key once in the server vault, assemble and publish a paper-only candidate snapshot, then reconstruct the evaluation key after the exam from protected ID lineage.'
          + '</div>';
        return;
      }
      box.innerHTML = rows.map((row, index) => {
        const assemblyId = row.assemblyId || '';
        const isSelected = assemblyId && assemblyId === qbankAssemblyState.assemblyId;
        const published = Boolean(row.publishedAt);
        return '<div class="qbank-history-card' + (isSelected ? ' selected' : '') + '">'
          + '<div class="qbank-history-title"><span>' + escapeHtml(published ? 'Published Server Paper' : 'Draft Server Paper') + '</span>'
          + '<span>' + escapeHtml(row.questionCount || 0) + ' Q | ' + escapeHtml(row.totalMarks || 0) + 'M</span></div>'
          + '<div class="qbank-history-meta">'
          + 'Assembly: ' + escapeHtml(assemblyId || '-') + '<br>'
          + 'Created: ' + escapeHtml(row.createdAt || '-') + ' | Published: ' + escapeHtml(row.publishedAt || '-') + '<br>'
          + 'Reconstructed answers: ' + escapeHtml(row.matchedAnswerCount || 0) + ' | Evaluation key: ' + escapeHtml(row.answerKeyAvailable ? 'ready' : 'not reconstructed')
          + '</div>'
          + qbankSelectedQuestionList(row.selectedQuestions, 999)
          + '<div class="qbank-history-actions">'
          + '<button class="secondary" type="button" data-qbank-history-action="use" data-assembly-id="' + escapeHtml(assemblyId) + '" data-key-ready="' + (row.answerKeyAvailable ? '1' : '0') + '">Use In Controls</button>'
          + '<button class="secondary" type="button" data-qbank-history-action="paper" data-assembly-id="' + escapeHtml(assemblyId) + '">Download Paper JSON</button>'
          + '<button class="secondary" type="button" data-qbank-history-action="key" data-assembly-id="' + escapeHtml(assemblyId) + '"' + (row.answerKeyAvailable ? '' : ' disabled') + '>Download Key JSON</button>'
          + '<button class="secondary" type="button" data-qbank-history-action="paperPdf" data-assembly-id="' + escapeHtml(assemblyId) + '">Branded Paper PDF</button>'
          + '<button class="secondary" type="button" data-qbank-history-action="keyPdf" data-assembly-id="' + escapeHtml(assemblyId) + '"' + (row.answerKeyAvailable ? '' : ' disabled') + '>Branded Key PDF</button>'
          + '<button type="button" data-qbank-history-action="publish" data-assembly-id="' + escapeHtml(assemblyId) + '">Publish This Paper</button>'
          + '</div>'
          + '</div>';
      }).join('');
      box.querySelectorAll('[data-qbank-history-action]').forEach((button) => {
        button.addEventListener('click', () => {
          const assemblyId = button.dataset.assemblyId || '';
          const action = button.dataset.qbankHistoryAction;
          if (action === 'use') {
            setQBankAssemblyDownloads(assemblyId, button.dataset.keyReady === '1');
            renderQBankAssemblyHistory();
            const status = document.getElementById('qbankAssemblyStatus');
            if (status) status.textContent = 'Selected assembly loaded. Publish the paper now; reconstruct its evaluation key only after the exam.';
          } else if (action === 'paper') {
            window.location.href = qbankAssemblyDownloadUrl('paper', assemblyId);
          } else if (action === 'key') {
            window.location.href = qbankAssemblyDownloadUrl('key', assemblyId);
          } else if (action === 'paperPdf') {
            window.location.href = qbankAssemblyDownloadUrl('paperPdf', assemblyId);
          } else if (action === 'keyPdf') {
            window.location.href = qbankAssemblyDownloadUrl('keyPdf', assemblyId);
          } else if (action === 'publish') {
            publishQBankAssembly(assemblyId);
          }
        });
      });
    }

    async function analyzeQBank() {
      const exam = activeExam();
      const fileInput = document.getElementById('qbankFile');
      const status = document.getElementById('qbankStatus');
      if (!exam) {
        status.textContent = 'Select an exam project first.';
        return;
      }
      if (!fileInput.files[0]) {
        status.textContent = 'Choose the full qbank JSON first.';
        return;
      }
      resetQBankAssemblyUi('Analyzing qbank inventory...');
      try {
        const formData = new FormData();
        formData.append('file', fileInput.files[0]);
        const response = await fetch('/api/exams/' + encodeURIComponent(exam.examCode) + '/qbank/analyze', {
          method: 'POST',
          body: formData
        });
        const text = await response.text();
        const data = text ? JSON.parse(text) : {};
        if (!response.ok) throw new Error(data.detail || data.message || text || response.statusText);
        qbankAssemblyState = { qbankId: data.qbankId || '', assemblyId: '', inventory: data, history: qbankAssemblyState.history || [] };
        status.innerHTML = '<span class="ok">Qbank analyzed</span>: '
          + escapeHtml(data.originalName || fileInput.files[0].name)
          + ' | Questions: ' + escapeHtml(data.questionCount || 0)
          + ' | Marks available: ' + escapeHtml(data.totalMarks || 0);
        renderQBankInventory(data);
      } catch (error) {
        resetQBankAssemblyUi('Qbank analysis failed.');
        status.innerHTML = '<span class="error">Qbank analysis failed</span>\\n' + escapeHtml(error.message);
      }
    }

    async function uploadQBankDefaultAnswerKey() {
      const exam = activeExam();
      const fileInput = document.getElementById('qbankAnswerKeyFile');
      const status = document.getElementById('qbankStatus');
      if (!exam) {
        status.textContent = 'Select an exam project first.';
        return;
      }
      if (!fileInput.files[0]) {
        status.textContent = 'Choose the full-bank answer key JSON first.';
        return;
      }
      status.textContent = 'Storing the original full-bank answer key in the protected server vault...';
      try {
        const formData = new FormData();
        formData.append('file', fileInput.files[0]);
        const response = await fetch('/api/exams/' + encodeURIComponent(exam.examCode) + '/qbank/default-answer-key', {
          method: 'POST',
          body: formData
        });
        const text = await response.text();
        const data = text ? JSON.parse(text) : {};
        if (!response.ok) throw new Error(data.detail || data.message || text || response.statusText);
        status.innerHTML = '<span class="ok">Full-bank key secured in server vault</span>: '
          + escapeHtml(data.originalName || fileInput.files[0].name)
          + ' | Answers: ' + escapeHtml(data.answerCount || 0)
          + ' | Marks: ' + escapeHtml(data.totalMarks || 0);
        fileInput.value = '';
      } catch (error) {
        status.innerHTML = '<span class="error">Full-bank key upload failed</span>\\n' + escapeHtml(error.message);
      }
    }

    function renderQBankInventory(data) {
      const box = document.getElementById('qbankInventory');
      const assembleButton = document.getElementById('assembleQBankPaper');
      const topics = Array.isArray(data && data.topics) ? data.topics : [];
      const difficulties = normalizedQBankDifficulties(data);
      if (!topics.length) {
        box.textContent = 'No topic inventory was found in this qbank.';
        assembleButton.disabled = true;
        return;
      }
      box.innerHTML = '<div class="qbank-inventory-note">Enter how many questions to pick from each topic and difficulty. Leave cells as 0 to skip. The server checks availability again before assembly.</div>'
        + '<div class="qbank-table-wrap"><table class="qbank-table"><thead><tr>'
        + '<th>Subject / Section</th><th>Topic</th><th>Available</th>'
        + difficulties.map((difficulty) => '<th>' + escapeHtml(difficulty) + '</th>').join('')
        + '</tr></thead><tbody>'
        + topics.map((topic, index) => {
          const diffMap = new Map((topic.difficulties || []).map((item) => [String(item.difficulty || ''), item]));
          const marksLine = (topic.difficulties || []).map((item) => {
            const marks = item.marks || {};
            const detail = Object.keys(marks).map((mark) => mark + 'M:' + marks[mark]).join(', ');
            return item.difficulty + (detail ? ' [' + detail + ']' : '');
          }).join(' | ');
          return '<tr data-qbank-topic-row="' + index + '">'
            + '<td><strong>' + escapeHtml(topic.subject || 'Subject') + '</strong><br><span>' + escapeHtml(topic.section || '-') + '</span></td>'
            + '<td><strong>' + escapeHtml(topic.topic || 'Unassigned Topic') + '</strong><br><span>' + escapeHtml(topic.topicCode || '') + '</span></td>'
            + '<td>' + escapeHtml(topic.questionCount || 0) + ' Q<br><span>' + escapeHtml(topic.totalMarks || 0) + ' marks</span><br><small>' + escapeHtml(marksLine || 'No mark split') + '</small></td>'
            + difficulties.map((difficulty) => {
              const available = Number((diffMap.get(difficulty) || {}).questionCount || 0);
              return '<td><input class="qbank-count-input" data-qbank-topic="' + index + '" data-qbank-difficulty="' + escapeHtml(difficulty) + '" type="number" min="0" max="' + available + '" value="0">'
                + '<small>of ' + available + '</small></td>';
            }).join('')
            + '</tr>';
        }).join('')
        + '</tbody></table></div>';
      assembleButton.disabled = false;
      ['downloadQBankPaper', 'downloadQBankKey', 'publishQBankAssembly'].forEach((id) => {
        const button = document.getElementById(id);
        if (button) button.disabled = true;
      });
      document.getElementById('qbankAssemblyStatus').textContent = 'Set counts, then assemble. Nothing is live until publish.';
    }

    function normalizedQBankDifficulties(data) {
      const detected = Array.isArray(data && data.difficulties) ? data.difficulties.filter(Boolean) : [];
      const preferred = ['Easy', 'Medium', 'Tough'];
      const out = [];
      preferred.forEach((difficulty) => {
        if (detected.some((item) => String(item).toLowerCase() === difficulty.toLowerCase())) out.push(difficulty);
      });
      detected.forEach((difficulty) => {
        if (!out.some((item) => item.toLowerCase() === String(difficulty).toLowerCase())) out.push(String(difficulty));
      });
      return out.length ? out : preferred;
    }

    function collectQBankBlueprint() {
      const inventory = qbankAssemblyState.inventory;
      const topics = Array.isArray(inventory && inventory.topics) ? inventory.topics : [];
      const rows = [];
      topics.forEach((topic, index) => {
        const difficultyCounts = {};
        document.querySelectorAll('[data-qbank-topic="' + index + '"]').forEach((input) => {
          const count = Math.max(0, Number(input.value || 0));
          if (count > 0) {
            difficultyCounts[input.dataset.qbankDifficulty] = count;
          }
        });
        if (Object.keys(difficultyCounts).length) {
          rows.push({
            subject: topic.subject || '',
            section: topic.section || '',
            topic: topic.topic || '',
            topicCode: topic.topicCode || '',
            difficultyCounts
          });
        }
      });
      return rows;
    }

    async function assembleQBankPaper() {
      const exam = activeExam();
      const status = document.getElementById('qbankAssemblyStatus');
      if (!exam) {
        status.textContent = 'Select an exam project first.';
        return;
      }
      if (!qbankAssemblyState.qbankId) {
        status.textContent = 'Analyze a qbank first.';
        return;
      }
      const blueprint = collectQBankBlueprint();
      if (!blueprint.length) {
        status.textContent = 'Enter at least one topic/difficulty count before assembly.';
        return;
      }
      status.textContent = 'Assembling the paper-only candidate snapshot and private ID lineage...';
      try {
        const data = await jsonRequest('/api/exams/' + encodeURIComponent(exam.examCode) + '/qbank/assemble', 'POST', {
          qbankId: qbankAssemblyState.qbankId,
          seed: '',
          topics: blueprint,
          randomizeOptions: Boolean(document.getElementById('optionRandomizationEnabled')?.checked)
        });
        qbankAssemblyState.assemblyId = data.assemblyId || '';
        setQBankAssemblyDownloads(qbankAssemblyState.assemblyId, Boolean(data.answerKeyAvailable || data.answerKeyDownloadUrl));
        const paperButton = document.getElementById('downloadQBankPaper');
        const keyButton = document.getElementById('downloadQBankKey');
        if (paperButton) paperButton.dataset.downloadUrl = data.questionPaperDownloadUrl || paperButton.dataset.downloadUrl || '';
        if (keyButton) keyButton.dataset.downloadUrl = data.answerKeyDownloadUrl || keyButton.dataset.downloadUrl || '';
        const paperPdfButton = document.getElementById('downloadQBankPaperPdf');
        const keyPdfButton = document.getElementById('downloadQBankKeyPdf');
        if (paperPdfButton) paperPdfButton.dataset.downloadUrl = qbankAssemblyDownloadUrl('paperPdf', qbankAssemblyState.assemblyId);
        if (keyPdfButton) keyPdfButton.dataset.downloadUrl = qbankAssemblyDownloadUrl('keyPdf', qbankAssemblyState.assemblyId);
        const selected = Array.isArray(data.selectedQuestions) ? data.selectedQuestions : [];
        status.innerHTML = '<span class="ok">Selected paper assembled</span>: '
          + escapeHtml(data.questionCount || 0) + ' questions | '
          + escapeHtml(data.totalMarks || 0) + ' marks'
          + '<br><span class="ok">Candidate paper contains no answer key. Private source/display ID lineage is held by the server.</span>'
          + qbankSelectedQuestionList(selected, 18);
        await loadQBankAssemblyHistory(exam);
      } catch (error) {
        status.innerHTML = '<span class="error">Assembly failed</span>\\n' + escapeHtml(error.message);
      }
    }

    function downloadQBankAssembly(kind) {
      const button = document.getElementById(
        kind === 'key' ? 'downloadQBankKey'
          : kind === 'paperPdf' ? 'downloadQBankPaperPdf'
            : kind === 'keyPdf' ? 'downloadQBankKeyPdf'
              : 'downloadQBankPaper'
      );
      const url = button ? button.dataset.downloadUrl : '';
      if (!url) return;
      window.location.href = url;
    }

    async function publishQBankAssembly(assemblyIdOverride = '') {
      const exam = activeExam();
      const status = document.getElementById('qbankAssemblyStatus');
      const assemblyId = typeof assemblyIdOverride === 'string' && assemblyIdOverride
        ? assemblyIdOverride
        : qbankAssemblyState.assemblyId;
      if (!exam || !assemblyId) {
        status.textContent = 'Assemble a selected paper first.';
        return;
      }
      if (!confirm('Publish this paper-only snapshot to the live exam? The evaluation key will remain unavailable until post-exam reconstruction.')) {
        return;
      }
      status.textContent = 'Publishing the selected paper-only snapshot to the live exam...';
      try {
        const data = await jsonRequest(
          '/api/exams/' + encodeURIComponent(exam.examCode) + '/qbank/assemblies/' + encodeURIComponent(assemblyId) + '/publish',
          'POST'
        );
        qbankAssemblyState.assemblyId = assemblyId;
        status.innerHTML = '<span class="ok">Published</span>\\n' + escapeHtml(data.message || 'Selected paper published.');
        await loadExams(exam.examCode);
        setQBankAssemblyDownloads(assemblyId, false);
        await refreshDatabaseView();
        await loadQBankAssemblyHistory(exam);
      } catch (error) {
        status.innerHTML = '<span class="error">Publish failed</span>\\n' + escapeHtml(error.message);
      }
    }

    async function aggregatePublishedQBankKey() {
      const exam = activeExam();
      const status = document.getElementById('qbankAssemblyStatus');
      if (!exam) {
        status.textContent = 'Select an exam project first.';
        return;
      }
      status.textContent = 'Reconstructing the evaluation key from the published snapshot and protected server lineage...';
      try {
        const data = await jsonRequest('/api/exams/' + encodeURIComponent(exam.examCode) + '/qbank/reconstruct-evaluation-key', 'POST');
        document.getElementById('downloadQBankKey').disabled = !data.answerKeyDownloadUrl;
        document.getElementById('downloadQBankKey').dataset.downloadUrl = data.answerKeyDownloadUrl || '';
        const aggregatedAssemblyId = data.assemblyId || qbankAssemblyState.assemblyId;
        qbankAssemblyState.assemblyId = aggregatedAssemblyId;
        setQBankAssemblyDownloads(aggregatedAssemblyId, Boolean(data.answerKeyDownloadUrl));
        status.innerHTML = '<span class="ok">Evaluation key reconstructed</span>: '
          + escapeHtml(data.matchedAnswerCount || 0) + ' / '
          + escapeHtml(data.questionCount || 0) + ' selected answers matched | '
          + escapeHtml(data.totalMarks || 0) + ' marks<br>'
          + escapeHtml(data.message || 'The reconstructed key is paired to the exact served paper and ready for evaluation.');
        await loadExams(exam.examCode);
        setQBankAssemblyDownloads(aggregatedAssemblyId, Boolean(data.answerKeyDownloadUrl));
        await refreshEvaluation();
        await loadQBankAssemblyHistory(exam);
      } catch (error) {
        status.innerHTML = '<span class="error">Evaluation key reconstruction failed</span>\\n' + escapeHtml(error.message);
      }
    }

    async function clearNotificationOutbox() {
      if (!confirm('Clear all email/SMS notification outbox rows?')) return;
      const status = document.getElementById('dbStatus');
      status.textContent = 'Clearing notification outbox...';
      try {
        await jsonRequest('/api/notifications', 'DELETE');
        await refreshDatabaseView();
      } catch (error) {
        status.innerHTML = '<span class="error">Could not clear notification outbox</span>\\n' + error.message;
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

