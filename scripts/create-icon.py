"""Copy the user-provided project logo byte-for-byte into both mod variants."""
from pathlib import Path
import shutil
root=Path(__file__).resolve().parents[1]
source=root/'branding/logo_white.png'
if source.read_bytes()[:8] != b'\x89PNG\r\n\x1a\n':
    raise ValueError('branding/logo_white.png must be a PNG')
for version in ['26.3','1.21.11']:
    target=root/'versions'/version/'src/main/resources/assets/egoxseedcracker/icon.png'
    target.parent.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(source,target)
print('EgoX logo copied unchanged to both Minecraft variants')
