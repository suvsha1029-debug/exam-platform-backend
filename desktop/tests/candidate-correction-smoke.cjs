const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const assert = require('node:assert/strict');
const { chromium } = require('playwright');
const root = path.resolve(__dirname,'../..');
const staticRoot = path.join(root,'src/main/resources/static');
const output = path.join(root,'output/correction');
const photo = fs.readFileSync(path.join(root,'output/pdf/candidate-photo-fixture.png'));
const fixture = {registrationId:'DEMO2027001384',examCode:'DEMO27',examName:'Graduate Engineering Examination 2027',fullName:'Aarav S. Menon',dateOfBirth:'2001-08-14',email:'aarav@example.test',phone:'+91 90000 12345',nationality:'India',address:'',state:'Tamil Nadu',pincode:'600020',streamCode:'EC',createdAt:'2026-09-15T10:00:00Z',correctionsOpen:true,photoPath:'old-photo.png',photoUploaded:true,customFieldValues:{},customFilePaths:{},candidateCorrectionFields:{FULL_NAME:'true',ADDRESS:'true',PHOTO:'true',ID_CARD:'true',CLASS_10_MARKSHEET:'true',customField_proof:'true',customField_note:'true'}};
const exam = {examCode:'DEMO27',name:fixture.examName,registrationOpen:false,customFields:[{key:'proof',label:'Eligibility certificate',type:'PDF_UPLOAD',maxSizeKb:1024,correctionAllowed:true},{key:'note',label:'Additional information',type:'TEXTAREA',correctionAllowed:true}]};
let candidate = structuredClone(fixture), paths = {photo:'old-photo.png'}, submits = 0, failSubmit = false;
const savedFiles = new Map([['old-photo.png',{type:'image/png',bytes:photo}]]);
const server = http.createServer(async (req,res) => {
  try {
    const url = new URL(req.url,'http://localhost');
    const json = data => { res.setHeader('Content-Type','application/json'); res.end(JSON.stringify(data)); };
    if (url.pathname.endsWith('security-token')) return json({token:'test-token'});
    if (url.pathname === '/api/public/exams') return json([exam]);
    if (url.pathname === '/api/candidate/dashboard') return json(candidate);
    if (url.pathname === '/api/candidate/correction/files') return json(paths);
    if (url.pathname === '/api/candidate/file') {
      let body=''; for await (const chunk of req) body+=chunk;
      const file = savedFiles.get(JSON.parse(body).path);
      if (!file) {res.statusCode=404; return json({detail:'Unavailable'});}
      return json({dataUrl:`data:${file.type};base64,${file.bytes.toString('base64')}`});
    }
    if (url.pathname === '/api/candidate/correction/submit') {
      if (failSubmit) {res.statusCode=400; return json({detail:'Corrections were closed by the administrator.'});}
      assert.equal(req.headers['x-candidate-csrf'],'test-token');
      const chunks=[]; for await (const chunk of req) chunks.push(chunk);
      const request = new Request('http://localhost',{method:'POST',headers:{'Content-Type':req.headers['content-type']},body:Buffer.concat(chunks)});
      const form = await request.formData();
      Object.assign(candidate,JSON.parse(await form.get('details').text()));
      for (const [key,value] of form.entries()) {
        if (key === 'details') continue;
        if (typeof value === 'string') candidate.customFieldValues[key.replace('customField_','')] = value;
        else {
          const stored = `saved-${submits}-${key}`; paths[key] = stored;
          savedFiles.set(stored,{type:value.type,bytes:Buffer.from(await value.arrayBuffer())});
          if (key.startsWith('customField_')) candidate.customFilePaths[key.replace('customField_','')] = stored;
          else {candidate[key+'Path']=stored;candidate[key+'Uploaded']=true;}
        }
      }
      submits++; return json(candidate);
    }
    const file = path.resolve(staticRoot,'.'+(url.pathname==='/'?'/candidate-correction.html':url.pathname));
    if (!file.startsWith(staticRoot+path.sep) || !fs.existsSync(file)) {res.writeHead(404);res.end();return;}
    res.setHeader('Content-Type',({'.html':'text/html','.css':'text/css','.js':'application/javascript'})[path.extname(file)] || 'application/octet-stream');
    fs.createReadStream(file).pipe(res);
  } catch(error) {res.statusCode=500;res.end(JSON.stringify({detail:error.message}));}
});
async function run(origin) {
  const browser = await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});
  fs.mkdirSync(output,{recursive:true});
  try {
    for (const [label,width,height] of [['desktop',1365,950],['mobile',390,844]]) {
      candidate=structuredClone(fixture); paths={photo:'old-photo.png'}; submits=0; failSubmit=false;
      const page = await browser.newPage({viewport:{width,height}}); const errors=[]; page.on('pageerror',error=>errors.push(error.message));
      await page.addInitScript(data=>sessionStorage.setItem('candidateDashboardData',JSON.stringify(data)),fixture);
      await page.goto(origin); await page.locator('#edit-fullName').waitFor();
      assert.equal(await page.locator('#edit-email').count(),0);
      await page.locator('#edit-address').fill('24 Research Park Road, Adyar, Chennai');
      await page.locator('#edit-customField_note').fill('Updated supporting certificate attached for verification.');
      await page.locator('#edit-photo').setInputFiles({name:'updated-photo.png',mimeType:'image/png',buffer:photo});
      await page.locator('#edit-customField_proof').setInputFiles({name:'eligibility.pdf',mimeType:'application/pdf',buffer:Buffer.from('%PDF-1.4\nTest fixture for UI selection')});
      await page.screenshot({path:path.join(output,`correction-edit-${label}.png`),fullPage:true});
      await page.getByRole('button',{name:'Review application',exact:true}).click();
      await page.locator('#reviewPanel:not([hidden])').waitFor();
      assert.equal(submits,0); assert(await page.locator('#finalSubmit').isDisabled());
      assert((await page.locator('#applicationReview').innerText()).includes('24 Research Park Road'));
      await page.locator('.document-entry').filter({hasText:'Eligibility certificate'}).getByRole('button').click();
      await page.getByRole('link',{name:'Open full document'}).waitFor();
      await page.screenshot({path:path.join(output,`correction-review-${label}.png`),fullPage:true});
      await page.locator('#backEdit').click(); await page.locator('#edit-address').fill('25 Research Park Road, Chennai');
      await page.getByRole('button',{name:'Review application',exact:true}).click(); await page.locator('#reviewPanel:not([hidden])').waitFor();
      await page.locator('#confirmDetails').check(); failSubmit=true; await page.locator('#finalSubmit').click();
      await page.waitForFunction(()=>document.getElementById('result').textContent.includes('closed by')); assert.equal(submits,0);
      failSubmit=false; await page.locator('#finalSubmit').click(); await page.waitForFunction(()=>document.getElementById('result').textContent.includes('submitted successfully'));
      assert.equal(submits,1); assert.equal(candidate.address,'25 Research Park Road, Chennai');
      assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth <= innerWidth),true);
      await page.screenshot({path:path.join(output,`correction-submitted-${label}.png`),fullPage:true});
      await page.locator('#editAgain').click(); await page.locator('#edit-fullName').waitFor();
      assert.equal(await page.locator('#edit-address').inputValue(),candidate.address);
      candidate.correctionsOpen=false; await page.reload(); await page.waitForFunction(()=>document.getElementById('result').textContent.includes('window is closed'));
      assert(await page.locator('#editPanel').isHidden()); assert.deepEqual(errors,[]); await page.close();
    }
    console.log('Correction UI: desktop/mobile edit, uploads, full review, retry, final submission, repeat corrections and closed-window checks passed.');
  } finally {await browser.close();}
}
server.listen(0,'127.0.0.1',()=>run(`http://127.0.0.1:${server.address().port}`).catch(error=>{console.error(error);process.exitCode=1;}).finally(()=>server.close()));
