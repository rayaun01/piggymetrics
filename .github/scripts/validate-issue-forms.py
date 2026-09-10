#!/usr/bin/env python3
"""Validate GitHub issue-form YAML files."""

from pathlib import Path
import sys

import yaml


TEMPLATE_DIR = Path(__file__).resolve().parent.parent / "ISSUE_TEMPLATE"
TOP_LEVEL_KEYS = {
    "name",
    "description",
    "title",
    "labels",
    "assignees",
    "projects",
    "type",
    "body",
}
ITEM_TYPES = {"markdown", "textarea", "input", "dropdown", "checkboxes"}
ATTRIBUTE_KEYS = {
    "markdown": {"value"},
    "textarea": {"label", "description", "placeholder", "value", "render"},
    "input": {"label", "description", "placeholder", "value"},
    "dropdown": {"label", "description", "multiple", "options", "default"},
    "checkboxes": {"label", "description", "options"},
}


def issue(path, message):
    return f"{path}: {message}"


def validate_file(path):
    display_path = Path(path)
    try:
        data = yaml.safe_load(display_path.read_text())
    except (OSError, yaml.YAMLError) as exc:
        return [issue(display_path, f"cannot read YAML: {exc}")]

    violations = []
    if not isinstance(data, dict):
        return [issue(display_path, "top level must be a mapping")]

    for key in ("name", "description"):
        if not isinstance(data.get(key), str) or not data[key].strip():
            violations.append(issue(display_path, f"{key} must be a non-empty string"))
    body = data.get("body")
    if not isinstance(body, list) or not body:
        violations.append(issue(display_path, "body must be a non-empty list"))
    for key in data:
        if key not in TOP_LEVEL_KEYS:
            violations.append(issue(display_path, f"unknown top-level key: {key}"))
    if not isinstance(body, list):
        return violations

    seen_ids = set()
    for index, item in enumerate(body):
        location = f"{display_path}: body[{index}]"
        if not isinstance(item, dict):
            violations.append(f"{location} must be a mapping")
            continue

        item_type = item.get("type")
        if item_type not in ITEM_TYPES:
            violations.append(
                f"{location}.type must be one of {', '.join(sorted(ITEM_TYPES))}"
            )
            continue

        attributes = item.get("attributes")
        if not isinstance(attributes, dict):
            violations.append(f"{location}.attributes must be a mapping")
            attributes = {}
        allowed = ATTRIBUTE_KEYS[item_type]
        for key in attributes:
            if key not in allowed:
                violations.append(f"{location}.attributes has unknown key: {key}")

        required = {"markdown": ("value",), "textarea": ("label",),
                    "input": ("label",), "dropdown": ("label", "options"),
                    "checkboxes": ("label", "options")}[item_type]
        for key in required:
            if key not in attributes:
                violations.append(f"{location}.attributes requires {key}")

        if item_type == "dropdown":
            options = attributes.get("options")
            if not isinstance(options, list) or not options:
                violations.append(
                    f"{location}.attributes.options must be a non-empty list of strings"
                )
            elif any(not isinstance(option, str) for option in options):
                violations.append(
                    f"{location}.attributes.options must be a non-empty list of strings"
                )
        elif item_type == "checkboxes":
            options = attributes.get("options")
            if not isinstance(options, list) or not options:
                violations.append(
                    f"{location}.attributes.options must be a non-empty list of mappings"
                )
            elif any(
                not isinstance(option, dict) or "label" not in option
                for option in options
            ):
                violations.append(
                    f"{location}.attributes.options must be mappings with a label"
                )

        if "id" in item:
            item_id = item["id"]
            if not isinstance(item_id, str) or not item_id.strip():
                violations.append(f"{location}.id must be a non-empty string")
            elif item_id in seen_ids:
                violations.append(f"{location}.id must be unique within the file")
            else:
                seen_ids.add(item_id)
            if item_type == "markdown":
                violations.append(f"{location} markdown items must not have an id")

        if "validations" in item:
            validations = item["validations"]
            if item_type == "markdown":
                violations.append(f"{location} markdown items must not have validations")
            if (
                not isinstance(validations, dict)
                or "required" not in validations
                or set(validations) - {"required"}
                or not isinstance(validations.get("required"), bool)
            ):
                violations.append(
                    f"{location}.validations must only contain boolean required"
                )

    return violations


def template_paths(arguments):
    paths = []
    inputs = [TEMPLATE_DIR] if not arguments else [Path(arg) for arg in arguments]
    for input_path in inputs:
        if input_path.is_dir():
            matches = sorted(
                path
                for pattern in ("*.yml", "*.yaml")
                for path in input_path.glob(pattern)
            )
        else:
            matches = [input_path]
        paths.extend(matches)

    unique = []
    seen = set()
    for path in paths:
        resolved = path.resolve()
        if resolved not in seen and path.name != "config.yml":
            seen.add(resolved)
            unique.append(path)
    return unique


def main(arguments):
    all_violations = []
    paths = template_paths(arguments)
    for path in paths:
        violations = validate_file(path)
        if violations:
            all_violations.extend(violations)
        else:
            print(f"OK {path}")
    for violation in all_violations:
        print(violation, file=sys.stderr)
    return 1 if all_violations else 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
