const os = require("os");
const fs = require("fs");
const path = require("path");
const { execFile } = require("child_process");

const URL_ALIASES = new Map([
  ["youtube", "https://www.youtube.com"],
  ["whatsapp", "https://web.whatsapp.com"],
  ["whatsapp_web", "https://web.whatsapp.com"],
  ["google", "https://www.google.com"],
]);

const APP_ALLOWLIST = new Set(["app:chrome", "app:notepad", "app:calculator"]);

const SENSITIVE_ACTIONS = new Set(["DRAFT_MESSAGE_PREVIEW", "ARCHIVE_SCOPED_FILE"]);

function buildApprovedFolders(safeWorkspaceDir, homeDir = os.homedir()) {
  const workspace = path.resolve(safeWorkspaceDir);
  return new Map([
    ["folder:workspace", workspace],
    ["folder:documents", path.resolve(homeDir, "Documents")],
    ["folder:downloads", path.resolve(homeDir, "Downloads")],
    ["folder:desktop", path.resolve(homeDir, "Desktop")],
    ["folder:pictures", path.resolve(homeDir, "Pictures")],
  ]);
}

function isPrivateOrLoopbackHostname(hostname) {
  const lower = String(hostname || "").toLowerCase();
  if (
    lower === "localhost" ||
    lower === "127.0.0.1" ||
    lower === "::1" ||
    lower.endsWith(".local") ||
    lower.startsWith("169.254.") ||
    lower.startsWith("10.") ||
    lower.startsWith("192.168.")
  ) {
    return true;
  }
  const m172 = /^172\.(\d+)\./.exec(lower);
  if (m172) {
    const second = Number(m172[1]);
    if (second >= 16 && second <= 31) return true;
  }
  return false;
}

function resolveChromeExecutable(existsSync = fs.existsSync, env = process.env) {
  const candidates = [
    "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
    "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
  ];
  if (env.LOCALAPPDATA) {
    candidates.push(path.join(env.LOCALAPPDATA, "Google", "Chrome", "Application", "chrome.exe"));
  }
  return candidates.find((p) => existsSync(p)) || null;
}

function defaultSpawnDetached(command, args) {
  const child = execFile(command, args, {
    shell: false,
    detached: true,
    stdio: "ignore",
  });
  child.unref?.();
}

