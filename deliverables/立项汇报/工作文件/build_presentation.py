from pathlib import Path
import json
import math
import re

from pptx import Presentation
from pptx.util import Inches, Pt
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE, MSO_CONNECTOR
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.oxml.xmlchemy import OxmlElement
import openpyxl


BASE = Path(__file__).resolve().parent.parent
SOURCE = Path('/Users/animenzzz/Downloads/软件工程课程大作业任务分工与进度表.xlsx')
OUT = BASE / '多媒体朋友圈展示系统_项目立项汇报.pptx'
W, H = 13.333333, 7.5
FONT = 'Hiragino Sans GB'
BG = 'F5F7FA'
WHITE = 'FFFFFF'
NAVY = '112A40'
INK = '1C3346'
MUTED = '586D7E'
TEAL = '008A83'
MINT = 'E3F3EF'
BLUE = '306BCE'
PALE = 'EAF0FA'
ORANGE = 'D9843B'
SAND = 'FCF0E4'
LINE = 'D9E2EA'

prs = Presentation()
prs.slide_width = Inches(W)
prs.slide_height = Inches(H)
prs.core_properties.title = '多媒体朋友圈展示系统｜项目立项汇报'
prs.core_properties.subject = '需求分析、系统设计、八人分工与建议进度安排'
prs.core_properties.author = '软件工程课程项目小组'
prs.core_properties.keywords = '软件工程,立项汇报,多媒体,小程序,Web后台'
notes = []

wb = openpyxl.load_workbook(SOURCE, data_only=True)
ws = wb['任务分工与进度表']
names = [str(ws.cell(r, 2).value).strip() if ws.cell(r, 2).value else f'成员{r-5:02}（待确认）' for r in range(6, 14)]
names[7] = '叶天豪'  # 原表 B13 为空，用户在本次会话中补充确认。
assert len(names) == 8
# 姓名直接取自小组最终表格；不依照此前匿名分工推断姓名。


def color(v):
    return RGBColor.from_string(v)


def shape(s, x, y, w, h, fill=WHITE, line=None, radius=False, kind=None):
    kind = kind or (MSO_SHAPE.ROUNDED_RECTANGLE if radius else MSO_SHAPE.RECTANGLE)
    o = s.shapes.add_shape(kind, Inches(x), Inches(y), Inches(w), Inches(h))
    o.fill.solid()
    o.fill.fore_color.rgb = color(fill)
    if line:
        o.line.color.rgb = color(line)
        o.line.width = Pt(.8)
    else:
        o.line.fill.background()
    if radius:
        try:
            o.adjustments[0] = .12
        except (ValueError, IndexError):
            pass
    o._element.spPr.append(OxmlElement('a:effectLst'))
    for ref in o._element.xpath('./p:style/a:effectRef'):
        ref.set('idx', '0')
    return o


def txt(s, text, x, y, w, h, size=18, c=INK, bold=False, align='left', valign='top', margin=0, font=FONT):
    box = s.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h))
    tf = box.text_frame
    tf.clear()
    tf.word_wrap = True
    tf.margin_left = tf.margin_right = Inches(margin)
    tf.margin_top = tf.margin_bottom = 0
    tf.vertical_anchor = {'top': MSO_ANCHOR.TOP, 'middle': MSO_ANCHOR.MIDDLE}[valign]
    for i, line in enumerate(text.split('\n')):
        p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        p.alignment = {'left': PP_ALIGN.LEFT, 'center': PP_ALIGN.CENTER, 'right': PP_ALIGN.RIGHT}[align]
        p.space_after = Pt(4)
        p.space_before = Pt(0)
        p.line_spacing = 1.15
        r = p.add_run()
        r.text = line
        r.font.name = font
        r.font.size = Pt(size)
        r.font.bold = bold
        r.font.color.rgb = color(c)
        rp = r._r.get_or_add_rPr()
        rp.set('lang', 'zh-CN')
        ea = OxmlElement('a:ea')
        ea.set('typeface', font)
        rp.append(ea)
    return box


def line(s, x1, y1, x2, y2, c=LINE, width=1.5, arrow=False):
    o = s.shapes.add_connector(MSO_CONNECTOR.STRAIGHT, Inches(x1), Inches(y1), Inches(x2), Inches(y2))
    o.line.color.rgb = color(c)
    o.line.width = Pt(width)
    o._element.spPr.append(OxmlElement('a:effectLst'))
    for ref in o._element.xpath('./p:style/a:effectRef'):
        ref.set('idx', '0')
    if arrow:
        end = OxmlElement('a:tailEnd')
        end.set('type', 'triangle')
        end.set('w', 'sm')
        end.set('len', 'sm')
        o.line._get_or_add_ln().append(end)
    return o


def pill(s, text, x, y, w, fill=MINT, c=TEAL, size=12):
    shape(s, x, y, w, .32, fill, radius=True)
    txt(s, text, x + .06, y + .025, w - .12, .26, size, c, align='center')


def number(s, n, x, y, fill=TEAL):
    shape(s, x, y, .42, .42, fill, kind=MSO_SHAPE.OVAL)
    txt(s, str(n), x, y + .045, .42, .3, 16, WHITE, True, 'center')


def base(title, section, subtitle=None):
    s = prs.slides.add_slide(prs.slide_layouts[6])
    s.background.fill.solid()
    s.background.fill.fore_color.rgb = color(BG)
    shape(s, .6, .48, .08, .5, TEAL)
    txt(s, title, .82, .5, 11.8, .55, 28, NAVY, True)
    if subtitle:
        txt(s, subtitle, .82, 1.15, 11.7, .42, 14.5, MUTED)
    line(s, .65, 7.04, 12.68, 7.04, LINE, .8)
    txt(s, '多媒体朋友圈展示系统  /  项目立项汇报', .68, 7.15, 8.5, .2, 10, MUTED)
    txt(s, section, 9.4, 7.15, 2.6, .2, 10, MUTED, align='right')
    txt(s, f'{len(prs.slides):02}', 12.2, 7.12, .45, .26, 12, TEAL, True, 'right')
    return s


