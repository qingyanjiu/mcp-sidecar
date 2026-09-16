#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""场景通道联调控制台 —— 不起服务，只当客户端。

单元测试证明不了联调，因为测试是自己扮演页面的（随机端口 + 自己回执），
真前端接不进去。真联调时缺的是另一半：**除了 LLM，没有第二条路能下发动作帧**。
这个脚本补的就是这一环 —— 它把「扮演页面」和「扮演 agent」拆成两个终端。

    # 终端 1：扮演场景页面，收到动作自动回执并打印
    python3 scripts/scene-console.py page --session-id dev-1

    # 终端 2：扮演 agent 侧，手动下发动作
    python3 scripts/scene-console.py call --session-id dev-1 scene_switch_floor '{"floor":3}'

    # 看看有哪些工具、参数长什么样
    python3 scripts/scene-console.py list

    # 想让真前端连进来，只需要跑终端 2 那条 call，session-id 填前端 hello 里报的那个
    python3 scripts/scene-console.py call --session-id <前端的 sessionId> scene_reset_view

两个终端的 --session-id 必须一致：它就是工具路由到哪个页面的依据，
在 HTTP 那头是 X-Session-Id 请求头（方案 §2.8）。

依赖：pip install websockets
"""

import argparse
import asyncio
import json
import sys
import urllib.error
import urllib.request

DEFAULT_HOST = "127.0.0.1"
DEFAULT_PORT = 2222
PROTOCOL_VERSION = "2025-06-18"


# ----------------------------------------------------------------------
# MCP streamable HTTP 侧
# ----------------------------------------------------------------------

class McpClient:
    """够用的 MCP 客户端：initialize → initialized → tools/list / tools/call。"""

    def __init__(self, host, port):
        self.endpoint = f"http://{host}:{port}/mcp/scene"
        self.session_id = None

    def _post(self, body, session_header=None):
        request = urllib.request.Request(
            self.endpoint, data=json.dumps(body).encode("utf-8"), method="POST")
        request.add_header("Content-Type", "application/json")
        request.add_header("Accept", "application/json, text/event-stream")
        if session_header:
            request.add_header("X-Session-Id", session_header)
        if self.session_id:
            request.add_header("mcp-session-id", self.session_id)

        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                self.session_id = response.headers.get("mcp-session-id") or self.session_id
                return _jsonrpc_payload(response.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            raise SystemExit(f"MCP 请求失败 HTTP {e.code}: {e.read().decode('utf-8', 'replace')}")
        except urllib.error.URLError as e:
            raise SystemExit(
                f"连不上 {self.endpoint} —— 服务起了吗？（{e.reason}）\n"
                f"  起服务：mvn spring-boot:run")

    def handshake(self):
        self._post({"jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {
            "protocolVersion": PROTOCOL_VERSION,
            "capabilities": {},
            "clientInfo": {"name": "scene-console", "version": "1.0"}}})
        self._post({"jsonrpc": "2.0", "method": "notifications/initialized"})
        return self

    def list_tools(self, session_header=None):
        payload = self._post(
            {"jsonrpc": "2.0", "id": 2, "method": "tools/list", "params": {}}, session_header)
        return payload["result"]["tools"]

    def call_tool(self, tool, arguments, session_header):
        payload = self._post({"jsonrpc": "2.0", "id": 3, "method": "tools/call", "params": {
            "name": tool, "arguments": arguments}}, session_header)

        if "error" in payload:
            raise SystemExit(f"JSON-RPC 错误：{json.dumps(payload['error'], ensure_ascii=False)}")
        result = payload.get("result", {})
        if result.get("isError"):
            raise SystemExit(_text_of(result))
        return _text_of(result)


def _jsonrpc_payload(raw):
    """传输层可能用 application/json 也可能用 SSE 回，两种都认。

    通知类请求（如 notifications/initialized）只有 202 空响应，返回 None。
    """
    text = raw.strip()
    if not text:
        return None
    if text.startswith("{"):
        return json.loads(text)

    payload = None
    for line in text.split("\n"):
        if line.startswith("data:"):
            candidate = json.loads(line[5:].strip())
            if "id" in candidate:
                payload = candidate
    if payload is None:
        raise SystemExit(f"没解析出 JSON-RPC 响应：{raw[:200]}")
    return payload


def _text_of(result):
    return "\n".join(part.get("text", "") for part in result.get("content", []))


# ----------------------------------------------------------------------
# 子命令
# ----------------------------------------------------------------------

def cmd_list(args):
    tools = McpClient(args.host, args.port).handshake().list_tools(args.session_id)
    print(f"{len(tools)} 个工具\n")
    for tool in tools:
        schema = tool.get("inputSchema", {})
        properties = schema.get("properties", {})
        required = set(schema.get("required", []))

        print(f"  {tool['name']}")
        for name, spec in properties.items():
            kind = spec.get("type", "?")
            if "enum" in spec:
                kind = "|".join(spec["enum"])
            flag = "" if name in required else "?"
            print(f"      {name}{flag}: {kind}")
        description = (tool.get("description") or "").strip().split("\n")[0]
        print(f"      ── {description}\n")


def cmd_call(args):
    arguments = json.loads(args.params) if args.params else {}
    client = McpClient(args.host, args.port).handshake()
    print(f"→ {args.tool} {json.dumps(arguments, ensure_ascii=False)}"
          f"  (session={args.session_id})")
    print(f"← {client.call_tool(args.tool, arguments, args.session_id)}")


def cmd_page(args):
    try:
        import websockets
    except ImportError:
        raise SystemExit("缺依赖：pip install websockets")

    url = f"ws://{args.host}:{args.port}/ws/scene"
    asyncio.run(_run_page(args, websockets, url))


async def _run_page(args, websockets, url):
    try:
        # 带 Origin 是为了走浏览器同一条路径 —— 服务端 setAllowedOrigins("*")
        async with websockets.connect(url, additional_headers={"Origin": "http://localhost"}) as ws:
            await ws.send(json.dumps({
                "type": "hello", "sessionId": args.session_id, "userId": args.user_id}))
            print(f"已连接 {url}  sessionId={args.session_id}")

            await ws.send(json.dumps({
                "type": "scene_state", "sessionId": args.session_id, "ready": True,
                "floor": args.floor, "build": args.build}))
            print(f"已上报 scene_state (floor={args.floor}, build={args.build})")
            print("等下发的动作…（Ctrl-C 退出）\n")

            async for raw in ws:
                try:
                    frame = json.loads(raw)
                except json.JSONDecodeError:
                    print(f"[收] 非 JSON：{raw[:200]}")
                    continue

                if frame.get("event") != "action":
                    print(f"[收] {json.dumps(frame, ensure_ascii=False)}")
                    continue

                data = frame["data"]
                print(f"[动作] {data['name']}  {json.dumps(data['params'], ensure_ascii=False)}"
                      f"   actionId={data['actionId']}")

                if args.no_reply:
                    print("       (--no-reply：不回执，工具那边会等到超时)")
                    continue
                if args.delay:
                    await asyncio.sleep(args.delay)

                reply = {
                    "type": "action_result", "sessionId": args.session_id,
                    "actionId": data["actionId"], "state": args.state,
                    "durationMs": int(args.delay * 1000), "error": args.error,
                }
                await ws.send(json.dumps(reply))
                print(f"[回执] state={args.state}"
                      + (f" error={args.error}" if args.error else ""))

    except KeyboardInterrupt:
        print("\n退出")
    except OSError as e:
        raise SystemExit(f"连不上 {url} —— 服务起了吗？（{e}）\n  起服务：mvn spring-boot:run")


# ----------------------------------------------------------------------

def main():
    # 输出重定向到文件/管道时 Python 默认块缓冲，联调时最需要实时看到动作，
    # 所以强制行缓冲 —— 否则 page 那边的日志要等进程退出才刷出来。
    sys.stdout.reconfigure(line_buffering=True)

    # --host/--port/--session-id 挂在子命令上而不是顶层：顶层选项必须写在子命令**前面**，
    # 而 `scene-console.py call --session-id x ...` 才是顺手的写法。
    common = argparse.ArgumentParser(add_help=False)
    common.add_argument("--host", default=DEFAULT_HOST, help=f"默认 {DEFAULT_HOST}")
    common.add_argument("--port", type=int, default=DEFAULT_PORT, help=f"默认 {DEFAULT_PORT}")
    common.add_argument("--session-id", default="dev-1",
                        help="会话标识，两个终端要一致（HTTP 那头是 X-Session-Id），默认 dev-1")

    parser = argparse.ArgumentParser(
        description="场景通道联调控制台",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__.split("依赖：")[0].strip())
    sub = parser.add_subparsers(dest="command", required=True)

    sub.add_parser("list", parents=[common], help="列出所有工具及其参数")

    call = sub.add_parser("call", parents=[common], help="手动下发一个动作")
    call.add_argument("tool", help="工具名，如 scene_switch_floor")
    call.add_argument("params", nargs="?", default=None, help='JSON 参数，如 \'{"floor":3}\'')

    page = sub.add_parser("page", parents=[common], help="扮演场景页面")
    page.add_argument("--state", default="dispatched", choices=["dispatched", "rejected"],
                      help="回执状态，默认 dispatched；选 rejected 配合 --error 测拒绝路径")
    page.add_argument("--error", default=None, help="rejected 时的错误码，如 BAD_PARAM:floor")
    page.add_argument("--delay", type=float, default=0.05, help="模拟执行耗时（秒），默认 0.05")
    page.add_argument("--no-reply", action="store_true", help="收到动作不回执，用来测超时")
    page.add_argument("--floor", type=int, default=11, help="上报的当前楼层，默认 11")
    page.add_argument("--build", default="科研办公楼", help="上报的楼栋名")
    page.add_argument("--user-id", default="u-dev", help="hello 里的 userId")

    args = parser.parse_args()
    {"list": cmd_list, "call": cmd_call, "page": cmd_page}[args.command](args)


if __name__ == "__main__":
    main()
