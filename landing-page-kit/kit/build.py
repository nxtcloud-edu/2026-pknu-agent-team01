#!/usr/bin/env python3
"""
landing.json  ->  index.html (단일 파일)

사용법 (프로젝트 루트에서 실행):
    python3 landing-page-kit/kit/build.py landing.json
    python3 landing-page-kit/kit/build.py landing.json --open    # 빌드 후 브라우저로 열기
    python3 landing-page-kit/kit/build.py landing.json --check   # 검사만, 파일 안 만듦

파이썬 3.8+ 표준 라이브러리만 씁니다. 설치할 것 없습니다.
결과물은 CSS/JS가 전부 안에 들어있는 <프로젝트명>_landingPage.html 한 개입니다.
프로젝트 루트에 landing.json 을 두고 실행하면 그 옆에 만들어집니다.
"""

import argparse
import html
import json
import re
import sys
import webbrowser
from pathlib import Path

KIT = Path(__file__).resolve().parent


# ---------------------------------------------------------------- 유틸

def die(msg):
    print(f"\n오류: {msg}\n", file=sys.stderr)
    sys.exit(1)


def warn(msg):
    print(f"  경고: {msg}")


def esc(s):
    return html.escape(str(s if s is not None else ""), quote=True)


def rich(s):
    """문장 안의 간단한 마크다운만 처리: **굵게**, `코드`, [링크](url)"""
    t = esc(s)
    t = re.sub(r"\[([^\]]+)\]\(([^)\s]+)\)", r'<a href="\2">\1</a>', t)
    t = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", t)
    t = re.sub(r"`(.+?)`", r"<code>\1</code>", t)
    return t


def paras(s):
    if not s:
        return ""
    blocks = [b.strip() for b in re.split(r"\n\s*\n", str(s).strip()) if b.strip()]
    return "\n".join(f"<p>{rich(b)}</p>" for b in blocks)


def cols_for(items, explicit):
    n = len(items)
    if explicit:
        return max(1, min(4, int(explicit)))
    if n <= 1:
        return 1
    if n == 2 or n == 4:
        return 2
    return 3


def anchor(sec):
    return sec.get("id") or sec.get("type")


def out_filename(spec, spec_path):
    """<프로젝트명>_landingPage.html 을 만든다.

    프로젝트명은 meta.slug 가 있으면 그것, 없으면 landing.json 이 놓인 폴더 이름.
    """
    slug = (spec.get("meta") or {}).get("slug")
    if not slug:
        slug = spec_path.parent.resolve().name
    slug = re.sub(r"[^0-9A-Za-z\uac00-\ud7a3_-]+", "-", str(slug)).strip("-")
    return f"{slug or 'landing'}_landingPage.html"


# ---------------------------------------------------------------- 조각

def cta_html(c, fallback_style="primary", extra=""):
    if not c:
        return ""
    label = c.get("label")
    href = c.get("href")
    if not label or not href:
        return ""
    style = c.get("style", fallback_style)
    tgt = ' target="_blank" rel="noopener"' if c.get("newTab") else ""
    cls = f"btn btn-{style} {extra}".strip()
    return f'<a class="{cls}" href="{esc(href)}"{tgt}>{esc(label)}</a>'


def cta_row(sec, center=False):
    buttons = cta_html(sec.get("primaryCta"), "primary") + cta_html(sec.get("secondaryCta"), "secondary")
    if not buttons:
        return ""
    note = f'<p class="note">{rich(sec["note"])}</p>' if sec.get("note") else ""
    cls = "ctarow" + (" center" if center else "")
    return f'<div class="{cls}">{buttons}</div>{note}'


def head_block(sec, center=False):
    out = []
    if sec.get("eyebrow"):
        out.append(f'<p class="eyebrow">{esc(sec["eyebrow"])}</p>')
    if sec.get("heading"):
        out.append(f'<h2>{rich(sec["heading"])}</h2>')
    if sec.get("subheading"):
        out.append(f'<p class="lead">{rich(sec["subheading"])}</p>')
    if not out:
        return ""
    cls = "sechead" + (" center" if center else "")
    return f'<div class="{cls}">{"".join(out)}</div>'


def img_tag(src, alt, cls="", loading="lazy"):
    if not src:
        return ""
    c = f' class="{cls}"' if cls else ""
    return f'<img src="{esc(src)}" alt="{esc(alt or "")}" loading="{loading}"{c}>'


# ---------------------------------------------------------------- 섹션 렌더러

def r_hero(sec, ctx):
    left = []
    if sec.get("eyebrow"):
        left.append(f'<p class="eyebrow">{esc(sec["eyebrow"])}</p>')
    left.append(f'<h1>{rich(sec.get("heading", ctx["brand"].get("name", "")))}</h1>')
    if sec.get("subheading"):
        left.append(f'<p class="lead">{rich(sec["subheading"])}</p>')
    left.append(cta_row(sec))
    body = "".join(left)

    if sec.get("video") or sec.get("image"):
        art = video_tag(sec["video"]) if sec.get("video") else img_tag(
            sec["image"], sec.get("imageAlt"), "shot", "eager")
        return (
            f'<div class="hero-split">'
            f'<div class="hero-copy">{body}</div>'
            f'<div class="hero-art">{art}</div>'
            f"</div>"
        )
    return f'<div class="hero-copy center">{body}</div>'


def r_logos(sec, ctx):
    head = ""
    if sec.get("heading"):
        head = f'<p class="logos-title">{esc(sec["heading"])}</p>'
    cells = []
    for it in sec.get("items", []):
        if it.get("image"):
            cells.append(f'<li>{img_tag(it["image"], it.get("label"))}</li>')
        elif it.get("label"):
            cells.append(f'<li><span>{esc(it["label"])}</span></li>')
    return head + f'<ul class="logos">{"".join(cells)}</ul>'


def r_stats(sec, ctx):
    cells = []
    for it in sec.get("items", []):
        cells.append(
            f'<li><span class="stat-v">{esc(it.get("value", ""))}</span>'
            f'<span class="stat-l">{esc(it.get("label", ""))}</span></li>'
        )
    n = cols_for(sec.get("items", []), sec.get("columns"))
    return head_block(sec, center=True) + f'<ul class="stats cols-{n}">{"".join(cells)}</ul>'


