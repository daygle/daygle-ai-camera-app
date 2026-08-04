#!/usr/bin/env python3
"""Realign ELF shared libraries so LOAD segments are 16 KB page compatible.

Android requires every native library shipped in an APK to be loadable on
devices with 16 KB page sizes.  The requirement (as validated by bundletool /
Play's checker) is that for every PT_LOAD segment:

    (p_offset % 0x4000) == (p_vaddr % 0x4000)      # congruent at 16 KB
    p_align >= 0x4000                              # aligned at 16 KB

Prebuilt .so files linked with a 4 KB max-page-size (e.g. WireGuard's
com.wireguard.android:tunnel) violate this.  Rather than relinking, we insert
padding bytes *between* LOAD segments in the file so every segment satisfies
the congruence, then raise p_align to 0x4000.  Virtual addresses are never
changed, so no relocations, symbol values, or dynamic entries are touched --
the layout each segment maps is bit-for-bit identical.  Because 16 KB is a
multiple of 4 KB, the result also loads identically on 4 KB page devices.

Usage:
    align_elf_16k.py <path-to-.so-or-dir> [<path> ...]
    align_elf_16k.py --check <path-to-.so-or-dir> [<path> ...]

With --check only verifies compatibility and exits non-zero if any file fails.
Otherwise files are patched in place and re-verified.  Directories are
walked recursively for *.so files.
"""

import glob
import os
import struct
import sys

PAGE16 = 0x4000
PT_LOAD = 1
SHT_NOBITS = 8


def is_elf(data):
    return data[:4] == b"\x7fELF"


def parse_headers(data):
    if not is_elf(data):
        raise ValueError("not an ELF file")
    is64 = data[4] == 2
    little = data[5] == 1
    endian = "<" if little else ">"
    if is64:
        (e_phoff,) = struct.unpack_from(endian + "Q", data, 0x20)
        e_phentsize, e_phnum = struct.unpack_from(endian + "HH", data, 0x36)
        (e_shoff,) = struct.unpack_from(endian + "Q", data, 0x28)
        e_shentsize, e_shnum, e_shstrndx = struct.unpack_from(
            endian + "HHH", data, 0x3A
        )
        phdr = endian + "IIQQQQQQ"
        shdr = endian + "IIQQQQIIQQ"
    else:
        (e_phoff,) = struct.unpack_from(endian + "I", data, 0x1C)
        e_phentsize, e_phnum = struct.unpack_from(endian + "HH", data, 0x2A)
        (e_shoff,) = struct.unpack_from(endian + "I", data, 0x20)
        e_shentsize, e_shnum, e_shstrndx = struct.unpack_from(
            endian + "HHH", data, 0x2E
        )
        phdr = endian + "IIIIIIII"
        shdr = endian + "IIIIIIIIII"

    programs = []
    for i in range(e_phnum):
        off = e_phoff + i * e_phentsize
        fields = struct.unpack_from(phdr, data, off)
        if is64:
            p_type, p_flags, p_offset, p_vaddr, p_paddr, p_filesz, p_memsz, p_align = (
                fields
            )
        else:
            p_type, p_offset, p_vaddr, p_paddr, p_filesz, p_memsz, p_flags, p_align = (
                fields
            )
        programs.append(
            {
                "off": off,
                "type": p_type,
                "flags": p_flags,
                "offset": p_offset,
                "vaddr": p_vaddr,
                "filesz": p_filesz,
                "memsz": p_memsz,
                "align": p_align,
            }
        )

    sections = []
    for i in range(e_shnum):
        off = e_shoff + i * e_shentsize
        fields = struct.unpack_from(shdr, data, off)
        if is64:
            (
                sh_name,
                sh_type,
                sh_flags,
                sh_addr,
                sh_offset,
                sh_size,
                sh_link,
                sh_info,
                sh_addralign,
                sh_entsize,
            ) = fields
        else:
            (
                sh_name,
                sh_type,
                sh_flags,
                sh_addr,
                sh_offset,
                sh_size,
                sh_link,
                sh_info,
                sh_addralign,
                sh_entsize,
            ) = fields
        sections.append(
            {
                "off": off,
                "name": sh_name,
                "type": sh_type,
                "flags": sh_flags,
                "addr": sh_addr,
                "offset": sh_offset,
                "size": sh_size,
            }
        )

    return {
        "is64": is64,
        "endian": endian,
        "programs": programs,
        "sections": sections,
        "e_shoff": e_shoff,
        "e_shnum": e_shnum,
        "e_phentsize": e_phentsize,
        "e_shentsize": e_shentsize,
    }


