#!/usr/bin/env python3
"""执行 M03 媒体模块自动化测试：优先使用独立临时 MySQL 8.4 实例；若当前环境无本地 mysqld，则自动回退至 H2 (MySQL 模式) 执行完整 Flyway 迁移与跨模块测试。"""
import os
from pathlib import Path
import secrets
import shutil
import socket
import subprocess
import sys
import tempfile
import time

MODULE = Path(__file__).resolve().parents[1]
POSTS_MODULE = MODULE.parent / "posts"


def resolve_java_home(env: dict) -> None:
    if env.get("JAVA_HOME"):
        return
    candidates = [
        Path("C:/Program Files/Java/jdk-21"),
        Path("/Applications/PyCharm.app/Contents/jbr/Contents/Home"),
    ]
    for candidate in candidates:
        if (candidate / "bin" / "javac").exists() or (candidate / "bin" / "javac.exe").exists():
            env["JAVA_HOME"] = str(candidate)
            env["PATH"] = str(candidate / "bin") + os.pathsep + env.get("PATH", "")
            return


def find_maven(env: dict) -> str:
    maven = shutil.which("mvn", path=env.get("PATH")) or shutil.which("mvn.cmd", path=env.get("PATH")) or shutil.which("mvn")
    if not maven:
        raise SystemExit("请安装 Maven 3.6.3 或更高版本。")
    return maven


def ensure_posts_module_installed(maven: str, env: dict) -> None:
    if POSTS_MODULE.exists():
        print("正在构建并安装前置依赖 M02 posts-module 至本地 Maven 仓库...", flush=True)
        subprocess.run(
            [maven, "-B", "-ntp", "-f", str(POSTS_MODULE / "pom.xml"), "-DskipTests", "install"],
            env=env,
            check=True,
        )


def run() -> int:
    env = os.environ.copy()
    resolve_java_home(env)
    maven = find_maven(env)
    subprocess.run([maven, "-v"], env=env, check=True)
    ensure_posts_module_installed(maven, env)

    mysql_dir = Path(env.get("MYSQL_BIN", "/opt/homebrew/opt/mysql@8.4/bin"))
    mysqld = mysql_dir / ("mysqld.exe" if os.name == "nt" else "mysqld")
    mysql = mysql_dir / ("mysql.exe" if os.name == "nt" else "mysql")

    if mysqld.exists() and mysql.exists():
        version = subprocess.check_output([str(mysqld), "--version"], text=True).strip()
        print("检测到本地 MySQL：" + version, flush=True)
        with tempfile.TemporaryDirectory(prefix="m03-") as work_str:
            work = Path(work_str)
            data = work / "data"
            data.mkdir()
            sock = work / "mysql.sock"
            log = work / "mysql.log"
            subprocess.run(
                [str(mysqld), "--no-defaults", "--initialize-insecure", f"--datadir={data}", f"--log-error={log}"],
                check=True,
            )
            with socket.socket() as probe:
                probe.bind(("127.0.0.1", 0))
                port = probe.getsockname()[1]
            args = [
                str(mysqld), "--no-defaults", f"--datadir={data}", f"--socket={sock}",
                f"--port={port}", "--bind-address=127.0.0.1", "--mysqlx=0", "--skip-log-bin",
                "--innodb-buffer-pool-size=64M", f"--pid-file={work / 'mysql.pid'}", f"--log-error={log}",
            ]
            server = subprocess.Popen(args, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            try:
                client = [str(mysql), "--no-defaults", f"--socket={sock}", "-uroot"]
                for _ in range(120):
                    if server.poll() is not None:
                        raise RuntimeError("临时数据库启动失败：" + log.read_text(encoding="utf-8", errors="replace"))
                    if subprocess.run(client + ["-e", "SELECT 1"], capture_output=True).returncode == 0:
                        break
                    time.sleep(0.25)
                else:
                    raise RuntimeError("临时数据库启动超时")
                password = secrets.token_hex(24)
                root_password = secrets.token_hex(24)
                sql = (
                    "CREATE DATABASE m03_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;"
                    f"CREATE USER 'm03_test'@'127.0.0.1' IDENTIFIED BY '{password}';"
                    "GRANT ALL ON m03_test.* TO 'm03_test'@'127.0.0.1';"
                    f"ALTER USER 'root'@'localhost' IDENTIFIED BY '{root_password}';"
                )
                subprocess.run(client, input=sql, text=True, check=True, capture_output=True)
                env["M03_TEST_DB_URL"] = (
                    f"jdbc:mysql://127.0.0.1:{port}/m03_test"
                    "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&allowPublicKeyRetrieval=true&sslMode=DISABLED"
                )
                env["M03_TEST_DB_USER"] = "m03_test"
                env["M03_TEST_DB_PASSWORD"] = password
                print("独立临时 MySQL 实例已就绪，开始执行 M03 verify...", flush=True)
                res = subprocess.run([maven, "-B", "-ntp", "-f", str(MODULE / "pom.xml"), "verify"] + sys.argv[1:], env=env)
                return res.returncode
            finally:
                server.terminate()
                try:
                    server.wait(timeout=30)
                except subprocess.TimeoutExpired:
                    server.kill()
                    server.wait()
                print("临时 MySQL 实例已关闭并清理。", flush=True)
    else:
        print("当前环境未指定本地 mysqld，自动使用内置 H2 (MODE=MySQL) + Flyway 执行 M03 单元与跨模块集成测试...", flush=True)
        res = subprocess.run([maven, "-B", "-ntp", "-f", str(MODULE / "pom.xml"), "verify"] + sys.argv[1:], env=env)
        return res.returncode


if __name__ == "__main__":
    raise SystemExit(run())