def _cards(sec, ctx, numbered=False):
    items = sec.get("items", [])
    n = cols_for(items, sec.get("columns"))
    cards = []
    for i, it in enumerate(items, 1):
        badge = ""
        if numbered:
            badge = f'<span class="step-n">{i}</span>'
        elif it.get("icon"):
            badge = f'<span class="card-icon" aria-hidden="true">{esc(it["icon"])}</span>'
        title = f'<h3>{rich(it["title"])}</h3>' if it.get("title") else ""
        body = f'<p>{rich(it["body"])}</p>' if it.get("body") else ""
        shot = img_tag(it.get("image"), it.get("title"), "card-img")
        cards.append(f'<li class="card">{badge}{shot}{title}{body}</li>')
    cls = "cards steps" if numbered else "cards"
    return head_block(sec, center=True) + f'<ul class="{cls} cols-{n}">{"".join(cards)}</ul>'


def r_problem(sec, ctx):
    intro = head_block(sec, center=True)
    body = f'<div class="prose center">{paras(sec.get("body"))}</div>' if sec.get("body") else ""
    items = sec.get("items", [])
    if not items:
        return intro + body
    n = cols_for(items, sec.get("columns"))
    cards = []
    for it in items:
        icon = f'<span class="card-icon" aria-hidden="true">{esc(it["icon"])}</span>' if it.get("icon") else ""
        title = f'<h3>{rich(it["title"])}</h3>' if it.get("title") else ""
        txt = f'<p>{rich(it["body"])}</p>' if it.get("body") else ""
        cards.append(f'<li class="card card-problem">{icon}{title}{txt}</li>')
    return intro + body + f'<ul class="cards cols-{n}">{"".join(cards)}</ul>'


def r_steps(sec, ctx):
    return _cards(sec, ctx, numbered=True)


def r_features(sec, ctx):
    return _cards(sec, ctx)


def r_showcase(sec, ctx):
    flip = " flip" if ctx["showcase_count"] % 2 else ""
    ctx["showcase_count"] += 1
    copy = []
    if sec.get("eyebrow"):
        copy.append(f'<p class="eyebrow">{esc(sec["eyebrow"])}</p>')
    if sec.get("heading"):
        copy.append(f'<h2>{rich(sec["heading"])}</h2>')
    if sec.get("body"):
        copy.append(f'<div class="prose">{paras(sec["body"])}</div>')
    copy.append(cta_row(sec))
    if sec.get("video"):
        art = video_tag(sec["video"])
    else:
        art = img_tag(sec.get("image"), sec.get("imageAlt"), "shot")
    return (
        f'<div class="split{flip}">'
        f'<div class="split-copy">{"".join(copy)}</div>'
        f'<div class="split-art">{art}</div>'
        f"</div>"
    )


def video_tag(src):
    """유튜브/비메오 링크는 임베드로, mp4 등은 <video> 로."""
    if not src:
        return ""
    m = re.search(r"(?:youtube\.com/(?:watch\?v=|embed/|live/)|youtu\.be/)([A-Za-z0-9_-]{6,})", src)
    if m:
        return (f'<div class="video"><iframe src="https://www.youtube.com/embed/{m.group(1)}" '
                f'title="데모 영상" allow="accelerometer;autoplay;clipboard-write;encrypted-media;'
                f'gyroscope;picture-in-picture" allowfullscreen loading="lazy"></iframe></div>')
    m = re.search(r"vimeo\.com/(?:video/)?(\d+)", src)
    if m:
        return (f'<div class="video"><iframe src="https://player.vimeo.com/video/{m.group(1)}" '
                f'title="데모 영상" allow="autoplay;fullscreen;picture-in-picture" '
                f'allowfullscreen loading="lazy"></iframe></div>')
    return f'<div class="video"><video src="{esc(src)}" controls playsinline preload="metadata"></video></div>'


def r_team(sec, ctx):
    items = sec.get("items", [])
    n = cols_for(items, sec.get("columns"))
    cells = []
    for it in items:
        if it.get("image"):
            face = f'<img class="face" src="{esc(it["image"])}" alt="{esc(it.get("title", ""))}" loading="lazy">'
        else:
            initial = (it.get("title") or "?").strip()[:1]
            face = f'<span class="face face-txt" aria-hidden="true">{esc(initial)}</span>'
        name = f'<h3>{esc(it["title"])}</h3>' if it.get("title") else ""
        role = f'<p class="member-role">{esc(it["role"])}</p>' if it.get("role") else ""
        bio = f'<p class="member-bio">{rich(it["body"])}</p>' if it.get("body") else ""
        link = ""
        if it.get("url"):
            link = (f'<a class="member-link" href="{esc(it["url"])}" target="_blank" rel="noopener">'
                    f'{esc(it.get("label") or "프로필 보기")}</a>')
        cells.append(f'<li class="member">{face}{name}{role}{bio}{link}</li>')
    return head_block(sec, center=True) + f'<ul class="cards team cols-{n}">{"".join(cells)}</ul>'


def r_testimonials(sec, ctx):
    items = sec.get("items", [])
    n = cols_for(items, sec.get("columns"))
    cells = []
    for it in items:
        who = []
        if it.get("author"):
            who.append(f'<strong>{esc(it["author"])}</strong>')
        if it.get("role"):
            who.append(f'<span>{esc(it["role"])}</span>')
        foot = f'<figcaption>{"".join(who)}</figcaption>' if who else ""
        cells.append(
            f'<li><figure class="quote"><blockquote>{rich(it.get("quote", ""))}</blockquote>{foot}</figure></li>'
        )
    return head_block(sec, center=True) + f'<ul class="cards cols-{n}">{"".join(cells)}</ul>'


