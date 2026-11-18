{
  lib,
  stdenv,
  groovy,
  jdk,
  jre_headless,
  makeWrapper,
  git,
}:

stdenv.mkDerivation rec {
  pname = "aigest";
  version = "0.1.0";

  src = lib.cleanSource ../.;

  nativeBuildInputs = [
    groovy
    jdk
    makeWrapper
  ];

  buildPhase = ''
    runHook preBuild

    mkdir -p build/classes
    find src/aigest -name "*.groovy" > sources.txt
    groovyc -d build/classes @sources.txt

    if [ -d src/resources ]; then
      cp -r src/resources/* build/classes/ 2>/dev/null || true
    fi

    jar --create --file build/aigest.jar --main-class aigest.Main -C build/classes .

    runHook postBuild
  '';

  installPhase = ''
    runHook preInstall

    mkdir -p $out/share/aigest $out/bin
    cp build/aigest.jar $out/share/aigest/aigest.jar

    makeWrapper ${jre_headless}/bin/java $out/bin/aigest \
      --add-flags "-cp $out/share/aigest/aigest.jar:${groovy}/lib/*" \
      --add-flags "aigest.Main" \
      --prefix PATH : ${lib.makeBinPath [ git ]}

    runHook postInstall
  '';

  meta = with lib; {
    description = "Git repo ingestion tool";
    license = licenses.gpl3;
    platforms = platforms.unix;
    mainProgram = "aigest";
  };
}
