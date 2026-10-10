(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  const fields = [
    ['FULL_NAME','fullName','Full name'],['DATE_OF_BIRTH','dateOfBirth','Date of birth','date'],
    ['EMAIL','email','Email','email'],['ALTERNATE_EMAIL','alternateEmail','Alternate email','email'],
    ['PHONE','phone','Phone','tel'],['ADDRESS','address','Address','textarea'],['STATE','state','State / province'],
    ['PINCODE','pincode','Postal code'],['NATIONALITY','nationality','Country / nationality'],['GENDER','gender','Gender'],
    ['PARENT_NAME','parentName','Parent name'],['PARENT_PHONE','parentPhone','Parent phone','tel'],
    ['GUARDIAN_NAME','guardianName','Guardian name'],['GUARDIAN_PHONE','guardianPhone','Guardian phone','tel'],
    ['GUARDIAN_RELATION','guardianRelation','Guardian relation'],['UG_DETAILS','ugDetails','Undergraduate details','textarea'],
    ['PG_DETAILS','pgDetails','Postgraduate details','textarea']
  ];
  const uploads = [
    ['PHOTO','photo','Photograph',100,true],['SIGNATURE','signature','Signature',100,true],
    ['ID_CARD','idCard','Identity document',1024],['CLASS_10_MARKSHEET','class10Marksheet','Class 10 marksheet',1024],
    ['CLASS_12_MARKSHEET','class12Marksheet','Class 12 marksheet',1024],['UG_DETAILS','ugDocument','Undergraduate document',1024],
    ['PG_DETAILS','pgDocument','Postgraduate document',1024]
  ];
  let candidate, exam, paths = {}, descriptors = [], pending, token, busy = false;
  const localFiles = new Map(), objectUrls = [];
  const message = (text, state = '') => { $('result').textContent = text; $('result').className = state; };
  async function api(url, body, multipart = false) {
    if (!token) {
      const response = await fetch('/api/public/candidate-security-token', {cache:'no-store'});
      if (!response.ok) throw new Error('Could not verify your session. Please sign in again.');
      token = (await response.json()).token;
    }
    const response = await fetch(url, {method:'POST',cache:'no-store',headers:{'X-Candidate-CSRF':token,...(multipart ? {} : {'Content-Type':'application/json'})},body:multipart ? body : JSON.stringify(body)});
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.detail || data.message || 'Unable to complete the request. Your changes have not been submitted.');
    return data;
  }
  function step(index) {
    document.querySelectorAll('.correction-steps li').forEach((item, i) => {
      if (i === index) item.setAttribute('aria-current','step'); else item.removeAttribute('aria-current');
    });
  }
  function resetUrls() { objectUrls.splice(0).forEach(url => URL.revokeObjectURL(url)); }
  function dataUrl(file) { return new Promise((resolve,reject) => { const reader = new FileReader(); reader.onload = () => resolve(reader.result); reader.onerror = reject; reader.readAsDataURL(file); }); }
  function enabled(key) { return String(candidate.candidateCorrectionFields?.[key]) === 'true'; }
  function buildEditor() {
    $('editFields').replaceChildren(); descriptors = [];
    const required = exam.candidateRequiredFields || {};
    fields.filter(([key]) => enabled(key)).forEach(([key,name,label,type='text']) => descriptors.push({key,name,label,type,value:candidate[name] || '',required:['FULL_NAME','DATE_OF_BIRTH','EMAIL','PHONE'].includes(key) || String(required[key]) === 'true'}));
    uploads.filter(([key]) => enabled(key)).forEach(([key,name,label,maxKb,image]) => descriptors.push({key,name,label,type:'file',maxKb,accept:image ? 'image/png,image/jpeg' : 'application/pdf,image/png,image/jpeg',required:String(required[key]) === 'true' && !paths[name]}));
    (exam.customFields || []).filter(field => enabled('customField_' + field.key)).forEach(field => {
      const name = 'customField_' + field.key, file = ['IMAGE_UPLOAD','PDF_UPLOAD'].includes(field.type);
      descriptors.push({key:name,name,label:field.label,custom:field.key,type:file ? 'file' : field.type === 'TEXTAREA' ? 'textarea' : 'text',value:candidate.customFieldValues?.[field.key] || '',required:field.required && (!file || !paths[name]),maxKb:field.maxSizeKb || (field.type === 'IMAGE_UPLOAD' ? 100 : 1024),accept:field.type === 'IMAGE_UPLOAD' ? 'image/png,image/jpeg' : 'application/pdf'});
    });
    for (const field of descriptors) {
      const wrap = document.createElement('div'); wrap.className = 'correction-field';
      const label = document.createElement('label'); label.htmlFor = 'edit-' + field.name; label.textContent = field.label + (field.required ? ' *' : '');
      const input = document.createElement(field.type === 'textarea' ? 'textarea' : 'input');
      input.id = label.htmlFor; input.name = field.name; input.required = Boolean(field.required);
      if (field.type !== 'textarea') input.type = field.type;
      if (field.type === 'file') input.accept = field.accept;
      else {
        input.value = field.value;
        input.maxLength = field.custom ? 2000 : ({address:600,ugDetails:600,pgDetails:600,state:80,nationality:80,guardianRelation:80,pincode:12,gender:40,phone:32,parentPhone:32,guardianPhone:32}[field.name] || 200);
      }
      wrap.append(label,input);
      if (field.type === 'file') {
        const hint = document.createElement('small'); hint.textContent = (paths[field.name] ? 'Submitted file retained unless replaced. ' : 'Not yet uploaded. ') + 'Maximum ' + field.maxKb + ' KB.'; wrap.append(hint);
      }
      $('editFields').append(wrap);
    }
    $('editPanel').hidden = !candidate.correctionsOpen || !descriptors.length;
    if (!candidate.correctionsOpen) message('The correction window is closed. Your submitted application is unchanged.');
    else if (!descriptors.length) message('No fields are currently enabled for correction.');
  }
  async function renderReview(record, fileMap, saved = false) {
    resetUrls();
    await ApplicationDocument.render($('applicationReview'),record,async (url,body) => localFiles.has(body.path) && !saved ? {dataUrl:await dataUrl(localFiles.get(body.path))} : api(url,body));
    $('documentReview').replaceChildren();
    const all = [...uploads.map(([,name,label]) => ({name,label})),...(exam.customFields || []).filter(field => ['IMAGE_UPLOAD','PDF_UPLOAD'].includes(field.type)).map(field => ({name:'customField_' + field.key,label:field.label}))];
    for (const {name,label} of all) {
      const path = fileMap[name]; if (!path) continue;
      const row = document.createElement('div'); row.className = 'document-entry';
      const detail = document.createElement('div'); const title = document.createElement('strong'); title.textContent = label;
      const status = document.createElement('small'); status.textContent = !saved && localFiles.has(path) ? 'Replacement selected: ' + localFiles.get(path).name : 'Submitted document';
      detail.append(title,status); row.append(detail);
      const button = document.createElement('button'); button.type = 'button'; button.className = 'secondary'; button.textContent = 'View document';
      button.addEventListener('click',async () => {
        button.disabled = true;
        try {
          let file = !saved && localFiles.get(path);
          if (!file) {
            const data = await api('/api/candidate/file',{registrationId:candidate.registrationId,path});
            const match = /^data:(image\/(?:png|jpeg|webp)|application\/pdf);base64,(.+)$/.exec(data.dataUrl || '');
            if (!match) throw new Error('This document format is unavailable for preview.');
            file = new Blob([Uint8Array.from(atob(match[2]), character => character.charCodeAt(0))],{type:match[1]});
          }
          const url = URL.createObjectURL(file); objectUrls.push(url);
          if (file.type.startsWith('image/')) {
            let image = detail.querySelector('img'); if (!image) { image = document.createElement('img'); image.alt = label; detail.append(image); } image.src = url;
          }
          const link = document.createElement('a'); link.href = url; link.target = '_blank'; link.rel = 'noopener'; link.textContent = 'Open full document';
          row.append(link); button.remove();
        } catch(error) { message(error.message,'error'); button.disabled = false; }
      });
      row.append(button); $('documentReview').append(row);
    }
  }
  $('correctionForm').addEventListener('submit',async event => {
    event.preventDefault(); if (busy) return; busy = true;
    const reviewButton = event.currentTarget.querySelector('button'); reviewButton.disabled = true;
    try {
      pending = new FormData(); localFiles.clear(); const merged = structuredClone(candidate);
      const details = {registrationId:candidate.registrationId}, reviewPaths = {...paths};
      for (const field of descriptors) {
        const input = $('edit-' + field.name);
        if (field.type === 'file') {
          const file = input.files[0]; if (!file) continue;
          if (!file.size || file.size > field.maxKb * 1024 || !field.accept.split(',').includes(file.type)) throw new Error(field.label + ': choose an allowed file up to ' + field.maxKb + ' KB.');
          pending.append(field.name,file); const marker = 'pending:' + field.name; localFiles.set(marker,file); reviewPaths[field.name] = marker;
          if (field.custom) merged.customFilePaths = {...merged.customFilePaths,[field.custom]:marker};
          else { merged[field.name + 'Path'] = marker; merged[field.name + 'Uploaded'] = true; }
        } else {
          const value = input.value.trim();
          if (field.custom) { pending.append(field.name,value); merged.customFieldValues = {...merged.customFieldValues,[field.custom]:value}; }
          else { details[field.name] = value; merged[field.name] = value; }
        }
      }
      pending.append('details',new Blob([JSON.stringify(details)],{type:'application/json'}));
      message('Preparing application review...'); await renderReview(merged,reviewPaths);
      $('editPanel').hidden = true; $('reviewPanel').hidden = false; $('confirmation').hidden = false; $('successActions').hidden = true;
      $('reviewTitle').textContent = 'Review application'; $('confirmDetails').checked = false; $('finalSubmit').disabled = true; step(1); message('Changes are not saved until final submission.'); window.scrollTo(0,0);
    } catch(error) { pending = null; message(error.message,'error'); }
    finally { busy = false; reviewButton.disabled = false; }
  });
  $('confirmDetails').addEventListener('change',() => { $('finalSubmit').disabled = !$('confirmDetails').checked || busy; });
  $('backEdit').addEventListener('click',() => { if (busy) return; $('reviewPanel').hidden = true; $('editPanel').hidden = false; step(0); message(''); });
  $('finalSubmit').addEventListener('click',async () => {
    if (busy || !$('confirmDetails').checked || !pending) return;
    busy = true; $('finalSubmit').disabled = true; $('backEdit').disabled = true; message('Submitting corrections...');
    let committed = false;
    try {
      candidate = await api('/api/candidate/correction/submit',pending,true); committed = true;
      sessionStorage.setItem('candidateDashboardData',JSON.stringify(candidate)); pending = null; localFiles.clear();
      $('confirmation').hidden = true; $('successActions').hidden = false; $('reviewTitle').textContent = 'Submitted application'; step(2);
      paths = await api('/api/candidate/correction/files',{registrationId:candidate.registrationId});
      await renderReview(candidate,paths,true); message('Corrections submitted successfully.','success');
    } catch(error) { message(committed ? 'Corrections were saved. Open your dashboard to reload the submitted application.' : error.message,committed ? 'success' : 'error'); }
    finally { busy = false; $('finalSubmit').disabled = !$('confirmDetails').checked; $('backEdit').disabled = false; }
  });
  $('editAgain').addEventListener('click',() => location.reload());
  window.addEventListener('beforeunload',event => { if (pending) { event.preventDefault(); event.returnValue = ''; } });
  async function init() {
    try {
      const stored = JSON.parse(sessionStorage.getItem('candidateDashboardData') || 'null');
      if (!stored?.registrationId) throw new Error('Please sign in to access your application.');
      candidate = await api('/api/candidate/dashboard',{registrationId:stored.registrationId});
      const response = await fetch('/api/public/exams',{cache:'no-store'}); if (!response.ok) throw new Error('Unable to load correction settings.');
      const exams = await response.json(); exam = exams.find(item => item.examCode === candidate.examCode);
      if (!exam) throw new Error('Exam configuration is unavailable.');
      paths = await api('/api/candidate/correction/files',{registrationId:candidate.registrationId});
      $('candidateTitle').textContent = candidate.fullName; $('candidateMeta').textContent = candidate.registrationId + ' | ' + candidate.examName;
      buildEditor();
    } catch(error) { message(error.message,'error'); }
  }
  init();
})();