def r_pricing(sec, ctx):
    items = sec.get("items", [])
    n = cols_for(items, sec.get("columns"))
    cells = []
    for it in items:
        feat = "".join(f"<li>{rich(f)}</li>" for f in it.get("features", []))
        feats = f'<ul class="plan-feats">{feat}</ul>' if feat else ""
        price = ""
        if it.get("price"):
            per = f'<span class="per">{esc(it["period"])}</span>' if it.get("period") else ""
            price = f'<p class="price">{esc(it["price"])}{per}</p>'
        desc = f'<p class="plan-desc">{rich(it["body"])}</p>' if it.get("body") else ""
        tag = '<span class="plan-tag">추천</span>' if it.get("featured") else ""
        btn = cta_html(it.get("cta"), "primary" if it.get("featured") else "secondary", "btn-block")
        cls = "plan featured" if it.get("featured") else "plan"
        cells.append(
            f'<li class="{cls}">{tag}<h3>{esc(it.get("title", ""))}</h3>{price}{desc}{feats}{btn}</li>'
        )
    return head_block(sec, center=True) + f'<ul class="plans cols-{n}">{"".join(cells)}</ul>'


def r_faq(sec, ctx):
    rows = []
    for it in sec.get("items", []):
        rows.append(
            f"<details><summary>{rich(it.get('q', ''))}</summary>"
            f'<div class="faq-a">{paras(it.get("a", ""))}</div></details>'
        )
    return head_block(sec, center=True) + f'<div class="faq">{"".join(rows)}</div>'


def r_cta(sec, ctx):
    inner = []
    if sec.get("heading"):
        inner.append(f'<h2>{rich(sec["heading"])}</h2>')
    if sec.get("subheading"):
        inner.append(f'<p class="lead">{rich(sec["subheading"])}</p>')
    inner.append(cta_row(sec, center=True))
    return f'<div class="cta-box center">{"".join(inner)}</div>'


def r_form(sec, ctx):
    fields = []
    for it in sec.get("items", []):
        name = it.get("name") or it.get("label") or "field"
        label = it.get("label") or it.get("title") or name
        ftype = it.get("fieldType", "text")
        req = " required" if it.get("required") else ""
        star = ' <span class="req">*</span>' if it.get("required") else ""
        ph = f' placeholder="{esc(it["placeholder"])}"' if it.get("placeholder") else ""
        fid = f"f-{re.sub(r'[^a-zA-Z0-9_-]', '-', str(name))}"

        if ftype == "textarea":
            ctl = f'<textarea id="{fid}" name="{esc(name)}" rows="4"{ph}{req}></textarea>'
        elif ftype == "select":
            opts = "".join(f"<option>{esc(o)}</option>" for o in it.get("options", []))
            ctl = f'<select id="{fid}" name="{esc(name)}"{req}><option value="">선택하세요</option>{opts}</select>'
        elif ftype == "checkbox":
            fields.append(
                f'<label class="fld check"><input type="checkbox" id="{fid}" name="{esc(name)}"{req}>'
                f"<span>{rich(label)}{star}</span></label>"
            )
            continue
        else:
            ctl = f'<input type="{esc(ftype)}" id="{fid}" name="{esc(name)}"{ph}{req}>'

        fields.append(f'<label class="fld"><span>{rich(label)}{star}</span>{ctl}</label>')

    action = sec.get("action", "")
    attrs = f' action="{esc(action)}" method="post"' if action else ' data-noaction="1"'
    note = f'<p class="note">{rich(sec["note"])}</p>' if sec.get("note") else ""
    btn = sec.get("primaryCta", {}).get("label") if sec.get("primaryCta") else None
    return (
        head_block(sec, center=True)
        + f'<form class="lform"{attrs}>{"".join(fields)}'
        f'<button class="btn btn-primary btn-block" type="submit">{esc(btn or "제출하기")}</button>'
        f'{note}<p class="form-msg" hidden></p></form>'
    )


RENDERERS = {
    "hero": r_hero,
    "logos": r_logos,
    "stats": r_stats,
    "problem": r_problem,
    "steps": r_steps,
    "features": r_features,
    "showcase": r_showcase,
    "team": r_team,
    "testimonials": r_testimonials,
    "pricing": r_pricing,
    "faq": r_faq,
    "cta": r_cta,
    "form": r_form,
}

DEFAULT_BG = {"hero": "hero", "cta": "primary"}


# ---------------------------------------------------------------- 헤더 / 푸터

def render_nav(nav, ctx):
    b = ctx["brand"]
    logo = img_tag(b.get("logo"), b.get("name"), "logo-img", "eager") if b.get("logo") else ""
    brand = f'<a class="brand" href="#top">{logo}<span>{esc(b.get("name", ""))}</span></a>'
    links = "".join(
        f'<a href="#{esc(a)}">{esc(l)}</a>' for a, l in ctx["navlinks"]
    )
    toggle = ""
    if ctx["darkmode"] == "auto":
        toggle = (
            '<button class="tgl" type="button" data-theme-toggle aria-label="밝게/어둡게 전환">'
            '<span data-icon-light>☀</span><span data-icon-dark>☾</span></button>'
        )
    btn = cta_html((nav or {}).get("primaryCta"), "primary", "btn-sm")
    burger = (
        '<button class="burger" type="button" data-menu aria-label="메뉴 열기" aria-expanded="false">'
        "<span></span><span></span><span></span></button>"
        if links
        else ""
    )
    return (
        f'<header class="nav" id="top"><div class="wrap nav-in">{brand}'
        f'<nav class="nav-links" data-menu-panel>{links}</nav>'
        f'<div class="nav-act">{toggle}{btn}{burger}</div></div></header>'
    )


def render_footer(sec, ctx):
    b = ctx["brand"]
    sec = sec or {}
    left = [f'<p class="foot-brand">{esc(b.get("name", ""))}</p>']
    if sec.get("body"):
        left.append(f'<div class="foot-body">{paras(sec["body"])}</div>')
    contact = []
    if b.get("contactEmail"):
        contact.append(f'<a href="mailto:{esc(b["contactEmail"])}">{esc(b["contactEmail"])}</a>')
    if b.get("phone"):
        contact.append(f'<span>{esc(b["phone"])}</span>')
    if b.get("address"):
        contact.append(f'<span>{esc(b["address"])}</span>')
    if contact:
        left.append(f'<p class="foot-contact">{" · ".join(contact)}</p>')

    links = []
    for it in sec.get("items", []):
        if it.get("url"):
            links.append(f'<a href="{esc(it["url"])}">{esc(it.get("label") or it["url"])}</a>')
    for s in b.get("socials", []):
        links.append(f'<a href="{esc(s["url"])}" target="_blank" rel="noopener">{esc(s["label"])}</a>')
    right = f'<nav class="foot-links">{"".join(links)}</nav>' if links else ""

    return (
        f'<footer class="foot"><div class="wrap foot-in"><div>{"".join(left)}</div>{right}</div>'
        f'<div class="wrap foot-legal">© {esc(b.get("name", ""))}. All rights reserved.</div></footer>'
    )


