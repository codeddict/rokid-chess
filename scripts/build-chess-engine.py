"""Build SF19 for Android ARM64/API31 with 16KB ELF load alignment.

No source patches. Reproducible flags and pinned neural network are recorded below.
Requires local NDK r28c (sdkmanager 'ndk;28.2.13676358').
"""
import concurrent.futures
import hashlib
import pathlib
import re
import subprocess
import tarfile
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
NDK = ROOT / '.tools/android-sdk/ndk/28.2.13676358'
SOURCE = ROOT / '.tools/stockfish-android/stockfish/src'
NETWORK = 'nn-1a298aa575a0.nnue'
NETWORK_SHA = '1a298aa575a085434d29027978dc36867fe9c5bcea9376654b7a8eba1e52dfc2'
URL = 'https://github.com/official-stockfish/Stockfish/releases/download/sf_19/stockfish-android-arm64-universal.tar.gz'


def main():
    toolchain = NDK / 'toolchains/llvm/prebuilt/windows-x86_64'
    compiler = toolchain / 'bin/clang++.exe'
    if not compiler.is_file():
        raise SystemExit('Install NDK: sdkmanager "ndk;28.2.13676358"')
    if not SOURCE.is_dir():
        directory = SOURCE.parents[1]
        directory.mkdir(parents=True, exist_ok=True)
        archive = directory / 'stockfish-android-arm64-universal.tar.gz'
        urllib.request.urlretrieve(URL, archive)
        with tarfile.open(archive) as tar:
            tar.extractall(directory, filter='data')
    network = SOURCE / NETWORK
    if not network.is_file() or hashlib.sha256(network.read_bytes()).hexdigest() != NETWORK_SHA:
        urllib.request.urlretrieve('https://media.githubusercontent.com/media/official-stockfish/networks/master/' + NETWORK, network)
    assert hashlib.sha256(network.read_bytes()).hexdigest() == NETWORK_SHA, 'NNUE checksum mismatch'
    makefile = (SOURCE / 'Makefile').read_text()
    sources = re.search(r'^SRCS = (.*?)\n\n', makefile, re.M | re.S).group(1).replace('\\\n', ' ').split()
    objects = ROOT / '.tools/chess-engine-objects'
    objects.mkdir(exist_ok=True)
    flags = ['--target=aarch64-linux-android31', '--sysroot=' + str(toolchain / 'sysroot'),
             '-O3', '-funroll-loops', '-std=c++17', '-fno-exceptions', '-fPIE',
             '-DNDEBUG', '-DIS_64BIT', '-DUSE_POPCNT', '-DUSE_NEON=8', '-march=armv8-a',
             '-fconstexpr-steps=500000000', '-pthread']

    def compile_one(name):
        obj = objects / (name.replace('/', '_') + '.o')
        subprocess.run([str(compiler), *flags, '-c', name, '-o', str(obj)], cwd=SOURCE, check=True)
        return obj

    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        compiled = list(pool.map(compile_one, sources))
    destination = ROOT / 'chessphone/src/main/jniLibs/arm64-v8a/libstockfish.so'
    destination.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run([str(compiler), '--target=aarch64-linux-android31', '--sysroot=' + str(toolchain / 'sysroot'),
                    '-pie', '-static-libstdc++', '-pthread', '-Wl,-z,max-page-size=16384', '-Wl,-z,common-page-size=16384',
                    *map(str, compiled), '-latomic', '-lm', '-o', str(destination)], check=True)
    subprocess.run([str(toolchain / 'bin/llvm-strip.exe'), str(destination)], check=True)
    print('Android ARM64 SF19 built:', hashlib.sha256(destination.read_bytes()).hexdigest())


if __name__ == '__main__':
    main()
