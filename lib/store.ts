// The owner's files on this machine: where they live, how one is written without ever
// leaving half a file behind, and how changes to one are made one at a time.

import { mkdir, readFile, rename, writeFile } from 'node:fs/promises';
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
  await mkdir(dataDir(), { recursive: true });
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
