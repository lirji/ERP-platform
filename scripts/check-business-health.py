#!/usr/bin/env python3
"""读取现有 Actuator/Micrometer JSON 并按仓库阈值返回状态；不需要另起监控中间件。"""
import argparse
import json
import math
import sys
import urllib.parse
import urllib.request

parser = argparse.ArgumentParser()
parser.add_argument('--url', default='http://127.0.0.1:8500')
parser.add_argument('--outbox-age', type=float, default=30)
parser.add_argument('--scan-age', type=float, default=3600)
args = parser.parse_args()
if args.outbox_age <= 0 or args.scan_age <= 0:
    parser.error('阈值必须为正')


def metric(name, domain=None):
    url = args.url.rstrip('/') + '/actuator/metrics/' + name
    if domain:
        url += '?' + urllib.parse.urlencode({'tag': 'domain:' + domain})
    with urllib.request.urlopen(url, timeout=5) as response:
        payload = json.load(response)
    value = payload['measurements'][0]['value']
    return float(value) if value is not None else math.nan


findings = []
try:
    for name, ceiling in [('erp.outbox.dead', 0), ('erp.outbox.oldest.seconds', args.outbox_age),
                          ('erp.outbox.sample.age.seconds', 30)]:
        value = metric(name)
        if not math.isfinite(value) or value > ceiling:
            findings.append(f'{name}={value}, threshold={ceiling}')
    for domain in ('inventory', 'ar', 'ap'):
        for name, ceiling in [('erp.integrity.mismatches', 0), ('erp.integrity.completed.age.seconds', args.scan_age)]:
            value = metric(name, domain)
            if not math.isfinite(value) or value > ceiling:
                findings.append(f'{name}[{domain}]={value}, threshold={ceiling}')
except (OSError, ValueError, KeyError, IndexError) as error:
    findings.append(f'指标不可读取: {type(error).__name__}')
if findings:
    print(json.dumps({'status': 'ALERT', 'findings': findings,
                      'runbook': 'docs/operations/OBSERVABILITY.md'}, ensure_ascii=False))
    sys.exit(1)
print(json.dumps({'status': 'OK', 'runbook': 'docs/operations/OBSERVABILITY.md'}))
