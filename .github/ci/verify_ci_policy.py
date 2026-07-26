#!/usr/bin/env python3
"""Enforce immutable CI actions and active strict Gradle verification."""

from __future__ import annotations

import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
WORKFLOWS = ROOT / ".github" / "workflows"
REMOTE_ACTION = re.compile(
    r"^\s*uses:\s+"
    r"(?P<action>[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)?)"
    r"@(?P<sha>[0-9a-f]{40})\s+#\s+v[0-9][A-Za-z0-9_.-]*\s*$"
)
ANY_USES = re.compile(r"^\s*uses:\s+")
SHA256 = re.compile(r"^[0-9a-f]{64}$")
NS = {"v": "https://schema.gradle.org/dependency-verification"}


def gradle_invocations(path: Path) -> list[tuple[int, str]]:
    lines = path.read_text(encoding="utf-8").splitlines()
    invocations: list[tuple[int, str]] = []
    for index, line in enumerate(lines):
        if "./gradlew" not in line:
            continue
        command_lines = [line]
        cursor = index
        while command_lines[-1].rstrip().endswith("\\") and cursor + 1 < len(lines):
            cursor += 1
            command_lines.append(lines[cursor])
        invocations.append((index + 1, "\n".join(command_lines)))
    return invocations


def main() -> int:
    problems: list[str] = []
    workflow_files = sorted(WORKFLOWS.glob("*.y*ml"))
    for workflow in workflow_files:
        text = workflow.read_text(encoding="utf-8")
        if not re.search(
            r"^permissions:\s*\n\s{2}contents:\s*read\s*$",
            text,
            re.MULTILINE,
        ):
            problems.append(
                f"{workflow}: workflow-level permissions must be contents: read."
            )
        if "pull_request_target:" in text:
            problems.append(f"{workflow}: pull_request_target is not allowed.")
        if re.search(r"^\s+[A-Za-z0-9_-]+:\s+write\s*$", text, re.MULTILINE):
            problems.append(f"{workflow}: write permission requires a separate review.")
        for line_number, line in enumerate(text.splitlines(), start=1):
            if ANY_USES.match(line) and not REMOTE_ACTION.match(line):
                problems.append(
                    f"{workflow}:{line_number}: action must use a 40-character SHA "
                    "and adjacent version comment."
                )

    gradle_ci_files = [
        *workflow_files,
        *sorted((ROOT / ".github" / "ci").glob("*.sh")),
    ]
    for gradle_ci_file in gradle_ci_files:
        for line_number, invocation in gradle_invocations(gradle_ci_file):
            if "--dependency-verification=strict" not in invocation:
                problems.append(
                    f"{gradle_ci_file}:{line_number}: Gradle invocation must "
                    "explicitly enable strict dependency verification."
                )

    metadata = ROOT / "gradle" / "verification-metadata.xml"
    if not metadata.is_file():
        problems.append("gradle/verification-metadata.xml is missing.")
    else:
        try:
            root = ET.parse(metadata).getroot()
        except ET.ParseError as error:
            problems.append(f"{metadata}: invalid XML: {error}")
        else:
            verify_metadata = root.findtext("v:configuration/v:verify-metadata", None, NS)
            if verify_metadata != "true":
                problems.append("Dependency metadata verification must be enabled.")
            artifacts = root.findall("v:components/v:component/v:artifact", NS)
            if not artifacts:
                problems.append("Dependency verification metadata has no artifacts.")
            for artifact in artifacts:
                checksums = [
                    checksum.get("value", "")
                    for checksum in artifact.findall("v:sha256", NS)
                ]
                if not checksums or any(not SHA256.fullmatch(value) for value in checksums):
                    problems.append(
                        "Every dependency artifact must have a valid SHA-256 checksum: "
                        f"{artifact.get('name', '<unnamed>')}."
                    )

    for stale_name in (
        "verification-metadata.xml.bak",
        "verification-metadata.xml.disabled",
    ):
        if (ROOT / "gradle" / stale_name).exists():
            problems.append(f"Remove stale gradle/{stale_name}.")

    wrapper_properties = (
        ROOT / "gradle" / "wrapper" / "gradle-wrapper.properties"
    ).read_text(encoding="utf-8")
    checksum_match = re.search(
        r"^distributionSha256Sum=([0-9a-f]{64})$",
        wrapper_properties,
        re.MULTILINE,
    )
    if not checksum_match:
        problems.append("The Gradle wrapper distribution checksum is not pinned.")

    dependabot = ROOT / ".github" / "dependabot.yml"
    renovate = ROOT / "renovate.json"
    dependency_bots = [path for path in (dependabot, renovate) if path.is_file()]
    if len(dependency_bots) != 1:
        problems.append(
            "Configure exactly one dependency update bot (Dependabot or Renovate)."
        )
    elif renovate.is_file():
        try:
            renovate_config = json.loads(renovate.read_text(encoding="utf-8"))
        except json.JSONDecodeError as error:
            problems.append(f"{renovate}: invalid JSON: {error}")
        else:
            if renovate_config.get("automerge") is not False:
                problems.append("Renovate automerge must be disabled.")
            if renovate_config.get("platformAutomerge") is not False:
                problems.append("Renovate platformAutomerge must be disabled.")

    if problems:
        for problem in problems:
            print(f"ERROR: {problem}", file=sys.stderr)
        return 1
    print(
        f"CI policy verified across {len(workflow_files)} workflow file(s): "
        "immutable actions, read-only permissions, and strict dependency checks."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
