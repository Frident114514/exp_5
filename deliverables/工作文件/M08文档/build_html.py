"""Build the offline HTML review copy from the task's Markdown source."""

from pathlib import Path
from html import escape
import re
import subprocess
import sys
from urllib.parse import quote

ROOT = Path(__file__).resolve().parents[3]
SOURCE = ROOT / "deliverables/M08_第一周后台实施方案.md"
SHELL = Path(__file__).with_name("document-shell.html")
COMMIT = "8c399d44894e3bd922657f244b31ae1a1237513c"
BASELINE_PATHS = set(subprocess.check_output(["git", "ls-tree", "-r", "--name-only", COMMIT], cwd=ROOT, text=True).splitlines())


def href_for(target):
    if target.startswith(str(ROOT) + "/"):
        relative = target[len(str(ROOT)) + 1:]
        match = re.match(r"(.*):(\d+)$", relative)
        fragment = ""
        if match:
            relative, line = match.groups()
            fragment = "#L" + line
        if relative in BASELINE_PATHS:
            return "https://github.com/Frident114514/exp_5/blob/" + COMMIT + "/" + quote(relative) + fragment
        return "../" + quote(relative) + fragment
    return target


def inline(value):
    tokens = []

    def protect(html):
        token = "\x00" + str(len(tokens)) + "\x00"
        tokens.append(html)
        return token

    value = re.sub(r"`([^`]+)`", lambda m: protect("<code>" + escape(m[1]) + "</code>"), value)
    value = re.sub(r"\[([^\]]+)\]\(([^)]+)\)", lambda m: protect('<a href="' + escape(href_for(m[2]), quote=True) + '">' + escape(m[1]) + "</a>"), value)
    value = escape(value)
    value = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", value)
    for i, token in enumerate(tokens):
        value = value.replace("\x00" + str(i) + "\x00", token)
    return value


def overview():
    # Offline vector rendering of the Mermaid flow defined in the source.
    # The original Mermaid source is also retained below the visual.
    nodes = [
        ("A", 45, 45, 240, 60, "启动后台工程"),
        ("B", 335, 45, 240, 60, "打开管理员登录页"),
        ("C", 625, 45, 240, 60, "调用登录接口"),
        ("D", 625, 160, 240, 60, "认证结果"),
        ("E", 335, 160, 240, 60, "留在登录页并提示"),
        ("F", 625, 275, 240, 60, "保存会话并进入后台布局"),
        ("G", 335, 390, 240, 60, "用户管理查询表格"),
        ("H", 625, 390, 240, 60, "动态管理查询表格"),
        ("I", 45, 390, 240, 60, "统一请求层与分页状态"),
        ("J", 45, 510, 240, 60, "查询结果"),
        ("K", 335, 480, 240, 60, "表格或空结果"),
        ("L", 335, 565, 240, 60, "错误说明与重试"),
    ]
    paths = [
        ("M285 75H335", None), ("M575 75H625", None),
        ("M745 105V160", None), ("M625 190H575", (490, 145, "失败或普通用户")),
        ("M745 220V275", (811, 247, "管理员")),
        ("M745 335V355H455V390", None), ("M745 335V390", None),
        ("M335 420H285", None), ("M745 450V465H165V450", None),
        ("M165 450V510", None), ("M285 530H310V510H335", (310, 493, "成功")),
        ("M285 550H310V595H335", (310, 585, "失败")),
    ]
    s = ['<svg viewBox="0 0 910 675" role="img" aria-labelledby="overview-title overview-desc" xmlns="http://www.w3.org/2000/svg">',
         '<title id="overview-title">第一周管理后台流程</title><desc id="overview-desc">工程启动后登录，仅管理员进入后台布局，两个查询页共用请求和分页状态；成功显示表格或空结果，失败显示重试，401返回登录页。</desc>',
         '<defs><marker id="arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M0 0L8 4L0 8" fill="none" stroke="#5274a6" stroke-width="1.2"/></marker></defs>',
         '<g fill="none" stroke="#5274a6" stroke-width="2" marker-end="url(#arrow)">']
    for path, _ in paths:
        s.append('<path d="' + path + '"/>')
    s.append('<path d="M70 390V315H20V130H455V105" stroke-dasharray="7 5"/>')
    s.append('</g>')
    for node, x, y, width, height, label in nodes:
        is_result = node in ["D", "J"]
        fill = "#d9e9ff" if is_result else "#ffffff"
        s.append(f'<rect x="{x}" y="{y}" width="{width}" height="{height}" rx="12" fill="{fill}" stroke="#90b4e8" stroke-width="1.5"/>')
        s.append(f'<text x="{x+width/2}" y="{y+height/2+7}" text-anchor="middle" fill="#173f73" font-size="19">{escape(label)}</text>')
    for _, label in paths:
        if label:
            x, y, value = label
            s.append(f'<text x="{x}" y="{y}" text-anchor="middle" fill="#46658f" font-size="16">{escape(value)}</text>')
    s.append('<text x="28" y="282" fill="#46658f" font-size="16">401</text></svg>')
    return "".join(s)