def note(s, title, content, seconds, source):
    full = f'建议讲解：{seconds} 秒\n\n{content}\n\n依据：{source}\n状态：立项规划；页面示意、建议进度和测试计划不代表实现结果。'
    s.notes_slide.notes_text_frame.text = full
    notes.append({'page': len(prs.slides), 'title': title, 'content': content, 'seconds': seconds, 'source': source})


def bullet(s, items, x, y, w, size=17, spacing=.48, c=INK):
    for i, text in enumerate(items):
        shape(s, x, y + i * spacing + .115, .055, .055, TEAL)
        txt(s, text, x + .19, y + i * spacing, w - .19, spacing, size, c)


def card(s, x, y, w, h, title, body, accent=TEAL, fill=WHITE, body_size=17):
    shape(s, x, y, w, h, fill, line=LINE, radius=True)
    shape(s, x, y + .17, .045, .43, accent)
    txt(s, title, x + .23, y + .2, w - .46, .48, 21, accent, True)
    txt(s, body, x + .23, y + .86, w - .46, h - 1, body_size, INK)


# 01 封面：原生图形绘制内容卡片，保持可编辑。
s = prs.slides.add_slide(prs.slide_layouts[6])
s.background.fill.solid()
s.background.fill.fore_color.rgb = color(NAVY)
shape(s, .75, .78, .6, .07, '42C7B5')
txt(s, '软件工程课程项目', .75, 1.04, 7, .4, 18, 'A6C8D8')
txt(s, '多媒体朋友圈\n展示系统', .72, 1.9, 8, 1.9, 43, WHITE, True)
txt(s, '项目立项汇报', .77, 4.06, 7, .5, 26, '63D4C5')
txt(s, '需求分析  /  系统设计  /  人员分工  /  进度安排', .77, 4.8, 8, .45, 17, 'D5E3EC')
txt(s, f'组长：{names[0]}    ·    8 人协作', .77, 6.32, 7, .4, 16, 'D5E3EC')
txt(s, '2026 · 11', 10.9, 6.4, 1.6, .32, 14, 'A6C8D8', align='right')
shape(s, 9.0, 1.38, 3.5, 4.65, '244259', radius=True)
shape(s, 8.75, 1.15, 3.5, 4.65, WHITE, radius=True)
shape(s, 9.04, 1.5, .46, .46, TEAL, kind=MSO_SHAPE.OVAL)
txt(s, '分享身边的精彩', 9.65, 1.56, 2.3, .33, 15, NAVY, True)
shape(s, 9.03, 2.21, 2.94, 1.95, MINT, radius=True)
shape(s, 9.24, 2.44, 1.18, 1.48, '95CFC5', radius=True)
shape(s, 10.59, 2.44, 1.17, .66, 'C1DED8', radius=True)
shape(s, 10.59, 3.26, 1.17, .66, '67B5AC', radius=True)
txt(s, '文字  ·  图片  ·  视频', 9.06, 4.43, 2.9, .38, 16, INK)
pill(s, '# 校园生活', 9.05, 5.05, 1.38)
pill(s, '评分 / 评论', 10.57, 5.05, 1.37, PALE, BLUE)
note(s, '多媒体朋友圈展示系统', '大家好，我们小组拟开发一个由用户小程序和管理员 Web 后台组成的多媒体朋友圈展示系统。本次汇报主要介绍我们对课程需求的分析、初步设计、最终确认的八人分工，以及拟定的开发进度。', 20, '小组最终分工表；specs/proposal.md')

# 02 项目定位
s = base('项目目标：形成完整的内容分享与管理流程', '01 / 需求分析', '面向课程展示场景，连接发布者、浏览者与管理员。')
card(s, .7, 1.85, 3.85, 3.35, '便捷发布与个人管理', '用户发布文字、图片或视频\n为内容添加标签\n随时查看、编辑和删除本人动态')
card(s, 4.75, 1.85, 3.85, 3.35, '发现内容与交流反馈', '按日期和标签检索动态\n浏览图文、预览图片、播放视频\n对他人内容评分并发表评论', BLUE)
card(s, 8.8, 1.85, 3.85, 3.35, '统一管理与运行维护', '管理员查询用户和动态\n执行用户禁用与动态删除\n记录管理行为，维护内容秩序', ORANGE)
shape(s, .7, 5.55, 11.95, .95, NAVY, radius=True)
txt(s, '拟验收的完整流程', .95, 5.83, 2.5, .36, 18, '63D4C5', True)
txt(s, '发布 → 检索 → 浏览 → 评分评论 → 作者管理 → 后台管理', 3.55, 5.86, 8.75, .36, 18, WHITE)
note(s, '项目目标', '我们将系统定位为一个支持多媒体展示与互动的课程项目。用户侧解决内容发布、个人管理和检索交流的问题，管理员侧提供统一管理能力。项目验收将围绕一条完整流程展开：先发布，再由另一位用户检索和互动，最后验证作者管理与后台管理，保证各模块可以协同运行。', 30, 'specs/proposal.md 第 1、2、4、8 节')