# ---------------------------------------------------------------- CSS

CSS = r"""
*,*::before,*::after{box-sizing:border-box}
html{scroll-behavior:smooth;scroll-padding-top:72px;-webkit-text-size-adjust:100%}
body{margin:0;background:var(--bg);color:var(--text);font-family:var(--font-sans);
  font-size:17px;line-height:1.7;-webkit-font-smoothing:antialiased;overflow-x:hidden;
  word-break:keep-all;overflow-wrap:break-word}
img{max-width:100%;height:auto;display:block}
a{color:var(--primary);text-decoration:none}
a:hover{text-decoration:underline}
h1,h2,h3{font-family:var(--font-display);line-height:1.25;letter-spacing:-.02em;margin:0 0 .5em;
  text-wrap:balance;font-weight:800;word-break:keep-all}
h1{font-size:clamp(2rem,5.2vw,3.6rem)}
h2{font-size:clamp(1.6rem,3.4vw,2.4rem)}
h3{font-size:1.12rem;font-weight:700}
p{margin:0 0 1em}
p:last-child{margin-bottom:0}
code{background:var(--surface-alt);border:1px solid var(--border);border-radius:6px;
  padding:.1em .38em;font-size:.88em}
:focus-visible{outline:3px solid var(--ring);outline-offset:3px;border-radius:6px}
.wrap{width:min(1120px,100% - 2.5rem);margin-inline:auto}
.center{text-align:center}

/* 섹션 */
.sec{padding:var(--pad) 0;position:relative}
.sec.bg-base{background:var(--bg)}
.sec.bg-alt{background:var(--bg-alt)}
.sec.bg-surface{background:var(--surface)}
.sec.bg-hero{background:var(--hero-bg);color:var(--hero-text)}
.sec.bg-hero .lead,.sec.bg-hero .eyebrow,.sec.bg-hero .note{color:var(--hero-text-muted)}
.sec.bg-hero h1,.sec.bg-hero h2{color:var(--hero-text)}
.sec.bg-primary{background:var(--primary);color:var(--on-primary)}
.sec.bg-primary h2{color:var(--on-primary)}
.sec.bg-primary .lead,.sec.bg-primary .note{color:var(--on-primary);opacity:.85}
.sechead{max-width:62ch;margin:0 0 2.75rem}
.sechead.center{margin-inline:auto}
.eyebrow{font-size:.8rem;font-weight:700;letter-spacing:.09em;text-transform:uppercase;
  color:var(--primary);margin:0 0 .7rem}
.lead{font-size:clamp(1.02rem,1.7vw,1.2rem);color:var(--text-muted);margin:0;text-wrap:pretty}
.prose{max-width:66ch;color:var(--text-muted)}
.prose.center{margin-inline:auto}

/* 버튼 */
.btn{display:inline-flex;align-items:center;justify-content:center;gap:.45em;
  padding:.82em 1.55em;border-radius:var(--r-btn);font-weight:700;font-size:.98rem;
  border:1.5px solid transparent;cursor:pointer;transition:transform .15s,background .15s,box-shadow .15s;
  text-decoration:none!important;font-family:inherit;line-height:1.2}
.btn:hover{transform:translateY(-2px)}
.btn:active{transform:translateY(0)}
.btn-primary{background:var(--primary);color:var(--on-primary);box-shadow:var(--shadow)}
.btn-primary:hover{background:var(--primary-hover)}
.btn-secondary{background:var(--surface);color:var(--text);border-color:var(--border)}
.btn-secondary:hover{border-color:var(--primary);color:var(--primary)}
.btn-ghost{background:transparent;color:inherit;border-color:currentColor;opacity:.9}
.btn-sm{padding:.6em 1.1em;font-size:.9rem}
.btn-block{display:flex;width:100%;margin-top:1.25rem}
.bg-hero .btn-secondary,.bg-primary .btn-secondary{background:transparent;color:inherit;border-color:currentColor}
.bg-primary .btn-primary{background:var(--surface);color:var(--primary)}
.ctarow{display:flex;flex-wrap:wrap;gap:.7rem;margin-top:2rem}
.ctarow.center{justify-content:center}
.note{font-size:.85rem;color:var(--text-muted);margin:.9rem 0 0}

/* 헤더 */
.nav{position:sticky;top:0;z-index:50;background:var(--bg);background:color-mix(in srgb,var(--bg) 88%,transparent);
  backdrop-filter:saturate(180%) blur(12px);border-bottom:1px solid var(--border)}
.nav-in{display:flex;align-items:center;gap:1rem;height:64px}
.brand{display:flex;align-items:center;gap:.55rem;font-weight:800;font-size:1.05rem;
  color:var(--text);text-decoration:none!important;flex:0 0 auto}
.logo-img{height:28px;width:auto}
.nav-links{display:flex;gap:.35rem;margin-left:auto;flex-wrap:wrap}
.nav-links a{padding:.45rem .75rem;border-radius:8px;font-size:.94rem;font-weight:600;
  color:var(--text-muted);text-decoration:none!important}
.nav-links a:hover{color:var(--text);background:var(--surface-alt)}
.nav-act{display:flex;align-items:center;gap:.5rem;margin-left:.5rem}
.tgl{width:38px;height:38px;border-radius:10px;border:1px solid var(--border);background:var(--surface);
  color:var(--text);cursor:pointer;font-size:1rem;line-height:1;display:grid;place-items:center}
.tgl:hover{border-color:var(--primary)}
[data-icon-dark]{display:none}
.burger{display:none;width:38px;height:38px;border-radius:10px;border:1px solid var(--border);
  background:var(--surface);cursor:pointer;padding:9px 8px;flex-direction:column;justify-content:space-between}
.burger span{display:block;height:2px;background:var(--text);border-radius:2px}

/* 히어로 */
.hero-split{display:grid;grid-template-columns:1fr 1fr;gap:clamp(2rem,5vw,4rem);align-items:center}
.hero-copy{max-width:62ch}
.hero-copy.center{margin-inline:auto;text-align:center}
.hero-copy.center .ctarow{justify-content:center}
.shot{border-radius:var(--r-card);box-shadow:var(--shadow);border:1px solid var(--border);width:100%}

/* 좌우 분할 */
.split{display:grid;grid-template-columns:1fr 1fr;gap:clamp(2rem,5vw,4rem);align-items:center}
.split.flip .split-copy{order:2}
.split-copy{max-width:56ch}

/* 카드 */
.cards,.stats,.plans,.logos{list-style:none;margin:0;padding:0;display:grid;gap:1.15rem}
.cols-1{grid-template-columns:1fr}
.cols-2{grid-template-columns:repeat(2,1fr)}
.cols-3{grid-template-columns:repeat(3,1fr)}
.cols-4{grid-template-columns:repeat(4,1fr)}
.card{background:var(--surface);border:1px solid var(--border);border-radius:var(--r-card);
  padding:1.7rem;transition:transform .18s,box-shadow .18s}
.card:hover{transform:translateY(-3px);box-shadow:var(--shadow)}
.card p{color:var(--text-muted);font-size:.97rem;margin:0}
.card h3{margin:0 0 .45rem}
.card-icon{font-size:1.9rem;line-height:1;display:block;margin-bottom:.9rem}
.card-img{border-radius:calc(var(--r-card) - 6px);margin-bottom:1rem}
.card-problem{background:var(--surface-alt)}
.steps .card{padding-top:1.5rem}
.step-n{display:grid;place-items:center;width:34px;height:34px;border-radius:50%;
  background:var(--primary-soft);color:var(--primary);font-weight:800;font-size:.95rem;margin-bottom:.9rem}

/* 숫자 */
.stats{gap:1rem;text-align:center}
.stats li{background:var(--surface);border:1px solid var(--border);border-radius:var(--r-card);padding:1.6rem 1rem}
.stat-v{display:block;font-family:var(--font-display);font-size:clamp(1.8rem,3.4vw,2.5rem);
  font-weight:800;color:var(--primary);letter-spacing:-.03em;line-height:1.1}
.stat-l{display:block;font-size:.9rem;color:var(--text-muted);margin-top:.4rem}

/* 로고 */
.logos-title{text-align:center;color:var(--text-muted);font-size:.88rem;font-weight:600;
  letter-spacing:.05em;margin:0 0 1.5rem}
.logos{display:flex;flex-wrap:wrap;justify-content:center;align-items:center;gap:1rem 2.5rem}
.logos li{opacity:.72}
.logos img{max-height:32px;width:auto}
.logos span{font-weight:700;font-size:1.05rem;color:var(--text-muted)}

/* 팀 */
.team .member{text-align:center;display:flex;flex-direction:column;align-items:center}
.face{width:88px;height:88px;border-radius:50%;object-fit:cover;margin-bottom:1rem;
  border:2px solid var(--border);background:var(--surface-alt)}
.face-txt{display:grid;place-items:center;font-family:var(--font-display);font-size:2rem;
  font-weight:800;color:var(--primary);background:var(--primary-soft);border-color:transparent}
.member h3{margin:0 0 .2rem}
.member-role{font-size:.9rem;font-weight:600;color:var(--primary);margin:0 0 .6rem}
.member-bio{font-size:.94rem;color:var(--text-muted);margin:0}
.member-link{margin-top:.8rem;font-size:.88rem;font-weight:600}

/* 영상 */
.video{position:relative;aspect-ratio:16/9;border-radius:var(--r-card);overflow:hidden;
  border:1px solid var(--border);box-shadow:var(--shadow);background:#000}
.video iframe,.video video{position:absolute;inset:0;width:100%;height:100%;border:0;display:block}

/* 후기 */
.quote{margin:0}
.quote blockquote{margin:0 0 1.1rem;font-size:1.02rem;line-height:1.75}
.quote blockquote::before{content:"“";font-size:2.4rem;line-height:0;color:var(--primary);
  vertical-align:-.35em;margin-right:.1em}
.quote figcaption{display:flex;flex-direction:column;font-size:.9rem}
.quote figcaption span{color:var(--text-muted)}

/* 가격 */
.plans{align-items:start}
.plan{position:relative;background:var(--surface);border:1px solid var(--border);
  border-radius:var(--r-card);padding:1.9rem;display:flex;flex-direction:column}
.plan.featured{border-color:var(--primary);border-width:2px;box-shadow:var(--shadow)}
.plan-tag{position:absolute;top:-12px;left:1.9rem;background:var(--primary);color:var(--on-primary);
  font-size:.75rem;font-weight:700;padding:.25em .8em;border-radius:99px}
.price{font-family:var(--font-display);font-size:2.1rem;font-weight:800;margin:.2rem 0 .6rem;letter-spacing:-.03em}
.price .per{font-size:.95rem;font-weight:600;color:var(--text-muted);margin-left:.15em}
.plan-desc{color:var(--text-muted);font-size:.95rem}
.plan-feats{list-style:none;margin:1.1rem 0 0;padding:0;display:grid;gap:.55rem;font-size:.95rem}
.plan-feats li{padding-left:1.5rem;position:relative;color:var(--text-muted)}
.plan-feats li::before{content:"✓";position:absolute;left:0;color:var(--primary);font-weight:800}
.plan .btn{margin-top:auto}

/* FAQ */
.faq{max-width:760px;margin-inline:auto;border-top:1px solid var(--border)}
.faq details{border-bottom:1px solid var(--border)}
.faq summary{cursor:pointer;list-style:none;padding:1.15rem .5rem;font-weight:700;
  display:flex;justify-content:space-between;gap:1rem;align-items:flex-start}
.faq summary::-webkit-details-marker{display:none}
.faq summary::after{content:"+";font-size:1.4rem;line-height:1;color:var(--primary);flex:0 0 auto;transition:transform .2s}
.faq details[open] summary::after{transform:rotate(45deg)}
.faq summary:hover{color:var(--primary)}
.faq-a{padding:0 .5rem 1.3rem;color:var(--text-muted);font-size:.97rem}

/* 최종 CTA */
.cta-box{max-width:64ch;margin-inline:auto}

/* 폼 */
.lform{max-width:520px;margin-inline:auto;display:grid;gap:1rem}
.fld{display:grid;gap:.4rem;font-size:.92rem;font-weight:600}
.fld input,.fld select,.fld textarea{font:inherit;font-weight:400;padding:.75em .9em;
  border:1px solid var(--border);border-radius:var(--r-btn);background:var(--surface);
  color:var(--text);width:100%}
.fld input:focus,.fld select:focus,.fld textarea:focus{border-color:var(--primary);outline:none;
  box-shadow:0 0 0 3px var(--ring)}
.fld.check{grid-template-columns:auto 1fr;align-items:start;gap:.6rem;font-weight:400;font-size:.9rem}
.fld.check input{width:auto;margin-top:.35em}
.req{color:var(--primary)}
.form-msg{font-size:.9rem;margin:.4rem 0 0;padding:.7em 1em;border-radius:var(--r-btn);
  background:var(--primary-soft);color:var(--primary)}

/* 푸터 */
.foot{background:var(--bg-alt);border-top:1px solid var(--border);padding:3rem 0 1.5rem;
  font-size:.92rem;color:var(--text-muted)}
.foot-in{display:flex;flex-wrap:wrap;gap:2rem;justify-content:space-between}
.foot-brand{font-weight:800;color:var(--text);font-size:1.05rem;margin:0 0 .5rem}
.foot-contact{margin:.6rem 0 0}
.foot-links{display:flex;flex-wrap:wrap;gap:.4rem 1.4rem;align-items:flex-start}
.foot-links a{color:var(--text-muted);font-weight:600}
.foot-legal{border-top:1px solid var(--border);margin-top:2rem;padding-top:1.2rem;font-size:.82rem;opacity:.75}

/* 등장 애니메이션 */
[data-reveal]{opacity:0;transform:translateY(18px);transition:opacity .6s ease,transform .6s ease}
[data-reveal].in{opacity:1;transform:none}

/* 반응형 */
@media (max-width:900px){
  .cols-3,.cols-4{grid-template-columns:repeat(2,1fr)}
  .hero-split,.split{grid-template-columns:1fr}
  .split.flip .split-copy{order:0}
  .hero-copy,.split-copy{max-width:none}
}
@media (max-width:720px){
  body{font-size:16px}
  .nav-links{position:absolute;top:64px;left:0;right:0;flex-direction:column;gap:0;
    background:var(--surface);border-bottom:1px solid var(--border);padding:.6rem 1.25rem 1rem;
    display:none;box-shadow:var(--shadow)}
  .nav-links.open{display:flex}
  .nav-links a{padding:.7rem .4rem}
  .burger{display:flex}
  .cols-2,.cols-3,.cols-4{grid-template-columns:1fr}
  .stats,.team{grid-template-columns:repeat(2,1fr)}
  .team .card{padding:1.3rem .9rem}
  .face{width:68px;height:68px}
  .foot-in{flex-direction:column;gap:1.5rem}
}
@media (prefers-reduced-motion:reduce){
  html{scroll-behavior:auto}
  *{animation:none!important;transition:none!important}
  [data-reveal]{opacity:1;transform:none}
}
@media print{.nav,.tgl,.burger{display:none}.sec{padding:1.5rem 0;break-inside:avoid}
  [data-reveal]{opacity:1!important;transform:none!important}}
"""

