#!/usr/bin/env python3
"""Dependency-free web fetch MCP server for the TaiXu sandbox."""
import html
import json
import re
import sys
import urllib.request

PROTOCOL_VERSION = "2025-06-18"


def response(req_id, result=None, error=None):
    value = {"jsonrpc": "2.0", "id": req_id}
    value["error" if error is not None else "result"] = error if error is not None else result
    return value


def strip_html(raw):
    raw = re.sub(r"(?is)<script.*?</script>|<style.*?</style>|<noscript.*?</noscript>", " ", raw)
    raw = re.sub(r"(?i)</(p|div|h[1-6]|li|br|tr)>", "\n", raw)
    raw = re.sub(r"(?s)<[^>]+>", " ", raw)
    return re.sub(r"[ \t]+", " ", html.unescape(raw)).strip()


def main():
    tools = [{"name": "fetch", "description": "抓取网页并提取正文文本", "inputSchema": {"type": "object", "properties": {"url": {"type": "string"}, "max_length": {"type": "integer"}}, "required": ["url"]}}]
    for line in sys.stdin:
        try:
            req = json.loads(line)
            method, req_id = req.get("method"), req.get("id")
            if method == "initialize":
                out = response(req_id, {"protocolVersion": PROTOCOL_VERSION, "capabilities": {"tools": {}}, "serverInfo": {"name": "taixu-fetch", "version": "1.0.0"}})
            elif method == "notifications/initialized":
                continue
            elif method == "tools/list":
                out = response(req_id, {"tools": tools})
            elif method == "tools/call":
                params = req.get("params") or {}
                try:
                    if params.get("name") != "fetch":
                        raise ValueError("未知工具: " + str(params.get("name")))
                    opts = params.get("arguments") or {}
                    url = str(opts.get("url", "")).strip()
                    if not (url.startswith("http://") or url.startswith("https://")):
                        raise ValueError("只支持 HTTP/HTTPS URL")
                    max_length = max(1, min(int(opts.get("max_length", 5000)), 100000))
                    request = urllib.request.Request(url, headers={"User-Agent": "TaiXu-MCP-Fetch/1.0"})
                    with urllib.request.urlopen(request, timeout=20) as stream:
                        body = stream.read(2 * 1024 * 1024).decode("utf-8", errors="replace")
                    text = strip_html(body)[:max_length]
                    result = {"content": [{"type": "text", "text": text}], "isError": False}
                except Exception as exc:
                    result = {"content": [{"type": "text", "text": str(exc)}], "isError": True}
                out = response(req_id, result)
            else:
                continue
            sys.stdout.write(json.dumps(out, ensure_ascii=False) + "\n")
            sys.stdout.flush()
        except Exception as exc:
            sys.stdout.write(json.dumps(response(None, error={"code": -32603, "message": str(exc)}), ensure_ascii=False) + "\n")
            sys.stdout.flush()


if __name__ == "__main__":
    main()
