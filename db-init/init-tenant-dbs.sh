#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE DATABASE tenant1_db;
    CREATE DATABASE tenant2_db;
    CREATE DATABASE tenant3_db;
EOSQL