# 03 范围
s = base('需求范围：先完成必做功能，再评估扩展能力', '01 / 需求分析', '围绕普通用户与管理员两类角色，明确本阶段的功能边界。')
shape(s, .7, 1.82, 7.45, 4.02, WHITE, LINE, True)
pill(s, '用户小程序', .95, 2.04, 1.65)
items = [('账号与密码', '注册、登录、设置与修改密码'), ('动态与媒体', '文本 / 图片 / 视频发布与本人管理'), ('检索与互动', '日期 / 标签筛选、评分与一级评论')]
for i, (a, b) in enumerate(items):
    y = 2.72 + i * .93
    number(s, i + 1, .99, y)
    txt(s, a, 1.63, y + .02, 2.1, .36, 20, NAVY, True)
    txt(s, b, 3.65, y + .04, 4.15, .62, 16, MUTED)
shape(s, 8.37, 1.82, 4.28, 4.02, WHITE, LINE, True)
pill(s, '管理员 Web 后台', 8.62, 2.04, 2.3, PALE, BLUE)
bullet(s, ['用户查询与删除（禁用）', '动态查询与逻辑删除', '管理员角色与操作记录'], 8.68, 2.85, 3.6, 18, .82)
shape(s, .7, 6.1, 11.95, .54, SAND, radius=True)
txt(s, '候选加分项：好友关系、敏感词过滤、视频流处理、大模型辅助、活跃统计图；核心流程验收后再排期。', .92, 6.24, 11.55, .3, 14, '855224')
note(s, '需求范围', '必做功能按照两个终端划分。小程序覆盖账号、发布与本人管理、检索、评分和评论；后台覆盖用户与动态管理。删除用户在实现层采用禁用，删除动态采用逻辑删除。好友、敏感词过滤、视频流处理、大模型与统计图保留为加分候选，是否投入开发取决于核心功能完成后的实际进度。', 35, 'specs/proposal.md 第 2、3、5 节；最终分工表 A6:A13')

# 04 用户旅程
s = base('业务分析：用双账号验证发布、互动与管理', '01 / 需求分析', '把页面功能串成可演示、可验收的业务流程。')
steps = [('用户甲', '登录并发布', '图文 / 视频 + 标签'), ('用户乙', '检索与浏览', '日期 / 标签 → 详情'), ('用户乙', '评分与评论', '1–5 分 + 一级评论'), ('用户甲', '编辑或删除', '本人动态管理'), ('管理员', '查询与管理', '用户禁用 / 动态删除')]
for i, (who, title, body) in enumerate(steps):
    x = .7 + i * 2.42
    number(s, i + 1, x + .82, 1.94, TEAL if i < 4 else BLUE)
    if i < 4:
        line(s, x + 1.37, 2.15, x + 2.25, 2.15, TEAL, 2, True)
    shape(s, x, 2.68, 2.25, 1.98, WHITE, LINE, True)
    pill(s, who, x + .51, 2.9, 1.23, MINT if i < 4 else PALE, TEAL if i < 4 else BLUE)
    txt(s, title, x + .12, 3.45, 2.01, .42, 20, NAVY, True, 'center')
    txt(s, body, x + .12, 4.06, 2.01, .4, 13.5, MUTED, align='center')
txt(s, '贯穿流程的三条规则', .73, 5.04, 8, .4, 20, NAVY, True)
for x, head, body in [(.7, '身份正确', '改密与禁用后，旧令牌失效'), (4.75, '权限正确', '非作者不可编辑；管理接口限管理员'), (8.8, '状态一致', '删除后，列表、详情与媒体同步隐藏')]:
    shape(s, x, 5.62, 3.85, .98, MINT, radius=True)
    txt(s, head, x + .18, 5.75, 3.49, .28, 16, TEAL, True)
    txt(s, body, x + .18, 6.17, 3.49, .29, 12.5, INK)
note(s, '业务流程与验收场景', '核心验收会使用两个普通账号和一个管理员账号。用户甲发布，用户乙检索、浏览并互动，随后用户甲编辑或删除，再由管理员执行管理操作。除了页面能正常操作，还必须检查身份、权限和数据状态。例如非作者不能编辑动态，改密后旧令牌失效，删除后的详情与媒体也应不可读取。', 35, 'specs/proposal.md 第 8 节；specs/contracts/api.md；data-model.md')

# 05 总体架构
s = base('总体设计：双前端共用一个分层服务端', '02 / 系统设计', '统一接口与权限规则，分别实现用户体验和后台管理。')
for x, w, title, sub, accent in [(.7, 5.85, '用户小程序', 'uni-app · Vue 3 · TypeScript', TEAL), (6.8, 5.85, '管理员 Web 后台', 'Vue 3 · Vite · TypeScript · Element Plus', BLUE)]:
    shape(s, x, 1.8, w, 1.0, WHITE, LINE, True)
    txt(s, title, x + .23, 1.95, w - .46, .36, 20, accent, True)
    txt(s, sub, x + .23, 2.42, w - .46, .25, 13, MUTED)
line(s, 3.62, 2.8, 3.62, 3.48, TEAL, 2, True)
line(s, 9.72, 2.8, 9.72, 3.48, BLUE, 2, True)
pill(s, 'HTTP / REST · /api/v1 · JSON · JWT', 4.62, 2.99, 4.1, BG, MUTED, 13)
shape(s, .7, 3.5, 11.95, 1.68, NAVY, radius=True)
txt(s, 'Java 21 / Spring Boot 3.5', .98, 3.67, 5, .35, 20, WHITE, True)
txt(s, 'Controller → Service → Repository', 6.7, 3.73, 5.6, .3, 16, 'A6C8D8', align='right')
for i, label in enumerate(['账号与权限', '动态与检索', '媒体文件服务', '评分与评论']):
    shape(s, 1 + i * 2.88, 4.39, 2.65, .51, '29495E', radius=True)
    txt(s, label, 1 + i * 2.88, 4.51, 2.65, .29, 16, WHITE, align='center')
