import fs from 'node:fs';
import path from 'node:path';
import readline from 'node:readline';
const base='/home/nilsb/.claude/projects/-home-nilsb-Documents-projects-logisim-revolution/aa97432b-04cd-4681-802b-fbefae8bfa3e';
const work=base+'/subagents/workflows/wf_2db45d7c-b0c';
const out='/tmp/logisim-qa-recovery-20260924';
fs.mkdirSync(out+'/reports',{recursive:true});
fs.mkdirSync(out+'/prompts',{recursive:true});
const rows=fs.readFileSync(work+'/journal.jsonl','utf8').trim().split('\n').map(x=>JSON.parse(x));
const index=[];
for(const start of rows.filter(x=>x.type==='started')) {
  const result=rows.find(x=>x.type==='result' && x.agentId===start.agentId);
  const name=start.label.replace(':','-');
  const entry={label:start.label,agentId:start.agentId,complete:!!result};
  if(result) {
    fs.writeFileSync(out+'/reports/'+name+'.json',JSON.stringify(result.result,null,2)+'\n');
    entry.findings=result.result.findings?.length || result.result.verdicts?.length || 0;
  }
  const messages=readline.createInterface({input:fs.createReadStream(work+'/agent-'+start.agentId+'.jsonl'),crlfDelay:Infinity});
  const prompts=[]; const last=[];
  for await(const line of messages) {
    let m; try {m=JSON.parse(line);}catch{continue;}
    const content=m.message?.content;
    if(m.type==='user' && typeof content==='string' && content.includes('[Workflow harness')) prompts.push(content);
    if(m.type==='assistant' && Array.isArray(content)) for(const b of content) if(b.type==='text'){last.push(b.text); if(last.length>8)last.shift();}
    if(!result && Array.isArray(content)) for(const b of content){
      if(b.type==='tool_use') last.push('TOOL '+b.name+' '+JSON.stringify(b.input).slice(0,3000));
      if(b.type==='tool_result' && typeof b.content==='string') last.push('RESULT '+b.content.slice(0,5000));
      while(last.length>35)last.shift();
    }
  }
  if(prompts.length) fs.writeFileSync(out+'/prompts/'+name+'.md',prompts.join('\n\n')+'\n');
  if(!result) fs.writeFileSync(out+'/reports/'+name+'-partial.md',last.join('\n\n---\n\n')+'\n');
  index.push(entry);
}
fs.writeFileSync(out+'/index.json',JSON.stringify(index,null,2)+'\n');
const transcript=readline.createInterface({input:fs.createReadStream(base+'.jsonl'),crlfDelay:Infinity});
let scripts=0;
for await(const line of transcript){
  if(!line.includes('visual-qa-sweep') && !line.includes('wf_2db45d7c-b0c'))continue;
  let m;try{m=JSON.parse(line);}catch{continue;}
  for(const b of Array.isArray(m.message?.content)?m.message.content:[]){
    if(b.type!=='tool_use')continue;
    const a=b.input||{};
    if(typeof a.script==='string' && a.script.includes('visual-qa-sweep')){
      fs.writeFileSync(out+'/recovered-workflow-'+(++scripts)+'.js',a.script);
    } else if(a.file_path?.includes('visual-qa-sweep') && typeof a.content==='string'){
      fs.writeFileSync(out+'/recovered-workflow-'+(++scripts)+'.js',a.content);
    } else if(a.command?.includes('visual-qa-sweep')){
      fs.writeFileSync(out+'/recovered-workflow-command-'+(++scripts)+'.txt',a.command);
    }
  }
}
console.log(JSON.stringify({out,scripts,index},null,2));
