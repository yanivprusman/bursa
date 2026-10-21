// The owner's files on this machine: where they live, how one is written without ever
// leaving half a file behind, and how changes to one are made one at a time.

import { mkdir, open, readFile, rename, rm, stat, writeFile } from 'node:fs/promises';
import path from 'node:path';

/** A failure a client caused (or should hear about), with the HTTP status to answer with. */
export class StoreError extends Error {
  readonly status: number;
  // Spelled out rather than a parameter property, so `node --test` can run this file as it is.
  constructor(message: string, status = 400) {
    super(message);
    this.name = 'StoreError';
    this.status = status;
  }
}

/** Dev and prod keep separate files, so an experiment in dev cannot touch the real ones. */
export function dataDir(): string {
  if (process.env.BURSA_DATA_DIR) return process.env.BURSA_DATA_DIR;
  const root = process.env.AUTOMATE_LINUX_DIR;
  if (!root) throw new StoreError('AUTOMATE_LINUX_DIR is not set, so there is nowhere to keep the data', 500);
  return path.join(root, 'data', 'bursa', process.env.NODE_ENV === 'production' ? 'prod' : 'dev');
}

export const dataFile = (name: string) => path.join(dataDir(), name);

/** The file's text, or null when it does not exist yet. */
export async function readText(name: string): Promise<string | null> {
  try {
    return await readFile(dataFile(name), 'utf8');
  } catch (e) {
    if ((e as NodeJS.ErrnoException).code === 'ENOENT') return null;
    throw e;
  }
}

/** Write through a temporary file and rename, so a crash leaves the old file or the new one. */
export async function writeJson(name: string, value: unknown): Promise<void> {
  await mkdir(path.dirname(dataFile(name)), { recursive: true });
  const tmp = `${dataFile(name)}.${process.pid}.tmp`;
  await writeFile(tmp, JSON.stringify(value, null, 2) + '\n', { mode: 0o600 });
  await rename(tmp, dataFile(name));
}

/** Run changes one after another: two requests must not both read revision 7 and both write 8. */
export function serial() {
  let queue: Promise<unknown> = Promise.resolve();
  return function inTurn<T>(fn: () => Promise<T>): Promise<T> {
    const run = queue.then(fn, fn);
    queue = run.catch(() => undefined);
    return run;
  };
}

// ── one writer across processes ─────────────────────────────────────────────
//
// `serial()` orders the changes inside one process. The account is also written by the
// trader (scripts/trader.mts), a separate process, so a change to it additionally holds
// `<file>.lock` — created with O_EXCL, so exactly one process can hold it. A lock whose
// owner has died (a crash mid-change) is taken over: its pid is gone, or it is older than
// any change could take.

const LOCK_WAIT_MS = 20_000;
const LOCK_STALE_MS = 60_000;

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

function alive(pid: number): boolean {
  try {
    process.kill(pid, 0);
    return true;
  } catch (e) {
    return (e as NodeJS.ErrnoException).code === 'EPERM';
  }
}

async function stale(lock: string): Promise<boolean> {
  let text: string;
  try {
    text = await readFile(lock, 'utf8');
  } catch (e) {
    if ((e as NodeJS.ErrnoException).code === 'ENOENT') return false; // released meanwhile: just try again
    throw e;
  }
  const [pid, at] = text.split(' ').map(Number);
  // No content: being written this instant — or its writer died between create and write.
  if (!pid) {
    const s = await stat(lock).catch(() => null);
    return s !== null && Date.now() - s.mtimeMs > 5_000;
  }
  return !alive(pid) || Date.now() - at > LOCK_STALE_MS;
}

export async function withFileLock<T>(name: string, fn: () => Promise<T>): Promise<T> {
  const lock = `${dataFile(name)}.lock`;
  await mkdir(path.dirname(lock), { recursive: true });
  const deadline = Date.now() + LOCK_WAIT_MS;
  for (;;) {
    try {
      const fh = await open(lock, 'wx', 0o600);
      await fh.writeFile(`${process.pid} ${Date.now()}`);
      await fh.close();
      break;
    } catch (e) {
      if ((e as NodeJS.ErrnoException).code !== 'EEXIST') throw e;
      if (await stale(lock)) {
        await rm(lock, { force: true });
        continue;
      }
      if (Date.now() > deadline) throw new StoreError(`${name} is held by another process; try again`, 503);
      await sleep(25);
    }
  }
  try {
    return await fn();
  } finally {
    await rm(lock, { force: true });
  }
}