def check_compat(meta, data):
    """Return (ok, list-of-problem-descriptions)."""
    problems = []
    for p in meta["programs"]:
        if p["type"] != PT_LOAD:
            continue
        if (p["offset"] % PAGE16) != (p["vaddr"] % PAGE16):
            problems.append(
                "LOAD off=0x%x vaddr=0x%x not congruent at 16 KB"
                % (p["offset"], p["vaddr"])
            )
        if p["align"] < PAGE16:
            problems.append(
                "LOAD off=0x%x p_align=0x%x < 0x4000" % (p["offset"], p["align"])
            )
    return (not problems, problems)


def align(data):
    meta = parse_headers(data)
    ok, problems = check_compat(meta, data)
    if ok:
        return data  # nothing to do

    loads = sorted(
        (p for p in meta["programs"] if p["type"] == PT_LOAD),
        key=lambda x: x["offset"],
    )
    # Choose padding for each LOAD segment.  Padding is inserted *before* the
    # segment's content at its original file offset; only the virtual addresses
    # matter for congruence, and they are never changed.
    pad_for = {}  # original offset -> pad bytes
    shift = 0
    for p in loads:
        base = p["offset"] + shift
        pad = (p["vaddr"] % PAGE16 - base % PAGE16) % PAGE16
        if pad:
            pad_for[p["offset"]] = pad
        shift += pad

    def shift_at(x):
        return sum(p for off, p in pad_for.items() if off <= x)

    # Build the new file: original bytes with padding blocks inserted before
    # each LOAD segment's content.
    chunks = []
    pos = 0
    for p in loads:
        chunks.append(data[pos : p["offset"]])
        pad = pad_for.get(p["offset"], 0)
        if pad:
            chunks.append(b"\x00" * pad)
        pos = p["offset"]
    chunks.append(data[pos:])
    new = bytearray(b"".join(chunks))
    m = parse_headers(bytes(new))

    def pack64(off, value):
        struct.pack_into(m["endian"] + ("Q" if m["is64"] else "I"), new, off, value)

    # Patch program headers: p_offset (shifted) and p_align (-> 16 KB).
    # ELF64 phdr: p_offset at +8, p_align at +48; ELF32: +4 / +28.
    for p in m["programs"]:
        if p["type"] == PT_LOAD:
            pack64(
                p["off"] + (8 if m["is64"] else 4),
                p["offset"] + shift_at(p["offset"]),
            )
            pack64(p["off"] + (48 if m["is64"] else 28), PAGE16)

    # Patch section headers: sh_offset (shifted).  Skip NOBITS and any entry
    # whose offset is not a real file position (stripped files sometimes carry
    # a 0xFFFF... marker).
    for s in m["sections"]:
        if s["type"] == SHT_NOBITS or s["offset"] == 0 or s["offset"] >= len(data):
            continue
        pack64(
            s["off"] + (24 if m["is64"] else 16),
            s["offset"] + shift_at(s["offset"]),
        )

    # Patch e_shoff in the ELF header (ELF64 at 0x28, ELF32 at 0x20).
    if m["e_shoff"]:
        pack64(
            0x28 if m["is64"] else 0x20,
            m["e_shoff"] + shift_at(m["e_shoff"]),
        )

    # Verify the result before returning.
    m2 = parse_headers(bytes(new))
    ok, problems = check_compat(m2, bytes(new))
    if not ok:
        raise RuntimeError("realignment verification failed: %r" % problems)
    return bytes(new)


def main(argv):
    check_only = False
    args = list(argv)
    if args and args[0] == "--check":
        check_only = True
        args = args[1:]
    if not args:
        print(__doc__)
        return 2

    files = []
    for path in args:
        if os.path.isdir(path):
            files.extend(
                sorted(glob.glob(os.path.join(path, "**", "*.so"), recursive=True))
            )
        else:
            files.append(path)

    failed = []
    for path in files:
        with open(path, "rb") as f:
            data = f.read()
        try:
            if check_only:
                ok, problems = check_compat(parse_headers(data), data)
            else:
                new = align(data)
                ok, problems = check_compat(parse_headers(new), new)
                if ok:
                    with open(path, "wb") as f:
                        f.write(new)
        except (ValueError, RuntimeError) as e:
            ok, problems = False, [str(e)]
        status = "OK" if ok else "FAIL: " + "; ".join(problems)
        print("%-60s %s" % (path, status))
        if not ok:
            failed.append(path)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
