"""Чтение и запись NBT шаблонов структур без сторонних библиотек.

Каждое значение — пара (тип тега, содержимое), поэтому файл записывается
обратно байт в байт, с теми же типами. Шаблоны астероидов правились этим.
"""
import gzip
import struct


def _read(buf, pos, tag):
    if tag == 1:
        return struct.unpack_from('>b', buf, pos)[0], pos + 1
    if tag == 2:
        return struct.unpack_from('>h', buf, pos)[0], pos + 2
    if tag == 3:
        return struct.unpack_from('>i', buf, pos)[0], pos + 4
    if tag == 4:
        return struct.unpack_from('>q', buf, pos)[0], pos + 8
    if tag == 5:
        return struct.unpack_from('>f', buf, pos)[0], pos + 4
    if tag == 6:
        return struct.unpack_from('>d', buf, pos)[0], pos + 8
    if tag == 7:
        n = struct.unpack_from('>i', buf, pos)[0]
        return bytes(buf[pos + 4:pos + 4 + n]), pos + 4 + n
    if tag == 8:
        n = struct.unpack_from('>H', buf, pos)[0]
        return buf[pos + 2:pos + 2 + n].decode('utf-8'), pos + 2 + n
    if tag == 9:
        inner = buf[pos]
        n = struct.unpack_from('>i', buf, pos + 1)[0]
        pos += 5
        items = []
        for _ in range(n):
            v, pos = _read(buf, pos, inner)
            items.append(v)
        return (inner, items), pos
    if tag == 10:
        out = {}
        while True:
            t = buf[pos]
            pos += 1
            if t == 0:
                return out, pos
            n = struct.unpack_from('>H', buf, pos)[0]
            name = buf[pos + 2:pos + 2 + n].decode('utf-8')
            pos += 2 + n
            v, pos = _read(buf, pos, t)
            out[name] = (t, v)
    if tag == 11:
        n = struct.unpack_from('>i', buf, pos)[0]
        return list(struct.unpack_from('>%di' % n, buf, pos + 4)), pos + 4 + 4 * n
    if tag == 12:
        n = struct.unpack_from('>i', buf, pos)[0]
        return list(struct.unpack_from('>%dq' % n, buf, pos + 4)), pos + 4 + 8 * n
    raise ValueError(tag)


def _write(out, tag, v):
    if tag == 1:
        out += struct.pack('>b', v)
    elif tag == 2:
        out += struct.pack('>h', v)
    elif tag == 3:
        out += struct.pack('>i', v)
    elif tag == 4:
        out += struct.pack('>q', v)
    elif tag == 5:
        out += struct.pack('>f', v)
    elif tag == 6:
        out += struct.pack('>d', v)
    elif tag == 7:
        out += struct.pack('>i', len(v)) + v
    elif tag == 8:
        b = v.encode('utf-8')
        out += struct.pack('>H', len(b)) + b
    elif tag == 9:
        inner, items = v
        out += struct.pack('>bi', inner if items else (inner or 0), len(items))
        for item in items:
            _write(out, inner, item)
    elif tag == 10:
        for name, (t, val) in v.items():
            b = name.encode('utf-8')
            out += struct.pack('>bH', t, len(b)) + b
            _write(out, t, val)
        out += b'\x00'
    elif tag == 11:
        out += struct.pack('>i%di' % len(v), len(v), *v)
    elif tag == 12:
        out += struct.pack('>i%dq' % len(v), len(v), *v)
    else:
        raise ValueError(tag)


def load(path):
    buf = gzip.decompress(open(path, 'rb').read())
    assert buf[0] == 10
    n = struct.unpack_from('>H', buf, 1)[0]
    name = buf[3:3 + n].decode('utf-8')
    root, _ = _read(buf, 3 + n, 10)
    return name, root


def save(path, name, root):
    out = bytearray()
    b = name.encode('utf-8')
    out += struct.pack('>bH', 10, len(b)) + b
    _write(out, 10, root)
    open(path, 'wb').write(gzip.compress(bytes(out), mtime=0))
