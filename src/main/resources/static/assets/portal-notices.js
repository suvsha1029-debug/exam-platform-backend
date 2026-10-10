(() => {
  function ordered(notices) {
    return [...notices].sort((a, b) => Number(b.isNew) - Number(a.isNew) || String(b.publishedOn || '').localeCompare(String(a.publishedOn || '')));
  }
  function fromExam(exam = {}) {
    if (Array.isArray(exam.portalNotices)) return ordered(exam.portalNotices);
    return String(exam.portalNotifications || exam.portalNotification || '').split(/\r?\n/).map(text => text.trim()).filter(Boolean)
      .map(text => ({ text, isNew: exam.portalNotificationBadgeStyle !== 'OLD', publishedOn: '' }));
  }
  function dateLabel(value) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(value || '')) return '';
    const date = new Date(value + 'T12:00:00Z');
    return Number.isNaN(date.getTime()) ? '' : new Intl.DateTimeFormat('en-GB', { day: '2-digit', month: 'short', year: 'numeric', timeZone: 'UTC' }).format(date);
  }
  window.PortalNotices = { ordered, fromExam, dateLabel };
})();
