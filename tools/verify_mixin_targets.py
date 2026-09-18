#!/usr/bin/env python3


import os
import re
import subprocess
import sys
import zipfile

MIXIN_PACKAGE = os.path.join("dj2", "ae2opt", "mixin")
RETURN_OPS = ("return", "areturn", "ireturn", "lreturn", "freturn", "dreturn")


def _skip_string(text, i):

    i += 1
    while i < len(text):
        if text[i] == "\\":
            i += 2
            continue
        if text[i] == '"':
            return i + 1
        i += 1
    return i


def balanced_block(text, start):


    depth, i = 0, start
    while i < len(text):
        c = text[i]
        if c == '"':
            i = _skip_string(text, i)
            continue
        if c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return text[start + 1:i], i
        i += 1
    return None, len(text)


def strip_nested(block):

    out, i = [], 0
    while i < len(block):
        if block[i] == '"':
            end = _skip_string(block, i)
            out.append(block[i:end])
            i = end
            continue
        if block[i] == "(":
            _inner, end = balanced_block(block, i)
            i = end + 1
            continue
        out.append(block[i])
        i += 1
    return "".join(out)


def parse_injects(text):

    injects, errors = [], []
    for m in re.finditer(r"injection\.Inject\(", text):
        block, _end = balanced_block(text, m.end() - 1)
        if block is None:
            errors.append("unterminated @Inject annotation in javap output")
            continue
        own = strip_nested(block)
        method = re.search(r'method=\["([^"]+)"', own)
        allow = re.search(r"allow=(-?\d+)", own)
        at_match = re.search(r"injection\.At\(", block)
        at_value = None
        if at_match:
            at_block, _e = balanced_block(block, at_match.end() - 1)
            found = re.search(r'value="(\w+)"', at_block or "")
            at_value = found.group(1) if found else None
        if not method or not at_value:
            errors.append("could not read method/at from an @Inject annotation")
            continue
        injects.append((method.group(1), at_value,
                        int(allow.group(1)) if allow else None))
    return injects, errors


def member_blocks(text):

    body = text[text.index("{"):] if "{" in text else text
    blocks, current = [], None
    for line in body.splitlines():
        if re.match(r"^  \S", line) and line.rstrip().endswith(";"):
            if current:
                blocks.append(current)
            current = [line]
        elif current is not None:
            current.append(line)
    if current:
        blocks.append(current)
    return ["\n".join(b) for b in blocks]


def _is_shadow(block):
    return re.search(r"RuntimeVisible\w*Annotations:(?:(?!\n  \S).)*?mixin\.Shadow", block, re.S) is not None


def parse_shadows(text):

    shadows = []
    for block in member_blocks(text):
        head = block.splitlines()[0].strip()
        if head.endswith(");") or "(" in head:
            continue
        name = re.match(r"^.*?([\w$]+);$", head)
        desc = re.search(r"^\s*descriptor: (\S+)$", block, re.M)
        if name and desc and _is_shadow(block):
            shadows.append((name.group(1), desc.group(1)))
    return shadows


def parse_redirects(text):


    redirects, errors = [], []
    for m in re.finditer(r"injection\.Redirect\(", text):
        block, _end = balanced_block(text, m.end() - 1)
        if block is None:
            errors.append("unterminated @Redirect annotation in javap output")
            continue
        own = strip_nested(block)
        method = re.search(r'method=\["([^"]+)"', own)
        require = re.search(r"require=(-?\d+)", own)
        at_match = re.search(r"injection\.At\(", block)
        at_value = at_target = at_opcode = None
        if at_match:
            at_block, _e = balanced_block(block, at_match.end() - 1)
            at_block = at_block or ""
            v = re.search(r'value="(\w+)"', at_block)
            t = re.search(r'target="([^"]+)"', at_block)
            o = re.search(r"opcode=(-?\d+)", at_block)
            at_value = v.group(1) if v else None
            at_target = t.group(1) if t else None
            at_opcode = int(o.group(1)) if o else None
        if not method or not at_value:
            errors.append("could not read method/at from an @Redirect annotation")
            continue
        redirects.append((method.group(1), at_value, at_target, at_opcode,
                          int(require.group(1)) if require else None))
    return redirects, errors


