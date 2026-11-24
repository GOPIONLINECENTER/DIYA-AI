/**
 * Maps spoken or typed DIYA AI commands (English, Bengali, Hindi) strictly to
 * allowlisted desktop actions. Never allows arbitrary shell or unapproved paths.
 */
export function parseSafeVoiceCommand(rawInput) {
  const text = String(rawInput || "").trim();
  if (!text) {
    return { allowed: false, reason: "Enter or speak a command first." };
  }

  const lower = text.toLowerCase();

  // Explicit guard against shell / destructive intent
  const blockedPatterns = [
    /\b(cmd|powershell|bash|sh|terminal|regedit|format|shutdown|rm\s+-rf|del\s+\/|taskkill)\b/i,
    /[;&|`$><]/,
  ];
  if (blockedPatterns.some((re) => re.test(text))) {
    return {
      allowed: false,
      reason: "Blocked: Arbitrary shell commands and system overrides are not permitted.",
    };
  }

  if (lower.includes("youtube") || text.includes("ইউটিউব") || text.includes("यूट्यूब")) {
    return {
      allowed: true,
      actionType: "OPEN_BROWSER_URL",
      targetScope: "https://www.youtube.com",
      summaryText: "Open YouTube in desktop browser",
      confirmationRequired: false,
    };
  }

  if (lower.includes("whatsapp") || text.includes("হোয়াটসঅ্যাপ") || text.includes("व्हाट्सएप")) {
    if (lower.includes("send") || lower.includes("message") || lower.includes("draft") || text.includes("মেসেজ")) {
      return {
        allowed: true,
        actionType: "DRAFT_MESSAGE_PREVIEW",
        targetScope: "channel:whatsapp_draft",
        summaryText: "Sensitive: Open WhatsApp Web for message review",
        confirmationRequired: true,
      };
    }
    return {
      allowed: true,
      actionType: "OPEN_BROWSER_URL",
      targetScope: "https://web.whatsapp.com",
      summaryText: "Open WhatsApp Web in desktop browser",
      confirmationRequired: false,
    };
  }

  if (lower.includes("chrome") || text.includes("ক্রোম") || text.includes("क्रोम")) {
    return {
      allowed: true,
      actionType: "OPEN_BROWSER_URL",
      targetScope: "app:chrome",
      summaryText: "Open Google Chrome on Windows PC",
      confirmationRequired: false,
    };
  }

  if (lower.includes("notepad") || text.includes("নোটপ্যাড") || text.includes("नोटपैड")) {
    return {
      allowed: true,
      actionType: "OPEN_BROWSER_URL",
      targetScope: "app:notepad",
      summaryText: "Open Notepad on Windows PC",
      confirmationRequired: false,
    };
  }

  if (lower.includes("calculator") || lower.includes("calc") || text.includes("ক্যালকুলেটর") || text.includes("कैलकुलेटर")) {
    return {
      allowed: true,
      actionType: "OPEN_BROWSER_URL",
      targetScope: "app:calculator",
      summaryText: "Open Calculator on Windows PC",
      confirmationRequired: false,
    };
  }

  if (lower.includes("downloads") || text.includes("ডাউনলোড")) {
    return {
      allowed: true,
      actionType: "SEARCH_SCOPED_DOCUMENTS",
      targetScope: "folder:downloads",
      summaryText: "Open approved Downloads folder",
      confirmationRequired: false,
    };
  }

  if (lower.includes("documents") || text.includes("ডকুমেন্টস")) {
    return {
      allowed: true,
      actionType: "SEARCH_SCOPED_DOCUMENTS",
      targetScope: "folder:documents",
      summaryText: "Open approved Documents folder",
      confirmationRequired: false,
    };
  }

  if (lower.includes("desktop") || text.includes("ডেস্কটপ")) {
    return {
      allowed: true,
      actionType: "SEARCH_SCOPED_DOCUMENTS",
      targetScope: "folder:desktop",
      summaryText: "Open approved Desktop folder",
      confirmationRequired: false,
    };
  }

  if (lower.includes("pictures") || lower.includes("photos")) {
    return {
      allowed: true,
      actionType: "SEARCH_SCOPED_DOCUMENTS",
      targetScope: "folder:pictures",
      summaryText: "Open approved Pictures folder",
      confirmationRequired: false,
    };
  }

  if (lower.includes("workspace") || lower.includes("folder")) {
    return {
      allowed: true,
      actionType: "SEARCH_SCOPED_DOCUMENTS",
      targetScope: "folder:workspace",
      summaryText: "Open approved DiyaWorkspace folder",
      confirmationRequired: false,
    };
  }

  if (lower.includes("play") || lower.includes("pause") || lower.includes("music")) {
    return {
      allowed: true,
      actionType: "MEDIA_PLAYBACK_CONTROL",
      targetScope: "media:play_pause",
      summaryText: "Toggle desktop media playback",
      confirmationRequired: false,
    };
  }

  if (lower.includes("status") || lower.includes("ping") || lower.includes("health")) {
    return {
      allowed: true,
      actionType: "WORKSPACE_STATUS_CHECK",
      targetScope: "telemetry:status",
      summaryText: "Verify Windows agent heartbeat & status",
      confirmationRequired: false,
    };
  }

  if (lower.includes("archive")) {
    return {
      allowed: true,
      actionType: "ARCHIVE_SCOPED_FILE",
      targetScope: "scope:archive_note.txt",
      summaryText: "Sensitive: Archive scoped workspace file",
      confirmationRequired: true,
    };
  }

  return {
    allowed: false,
    reason:
      "Unrecognized or unapproved command. Supported: YouTube, Chrome, WhatsApp Web, Notepad, Calculator, Documents/Downloads/Desktop/Pictures/Workspace folders, Play/Pause, or Status.",
  };
}
