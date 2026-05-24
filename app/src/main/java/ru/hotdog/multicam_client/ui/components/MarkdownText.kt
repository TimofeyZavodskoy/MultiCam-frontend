package ru.hotdog.multicam_client.ui.components

import android.graphics.Color
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import android.util.Base64
import java.util.Locale

@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val textColor    = intToHexColor(MaterialTheme.colorScheme.onSurface.toArgb())
    val headingColor = intToHexColor(MaterialTheme.colorScheme.primary.toArgb())
    val codeColor    = intToHexColor(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f).toArgb())

    // Передаём сырой markdown через base64 — никаких проблем с экранированием
    val htmlContent = remember(markdown, textColor, headingColor) {
        val base64 = Base64.encodeToString(markdown.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        buildHtml(base64, textColor, headingColor, codeColor)
    }

    AndroidView(
        modifier = modifier.fillMaxWidth().wrapContentHeight(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = false
                settings.defaultTextEncodingName = "UTF-8"
                settings.setSupportZoom(false)
                settings.textZoom = 100
                setBackgroundColor(Color.TRANSPARENT)
                isFocusable = false
                isFocusableInTouchMode = false
                webViewClient = WebViewClient()
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
                )
            }
        },
        update = { webView ->
            val hash = htmlContent.hashCode()
            if (webView.tag as? Int != hash) {
                webView.tag = hash
                webView.loadDataWithBaseURL(
                    "https://cdn.jsdelivr.net", // baseUrl нужен чтобы CDN-скрипты грузились
                    htmlContent,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        }
    )
}

private fun intToHexColor(color: Int) =
    String.format(Locale.US, "#%06X", 0xFFFFFF and color)

private fun buildHtml(
    base64Markdown: String,
    textColor: String,
    headingColor: String,
    codeColor: String
) = """
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">

  <!-- marked.js — полноценный Markdown-парсер -->
  <script src="https://cdn.jsdelivr.net/npm/marked@9/marked.min.js"></script>

  <!-- KaTeX -->
  <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.css">
  <script src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.js"></script>
  <script src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/contrib/auto-render.min.js"></script>

  <style>
    body {
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      font-size: 16px;
      line-height: 1.6;
      color: $textColor;
      background: transparent;
      margin: 0; padding: 0;
    }
    h1, h2, h3, h4 { color: $headingColor; margin: 14px 0 6px; }
    h1 { font-size: 22px; }
    h2 { font-size: 20px; }
    h3 { font-size: 18px; }
    h4 { font-size: 16px; }
    p  { margin: 6px 0; }
    ul, ol { padding-left: 20px; margin: 6px 0; }
    li { margin: 3px 0; }
    code {
      background: rgba(128,128,128,0.15);
      padding: 2px 6px;
      border-radius: 4px;
      font-family: monospace;
      font-size: 0.88em;
      color: $codeColor;
    }
    pre code { display: block; padding: 10px; overflow-x: auto; }
    .katex          { font-size: 1.1em; }
    .katex-display  { margin: 12px 0; overflow-x: auto; text-align: center; }
    strong { font-weight: 700; }
    em     { font-style: italic; }
    hr     { border: none; border-top: 1px solid rgba(128,128,128,0.3); margin: 12px 0; }
    blockquote {
      border-left: 3px solid $headingColor;
      margin: 8px 0;
      padding: 4px 12px;
      opacity: 0.85;
    }
  </style>
</head>
<body>
  <div id="content"></div>

  <script>
    // Декодируем base64 → UTF-8 строку (поддержка кириллицы и LaTeX)
    function decodeBase64Utf8(b64) {
      var binary = atob(b64);
      var bytes = new Uint8Array(binary.length);
      for (var i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
      return new TextDecoder('utf-8').decode(bytes);
    }

    var rawMarkdown = decodeBase64Utf8("$base64Markdown");

    // 1. Рендерим Markdown → HTML через marked.js
    //    (он корректно обрабатывает *, **, #, списки и т.д.)
    marked.setOptions({ breaks: true, gfm: true });
    document.getElementById('content').innerHTML = marked.parse(rawMarkdown);

    // 2. Рендерим LaTeX через KaTeX auto-render
    //    (ПОСЛЕ markdown-рендера, чтобы $...$ внутри <p> тоже обработались)
    renderMathInElement(document.body, {
      delimiters: [
        { left: "\$\$", right: "\$\$", display: true  },
        { left: "\$",   right: "\$",   display: false },
        { left: "\\\\[", right: "\\\\]", display: true  },
        { left: "\\\\(", right: "\\\\)", display: false }
      ],
      throwOnError: false,
      trust: true
    });
  </script>
</body>
</html>
""".trimIndent()