#!/usr/bin/env python3
"""i18n guard for the game UI.

Checks that:
  * every T('...') argument exists in the EN dictionary
  * every static Arabic text node exists in the EN dictionary
  * every screen (id="s-*") lives INSIDE #ui — applyLang() only walks #ui,
    so a screen outside it silently stays Arabic in English mode
  * the EN dictionary has no duplicate keys (JS would silently keep the last)
  * no leftover multiplayer strings
"""
import re, sys
from collections import Counter
from html.parser import HTMLParser

html = open(sys.argv[1] if len(sys.argv) > 1 else 'www/index.html', encoding='utf-8').read()

dict_body = re.search(r"const EN=\{(.*?)\};\n", html, re.S).group(1)
raw_keys = re.findall(r"'((?:[^'\\]|\\.)*)':", dict_body)
keys = set(raw_keys)

missT = [t for t in set(re.findall(r"\bT\('([^']+)'", html)) if t not in keys]
assert not missT, 'i18n: T() args missing from dict: %r' % missT

title = re.search(r'<title>([^<]*)</title>', html).group(1)
ns = re.sub(r'<script[\s\S]*?</script>', '', html)
ns = re.sub(r'<style[\s\S]*?</style>', '', ns)
missH = {m.group(1).strip() for m in re.finditer(r'>([^<>]+)<', ns)}
missH = {t for t in missH if re.search(r'[\u0600-\u06EF]', t) and t not in keys and t != title}
assert not missH, 'i18n: static arabic text missing from dict: %r' % missH

dups = [k for k, n in Counter(raw_keys).items() if n > 1]
assert not dups, 'i18n: duplicate keys in the EN dictionary: %r' % dups


class _Ancestry(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.stack = []
        self.parents = {}

    def handle_starttag(self, tag, attrs):
        i = dict(attrs).get('id')
        if i:
            self.parents[i] = [x[1] for x in self.stack if x[1]]
        self.stack.append((tag, i))

    def handle_endtag(self, tag):
        for k in range(len(self.stack) - 1, -1, -1):
            if self.stack[k][0] == tag:
                del self.stack[k:]
                break


anc = _Ancestry()
anc.feed(html)
outside = sorted(k for k in anc.parents if k.startswith('s-') and 'ui' not in anc.parents[k])
assert not outside, ('i18n: screens outside #ui are never translated by applyLang() '
                      '(they stay Arabic in English mode): %r' % outside)

residue = re.search('أونلاين|بلوتوث|اللعب مع الأصدقاء|إنشاء غرفة|رمز الغرفة|ابدأ السباق|بانتظار المضيف|[Pp]eer[JM]', html)
assert not residue, 'i18n: multiplayer residue found: %r' % residue.group(0)

print('i18n: dict complete (%d keys), all screens inside #ui, no mixing, no multiplayer residue' % len(keys))
