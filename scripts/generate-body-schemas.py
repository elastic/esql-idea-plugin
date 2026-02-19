#!/usr/bin/env python3
"""
Processes elasticsearch-specification schema.json into a simplified
body-schemas.json for REST console autocomplete.

Usage:
    python3 scripts/generate-body-schemas.py [schema.json path] [output path]

If schema.json path is not provided, downloads from GitHub.
"""

import json
import sys
import os
from urllib.request import urlopen

SCHEMA_URL = "https://raw.githubusercontent.com/elastic/elasticsearch-specification/main/output/schema/schema.json"
MAX_DEPTH = 2
OUTPUT_PATH = "src/main/resources/rest-api/body-schemas.json"


def download_schema(cache_path):
    if os.path.exists(cache_path):
        print(f"Using cached schema: {cache_path}")
        with open(cache_path) as f:
            return json.load(f)
    print(f"Downloading schema from {SCHEMA_URL}...")
    os.makedirs(os.path.dirname(cache_path), exist_ok=True)
    with urlopen(SCHEMA_URL) as resp:
        data = json.loads(resp.read())
    with open(cache_path, "w") as f:
        json.dump(data, f)
    print(f"Cached schema to {cache_path}")
    return data


def build_type_lookup(types):
    lookup = {}
    for t in types:
        name = t.get("name", {})
        key = f"{name.get('namespace', '')}.{name.get('name', '')}"
        lookup[key] = t
    return lookup


def resolve_type(type_spec, lookup, depth):
    if depth > MAX_DEPTH or not isinstance(type_spec, dict):
        return {"type": "object"}

    kind = type_spec.get("kind", "")

    if kind == "instance_of":
        type_ref = type_spec.get("type", {})
        ns = type_ref.get("namespace", "")
        name = type_ref.get("name", "")

        builtins = {
            "string": "string", "boolean": "boolean", "integer": "integer",
            "long": "long", "float": "float", "double": "double",
            "number": "number", "binary": "string", "null": "null",
            "void": "object"
        }
        if ns == "_builtins":
            return {"type": builtins.get(name, name)}

        return resolve_named_type(ns, name, lookup, depth)

    elif kind == "dictionary_of":
        return {"type": "object"}

    elif kind == "array_of":
        value = type_spec.get("value")
        if value:
            items = resolve_type(value, lookup, depth)
            return {"type": "array", "items": items}
        return {"type": "array"}

    elif kind == "union_of":
        items = type_spec.get("items", [])
        for item in items:
            resolved = resolve_type(item, lookup, depth)
            if "properties" in resolved:
                return resolved
        if items:
            return resolve_type(items[0], lookup, depth)
        return {"type": "object"}

    elif kind == "literal_value":
        return {"type": "string"}

    return {"type": "object"}


def resolve_named_type(ns, name, lookup, depth):
    key = f"{ns}.{name}"
    t = lookup.get(key)
    if not t:
        return {"type": "object"}

    t_kind = t.get("kind", "")

    if t_kind == "interface":
        props = extract_properties(t, lookup, depth + 1)
        if props:
            return {"type": "object", "properties": props}
        return {"type": "object"}

    elif t_kind == "enum":
        members = [m.get("name", "") for m in t.get("members", [])]
        if len(members) > 20:
            members = members[:20]
        return {"type": "string", "enum": members}

    elif t_kind == "type_alias":
        alias_type = t.get("type")
        if alias_type:
            return resolve_type(alias_type, lookup, depth + 1)
        return {"type": "object"}

    return {"type": "object"}


def extract_properties(type_def, lookup, depth):
    result = {}
    if depth > MAX_DEPTH:
        return result

    body = type_def.get("body")
    if body and body.get("kind") == "properties":
        props_list = body.get("properties", [])
    else:
        props_list = type_def.get("properties", [])

    for prop in props_list:
        prop_name = prop.get("name")
        if not prop_name:
            continue
        prop_type = prop.get("type")
        if prop_type:
            resolved = resolve_type(prop_type, lookup, depth)
            if depth == 0:
                desc = prop.get("description", "")
                if desc:
                    resolved = {**resolved, "description": desc[:120]}
            result[prop_name] = resolved

    inherits = type_def.get("inherits")
    if inherits and isinstance(inherits, dict):
        parent_type = inherits.get("type", {})
        parent_ns = parent_type.get("namespace", "")
        parent_name = parent_type.get("name", "")
        parent_key = f"{parent_ns}.{parent_name}"
        parent_def = lookup.get(parent_key)
        if parent_def and parent_def.get("kind") == "interface":
            parent_props = extract_properties(parent_def, lookup, depth)
            for k, v in parent_props.items():
                if k not in result:
                    result[k] = v

    return result


def main():
    schema_path = sys.argv[1] if len(sys.argv) > 1 else "build/elastic-spec/schema.json"
    output_path = sys.argv[2] if len(sys.argv) > 2 else OUTPUT_PATH

    schema = download_schema(schema_path)

    types = schema.get("types", [])
    endpoints = schema.get("endpoints", [])

    lookup = build_type_lookup(types)
    print(f"Loaded {len(endpoints)} endpoints, {len(types)} types")

    result = {}
    for ep in endpoints:
        ep_name = ep.get("name", "")
        request_ref = ep.get("request")
        if not request_ref:
            continue
        req_key = f"{request_ref.get('namespace', '')}.{request_ref.get('name', '')}"
        req_type = lookup.get(req_key)
        if not req_type:
            continue

        body = req_type.get("body")
        if not body:
            continue
        if body.get("kind") != "properties":
            continue

        props = extract_properties(req_type, lookup, 0)
        if props:
            result[ep_name] = props

    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    with open(output_path, "w") as f:
        json.dump(result, f, separators=(",", ":"), ensure_ascii=False)

    print(f"Generated {output_path} with {len(result)} endpoint schemas")
    total_props = sum(len(v) for v in result.values())
    print(f"Total top-level properties: {total_props}")


if __name__ == "__main__":
    main()