JS = r"""
(function(){
  var b=document.querySelector('[data-menu]'),p=document.querySelector('[data-menu-panel]');
  if(b&&p){b.addEventListener('click',function(){
    var o=p.classList.toggle('open');b.setAttribute('aria-expanded',o?'true':'false');});
    p.addEventListener('click',function(e){if(e.target.tagName==='A'){p.classList.remove('open');
      b.setAttribute('aria-expanded','false');}});}

  var t=document.querySelector('[data-theme-toggle]');
  function paint(m){document.documentElement.setAttribute('data-theme',m);
    var l=document.querySelector('[data-icon-light]'),d=document.querySelector('[data-icon-dark]');
    if(l&&d){l.style.display=m==='dark'?'none':'';d.style.display=m==='dark'?'':'none';}}
  if(t){
    var saved=null;try{saved=localStorage.getItem('lp-theme');}catch(e){}
    var sys=window.matchMedia&&window.matchMedia('(prefers-color-scheme:dark)').matches?'dark':'light';
    paint(saved||sys);
    t.addEventListener('click',function(){
      var n=document.documentElement.getAttribute('data-theme')==='dark'?'light':'dark';
      paint(n);try{localStorage.setItem('lp-theme',n);}catch(e){}});
  }

  if('IntersectionObserver' in window){
    var io=new IntersectionObserver(function(es){es.forEach(function(e){
      if(e.isIntersecting){e.target.classList.add('in');io.unobserve(e.target);}});},
      {rootMargin:'0px 0px -8% 0px',threshold:.05});
    document.querySelectorAll('[data-reveal]').forEach(function(el){io.observe(el);});
  }else{document.querySelectorAll('[data-reveal]').forEach(function(el){el.classList.add('in');});}

  function revealAll(){document.querySelectorAll('[data-reveal]').forEach(function(el){el.classList.add('in');});}
  window.addEventListener('beforeprint',revealAll);
  if(window.matchMedia){var mq=window.matchMedia('print');
    if(mq.addEventListener)mq.addEventListener('change',function(e){if(e.matches)revealAll();});}

  var f=document.querySelector('form[data-noaction]');
  if(f){f.addEventListener('submit',function(e){e.preventDefault();
    var m=f.querySelector('.form-msg');
    if(m){m.hidden=false;m.textContent='폼 전송이 아직 연결되지 않았습니다. landing.json 의 form 섹션에 action 값을 넣어주세요.';}});}
})();
"""


