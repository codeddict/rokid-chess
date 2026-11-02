"""Package corresponding app/engine sources without workspace credentials or game data."""
from pathlib import Path
import json
import re
import zipfile

ROOT=Path(__file__).resolve().parents[1]


def main():
    metadata=json.loads((ROOT/'chessphone/build/outputs/apk/debug/output-metadata.json').read_text(encoding='utf-8'))
    version=metadata['elements'][0]['versionName']
    if not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+',version):raise ValueError('Invalid release version')
    paths=[ROOT/n for n in ['build.gradle','gradle.properties','gradlew','gradlew.bat','chessphone/build.gradle','chessglasses/build.gradle',
        'scripts/chess.ps1','scripts/build-chess-engine.py','scripts/prepare-chess-tests.py','scripts/package-chess-source.py',
        'docs/ROKID_CHESS_LIVE.md','docs/ROKID_CHESS_GLASS_CONTROLS.md','docs/ROKID_CHESS_UPDATER.md',
        'docs/ROKID_CHESS_CAMERA.md','docs/ROKID_CHESS_RELEASE_v0.5.2.md']]
    for directory in ['gradle/wrapper','chessphone/src','chessphone/libs','chessglasses/src','chessshared/java','.tools/stockfish-android/stockfish/src']:
        paths.extend(p for p in (ROOT/directory).rglob('*') if p.is_file() and 'jniLibs' not in p.parts and '__pycache__' not in p.parts and p.suffix not in ('.o','.exe'))
    for name in ['AUTHORS','Copying.txt','README.md']:paths.append(ROOT/'.tools/stockfish-android/stockfish'/name)
    settings='\n'.join(l for l in (ROOT/'settings.gradle').read_text(encoding='utf-8').splitlines() if not l.startswith("include ':app'") and not l.startswith("include ':hqphone'"))+'\n'
    destination=ROOT/f'artifacts/rokid-chess-source-v{version}.zip'
    with zipfile.ZipFile(destination,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as archive:
        for path in sorted(set(paths)):
            relative=path.relative_to(ROOT).as_posix()
            if '.private/' in relative or path.suffix in ('.jks','.keystore'):raise ValueError('Private input refused')
            archive.write(path,relative)
        archive.writestr('settings.gradle',settings)
        archive.writestr('SOURCE-README.txt',f'Rokid Chess {version}: phone, glasses and shared controls source; unmodified Stockfish SF19 source and exact NNUE. GPL text and NDK r28c build recipe included. See docs/ROKID_CHESS_GLASS_CONTROLS.md, scripts/build-chess-engine.py. Configure JDK17, SDK36, NDK28.2.13676358 under .tools before building. No credentials or user game data included.\n')
    print(str(destination))


if __name__=='__main__':main()
