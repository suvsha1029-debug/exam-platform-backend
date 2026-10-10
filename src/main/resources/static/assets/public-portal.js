(() => {
  const byId = id => document.getElementById(id);
  const icons = () => window.lucide?.createIcons({ attrs: { 'aria-hidden': 'true' } });
  const text = (tag, value, className) => {
    const element = document.createElement(tag);
    element.textContent = value || '';
    if (className) element.className = className;
    return element;
  };
  const icon = name => {
    const element = document.createElement('i');
    element.dataset.lucide = name;
    element.setAttribute('aria-hidden', 'true');
    return element;
  };
  const safeUrl = value => {
    if (!value) return '';
    try {
      const url = new URL(value, location.origin);
      return ['http:', 'https:'].includes(url.protocol) ? url.href : '';
    } catch (_) { return ''; }
  };
  const color = (element, property, value) => {
    element.style[property] = /^#[0-9a-f]{6}$/i.test(value || '') ? value : '';
  };
  const notifications = exam => PortalNotices.fromExam(exam);
  const image = (id, source) => {
    const element = byId(id);
    const url = safeUrl(source);
    element.hidden = !url;
    element.onerror = () => { element.hidden = true; };
    if (url) element.src = url;
    else element.removeAttribute('src');
  };

  function renderSupport(content = {}) {
    const faqs = byId('faqList');
    faqs.replaceChildren();
    for (const faq of content.faqs || []) {
      if (!faq?.question || !faq?.answer) continue;
      const detail = document.createElement('details');
      detail.className = 'faq-item';
      const summary = document.createElement('summary');
      summary.append(text('span', faq.question), icon('chevron-down'));
      detail.append(summary, text('p', faq.answer, 'faq-answer'));
      faqs.append(detail);
    }
    if (!faqs.children.length) faqs.append(text('p', 'FAQs will be published here.', 'empty-state'));
    const contacts = byId('helpContacts');
    contacts.replaceChildren();
    const contact = (name, label, value, href) => {
      if (!value) return;
      const row = text('div', '', 'contact-item');
      const body = document.createElement('span');
      body.append(text('small', label));
      const item = text(href ? 'a' : 'span', value);
      if (href) item.href = href;
      if (href?.startsWith('http')) { item.target = '_blank'; item.rel = 'noopener noreferrer'; }
      body.append(item);
      row.append(icon(name), body);
      contacts.append(row);
    };
    const email = String(content.helpEmail || '').trim();
    const phone = String(content.helpPhone || '').trim();
    contact('mail', 'Email', email, /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) ? 'mailto:' + email : '');
    contact('phone', 'Telephone', phone, /^[+0-9() .-]+$/.test(phone) ? 'tel:' + phone.replace(/[() .-]/g, '') : '');
    contact('clock-3', 'Support Hours', content.helpHours);
    const url = safeUrl(content.helpUrl);
    if (url) contact('external-link', 'Online Support', 'Contact the examination helpdesk', url);
    if (!contacts.children.length) contacts.append(text('p', 'Contact details will be published here.', 'empty-state'));
  }

  function renderExam(exam) {
    const open = Boolean(exam?.registrationOpen);
    const correction = Boolean(exam?.correctionsOpen);
    byId('portalHeaderTitle').textContent = exam?.portalHeaderTitle || exam?.name || 'Candidate Portal';
    byId('portalHeaderSubtitle').textContent = exam?.portalHeaderSubtitle || 'Examination services';
    color(byId('portalHeader'), 'color', exam?.portalHeaderTextColor);
    color(byId('portalHeader'), 'backgroundColor', exam?.portalHeaderBackgroundColor);
    image('portalBanner', exam?.portalTopBannerImageUrl);
    image('heroWatermark', exam?.portalHeroImageUrl);
    const background = safeUrl(exam?.portalPageBackgroundImageUrl);
    byId('pageWatermark').hidden = !background;
    byId('pageWatermark').style.backgroundImage = background ? 'url("' + background.replaceAll('"', '%22') + '")' : '';
    const notices = exam ? notifications(exam) : [];
    byId('topTicker').hidden = !notices.length;
    byId('tickerText').textContent = notices[0]?.text || '';
    color(byId('topTicker'), 'color', exam?.portalNotificationTextColor);
    color(byId('topTicker'), 'backgroundColor', exam?.portalNotificationBackgroundColor);
    const size = /^(\d{1,2})px$/.exec(exam?.portalNotificationFontSize || '');
    byId('tickerText').style.fontSize = size ? Math.max(12, Math.min(24, Number(size[1]))) + 'px' : '';
    const noticeLink = byId('publicNoticeTab');
    noticeLink.hidden = !(exam?.portalNoticeTabEnabled && exam?.portalNoticePdfUploaded);
    noticeLink.href = exam ? '/api/public/exams/' + encodeURIComponent(exam.examCode) + '/notice-document.pdf' : '#';
    byId('publicNoticeLabel').textContent = exam?.portalNoticeDocumentLabel || 'Notices';
    noticeLink.title = byId('publicNoticeLabel').textContent;
    byId('mainStatus').textContent = correction ? 'Correction Window Open' : open ? 'Registration Open' : 'Registration Closed';
    byId('mainCopy').textContent = exam?.portalWelcomeText || (correction
      ? 'Log in to review your application and update permitted fields.'
      : open ? 'Applications are now open. Submit your registration for this examination.'
        : 'Candidate login remains available for your application, responses and results.');
    color(byId('mainCopy'), 'color', exam?.portalWelcomeTextColor);
    const action = byId('primaryAction');
    const registerAction = open && !correction;
    action.href = registerAction ? '/candidate-entry/registration-personal' : '/candidate-entry/dashboard';
    byId('primaryActionLabel').textContent = registerAction ? 'Register Now' : 'Candidate Login';
    action.classList.toggle('login-pulse', !open);
    byId('registrationNav').hidden = !open;
    byId('registeredLogin').hidden = !open;
    renderSupport(exam?.portalSupportContent || {});
    icons();
  }

  function renderBoard(exams) {
    const rows = byId('examRows');
    rows.replaceChildren();
    for (const exam of exams) {
      const section = text('div', '', 'exam-notification');
      const heading = text('div', '', 'exam-row-heading');
      const status = exam.correctionsOpen ? 'Correction Open' : exam.registrationOpen ? 'Registration Open' : 'Registration Closed';
      heading.append(text('h3', exam.name), text('span', status, 'registration-tag' + (exam.registrationOpen || exam.correctionsOpen ? ' open' : '')));
      section.append(heading);
      const notices = notifications(exam);
      if (notices.length) {
        const list = text('ul', '', 'notification-list');
        for (const notice of notices) {
          const row = document.createElement('li');
          const old = !notice.isNew;
          const metadata = text('div', '', 'notice-metadata');
          metadata.append(text('span', old ? 'ARCHIVE' : 'NEW', 'board-badge' + (old ? ' old' : '')));
          const date = PortalNotices.dateLabel(notice.publishedOn);
          if (date) { const time = text('time', date); time.dateTime = notice.publishedOn; metadata.append(time); }
          row.append(metadata, text('span', notice.text, 'notice-copy'));
          list.append(row);
        }
        section.append(list);
      } else {
        section.append(text('p', 'No notifications published.', 'empty-state'));
      }
      rows.append(section);
    }
    if (!exams.length) rows.append(text('p', 'No examinations are currently published.', 'empty-state'));
  }

  async function loadStatus() {
    const response = await fetch('/api/public/exams', { cache: 'no-store' });
    if (!response.ok) throw new Error('Examination details unavailable');
    const exams = await response.json();
    if (!Array.isArray(exams)) throw new Error('Invalid examination details');
    const selected = exams.find(exam => exam.correctionsOpen) || exams.find(exam => exam.registrationOpen) || exams[0];
    const selector = byId('examSelector');
    selector.replaceChildren();
    for (const exam of exams) selector.add(new Option(exam.name, exam.examCode, false, exam === selected));
    byId('examSelectorWrap').hidden = exams.length < 2;
    selector.addEventListener('change', () => renderExam(exams.find(exam => exam.examCode === selector.value)));
    renderBoard(exams);
    renderExam(selected);
  }

  icons();
  loadStatus().catch(() => {
    byId('mainStatus').textContent = 'Portal Temporarily Unavailable';
    byId('mainCopy').textContent = 'Examination details could not be loaded. Please try again shortly.';
    byId('examRows').append(text('p', 'Notifications are temporarily unavailable.', 'empty-state'));
  });
})();
