#!/usr/bin/env python3
"""Check a source tree for accidental local files and embedded credentials.

This check complements, rather than replaces, Android Lint and a dependency audit.
"""
from __future__ import annotations

import ipaddress
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TEXT_SUFFIXES = {
    ".kt", ".java", ".xml", ".kts", ".gradle", ".md", ".txt",
    ".py", ".sh", ".toml", ".yml", ".yaml", ".json", ".properties",
    ".pro", ".bat", ".gitignore",
}
SECRET_FILE_NAMES = {
    "local.properties", "keystore.properties", ".env", "google-services.json",
    "id_rsa", "id_ed25519", "credentials.json", "service-account.json",
}
SECRET_SUFFIXES = {".jks", ".keystore", ".p12", ".pfx", ".pem", ".key"}
SKIP_DIRS = {".git", ".gradle", "build", "__pycache__", ".idea"}
SENSITIVE = {
    "personal home directory": re.compile(r"(?:/home/|/Users/)[^/\\\s]+/|[A-Za-z]:\\Users\\[^\\\s]+\\"),
    "private key header": re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----"),
    "credential-shaped token": re.compile(r"(?:ghp_[a-zA-Z0-9]{30,}|github_pat_[a-zA-Z0-9_]{30,}|sk-[a-zA-Z0-9_-]{28,}|AIza[0-9A-Za-z_-]{35}|AKIA[0-9A-Z]{16}|xox[baprs]-[0-9A-Za-z-]{20,})"),
    "literal credential assignment": re.compile(r"(?im)^\s*(?:val|var|const|let|export)?\s*(?:[A-Z0-9_]*?(?:PASSWORD|PASSWD|API_KEY|CLIENT_SECRET|ACCESS_TOKEN)[A-Z0-9_]*?)\s*[:=]\s*[\"'](?!\s*[\"'])[\w+/=-]{8,}[\"']"),
}
ADDRESS = re.compile(r"(?<![\w.])(?:\d{1,3}\.){3}\d{1,3}(?![\w.])")


def scan() -> list[tuple[str, str]]:
    problems: list[tuple[str, str]] = []
    for file in sorted(ROOT.rglob("*")):
        if any(part in SKIP_DIRS for part in file.relative_to(ROOT).parts):
            continue
        if not file.is_file():
            continue
        relative = str(file.relative_to(ROOT))
        if file.name in SECRET_FILE_NAMES or file.suffix.lower() in SECRET_SUFFIXES:
            problems.append((relative, "local credential or signing file"))
            continue
        if file.suffix.lower() not in TEXT_SUFFIXES and file.name not in {"gradlew", "LICENSE", ".gitignore"}:
            continue
        if file.stat().st_size > 3_000_000:
            continue
        try:
            source = file.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        for label, pattern in SENSITIVE.items():
            # The scanner necessarily contains the example path prefix it searches for.
            if relative == "tools/check-source-hygiene.py" and label == "personal home directory":
                continue
            if pattern.search(source):
                problems.append((relative, label))
        for match in ADDRESS.finditer(source):
            try:
                address = ipaddress.ip_address(match.group())
            except ValueError:
                continue
            if address.is_private and not address.is_global and not address.is_loopback:
                # Skip documentation IP notation only when it is non-routable public documentation space.
                problems.append((relative, "private IP address"))
                break
    return sorted(set(problems))


def main() -> int:
    issues = scan()
    if issues:
        for filename, reason in issues:
            print(f"REVIEW  {filename}: {reason}")
        print(f"Source hygiene: {len(issues)} issue(s) need review")
        return 1
    print("PASS  No obvious personal paths, embedded credentials, signing files or private IP addresses")
    print("NOTE  Pattern-based checks cannot guarantee that a repository contains no private information")
    return 0


if __name__ == "__main__":
    sys.exit(main())
