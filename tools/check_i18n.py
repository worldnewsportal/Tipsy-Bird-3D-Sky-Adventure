#!/usr/bin/env python3
import re, sys

html = open(sys.argv[1] if len(sys.argv) > 1 else 'www/index.html', encoding='utf-8').read()

keys = set(re.findall(r"'((?:[^'\\]|\\.)*)':", re.search(r"const EN=\{(.*?)\};\n", html, re.S).group(1)))

missT = [t for t in set(re.findall(r"\bT\('([^']+)'", html)) if t not in keys]
assert not missT, 'i18n: T() args missing from dict: %r' % missT

title = re.search(r'<title>([^<]*)</title>', html).group(1)
ns = re.sub(r'<script[\s\S]*?</script>', '', html)
ns = re.sub(r'<style[\s\S]*?</style>', '', ns)
missH = {m.group(1).strip() for m in re.finditer(r'>([^<>]+)<', ns)}
missH = {t for t in missH if re.search(r'[\u0600-\u06FF]', t) and t not in keys and t != title}
assert not missH, 'i18n: static arabic text missing from dict: %r' % missH

residue = re.search('أونلاين|بلوتوث|اللعب مع الأصدقاء|إنشاء غرفة|رمز الغرفة|ابدأ السباق|بانتظار المضيف|[Pp]eer[JM]', html)
assert not residue, 'i18n: multiplayer residue found: %r' % residue.group(0)

print('i18n: dict complete (%d keys), no mixing, no multiplayer residue' % len(keys))