def render(source):
    lines = source.splitlines()
    title = lines[0][2:]
    metadata, nav, body = [], [], []
    i = 1
    while i < len(lines):
        line = lines[i]
        if not line.strip():
            i += 1
            continue
        if line.startswith("<!--"):
            i += 1
            continue
        if line.startswith("> "):
            metadata.append("<p>" + inline(line[2:].strip()) + "</p>")
            i += 1
            continue
        if line.startswith("```"):
            language = line[3:].strip()
            code = []
            i += 1
            while i < len(lines) and lines[i] != "```":
                code.append(lines[i])
                i += 1
            payload = "\n".join(code)
            if language == "mermaid":
                body.append('<div class="diagram-card">' + overview() + '</div><details class="diagram-source"><summary>查看可复制的 Mermaid 流程图源码</summary><pre><code>' + escape(payload) + '</code></pre></details>')
            elif language == "text":
                body.append('<div class="call-chain-card"><div class="call-chain-toolbar">纯文本</div><pre><code>' + escape(payload) + '</code></pre></div>')
            else:
                body.append('<pre><code>' + escape(payload) + '</code></pre>')
            i += 1
            continue
        if line.startswith("##"):
            match = re.match(r"(#{2,4}) (.+)", line)
            level = len(match[1])
            heading = match[2]
            anchor = "section-" + str(len(nav) + 1)
            body.append(f'<h{level} id="{anchor}">{inline(heading)}</h{level}>')
            cls = ' class="nested"' if level > 2 else ""
            nav.append(f'<a{cls} href="#{anchor}">{escape(heading)}</a>')
            i += 1
            continue
        if line.startswith("| "):
            rows = []
            while i < len(lines) and lines[i].startswith("| "):
                cells = [x.strip() for x in lines[i].strip().strip("|").split("|")]
                if not all(re.fullmatch(r":?-+:?", x) for x in cells):
                    rows.append(cells)
                i += 1
            header = "<thead><tr>" + "".join("<th scope=\"col\">" + inline(x) + "</th>" for x in rows[0]) + "</tr></thead>"
            content = "<tbody>" + "".join("<tr>" + "".join("<td>" + inline(x) + "</td>" for x in row) + "</tr>" for row in rows[1:]) + "</tbody>"
            body.append('<div class="table-scroll"><table>' + header + content + '</table></div>')
            continue
        if line.startswith("- "):
            items = []
            while i < len(lines) and lines[i].startswith("- "):
                items.append("<li>" + inline(lines[i][2:]) + "</li>")
                i += 1
            body.append("<ul>" + "".join(items) + "</ul>")
            continue
        paragraph = []
        while i < len(lines) and lines[i].strip() and not lines[i].startswith(("#", "| ", "```", "- ", "> ", "<!--")):
            paragraph.append(lines[i].strip())
            i += 1
        body.append("<p>" + inline(" ".join(paragraph)) + "</p>")
    html = SHELL.read_text()
    values = {"TITLE": escape(title), "NAV_TITLE": "第一周实施目录", "NAV_HTML": "\n".join(nav), "METADATA_HTML": "\n".join(metadata), "BODY_HTML": "\n".join(body)}
    for name, value in values.items():
        html = html.replace("{{" + name + "}}", value)
    html = html.replace("</style>", """\n.container { max-width: 1150px; }
h1 { border-bottom: 0; padding-bottom: 0; }
.diagram-card { padding: 8px; }
.diagram-source { font-size: 13px; color: #46658f; margin: -12px 0 22px; }
summary { cursor: pointer; }
th:first-child { min-width: 95px; }
.function-nav .nested { font-size: 12px; padding-top: 6px; padding-bottom: 6px; }
@media print { .diagram-source { display: none; } table { font-size: 10px; } .table-scroll { overflow: visible; } }
</style>""")
    return html


if __name__ == "__main__":
    if len(sys.argv) > 1:
        SOURCE = Path(sys.argv[1]).resolve()
    output = SOURCE.with_suffix(".html")
    output.write_text(render(SOURCE.read_text()), encoding="utf-8")
    print(output)
