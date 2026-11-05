// 로컬 개발 DB에 테스트 세션을 남깁니다. 테스트 계획은 실행 후 삭제합니다.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { sign, randomUUID } = require('node:crypto');
const base = process.env.SMOKE_BASE_URL || 'http://127.0.0.1:3002';
const key = fs.readFileSync(path.join(__dirname, '../keys/jwt-private.pem'));
const kid = JSON.parse(fs.readFileSync(path.join(__dirname, '../keys/jwt-jwks.json'))).keys[0].kid;
const b64 = v => Buffer.from(JSON.stringify(v)).toString('base64url');
function token(userId) {
  const now = Math.floor(Date.now() / 1000);
  const data = b64({ alg: 'RS256', kid, typ: 'JWT' }) + '.' + b64({
    iss: 'barofit-identity', aud: ['barofit-api'], sub: userId,
    iat: now, exp: now + 600, role: 'USER', jti: randomUUID(),
  });
  return data + '.' + sign('RSA-SHA256', Buffer.from(data), key).toString('base64url');
}
const auth = token(randomUUID()), other = token(randomUUID());
async function request(method, uri, data, jwt = auth, headers = {}) {
  return fetch(base + uri, { method, headers: { Authorization: `Bearer ${jwt}`, 'Content-Type': 'application/json', ...headers },
    body: data === undefined ? undefined : JSON.stringify(data) });
}
async function expect(method, uri, status, data, jwt = auth, headers = {}) {
  const response = await request(method, uri, data, jwt, headers);
  assert.equal(response.status, status, `${method} ${uri} status`);
  return status === 204 ? null : response.json();
}

