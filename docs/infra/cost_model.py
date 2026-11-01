"""ex-day の月間PVごとの費用と広告収入の見積もり（#81）。

使い方：python3 docs/infra/cost_model.py
前提と単価は、このファイルの上の定数にまとめている。単価が変わったら定数を直し、
出力を docs/infra/cost-model.md の表へ写す。単価の出典と確かめた日は cost-model.md に書く。
"""
import math

YEN = 160          # 1ドルの円
TAX = 1.10         # AWS・認証サービスの請求に消費税10%
H = 730            # 1か月の時間

# ---- アクセスの前提（仮置き。実測で直す） ----
POST_RATE = 0.01        # 1PVあたりの投稿数（投稿1件に閲覧100回）
MAU_RATE = 0.005        # 1PVあたりの、ログインした月間ユーザー（MAU）。Web だけの場合
ORIGIN_REQ_PER_PV = 4   # サーバーまで届く要求（SSR の HTML 1＋API 3）
CDN_REQ_PER_PV = 12     # CloudFront が受ける要求
CDN_MB_PER_PV = 0.8     # CloudFront から出る量（MB。写真が大半）
PEAK = 4                # 混む時間の倍率（平均に対して）
REQ_PER_VCPU = 20       # 1 vCPU が余裕を持ってさばく要求／秒（未測定）
LOG_KB_PER_ORIGIN_REQ = 2

# ---- 単価（東京リージョン、ドル）。出典は cost-model.md ----
FG_VCPU, FG_GB = 0.04045, 0.00442   # Fargate Linux/ARM の vCPU 時間・GB 時間
IPV4 = 0.005 * H                    # パブリック IPv4 アドレス1つ
ALB = 0.0243 * H                    # ALB の時間料金（未確認）
ALB_LCU = 0.008 * H                 # ALB の LCU 1つ分（未確認）
RDS = {"db.t4g.small": 37, "db.t4g.medium": 74,
       "db.t4g.large": 148, "db.t4g.xlarge": 296}  # RDS for PostgreSQL 月額（large 以上は倍々の見込み）
GP3 = 0.138                         # RDS gp3 の GB 月（未確認）
LOGS = 0.76                         # CloudWatch Logs 取り込みの GB（未確認）
# その他の部品（未確認の単価を含む。cost-model.md の単価の表）
R53_ZONE, R53_PER_M_QUERY = 0.50, 0.40      # Route 53 のホストゾーン、100万問い合わせ
SECRET = 0.40                               # Secrets Manager 1つ（DB のパスワード）。ほかは Parameter Store（無料）
ECR_GB, ECR_STORED_GB = 0.10, 3             # ECR の GB 月、置いておくイメージの量
S3_GB = 0.025                               # S3 標準の GB 月（写真）
PHOTO_MB_PER_POST = 0.75                    # 投稿の半分に写真、変換後を含め 1.5MB
SES_PER_1000 = 0.10                         # SES 1,000通
EMAILS_PER_MAU = 0.5                        # メールのコードを使う割合（パスキーの人は送らない）
ALARM, ALARMS = 0.10, 10                    # CloudWatch のアラーム1つ、数
LOG_STORE_GB = 0.033                        # CloudWatch Logs の保存の GB 月（30日で消す）
JOB_MIN_PER_DAY_BASE = 10                   # AI の解析などの定期タスク（1 vCPU・2GB）の1日の分数（固定分）
JOB_MIN_PER_POST = 0.01                     # 投稿1件あたりに増える分数
DOMAIN = 2                                  # ドメイン（.jp を年 約3,000〜4,000円で見込む）
AI_YEN_PER_POST = 0.5               # Haiku の標準経路 0.25円（#127）＋門番・抜き取り・投稿時の確認で2倍


def cloudfront(pv):
    """CloudFront の月額。定額プランと従量のうち安いほう"""
    req = pv * CDN_REQ_PER_PV
    gb = pv * CDN_MB_PER_PV / 1000
    flat = None
    for price, r, d in [(0, 1e6, 100), (15, 10e6, 50e3), (200, 125e6, 50e3), (1000, 500e6, 50e3),
                        (1450, 750e6, 75e3), (2250, 1.25e9, 125e3)]:
        if req <= r and gb <= d:
            flat = price
            break
    # 従量（日本、未確認）：毎月 1TB と 1,000万要求が無料、$0.114/GB、$0.012/1万要求
    payg = max(0, gb - 1000) * 0.114 + max(0, req - 10e6) / 1e4 * 0.012
    return payg if flat is None else min(flat, payg)


