#!/usr/bin/env python3
"""서버(backend/)의 값 계약을 앱 계약 시험 자료(android/contract/)로 뽑는다. backend/는 읽기만 한다.

  python android/contract/sync_from_server.py

만드는 것
  enums/<Enum>.txt              서버 enum 상수 이름(한 줄에 하나)
  enums/ConsentItem.required.txt  가입 때 필수인 동의 항목
  enums/StandardDistance.txt    "이름 미터 쓰임,쓰임" (RANDOM_BATTLE·SELF_BATTLE·CREW_RUN·RECORD_BASE)
  enums/PersonalityTag.categories.txt  "태그 분류" (분류 번호 1~7)
  enums/ErrorCode.http.txt      ReasonCode가 아닌 서버 오류 응답 code (web.error.ApiExceptionHandler)
  dto-records.json              서버 요청·응답 record의 항목 이름·필수 여부·중첩 형식
  policy/ValidityRule.txt       유효 판정 초과 시속 상한 "maxSpeedKmh 값" (running.ValidityRule.STANDARD, FR-047)
  policy/TierPlacement.txt      배치고사 판 수 "placementRuns 값" (member.PlacementProgress.ROUNDS, 진행 「n/3」의 분모, D-509)
  policy/GatedNotification.txt  "알림종류 미공개기능" (notification.NotificationType의 GatedFeature, 없으면 "-", D138)

앱 쪽 시험(ContractEnumsTest·ServerDtoContractTest·정책 계약 시험)이 이 파일들과 앱 상수·DTO를 대조한다. 서버 값이 바뀌면 이 스크립트를 다시
돌려 변경분을 커밋하고, 앱이 따라가지 못하면 시험이 실패한다.
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
JAVA = os.path.join(ROOT, 'backend', 'src', 'main', 'java', 'com', 'a207', 'runkk')
ENUM_OUT = os.path.join(HERE, 'enums')

ENUMS = {
    'common': ['PersonalityTag', 'CrewTag', 'ReasonCode', 'RecordVisibility', 'StandardDistance', 'ItemSlot', 'Tier',
               'AggregationPeriod', 'DistanceUnit'],
    'member': ['ConsentItem', 'FreeRunTarget', 'GapAlertInterval', 'ProfileVisibility', 'Gender', 'ProviderType',
               'SettingItem', 'DocumentType', 'AccountType', 'ScreenTheme', 'PlacementStatus'],
    'notification': ['NotificationType', 'Screen'],
    'avatar': ['DefaultCharacter', 'BodyShape', 'SkinTone', 'FailureReason'],
    'battle': ['BattleType', 'Verdict', 'PlacementRoundState'],
    'running': ['CardKind', 'RunMode', 'SignalState', 'DisposalKind', 'StartDevice'],
    'runimal': ['RunimalType'],
    'crew': ['CrewVisibility', 'JoinPolicy', 'AttendanceAnswer', 'PostKind', 'CrewRole', 'CrewRunStatus', 'Readiness',
             'JoinRequestStatus', 'PollMethod', 'CrewRunRepeat', 'CrewRunPhase', 'PostOrder'],
    'crew.chat': ['ReactionKind', 'ChatMessageKind'],
    'crew.chat.live': ['ChatRemovalReason'],
    'report': ['ReportTargetKind', 'ReportReason', 'ReportStatus'],
}


def read(path):
    with open(path, encoding='utf-8') as f:
        return f.read()


def strip_comments_and_literals(src, keep_alias=True):
    """주석을 지우고 문자열·문자 리터럴 내용을 '_'로 바꾼다(길이 유지). @JsonAlias 값은 먼저 빼 둔다."""
    aliases = {}
    out = []
    i = 0
    n = len(src)
    while i < n:
        c = src[i]
        if src.startswith('//', i):
            j = src.find('\n', i)
            j = n if j < 0 else j
            out.append(' ' * (j - i))
            i = j
        elif src.startswith('/*', i):
            j = src.find('*/', i + 2)
            j = n if j < 0 else j + 2
            out.append(re.sub(r'[^\n]', ' ', src[i:j]))
            i = j
        elif src.startswith('"""', i):
            j = src.find('"""', i + 3)
            j = n if j < 0 else j + 3
            out.append(re.sub(r'[^\n]', '_', src[i:j]))
            i = j
        elif c == '"':
            j = i + 1
            while j < n and src[j] != '"':
                j += 2 if src[j] == '\\' else 1
            literal = src[i + 1:j]
            out.append('"' + '_' * len(literal) + '"')
            aliases[i] = literal
            i = j + 1
        elif c == "'":
            j = i + 1
            while j < n and src[j] != "'":
                j += 2 if src[j] == '\\' else 1
            out.append("'" + '_' * (j - i - 1) + "'")
            i = j + 1
        else:
            out.append(c)
            i += 1
    return ''.join(out), aliases