def parse_field_target(target):

    m = re.match(r"^L([^;]+);([^:]+):(.+)$", target or "")
    if not m:
        return None
    return m.group(1), m.group(2), m.group(3)


def target_method_bodies(binary_name, classpath):


    text, ok = run_javap(["-p", "-c", "-s", "-cp", classpath, binary_name])
    if not ok:
        return None
    bodies, pending, current = {}, None, None
    for line in text.splitlines():
        stripped = line.strip()
        m = re.match(r"descriptor: (\S+)", stripped)
        if m and pending is not None:
            if m.group(1).startswith("("):
                current = (pending, m.group(1))
                bodies[current] = []
            else:
                current = None
            pending = None
            continue
        m = re.match(r"^(?:.*\s)?([A-Za-z_$<][\w$<>]*)\s*\(.*\);$", stripped)
        if m:
            pending = m.group(1)
            continue
        if current is not None:
            bodies[current].append(stripped)
    return dict((k, "\n".join(v)) for k, v in bodies.items())


def parse_shadow_methods(text):


    shadows = []
    for block in member_blocks(text):
        head = block.splitlines()[0].strip()
        if not head.endswith(");"):
            continue
        name = re.match(r"^(?:.*\s)?([A-Za-z_$<][\w$<>]*)\s*\(.*\);$", head)
        desc = re.search(r"^\s*descriptor: (\(\S*)$", block, re.M)
        if name and desc and _is_shadow(block):
            shadows.append((name.group(1), desc.group(1)))
    return shadows


def run_javap(args):

    proc = subprocess.run(["javap"] + args, capture_output=True, text=True)
    return proc.stdout, proc.returncode == 0


def target_members(binary_name, classpath):
    text, ok = run_javap(["-p", "-c", "-s", "-cp", classpath, binary_name])
    if not ok or ("class" not in text and "interface" not in text):
        return None, None
    methods, fields, pending, current = {}, {}, None, None
    for line in text.splitlines():
        stripped = line.strip()
        m = re.match(r"descriptor: (\S+)", stripped)
        if m:
            desc = m.group(1)
            if pending is not None:
                if desc.startswith("("):
                    current = (pending, desc)
                    methods[current] = 0
                else:
                    fields[pending] = desc
                    current = None
                pending = None
            continue
        m = re.match(r"^(?:.*\s)?([A-Za-z_$<][\w$<>]*)\s*\(.*\);$", stripped)
        if m:
            pending = m.group(1)
            continue
        m = re.match(r"^(?:.*\s)?([A-Za-z_$][\w$]*);$", stripped)
        if m:
            pending = m.group(1)
            continue
        m = re.match(r"^\d+: (\w+)", stripped)
        if m and current is not None and m.group(1) in RETURN_OPS:
            methods[current] += 1
    return methods, fields


def classify_expectations(errors, expected):

    missing = [e for e in expected if not any(e in err for err in errors)]
    unexpected = [err for err in errors if not any(e in err for e in expected)]
    return missing, unexpected