# ---------------------------------------------------------------- 조립

def css_vars(tokens):
    return "".join(f"--{k}:{v};" for k, v in tokens.items())


def build(spec, themes):
    theme_id = spec.get("theme")
    theme = next((t for t in themes["themes"] if t["id"] == theme_id), None)
    if not theme:
        ids = ", ".join(t["id"] for t in themes["themes"])
        die(f"theme '{theme_id}' 를 찾을 수 없습니다. 가능한 값: {ids}")

    ov = spec.get("themeOverrides") or {}
    light = dict(theme["tokens"], **(ov.get("tokens") or {}))
    dark = dict(theme["dark"], **(ov.get("dark") or {}))

    opts = spec.get("options") or {}
    dm = opts.get("darkMode", "auto")
    radius = {"sharp": ("4px", "6px"), "soft": ("10px", "16px"), "round": ("99px", "24px")}[
        opts.get("radius", "soft")
    ]
    pad = {"compact": "clamp(2.75rem,6vw,4rem)", "normal": "clamp(4rem,8vw,6.5rem)",
           "airy": "clamp(5rem,10vw,9rem)"}[opts.get("density", "normal")]

    base = light if dm != "dark-only" else dark
    root = (
        f":root{{{css_vars(base)}"
        f'--font-sans:{theme["font"]["sans"]};--font-display:{theme["font"]["display"]};'
        f"--r-btn:{radius[0]};--r-card:{radius[1]};--pad:{pad};color-scheme:"
        f'{"dark" if dm == "dark-only" else "light dark" if dm == "auto" else "light"}}}'
    )
    if dm == "auto":
        root += (
            f"@media (prefers-color-scheme:dark){{:root:not([data-theme='light']){{{css_vars(dark)}}}}}"
            f":root[data-theme='dark']{{{css_vars(dark)}}}"
            f":root[data-theme='light']{{{css_vars(light)}}}"
        )

    brand = spec.get("brand", {})
    sections = spec.get("sections", [])
    ctx = {
        "brand": brand,
        "darkmode": dm,
        "showcase_count": 0,
        "navlinks": [(anchor(s), s["navLabel"]) for s in sections if s.get("navLabel")],
    }

    nav_sec = next((s for s in sections if s.get("type") == "nav"), None)
    foot_sec = next((s for s in sections if s.get("type") == "footer"), None)

    body = []
    if nav_sec:
        body.append(render_nav(nav_sec, ctx))

    auto_bg = "base"
    reveal = "" if opts.get("animate") is False else " data-reveal"
    for sec in sections:
        t = sec.get("type")
        if t in ("nav", "footer"):
            continue
        fn = RENDERERS.get(t)
        if not fn:
            warn(f"모르는 섹션 type '{t}' -> 건너뜁니다")
            continue
        bg = sec.get("background") or DEFAULT_BG.get(t)
        if not bg:
            bg = auto_bg
            auto_bg = "alt" if auto_bg == "base" else "base"
        else:
            auto_bg = "alt" if bg in ("base", "hero") else "base"
        inner = fn(sec, ctx)
        body.append(
            f'<section id="{esc(anchor(sec))}" class="sec sec-{esc(t)} bg-{esc(bg)}"{reveal}>'
            f'<div class="wrap">{inner}</div></section>'
        )

    body.append(render_footer(foot_sec, ctx))

    meta = spec.get("meta", {})
    lang = meta.get("lang", "ko")
    fav = meta.get("favicon", "")
    if fav and len(fav) <= 4 and not re.search(r"[./]", fav):
        icon = (
            '<link rel="icon" href="data:image/svg+xml,'
            "%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22%3E"
            f"%3Ctext y=%22.9em%22 font-size=%2290%22%3E{esc(fav)}%3C/text%3E%3C/svg%3E\">"
        )
    elif fav:
        icon = f'<link rel="icon" href="{esc(fav)}">'
    else:
        icon = ""

    og = []
    og.append(f'<meta property="og:title" content="{esc(meta.get("title", ""))}">')
    og.append(f'<meta property="og:description" content="{esc(meta.get("description", ""))}">')
    og.append('<meta property="og:type" content="website">')
    if meta.get("ogImage"):
        og.append(f'<meta property="og:image" content="{esc(meta["ogImage"])}">')
    og.append('<meta name="twitter:card" content="summary_large_image">')
    canon = f'<link rel="canonical" href="{esc(meta["canonical"])}">' if meta.get("canonical") else ""

    fonts = (
        '<link rel="preconnect" href="https://cdn.jsdelivr.net">'
        '<link rel="stylesheet" href="https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/'
        'dist/web/variable/pretendardvariable-dynamic-subset.min.css">'
    )

    return f"""<!doctype html>
<html lang="{esc(lang)}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>{esc(meta.get('title', brand.get('name', '')))}</title>
<meta name="description" content="{esc(meta.get('description', ''))}">
{icon}{canon}
{''.join(og)}
{fonts}
<style>{root}{CSS}</style>
<noscript><style>[data-reveal]{{opacity:1!important;transform:none!important}}</style></noscript>
</head>
<body>
{''.join(body)}
<script>{JS}</script>
</body>
</html>
"""


