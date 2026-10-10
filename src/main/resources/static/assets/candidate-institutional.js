(() => {
  const shell = document.createElement('div');
  shell.className = 'candidate-site-shell';
  shell.innerHTML = `
    <div class="candidate-site-minibar">
      <a class="candidate-site-home" href="/">Home</a>
      <nav class="candidate-site-nav" aria-label="Candidate portal">
        <a id="candidateNoticeLink" href="#" target="_blank" rel="noopener" hidden>Notices</a>
        <a href="/candidate-entry/dashboard" data-candidate-logout>Logout</a>
      </nav>
    </div>`;

  const clearCandidateSession = () => {
    try {
      fetch('/api/candidate/logout', {
        method: 'POST',
        keepalive: true
      }).catch(() => {});
    } catch (_) {
      // Local cleanup below must still run even if the network is unavailable.
    }
    sessionStorage.clear();
    localStorage.removeItem('candidateExamAuth');
    localStorage.removeItem('candidateDashboardData');
    localStorage.removeItem('candidateDashboardPassword');
  };

  const loadNoticeLink = async () => {
    const link = document.getElementById('candidateNoticeLink');
    if (!link) return;
    try {
      const response = await fetch('/api/public/exams', {cache:'no-store'});
      if (!response.ok) return;
      const exams = await response.json();
      const exam = exams.find((item) => item.portalNoticeTabEnabled && item.portalNoticePdfUploaded);
      if (!exam) return;
      link.textContent = exam.portalNoticeDocumentLabel || 'Notices / Brochure';
      link.href = '/api/public/exams/' + encodeURIComponent(exam.examCode) + '/notice-document.pdf';
      link.hidden = false;
    } catch (_) {
      link.hidden = true;
    }
  };

  const mount = () => {
    if (!document.body || document.querySelector('.candidate-site-shell')) return;
    document.body.classList.add('candidate-institutional');
    if (document.querySelector('#loginForm, #candidateForm, #correctionForm')) {
      document.body.classList.add('candidate-forms');
    }
    const path = window.location.pathname.toLowerCase();
    const isPublicPage = path === '/'
      || path.endsWith('/index.html')
      || path.includes('candidate-dashboard')
      || path.includes('candidate-registration')
      || path.includes('reset-password');
    if (isPublicPage) return;
    document.body.prepend(shell);
    shell.querySelector('[data-candidate-logout]').addEventListener('click', clearCandidateSession);
    loadNoticeLink();
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', mount, {once:true});
  } else {
    mount();
  }
})();