def selftest():

    same_line = '''
        org.spongepowered.asm.mixin.injection.Inject(
          method=["cellUpdate"]
          at=[@org.spongepowered.asm.mixin.injection.At(
            value="RETURN"
          )]
          require=1
          allow=1
        )
'''
    split_line = same_line.replace("          )]\n", "          )\n          ]\n")
    descriptor = same_line.replace(
        'method=["cellUpdate"]',
        'method=["extractItems(Lappeng/api/storage/data/IAEItemStack;'
        'Lappeng/api/config/Actionable;)Lappeng/api/storage/data/IAEItemStack;"]')
    failures = []
    cases = (
        ("nested-close-on-same-line", same_line, [("cellUpdate", "RETURN", 1)]),
        ("nested-close-on-own-line", split_line, [("cellUpdate", "RETURN", 1)]),
        ("method spec containing parentheses", descriptor,
         [("extractItems(Lappeng/api/storage/data/IAEItemStack;"
           "Lappeng/api/config/Actionable;)Lappeng/api/storage/data/IAEItemStack;", "RETURN", 1)]),
    )
    for label, text, expected in cases:
        injects, errors = parse_injects(text)
        if errors or injects != expected:
            failures.append("%s: parsed %s errors=%s" % (label, injects, errors))
        else:
            print("pass  parsed correctly with %s" % label)

    generic_field = '''{
  private appeng.api.storage.data.IItemList<appeng.api.storage.data.IAEItemStack> currentlyCached;
    descriptor: Lappeng/api/storage/data/IItemList;
    flags: (0x0002) ACC_PRIVATE
    Signature: #318                         // generic
    RuntimeVisibleAnnotations:
      0: #320()
        org.spongepowered.asm.mixin.Shadow

  private int plain;
    descriptor: I
    flags: (0x0002) ACC_PRIVATE
}'''
    redirect_text = """
        org.spongepowered.asm.mixin.injection.Redirect(
          method=["extractItem(Lnet/minecraft/item/ItemStack;IZLjava/util/function/Predicate;)Lnet/minecraft/item/ItemStack;"]
          at=[@org.spongepowered.asm.mixin.injection.At(
            value="FIELD"
            target="Lcom/jaquadro/minecraft/storagedrawers/block/tile/TileEntityController;drawerSlots:[I"
            opcode=180
          )]
          require=1
        )
"""
    redirects, errs = parse_redirects(redirect_text)
    expected = [("extractItem(Lnet/minecraft/item/ItemStack;IZLjava/util/function/Predicate;)"
                 "Lnet/minecraft/item/ItemStack;", "FIELD",
                 "Lcom/jaquadro/minecraft/storagedrawers/block/tile/TileEntityController;drawerSlots:[I",
                 180, 1)]
    if errs or redirects != expected:
        failures.append("@Redirect: parsed %s errors=%s" % (redirects, errs))
    elif parse_field_target(expected[0][2]) != (
            "com/jaquadro/minecraft/storagedrawers/block/tile/TileEntityController", "drawerSlots", "[I"):
        failures.append("@Redirect field target split is wrong")
    else:
        print("pass  a @Redirect field target is parsed with its opcode and require")

    shadow_method = """{
  public abstract com.jaquadro...IDrawer getDrawer(int);
    descriptor: (I)Lcom/jaquadro/minecraft/storagedrawers/api/storage/IDrawer;
    flags: (0x0401) ACC_PUBLIC, ACC_ABSTRACT
    RuntimeVisibleAnnotations:
      0: #40()
        org.spongepowered.asm.mixin.Shadow

  public void notShadowed();
    descriptor: ()V
    flags: (0x0001) ACC_PUBLIC
}"""
    methods = parse_shadow_methods(shadow_method)
    if methods != [("getDrawer", "(I)Lcom/jaquadro/minecraft/storagedrawers/api/storage/IDrawer;")]:
        failures.append("@Shadow method: parsed %s" % (methods,))
    elif parse_shadows(shadow_method):
        failures.append("@Shadow method was also parsed as a field")
    else:
        print("pass  a @Shadow method is verified and not mistaken for a field")

    shadows = parse_shadows(generic_field)
    if shadows != [("currentlyCached", "Lappeng/api/storage/data/IItemList;")]:
        failures.append("generic @Shadow: parsed %s" % (shadows,))
    else:
        print("pass  a Signature line does not hide a generic @Shadow")

    missing, unexpected = classify_expectations(
        ["MixinBadAllow: 2 injection sites but allow=1", "MixinBadAllow: @Shadow field x"],
        ["2 injection sites but allow=1", "@Shadow field x"])
    if missing or unexpected:
        failures.append("expectation matching: missing=%s unexpected=%s" % (missing, unexpected))
    else:
        print("pass  expected errors are matched")

    missing, unexpected = classify_expectations(
        ["no mixin classes found - nothing was verified"], ["2 injection sites but allow=1"])
    if not missing or not unexpected:
        failures.append("expectation matching did not reject an unrelated failure")
    else:
        print("pass  an unrelated failure is not accepted as the expected one")

    for f in failures:
        print("FAIL  " + f)
    print("RESULT: " + ("FAIL" if failures else "OK"))
    return 1 if failures else 0


