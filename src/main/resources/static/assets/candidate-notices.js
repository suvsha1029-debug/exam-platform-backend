(() => {
  let generation = 0;
  window.renderCandidateNotices = async examCode => {
    const current = ++generation;
    const section = document.getElementById('candidateBulletin');
    section.hidden = true;
    try {
      const response = await fetch('/api/public/exams', { cache: 'no-store' });
      if (!response.ok) return;
      const exams = await response.json();
      if (current !== generation) return;
      const exam = exams.find(item => item.examCode === examCode);
      const notices = exam ? PortalNotices.fromExam(exam).slice(0, 3) : [];
      const list = document.getElementById('candidateNoticeList');
      list.replaceChildren();
      for (const notice of notices) {
        const row = document.createElement('li');
        const meta = document.createElement('span'); meta.className = 'candidate-notice-meta';
        const badge = document.createElement('span'); badge.className = 'candidate-notice-badge' + (notice.isNew ? ' new' : ''); badge.textContent = notice.isNew ? 'NEW' : 'ARCHIVE'; meta.append(badge);
        const date = PortalNotices.dateLabel(notice.publishedOn);
        if (date) { const time = document.createElement('time'); time.dateTime = notice.publishedOn; time.textContent = date; meta.append(time); }
        const copy = document.createElement('span'); copy.textContent = notice.text; row.append(meta, copy); list.append(row);
      }
      section.hidden = !notices.length;
    } catch (_) { if (current === generation) section.hidden = true; }
  };
})();
