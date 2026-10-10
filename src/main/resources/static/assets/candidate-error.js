(function () {
  const knownReasons = new Set([
    'invalid-link',
    'auth-required',
    'auth-mismatch',
    'exam-not-ready',
    'session-closed',
    'unknown-page'
  ]);

  function normalizeCandidateErrorReason(reason) {
    const value = String(reason || '').trim().toLowerCase();
    return knownReasons.has(value) ? value : 'invalid-link';
  }

  function candidateErrorReasonFromMessage(message) {
    const text = String(message || '').toLowerCase();
    if (text.includes('registration id or date of birth') || text.includes('date of birth') || text.includes('does not match')) {
      return 'auth-mismatch';
    }
    if (text.includes('not enabled') || text.includes('not uploaded') || text.includes('not ready') || text.includes('lobby') || text.includes('launch')) {
      return 'exam-not-ready';
    }
    if (text.includes('expired') || text.includes('session') || text.includes('submitted') || text.includes('closed')) {
      return 'session-closed';
    }
    if (text.includes('login') || text.includes('required') || text.includes('candidate')) {
      return 'auth-required';
    }
    return 'invalid-link';
  }

  function openCandidateError(reason) {
    window.location.href = '/candidate-error.html?reason=' + encodeURIComponent(normalizeCandidateErrorReason(reason));
  }

  window.normalizeCandidateErrorReason = normalizeCandidateErrorReason;
  window.candidateErrorReasonFromMessage = candidateErrorReasonFromMessage;
  window.openCandidateError = openCandidateError;
})();