line(s, 3.62, 5.18, 3.62, 5.6, TEAL, 2, True)
line(s, 9.72, 5.18, 9.72, 5.6, TEAL, 2, True)
for x, title, sub in [(.7, 'MySQL 8.4 LTS', '业务数据 · Spring Data JPA · Flyway'), (6.8, '本地媒体目录', '图片 / MP4 · 元数据入库 · Range 视频读取')]:
    shape(s, x, 5.62, 5.85, .95, WHITE, LINE, True)
    txt(s, title, x + .23, 5.76, 5.4, .32, 19, TEAL, True)
    txt(s, sub, x + .23, 6.2, 5.4, .25, 13, MUTED)
note(s, '总体架构与技术栈', '架构采用两个前端共用服务端的形式。小程序使用 uni-app、Vue 3 和 TypeScript，后台使用 Vue 3 与 Element Plus。服务端采用 Java 21 和 Spring Boot，按控制器、业务服务、数据访问分层。MySQL 保存业务与媒体元数据，文件放在独立媒体目录。统一接口和角色规则有利于八人并行开发；Flyway 用于同步数据库结构。', 40, 'specs/design.md 第 1、5、6 节；specs/decisions/0001-tech-stack.md')

# 06 数据模型
s = base('数据设计：以动态为中心关联媒体与互动', '02 / 系统设计', '通过关联关系与唯一约束，保证数据可查询、状态可追踪。')

def entity(x, y, w, title, fields, accent=TEAL):
    shape(s, x, y, w, 1.06, WHITE, LINE, True)
    shape(s, x, y, w, .39, accent, radius=True)
    txt(s, title, x + .13, y + .065, w - .26, .28, 16, WHITE, True)
    txt(s, fields, x + .13, y + .54, w - .26, .39, 12, INK)

# 先绘线，再放实体，避免线遮挡文本。
line(s, 3.6, 2.58, 4.63, 2.58, TEAL, 1.5, True)
txt(s, '1 : N', 3.74, 2.25, .65, .25, 12, TEAL, align='center')
line(s, 7.58, 2.6, 8.25, 2.6, TEAL)
line(s, 8.25, 2.18, 8.25, 4.98, TEAL)
for y in [2.18, 3.58, 4.98]:
    line(s, 8.25, y, 9.08, y, TEAL, 1.5, True)
    txt(s, '1 : N', 8.4, y - .31, .62, .25, 11, TEAL)
line(s, 6.1, 3.11, 6.1, 4.16, TEAL, 1.5, True)
txt(s, '1 : N', 6.3, 3.52, .8, .3, 12, TEAL)
line(s, 4.63, 4.69, 3.6, 4.69, TEAL, 1.5, True)
txt(s, 'N : 1', 3.74, 4.37, .65, .25, 12, TEAL)
entity(.7, 2.05, 2.9, 'User  用户', 'account · role · status')
entity(4.63, 2.05, 2.95, 'Post  动态', 'author_id · text · status', BLUE)
entity(9.08, 1.65, 3.57, 'Media  媒体', 'post_id · storage_key · status')
entity(9.08, 3.05, 3.57, 'Rating  评分', 'post_id · user_id · score')
entity(9.08, 4.45, 3.57, 'Comment  评论', 'post_id · user_id · content')
entity(4.63, 4.16, 2.95, 'PostTag  动态标签', 'post_id + tag_id 唯一', BLUE)
entity(.7, 4.16, 2.9, 'Tag  标签', 'name 唯一')
txt(s, '评分和评论均通过 user_id 关联用户。\n管理员操作另记录至操作日志表。', .75, 5.63, 7.6, .68, 14, MUTED)
shape(s, .7, 6.39, 11.95, .34, MINT, radius=True)
txt(s, '关键约束：账号唯一  ·  每人每条动态一份评分  ·  删除状态统一过滤  ·  数据库结构用 Flyway 迁移', .87, 6.45, 11.65, .23, 12, TEAL)
note(s, '核心数据模型', '数据模型以动态为中心。用户可以发布多条动态，一条动态可以包含多个媒体，并通过关联表对应多个标签。评分和评论都关联用户与动态，其中评分通过用户和动态的联合唯一约束实现重复提交时更新原评分。用户和动态都保留状态字段，用于禁用和逻辑删除；管理行为另外记录日志。这里展示的是初步关系设计。', 40, 'specs/contracts/data-model.md')

# 07 核心流程设计
s = base('关键流程：分步上传，统一校验与事务处理', '02 / 系统设计', '发布过程与删除过程都需要保持前端、数据库和媒体状态一致。')
txt(s, '发布动态', .75, 1.82, 4, .4, 22, TEAL, True)
flow = [('01', '选择并上传媒体', '校验格式与大小'), ('02', '返回媒体 ID', '未绑定状态 PENDING'), ('03', '提交动态', '文本 + 媒体 ID + 标签'), ('04', '保存与展示', '校验归属并完成绑定')]
for i, (n, title, body) in enumerate(flow):
    x = .7 + i * 3.05
    shape(s, x, 2.45, 2.8, 1.62, WHITE, LINE, True)
    txt(s, n, x + .18, 2.63, .6, .38, 21, TEAL, True)
    txt(s, title, x + .18, 3.14, 2.48, .34, 17, NAVY, True)
    txt(s, body, x + .18, 3.65, 2.48, .3, 12.5, MUTED)
    if i < 3:
        line(s, x + 2.8, 3.25, x + 3.02, 3.25, TEAL, 1.5, True)
