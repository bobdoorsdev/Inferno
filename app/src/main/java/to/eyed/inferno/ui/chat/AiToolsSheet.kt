package to.eyed.inferno.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import to.eyed.inferno.ui.components.Hairline
import to.eyed.inferno.ui.components.InfernoSheet
import to.eyed.inferno.ui.components.SheetHeader
import to.eyed.inferno.ui.theme.Ink
import to.eyed.inferno.ui.theme.Radii
import to.eyed.inferno.ui.theme.Typography

private data class AiToolShortcut(val title: String, val description: String, val prompt: String)

private val aiToolShortcuts = listOf(
    AiToolShortcut("Summarize", "Condense long text without losing important details.", "Summarize the following content. Preserve key facts, numbers, caveats, and nuance. Use concise headings where useful.\n\n"),
    AiToolShortcut("Translate", "Translate while preserving tone and formatting.", "Translate the following text into the target language I specify. Preserve meaning, tone, names, and formatting. If I have not specified a target language, ask me first.\n\n"),
    AiToolShortcut("Rewrite", "Improve clarity, flow, or tone without changing meaning.", "Rewrite the following text for clarity and flow while preserving its meaning and factual claims. Do not invent details.\n\n"),
    AiToolShortcut("Proofread", "Correct grammar, spelling, punctuation, and phrasing.", "Proofread the following text. Correct grammar, spelling, punctuation, and awkward phrasing while preserving my voice. Return the corrected text first.\n\n"),
    AiToolShortcut("Explain simply", "Explain a difficult topic in plain language.", "Explain the following topic in plain language. Start with the core idea, define technical terms, and give a concrete example.\n\n"),
    AiToolShortcut("Step-by-step tutor", "Teach a topic progressively with examples.", "Teach me the following step by step. Identify prerequisites, explain each stage, include a worked example, then give me a short knowledge check.\n\n"),
    AiToolShortcut("Solve a problem", "Show the method, assumptions, and verification.", "Solve the following problem carefully. Show the method, state assumptions, verify the result, and avoid skipping important steps.\n\n"),
    AiToolShortcut("Brainstorm", "Generate varied, practical ideas and rank them.", "Brainstorm practical ideas for the following goal. Group different approaches, explain trade-offs, and rank the strongest options.\n\n"),
    AiToolShortcut("Compare options", "Compare alternatives with consistent criteria.", "Compare the following options using consistent criteria. Provide a concise comparison table and explain which option is best for each use case.\n\n"),
    AiToolShortcut("Pros and cons", "Evaluate benefits, risks, and trade-offs.", "Analyze the following choice. Separate advantages, disadvantages, risks, hidden costs, and trade-offs, then give a balanced conclusion.\n\n"),
    AiToolShortcut("Create a plan", "Turn a goal into an actionable sequence.", "Create a practical plan for this goal. Break it into ordered phases, concrete tasks, dependencies, and measurable completion criteria.\n\n"),
    AiToolShortcut("Action items", "Extract tasks, owners, and deadlines from notes.", "Extract actionable tasks from the following notes. Return a table with task, owner (only if stated), deadline (only if stated), and dependencies. Do not invent missing details.\n\n"),
    AiToolShortcut("Meeting notes", "Turn rough notes into structured minutes.", "Turn the following rough notes into structured meeting minutes with a summary, decisions, open questions, and action items. Do not fabricate attendees or decisions.\n\n"),
    AiToolShortcut("Extract to JSON", "Convert unstructured information into valid JSON.", "Extract the information below into valid JSON. Preserve source values, use consistent types, represent unknown values as null, and return JSON only in one fenced code block.\n\n"),
    AiToolShortcut("Generate CSV", "Create clean CSV data for spreadsheets.", "Convert the requirements or data below into valid CSV. Include a header row, quote fields correctly, and return only the CSV in one fenced code block.\n\n"),
    AiToolShortcut("Make a table", "Organize facts into a readable data table.", "Organize the following information into a clear Markdown table. Choose useful columns, preserve all values, and do not infer missing data.\n\n"),
    AiToolShortcut("Write code", "Generate complete, documented code.", "Write a complete solution for the following programming task. State assumptions briefly, include necessary error handling, and return the complete code in a single correctly labelled fenced block.\n\n"),
    AiToolShortcut("Debug code", "Find bugs and return a corrected implementation.", "Debug the following code. Identify the root cause, explain the smallest safe fix, and return the corrected complete code in one fenced block.\n\n"),
    AiToolShortcut("Review code", "Review correctness, performance, security, and maintainability.", "Review the following code for correctness, edge cases, performance, security, and maintainability. Prioritize concrete bugs, then suggest targeted improvements.\n\n"),
    AiToolShortcut("Create a downloadable file", "Generate complete file contents that can be saved from a code block.", "Create the requested file. Choose the best file format, state a suggested filename, and return the complete file contents in exactly one fenced code block using the correct language tag (for example json, csv, html, md, lua, py, or kt). Do not use placeholders or omit sections.\n\n"),
)

@Composable
fun AiToolsSheet(onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    InfernoSheet(onDismiss = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SheetHeader("AI tools", Lucide.X, onLeading = onDismiss)
            Text(
                "Choose a workflow to insert a ready-to-use instruction. Your currently loaded on-device model performs the task.",
                style = Typography.bodySmall,
                color = Ink.I500,
            )
            Spacer(Modifier.height(2.dp))
            aiToolShortcuts.forEachIndexed { index, tool ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.control))
                        .background(Ink.SurfaceHigh).clickable { onSelect(tool.prompt) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(tool.title, style = Typography.bodyLarge, color = Ink.White)
                    Spacer(Modifier.height(3.dp))
                    Text(tool.description, style = Typography.bodySmall, color = Ink.I500)
                }
                if (index != aiToolShortcuts.lastIndex) Hairline()
            }
        }
    }
}