def main():
    if len(sys.argv) == 2 and sys.argv[1] == "--selftest":
        return selftest()
    argv = sys.argv[1:]
    expected = []
    while argv and argv[0] == "--expect-error":
        if len(argv) < 2:
            print("--expect-error needs a substring")
            return 2
        expected.append(argv[1])
        argv = argv[2:]
    if len(argv) < 2:
        print(__doc__)
        return 2

    classes_dir = argv[0].rstrip(os.sep).rstrip("/")
    jars = argv[1:]
    classpath = os.pathsep.join(jars + [classes_dir])

    mixin_classes = []
    for root, _dirs, files in os.walk(classes_dir):
        if MIXIN_PACKAGE not in root.replace("/", os.sep):
            continue
        mixin_classes += [os.path.join(root, f) for f in files
                          if f.endswith(".class") and "$" not in f]

    jar_entries = set()
    for jar in jars:
        with zipfile.ZipFile(jar) as z:
            jar_entries.update(n[:-6].replace("/", ".") for n in z.namelist()
                               if n.endswith(".class"))

    errors, warnings, checked = [], [], 0
    if not mixin_classes:
        errors.append("no mixin classes found under %s - nothing was verified"
                      % os.path.join(classes_dir, MIXIN_PACKAGE))

    for class_file in sorted(mixin_classes):
        entry = class_file[len(classes_dir):].lstrip(os.sep).lstrip("/")
        entry = entry.replace(os.sep, ".").replace("/", ".")[:-len(".class")]
        short = entry.rsplit(".", 1)[-1]
        text, javap_ok = run_javap(["-v", "-p", "-cp", classpath, entry])
        if not javap_ok:
            errors.append("%s: javap failed to read the compiled mixin - nothing was verified"
                          % short)
            continue
        targets = re.findall(r'targets=\["([^"]+)"\]', text)
        targets += [v.replace("/", ".") for v in re.findall(r'Mixin\(\s*value=\[class ([\w./$]+)', text)]
        injects, parse_errors = parse_injects(text)
        shadows = parse_shadows(text)
        shadow_methods = parse_shadow_methods(text)
        redirects, redirect_errors = parse_redirects(text)
        errors += ["%s: %s" % (short, e) for e in redirect_errors]
        errors += ["%s: %s" % (short, e) for e in parse_errors]

        if not targets:
            errors.append("%s: no @Mixin target could be parsed - this class was not verified" % short)
            continue

        for target in targets:
            if target not in jar_entries:
                errors.append("%s: target %s is not in any supplied jar" % (short, target))
                continue
            methods, fields = target_members(target, classpath)
            if methods is None:
                errors.append("%s: could not read %s" % (short, target))
                continue
            print("  %s -> %s (%d methods)" % (short, target, len(methods)))

            for spec, at, allow in injects:
                checked += 1
                if "(" in spec:
                    mname, desc = spec.split("(", 1)
                    matches = [k for k in methods if k == (mname, "(" + desc)]
                else:
                    matches = [k for k in methods if k[0] == spec]
                if not matches:
                    errors.append("%s: @Inject method %s does not exist in %s" % (short, spec, target))
                    continue
                if len(matches) > 1:
                    warnings.append("%s: @Inject %s is ambiguous (%d overloads)"
                                    % (short, spec, len(matches)))
                sites = methods[matches[0]] if at == "RETURN" else 1
                print("      %-7s %2d site(s)  allow=%-4s %s" % (at, sites, allow, spec[:58]))
                if allow is not None and sites > allow:
                    errors.append(
                        '%s: @At("%s") on %s has %d injection sites but allow=%d - Mixin raises '
                        "a critical injection failure at apply time" % (short, at, spec, sites, allow))

            bodies = None
            for spec, at, at_target, opcode, require in redirects:
                checked += 1
                if "(" not in spec:
                    errors.append("%s: @Redirect method %s needs an explicit descriptor" % (short, spec))
                    continue
                mname, mdesc = spec.split("(", 1)
                key = (mname, "(" + mdesc)
                if key not in methods:
                    errors.append("%s: @Redirect method %s does not exist in %s" % (short, spec, target))
                    continue
                if at != "FIELD":
                    warnings.append("%s: @Redirect at %s is not verified by this tool" % (short, at))
                    continue
                parsed = parse_field_target(at_target)
                if parsed is None:
                    errors.append("%s: @Redirect field target %r is unparseable" % (short, at_target))
                    continue
                owner, fname, fdesc = parsed
                owner_binary = owner.replace("/", ".")
                if owner_binary not in jar_entries:
                    errors.append("%s: @Redirect field owner %s is not in any supplied jar"
                                  % (short, owner_binary))
                    continue
                _om, ofields = target_members(owner_binary, classpath)
                if ofields is None or fname not in ofields:
                    errors.append("%s: @Redirect field %s.%s does not exist" % (short, owner_binary, fname))
                    continue
                if ofields[fname] != fdesc:
                    errors.append("%s: @Redirect field %s.%s is %s but the target declares %s"
                                  % (short, owner_binary, fname, fdesc, ofields[fname]))
                    continue
                if bodies is None:
                    bodies = target_method_bodies(target, classpath) or {}
                body = bodies.get(key, "")
                op = {180: "getfield", 181: "putfield"}.get(opcode)
                pattern = r"\b%s\b.*Field %s\.%s:%s" % (op or "(get|put)field",
                                                          re.escape(owner), re.escape(fname),
                                                          re.escape(fdesc))
                sites = len(re.findall(pattern, body))
                print("      REDIRECT %2d site(s)  require=%-4s %s.%s" % (sites, require, owner.split("/")[-1], fname))
                if sites == 0:
                    errors.append("%s: @Redirect target %s.%s is never read in %s"
                                  % (short, owner_binary, fname, spec))
                elif require is not None and sites != require:
                    errors.append("%s: @Redirect on %s.%s has %d sites in %s but require=%d"
                                  % (short, owner_binary, fname, sites, spec, require))

            for mname, mdesc in shadow_methods:
                checked += 1
                if (mname, mdesc) not in methods:
                    errors.append("%s: @Shadow method %s%s does not exist in %s"
                                  % (short, mname, mdesc, target))
                else:
                    print("      SHADOW     %s%s" % (mname, mdesc))

            for fname, fdesc in shadows:
                checked += 1
                if fname not in fields:
                    errors.append("%s: @Shadow field %s does not exist in %s" % (short, fname, target))
                elif fields[fname] != fdesc:
                    errors.append("%s: @Shadow %s is %s but the target has %s"
                                  % (short, fname, fdesc, fields[fname]))
                else:
                    print("      SHADOW     %s %s" % (fname, fdesc))

    print("\nchecked %d annotation references across %d mixins" % (checked, len(mixin_classes)))
    for w in warnings:
        print("WARN  " + w)
    for e in errors:
        print("ERROR " + e)
    print("RESULT: " + ("FAIL" if errors else "OK"))
    if expected:
        missing, unexpected = classify_expectations(errors, expected)
        for e in missing:
            print("ERROR expected error not produced: " + e)
        for e in unexpected:
            print("ERROR unexpected error: " + e)
        if missing or unexpected:
            return 1
        print("EXPECTED FAILURES ONLY: the fixture was rejected for exactly the intended reasons.")
        return 0
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
