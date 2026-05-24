package com.example.multicam.ui.component

import android.graphics.Color
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import android.util.Base64
import android.widget.FrameLayout
import java.util.Locale

@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val textColor    = intToHexColor(MaterialTheme.colorScheme.onSurface.toArgb())
    val headingColor = intToHexColor(MaterialTheme.colorScheme.primary.toArgb())
    val codeColor    = intToHexColor(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f).toArgb())

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
                isHorizontalScrollBarEnabled = false
                isVerticalScrollBarEnabled = false
                overScrollMode = WebView.OVER_SCROLL_NEVER
                webViewClient = WebViewClient()
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            }
        },
        update = { webView ->
            val hash = htmlContent.hashCode()
            if (webView.tag as? Int != hash) {
                webView.tag = hash
                webView.loadDataWithBaseURL(
                    "https://cdn.jsdelivr.net",
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
  <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">

  <script src="https://cdn.jsdelivr.net/npm/marked@9/marked.min.js"></script>

  <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.css">
  <script src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.js"></script>
  <script src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/contrib/auto-render.min.js"></script>

  <style>
    html, body {
      overflow-x: hidden;
      width: 100%;
    }
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

    /* Формулы: убираем внутренний скролл, масштабируем через JS */
    .katex { font-size: 1em; }
    .katex-display {
      margin: 12px 0;
      overflow-x: visible;
      text-align: center;
      width: 100%;
    }
    .katex-display > .katex {
      white-space: normal;
      max-width: 100%;
    }

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
    function decodeBase64Utf8(b64) {
      var binary = atob(b64);
      var bytes = new Uint8Array(binary.length);
      for (var i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
      return new TextDecoder('utf-8').decode(bytes);
    }

    var rawMarkdown = decodeBase64Utf8("$base64Markdown");

    var mathStore = [];

    function saveMath(str) {
      var id = '\x02MATH' + mathStore.length + '\x03';
      mathStore.push(str);
      return id;
    }

    var protected = rawMarkdown
      .replace(/\$\$[\s\S]*?\$\$/g, function(m) { return saveMath(m); })
      .replace(/\\\[[\s\S]*?\\\]/g, function(m) { return saveMath(m); })
      .replace(/\\\([\s\S]*?\\\)/g, function(m) { return saveMath(m); })
      .replace(/(?<!\$)\$(?!\$)([^$\n]+?)\$/g, function(m) { return saveMath(m); });

    marked.setOptions({ breaks: true, gfm: true });
    var html = marked.parse(protected);

    html = html.replace(/\x02MATH(\d+)\x03/g, function(_, idx) {
      return mathStore[parseInt(idx, 10)];
    });

    document.getElementById('content').innerHTML = html;

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

    // Масштабируем широкие формулы чтобы они влезали в экран
    var containerWidth = document.body.clientWidth;
    document.querySelectorAll('.katex-display').forEach(function(el) {
      var elWidth = el.scrollWidth;
      if (elWidth > containerWidth && containerWidth > 0) {
        var scale = containerWidth / elWidth;
        el.style.transform = 'scale(' + scale + ')';
        el.style.transformOrigin = 'left top';
        // Компенсируем уменьшение высоты после scale
        el.style.marginBottom = ((el.offsetHeight * scale) - el.offsetHeight) + 'px';
      }
    });
  </script>
</body>
</html>
""".trimIndent()