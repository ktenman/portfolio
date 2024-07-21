#!/bin/sh
pg_isready -U "$(cat /run/secrets/postgres_user)" -d portfolio
