"""Build the course report and export its three UML figures as SVG.

Run `python docs/build_report.py` after installing reportlab and pymupdf.
The report uses the terminal screenshots in docs/screenshots/.
"""

import argparse
import os
import tempfile
from pathlib import Path
from xml.sax.saxutils import escape

import pymupdf
from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.utils import ImageReader
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen.canvas import Canvas
from reportlab.platypus import Paragraph


DOCS = Path(__file__).resolve().parent
ROOT = DOCS.parent
JAVA = ROOT / 'src' / 'main' / 'java'
WIDTH, HEIGHT = A4
LEFT, RIGHT = 50, WIDTH - 50
PRINT_WIDTH = RIGHT - LEFT
INK, INK_2, MUTED = '#192421', '#374642', '#51645D'
PAPER, LIGHT, RULE = '#F9F8F2', '#EFEEE6', '#CBD0C4'
RED, WHITE = '#A3342B', '#FFFFFF'


def fonts():
    folder = Path(os.environ.get('WINDIR', 'C:/Windows')) / 'Fonts'
    choices = {
        'Serif': ('georgia.ttf', 'Times-Roman'),
        'SerifBold': ('georgiab.ttf', 'Times-Bold'),
        'Sans': ('arial.ttf', 'Helvetica'),
        'SansBold': ('arialbd.ttf', 'Helvetica-Bold'),
        'Mono': ('consola.ttf', 'Courier'),
    }
    names = {}
    for name, (filename, fallback) in choices.items():
        path = folder / filename
        if path.is_file():
            pdfmetrics.registerFont(TTFont(name, str(path)))
            names[name] = name
        else:
            names[name] = fallback
    return names


FONTS = fonts()


def c(hex_value):
    return colors.HexColor(hex_value)


