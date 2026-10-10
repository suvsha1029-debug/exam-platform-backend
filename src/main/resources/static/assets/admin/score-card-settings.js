(function () {
  const byId = (id) => document.getElementById(id);
  const fields = {
    instituteName: 'scoreInstituteName', backgroundColor: 'scoreBackgroundColor',
    accentColor: 'scoreAccentColor', stripColor: 'scoreStripColor',
    notesTitle: 'scoreNotesTitle', notesText: 'scoreNotesText', notesImageDataUrl: 'scoreNotesImageDataUrl'
  };
  const defaults = { instituteName: '', backgroundColor: '#ffffff', accentColor: '#153e50', stripColor: '#967344', notesTitle: 'Score information', notesText: '', notesImageDataUrl: '' };
  function populate(exam) {
    const settings = exam.reportCardAppearance || {};
    for (const [key, id] of Object.entries(fields)) byId(id).value = settings[key] ?? defaults[key];
    byId('scoreStripsEnabled').checked = settings.stripsEnabled !== false;
    byId('scoreWatermarkOpacity').value = settings.watermarkOpacity ?? 6;
    byId('scoreWatermarkValue').value = byId('scoreWatermarkOpacity').value + '%';
    byId('scoreNotesImageFile').value = '';
  }
  async function readAsset(fileInput, hiddenInput) {
    const file = fileInput.files?.[0];
    if (file && file.size > 4 * 1024 * 1024) throw new Error('Score-card images must be smaller than 4 MB.');
    const source = file ? await new Promise((resolve, reject) => {
      const reader = new FileReader(); reader.onload = () => resolve(reader.result); reader.onerror = reject; reader.readAsDataURL(file);
    }) : hiddenInput.value;
    if (!source) return '';
    const image = new Image();
    image.src = source;
    try { await image.decode(); } catch { throw new Error('A score-card image could not be decoded. Choose a valid PNG, JPG, WebP or SVG.'); }
    const scale = Math.min(1, 1600 / Math.max(image.naturalWidth, image.naturalHeight));
    const canvas = document.createElement('canvas');
    canvas.width = Math.max(1, Math.round(image.naturalWidth * scale));
    canvas.height = Math.max(1, Math.round(image.naturalHeight * scale));
    canvas.getContext('2d').drawImage(image, 0, 0, canvas.width, canvas.height);
    const result = canvas.toDataURL('image/png');
    if (result.length > 3000000) throw new Error('The score-card image is too large. Choose a smaller image.');
    return result;
  }
  async function serialize() {
    const result = {};
    for (const [key, id] of Object.entries(fields)) result[key] = byId(id).value.trim();
    result.stripsEnabled = byId('scoreStripsEnabled').checked;
    result.watermarkOpacity = Number(byId('scoreWatermarkOpacity').value);
    result.notesImageDataUrl = await readAsset(byId('scoreNotesImageFile'), byId('scoreNotesImageDataUrl'));
    return result;
  }
  byId('scoreWatermarkOpacity').addEventListener('input', (event) => { byId('scoreWatermarkValue').value = event.target.value + '%'; });
  document.querySelectorAll('[data-clear-score-image]').forEach((button) => button.addEventListener('click', () => {
    byId(button.dataset.clearScoreImage + 'File').value = '';
    byId(button.dataset.clearScoreImage + 'DataUrl').value = '';
  }));
  byId('scorePreviewClose').addEventListener('click', () => byId('scorePreviewDialog').close());
  byId('scorePreviewButton').addEventListener('click', async () => {
    const status = byId('scorePreviewStatus');
    const exam = byId('configExamCode').value;
    const application = byId('scorePreviewApplication').value.trim();
    if (!exam || !application) { status.textContent = 'Select an exam and enter an evaluated application number.'; return; }
    byId('scorePreviewButton').disabled = true;
    status.textContent = 'Preparing preview...';
    try {
      const response = await fetch('/api/evaluation/' + encodeURIComponent(exam) + '/score-card-document/' + encodeURIComponent(application), { credentials: 'same-origin', cache: 'no-store' });
      const data = await response.json();
      if (!response.ok) throw new Error(data.detail || data.message || 'Preview could not be prepared.');
      byId('scorePreviewPages').replaceChildren(...data.pages.map((source, index) => {
        const image = new Image(); image.src = source; image.alt = 'Score card page ' + (index + 1); image.style.cssText = 'width:100%;height:auto;display:block;margin-bottom:12px'; return image;
      }));
      byId('scorePreviewDownload').href = data.pdfDataUrl;
      byId('scorePreviewDownload').download = data.fileName;
      byId('scorePreviewDialog').showModal(); status.textContent = '';
    } catch (error) { status.textContent = error.message; }
    finally { byId('scorePreviewButton').disabled = false; }
  });
  window.ScoreCardSettings = { populate, serialize, readAsset };
}());