card(s, .7, 4.45, 5.85, 1.98, '媒体规则', '纯文本 / 1–9 张图片 / 1 个 MP4\n图片 ≤10 MB / 张；视频 ≤50 MB\n未绑定媒体超过 24 小时清理', TEAL, WHITE, 15)
card(s, 6.8, 4.45, 5.85, 1.98, '删除与禁用规则', '删除动态后隐藏列表、详情、评论和媒体\n禁用用户后处理其动态、评论和评分\n撤销旧令牌；关联操作失败时事务回滚', BLUE, WHITE, 15)
note(s, '发布与管理流程设计', '发布时先上传媒体，服务端验证后返回媒体 ID，再提交文本、标签与媒体 ID。动态服务需要核对上传者与作者身份，完成关联后才能公开访问；超过二十四小时未绑定的媒体将清理。删除与禁用则需要跨模块协调，保证列表、详情和媒体访问一致，禁用用户时还需要撤销令牌并处理其互动数据。', 40, 'specs/design.md 第 9 节；specs/contracts/api.md；data-model.md')

# 08 页面方案，全部为设计示意。
s = base('页面设计：围绕发布、发现与管理组织界面', '02 / 系统设计', '界面示意用于说明信息布局，后续在统一组件和样式基础上实现。')

def phone(x, title):
    shape(s, x, 1.9, 2.67, 4.65, WHITE, LINE, True)
    shape(s, x + .9, 2.05, .88, .06, LINE, radius=True)
    txt(s, title, x + .18, 2.34, 2.31, .36, 17, NAVY, True)
    line(s, x + .16, 2.88, x + 2.5, 2.88, LINE, .8)

phone(.7, '动态广场')
pill(s, '日期', .9, 3.06, .95, PALE, BLUE, 11)
pill(s, '标签', 1.99, 3.06, 1.13, MINT, TEAL, 11)
shape(s, .92, 3.58, .28, .28, TEAL, kind=MSO_SHAPE.OVAL)
txt(s, '用户昵称', 1.35, 3.57, 1.6, .3, 12, INK)
txt(s, '记录今天的校园生活', .92, 4.02, 2.17, .3, 13, INK)
for j in range(3):
    shape(s, .93 + j * .73, 4.5, .63, .79, ['B4DAD2', '8FC7BD', 'C9E1DB'][j], radius=True)
pill(s, '# 校园', .94, 5.51, 1.08, MINT, TEAL, 11)
txt(s, '广场       发布       我的', .91, 6.13, 2.26, .24, 11, MUTED)

phone(3.62, '发布动态')
shape(s, 3.83, 3.08, 2.25, 1.05, BG, radius=True)
txt(s, '分享此刻的想法……', 3.99, 3.27, 1.96, .48, 12, MUTED)
shape(s, 3.83, 4.36, 1.02, .83, MINT, radius=True)
shape(s, 5.06, 4.36, 1.02, .83, PALE, radius=True)
txt(s, '+ 图片', 3.85, 4.63, .98, .3, 13, TEAL, align='center')
txt(s, '+ 视频', 5.08, 4.63, .98, .3, 13, BLUE, align='center')
txt(s, '标签  # 校园  + 添加', 3.85, 5.46, 2.24, .29, 12, MUTED)
shape(s, 3.85, 5.96, 2.23, .39, TEAL, radius=True)
txt(s, '发布', 3.85, 6.025, 2.23, .25, 13, WHITE, align='center')

phone(6.54, '动态详情')
shape(s, 6.75, 3.09, 2.24, 1.33, PALE, radius=True)
shape(s, 7.66, 3.54, .4, .4, BLUE, kind=MSO_SHAPE.RIGHT_TRIANGLE)
txt(s, '图文 / 视频内容区', 6.78, 4.63, 2.18, .32, 13, INK)
pill(s, '评分  1 — 5', 6.77, 5.14, 2.16, MINT, TEAL, 12)
shape(s, 6.77, 5.72, 2.16, .5, BG, radius=True)
txt(s, '写下你的评论……', 6.91, 5.88, 1.88, .23, 11, MUTED)

shape(s, 9.48, 1.9, 3.17, 2.54, WHITE, LINE, True)
shape(s, 9.48, 1.9, 3.17, .43, NAVY, radius=True)
txt(s, '管理后台', 9.67, 1.99, 2.75, .25, 13, WHITE, True)
pill(s, '用户', 9.68, 2.56, 1.23, PALE, BLUE, 11)
pill(s, '动态', 11.05, 2.56, 1.23, MINT, TEAL, 11)
for j in range(3):
    line(s, 9.69, 3.21 + j * .39, 12.42, 3.21 + j * .39, LINE, .8)
    txt(s, '记录信息', 9.76, 3.27 + j * .39, 1.5, .23, 10, MUTED)
    txt(s, '删除', 11.78, 3.27 + j * .39, .54, .23, 10, ORANGE)
txt(s, '统一体验规范', 9.54, 4.87, 3.05, .36, 19, TEAL, True)
bullet(s, ['加载、空状态与失败反馈', '上传进度与失败恢复', '删除确认与越权提示'], 9.55, 5.45, 3.05, 13.5, .39)
note(s, '页面与交互方案', '页面采用围绕任务组织的方式：广场负责发现内容，发布页负责输入与上传，详情页集中展示媒体和互动，后台使用列表完成管理。本页是设计示意，并非系统截图。我们会由小程序基础负责人统一按钮、表单、请求和错误状态，其他页面复用这些约定，重点处理上传失败、空结果以及删除确认等场景。', 30, 'specs/proposal.md 第 7 节；specs/design.md 第 7、10 节；最终分工表 A10:A13')