function createActionDispatcher(options = {}) {
  const platform = options.platform || process.platform;
  const safeWorkspaceDir = path.resolve(
    options.safeWorkspaceDir || path.join(os.homedir(), "Documents", "DiyaWorkspace")
  );
  const homeDir = options.homeDir || os.homedir();
  const approvedFolders = buildApprovedFolders(safeWorkspaceDir, homeDir);
  const existsSync = options.existsSync || fs.existsSync;
  const readdirSync = options.readdirSync || fs.readdirSync;
  const mkdirSync = options.mkdirSync || fs.mkdirSync;
  const renameSync = options.renameSync || fs.renameSync;
  const spawnDetached = options.spawnDetached || defaultSpawnDetached;

  return function dispatchAllowlistedAction(msg) {
    const commandId = String(msg?.commandId || "");
    const actionType = String(msg?.actionType || "");
    const rawScope = String(msg?.targetScope || "").trim();
    const confirmed = msg?.confirmed === true;
    const confirmationRequired = msg?.confirmationRequired === true;

    if ((SENSITIVE_ACTIONS.has(actionType) || confirmationRequired) && !confirmed) {
      return {
        commandId,
        status: "REJECTED",
        detail: "Explicit user confirmation is required before executing this sensitive action.",
      };
    }

    switch (actionType) {
      case "WORKSPACE_STATUS_CHECK":
        return {
          commandId,
          status: "EXECUTED",
          detail: `Desktop agent healthy on ${platform} (outbound WSS active).`,
        };

      case "OPEN_BROWSER_URL": {
        const lowerScope = rawScope.toLowerCase();

        if (APP_ALLOWLIST.has(lowerScope)) {
          if (platform !== "win32") {
            return {
              commandId,
              status: "REJECTED",
              detail: `Desktop app launch (${lowerScope}) requires Windows 10/11 runtime.`,
            };
          }

          if (lowerScope === "app:notepad") {
            const notepadExe = path.join(
              process.env.SystemRoot || "C:\\Windows",
              "System32",
              "notepad.exe"
            );
            spawnDetached(existsSync(notepadExe) ? notepadExe : "notepad.exe", []);
            return { commandId, status: "EXECUTED", detail: "Opened Windows Notepad." };
          }

          if (lowerScope === "app:calculator") {
            const calcExe = path.join(
              process.env.SystemRoot || "C:\\Windows",
              "System32",
              "calc.exe"
            );
            spawnDetached(existsSync(calcExe) ? calcExe : "calc.exe", []);
            return { commandId, status: "EXECUTED", detail: "Opened Windows Calculator." };
          }

          if (lowerScope === "app:chrome") {
            const chromePath = resolveChromeExecutable(existsSync, process.env);
            if (!chromePath) {
              return {
                commandId,
                status: "REJECTED",
                detail: "Google Chrome was not found in standard Windows installation directories.",
              };
            }
            spawnDetached(chromePath, ["--new-window"]);
            return { commandId, status: "EXECUTED", detail: "Opened Google Chrome." };
          }
        }

        const candidateUrl = URL_ALIASES.get(lowerScope) || rawScope;
        let parsed;
        try {
          parsed = new URL(candidateUrl);
        } catch {
          return {
            commandId,
            status: "REJECTED",
            detail: "Only allowlisted apps or valid HTTPS URLs are permitted.",
          };
        }

        if (parsed.protocol !== "https:" || isPrivateOrLoopbackHostname(parsed.hostname)) {
          return {
            commandId,
            status: "REJECTED",
            detail: "Only public HTTPS URLs are permitted.",
          };
        }

        if (platform !== "win32") {
          return {
            commandId,
            status: "REJECTED",
            detail: "Browser launch requires Windows 10/11 runtime.",
          };
        }

        // Strict non-shell Windows URL handler (shell: false)
        spawnDetached("rundll32.exe", ["url.dll,FileProtocolHandler", parsed.toString()]);
        return {
          commandId,
          status: "EXECUTED",
          detail: `Opened HTTPS URL: ${parsed.hostname}`,
        };
      }

      case "SEARCH_SCOPED_DOCUMENTS": {
        const lowerScope = rawScope.toLowerCase();
        if (lowerScope.startsWith("folder:")) {
          const targetFolder = approvedFolders.get(lowerScope);
          if (!targetFolder) {
            return {
              commandId,
              status: "REJECTED",
              detail: "Folder is not in the approved folders allowlist.",
            };
          }
          if (lowerScope === "folder:workspace" && !existsSync(targetFolder)) {
            mkdirSync(targetFolder, { recursive: true });
          }
          if (!existsSync(targetFolder)) {
            return {
              commandId,
              status: "REJECTED",
              detail: `Approved folder does not exist: ${lowerScope}`,
            };
          }
          if (platform !== "win32") {
            return {
              commandId,
              status: "REJECTED",
              detail: "Opening Windows Explorer requires Windows 10/11 runtime.",
            };
          }
          spawnDetached("explorer.exe", [targetFolder]);
          return {
            commandId,
            status: "EXECUTED",
            detail: `Opened approved folder (${lowerScope.replace("folder:", "")}).`,
          };
        }

        if (!existsSync(safeWorkspaceDir)) {
          return {
            commandId,
            status: "EXECUTED",
            detail: "Scoped workspace folder does not exist yet.",
          };
        }
        const files = readdirSync(safeWorkspaceDir).slice(0, 25);
        return {
          commandId,
          status: "EXECUTED",
          detail: files.length ? `Scoped files: ${files.join(", ")}` : "Scoped workspace is empty.",
        };
      }

      case "MEDIA_PLAYBACK_CONTROL": {
        if (platform !== "win32") {
          return {
            commandId,
            status: "REJECTED",
            detail: "Desktop media keys require Windows 10/11 runtime.",
          };
        }
        const keyMap = new Map([
          ["media:play_pause", 0xb3],
          ["media:next", 0xb0],
          ["media:previous", 0xb1],
          ["media:mute", 0xad],
        ]);
        const vk = keyMap.get(rawScope.toLowerCase() || "media:play_pause");
        if (!vk) {
          return {
            commandId,
            status: "REJECTED",
            detail: "Unsupported media control command.",
          };
        }
        const staticPsScript =
          `$sig='[DllImport("user32.dll")] public static extern void keybd_event(byte bVk, byte bScan, int dwFlags, int dwExtraInfo);';` +
          `$t=Add-Type -MemberDefinition $sig -Name 'DiyaMediaKey' -Namespace 'Win32' -PassThru;` +
          `$t::keybd_event(${vk},0,0,0);$t::keybd_event(${vk},0,2,0);`;
        spawnDetached("powershell.exe", ["-NoProfile", "-NonInteractive", "-Command", staticPsScript]);
        return {
          commandId,
          status: "EXECUTED",
          detail: `Sent allowlisted media command (${rawScope || "media:play_pause"}).`,
        };
      }

      case "DRAFT_MESSAGE_PREVIEW": {
        if (platform !== "win32") {
          return {
            commandId,
            status: "REJECTED",
            detail: "Opening WhatsApp Web requires Windows 10/11 runtime.",
          };
        }
        spawnDetached("rundll32.exe", ["url.dll,FileProtocolHandler", "https://web.whatsapp.com"]);
        return {
          commandId,
          status: "EXECUTED",
          detail: "Confirmed sensitive action: opened WhatsApp Web for message review.",
        };
      }

      case "ARCHIVE_SCOPED_FILE": {
        const cleanName = path.basename(rawScope.replace(/^scope:/, ""));
        if (!cleanName || cleanName === "." || cleanName === "..") {
          return {
            commandId,
            status: "REJECTED",
            detail: "Valid scoped filename required.",
          };
        }
        const resolvedSource = path.resolve(safeWorkspaceDir, cleanName);
        if (!resolvedSource.startsWith(safeWorkspaceDir + path.sep)) {
          return {
            commandId,
            status: "REJECTED",
            detail: "Path outside confined workspace denied.",
          };
        }
        if (!existsSync(resolvedSource)) {
          return {
            commandId,
            status: "REJECTED",
            detail: `Scoped file not found in workspace: ${cleanName}`,
          };
        }
        const archiveDir = path.resolve(safeWorkspaceDir, "_archive");
        const resolvedDest = path.resolve(archiveDir, cleanName);
        if (!resolvedDest.startsWith(archiveDir + path.sep)) {
          return {
            commandId,
            status: "REJECTED",
            detail: "Archive destination outside confined workspace denied.",
          };
        }
        mkdirSync(archiveDir, { recursive: true });
        renameSync(resolvedSource, resolvedDest);
        return {
          commandId,
          status: "EXECUTED",
          detail: `Archived scoped file to _archive/${cleanName}`,
        };
      }

      default:
        return {
          commandId,
          status: "REJECTED",
          detail: "Action not in Windows agent allowlist.",
        };
    }
  };
}

module.exports = {
  URL_ALIASES,
  APP_ALLOWLIST,
  SENSITIVE_ACTIONS,
  buildApprovedFolders,
  resolveChromeExecutable,
  createActionDispatcher,
};