# ---------------------------------------------------------------- 검사

def check(spec):
    problems, notes = [], []
    types = [s.get("type") for s in spec.get("sections", [])]

    if "hero" not in types:
        problems.append("hero 섹션이 없습니다. 방문자가 뭘 보는 페이지인지 알 수 없습니다.")
    if "footer" not in types:
        notes.append("footer 섹션이 없습니다. 넣는 걸 권합니다.")
    if "nav" in types and types[0] != "nav":
        notes.append("nav 는 sections 의 맨 앞에 두는 게 자연스럽습니다.")

    has_cta = any(
        s.get("primaryCta") for s in spec.get("sections", [])
    ) or "form" in types
    if not has_cta:
        problems.append("primaryCta 가 어디에도 없습니다. 방문자가 할 행동이 없는 페이지입니다.")

    raw = json.dumps(spec, ensure_ascii=False)
    for ph in ("[TODO]", "TODO:", "Lorem ipsum", "여기에 내용", "placeholder text", "XXX"):
        if ph.lower() in raw.lower():
            problems.append(f"자리표시자가 남아있습니다: '{ph}'")

    bad_labels = {"자세히 보기", "더 알아보기", "클릭", "확인", "바로가기", "Learn more", "Click here"}
    for s in spec.get("sections", []):
        for key in ("primaryCta", "secondaryCta"):
            c = s.get(key)
            if c and c.get("label", "").strip() in bad_labels:
                notes.append(f"[{s.get('type')}] 버튼 라벨 '{c['label']}' 은 행동이 안 보입니다. 동사+목적어로 바꾸세요.")
            if c and c.get("href", "").strip() in ("#", ""):
                problems.append(f"[{s.get('type')}] 버튼 '{c.get('label')}' 의 href 가 비어있습니다.")

    ids = {anchor(s) for s in spec.get("sections", [])}
    for s in spec.get("sections", []):
        for key in ("primaryCta", "secondaryCta"):
            c = s.get(key)
            if c and c.get("href", "").startswith("#"):
                tgt = c["href"][1:]
                if tgt and tgt not in ids and tgt != "top":
                    problems.append(f"버튼 '{c.get('label')}' 이 존재하지 않는 앵커 '#{tgt}' 를 가리킵니다.")

    for s in spec.get("sections", []):
        if s.get("image") and not s.get("imageAlt"):
            notes.append(f"[{s.get('type')}] 이미지에 imageAlt 가 없습니다.")
        if s.get("type") == "form" and not s.get("action"):
            notes.append("form 섹션에 action 이 없습니다. 제출해도 아무 데도 전달되지 않습니다.")
        if s.get("type") == "pricing":
            f = sum(1 for i in s.get("items", []) if i.get("featured"))
            if f > 1:
                notes.append(f"pricing 에 featured 플랜이 {f}개입니다. 1개만 두세요.")

    m = spec.get("meta", {})
    if len(m.get("title", "")) > 60:
        notes.append(f"meta.title 이 {len(m['title'])}자입니다. 60자 이하로.")
    if len(m.get("description", "")) > 160:
        notes.append(f"meta.description 이 {len(m['description'])}자입니다. 160자 이하로.")
    if not m.get("favicon"):
        notes.append("meta.favicon 이 비어있습니다. 이모지 하나만 넣어도 좋습니다.")

    n = len([t for t in types if t not in ("nav", "footer")])
    if n < 4:
        notes.append(f"콘텐츠 섹션이 {n}개뿐입니다. 6~9개를 권합니다.")
    if n > 12:
        notes.append(f"콘텐츠 섹션이 {n}개입니다. 끝까지 읽는 사람이 줄어듭니다.")

    return problems, notes