class Report:
    def __init__(self, target, members, group):
        self.canvas = Canvas(str(target), pagesize=A4, pageCompression=1)
        self.canvas.setTitle('The Signal | News Aggregator')
        self.canvas.setSubject('Software Design Patterns final project report')
        self.figure_regions = []
        self.members = members
        self.group = group

    def text(self, value, x, y, size=10, font='Sans', color=INK, align='left'):
        self.canvas.setFillColor(c(color))
        self.canvas.setFont(FONTS[font], size)
        baseline = HEIGHT - y - size * 0.79
        if align == 'center':
            self.canvas.drawCentredString(x, baseline, value)
        elif align == 'right':
            self.canvas.drawRightString(x, baseline, value)
        else:
            self.canvas.drawString(x, baseline, value)

    def paragraph(self, value, x, y, width=PRINT_WIDTH, size=11, leading=16, color=INK_2):
        style = ParagraphStyle('body', fontName=FONTS['Sans'], fontSize=size,
                               leading=leading, textColor=c(color))
        paragraph = Paragraph(value, style)
        _, height = paragraph.wrap(width, HEIGHT)
        paragraph.drawOn(self.canvas, x, HEIGHT - y - height)
        return y + height

    def rect(self, x, y, width, height, fill=None, stroke=None):
        if fill:
            self.canvas.setFillColor(c(fill))
        if stroke:
            self.canvas.setStrokeColor(c(stroke))
            self.canvas.setLineWidth(0.65)
        self.canvas.rect(x, HEIGHT - y - height, width, height,
                         fill=bool(fill), stroke=bool(stroke))

    def line(self, *points, color=RULE, width=0.75, dashed=False):
        self.canvas.saveState()
        self.canvas.setStrokeColor(c(color))
        self.canvas.setLineWidth(width)
        if dashed:
            self.canvas.setDash(2.7, 2.5)
        path = self.canvas.beginPath()
        path.moveTo(points[0][0], HEIGHT - points[0][1])
        for x, y in points[1:]:
            path.lineTo(x, HEIGHT - y)
        self.canvas.drawPath(path)
        self.canvas.restoreState()

    def polygon(self, points, fill, stroke=INK_2):
        self.canvas.setFillColor(c(fill))
        self.canvas.setStrokeColor(c(stroke))
        self.canvas.setLineWidth(0.8)
        path = self.canvas.beginPath()
        path.moveTo(points[0][0], HEIGHT - points[0][1])
        for x, y in points[1:]:
            path.lineTo(x, HEIGHT - y)
        path.close()
        self.canvas.drawPath(path, fill=1, stroke=1)

    def arrow(self, *points, end='open', dashed=False, color=INK_2):
        self.line(*points, color=color, dashed=dashed)
        (before_x, before_y), (x, y) = points[-2:]
        length = ((x - before_x) ** 2 + (y - before_y) ** 2) ** 0.5
        ux, uy = (x - before_x) / length, (y - before_y) / length
        size = 8 if end == 'triangle' else 5
        sides = [(x - size * ux + direction * 3.3 * uy,
                  y - size * uy - direction * 3.3 * ux) for direction in [-1, 1]]
        if end == 'triangle':
            self.polygon([sides[0], (x, y), sides[1]], WHITE, color)
        else:
            self.line(sides[0], (x, y), sides[1], color=color)

    def page(self, index, label):
        self.rect(0, 0, WIDTH, HEIGHT, fill=PAPER)
        self.text('THE SIGNAL', LEFT, 27, 10, 'SansBold', RED)
        self.text(label.upper(), RIGHT, 27, 8.5, 'SansBold', MUTED, 'right')
        self.line((LEFT, 45), (RIGHT, 45))
        self.line((LEFT, 796), (RIGHT, 796))
        self.text('SOFTWARE DESIGN PATTERNS  /  FINAL PROJECT', LEFT, 807, 8, 'SansBold', MUTED)
        self.text(f'{index:02d} / 08', RIGHT, 807, 8, 'SansBold', MUTED, 'right')

    def heading(self, value, subtitle=''):
        self.text(value, LEFT, 63, 34, 'SerifBold')
        return self.paragraph(subtitle, LEFT, 111) if subtitle else 111

    def section(self, label, body, y, x=LEFT, width=PRINT_WIDTH):
        self.text(label, x, y, 14, 'SerifBold')
        return self.paragraph(body, x, y + 22, width)

    def end(self):
        self.canvas.showPage()

    def opening(self):
        self.page(1, 'Introduction')
        self.text('SOFTWARE DESIGN PATTERNS', LEFT, 79, 11, 'SansBold', RED)
        self.text('The Signal', LEFT, 113, 65, 'SerifBold')
        self.text('News Aggregator', LEFT, 195, 37, 'Serif')
        self.paragraph('A Java HTTP service that combines two public news sources in one searchable feed.',
                       LEFT, 254, 395, size=14, leading=20)

        self.rect(LEFT, 323, PRINT_WIDTH, 93, fill=LIGHT)
        self.text('BBC NEWS', LEFT + 16, 340, 8, 'SansBold', MUTED)
        self.text('World RSS', LEFT + 16, 356, 18, 'SerifBold')
        self.text('DEV COMMUNITY', LEFT + 16, 384, 8, 'SansBold', MUTED)
        self.text('Articles JSON', LEFT + 116, 383, 11, 'SansBold')
        self.line((LEFT + 216, 340), (LEFT + 216, 398), color=RULE)
        self.text('SHARED ARTICLE MODEL', LEFT + 232, 340, 8, 'SansBold', MUTED)
        self.text('Searchable feed', LEFT + 232, 356, 18, 'SerifBold')
        self.text('Category, source, tags and story link', LEFT + 232, 384, 9, 'Sans', MUTED)

        self.section('Introduction',
                     'Publishers expose article data in different formats. The Signal normalizes RSS items and JSON '
                     'articles, assigns categories and tags, and provides a single HTTP API for searching, filtering '
                     'and sorting the feed. Each article includes a link to its story.', 452)
        self.section('Project scope',
                     'Demo mode loads ten bundled fictional articles offline. Live mode fetches BBC News World RSS '
                     'and DEV Community articles and returns articles from any source that loads successfully. Both '
                     'launch modes expose the same Java 17 API and Server-Sent Events (SSE) stream. GET /api lists '
                     'the routes. Web mode adds a browser interface at /.', 542)

        self.line((LEFT, 651), (RIGHT, 651))
        self.text('PROJECT TEAM', LEFT, 664, 9, 'SansBold', RED)
        if self.group:
            self.text(f'Group {self.group}', LEFT, 683, 10, 'Sans', MUTED)
        for index, member in enumerate(self.members or [None] * 2):
            x = LEFT + index * 254
            self.text(f'Member {index + 1}', x, 715, 10, 'SansBold')
            if member:
                bottom = self.paragraph(escape(member), x, 732, 223, size=9.5, leading=13)
                if bottom > 788:
                    raise ValueError('Member name does not fit on the cover')
            else:
                self.line((x, 749), (x + 223, 749))
        self.end()

    def patterns(self):
        self.page(2, 'Design and implementation')
        self.heading('Six design patterns', 'The patterns coordinate source ingestion, article enrichment and feed delivery.')
        rows = [
            ('Adapter', 'Structural', 'RssNewsAdapter and JsonNewsAdapter implement NewsSource. They wrap RssFeedReader and DevApiClient, turning records from both readers into Article objects.'),
            ('Factory Method', 'Creational', 'NewsSourceFactory selects a feed URI. RssSourceFactory and JsonSourceFactory override create(URI) to construct the corresponding NewsSource.'),
            ('Decorator', 'Structural', 'CategorizedArticle implements and wraps Article. It adds a category and normalized tags and delegates the remaining operations.'),
            ('Observer', 'Behavioral', 'FeedPublisher notifies subscribers after the new snapshot is stored. NewsServer sends its revision and mode to clients connected to /api/events.'),
            ('Strategy', 'Behavioral', 'NewsFacade selects a SortStrategy for each request. LatestFirst, OldestFirst and TitleOrder provide comparators for the three supported sort orders.'),
            ('Facade', 'Structural', 'NewsServer uses NewsFacade to query the feed, refresh sources and manage subscriptions through a single interface.'),
        ]
        self.rect(LEFT, 150, PRINT_WIDTH, 29, fill=INK)
        self.text('PATTERN', LEFT + 12, 160, 8.5, 'SansBold', WHITE)
        self.text('APPLICATION ROLE', LEFT + 150, 160, 8.5, 'SansBold', WHITE)
        for i, (name, group, description) in enumerate(rows):
            y = 179 + i * 63
            self.rect(LEFT, y, PRINT_WIDTH, 63, fill=WHITE if i % 2 == 0 else LIGHT)
            self.text(name, LEFT + 12, y + 11, 14, 'SerifBold')
            self.text(group, LEFT + 12, y + 31, 9.5, 'Sans', MUTED)
            self.paragraph(description, LEFT + 150, y + 10, PRINT_WIDTH - 165,
                           size=10.1, leading=13.3)
        self.line((LEFT, 557), (RIGHT, 557))

        self.section('Defense presentation assignments',
                     'The course requires each student to implement at least two patterns. The table shows who will '
                     'explain each group of patterns.', 584)
        roles = ['Adapter, Factory Method and Decorator', 'Strategy, Observer and Facade']
        for i, patterns in enumerate(roles):
            y = 654 + i * 29
            self.line((LEFT, y), (RIGHT, y))
            self.text(f'Participant {i + 1}', LEFT + 8, y + 8, 10, 'SansBold')
            self.text(patterns, LEFT + 150, y + 8, 10, 'Sans')
        self.line((LEFT, 654 + len(roles) * 29), (RIGHT, 654 + len(roles) * 29))
        self.end()

    def uml_box(self, name, x, y, width, stereotype='', operation='', highlight=False):
        operations = operation.splitlines()
        height = 33 + 11 * len(operations) if operations else 31
        self.rect(x, y, width, height, fill=INK if highlight else WHITE, stroke=RULE)
        color = WHITE if highlight else INK
        if stereotype:
            self.text(stereotype, x + width / 2, y + 4, 6.9, 'Sans',
                      LIGHT if highlight else MUTED, 'center')
        self.text(name, x + width / 2, y + (14 if stereotype else 11),
                  7.8 if len(name) > 21 else 8.2, 'SansBold', color, 'center')
        if operations:
            self.line((x, y + 27), (x + width, y + 27))
            for index, member in enumerate(operations):
                if pdfmetrics.stringWidth(member, FONTS['Mono'], 7.4) > width - 12:
                    raise ValueError(f'UML operation does not fit in {name}: {member}')
                self.text(member, x + 6, y + 32 + 11 * index, 7.4, 'Mono', color)
        return height

    def relationship_label(self, value, x, y, align='center', color=MUTED):
        self.text(value, x, y, 7.7, 'Sans', color, align)

    def diagram_end(self, index, filename, bottom):
        if bottom > 794:
            raise ValueError(f'Diagram content exceeds the page: {filename}')
        self.figure_regions.append((index - 1, (34, 55, WIDTH - 34, bottom + 14), filename))
        self.end()

    def client_and_server(self):
        self.uml_box('HTTP / SSE client', 219, 163, 157, '«external client»', 'JSON requests / SSE stream')
        self.uml_box('NewsServer', 219, 250, 157, '«HTTP server»', 'feed / refresh / events')
        self.arrow((298, 207), (298, 250), dashed=True)
        self.relationship_label('HTTP requests and SSE subscription', 306, 222, 'left')
        self.polygon([(50, 166), (185, 166), (195, 176), (195, 209), (50, 209)], LIGHT, RULE)
        self.line((185, 166), (185, 176), (195, 176))
        self.text('GET /api/events', 58, 176, 8, 'Mono', INK)
        self.text('Streams feed events with', 58, 187, 8, 'Sans', INK_2)
        self.text('the revision and mode.', 58, 197, 8, 'Sans', INK_2)
        self.line((195, 205), (228, 250), color=MUTED, dashed=True)

    def project_diagram(self):
        self.page(3, 'Architecture / whole project')
        self.heading('Project overview', 'HTTP API, source ingestion, article model and notification classes')
        self.client_and_server()
        self.uml_box('FeedJson', 50, 250, 117, '«serializer»', '~snapshot(snapshot)')
        self.arrow((219, 272), (167, 272), dashed=True)
        self.relationship_label('encodes', 193, 260)

        self.uml_box('NewsFacade', 219, 338, 157, '«facade»', '+query(...)  +refresh(mode)', highlight=True)
        self.arrow((298, 294), (298, 338))
        self.relationship_label('delegates', 305, 313, 'left')
        self.uml_box('FeedSnapshot', 50, 338, 141, '«record»',
                     '+articles(): List<Article>\n+sources(): List<SourceStatus>')
        self.arrow((219, 360), (191, 360))
        self.relationship_label('holds', 205, 348)
        self.uml_box('FeedPublisher', 425, 338, 120, '«subject»', '~publish(snapshot)')
        self.uml_box('Consumer<FeedSnapshot>', 425, 250, 120, '«observer»', '+accept(snapshot)')
        self.arrow((376, 360), (425, 360))
        self.arrow((485, 338), (485, 294))
        self.relationship_label('0..*', 492, 311, 'left')
        self.arrow((376, 272), (425, 272), dashed=True)
        self.relationship_label('listener', 400, 260)

        for x, label in ((50, 'SOURCE INGESTION'), (224, 'ARTICLE MODEL'), (408, 'SORTING')):
            self.text(label, x, 416, 8, 'SansBold', RED)
        self.uml_box('NewsSourceFactory', 50, 453, 132, '«abstract»', '~forMode(mode)')
        self.uml_box('Article', 224, 453, 147, '«interface»', '+category()  +tags()')
        self.uml_box('SortStrategy', 408, 453, 137, '«interface»', '+comparator()')
        self.arrow((242, 382), (242, 405), (195, 405), (195, 475), (182, 475))
        self.arrow((298, 382), (298, 453), dashed=True)
        self.arrow((352, 382), (352, 405), (396, 405), (396, 475), (408, 475), dashed=True)

        self.uml_box('NewsSource', 50, 533, 132, '«interface»', '+fetch()')
        self.arrow((117, 497), (117, 533), dashed=True)
        self.relationship_label('returns', 125, 512, 'left')
        self.uml_box('RssNewsAdapter', 50, 614, 117)
        self.uml_box('JsonNewsAdapter', 50, 664, 117)
        self.line((167, 629), (185, 629), (185, 679), (167, 679), color=INK_2, dashed=True)
        self.arrow((185, 629), (185, 595), (117, 595), (117, 577), end='triangle', dashed=True)

        self.uml_box('BaseArticle', 224, 558, 126, '«record»')
        self.uml_box('CategorizedArticle', 224, 624, 126, '«decorator»', '+category()  +tags()')
        self.line((350, 574), (361, 574), (361, 647), (350, 647), color=INK_2, dashed=True)
        self.arrow((361, 574), (361, 527), (298, 527), (298, 497), end='triangle', dashed=True)
        self.arrow((224, 647), (212, 647), (212, 475), (224, 475))
        self.relationship_label('wraps', 207, 567, 'right')

        for y, label in ((558, 'LatestFirst'), (611, 'OldestFirst'), (664, 'TitleOrder')):
            self.uml_box(label, 408, y, 118)
            self.line((526, y + 15), (537, y + 15), color=INK_2, dashed=True)
        self.arrow((537, 679), (537, 527), (476, 527), (476, 497), end='triangle', dashed=True)

        self.diagram_end(3, 'project-class-diagram.svg', 695)

    def participant_one_diagram(self):
        self.page(4, 'Architecture / participant 1')
        self.heading('Source processing')

        for x, label in ((50, 'FACTORY METHOD'), (222, 'ADAPTER'), (405, 'DECORATOR')):
            self.text(label, x, 173, 8.8, 'SansBold', RED)
        self.uml_box('NewsSourceFactory', 50, 196, 121, '«abstract»', '~forMode(mode)\n#create(URI)')
        self.uml_box('NewsSource', 222, 196, 129, '«interface»', '+fetch(): List<Article>')
        self.uml_box('Article', 405, 196, 131, '«interface»', '+category()  +tags()')
        self.arrow((171, 218), (222, 218), dashed=True)
        self.relationship_label('returns', 196, 206)
        self.arrow((351, 218), (405, 218), dashed=True)
        self.relationship_label('returns list of', 378, 206)

        self.uml_box('RssSourceFactory', 50, 337, 121)
        self.uml_box('JsonSourceFactory', 50, 523, 121)
        self.line((50, 352), (41, 352), (41, 538), (50, 538), color=INK_2)
        self.arrow((41, 352), (41, 271), (109, 271), (109, 251), end='triangle')
        self.uml_box('RssNewsAdapter', 222, 330, 129, '«adapter»', '+fetch()')
        self.uml_box('JsonNewsAdapter', 222, 516, 129, '«adapter»', '+fetch()')
        self.arrow((171, 352), (222, 352), dashed=True)
        self.arrow((171, 538), (222, 538), dashed=True)
        self.relationship_label('«create»', 196, 340)
        self.relationship_label('«create»', 196, 526)
        self.line((351, 362), (365, 362), (365, 548), (351, 548), color=INK_2, dashed=True)
        self.arrow((365, 362), (365, 272), (286, 272), (286, 240), end='triangle', dashed=True)
        self.uml_box('RssFeedReader', 222, 426, 129, '«adaptee»', '~readItems()')
        self.uml_box('DevApiClient', 222, 614, 129, '«adaptee»', '~readPosts()')
        self.arrow((286, 374), (286, 426))
        self.arrow((286, 560), (286, 614))
        self.relationship_label('reads XML', 294, 396, 'left')
        self.relationship_label('reads JSON', 294, 583, 'left')

        self.uml_box('BaseArticle', 405, 330, 131, '«record»')
        self.uml_box('CategorizedArticle', 405, 516, 131, '«decorator»', '+category()  +tags()')
        self.line((405, 345), (389, 345), (389, 538), (405, 538), color=INK_2, dashed=True)
        self.arrow((389, 345), (389, 287), (470, 287), (470, 240), end='triangle', dashed=True)
        self.arrow((536, 538), (553, 538), (553, 218), (536, 218))
        self.relationship_label('wraps', 546, 456, 'right')
        self.relationship_label('1', 543, 204, 'left')
        self.arrow((351, 350), (405, 350), dashed=True)
        self.arrow((351, 528), (377, 528), (377, 389), (460, 389), (460, 361), dashed=True)

        self.diagram_end(4, 'participant-1-class-diagram.svg', 658)

    def participant_two_diagram(self):
        self.page(5, 'Architecture / participant 2')
        self.heading('Feed delivery', 'Strategy, Observer and Facade')
        self.client_and_server()

        self.uml_box('NewsFacade', 219, 367, 157, '«facade»', '+query(...)  +refresh(mode)', highlight=True)
        self.arrow((298, 294), (298, 367))
        self.relationship_label('query / refresh / subscribe', 306, 327, 'left')
        self.uml_box('SortStrategy', 50, 367, 115, '«interface»', '+comparator()')
        self.arrow((219, 389), (165, 389), dashed=True)
        self.relationship_label('selects', 192, 377)
        for y, label in ((467, 'LatestFirst'), (547, 'OldestFirst'), (627, 'TitleOrder')):
            self.uml_box(label, 50, y, 115)
            self.line((165, y + 15), (181, y + 15), color=INK_2, dashed=True)
        self.arrow((181, 642), (181, 439), (107, 439), (107, 411), end='triangle', dashed=True)

        self.uml_box('FeedSnapshot', 219, 521, 157, '«record»', '+revision()  +articles()')
        self.arrow((298, 411), (298, 521))
        self.relationship_label('current snapshot', 307, 463, 'left')
        self.uml_box('FeedPublisher', 423, 367, 122, '«subject»', '~publish(snapshot)')
        self.arrow((376, 389), (423, 389))
        self.relationship_label('signals', 399, 377)
        self.uml_box('Consumer<FeedSnapshot>', 423, 521, 122, '«observer»', '+accept(snapshot)')
        self.arrow((484, 411), (484, 521))
        self.relationship_label('notifies', 491, 463, 'left')
        self.relationship_label('0..*', 491, 503, 'left')
        self.arrow((376, 272), (554, 272), (554, 543), (545, 543), dashed=True)
        self.relationship_label('creates SSE listener', 470, 259)

        self.diagram_end(5, 'participant-2-class-diagram.svg', 658)

    def screenshots(self, draft):
        self.page(6, 'Terminal screenshots')
        self.heading('The API in use', 'These terminal sessions use the API-only launch mode with the bundled Demo feed.')
        figures = [
            ('feed.png', 'The Demo feed returns two tagged science articles and the status of both sources.'),
            ('filtered.png', 'A POST refresh advances the feed from revision 1 to 2. The subscribed terminal receives the new SSE event.'),
        ]
        for i, (filename, caption) in enumerate(figures):
            path = DOCS / 'screenshots' / filename
            y = 144 + i * 319
            if path.is_file():
                image = ImageReader(str(path))
                image_width, image_height = image.getSize()
                scale = min(PRINT_WIDTH / image_width, 286 / image_height)
                w, h = image_width * scale, image_height * scale
                x = LEFT + (PRINT_WIDTH - w) / 2
                self.canvas.drawImage(image, x, HEIGHT - y - h, width=w, height=h)
                self.rect(x, y, w, h, stroke=RULE)
                caption_y = y + h + 11
            elif draft:
                self.rect(LEFT, y, PRINT_WIDTH, 286, fill=LIGHT, stroke=RULE)
                self.text(filename, WIDTH / 2, y + 132, 12, 'Mono', MUTED, 'center')
                caption_y = y + 297
            else:
                raise FileNotFoundError(path)
            self.paragraph(caption, LEFT, caption_y, size=9.7, leading=13, color=MUTED)
        self.end()

    def code_block(self, label, source_path, lines, y):
        height = 26 + len(lines) * 11.3
        self.text(label, LEFT, y, 12.5, 'SerifBold')
        self.text(source_path, RIGHT, y + 2, 8.7, 'Sans', MUTED, 'right')
        self.rect(LEFT, y + 18, PRINT_WIDTH, height, fill=WHITE, stroke=RULE)
        for i, value in enumerate(lines):
            if pdfmetrics.stringWidth(value, FONTS['Mono'], 8.9) > PRINT_WIDTH - 24:
                raise ValueError(f'Code line is too wide in {source_path}: {value}')
            self.text(value, LEFT + 12, y + 29 + i * 11.3, 8.9, 'Mono')
        return y + height + 34

    def source(self, draft):
        self.page(7, 'Java source excerpts')
        y = self.heading('Java implementation', 'Selected Java excerpts from the feed workflow. Ellipses mark omitted lines.') + 16
        for label, file, fragments in EXCERPTS:
            path = JAVA / file
            if not path.exists() and draft:
                lines = ['// ' + file.name, '// Excerpt reserved for source verification.']
            else:
                lines = extract(path, fragments)
            y = self.code_block(label, file.name, lines, y)
        if y > 795:
            raise ValueError('Source excerpts overflow the allotted page')
        self.end()

    def closing(self):
        self.page(8, 'Conclusion and further work')
        self.heading('Defense walkthrough', 'On Windows with JDK 17 or later, extract the ZIP. Open START.cmd for the web interface or START_BACKEND.cmd for the JSON feed. Both print the API base URL.')
        steps = [
            ('Load the demo', 'Request GET /api/feed and inspect the RSS and JSON source counts.'),
            ('Read a filtered feed', 'Add category, source and sort query parameters and inspect the article tags.'),
            ('Show the observer', 'Stream GET /api/events. In a second terminal, POST /api/refresh?mode=demo.'),
            ('Exercise live retrieval', 'POST /api/refresh?mode=live and inspect the source statuses.'),
        ]
        for index, (label, description) in enumerate(steps, 1):
            y = 151 + (index - 1) * 51
            self.line((LEFT, y), (RIGHT, y))
            self.text(f'{index:02d}', LEFT, y + 10, 11, 'SansBold', RED)
            self.text(label, LEFT + 31, y + 10, 12, 'SerifBold')
            self.paragraph(description, LEFT + 31, y + 29, PRINT_WIDTH - 31, size=9.7, leading=12.3)
        self.line((LEFT, 355), (RIGHT, 355))
        self.section('Conclusion',
                     'The Signal combines BBC News RSS and DEV Community JSON in one API. The six patterns '
                     'support concrete steps in that pipeline, from constructing the right source adapters to '
                     'enriching, ordering and publishing the resulting feed.', 375)
        self.section('Further work',
                     'Persist feed snapshots across restarts, refresh on a schedule and support per-reader topic '
                     'subscriptions. Cross-publisher story grouping could also identify related articles whose '
                     'links differ.', 458)
        self.section('Verification',
                     'Tests passed for both adapters, deduplication, filtering, all sort orders and delivery to two '
                     'SSE listeners. They also cover partial source failure, the four-second fetch deadline and the '
                     '2 MiB response limit. A live run on 16 November 2026 retrieved 23 BBC and 12 DEV articles.', 535)
        self.text('References', LEFT, 627, 14, 'SerifBold')
        items = [
            ('Course handout, Final Project Defense Requirements', 'Requirements and suggested News Aggregator topic'),
            ('AIT Software Design Patterns, lectures 2, 3, 5-8', 'https://lms.astanait.edu.kz/course/view.php?id=2312'),
            ('BBC News World RSS', 'https://feeds.bbci.co.uk/news/world/rss.xml'),
            ('Forem API v1 documentation', 'https://developers.forem.com/api/v1'),
        ]
        for i, (description, url) in enumerate(items):
            y = 650 + i * 36
            self.text(description, LEFT, y, 10.2, 'SansBold')
            self.text(url, LEFT, y + 15, 9.2, 'Sans', MUTED)
            if url.startswith('https://'):
                width = pdfmetrics.stringWidth(url, FONTS['Sans'], 9.2)
                self.canvas.linkURL(url, (LEFT, HEIGHT - y - 27, LEFT + width, HEIGHT - y - 14), relative=0)
        self.end()