# 09 质量与验收
s = base('质量计划：每个模块有测试，完整流程有复验', '02 / 系统设计', '测试由各模块负责人执行，梁国浩汇总验收矩阵与真实证据。')
cols = [(.7, '模块测试', 'JUnit 5 / Spring Boot Test / Vitest', ['认证与令牌失效', '文件格式、大小及归属', '筛选、评分与评论边界'], TEAL), (4.75, '接口联调', '使用真实 API 与不同角色账号', ['字段、状态码与分页一致', '普通用户越权应被拒绝', '删除后的关联状态一致'], BLUE), (8.8, '完整流程验收', '微信开发者工具 + 浏览器', ['发布 → 检索 → 互动', '本人编辑 / 删除 → 后台管理', '另一成员复现运行环境'], ORANGE)]
for x, title, sub, items, acc in cols:
    shape(s, x, 1.85, 3.85, 3.75, WHITE, LINE, True)
    txt(s, title, x + .23, 2.13, 3.39, .45, 22, acc, True)
    txt(s, sub, x + .23, 2.8, 3.39, .75, 13, MUTED)
    bullet(s, items, x + .24, 3.75, 3.37, 15.5, .55)
shape(s, .7, 5.92, 11.95, .72, NAVY, radius=True)
txt(s, '验收记录', .95, 6.13, 1.6, .32, 17, '63D4C5', True)
txt(s, '执行人 · 环境 · 步骤 · 预期 / 实际结果 · 证据 · 缺陷修复与复验', 2.76, 6.15, 9.55, .32, 17, WHITE)
note(s, '质量保证与测试计划', '测试分为模块测试、真实接口联调和完整流程验收三个层次。每位成员测试并修复自己负责的模块，梁国浩负责汇总验收矩阵和测试报告。测试记录需要保留执行人、环境、操作步骤、实际结果和证据，发现缺陷后还要复验。当前这些内容是验收计划，后续只把实际执行过并有证据的结果记为通过。', 35, '最终分工表 A6:A13，尤其 A9；tests/README.md')

# 10、11 人员职责
def person_card(x, y, index, title, scope, output, peer, accent=TEAL):
    shape(s, x, y, 5.85, 2.25, WHITE, LINE, True)
    pill(s, f'M{index+1:02}', x + .23, y + .23, .75, MINT if accent == TEAL else PALE, accent, 12)
    txt(s, names[index], x + 1.16, y + .21, 3.95, .4, 23, NAVY, True)
    txt(s, title, x + .24, y + .79, 5.37, .37, 17, accent, True)
    txt(s, scope, x + .24, y + 1.27, 5.37, .38, 14, INK)
    txt(s, output, x + .24, y + 1.75, 5.37, .3, 12.5, MUTED)

s = base('人员分工：四人建设服务端与运行基础', '03 / 人员分工', '依据小组最终确认表；每人同时负责本人模块的测试、接口说明和工作记录。')
person_card(.7, 1.8, 0, '组长 · 服务端基础 / 认证 / 用户管理', '注册登录、密码与角色、用户禁用、项目协调', '交付：认证与管理 API、权限证据、接口约定', '')
person_card(6.8, 1.8, 1, '动态 / 标签 / 检索服务', '动态增删改查、组合筛选、后台内容管理', '交付：动态 API、数据库关系图、边界测试', '', BLUE)
person_card(.7, 4.28, 2, '媒体服务 / 运行环境', '图片视频上传、读取与清理、环境复现', '交付：媒体 API、异常测试、运行说明', '')
person_card(6.8, 4.28, 3, '评分评论服务 / 测试汇总', '评分更新、评论分页、验收矩阵与测试报告', '交付：互动 API、真实测试证据汇总', '', BLUE)
note(s, '服务端四人分工', f'服务端由四位成员承担。组长{names[0]}负责基础工程、认证与用户管理，并协调进度和接口；{names[1]}负责动态、标签和检索，整理数据库关系图；{names[2]}负责媒体服务和环境复现；{names[3]}负责评分评论及测试汇总。各负责人同时承担本模块测试与材料整理，汇总工作不会替代其他成员的实际测试。', 40, '小组最终分工表 A6:B9')

s = base('人员分工：三人完成小程序，一人建设后台', '03 / 人员分工', '公共能力、作者操作、浏览互动和管理后台各有负责人，减少重复修改。')
person_card(.7, 1.8, 4, '小程序基础 / 账号页面', '工程、路由、请求层、登录状态与公共组件', '交付：账号页面、界面规范、状态测试', '')
person_card(6.8, 1.8, 5, '发布 / 编辑 / 我的动态', '媒体选择与上传、标签输入、本人内容管理', '交付：作者操作流程、异常恢复测试', '', BLUE)
person_card(.7, 4.28, 6, '广场 / 检索 / 详情 / 互动', '内容展示、筛选、评分评论、PPT 素材汇总', '交付：浏览互动流程、跨模块复验、PPT', '')
person_card(6.8, 4.28, 7, '管理员 Web 后台 / 交付汇总', '管理登录、用户与动态管理、报告合并排版', '交付：后台页面、权限测试、报告与文件清单', '', BLUE)
note(s, '前端四人分工', f'前端由四位成员分工。{names[4]}维护小程序框架、公共请求和账号页面，{names[5]}负责发布、编辑和我的动态，{names[6]}负责广场、检索、详情和互动，并汇总全员 PPT 素材。{names[7]}负责管理员 Web 后台、报告排版和交付清单。每位成员都需提供自己的真实素材，并在汇总后核对本人内容。', 40, '小组最终分工表 A10:B13')

