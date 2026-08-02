import { app, BrowserWindow, ipcMain, Menu, shell } from "electron";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { CanoeLauncherBridge } from "./launcherBridge";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const launcher = new CanoeLauncherBridge();

function createWindow() {
  const window = new BrowserWindow({
    width: 1280,
    height: 820,
    minWidth: 1040,
    minHeight: 680,
    backgroundColor: "#f4f7f3",
    titleBarStyle: "hiddenInset",
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, "preload.js"),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });

  launcher.bindWindow(window);

  if (process.env.VITE_DEV_SERVER_URL) {
    window.loadURL(process.env.VITE_DEV_SERVER_URL);
  } else {
    window.loadFile(path.join(__dirname, "../dist/index.html"));
  }
}

app.whenReady().then(() => {
  Menu.setApplicationMenu(null);
  createWindow();

  app.on("activate", () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow();
    }
  });
});

app.on("window-all-closed", () => {
  if (process.platform !== "darwin") {
    app.quit();
  }
});

ipcMain.handle("launcher:get-library", () => launcher.getLibrary());
ipcMain.handle("launcher:get-settings", () => launcher.getSettings());
ipcMain.handle("launcher:list-accounts", () => launcher.listAccounts());
ipcMain.handle("launcher:list-processes", () => launcher.listProcesses());
ipcMain.handle("launcher:update-settings", (_event, patch) => launcher.updateSettings(patch));
ipcMain.handle("launcher:add-offline-account", (_event, username: string) => launcher.addOfflineAccount(username));
ipcMain.handle("launcher:create-vanilla-instance", (_event, input) => launcher.createVanillaInstance(input));
ipcMain.handle("launcher:install-pack", (_event, packId: string) => launcher.installPack(packId));
ipcMain.handle("launcher:update-pack", (_event, packId: string) => launcher.updatePack(packId));
ipcMain.handle("launcher:launch-instance", (_event, packId: string) => launcher.launchInstance(packId));
ipcMain.handle("launcher:stop-process", (_event, processId: string) => launcher.stopProcess(processId));
ipcMain.handle("launcher:open-folder", (_event, packId: string) => launcher.openInstanceFolder(packId));
ipcMain.handle("shell:open-external", (_event, url: string) => shell.openExternal(url));
