import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';
import { Counter, Trend } from 'k6/metrics';

// Fixture format: [{"account":"load_001","password":"...","accessToken":"..."}].
// Provision dedicated test users beforehand; this script never registers production accounts.
const users = new SharedArray('users', () => JSON.parse(open(__ENV.M01_USERS_FILE)));
if (!users.length) throw new Error('M01_USERS_FILE must contain at least one test user');
const mode = __ENV.MODE || 'me';
if (!['me', 'login'].includes(mode)) throw new Error('MODE must be me or login');
const base = (__ENV.BASE_URL || 'http://127.0.0.1:8080/api/v1').replace(/\/$/, '');
const rejected = new Counter('admission_rejected');
const successful = new Counter('successful_requests');
const rateLimited = new Counter('responses_429');
const unavailable = new Counter('responses_503');
const transportErrors = new Counter('transport_errors');
const successDuration = new Trend('successful_request_duration', true);
const scenario = __ENV.BURST_VUS
  ? {
      executor: 'per-vu-iterations',
      vus: Number(__ENV.BURST_VUS),
      iterations: 1,
      maxDuration: __ENV.DURATION || '30s',
    }
  : {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 20),
      timeUnit: '1s',
      duration: __ENV.DURATION || '60s',
      preAllocatedVUs: Number(__ENV.VUS || 50),
      maxVUs: Number(__ENV.MAX_VUS || 200),
    };
export const options = {
  scenarios: { auth: scenario },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: [`p(95)<${__ENV.P95_MS || 1000}`],
    checks: ['rate>0.99'],
    successful_request_duration: [`p(95)<${__ENV.P95_MS || 1000}`],
    dropped_iterations: ['count==0'],
  },
};

export default function () {
  const user = users[(__VU - 1) % users.length];
  const response = mode === 'login'
    ? http.post(`${base}/auth/login`, JSON.stringify({ account: user.account, password: user.password }), {
        headers: { 'Content-Type': 'application/json' }, timeout: '10s',
      })
    : http.get(`${base}/users/me`, {
        headers: { Authorization: `Bearer ${user.accessToken}` }, timeout: '10s',
      });
  rejected.add(response.status === 429 || response.status === 503 ? 1 : 0);
  successful.add(response.status === 200 ? 1 : 0);
  rateLimited.add(response.status === 429 ? 1 : 0);
  unavailable.add(response.status === 503 ? 1 : 0);
  transportErrors.add(response.status === 0 ? 1 : 0);
  if (response.status === 200) successDuration.add(response.timings.duration);
  check(response, {
    'HTTP 200': r => r.status === 200,
    'expected authenticated user': r => {
      try {
        const body = r.json();
        return body.code === 'OK'
          && (mode === 'login' ? body.data.user.account : body.data.account) === user.account;
      } catch (_) { return false; }
    },
  });
}