# 12 协作依赖
s = base('协作安排：先对齐接口，再按业务流程联调', '03 / 人员分工', '每个任务形成“设计 → 实现 → 测试 → 联调 → 文档”的闭环。')
txt(s, '主要接口对接', .73, 1.78, 6, .4, 21, TEAL, True)
rows = [
    ('账号与权限', names[0], f'{names[4]} · {names[7]}'),
    ('动态与检索', names[1], f'{names[5]} · {names[6]} · {names[7]}'),
    ('媒体上传与播放', names[2], f'{names[5]} · {names[6]}'),
    ('评分与评论', names[3], names[6]),
]
for i, (feature, server, client) in enumerate(rows):
    y = 2.4 + i * .78
    shape(s, .7, y, 7.12, .64, WHITE, LINE, True)
    txt(s, feature, .9, y + .17, 2.05, .3, 16, INK, True)
    txt(s, server, 3.06, y + .17, 1.3, .3, 16, TEAL)
    line(s, 4.43, y + .33, 4.94, y + .33, TEAL, 1.5, True)
    txt(s, client, 5.1, y + .19, 2.5, .29, 12, MUTED)
shape(s, 8.08, 1.8, 4.57, 4.32, NAVY, radius=True)
txt(s, '统一协作约定', 8.35, 2.08, 4.02, .4, 22, WHITE, True)
for i, (head, desc) in enumerate([('接口先行', '字段、错误码、权限与分页先约定'), ('工作留痕', '每次记录改动、测试、问题与下一步'), ('阶段验收', '至少一名对接成员复验并记录结果'), ('协调推进', f'{names[0]}跟进依赖、阻塞和联调修复')]):
    y = 2.84 + i * .73
    txt(s, head, 8.37, y, 3.95, .29, 16, '63D4C5', True)
    txt(s, desc, 8.37, y + .36, 3.95, .3, 12.5, 'E4EDF2')
txt(s, '跨模块重点：用户禁用关联处理由组长统筹，动态、互动与媒体负责人共同验证状态一致性。', .74, 6.42, 11.87, .3, 14, MUTED)
note(s, '接口对接与协作方式', '并行开发首先要把接口说清楚。账号服务主要对接账号页与后台，动态服务对接发布、广场和后台，媒体服务对接上传和播放，互动服务对接详情页。每次工作结束都记录修改、测试、问题和下一步，每个阶段至少由一位对接成员复验。用户禁用等跨模块操作由组长统筹，避免各模块分别完成后仍存在状态不一致。', 35, '最终分工表中的协作对象及完成条件；specs/contracts/api.md')

# 13 进度甘特图
s = base('建议进度：按六周推进，逐步形成可演示版本', '04 / 进度安排', '当前表格未填写日历日期；以下为建议计划，第 1 周从小组确认启动周计算。')
startx, cw, rowy, rh = 4.73, 1.27, 2.24, .54
txt(s, '工作阶段 / 重点产出', .85, 1.83, 3.75, .32, 17, NAVY, True)
for j in range(6):
    x = startx + j * cw
    txt(s, f'第 {j+1} 周', x, 1.83, cw - .09, .32, 16, NAVY, True, 'center')
    shape(s, x, 2.18, cw - .09, 3.93, 'EAF0F4')
tasks = [
    ('需求确认与接口 / 数据设计', [(0, 1, TEAL)]),
    ('工程骨架与账号主流程', [(0, 2, TEAL)]),
    ('动态 / 媒体 / 互动服务', [(1, 3, TEAL)]),
    ('小程序与后台页面', [(1, 3, BLUE)]),
    ('模块测试与缺陷修复', [(1, 5, '7E9EB8')]),
    ('真实接口联调与回归', [(3, 2, BLUE)]),
    ('报告 / 附件 / PPT 与演练', [(4, 2, ORANGE)]),
]
for i, (label, bars) in enumerate(tasks):
    y = rowy + i * rh
    txt(s, label, .88, y + .09, 3.68, .34, 15, INK)
    for a, duration, c in bars:
        shape(s, startx + a * cw + .06, y + .09, duration * cw - .21, .32, c, radius=True)
shape(s, .7, 6.33, 11.95, .4, MINT, radius=True)
txt(s, '里程碑：W1 约定确认  →  W2 账号跑通  →  W4 核心功能联调  →  W5 回归验收  →  W6 演练交付', .85, 6.43, 11.65, .24, 13, TEAL)
note(s, '建议六周进度', '最终分工表已经确认人员，但还没有填写具体日期，因此这里提出六周安排供小组确定。第一周完成需求、数据与接口约定；第二周跑通账号和工程骨架；第三到第四周并行完成服务与页面，并开始真实联调；第五周集中回归与补齐证据；第六周完成材料、环境复现和演练。模块测试从实现阶段持续开展，报告素材也随个人记录积累。', 40, '分工表 A6:A13 的阶段交付；六周安排为本次拟定，尚未确认日历日期')

# 14 里程碑与风险
s = base('阶段检查：以可验证的产出决定是否进入下一步', '04 / 进度安排', '优先完成课程核心要求；遇到阻塞时调整分工和扩展范围。')
shape(s, .7, 1.8, 7.22, 4.85, WHITE, LINE, True)
txt(s, '里程碑与退出标准', .95, 2.04, 6.74, .4, 22, TEAL, True)
gates = [('W1', '需求与设计确认', '核心范围、字段、权限和页面边界一致'), ('W2', '最小账号流程跑通', '环境可启动；注册、登录与角色访问可验证'), ('W4', '主要业务完成联调', '发布、检索、互动与管理使用真实接口'), ('W5', '核心流程完成回归', '重要缺陷关闭；证据齐全；他人复验通过'), ('W6', '课堂演示准备完成', '运行可复现；代码、报告、附件和 PPT 一致')]
for i, (wk, title, desc) in enumerate(gates):
    y = 2.75 + i * .7
    pill(s, wk, .97, y, .7, MINT, TEAL, 12)
    txt(s, title, 1.87, y - .02, 5.69, .3, 16, NAVY, True)
    txt(s, desc, 1.87, y + .34, 5.69, .28, 12.5, MUTED)
