"""Reproducible source JAR, with no native binaries or bundled dependencies."""
from pathlib import Path
import hashlib,json,sys,urllib.request,zipfile

ROOT=Path(__file__).resolve().parents[1]
def runtime(directory):
    directory.mkdir(parents=True,exist_ok=True)
    for name,version in [('clojure','1.12.6'),('spec.alpha','0.5.238'),('core.specs.alpha','0.4.74')]:
        url=f'https://repo.maven.apache.org/maven2/org/clojure/{name}/{version}/{name}-{version}.jar'
        path=directory/f'{name}-{version}.jar'
        if not path.exists():path.write_bytes(urllib.request.urlopen(url,timeout=45).read())
        assert hashlib.sha1(path.read_bytes()).hexdigest()==urllib.request.urlopen(url+'.sha1',timeout=30).read().decode().strip()

def build(directory):
    directory.mkdir(parents=True,exist_ok=True)
    jar=directory/'property-payload-redactor-1.0.0.jar'
    files={str(p.relative_to(ROOT/'src')).replace('\\','/'):p.read_bytes() for p in (ROOT/'src').rglob('*.clj')}
    for name in ['README.md','LICENSE']:
        files['META-INF/'+name]=(ROOT/name).read_bytes()
    files['META-INF/maven/org.clojars.novasia-estate/property-payload-redactor/pom.xml']=(ROOT/'pom.xml').read_bytes()
    files['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\r\nImplementation-Title: Property Payload Redactor\r\nImplementation-Version: 1.0.0\r\n\r\n'
    with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as output:
        for name,data in sorted(files.items()):
            info=zipfile.ZipInfo(name,(2026,11,6,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
            output.writestr(info,data)
    (directory/'property-payload-redactor-1.0.0.pom').write_bytes((ROOT/'pom.xml').read_bytes())
    print(json.dumps({'jar':str(jar),'bytes':jar.stat().st_size,'sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'entries':sorted(files)}))

if __name__=='__main__':
    mode,directory=sys.argv[1:]
    if mode=='runtime':runtime(Path(directory))
    elif mode=='jar':build(Path(directory))
    else:raise SystemExit('Expected runtime or jar')
