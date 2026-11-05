// 로컬 개발용 스모크 테스트. 완료 후 출력된 세션 UUID는 필요하면 개발 DB에서 정리합니다.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { sign, randomUUID } = require('node:crypto');

const base = process.env.SMOKE_BASE_URL || 'http://127.0.0.1:3002';
const key = fs.readFileSync(path.join(__dirname, '../keys/jwt-private.pem'));
const kid = JSON.parse(fs.readFileSync(path.join(__dirname, '../keys/jwt-jwks.json'))).keys[0].kid;
const b64 = value => Buffer.from(JSON.stringify(value)).toString('base64url');
function token(userId) {
  const now = Math.floor(Date.now() / 1000);
  const signed = b64({ alg: 'RS256', kid, typ: 'JWT' }) + '.' + b64({
    iss: 'barofit-identity', aud: ['barofit-api'], sub: userId,
    iat: now, exp: now + 300, role: 'USER', jti: randomUUID(),
  });
  return signed + '.' + sign('RSA-SHA256', Buffer.from(signed), key).toString('base64url');
}

const sessionId = randomUUID();
const userId = randomUUID();
const otherId = randomUUID();
let routineId;
let snapshot;
let expectedReps;
const accessToken = token(userId);
const startedAt = new Date().toISOString();
const pathName = `/api/v1/workout-sessions/${sessionId}`;
const seoulParts = Object.fromEntries(new Intl.DateTimeFormat('en-US', {
  timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit',
}).formatToParts(new Date()).map(part => [part.type, part.value]));
const date = `${seoulParts.year}-${seoulParts.month}-${seoulParts.day}`;