shape(s, 8.17, 1.8, 4.48, 4.85, SAND, radius=True)
txt(s, '主要风险与应对', 8.43, 2.04, 3.96, .4, 22, 'A66028', True)
risks = [('接口不一致', '先维护契约与样例，变更同步调用方'), ('媒体 / 真机环境差异', '提前验证上传播放与 HTTPS 条件'), ('联调集中、返工过多', '从账号流程开始分批联调和复验'), ('收尾工作不足', '预留第 6 周；核心未通过则暂缓加分项')]
for i, (head, body) in enumerate(risks):
    y = 2.76 + i * .86
    txt(s, head, 8.45, y, 3.94, .3, 16, 'A66028', True)
    txt(s, body, 8.45, y + .37, 3.94, .42, 12.5, INK)
note(s, '里程碑与风险应对', '进度不能只看代码提交数量，每个阶段都要有能够验证的产出。第一周看约定是否一致，第二周看最小流程能否跑通，第四周看真实联调，第五周看回归证据，第六周看交付和演示。主要风险是接口变动、媒体环境差异和联调过晚，因此我们会分批对接并预留收尾时间，核心功能未通过时暂缓加分功能。', 35, '最终分工表的验收标准；specs/design.md 第 6、10 节；本次建议里程碑')

# 15 结尾与成果
s = base('预期成果：交付可运行、可验证、可讲解的项目', '05 / 预期成果', '以课程要求为验收依据，围绕同一套功能与证据完成最终材料。')
shape(s, .7, 1.87, 4.0, 4.77, NAVY, radius=True)
txt(s, '一套完整系统', 1.02, 2.25, 3.35, .55, 28, WHITE, True)
txt(s, '用户小程序\n服务端 API\n管理员 Web 后台', 1.04, 3.18, 3.31, 1.8, 23, 'D5E3EC')
txt(s, '围绕发布、检索、互动与管理\n演示完整业务流程', 1.04, 5.56, 3.31, .69, 16, '63D4C5')
deliveries = [
    ('代码与运行说明', f'各模块负责人提交；{names[2]}汇总环境说明'),
    ('实验报告与测试证据', f'{names[7]}合并报告；{names[3]}汇总测试结果'),
    ('四份课程附件', '按老师正式模板填写，各模块提供真实材料'),
    ('课堂 PPT 与演示', f'{names[6]}汇总素材；全员校对并参加演练'),
]
for i, (title, body) in enumerate(deliveries):
    y = 1.91 + i * 1.17
    number(s, i + 1, 5.11, y + .08)
    txt(s, title, 5.77, y + .07, 6.56, .4, 22, NAVY, True)
    txt(s, body, 5.79, y + .61, 6.5, .38, 15, MUTED)
note(s, '预期成果与结束', '最终我们计划交付小程序、服务端和管理后台三部分代码，以及运行说明、实验报告、四份课程附件和课堂 PPT。报告与展示将使用同一套功能名称和真实测试证据，确保系统能运行、过程可验证、成员能够说明自己的贡献。以上是我们小组的项目规划，接下来将按确认的接口、分工和阶段验收标准推进。', 25, '课程要求；最终分工表 A8、A9、A12、A13；deliverables/README.md')

prs.save(OUT)

md = [
    '# 多媒体朋友圈展示系统 · 立项汇报讲稿',
    '',
    '配套文件：`多媒体朋友圈展示系统_项目立项汇报.pptx`。共 15 页，建议讲解约 9 分钟；各页讲稿已写入 PPT 演讲者备注。',
    '',
    '## 内容依据与使用说明',
    '',
    '- 人员姓名与职责采用小组提供的《软件工程课程大作业任务分工与进度表.xlsx》（工作表“任务分工与进度表”，A6:B13）。',
    '- 项目目标、业务规则与技术架构采用项目现有 `specs/proposal.md`、`specs/design.md` 及 `specs/contracts/`。',
    '- 原表 B13（成员 08）姓名为空，用户在本次会话中确认姓名为叶天豪；本次 PPT 与讲稿已补全，原始表格未改动。',
    '- 表格未填写计划日期、实际进度或测试证据。第 13–14 页的六周安排为建议计划，启动日期和最终截止日期待小组确认。',
    '- 本次为项目立项汇报，页面为设计示意；不把计划中的功能、测试或验收表述为已经完成。',
    '- 四份附件的正式名称与格式按老师提供的模板填写，本 PPT 不补造模板内容。',
    '- PPT 采用原生文字、图形和连接线，可编辑；PDF 用于快速预览和保持字体显示。',
    '',
    '## 小组成员',
    '',
]
for i, name in enumerate(names):
    md.append(f'- M{i+1:02}：{name}' + ('（组长）' if i == 0 else ''))
for n in notes:
    md += ['', f'## {n["page"]:02}｜{n["title"]}', '', f'建议用时：{n["seconds"]} 秒。', '', n['content'], '', f'依据：{n["source"]}。']
(BASE / '汇报讲稿.md').write_text('\n'.join(md) + '\n', encoding='utf-8')
(BASE / '工作文件' / '内容索引.json').write_text(json.dumps({'source': str(SOURCE), 'members': names, 'slides': notes}, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps({'pptx': str(OUT), 'slides': len(prs.slides), 'members': names, 'suggested_seconds': sum(n['seconds'] for n in notes)}, ensure_ascii=False))