(async () => {
  const today = Object.fromEntries(new Intl.DateTimeFormat('en-US', {
    timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit',
  }).formatToParts(new Date()).map(p => [p.type, p.value]));
  const previous = new Date(Date.UTC(Number(today.year), Number(today.month) - 2, 1));
  const year = previous.getUTCFullYear(), month = previous.getUTCMonth() + 1;
  const prefix = `${year}-${String(month).padStart(2, '0')}`;
  const completedDate = `${prefix}-10`, partialDate = `${prefix}-11`, missedDate = `${prefix}-12`;
  const templates = await expect('GET', '/api/v1/routine-templates', 200);
  const template = templates.find(t => t.exercises.length >= 3 && t.exercises.every(e => e.sets.length)
    && t.exercises.some(e => e.exerciseId === 'push-up' || e.exerciseId === 'pushup'));
  assert.ok(template, 'Expected a template with at least three exercises');
  const completedSessionId = randomUUID(), partialSessionId = randomUUID();
  function session(date, selectedExercises) {
    const startedAt = `${date}T00:00:00Z`, endedAt = `${date}T00:20:00Z`;
    const sets = selectedExercises.flatMap(e => e.sets.map(s => ({
      routineSetId: s.id, actualReps: s.targetReps == null ? null : s.targetReps,
      actualDurationSeconds: s.targetDurationSeconds == null ? null : s.targetDurationSeconds,
      feedbackCodes: [], errorCounts: { HIP_DROP: 1 }, feedbackText: null, modelVersion: 'smoke',
      referenceModelId: null, trackingAvailablePct: null, completedAt: `${date}T00:10:00Z`,
    })));
    return { routineId: template.id, routineSnapshot: template, status: 'COMPLETED', revision: 0,
      startedAt, endedAt, activeDurationSeconds: 900, restDurationSeconds: 120, sets };
  }
  try {
    await expect('PUT', `/api/v1/workout-plans/${missedDate}`, 200, { routineId: template.id });
    await expect('PUT', `/api/v1/workout-sessions/${completedSessionId}`, 200,
      session(completedDate, template.exercises));
    await expect('PUT', `/api/v1/workout-sessions/${partialSessionId}`, 200,
      session(partialDate, template.exercises.slice(0, 1)));
    const calendar = await expect('GET', `/api/v1/reports/calendar?year=${year}&month=${month}`, 200);
    assert.equal(calendar.days.find(d => d.date === completedDate).status, 'COMPLETED');
    assert.equal(calendar.days.find(d => d.date === partialDate).status, 'PARTIAL');
    assert.equal(calendar.days.find(d => d.date === missedDate).status, 'MISSED');
    const history = await expect('GET', `/api/v1/reports/history?year=${year}&month=${month}&page=0&size=2`, 200);
    assert.equal(history.content.length, 2);
    assert.equal(history.hasNext, true);
    const dateRecords = await expect('GET', `/api/v1/reports/dates/${completedDate}`, 200);
    assert.equal(dateRecords[0].sessionId, completedSessionId);
    const feedback = { difficulty: 'MODERATE', discomfortAreas: ['ARM_WRIST'] };
    await expect('PUT', `/api/v1/reports/sessions/${completedSessionId}/feedback`, 200, feedback);
    const result = await expect('GET', `/api/v1/reports/sessions/${completedSessionId}/result`, 200);
    assert.equal(result.feedback.difficulty, 'MODERATE');
    assert.ok(result.completedSets > 0);
    assert.equal(result.restSeconds, 120);
    assert.equal(result.routineScore, null);
    let scoreSum = 0, setCount = 0;
    for (let i = 0; i < template.exercises.length; i++) {
      const entry = template.exercises[i], score = 70 + (i % 3) * 10;
      scoreSum += score;
      const exercise = entry.exerciseId.replaceAll('-', '_');
      for (const set of entry.sets) {
        const setId = randomUUID();
        const payload = { schemaVersion: '2.0', type: 'set.completed', exercise, engineVersion: 'smoke', timestampMs: null,
          summary: { schemaVersion: '2.0', exercise, engineVersion: 'smoke', count: 10,
            holdSeconds: null, observedSeconds: null, gapSeconds: null,
            items: { alignment: { aggregationBasis: 'repetition', unit: 'repetitions',
              compliant: score / 10, warning: 10 - score / 10, unknown: 0, notApplicable: 0, evaluated: 10, compliancePct: score } },
            details: {} } };
        const headers = { 'X-Workout-Performed-At': `${completedDate}T00:10:00Z`, 'X-Routine-Set-Id': set.id };
        const url = `/api/v1/reports/sessions/${completedSessionId}/sets/${setId}`;
        if (setCount === 0) {
          await expect('POST', url, 400, payload, auth, { ...headers, 'X-Routine-Set-Id': randomUUID() });
          await expect('POST', url, 201, payload, auth, { 'X-Workout-Performed-At': headers['X-Workout-Performed-At'] });
        }
        await expect('POST', url, 201, payload, auth, headers);
        const retry = await expect('POST', url, 201, payload, auth, headers);
        assert.equal(retry.status, 'already_saved');
        setCount++;
        if (setCount === 1) {
          await expect('POST', `/api/v1/reports/sessions/${completedSessionId}/sets/${randomUUID()}`, 409, payload, auth, headers);
          const incomplete = await expect('GET', `/api/v1/reports/sessions/${completedSessionId}/result`, 200);
          assert.equal(incomplete.routineScore, null);
        }
      }
    }
    const scored = await expect('GET', `/api/v1/reports/sessions/${completedSessionId}/result`, 200);
    assert.ok(Math.abs(scored.routineScore - scoreSum / template.exercises.length) < 0.0001);
    assert.equal(scored.scoredExercises, template.exercises.length);
    const scores = await expect('GET', `/api/v1/reports/exercises/${template.exercises[0].exerciseId}/scores`, 200);
    assert.equal(scores[0].score, 70);
    const generated = await expect('POST', `/api/v1/reports/sessions/${completedSessionId}/generated`, 200);
    assert.equal(generated.restSeconds, 120);
    assert.equal(generated.routineScore, scored.routineScore);
    assert.equal(generated.exercises[0].errorCounts.HIP_DROP, template.exercises[0].sets.length);
    assert.ok(generated.exercises[0].strengths);
    assert.deepEqual(await expect('GET', `/api/v1/reports/sessions/${completedSessionId}/generated`, 200), generated);
    assert.deepEqual(await expect('POST', `/api/v1/reports/sessions/${completedSessionId}/generated`, 200), generated);
    await expect('GET', `/api/v1/reports/sessions/${completedSessionId}/generated`, 404, undefined, other);
    await expect('GET', `/api/v1/reports/sessions/${completedSessionId}/result`, 404, undefined, other);
    const daily = await expect('GET', `/api/v1/reports/daily?date=${completedDate}`, 200);
    assert.equal(daily.setCount, setCount);
    const posture = await expect('GET', `/api/v1/reports/sessions/${completedSessionId}/summary`, 200);
    assert.ok(posture.postureItems.length > 0);
    console.log(`PASS: report calendar, history, feedback, ownership and offline result. Sessions ${completedSessionId}, ${partialSessionId}`);
  } finally {
    await request('DELETE', `/api/v1/workout-plans/${missedDate}`);
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
