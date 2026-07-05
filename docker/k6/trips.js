import http from 'k6/http';
import { check } from 'k6';
const BASE = __ENV.BASE_URL || 'http://app:8080';
const DEV = '00000000-0000-0000-0000-000000000001';
export const options = {
  scenarios: { trips: {
    executor: 'constant-arrival-rate',
    rate: 5, timeUnit: '1s', duration: '3m',
    preAllocatedVUs: 20, maxVUs: 100,
  }},
};
export default function () {
  const r = http.get(`${BASE}/api/trips?status=SCHEDULED&date=2026-12-31`, {
    headers: { 'X-Device-Id': DEV },
  });
  check(r, { 'status 200': (x) => x.status === 200 });
}