def match(text, i, open_c, close_c):
    depth = 0
    for j in range(i, len(text)):
        if text[j] == open_c:
            depth += 1
        elif text[j] == close_c:
            depth -= 1
            if depth == 0:
                return j
    raise ValueError('짝이 없다: %d' % i)


def enum_constants(path):
    clean, _ = strip_comments_and_literals(read(path))
    m = re.search(r'\benum\s+\w+[^{]*\{', clean)
    i = m.end()
    depth = 0
    cur = ''
    names = []
    while i < len(clean):
        c = clean[i]
        if depth == 0 and c in ',;}':
            n = cur.strip()
            if n:
                names.append(re.match(r'\w+', n).group(0))
            cur = ''
            if c in ';}':
                break
        elif c in '({':
            depth += 1
        elif c in ')}':
            depth -= 1
        elif depth == 0:
            cur += c
        i += 1
    return names, clean


def write(path, lines):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        f.write('\n'.join(lines) + '\n')


def sync_enums():
    for pkg, names in ENUMS.items():
        for name in names:
            consts, clean = enum_constants(os.path.join(JAVA, *pkg.split('.'), name + '.java'))
            write(os.path.join(ENUM_OUT, name + '.txt'), consts)
            if name == 'ConsentItem':
                required = [c for c in consts if re.search(r'\b%s\(\s*true' % c, clean)]
                write(os.path.join(ENUM_OUT, 'ConsentItem.required.txt'), required)
            if name == 'StandardDistance':
                rows = []
                for c in consts:
                    m = re.search(r'\b%s\(\s*([\d_]+)\s*(?:,([^)]*))?\)' % c, clean)
                    meters = int(m.group(1).replace('_', ''))
                    uses = re.findall(r'Use\.(\w+)', m.group(2) or '')
                    rows.append('%s %d %s' % (c, meters, ','.join(uses) if uses else '-'))
                write(os.path.join(ENUM_OUT, 'StandardDistance.txt'), rows)
            if name == 'PersonalityTag':
                rows = []
                for c in consts:
                    m = re.search(r'\b%s\(\s*TagCategory\.(\w+)' % c, clean)
                    rows.append('%s %s' % (c, m.group(1)))
                write(os.path.join(ENUM_OUT, 'PersonalityTag.categories.txt'), rows)
    # ApiExceptionHandler가 ReasonCode 밖에서 주는 오류 코드
    handler = read(os.path.join(JAVA, 'web', 'error', 'ApiExceptionHandler.java'))
    codes = sorted(set(re.findall(r'new ErrorResponse\("([A-Z_]+)"', handler)))
    write(os.path.join(ENUM_OUT, 'ErrorCode.http.txt'), codes)


# ---------- record(요청·응답 형식) ----------

DECL = re.compile(r'\b(record|class|interface|enum)\s+(\w+)')
LIST_TYPES = {'List', 'Set', 'Collection', 'Optional', 'Iterable'}
REQUIRED_ANNOTATIONS = {'NotNull', 'NotBlank', 'NotEmpty'}


def split_top(text, sep=','):
    parts = []
    depth = 0
    cur = ''
    for c in text:
        if c in '<({[':
            depth += 1
        elif c in '>)}]':
            depth -= 1
        if c == sep and depth == 0:
            parts.append(cur)
            cur = ''
        else:
            cur += c
    if cur.strip():
        parts.append(cur)
    return parts


