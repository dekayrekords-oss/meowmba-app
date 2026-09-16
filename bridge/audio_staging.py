"""Import working copies: Logic may add metadata even to source audio files."""
import hashlib
import os
from pathlib import Path
import secrets


def stage_audio(source, root):
    source=Path(source); root=Path(root).resolve()
    if source.suffix.lower() not in ('.wav','.aif','.aiff','.mp3','.m4a','.caf','.flac'):
        raise ValueError('Choose a supported audio file for the Logic import')
    before=source.stat()
    if not source.is_file() or not 1 <= before.st_size <= 2_000_000_000:
        raise ValueError('Import audio must be a regular file under 2 GB')
    parent=root/'logic-imports'
    if parent.is_symlink():raise ValueError('The Logic import folder must not be a link')
    parent.mkdir(exist_ok=True)
    if len(list(parent.iterdir()))>=200:raise ValueError('Archive old Logic import copies before adding more')
    folder=parent/secrets.token_hex(12);folder.mkdir(mode=0o700)
    target=folder/source.name
    try:
        digest=hashlib.sha256()
        fd=os.open(target,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
        count=0
        with source.open('rb') as src,os.fdopen(fd,'wb') as dst:
            while block:=src.read(1_048_576):
                count+=len(block)
                if count>before.st_size:raise ValueError('Import source changed during copying')
                digest.update(block);dst.write(block)
            dst.flush();os.fsync(dst.fileno())
        after=source.stat()
        if count!=before.st_size or (before.st_ino,before.st_size,before.st_mtime_ns)!=(after.st_ino,after.st_size,after.st_mtime_ns):
            raise ValueError('Import source changed during copying')
        def file_hash(path):
            h=hashlib.sha256()
            with path.open('rb') as f:
                while block:=f.read(1_048_576):h.update(block)
            return h.hexdigest()
        sha=digest.hexdigest()
        if file_hash(source)!=sha or file_hash(target)!=sha:raise ValueError('Import copy failed hash verification')
        return dict(path=str(target),source=str(source),sha256=sha,bytes=count)
    except Exception:
        target.unlink(missing_ok=True);folder.rmdir();raise
