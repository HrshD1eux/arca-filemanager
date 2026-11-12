package com.Hrshd1eux.filemanager.core.drop.server

import com.Hrshd1eux.filemanager.core.drop.model.DropTransferItem
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter

/**
 * Generates ARCA Drop's light-theme browser web UI and inline vector SVG logo/favicon.
 */
object ArcaDropHtmlPortal {

    /**
     * Exact ARCA adaptive launcher icon geometry wrapped into a crisp vector SVG
     * for browser header branding and <link rel="icon"> favicon.
     */
    val ARCA_LOGO_SVG = """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="40" height="40" role="img" aria-label="ARCA Logo">
          <rect width="108" height="108" rx="26" fill="#0B0E14"/>
          <path fill="#3B82F6" d="M34,36h14l6,6h20c2.2,0 4,1.8 4,4v26c0,2.2 -1.8,4 -4,4H34c-2.2,0 -4,-1.8 -4,-4V40c0,-2.2 1.8,-4 4,-4z"/>
          <path fill="#FFFFFF" d="M48,56l6,-6 6,6 -2,2 -4,-4 -4,4 -2,-2z"/>
        </svg>
    """.trimIndent()

    val ARCA_FAVICON_BYTES: ByteArray = ARCA_LOGO_SVG.toByteArray(Charsets.UTF_8)

    fun escapeHtml(raw: String): String {
        val sb = StringBuilder(raw.length + 16)
        for (ch in raw) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&#39;")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun renderPinGateHtml(errorMessage: String? = null): String {
        val errorBanner = if (!errorMessage.isNullOrBlank()) {
            """<div class="alert">${escapeHtml(errorMessage)}</div>"""
        } else {
            ""
        }

        return """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>ARCA Drop — Enter PIN</title>
  <link rel="icon" type="image/svg+xml" href="/favicon.svg" />
  <style>
    :root {
      color-scheme: light;
      --bg: #F8FAFC;
      --surface: #FFFFFF;
      --border: #E2E8F0;
      --text: #0F172A;
      --muted: #64748B;
      --accent: #3B82F6;
      --accent-hover: #2563EB;
      --danger-bg: #FEF2F2;
      --danger-text: #B91C1C;
      --danger-border: #FECACA;
    }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 20px;
      background: var(--bg);
      color: var(--text);
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Inter, sans-serif;
    }
    .card {
      width: 100%;
      max-width: 400px;
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: 16px;
      padding: 28px 24px;
      box-shadow: 0 10px 30px rgba(15, 23, 42, 0.06);
      text-align: center;
    }
    .brand {
      display: inline-flex;
      align-items: center;
      gap: 12px;
      margin-bottom: 14px;
    }
    .brand-title {
      font-size: 1.35rem;
      font-weight: 700;
      letter-spacing: -0.02em;
    }
    p {
      margin: 0 0 18px;
      color: var(--muted);
      font-size: 0.94rem;
      line-height: 1.5;
    }
    .alert {
      background: var(--danger-bg);
      color: var(--danger-text);
      border: 1px solid var(--danger-border);
      border-radius: 10px;
      padding: 10px 12px;
      margin-bottom: 14px;
      font-size: 0.88rem;
      font-weight: 500;
    }
    input[type="text"] {
      width: 100%;
      padding: 12px 14px;
      font-size: 1.35rem;
      font-weight: 700;
      letter-spacing: 0.35em;
      text-align: center;
      border-radius: 10px;
      border: 1px solid var(--border);
      background: #F8FAFC;
      color: var(--text);
      outline: none;
      margin-bottom: 14px;
    }
    input[type="text"]:focus {
      border-color: var(--accent);
      background: #FFFFFF;
      box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15);
    }
    button {
      width: 100%;
      padding: 12px 16px;
      border: none;
      border-radius: 10px;
      background: var(--accent);
      color: #FFFFFF;
      font-size: 0.96rem;
      font-weight: 600;
      cursor: pointer;
    }
    button:hover { background: var(--accent-hover); }
    .foot {
      margin-top: 16px;
      font-size: 0.8rem;
      color: var(--muted);
    }
  </style>
</head>
<body>
  <main class="card">
    <div class="brand">
      $ARCA_LOGO_SVG
      <span class="brand-title">ARCA Drop</span>
    </div>
    <p>Enter the 6-digit PIN shown on your Android device to connect over local Wi-Fi.</p>
    $errorBanner
    <form method="GET" action="/">
      <input type="text" name="pin" inputmode="numeric" pattern="[0-9]{6}" maxlength="6" placeholder="000000" autofocus required />
      <button type="submit">Unlock Transfer Portal</button>
    </form>
    <div class="foot">Direct peer-to-peer LAN transfer • Zero cloud</div>
  </main>
</body>
</html>
        """.trimIndent()
    }