def auth0(mau):
    """Auth0 の月額（B2C）。Free は 25,000 MAU まで。
    30,000 MAU の $2,100 は公式の料金画面で確かめた。40,000・50,000 の値と 50,000 超の
    $0.07/MAU は参考試算（今の公式画面では個別見積もりの扱い）。採用するなら書面の見積もりを取る"""
    if mau <= 25000:
        return 0
    for m, p in [(30000, 2100), (40000, 2800), (50000, 3500)]:
        if mau <= m:
            return p
    return mau * 0.07


def cognito(mau):
    """Cognito Essentials の月額。10,000 MAU まで無料（期限なし）"""
    return max(0, mau - 10000) * 0.015


COGNITO_M2M = 0.00225  # 成功したトークン発行1回（us-east-1 の例。無料枠なし、App Client の数での課金なし）


def cognito_m2m(partners, tokens_per_day):
    """外部の事業者（提供先）が、トークンを有効期限まで使い回してデータを登録する場合の月額"""
    return partners * tokens_per_day * 30 * COGNITO_M2M


def misc_parts(pv, mau_rate=None):
    """その他の部品の月額（ドル、税抜き）の内訳"""
    mau = pv * (MAU_RATE if mau_rate is None else mau_rate)
    posts = pv * POST_RATE
    log_gb = pv * ORIGIN_REQ_PER_PV * LOG_KB_PER_ORIGIN_REQ / 1e6
    job_hours = (JOB_MIN_PER_DAY_BASE + posts / 30 * JOB_MIN_PER_POST) * 30 / 60
    return dict(
        route53=R53_ZONE + pv * 0.05 / 1e6 * R53_PER_M_QUERY,
        secrets=SECRET,
        ecr=ECR_GB * ECR_STORED_GB,
        s3_photos=posts * 12 * PHOTO_MB_PER_POST / 1000 * S3_GB,  # 1年分たまった量で見る
        ses=mau * EMAILS_PER_MAU / 1000 * SES_PER_1000,
        alarms=ALARM * ALARMS,
        log_storage=log_gb * LOG_STORE_GB,
        jobs=job_hours * (1 * FG_VCPU + 2 * FG_GB),
        domain=DOMAIN,
    )


def infra(pv):
    """AI・認証を除く AWS の月額（ドル、税抜き）、構成の説明、内訳"""
    peak_rps = pv / (30 * 86400) * ORIGIN_REQ_PER_PV * PEAK
    vcpu = peak_rps / REQ_PER_VCPU
    if pv <= 1e6:
        tasks = [(0.5, 1), (0.5, 2)]      # 画面（Next.js）と API（Spring Boot）を1つずつ
        db, multi, storage = "db.t4g.small", False, 30
    else:
        n = max(2, math.ceil(vcpu / 2))   # 1 vCPU のタスクを画面・API それぞれ n 個（最低2で冗長）
        tasks = [(1, 2)] * n + [(1, 3)] * n
        if pv <= 3e6:
            db, multi, storage = "db.t4g.medium", True, 50
        elif pv <= 10e6:
            db, multi, storage = "db.t4g.large", True, 100
        else:
            db, multi, storage = "db.t4g.xlarge", True, 200
    k = 2 if multi else 1
    parts = dict(
        fargate=sum(c * FG_VCPU * H + g * FG_GB * H + IPV4 for c, g in tasks),
        # ALB は内部（CloudFront の VPC オリジンから入る）ので、パブリック IPv4 は要らない
        alb=ALB + ALB_LCU * max(1, peak_rps / 25),
        rds=RDS[db] * k + storage * GP3 * k,
        logs=pv * ORIGIN_REQ_PER_PV * LOG_KB_PER_ORIGIN_REQ / 1e6 * LOGS,
        misc=sum(misc_parts(pv).values()),
        cloudfront=cloudfront(pv),
    )
    desc = f"Fargate {len(tasks)}タスク・{db}{'（Multi-AZ）' if multi else ''}"
    return sum(parts.values()), desc, parts


def infra_config(pv, tasks, db, multi, storage, cdn_plan=None):
    """構成を決めて AWS の月額（ドル、税抜き）を出す（シナリオ用）"""
    peak_rps = pv / (30 * 86400) * ORIGIN_REQ_PER_PV * PEAK
    k = 2 if multi else 1
    parts = dict(
        fargate=sum(c * FG_VCPU * H + g * FG_GB * H + IPV4 for c, g in tasks),
        alb=ALB + ALB_LCU * max(1, peak_rps / 25),  # 内部の ALB（パブリック IPv4 なし）
        rds=RDS[db] * k + storage * GP3 * k,
        logs=pv * ORIGIN_REQ_PER_PV * LOG_KB_PER_ORIGIN_REQ / 1e6 * LOGS,
        misc=sum(misc_parts(pv).values()),
        cloudfront=cloudfront(pv) if cdn_plan is None else cdn_plan,
    )
    return sum(parts.values()), parts


