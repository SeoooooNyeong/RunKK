"""E2E 시험용 카카오 토큰 정보 스텁(표준 라이브러리만).

서버(backend local 프로필)의 KAKAO_API_BASE_URL을 이 스텁으로 두면, 서버의 KakaoLoginGateway가 실제와 같은 절차로
GET /v1/user/access_token_info 를 부른다. 스텁은 `Authorization: Bearer e2e-<숫자>` 토큰에만
{"id": <숫자>, "app_id": <KAKAO_APP_ID>, "expires_in": 3600} 을 돌려주고, 그 밖의 토큰은 카카오처럼 401로 거부한다.
서버 쪽 인증 우회 코드는 없다. 설정만 바꾼다.

실행: python e2e/kakao_stub.py --port 18081 --app-id 900001
"""

from __future__ import annotations

import argparse
import json
import re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

TOKEN_PATTERN = re.compile(r"^Bearer e2e-(\d{1,18})$")
TOKEN_INFO_PATH = "/v1/user/access_token_info"


def token_info(authorization: str | None, app_id: int) -> tuple[int, dict]:
    """Authorization 헤더 값으로 (상태 코드, 응답 본문)을 정한다. 카카오 오류 형식(code·msg)을 흉내 낸다."""
    match = TOKEN_PATTERN.match(authorization or "")
    if match is None:
        return 401, {"code": -401, "msg": "this access token does not exist"}
    return 200, {"id": int(match.group(1)), "app_id": app_id, "expires_in": 3600}


def make_handler(app_id: int) -> type[BaseHTTPRequestHandler]:
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self) -> None:  # noqa: N802 (http.server 규칙)
            if self.path.split("?", 1)[0] == "/health":
                self._send(200, {"status": "UP"})
                return
            if self.path.split("?", 1)[0] != TOKEN_INFO_PATH:
                self._send(404, {"code": -404, "msg": "not found"})
                return
            status, body = token_info(self.headers.get("Authorization"), app_id)
            self._send(status, body)

        def _send(self, status: int, body: dict) -> None:
            data = json.dumps(body).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json;charset=UTF-8")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def log_message(self, fmt: str, *args: object) -> None:
            # 토큰은 남기지 않는다. 경로·상태만 표준 오류로.
            print(f"[kakao-stub] {self.command} {self.path.split('?', 1)[0]} {args[1] if len(args) > 1 else ''}", flush=True)

    return Handler


def main() -> None:
    parser = argparse.ArgumentParser(description="런크크 E2E 카카오 토큰 정보 스텁")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=18081)
    parser.add_argument("--app-id", type=int, default=900001, help="서버 KAKAO_APP_ID와 같은 값")
    args = parser.parse_args()
    server = ThreadingHTTPServer((args.host, args.port), make_handler(args.app_id))
    print(f"[kakao-stub] http://{args.host}:{args.port}{TOKEN_INFO_PATH} app_id={args.app_id}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
