import { rmSync } from "node:fs";
import { resolve } from "node:path";

const nextDir = resolve(process.cwd(), ".next");

try {
  rmSync(nextDir, { recursive: true, force: true });
} catch (error) {
  console.warn(`Could not clear ${nextDir}: ${error instanceof Error ? error.message : String(error)}`);
}