SCENARIOS = [
    # 名前, 月間PV, タスク（vCPU, GB）, DB, Multi-AZ, ストレージGB, メモ
    ("1 MVP の最低の稼働", 1e5, [(0.5, 1), (0.5, 2)], "db.t4g.small", False, 30,
     "画面・API 1つずつ。DB は最小（CPU クレジットに注意）"),
    ("2 DB のメモリを増やした比較（実測前の仮置き）", 1e6, [(0.5, 1), (1, 2)], "db.t4g.medium", False, 50,
     "t4g.medium は small と同じ 2 vCPU・基準の CPU 20% で、メモリだけ2倍。CPU が詰まる場合の解決にはならない。"
     "測った結果で t4g.large・xlarge、CPU が一定の M 系、メモリ優先の R 系と比べる"),
    ("3 利用者・運営が増えたあと", 5e6, [(1, 2)] * 2 + [(1, 3)] * 2, "db.t4g.large", True, 100,
     "Multi-AZ、タスク4つ、ログ・AI の実行が増える"),
]

# 短命の検証環境（必要なときだけ CDK で作り、確かめたら消す）
EPHEMERAL_HOURS = 2


def ephemeral_run(with_rds):
    """1回あたりのドル（税抜き）。CloudFront・ALB・Fargate 2タスク（＋一時 RDS）を EPHEMERAL_HOURS 動かす"""
    h = EPHEMERAL_HOURS
    fargate = (0.5 * FG_VCPU + 1 * FG_GB + 0.005) * h + (0.5 * FG_VCPU + 2 * FG_GB + 0.005) * h
    alb = (0.0243 + 0.008) * h  # 内部の ALB（パブリック IPv4 なし）
    rds = RDS["db.t4g.small"] / H * h if with_rds else 0
    return fargate + alb + rds


# RDS の T4g の CPU クレジットの追加料金（Unlimited モード。24時間の平均が基準の CPU を超えた分）
RDS_SURPLUS_PER_VCPU_HOUR = 0.075   # RDS for PostgreSQL。予約の割引の対象外
T4G_BASELINE = 0.20                 # 1 vCPU あたりの基準の CPU（small・medium とも）
T4G_VCPU = 2                        # small・medium とも 2 vCPU


def t4g_surplus(avg_cpu):
    """1か月の平均の CPU 使用率（0〜1）が基準を超えたときの、1か月の追加料金（ドル、税抜き）"""
    return max(0.0, avg_cpu - T4G_BASELINE) * T4G_VCPU * H * RDS_SURPLUS_PER_VCPU_HOUR


def month(pv, rpm, auth="auth0", ai_yen=AI_YEN_PER_POST, mau_rate=MAU_RATE):
    """1か月の費用・収入・収支（円）"""
    inf, desc, parts = infra(pv)
    mau = pv * mau_rate
    au = auth0(mau) if auth == "auth0" else cognito(mau)
    ai = pv * POST_RATE * ai_yen
    cost = (inf + au) * TAX * YEN + ai
    rev = pv / 1000 * rpm
    return dict(pv=pv, mau=mau, infra=inf * TAX * YEN, auth=au * TAX * YEN, ai=ai,
                cost=cost, rev=rev, profit=rev - cost, desc=desc, parts=parts)


def breakeven(rpm, auth="auth0"):
    """収入が費用を初めて上回る月間PV（1%刻み）"""
    pv = 1000.0
    while pv < 1e9:
        if month(pv, rpm, auth)["profit"] >= 0:
            return pv
        pv *= 1.01
    return None


def f(y):
    return f"{y:,.0f}"


