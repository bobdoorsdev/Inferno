package to.eyed.inferno.ui.chat

import android.view.MotionEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import to.eyed.inferno.ui.theme.Ink
import to.eyed.inferno.ui.theme.Typography

/**
 * Browser-format code preview. The WebView has no JavaScript bridge, no file/content access,
 * no network access and cannot navigate away. This is a preview sandbox, not a general shell.
 */
@Composable
internal fun CodeRunnerDialog(code: String, language: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lang = language.orEmpty().trim().lowercase().substringBefore(' ')
    val document = remember(code, lang) { runnerDocument(code, lang) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Ink.SurfaceHigh),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Code preview", style = Typography.titleMedium, color = Ink.White)
                        Text("Isolated WebView · network and file access blocked", style = Typography.bodySmall, color = Ink.I500)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Lucide.X, contentDescription = "Close preview", tint = Ink.I100)
                    }
                }
                AndroidView(
                    modifier = Modifier.fillMaxWidth().height(520.dp),
                    factory = {
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.domStorageEnabled = false
                            settings.databaseEnabled = false
                            settings.javaScriptCanOpenWindowsAutomatically = false
                            settings.setSupportMultipleWindows(false)
                            settings.blockNetworkLoads = true
                            settings.blockNetworkImage = true
                            webChromeClient = WebChromeClient()
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = true
                                override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean = true
                            }
                        }
                    },
                    update = { view ->
                        if (view.tag != document) {
                            view.tag = document
                            view.loadDataWithBaseURL("https://inferno.local/", document, "text/html", "UTF-8", null)
                        }
                    },
                    onRelease = { it.stopLoading(); it.destroy() },
                )
            }
        }
    }
}


/** Inline preview displayed in the same code card; no external browser tab or dialog. */
@Composable
internal fun CodeRunnerInline(code: String, language: String?, modifier: Modifier = Modifier) {
    val lang = language.orEmpty().trim().lowercase().substringBefore(' ')
    val document = remember(code, lang) { runnerDocument(code, lang) }
    AndroidView(
        modifier = modifier.fillMaxWidth().height(380.dp),
        factory = { viewContext ->
            WebView(viewContext).apply {
                setBackgroundColor(android.graphics.Color.rgb(23, 23, 23))
                isVerticalScrollBarEnabled = true
                isHorizontalScrollBarEnabled = true
                isNestedScrollingEnabled = true
                overScrollMode = View.OVER_SCROLL_NEVER
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.domStorageEnabled = false
                settings.databaseEnabled = false
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.setSupportMultipleWindows(false)
                settings.blockNetworkLoads = true
                settings.blockNetworkImage = true
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = true
                    override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean = true
                }
                var lastY = 0f
                setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            lastY = event.y
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val direction = if (lastY - event.y > 0f) 1 else -1
                            view.parent?.requestDisallowInterceptTouchEvent(view.canScrollVertically(direction))
                            lastY = event.y
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    false
                }
            }
        },
        update = { view ->
            if (view.tag != document) {
                view.tag = document
                view.loadDataWithBaseURL("https://inferno.invalid/", document, "text/html", "UTF-8", null)
            }
        },
        onRelease = { it.stopLoading(); it.destroy() },
    )
}

private fun runnerDocument(source: String, language: String): String {
    val csp = """<meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src data: blob:; style-src 'unsafe-inline'; script-src 'unsafe-inline'; font-src data:">"""
    val baseStyle = """<style>
:root{color-scheme:dark;background:#171717}
html{width:100%;min-height:100%;margin:0!important;background:#171717!important;overflow-x:hidden}
body{display:flow-root;width:100%;min-height:100vh;margin:0!important;box-sizing:border-box;background:#171717!important;color:#eeeeee!important;font:14px system-ui,sans-serif}
*,*::before,*::after{box-sizing:border-box}
img,svg,canvas,video{max-width:100%;height:auto}
svg{display:block;max-width:100%;height:auto}
pre{white-space:pre-wrap;overflow-wrap:anywhere}a{color:#a9c7ff}
</style>"""
    return when (language) {
        "svg", "xml" -> {
            val body = if (source.trimStart().startsWith("<svg", ignoreCase = true)) {
                source.replaceFirst(Regex("(?i)<svg\\b")) { it.value + " style='display:block;max-width:100%;height:auto'" }
            } else "<pre style='white-space:pre-wrap;color:#ddd;margin:0'>${escapeHtml(source)}</pre>"
            "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=5'>$csp$baseStyle</head><body>$body</body></html>"
        }
        "javascript", "js" -> {
            val safe = source.replace(Regex("(?i)</script"), "<\\\\/script")
            """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=5">$csp$baseStyle
<style>body{font:14px monospace;padding:14px}pre{white-space:pre-wrap;overflow-wrap:anywhere}</style></head>
<body><b>JavaScript output</b><pre id="out"></pre><script>
const out=document.getElementById('out');console.log=(...v)=>out.textContent+=v.join(' ')+'\n';
try{$safe}catch(e){out.textContent+='Error: '+(e&&e.stack||e)}
</script></body></html>"""
        }
        "css" -> """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=5">$csp
$baseStyle<style>$source</style></head><body><main style="padding:16px"><h2>CSS preview</h2><p>Sample text</p><button>Button</button><div class="preview">Preview element</div></main></body></html>"""
        else -> {
            val headTag = Regex("(?i)<head\\b[^>]*>").find(source)?.value
            val htmlTag = Regex("(?i)<html\\b[^>]*>").find(source)?.value
            val bodyTag = Regex("(?i)<body\\b[^>]*>").find(source)?.value
            when {
                headTag != null -> source.replaceFirst(headTag, headTag + csp + baseStyle)
                htmlTag != null -> source.replaceFirst(htmlTag, htmlTag + "<head>$csp$baseStyle</head>")
                bodyTag != null -> "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=5'>$csp$baseStyle</head>$source</html>"
                else -> "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=5'>$csp$baseStyle</head><body>$source</body></html>"
            }
        }
    }
}

private fun escapeHtml(value: String): String = value
    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