def parse_component(text, alias_by_pos, base):
    text = text.strip()
    annotations = []
    pos = 0
    # 앞쪽 주석(애너테이션)을 뗀다.
    while True:
        m = re.match(r'\s*@(\w+)(\s*\()?', text[pos:])
        if not m:
            break
        name = m.group(1)
        end = pos + m.end()
        args = ''
        if m.group(2):
            close = match(text, end - 1, '(', ')')
            args = text[end:close]
            end = close + 1
        annotations.append((name, args, base + pos))
        pos = end
    rest = text[pos:].strip()
    m = re.match(r'(.*\S)\s+(\w+)$', rest, re.S)
    type_text, name = m.group(1), m.group(2)
    type_text = re.sub(r'@\w+(\([^)]*\))?\s*', '', type_text).strip()
    aliases = []
    for a_name, _args, a_pos in annotations:
        if a_name == 'JsonAlias':
            # 원문에서 문자열 값을 찾는다(문자열은 위치로 따로 보관했다).
            for p, literal in alias_by_pos.items():
                if a_pos <= p <= a_pos + len(a_name) + 80:
                    aliases.append(literal)
                    break
    required = any(a in REQUIRED_ANNOTATIONS for a, _, _ in annotations)
    return {'name': name, 'type': type_text, 'required': required, 'aliases': aliases}


def parse_records(path):
    src = read(path)
    clean, literals = strip_comments_and_literals(src)
    stem = os.path.splitext(os.path.basename(path))[0]
    records = {}

    def scan(i, end, trail):
        while i < end:
            m = DECL.search(clean, i, end)
            if not m:
                break
            kind, name = m.group(1), m.group(2)
            j = m.end()
            while clean[j].isspace():
                j += 1
            if clean[j] == '<':
                j = match(clean, j, '<', '>') + 1
            comps = None
            comps_start = 0
            while clean[j].isspace():
                j += 1
            if kind == 'record' and clean[j] == '(':
                close = match(clean, j, '(', ')')
                comps = clean[j + 1:close]
                comps_start = j + 1
                j = close + 1
            b = clean.find('{', j)
            semi = clean.find(';', j)
            if b < 0 or (0 <= semi < b):
                i = j
                continue
            e = match(clean, b, '{', '}')
            qual = trail + [name]
            if comps is not None:
                items = []
                offset = 0
                for part in split_top(comps):
                    comp = parse_component(part, literals, comps_start + offset)
                    items.append(comp)
                    offset += len(part) + 1
                records['.'.join(qual)] = {'components': items}
            scan(b + 1, e, qual)
            i = e + 1

    scan(0, len(clean), [])
    return stem, records


def resolve(type_text, owner, all_records):
    """component 형식 → {kind, ref}. owner는 이 component를 가진 record의 키."""
    t = type_text.strip()
    m = re.match(r'(\w[\w.]*)\s*<(.*)>$', t, re.S)
    base = m.group(1).split('.')[-1] if m else t.split('.')[-1]
    kind = 'leaf'
    inner = t
    if m and base in LIST_TYPES:
        kind = 'list'
        inner = m.group(2).strip()
        inner = re.sub(r'@\w+(\([^)]*\))?\s*', '', inner).strip()
    elif m and base == 'Map':
        return {'kind': 'map'}
    ref = lookup(inner, owner, all_records)
    if ref:
        return {'kind': kind if kind == 'list' else 'record', 'ref': ref}
    return {'kind': 'list-leaf' if kind == 'list' else 'leaf'}


def lookup(simple, owner, all_records):
    simple = simple.split('.')[-1]
    if not re.match(r'^\w+$', simple):
        return None
    parts = owner.split('.')
    stem_parts = parts
    # owner의 바깥쪽부터 차례로: Outer.Inner.Simple, Outer.Simple, Simple
    for cut in range(len(stem_parts), -1, -1):
        key = '.'.join(stem_parts[:cut] + [simple])
        if key in all_records and key != owner:
            return key
    candidates = [k for k in all_records if k.split('.')[-1] == simple]
    return candidates[0] if len(candidates) == 1 else None


