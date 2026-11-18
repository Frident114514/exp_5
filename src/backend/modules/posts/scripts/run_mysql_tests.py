#!/usr/bin/env python3
"""在独立临时 MySQL 8.4 实例中执行 M02 测试，不连接或修改现有数据库。"""
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


def run():
    env = os.environ.copy()
    if not env.get("JAVA_HOME"):
        candidate = Path("/Applications/PyCharm.app/Contents/jbr/Contents/Home")
        if (candidate / "bin/javac").exists():
            env["JAVA_HOME"] = str(candidate)
    mysql_dir = Path(env.get("MYSQL_BIN", "/opt/homebrew/opt/mysql@8.4/bin"))
    mysqld = mysql_dir / "mysqld"
    mysql = mysql_dir / "mysql"
    if not mysqld.exists() or not mysql.exists():
        raise SystemExit("请安装 MySQL 8.4 并通过 MYSQL_BIN 指定其 bin 目录。")
    version = subprocess.check_output([str(mysqld), "--version"], text=True).strip()
    if "Ver 8.4." not in version:
        raise SystemExit("此脚本要求 MySQL 8.4；当前：" + version)
    maven = shutil.which("mvn")
    if not maven:
        raise SystemExit("请安装 Maven 3.6.3 或更高版本。")
    print("数据库：" + version, flush=True)
    subprocess.run([maven, "-v"], env=env, check=True)
    with tempfile.TemporaryDirectory(prefix="m02-", dir="/tmp") as work:
        work = Path(work)
        data = work / "data"
        data.mkdir()
        sock = work / "mysql.sock"
        log = work / "mysql.log"
        subprocess.run([str(mysqld), "--no-defaults", "--initialize-insecure",
                        f"--datadir={data}", f"--log-error={log}"], check=True)
        with socket.socket() as probe:
            probe.bind(("127.0.0.1", 0))
            port = probe.getsockname()[1]
        args = [str(mysqld), "--no-defaults", f"--datadir={data}", f"--socket={sock}",
                f"--port={port}", "--bind-address=127.0.0.1", "--mysqlx=0", "--skip-log-bin",
                "--innodb-buffer-pool-size=64M", f"--pid-file={work / 'mysql.pid'}",
                f"--log-error={log}"]
        server = subprocess.Popen(args, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        try:
            client = [str(mysql), "--no-defaults", f"--socket={sock}", "-uroot"]
            for _ in range(120):
                if server.poll() is not None:
                    raise RuntimeError("临时数据库启动失败：" + log.read_text())
                result = subprocess.run(client + ["-e", "SELECT 1"], capture_output=True)
                if result.returncode == 0:
                    break
                time.sleep(.25)
            else:
                raise RuntimeError("临时数据库启动超时")
            password = secrets.token_hex(24)
            root_password = secrets.token_hex(24)
            sql = (
                "CREATE DATABASE m02_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;"
                f"CREATE USER 'm02_test'@'127.0.0.1' IDENTIFIED BY '{password}';"
                "GRANT ALL ON m02_test.* TO 'm02_test'@'127.0.0.1';"
                f"ALTER USER 'root'@'localhost' IDENTIFIED BY '{root_password}';"
            )
            subprocess.run(client, input=sql, text=True, check=True, capture_output=True)
            env["M02_TEST_DB_URL"] = f"jdbc:mysql://127.0.0.1:{port}/m02_test?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&allowPublicKeyRetrieval=true&sslMode=DISABLED"
            env["M02_TEST_DB_USER"] = "m02_test"
            env["M02_TEST_DB_PASSWORD"] = password
            print("独立临时数据库已就绪；随机密码仅通过进程环境传递。", flush=True)
            result = subprocess.run([maven, "-B", "-ntp", "-f", str(MODULE / "pom.xml"), "verify"] + sys.argv[1:], env=env)
            return result.returncode
        finally:
            server.terminate()
            try:
                server.wait(timeout=30)
            except subprocess.TimeoutExpired:
                server.kill()
                server.wait()
            print("临时 MySQL 实例已关闭，临时数据目录已清理。", flush=True)


if __name__ == "__main__":
    raise SystemExit(run())

