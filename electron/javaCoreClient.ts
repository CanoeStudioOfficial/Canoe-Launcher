import { app } from "electron";
import { spawn, type ChildProcessWithoutNullStreams } from "node:child_process";
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import readline from "node:readline";
import type { LaunchJobEvent } from "../src/types/launcher";

interface CoreResponse<T> {
  type: "response";
  id: string;
  ok: boolean;
  payload: T;
  error?: string;
}

interface CoreEvent {
  type: "event";
  event: string;
  payload: unknown;
}

interface PendingRequest<T> {
  resolve: (value: T) => void;
  reject: (error: Error) => void;
  timeout: NodeJS.Timeout;
}

export class JavaCoreClient {
  private process: ChildProcessWithoutNullStreams | null = null;
  private pending = new Map<string, PendingRequest<unknown>>();

  constructor(private readonly onJobEvent: (event: LaunchJobEvent) => void) {}

  async request<T>(command: string, payload: Record<string, unknown> = {}, timeoutMs = 120_000): Promise<T> {
    this.ensureStarted();

    const id = crypto.randomUUID();
    const message = JSON.stringify({
      id,
      type: "request",
      command,
      payload,
    });

    return new Promise<T>((resolve, reject) => {
      const timeout = setTimeout(() => {
        this.pending.delete(id);
        reject(new Error(`Java core request timed out: ${command}`));
      }, timeoutMs);

      this.pending.set(id, {
        resolve: resolve as (value: unknown) => void,
        reject,
        timeout,
      });

      this.process?.stdin.write(`${message}\n`, "utf8", (error) => {
        if (error) {
          clearTimeout(timeout);
          this.pending.delete(id);
          reject(error);
        }
      });
    });
  }

  private ensureStarted() {
    if (this.process && !this.process.killed) {
      return;
    }

    const jarPath = resolveCoreJarPath();
    if (!fs.existsSync(jarPath)) {
      throw new Error(`Canoe Java core jar not found: ${jarPath}`);
    }

    this.process = spawn("java", ["-jar", jarPath], {
      cwd: process.cwd(),
      stdio: ["pipe", "pipe", "pipe"],
      windowsHide: true,
    });

    const reader = readline.createInterface({ input: this.process.stdout });
    reader.on("line", (line) => this.handleLine(line));

    this.process.stderr.on("data", (chunk) => {
      console.warn(`[canoe-core] ${chunk.toString("utf8").trim()}`);
    });

    this.process.on("exit", (code) => {
      const error = new Error(`Canoe Java core exited with code ${code}`);
      for (const pending of this.pending.values()) {
        clearTimeout(pending.timeout);
        pending.reject(error);
      }
      this.pending.clear();
      this.process = null;
    });
  }

  private handleLine(line: string) {
    if (!line.trim()) {
      return;
    }

    let message: CoreResponse<unknown> | CoreEvent;
    try {
      message = JSON.parse(line);
    } catch (error) {
      console.warn(`[canoe-core] Ignoring non-JSON output: ${line}`, error);
      return;
    }

    if (message.type === "event") {
      if (message.event === "job") {
        this.onJobEvent(message.payload as LaunchJobEvent);
      }
      return;
    }

    const pending = this.pending.get(message.id);
    if (!pending) {
      return;
    }

    clearTimeout(pending.timeout);
    this.pending.delete(message.id);

    if (message.ok) {
      pending.resolve(message.payload);
    } else {
      pending.reject(new Error(message.error ?? "Java core request failed"));
    }
  }
}

function resolveCoreJarPath() {
  if (app.isPackaged) {
    return path.join(process.resourcesPath, "launcher-core", "canoe-launcher-core.jar");
  }

  return path.join(process.cwd(), "launcher-core", "build", "libs", "canoe-launcher-core.jar");
}
