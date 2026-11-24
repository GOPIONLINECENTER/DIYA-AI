# DIYA AI — Windows 10/11 Desktop Agent

Outbound-only authenticated Node.js agent that links your Windows 10/11 PC to the DIYA AI Cloud Relay over encrypted HTTPS/WSS.

- **Zero exposed ports** on your Windows PC during operation.
- **Strict allowlist** execution (`shell: false`): YouTube, Chrome, WhatsApp Web, Notepad, Calculator, approved user folders, media keys, and scoped workspace files.
- **Expiring session tokens** with automatic rotation and **exponential backoff reconnection**.

---

## 1. Prerequisites (Windows 10/11)

- **Node.js 20 LTS or newer**: Verify in PowerShell:
  ```powershell
  node -v
  npm -v
  ```
  If missing, install Node.js LTS from https://nodejs.org/ or via winget:
  ```powershell
  winget install OpenJS.NodeJS.LTS
  ```

---

## 2. Installation

Open PowerShell inside the `windows-agent` folder:

```powershell
cd path\to\DIYA-AI\windows-agent
npm install
copy .env.example .env
```

Edit `.env` (for example with `notepad .env`):

```env
RELAY_HTTPS_URL=https://YOUR_CLOUD_RELAY_URL.run.app
WEB_CONSOLE_URL=https://YOUR_DIYA_CONSOLE.vercel.app
PC_DEVICE_ID=win_workstation_01
PC_DEVICE_NAME=My Windows PC
SAFE_WORKSPACE_DIR=C:\Users\YourName\Documents\DiyaWorkspace
```

> Never commit `.env` or `.agent-session.json`. Both are excluded by `.gitignore`.

---

## 3. First-Time Pairing & Startup

1. Run the pairing command in PowerShell:
   ```powershell
   npm run pair
   ```
2. Your browser will open the DIYA AI Web Console (or copy the printed URL into your browser).
3. Sign in with your Google account and click **Approve & Pair Windows PC**.
4. The browser sends the expiring agent session token back to the local `127.0.0.1` pairing listener, saves `.agent-session.json`, closes the local listener, and immediately connects outbound over WSS to `cloud-relay`.

On future startups, simply run:

```powershell
npm start
```

---

## 4. Optional: Auto-Start on Windows Sign-In (Task Scheduler)

To launch the DIYA AI Windows Agent automatically when you log into Windows:

1. Press **Win + R**, type `taskschd.msc`, and press Enter.
2. Click **Create Basic Task** -> Name: `DIYA AI Desktop Agent`.
3. Trigger: **When I log on**.
4. Action: **Start a program**:
   - **Program/script**: `C:\Program Files\nodejs\node.exe`
   - **Add arguments**: `agent.js`
   - **Start in**: `C:\path\to\DIYA-AI\windows-agent`
5. Finish and check **Open the Properties dialog** -> enable **Run whether user is logged on or not** (optional) and uncheck **Stop the task if it runs longer than 3 days** under *Settings*.

---

## 5. Approved Capabilities & Security Boundaries

- **Approved Actions**:
  - Open YouTube (`https://www.youtube.com`)
  - Open Google Chrome (`app:chrome`)
  - Open WhatsApp Web (`https://web.whatsapp.com`)
  - Open Notepad (`app:notepad`)
  - Open Calculator (`app:calculator`)
  - Open approved folders (`Documents`, `Downloads`, `Desktop`, `Pictures`, `DiyaWorkspace`)
  - Media playback keys & scoped workspace status/archiving
- **Sensitive Actions**: Message previews and file archiving always require explicit **Confirm** approval in the DIYA AI dashboard/app before dispatch and execution.
- **Prohibited**: Arbitrary shell commands (`cmd`, `powershell` input, `exec`) and paths outside approved folders are always rejected.
