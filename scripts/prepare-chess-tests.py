"""Download official Windows test runtimes; extract data only, never run an installer."""
import pathlib
import struct
import subprocess
import urllib.request
import zipfile
import zlib

ROOT = pathlib.Path(__file__).resolve().parents[1]


def main():
    cv = ROOT / '.tools/opencv-host'
    dll = cv / 'official412/opencv/build/java/x64/opencv_java4120.dll'
    if not dll.is_file():
        cv.mkdir(parents=True, exist_ok=True)
        executable = cv / 'opencv-4.12.0-windows.exe'
        if not executable.is_file():
            urllib.request.urlretrieve('https://github.com/opencv/opencv/releases/download/4.12.0/opencv-4.12.0-windows.exe', executable)
        raw = executable.read_bytes()
        start = 0
        while True:
            offset = raw.find(b'7z\xbc\xaf\x27\x1c', start)
            if offset < 0:
                raise ValueError('Valid 7-Zip header not found')
            if offset + 32 <= len(raw) and zlib.crc32(raw[offset+12:offset+32]) == struct.unpack_from('<I', raw, offset+8)[0]:
                break
            start = offset + 6
        archive = cv / 'opencv412.7z'
        archive.write_bytes(raw[offset:])
        (cv / 'official412').mkdir(exist_ok=True)
        subprocess.run(['tar', '-xf', str(archive), '-C', str(cv / 'official412'), 'opencv/build/java/x64/opencv_java4120.dll'], check=True)
    engine = ROOT / '.tools/stockfish/stockfish/stockfish-windows-x86-64-universal.exe'
    if not engine.is_file():
        directory = ROOT / '.tools/stockfish'
        directory.mkdir(parents=True, exist_ok=True)
        archive = directory / 'stockfish.zip'
        urllib.request.urlretrieve('https://github.com/official-stockfish/Stockfish/releases/download/sf_19/stockfish-windows-x86-64-universal.zip', archive)
        with zipfile.ZipFile(archive) as zip_file:
            data = zip_file.read('stockfish/stockfish-windows-x86-64-universal.exe')
        engine.parent.mkdir(parents=True, exist_ok=True)
        engine.write_bytes(data)
    print('Desktop vision and UCI test runtimes ready. No PC runtime service required.')


if __name__ == '__main__':
    main()
