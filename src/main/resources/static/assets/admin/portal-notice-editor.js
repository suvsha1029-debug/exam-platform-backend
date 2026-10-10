(() => {
  const byId = id => document.getElementById(id);
  const editor = byId('portalNoticeEditor');
  let sequence = 0;
  const values = row => ({ text: row.querySelector('[data-notice-text]').value.trim(), isNew: row.querySelector('[data-notice-new]').checked, publishedOn: row.querySelector('[data-notice-date]').value });
  function sync() {
    const rows = [...editor.children];
    const notices = PortalNotices.ordered(rows.map(values));
    const legacy = notices.map(n => n.text).filter(Boolean).join('\n');
    byId('portalNotifications').value = legacy;
    byId('portalNotification').value = notices[0]?.text || '';
    byId('portalNoticeCount').textContent = rows.length + ' / 50';
    byId('addPortalNotice').disabled = rows.length >= 50;
    byId('portalNoticeEditorStatus').textContent = legacy.length > 2400 ? 'Shorten the notices: the combined limit is 2400 characters.' : '';
    rows.forEach((row, i) => {
      row.querySelector('legend').textContent = 'Notice ' + (i + 1);
      for (const [action, adjacent] of [['up', rows[i - 1]], ['down', rows[i + 1]]]) {
        const a = values(row), b = adjacent && values(adjacent);
        row.querySelector('[data-notice-action="' + action + '"]').disabled = !b || a.isNew !== b.isNew || a.publishedOn !== b.publishedOn;
      }
    });
    lucide.createIcons({ attrs: { 'aria-hidden': 'true' } });
  }
  function sort() {
    [...editor.children].sort((a, b) => Number(values(b).isNew) - Number(values(a).isNew) || values(b).publishedOn.localeCompare(values(a).publishedOn)).forEach(row => editor.append(row));
    sync();
  }
  function add(notice, preserveExisting = false) {
    if (!preserveExisting && editor.children.length >= 50) return;
    const id = ++sequence;
    const row = document.createElement('fieldset');
    row.className = 'notice-editor-row';
    row.innerHTML = '<legend></legend><div class="notice-entry-text"><label for="noticeText' + id + '">Notice</label><textarea id="noticeText' + id + '" data-notice-text rows="2" maxlength="500"></textarea></div>'
      + '<div><label for="noticeDate' + id + '">Publication date</label><input id="noticeDate' + id + '" data-notice-date type="date"></div>'
      + '<label class="notice-new-check"><input data-notice-new type="checkbox">New</label><div class="notice-row-actions">'
      + ['up', 'down', 'remove'].map(action => '<button type="button" class="secondary" data-notice-action="' + action + '" title="' + (action === 'remove' ? 'Remove notice' : 'Move ' + action + ' within the same date and status') + '" aria-label="' + (action === 'remove' ? 'Remove notice' : 'Move notice ' + action) + '"><i data-lucide="' + (action === 'remove' ? 'trash-2' : 'arrow-' + action) + '"></i></button>').join('') + '</div>';
    row.querySelector('[data-notice-text]').value = notice.text || '';
    row.querySelector('[data-notice-date]').value = notice.publishedOn || '';
    row.querySelector('[data-notice-new]').checked = Boolean(notice.isNew);
    editor.prepend(row); sort(); return row;
  }
  byId('addPortalNotice').addEventListener('click', () => {
    const publishedOn = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
    add({ text: '', isNew: true, publishedOn })?.querySelector('textarea').focus();
  });
  editor.addEventListener('input', sync);
  editor.addEventListener('change', sort);
  editor.addEventListener('click', event => {
    const button = event.target.closest('[data-notice-action]');
    if (!button || button.disabled) return;
    const row = button.closest('fieldset');
    if (button.dataset.noticeAction === 'remove') row.remove();
    if (button.dataset.noticeAction === 'up') editor.insertBefore(row, row.previousElementSibling);
    if (button.dataset.noticeAction === 'down') editor.insertBefore(row.nextElementSibling, row);
    sync();
  });
  window.PortalNoticeEditor = {
    populate(exam) {
      editor.replaceChildren();
      for (const notice of PortalNotices.fromExam(exam).reverse()) add(notice, true);
      sync();
    },
    clear() { editor.replaceChildren(); sync(); },
    serialize() {
      const notices = PortalNotices.ordered([...editor.children].map(values));
      if (notices.length > 50) throw new Error('Keep no more than 50 notices in the register.');
      if (notices.some(n => !n.text || n.text.length > 500)) throw new Error('Each notice must contain 1 to 500 characters.');
      sync();
      if (byId('portalNotifications').value.length > 2400) throw new Error('Notifications must total no more than 2400 characters.');
      return notices;
    }
  };
  sync();
})();
