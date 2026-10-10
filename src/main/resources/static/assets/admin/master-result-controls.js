(function () {
  'use strict';
  const byId = id => document.getElementById(id);
  const sections = [...document.querySelectorAll('main > .grid > section')];
  let page = 0, totalPages = 0, rows = [], selected = null, busy = false, generation = 0;
  let historyId = '', historyPage = 0, historyGeneration = 0;
  const icons = () => lucide.createIcons({ attrs: { 'aria-hidden': 'true' } });
  function show(view) {
    if (!['accounts', 'approvals', 'results'].includes(view)) view = 'accounts';
    sections.forEach((section, index) => section.hidden = view === 'results' || (view === 'accounts' ? index > 1 : index < 2));
    byId('masterResultControls').hidden = view !== 'results';
    document.querySelectorAll('[data-master-view]').forEach(button => {
      button.classList.toggle('active', button.dataset.masterView === view);
      button.setAttribute('aria-current', button.dataset.masterView === view ? 'page' : 'false');
    });
    if (view === 'results') load();
  }
  document.querySelectorAll('[data-master-view]').forEach(button => button.addEventListener('click', () => { location.hash = button.dataset.masterView; }));
  window.addEventListener('hashchange', () => show(location.hash.slice(1)));
  async function load() {
    const token = ++generation;
    byId('holdStatus').textContent = 'Loading candidates...';
    byId('holdRows').replaceChildren();
    byId('holdPrevious').disabled = byId('holdNext').disabled = true;
    try {
      const params = new URLSearchParams({ page, exam: byId('holdExam').value.trim(), search: byId('holdSearch').value.trim(), heldOnly: byId('holdOnly').checked });
      const data = await request('/api/master-admin/result-controls?' + params);
      if (token !== generation) return;
      rows = data.content; page = data.page; totalPages = data.totalPages;
      byId('holdRows').innerHTML = rows.map((row, index) => '<tr><td><strong>' + esc(row.registrationId) + '</strong></td><td>' + esc(row.name) + '</td><td>' + esc(row.examCode) + '</td><td><span class="operations-status ' + (row.withheld ? 'held' : '') + '">' + (row.withheld ? 'Withheld' : 'No hold') + '</span></td><td><div class="actions"><button type="button" data-decision="' + index + '" class="' + (row.withheld ? 'restore' : 'danger') + '">' + (row.withheld ? 'Restore result' : 'Withhold result') + '</button><button type="button" data-history="' + index + '" class="secondary">History</button></div></td></tr>').join('') || '<tr><td colspan="5">No matching candidates.</td></tr>';
      byId('holdStatus').textContent = data.totalElements + ' candidate(s)';
      byId('holdPage').textContent = 'Page ' + (page + 1) + ' of ' + Math.max(1, totalPages);
      byId('holdPrevious').disabled = page === 0;
      byId('holdNext').disabled = page + 1 >= totalPages;
    } catch (error) { if (token === generation) byId('holdStatus').textContent = error.message; }
  }
  byId('resultControlSearch').addEventListener('submit', event => { event.preventDefault(); page = 0; load(); });
  byId('holdPrevious').addEventListener('click', () => { if (page > 0) { page--; load(); } });
  byId('holdNext').addEventListener('click', () => { if (page + 1 < totalPages) { page++; load(); } });
  byId('holdRows').addEventListener('click', event => {
    const decision = event.target.closest('[data-decision]');
    const history = event.target.closest('[data-history]');
    if (history) { historyId = rows[Number(history.dataset.history)].registrationId; historyPage = 0; byId('holdHistoryDialog').showModal(); loadHistory(); }
    if (!decision) return;
    selected = rows[Number(decision.dataset.decision)];
    byId('holdDialogTitle').textContent = selected.withheld ? 'Restore candidate result' : 'Withhold candidate result';
    byId('holdCandidate').textContent = selected.name + ' / ' + selected.registrationId + ' / ' + selected.examCode;
    byId('holdImpact').textContent = selected.withheld ? 'Removes this hold. The result is visible only when exam-wide publication is enabled.' : 'Blocks this candidate from opening or downloading their result and score analysis. The saved response is retained.';
    byId('holdReason').value = ''; byId('holdDecisionStatus').textContent = '';
    byId('holdConfirm').textContent = selected.withheld ? 'Restore result' : 'Withhold result';
    byId('holdDialog').showModal();
  });
  function closeDecision() { if (!busy) byId('holdDialog').close(); }
  byId('holdDialogClose').addEventListener('click', closeDecision);
  byId('holdCancel').addEventListener('click', closeDecision);
  byId('holdDialog').addEventListener('cancel', event => { if (busy) event.preventDefault(); });
  byId('holdDecisionForm').addEventListener('submit', async event => {
    event.preventDefault(); if (!selected || busy) return;
    const reason = byId('holdReason').value.trim();
    if (reason.length < 5) { byId('holdDecisionStatus').textContent = 'Enter a reason of at least 5 characters.'; return; }
    busy = true; byId('holdConfirm').disabled = true;
    try {
      await request('/api/master-admin/result-controls/' + encodeURIComponent(selected.registrationId), { method: 'PATCH', body: JSON.stringify({ withheld: !selected.withheld, expectedWithheld: selected.withheld, reason }) });
      byId('holdDialog').close(); await load();
    } catch (error) { byId('holdDecisionStatus').textContent = error.message; }
    finally { busy = false; byId('holdConfirm').disabled = false; }
  });
  async function loadHistory() {
    const token = ++historyGeneration;
    byId('holdHistoryBody').textContent = 'Loading decision history...';
    byId('holdHistoryPrevious').disabled = byId('holdHistoryNext').disabled = true;
    try {
      const events = await request('/api/master-admin/result-controls/' + encodeURIComponent(historyId) + '/history?page=' + historyPage);
      if (token !== historyGeneration) return;
      byId('holdHistoryTitle').textContent = historyId + ' / Decision history';
      byId('holdHistoryBody').innerHTML = events.map(event => '<article class="operations-history"><strong>' + (event.withheld ? 'Result withheld' : 'Result restored') + '</strong><p>' + esc(event.reason) + '</p><small>' + esc(event.actor) + ' / ' + esc(new Date(event.decidedAt).toLocaleString()) + '</small></article>').join('') || '<p>No decisions recorded.</p>';
      byId('holdHistoryPage').textContent = 'Page ' + (historyPage + 1);
      byId('holdHistoryPrevious').disabled = historyPage === 0;
      byId('holdHistoryNext').disabled = events.length < 20;
    } catch (error) { if (token === historyGeneration) byId('holdHistoryBody').textContent = error.message; }
  }
  byId('holdHistoryClose').addEventListener('click', () => byId('holdHistoryDialog').close());
  byId('holdHistoryPrevious').addEventListener('click', () => { if (historyPage) { historyPage--; loadHistory(); } });
  byId('holdHistoryNext').addEventListener('click', () => { historyPage++; loadHistory(); });
  icons(); show(location.hash.slice(1));
})();