def sync_records():
    all_records = {}
    for dirpath, _dirs, files in os.walk(JAVA):
        for f in files:
            if not f.endswith('.java'):
                continue
            stem, recs = parse_records(os.path.join(dirpath, f))
            for k, v in recs.items():
                key = k
                if key in all_records:
                    key = '%s#%s' % (os.path.relpath(os.path.join(dirpath, f), JAVA).replace(os.sep, '/'), k)
                v['file'] = os.path.relpath(os.path.join(dirpath, f), JAVA).replace(os.sep, '/')
                all_records[key] = v
    # 형식 해석
    for key, rec in all_records.items():
        for comp in rec['components']:
            info = resolve(comp['type'], key, all_records)
            comp.update(info)
    # web 요청·응답과 거기서 닿는 record만 남긴다.
    roots = [k for k, v in all_records.items() if v['file'].startswith('web/')]
    roots += ['InboxView', 'InboxView.Item', 'NavigationTarget', 'MemberSettingsView', 'RunSettings', 'BattleSettings',
              'LegalDocument', 'DisposalOrderView', 'WithdrawalResult']
    keep = {}
    stack = [r for r in roots if r in all_records]
    while stack:
        k = stack.pop()
        if k in keep:
            continue
        keep[k] = all_records[k]
        for comp in all_records[k]['components']:
            if comp.get('ref'):
                stack.append(comp['ref'])
    out = {}
    for k in sorted(keep):
        rec = keep[k]
        out[k] = {
            'file': rec['file'],
            'components': [
                {key: c[key] for key in ('name', 'required', 'aliases', 'kind', 'ref') if c.get(key) not in (None, [], False)
                 or key in ('name',)}
                for c in rec['components']
            ],
        }
    with open(os.path.join(HERE, 'dto-records.json'), 'w', encoding='utf-8', newline='\n') as f:
        json.dump(out, f, ensure_ascii=False, indent=1, sort_keys=True)
        f.write('\n')
    return len(out)


# ---------- 정책 고정값(앱이 같은 값을 써야 하는 기준) ----------

def sync_policy():
    """running.ValidityRule.STANDARD의 maxSpeedKmh(마지막 인자)를 적는다. 앱 OverspeedRule.MAX_SEGMENT_SPEED_KMH가 같아야 한다(FR-047)."""
    src, _ = strip_comments_and_literals(read(os.path.join(JAVA, 'running', 'ValidityRule.java')))
    m = re.search(r'STANDARD\s*=\s*new\s+ValidityRule\s*\(', src)
    end = match(src, m.end() - 1, '(', ')')
    args = [a.strip() for a in split_top(src[m.end():end])]
    write(os.path.join(HERE, 'policy', 'ValidityRule.txt'), ['maxSpeedKmh %s' % float(args[-1])])
    # 배치고사 판 수(member.PlacementProgress.ROUNDS). 앱 RandomBattleGate.PLACEMENT_RUNS가 같아야 한다(H-44, D-333, D-509).
    src, _ = strip_comments_and_literals(read(os.path.join(JAVA, 'member', 'PlacementProgress.java')))
    m = re.search(r'\bint\s+ROUNDS\s*=\s*(\d+)\s*;', src)
    write(os.path.join(HERE, 'policy', 'TierPlacement.txt'), ['placementRuns %d' % int(m.group(1))])
    # 닫힌 기능 알림 분류(notification.NotificationType의 GatedFeature 인자). 앱 GatedFeature.of가 같아야 한다(D138, H-47).
    path = os.path.join(JAVA, 'notification', 'NotificationType.java')
    consts, clean = enum_constants(path)
    rows = []
    for c in consts:
        m = re.search(r'\b%s\(\s*Screen\.\w+\s*(?:,\s*GatedFeature\.(\w+)\s*)?\)' % c, clean)
        rows.append('%s %s' % (c, m.group(1) or '-'))
    write(os.path.join(HERE, 'policy', 'GatedNotification.txt'), rows)


if __name__ == '__main__':
    sync_enums()
    sync_policy()
    count = sync_records()
    print('enums + %d records' % count, file=sys.stderr)
