package top.wkbin.taixu.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

object SyntaxHighlighter {

    private val ColorDefault = Color(0xFFDCE6F5)
    private val ColorKeyword = Color(0xFFFF7B72)
    private val ColorType = Color(0xFFFFA657)
    private val ColorString = Color(0xFFA5D6FF)
    private val ColorNumber = Color(0xFF79C0FF)
    private val ColorComment = Color(0xFF8B949E)
    private val ColorFunction = Color(0xFFD2A8FF)

    private val KeywordsCommon = setOf(
        "if", "else", "for", "while", "do", "break", "continue", "return", "try",
        "catch", "finally", "throw", "throws", "new", "this", "super", "class",
        "interface", "enum", "val", "var", "fun", "def", "import", "from", "as",
        "package", "public", "private", "protected", "internal", "override", "open",
        "abstract", "final", "const", "let", "function", "async", "await", "yield",
        "export", "default", "struct", "impl", "trait", "fn", "pub", "mut", "use",
        "type", "module", "select", "where", "insert", "update", "delete", "from",
        "and", "or", "not", "in", "is", "null", "nil", "true", "false", "None",
        "True", "False", "self", "echo", "exit", "case", "esac", "fi", "then",
        "sudo", "apt", "npm", "pip", "git", "cd", "ls", "mkdir", "rm", "cp", "mv",
    )

    private val TypesCommon = setOf(
        "Int", "Long", "Float", "Double", "String", "Boolean", "Char", "Byte", "Short",
        "Array", "List", "Map", "Set", "Unit", "Any", "void", "int", "long", "float",
        "double", "char", "boolean", "bool", "str", "list", "dict", "tuple", "set",
        "number", "string", "boolean", "any", "unknown", "never", "object", "i32",
        "i64", "u32", "u64", "f32", "f64", "usize", "isize",
    )

    /**
     * 将代码字符串转换为带有语法着色的 AnnotatedString。
     * 当文本超过限制（如 500 KB）时降级为纯文本，保证超大文件渲染性能。
     */
    fun highlight(text: String, extension: String): AnnotatedString {
        if (text.isEmpty()) return AnnotatedString("")
        if (text.length > MAX_HIGHLIGHT_LENGTH) {
            return buildAnnotatedString {
                append(text)
                addStyle(SpanStyle(color = ColorDefault), 0, text.length)
            }
        }

        return buildAnnotatedString {
            append(text)
            // 默认颜色底色
            addStyle(SpanStyle(color = ColorDefault), 0, text.length)

            val ext = extension.lowercase()
            when (ext) {
                "json" -> highlightJson(text, this)
                "md", "markdown" -> highlightMarkdown(text, this)
                else -> highlightGeneral(text, this, ext)
            }
        }
    }

    private fun highlightGeneral(text: String, builder: AnnotatedString.Builder, ext: String) {
        val len = text.length
        var i = 0

        while (i < len) {
            val c = text[i]

            // 1. 行注释 (// 或 # 或 --)
            if ((c == '/' && i + 1 < len && text[i + 1] == '/') ||
                (c == '#' && (ext in setOf("py", "sh", "bash", "zsh", "yaml", "yml", "toml", "conf", "env"))) ||
                (c == '-' && i + 1 < len && text[i + 1] == '-' && ext == "sql")
            ) {
                val start = i
                while (i < len && text[i] != '\n') i++
                builder.addStyle(SpanStyle(color = ColorComment, fontStyle = FontStyle.Italic), start, i)
                continue
            }

            // 2. 块注释 (/* ... */)
            if (c == '/' && i + 1 < len && text[i + 1] == '*') {
                val start = i
                i += 2
                while (i < len && !(text[i - 1] == '*' && text[i] == '/')) i++
                if (i < len) i++ // 包含最后的 /
                builder.addStyle(SpanStyle(color = ColorComment, fontStyle = FontStyle.Italic), start, i)
                continue
            }

            // 3. 字符串 ("...", '...', `...`)
            if (c == '"' || c == '\'' || c == '`') {
                val quote = c
                val start = i
                i++
                while (i < len) {
                    if (text[i] == '\\' && i + 1 < len) {
                        i += 2
                        continue
                    }
                    if (text[i] == quote) {
                        i++
                        break
                    }
                    if (text[i] == '\n' && quote != '`') break // 单行字符串不能跨行
                    i++
                }
                builder.addStyle(SpanStyle(color = ColorString), start, i)
                continue
            }

            // 4. 数字
            if (c.isDigit()) {
                val start = i
                while (i < len && (text[i].isDigit() || text[i] == '.' || text[i] in "xXbBaAfFL_")) i++
                builder.addStyle(SpanStyle(color = ColorNumber), start, i)
                continue
            }

            // 5. 标识符（关键字、类型、函数名）
            if (c.isLetter() || c == '_') {
                val start = i
                while (i < len && (text[i].isLetterOrDigit() || text[i] == '_')) i++
                val word = text.substring(start, i)
                when {
                    word in KeywordsCommon -> {
                        builder.addStyle(SpanStyle(color = ColorKeyword, fontWeight = FontWeight.Bold), start, i)
                    }
                    word in TypesCommon || (word.first().isUpperCase() && word.length > 1) -> {
                        builder.addStyle(SpanStyle(color = ColorType), start, i)
                    }
                    i < len && text[i] == '(' -> {
                        builder.addStyle(SpanStyle(color = ColorFunction), start, i)
                    }
                }
                continue
            }

            i++
        }
    }

