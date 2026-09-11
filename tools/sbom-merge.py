#!/usr/bin/env python3
"""Merge a Maven CycloneDX BOM with an npm CycloneDX BOM.

Why this exists at all: cyclonedx-maven-plugin describes what Maven resolved,
and it is very good at that - group, artifact, version and scope come straight
from the resolution, not from guessing at file names. What it cannot do is see
npm. A Vaadin application ships a frontend bundle built from npm packages, and
those packages are simply absent from the Maven BOM. Measured on this project:
129 Maven components, 118 npm components, no overlap at all.

The official merge tool, cyclonedx-cli, is a .NET binary distributed through
GitHub releases. Requiring it would mean every machine that builds this project
first has to install it. The merge itself is small enough to keep in the tree
where it can be read, so that is what this file is.

Usage:
    sbom-merge.py <maven-bom.json> <npm-bom.json> <output.json> [root-name]

The two inputs must carry the same CycloneDX specVersion; the script refuses
otherwise rather than producing something that only looks like a BOM.
"""
import hashlib
import json
import sys
import uuid


def fail(message):
    print(f"sbom-merge: {message}", file=sys.stderr)
    raise SystemExit(1)


def load(path):
    try:
        with open(path, encoding="utf-8") as handle:
            return json.load(handle)
    except FileNotFoundError:
        fail(f"input not found: {path}")
    except json.JSONDecodeError as error:
        fail(f"{path} is not valid JSON: {error}")


def refs_of(bom):
    """Every bom-ref the document defines, so collisions can be detected."""
    found = {c.get("bom-ref") for c in bom.get("components", []) if c.get("bom-ref")}
    root = bom.get("metadata", {}).get("component", {}).get("bom-ref")
    if root:
        found.add(root)
    return found


def stable_serial(*sources):
    """A serial number derived from the inputs.

    CycloneDX does not require the serial to be reproducible, but a build that
    produces a different document for identical inputs is harder to compare
    across releases - and comparing releases is the whole point of keeping the
    BOM around.
    """
    digest = hashlib.sha256("|".join(sources).encode("utf-8")).digest()
    return f"urn:uuid:{uuid.UUID(bytes=digest[:16], version=5)}"


def main(argv):
    if not 4 <= len(argv) <= 5:
        fail("usage: sbom-merge.py <maven-bom> <npm-bom> <output> [root-name]")
    maven_path, npm_path, out_path = argv[1:4]
    root_name = argv[4] if len(argv) == 5 else "frontend"

    maven, npm = load(maven_path), load(npm_path)

    if maven.get("specVersion") != npm.get("specVersion"):
        fail(f"specVersion differs: maven={maven.get('specVersion')} "
             f"npm={npm.get('specVersion')} - merging these would be a guess")

    collisions = refs_of(maven) & refs_of(npm)
    if collisions:
        fail(f"bom-ref collision, refusing to merge: {sorted(collisions)[:5]}")

    merged = json.loads(json.dumps(maven))  # deep copy, leave the input alone
    maven_root = merged.get("metadata", {}).get("component", {}).get("bom-ref")

    # The npm document has a root component of its own. Vaadin generates
    # package.json without a name field, so that root arrives called "no-name";
    # it is renamed here and hung under the Maven root, which keeps the
    # dependency graph connected instead of leaving two unrelated trees.
    npm_root = npm.get("metadata", {}).get("component")
    if npm_root:
        npm_root = json.loads(json.dumps(npm_root))
        npm_root["name"] = root_name
        npm_root["type"] = "application"
        # This node is an aggregate this script invents so the npm subtree has
        # somewhere to hang; it is not a package anyone can install. Leaving the
        # npm root's purl on it would claim a package called "no-name" exists,
        # and leaving its licence would put a spurious UNLICENSED entry into the
        # licence statistics of every report built from this document.
        npm_root.pop("purl", None)
        npm_root.pop("licenses", None)
        npm_root.pop("externalReferences", None)
        # The bom-ref travels into every report, so it gets the same treatment as
        # the name. Renaming it means rewriting the references that point at it.
        old_ref = npm_root.get("bom-ref")
        if old_ref:
            npm_root["bom-ref"] = root_name
            # cyclonedx-npm prefixes every reference with the root package name,
            # so all 118 npm refs read "no-name|@vaadin/grid@25.2.7". Renaming
            # only the root would leave that prefix everywhere; the prefix is
            # rewritten too, on the components and on both sides of every edge.
            def rename(ref):
                if ref == old_ref:
                    return root_name
                prefix = f"{old_ref}|"
                return root_name + "|" + ref[len(prefix):] if ref.startswith(prefix) else ref

            for component in npm.get("components", []):
                if component.get("bom-ref"):
                    component["bom-ref"] = rename(component["bom-ref"])
            for entry in npm.get("dependencies", []):
                if entry.get("ref"):
                    entry["ref"] = rename(entry["ref"])
                entry["dependsOn"] = [rename(r) for r in entry.get("dependsOn", [])]
        merged.setdefault("components", []).append(npm_root)

    merged.setdefault("components", []).extend(npm.get("components", []))

    dependencies = merged.setdefault("dependencies", [])
    dependencies.extend(npm.get("dependencies", []))
    if npm_root and maven_root:
        npm_root_ref = npm_root.get("bom-ref")
        for entry in dependencies:
            if entry.get("ref") == maven_root and npm_root_ref:
                entry.setdefault("dependsOn", []).append(npm_root_ref)
                break

    merged["serialNumber"] = stable_serial(
        maven.get("serialNumber", ""), npm.get("serialNumber", ""))
    merged.setdefault("metadata", {})["tools"] = maven.get("metadata", {}).get("tools", {})

    with open(out_path, "w", encoding="utf-8") as handle:
        json.dump(merged, handle, indent=2, ensure_ascii=False)
        handle.write("\n")

    ecosystems = {}
    for component in merged.get("components", []):
        purl = component.get("purl") or ""
        key = purl.split("/")[0].replace("pkg:", "") if purl else "(no purl)"
        ecosystems[key] = ecosystems.get(key, 0) + 1
    summary = ", ".join(f"{k} {v}" for k, v in sorted(ecosystems.items()))
    print(f"sbom-merge: {len(merged.get('components', []))} components "
          f"({summary}) -> {out_path}")


if __name__ == "__main__":
    main(sys.argv)
