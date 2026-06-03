import http from "k6/http";
import { check, sleep } from "k6";

export const options = {
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<300"],
  },
  scenarios: {
    smoke: {
      executor: "constant-vus",
      vus: 5,
      duration: "30s",
    },
  },
};

const BASE_URL = __ENV.BASE_URL || "http://localhost:2000";

export default function () {
  const health = http.get(`${BASE_URL}/api/health`);
  check(health, { "health ok": (r) => r.status === 200 });

  const community = http.get(`${BASE_URL}/api/community/questions`);
  check(community, { "community readable": (r) => r.status === 200 });
  sleep(1);
}
