import http from "k6/http";
import { check, sleep } from "k6";

export const options = {
  thresholds: {
    http_req_failed: ["rate<0.02"],
    http_req_duration: ["p(95)<500"],
  },
  scenarios: {
    auth: {
      executor: "ramping-vus",
      stages: [
        { duration: "30s", target: 20 },
        { duration: "1m", target: 20 },
        { duration: "30s", target: 0 },
      ],
    },
  },
};

const BASE_URL = __ENV.BASE_URL || "http://localhost:2000";

export default function () {
  const suffix = `${__VU}-${Date.now()}`;
  const signup = http.post(
    `${BASE_URL}/api/auth/signup`,
    JSON.stringify({
      name: `Load User ${suffix}`,
      email: `load-${suffix}@example.com`,
      password: "123456",
    }),
    { headers: { "Content-Type": "application/json", "X-Device-Name": "k6" } }
  );
  check(signup, { "signup accepted": (r) => [200, 409].includes(r.status) });
  sleep(1);
}
