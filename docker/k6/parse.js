import http from 'k6/http';
const BASE = __ENV.BASE_URL || 'http://app:8080';
const audio = open('./audio.bin', 'b');
export const options = {
  scenarios: { parse: {
    executor: 'constant-arrival-rate',
    rate: 15, timeUnit: '1s', duration: '3m',
    preAllocatedVUs: 50, maxVUs: 200,
  }},
};
export default function () {
  http.post(`${BASE}/api/trips/parse`, { audio: http.file(audio, 'v.mp3', 'audio/mp3') });
}