    fun renderPortalHtml(
        items: List<DropTransferItem>,
        sharedText: String?,
        receiveFolderName: String,
        pinToken: String
    ): String {
        val totalSize = items.sumOf { it.size.coerceAtLeast(0L) }
        val escapedPin = escapeHtml(pinToken)
        val escapedFolder = escapeHtml(receiveFolderName)

        val textSection = if (!sharedText.isNullOrBlank()) {
            val escapedText = escapeHtml(sharedText)
            """
            <section class="card">
              <div class="section-head">
                <div>
                  <h2>Shared Text from Phone</h2>
                  <p class="sub">Copy links, notes, or commands directly to your clipboard</p>
                </div>
                <button type="button" class="btn btn-secondary" onclick="copySharedText()">Copy Text</button>
              </div>
              <pre id="sharedTextBox" class="text-box">$escapedText</pre>
            </section>
            """.trimIndent()
        } else {
            ""
        }

        val itemsJson = buildString {
            append('[')
            items.forEachIndexed { idx, item ->
                if (idx > 0) append(',')
                append('{')
                append("\"id\":\"").append(escapeJson(item.id)).append("\",")
                append("\"name\":\"").append(escapeJson(item.name)).append("\",")
                append("\"rel\":\"").append(escapeJson(item.relativePath)).append("\",")
                append("\"mime\":\"").append(escapeJson(item.mimeType)).append("\",")
                append("\"size\":").append(item.size).append(',')
                append("\"sizeText\":\"").append(escapeJson(FileFormatter.formatFileSize(item.size))).append('"')
                append('}')
            }
            append(']')
        }

        return """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>ARCA Drop — Local Wi-Fi File Transfer</title>
  <link rel="icon" type="image/svg+xml" href="/favicon.svg" />
  <style>
    :root {
      color-scheme: light;
      --bg: #F8FAFC;
      --surface: #FFFFFF;
      --surface-alt: #F1F5F9;
      --border: #E2E8F0;
      --text: #0F172A;
      --muted: #64748B;
      --accent: #3B82F6;
      --accent-soft: #EFF6FF;
      --accent-hover: #2563EB;
      --success: #16A34A;
      --success-soft: #F0FDF4;
      --danger: #DC2626;
    }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      background: var(--bg);
      color: var(--text);
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Inter, sans-serif;
      line-height: 1.45;
    }
    .topbar {
      background: var(--surface);
      border-bottom: 1px solid var(--border);
      position: sticky;
      top: 0;
      z-index: 20;
    }
    .topbar-inner {
      max-width: 1080px;
      margin: 0 auto;
      padding: 12px 20px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      flex-wrap: wrap;
    }
    .brand {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .brand-name {
      font-size: 1.15rem;
      font-weight: 700;
      letter-spacing: -0.02em;
    }
    .brand-sub {
      font-size: 0.8rem;
      color: var(--muted);
    }
    .pill {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      background: var(--surface-alt);
      border: 1px solid var(--border);
      border-radius: 999px;
      padding: 6px 12px;
      font-size: 0.82rem;
      font-weight: 600;
      color: var(--text);
    }
    .dot {
      width: 8px;
      height: 8px;
      border-radius: 999px;
      background: var(--success);
    }
    .container {
      max-width: 1080px;
      margin: 0 auto;
      padding: 20px;
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    .card {
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: 14px;
      padding: 18px 20px;
      box-shadow: 0 1px 2px rgba(15, 23, 42, 0.03);
    }
    .section-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      flex-wrap: wrap;
      margin-bottom: 14px;
    }
    h2 {
      margin: 0;
      font-size: 1.05rem;
      font-weight: 700;
    }
    .sub {
      margin: 2px 0 0;
      color: var(--muted);
      font-size: 0.84rem;
    }
    .btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 6px;
      border: 1px solid transparent;
      border-radius: 9px;
      padding: 8px 14px;
      font-size: 0.86rem;
      font-weight: 600;
      cursor: pointer;
      text-decoration: none;
      transition: background 0.15s ease;
    }
    .btn-primary {
      background: var(--accent);
      color: #FFFFFF;
    }
    .btn-primary:hover { background: var(--accent-hover); }
    .btn-secondary {
      background: var(--surface-alt);
      color: var(--text);
      border-color: var(--border);
    }
    .btn-secondary:hover { background: #E2E8F0; }
    .dropzone {
      border: 2px dashed #CBD5E1;
      border-radius: 12px;
      background: #F8FAFC;
      padding: 26px 16px;
      text-align: center;
      cursor: pointer;
      transition: border-color 0.15s ease, background 0.15s ease;
    }
    .dropzone.dragover {
      border-color: var(--accent);
      background: var(--accent-soft);
    }
    .dropzone strong { color: var(--accent); }
    .progress-wrap {
      margin-top: 12px;
      display: none;
      flex-direction: column;
      gap: 6px;
    }
    .progress-track {
      height: 8px;
      width: 100%;
      border-radius: 999px;
      background: var(--surface-alt);
      overflow: hidden;
    }
    .progress-fill {
      height: 100%;
      width: 0%;
      background: var(--accent);
      transition: width 0.1s linear;
    }
    .text-send-row {
      margin-top: 14px;
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
    }
    .input {
      flex: 1;
      min-width: 220px;
      padding: 9px 12px;
      border-radius: 9px;
      border: 1px solid var(--border);
      background: #FFFFFF;
      color: var(--text);
      font-size: 0.9rem;
      outline: none;
    }
    .input:focus {
      border-color: var(--accent);
      box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.12);
    }
    .text-box {
      margin: 0;
      padding: 12px;
      background: var(--surface-alt);
      border: 1px solid var(--border);
      border-radius: 10px;
      font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
      font-size: 0.86rem;
      white-space: pre-wrap;
      word-break: break-word;
    }
    .toolbar {
      display: flex;
      gap: 10px;
      align-items: center;
      flex-wrap: wrap;
    }
    .grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
      gap: 12px;
    }
    .file-card {
      border: 1px solid var(--border);
      border-radius: 12px;
      background: var(--surface);
      overflow: hidden;
      display: flex;
      flex-direction: column;
    }
    .preview-area {
      height: 124px;
      background: var(--surface-alt);
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      overflow: hidden;
    }
    .preview-area img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
    .type-badge {
      padding: 6px 12px;
      border-radius: 8px;
      background: var(--accent-soft);
      color: var(--accent);
      font-weight: 700;
      font-size: 0.85rem;
      border: 1px solid #BFDBFE;
    }
    .file-body {
      padding: 12px;
      display: flex;
      flex-direction: column;
      gap: 6px;
      flex: 1;
    }
    .file-name {
      font-weight: 600;
      font-size: 0.9rem;
      word-break: break-word;
    }
    .file-meta {
      font-size: 0.78rem;
      color: var(--muted);
      word-break: break-all;
    }
    .file-actions {
      margin-top: auto;
      padding-top: 8px;
      display: flex;
      gap: 8px;
    }
    .file-actions .btn { flex: 1; padding: 7px 10px; font-size: 0.8rem; }
    .empty {
      padding: 28px;
      text-align: center;
      color: var(--muted);
      font-size: 0.92rem;
    }
    .modal-backdrop {
      position: fixed;
      inset: 0;
      background: rgba(15, 23, 42, 0.55);
      display: none;
      align-items: center;
      justify-content: center;
      padding: 20px;
      z-index: 50;
    }
    .modal {
      background: var(--surface);
      border-radius: 14px;
      max-width: 820px;
      width: 100%;
      max-height: 88vh;
      display: flex;
      flex-direction: column;
      overflow: hidden;
      border: 1px solid var(--border);
    }
    .modal-head {
      padding: 12px 16px;
      border-bottom: 1px solid var(--border);
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 10px;
    }
    .modal-body {
      padding: 16px;
      overflow: auto;
      text-align: center;
      background: #F8FAFC;
    }
    .modal-body img, .modal-body video {
      max-width: 100%;
      max-height: 68vh;
      border-radius: 8px;
    }
    .modal-body iframe {
      width: 100%;
      height: 66vh;
      border: 1px solid var(--border);
      border-radius: 8px;
      background: #FFFFFF;
    }
    .toast {
      position: fixed;
      bottom: 20px;
      right: 20px;
      background: #0F172A;
      color: #FFFFFF;
      padding: 10px 14px;
      border-radius: 10px;
      font-size: 0.86rem;
      display: none;
      z-index: 60;
    }
  </style>
</head>
<body>
  <header class="topbar">
    <div class="topbar-inner">
      <div class="brand">
        $ARCA_LOGO_SVG
        <div>
          <div class="brand-name">ARCA Drop</div>
          <div class="brand-sub">Direct Local Wi-Fi File Transfer • Zero Cloud</div>
        </div>
      </div>
      <div class="pill">
        <span class="dot"></span>
        <span>${items.size} items (${escapeHtml(FileFormatter.formatFileSize(totalSize))})</span>
      </div>
    </div>
  </header>

  <main class="container">
    $textSection

    <section class="card">
      <div class="section-head">
        <div>
          <h2>Send to Phone</h2>
          <p class="sub">Uploads save directly into: <strong>$escapedFolder</strong></p>
        </div>
      </div>

      <div id="dropzone" class="dropzone" onclick="document.getElementById('fileInput').click()">
        <div>Drop files here or <strong>browse from device</strong></div>
        <div class="sub">Streams directly to storage with zero cloud intermediaries</div>
        <input id="fileInput" type="file" multiple style="display:none" />
      </div>

      <div id="progressWrap" class="progress-wrap">
        <div class="section-head" style="margin-bottom:0">
          <span id="progressLabel" style="font-size:0.84rem;font-weight:600;">Uploading…</span>
          <span id="progressSpeed" style="font-size:0.82rem;color:var(--muted);">0%</span>
        </div>
        <div class="progress-track">
          <div id="progressFill" class="progress-fill"></div>
        </div>
      </div>

      <form class="text-send-row" onsubmit="return sendTextToPhone(event)">
        <input id="textToPhoneInput" class="input" type="text" placeholder="Send a link or text note straight to phone…" />
        <button type="submit" class="btn btn-secondary">Send Text</button>
      </form>
    </section>

    <section class="card">
      <div class="section-head">
        <div>
          <h2>Shared from Phone (${items.size})</h2>
          <p class="sub">Preview in browser, download individually, or stream all folders as ZIP</p>
        </div>
        <div class="toolbar">
          <input id="searchBox" class="input" style="min-width:180px;" type="search" placeholder="Filter shared items…" oninput="renderFiles()" />
          ${if (items.isNotEmpty()) """<a class="btn btn-primary" href="/download-all?pin=$escapedPin">Download All ZIP</a>""" else ""}
        </div>
      </div>

      <div id="filesGrid" class="grid"></div>
      <div id="emptyState" class="empty" style="display:none;">No shared files queued yet. Use "Send to Phone" above or add files in ARCA.</div>
    </section>
  </main>

  <div id="previewModal" class="modal-backdrop" onclick="if(event.target===this) closePreview()">
    <div class="modal">
      <div class="modal-head">
        <strong id="previewTitle" style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">Preview</strong>
        <button type="button" class="btn btn-secondary" onclick="closePreview()">Close</button>
      </div>
      <div id="previewBody" class="modal-body"></div>
    </div>
  </div>

  <div id="toast" class="toast"></div>

  <script>
    const PIN = "$escapedPin";
    const ITEMS = $itemsJson;

    function esc(s) {
      return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;').replace(/'/g,'&#39;');
    }

    function showToast(msg) {
      const el = document.getElementById('toast');
      el.textContent = msg;
      el.style.display = 'block';
      clearTimeout(window.__toastTimer);
      window.__toastTimer = setTimeout(() => { el.style.display = 'none'; }, 2800);
    }

    function extLabel(name, mime) {
      const idx = name.lastIndexOf('.');
      if (idx > 0 && idx < name.length - 1) return name.slice(idx + 1, idx + 6).toUpperCase();
      if (mime.startsWith('audio/')) return 'AUDIO';
      if (mime.startsWith('video/')) return 'VIDEO';
      return 'FILE';
    }

    function canPreviewInline(mime, name) {
      const lower = name.toLowerCase();
      if (lower.endsWith('.html') || lower.endsWith('.htm') || lower.endsWith('.svg')) return false;
      return mime.startsWith('image/') || mime.startsWith('video/') || mime.startsWith('audio/') ||
             mime === 'application/pdf' || mime.startsWith('text/');
    }

    function renderFiles() {
      const grid = document.getElementById('filesGrid');
      const empty = document.getElementById('emptyState');
      const q = (document.getElementById('searchBox').value || '').trim().toLowerCase();
      const filtered = q ? ITEMS.filter(i => i.rel.toLowerCase().includes(q) || i.name.toLowerCase().includes(q)) : ITEMS;

      if (!filtered.length) {
        grid.innerHTML = '';
        empty.style.display = 'block';
        return;
      }
      empty.style.display = 'none';

      grid.innerHTML = filtered.map(item => {
        const isImg = item.mime.startsWith('image/') && !item.name.toLowerCase().endsWith('.svg');
        const isVid = item.mime.startsWith('video/');
        const fileUrl = '/file/' + encodeURIComponent(item.id) + '?pin=' + encodeURIComponent(PIN);
        const thumbUrl = '/thumb/' + encodeURIComponent(item.id) + '?pin=' + encodeURIComponent(PIN);
        const previewHtml = isImg
          ? '<img loading="lazy" src="' + thumbUrl + '" alt="' + esc(item.name) + '" />'
          : (isVid
              ? '<div class="type-badge">VIDEO • ' + esc(extLabel(item.name, item.mime)) + '</div>'
              : '<div class="type-badge">' + esc(extLabel(item.name, item.mime)) + '</div>');
        const previewBtn = canPreviewInline(item.mime, item.name)
          ? '<button type="button" class="btn btn-secondary" onclick="openPreview(\'' + esc(item.id) + '\')">Preview</button>'
          : '';

        return '<article class="file-card">' +
          '<div class="preview-area" onclick="openPreview(\'' + esc(item.id) + '\')">' + previewHtml + '</div>' +
          '<div class="file-body">' +
            '<div class="file-name">' + esc(item.name) + '</div>' +
            '<div class="file-meta">' + esc(item.sizeText) + (item.rel !== item.name ? ' • ' + esc(item.rel) : '') + '</div>' +
            '<div class="file-actions">' +
              previewBtn +
              '<a class="btn btn-primary" href="' + fileUrl + '" download="' + esc(item.name) + '">Download</a>' +
            '</div>' +
          '</div>' +
        '</article>';
      }).join('');
    }

    function openPreview(id) {
      const item = ITEMS.find(x => x.id === id);
      if (!item) return;
      const url = '/file/' + encodeURIComponent(item.id) + '?inline=1&pin=' + encodeURIComponent(PIN);
      if (!canPreviewInline(item.mime, item.name)) {
        window.location.href = '/file/' + encodeURIComponent(item.id) + '?pin=' + encodeURIComponent(PIN);
        return;
      }

      document.getElementById('previewTitle').textContent = item.rel;
      const body = document.getElementById('previewBody');
      if (item.mime.startsWith('image/')) {
        body.innerHTML = '<img src="' + url + '" alt="' + esc(item.name) + '" />';
      } else if (item.mime.startsWith('video/')) {
        body.innerHTML = '<video controls autoplay src="' + url + '"></video>';
      } else if (item.mime.startsWith('audio/')) {
        body.innerHTML = '<audio controls autoplay style="width:100%" src="' + url + '"></audio>';
      } else {
        body.innerHTML = '<iframe src="' + url + '"></iframe>';
      }
      document.getElementById('previewModal').style.display = 'flex';
    }

    function closePreview() {
      document.getElementById('previewBody').innerHTML = '';
      document.getElementById('previewModal').style.display = 'none';
    }

    function copySharedText() {
      const text = document.getElementById('sharedTextBox')?.textContent || '';
      navigator.clipboard?.writeText(text).then(() => showToast('Copied text to clipboard')).catch(() => showToast('Copy failed'));
    }

    async function sendTextToPhone(e) {
      e.preventDefault();
      const input = document.getElementById('textToPhoneInput');
      const val = input.value.trim();
      if (!val) return false;
      try {
        const res = await fetch('/api/text?pin=' + encodeURIComponent(PIN), {
          method: 'POST',
          headers: { 'Content-Type': 'text/plain; charset=utf-8', 'X-Arca-Drop-Pin': PIN },
          body: val
        });
        if (res.ok) {
          input.value = '';
          showToast('Text sent to phone');
        } else {
          showToast('Failed to send text');
        }
      } catch (_) {
        showToast('Connection failed');
      }
      return false;
    }

    const dropzone = document.getElementById('dropzone');
    const fileInput = document.getElementById('fileInput');
    dropzone.addEventListener('dragover', e => { e.preventDefault(); dropzone.classList.add('dragover'); });
    dropzone.addEventListener('dragleave', () => dropzone.classList.remove('dragover'));
    dropzone.addEventListener('drop', e => {
      e.preventDefault();
      dropzone.classList.remove('dragover');
      if (e.dataTransfer?.files?.length) uploadFiles(Array.from(e.dataTransfer.files));
    });
    fileInput.addEventListener('change', () => {
      if (fileInput.files?.length) uploadFiles(Array.from(fileInput.files));
      fileInput.value = '';
    });

    async function uploadFiles(files) {
      const wrap = document.getElementById('progressWrap');
      const fill = document.getElementById('progressFill');
      const label = document.getElementById('progressLabel');
      const speed = document.getElementById('progressSpeed');
      wrap.style.display = 'flex';

      let okCount = 0;
      for (let i = 0; i < files.length; i++) {
        const file = files[i];
        label.textContent = 'Uploading (' + (i + 1) + '/' + files.length + '): ' + file.name;
        const ok = await uploadSingleFile(file, (pct, mbps) => {
          fill.style.width = pct + '%';
          speed.textContent = pct + '% • ' + mbps.toFixed(1) + ' MB/s';
        });
        if (ok) okCount++;
      }

      fill.style.width = '100%';
      showToast('Uploaded ' + okCount + ' of ' + files.length + ' file(s) to phone');
      setTimeout(() => { wrap.style.display = 'none'; fill.style.width = '0%'; }, 1500);
    }

    function uploadSingleFile(file, onProgress) {
      return new Promise(resolve => {
        const xhr = new XMLHttpRequest();
        const start = performance.now();
        xhr.open('POST', '/upload?filename=' + encodeURIComponent(file.name) + '&pin=' + encodeURIComponent(PIN));
        xhr.setRequestHeader('X-Arca-Drop-Pin', PIN);
        xhr.upload.onprogress = e => {
          if (e.lengthComputable && e.total > 0) {
            const pct = Math.round((e.loaded * 100) / e.total);
            const elapsedSec = Math.max((performance.now() - start) / 1000, 0.1);
            const mbps = (e.loaded / (1024 * 1024)) / elapsedSec;
            onProgress(pct, mbps);
          }
        };
        xhr.onload = () => resolve(xhr.status >= 200 && xhr.status < 300);
        xhr.onerror = () => resolve(false);
        xhr.send(file);
      });
    }

    renderFiles();
  </script>
</body>
</html>
        """.trimIndent()
    }

    private fun escapeJson(raw: String): String {
        val sb = StringBuilder(raw.length + 16)
        for (ch in raw) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '<' -> sb.append("\\u003C")
                '>' -> sb.append("\\u003E")
                '&' -> sb.append("\\u0026")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
