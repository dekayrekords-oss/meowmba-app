"""Render the versioned, text-first manual; one Markdown section per PDF page."""
from pathlib import Path
import re
from html import escape
from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, PageBreak

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'docs/manual/USER_MANUAL_152.md'
OUTPUT = ROOT / 'output/pdf/Meowmba-User-Manual.pdf'
TEAL = colors.HexColor('#157A78')
INK = colors.HexColor('#19313B')
GRAY = colors.HexColor('#52636B')
PAGE_W, PAGE_H = A4

styles = {
    'title': ParagraphStyle('Title', fontName='Helvetica-Bold', fontSize=25,
        leading=30, textColor=INK, spaceAfter=18),
    'cover': ParagraphStyle('Cover', fontName='Helvetica-Bold', fontSize=38,
        leading=43, textColor=INK, spaceAfter=16),
    'h2': ParagraphStyle('Heading', fontName='Helvetica-Bold', fontSize=13,
        leading=17, textColor=TEAL, spaceBefore=11, spaceAfter=7, keepWithNext=True),
    'body': ParagraphStyle('Body', fontName='Helvetica', fontSize=10.3,
        leading=15.1, textColor=INK, spaceAfter=9, alignment=TA_LEFT),
    'bullet': ParagraphStyle('Bullet', fontName='Helvetica', fontSize=10.3,
        leading=15.1, textColor=INK, leftIndent=13, firstLineIndent=-11, spaceAfter=6),
    'kicker': ParagraphStyle('Kicker', fontName='Helvetica-Bold', fontSize=9,
        leading=12, textColor=TEAL, spaceAfter=11),
}

def markup(text):
    text = escape(text)
    text = re.sub(r'`([^`]+)`', r'<font name="Courier" size="9">\1</font>', text)
    return re.sub(r'https://[^\s<>]+', lambda m:
        '<link href="' + m.group(0) + '" color="#157A78">' + m.group(0) + '</link>', text)

class ManualDoc(SimpleDocTemplate):
    def afterFlowable(self, flowable):
        if isinstance(flowable, Paragraph) and flowable.style.name in ('Title', 'Cover'):
            key = 'section-' + str(self.page)
            self.canv.bookmarkPage(key)
            self.canv.addOutlineEntry(flowable.getPlainText(), key, 0, False)

def decorate(canvas, doc):
    canvas.saveState()
    canvas.setFillColor(TEAL)
    canvas.rect(0, PAGE_H - 8, PAGE_W, 8, stroke=0, fill=1)
    canvas.setFont('Helvetica-Bold', 8)
    canvas.setFillColor(GRAY)
    canvas.drawString(48, PAGE_H - 34, 'MEOW ASSISTANT  /  USER MANUAL')
    canvas.setStrokeColor(colors.HexColor('#D6E3E4'))
    canvas.line(48, 45, PAGE_W - 48, 45)
    canvas.setFont('Helvetica', 8)
    canvas.drawString(48, 30, '1.3.96 (152)  |  September 19, 2026')
    canvas.drawRightString(PAGE_W - 48, 30, f'{doc.page} / {len(sections)}')
    canvas.restoreState()

sections = re.split(r'\n---\n', SOURCE.read_text())
story = []
for i, section in enumerate(sections):
    if i:
        story.append(PageBreak())
    story.append(Paragraph('COMPANION GUIDE' if i == 0 else f'REFERENCE  /  {i:02d}', styles['kicker']))
    lines = section.strip().splitlines()
    pending = []
    item_kind = None

    def flush():
        global pending, item_kind
        if pending:
            text = ' '.join(pending)
            story.append(Paragraph(markup(text), styles['bullet' if item_kind else 'body']))
        pending, item_kind = [], None

    for line in lines:
        if line.startswith('# '):
            flush()
            story.append(Paragraph(markup(line[2:]), styles['cover' if i == 0 else 'title']))
        elif line.startswith('## '):
            flush()
            story.append(Paragraph(markup(line[3:]), styles['h2']))
        elif not line.strip():
            flush()
        elif line.startswith('- ') or re.match(r'^\d+\. ', line):
            flush()
            pending = [line]
            item_kind = True
        else:
            pending.append(line.strip())
    flush()

OUTPUT.parent.mkdir(parents=True, exist_ok=True)
doc = ManualDoc(str(OUTPUT), pagesize=A4, leftMargin=48, rightMargin=48,
    topMargin=57, bottomMargin=60, title='Meow Assistant User Manual - 1.3.96 (152)',
    author='Meow Assistant', subject='Setup, controls, providers, Computer tasks and troubleshooting')
doc.build(story, onFirstPage=decorate, onLaterPages=decorate)
print(f'{OUTPUT} | {len(sections)} planned pages')
