(() => {
  const escape = value => String(value ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]));
  const display = value => value == null || value === '' ? 'Not provided' : String(value);
  const date = value => {
    if (!value) return 'Not provided';
    const parsed = new Date(value);
    return Number.isNaN(parsed.getTime()) ? String(value) : new Intl.DateTimeFormat('en-GB', { day: '2-digit', month: 'short', year: 'numeric', timeZone: 'Asia/Kolkata' }).format(parsed);
  };
  const field = (label, value, wide = false, key = '') => ({ label, value: display(value), wide, key });
  function section(number, title, fields) {
    let rows = '';
    for (let index = 0; index < fields.length; index++) {
      const first = fields[index];
      const pair = !first.wide && fields[index + 1] && !fields[index + 1].wide ? [first, fields[++index]] : [first];
      const long = pair.some(item => item.value.length > 900);
      rows += '<tr class="document-row' + (long ? ' long-entry' : '') + '">' + pair.map(item =>
        '<td' + (pair.length === 1 ? ' colspan="2"' : '') + '><span class="field-label"' + (item.key ? ' data-custom-label="' + escape(item.key) + '"' : '') + '>'
        + escape(item.label) + '</span><div class="field-value">' + escape(item.value) + '</div></td>').join('') + '</tr>';
    }
    const compact = fields.reduce((sum, item) => sum + item.value.length, 0) < 700;
    return '<table class="document-fields' + (compact ? ' compact-section' : '') + (title === 'Document Record' ? ' document-uploads' : '') + '"><thead><tr><th colspan="2"><span class="section-number">' + number
      + '</span>' + escape(title) + '</th></tr></thead><tbody>' + rows + '</tbody></table>';
  }
  function media(path, type) {
    return '<div class="' + type + '-frame">' + (path
      ? '<img class="document-' + type + '" alt="Candidate ' + type + '" data-document-file="' + escape(path) + '" hidden><span class="media-status">Loading ' + type + '...</span>'
      : '<span class="media-status">' + (type === 'photo' ? 'Photograph not uploaded' : 'Signature not uploaded') + '</span>') + '</div>';
  }
  function markup(candidate) {
    const uploads = [
      ['Photograph', candidate.photoUploaded], ['Signature', candidate.signatureUploaded], ['Identity document', candidate.idCardUploaded],
      ['Class 10 marksheet', candidate.class10MarksheetUploaded], ['Class 12 marksheet', candidate.class12MarksheetUploaded],
      ['Undergraduate document', candidate.ugDocumentUploaded], ['Postgraduate document', candidate.pgDocumentUploaded]
    ].map(([label, uploaded]) => field(label, uploaded ? 'Uploaded' : 'Not uploaded'));
    for (const [key, path] of Object.entries(candidate.customFilePaths || {})) uploads.push(field(key.replaceAll('_', ' '), path ? 'Uploaded' : 'Not uploaded', false, key));
    const custom = Object.entries(candidate.customFieldValues || {}).map(([key, value]) => field(key.replaceAll('_', ' '), value, String(value).length > 180 || String(value).includes('\n'), key));
    return '<div class="document-heading"><img class="document-banner" alt="Examination institute banner" hidden>'
      + '<div class="document-kicker">Candidate Application Record</div><div class="document-title">' + escape(candidate.examName || 'Examination') + '</div>'
      + '<div class="document-reference"><span>Application Number</span><strong>' + escape(candidate.registrationId) + '</strong></div></div>'
      + '<div class="document-identity"><div><span class="field-label">Candidate Name</span><div class="document-name">' + escape(display(candidate.fullName)) + '</div>'
      + '<dl class="identity-facts"><div><dt>Date of Birth</dt><dd>' + escape(date(candidate.dateOfBirth)) + '</dd></div>'
      + '<div><dt>Paper / Stream</dt><dd>' + escape(candidate.streamCode || 'Not applicable') + '</dd></div>'
      + '<div><dt>Registered On</dt><dd>' + escape(date(candidate.createdAt)) + '</dd></div></dl></div>'
      + '<div class="document-photo-block">' + media(candidate.photoPath, 'photo') + '<span>Candidate Photograph</span></div></div>'
      + section('01', 'Personal & Contact Details', [
        field('Gender', candidate.gender), field('Nationality', candidate.nationality),
        field('Email Address', candidate.email), field('Telephone', candidate.phone),
        field('Alternate Email', candidate.alternateEmail, true), field('Address', candidate.address, true),
        field('State / Province', candidate.state), field('Postal Code', candidate.pincode)
      ])
      + section('02', 'Parent & Guardian Details', [
        field('Parent Name', candidate.parentName), field('Parent Telephone', candidate.parentPhone),
        field('Guardian Name', candidate.guardianName), field('Guardian Telephone', candidate.guardianPhone),
        field('Guardian Relationship', candidate.guardianRelation)
      ])
      + section('03', 'Academic Details', [field('Undergraduate Details', candidate.ugDetails, true), field('Postgraduate Details', candidate.pgDetails, true)])
      + (custom.length ? section('04', 'Additional Application Details', custom) : '')
      + section(custom.length ? '05' : '04', 'Document Record', uploads)
      + '<div class="document-declaration"><h3>Candidate Declaration</h3><p>I hereby declare that the information submitted in this application is true and correct to the best of my knowledge. I understand that false information may lead to cancellation of candidature.</p>'
      + '<div class="document-signoff"><div>' + media(candidate.signaturePath, 'signature') + '<span>Candidate Signature</span></div>'
      + '<div class="document-issued"><span class="field-label">Record Generated</span><strong>' + escape(date(new Date().toISOString())) + '</strong></div></div></div>'
      + '<div class="document-footer"><span>Application Record | ' + escape(candidate.examCode || candidate.examName || '') + '</span><strong>' + escape(candidate.registrationId) + '</strong></div>';
  }
  async function metadata(container, candidate) {
    try {
      const response = await fetch('/api/public/exams', { cache: 'no-store' });
      if (!response.ok) return;
      const exams = await response.json();
      const exam = Array.isArray(exams) ? exams.find(item => item.examCode === candidate.examCode) : null;
      if (!exam) return;
      const labels = new Map((exam.customFields || []).map(item => [item.key, item.label]));
      container.querySelectorAll('[data-custom-label]').forEach(label => {
        if (labels.has(label.dataset.customLabel)) label.textContent = labels.get(label.dataset.customLabel);
      });
      if (exam.portalTopBannerImageUrl) {
        const url = new URL(exam.portalTopBannerImageUrl, location.origin);
        if (!['http:', 'https:'].includes(url.protocol)) return;
        const banner = container.querySelector('.document-banner');
        banner.src = url.href;
        await banner.decode();
        banner.hidden = false;
      }
    } catch (_) {
      // Keep the complete candidate record printable if optional branding is unavailable.
    }
  }
  async function loadMedia(image, candidate, request) {
    const status = image.nextElementSibling;
    try {
      const data = await request('/api/candidate/file', { registrationId: candidate.registrationId, path: image.dataset.documentFile });
      if (!/^data:image\/(png|jpeg|webp);base64,/i.test(data.dataUrl || '')) throw new Error('Image unavailable');
      image.src = data.dataUrl;
      await image.decode();
      image.hidden = false;
      status.hidden = true;
    } catch (_) {
      image.hidden = true;
      status.textContent = image.alt.replace('Candidate ', '') + ' unavailable';
      status.classList.add('media-unavailable');
    }
  }
  window.ApplicationDocument = {
    signature(candidate) {
      const keys = ['registrationId','examCode','examName','fullName','dateOfBirth','createdAt','streamCode','gender','nationality',
        'email','alternateEmail','phone','address','state','pincode','parentName','parentPhone','guardianName','guardianPhone','guardianRelation',
        'ugDetails','pgDetails','photoPath','signaturePath','photoUploaded','signatureUploaded','idCardUploaded','class10MarksheetUploaded',
        'class12MarksheetUploaded','ugDocumentUploaded','pgDocumentUploaded','customFieldValues','customFilePaths'];
      return JSON.stringify(keys.map(key => candidate[key]));
    },
    async render(container, candidate, request) {
      const generation = String(Number(container.dataset.generation || 0) + 1);
      container.dataset.generation = generation;
      container.innerHTML = markup(candidate);
      container.querySelectorAll('.document-fields th').forEach(heading => {
        const reference = document.createElement('span');
        reference.className = 'section-reference';
        reference.textContent = candidate.registrationId;
        heading.append(reference);
      });
      container.dataset.ready = 'false';
      await Promise.all([
        metadata(container, candidate),
        ...[...container.querySelectorAll('[data-document-file]')].map(image => loadMedia(image, candidate, request))
      ]);
      await document.fonts.ready;
      if (container.dataset.generation === generation) container.dataset.ready = 'true';
    }
  };
})();
