#!/bin/bash

set -e

docker run -d \
  --name ligerblocks-build \
  --user "$(id -u):$(id -g)" \
  --env GRADLE_USER_HOME=/tmp/gradle \
  --volume "$PWD:/workspace" \
  --workdir /workspace \
  eclipse-temurin:21-jdk \
  tail -f /dev/null

echo "Persistent build container started with name 'ligerblocks-build'"
echo "You can now exec into the container using: docker exec -it ligerblocks-build bash"
echo "To stop the container, use: docker stop ligerblocks-build"
echo "To remove the container, use: docker rm ligerblocks-build"