async function request(method, uri, body, jwt = accessToken) {
  return fetch(base + uri, { method, headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${jwt}` },
    body: body ? JSON.stringify(body) : undefined });
}

async function websocketAuth() {
  const ws = new WebSocket(base.replace(/^http/, 'ws') + '/ws/coaching');
  try {
    const ack = await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('WebSocket auth timeout')), 15000);
      ws.addEventListener('open', () => ws.send(JSON.stringify({ type: 'AUTH', accessToken, sessionId })));
      ws.addEventListener('message', event => { clearTimeout(timer); resolve(JSON.parse(event.data)); });
      ws.addEventListener('close', event => { clearTimeout(timer); reject(new Error(`WebSocket closed during auth: ${event.code}`)); }, { once: true });
      ws.addEventListener('error', reject);
    });
    assert.equal(ack.type, 'AUTH_OK');
    if (process.env.SMOKE_EXPECT_GPT_AUDIO === '1' || process.env.SMOKE_EXPECT_GPT_TEXT === '1') {
      const coaching = await new Promise((resolve, reject) => {
        const timer = setTimeout(() => reject(new Error('GPT coaching push timeout')), 30000);
        ws.addEventListener('message', event => { clearTimeout(timer); resolve(JSON.parse(event.data)); }, { once: true });
        ws.send(JSON.stringify({ type: 'COACH_EVENT', eventType: 'SET_FEEDBACK',
          feedbackCode: 'HIP_DROP', actualReps: 12 }));
      });
      assert.equal(coaching.type, 'SET_FEEDBACK');
      assert.ok(coaching.text && coaching.text.length > 5);
      assert.notEqual(coaching.text, '한 세트를 마쳤습니다. 골반이 아래로 처졌어요. 몸통을 곧게 유지해 주세요.',
        'OpenAI fell back to the CALM catalog sentence');
      if (process.env.SMOKE_EXPECT_GPT_AUDIO === '1') {
        assert.ok(Buffer.from(coaching.audioBase64 || '', 'base64').length > 100,
          'GPT feedback did not include ElevenLabs audio');
      } else {
        assert.equal(coaching.audioBase64, undefined, 'Expected text-only fallback');
      }
      console.log(`PASS: GPT coaching feedback and ${coaching.audioBase64 ? 'ElevenLabs MP3' : 'text-only TTS fallback'}`);
    }
    if (process.env.SMOKE_EXPECT_TEXT_FALLBACK === '1' || process.env.SMOKE_EXPECT_AUDIO === '1') {
      const coaching = await new Promise((resolve, reject) => {
        const timer = setTimeout(() => reject(new Error('Coaching push timeout')), 20000);
        ws.addEventListener('message', event => { clearTimeout(timer); resolve(JSON.parse(event.data)); }, { once: true });
        ws.send(JSON.stringify({ type: 'COACH_EVENT', eventType: 'WORKOUT_START' }));
      });
      assert.equal(coaching.type, 'WORKOUT_START');
      assert.ok(coaching.text);
      if (process.env.SMOKE_EXPECT_AUDIO === '1') {
        assert.ok(typeof coaching.audioBase64 === 'string' && coaching.audioBase64.length > 100,
          'ElevenLabs MP3 audioBase64 was not delivered');
        assert.ok(Buffer.from(coaching.audioBase64, 'base64').length > 100,
          'ElevenLabs MP3 payload is empty');
      }
    }
  } finally { ws.close(); }
}

(async () => {
  const templatesResponse = await request('GET', '/api/v1/routine-templates');
  assert.equal(templatesResponse.status, 200);
  const templates = await templatesResponse.json();
  assert.ok(templates.length > 0, 'No routine template is available');
  routineId = templates[0].id;
  const detailResponse = await request('GET', `/api/v1/routine-templates/${routineId}`);
  assert.equal(detailResponse.status, 200);
  snapshot = await detailResponse.json();
  const performedSets = snapshot.exercises.flatMap(exercise => exercise.sets.map(set => ({
    routineSetId: set.id,
    actualReps: set.targetReps == null ? null : set.targetReps,
    actualDurationSeconds: set.targetDurationSeconds == null ? null : set.targetDurationSeconds,
    feedbackCodes: [], errorCounts: {}, feedbackText: null,
    modelVersion: 'mobile-stream-1.0', referenceModelId: 'sample-model',
    trackingAvailablePct: 92.8, completedAt: new Date().toISOString(),
  })));
  expectedReps = performedSets.reduce((sum, set) => sum + (set.actualReps || 0), 0);
  const initial = { routineId, routineSnapshot: snapshot, status: 'ACTIVE', revision: 0,
    startedAt, endedAt: null, activeDurationSeconds: 0, sets: [] };
  assert.equal((await request('PUT', pathName, initial)).status, 200);
  assert.equal((await request('PUT', pathName, initial)).status, 200);
  assert.equal((await request('GET', pathName, undefined, token(otherId))).status, 404);
  await websocketAuth();
  const paused = { ...initial, status: 'PAUSED', revision: 1, activeDurationSeconds: 25 };
  assert.equal((await request('PUT', pathName, paused)).status, 200);
  assert.equal((await request('PUT', pathName, initial)).status, 409);
  const completed = { ...paused, status: 'COMPLETED', revision: 2,
    endedAt: new Date().toISOString(), activeDurationSeconds: 30,
    sets: performedSets };
  assert.equal((await request('PUT', pathName, completed)).status, 200);
  assert.equal((await request('PUT', pathName, completed)).status, 200);
  const loaded = await (await request('GET', pathName)).json();
  assert.equal(loaded.status, 'COMPLETED');
  assert.equal(loaded.sets.length, performedSets.length);
  assert.equal((await request('PUT', pathName, { ...completed, revision: 3 })).status, 409);
  let record;
  for (let attempt = 0; attempt < 20; attempt++) {
    const response = await request('GET', `/api/v1/workout-records/${sessionId}`);
    if (response.status === 200) { record = await response.json(); break; }
    assert.equal(response.status, 404);
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
  assert.ok(record, 'Workout completion did not reach Analytics');
  assert.equal(record.sessionId, sessionId);
  assert.equal(record.totalReps, expectedReps);
  assert.equal((await request('GET', `/api/v1/workout-records/${sessionId}`, undefined, token(otherId))).status, 404);
  const daily = await (await request('GET', `/api/v1/statistics/daily?startDate=${date}&endDate=${date}`)).json();
  assert.equal(daily.find(item => item.workoutDate === date)?.sessionCount, 1);
  const calendar = await (await request('GET', `/api/v1/calendar?year=${date.slice(0, 4)}&month=${Number(date.slice(5, 7))}`)).json();
  assert.equal(calendar.find(item => item.workoutDate === date)?.sessionCount, 1);
  const offlineId = randomUUID();
  const offlinePath = `/api/v1/workout-sessions/${offlineId}`;
  const offline = { ...completed, revision: 0, startedAt: new Date(Date.now() - 60000).toISOString(),
    endedAt: new Date().toISOString() };
  assert.equal((await request('PUT', offlinePath, offline)).status, 200);
  assert.equal((await request('PUT', offlinePath, offline)).status, 200);
  let offlineRecord;
  for (let attempt = 0; attempt < 20; attempt++) {
    const response = await request('GET', `/api/v1/workout-records/${offlineId}`);
    if (response.status === 200) { offlineRecord = await response.json(); break; }
    assert.equal(response.status, 404);
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
  assert.equal(offlineRecord?.sessionId, offlineId, 'Offline terminal upload did not reach Analytics');
  const afterOffline = await (await request('GET', `/api/v1/statistics/daily?startDate=${date}&endDate=${date}`)).json();
  assert.equal(afterOffline.find(item => item.workoutDate === date)?.sessionCount, 2);
  const otherDaily = await (await request('GET', `/api/v1/statistics/daily?startDate=${date}&endDate=${date}`, undefined, token(otherId))).json();
  assert.ok(!otherDaily.some(item => item.workoutDate === date));
  const createRoutine = await request('POST', '/api/v1/routines', { templateId: routineId });
  assert.equal(createRoutine.status, 201);
  const mine = await createRoutine.json();
  const mineFromGet = await (await request('GET', `/api/v1/routines/${mine.id}`)).json();
  const changedKeys = Object.keys(mine).filter(key => JSON.stringify(mine[key]) !== JSON.stringify(mineFromGet[key]));
  if (changedKeys.length) console.log(`Create/detail snapshot fields differ: ${changedKeys.join(', ')}`);
  const privateSessionId = randomUUID();
  const privatePath = `/api/v1/workout-sessions/${privateSessionId}`;
  const privateInitial = { ...initial, routineId: mine.id, routineSnapshot: mine };
  assert.equal((await request('PUT', privatePath, privateInitial, token(otherId))).status, 404);
  assert.equal((await request('PUT', privatePath, privateInitial)).status, 200);
  assert.equal((await request('DELETE', `/api/v1/routines/${mine.id}`)).status, 204);
  assert.equal((await request('PUT', privatePath, { ...privateInitial, revision: 1,
    status: 'ABANDONED', endedAt: new Date().toISOString() })).status, 200);
  console.log(`PASS: template and personal routine ownership, offline upload, idempotency, Analytics, WebSocket JWT. Sessions ${sessionId}, ${offlineId}, ${privateSessionId}`);
})().catch(error => { console.error(error); process.exitCode = 1; });
