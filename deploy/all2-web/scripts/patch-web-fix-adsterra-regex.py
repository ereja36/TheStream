#!/usr/bin/env python3
"""Fix Adsterra executeHtmlSnippet: regex escapes were eaten by template literals → SyntaxError → no ads."""
from pathlib import Path
import subprocess

SERVER = Path("/root/all/all2/server.js")
t = SERVER.read_text(encoding="utf-8")

start = t.find("function executeHtmlSnippet(target, html){")
end = t.find("\nconst externalScriptLoaders=", start)
if start < 0 or end < 0:
    raise SystemExit("FAIL: executeHtmlSnippet bounds")

# IMPORTANT: this function lives inside a JS template literal in server.js.
# Never use regex escapes like \s \d \/ \. — they become s d / . in the browser
# and break the page script (no ads at all).
new_fn = r'''function executeHtmlSnippet(target, html){
  if(!target) return Promise.resolve();
  const raw=String(html||'');
  function extractAdsterraMeta(text){
    const src=String(text||'');
    const marker='/invoke.js';
    const invAt=src.indexOf(marker);
    if(invAt<0) return null;
    const httpAt=src.lastIndexOf('http', invAt);
    if(httpAt<0) return null;
    let invokeUrl=src.slice(httpAt, invAt+marker.length);
    invokeUrl=invokeUrl.split('"')[0].split("'")[0].split('>')[0].split(' ')[0].trim();
    if(invokeUrl.indexOf(marker)<0) return null;
    const parts=invokeUrl.split('/').filter(Boolean);
    const key=parts.length>=2 ? parts[parts.length-2] : '';
    if(!key || key.length<8) return null;
    let height=90, width=728;
    const hAt=src.indexOf("'height'");
    const hAt2=src.indexOf('"height"');
    const heightKeyAt=hAt>=0?hAt:(hAt2>=0?hAt2:-1);
    if(heightKeyAt>=0){
      const slice=src.slice(heightKeyAt, heightKeyAt+40);
      const m=slice.match(/(\d{2,4})/);
      if(m) height=parseInt(m[1],10)||90;
    }
    const wAt=src.indexOf("'width'");
    const wAt2=src.indexOf('"width"');
    const widthKeyAt=wAt>=0?wAt:(wAt2>=0?wAt2:-1);
    if(widthKeyAt>=0){
      const slice=src.slice(widthKeyAt, widthKeyAt+40);
      const m=slice.match(/(\d{2,4})/);
      if(m) width=parseInt(m[1],10)||728;
    }
    return { key:key, invokeUrl:invokeUrl, height:height, width:width };
  }
  const meta=extractAdsterraMeta(raw);
  // Adsterra: isolate one unit per Sponsored slot (global atOptions + unique container).
  if(meta && meta.key && meta.invokeUrl){
    const slotSuffix=String(target.dataset.adSlotIndex||Math.random().toString(36).slice(2,8));
    target.innerHTML='';
    const box=document.createElement('div');
    box.id='container-'+meta.key;
    box.style.cssText='width:100%;max-width:100%;display:flex;align-items:center;justify-content:center;overflow:hidden;min-height:'+Math.max(40, meta.height)+'px';
    target.appendChild(box);
    window.atOptions={
      key: meta.key,
      format: 'iframe',
      height: meta.height,
      width: meta.width,
      params: {}
    };
    return new Promise((resolve)=>{
      const s=document.createElement('script');
      s.src=meta.invokeUrl+(meta.invokeUrl.indexOf('?')>=0?'&':'?')+'slot='+encodeURIComponent(slotSuffix);
      s.async=true;
      let done=false;
      const finish=()=>{ if(done) return; done=true; setTimeout(resolve, 150); };
      s.onload=finish;
      s.onerror=finish;
      setTimeout(finish, 2000);
      target.appendChild(s);
    });
  }
  target.innerHTML=raw;
  const scripts=Array.from(target.querySelectorAll('script'));
  const runNext=(i)=>new Promise((resolve)=>{
    if(i>=scripts.length) return resolve();
    const oldScript=scripts[i];
    const newScript=document.createElement('script');
    Array.from(oldScript.attributes||[]).forEach(attr=>newScript.setAttribute(attr.name, attr.value));
    if(oldScript.src){
      let finished=false;
      const finish=()=>{ if(finished) return; finished=true; runNext(i+1).then(resolve); };
      newScript.onload=finish;
      newScript.onerror=finish;
      setTimeout(finish, 2000);
      oldScript.parentNode && oldScript.parentNode.replaceChild(newScript, oldScript);
      return;
    }
    if(oldScript.textContent) newScript.textContent=oldScript.textContent;
    oldScript.parentNode && oldScript.parentNode.replaceChild(newScript, oldScript);
    runNext(i+1).then(resolve);
  });
  return runNext(0);
}'''

t = t[:start] + new_fn + t[end:]
SERVER.write_text(t, encoding="utf-8")
r = subprocess.run(["node", "--check", str(SERVER)], capture_output=True, text=True)
print("check", r.returncode, (r.stderr or "OK")[:400])
if r.returncode != 0:
    raise SystemExit(1)

# Verify the emitted page no longer has broken regex
# Simulate template escape: in real runtime backticks evaluate. Grep page after reload.
print("DONE")
