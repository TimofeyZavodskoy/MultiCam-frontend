package ru.hotdog.multicam.ui.component

import android.graphics.Color
import android.view.MotionEvent
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import android.util.Base64
import android.widget.FrameLayout
import java.util.Locale

// Рендерит Markdown и LaTeX через WebView с цветами текущей темы.
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
                settings.useWideViewPort = true
                settings.defaultTextEncodingName = "UTF-8"
                settings.setSupportZoom(false)
                settings.textZoom = 100
                setBackgroundColor(Color.TRANSPARENT)
                isFocusable = false
                isFocusableInTouchMode = false
                isHorizontalScrollBarEnabled = true
                isVerticalScrollBarEnabled = false
                overScrollMode = WebView.OVER_SCROLL_IF_CONTENT_SCROLLS

                // Разделяем горизонтальный и вертикальный жест
                var downX = 0f
                var downY = 0f
                setOnTouchListener { view, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = event.x
                            downY = event.y
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val isHorizontal = kotlin.math.abs(event.x - downX) >
                                    kotlin.math.abs(event.y - downY)
                            view.parent?.requestDisallowInterceptTouchEvent(isHorizontal)
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                    false
                }

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

// Выбирает простой Text или MarkdownText для результата анализа.
@Composable
fun ResultText(text: String, modifier: Modifier = Modifier) {
    if (text.needsRichTextRenderer()) {
        // Markdown или LaTeX — рендерим через WebView
        MarkdownText(markdown = text, modifier = modifier)
    } else {
        // Обычный текст — нативный Compose Text с нормальным переносом строк
        // softWrap = true (дефолт) — строки переносятся автоматически
        // НЕ используем horizontalScroll — он был причиной обрезания
        Text(
            text     = text,
            modifier = modifier.fillMaxWidth(),
            style    = MaterialTheme.typography.bodyMedium,
            color    = MaterialTheme.colorScheme.onSurface,
            // softWrap по умолчанию true — оставляем, не переопределяем
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ДЕТЕКТОРЫ РАЗМЕТКИ
// ─────────────────────────────────────────────────────────────────────────────

// Проверяет, нужен ли rich-renderer для Markdown или LaTeX.
private fun String.needsRichTextRenderer(): Boolean {
    return containsLatex() || containsMarkdown()
}

// Находит признаки LaTeX-формул в строке.
private fun String.containsLatex(): Boolean {
    return contains("$$") ||
            contains("\\(") ||
            contains("\\)") ||
            contains("\\[") ||
            contains("\\]") ||
            // Одиночный $: не начало $$, хотя бы 1 символ внутри, без переноса
            Regex("""(^|[^$])\$[^$\n]+\$""").containsMatchIn(this)
}

// Находит признаки Markdown-разметки в строке.
private fun String.containsMarkdown(): Boolean {
    return lineSequence().any { line ->
        val trimmed = line.trimStart()
        trimmed.startsWith("#") ||
                trimmed.startsWith("- ") ||
                trimmed.startsWith("* ") ||
                trimmed.startsWith("> ") ||
                Regex("""^\d+\.\s+""").containsMatchIn(trimmed) ||
                Regex("""(\*\*|__|`|\[.+]\(.+\))""").containsMatchIn(trimmed)
    }
}

// Преобразует ARGB-цвет Android в CSS hex RGB.
private fun intToHexColor(color: Int) =
    String.format(Locale.US, "#%06X", 0xFFFFFF and color)

// Собирает HTML-документ для безопасного рендера Markdown и LaTeX в WebView.
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
      overflow-x: auto;
      width: 100%;
    }
    body {
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      font-size: 15px;
      line-height: 1.65;
      color: $textColor;
      background: transparent;
      margin: 0;
      padding: 0;
      /* Принудительный перенос слов — строки не выходят за пределы экрана */
      word-wrap: break-word;
      overflow-wrap: break-word;
      word-break: break-word;
    }
    h1, h2, h3, h4 { color: $headingColor; margin: 14px 0 6px; }
    h1 { font-size: 20px; }
    h2 { font-size: 18px; }
    h3 { font-size: 16px; }
    h4 { font-size: 15px; }
    p  { margin: 6px 0; word-wrap: break-word; overflow-wrap: break-word; }
    ul, ol { padding-left: 20px; margin: 6px 0; }
    li { margin: 3px 0; word-wrap: break-word; }
    code {
      background: rgba(128,128,128,0.15);
      padding: 2px 6px;
      border-radius: 4px;
      font-family: monospace;
      font-size: 0.88em;
      color: $codeColor;
      /* inline code тоже переносится */
      word-break: break-all;
    }
    /* Блоки кода — горизонтальный скролл разрешён */
    pre {
      overflow-x: auto;
      -webkit-overflow-scrolling: touch;
    }
    pre code {
      display: block;
      padding: 10px;
      /* pre code НЕ переносится — горизонтальный скролл */
      word-break: normal;
      white-space: pre;
      width: max-content;
      min-width: 100%;
    }

    /* Формулы: широкие — с горизонтальным скроллом */
    .katex { font-size: 1em; }
    .katex-display {
      margin: 12px 0;
      overflow-x: auto;
      overflow-y: hidden;
      -webkit-overflow-scrolling: touch;
      text-align: center;
      width: 100%;
    }
    .katex-display > .katex {
      display: inline-block;
      white-space: nowrap;
      min-width: max-content;
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

    // Защищаем LaTeX от marked.js
    var mathStore = [];
    function saveMath(str) {
      var id = '\x02MATH' + mathStore.length + '\x03';
      mathStore.push(str);
      return id;
    }

    var protectedMarkdown = rawMarkdown
      .replace(/\$\$[\s\S]*?\$\$/g, function(m) { return saveMath(m); })
      .replace(/\\\[[\s\S]*?\\\]/g, function(m) { return saveMath(m); })
      .replace(/\\\([\s\S]*?\\\)/g, function(m) { return saveMath(m); })
      .replace(/(^|[^$])\$(?!\$)([^$\n]+?)\$/g, function(m, prefix) {
        return prefix + saveMath(m.substring(prefix.length));
      });

    marked.setOptions({ breaks: true, gfm: true });
    var html = marked.parse(protectedMarkdown);

    // Возвращаем LaTeX
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
  </script>
</body>
</html>
""".trimIndent()