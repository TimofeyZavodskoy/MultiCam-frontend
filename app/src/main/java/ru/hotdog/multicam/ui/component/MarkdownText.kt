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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import android.util.Base64
import android.widget.FrameLayout
import java.util.Locale

@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    // Подтягиваем цвета из темы, чтобы HTML-рендер не выглядел чужеродным внутри Compose-экрана.
    val textColor    = intToHexColor(MaterialTheme.colorScheme.onSurface.toArgb())
    val headingColor = intToHexColor(MaterialTheme.colorScheme.primary.toArgb())
    val codeColor    = intToHexColor(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f).toArgb())

    // Формируем HTML только когда меняется сам markdown или палитра темы.
    val htmlContent = remember(markdown, textColor, headingColor) {
        // Переводим в base64, чтобы безопасно прокинуть текст через JS/HTML без экранирования вручную.
        val base64 = Base64.encodeToString(markdown.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        buildHtml(base64, textColor, headingColor, codeColor)
    }

    // WebView здесь выступает как мини-рендерер markdown + KaTeX.
    AndroidView(
        modifier = modifier.fillMaxWidth().wrapContentHeight(),
        factory = { context ->
            WebView(context).apply {
                // marked и KaTeX работают через JS, поэтому движок должен быть включён.
                settings.javaScriptEnabled = true
                // Эти два флага помогают контенту уложиться в ширину карточки без ручного масштабирования.
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.defaultTextEncodingName = "UTF-8"
                // Пользовательский зум не нужен, размер контролируется CSS и layout-ом.
                settings.setSupportZoom(false)
                settings.textZoom = 100
                // Прозрачный фон позволяет WebView вести себя как обычный блок в Compose.
                setBackgroundColor(Color.TRANSPARENT)
                isFocusable = false
                isFocusableInTouchMode = false
                // Горизонтальный скролл нужен для длинных формул и блоков кода.
                isHorizontalScrollBarEnabled = true
                isVerticalScrollBarEnabled = false
                overScrollMode = WebView.OVER_SCROLL_IF_CONTENT_SCROLLS
                // Родительский скролл надо отключать только когда жест реально горизонтальный.
                var downX = 0f
                var downY = 0f
                setOnTouchListener { view, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            // Запоминаем точку касания для определения направления свайпа.
                            downX = event.x
                            downY = event.y
                            // Пока жест не классифицирован, не даём родителю перехватить его раньше времени.
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                        MotionEvent.ACTION_MOVE -> {
                            // Если движение идёт вбок, оставляем жест внутри WebView.
                            val isHorizontalDrag = kotlin.math.abs(event.x - downX) >
                                    kotlin.math.abs(event.y - downY)
                            view.parent?.requestDisallowInterceptTouchEvent(isHorizontalDrag)
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            // После завершения жеста возвращаем управление родителю.
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                    // Возвращаем false, чтобы сам WebView тоже продолжил стандартную обработку события.
                    false
                }
                // Особая логика навигации внутри этой встраиваемой страницы не нужна.
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

@Composable
fun ResultText(text: String, modifier: Modifier = Modifier) {
    // Markdown/LaTeX рендерим только тогда, когда в строке действительно есть разметка.
    if (text.needsRichTextRenderer()) {
        MarkdownText(markdown = text, modifier = modifier)
    } else {
        // Обычный текст дешевле и предсказуемее, поэтому его не нужно гонять через WebView.
        Text(
            text = text,
            modifier = modifier
                .fillMaxWidth()
                // Длинные строки можно пролистывать вбок, не ломая карточку.
                .horizontalScroll(rememberScrollState()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            softWrap = false,
            overflow = TextOverflow.Visible
        )
    }
}

// Быстрый фильтр: решает, нужен ли нам тяжёлый html/webview-рендерер.
private fun String.needsRichTextRenderer(): Boolean {
    return containsLatex() || containsMarkdown()
}

// Ищем привычные формы LaTeX-разметки: display, inline и простые одиночные формулы.
private fun String.containsLatex(): Boolean {
    return contains("$$") ||
            contains("\\(") ||
            contains("\\)") ||
            contains("\\[") ||
            contains("\\]") ||
            Regex("""(^|[^$])\$[^$\n]+\$""").containsMatchIn(this)
}

// Markdown распознаётся по признакам разметки, а не по "магии" внутри текста.
private fun String.containsMarkdown(): Boolean {
    return lineSequence().any { line ->
        val trimmed = line.trimStart()
        trimmed.startsWith("#") ||
                trimmed.startsWith("- ") ||
                trimmed.startsWith("* ") ||
                trimmed.startsWith(">") ||
                Regex("""^\d+\.\s+""").containsMatchIn(trimmed) ||
                Regex("""(\*\*|__|`|\[.+]\(.+\))""").containsMatchIn(trimmed)
    }
}

// WebView ждёт обычную hex-строку, поэтому конвертируем Compose Color в формат #RRGGBB.
private fun intToHexColor(color: Int) =
    String.format(Locale.US, "#%06X", 0xFFFFFF and color)

// HTML-шаблон — это изолированный мини-рендерер markdown с отдельной обработкой формул.
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
  <!-- Запрещаем встроенное масштабирование страницы, чтобы управление размером оставалось у layout. -->
  <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">

  <!-- marked превращает markdown в HTML. -->
  <script src="https://cdn.jsdelivr.net/npm/marked@9/marked.min.js"></script>

  <!-- KaTeX нужен для LaTeX-формул, которые marked сам не понимает. -->
  <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.css">
  <script src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.js"></script>
  <script src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/contrib/auto-render.min.js"></script>

  <style>
    /* Базовый контейнер разрешает горизонтальный скролл для длинного контента. */
    html, body {
      overflow-x: auto;
      width: 100%;
    }
    /* Тело страницы наследует общую типографику приложения. */
    body {
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      font-size: 16px;
      line-height: 1.6;
      color: $textColor;
      background: transparent;
      margin: 0; padding: 0;
    }
    /* Заголовки просто подсвечиваем цветом акцента. */
    h1, h2, h3, h4 { color: $headingColor; margin: 14px 0 6px; }
    h1 { font-size: 22px; }
    h2 { font-size: 20px; }
    h3 { font-size: 18px; }
    h4 { font-size: 16px; }
    p  { margin: 6px 0; }
    ul, ol { padding-left: 20px; margin: 6px 0; }
    li { margin: 3px 0; }
    /* Inline code должен читаться, но не раздувать строку. */
    code {
      background: rgba(128,128,128,0.15);
      padding: 2px 6px;
      border-radius: 4px;
      font-family: monospace;
      font-size: 0.88em;
      color: $codeColor;
    }
    /* Блоки кода не сжимаем, а даём им горизонтальный скролл. */
    pre {
      overflow-x: auto;
      -webkit-overflow-scrolling: touch;
    }
    pre code {
      display: block;
      padding: 10px;
      width: max-content;
      min-width: 100%;
      white-space: pre;
    }

    /* Формулы не обрезаем: широкие выражения можно свайпать вбок. */
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

    /* Остальная markdown-разметка рендерится обычными браузерными стилями. */
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
    // Декодируем исходный текст обратно из base64.
    function decodeBase64Utf8(b64) {
      var binary = atob(b64);
      var bytes = new Uint8Array(binary.length);
      for (var i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
      return new TextDecoder('utf-8').decode(bytes);
    }

    var rawMarkdown = decodeBase64Utf8("$base64Markdown");

    // Сюда временно складываем формулы, чтобы markdown-парсер их не поломал.
    var mathStore = [];

    // Каждой формуле выдаём временный маркер.
    function saveMath(str) {
      var id = '\x02MATH' + mathStore.length + '\x03';
      mathStore.push(str);
      return id;
    }

    // Сначала вытаскиваем все варианты LaTeX, потом запускаем markdown.
    var protectedMarkdown = rawMarkdown
      .replace(/\$\$[\s\S]*?\$\$/g, function(m) { return saveMath(m); })
      .replace(/\\\[[\s\S]*?\\\]/g, function(m) { return saveMath(m); })
      .replace(/\\\([\s\S]*?\\\)/g, function(m) { return saveMath(m); })
      .replace(/(^|[^$])\$(?!\$)([^$\n]+?)\$/g, function(m, prefix) {
        return prefix + saveMath(m.substring(prefix.length));
      });

    // Рендерим markdown в HTML.
    marked.setOptions({ breaks: true, gfm: true });
    var html = marked.parse(protectedMarkdown);

    // Возвращаем формулы обратно на их места.
    html = html.replace(/\x02MATH(\d+)\x03/g, function(_, idx) {
      return mathStore[parseInt(idx, 10)];
    });

    // Вставляем готовый HTML в страницу.
    document.getElementById('content').innerHTML = html;

    // После вставки HTML добираем оставшиеся формулы через автозамену KaTeX.
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