    private fun highlightJson(text: String, builder: AnnotatedString.Builder) {
        val len = text.length
        var i = 0
        while (i < len) {
            val c = text[i]
            if (c == '"') {
                val start = i
                i++
                while (i < len && text[i] != '"') {
                    if (text[i] == '\\' && i + 1 < len) i++
                    i++
                }
                if (i < len) i++ // 包含闭合引号
                // 判断是否是 JSON key (后面紧跟冒号)
                var lookAhead = i
                while (lookAhead < len && text[lookAhead].isWhitespace()) lookAhead++
                if (lookAhead < len && text[lookAhead] == ':') {
                    builder.addStyle(SpanStyle(color = ColorType, fontWeight = FontWeight.Medium), start, i)
                } else {
                    builder.addStyle(SpanStyle(color = ColorString), start, i)
                }
                continue
            }
            if (c.isDigit() || c == '-') {
                val start = i
                i++
                while (i < len && (text[i].isDigit() || text[i] in ".eE+-")) i++
                builder.addStyle(SpanStyle(color = ColorNumber), start, i)
                continue
            }
            if (c.isLetter()) {
                val start = i
                while (i < len && text[i].isLetter()) i++
                val word = text.substring(start, i)
                if (word in setOf("true", "false", "null")) {
                    builder.addStyle(SpanStyle(color = ColorKeyword, fontWeight = FontWeight.Bold), start, i)
                }
                continue
            }
            i++
        }
    }

    private fun highlightMarkdown(text: String, builder: AnnotatedString.Builder) {
        val lines = text.split('\n')
        var offset = 0
        for (line in lines) {
            val trimmed = line.trimStart()
            when {
                trimmed.startsWith("#") -> {
                    builder.addStyle(
                        SpanStyle(color = ColorKeyword, fontWeight = FontWeight.Bold),
                        offset,
                        offset + line.length,
                    )
                }
                trimmed.startsWith("```") -> {
                    builder.addStyle(
                        SpanStyle(color = ColorComment, fontStyle = FontStyle.Italic),
                        offset,
                        offset + line.length,
                    )
                }
                trimmed.startsWith(">") -> {
                    builder.addStyle(
                        SpanStyle(color = ColorType),
                        offset,
                        offset + line.length,
                    )
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") -> {
                    val bulletLen = line.indexOfFirst { it == '-' || it == '*' || it == '+' } + 2
                    if (bulletLen <= line.length) {
                        builder.addStyle(
                            SpanStyle(color = ColorKeyword, fontWeight = FontWeight.Bold),
                            offset,
                            offset + bulletLen,
                        )
                    }
                }
            }
            offset += line.length + 1
        }
    }

    private const val MAX_HIGHLIGHT_LENGTH = 500 * 1024 // 500 KB
}
