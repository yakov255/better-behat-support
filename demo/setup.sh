#!/bin/sh
docker run --rm -v "$(pwd)/demo:/app" composer:latest install
