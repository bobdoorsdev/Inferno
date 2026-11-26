package to.eyed.inferno.ui.chat

import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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

private fun runnerDocument(source: String, language: String): String {
    val csp = """<meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src data: blob:; style-src 'unsafe-inline'; script-src 'unsafe-inline'; font-src data:">"""
    return when (language) {
        "svg", "xml" -> {
            val body = if (source.trimStart().startsWith("<svg", ignoreCase = true)) source
                else "<pre style='white-space:pre-wrap;color:#ddd'>${escapeHtml(source)}</pre>"
            "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>$csp</head><body style='margin:0;background:#171717;color:white'>$body</body></html>"
        }
        "javascript", "js" -> {
            val safe = source.replace(Regex("(?i)</script"), "<\\\\/script")
            """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">$csp
<style>body{background:#171717;color:#eee;font:14px monospace;padding:14px}pre{white-space:pre-wrap;overflow-wrap:anywhere}</style></head>
<body><b>JavaScript output</b><pre id="out"></pre><script>
const out=document.getElementById('out');console.log=(...v)=>out.textContent+=v.join(' ')+'\n';
try{$safe}catch(e){out.textContent+='Error: '+(e&&e.stack||e)}
</script></body></html>"""
        }
        "css" -> """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">$csp
<style>$source</style></head><body><h2>CSS preview</h2><p>Sample text</p><button>Button</button><div class="preview">Preview element</div></body></html>"""
        else -> {
            when {
                Regex("(?i)<head\\b").containsMatchIn(source) ->
                    Regex("(?i)<head\\b[^>]*>").replaceFirst(source) { it.value + csp }
                Regex("(?i)<html\\b").containsMatchIn(source) ->
                    Regex("(?i)<html\\b[^>]*>").replaceFirst(source) { it.value + "<head>$csp</head>" }
                else -> "<!doctype html><html><head>$csp</head><body style='margin:0'>$source</body></html>"
            }
        }
    }
}

private fun escapeHtml(value: String): String = value
    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
