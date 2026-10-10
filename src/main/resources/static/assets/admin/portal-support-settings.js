(() => {
  const editor = document.getElementById('portalFaqEditor');
  if (!editor) return;
  const byId = id => document.getElementById(id);
  let sequence = 0;
  const refresh = () => {
    const rows = [...editor.children];
    rows.forEach((row, index) => {
      row.querySelector('strong').textContent = 'FAQ ' + (index + 1);
      row.querySelector('[data-faq-action="up"]').disabled = index === 0;
      row.querySelector('[data-faq-action="down"]').disabled = index === rows.length - 1;
    });
    byId('portalFaqCount').textContent = rows.length + ' / 25';
    byId('addPortalFaq').disabled = rows.length >= 25;
    window.lucide?.createIcons({ attrs: { 'aria-hidden': 'true' } });
  };
  function addFaq(faq = {}) {
    if (editor.children.length >= 25) return;
    const id = ++sequence;
    const row = document.createElement('fieldset');
    row.className = 'portal-faq-entry';
    row.innerHTML = '<div class="portal-faq-entry-head"><strong></strong><div class="portal-faq-actions">'
      + '<button type="button" class="secondary" data-faq-action="up" title="Move FAQ up" aria-label="Move FAQ up"><i data-lucide="arrow-up"></i></button>'
      + '<button type="button" class="secondary" data-faq-action="down" title="Move FAQ down" aria-label="Move FAQ down"><i data-lucide="arrow-down"></i></button>'
      + '<button type="button" class="secondary" data-faq-action="remove" title="Remove FAQ" aria-label="Remove FAQ"><i data-lucide="trash-2"></i></button></div></div>'
      + '<label for="faqQuestion' + id + '">Question</label><input id="faqQuestion' + id + '" data-faq-question maxlength="240">'
      + '<label for="faqAnswer' + id + '">Answer</label><textarea id="faqAnswer' + id + '" data-faq-answer maxlength="3000"></textarea>';
    row.querySelector('input').value = faq.question || '';
    row.querySelector('textarea').value = faq.answer || '';
    editor.append(row);
    refresh();
    return row;
  }
  byId('addPortalFaq').addEventListener('click', () => addFaq()?.querySelector('input').focus());
  editor.addEventListener('click', event => {
    const button = event.target.closest('[data-faq-action]');
    if (!button || button.disabled) return;
    const row = button.closest('.portal-faq-entry');
    if (button.dataset.faqAction === 'remove') row.remove();
    if (button.dataset.faqAction === 'up' && row.previousElementSibling) editor.insertBefore(row, row.previousElementSibling);
    if (button.dataset.faqAction === 'down' && row.nextElementSibling) editor.insertBefore(row.nextElementSibling, row);
    refresh();
  });
  window.PortalSupportSettings = {
    populate(exam) {
      const content = exam.portalSupportContent || {};
      editor.replaceChildren();
      for (const faq of content.faqs || []) addFaq(faq);
      byId('portalHelpEmail').value = content.helpEmail || '';
      byId('portalHelpPhone').value = content.helpPhone || '';
      byId('portalHelpHours').value = content.helpHours || '';
      byId('portalHelpUrl').value = content.helpUrl || '';
      refresh();
    },
    serialize() {
      const faqs = [];
      for (const row of editor.children) {
        const question = row.querySelector('input').value.trim();
        const answer = row.querySelector('textarea').value.trim();
        if (!question && !answer) continue;
        if (!question || !answer) throw new Error('Enter both a question and an answer for each FAQ.');
        faqs.push({ question, answer });
      }
      const helpUrl = byId('portalHelpUrl').value.trim();
      if (helpUrl && !/^https?:\/\/[^\s]+$/i.test(helpUrl)) throw new Error('Helpdesk website must use http:// or https://.');
      return {
        faqs,
        helpEmail: byId('portalHelpEmail').value.trim(),
        helpPhone: byId('portalHelpPhone').value.trim(),
        helpHours: byId('portalHelpHours').value.trim(),
        helpUrl
      };
    }
  };
  refresh();
})();
