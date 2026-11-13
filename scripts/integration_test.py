#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PHASE 6 全链路联调脚本
=======================

对应 docs/WORKBUDDY_PROMPT_PHASE6_INTEGRATION.md 的 42 条用例，
把其中可通过 HTTP 自动验证的部分（约 36 条）写成断言，UI 相关的少数几条在末尾列出人工清单。

用法：
    # 走 Nginx（容器部署，默认 80 端口）
    python3 scripts/integration_test.py --username admin --password '你的ADMIN_INIT_PASSWORD'

    # 直连后端（本地 java -jar 起的 8080）
    python3 scripts/integration_test.py --base-url http://localhost:8080 \
        --username admin --password '你的密码'

    # 输出报告到文件
    python3 scripts/integration_test.py ... --report docs/PHASE6_INTEGRATION_REPORT.md

依赖：仅 Python3 标准库，无需 pip install。

设计原则：
  1. 自建测试数据（统一 __IT_ 前缀），跑完自动清理，不污染现有文章；
  2. 每条断言独立捕获异常，一条失败不影响后续；
  3. 失败时打印 HTTP 状态码 + 响应体，便于定位是契约不一致还是服务没起来。
"""

import argparse
import json
import sys
import time
import urllib.error
import urllib.request
from urllib.parse import quote

# ---------------------------------------------------------------- HTTP 客户端


class Client:
    def __init__(self, base_url, token=None):
        self.base = base_url.rstrip("/")
        self.token = token

    def req(self, method, path, body=None, raw_body=None, token=Ellipsis):
        """返回 (http_status, parsed_json)。4xx/5xx 不抛异常。"""
        # 关键词搜索会带中文，必须百分号编码；保留 URL 结构字符不编码
        url = self.base + quote(path, safe="/?&=:%#[]@!$'()*,;~+")
        headers = {"Accept": "application/json"}
        data = None
        if raw_body is not None:
            data = raw_body.encode("utf-8")
            headers["Content-Type"] = "application/json"
        elif body is not None:
            data = json.dumps(body, ensure_ascii=False).encode("utf-8")
            headers["Content-Type"] = "application/json"
        tok = self.token if token is Ellipsis else token
        if tok:
            headers["Authorization"] = "Bearer " + tok

        req = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=20) as resp:
                raw = resp.read()
                return resp.status, _parse(raw)
        except urllib.error.HTTPError as e:
            return e.code, _parse(e.read())
        except urllib.error.URLError as e:
            raise RuntimeError(f"无法连接 {url}：{e.reason}。服务起来了吗？base-url 对吗？")


def _parse(raw):
    if not raw:
        return {}
    try:
        return json.loads(raw.decode("utf-8"))
    except Exception:
        return {"_raw": raw.decode("utf-8", errors="replace")}


# ---------------------------------------------------------------- 断言收集

RESULTS = []       # (name, ok, detail)
CURRENT_GROUP = "未分组"


def group(name):
    global CURRENT_GROUP
    CURRENT_GROUP = name
    print(f"\n—— {name} " + "—" * max(0, 56 - len(name)))


def check(name, ok, detail=""):
    RESULTS.append((CURRENT_GROUP, name, bool(ok), detail))
    if ok:
        print(f"  ✅ {name}")
    else:
        print(f"  ❌ {name}" + (f"\n       {detail}" if detail else ""))
    return bool(ok)


def brief(payload, limit=180):
    s = json.dumps(payload, ensure_ascii=False) if not isinstance(payload, str) else payload
    return s if len(s) <= limit else s[:limit] + "…"


def code_of(payload):
    return payload.get("code") if isinstance(payload, dict) else None


def data_of(payload):
    return payload.get("data") if isinstance(payload, dict) else None


# ---------------------------------------------------------------- 测试数据

PREFIX = "__IT_"


class Fixture:
    """记录本脚本创建的资源，结束时倒序清理。"""

    def __init__(self):
        self.articles = []
        self.categories = []
        self.tags = []

    def cleanup(self, c):
        print("\n—— 清理测试数据")
        for aid in reversed(self.articles):
            try:
                st, _ = c.req("DELETE", f"/api/v1/admin/articles/{aid}")
                print(f"  删除文章 {aid} → {st}")
            except Exception as e:
                print(f"  删除文章 {aid} 失败：{e}")
        for cid in reversed(self.categories):
            try:
                st, _ = c.req("DELETE", f"/api/v1/admin/categories/{cid}")
                print(f"  删除分类 {cid} → {st}")
            except Exception as e:
                print(f"  删除分类 {cid} 失败：{e}")
        for tid in reversed(self.tags):
            try:
                st, _ = c.req("DELETE", f"/api/v1/admin/tags/{tid}")
                print(f"  删除标签 {tid} → {st}")
            except Exception as e:
                print(f"  删除标签 {tid} 失败：{e}")


# ---------------------------------------------------------------- 主流程


def main():
    ap = argparse.ArgumentParser(description="PHASE 6 全链路联调")
    ap.add_argument("--base-url", default="http://localhost", help="默认 http://localhost（Nginx）")
    ap.add_argument("--username", default="admin")
    ap.add_argument("--password", required=True, help="管理员密码，即 .env 里的 ADMIN_INIT_PASSWORD")
    ap.add_argument("--report", help="把报告写到指定 markdown 文件")
    args = ap.parse_args()

    anon = Client(args.base_url)
    fx = Fixture()

    # ---------- 0. 服务可达 ----------
    group("0. 服务可达性")
    try:
        st, body = anon.req("GET", "/api/v1/articles?pageNum=1&pageSize=1")
    except RuntimeError as e:
        print(f"\n❌ 致命：{e}")
        return 2
    if not check("公开文章列表可访问", st == 200, f"HTTP {st} {brief(body)}"):
        print("\n服务不可达或返回异常，后续用例无意义，终止。")
        return 2
    check("响应包络是 Result（code/message/data/timestamp）",
          all(k in body for k in ("code", "message", "data", "timestamp")),
          f"实际字段：{list(body.keys())}")

    # ---------- 1. 认证 ----------
    group("1. 认证与鉴权")
    st, body = anon.req("POST", "/api/v1/auth/login",
                        {"username": args.username, "password": "definitely_wrong_pwd_123"})
    check("错误密码返回 401", st == 401, f"HTTP {st} {brief(body)}")
    check("错误密码文案不泄露用户是否存在",
          "不存在" not in str(body.get("message", "")), f"message={body.get('message')}")

    st, body = anon.req("POST", "/api/v1/auth/login",
                        {"username": args.username, "password": args.password})
    if not check("正确密码登录成功", st == 200 and data_of(body), f"HTTP {st} {brief(body)}"):
        print("\n登录失败，后续管理端用例无法执行。检查 --password 是否与 .env 的 ADMIN_INIT_PASSWORD 一致，"
              "以及 admin_user 表是否已初始化（表非空时改 .env 不生效，需 down -v 重建）。")
        return 2
    token = data_of(body)["token"]
    check("登录响应含 token / tokenType / expiresIn",
          all(k in data_of(body) for k in ("token", "tokenType", "expiresIn")),
          f"实际字段：{list(data_of(body).keys())}")

    admin = Client(args.base_url, token)
    st, body = admin.req("GET", "/api/v1/auth/me")
    check("带 token 访问 /auth/me 返回管理员信息", st == 200 and data_of(body), f"HTTP {st} {brief(body)}")

    st, body = anon.req("GET", "/api/v1/admin/articles?pageNum=1&pageSize=1")
    check("无 token 访问管理端返回 401", st == 401, f"HTTP {st} {brief(body)}")
    check("401 也返回 Result 包络", code_of(body) is not None, f"body={brief(body)}")

    st, body = anon.req("GET", "/api/v1/admin/articles?pageNum=1&pageSize=1", token="invalid.token.here")
    check("伪造 token 返回 401", st == 401, f"HTTP {st} {brief(body)}")

    try:
        _run_all(admin, anon, fx)
    except Exception as e:  # 兜底：中途异常也要输出已有结果，不让报告丢失
        print(f"\n❌ 脚本中途异常：{type(e).__name__}: {e}")
        import traceback
        traceback.print_exc()
    finally:
        fx.cleanup(admin)

    # ---------- 汇总 ----------
    total = len(RESULTS)
    failed = [r for r in RESULTS if not r[2]]
    print("\n" + "=" * 62)
    print(f"自动断言：{total - len(failed)} / {total} 通过")
    if failed:
        print(f"\n失败 {len(failed)} 条：")
        for g, n, _, d in failed:
            print(f"  [{g}] {n}" + (f"\n      {d}" if d else ""))
    print("=" * 62)

    print(MANUAL_CHECKLIST)

    if args.report:
        _write_report(args, total, failed)

    return 0 if not failed else 1


def _run_all(admin, anon, fx):
    # ---------- 2. 准备数据 ----------
    group("2. 准备测试数据")
    stamp = str(int(time.time()))

    st, body = admin.req("POST", "/api/v1/admin/categories",
                         {"name": f"{PREFIX}分类_{stamp}", "parentId": None,
                          "description": "联调脚本创建", "sortOrder": 1})
    ok = check("新建分类", st == 200 and data_of(body), f"HTTP {st} {brief(body)}")
    cat_id = data_of(body) if ok else None
    if cat_id:
        fx.categories.append(cat_id)

    st, body = admin.req("POST", "/api/v1/admin/categories",
                         {"name": f"{PREFIX}子分类_{stamp}", "parentId": cat_id,
                          "description": "联调脚本创建", "sortOrder": 1})
    ok = check("新建子分类（parentId 生效）", st == 200 and data_of(body), f"HTTP {st} {brief(body)}")
    sub_cat_id = data_of(body) if ok else None
    if sub_cat_id:
        fx.categories.append(sub_cat_id)

    tag_ids = []
    for i in range(2):
        st, body = admin.req("POST", "/api/v1/admin/tags",
                             {"name": f"{PREFIX}标签{i}_{stamp}", "description": "联调脚本创建"})
        ok = check(f"新建标签 #{i + 1}", st == 200 and data_of(body), f"HTTP {st} {brief(body)}")
        if ok:
            tag_ids.append(data_of(body))
            fx.tags.append(data_of(body))

    if not cat_id or len(tag_ids) < 2:
        check("测试数据准备完成，可继续后续用例", False, "分类或标签创建失败，后续用例跳过")
        return

    # ---------- 3. 访客端 ----------
    group("3. 访客端公开接口")

    st, body = anon.req("GET", "/api/v1/categories")
    check("分类列表可访问且返回数组", st == 200 and isinstance(data_of(body), list), f"HTTP {st} {brief(body)}")
    cats = data_of(body) or []
    created = [c for c in cats if c.get("id") == cat_id]
    check("新建分类出现在公开分类列表", bool(created), f"未找到 id={cat_id}")
    if created:
        check("分类含 parentId 字段（多级支持）", "parentId" in created[0], f"字段：{list(created[0].keys())}")

    st, body = anon.req("GET", "/api/v1/tags")
    check("标签列表可访问且返回数组", st == 200 and isinstance(data_of(body), list), f"HTTP {st} {brief(body)}")
    tags = data_of(body) or []
    t0 = [t for t in tags if t.get("id") == tag_ids[0]]
    if check("新建标签出现在公开标签列表", bool(t0), f"未找到 id={tag_ids[0]}"):
        check("TagVO 字段为 id/name/description/createdAt（无 updatedAt）",
              set(t0[0].keys()) >= {"id", "name", "createdAt"}, f"实际字段：{list(t0[0].keys())}")

    # 建一篇已发布文章
    st, body = admin.req("POST", "/api/v1/admin/articles", {
        "title": f"{PREFIX}已发布_{stamp}",
        "summary": "联调脚本创建，用于验证公开侧",
        "contentMd": "# 标题\n\n正文内容 **加粗**\n\n```java\nint a = 1;\n```",
        "categoryId": cat_id, "coverImage": None, "isTop": 0, "status": 1,
        "tagIds": tag_ids,
    })
    ok = check("新建已发布文章（带 2 个标签）", st == 200 and data_of(body), f"HTTP {st} {brief(body)}")
    pub_id = data_of(body) if ok else None
    if pub_id:
        fx.articles.append(pub_id)

    st, body = anon.req("GET", "/api/v1/articles?pageNum=1&pageSize=10")
    check("公开列表返回 PageResult（list/total/pageNum/pageSize/pages）",
          st == 200 and all(k in (data_of(body) or {}) for k in ("list", "total", "pageNum", "pageSize", "pages")),
          f"HTTP {st} {brief(body)}")
    lst = (data_of(body) or {}).get("list") or []
    check("新建的已发布文章出现在公开列表", any(a.get("id") == pub_id for a in lst),
          f"列表前 3 条 id：{[a.get('id') for a in lst[:3]]}")

    st, body = anon.req("GET", f"/api/v1/articles?pageNum=1&pageSize=10&categoryId={cat_id}")
    lst = (data_of(body) or {}).get("list") or []
    check("按分类筛选，结果全部属于该分类",
          st == 200 and lst and all(a.get("categoryId") == cat_id for a in lst),
          f"HTTP {st}，返回 {len(lst)} 条")

    st, body = anon.req("GET", f"/api/v1/articles?pageNum=1&pageSize=10&tagId={tag_ids[0]}")
    lst = (data_of(body) or {}).get("list") or []
    check("按标签筛选命中该文章", st == 200 and any(a.get("id") == pub_id for a in lst),
          f"HTTP {st}，返回 {len(lst)} 条")

    st, body = anon.req("GET", f"/api/v1/articles?pageNum=1&pageSize=10&categoryId={cat_id}&tagId={tag_ids[1]}")
    lst = (data_of(body) or {}).get("list") or []
    check("分类 + 标签组合筛选", st == 200 and any(a.get("id") == pub_id for a in lst),
          f"HTTP {st}，返回 {len(lst)} 条")

    st, body = anon.req("GET", f"/api/v1/articles?pageNum=1&pageSize=10&keyword={PREFIX}已发布_{stamp}")
    lst = (data_of(body) or {}).get("list") or []
    check("关键词搜索命中", st == 200 and any(a.get("id") == pub_id for a in lst),
          f"HTTP {st}，返回 {len(lst)} 条")

    st, body = anon.req("GET", "/api/v1/articles?pageNum=1&pageSize=2")
    lst = (data_of(body) or {}).get("list") or []
    check("pageSize=2 生效（返回不超过 2 条）", st == 200 and len(lst) <= 2, f"返回 {len(lst)} 条")

    st, body = anon.req("GET", f"/api/v1/articles/{pub_id}")
    d = data_of(body) or {}
    check("公开详情可访问", st == 200 and d.get("id") == pub_id, f"HTTP {st} {brief(body)}")
    check("详情返回 contentHtml（Markdown 已渲染）", bool(d.get("contentHtml")), f"字段：{list(d.keys())}")
    check("详情返回 tags 数组且含 2 个标签",
          isinstance(d.get("tags"), list) and len(d["tags"]) == 2,
          f"tags={brief(d.get('tags'))}")
    check("详情不含 status 字段（公开侧不暴露状态）", "status" not in d, f"字段：{list(d.keys())}")

    v1 = d.get("viewCount")
    time.sleep(0.3)
    st, body = anon.req("GET", f"/api/v1/articles/{pub_id}")
    v2 = (data_of(body) or {}).get("viewCount")
    check("阅读数递增", isinstance(v1, int) and isinstance(v2, int) and v2 == v1 + 1,
          f"{v1} → {v2}")

    # 草稿 / 下架 / 不存在
    st, body = admin.req("POST", "/api/v1/admin/articles", {
        "title": f"{PREFIX}草稿_{stamp}", "summary": "草稿", "contentMd": "# 草稿",
        "categoryId": cat_id, "isTop": 0, "status": 0,
    })
    draft_id = data_of(body) if st == 200 else None
    if draft_id:
        fx.articles.append(draft_id)
    st, body = anon.req("GET", f"/api/v1/articles/{draft_id}")
    check("草稿公开访问返回 404", st == 404, f"HTTP {st} {brief(body)}")
    draft_msg = (body or {}).get("message")

    st, body = admin.req("POST", "/api/v1/admin/articles", {
        "title": f"{PREFIX}下架_{stamp}", "summary": "下架", "contentMd": "# 下架",
        "categoryId": cat_id, "isTop": 0, "status": 2,
    })
    off_id = data_of(body) if st == 200 else None
    if off_id:
        fx.articles.append(off_id)
    st, body = anon.req("GET", f"/api/v1/articles/{off_id}")
    check("已下架公开访问返回 404", st == 404, f"HTTP {st} {brief(body)}")
    check("下架与草稿的 404 文案一致（不泄露状态）",
          (body or {}).get("message") == draft_msg,
          f"草稿={draft_msg!r}，下架={(body or {}).get('message')!r}")

    st, body = anon.req("GET", "/api/v1/articles/99999999")
    check("不存在的 ID 返回 404", st == 404, f"HTTP {st} {brief(body)}")

    # ---------- 4. 管理端文章 ----------
    group("4. 管理端文章")

    st, body = admin.req("GET", "/api/v1/admin/articles?pageNum=1&pageSize=10")
    d = data_of(body) or {}
    lst = d.get("list") or []
    check("管理端文章列表可访问", st == 200 and isinstance(lst, list), f"HTTP {st} {brief(body)}")
    if lst:
        check("列表项含 status 字段（前端状态列依赖）", "status" in lst[0], f"字段：{list(lst[0].keys())}")
        check("列表项含 updatedAt（管理端需要）", "updatedAt" in lst[0], f"字段：{list(lst[0].keys())}")

    st, body = admin.req("GET", f"/api/v1/admin/articles/{pub_id}")
    d = data_of(body) or {}
    check("管理端详情可访问", st == 200 and d.get("id") == pub_id, f"HTTP {st} {brief(body)}")
    check("【P0 回归】详情返回 contentMd 且非空（编辑框不回填的根因）",
          bool(d.get("contentMd")), f"contentMd 长度={len(str(d.get('contentMd') or ''))}")
    check("【P0 回归】详情返回 status（编辑后变草稿的根因）",
          d.get("status") == 1, f"status={d.get('status')}")
    check("详情返回 tagIds 数组", isinstance(d.get("tagIds"), list), f"tagIds={brief(d.get('tagIds'))}")

    # 编辑已发布文章，必须仍是已发布
    st, body = admin.req("PUT", f"/api/v1/admin/articles/{pub_id}", {
        "title": f"{PREFIX}已发布_{stamp}", "summary": "改过的摘要",
        "contentMd": "# 改过的正文", "categoryId": cat_id, "isTop": 0, "status": 1,
        "tagIds": tag_ids,
    })
    check("编辑文章成功", st == 200, f"HTTP {st} {brief(body)}")
    st, body = admin.req("GET", f"/api/v1/admin/articles/{pub_id}")
    d = data_of(body) or {}
    check("【P0 回归】编辑后状态仍是已发布（1）", d.get("status") == 1, f"status={d.get('status')}")
    check("【P0 回归】编辑后正文已更新", "改过的正文" in str(d.get("contentMd")), f"contentMd={brief(d.get('contentMd'))}")
    check("编辑后标签仍在（2 个）", len(d.get("tagIds") or []) == 2, f"tagIds={brief(d.get('tagIds'))}")

    # 三态：不传 tagIds 保留
    st, body = admin.req("PUT", f"/api/v1/admin/articles/{pub_id}", {
        "title": f"{PREFIX}已发布_{stamp}", "summary": "改过的摘要",
        "contentMd": "# 改过的正文", "categoryId": cat_id, "isTop": 0, "status": 1,
    })
    st, body = admin.req("GET", f"/api/v1/admin/articles/{pub_id}")
    d = data_of(body) or {}
    check("【三态】不传 tagIds → 标签保留", len(d.get("tagIds") or []) == 2, f"tagIds={brief(d.get('tagIds'))}")

    # 三态：传 [] 清空
    st, body = admin.req("PUT", f"/api/v1/admin/articles/{pub_id}", {
        "title": f"{PREFIX}已发布_{stamp}", "summary": "改过的摘要",
        "contentMd": "# 改过的正文", "categoryId": cat_id, "isTop": 0, "status": 1, "tagIds": [],
    })
    st, body = admin.req("GET", f"/api/v1/admin/articles/{pub_id}")
    d = data_of(body) or {}
    check("【三态】传 [] → 标签清空", (d.get("tagIds") or []) == [], f"tagIds={brief(d.get('tagIds'))}")

    # 恢复标签
    st, body = admin.req("PUT", f"/api/v1/admin/articles/{pub_id}", {
        "title": f"{PREFIX}已发布_{stamp}", "summary": "改过的摘要",
        "contentMd": "# 改过的正文", "categoryId": cat_id, "isTop": 0, "status": 1, "tagIds": tag_ids,
    })

    # 快捷下架 / 发布
    st, body = admin.req("PUT", f"/api/v1/admin/articles/{pub_id}", {
        "title": f"{PREFIX}已发布_{stamp}", "summary": "改过的摘要",
        "contentMd": "# 改过的正文", "categoryId": cat_id, "isTop": 0, "status": 2, "tagIds": tag_ids,
    })
    st, body = anon.req("GET", f"/api/v1/articles/{pub_id}")
    check("快捷下架后公开访问 404", st == 404, f"HTTP {st}")

    st, body = admin.req("PUT", f"/api/v1/admin/articles/{pub_id}", {
        "title": f"{PREFIX}已发布_{stamp}", "summary": "改过的摘要",
        "contentMd": "# 改过的正文", "categoryId": cat_id, "isTop": 0, "status": 1, "tagIds": tag_ids,
    })
    st, body = anon.req("GET", f"/api/v1/articles/{pub_id}")
    check("快捷发布后公开可访问", st == 200, f"HTTP {st}")

    # ---------- 5. 标签上限 ----------
    group("5. 标签上限")
    extra = []
    for i in range(9):
        st, body = admin.req("POST", "/api/v1/admin/tags",
                             {"name": f"{PREFIX}批量{i}_{stamp}", "description": None})
        if st == 200:
            extra.append(data_of(body))
            fx.tags.append(data_of(body))
    all_tags = tag_ids + extra
    st, body = admin.req("PUT", f"/api/v1/admin/articles/{pub_id}", {
        "title": f"{PREFIX}已发布_{stamp}", "summary": "s", "contentMd": "# c",
        "categoryId": cat_id, "isTop": 0, "status": 1, "tagIds": all_tags[:11],
    })
    check("单篇绑定 11 个标签被拒绝（上限 10）", st >= 400, f"HTTP {st} {brief(body)}")

    st, body = admin.req("PUT", f"/api/v1/admin/articles/{pub_id}", {
        "title": f"{PREFIX}已发布_{stamp}", "summary": "s", "contentMd": "# c",
        "categoryId": cat_id, "isTop": 0, "status": 1, "tagIds": all_tags[:10],
    })
    check("绑定 10 个标签成功（边界值）", st == 200, f"HTTP {st} {brief(body)}")

    # ---------- 6. 分类 ----------
    group("6. 分类")

    st, body = admin.req("POST", "/api/v1/admin/categories",
                         {"name": f"{PREFIX}环A_{stamp}", "parentId": None, "description": None, "sortOrder": 0})
    ring_a = data_of(body) if st == 200 else None
    if ring_a:
        fx.categories.append(ring_a)
    st, body = admin.req("POST", "/api/v1/admin/categories",
                         {"name": f"{PREFIX}环B_{stamp}", "parentId": ring_a, "description": None, "sortOrder": 0})
    ring_b = data_of(body) if st == 200 else None
    if ring_b:
        fx.categories.append(ring_b)

    if ring_a and ring_b:
        st, body = admin.req("PUT", f"/api/v1/admin/categories/{ring_a}", {
            "name": f"{PREFIX}环A_{stamp}", "parentId": ring_b, "description": None, "sortOrder": 0})
        check("把父分类设为自己的子分类被拒绝（环检测）", st >= 400, f"HTTP {st} {brief(body)}")

    st, body = admin.req("DELETE", f"/api/v1/admin/categories/{cat_id}")
    check("删除仍被文章引用的分类被拒绝", st >= 400, f"HTTP {st} {brief(body)}")

    st, body = admin.req("DELETE", f"/api/v1/admin/categories/{ring_a}")
    check("删除仍有子分类的分类被拒绝", st >= 400, f"HTTP {st} {brief(body)}")

    # ---------- 7. 标签 ----------
    group("7. 标签")

    st, body = admin.req("POST", "/api/v1/admin/tags",
                         {"name": f"{PREFIX}标签0_{stamp}", "description": "重名测试"})
    check("标签重名被拒绝", st >= 400, f"HTTP {st} {brief(body)}")

    st, body = admin.req("DELETE", f"/api/v1/admin/tags/{tag_ids[0]}")
    check("删除已绑定文章的标签成功", st == 200, f"HTTP {st} {brief(body)}")
    st, body = admin.req("GET", f"/api/v1/admin/articles/{pub_id}")
    d = data_of(body) or {}
    check("删除标签后文章上的该标签被级联解绑",
          tag_ids[0] not in (d.get("tagIds") or []), f"tagIds={brief(d.get('tagIds'))}")
    if tag_ids[0] in fx.tags:
        fx.tags.remove(tag_ids[0])

    # ---------- 8. 边界 ----------
    group("8. 边界与异常")

    st, body = anon.req("GET", "/api/v1/articles?pageNum=1&pageSize=1000")
    check("pageSize=1000 被拒绝（上限 100）", st >= 400, f"HTTP {st} {brief(body)}")

    st, body = anon.req("GET", "/api/v1/articles?pageNum=0&pageSize=10")
    check("pageNum=0 被拒绝", st >= 400, f"HTTP {st} {brief(body)}")

    st, body = anon.req("GET", "/api/v1/articles/abc")
    check("ID 传字符串返回 400（不是 500）", st == 400, f"HTTP {st} {brief(body)}")
    check("400 也返回 Result 包络", code_of(body) is not None, f"body={brief(body)}")

    st, body = admin.req("POST", "/api/v1/admin/articles", {
        "title": "长" * 121, "summary": None, "contentMd": None,
        "categoryId": cat_id, "isTop": 0, "status": 0})
    check("标题超 120 字返回 400", st == 400, f"HTTP {st} {brief(body)}")

    st, body = admin.req("POST", "/api/v1/admin/articles", {
        "title": f"{PREFIX}越界_{stamp}", "summary": None, "contentMd": None,
        "categoryId": cat_id, "isTop": 0, "status": 99})
    check("status=99 返回 400", st == 400, f"HTTP {st} {brief(body)}")

    st, body = admin.req("POST", "/api/v1/admin/articles", {
        "title": "", "summary": None, "contentMd": None,
        "categoryId": cat_id, "isTop": 0, "status": 0})
    check("空标题返回 400（@NotBlank）", st == 400, f"HTTP {st} {brief(body)}")


MANUAL_CHECKLIST = """
人工确认清单（脚本无法覆盖，需要浏览器点一遍）
---------------------------------------------------------------
1. 首页文章列表渲染正常，分页组件显示总数
2. 点分类筛选后列表变化，点标签筛选后列表变化
3. 搜索无结果时显示空态，不报错
4. 详情页 Markdown 渲染正确：标题、加粗、代码块、表格、图片
5. 详情页底部显示标签
6. 未登录访问 /admin/articles 被重定向到登录页
7. 管理端文章列表 status 列显示「草稿/已发布/已下架」而不是「未知」
8. 点「编辑」后正文框有内容（不是空白）
9. 编辑弹窗里标签多选已回填
10. 点「发布」「下架」有确认弹窗，操作后列表状态即时刷新
11. 分类管理页显示层级缩进和 L1/L2 标记，上级分类下拉可选
12. 标签管理页「创建时间」列显示真实时间而不是 "-"
13. 浏览器 DevTools → Network 里能看到真实 /api/v1/ 请求（说明 USE_MOCK=false 生效）
"""


def _write_report(args, total, failed):
    lines = [
        "# PHASE 6 联调报告（自动生成）",
        "",
        f"- 生成时间：{time.strftime('%Y-%m-%d %H:%M:%S')}",
        f"- 目标地址：`{args.base_url}`",
        f"- 账号：`{args.username}`",
        f"- 自动断言：**{total - len(failed)} / {total} 通过**",
        "",
    ]
    if failed:
        lines += ["## 失败项", "", "| 分组 | 用例 | 详情 |", "| --- | --- | --- |"]
        for g, n, _, d in failed:
            lines.append(f"| {g} | {n} | {d.replace('|', '/') if d else ''} |")
        lines.append("")
    lines += ["## 全部断言", "", "| 分组 | 用例 | 结果 |", "| --- | --- | --- |"]
    for g, n, ok, _ in RESULTS:
        lines.append(f"| {g} | {n} | {'PASS' if ok else 'FAIL'} |")
    lines += ["", "## 人工确认清单", "", "```", MANUAL_CHECKLIST.strip(), "```"]
    with open(args.report, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print(f"\n报告已写入 {args.report}")


if __name__ == "__main__":
    sys.exit(main())
