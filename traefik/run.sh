docker run --rm -d -p 80:80 -v "/var/run/docker.sock:/var/run/docker.sock" --name traefik docker.io/traefik:3 --providers.docker=true --providers.docker.exposedbydefault=false --entrypoints.web.address=:80 --log.level=TRACE