# ---------------------------------------------------------------- main

def main():
    # 한글 Windows 는 기본 출력 인코딩이 cp949 라, 출력이 파일·파이프로 넘어가면
    # 일부 문자에서 UnicodeEncodeError 로 죽는다. UTF-8 로 맞춰준다.
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except Exception:
            pass

    ap = argparse.ArgumentParser(description="landing.json 으로 단일 index.html 을 만듭니다")
    ap.add_argument("spec", nargs="?", help="landing.json 경로")
    ap.add_argument("-o", "--out",
                    help="출력 경로 (기본: landing.json 과 같은 폴더의 <프로젝트명>_landingPage.html)")
    ap.add_argument("--open", action="store_true", dest="do_open", help="빌드 후 브라우저로 열기")
    ap.add_argument("--check", action="store_true", help="검사만 하고 파일은 만들지 않음")
    ap.add_argument("--theme", help="landing.json 을 고치지 않고 이번 한 번만 다른 테마로 빌드 (색 비교용)")
    ap.add_argument("--list-themes", action="store_true", help="쓸 수 있는 테마 목록 보기")
    a = ap.parse_args()

    if a.list_themes:
        th = json.loads((KIT / "themes.json").read_text(encoding="utf-8"))
        print("\n쓸 수 있는 테마:\n")
        for t in th["themes"]:
            print(f"  {t['id']:<10} {t['name']:<14} {t['mood']}")
            print(f"  {'':<10} 어울리는 것: {', '.join(t['bestFor'])}\n")
        print("직접 만들려면 kit/themes.json 에 같은 형식으로 항목을 추가하세요.")
        print("일부 색만 바꿀 거면 landing.json 의 themeOverrides 를 쓰세요.\n")
        sys.exit(0)

    if not a.spec:
        ap.error("landing.json 경로가 필요합니다.")
    sp = Path(a.spec)
    if not sp.exists():
        die(f"{sp} 파일이 없습니다.")
    try:
        spec = json.loads(sp.read_text(encoding="utf-8"))
    except json.JSONDecodeError as e:
        die(f"{sp} 의 JSON 문법이 잘못되었습니다. {e.lineno}번째 줄: {e.msg}")

    themes = json.loads((KIT / "themes.json").read_text(encoding="utf-8"))

    if a.theme:
        spec["theme"] = a.theme

    print(f"\n검사 중: {sp}")
    problems, notes = check(spec)
    for p in problems:
        print(f"  [고쳐야 함] {p}")
    for n in notes:
        print(f"  [확인 권장] {n}")
    if not problems and not notes:
        print("  통과. 걸리는 것 없습니다.")

    sys.stdout.flush()

    if a.check:
        sys.exit(1 if problems else 0)

    out = Path(a.out) if a.out else sp.parent / out_filename(spec, sp)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(build(spec, themes), encoding="utf-8")
    size = out.stat().st_size / 1024
    print(f"\n생성 완료: {out}  ({size:.0f} KB, 단일 파일)")
    print(f"테마: {spec.get('theme')}   섹션: {len(spec.get('sections', []))}개")
    if problems:
        print("\n※ [고쳐야 함] 항목이 남아있습니다. landing.json 을 고치고 다시 실행하세요.")

    if a.do_open:
        webbrowser.open(out.resolve().as_uri())


if __name__ == "__main__":
    main()
