// 로컬 개발용: keys/의 테스트 키로 JWT를 서명합니다. 실제 소셜 로그인 검증은 아닙니다.
const assert=require('node:assert/strict');
const fs=require('node:fs'); const {sign,randomUUID}=require('node:crypto');
const base=process.env.SMOKE_BASE_URL || 'http://localhost:3002';
const key=fs.readFileSync(require('node:path').join(__dirname,'../keys/jwt-private.pem'));
const kid=JSON.parse(fs.readFileSync(require('node:path').join(__dirname,'../keys/jwt-jwks.json'))).keys[0].kid;
const b64=x=>Buffer.from(JSON.stringify(x)).toString('base64url');
function token(){const now=Math.floor(Date.now()/1000);const msg=b64({alg:'RS256',kid,typ:'JWT'})+'.'+b64({iss:'barofit-identity',aud:['barofit-api'],sub:randomUUID(),iat:now,exp:now+300,role:'USER',jti:randomUUID()});return msg+'.'+sign('RSA-SHA256',Buffer.from(msg),key).toString('base64url');}
const own=token(),other=token();
async function call(path,method='GET',body,auth){return fetch(base+path,{method,headers:{'Content-Type':'application/json',...(auth?{Authorization:'Bearer '+auth}:{})},body:body?JSON.stringify(body):undefined});}
(async()=>{
 assert.equal((await call('/actuator/health')).status,200);
 assert.equal((await call('/identity/actuator/health')).status,200);
 const identityDoc=await (await call('/identity/v3/api-docs')).json();
 assert.ok(identityDoc.paths['/api/v1/auth/social-login/code']);
 assert.equal(identityDoc.paths['/api/v1/routines'],undefined);
 const identityBase=process.env.SMOKE_IDENTITY_URL || 'http://localhost:3004';
 const routineBase=process.env.SMOKE_ROUTINE_URL || 'http://localhost:3005';
 assert.equal((await fetch(identityBase+'/api/v1/exercises')).status,404);
 assert.equal((await fetch(routineBase+'/.well-known/jwks.json')).status,404);
 assert.equal((await call('/.well-known/jwks.json')).status,200);

 const docs=await (await call('/v3/api-docs')).json();assert.ok(docs.paths['/api/v1/routines']);assert.equal(docs.paths['/api/v1/auth/social-login/code'],undefined);
 const templates=await (await call('/api/v1/routine-templates')).json();assert.equal(templates.length,9);
 assert.equal((await call('/api/v1/routines')).status,401);
 const template=templates.find(t=>t.category==='FULL_BODY'&&t.difficulty==='BEGINNER');
 const plank=template.exercises.find(e=>e.exerciseId==='plank');
 const created=await call('/api/v1/routines','POST',{templateId:template.id,exercises:[{templateExerciseId:plank.id,setCount:3,targetDurationSeconds:40}]},own);
 assert.equal(created.status,201);const saved=await created.json();const path='/api/v1/routines/'+saved.id;
 try{
  assert.equal(saved.exercises.length,3);const item=saved.exercises.find(e=>e.exerciseId==='plank');assert.equal(item.sets.length,3);assert.equal(item.sets[0].targetDurationSeconds,40);assert.ok(item.introTtsText);
  assert.equal((await call(path,'GET',undefined,other)).status,404);
  const changed=await call(path,'PATCH',{exercises:[{routineExerciseId:item.id,targetDurationSeconds:45}]},own);assert.equal(changed.status,200);
  const loaded=await (await call(path,'GET',undefined,own)).json();assert.equal(loaded.exercises.find(e=>e.id===item.id).sets[2].targetDurationSeconds,45);
  assert.equal((await call(path,'PATCH',{name:'forbidden'},own)).status,400);
  const list=await (await call('/api/v1/routines','GET',undefined,own)).json();assert.ok(list.content.some(r=>r.id===saved.id));
 }finally{assert.equal((await call(path,'DELETE',undefined,own)).status,204);}
 assert.equal((await call(path,'GET',undefined,own)).status,404);
 const cors=await fetch(base+'/api/v1/routines',{method:'OPTIONS',headers:{Origin:'http://localhost:8081','Access-Control-Request-Method':'POST','Access-Control-Request-Headers':'authorization,content-type'}});assert.equal(cors.status,200);assert.equal(cors.headers.get('access-control-allow-origin'),'http://localhost:8081');
 console.log('PASS: health, Swagger, 9 templates, JWT protection, create/read/update/delete, ownership, immutable fields, CORS. Test routine deleted. Local signed JWT; no real OAuth login.');
})().catch(e=>{console.error(e.message);process.exit(1)});
