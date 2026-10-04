#!/bin/sh
mvn -q clean package && echo "DONE: target/UnstableFFA-1.0.0.jar -> copy it into your server's plugins folder"
