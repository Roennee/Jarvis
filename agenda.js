/* Shared, dependency-free agenda presentation. No HTML from imported descriptions is inserted. */
'use strict';
const JarvisAgenda=(()=>{
 const zone='Europe/Zurich', date=d=>new Date(d), format=(d,options)=>new Intl.DateTimeFormat('de-CH',{timeZone:zone,...options}).format(date(d));
 function dayKey(d){const p=new Intl.DateTimeFormat('en-CA',{timeZone:zone,year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(date(d));return ['year','month','day'].map(k=>p.find(v=>v.type===k).value).join('-')}
 function days(now=new Date()){const key=dayKey(now);return Array.from({length:7},(_,i)=>{const d=new Date(key+'T12:00:00Z');d.setUTCDate(d.getUTCDate()+i);return {key:dayKey(d),date:d,label:i===0?'Heute':i===1?'Morgen':format(d,{weekday:'short'}),number:format(d,{day:'numeric'})}})}
 function lastDay(e){return dayKey(new Date(date(e.end).getTime()-1))}
 function time(e){if(e.all_day)return 'Ganztägig';return format(e.start,{hour:'2-digit',minute:'2-digit'})+'–'+format(e.end,{hour:'2-digit',minute:'2-digit'})}
 function range(e){const a=dayKey(e.start),z=lastDay(e);return a===z?time(e):time(e)+' · '+format(e.start,{day:'numeric',month:'short'})+' – '+format(new Date(date(e.end).getTime()-(e.all_day?1:0)),{day:'numeric',month:'short'})}
 function select(events,{day='',source='',query='',now=new Date()}={}){return events.filter(e=>date(e.end)>now&&(!source||e.calendar===source)&&(!query||(e.title+' '+e.calendar).toLocaleLowerCase().includes(query.toLocaleLowerCase()))&&(!day||(dayKey(e.start)<=day&&lastDay(e)>=day)))}
 function groups(events,now=new Date(),selected=''){const today=dayKey(now),map=new Map();for(const e of events){const key=selected||[today,dayKey(e.start)].sort().pop();if(!map.has(key))map.set(key,[]);map.get(key).push(e)}return [...map].sort(([a],[b])=>a.localeCompare(b))}
 function render(target,events,options={}){target.replaceChildren();const rows=select(events,options),now=options.now||new Date();
  const node=(tag,text,cls)=>{const n=document.createElement(tag);if(text)n.textContent=text;if(cls)n.className=cls;return n};
  if(!rows.length){const empty=node('div','','agenda-empty');empty.append(node('span','○'),node('h2','Zeit für Freiraum.'),node('p','Hier stehen deine nächsten Termine, sobald du Kalender abrufst oder einen Termin planst.'));target.append(empty);return 0}
  for(const [key,list] of groups(rows,now,options.day)){const section=node('section','','agenda-day'),heading=node('h2',key===dayKey(now)?'Heute':format(key+'T12:00:00Z',{weekday:'long',day:'numeric',month:'long'}));section.append(heading);
   for(const e of list){const details=node('details','','agenda-event'),summary=node('summary'),clock=node('time',e.all_day?'GANZTÄGIG':format(e.start,{hour:'2-digit',minute:'2-digit'}),'agenda-clock');clock.dateTime=e.start;
    const body=node('div','','agenda-title');body.append(node('strong',e.title||'Termin'),node('span',e.calendar||e.source,'agenda-source'));if(!e.all_day&&date(e.start)<=now)body.append(node('span','Läuft gerade','agenda-live'));summary.append(clock,body,node('span','+','agenda-expand'));details.append(summary);
    const info=node('div','','agenda-detail');info.append(node('p',range(e)));if(e.details)info.append(node('p',e.details,'agenda-description'));
    if(['lokal','Jarvis Sprache'].includes(e.source)){const a=node('a','In Kalender-App speichern','button');a.href=options.native?'jarvis-native://calendar-add?id='+encodeURIComponent(e.id):'/api/calendar/export?id='+encodeURIComponent(e.id);if(!options.native)a.download='jarvis-termin.ics';info.append(a)}details.append(info);section.append(details)}target.append(section)}return rows.length}
 return {dayKey,days,select,groups,time,range,render};
})();
if(typeof module!=='undefined')module.exports=JarvisAgenda;
