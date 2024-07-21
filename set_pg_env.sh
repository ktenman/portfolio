#!/bin/sh
export POSTGRES_USER=$(cat /run/secrets/postgres_user)
export POSTGRES_PASSWORD=$(cat /run/secrets/postgres_password)
