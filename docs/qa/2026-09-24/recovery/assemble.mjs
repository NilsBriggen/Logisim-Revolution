import fs from 'node:fs';
import path from 'node:path';
const scratch='/tmp/logisim-qa-recovery-20260924';
const dest='/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24';
const read=p=>JSON.parse(fs.readFileSync(p,'utf8'));
const candidates=[];
for(const name of fs.readdirSync(scratch+'/reports').filter(n=>/^inspect-.*\.json$/.test(n))){
  const r=read(scratch+'/reports/'+name);
  candidates.push(...r.findings.map(f=>({...f,origin:name})));
}
const originalVerifier=read(scratch+'/reports/verify-codeaudit.json');
candidates.push(...originalVerifier.newFindings.map(f=>({...f,origin:'verify-codeaudit.json'})));
const reviews=['foundations','editing','dialogs','remaining','components'];
const decisionMap=new Map(); const newFindings=[]; const reviewerMetadata=[]; const corroborations=[];
for(const name of reviews){
  const r=read(scratch+'/'+name+'.json');
  for(const d of r.dispositions||[]) decisionMap.set(d.id,{...d,reviewer:name});
  newFindings.push(...(r.newFindings||[]).map(f=>({...f,reviewer:name})));
  corroborations.push(...(r.relatedFindings||[]).map(f=>({...f,reviewer:name})));
  reviewerMetadata.push({name,tested:r.tested,notTested:r.notTested,coverage:r.coverage});
  fs.copyFileSync(scratch+'/'+name+'.json',dest+'/reviewers/'+name+'.json');
  if(fs.existsSync(scratch+'/'+name+'.md'))fs.copyFileSync(scratch+'/'+name+'.md',dest+'/reviewers/'+name+'.md');
}
for(const d of read(scratch+'/lead-decisions.json').dispositions)decisionMap.set(d.id,{...d,reviewer:'lead'});
newFindings.push(...read(scratch+'/lead-new-findings.json').map(f=>({...f,reviewer:'lead'})));
const iconDuplicate=newFindings.find(f=>f.id==='qa-new-01');
iconDuplicate.status='duplicate';iconDuplicate.canonicalId='codeaudit-v-10';
iconDuplicate.reason='Independent runtime corroboration of the recovered cached-icon mutation finding, not a second bug.';
for(const id of ['editing-harness-01','dialogs-new-01']){
  const f=newFindings.find(x=>x.id===id);
  if(f){f.status='duplicate';f.canonicalId='qa-new-06';decisionMap.set(id,{...decisionMap.get(id),id,status:'duplicate',canonicalId:'qa-new-06',reason:f.reason,evidence:f.evidence,reviewer:f.reviewer});}
}
const cancelExtension=newFindings.find(f=>f.id==='editing-new-01');
if(cancelExtension){cancelExtension.status='duplicate';cancelExtension.canonicalId='inspector-19';decisionMap.set(cancelExtension.id,{...decisionMap.get(cancelExtension.id),id:cancelExtension.id,status:'duplicate',canonicalId:'inspector-19',reason:cancelExtension.reason,evidence:cancelExtension.evidence,reviewer:'editing'});}
const missing=candidates.filter(f=>!decisionMap.has(f.id)).map(f=>f.id);
if(missing.length)throw new Error('Missing decisions: '+missing.join(', '));
const wsNames={W0:'QA isolation and provenance',W1:'Data integrity and broken actions',W2:'Scale, theme and lifecycle',W3:'Picker, shell and navigation',W4:'Property editing',W5:'Canvas, component rendering and output',W6:'Simulation and docked tools',W7:'Analysis, settings and secondary editors',W8:'Keyboard access and visual completion'};
function choose(f){
  if(f.workstream && wsNames[f.workstream])return f.workstream;
  if(['shell-01','shell-31','analyzer-01','components-13','components-22'].includes(f.id))return 'W1';
  if(f.id==='remaining-hdl-01')return 'W1';
  if(f.id==='dialogs-17')return 'W3';
  if(f.id==='dialogs-02')return 'W2';
  if(f.id.startsWith('palette-'))return 'W3';
  if(f.id.startsWith('inspector-'))return 'W4';
  if(f.id.startsWith('components-'))return 'W5';
  if(f.id.startsWith('analyzer-')||f.id.startsWith('dialogs-'))return 'W7';
  if(f.id.startsWith('shell-'))return ['shell-02','shell-05'].includes(f.id)?'W2':'W3';
  if(f.id.startsWith('codeaudit-')){
    if(['codeaudit-01','codeaudit-18','codeaudit-20','codeaudit-28','codeaudit-36','codeaudit-v-02','codeaudit-v-03','codeaudit-v-12','codeaudit-v-13'].includes(f.id))return 'W3';
    if(['codeaudit-10','codeaudit-11','codeaudit-12','codeaudit-13','codeaudit-21','codeaudit-22','codeaudit-30','codeaudit-v-06','codeaudit-v-07','codeaudit-v-08','codeaudit-v-15'].includes(f.id))return 'W7';
    if(['codeaudit-14','codeaudit-15','codeaudit-35','codeaudit-v-11'].includes(f.id))return 'W5';
    if(['codeaudit-19','codeaudit-29','codeaudit-32'].includes(f.id))return 'W8';
    if(f.id==='codeaudit-v-05')return 'W4';
    if(f.id==='codeaudit-v-14')return 'W6';
    return 'W2';
  }
  const s=(f.title+' '+(f.surface||'')).toLowerCase();
  if(/discard|save.*cancel|cancel.*save|lost.*edit|exception|classcast|cancel.*mutat/.test(s))return 'W1';
  if(/theme|scal|font|contrast|listener|icon.*mutat/.test(s))return 'W2';
  if(/simulat|timing|waveform|chronogram|test.vector|clock|tick|drawer/.test(s))return 'W6';
  if(/hdl|fpga|analy|hex|dialog|preference|assembler/.test(s))return 'W7';
  if(/attribute|propert|inspector/.test(s))return 'W4';
  if(/canvas|component|export|wire|gate/.test(s))return 'W5';
  if(/keyboard|shortcut|focus|tab.*key/.test(s))return 'W8';
  return 'W3';
}
const findings=candidates.map(f=>({id:f.id,title:f.title,workstream:choose(f),originalClaim:f,review:decisionMap.get(f.id)}));
for(const f of newFindings){
  if(!f.id)throw new Error('New finding has no id');
  findings.push({id:f.id,title:f.title,workstream:choose(f),originalClaim:f,review:{status:f.status||'unverified',reviewer:f.reviewer,reason:f.reason||f.observed||f.description,priority:f.priority||f.severity,evidence:f.evidence,canonicalId:f.canonicalId,...decisionMap.get(f.id)}});
}
const byId=new Map(findings.map(f=>[f.id,f]));
if(byId.size!==findings.length)throw new Error('Duplicate finding IDs');
for(const f of findings){
  const canonical=f.review.canonicalId||f.review.duplicateOf||f.review.canonical;
  if(canonical&&byId.has(canonical)){f.review.canonicalId=canonical;f.workstream=byId.get(canonical).workstream;}
}
for(const c of corroborations){
  const target=byId.get(c.canonicalId);
  if(target)(target.corroborations??=[]).push(c);
}
const evidenceMap={};
function collect(value){
  if(typeof value==='string' && value.startsWith(scratch+'/') && /\.(png|log)$/.test(value) && fs.existsSync(value)){
    const rel='evidence/'+path.relative(scratch,value);
    const target=dest+'/'+rel;fs.mkdirSync(path.dirname(target),{recursive:true});fs.copyFileSync(value,target);evidenceMap[value]=rel;
  }else if(Array.isArray(value)){value.forEach(collect);}else if(value && typeof value==='object'){Object.values(value).forEach(collect);}
}
collect(findings);collect(corroborations);
for(const name of reviews){
  const p=scratch+'/'+name+'.md';
  if(!fs.existsSync(p))continue;
  for(const match of fs.readFileSync(p,'utf8').matchAll(/\]\((\/tmp\/logisim-qa-recovery-20260924\/[^)]+)\)/g))collect(match[1]);
}
for(const name of reviews){
  const p=dest+'/reviewers/'+name+'.md';
  if(!fs.existsSync(p))continue;
  let md=fs.readFileSync(p,'utf8');
  for(const [source,rel] of Object.entries(evidenceMap))md=md.replaceAll(source,dest+'/'+rel);
  md=md.replaceAll(scratch+'/'+name+'.json',dest+'/reviewers/'+name+'.json');
  fs.writeFileSync(p,md);
}
const stats={recoveredObservations:candidates.length,originalInspectionObservations:352,recoveredVerifierAdditions:15,newObservations:newFindings.length,totalTracked:findings.length,byStatus:{},byWorkstream:{}};
for(const f of findings){stats.byStatus[f.review.status]=(stats.byStatus[f.review.status]||0)+1;stats.byWorkstream[f.workstream]=(stats.byWorkstream[f.workstream]||0)+1;}
fs.writeFileSync(dest+'/findings.json',JSON.stringify({revision:'0a6226664',date:'2026-09-24',countsAreObservationsNotUniqueDefects:true,stats,reviewerMetadata,evidenceMap,corroborations,findings},null,2)+'\n');
fs.writeFileSync(dest+'/stats.json',JSON.stringify(stats,null,2)+'\n');
const esc=s=>String(s||'').replaceAll('|','\\|').replaceAll('\n',' ');
let md='# QA finding checklist\n\nEvery recovered observation has a disposition. Counts include duplicated symptoms, recommendations and unresolved hypotheses; they are not a defect count. The original claims and full evidence are retained in [findings.json](findings.json). Workstream descriptions and acceptance criteria are in [the plan](README.md). No item is marked fixed by this audit.\n\n';
for(const [id,name] of Object.entries(wsNames)){
  md+='## '+id+' — '+name+'\n\n| ID | Disposition | Observation | Review note |\n| --- | --- | --- | --- |\n';
  for(const f of findings.filter(x=>x.workstream===id))md+='| '+esc(f.id)+' | '+esc(f.review.status)+' | '+esc(f.title)+' | '+esc((f.review.canonicalId?'See '+f.review.canonicalId+'. ':'')+(f.review.reason||f.review.note||''))+' |\n';
  md+='\n';
}
fs.writeFileSync(dest+'/checklist.md',md);
console.log(JSON.stringify(stats,null,2));
