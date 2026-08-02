import { spawnSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { Readable } from "node:stream";
import { finished } from "node:stream/promises";

const gradleVersion = "9.5.1";
const root = process.cwd();
const cacheRoot = path.join(root, ".gradle", "codex");
const distRoot = path.join(cacheRoot, `gradle-${gradleVersion}`);
const gradleHome = path.join(distRoot, `gradle-${gradleVersion}`);
const gradleExecutable = process.platform === "win32"
  ? path.join(gradleHome, "bin", "gradle.bat")
  : path.join(gradleHome, "bin", "gradle");
const zipPath = path.join(cacheRoot, `gradle-${gradleVersion}-bin.zip`);
const distributionUrl = `https://services.gradle.org/distributions/gradle-${gradleVersion}-bin.zip`;

const requestedArgs = process.argv.slice(2);
const gradleArgs = requestedArgs.length > 0 ? requestedArgs : ["build"];
const executable = await resolveGradleExecutable();
const result = runGradle(executable, gradleArgs);

if (result.error) {
  throw result.error;
}
process.exit(result.status ?? 1);

async function resolveGradleExecutable() {
  const wrapper = path.join(root, process.platform === "win32" ? "gradlew.bat" : "gradlew");
  if (fs.existsSync(wrapper)) {
    if (process.platform !== "win32") {
      fs.chmodSync(wrapper, 0o755);
    }
    return wrapper;
  }

  const globalGradle = spawnSync("gradle", ["--version"], {
    encoding: "utf8",
    shell: process.platform === "win32",
  });

  if (globalGradle.status === 0 && globalGradle.stdout.includes(`Gradle ${gradleVersion}`)) {
    return "gradle";
  }

  if (!fs.existsSync(gradleExecutable)) {
    await downloadGradle();
    extractGradle();
  }

  return gradleExecutable;
}

async function downloadGradle() {
  fs.mkdirSync(cacheRoot, { recursive: true });
  if (fs.existsSync(zipPath) && fs.statSync(zipPath).size > 10 * 1024 * 1024) {
    return;
  }

  fs.rmSync(zipPath, { force: true });
  console.log(`Downloading Gradle ${gradleVersion}...`);
  if (process.platform === "win32") {
    run("powershell.exe", [
      "-NoProfile",
      "-ExecutionPolicy",
      "Bypass",
      "-Command",
      `$ProgressPreference = 'SilentlyContinue'; Invoke-WebRequest -Uri ${quotePowerShell(distributionUrl)} -OutFile ${quotePowerShell(zipPath)}`,
    ]);
    return;
  }

  const response = await fetch(distributionUrl);
  if (!response.ok || !response.body) {
    throw new Error(`Failed to download ${distributionUrl}: ${response.status} ${response.statusText}`);
  }

  const file = fs.createWriteStream(zipPath);
  await finished(Readable.fromWeb(response.body).pipe(file));
}

function extractGradle() {
  fs.rmSync(distRoot, { recursive: true, force: true });
  fs.mkdirSync(distRoot, { recursive: true });

  if (process.platform === "win32") {
    run("powershell.exe", [
      "-NoProfile",
      "-ExecutionPolicy",
      "Bypass",
      "-Command",
      `Expand-Archive -LiteralPath ${quotePowerShell(zipPath)} -DestinationPath ${quotePowerShell(distRoot)} -Force`,
    ]);
    return;
  }

  run("unzip", ["-q", zipPath, "-d", distRoot]);
}

function run(command, args) {
  const result = spawnSync(command, args, { stdio: "inherit" });
  if (result.error) {
    throw result.error;
  }
  if (result.status !== 0) {
    throw new Error(`${command} exited with ${result.status}`);
  }
}

function runGradle(command, args) {
  if (process.platform !== "win32") {
    return spawnSync(command, args, { cwd: root, stdio: "inherit" });
  }

  const commandLine = `& ${quotePowerShell(command)} ${args.map(quotePowerShell).join(" ")}`;
  return spawnSync("powershell.exe", ["-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", commandLine], {
    cwd: root,
    stdio: "inherit",
  });
}

function quoteCmd(value) {
  return `"${String(value).replaceAll('"', '\\"')}"`;
}

function quotePowerShell(value) {
  return `'${String(value).replaceAll("'", "''")}'`;
}