def extract(path, fragments):
    content = path.read_text(encoding='utf-8').splitlines()
    lines = []
    for string, count in fragments:
        start = next((i for i, line in enumerate(content) if string in line), None)
        if start is None:
            raise ValueError(f'Missing code excerpt in {path.name}: {string}')
        snippet = content[start:start + count]
        indent = min(len(line) - len(line.lstrip()) for line in snippet if line.strip())
        if lines:
            lines.append('// ...')
        lines.extend(line[indent:] for line in snippet)
    return lines


# Fragments are copied from the Java source at build time.
CORE = Path('newsaggregator') / 'core'
EXCERPTS = [
    ('Factory Method creates an RSS adapter', CORE / 'RssSourceFactory.java', [
        ('protected NewsSource create(', 3),
    ]),
    ('Adapter converts feed records', CORE / 'RssNewsAdapter.java', [
        ('for (RssFeedReader.Item', 4),
    ]),
    ('Decorator delegates and adds metadata', CORE / 'CategorizedArticle.java', [
        ('@Override public String title()', 1),
        ('@Override public String category()', 2),
    ]),
    ('Strategy selects the sort order', CORE / 'NewsFacade.java', [
        ('SortStrategy strategy =', 1),
        ('.sorted(strategy.comparator())', 2),
    ]),
    ('Observer publishes the committed snapshot', CORE / 'NewsFacade.java', [
        ('snapshot = updated;', 2),
        ('publisher.subscribe(observer);', 1),
    ]),
    ('Facade handles the HTTP feed query', Path('newsaggregator') / 'NewsServer.java', [
        ('FeedSnapshot result = facade.query(', 3),
    ]),
]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--draft', action='store_true', help='Preview the report in the temp folder.')
    parser.add_argument('--members', nargs=2, metavar='NAME', default=[],
                        help='Two quoted full names in presentation order.')
    parser.add_argument('--group', default='', help='Course group identifier.')
    args = parser.parse_args()
    if any(not name.strip() for name in args.members):
        parser.error('--members expects two nonempty names')
    filename = 'News Aggregator - Project Report.pdf'
    target = (Path(tempfile.gettempdir()) if args.draft else ROOT) / filename
    report = Report(target, args.members, args.group)
    report.opening()
    report.patterns()
    report.project_diagram()
    report.participant_one_diagram()
    report.participant_two_diagram()
    report.screenshots(args.draft)
    report.source(args.draft)
    report.closing()
    report.canvas.save()
    if not args.draft:
        with pymupdf.open(target) as pdf:
            for page_number, rectangle, svg_name in report.figure_regions:
                page = pdf[page_number]
                page.set_cropbox(pymupdf.Rect(rectangle))
                (DOCS / svg_name).write_text(page.get_svg_image(), encoding='utf-8')
    print(target)


if __name__ == '__main__':
    main()
