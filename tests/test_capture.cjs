const fs=require('fs'),vm=require('vm'),assert=require('node:assert/strict');
const source=fs.readFileSync('app.js','utf8');
const handler=source.slice(source.indexOf("$('record').onclick="),source.indexOf("$('audioFile').onchange="));
async function scenario(settings,{denied=false,noRecorder=false}={}){
 let stopped=0,requested,intervals=0,error='',saved=null;const nodes={};
 const stream={getTracks:()=>[{stop:()=>stopped++}],getAudioTracks:()=>[{getSettings:()=>settings}]};
 class Recorder{static isTypeSupported(){return true}constructor(){this.state='inactive';this.mimeType='audio/webm'}start(){this.state='recording'}stop(){this.state='inactive';return this.onstop()}}
 const c={navigator:{mediaDevices:{getUserMedia:async x=>{requested=x;if(denied)throw Error('denied');return stream}}},window:{MediaRecorder:noRecorder?null:Recorder},MediaRecorder:Recorder,$:id=>nodes[id]??=({value:id==='recordMode'?'protocol':'',checked:false,disabled:false,pause(){},classList:{add(){},remove(){}}}),loadMicrophones:async()=>{},stopSpeech(){},toast:x=>error=x,setAudio:blob=>saved=blob,Blob,Date,Math,String,setInterval:()=>++intervals,clearInterval(){},recorder:null,recordStream:null,recordChunks:[],recordMime:'',recordTimer:null,recordStart:0,system:{settings:{denoise:false}}};
 c.finishRecording=()=>c.recorder.stop();vm.createContext(c);vm.runInContext(handler,c);await nodes.record.onclick();
 if(noRecorder){assert.match(error,/nicht unterstützt/);assert.equal(intervals,0);assert.equal(nodes.record.disabled,false);return}
 assert.equal(requested.audio.autoGainControl.ideal,false);assert.equal(requested.audio.noiseSuppression.ideal,true);assert.equal(requested.audio.channelCount.ideal,1);
 if(denied){assert.equal(error,'denied');assert.equal(intervals,0);assert.equal(nodes.record.disabled,false);return}
 assert.equal(nodes.recordStatus.textContent.includes('nicht alle'),settings.noiseSuppression===false||settings.autoGainControl===true);
 c.recorder.ondataavailable({data:new Blob(['voice'])});await c.recorder.stop();
 assert.equal(stopped,1);assert.equal(saved.size,5);assert.equal(c.recorder.state,'inactive');assert.equal(nodes.record.disabled,false);
}
(async()=>{await scenario({noiseSuppression:true,autoGainControl:false});await scenario({});await scenario({noiseSuppression:false,autoGainControl:true});await scenario({},{denied:true});await scenario({},{noRecorder:true});console.log('Capture: supported/ignored constraints, permission denial, unsupported recorder, original data and stop cleanup passed.')})().catch(e=>{console.error(e);process.exit(1)})