if __name__ == "__main__":
    pvs = [1e3, 1e4, 1e5, 1e6, 1e7, 3e7]
    print("## 月間PVごとの費用と収入（円／月、税込み）\n")
    print("| 月間PV（1日あたり） | 構成 | ログインMAU | AWS | 認証 Auth0 | 認証 Cognito | AI 解析 | 費用計（Auth0） | 収入 RPM100 | 収入 RPM200 | 収入 RPM400 |")
    print("|---|---|---|---|---|---|---|---|---|---|---|")
    for pv in pvs:
        a, c = month(pv, 200, "auth0"), month(pv, 200, "cognito")
        print(f"| {f(pv)}（{f(pv/30)}） | {a['desc']} | {f(a['mau'])} | {f(a['infra'])} | {f(a['auth'])} | "
              f"{f(c['auth'])} | {f(a['ai'])} | {f(a['cost'])} | {f(pv/10)} | {f(pv/5)} | {f(pv*0.4)} |")
    print("\n## 収支（円／月）\n")
    print("| 月間PV | RPM100・Auth0 | RPM100・Cognito | RPM200・Auth0 | RPM200・Cognito | RPM400・Auth0 | RPM400・Cognito |")
    print("|---|---|---|---|---|---|---|")
    for pv in pvs:
        row = [f(month(pv, r, au)["profit"]) for r in (100, 200, 400) for au in ("auth0", "cognito")]
        print(f"| {f(pv)} | " + " | ".join(row) + " |")
    print("\n## 損益分岐の月間PV\n")
    for rpm in (100, 200, 400):
        b = breakeven(rpm)
        print(f"- RPM {rpm}円：月 {f(b)} PV（1日 {f(b/30)} PV）")
    print("\n## AWS の内訳（ドル／月、税抜き）\n")
    print("| 月間PV | " + " | ".join(infra(1e3)[2].keys()) + " | 計 |")
    print("|---|" + "---|" * (len(infra(1e3)[2]) + 1))
    for pv in pvs:
        t, _, p = infra(pv)
        print(f"| {f(pv)} | " + " | ".join(f"{v:,.1f}" for v in p.values()) + f" | {t:,.1f} |")
    print("\n## その他の部品の内訳（ドル／月、税抜き）\n")
    keys = list(misc_parts(1e3).keys())
    print("| 月間PV | " + " | ".join(keys) + " | 計 |")
    print("|---|" + "---|" * (len(keys) + 1))
    for pv in pvs:
        mp = misc_parts(pv)
        print(f"| {f(pv)} | " + " | ".join(f"{v:,.2f}" for v in mp.values()) + f" | {sum(mp.values()):,.2f} |")
    print("\n## 構成のシナリオ（円／月、税込み。認証は Cognito、AI は 0.5円／投稿）\n")
    print("| シナリオ | 月間PV | 構成 | AWS | 認証 | AI | 計 | 収入 RPM200 | メモ |")
    print("|---|---|---|---|---|---|---|---|---|")
    for name, pv, tasks, db, multi, st, memo in SCENARIOS:
        t, _ = infra_config(pv, tasks, db, multi, st)
        aws_yen = t * TAX * YEN
        au = cognito(pv * MAU_RATE) * TAX * YEN
        ai = pv * POST_RATE * AI_YEN_PER_POST
        conf = f"Fargate {len(tasks)}タスク・{db}{'（Multi-AZ）' if multi else ''}"
        print(f"| {name} | {f(pv)} | {conf} | {f(aws_yen)} | {f(au)} | {f(ai)} | {f(aws_yen + au + ai)} | {f(pv/5)} | {memo} |")
    print("\n## RDS の CPU クレジットの追加料金（db.t4g.small・medium、円／月、税込み）\n")
    print("| 1か月の平均の CPU 使用率 | 追加料金 |")
    print("|---|---|")
    for c in (0.2, 0.3, 0.5, 0.8):
        print(f"| {c*100:.0f}% | {f(t4g_surplus(c)*TAX*YEN)} |")
    print("\n## 短命の検証環境（1回あたり、円、税込み）\n")
    for with_rds in (False, True):
        y = ephemeral_run(with_rds) * TAX * YEN
        print(f"- {EPHEMERAL_HOURS}時間・{'一時の RDS あり' if with_rds else 'DB はコンテナ'}：約 {y:,.0f}円（月10回で 約 {y*10:,.0f}円）")

    print("\n## 感度\n")
    for ai in (0.5, 4.6):
        for pv in (1e6, 1e7):
            m = month(pv, 200, "auth0", ai)
            print(f"- AI {ai}円／投稿・月 {f(pv)} PV：AI {f(m['ai'])}円、収支 {f(m['profit'])}円")
    for r in (0.002, 0.005, 0.01):
        print(f"- MAU が PV の {r*100:.1f}%：Auth0 の無料（25,000 MAU）が切れるのは月 {f(25000/r)} PV、"
              f"Cognito の無料（10,000 MAU）が切れるのは月 {f(10000/r)} PV")
    print("\n## アプリの利用者（月間）ごとの認証の費用（円／月、税込み）\n")
    print("| アプリの MAU | Auth0 | Cognito |")
    print("|---|---|---|")
    for mau in (10000, 25000, 30000, 50000, 100000, 300000):
        print(f"| {f(mau)} | {f(auth0(mau)*TAX*YEN)} | {f(cognito(mau)*TAX*YEN)} |")
    print("\n## 外部の事業者からのデータ登録（M2M）の認証の費用（Cognito、円／月、税込み）\n")
    print("| 提供先 | トークンの取得 | 月間のトークン発行 | 月額 |")
    print("|---|---|---|---|")
    for p, t, label in [(10, 1, "1日1回"), (100, 1, "1日1回"), (10, 24, "1時間に1回"), (100, 24, "1時間に1回")]:
        print(f"| {p} | {label} | {f(p*t*30)} | {f(cognito_m2m(p, t)*TAX*YEN)} |")
